package com.grooxtyper.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dua opsi model bubble yang bisa dipilih user di dialog Bubble Detector.
 * Masing-masing opsi memetakan 1:1 ke file di `assets/models/`:
 * - KOHARU_YOLO26S_SEG -> `koharu/koharu-yolo26s-seg.onnx` (YOLO26s-seg, ganti best.pt)
 * - BEST1_PT            -> `best1.pt` (YOLOv11n-seg, tetap)
 *
 * Opsi KOHARU kini dieksekusi NYATA via ONNX Runtime (lihat [OnnxRuntimeEngine]):
 * letterbox 1024 → forward → filter skor/kelas 'balloon' → NMS → bbox + mask
 * segmentasi per bubble. Opsi BEST1_PT tetap heuristik putih-tersaturasi-rendah
 * (checkpoint .pt PyTorch tidak bisa dieksekusi langsung di Android).
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
    KOHARU_YOLO26S_SEG(
        "koharu yolo26s-seg • ONNX Runtime",
        "Inferensi nyata di perangkat — bbox + mask segmentasi",
        "models/koharu/koharu-yolo26s-seg.onnx"
    ),
    BEST1_PT(
        "best1.pt • YOLOv11n-seg",
        "Heuristik putih (fallback, .pt belum mobile)",
        "models/best1.pt"
    )
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float,
    /** Mask segmentasi per bubble dalam koordinat kanvas penuh (boleh null). */
    val mask: Bitmap? = null,
    /** Id kelas model ONNX (bila tersedia): 0=frame,1=dialogue_text,2=balloon,3=onomatopoeia. */
    val classId: Int = -1
)

/**
 * Detektor balon teks manga on-device.
 *
 * KOHARU_YOLO26S_SEG: inferensi ONNX Runtime sungguhan per tile persegi
 * (letterbox ke 1024, sesuai input model). Untuk kanvas jangkung
 * (mis. 720x16000) gambar dipotong jadi tile persegi 720px ber-overlap 15%
 * sehingga sisi pendek tidak hancur saat downscale; duplikat di area overlap
 * dibuang via NMS global.
 *
 * BEST1_PT: fallback heuristik (area putih jenuh-rendah ber-outline gelap),
 * dipertahankan apa adanya sebagai opsi kedua.
 */
class BubbleDetector {

    /** Ambang skor minimum deteksi ONNX (konservatif; bubble manga skornya tinggi). */
    private val onnxScoreThresh = 0.25f

    /**
     * Jalankan opsi terpilih. [appContext] dipakai untuk memuat session ONNX
     * (opsional — bila null atau runtime gagal, otomatis fallback heuristik).
     */
    suspend fun detect(
        bitmap: Bitmap,
        model: BubbleModel,
        appContext: Context? = null
    ): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
                val useOnnx = model == BubbleModel.KOHARU_YOLO26S_SEG &&
                    appContext != null && OnnxRuntimeEngine.isAvailable()
                if (useOnnx) {
                    val result = detectOnnx(bitmap, appContext!!)
                    if (result != null) return@withContext result
                }
                // Fallback heuristik (juga untuk BEST1_PT).
                val longSide = max(bitmap.width, bitmap.height)
                val shortSide = min(bitmap.width, bitmap.height).coerceAtLeast(1)
                val aspect = longSide / shortSide.toFloat()
                if (aspect >= 4f || longSide > 3000) {
                    return@withContext detectTallHeuristic(bitmap, model)
                }
                val (maxDim, thresh, cap) = rawParams(model)
                detectAtScaleHeuristic(bitmap, maxDim, thresh)
                    .sortedByDescending { it.score }
                    .take(cap)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /** Parameter mentah per opsi model (untuk jalur heuristik). */
    private fun rawParams(model: BubbleModel): Triple<Int, Int, Int> {
        return when (model) {
            BubbleModel.KOHARU_YOLO26S_SEG -> Triple(1024, 225, 90)
            BubbleModel.BEST1_PT -> Triple(1024, 220, 80)
        }
    }

    // ------------------------------------------------------------------
    // Jalur ONNX Runtime (inferensi nyata)
    // ------------------------------------------------------------------

    /**
     * Inferensi ONNX per tile persegi sepanjang sumbu panjang. Tile = sisi
     * pendek (min 512, maks 1024 agar tak melebihi input model), overlap 15%.
     * Koordinat output dipetakan kembali ke kanvas global, lalu NMS.
     */
    private fun detectOnnx(bitmap: Bitmap, context: Context): List<DetectedBubble>? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        val win = shortSide.coerceIn(256, OnnxRuntimeEngine.INPUT_SIZE)
        val step = max(64, (win * 0.85f).toInt())
        val inputSize = OnnxRuntimeEngine.INPUT_SIZE.toFloat()

        val all = mutableListOf<DetectedBubble>()
        val maskPool = ByteArray(256 * 256)
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
                    val result = OnnxRuntimeEngine.run(context, crop)
                    if (result != null) {
                        // Skala dari ruang model 1024 ke piksel crop.
                        val sx = crop.width / inputSize
                        val sy = crop.height / inputSize
                        for (i in result.detections.indices) {
                            val det = result.detections[i]
                            if (det.size < 6 + OnnxRuntimeEngine.NUM_MASK_COEF) continue
                            val score = det[4]
                            val cls = det[5].toInt()
                            if (score < onnxScoreThresh || cls != OnnxRuntimeEngine.CLASS_BALLOON) continue
                            val cx = det[0] * sx
                            val cy = det[1] * sy
                            val bw = det[2] * sx
                            val bh = det[3] * sy
                            if (bw < 8f || bh < 8f) continue
                            val box = if (vertical) {
                                RectF(cx - bw / 2f, cy - bh / 2f + start, cx + bw / 2f, cy + bh / 2f + start)
                            } else {
                                RectF(cx - bw / 2f + start, cy - bh / 2f, cx + bw / 2f + start, cy + bh / 2f)
                            }
                            // Bangun mask segmentasi instan (dipotong ke bbox).
                            java.util.Arrays.fill(maskPool, 0.toByte())
                            OnnxRuntimeEngine.buildInstanceMask(
                                result, i, det[0], det[1], det[2], det[3], maskPool
                            )
                            val mask = instanceMaskToBitmap(
                                maskPool, result.protoDim, box, start, vertical, sx, sy
                            )
                            all.add(DetectedBubble(box, score, mask, cls))
                        }
                    }
                } finally {
                    runCatching { crop.recycle() }
                }
            }
            if (end >= longSide) break
            offset += step
            if (offset >= longSide) break
        }
        return nms(all, iouThresh = 0.5f)
            .sortedByDescending { it.score }
            .take(150)
    }

    /**
     * Konversi mask instan (ruang proto 256, dipotong bbox 1024) menjadi
     * Bitmap alpha sebesar bbox kanvas global.
     */
    private fun instanceMaskToBitmap(
        maskBytes: ByteArray,
        protoDim: Int,
        globalBox: RectF,
        stripOffset: Int,
        vertical: Boolean,
        sx: Float,
        sy: Float
    ): Bitmap? {
        return try {
            val bw = (globalBox.width()).toInt().coerceAtLeast(1)
            val bh = (globalBox.height()).toInt().coerceAtLeast(1)
            val pixels = IntArray(bw * bh)
            val inputSize = OnnxRuntimeEngine.INPUT_SIZE.toFloat()
            val protoScale = protoDim / inputSize
            // Posisi bbox dalam ruang model (1024), globalBox sudah termasuk offset strip.
            val localLeft = if (vertical) globalBox.left else globalBox.left - stripOffset
            val localTop = if (vertical) globalBox.top - stripOffset else globalBox.top
            val boxLeftModel = localLeft / sx
            val boxTopModel = localTop / sy
            for (y in 0 until bh) {
                val modelY = boxTopModel + y / sy
                val py = (modelY * protoScale).toInt().coerceIn(0, protoDim - 1)
                val rowOff = y * bw
                val protoRow = py * protoDim
                for (x in 0 until bw) {
                    val modelX = boxLeftModel + x / sx
                    val px = (modelX * protoScale).toInt().coerceIn(0, protoDim - 1)
                    val a = maskBytes[protoRow + px].toInt() and 0xFF
                    if (a > 8) pixels[rowOff + x] = (a shl 24) or 0xFFFFFF
                }
            }
            Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** NMS berbasis IoU: simpan skor tertinggi, buang yang tumpang tindih. */
    private fun nms(list: List<DetectedBubble>, iouThresh: Float): List<DetectedBubble> {
        val out = mutableListOf<DetectedBubble>()
        for (b in list.sortedByDescending { it.score }) {
            if (out.none { iou(it.boundingBox, b.boundingBox) > iouThresh }) out.add(b)
            else runCatching { b.mask?.recycle() }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Jalur heuristik (fallback / BEST1_PT)
    // ------------------------------------------------------------------

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

    private fun iou(a: RectF, b: RectF): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        if (inter <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/** Engine YOLO ONNX (koharu-yolo26s-seg) kini aktif via [OnnxRuntimeEngine]. */
object YoloBubbleModel {
    const val DETECT_ASSET = "models/koharu/koharu-yolo26s-seg.onnx"
    const val SEG_ASSET = "models/best1.pt"

    /** True bila ONNX Runtime + model siap (diinisialisasi saat pemakaian pertama). */
    fun isAvailable(): Boolean = OnnxRuntimeEngine.isAvailable()
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
