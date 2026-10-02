package com.grooxtyper.app.model.selection

import android.graphics.Rect

/**
 * Satu region seleksi yang punya identitas sendiri.
 *
 * Region tidak dilebur jadi satu Path besar: tiap bubble tetap punya
 * identitas, sehingga script bisa memilih, memproses, atau menghapus
 * region per nomor.
 */
data class SelectionRegion(
    /** ID unik sepanjang sesi. */
    val id: Long,
    /** Kotak batas dalam koordinat kanvas. */
    val bounds: Rect,
    /** Mask jarang milik region ini saja. */
    val mask: SelectionTileMap
) {
    companion object {
        private var nextId = 1L

        /** ID baru yang unik. */
        @Synchronized
        fun freshId(): Long = nextId++
    }
}
