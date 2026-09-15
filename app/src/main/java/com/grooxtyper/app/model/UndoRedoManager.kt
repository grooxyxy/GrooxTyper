package com.grooxtyper.app.model

import android.graphics.Bitmap

class LayerStateSnapshot(
    val layerId: String,
    val bitmapSnapshot: Bitmap
)

class UndoRedoManager(private val maxHistory: Int = 20) {
    private val undoStack = mutableListOf<LayerStateSnapshot>()
    private val redoStack = mutableListOf<LayerStateSnapshot>()

    fun saveSnapshot(layer: DrawingLayer) {
        val copy = layer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)
        undoStack.add(LayerStateSnapshot(layer.id, copy))
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(layerManager: LayerManager) {
        if (undoStack.isEmpty()) return
        val currentLayer = layerManager.getActiveLayer() ?: return

        val currentCopy = currentLayer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)
        redoStack.add(LayerStateSnapshot(currentLayer.id, currentCopy))

        val snapshot = undoStack.removeAt(undoStack.size - 1)
        restoreSnapshot(currentLayer, snapshot)
    }

    fun redo(layerManager: LayerManager) {
        if (redoStack.isEmpty()) return
        val currentLayer = layerManager.getActiveLayer() ?: return

        val currentCopy = currentLayer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)
        undoStack.add(LayerStateSnapshot(currentLayer.id, currentCopy))

        val snapshot = redoStack.removeAt(redoStack.size - 1)
        restoreSnapshot(currentLayer, snapshot)
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
