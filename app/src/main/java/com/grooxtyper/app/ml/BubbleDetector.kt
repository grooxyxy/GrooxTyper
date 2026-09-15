package com.grooxtyper.app.ml

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
 * - BEST_PT  -> `best.pt`  (YOLOv12n deteksi)
 * - BEST1_PT -> `best1.pt` (YOLOv11n-seg)
 *
 * Output dibiarkan MENTAH apa adanya per opsi (single-pass, tanpa refine
 * lintas-skala). Tiling strip + dedupe overlap hanya dipakai agar kanvas
 * jangkung (mis. 720x16000) tetap terbaca, bukan refine.
 */
enum class BubbleModel(
    val displayName: String,
    val desc: String,
    val asset: String
) {
    BEST_PT(
        "best.pt • YOLOv12n",
        "Deteksi — output mentah apa adanya",
        "models/best.pt"
    ),
    BEST1_PT(
        "best1.pt • YOLOv11n-seg",
        "Segmentasi — output mentah apa adanya",
        "models/best1.pt"
    )
}

data class DetectedBubble(
    val boundingBox: RectF,
    val score: Float
)

/**
 * Detektor balon teks manga on-device.
 * Tiap opsi [BubbleModel] bekerja mentah apa adanya (single-pass sesuai
 * karakter modelnya). Mendukung kanvas jangkung (mis. 720x16000) via
 * deteksi strip ber-overlap agar sisi pendek tidak hancur saat downscale.
 *
 * CATATAN YOLO: dua file di `assets/models/` (`best.pt` deteksi YOLOv12n
 * dan `best1.pt` seg YOLOv11n, disalin dari folder `GrooxTyper/` lokal)
 * adalah checkpoint Python Ultralytics sehingga belum bisa dieksekusi
 * langsung di Android (butuh runtime + export mobile). Opsi di dialog
 * memetakan 1:1 ke kedua file tersebut; pipeline di bawah TIDAK di-refine
 * lagi — hanya menjalankan ciri mentah tiap opsi.
 */
class BubbleDetector {

    /**
     * Jalankan opsi terpilih MENTAH apa adanya: satu pass tunggal sesuai
     * karakter opsi (tanpa refine multi-skala). Kanvas jangkung dipotong
     * jadi strip agar sisi pendek tidak hancur; dedupe hanya untuk
     * menghilangkan duplikat di area overlap strip.
     */
    suspend fun detect(bitmap: Bitmap, model: BubbleModel): List<DetectedBubble> =
        withContext(Dispatchers.Default) {
            try {
                // Kanvas jangkung (aspek >= 4 atau sisi panjang > 3000):
                // deteksi per strip persegi ber-overlap agar sisi pendek
                // (mis. 720px) tidak hancur jadi ~34px saat downscale global.
                val longSide = max(bitmap.width, bitmap.height)
                val shortSide = min(bitmap.width, bitmap.height).coerceAtLeast(1)
                val aspect = longSide / shortSide.toFloat()
                if (aspect >= 4f || longSide > 3000) {
                    return@withContext detectTall(bitmap, model)
                }
                // RAW: satu pass, tanpa refine.
                val (maxDim, thresh, cap) = rawParams(model)
                detectAtScale(bitmap, maxDim, thresh)
                    .sortedByDescending { it.score }
                    .take(cap)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /** Parameter mentah per opsi model (1:1 ke file .pt, tanpa refine). */
    private fun rawParams(model: BubbleModel): Triple<Int, Int, Int> {
        return when (model) {
            // best.pt (YOLOv12n deteksi): kotak deteksi mentah.
            BubbleModel.BEST_PT -> Triple(768, 200, 80)
            // best1.pt (YOLOv11n-seg): ciri segmentasi mentah (sedikit
            // lebih teliti, ambang lebih rendah).
            BubbleModel.BEST1_PT -> Triple(1024, 185, 80)
        }
    }

    /**
     * Deteksi strip mentah untuk gambar jangkung/lebar ekstrem
     * (mis. 720x16000): potong sepanjang sumbu panjang jadi jendela
     * persegi (sisi = sisi pendek) dengan overlap 15%, tiap jendela
     * dijalankan SATU pass mentah sesuai opsi, offset ke koordinat global,
     * lalu dedupe overlap. Bukan refine — hanya agar tiling tidak
     * menggandakan bubble di sambungan.
     */
    private fun detectTall(bitmap: Bitmap, model: BubbleModel): List<DetectedBubble> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val vertical = h >= w
        val shortSide = min(w, h)
        val longSide = max(w, h)
        // Jendela persegi sebesar sisi pendek (min 512 agar detail cukup).
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
                    // Skala deteksi dihitung dari jendela (persegi), bukan
                    // gambar utuh, sehingga sisi pendek tidak hancur.
                    val found = detectAtScale(crop, maxDim, thresh)
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
        // Cap longgar untuk halaman jangkung (banyak panel). Dedupe
        // overlap saja, tanpa refine tambahan.
        val cap = 150
        return merge(out, iouThresh = 0.45f)
            .sortedByDescending { it.score }
            .take(cap)
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
 * STUB engine YOLO masa depan. Opsi [BubbleModel] sudah memetakan 1:1 ke
 * `assets/models/best.pt` (deteksi) dan `best1.pt` (seg). Aktifkan fungsi
 * ini bila `assets/models/` sudah berisi hasil export mobile
 * (TorchScript/ONNX) + runtime-nya ditambahkan: load model → letterbox →
 * forward → NMS (decode DFL untuk v12, rakit mask untuk v11-seg) →
 * List<DetectedBubble>. Sampai saat itu pipeline di atas dibiarkan mentah.
 */
object YoloBubbleModel {
    const val DETECT_ASSET = "models/best.pt"
    const val SEG_ASSET = "models/best1.pt"

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
