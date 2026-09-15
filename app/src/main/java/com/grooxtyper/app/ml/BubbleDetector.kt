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
 * BEST1_ONNX -> `models/best1.onnx` (YOLOv11n-seg, hasil konversi dari
 * checkpoint `best1.pt`). Model DIEKSEKUSI LANGSUNG di perangkat via
 * ONNX Runtime Mobile: 1 kelas ("balloon"), input 640x640, output deteksi
 * (1,37,8400) + prototipe mask (1,32,160,160).
 *
 * `best1.pt` tetap disertakan di assets sebagai referensi / arsip checkpoint
 * latih, tapi tidak dipakai saat runtime.
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
    BEST1_ONNX(
        "best1.onnx • YOLOv11n-seg (on-device)",
        "Inferensi nyata ONNX Runtime, input 640x640, kelas balloon",
        "models/best1.onnx"
    )
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float,
    /** Mask segmentasi per bubble dalam koordinat kanvas penuh (boleh null). */
    val mask: Bitmap? = null,
    /** Id kelas model (bila tersedia): 0 = balloon. */
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
 * Jalur utama: inferensi ONNX (YOLOv11n-seg) via ONNX Runtime Mobile.
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
        private const val NUM_CHANNELS = 4 + 1 + NUM_MASK_COEF // box + 1 kelas + coef = 37
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

    /**
     * Inferensi satu bitmap ukuran bebas: letterbox ke 640x640, decode
     * output YOLOv11n-seg, kembalikan deteksi dalam koordinat bitmap asli.
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
                val out0 = results[0].value as Array<Array<FloatArray>> // (1,37,8400)
                val out1 = results[1].value as Array<Array<Array<FloatArray>>> // (1,32,160,160)
                decodeSeg(out0[0], out1[0], src.width, src.height, scale, padX, padY)
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
     * Decode output YOLOv11n-seg: preds (37, 8400) + protos (32,160,160).
     * 37 kanal = 4 box (cx,cy,w,h relatif 640) + 1 skor kelas + 32 koefisien mask.
     * Mask diproses HANYA untuk deteksi terbaik per region (hemat memori/CPU):
     * protos x koefisien -> sigmoid -> crop ke box -> resize ke koordinat asli.
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
