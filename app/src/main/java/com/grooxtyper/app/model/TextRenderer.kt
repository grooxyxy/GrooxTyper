package com.grooxtyper.app.model

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Renderer teks baru: per-baris (bukan per-kata) sehingga
 * letterSpacing, align, dan lineSpacing selalu konsisten.
 * Bayangan memakai Paint.setShadowLayer agar menempel tepat
 * pada glif, bukan salinan teks yang digeser manual.
 */
object TextRenderer {

    fun render(canvas: Canvas, box: TextBox) {
        if (box.text.isEmpty()) return
        canvas.save()
        canvas.translate(box.position.x, box.position.y)
        if (box.rotation != 0f) canvas.rotate(box.rotation)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = box.fontSize * box.scale
            typeface = box.effectiveTypeface()
        }
        val fm = paint.fontMetrics
        val extra = box.letterSpacing * box.scale
        val lines = box.text.split("\n")
        val lineH = (fm.descent - fm.ascent) + box.lineSpacing * box.scale

        var contentW = 0f
        val widths = FloatArray(lines.size)
        for (i in lines.indices) {
            val w = TextBox.spacedWidth(paint, lines[i], extra)
            widths[i] = w
            if (w > contentW) contentW = w
        }
        val contentH = lineH * lines.size

        for (i in lines.indices) {
            val baseline = -contentH / 2f - fm.ascent + i * lineH
            val x0 = when (box.align) {
                TextAlignMode.LEFT -> -contentW / 2f
                TextAlignMode.CENTER -> -widths[i] / 2f
                TextAlignMode.RIGHT -> contentW / 2f - widths[i]
            }
            // 1. Outline dulu (tanpa bayangan) agar tajam.
            if (box.outlineWidth > 0f) {
                val outline = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = box.outlineWidth * box.scale
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    color = box.outlineColor
                }
                outline.clearShadowLayer()
                drawSpaced(canvas, lines[i], x0, baseline, outline, extra)
            }
            // 2. Isi + bayangan.
            val fill = Paint(paint).apply {
                style = Paint.Style.FILL
                color = box.color
            }
            box.shadow?.let { s ->
                fill.setShadowLayer(s.blur * box.scale, s.dx * box.scale, s.dy * box.scale, s.color)
            } ?: fill.clearShadowLayer()
            drawSpaced(canvas, lines[i], x0, baseline, fill, extra)
        }
        canvas.restore()
    }

    private fun drawSpaced(
        canvas: Canvas,
        line: String,
        x0: Float,
        y: Float,
        paint: Paint,
        extraPerChar: Float
    ) {
        if (line.isEmpty()) return
        if (extraPerChar == 0f) {
            canvas.drawText(line, x0, y, paint)
            return
        }
        var x = x0
        for (ch in line) {
            val s = ch.toString()
            canvas.drawText(s, x, y, paint)
            x += paint.measureText(s) + extraPerChar
        }
    }

    /** Bakar teks ke layer gambar (panggil setelah saveSnapshot untuk undo). */
    fun flatten(layer: DrawingLayer, box: TextBox) {
        val canvas = Canvas(layer.getPersistentBitmap())
        render(canvas, box)
        layer.tileMap.importFromBitmap(layer.getPersistentBitmap())
        layer.markDirty()
    }
}
