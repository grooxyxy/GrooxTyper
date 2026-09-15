package com.grooxtyper.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Dua variasi mask baru sesuai permintaan user:
 * - MASK_KOTAK: persegi panjang solid (bounding box) dengan sedikit padding
 * - MASK_BENTUK_TEKS: mengikuti bentuk huruf/glyph (tight text shape)
 * Tidak ada download tambahan — semua proses lokal.
 */
enum class MLMaskType(val displayName: String) {
    MASK_KOTAK("Mask Kotak"),
    MASK_BENTUK_TEKS("Mask Bentuk Teks")
}

enum class MLScript(val displayName: String) {
    LATIN("Latin"),
    CHINESE("China"),
    JAPANESE("Jepang"),
    KOREAN("Korea")
}

data class DetectedTextRegion(
    val text: String,
    val boundingBox: Rect,
    val cornerPoints: Array<android.graphics.Point>?,
    val script: MLScript = MLScript.LATIN
)

class MLTextDetector {
    private val clients: Map<MLScript, TextRecognizer> = mapOf(
        MLScript.LATIN to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        MLScript.CHINESE to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        MLScript.JAPANESE to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        MLScript.KOREAN to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    )

    /** Deteksi teks dengan recognizer per script terpilih, lalu gabung + hapus duplikat. */
    suspend fun detectTextRegions(
        bitmap: Bitmap,
        scripts: Set<MLScript> = setOf(MLScript.LATIN)
    ): List<DetectedTextRegion> {
        if (scripts.isEmpty()) return emptyList()
        // Kanvas jangkung (mis. 720x16000): ML Kit dibatasi ukuran input,
        // jadi potong jadi strip horizontal ber-overlap lalu gabung.
        if (bitmap.height > 2048 && bitmap.height > bitmap.width * 2) {
            return detectTiled(bitmap, scripts)
        }
        val all = mutableListOf<DetectedTextRegion>()
        for (script in scripts) {
            val client = clients[script] ?: continue
            try {
                all += detectWith(client, bitmap, script)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return dedupe(all)
    }

    /**
     * Deteksi per strip untuk gambar jangkung (mis. 720x16000):
     * strip 1800px dengan overlap 200px agar baris di sambungan tidak
     * terpotong, koordinat di-offset ke global lalu dedupe.
     */
    private suspend fun detectTiled(
        bitmap: Bitmap,
        scripts: Set<MLScript>
    ): List<DetectedTextRegion> {
        val w = bitmap.width
        val h = bitmap.height
        val stripH = 1800
        val overlap = 200
        val step = stripH - overlap
        val all = mutableListOf<DetectedTextRegion>()
        var top = 0
        while (top < h) {
            val bottom = min(h, top + stripH)
            val curTop = max(0, bottom - stripH).let { if (bottom >= h) it else top }
            val curH = bottom - curTop
            if (curH <= 0) break
            val crop = try {
                Bitmap.createBitmap(bitmap, 0, curTop, w, curH)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            if (crop != null) {
                try {
                    for (script in scripts) {
                        val client = clients[script] ?: continue
                        try {
                            val found = detectWith(client, crop, script)
                            for (r in found) {
                                val b = r.boundingBox
                                val shifted = Rect(
                                    b.left, b.top + curTop, b.right, b.bottom + curTop
                                )
                                val shiftedCorners = r.cornerPoints?.map { p ->
                                    android.graphics.Point(p.x, p.y + curTop)
                                }?.toTypedArray()
                                all.add(r.copy(boundingBox = shifted, cornerPoints = shiftedCorners))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } finally {
                    runCatching { crop.recycle() }
                }
            }
            if (bottom >= h) break
            top += step
        }
        return dedupe(all)
    }

    private suspend fun detectWith(
        client: TextRecognizer,
        bitmap: Bitmap,
        script: MLScript
    ): List<DetectedTextRegion> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        client.process(image)
            .addOnSuccessListener { visionText ->
                val regions = mutableListOf<DetectedTextRegion>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
                            regions.add(
                                DetectedTextRegion(
                                    text = line.text,
                                    boundingBox = box,
                                    cornerPoints = line.cornerPoints,
                                    script = script
                                )
                            )
                        }
                    }
                }
                continuation.resume(regions)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }

    /** Buang prediksi ganda antar-script (IoU tinggi → simpan teks terpanjang). */
    private fun dedupe(regions: List<DetectedTextRegion>): List<DetectedTextRegion> {
        val out = mutableListOf<DetectedTextRegion>()
        for (r in regions.sortedByDescending { it.text.length }) {
            if (out.none { iou(it.boundingBox, r.boundingBox) > 0.6f }) out.add(r)
        }
        return out
    }

    private fun iou(a: Rect, b: Rect): Float {
        val ix = max(0, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        if (inter <= 0) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0) 0f else inter.toFloat() / union
    }

    /**
     * Estimasi warna teks asli di dalam [box]: rata-rata piksel yang lebih
     * gelap dari rata-rata (diasumsikan tinta), agar TextBox pengganti
     * langsung mirip warna aslinya. Fallback bila tak ada piksel valid.
     */
    fun estimateRegionTextColor(source: Bitmap, box: Rect, fallback: Int): Int {
        return try {
            val left = box.left.coerceIn(0, source.width - 1)
            val top = box.top.coerceIn(0, source.height - 1)
            val right = box.right.coerceIn(left + 1, source.width)
            val bottom = box.bottom.coerceIn(top + 1, source.height)
            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return fallback
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, left, top, w, h)
            var sumLum = 0L
            var count = 0
            for (p in pixels) {
                if ((p ushr 24) < 16) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sumLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                count++
            }
            if (count == 0) return fallback
            val avg = sumLum / count
            var sr = 0L
            var sg = 0L
            var sb = 0L
            var sc = 0L
            for (p in pixels) {
                if ((p ushr 24) < 16) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                if (lum < avg) {
                    sr += r
                    sg += g
                    sb += b
                    sc++
                }
            }
            if (sc == 0L) return fallback
            Color.rgb((sr / sc).toInt(), (sg / sc).toInt(), (sb / sc).toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            fallback
        }
    }

    fun generateMaskBitmap(
        canvasWidth: Int,
        canvasHeight: Int,
        regions: List<DetectedTextRegion>,
        sourceBitmap: Bitmap,
        maskType: MLMaskType
    ): Bitmap {
        val maskBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        for (region in regions) {
            when (maskType) {
                MLMaskType.MASK_KOTAK -> {
                    // Mask Kotak: persegi panjang solid dengan padding kecil agar
                    // inpaint menutup tepi huruf sepenuhnya (variasi kotak).
                    val box = region.boundingBox
                    // Padding 2-4px tergantung ukuran box, dijepit ke kanvas.
                    val padX = (box.width() * 0.04f).coerceIn(2f, 6f)
                    val padY = (box.height() * 0.04f).coerceIn(2f, 6f)
                    val l = (box.left - padX).coerceIn(0f, canvasWidth.toFloat())
                    val t = (box.top - padY).coerceIn(0f, canvasHeight.toFloat())
                    val r = (box.right + padX).coerceIn(0f, canvasWidth.toFloat())
                    val b = (box.bottom + padY).coerceIn(0f, canvasHeight.toFloat())
                    canvas.drawRect(l, t, r, b, fillPaint)
                }
                MLMaskType.MASK_BENTUK_TEKS -> {
                    // Mask Bentuk Teks: mengikuti bentuk huruf, bukan kotak penuh.
                    // Strategi:
                    // 1) Jika cornerPoints tersedia (4 titik dari ML Kit), buat Path polygon sebagai clip.
                    // 2) Di dalam bounding box, gunakan luminance threshold untuk hanya menutupi stroke teks.
                    // 3) Fallback tetap Otsu tight bila tidak ada cornerPoints.
                    val box = region.boundingBox
                    val left = maxOf(0, box.left)
                    val top = maxOf(0, box.top)
                    val right = minOf(sourceBitmap.width, box.right)
                    val bottom = minOf(sourceBitmap.height, box.bottom)
                    val boxW = right - left
                    val boxH = bottom - top
                    if (boxW <= 0 || boxH <= 0) continue

                    val hasPolygon = region.cornerPoints != null && region.cornerPoints.size >= 4
                    if (hasPolygon) {
                        // Gunakan polygon cornerPoints + threshold untuk tight shape.
                        val pts = region.cornerPoints!!
                        // Build path polygon di koordinat kanvas
                        val polyPath = Path().apply {
                            moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                            for (i in 1 until pts.size) lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
                            close()
                        }
                        // Ambil pixel di dalam rect untuk threshold, lalu mask hanya di dalam polygon
                        val pixels = IntArray(boxW * boxH)
                        sourceBitmap.getPixels(pixels, 0, boxW, left, top, boxW, boxH)
                        var sumLum = 0L
                        for (p in pixels) {
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            sumLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                        }
                        val avgLum = if (pixels.isNotEmpty()) sumLum / pixels.size else 128L
                        val thresh = (avgLum * 0.92).toInt() // sedikit lebih ketat untuk bentuk teks
                        val maskPixels = IntArray(boxW * boxH)
                        for (i in pixels.indices) {
                            val p = pixels[i]
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            // Hanya piksel gelap = teks → putih di mask, lainnya transparan
                            maskPixels[i] = if (lum < thresh) -1 else 0
                        }
                        val tmp = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
                        tmp.setPixels(maskPixels, 0, boxW, 0, 0, boxW, boxH)
                        // Clip ke polygon agar tepi mask mengikuti rotasi/bentuk teks
                        val save = canvas.save()
                        canvas.clipPath(polyPath)
                        canvas.drawBitmap(tmp, left.toFloat(), top.toFloat(), null)
                        canvas.restoreToCount(save)
                        tmp.recycle()
                    } else {
                        // Fallback: Otsu tight tanpa polygon (tetap bentuk teks via threshold)
                        val pixels = IntArray(boxW * boxH)
                        sourceBitmap.getPixels(pixels, 0, boxW, left, top, boxW, boxH)
                        var sumLum = 0L
                        for (p in pixels) {
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            sumLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                        }
                        val avgLum = if (pixels.isNotEmpty()) sumLum / pixels.size else 128L
                        val thresh = (avgLum * 0.95).toInt()
                        val maskPixels = IntArray(boxW * boxH)
                        for (i in pixels.indices) {
                            val p = pixels[i]
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            maskPixels[i] = if (lum < thresh) -1 else 0
                        }
                        val tmp = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
                        tmp.setPixels(maskPixels, 0, boxW, 0, 0, boxW, boxH)
                        canvas.drawBitmap(tmp, left.toFloat(), top.toFloat(), null)
                        tmp.recycle()
                    }
                }
            }
        }
        return maskBitmap
    }
}
