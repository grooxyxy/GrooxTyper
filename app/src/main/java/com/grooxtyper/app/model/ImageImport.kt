package com.grooxtyper.app.model

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/**
 * Decode gambar dari galeri/kamera secara aman:
 * - Downsampling (inSampleSize) agar foto besar tidak OOM.
 * - Koreksi orientasi EXIF (foto portrait tidak miring).
 * - Stream selalu ditutup.
 */
object ImageImport {
    /** Batas dimensi kanvas baru dari gambar import (mencegah OOM). */
    const val MAX_CANVAS_DIM = 2048
    /** Batas dimensi thumbnail galeri. */
    const val MAX_THUMB_DIM = 512

    fun decodeContentUri(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? {
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

            var sample = 1
            val cap = max(1, maxDimension)
            while (srcW / sample > cap || srcH / sample > cap) sample *= 2

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

    fun scaleTo(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width == dstW && src.height == dstH) return src
        return try {
            val out = Bitmap.createScaledBitmap(src, dstW, dstH, true)
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
}
