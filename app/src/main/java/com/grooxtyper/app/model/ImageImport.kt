package com.grooxtyper.app.model

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decode gambar dari galeri/kamera secara aman TANPA resize untuk kanvas
 * jangkung (mis. 720x16000 didukung full-res):
 * - Sampling hanya bila melebihi BUDGET PIKSEL / batas absolut (bukan
 *   sisi terpanjang) sehingga 720x16000 (11,5MP) lolos sample=1.
 * - Koreksi orientasi EXIF (foto portrait tidak miring).
 * - Stream selalu ditutup.
 *
 * Perbaikan import gambar besar (diadaptasi dari VasiliasTyper
 * `engine/FileManager.kt` + `engine/BitmapSafety.kt` dengan modifikasi
 * GrooxTyper):
 * - Budget heap-aware ([canvasPixelBudget]/[heapImportBudgetBytes]) agar
 *   720x16000 tetap full-res di HP normal (>=256MB heap) tapi otomatis
 *   downsample aman di HP low-end (tidak OOM).
 * - Jalur [BitmapRegionDecoder] tiled 1024px bila sample>4: hindari satu
 *   alokasi monster, tiap tile di-downscale sendiri (lebih tajam daripada
 *   inSampleSize besar sekali tembak).
 * - Second-stage progressive halving bila hasil decode masih di atas budget.
 * - Modifikasi vs Vasilias: tetap sinkron (pemanggil sudah
 *   withContext(Dispatchers.IO)), EXIF tetap diterapkan setelah stitch,
 *   stream decoder TIDAK ditutup prematur via `use` (bug Vasilias), dan
 *   batas absolut Groox 32MP/16384 dipertahankan.
 */
object ImageImport {
    /** Batas dimensi kanvas baru dari gambar import (mendukung 720x16000). */
    const val MAX_CANVAS_DIM = 16384
    /** Sisi panjang maksimum kanvas jangkung (mis. 720x16000). */
    const val MAX_CANVAS_LONG = 16000
    /** Budget piksel kanvas/import: 720x16000 = 11,5MP lolos full-res. */
    const val MAX_CANVAS_PIXELS = 32_000_000L
    /** Batas dimensi thumbnail galeri. */
    const val MAX_THUMB_DIM = 512
    /** Budget decode: full-res sampai 32MP (mencakup 720x16000). */
    const val MAX_IMPORT_PIXELS = 32_000_000L
    /** Batas absolut sisi decode (mencegah alokasi absurd). */
    const val MAX_IMPORT_DIM = 16384
    /** Ukuran tile sumber untuk decode berubin (diambil dari Vasilias, 1024px). */
    const val TILE_DECODE_SIZE = 1024
    /** Sample darurat bila OOM (sama seperti fallback Vasilias). */
    const val FALLBACK_SAMPLE = 8

    // ── Budget image LAYER (gambar atas kanvas: stiker/watermark) ───────────
    // Yang dijaga di sini adalah KEJELASAN saat di-zoom. 12MP/8192px membuat
    // 720x16000 (11,5MP) tetap utuh; hanya foto raksasa (mis. 8000x8000 =
    // 64MP) yang diturunkan supaya tidak OOM.
    const val MAX_LAYER_SOURCE_PIXELS = 12_000_000L
    const val MAX_LAYER_SOURCE_DIM = 8192

    // ── Budget jendela REFERENSI ───────────────────────────────────────────
    // File 4MB boleh diturunkan jadi ≤2MB asal tetap jernih; budget diterapkan
    // ke hasil AKHIR (bitmap + ukuran JPEG), bukan ke file aslinya.
    const val REFERENCE_TARGET_BYTES = 2_000_000L
    const val MAX_REFERENCE_PIXELS = 16_000_000L
    /** Perkiraan byte JPEG per piksel (kualitas 88, foto ≈0.5 — diamankan). */
    private const val JPEG_BYTES_PER_PIXEL = 0.55f

    /**
     * Jaga sumber image layer tetap tajam tapi tidak meledakkan heap.
     * Gambar yang sudah di bawah batas (mis. 720x16000) tidak diCCR sama sekali.
     */
    fun capLayerSource(src: Bitmap): Bitmap {
        if (src.isRecycled) return src
        val pixels = src.width.toLong() * src.height.toLong()
        val longSide = max(src.width, src.height)
        if (pixels <= MAX_LAYER_SOURCE_PIXELS && longSide <= MAX_LAYER_SOURCE_DIM) return src
        val heapPixels = min(
            MAX_LAYER_SOURCE_PIXELS,
            (Runtime.getRuntime().maxMemory() / 24L).coerceAtLeast(2_000_000L)
        )
        val byPixels = kotlin.math.sqrt(heapPixels.toDouble() / pixels.toDouble()).toFloat()
        val byDim = MAX_LAYER_SOURCE_DIM.toFloat() / longSide
        val s = min(1f, min(byPixels, byDim))
        val tw = max(1, (src.width * s).roundToInt())
        val th = max(1, (src.height * s).roundToInt())
        return try {
            val out = scaleDownHighQuality(src, tw, th)
            if (out !== src) src.recycle()
            out
        } catch (e: Exception) {
            src
        } catch (e: OutOfMemoryError) {
            src
        }
    }

    /**
     * Decode gambar untuk jendela referensi dengan budget byte (default 2MB).
     *
     * Decode dulu pada budget piksel yang diperkirakan cukup, lalu VERIFIKASI
     * dengan kompresi JPEG sungguhan. Kalau masih di atas budget (mis. line
     * art Bernoulli yang sulit dikompresi), turunkan sekali lagi dengan faktor
     * akar dari rasio byte → hasil biasanya pas di budget dan tetap jernih
     * karena reduksinya hanya satu tahap.
     */
    fun decodeForReference(
        resolver: ContentResolver,
        uri: Uri,
        targetBytes: Long = REFERENCE_TARGET_BYTES
    ): Bitmap? {
        val targetPixels = (targetBytes / JPEG_BYTES_PER_PIXEL)
            .toLong()
            .coerceIn(600_000L, MAX_REFERENCE_PIXELS)
        val decoded = decodeContentUri(resolver, uri, targetPixels) ?: return null
        var bmp = capLayerSource(decoded)
        if (bmp.isRecycled) return null
        val bytes = estimateJpegBytes(bmp)
        if (bytes > targetBytes) {
            val ratio = kotlin.math.sqrt(targetBytes.toDouble() / bytes.toDouble()).toFloat()
            val tw = max(1, (bmp.width * ratio).roundToInt())
            val th = max(1, (bmp.height * ratio).roundToInt())
            val out = try {
                scaleDownHighQuality(bmp, tw, th)
            } catch (e: Exception) {
                bmp
            } catch (e: OutOfMemoryError) {
                bmp
            }
            if (out !== bmp) runCatching { bmp.recycle() }
            bmp = out
        }
        return bmp
    }

    /** Ukuran JPEG diukur dengan kompresi nyata (sekali, di IO thread). */
    fun estimateJpegBytes(bmp: Bitmap, quality: Int = 88): Long {
        if (bmp.isRecycled) return 0L
        return try {
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            bos.size().toLong()
        } catch (e: Exception) {
            // Fallback konservatif bila kompresi gagal: asumsi 1 byte/px.
            bmp.width.toLong() * bmp.height.toLong()
        } catch (e: OutOfMemoryError) {
            bmp.width.toLong() * bmp.height.toLong()
        }
    }

    // ── Budget heap-aware (adaptasi BitmapSafety Vasilias) ──────────────
    // Vasilias: MAX_CANVAS 12MP, divisor 24, min 2MP. Modifikasi Groox:
    // butuh 11,5MP lolos di HP normal → divisor 12, min 8MP, max 32MP.
    // 512MB heap → 32MP (128MB), 256MB → ~21MP (85MB, 720x16000 lolos),
    // 128MB → 8MP (32MB, 720x16000 fallback sample=2, tidak OOM).

    /** Budget piksel kanvas adaptif heap (tidak pernah melebihi [MAX_CANVAS_PIXELS]). */
    fun canvasPixelBudget(maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Int {
        val heapBased = (maxMemoryBytes / 12L).coerceAtLeast(8_000_000L).coerceAtMost(MAX_CANVAS_PIXELS)
        return heapBased.toInt()
    }

    /** Budget byte untuk satu bitmap import (ARGB_8888 = 4 byte/px). */
    fun heapImportBudgetBytes(maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Long {
        return canvasPixelBudget(maxMemoryBytes).toLong() * 4L
    }

    /** Budget piksel efektif = min(mintaan, absolut 32MP, budget heap). */
    fun effectivePixelBudget(requestedMaxPixels: Long): Long {
        val heapPixels = canvasPixelBudget().toLong()
        return min(requestedMaxPixels.coerceAtLeast(1L), min(MAX_IMPORT_PIXELS, heapPixels))
    }

    fun decodeContentUri(
        resolver: ContentResolver,
        uri: Uri,
        maxPixels: Long = MAX_IMPORT_PIXELS
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val srcW = if (rotated) bounds.outHeight else bounds.outWidth
            val srcH = if (rotated) bounds.outWidth else bounds.outHeight

            // Budget heap-aware: 720x16000 (11,5MP) lolos sample=1 di HP normal,
            // otomatis sample=2 di HP low-end (tidak OOM).
            val budget = max(1L, effectivePixelBudget(maxPixels))
            var sample = 1
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            while (srcW.toLong() * srcH / (sample.toLong() * sample) > budget ||
                max(srcW, srcH) / sample > MAX_IMPORT_DIM
            ) sample *= 2

            val decoded: Bitmap = if (sample > 4) {
                // Gambar sangat besar: jahit tile native 1024px via RegionDecoder
                // (diadaptasi dari FileManager.decodeTiled Vasilias).
                val targetW = max(1, rawW / sample)
                val targetH = max(1, rawH / sample)
                decodeTiledContent(resolver, uri, rawW, rawH, targetW, targetH)
                    ?: return loadFallbackContent(resolver, uri)
            } else {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return null
            }
            // Second-stage: bila masih di atas budget heap (mis. EXIF swap),
            // halving progresif (tajam, sama seperti Vasilias progressiveScaleDown
            // tapi memakai scaleDownHighQuality Groox agar tanpa kode ganda).
            val heapBudget = heapImportBudgetBytes()
            val decodedBytes = decoded.width.toLong() * decoded.height.toLong() * 4L
            val shrunk = if (decodedBytes > heapBudget) {
                val scale = kotlin.math.sqrt(heapBudget.toDouble() / decodedBytes.toDouble()).toFloat()
                val tw = max(1, (decoded.width * scale).toInt())
                val th = max(1, (decoded.height * scale).toInt())
                try {
                    val out = scaleDownHighQuality(decoded, tw, th)
                    if (out !== decoded) decoded.recycle()
                    out
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    decoded
                }
            } else decoded
            applyExifOrientation(shrunk, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            try {
                loadFallbackContent(resolver, uri)
            } catch (_: Exception) { null }
        }
    }

    /**
     * Decode berubin via BitmapRegionDecoder (adaptasi FileManager.decodeTiled).
     * Modifikasi: stream decoder tetap terbuka selama tiling (Vasilias menutup
     * via `use` sebelum decode → rawan gagal di sebagian ROM), EXIF diterapkan
     * pemanggil setelah stitch, dan OOM per-tile dilewati aman.
     */
    private fun decodeTiledContent(
        resolver: ContentResolver,
        uri: Uri,
        rawW: Int, rawH: Int,
        targetW: Int, targetH: Int
    ): Bitmap? {
        var stream: java.io.InputStream? = null
        var decoder: BitmapRegionDecoder? = null
        try {
            stream = resolver.openInputStream(uri) ?: return null
            decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            } ?: return null
            // Stream boleh ditutup setelah decoder terbentuk untuk file-based;
            // untuk content-uri pertahankan hingga selesai? newInstance(InputStream)
            // pada AOSP menyalin data, jadi aman ditutup — tapi tiling segera.
            val result = try {
                Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                return null
            }
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true; isDither = true
            }
            val tileSize = TILE_DECODE_SIZE
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var y = 0
            while (y < rawH) {
                var x = 0
                while (x < rawW) {
                    val x2 = min(x + tileSize, rawW)
                    val y2 = min(y + tileSize, rawH)
                    val tileBmp = try {
                        decoder.decodeRegion(Rect(x, y, x2, y2), opts)
                    } catch (e: Exception) {
                        e.printStackTrace(); null
                    } catch (e: OutOfMemoryError) {
                        e.printStackTrace(); null
                    }
                    if (tileBmp != null) {
                        try {
                            val dstX1 = (x.toLong() * targetW / rawW).toInt()
                            val dstY1 = (y.toLong() * targetH / rawH).toInt()
                            val dstX2 = (x2.toLong() * targetW / rawW).toInt().coerceAtMost(targetW)
                            val dstY2 = (y2.toLong() * targetH / rawH).toInt().coerceAtMost(targetH)
                            val dstW = (dstX2 - dstX1).coerceAtLeast(1)
                            val dstH = (dstY2 - dstY1).coerceAtLeast(1)
                            val scaled = if (tileBmp.width == dstW && tileBmp.height == dstH) {
                                tileBmp
                            } else {
                                Bitmap.createScaledBitmap(tileBmp, dstW, dstH, true)
                            }
                            canvas.drawBitmap(scaled, dstX1.toFloat(), dstY1.toFloat(), paint)
                            if (scaled !== tileBmp) runCatching { scaled.recycle() }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } catch (e: OutOfMemoryError) {
                            e.printStackTrace()
                        } finally {
                            runCatching { tileBmp.recycle() }
                        }
                    }
                    x += tileSize
                }
                y += tileSize
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        } finally {
            runCatching { decoder?.recycle() }
            runCatching { stream?.close() }
        }
    }

    /** Fallback konservatif sample=8 bila OOM (sama seperti Vasilias loadFallback). */
    private fun loadFallbackContent(resolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = FALLBACK_SAMPLE
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }
    }

    fun decodeFileSampled(path: String, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val cap = max(1, maxDimension)
            while (bounds.outWidth / sample > cap || bounds.outHeight / sample > cap) sample *= 2
            // Hormati juga budget heap agar thumbnail 720x16000 tidak OOM di low-end.
            val budget = effectivePixelBudget(MAX_IMPORT_PIXELS)
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > budget) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(path, opts) ?: return null
            // Koreksi EXIF untuk file (sebelumnya diabaikan → foto portrait miring).
            try {
                val exif = ExifInterface(path)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
                applyExifOrientation(raw, orientation)
            } catch (_: Exception) { raw }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decode file full-res heap-aware (untuk buka ulang project 720x16000).
     * Dipakai [com.grooxtyper.app.model.ProjectManager.loadProjectBitmap]
     * agar tidak OOM mentah via BitmapFactory.decodeFile.
     */
    fun decodeFileHeapAware(path: String, maxPixels: Long = MAX_IMPORT_PIXELS): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                orientation = ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) { }
            val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val srcW = if (rotated) rawH else rawW
            val srcH = if (rotated) rawW else rawH
            val budget = max(1L, effectivePixelBudget(maxPixels))
            var sample = 1
            while (srcW.toLong() * srcH / (sample.toLong() * sample) > budget ||
                max(srcW, srcH) / sample > MAX_IMPORT_DIM
            ) sample *= 2
            val decoded: Bitmap = if (sample > 4) {
                decodeTiledFile(path, rawW, rawH, max(1, rawW / sample), max(1, rawH / sample))
                    ?: run {
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = FALLBACK_SAMPLE
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeFile(path, opts) ?: return null
                    }
            } else {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, opts) ?: return null
            }
            applyExifOrientation(decoded, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeTiledFile(
        path: String, rawW: Int, rawH: Int, targetW: Int, targetH: Int
    ): Bitmap? {
        var decoder: BitmapRegionDecoder? = null
        try {
            decoder = BitmapRegionDecoder.newInstance(path, false) ?: return null
            val result = try {
                Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace(); return null
            }
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true; isDither = true
            }
            val tileSize = TILE_DECODE_SIZE
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var y = 0
            while (y < rawH) {
                var x = 0
                while (x < rawW) {
                    val x2 = min(x + tileSize, rawW)
                    val y2 = min(y + tileSize, rawH)
                    val tileBmp = try {
                        decoder.decodeRegion(Rect(x, y, x2, y2), opts)
                    } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
                    if (tileBmp != null) {
                        try {
                            val dstX1 = (x.toLong() * targetW / rawW).toInt()
                            val dstY1 = (y.toLong() * targetH / rawH).toInt()
                            val dstX2 = (x2.toLong() * targetW / rawW).toInt().coerceAtMost(targetW)
                            val dstY2 = (y2.toLong() * targetH / rawH).toInt().coerceAtMost(targetH)
                            val dstW = (dstX2 - dstX1).coerceAtLeast(1)
                            val dstH = (dstY2 - dstY1).coerceAtLeast(1)
                            val scaled = if (tileBmp.width == dstW && tileBmp.height == dstH) tileBmp
                            else Bitmap.createScaledBitmap(tileBmp, dstW, dstH, true)
                            canvas.drawBitmap(scaled, dstX1.toFloat(), dstY1.toFloat(), paint)
                            if (scaled !== tileBmp) runCatching { scaled.recycle() }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } catch (e: OutOfMemoryError) {
                            e.printStackTrace()
                        } finally {
                            runCatching { tileBmp.recycle() }
                        }
                    }
                    x += tileSize
                }
                y += tileSize
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace(); return null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace(); return null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }

    /** Terapkan orientasi EXIF (matriks dihitung dari titik tengah → tepat untuk 8 kasus). */
    fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return src
        return try {
            val swap = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val dw = if (swap) src.height else src.width
            val dh = if (swap) src.width else src.height
            val m = Matrix()
            m.postTranslate(-src.width / 2f, -src.height / 2f)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    m.postRotate(90f)
                    m.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    m.postRotate(270f)
                    m.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                else -> return src
            }
            m.postTranslate(dw / 2f, dh / 2f)
            val out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            src.recycle()
            out
        } catch (e: Exception) {
            e.printStackTrace()
            src
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            src
        }
    }

    /** Dimensi kanvas yang muat dalam [maxDim] dengan aspek tetap (min 1px). */
    fun fitDimensions(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 512 to 512
        val cap = max(1, maxDim).coerceAtMost(MAX_CANVAS_DIM)
        val s = min(1f, cap / max(w, h).toFloat())
        return max(1, (w * s).toInt()) to max(1, (h * s).toInt())
    }

    /**
     * Dimensi kanvas untuk gambar import TANPA resize bila muat:
     * kembalikan ukuran asli (w,h) selama total piksel <= budget efektif
     * (min 32MP absolut, budget heap) dan sisi panjang <= [MAX_CANVAS_LONG].
     * Hanya bila melebihi budget dilakukan downscale proporsional minimal
     * (aspek tetap). Dijamin mendukung 720x16000 full-res (11,5MP) di HP
     * normal; di HP low-end menyesuaikan heap (adaptasi
     * BitmapSafety.fitWithinMaxPixels Vasilias dengan batas Groox).
     */
    fun fitImportDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 512 to 512
        val pixels = w.toLong() * h.toLong()
        val longSide = max(w, h)
        val effectiveBudget = min(MAX_CANVAS_PIXELS, canvasPixelBudget().toLong())
        if (pixels <= effectiveBudget && longSide <= MAX_CANVAS_LONG) {
            return w to h
        }
        val scaleByPixels = kotlin.math.sqrt(effectiveBudget / pixels.toDouble()).toFloat()
        val scaleByLong = MAX_CANVAS_LONG / longSide.toFloat()
        val s = min(1f, min(scaleByPixels, scaleByLong))
        return max(1, (w * s).toInt()) to max(1, (h * s).toInt())
    }

    /**
     * Bitmap papan catur untuk latar transparansi (di-render sekali per
     * ukuran kanvas, lalu digambar di bawah artwork). Untuk kanvas jangkung
     * (mis. 720x16000 = 46MB bila full) gunakan [makeCheckerTile] +
     * gambar berubin agar hemat memori.
     */
    fun makeCheckerBitmap(w: Int, h: Int, cell: Int = 16): Bitmap {
        val bw = max(1, w)
        val bh = max(1, h)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p1 = Paint().apply { color = Color.rgb(176, 176, 176) }
        val p2 = Paint().apply { color = Color.rgb(136, 136, 136) }
        var y = 0
        var row = 0
        while (y < bh) {
            var x = 0
            var col = 0
            val bottom = min(y + cell, bh)
            while (x < bw) {
                val right = min(x + cell, bw)
                canvas.drawRect(x.toFloat(), y.toFloat(), right.toFloat(), bottom.toFloat(), if ((row + col) % 2 == 0) p1 else p2)
                x += cell
                col++
            }
            y += cell
            row++
        }
        return bmp
    }

    /**
     * Tile kecil papan catur (hemat memori untuk kanvas jangkung).
     * Gambar berubin dengan BitmapShader REPEAT, bukan full-bitmap 46MB.
     */
    fun makeCheckerTile(cells: Int = 4, cell: Int = 16): Bitmap {
        val s = max(1, cells * cell)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p1 = Paint().apply { color = Color.rgb(176, 176, 176) }
        val p2 = Paint().apply { color = Color.rgb(136, 136, 136) }
        for (row in 0 until cells) {
            for (col in 0 until cells) {
                val paint = if ((row + col) % 2 == 0) p1 else p2
                canvas.drawRect(
                    (col * cell).toFloat(), (row * cell).toFloat(),
                    ((col + 1) * cell).toFloat(), ((row + 1) * cell).toFloat(),
                    paint
                )
            }
        }
        return bmp
    }

    /**
     * Gambar [src] ke tengah [target] 1:1 TANPA scaling (no-resize).
     * Kelebihan di-crop, kekurangan dibiarkan transparan di tepi.
     * Dipakai untuk import agar 720x16000 tidak diubah.
     */
    fun drawBitmapCenterNoScale(target: Bitmap, src: Bitmap) {
        if (src.isRecycled || target.isRecycled) return
        if (src.width <= 0 || src.height <= 0) return
        val left = (target.width - src.width) / 2f
        val top = (target.height - src.height) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(target).drawBitmap(src, left, top, paint)
    }

    fun scaleTo(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width == dstW && src.height == dstH) return src
        return try {
            val out = scaleDownHighQuality(src, dstW, dstH)
            if (out !== src) src.recycle()
            out
        } catch (e: Exception) {
            e.printStackTrace()
            src
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            src
        }
    }

    /**
     * Downscale bertahap (belah dua berulang): jauh lebih tajam daripada
     * sekali tembak untuk reduksi ekstrem (mis. 16000px → 1280px).
     * Upscale kecil dilewatkan sekali jalan (tetap tajam).
     */
    fun scaleDownHighQuality(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val w = max(1, dstW)
        val h = max(1, dstH)
        if (src.width == w && src.height == h) return src
        var cur = src
        var cw = src.width
        var ch = src.height
        while (cw / 2 >= w && ch / 2 >= h && cw > 1 && ch > 1) {
            cw /= 2
            ch /= 2
            val half = Bitmap.createScaledBitmap(cur, cw, ch, true)
            if (cur !== src) cur.recycle()
            cur = half
        }
        if (cur.width == w && cur.height == h) return cur
        val out = Bitmap.createScaledBitmap(cur, w, h, true)
        if (cur !== src) cur.recycle()
        return out
    }

    /** Gambar [src] ke tengah [target] (fit, aspek tetap). Tidak menghapus isi lama. */
    fun drawBitmapCenterFit(target: Bitmap, src: Bitmap) {
        if (src.isRecycled || target.isRecycled) return
        if (src.width <= 0 || src.height <= 0) return
        val s = min(
            target.width / src.width.toFloat(),
            target.height / src.height.toFloat()
        )
        val dw = src.width * s
        val dh = src.height * s
        val left = (target.width - dw) / 2f
        val top = (target.height - dh) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(target).drawBitmap(src, null, RectF(left, top, left + dw, top + dh), paint)
    }

    /**
     * Seperti [drawBitmapCenterFit] tapi sisi yang mengecil di-downscale
     * bertahap dulu → hasil tajam untuk gambar jangkung/lebar ekstrem.
     */
    fun drawBitmapCenterFitHQ(target: Bitmap, src: Bitmap) {
        if (src.isRecycled || target.isRecycled) return
        if (src.width <= 0 || src.height <= 0) return
        val s = min(
            target.width / src.width.toFloat(),
            target.height / src.height.toFloat()
        )
        val dw = max(1, (src.width * s).roundToInt())
        val dh = max(1, (src.height * s).roundToInt())
        var fitted: Bitmap? = null
        try {
            fitted = if (dw != src.width || dh != src.height) {
                scaleDownHighQuality(src, dw, dh)
            } else src
            val left = (target.width - fitted.width) / 2f
            val top = (target.height - fitted.height) / 2f
            Canvas(target).drawBitmap(fitted, left, top, null)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: gambar langsung satu langkah.
            drawBitmapCenterFit(target, src)
        } finally {
            if (fitted != null && fitted !== src) fitted.recycle()
        }
    }
}
