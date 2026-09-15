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
 * Opsi model bubble yang bisa dipilih user di dialog Bubble Detector.
 * Saat ini tersedia satu opsi:
 * - BEST1_PT -> `best1.pt` (YOLOv11n-seg, fallback heuristik di perangkat)
 *
 * Checkpoint .pt PyTorch tidak bisa dieksekusi langsung di Android, sehingga
 * deteksi dijalankan via heuristik putih-tersaturasi-rendah ber-outline gelap.
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
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
    /** Id kelas model (bila tersedia): 0=frame,1=dialogue_text,2=balloon,3=onomatopoeia. */
    val classId: Int = -1
)

/**
 * Detektor balon teks manga on-device.
 *
 * Heuristik area putih jenuh-rendah ber-outline gelap. Untuk kanvas jangkung
 * (mis. 720x16000) gambar dipotong jadi jendela persegi (sisi = sisi pendek)
 * ber-overlap 15% sehingga sisi pendek tidak hancur saat downscale; duplikat
 * di area overlap dibuang via NMS global.
 */
class BubbleDetector {

    /**
     * Jalankan opsi terpilih. [appContext] diterima untuk kompatibilitas API
     * (saat ini tidak dipakai — jalur heuristik tidak butuh Context).
     */
    suspend fun detect(
        bitmap: Bitmap,
        model: BubbleModel,
        appContext: Context? = null
    ): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
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
            BubbleModel.BEST1_PT -> Triple(1024, 220, 80)
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
    // Jalur heuristik
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

/** Asset model bubble yang tersedia di `assets/models/`. */
object YoloBubbleModel {
    const val SEG_ASSET = "models/best1.pt"
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
