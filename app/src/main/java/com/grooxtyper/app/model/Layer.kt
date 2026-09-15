package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class LayerBlendMode(val displayName: String, val porterDuffMode: PorterDuff.Mode) {
    NORMAL("Normal", PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", PorterDuff.Mode.SCREEN),
    DARKEN("Darken", PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", PorterDuff.Mode.LIGHTEN),
    ADD("Add", PorterDuff.Mode.ADD),
    CLEAR("Clear", PorterDuff.Mode.CLEAR),
    SRC_ATOP("Alpha Clip", PorterDuff.Mode.SRC_ATOP),
    XOR("XOR", PorterDuff.Mode.XOR)
}

open class LayerItem(
    var name: String = "Layer",
    var isFolder: Boolean = false,
    val id: String = UUID.randomUUID().toString()
) {
    var isVisible by mutableStateOf(true)
    var opacity by mutableFloatStateOf(1.0f)
    var blendMode by mutableStateOf(LayerBlendMode.NORMAL)
    var isAlphaLocked by mutableStateOf(false)
    var isClippingMask by mutableStateOf(false)
    val children = mutableStateListOf<LayerItem>()
}

class DrawingLayer(
    val width: Int,
    val height: Int,
    name: String = "Layer"
) : LayerItem(name = name, isFolder = false) {

    val tileMap = LayerTileMap(width, height)
    var compositeBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    fun getPersistentBitmap(): Bitmap {
        return compositeBitmap
    }

    fun getBitmap(): Bitmap {
        return compositeBitmap
    }

    fun markDirty() {
        // Bitmap updated directly
    }

    fun clear() {
        tileMap.clear()
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    fun flipHorizontal() {
        val current = compositeBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = android.graphics.Matrix().apply {
            postScale(-1f, 1f, width / 2f, height / 2f)
        }
        val flipped = Bitmap.createBitmap(current, 0, 0, width, height, matrix, true)
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(flipped, 0f, 0f, null)
        tileMap.importFromBitmap(compositeBitmap)
    }

    fun flipVertical() {
        val current = compositeBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = android.graphics.Matrix().apply {
            postScale(1f, -1f, width / 2f, height / 2f)
        }
        val flipped = Bitmap.createBitmap(current, 0, 0, width, height, matrix, true)
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(flipped, 0f, 0f, null)
        tileMap.importFromBitmap(compositeBitmap)
    }
}

class TextLayer(
    val box: TextBox,
    name: String = "Text: ${box.text.take(16)}"
) : LayerItem(name = name, isFolder = false)

class LayerManager(val width: Int, val height: Int) {
    val layers = mutableStateListOf<LayerItem>()
    var activeLayerId by mutableStateOf<String>("")

    init {
        val initialLayer = DrawingLayer(width, height, "Layer 1")
        layers.add(initialLayer)
        activeLayerId = initialLayer.id
    }

    fun getActiveLayer(): DrawingLayer? {
        fun findLayer(items: List<LayerItem>): DrawingLayer? {
            for (item in items) {
                if (item.id == activeLayerId && item is DrawingLayer) return item
                if (item.isFolder) {
                    val found = findLayer(item.children)
                    if (found != null) return found
                }
            }
            return null
        }
        val active = findLayer(layers)
        if (active != null) return active

        // Fallback: If activeLayerId is a TextLayer, find the first available DrawingLayer
        for (item in layers) {
            if (item is DrawingLayer) {
                activeLayerId = item.id
                return item
            }
        }
        return null
    }

    fun ensureDrawingLayer(): DrawingLayer {
        val current = getActiveLayer()
        if (current != null) return current
        val newLayer = DrawingLayer(width, height, "Layer ${layers.size + 1}")
        layers.add(0, newLayer)
        activeLayerId = newLayer.id
        return newLayer
    }

    fun addLayer(name: String = "Layer ${layers.size + 1}"): DrawingLayer {
        val layer = DrawingLayer(width, height, name)
        layers.add(0, layer)
        activeLayerId = layer.id
        return layer
    }

    fun addTextLayer(box: TextBox): TextLayer {
        val textLayer = TextLayer(box, name = "Text: ${box.text.take(16)}")
        layers.add(0, textLayer)
        activeLayerId = textLayer.id
        return textLayer
    }

    fun addFolder(name: String = "Folder ${layers.size + 1}"): LayerItem {
        val folder = LayerItem(name = name, isFolder = true)
        layers.add(0, folder)
        return folder
    }

    fun deleteLayer(id: String) {
        fun removeRec(items: MutableList<LayerItem>): Boolean {
            val it = items.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.id == id) {
                    it.remove()
                    return true
                }
                if (item.isFolder && removeRec(item.children)) return true
            }
            return false
        }
        removeRec(layers)
        if (layers.isNotEmpty() && (getActiveLayer() == null)) {
            val first = layers.firstOrNull()
            if (first is DrawingLayer) activeLayerId = first.id
        }
    }

    fun moveLayerUp(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = layers.removeAt(index)
            layers.add(index - 1, item)
        }
    }

    fun moveLayerDown(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index >= 0 && index < layers.size - 1) {
            val item = layers.removeAt(index)
            layers.add(index + 1, item)
        }
    }

    fun renderComposite(targetBitmap: Bitmap) {
        val canvas = Canvas(targetBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val flatLayers = mutableListOf<LayerItem>()
        fun collectLayers(items: List<LayerItem>) {
            for (item in items) {
                if (item.isVisible) {
                    if (item is DrawingLayer || item is TextLayer) {
                        flatLayers.add(item)
                    } else if (item.isFolder) {
                        collectLayers(item.children)
                    }
                }
            }
        }
        collectLayers(layers)

        var baseMaskBitmap: Bitmap? = null

        for (i in flatLayers.indices.reversed()) {
            val layer = flatLayers[i]
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = (layer.opacity * 255).toInt()
            paint.xfermode = PorterDuffXfermode(layer.blendMode.porterDuffMode)

            if (layer is DrawingLayer) {
                val bmp = layer.getBitmap()
                if (layer.isClippingMask && baseMaskBitmap != null) {
                    val tempLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val tempCanvas = Canvas(tempLayer)
                    tempCanvas.drawBitmap(bmp, 0f, 0f, null)

                    val clipPaint = Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    tempCanvas.drawBitmap(baseMaskBitmap, 0f, 0f, clipPaint)
                    canvas.drawBitmap(tempLayer, 0f, 0f, paint)
                    tempLayer.recycle()
                } else {
                    canvas.drawBitmap(bmp, 0f, 0f, paint)
                    if (!layer.isClippingMask) {
                        baseMaskBitmap = bmp
                    }
                }
            } else if (layer is TextLayer) {
                // Jalur cepat: tanpa bitmap intermediate saat opacity penuh & blend normal.
                if (layer.opacity >= 1f && layer.blendMode == LayerBlendMode.NORMAL) {
                    TextRenderer.render(canvas, layer.box)
                } else {
                    val textBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val textCanvas = Canvas(textBmp)
                    TextRenderer.render(textCanvas, layer.box)
                    canvas.drawBitmap(textBmp, 0f, 0f, paint)
                    textBmp.recycle()
                }
            }
        }
    }
}
