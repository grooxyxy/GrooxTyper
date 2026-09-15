package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

const val TILE_SIZE = 512

/** Ambang kanvas raksasa: tile cache dinonaktifkan (render pakai compositeBitmap). */
const val HUGE_CANVAS_PIXELS = 4_000_000L

class CanvasTile(val tileX: Int, val tileY: Int) {
    // Alokasi malas: 720x16000 = 64 tile = 64MB bila eager. Tile tidak
    // dibaca oleh render (renderComposite memakai compositeBitmap), jadi
    // jangan alokasi sampai benar-benar dipakai.
    private var _bitmap: Bitmap? = null
    private var _canvas: Canvas? = null
    val bitmap: Bitmap
        get() {
            var b = _bitmap
            if (b == null) {
                b = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
                _bitmap = b
                _canvas = Canvas(b)
            }
            return b
        }
    val canvas: Canvas
        get() {
            bitmap
            return _canvas!!
        }
    var isDirty: Boolean = true

    fun clear() {
        // Jangan alokasi hanya untuk clear — tile yang belum ada sudah kosong.
        val b = _bitmap ?: run { isDirty = true; return }
        Canvas(b).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        isDirty = true
    }

    fun recycle() {
        runCatching { _bitmap?.recycle() }
        _bitmap = null
        _canvas = null
    }
}

class LayerTileMap(val canvasWidth: Int, val canvasHeight: Int) {
    val numTilesX: Int = ceil(canvasWidth.toDouble() / TILE_SIZE).toInt()
    val numTilesY: Int = ceil(canvasHeight.toDouble() / TILE_SIZE).toInt()
    // Kanvas raksasa (mis. 720x16000): 64 tile ≈ 64MB cache yang tidak
    // dibaca render (renderComposite memakai compositeBitmap). Nonaktifkan
    // tulis tile agar import/sync tidak jank + hemat memori.
    val tilesEnabled: Boolean = canvasWidth.toLong() * canvasHeight <= HUGE_CANVAS_PIXELS
    val tiles: Array<Array<CanvasTile>> = Array(numTilesY) { y ->
        Array(numTilesX) { x -> CanvasTile(x, y) }
    }

    fun getTileAtPixel(x: Float, y: Float): CanvasTile? {
        val tx = (x / TILE_SIZE).toInt()
        val ty = (y / TILE_SIZE).toInt()
        if (tx in 0 until numTilesX && ty in 0 until numTilesY) {
            return tiles[ty][tx]
        }
        return null
    }

    fun clear() {
        if (!tilesEnabled) return
        for (y in 0 until numTilesY) {
            for (x in 0 until numTilesX) {
                tiles[y][x].clear()
            }
        }
    }

    fun renderToCompositeBitmap(targetBitmap: Bitmap) {
        val targetCanvas = Canvas(targetBitmap)
        targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (y in 0 until numTilesY) {
            for (x in 0 until numTilesX) {
                val tile = tiles[y][x]
                targetCanvas.drawBitmap(tile.bitmap, (x * TILE_SIZE).toFloat(), (y * TILE_SIZE).toFloat(), paint)
            }
        }
    }

    fun importFromBitmap(source: Bitmap) {
        // Kanvas raksasa: lewati 64× drawBitmap per sync (tidak dibaca render).
        if (!tilesEnabled) return
        clear()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (y in 0 until numTilesY) {
            for (x in 0 until numTilesX) {
                val tile = tiles[y][x]
                val srcRect = android.graphics.Rect(
                    x * TILE_SIZE,
                    y * TILE_SIZE,
                    minOf((x + 1) * TILE_SIZE, source.width),
                    minOf((y + 1) * TILE_SIZE, source.height)
                )
                val dstRect = android.graphics.Rect(
                    0,
                    0,
                    srcRect.width(),
                    srcRect.height()
                )
                if (srcRect.left < source.width && srcRect.top < source.height) {
                    tile.canvas.drawBitmap(source, srcRect, dstRect, paint)
                    tile.isDirty = true
                }
            }
        }
    }
}
