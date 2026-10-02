package com.grooxtyper.app.model.selection

import android.graphics.Rect

/**
 * Hasil wand: peta seleksi jarang + batas + jumlah piksel.
 *
 * Path TIDAK disimpan di sini. Path hanya representasi untuk rendering
 * (dibuat dari peta lewat MaskContour saat dibutuhkan), sedangkan peta
 * adalah sumber kebenaran.
 */
data class WandResult(
    /** Mask jarang hasil flood fill. */
    val selection: SelectionTileMap,
    /** Kotak batas dalam koordinat kanvas. */
    val bounds: Rect,
    /** Jumlah piksel terpilih. */
    val pixelCount: Long,
    /** Region hasil pemisahan bubble, kosong bila satu wilayah utuh. */
    val regions: List<SelectionRegion> = emptyList(),
    /** Kepercayaan batas bubble 0..1, null bila wand biasa. */
    val bubbleConfidence: Float? = null
)
