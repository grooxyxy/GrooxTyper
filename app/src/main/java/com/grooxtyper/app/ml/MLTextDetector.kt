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

    /**
     * Buat bitmap mask kanvas penuh untuk daftar region teks.
     *
     * MASK_BENTUK_TEKS ditulis ulang dari nol: alih-alih ambang rata-rata
     * (yang rapuh pada latar gelap/teks terang), kini memakai Otsu biner
     * sesungguhnya per region + deteksi polaritas otomatis (teks gelap ATAU
     * terang) + dilatasi 1px agar anti-alias tepi stroke ikut tertutup.
     * Bila cornerPoints ML Kit tersedia, hasil di-clip ke polygon teks miring.
     */
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
                    val padX = (box.width() * 0.04f).coerceIn(2f, 6f)
                    val padY = (box.height() * 0.04f).coerceIn(2f, 6f)
                    val l = (box.left - padX).coerceIn(0f, canvasWidth.toFloat())
                    val t = (box.top - padY).coerceIn(0f, canvasHeight.toFloat())
                    val r = (box.right + padX).coerceIn(0f, canvasWidth.toFloat())
                    val b = (box.bottom + padY).coerceIn(0f, canvasHeight.toFloat())
                    canvas.drawRect(l, t, r, b, fillPaint)
                }
                MLMaskType.MASK_BENTUK_TEKS -> {
                    val box = region.boundingBox
                    // Perluas box sedikit agar tepi stroke (anti-alias) ikut.
                    val grow = maxOf(2, box.height() / 12)
                    val left = maxOf(0, box.left - grow)
                    val top = maxOf(0, box.top - grow)
                    val right = minOf(sourceBitmap.width, box.right + grow)
                    val bottom = minOf(sourceBitmap.height, box.bottom + grow)
                    val boxW = right - left
                    val boxH = bottom - top
                    if (boxW <= 2 || boxH <= 2) continue

                    val shape = buildTextShapeMask(sourceBitmap, left, top, boxW, boxH) ?: continue

                    val hasPolygon = region.cornerPoints != null && region.cornerPoints.size >= 4
                    if (hasPolygon) {
                        // Clip ke polygon cornerPoints agar mask mengikuti rotasi teks.
                        val pts = region.cornerPoints!!
                        val polyPath = Path().apply {
                            moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                            for (i in 1 until pts.size) lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
                            close()
                        }
                        val save = canvas.save()
                        canvas.clipPath(polyPath)
                        canvas.drawBitmap(shape, left.toFloat(), top.toFloat(), null)
                        canvas.restoreToCount(save)
                        shape.recycle()
                    } else {
                        canvas.drawBitmap(shape, left.toFloat(), top.toFloat(), null)
                        shape.recycle()
                    }
                }
            }
        }
        return maskBitmap
    }

    /**
     * Mask bentuk teks untuk satu region: Otsu pada luminance, polaritas
     * otomatis, dilatasi 1px, dan pembersihan komponen kecil (noise).
     * Mengembalikan bitmap ARGB sebesar [w x h] (putih = teks).
     */
    private fun buildTextShapeMask(
        source: Bitmap, left: Int, top: Int, w: Int, h: Int
    ): Bitmap? {
        return try {
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, left, top, w, h)

            // 1) Histogram luminance untuk Otsu.
            val hist = IntArray(256)
            val lum = IntArray(w * h)
            var opaque = 0
            for (i in pixels.indices) {
                val p = pixels[i]
                if ((p ushr 24) < 16) { lum[i] = -1; continue }
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val l = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                lum[i] = l
                hist[l]++
                opaque++
            }
            if (opaque < 16) return null

            // 2) Ambang Otsu (maksimalkan varians antar-kelas).
            var sum = 0L
            for (t in 0 until 256) sum += t.toLong() * hist[t]
            var sumB = 0L
            var wB = 0L
            var maxVar = -1.0
            var thresh = 127
            for (t in 0 until 256) {
                wB += hist[t]
                if (wB == 0L) continue
                val wF = opaque - wB
                if (wF == 0L) break
                sumB += t.toLong() * hist[t]
                val mB = sumB / wB.toDouble()
                val mF = (sum - sumB) / wF.toDouble()
                val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
                if (between > maxVar) {
                    maxVar = between
                    thresh = t
                }
            }

            // 3) Polaritas: coba dua arah, pilih yang rasio tintanya masuk
            //    akal untuk teks (2%..60% area box).
            fun inkCount(dark: Boolean): Int {
                var c = 0
                for (l in lum) {
                    if (l < 0) continue
                    if (if (dark) l <= thresh else l > thresh) c++
                }
                return c
            }
            val darkCount = inkCount(true)
            val lightCount = opaque - darkCount
            val darkFrac = darkCount / opaque.toFloat()
            val lightFrac = lightCount / opaque.toFloat()
            // Default teks gelap; pakai teks terang bila gelap tak masuk akal
            // tapi terang masuk (mis. bubble hitam dengan teks putih).
            val inkIsDark = when {
                darkFrac in 0.02f..0.60f -> true
                lightFrac in 0.02f..0.60f -> false
                else -> darkFrac >= lightFrac // fallback pilih minoritas
            }

            // 4) Biner + dilatasi 1px (menutup anti-alias tepi stroke).
            val bin = BooleanArray(w * h)
            for (i in lum.indices) {
                val l = lum[i]
                bin[i] = l >= 0 && (if (inkIsDark) l <= thresh else l > thresh)
            }
            val dil = BooleanArray(w * h)
            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    val i = rowOff + x
                    if (!bin[i]) continue
                    dil[i] = true
                    if (x > 0) dil[i - 1] = true
                    if (x < w - 1) dil[i + 1] = true
                    if (y > 0) dil[i - w] = true
                    if (y < h - 1) dil[i + w] = true
                }
            }

            // 5) Buang komponen sangat kecil (< 4 piksel) sisa noise threshold.
            val label = IntArray(w * h) { -1 }
            val stack = IntArray(w * h)
            var cur = 0
            for (i in dil.indices) {
                if (!dil[i] || label[i] != -1) continue
                var sp = 0
                stack[sp++] = i
                label[i] = cur
                var area = 0
                var minX = w; var minY = h; var maxX = -1; var maxY = -1
                while (sp > 0) {
                    val p = stack[--sp]
                    val x = p % w
                    val y = p / w
                    area++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    if (x > 0) { val n = p - 1; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                    if (x < w - 1) { val n = p + 1; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                    if (y > 0) { val n = p - w; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                    if (y < h - 1) { val n = p + w; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                }
                if (area < 4) {
                    // Hapus komponen noise ini.
                    for (yy in minY..maxY) {
                        val ro = yy * w
                        for (xx in minX..maxX) {
                            if (label[ro + xx] == cur) dil[ro + xx] = false
                        }
                    }
                }
                cur++
            }

            // 6) Render ke bitmap mask (putih solid = area inpaint).
            val maskPixels = IntArray(w * h)
            for (i in dil.indices) if (dil[i]) maskPixels[i] = -1
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(maskPixels, 0, w, 0, 0, w, h)
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
