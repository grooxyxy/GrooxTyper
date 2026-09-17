package com.grooxtyper.app.model

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Properti display layer (untuk undo toggle/rename). */
data class LayerProps(
    val name: String,
    val isVisible: Boolean,
    val opacity: Float,
    val blendMode: LayerBlendMode,
    val isAlphaLocked: Boolean,
    val isClippingMask: Boolean
) {
    companion object {
        fun of(layer: LayerItem): LayerProps = LayerProps(
            name = layer.name,
            isVisible = layer.isVisible,
            opacity = layer.opacity,
            blendMode = layer.blendMode,
            isAlphaLocked = layer.isAlphaLocked,
            isClippingMask = layer.isClippingMask
        )
    }

    fun applyTo(layer: LayerItem) {
        layer.name = name
        layer.isVisible = isVisible
        layer.opacity = opacity
        layer.blendMode = blendMode
        layer.isAlphaLocked = isAlphaLocked
        layer.isClippingMask = isClippingMask
    }
}

/** Satu langkah history untuk SEMUA action (cat/lukis/teks/layer). */
sealed interface HistoryEntry {
    /**
     * Isi bitmap SEBELUM action cat (import/stroke/lasso/inpaint/flip/flatten).
     *
     * Performa/memori untuk kanvas jangkung 720x16000 (~46MB/bitmap mentah):
     * snapshot besar TIDAK disalin mentah, melainkan dikompresi PNG sekali
     * (~1-4MB untuk halaman manga) lalu bitmap sumber dibiarkan. Snapshot
     * di-materialize kembali (decode) hanya saat undo/redo benar-benar
     * dipanggil. Kanvas kecil tetap menyalin bitmap (lebih cepat).
     */
    class BitmapEntry(val layerId: String, source: Bitmap) : HistoryEntry {
        @Volatile private var liveBitmap: Bitmap? = null
        @Volatile private var pngBytes: ByteArray? = null
        private val w = source.width
        private val h = source.height

        // Thread encoder background (null di kanvas kecil). Dideklarasikan
        // SEBELUM init agar assignment di init tidak tertimpa initializer.
        @Volatile private var encodeThread: Thread? = null

        init {
            if (w.toLong() * h.toLong() > HUGE_CANVAS_PIXELS) {
                // Kanvas jangkung 720x16000: kompresi PNG 11,5MP di UI thread
                // memakan 1-3 detik = freeze NYATA di awal sapuan brush
                // (saveSnapshot dipanggil saat ACTION_DOWN). Pindahkan ke
                // background daemon; undo dipaksa menunggu bila encoding
                // belum selesai (sinkron, aman terhadap mutasi bitmap sumber).
                val src = source
                val t = Thread {
                    val out = java.io.ByteArrayOutputStream(1 shl 20)
                    try {
                        src.compress(Bitmap.CompressFormat.PNG, 100, out)
                        pngBytes = out.toByteArray()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: tetap salin mentah bila kompresi gagal.
                        runCatching { liveBitmap = src.copy(Bitmap.Config.ARGB_8888, true) }
                    } catch (e: OutOfMemoryError) {
                        e.printStackTrace()
                        liveBitmap = null // biarkan null; materialize() gagal aman
                    }
                }
                t.isDaemon = true
                t.priority = Thread.MIN_PRIORITY
                t.name = "GrooxUndoEncoder"
                encodeThread = t
                t.start()
            } else {
                liveBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            }
        }

        /** Pastikan encoding background selesai sebelum baca hasil. */
        private fun awaitEncode() {
            val t = encodeThread ?: return
            if (t.isAlive) {
                try { t.join(10_000) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            encodeThread = null
        }

        /** Bitmap siap pakai (decode bila disimpan terkompresi). */
        fun materialize(): Bitmap? {
            awaitEncode()
            liveBitmap?.let { return it }
            val bytes = pngBytes ?: return null
            return try {
                val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                decoded?.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun recycle() {
            awaitEncode()
            runCatching { liveBitmap?.recycle() }
            liveBitmap = null
            pngBytes = null
        }

        /** Lepas referensi tanpa join (aman dipanggil dari UI thread). */
        fun recycleAsync() {
            encodeThread = null
            runCatching { liveBitmap?.recycle() }
            liveBitmap = null
            pngBytes = null
        }
    }

    /** Isi TextBox SEBELUM diubah (geser/skala/putar/edit panel). */
    data class TextBoxEntry(val layerId: String, val box: TextBox) : HistoryEntry

    /** Layer baru ditambahkan (teks dibuat / layer baru / paste). Undo = hapus lagi. */
    data class LayerAddEntry(val layerId: String) : HistoryEntry

    /** Layer dihapus (objek dipertahankan agar bisa dikembalikan utuh). */
    data class LayerRemoveEntry(val layer: LayerItem, val index: Int) : HistoryEntry

    /** Layer dipindah (reorder). Indeks valid karena undo selalu LIFO. */
    data class LayerMoveEntry(val layerId: String, val from: Int, val to: Int) : HistoryEntry

    /** Properti layer diubah (nama/visibilitas/opacity/blend/kunci). */
    data class LayerPropEntry(val layerId: String, val before: LayerProps, val after: LayerProps) : HistoryEntry

    /** Transform image layer SEBELUM diubah (geser/skala/putar/panel). */
    data class ImageTransformEntry(val layerId: String, val before: ImageTransform) : HistoryEntry
}

class UndoRedoManager(private val maxHistory: Int = 15) {
    private val undoStack = mutableListOf<HistoryEntry>()
    private val redoStack = mutableListOf<HistoryEntry>()

    /**
     * Budget memori undo: kanvas jangkung 720x16000 = ~46MB/snapshot,
     * 15 snapshot = ~690MB → OOM. Batasi adaptif berdasar ukuran bitmap.
     */
    private fun maxBitmapEntriesFor(pixels: Long): Int {
        return when {
            pixels > 12_000_000L -> 2
            pixels > 8_000_000L -> 5
            pixels > 4_000_000L -> 8
            else -> maxHistory
        }
    }

    private fun trimBitmapHistory(pixels: Long) {
        val cap = maxBitmapEntriesFor(pixels)
        var count = undoStack.count { it is HistoryEntry.BitmapEntry }
        while (count > cap) {
            val idx = undoStack.indexOfFirst { it is HistoryEntry.BitmapEntry }
            if (idx < 0) break
            // Jangan join di thread pemanggil (sering UI thread saat ACTION_DOWN):
            // join 10 detik = brush "mati awal". Lepas referensi saja; thread
            // encoder menyelesaikan tulisannya sendiri lalu di-GC.
            (undoStack.removeAt(idx) as? HistoryEntry.BitmapEntry)?.recycleAsync()
            count--
        }
    }

    /**
     * Dinaikkan setiap mutasi stack agar tombol Undo/Redo di Compose
     * ikut recompose (isi stack sendiri tidak observable).
     */
    var historyVersion by mutableIntStateOf(0)
        private set

    // ---------- push ----------

    /** Snapshot bitmap sebelum action cat. Kanvas raksasa dikompresi PNG. */
    fun saveSnapshot(layer: DrawingLayer) {
        val bmp = layer.getBitmap()
        val pixels = bmp.width.toLong() * bmp.height.toLong()
        // Batasi adaptif dulu agar snapshot tidak menumpuk hingga OOM.
        trimBitmapHistory(pixels)
        push(HistoryEntry.BitmapEntry(layer.id, bmp))
    }

    /** Snapshot isi teks sebelum diubah. */
    fun pushTextBox(layerId: String, before: TextBox) {
        push(HistoryEntry.TextBoxEntry(layerId, before))
    }

    /** Snapshot transform image sebelum diubah. */
    fun pushImageTransform(layerId: String, before: ImageTransform) {
        push(HistoryEntry.ImageTransformEntry(layerId, before))
    }

    fun pushLayerAdd(layerId: String) {
        push(HistoryEntry.LayerAddEntry(layerId))
    }

    fun pushLayerRemove(layer: LayerItem, index: Int) {
        push(HistoryEntry.LayerRemoveEntry(layer, index.coerceAtLeast(0)))
    }

    fun pushLayerMove(layerId: String, from: Int, to: Int) {
        if (from == to) return
        push(HistoryEntry.LayerMoveEntry(layerId, from, to))
    }

    fun pushLayerProps(layerId: String, before: LayerProps, after: LayerProps) {
        if (before == after) return
        push(HistoryEntry.LayerPropEntry(layerId, before, after))
    }

    private fun push(entry: HistoryEntry) {
        undoStack.add(entry)
        while (undoStack.size > maxHistory) {
            (undoStack.removeAt(0) as? HistoryEntry.BitmapEntry)?.recycleAsync()
        }
        clearStack(redoStack)
        historyVersion++
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ---------- undo / redo ----------

    fun undo(layerManager: LayerManager): Boolean {
        if (undoStack.isEmpty()) return false
        val entry = undoStack.removeAt(undoStack.lastIndex)
        val ok = when (entry) {
            is HistoryEntry.BitmapEntry -> {
                val layer = layerManager.findDrawingLayerById(entry.layerId) ?: return false
                redoStack.add(HistoryEntry.BitmapEntry(layer.id, layer.getBitmap()))
                val snap = entry.materialize() ?: return false
                restoreBitmap(layer, snap)
                runCatching { snap.recycle() }
                true
            }
            is HistoryEntry.TextBoxEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) as? TextLayer
                    ?: return false
                redoStack.add(HistoryEntry.TextBoxEntry(layer.id, layer.box.copy()))
                layer.box.setFrom(entry.box)
                true
            }
            is HistoryEntry.ImageTransformEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) as? ImageLayer
                    ?: return false
                redoStack.add(HistoryEntry.ImageTransformEntry(layer.id, ImageTransform.of(layer)))
                entry.before.applyTo(layer)
                true
            }
            is HistoryEntry.LayerAddEntry -> {
                val idx = layerManager.indexOfLayer(entry.layerId)
                if (idx < 0) return false
                val removed = layerManager.removeLayerById(entry.layerId) ?: return false
                redoStack.add(HistoryEntry.LayerRemoveEntry(removed, idx))
                true
            }
            is HistoryEntry.LayerRemoveEntry -> {
                layerManager.insertLayerAt(entry.index, entry.layer)
                redoStack.add(HistoryEntry.LayerAddEntry(entry.layer.id))
                true
            }
            is HistoryEntry.LayerMoveEntry -> {
                if (!layerManager.moveLayerTo(entry.layerId, entry.from)) return false
                redoStack.add(entry)
                true
            }
            is HistoryEntry.LayerPropEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) ?: return false
                entry.before.applyTo(layer)
                redoStack.add(entry)
                true
            }
        }
        historyVersion++
        return ok
    }

    fun redo(layerManager: LayerManager): Boolean {
        if (redoStack.isEmpty()) return false
        val entry = redoStack.removeAt(redoStack.lastIndex)
        val ok = when (entry) {
            is HistoryEntry.BitmapEntry -> {
                val layer = layerManager.findDrawingLayerById(entry.layerId) ?: return false
                undoStack.add(HistoryEntry.BitmapEntry(layer.id, layer.getBitmap()))
                val snap = entry.materialize() ?: return false
                restoreBitmap(layer, snap)
                runCatching { snap.recycle() }
                true
            }
            is HistoryEntry.TextBoxEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) as? TextLayer
                    ?: return false
                undoStack.add(HistoryEntry.TextBoxEntry(layer.id, layer.box.copy()))
                layer.box.setFrom(entry.box)
                true
            }
            is HistoryEntry.ImageTransformEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) as? ImageLayer
                    ?: return false
                undoStack.add(HistoryEntry.ImageTransformEntry(layer.id, ImageTransform.of(layer)))
                entry.before.applyTo(layer)
                true
            }
            is HistoryEntry.LayerAddEntry -> {
                val idx = layerManager.indexOfLayer(entry.layerId)
                if (idx < 0) return false
                val removed = layerManager.removeLayerById(entry.layerId) ?: return false
                undoStack.add(HistoryEntry.LayerRemoveEntry(removed, idx))
                true
            }
            is HistoryEntry.LayerRemoveEntry -> {
                layerManager.insertLayerAt(entry.index, entry.layer)
                undoStack.add(HistoryEntry.LayerAddEntry(entry.layer.id))
                true
            }
            is HistoryEntry.LayerMoveEntry -> {
                if (!layerManager.moveLayerTo(entry.layerId, entry.to)) return false
                undoStack.add(entry)
                true
            }
            is HistoryEntry.LayerPropEntry -> {
                val layer = layerManager.findLayerById(entry.layerId) ?: return false
                entry.after.applyTo(layer)
                undoStack.add(entry)
                true
            }
        }
        trimUndo()
        historyVersion++
        return ok
    }

    fun clearAll() {
        clearStack(undoStack)
        clearStack(redoStack)
        historyVersion++
    }

    private fun trimUndo() {
        while (undoStack.size > maxHistory) {
            (undoStack.removeAt(0) as? HistoryEntry.BitmapEntry)?.recycleAsync()
        }
    }

    private fun clearStack(stack: MutableList<HistoryEntry>) {
        stack.forEach { (it as? HistoryEntry.BitmapEntry)?.recycleAsync() }
        stack.clear()
    }

    private fun restoreBitmap(layer: DrawingLayer, snapshot: Bitmap) {
        // compositeBitmap adalah sumber render, jadi harus dipulihkan langsung.
        val target = layer.getPersistentBitmap()
        val canvas = android.graphics.Canvas(target)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(snapshot, 0f, 0f, null)
        layer.tileMap.importFromBitmap(target)
        layer.markDirty()
    }
}
