package com.grooxtyper.app.ml

import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dua profil detektor yang bisa dipilih user di dialog Bubble Detector.
 */
enum class BubbleModel(val displayName: String, val desc: String) {
    FAST("Cepat", "Sekali jalan, halaman bersih"),
    ACCURATE("Teliti", "Multi-skala, halaman kompleks")
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float
)

/**
 * Detektor balon teks manga yang berjalan murni on-device tanpa AI:
 * region putih tertutup (bukan latar) yang bentuknya oval/membulat
 * dianggap bubble. Cukup akurat untuk halaman manga tipikal.
 *
 * CATATAN YOLO: dua file `.pt` di `assets/models/` (YOLOv12n-deteksi dan
 * YOLOv11n-seg, lihat `Model/README.md`) adalah checkpoint Python
 * Ultralytics sehingga belum bisa dieksekusi di Android. Bila nanti sudah
 * di-export ke TorchScript/ONNX, implementasikan [YoloBubbleModel] dan
 * daftarkan sebagai opsi ketiga di dialog.
 */
class BubbleDetector {

    suspend fun detect(bitmap: Bitmap, model: BubbleModel): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
                val out = mutableListOf<DetectedBubble>()
                val scales = when (model) {
                    BubbleModel.FAST -> listOf(768)
                    BubbleModel.ACCURATE -> listOf(768, 1152)
                }
                val thresh = if (model == BubbleModel.FAST) 200 else 190
                for (s in scales) out += detectAtScale(bitmap, s, thresh)
                merge(out, iouThresh = 0.5f)
                    .sortedByDescending { it.score }
                    .take(if (model == BubbleModel.FAST) 40 else 60)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun detectAtScale(src: Bitmap, maxDim: Int, whiteThresh: Int): List<DetectedBubble> {
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
                white[i] = (0.299 * r + 0.587 * g + 0.114 * b) >= whiteThresh
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
                        // Skor: makin dekat ke isi ellipse ideal (π/4) makin tinggi.
                        val score = (1f - abs(fill - 0.785f) * 1.5f).coerceIn(0.05f, 1f)
                        out.add(
                            DetectedBubble(
                                RectF(minX / s, minY / s, (maxX + 1) / s, (maxY + 1) / s),
                                score
                            )
                        )
                    }
                }
                cur++
            }
            return out
        } finally {
            small.recycle()
        }
    }

    private fun merge(list: List<DetectedBubble>, iouThresh: Float): List<DetectedBubble> {
        val out = mutableListOf<DetectedBubble>()
        for (b in list.sortedByDescending { it.score }) {
            if (out.none { iou(it.boundingBox, b.boundingBox) > iouThresh }) out.add(b)
        }
        return out
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

/**
 * STUB engine YOLO masa depan. Aktifkan bila `assets/models/` sudah berisi
 * hasil export mobile (TorchScript/ONNX) + runtime-nya ditambahkan:
 * load model → letterbox → forward → NMS (decode DFL untuk v12,
 * rakit mask untuk v11-seg) → List<DetectedBubble>.
 */
object YoloBubbleModel {
    const val DETECT_ASSET = "models/yolo12n_balloon.pt"
    const val SEG_ASSET = "models/yolo11n_seg_balloon.pt"

    fun isAvailable(): Boolean = false // TODO: true bila runtime + export mobile tersedia
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
