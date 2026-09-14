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
        val canvas = android.graphics.Canvas(currentLayer.getBitmap())
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(snapshot.bitmapSnapshot, 0f, 0f, null)
        currentLayer.tileMap.importFromBitmap(snapshot.bitmapSnapshot)
        currentLayer.markDirty()
    }

    fun redo(layerManager: LayerManager) {
        if (redoStack.isEmpty()) return
        val currentLayer = layerManager.getActiveLayer() ?: return

        val currentCopy = currentLayer.getBitmap().copy(Bitmap.Config.ARGB_8888, true)
        undoStack.add(LayerStateSnapshot(currentLayer.id, currentCopy))

        val snapshot = redoStack.removeAt(redoStack.size - 1)
        val canvas = android.graphics.Canvas(currentLayer.getBitmap())
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(snapshot.bitmapSnapshot, 0f, 0f, null)
        currentLayer.tileMap.importFromBitmap(snapshot.bitmapSnapshot)
        currentLayer.markDirty()
    }
}
