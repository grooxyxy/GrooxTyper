package com.grooxtyper.app.model.selection

import android.graphics.Rect

/**
 * Riwayat seleksi untuk undo, redo, script, dan macro.
 *
 * Tiap operasi menyimpan salinan peta (jarang, jadi murah) beserta
 * label yang bisa dibaca manusia seperti "Bubble A" atau "Tambah D".
 */
class SelectionHistory(
    private val cap: Int = 50
) {
    /** Satu langkah riwayat. */
    data class Entry(
        val id: Long,
        val label: String,
        val map: SelectionTileMap,
        val bounds: Rect?,
        val pixelCount: Long,
        val timeMs: Long = System.currentTimeMillis()
    )

    private val items = ArrayList<Entry>()
    private var nextId = 1L

    /** Dorong keadaan seleksi saat ini dengan label. */
    @Synchronized
    fun push(label: String, map: SelectionTileMap): Entry {
        val bounds = map.getBounds()
        val e = Entry(nextId++, label, map.deepCopy(), bounds?.let { Rect(it) }, map.pixelCount())
        items.add(e)
        while (items.size > cap) items.removeAt(0)
        return e
    }

    /** Entri terakhir, null bila kosong. */
    @Synchronized
    fun current(): Entry? = items.lastOrNull()

    /** Entri sebelum terakhir, null bila kurang dari dua. */
    @Synchronized
    fun previous(): Entry? = if (items.size >= 2) items[items.size - 2] else null

    /** Entri ke-[index], null bila di luar rentang. */
    @Synchronized
    fun get(index: Int): Entry? = items.getOrNull(index)

    /** Banyak langkah tersimpan. */
    @Synchronized
    fun size(): Int = items.size

    /** Buang entri terakhir, kembalikan sisanya yang kini terakhir. */
    @Synchronized
    fun pop(): Entry? {
        if (items.isEmpty()) return null
        items.removeAt(items.lastIndex)
        return items.lastOrNull()
    }

    /** Kosongkan riwayat. */
    @Synchronized
    fun clear() {
        items.clear()
    }

    /** Salinan daftar untuk dibaca script. */
    @Synchronized
    fun list(): List<Entry> = ArrayList(items)
}
