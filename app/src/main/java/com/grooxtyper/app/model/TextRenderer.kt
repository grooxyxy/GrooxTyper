package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Renderer teks: per-baris sehingga letterSpacing, wordSpacing,
 * align, dan lineSpacing selalu konsisten.
 * Bayangan memakai Paint.setShadowLayer agar menempel tepat
 * pada glif, bukan salinan teks yang digeser manual.
 */
object TextRenderer {

    private data class LineLayout(val text: String, val x0: Float, val baseline: Float)

    fun render(canvas: Canvas, box: TextBox) {
        if (box.text.isEmpty()) return
        canvas.save()
        canvas.translate(box.position.x, box.position.y)
        if (box.rotation != 0f) canvas.rotate(box.rotation)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = box.fontSize * box.scale
            typeface = box.effectiveTypeface()
            // Sempitkan horizontal (menyatukan lebar baris tanpa mematahkan kata).
            textScaleX = box.textScaleX.coerceIn(0.3f, 1f)
        }
        val fm = paint.fontMetrics
        val extra = box.letterSpacing * box.scale
        val wordExtra = box.wordSpacing * box.scale
        val lines = box.displayText().split("\n")
        val lineH = box.lineHeightPx()

        var contentW = 0f
        val widths = FloatArray(lines.size)
        for (i in lines.indices) {
            val w = TextBox.spacedWidth(paint, lines[i], extra, wordExtra)
            widths[i] = w
            if (w > contentW) contentW = w
        }
        val contentH = lineH * lines.size

        val layouts = lines.mapIndexed { i, line ->
            val baseline = -contentH / 2f - fm.ascent + i * lineH
            val x0 = when (box.align) {
                TextAlignMode.LEFT -> -contentW / 2f
                TextAlignMode.CENTER -> -widths[i] / 2f
                TextAlignMode.RIGHT -> contentW / 2f - widths[i]
            }
            LineLayout(line, x0, baseline)
        }

        val outline = if (box.outlineWidth > 0f) {
            Paint(paint).apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = box.outlineWidth * box.scale
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = withAlpha(box.outlineColor, box.strokeOpacity)
                alpha = (alpha * box.textOpacity).toInt().coerceIn(0, 255)
                clearShadowLayer()
            }
        } else null

        val fill = Paint(paint).apply {
            style = Paint.Style.FILL
            if (box.fillType == TextFillType.GRADIENT) {
                shader = gradientShader(box, contentW, contentH)
                color = Color.WHITE
            } else {
                shader = null
                color = box.color
            }
            alpha = (alpha * box.textOpacity).toInt().coerceIn(0, 255)
        }

        // Outer glow di bawah segalanya (cahaya di balik glif).
        if (box.glow != null) {
            drawOuterGlow(canvas, layouts, box, fill, extra, wordExtra)
        }

        when (box.strokePosition) {
            StrokePosition.OUTSIDE -> {
                // 1. Outline dulu (tanpa bayangan) agar tajam, 2. isi + bayangan.
                if (outline != null) {
                    for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, outline, extra, wordExtra)
                }
                drawFillWithShadow(canvas, layouts, box, fill, extra, wordExtra)
            }
            StrokePosition.CENTER -> {
                // Isi dulu, stroke menimpa tepat di tepi glif.
                drawFillWithShadow(canvas, layouts, box, fill, extra, wordExtra)
                if (outline != null) {
                    for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, outline, extra, wordExtra)
                }
            }
            StrokePosition.INSIDE -> {
                drawFillWithShadow(canvas, layouts, box, fill, extra, wordExtra)
                if (outline != null) {
                    drawInsideStroke(canvas, layouts, box, outline, contentW, contentH, extra, wordExtra)
                }
            }
        }

        // Bevel/emboss di atas isi agar tepinya terlihat.
        if (box.bevel != null) {
            drawBevel(canvas, layouts, box, fill, extra, wordExtra)
        }

        // Garis bawah / coret per baris.
        if (box.underline || box.strikethrough) {
            drawDecorations(canvas, layouts, widths, box, fill, fm)
        }
        canvas.restore()
    }

    /** Outer glow: bentuk digemukkan + shadow-layer warna cahaya. */
    private fun drawOuterGlow(
        canvas: Canvas,
        layouts: List<LineLayout>,
        box: TextBox,
        fill: Paint,
        extra: Float,
        wordExtra: Float
    ) {
        val g = box.glow ?: return
        val glowColor = withAlpha(g.color, g.opacity)
        val glowPaint = Paint(fill).apply {
            shader = null
            style = if (g.spread > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            strokeWidth = g.spread * box.scale
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
            alpha = (255 * box.textOpacity).toInt().coerceIn(0, 255)
            setShadowLayer(
                maxOf(1f, g.blur * box.scale),
                0f, 0f,
                glowColor
            )
        }
        for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, glowPaint, extra, wordExtra)
    }

    /** Bevel/emboss murah: salinan terang gelap di atas-bawah, isi menutup tengah. */
    private fun drawBevel(
        canvas: Canvas,
        layouts: List<LineLayout>,
        box: TextBox,
        fill: Paint,
        extra: Float,
        wordExtra: Float
    ) {
        val b = box.bevel ?: return
        val d = maxOf(0.5f, b.size * box.scale)
        val a = (255 * b.opacity * box.textOpacity).toInt().coerceIn(0, 255)
        val dark = Paint(fill).apply {
            shader = null
            style = Paint.Style.FILL
            strokeWidth = 0f
            color = Color.BLACK
            alpha = a
            clearShadowLayer()
        }
        val light = Paint(dark).apply { color = Color.WHITE }
        canvas.save()
        canvas.translate(d, d)
        for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, dark, extra, wordExtra)
        canvas.restore()
        canvas.save()
        canvas.translate(-d, -d)
        for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, light, extra, wordExtra)
        canvas.restore()
        // Isi normal menutup tengah agar hanya rim yang menyala.
        val clean = Paint(fill).apply {
            style = Paint.Style.FILL
            strokeWidth = 0f
            clearShadowLayer()
        }
        for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, clean, extra, wordExtra)
    }

    /** Underline & strikethrough mengikuti lebar tiap baris. */
    private fun drawDecorations(
        canvas: Canvas,
        layouts: List<LineLayout>,
        widths: FloatArray,
        box: TextBox,
        fill: Paint,
        fm: Paint.FontMetrics
    ) {
        val t = maxOf(1.5f, box.fontSize * box.scale / 16f)
        val deco = Paint(fill).apply {
            style = Paint.Style.FILL
            strokeWidth = 0f
            clearShadowLayer()
        }
        for (i in layouts.indices) {
            val l = layouts[i]
            val w = widths[i]
            if (w <= 0f) continue
            if (box.underline) {
                val y = l.baseline + fm.descent * 0.35f
                canvas.drawRect(l.x0, y - t / 2f, l.x0 + w, y + t / 2f, deco)
            }
            if (box.strikethrough) {
                val y = l.baseline + fm.ascent * 0.35f
                canvas.drawRect(l.x0, y - t / 2f, l.x0 + w, y + t / 2f, deco)
            }
        }
    }

    /** Isi teks + bayangan (mendukung opacity & spread ala Photoshop). */
    private fun drawFillWithShadow(
        canvas: Canvas,
        layouts: List<LineLayout>,
        box: TextBox,
        fill: Paint,
        extra: Float,
        wordExtra: Float
    ) {
        val s = box.shadow
        if (s == null) {
            fill.clearShadowLayer()
            fill.strokeWidth = 0f
            for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, fill, extra, wordExtra)
            return
        }
        val shadowColor = withAlpha(s.color, s.opacity)
        if (s.spread > 0f) {
            // Pass 1: bentuk digemukkan khusus untuk melebarkan bayangan,
            // Pass 2: isi normal menutupnya agar fill tidak ikut gemuk.
            val fat = Paint(fill).apply {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = s.spread * box.scale
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(s.blur * box.scale, s.dx * box.scale, s.dy * box.scale, shadowColor)
            }
            for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, fat, extra, wordExtra)
            val clean = Paint(fill).apply {
                style = Paint.Style.FILL
                strokeWidth = 0f
                clearShadowLayer()
            }
            for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, clean, extra, wordExtra)
        } else {
            fill.style = Paint.Style.FILL
            fill.strokeWidth = 0f
            fill.setShadowLayer(s.blur * box.scale, s.dx * box.scale, s.dy * box.scale, shadowColor)
            for (l in layouts) drawSpaced(canvas, l.text, l.x0, l.baseline, fill, extra, wordExtra)
        }
    }

    /**
     * Stroke DALAM: stroke di-clip ke interior glif lewat mask alpha
     * seukuran konten (bukan full-canvas) agar murah.
     */
    private fun drawInsideStroke(
        canvas: Canvas,
        layouts: List<LineLayout>,
        box: TextBox,
        strokePaint: Paint,
        contentW: Float,
        contentH: Float,
        extra: Float,
        wordExtra: Float
    ) {
        val pad = box.outlineWidth * box.scale + 4f
        val w = ceil((contentW + pad * 2).toDouble()).toInt().coerceAtLeast(1)
        val h = ceil((contentH + pad * 2).toDouble()).toInt().coerceAtLeast(1)
        val ox = contentW / 2f + pad
        val oy = contentH / 2f + pad

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        maskCanvas.translate(ox, oy)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = box.fontSize * box.scale
            typeface = box.effectiveTypeface()
            textScaleX = box.textScaleX.coerceIn(0.3f, 1f)
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        for (l in layouts) drawSpaced(maskCanvas, l.text, l.x0, l.baseline, maskPaint, extra, wordExtra)

        val strokeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val strokeCanvas = Canvas(strokeBmp)
        strokeCanvas.translate(ox, oy)
        val noShadow = Paint(strokePaint).apply { clearShadowLayer() }
        for (l in layouts) drawSpaced(strokeCanvas, l.text, l.x0, l.baseline, noShadow, extra, wordExtra)

        val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        Canvas(strokeBmp).drawBitmap(mask, 0f, 0f, dstIn)
        canvas.drawBitmap(strokeBmp, -ox, -oy, null)

        mask.recycle()
        strokeBmp.recycle()
    }

    private fun gradientShader(box: TextBox, contentW: Float, contentH: Float): Shader {
        val rad = Math.toRadians(box.gradient.angle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val half = hypot(contentW, contentH) / 2f
        return LinearGradient(
            -dx * half, -dy * half, dx * half, dy * half,
            box.gradient.colorStart, box.gradient.colorEnd,
            Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(color: Int, opacity: Float): Int {
        val a = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun drawSpaced(
        canvas: Canvas,
        line: String,
        x0: Float,
        y: Float,
        paint: Paint,
        extraPerChar: Float,
        wordExtra: Float = 0f
    ) {
        if (line.isEmpty()) return
        if (extraPerChar == 0f && wordExtra == 0f) {
            canvas.drawText(line, x0, y, paint)
            return
        }
        var x = x0
        for (ch in line) {
            val s = ch.toString()
            canvas.drawText(s, x, y, paint)
            x += paint.measureText(s) + extraPerChar
            if (ch == ' ') x += wordExtra
        }
    }

    /**
     * Render contoh bitmap untuk preview (font & style). Selalu di tengah
     * kanvas kecil dan diskalakan agar muat.
     */
    fun renderSampleBox(
        base: TextBox,
        sampleText: String,
        width: Int,
        height: Int,
        forceWhiteText: Boolean = true
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val denom = (base.fontSize * base.scale).takeIf { it > 0f } ?: 64f
        val fit = ((height * 0.52f) / denom).coerceIn(0.1f, 3f)
        val tmp = TextBox(
            text = sampleText,
            position = Offset(width / 2f, height / 2f),
            fontSize = base.fontSize,
            color = if (forceWhiteText && base.fillType == TextFillType.SOLID) Color.WHITE else base.color,
            bold = base.bold,
            italic = base.italic,
            align = TextAlignMode.CENTER,
            outlineWidth = base.outlineWidth,
            outlineColor = base.outlineColor,
            strokeOpacity = base.strokeOpacity,
            strokePosition = base.strokePosition,
            fillType = base.fillType,
            gradient = base.gradient.copy(),
            shadow = base.shadow?.copy(),
            letterSpacing = base.letterSpacing,
            wordSpacing = base.wordSpacing,
            lineSpacing = base.lineSpacing,
            textOpacity = base.textOpacity,
            uppercase = base.uppercase,
            underline = base.underline,
            strikethrough = base.strikethrough,
            glow = base.glow?.copy(),
            bevel = base.bevel?.copy(),
            scale = base.scale * fit,
            textScaleX = base.textScaleX,
            rotation = 0f,
            fontName = base.fontName,
            typeface = base.typeface
        )
        render(canvas, tmp)
        return bmp
    }

    /** Bakar teks ke layer gambar (panggil setelah saveSnapshot untuk undo). */
    fun flatten(layer: DrawingLayer, box: TextBox) {
        val canvas = Canvas(layer.getPersistentBitmap())
        render(canvas, box)
        layer.tileMap.importFromBitmap(layer.getPersistentBitmap())
        layer.markDirty()
    }
}
