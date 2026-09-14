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

class CanvasTile(val tileX: Int, val tileY: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
    val canvas: Canvas = Canvas(bitmap)
    var isDirty: Boolean = true

    fun clear() {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        isDirty = true
    }
}

class LayerTileMap(val canvasWidth: Int, val canvasHeight: Int) {
    val numTilesX: Int = ceil(canvasWidth.toDouble() / TILE_SIZE).toInt()
    val numTilesY: Int = ceil(canvasHeight.toDouble() / TILE_SIZE).toInt()
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
