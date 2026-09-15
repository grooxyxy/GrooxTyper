package com.grooxtyper.app.model

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decode gambar dari galeri/kamera secara aman:
 * - Sampling berbasis BUDGET PIKSEL (bukan sisi terpanjang) agar gambar
 *   sangat jangkung (mis. 720x16000) tidak hancur di sisi pendeknya.
 * - Downscale bertahap (stepped halving) agar hasil tajam, bukan buram.
 * - Koreksi orientasi EXIF (foto portrait tidak miring).
 * - Stream selalu ditutup.
 */
object ImageImport {
    /** Batas dimensi kanvas baru dari gambar import (mencegah OOM). */
    const val MAX_CANVAS_DIM = 2048
    /** Batas dimensi thumbnail galeri. */
    const val MAX_THUMB_DIM = 512
    /** Budget decode: detail dipertahankan sampai 16MP. */
    const val MAX_IMPORT_PIXELS = 16_000_000L
    /** Batas absolut sisi decode (mencegah alokasi absurd). */
    const val MAX_IMPORT_DIM = 8192

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

            // Sampling menjaga total piksel (bukan sisi max) + batas absolut.
            var sample = 1
            val budget = max(1L, maxPixels)
            while (srcW.toLong() * srcH / (sample.toLong() * sample) > budget ||
                max(srcW, srcH) / sample > MAX_IMPORT_DIM
            ) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            applyExifOrientation(decoded, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    fun decodeFileSampled(path: String, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val cap = max(1, maxDimension)
            while (bounds.outWidth / sample > cap || bounds.outHeight / sample > cap) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
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

    /** Dimensi kanvas yang muat dalam [maxDim] dengan aspek tetap (min 100px). */
    fun fitDimensions(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 512 to 512
        val s = min(1f, maxDim / max(w, h).toFloat())
        return max(100, (w * s).toInt()) to max(100, (h * s).toInt())
    }

    /**
     * Dimensi kanvas untuk gambar import: gambar biasa dibatasi MAX_CANVAS_DIM,
     * gambar dengan aspek ekstrem (>= 4:1, mis. tangkapan layar jangkung)
     * boleh memakai sisi panjang sampai 4096 agar tidak jadi strip buram.
     * Konsumsi memori tetap kecil karena sisi pendeknya mungil.
     */
    fun fitImportDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 512 to 512
        val ratio = max(w, h) / min(w, h).toFloat()
        val longCap = if (ratio >= 4f) 4096 else MAX_CANVAS_DIM
        val s = min(1f, longCap / max(w, h).toFloat())
        return max(100, (w * s).toInt()) to max(100, (h * s).toInt())
    }

    /**
     * Bitmap papan catur untuk latar transparansi (di-render sekali per
     * ukuran kanvas, lalu digambar di bawah artwork).
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
