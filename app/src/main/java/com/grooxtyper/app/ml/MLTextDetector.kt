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
import kotlin.math.abs
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

    private companion object {
        /** Sisi terpanjang input yang aman untuk satu kali proses ML Kit. */
        const val MAX_INPUT_SIDE = 2048
        /** Tinggi strip (piksel sumber) untuk kanvas jangkung. */
        const val STRIP_SRC = 1000
        /** Overlap antar strip agar baris di sambungan tidak terpotong. */
        const val STRIP_OVERLAP_SRC = 140
    }

    /**
     * Deteksi teks ML Kit v2 MULTI-PASS agar hasil jauh lebih lengkap:
     * pass 1 resolusi asli + pass 2 upscale 2x (teks kecil/tipis baru
     * terbaca setelah diperbesar). Hasil digabung, dedupe
     * containment-aware, lalu fragmen baris yang terpisah digabung ulang.
     */
    suspend fun detectTextRegions(
        bitmap: Bitmap,
        scripts: Set<MLScript> = setOf(MLScript.LATIN)
    ): List<DetectedTextRegion> {
        if (scripts.isEmpty() || bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val base = detectScaled(bitmap, scripts, scale = 1f)
        val hiRes = detectScaled(bitmap, scripts, scale = 2f)
        val merged = dedupe(base + hiRes)
        return mergeFragmentedLines(merged)
    }

    /**
     * Satu pass deteksi pada skala [scale] (1f = asli, 2f = upscale 2x).
     * Bila hasil upscale melebihi [MAX_INPUT_SIDE], gambar dipotong jadi
     * strip horizontal ber-overlap (dalam koordinat sumber) lalu koordinat
     * tiap strip dipetakan balik ke kanvas penuh.
     */
    private suspend fun detectScaled(
        bitmap: Bitmap,
        scripts: Set<MLScript>,
        scale: Float
    ): List<DetectedTextRegion> {
        val scaledH = bitmap.height * scale
        if (scaledH <= MAX_INPUT_SIDE) {
            val work = scaledCopy(bitmap, scale) ?: return emptyList()
            try {
                val found = mutableListOf<DetectedTextRegion>()
                for (script in scripts) {
                    val client = clients[script] ?: continue
                    try {
                        found += detectWith(client, work, script)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return found.map { scaleBack(it, scale, 0) }
            } finally {
                if (work !== bitmap) runCatching { work.recycle() }
            }
        }
        // Kanvas jangkung (mis. webtoon 720x16000): strip ber-overlap.
        val w = bitmap.width
        val h = bitmap.height
        val step = STRIP_SRC - STRIP_OVERLAP_SRC
        val all = mutableListOf<DetectedTextRegion>()
        var top = 0
        while (top < h) {
            val bottom = min(h, top + STRIP_SRC)
            val curTop = if (bottom >= h) max(0, bottom - STRIP_SRC) else top
            val curH = bottom - curTop
            if (curH <= 0) break
            var crop: Bitmap? = null
            var work: Bitmap? = null
            try {
                crop = Bitmap.createBitmap(bitmap, 0, curTop, w, curH)
                work = scaledCopy(crop, scale)
                if (work != null) {
                    for (script in scripts) {
                        val client = clients[script] ?: continue
                        try {
                            val found = detectWith(client, work, script)
                            for (r in found) all.add(scaleBack(r, scale, curTop))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (work != null && work !== crop) runCatching { work.recycle() }
                runCatching { crop?.recycle() }
            }
            if (bottom >= h) break
            top += step
        }
        return all
    }

    /** Salin bitmap pada skala tertentu; skala 1f mengembalikan sumber. */
    private fun scaledCopy(src: Bitmap, scale: Float): Bitmap? {
        if (scale == 1f) return src
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Petakan hasil pada gambar berskala [scale] balik ke koordinat sumber. */
    private fun scaleBack(r: DetectedTextRegion, scale: Float, offsetY: Int): DetectedTextRegion {
        if (scale == 1f && offsetY == 0) return r
        val b = r.boundingBox
        val inv = 1f / scale
        val box = Rect(
            (b.left * inv).toInt(),
            (b.top * inv).toInt() + offsetY,
            (b.right * inv).toInt(),
            (b.bottom * inv).toInt() + offsetY
        )
        val corners = r.cornerPoints?.map { p ->
            android.graphics.Point((p.x * inv).toInt(), (p.y * inv).toInt() + offsetY)
        }?.toTypedArray()
        return r.copy(boundingBox = box, cornerPoints = corners)
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

    /**
     * Buang prediksi ganda antar-pass/antar-script: duplikat bila IoU > 0.45
     * ATAU salah satu box > 70% termuat di dalam yang lain (pass upscale
     * sering menghasilkan box sedikit berbeda ukuran). Simpan teks terpanjang.
     */
    private fun dedupe(regions: List<DetectedTextRegion>): List<DetectedTextRegion> {
        val out = mutableListOf<DetectedTextRegion>()
        for (r in regions.sortedByDescending { it.text.length }) {
            val dup = out.any { o ->
                iou(o.boundingBox, r.boundingBox) > 0.45f ||
                    containment(o.boundingBox, r.boundingBox) > 0.7f
            }
            if (!dup) out.add(r)
        }
        return out
    }

    /** Seberapa besar box yang lebih kecil termuat di dalam box lain (0..1). */
    private fun containment(a: Rect, b: Rect): Float {
        val ix = max(0, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        val smaller = min(a.width() * a.height(), b.width() * b.height())
        return if (smaller <= 0) 0f else inter.toFloat() / smaller
    }

    /**
     * Gabung fragmen baris yang terpisah (mis. kata dalam satu baris bubble
     * terbaca sebagai dua line): syaratnya script sama, tinggi mirip, garis
     * tengah vertikal sejajar, celah horizontal < ~1,1x tinggi baris, dan
     * kedua box berbentuk horizontal (kolom teks vertikal tidak digabung).
     */
    private fun mergeFragmentedLines(regions: List<DetectedTextRegion>): List<DetectedTextRegion> {
        val items = regions.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            var done = false
            for (i in items.indices) {
                if (done) break
                for (j in i + 1 until items.size) {
                    val m = tryMergeLine(items[i], items[j])
                    if (m != null) {
                        items[i] = m
                        items.removeAt(j)
                        merged = true
                        done = true
                        break
                    }
                }
            }
        }
        return items.sortedWith(compareBy({ it.boundingBox.top }, { it.boundingBox.left }))
    }

    private fun tryMergeLine(a: DetectedTextRegion, b: DetectedTextRegion): DetectedTextRegion? {
        if (a.script != b.script) return null
        val ra = a.boundingBox
        val rb = b.boundingBox
        val hA = ra.height()
        val hB = rb.height()
        if (hA <= 0 || hB <= 0) return null
        // Hanya baris horizontal; teks vertikal (kolom) jangan digabung.
        if (ra.width() < hA || rb.width() < hB) return null
        val minH = min(hA, hB)
        val maxH = max(hA, hB)
        if (maxH > minH * 2.2f) return null
        if (abs(ra.centerY() - rb.centerY()) > minH * 0.45f) return null
        val gap = max(rb.left - ra.right, ra.left - rb.right)
        if (gap > minH * 1.1f) return null
        // Tumpang tindih dalam berarti bukan fragmen — urusan dedupe.
        if (gap < -min(ra.width(), rb.width()) / 2) return null
        val (leftR, rightR) = if (ra.left <= rb.left) a to b else b to a
        val joined = DetectedTextRegion(
            text = leftR.text + " " + rightR.text,
            boundingBox = Rect(
                min(ra.left, rb.left), min(ra.top, rb.top),
                max(ra.right, rb.right), max(ra.bottom, rb.bottom)
            ),
            cornerPoints = null,
            script = a.script
        )
        return joined
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
     * MASK_BENTUK_TEKS: Otsu di atas JARAK WARNA dari latar (bukan luminance
     * mentah) sehingga menutup: outline/shadow gelap, teks berwarna,
     * teks terang di atas gelap, maupun teks gradasi — selama berbeda dari
     * latar. Bila bentuk gagal dihitung, fallback ke kotak berpadding agar
     * region tak pernah lolos tanpa mask.
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
            // Kotak berpadding (dipakai MASK_KOTAK & fallback bentuk-teks).
            val box = region.boundingBox
            val padX = (box.width() * 0.04f).coerceIn(2f, 6f)
            val padY = (box.height() * 0.04f).coerceIn(2f, 6f)
            val rl = (box.left - padX).coerceIn(0f, canvasWidth.toFloat())
            val rt = (box.top - padY).coerceIn(0f, canvasHeight.toFloat())
            val rr = (box.right + padX).coerceIn(0f, canvasWidth.toFloat())
            val rb = (box.bottom + padY).coerceIn(0f, canvasHeight.toFloat())
            when (maskType) {
                MLMaskType.MASK_KOTAK -> {
                    // Mask Kotak: persegi panjang solid dengan padding kecil agar
                    // inpaint menutup tepi huruf sepenuhnya (variasi kotak).
                    canvas.drawRect(rl, rt, rr, rb, fillPaint)
                }
                MLMaskType.MASK_BENTUK_TEKS -> {
                    // Perluas box sedikit agar stroke/shadow (anti-alias) ikut.
                    val grow = maxOf(4, box.height() / 10)
                    val left = maxOf(0, box.left - grow)
                    val top = maxOf(0, box.top - grow)
                    val right = minOf(sourceBitmap.width, box.right + grow)
                    val bottom = minOf(sourceBitmap.height, box.bottom + grow)
                    val boxW = right - left
                    val boxH = bottom - top
                    if (boxW <= 2 || boxH <= 2) {
                        canvas.drawRect(rl, rt, rr, rb, fillPaint)
                        continue
                    }

                    val shape = buildTextShapeMask(sourceBitmap, left, top, boxW, boxH)
                    if (shape == null) {
                        canvas.drawRect(rl, rt, rr, rb, fillPaint)
                        continue
                    }

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
     * Mask bentuk teks untuk satu region: Sauvola adaptif primer (tahan bg tak rata,
     * ref scikit-image Niblack/Sauvola + SWT window adaptif), fallback Otsu jarak warna.
     * Mengembalikan bitmap ARGB sebesar [w x h] (putih = teks).
     */
    private fun buildTextShapeMask(
        source: Bitmap, left: Int, top: Int, w: Int, h: Int
    ): Bitmap? {
        // Primer: Sauvola integral-image (cepat, O(1)/px, hemat di 720x16000).
        try {
            buildTextShapeMaskSauvola(source, left, top, w, h)?.let { return it }
        } catch (e: Exception) { e.printStackTrace() }
        return buildTextShapeMaskOtsu(source, left, top, w, h)
    }

    /**
     * Sauvola lokal via integral images (hidouciyoucef/method-SAUVOLA,
     * wahabaftab integral-images, Yuyang-Du-NTU C++/OpenCV, SauvolaNet MWS):
     * T = m*(1+k*(s/R-1)), k=0.2, R=128, window adaptif SWT (Modified Sauvola:
     * window dari estimasi stroke, bukan fixed) + polaritas otomatis + clip polygon di pemanggil.
     */
    private fun buildTextShapeMaskSauvola(
        source: Bitmap, left: Int, top: Int, w: Int, h: Int
    ): Bitmap? {
        return try {
            if (w <= 4 || h <= 4 || w * h > 4_000_000) return null
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, left, top, w, h)
            val gray = DoubleArray(w * h)
            var gMean = 0.0
            var gCount = 0
            for (i in pixels.indices) {
                val p = pixels[i]
                if ((p ushr 24) < 16) { gray[i] = -1.0; continue }
                val g = 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
                gray[i] = g
                gMean += g
                gCount++
            }
            if (gCount < 32) return null
            gMean /= gCount
            // Estimasi latar dari bingkai (median approx via mean bingkai, cepat).
            var bSum = 0.0
            var bN = 0
            for (x in 0 until w) {
                for (y in intArrayOf(0, h - 1)) {
                    val g = gray[y * w + x]
                    if (g >= 0) { bSum += g; bN++ }
                }
            }
            for (y in 0 until h) {
                for (x in intArrayOf(0, w - 1)) {
                    val g = gray[y * w + x]
                    if (g >= 0) { bSum += g; bN++ }
                }
            }
            if (bN < 8) return null
            val bMean = bSum / bN
            // Polaritas otomatis: latar terang -> tinta gelap (pixel < T), sebaliknya pixel > T.
            val darkInk = bMean >= gMean
            // Window adaptif SWT: proporsional tinggi region (stroke ~ h/8), ganjil, 15..51.
            var win = (h / 6).coerceIn(15, 51)
            if (win % 2 == 0) win += 1
            val half = win / 2
            val k = 0.2
            val R = 128.0
            // Integral images sum + sumSq.
            val iw = w + 1
            val ih = h + 1
            val intSum = DoubleArray(iw * ih)
            val intSq = DoubleArray(iw * ih)
            for (y in 0 until h) {
                var rowS = 0.0
                var rowQ = 0.0
                for (x in 0 until w) {
                    var g = gray[y * w + x]
                    if (g < 0) g = bMean
                    rowS += g
                    rowQ += g * g
                    val idx = (y + 1) * iw + (x + 1)
                    intSum[idx] = intSum[y * iw + (x + 1)] + rowS
                    intSq[idx] = intSq[y * iw + (x + 1)] + rowQ
                }
            }
            fun rectStats(x0: Int, y0: Int, x1: Int, y1: Int): Pair<Double, Double> {
                val xa = x0.coerceIn(0, w - 1)
                val ya = y0.coerceIn(0, h - 1)
                val xb = x1.coerceIn(0, w - 1)
                val yb = y1.coerceIn(0, h - 1)
                val l = minOf(xa, xb); val r = maxOf(xa, xb)
                val t = minOf(ya, yb); val b = maxOf(ya, yb)
                val n = ((r - l + 1) * (b - t + 1)).toDouble().coerceAtLeast(1.0)
                val s = intSum[(b + 1) * iw + (r + 1)] - intSum[t * iw + (r + 1)] - intSum[(b + 1) * iw + l] + intSum[t * iw + l]
                val q = intSq[(b + 1) * iw + (r + 1)] - intSq[t * iw + (r + 1)] - intSq[(b + 1) * iw + l] + intSq[t * iw + l]
                val m = s / n
                var v = q / n - m * m
                if (v < 0) v = 0.0
                return m to kotlin.math.sqrt(v)
            }
            val bin = BooleanArray(w * h)
            var ink = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val g = gray[i]
                    if (g < 0) continue
                    val (m, s) = rectStats(x - half, y - half, x + half, y + half)
                    val t = m * (1.0 + k * (s / R - 1.0))
                    val isInk = if (darkInk) g < t else g > t
                    if (isInk) { bin[i] = true; ink++ }
                }
            }
            val frac = ink / gCount.toFloat()
            // SWT/MSER filter: fraksi tinta wajar untuk glyph (buang noise/flat).
            if (frac < 0.01f || frac > 0.65f) return null
            // Dilatasi 1px (anti-alias) + buang komponen <12px (MSER stability, naik dari 8).
            var cur = bin
            repeat(1) {
                val nxt = BooleanArray(w * h)
                for (y in 0 until h) for (x in 0 until w) {
                    if (cur[y * w + x]) {
                        for (dy in -1..1) for (dx in -1..1) {
                            val xx = x + dx; val yy = y + dy
                            if (xx in 0 until w && yy in 0 until h) nxt[yy * w + xx] = true
                        }
                    }
                }
                cur = nxt
            }
            val label = IntArray(w * h) { -1 }
            val stack = IntArray(w * h)
            var comp = 0
            for (i in cur.indices) {
                if (!cur[i] || label[i] != -1) continue
                var sp = 0
                stack[sp++] = i
                label[i] = comp
                var area = 0
                var minX = w; var minY = h; var maxX = -1; var maxY = -1
                while (sp > 0) {
                    val p = stack[--sp]
                    val x = p % w; val y = p / w
                    area++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    if (x > 0) { val n = p - 1; if (cur[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (x < w - 1) { val n = p + 1; if (cur[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (y > 0) { val n = p - w; if (cur[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (y < h - 1) { val n = p + w; if (cur[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                }
                // SWT filter: buang sangat kecil + sangat pipih tak wajar untuk huruf.
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                val aspect = bw.toFloat() / bh.coerceAtLeast(1).toFloat()
                if (area < 12 || aspect < 0.05f || aspect > 20f) {
                    for (yy in minY..maxY) for (xx in minX..maxX) {
                        if (label[yy * w + xx] == comp) cur[yy * w + xx] = false
                    }
                }
                comp++
            }
            var kept = 0
            for (b in cur) if (b) kept++
            if (kept < 12) return null
            val maskPixels = IntArray(w * h)
            for (i in cur.indices) if (cur[i]) maskPixels[i] = -1
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(maskPixels, 0, w, 0, 0, w, h)
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Mask bentuk teks untuk satu region: estimasi warna latar dari bingkai
     * tepi box, Otsu di atas histogram JARAK WARNA (Chebyshev) dari latar,
     * dilatasi 3 iterasi, dan pembersihan komponen kecil (noise).
     * Jarak bersifat tak-bertanda sehingga satu ambang menutup teks gelap,
     * teks berwarna/terang, gradasi, outline, sekaligus fringe shadow yang
     * masih cukup beda dari latar. Null bila box praktis datar.
     * Mengembalikan bitmap ARGB sebesar [w x h] (putih = teks).
     */
    private fun buildTextShapeMaskOtsu(
        source: Bitmap, left: Int, top: Int, w: Int, h: Int
    ): Bitmap? {
        return try {
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, left, top, w, h)

            // 1) Latar = median tiap kanal dari bingkai tepi box (tahan outlier).
            val histR = IntArray(256)
            val histG = IntArray(256)
            val histB = IntArray(256)
            var border = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val edge = y == 0 || y == h - 1 || x == 0 || x == w - 1
                    if (!edge) continue
                    val p = pixels[y * w + x]
                    if ((p ushr 24) < 16) continue
                    histR[(p shr 16) and 0xFF]++
                    histG[(p shr 8) and 0xFF]++
                    histB[p and 0xFF]++
                    border++
                }
            }
            if (border < 8) return null
            fun medianOf(hist: IntArray, total: Int): Int {
                var acc = 0
                val half = total / 2
                for (v in 0 until 256) {
                    acc += hist[v]
                    if (acc > half) return v
                }
                return 128
            }
            val br = medianOf(histR, border)
            val bg = medianOf(histG, border)
            val bb = medianOf(histB, border)

            // 2) Histogram jarak Chebyshev dari latar + hitung piksel valid.
            val histD = IntArray(256)
            val dist = IntArray(w * h)
            var valid = 0
            for (i in pixels.indices) {
                val p = pixels[i]
                if ((p ushr 24) < 16) {
                    dist[i] = -1
                    continue
                }
                val dr = kotlin.math.abs(((p shr 16) and 0xFF) - br)
                val dg = kotlin.math.abs(((p shr 8) and 0xFF) - bg)
                val db = kotlin.math.abs((p and 0xFF) - bb)
                val d = maxOf(dr, dg, db)
                dist[i] = d
                histD[d]++
                valid++
            }
            if (valid < 16) return null

            // 3) Ambang Otsu di atas histogram jarak (floor anti-noise).
            var sum = 0L
            for (t in 0 until 256) sum += t.toLong() * histD[t]
            var sumB = 0L
            var wB = 0L
            var maxVar = -1.0
            var thresh = 24
            for (t in 0 until 256) {
                wB += histD[t]
                if (wB == 0L) continue
                val wF = valid - wB
                if (wF == 0L) break
                sumB += t.toLong() * histD[t]
                val mB = sumB / wB.toDouble()
                val mF = (sum - sumB) / wF.toDouble()
                val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
                if (between > maxVar) {
                    maxVar = between
                    thresh = t
                }
            }
            thresh = maxOf(16, thresh)

            // 4) Fraksi tinta harus masuk akal; bila tidak, box datar → null.
            var ink = 0
            for (d in dist) if (d > thresh) ink++
            val frac = ink / valid.toFloat()
            if (frac < 0.005f || frac > 0.75f) return null

            // 5) Biner + dilatasi 3 iterasi (tutup fringe shadow/outline).
            var cur = BooleanArray(w * h) { dist[it] > thresh }
            repeat(3) {
                val nxt = BooleanArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        if (cur[y * w + x]) {
                            for (dy in -1..1) {
                                val yy = y + dy
                                if (yy < 0 || yy >= h) continue
                                for (dx in -1..1) {
                                    val xx = x + dx
                                    if (xx < 0 || xx >= w) continue
                                    nxt[yy * w + xx] = true
                                }
                            }
                        }
                    }
                }
                cur = nxt
            }
            val dil = cur

            // 6) Buang komponen sangat kecil (< 8 piksel) sisa noise threshold.
            val label = IntArray(w * h) { -1 }
            val stack = IntArray(w * h)
            var comp = 0
            for (i in dil.indices) {
                if (!dil[i] || label[i] != -1) continue
                var sp = 0
                stack[sp++] = i
                label[i] = comp
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
                    if (x > 0) { val n = p - 1; if (dil[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (x < w - 1) { val n = p + 1; if (dil[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (y > 0) { val n = p - w; if (dil[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                    if (y < h - 1) { val n = p + w; if (dil[n] && label[n] == -1) { label[n] = comp; stack[sp++] = n } }
                }
                if (area < 8) {
                    for (yy in minY..maxY) {
                        val ro = yy * w
                        for (xx in minX..maxX) {
                            if (label[ro + xx] == comp) dil[ro + xx] = false
                        }
                    }
                }
                comp++
            }

            // 7) Render ke bitmap mask (putih solid = area inpaint).
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
