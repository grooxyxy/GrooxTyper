package com.grooxtyper.app.model.selection

/**
 * Satu tile seleksi: mask 0..255 per piksel.
 *
 * Nilai 0 berarti tidak terpilih, 255 terpilih penuh, 1..254 terpilih
 * sebagian (tepi anti-alias atau hasil feather). Biner cukup untuk
 * flood-fill, sedangkan feather dan anti-alias memakai rentang penuh.
 */
class SelectionTile(
    val width: Int,
    val height: Int
) {
    /** Mask baris-mayor, satu byte per piksel (baca pakai `and 0xFF`). */
    val mask: ByteArray = ByteArray(width * height)

    /** Jumlah piksel tak nol di tile ini (dijaga SelectionTileMap). */
    var count: Int = 0

    /** Nilai 0..255 pada posisi lokal, 0 bila di luar tile. */
    fun get(lx: Int, ly: Int): Int {
        if (lx !in 0 until width || ly !in 0 until height) return 0
        return mask[ly * width + lx].toInt() and 0xFF
    }

    /**
     * Tulis nilai 0..255 pada posisi lokal. Mengembalikan true bila isi
     * tile berubah, supaya peta bisa membuang tile yang kosong.
     */
    fun set(lx: Int, ly: Int, value: Int): Boolean {
        if (lx !in 0 until width || ly !in 0 until height) return false
        val idx = ly * width + lx
        val old = mask[idx].toInt() and 0xFF
        val v = value.coerceIn(0, 255)
        if (old == v) return false
        if (old == 0 && v != 0) count++
        if (old != 0 && v == 0) count--
        mask[idx] = v.toByte()
        return true
    }

    /** True bila tidak ada piksel terpilih di tile ini. */
    fun isEmpty(): Boolean = count <= 0

    /** Salinan dalam untuk riwayat dan macro. */
    fun copy(): SelectionTile {
        val out = SelectionTile(width, height)
        mask.copyInto(out.mask)
        out.count = count
        return out
    }

    /** Hitung ulang [count] setelah tulis massal langsung ke [mask]. */
    fun recount() {
        var n = 0
        for (b in mask) if ((b.toInt() and 0xFF) != 0) n++
        count = n
    }
}
