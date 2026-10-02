package com.grooxtyper.app.model.selection

import android.graphics.Rect

/**
 * Metadata bubble hasil seleksi untuk script dan auto-layout teks.
 *
 * Klasifikasi bentuk kasar (kotak narasi, bubble bulat, bubble pikiran)
 * bisa ditambahkan kemudian oleh model AI; struktur ini sudah siap.
 */
data class BubbleInfo(
    /** Kotak batas dalam koordinat kanvas. */
    val bounds: Rect,
    /** Jumlah piksel terpilih. */
    val pixelCount: Long,
    /** Lebar dibagi tinggi, untuk memilih layout teks. */
    val aspectRatio: Float,
    /** Banyak region di dalam hasil ini. */
    val regionCount: Int,
    /** Kepercayaan 0..1 bahwa ini bubble tertutup. */
    val confidence: Float
) {
    companion object {
        /** Bangun info dari hasil wand. */
        fun from(result: WandResult): BubbleInfo {
            val b = result.bounds
            val w = (b.width()).toFloat().coerceAtLeast(1f)
            val h = (b.height()).toFloat().coerceAtLeast(1f)
            return BubbleInfo(
                bounds = Rect(b),
                pixelCount = result.pixelCount,
                aspectRatio = w / h,
                regionCount = if (result.regions.isEmpty()) 1 else result.regions.size,
                confidence = result.bubbleConfidence ?: 0.5f
            )
        }
    }
}
