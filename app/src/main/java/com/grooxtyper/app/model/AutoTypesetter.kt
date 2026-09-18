package com.grooxtyper.app.model

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset

/**
 * Auto Typesetting: mode kedua penempatan teks script.
 * Menghitung otomatis ukuran font, wrap baris, alignment, padding,
 * dan posisi agar teks pas di dalam bubble — tanpa perlu style rules.
 *
 * Dipanggil per bubble: [fit] menempatkan teks ke rect bubble dengan
 * heuristik dua sisi (persegi vs lebar-pendek ala manga) dan skor
 * keterbacaan, lalu memilih konfigurasi terbaik.
 */
object AutoTypesetter {

    private const val PADDING_FRACTION = 0.12f
    private const val MIN_FONT = 8f
    private const val MAX_FONT_RATIO = 0.42f

    /**
     * Terapkan auto typesetting ke [box] agar muat di [bubbleRect].
     * Mengubah fontSize, boxWidth (paragraph wrap), align, dan position.
     */
    fun fit(box: TextBox, bubbleRect: RectF) {
        if (bubbleRect.width() <= 8f || bubbleRect.height() <= 8f) return
        box.position = Offset(bubbleRect.centerX(), bubbleRect.centerY())
        box.rotation = 0f
        box.scale = 1f
        box.textScaleX = 1f
        box.boxWidth = null

        val pad = bubbleRect.width().coerceAtMost(bubbleRect.height()) * PADDING_FRACTION
        val usableW = (bubbleRect.width() - 2f * pad).coerceAtLeast(24f)
        val usableH = (bubbleRect.height() - 2f * pad).coerceAtLeast(24f)
        if (usableW < 24f || usableH < 24f) {
            box.fontSize = MIN_FONT
            return
        }

        // Alignment mengikuti isi: teks pendek center, teks multi-baris
        // panjang lebih nyaman dibaca rata kiri.
        box.align = if (box.text.length > 40 && box.text.contains(' ')) {
            TextAlignMode.LEFT
        } else {
            TextAlignMode.CENTER
        }

        val maxFont = (usableH * MAX_FONT_RATIO).coerceAtLeast(18f)
        var bestSize = MIN_FONT
        var bestWrap: Float? = null
        var bestScore = -1f

        // Kandidat lebar wrap: penuh (minimal wrap, bubble persegi) dan
        // 0.8x (lebih banyak baris, cocok untuk bubble lebar-pendek manga).
        val candidates = listOf(null, usableW * 0.8f)
        for (wrapW in candidates) {
            var size = maxFont
            var guard = 0
            while (guard++ < 60) {
                box.fontSize = size
                box.boxWidth = wrapW
                val (w, h) = box.contentSize()
                val fits = w <= usableW && h <= usableH
                if (fits) {
                    // Skor: font besar lebih baik; penalti bila baris terakhir
                    // terlalu pendek (ragged) karena terlihat tidak rapi.
                    val lines = box.wrappedLines()
                    var ragged = 1f
                    if (lines.size > 1 && wrapW != null) {
                        val lastW = box.contentSizeOf(lines.last())
                        val ratio = if (wrapW > 0f) lastW / wrapW else 1f
                        if (ratio < 0.4f) ragged = 0.9f
                    }
                    val score = size * ragged
                    if (score > bestScore) {
                        bestScore = score
                        bestSize = size
                        bestWrap = wrapW
                    }
                    break
                }
                size *= 0.9f
                if (size < MIN_FONT) {
                    // Bila wrap penuh tidak muat, coba kandidat berikutnya.
                    break
                }
            }
        }

        box.fontSize = bestSize
        box.boxWidth = bestWrap
        // Jaga agar teks tetap di tengah bubble setelah ukuran final.
        box.position = Offset(bubbleRect.centerX(), bubbleRect.centerY())
    }

    /** Lebar satu baris teks dengan paint milik box saat ini. */
    private fun TextBox.contentSizeOf(line: String): Float {
        val paint = basePaint()
        return TextBox.spacedWidth(paint, line, letterSpacing * scale, wordSpacing * scale)
    }
}
