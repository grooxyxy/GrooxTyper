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
 * BEST1_ONNX -> `models/best1.onnx` (YOLO detect, 2 kelas balloon/other,
 * input 640x640, single output (1,6,8400)). Model DIEKSEKUSI LANGSUNG di
 * perangkat via ONNX Runtime Mobile.
 *
 * Kompatibel mundur: bila ONNX mengembalikan 2 output (det + protos seg
 * legacy 37ch), dipakai jalur seg + mask. Bila 1 output (detect), dipakai
 * jalur detect tanpa mask (mask=null).
 *
 * `best1.pt` (checkpoint seg lama) tetap disertakan sebagai arsip bila ada,
 * tapi tidak dipakai saat runtime.
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
    BEST1_ONNX(
        "best1.onnx • YOLO detect balloon/other (on-device)",
        "Inferensi nyata ONNX Runtime, input 640x640, kelas balloon + other",
        "models/best1.onnx"
    )
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float,
    /** Mask segmentasi per bubble dalam koordinat kanvas penuh (boleh null; null untuk model detect). */
    val mask: Bitmap? = null,
    /** Id kelas model: 0 = balloon, 1 = other (model detect baru). -1 = tak diketahui/heuristik. */
    val classId: Int = -1
)

/** Asset model bubble yang tersedia di `assets/models/`. */
object YoloBubbleModel {
    const val SEG_ASSET = "models/best1.onnx"
    const val PT_REFERENCE_ASSET = "models/best1.pt"
}

/**
 * Detektor balon teks manga on-device.
 *
 * Jalur utama: inferensi ONNX via ONNX Runtime Mobile.
 * - Model baru: YOLO detect 2-class (balloon/other), input 640x640,
 *   output tunggal (1,6,8400) = 4 box + 2 skor kelas, tanpa mask.
 * - Legacy: YOLOv11n-seg 1-class, output (1,37,8400) + protos (1,32,160,160)
 *   dengan mask ALPHA_8 per bubble.
 * Untuk kanvas jangkung (mis. 720x16000) gambar dipotong jadi tile persegi
 * 720px ber-overlap 15%; setiap tile di-letterbox ke 640x640, dijalankan
 * lewat model, lalu duplikat di sambungan tile dibuang via NMS global.
 *
 * Bila session ONNX gagal dibuat (asset hilang / runtime tidak tersedia),
 * otomatis fallback ke heuristik putih-tersaturasi-rendah ber-outline gelap
 * agar fitur tetap hidup.
 */
class BubbleDetector {

    companion object {
        private const val INPUT_SIZE = 640
        private const val NUM_MASK_COEF = 32
        private const val PROTO_SIZE = 160
        // Legacy seg: box(4) + 1 kelas + 32 koef = 37 kanal.
        private const val NUM_CHANNELS_SEG = 4 + 1 + NUM_MASK_COEF
        private const val CONF_THRESH = 0.25f
        private const val IOU_THRESH = 0.45f
        private const val MAX_DETECTIONS = 150
    }

    @Volatile
    private var ortSession: OrtSession? = null
    private val sessionMutex = Mutex()
    private val inferMutex = Mutex()

    /**
     * Jalankan opsi terpilih. [appContext] WAJIB diisi agar asset ONNX bisa
     * dimuat; bila null atau session gagal dibuat, dipakai jalur heuristik.
     */
    suspend fun detect(
        bitmap: Bitmap,
        model: BubbleModel,
        appContext: Context? = null
    ): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
                val session = appContext?.let { ensureSession(it, model.asset) }
                if (session == null) {
                    return@withContext detectHeuristic(bitmap, model)
                }
                val longSide = max(bitmap.width, bitmap.height)
                val shortSide = min(bitmap.width, bitmap.height).coerceAtLeast(1)
                val aspect = longSide / shortSide.toFloat()
                if (aspect >= 4f || longSide > 3000) {
                    return@withContext detectTallOnnx(bitmap, session)
                }
                val dets = runOnnx(session, bitmap)
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
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(bytes, opts).also { ortSession = it }
            } catch (e: Exception) {
                e.printStackTrace()
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
    private suspend fun runOnnx(session: OrtSession, src: Bitmap): List<DetectedBubble> {
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
                            return@withLock decodeSeg(out0[0], out1[0], src.width, src.height, scale, padX, padY)
                        }
                    } catch (e: Exception) {
                        // Jatuh ke jalur detect di bawah.
                    }
                }
                // Jalur detect: 1 output (1,C,N) atau (1,N,C), C = 4 + numClasses.
                val rawVal = res[0].value
                val mat: Array<FloatArray>? = extractBatchMatrix(rawVal)
                if (mat != null) {
                    decodeDetect(mat, src.width, src.height, scale, padX, padY)
                } else {
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
     * Decode output YOLO detect: matriks (C,N) atau (N,C) dengan
     * C = 4 box (cx,cy,w,h relatif 640) + numClasses skor.
     * Model baru: C=6 (balloon=0, other=1). Mask=null (box-only).
     */
    private fun decodeDetect(
        mat: Array<FloatArray>,
        origW: Int, origH: Int,
        scale: Float, padX: Float, padY: Float
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
            if (bestScore < CONF_THRESH) continue
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

        // NMS global (abaikan kelas agar duplikat balloon/other menyatu).
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
     * pendek, min 256px, maks 1024px) ber-overlap 15%, inferensi per tile,
     * geser koordinat, lalu NMS global untuk buang duplikat di overlap.
     */
    private suspend fun detectTallOnnx(bitmap: Bitmap, session: OrtSession): List<DetectedBubble> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        val win = shortSide.coerceIn(256, 1024)
        val step = max(64, (win * 0.85f).toInt())
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
                    val found = runOnnx(session, crop)
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
            if (end >= longSide) break
            offset += step
            if (offset >= longSide) break
        }
        return nms(out, IOU_THRESH)
            .sortedByDescending { it.score }
            .take(MAX_DETECTIONS)
    }

    // ------------------------------------------------------------------
    // Jalur heuristik (fallback bila ONNX tidak tersedia)
    // ------------------------------------------------------------------

    private fun detectHeuristic(bitmap: Bitmap, model: BubbleModel): List<DetectedBubble> {
        val longSide = max(bitmap.width, bitmap.height)
        val shortSide = min(bitmap.width, bitmap.height).coerceAtLeast(1)
        val aspect = longSide / shortSide.toFloat()
        if (aspect >= 4f || longSide > 3000) {
            return detectTallHeuristic(bitmap, model)
        }
        val (maxDim, thresh, cap) = rawParams(model)
        return detectAtScaleHeuristic(bitmap, maxDim, thresh)
            .sortedByDescending { it.score }
            .take(cap)
    }

    /** Parameter mentah per opsi model (untuk jalur heuristik). */
    private fun rawParams(model: BubbleModel): Triple<Int, Int, Int> {
        return when (model) {
            BubbleModel.BEST1_ONNX -> Triple(1024, 220, 80)
        }
    }

    /**
     * Deteksi strip mentah untuk gambar jangkung/lebar ekstrem
     * (mis. 720x16000): potong sepanjang sumbu panjang jadi jendela
     * persegi (sisi = sisi pendek) dengan overlap 15%.
     */
    private fun detectTallHeuristic(bitmap: Bitmap, model: BubbleModel): List<DetectedBubble> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        val win = shortSide.coerceAtLeast(256)
        val step = max(64, (win * 0.85f).toInt())
        val (maxDim, thresh, _) = rawParams(model)
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
                    val found = detectAtScaleHeuristic(crop, maxDim, thresh)
                    for (b in found) {
                        val r = b.boundingBox
                        val shifted = if (vertical) {
                            RectF(r.left, r.top + start, r.right, r.bottom + start)
                        } else {
                            RectF(r.left + start, r.top, r.right + start, r.bottom)
                        }
                        out.add(DetectedBubble(shifted, b.score))
                    }
                } finally {
                    runCatching { crop.recycle() }
                }
            }
            if (end >= longSide) break
            offset += step
            if (offset >= longSide) break
        }
        return nms(out, iouThresh = 0.45f)
            .sortedByDescending { it.score }
            .take(150)
    }

    private fun detectAtScaleHeuristic(src: Bitmap, maxDim: Int, whiteThresh: Int): List<DetectedBubble> {
        if (src.width <= 0 || src.height <= 0) return emptyList()
        val s = min(1f, maxDim / max(src.width, src.height).toFloat())
        val w = max(32, (src.width * s).toInt())
        val h = max(32, (src.height * s).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            val white = BooleanArray(w * h)
            for (i in px.indices) {
                val p = px[i]
                if ((p ushr 24) < 16) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val sat = max(max(r, g), b) - min(min(r, g), b)
                white[i] = (0.299 * r + 0.587 * g + 0.114 * b) >= whiteThresh && sat <= 40
            }

            val label = IntArray(w * h) { -1 }
            val stack = IntArray(w * h)
            val out = mutableListOf<DetectedBubble>()
            var cur = 0
            for (i in white.indices) {
                if (!white[i] || label[i] != -1) continue
                var sp = 0
                stack[sp++] = i
                label[i] = cur
                var minX = w
                var minY = h
                var maxX = -1
                var maxY = -1
                var area = 0
                var touchesBorder = false
                while (sp > 0) {
                    val p = stack[--sp]
                    val x = p % w
                    val y = p / w
                    area++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesBorder = true
                    if (x > 0) {
                        val n = p - 1
                        if (white[n] && label[n] == -1) {
                            label[n] = cur
                            stack[sp++] = n
                        }
                    }
                    if (x < w - 1) {
                        val n = p + 1
                        if (white[n] && label[n] == -1) {
                            label[n] = cur
                            stack[sp++] = n
                        }
                    }
                    if (y > 0) {
                        val n = p - w
                        if (white[n] && label[n] == -1) {
                            label[n] = cur
                            stack[sp++] = n
                        }
                    }
                    if (y < h - 1) {
                        val n = p + w
                        if (white[n] && label[n] == -1) {
                            label[n] = cur
                            stack[sp++] = n
                        }
                    }
                }
                if (!touchesBorder) {
                    val bw = maxX - minX + 1
                    val bh = maxY - minY + 1
                    val areaFrac = area.toFloat() / (w * h)
                    val aspect = bw.toFloat() / bh
                    val fill = area.toFloat() / (bw * bh)
                    if (areaFrac in 0.0015f..0.35f &&
                        aspect in 0.35f..3.0f &&
                        fill in 0.45f..0.97f &&
                        min(bw, bh) >= 20
                    ) {
                        // Filter ala kelas 'balloon': area putih dengan OUTLINE
                        // GELAP mengelilingi DAN berisi teks gelap.
                        val ring = max(2, min(bw, bh) / 12)
                        var ringTotal = 0
                        var ringDark = 0
                        var innerTotal = 0
                        var innerDark = 0
                        for (yy in minY..maxY) {
                            val rowOff = yy * w
                            val nearY = yy - minY < ring || maxY - yy < ring
                            for (xx in minX..maxX) {
                                val p = px[rowOff + xx]
                                val r = (p shr 16) and 0xFF
                                val g = (p shr 8) and 0xFF
                                val b = p and 0xFF
                                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                                if (nearY || xx - minX < ring || maxX - xx < ring) {
                                    ringTotal++
                                    if (luma < 110) ringDark++
                                } else {
                                    innerTotal++
                                    if (luma < 120) innerDark++
                                }
                            }
                        }
                        val borderDarkFrac = ringDark / max(1, ringTotal).toFloat()
                        val innerDarkFrac = innerDark / max(1, innerTotal).toFloat()
                        if (borderDarkFrac >= 0.22f && innerDarkFrac in 0.003f..0.45f) {
                            val fillScore = (1f - abs(fill - 0.785f) * 1.5f).coerceIn(0.05f, 1f)
                            val score = (fillScore * 0.6f + borderDarkFrac * 0.4f).coerceIn(0.05f, 1f)
                            out.add(
                                DetectedBubble(
                                    RectF(minX / s, minY / s, (maxX + 1) / s, (maxY + 1) / s),
                                    score
                                )
                            )
                        }
                    }
                }
                cur++
            }
            return out
        } finally {
            small.recycle()
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
