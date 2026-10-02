package com.grooxtyper.app.model.selection

/**
 * Perintah kanvas: UI, script, dan macro menjalankan mesin perintah yang
 * sama, bukan tiga jalur berbeda. Dengan begitu hasil ketukan di layar
 * dan hasil panggilan script selalu konsisten.
 */
sealed interface CanvasCommand {

    /** Pilih wilayah lewat wand di titik kanvas. */
    data class WandSelectCommand(
        val x: Int,
        val y: Int,
        val tolerance: Int = 32,
        val contiguous: Boolean = true,
        val bubbleAware: Boolean = false,
        val antialias: Boolean = true,
        val mode: SelectionMode = SelectionMode.NEW
    ) : CanvasCommand

    /** Tambah seleksi yang sedang ada (gabungan). */
    data class AddSelectionCommand(val x: Int, val y: Int, val tolerance: Int = 32) : CanvasCommand

    /** Kurangi seleksi dengan wilayah wand di titik ini. */
    data class SubtractSelectionCommand(val x: Int, val y: Int, val tolerance: Int = 32) : CanvasCommand

    /** Iris seleksi dengan wilayah wand di titik ini. */
    data class IntersectSelectionCommand(val x: Int, val y: Int, val tolerance: Int = 32) : CanvasCommand

    /** Hapus seluruh seleksi. */
    data object ClearSelectionCommand : CanvasCommand

    /** Isi seleksi dengan warna ARGB. */
    data class FillSelectionCommand(val color: Int) : CanvasCommand

    /** Hapus piksel di dalam seleksi pada layer aktif. */
    data object DeleteSelectionCommand : CanvasCommand

    /** Lebarkan seleksi sejauh [pixels] piksel. */
    data class ExpandSelectionCommand(val pixels: Int) : CanvasCommand

    /** Sempitkan seleksi sejauh [pixels] piksel. */
    data class ContractSelectionCommand(val pixels: Int) : CanvasCommand

    /** Samarkan tepi seleksi sejauh [radius] piksel. */
    data class FeatherSelectionCommand(val radius: Int) : CanvasCommand
}
