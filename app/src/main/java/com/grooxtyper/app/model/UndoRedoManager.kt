package com.grooxtyper.app.model

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class LayerStateSnapshot(
    val layerId: String,
    val bitmapSnapshot: Bitmap
)

class UndoRedoManager(private val maxHistory: Int = 15) {
    private val undoStack = mutableListOf<LayerStateSnapshot>()
    private val redoStack = mutableListOf<LayerStateSnapshot>()

    /**
     * Dinaikkan setiap mutasi stack agar tombol Undo/Redo di Compose
     * ikut recompose (canUndo()/canRedo() sendiri tidak observable).
     */
    var historyVersion by mutableIntStateOf(0)
        private set

    fun saveSnapshot(layer: DrawingLayer) {
        val copy = layer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)
        undoStack.add(LayerStateSnapshot(layer.id, copy))
        while (undoStack.size > maxHistory) {
            undoStack.removeAt(0).bitmapSnapshot.recycle()
        }
        clearStack(redoStack)
        historyVersion++
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Undo mengembalikan snapshot ke LAYER PEMILIKNYA (dicari via layerId),
     * bukan ke layer yang sedang aktif. Return false jika layer sudah dihapus.
     */
    fun undo(layerManager: LayerManager): Boolean {
        if (undoStack.isEmpty()) return false
        val snapshot = undoStack.removeAt(undoStack.lastIndex)
        val layer = layerManager.findDrawingLayerById(snapshot.layerId)
        if (layer == null) {
            snapshot.bitmapSnapshot.recycle()
            historyVersion++
            return false
        }
        redoStack.add(LayerStateSnapshot(layer.id, layer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)))
        restoreSnapshot(layer, snapshot)
        historyVersion++
        return true
    }

    fun redo(layerManager: LayerManager): Boolean {
        if (redoStack.isEmpty()) return false
        val snapshot = redoStack.removeAt(redoStack.lastIndex)
        val layer = layerManager.findDrawingLayerById(snapshot.layerId)
        if (layer == null) {
            snapshot.bitmapSnapshot.recycle()
            historyVersion++
            return false
        }
        undoStack.add(LayerStateSnapshot(layer.id, layer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)))
        restoreSnapshot(layer, snapshot)
        historyVersion++
        return true
    }

    fun clearAll() {
        clearStack(undoStack)
        clearStack(redoStack)
        historyVersion++
    }

    private fun clearStack(stack: MutableList<LayerStateSnapshot>) {
        stack.forEach { runCatching { it.bitmapSnapshot.recycle() } }
        stack.clear()
    }

    private fun restoreSnapshot(layer: DrawingLayer, snapshot: LayerStateSnapshot) {
        // compositeBitmap adalah sumber render, jadi harus dipulihkan langsung.
        val target = layer.getPersistentBitmap()
        val canvas = android.graphics.Canvas(target)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(snapshot.bitmapSnapshot, 0f, 0f, null)
        layer.tileMap.importFromBitmap(target)
        layer.markDirty()
    }
}
