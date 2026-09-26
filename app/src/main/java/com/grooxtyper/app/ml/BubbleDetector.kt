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
 * BUBBLE -> `models/bd.onnx` (ogkalu RT-DETR v2 int8 "middle", 3 kelas
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
        "ogkalu RT-DETR v2 int8 (middle, ~44MB) via ONNX Runtime, input 640x640",
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
 * - Model aktif: ogkalu RT-DETR v2 int8 "middle" (Apache-2.0,
 *   ogkalu/comic-text-and-bubble-detector `detector_int8.onnx`, ~44MB,
 *   3 kelas bubble/text_bubble/text_free, latih 640), input 640x640,
 *   2 output logits (1,300,3) + boxes (1,300,4) cxcywh ternormalisasi
 *   → box kelas bubble (mask=null, tanpa NMS karena head sudah one-to-one).
 *   Model di-download saat build di GitHub Actions, tidak di-commit ke repo
 *   (batas <50MB terpenuhi).
 * - Legacy: Kiuyha YOLO26s detect end-to-end (1,300,6); YOLO seg 1-class,
 *   output (1,37,8400) + protos (1,32,160,160) dengan mask ALPHA_8 per bubble.
 * Untuk kanvas jangkung (mis. 720x16000) gambar dipotong jadi tile persegi
 * ber-overlap 30%; setiap tile di-letterbox ke 640x640, dijalankan
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
     * Inferensi satu bitmap ukuran bebas: letterbox ke 640x640, decode
     * output YOLO, kembalikan deteksi dalam koordinat bitmap asli.
     * Mendukung dua varian:
     * - detect 2-class baru: 1 output (1,6,8400) atau (1,8400,6), tanpa mask.
     * - seg legacy: 2 output (1,37,8400) + (1,32,160,160) dengan mask.
     */
    private suspend fun runOnnx(session: OrtSession, src: Bitmap, conf: Float = CONF_THRESH): List<DetectedBubble> {
        if (src.width <= 0 || src.height <= 0) return emptyList()
        // Letterbox: skala seragam + padding abu (114) ala YOLO.
        val scale = min(
            INPUT_SIZE / src.width.toFloat(),
            INPUT_SIZE / src.height.toFloat()
        )
        val nw = max(1, (src.width * scale).toInt())
        val nh = max(1, (src.height * scale).toInt())
        val padX = (INPUT_SIZE - nw) / 2f
        val padY = (INPUT_SIZE - nh) / 2f

        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val rpx = IntArray(nw * nh)
        resized.getPixels(rpx, 0, nw, 0, 0, nw, nh)
        resized.recycle()

        val data = FloatArray(3 * INPUT_SIZE * INPUT_SIZE) { 114f / 255f }
        val plane = INPUT_SIZE * INPUT_SIZE
        for (y in 0 until nh) {
            val dy = (y + padY).toInt()
            val rowBase = y * nw
            val outBase = dy * INPUT_SIZE
            for (x in 0 until nw) {
                val dx = (x + padX).toInt()
                val p = rpx[rowBase + x]
                val o = outBase + dx
                data[o] = ((p shr 16) and 0xFF) / 255f
                data[plane + o] = ((p shr 8) and 0xFF) / 255f
                data[2 * plane + o] = (p and 0xFF) / 255f
            }
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
                            return@withLock decodeSeg(out0[0], out1[0], src.width, src.height, scale, padX, padY)
                        }
                    } catch (e: Exception) {
                        // Jatuh ke jalur detect di bawah.
                    }
                }
                // Jalur RT-DETR (ogkalu middle/int8): 2 output — logits
                // (1,300,3) + boxes (1,300,4). Dicek SEBELUM jalur generik
                // agar tidak salah decode sebagai YOLO klasik.
                if (res.size() >= 2) {
                    val rt = decodeRtDetr(res, src.width, src.height, scale, padX, padY, conf)
                    if (rt != null) {
                        lastOutputDesc = "rtdetr kept=${rt.size}"
                        return@withLock rt
                    }
                }
                // Jalur detect: 1 output (1,C,N) klasik, (1,N,C) transpos, atau
                // (1,N,6) end-to-end NMS-free (YOLO26: x1,y1,x2,y2,score,cls).
                val rawVal = res[0].value
                val mat: Array<FloatArray>? = extractBatchMatrix(rawVal)
                if (mat != null) {
                    lastOutputDesc = "out=${res.size()} mat=${mat.size}x${mat[0].size}"
                    if (mat.isNotEmpty() && mat[0].size == 6 && mat.size in 2..1000) {
                        decodeE2E(mat, src.width, src.height, scale, padX, padY, conf).also {
                            lastOutputDesc = "e2e rows=${mat.size} kept=${it.size}"
                        }
                    } else {
                        decodeDetect(mat, src.width, src.height, scale, padX, padY, conf).also {
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
     * [x1,y1,x2,y2,score,cls] dalam skala input letterbox (640).
     * Sudah deduplikasi oleh head (one-to-one matching) sehingga tanpa NMS:
     * cukup threshold skor + buang box mungil. Mask=null (box-only).
     */
    private fun decodeE2E(
        mat: Array<FloatArray>,
        origW: Int, origH: Int,
        scale: Float, padX: Float, padY: Float,
        conf: Float = CONF_THRESH
    ): List<DetectedBubble> {
        val out = ArrayList<DetectedBubble>(mat.size.coerceAtMost(300))
        for (row in mat) {
            if (row.size < 6) continue
            val score = row[4]
            if (score < conf) continue
            val cls = row[5].toInt()
            // Balik letterbox ke koordinat bitmap asli.
            val x1 = (row[0] - padX) / scale
            val y1 = (row[1] - padY) / scale
            val x2 = (row[2] - padX) / scale
            val y2 = (row[3] - padY) / scale
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
     * Decode output RT-DETR v2 (ogkalu middle/int8): logits (1,300,3) untuk
     * kelas [bubble, text_bubble, text_free] + boxes (1,300,4) cxcywh yang
     * dinormalisasi 0..1 terhadap input 640. Skor = sigmoid (focal loss),
     * tanpa NMS (end-to-end one-to-one). Hanya kelas 0 (bubble) yang dipakai,
     * konsisten dengan filter classId == 0 di jalur lain.
     * Mengembalikan null bila bentuk output tidak cocok (biar jatuh ke jalur lain).
     */
    private fun decodeRtDetr(
        res: OrtSession.Result,
        origW: Int, origH: Int,
        scale: Float, padX: Float, padY: Float,
        conf: Float = CONF_THRESH
    ): List<DetectedBubble>? {
        return try {
            fun batchOf(idx: Int): Array<FloatArray>? {
                val v = res[idx].value as? Array<*> ?: return null
                @Suppress("UNCHECKED_CAST")
                val m = (v as Array<*>)[0] as? Array<FloatArray> ?: return null
                if (m.isEmpty() || m[0].isEmpty()) return null
                return m
            }
            if (res.size() < 2) return null
            val m0 = batchOf(0) ?: return null
            val m1 = batchOf(1) ?: return null
            // Tetapkan peran berdasar bentuk (urutan output bisa beda):
            // inner 3 = logits, inner 4 = boxes.
            val (logits, boxes) = when {
                m0[0].size == 3 && m1[0].size == 4 -> m0 to m1
                m0[0].size == 4 && m1[0].size == 3 -> m1 to m0
                else -> return null
            }
            if (logits.size != 300 || boxes.size != 300) return null
            val out = ArrayList<DetectedBubble>(64)
            for (i in 0 until 300) {
                val l = logits[i]
                val b = boxes[i]
                var best = 0
                var bestS = Float.NEGATIVE_INFINITY
                for (c in 0..2) {
                    val s = 1f / (1f + kotlin.math.exp(-l[c]))
                    if (s > bestS) { bestS = s; best = c }
                }
                if (bestS < conf || best != 0) continue
                // cxcywh ternormalisasi → piksel letterbox → koordinat asli.
                val cx = (b[0] * INPUT_SIZE - padX) / scale
                val cy = (b[1] * INPUT_SIZE - padY) / scale
                val bw = b[2] * INPUT_SIZE / scale
                val bh = b[3] * INPUT_SIZE / scale
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
        scale: Float, padX: Float, padY: Float,
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
            val x1 = (cx - w / 2f - padX) / scale
            val y1 = (cy - h / 2f - padY) / scale
            val x2 = (cx + w / 2f - padX) / scale
            val y2 = (cy + h / 2f - padY) / scale
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
        scale: Float, padX: Float, padY: Float
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
            // Balik letterbox ke koordinat bitmap asli.
            val x1 = (cx - w / 2f - padX) / scale
            val y1 = (cy - h / 2f - padY) / scale
            val x2 = (cx + w / 2f - padX) / scale
            val y2 = (cy + h / 2f - padY) / scale
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
            val mask = buildMask(protos, d.coef, box, origW, origH, scale, padX, padY)
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
        origW: Int, origH: Int,
        scale: Float, padX: Float, padY: Float
    ): Bitmap? {
        return try {
            val pw = box.width().toInt().coerceIn(1, 2048)
            val ph = box.height().toInt().coerceIn(1, 2048)

            // Box dalam koordinat proto (160x160): box-asli -> 640 -> 160.
            val px1 = ((box.left * scale + padX) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(0, PROTO_SIZE - 1)
            val py1 = ((box.top * scale + padY) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(0, PROTO_SIZE - 1)
            val px2 = ((box.right * scale + padX) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(px1 + 1, PROTO_SIZE)
            val py2 = ((box.bottom * scale + padY) * PROTO_SIZE / INPUT_SIZE).toInt().coerceIn(py1 + 1, PROTO_SIZE)

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
     * pendek, min 512px, maks 1024px) ber-overlap 30%, inferensi per tile,
     * geser koordinat, lalu NMS global untuk buang duplikat di overlap.
     */
    private suspend fun detectTallOnnx(bitmap: Bitmap, session: OrtSession, onProgress: ((Float) -> Unit)? = null): List<DetectedBubble> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        val win = shortSide.coerceIn(512, 1024)
        val step = max(64, (win * 0.70f).toInt())
        val out = mutableListOf<DetectedBubble>()
        var offset = 0
        while (offset < longSide) {
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
                } finally {
                    runCatching { crop.recycle() }
                }
            }
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
