package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SelectionEngine(val width: Int, val height: Int) {
    var hasSelection by mutableStateOf(false)
    val selectionPath = Path()
    var clipboardBitmap: Bitmap? = null

    val selectionMaskBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val maskCanvas = Canvas(selectionMaskBitmap)

    fun setLassoPath(path: Path) {
        selectionPath.reset()
        selectionPath.addPath(path)
        selectionPath.close()
        hasSelection = true
        updateMaskFromPath()
    }

    fun clearSelection() {
        selectionPath.reset()
        hasSelection = false
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /** Batas seleksi aktif (untuk auto-fit teks ke bubble). Null bila tak ada seleksi. */
    fun selectionBounds(): RectF? {
        if (!hasSelection) return null
        val r = RectF()
        selectionPath.computeBounds(r, true)
        return if (r.isEmpty) null else r
    }

    /** Jadikan oval sebagai seleksi aktif (mis. dari bubble terdeteksi). */
    fun selectOval(rect: RectF) {
        selectionPath.reset()
        selectionPath.addOval(rect, Path.Direction.CW)
        hasSelection = true
        updateMaskFromPath()
    }

    private fun updateMaskFromPath() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        maskCanvas.drawPath(selectionPath, fillPaint)
    }

    fun invertSelection() {
        if (!hasSelection) return
        val invertedBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(invertedBmp)
        invCanvas.drawColor(Color.WHITE)

        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        invCanvas.drawPath(selectionPath, erasePaint)

        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        maskCanvas.drawBitmap(invertedBmp, 0f, 0f, null)

        val rectPath = Path()
        rectPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        rectPath.op(selectionPath, Path.Op.DIFFERENCE)
        selectionPath.set(rectPath)
    }

    fun clearSelectedArea(layer: DrawingLayer) {
        if (!hasSelection) return
        val bmp = layer.getBitmap()
        val canvas = Canvas(bmp)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, clearPaint)
        layer.markDirty()
    }

    fun copySelectedArea(layer: DrawingLayer): Bitmap? {
        if (!hasSelection) return null
        val layerBmp = layer.getBitmap()
        val copyBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(copyBmp)
        canvas.drawBitmap(layerBmp, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, maskPaint)
        clipboardBitmap = copyBmp
        return copyBmp
    }

    fun cutSelectedArea(layer: DrawingLayer): Bitmap? {
        val copied = copySelectedArea(layer)
        if (copied != null) {
            clearSelectedArea(layer)
        }
        return copied
    }

    fun pasteToNewLayer(layerManager: LayerManager): DrawingLayer? {
        val clip = clipboardBitmap ?: return null
        val newLayer = layerManager.addLayer("Pasted Layer")
        val layerBmp = newLayer.getBitmap()
        val canvas = Canvas(layerBmp)
        canvas.drawBitmap(clip, 0f, 0f, null)
        newLayer.markDirty()
        return newLayer
    }
}
