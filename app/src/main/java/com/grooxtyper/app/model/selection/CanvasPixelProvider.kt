package com.grooxtyper.app.model.selection

/**
 * Penyedia piksel kanvas untuk mesin wand.
 *
 * Abstraksi ini yang membuat wand tidak peduli piksel berasal dari layer,
 * tile, composite, bitmap, atau cache. Mesin wand hanya meminta
 * persegi yang dibutuhkan, tidak pernah seluruh kanvas.
 */
interface CanvasPixelProvider {
    /** Lebar kanvas dalam piksel. */
    val canvasWidth: Int

    /** Tinggi kanvas dalam piksel. */
    val canvasHeight: Int

    /** Satu piksel ARGB pada koordinat kanvas. */
    fun getPixel(x: Int, y: Int): Int

    /**
     * Blok piksel ARGB baris-mayor selebar [width].
     *
     * Koordinat di luar kanvas diisi transparan (0), jadi pemanggil tidak
     * perlu memotong manual di tepi kanvas.
     */
    fun getPixels(x: Int, y: Int, width: Int, height: Int): IntArray
}
