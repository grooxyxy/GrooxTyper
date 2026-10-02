package com.grooxtyper.app.model.selection

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Penyedia piksel dari satu Bitmap yang sudah ada.
 *
 * Tidak membuat bitmap baru: hanya membaca persegi yang diminta dari
 * bitmap sumber (composite untuk Sample Merged, atau bitmap layer aktif
 * bila mati). Cache tile kecil menghindari baca ulang tile yang sama
 * saat flood fill berjalan.
 */
class HugePixelProvider(
    private val source: Bitmap,
    private val tileSize: Int = 256,
    private val cacheTiles: Int = 8
) : CanvasPixelProvider {

    override val canvasWidth: Int get() = source.width
    override val canvasHeight: Int get() = source.height

    private data class Entry(val tx: Int, val ty: Int, val px: IntArray, val w: Int, val h: Int)

    private val cache = ArrayDeque<Entry>()

    override fun getPixel(x: Int, y: Int): Int {
        if (x !in 0 until source.width || y !in 0 until source.height) return 0
        return runCatching { source.getPixel(x, y) }.getOrDefault(0)
    }

    override fun getPixels(x: Int, y: Int, width: Int, height: Int): IntArray {
        val out = IntArray(width * height)
        val sx0 = max(0, x)
        val sy0 = max(0, y)
        val sx1 = min(source.width, x + width)
        val sy1 = min(source.height, y + height)
        if (sx1 <= sx0 || sy1 <= sy0) return out
        // Baca per tile supaya tile yang sama dipakai ulang dari cache.
        var ty = sy0 / tileSize
        while (ty <= (sy1 - 1) / tileSize) {
            var tx = sx0 / tileSize
            while (tx <= (sx1 - 1) / tileSize) {
                copyTile(tx, ty, sx0, sy0, sx1, sy1, x, y, width, out)
                tx++
            }
            ty++
        }
        return out
    }

    private fun copyTile(
        tx: Int, ty: Int,
        sx0: Int, sy0: Int, sx1: Int, sy1: Int,
        ox: Int, oy: Int, stride: Int, out: IntArray
    ) {
        val e = tileOf(tx, ty) ?: return
        val ix0 = max(sx0, tx * tileSize)
        val iy0 = max(sy0, ty * tileSize)
        val ix1 = min(sx1, tx * tileSize + e.w)
        val iy1 = min(sy1, ty * tileSize + e.h)
        for (yy in iy0 until iy1) {
            val srcRow = (yy - ty * tileSize) * e.w
            val dstRow = (yy - oy) * stride
            for (xx in ix0 until ix1) {
                out[dstRow + (xx - ox)] = e.px[srcRow + (xx - tx * tileSize)]
            }
        }
    }

    private fun tileOf(tx: Int, ty: Int): Entry? {
        val hit = cache.firstOrNull { it.tx == tx && it.ty == ty }
        if (hit != null) {
            cache.remove(hit)
            cache.addLast(hit)
            return hit
        }
        val x0 = tx * tileSize
        val y0 = ty * tileSize
        if (x0 >= source.width || y0 >= source.height) return null
        val w = min(tileSize, source.width - x0)
        val h = min(tileSize, source.height - y0)
        if (w <= 0 || h <= 0) return null
        val buf = IntArray(w * h)
        return try {
            source.getPixels(buf, 0, w, x0, y0, w, h)
            val e = Entry(tx, ty, buf, w, h)
            cache.addLast(e)
            while (cache.size > cacheTiles) cache.removeFirst()
            e
        } catch (t: Throwable) {
            null
        }
    }

    /** Buang cache tile (dipanggil setelah layer berubah). */
    fun invalidate() {
        cache.clear()
    }
}
