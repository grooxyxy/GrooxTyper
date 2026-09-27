package com.grooxtyper.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Opsi model bubble yang bisa dipilih user di dialog Bubble Detector.
 *
 * BUBBLE -> `models/bd.onnx` (ogkalu RT-DETR v2 v4-small int8, 3 kelas
 * bubble/text_bubble/text_free, input 640x640, 2 output logits (1,300,3) +
 * boxes (1,300,4) cxcywh ternormalisasi, end-to-end NMS-free).
 * Model DIEKSEKUSI LANGSUNG di perangkat via ONNX Runtime Mobile.
 * Nama file disamarkan agar tak terekspos di APK.
 *
 * Kompatibel mundur: YOLO26 end-to-end (1,300,6), klasik detect (1,6,8400),
 * dan klasik 2-output seg (37ch + protos) tetap didukung bila model lama
 * dipakai; jalur detect tanpa mask.
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
    BUBBLE(
        "Bubble Detector • on-device",
        "ogkalu RT-DETR v2 v4-small int8 (~11MB) via ONNX Runtime, input 640x640",
        "models/bd.onnx"
    )
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float,
    /** Mask segmentasi per bubble dalam koordinat kanvas penuh (boleh null; null untuk model detect). */
    val mask: Bitmap? = null,
    /** Id kelas model: 0 = text. -1 = tak diketahui. */
    val classId: Int = -1
)

/** Asset model bubble yang tersedia di `assets/models/` (nama disamarkan). */
object YoloBubbleModel {
    const val SEG_ASSET = "models/bd.onnx"
}

/**
 * Detektor balon teks manga on-device.
 *
 * Jalur utama: inferensi ONNX via ONNX Runtime Mobile.
 * - Model aktif: ogkalu RT-DETR v2 v4-small int8 (Apache-2.0,
 *   ogkalu/comic-text-and-bubble-detector `detector-v4-s_int8.onnx`, ~11MB,
 *   3 kelas bubble/text_bubble/text_free, latih 640), input 640x640,
 *   2 output logits (1,300,3) + boxes (1,300,4) cxcywh ternormalisasi
 *   → box kelas bubble (mask=null, tanpa NMS karena head sudah one-to-one).
 *   Model di-download saat build di GitHub Actions, tidak di-commit ke repo
 *   (batas <50MB terpenuhi).
 * - Legacy: Kiuyha YOLO26s detect end-to-end (1,300,6); YOLO seg 1-class,
 *   output (1,37,8400) + protos (1,32,160,160) dengan mask ALPHA_8 per bubble.
 * Untuk kanvas jangkung (mis. 720x16000) gambar dipotong jadi tile persegi
 * ber-overlap 30%; setiap tile di-resize ke 640x640, dijalankan
 * lewat model, lalu duplikat di sambungan tile dibuang via NMS global.
 *
 * Bila session ONNX gagal dibuat (asset hilang / runtime tidak tersedia),
 * deteksi GAGAL dengan error (tanpa fallback) agar kegagalan model
 * selalu terlihat, bukan disamarkan hasil heuristik.
 */
class BubbleDetector {

    companion object {
        private const val INPUT_SIZE = 640
        private const val NUM_MASK_COEF = 32
        private const val PROTO_SIZE = 160
        // Legacy seg: box(4) + 1 kelas + 32 koef = 37 kanal.
        private const val NUM_CHANNELS_SEG = 4 + 1 + NUM_MASK_COEF
        private const val CONF_THRESH = 0.25f
        private const val TALL_CONF_THRESH = 0.15f
        private const val IOU_THRESH = 0.45f
        private const val MAX_DETECTIONS = 150
        private const val TALL_MAX_DETECTIONS = 300
    }

    @Volatile
    private var ortSession: OrtSession? = null
    private val sessionMutex = Mutex()
    private val inferMutex = Mutex()

    /** Alasan kegagalan pemuatan model terakhir (null bila belum ada / sukses). */
    @Volatile
    var lastError: String? = null
        private set

    /** Ringkasan output inferensi terakhir (untuk dialog diagnostik). */
    @Volatile
    var lastOutputDesc: String? = null
        private set

    /**
     * Pastikan session ONNX termuat. True bila siap inferensi; false + [lastError]
     * terisi bila asset hilang/rusak atau runtime tak tersedia. Tanpa fallback.
     */
    suspend fun ensureLoaded(context: Context, asset: String): Boolean {
        if (ortSession != null) return true
        val session = ensureSession(context, asset)
        if (session == null && lastError == null) {
            lastError = "Session ONNX null tanpa detail"
        }
        return session != null
    }

    /**
     * Jalankan opsi terpilih. [appContext] WAJIB diisi agar asset ONNX bisa
     * dimuat; bila null atau session gagal dibuat, kembalikan daftar kosong
     * dan isi [lastError]. TANPA fallback heuristik.
     */
    suspend fun detect(
        bitmap: Bitmap,
        model: BubbleModel,
        appContext: Context? = null,
        onProgress: ((Float) -> Unit)? = null
    ): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
                if (appContext == null) {
                    lastError = "Context null: asset ONNX tak bisa dibuka"
                    android.util.Log.e("Bubble", lastError!!)
                    return@withContext emptyList<DetectedBubble>()
                }
                val session = ensureSession(appContext, model.asset)
                if (session == null) {
                    if (lastError == null) lastError = "Session ONNX null tanpa detail"
                    android.util.Log.e("Bubble", lastError!!)
                    return@withContext emptyList<DetectedBubble>()
                }
                val longSide = max(bitmap.width, bitmap.height)
                val shortSide = min(bitmap.width, bitmap.height).coerceAtLeast(1)
                val aspect = longSide / shortSide.toFloat()
                if (aspect >= 3f || longSide >= 2000) {
                    return@withContext detectTallOnnx(bitmap, session, onProgress)
                }
                val dets = runOnnx(session, bitmap).filter { it.classId == 0 }
                nms(dets, IOU_THRESH)
                    .sortedByDescending { it.score }
                    .take(MAX_DETECTIONS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Pembatalan (dialog ditutup / deteksi baru) bukan error:
                // lempar ulang agar coroutine benar-benar berhenti.
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /** Bebaskan session ONNX (panggil saat editor ditutup bila perlu). */
    fun close() {
        runCatching { ortSession?.close() }
        ortSession = null
    }

    // ------------------------------------------------------------------
    // Jalur ONNX
    // ------------------------------------------------------------------

    private suspend fun ensureSession(context: Context, asset: String): OrtSession? {
        ortSession?.let { return it }
        return sessionMutex.withLock {
            ortSession?.let { return@withLock it }
            try {
                val bytes = context.assets.open(asset).use { it.readBytes() }
                if (bytes.isEmpty()) {
                    lastError = "Asset $asset kosong (0 byte)"
                    android.util.Log.e("Bubble", lastError!!)
                    return@withLock null
                }
                android.util.Log.i("Bubble", "Memuat model ${bytes.size} byte dari $asset")
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(bytes, opts).also {
                    ortSession = it
                    lastError = null
                    android.util.Log.i("Bubble", "Session ONNX siap: in=${it.inputNames} out=${it.outputNames}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lastError = "Gagal muat $asset: ${e.message ?: e.javaClass.simpleName}"
                android.util.Log.e("Bubble", lastError!!)
                null
            }
        }
    }

    private data class RawDet(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val score: Float,
        val coef: FloatArray
    )

    private data class RawBox(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val score: Float,
        val classId: Int
    )

    /**
     * Inferensi satu bitmap ukuran bebas: RESIZE langsung ke 640x640, decode
     * output, kembalikan deteksi dalam koordinat bitmap asli.
     * Resize (bukan letterbox) karena model card ogkalu eksplisit: "Training
     * Images were resized, not cropped" — padding abu ala YOLO di luar
     * distribusi latih dan menekan skor. Mendukung varian:
     * - RT-DETR: 2 output logits + boxes (bentuk fleksibel, lihat decodeRtDetr).
     * - detect 2-class baru: 1 output (1,6,8400) atau (1,8400,6), tanpa mask.
     * - seg legacy: 2 output (1,37,8400) + (1,32,160,160) dengan mask.
     */
    private suspend fun runOnnx(session: OrtSession, src: Bitmap, conf: Float = CONF_THRESH): List<DetectedBubble> {
        if (src.width <= 0 || src.height <= 0) return emptyList()
        kotlinx.coroutines.ensureActive()
        // Resize langsung (stretch) 640x640 sesuai preprocessor training.
        val scaleX = INPUT_SIZE / src.width.toFloat()
        val scaleY = INPUT_SIZE / src.height.toFloat()

        val resized = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        val rpx = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(rpx, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        resized.recycle()

        val data = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val plane = INPUT_SIZE * INPUT_SIZE
        for (i in rpx.indices) {
            val p = rpx[i]
            data[i] = ((p shr 16) and 0xFF) / 255f
            data[plane + i] = ((p shr 8) and 0xFF) / 255f
            data[2 * plane + i] = (p and 0xFF) / 255f
        }

        return inferMutex.withLock {
            var inputTensor: OnnxTensor? = null
            var results: OrtSession.Result? = null
            try {
                val env = OrtEnvironment.getEnvironment()
                inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(data),
                    longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
                )
                results = session.run(mapOf(session.inputNames.first() to inputTensor))
                val res = results ?: return@withLock emptyList<DetectedBubble>()
                // Jalur seg legacy bila ada 2 output.
                if (res.size() >= 2) {
                    try {
                        val out0 = res[0].value as Array<Array<FloatArray>> // (1,37,8400)
                        val out1 = res[1].value as Array<Array<Array<FloatArray>>> // (1,32,160,160)
                        // Pastikan kanal seg agar tidak salah decode model detect.
                        if (out0[0].size == NUM_CHANNELS_SEG) {
                            lastOutputDesc = "seg 37ch+protos"
                            return@withLock decodeSeg(out0[0], out1[0], src.width, src.height, scaleX, scaleY)
                        }
                    } catch (e: Exception) {
                        // Jatuh ke jalur detect di bawah.
                    }
                }
                // Jalur RT-DETR (ogkalu v4-small int8): logits + boxes.
                // Dicek SEBELUM jalur generik agar tidak salah decode
                // sebagai YOLO klasik.
                if (res.size() >= 2) {
                    val rt = decodeRtDetr(res, src.width, src.height, scaleX, scaleY, conf)
                    if (rt != null) {
                        lastOutputDesc = "rtdetr kept=${rt.size}"
                        return@withLock rt
                    }
                    // else: bentuk tak cocok → lastOutputDesc sudah diisi
                    // pola yang teramati (lihat decodeRtDetr) untuk diagnosa.
                }
                // Jalur detect: 1 output (1,C,N) klasik, (1,N,C) transpos, atau
                // (1,N,6) end-to-end NMS-free (YOLO26: x1,y1,x2,y2,score,cls).
                val rawVal = res[0].value
                val mat: Array<FloatArray>? = extractBatchMatrix(rawVal)
                if (mat != null) {
                    lastOutputDesc = "out=${res.size()} mat=${mat.size}x${mat[0].size}"
                    if (mat.isNotEmpty() && mat[0].size == 6 && mat.size in 2..1000) {
                        decodeE2E(mat, src.width, src.height, scaleX, scaleY, conf).also {
                            lastOutputDesc = "e2e rows=${mat.size} kept=${it.size}"
                        }
                    } else {
                        decodeDetect(mat, src.width, src.height, scaleX, scaleY, conf).also {
                            lastOutputDesc = "detect mat=${mat.size}x${mat[0].size} kept=${it.size}"
                        }
                    }
                } else {
                    lastOutputDesc = "out=${res.size()} bentuk tak dikenal"
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            } finally {
                runCatching { inputTensor?.close() }
                runCatching { results?.close() }
            }
        }
    }

    /**
     * Ambil matriks 2D batch-0 dari output ONNX 3D (1,C,N) atau (1,N,C).
     * Mengembalikan null bila bentuk tak dikenali.
     */
    private fun extractBatchMatrix(rawVal: Any?): Array<FloatArray>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val batch = (rawVal as Array<*>)[0] as? Array<FloatArray> ?: return null
            if (batch.isEmpty() || batch[0].isEmpty()) return null
            batch
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode output YOLO end-to-end NMS-free: matriks (N,6) dengan baris
     * [x1,y1,x2,y2,score,cls] dalam skala input resize (640).
     * Sudah deduplikasi oleh head (one-to-one matching) sehingga tanpa NMS:
     * cukup threshold skor + buang box mungil. Mask=null (box-only).
     */
    private fun decodeE2E(
        mat: Array<FloatArray>,
        origW: Int, origH: Int,
        scaleX: Float, scaleY: Float,
        conf: Float = CONF_THRESH
    ): List<DetectedBubble> {
        val out = ArrayList<DetectedBubble>(mat.size.coerceAtMost(300))
        for (row in mat) {
            if (row.size < 6) continue
            val score = row[4]
            if (score < conf) continue
            val cls = row[5].toInt()
            // Balik resize ke koordinat bitmap asli.
            val x1 = row[0] / scaleX
            val y1 = row[1] / scaleY
            val x2 = row[2] / scaleX
            val y2 = row[3] / scaleY
            val box = RectF(
                x1.coerceIn(0f, origW.toFloat()),
                y1.coerceIn(0f, origH.toFloat()),
                x2.coerceIn(0f, origW.toFloat()),
                y2.coerceIn(0f, origH.toFloat())
            )
            if (box.width() < 8f || box.height() < 8f) continue
            out.add(DetectedBubble(box, score, null, cls))
        }
        return out.sortedByDescending { it.score }
    }

    /**
     * Decode output RT-DETR v2 (ogkalu, mis. v4-small int8): logits (N,C)
     * untuk kelas [bubble, text_bubble, text_free, ...] + boxes (N,4) cxcywh
     * yang dinormalisasi 0..1 terhadap input 640. Skor = sigmoid (focal
     * loss), tanpa NMS (end-to-end one-to-one). Hanya kelas 0 (bubble) yang
     * dipakai, konsisten dengan filter classId == 0 di jalur lain.
     *
     * TAHAN-BENTUK (akar "model tidak mendeteksi" pada varian int8): peran
     * output dikenali dari NAMA dulu (logit* vs box*), lalu dari bentuk;
     * jumlah query N bebas (bukan hardcode 300); layout transpos (C,N)
     * didukung; baris numerik non-float (Byte/Short/Int/Long/Double, lazim
     * pada model terkuantisasi) dikonversi; kelas 2..8 didukung. Bentuk yang
     * teramati SELALU dicatat di lastOutputDesc untuk diagnosa.
     * Mengembalikan null bila bentuk output tidak cocok (biar jatuh ke jalur lain).
     */
    private fun decodeRtDetr(
        res: OrtSession.Result,
        origW: Int, origH: Int,
        scaleX: Float, scaleY: Float,
        conf: Float = CONF_THRESH
    ): List<DetectedBubble>? {
        return try {
            if (res.size() < 2) {
                lastOutputDesc = "rtdetr: output<2 (size=${res.size()})"
                return null
            }
            val names = try {
                res.map { it.key }.map { it.toString() }
            } catch (e: Exception) {
                emptyList<String>()
            }
            fun toFloatMatrix(idx: Int): Array<FloatArray>? {
                val v = res[idx].value as? Array<*> ?: return null
                val batch = (v as Array<*>)[0] as? Array<*> ?: return null
                if (batch.isEmpty()) return null
                return Array(batch.size) { r ->
                    when (val row = batch[r]) {
                        is FloatArray -> row
                        is DoubleArray -> FloatArray(row.size) { row[it].toFloat() }
                        is IntArray -> FloatArray(row.size) { row[it].toFloat() }
                        is LongArray -> FloatArray(row.size) { row[it].toFloat() }
                        is ShortArray -> FloatArray(row.size) { row[it].toFloat() }
                        is ByteArray -> FloatArray(row.size) { row[it].toFloat() }
                        else -> return null
                    }
                }
            }
            fun shapeOf(idx: Int): String {
                return try {
                    val m = toFloatMatrix(idx) ?: return "?"
                    "${m.size}x${m[0].size}"
                } catch (e: Exception) {
                    "?"
                }
            }
            val m0 = toFloatMatrix(0)
            val m1 = toFloatMatrix(1)
            if (m0 == null || m1 == null || m0.isEmpty() || m1.isEmpty()) {
                lastOutputDesc = "rtdetr: matriks tak terbaca (${shapeOf(0)}, ${shapeOf(1)})"
                return null
            }
            // Normalisasi orientasi ke (N,C): dukung (C,N) juga.
            fun orient(m: Array<FloatArray>): Array<FloatArray> {
                val d0 = m.size
                val d1 = m[0].size
                if (d0 in 2..8 && d1 > 100) {
                    // (C,N) → transpos ke (N,C).
                    return Array(d1) { a -> FloatArray(d0) { c -> m[c][a] } }
                }
                return m
            }
            var a = orient(m0)
            var b = orient(m1)
            // Tetapkan peran: nama dulu, lalu bentuk (logits = inner 2..8
            // kecuali 4 yang boxes; boxes = inner tepat 4).
            fun isLogitsName(n: String) = n.contains("logit", ignoreCase = true)
            fun isBoxName(n: String) = n.contains("box", ignoreCase = true) ||
                n.contains("bbox", ignoreCase = true)
            var logits: Array<FloatArray>? = null
            var boxes: Array<FloatArray>? = null
            val n0 = names.getOrNull(0) ?: ""
            val n1 = names.getOrNull(1) ?: ""
            if ((isLogitsName(n0) && isBoxName(n1)) || (isBoxName(n0) && isLogitsName(n1))) {
                if (isLogitsName(n0)) {
                    logits = a
                    boxes = b
                } else {
                    logits = b
                    boxes = a
                }
            } else {
                // Heuristik bentuk: inner==4 → boxes; inner 2..8 (≠4) → logits.
                fun role(m: Array<FloatArray>): Char = when (m[0].size) {
                    4 -> 'B'
                    in 2..8 -> 'L'
                    else -> '?'
                }
                val ra = role(a)
                val rb = role(b)
                if (ra == 'L' && rb == 'B') {
                    logits = a
                    boxes = b
                } else if (ra == 'B' && rb == 'L') {
                    logits = b
                    boxes = a
                } else {
                    lastOutputDesc = "rtdetr: bentuk tak cocok (${a.size}x${a[0].size}, ${b.size}x${b[0].size})"
                    return null
                }
            }
            val lg = logits ?: return null
            val bx = boxes ?: return null
            val n = minOf(lg.size, bx.size)
            // Jumlah query bebas (varian small/middle bisa beda dari 300).
            if (n < 10 || n > 5000) {
                lastOutputDesc = "rtdetr: N=$n di luar wajar"
                return null
            }
            if (bx[0].size != 4 || lg[0].size !in 2..8) {
                lastOutputDesc = "rtdetr: inner tak cocok (${lg[0].size}, ${bx[0].size})"
                return null
            }
            val numClasses = lg[0].size
            val out = ArrayList<DetectedBubble>(64)
            for (i in 0 until n) {
                val l = lg[i]
                val bb = bx[i]
                var best = 0
                var bestS = Float.NEGATIVE_INFINITY
                for (c in 0 until numClasses) {
                    val s = 1f / (1f + kotlin.math.exp(-l[c]))
                    if (s > bestS) {
                        bestS = s
                        best = c
                    }
                }
                if (bestS < conf || best != 0) continue
                // cxcywh ternormalisasi → piksel resize → koordinat asli.
                val cx = (bb[0] * INPUT_SIZE) / scaleX
                val cy = (bb[1] * INPUT_SIZE) / scaleY
                val bw = bb[2] * INPUT_SIZE / scaleX
                val bh = bb[3] * INPUT_SIZE / scaleY
                val box = RectF(
                    cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f
                )
                box.left = box.left.coerceIn(0f, origW.toFloat())
                box.top = box.top.coerceIn(0f, origH.toFloat())
                box.right = box.right.coerceIn(0f, origW.toFloat())
                box.bottom = box.bottom.coerceIn(0f, origH.toFloat())
                if (box.width() < 8f || box.height() < 8f) continue
                out.add(DetectedBubble(box, bestS, null, best))
            }
            out.sortedByDescending { it.score }
        } catch (e: Exception) {
            lastOutputDesc = "rtdetr: exception ${e.javaClass.simpleName}"
            null
        }
    }

    /**
     * Decode output YOLO detect: matriks (C,N) atau (N,C) dengan
     * C = 4 box (cx,cy,w,h relatif 640) + numClasses skor.
     * Model klasik: C=6 (2 kelas). Mask=null (box-only).
     */
    private fun decodeDetect(
        mat: Array<FloatArray>,
        origW: Int, origH: Int,
        scaleX: Float, scaleY: Float,
        conf: Float = CONF_THRESH
    ): List<DetectedBubble> {
        val d0 = mat.size
        if (d0 == 0) return emptyList()
        val d1 = mat[0].size
        if (d1 == 0) return emptyList()
        // Tentukan layout: (C,N) bila d0 kecil & d1 besar, (N,C) sebaliknya.
        val isChannelFirst = if (d0 in 5..10 && d1 > 100) true
        else if (d1 in 5..10 && d0 > 100) false
        else d0 <= d1 // fallback: kanal lebih sedikit dari anchor
        val numChannels: Int
        val numAnchors: Int
        fun get(c: Int, a: Int): Float = if (isChannelFirst) mat[c][a] else mat[a][c]
        if (isChannelFirst) {
            numChannels = d0
            numAnchors = d1
        } else {
            numChannels = d1
            numAnchors = d0
        }
        if (numChannels < 5 || numAnchors <= 0) return emptyList()
        val numClasses = numChannels - 4
        if (numClasses <= 0) return emptyList()

        val raw = ArrayList<RawBox>(64)
        for (a in 0 until numAnchors) {
            var bestScore = Float.NEGATIVE_INFINITY
            var bestCls = 0
            for (c in 0 until numClasses) {
                val s = get(4 + c, a)
                if (s > bestScore) {
                    bestScore = s
                    bestCls = c
                }
            }
            if (bestScore < conf) continue
            val cx = get(0, a)
            val cy = get(1, a)
            val w = get(2, a)
            val h = get(3, a)
            if (w <= 0f || h <= 0f) continue
            val x1 = (cx - w / 2f) / scaleX
            val y1 = (cy - h / 2f) / scaleY
            val x2 = (cx + w / 2f) / scaleX
            val y2 = (cy + h / 2f) / scaleY
            raw.add(RawBox(x1, y1, x2, y2, bestScore, bestCls))
        }
        if (raw.isEmpty()) return emptyList()

        // NMS global (abaikan kelas agar duplikat menyatu).
        val kept = ArrayList<RawBox>()
        for (d in raw.sortedByDescending { it.score }) {
            var dup = false
            for (k in kept) {
                if (iou(d.x1, d.y1, d.x2, d.y2, k.x1, k.y1, k.x2, k.y2) > IOU_THRESH) {
                    dup = true; break
                }
            }
            if (!dup) kept.add(d)
        }

        val out = ArrayList<DetectedBubble>(kept.size)
        for (d in kept) {
            val box = RectF(
                d.x1.coerceIn(0f, origW.toFloat()),
                d.y1.coerceIn(0f, origH.toFloat()),
                d.x2.coerceIn(0f, origW.toFloat()),
                d.y2.coerceIn(0f, origH.toFloat())
            )
            if (box.width() < 8f || box.height() < 8f) continue
            out.add(DetectedBubble(box, d.score, null, d.classId))
        }
        return out
    }

    /**
     * Decode legacy YOLOv11n-seg: preds (37, 8400) + protos (32,160,160).
     * 37 kanal = 4 box (cx,cy,w,h relatif 640) + 1 skor kelas + 32 koefisien mask.
     * Mask diproses HANYA untuk deteksi terbaik per region (hemat memori/CPU):
     * protos x koefisien -> sigmoid -> crop ke box -> resize ke koordinat asli.
     * Dipertahankan untuk kompatibilitas mundur; model baru memakai decodeDetect.
     */
    private fun decodeSeg(
        preds: Array<FloatArray>,
        protos: Array<Array<FloatArray>>,
        origW: Int, origH: Int,
        scaleX: Float, scaleY: Float
    ): List<DetectedBubble> {
        val numAnchors = preds[0].size
        if (numAnchors <= 0) return emptyList()

        val raw = ArrayList<RawDet>(64)
        for (a in 0 until numAnchors) {
            val score = preds[4][a]
            if (score < CONF_THRESH) continue
            val cx = preds[0][a]
            val cy = preds[1][a]
            val w = preds[2][a]
            val h = preds[3][a]
            // Balik resize ke koordinat bitmap asli.
            val x1 = (cx - w / 2f) / scaleX
            val y1 = (cy - h / 2f) / scaleY
            val x2 = (cx + w / 2f) / scaleX
            val y2 = (cy + h / 2f) / scaleY
            val coef = FloatArray(NUM_MASK_COEF) { k -> preds[5 + k][a] }
            raw.add(RawDet(x1, y1, x2, y2, score, coef))
        }
        if (raw.isEmpty()) return emptyList()

        // NMS per tile sebelum mask diproses.
        val kept = ArrayList<RawDet>()
        for (d in raw.sortedByDescending { it.score }) {
            var dup = false
            for (k in kept) {
                if (iou(d.x1, d.y1, d.x2, d.y2, k.x1, k.y1, k.x2, k.y2) > IOU_THRESH) {
                    dup = true; break
                }
            }
            if (!dup) kept.add(d)
        }

        val out = ArrayList<DetectedBubble>(kept.size)
        for (d in kept) {
            val box = RectF(
                d.x1.coerceIn(0f, origW.toFloat()),
                d.y1.coerceIn(0f, origH.toFloat()),
                d.x2.coerceIn(0f, origW.toFloat()),
                d.y2.coerceIn(0f, origH.toFloat())
            )
            if (box.width() < 8f || box.height() < 8f) continue
            val mask = buildMask(protos, d.coef, box, scaleX, scaleY)
            out.add(DetectedBubble(box, d.score, mask, classId = 0))
        }
        return out
    }

    /**
     * Bangun mask ALPHA_8 sebesar bounding-box bubble (koordinat kanvas
     * asli) dari protos segmentasi. Mask = sigmoid(protos . coef), di-crop
     * ke box lalu di-resize.
     */
    private fun buildMask(
        protos: Array<Array<FloatArray>>,
        coef: FloatArray,
        box: RectF,
        scaleX: Float,
        scaleY: Float
    ): Bitmap? {
        return try {
            val pw = box.width().toInt().coerceIn(1, 2048)
            val ph = box.height().toInt().coerceIn(1, 2048)

            // Box dalam koordinat proto (160x160): box-asli -> 640 -> 160.
            val px1 = ((box.left * scaleX) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(0, PROTO_SIZE - 1)
            val py1 = ((box.top * scaleY) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(0, PROTO_SIZE - 1)
            val px2 = ((box.right * scaleX) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(px1 + 1, PROTO_SIZE)
            val py2 = ((box.bottom * scaleY) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(py1 + 1, PROTO_SIZE)

            val cw = px2 - px1
            val ch = py2 - py1
            val protoMask = FloatArray(cw * ch)
            for (k in 0 until NUM_MASK_COEF) {
                val c = coef[k]
                if (c == 0f) continue
                val plane = protos[k]
                for (yy in 0 until ch) {
                    val row = plane[py1 + yy]
                    val off = yy * cw
                    for (xx in 0 until cw) {
                        protoMask[off + xx] += c * row[px1 + xx]
                    }
                }
            }
            // Sigmoid -> alpha 0..255
            val alphas = ByteArray(cw * ch)
            for (i in protoMask.indices) {
                val v = 1f / (1f + Math.exp(-protoMask[i].toDouble())).toFloat()
                alphas[i] = (v * 255f).toInt().coerceIn(0, 255).toByte()
            }
            val small = Bitmap.createBitmap(cw, ch, Bitmap.Config.ALPHA_8)
            small.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alphas))
            val scaled = Bitmap.createScaledBitmap(small, pw, ph, true)
            if (scaled != small) small.recycle()
            scaled
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deteksi ONNX untuk gambar jangkung/lebar ekstrem (mis. 720x16000):
     * potong sepanjang sumbu panjang jadi jendela persegi (sisi = sisi
     * pendek, min 512px, maks 1024px) ber-overlap, inferensi per tile,
     * geser koordinat, lalu NMS global untuk buang duplikat di overlap.
     *
     * Anti-delay/crash: tile yang hampir polos (sketsa kosong) dilewati
     * tanpa inferensi; jumlah tile dibatasi (overlap minimum 64px);
     * tiap tile mengecek pembatalan (ensureActive) sehingga dialog yang
     * ditutup / deteksi baru langsung menghentikan antrean.
     */
    private suspend fun detectTallOnnx(bitmap: Bitmap, session: OrtSession, onProgress: ((Float) -> Unit)? = null): List<DetectedBubble> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        val win = shortSide.coerceIn(512, 1024)
        // Batas tile: hitung step agar tile <= 40 dengan overlap min 64px.
        // Tanpa batas, 720x16000 = 31 tile × inferensi = menit + OOM.
        val maxTiles = 40
        var step = max(64, (win * 0.70f).toInt())
        val estTiles = (longSide - win).toFloat() / step + 1f
        if (estTiles > maxTiles) {
            step = max(64, ((longSide - win) / (maxTiles - 1f)).toInt().coerceAtMost(win - 64))
        }
        val out = mutableListOf<DetectedBubble>()
        var offset = 0
        var tileIdx = 0
        while (offset < longSide) {
            kotlinx.coroutines.ensureActive()
            val end = min(offset + win, longSide)
            val start = max(0, end - win)
            val crop = try {
                if (vertical) Bitmap.createBitmap(bitmap, 0, start, w, end - start)
                else Bitmap.createBitmap(bitmap, start, 0, end - start, h)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            if (crop != null) {
                try {
                    // Tile hampir polos (area kertas kosong) = tak ada bubble:
                    // lewati inferensi (penghemat terbesar di halaman manga).
                    if (!isTileBlank(crop)) {
                        val found = runOnnx(session, crop, TALL_CONF_THRESH).filter { it.classId == 0 }
                        for (b in found) {
                            val r = b.boundingBox
                            val shifted = if (vertical) {
                                RectF(r.left, r.top + start, r.right, r.bottom + start)
                            } else {
                                RectF(r.left + start, r.top, r.right + start, r.bottom)
                            }
                            val shiftedMask = b.mask
                            out.add(DetectedBubble(shifted, b.score, shiftedMask, b.classId))
                        }
                    }
                } finally {
                    runCatching { crop.recycle() }
                }
            }
            tileIdx++
            if (end >= longSide) {
                onProgress?.invoke(1f)
                break
            }
            offset += step
            onProgress?.invoke((end / longSide.toFloat()).coerceIn(0f, 1f))
            if (offset >= longSide) break
        }
        return nms(out, IOU_THRESH)
            .sortedByDescending { it.score }
            .take(TALL_MAX_DETECTIONS)
    }

    /**
     * True bila tile hampir polos (rentang kecerahan < 24 pada sampling
     * kasar): area kertas kosong tanpa garis/teks → inferensi dilewati.
     */
    private fun isTileBlank(crop: Bitmap): Boolean {
        return try {
            val w = crop.width
            val h = crop.height
            if (w <= 0 || h <= 0) return true
            val step = 16
            var minB = 255
            var maxB = 0
            // Buffer SEBARIS penuh (stride = w), lalu lompat tiap 16px agar
            // sampel tersebar merata (bukan blok kontinu di kiri).
            val row = IntArray(w)
            var y = 0
            while (y < h) {
                crop.getPixels(row, 0, w, 0, y, w, 1)
                var x = 0
                while (x < w) {
                    val p = row[x]
                    val b = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                    if (b < minB) minB = b
                    if (b > maxB) maxB = b
                    if (maxB - minB >= 24) return false
                    x += step
                }
                y += step
            }
            true
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Util
    // ------------------------------------------------------------------

    /** NMS berbasis IoU: simpan skor tertinggi, buang yang tumpang tindih. */
    private fun nms(list: List<DetectedBubble>, iouThresh: Float): List<DetectedBubble> {
        val out = mutableListOf<DetectedBubble>()
        for (b in list.sortedByDescending { it.score }) {
            if (out.none { iou(it.boundingBox, b.boundingBox) > iouThresh }) out.add(b)
            else runCatching { b.mask?.recycle() }
        }
        return out
    }

    private fun iou(a: RectF, b: RectF): Float =
        iou(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom)

    private fun iou(
        ax1: Float, ay1: Float, ax2: Float, ay2: Float,
        bx1: Float, by1: Float, bx2: Float, by2: Float
    ): Float {
        val ix = max(0f, min(ax2, bx2) - max(ax1, bx1))
        val iy = max(0f, min(ay2, by2) - max(ay1, by1))
        val inter = ix * iy
        if (inter <= 0f) return 0f
        val union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/** Urutan baca manga: baris atas→bawah, dalam baris kanan→kiri. */
fun readingOrder(bubbles: List<DetectedBubble>): List<DetectedBubble> {
    if (bubbles.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<DetectedBubble>>()
    for (b in bubbles.sortedBy { it.boundingBox.centerY() }) {
        val row = rows.find { r ->
            val h = min(r[0].boundingBox.height(), b.boundingBox.height())
            abs(r[0].boundingBox.centerY() - b.boundingBox.centerY()) < h * 0.6f
        }
        if (row != null) row.add(b) else rows.add(mutableListOf(b))
    }
    return rows.flatMap { it.sortedByDescending { it.boundingBox.centerX() } }
}
