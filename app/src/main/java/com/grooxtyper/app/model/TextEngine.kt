package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import java.io.File

class StackableTextConfig(
    var text: String = "GrooxTyper",
    var fontSize: Float = 56f,
    var textColor: Int = Color.WHITE,

    // 1. Outer Stroke / Outline (px, color, opacity)
    var hasOutline: Boolean = true,
    var outlineColor: Int = Color.BLACK,
    var outlineWidthPx: Float = 10f,
    var outlineOpacity: Float = 1.0f,

    // 2. Drop Shadow (px distance, px blur, opacity, color)
    var hasShadow: Boolean = true,
    var shadowColor: Int = Color.parseColor("#80000000"),
    var shadowRadiusPx: Float = 12f,
    var shadowDxPx: Float = 8f,
    var shadowDyPx: Float = 8f,
    var shadowOpacity: Float = 0.8f,

    // 3. Inner Shadow (px distance, px size, opacity, color)
    var hasInnerShadow: Boolean = false,
    var innerShadowColor: Int = Color.BLACK,
    var innerShadowDistancePx: Float = 6f,
    var innerShadowSizePx: Float = 8f,
    var innerShadowOpacity: Float = 0.7f,

    // 4. Outer Glow (px radius, opacity, color)
    var hasOuterGlow: Boolean = false,
    var outerGlowColor: Int = Color.YELLOW,
    var outerGlowRadiusPx: Float = 20f,
    var outerGlowOpacity: Float = 0.9f,

    // 5. Inner Glow (px spread size, opacity, color)
    var hasInnerGlow: Boolean = false,
    var innerGlowColor: Int = Color.CYAN,
    var innerGlowSizePx: Float = 10f,
    var innerGlowOpacity: Float = 0.8f,

    // 6. Linear Gradient Overlay (start/end color, angle)
    var hasGradient: Boolean = false,
    var gradientStartColor: Int = Color.RED,
    var gradientEndColor: Int = Color.YELLOW,

    // 7. Background Banner Frame (px corner radius, px padding, color)
    var hasBackgroundBanner: Boolean = false,
    var backgroundColor: Int = Color.parseColor("#99000000"),
    var backgroundCornerRadiusPx: Float = 16f,
    var backgroundPaddingPx: Float = 20f,

    // Spacing Metrics strictly in PX (Pixel)
    var wordSpacingPx: Float = 0.0f,      // Spasi antar kata (px)
    var letterSpacingPx: Float = 0.0f,    // Spasi antar huruf (px)
    var lineSpacingPx: Float = 10.0f,     // Jarak spasi antar baris atas-bawah (px)

    var blurRadius: Float = 0f,
    var fontName: String = "Default Bold",
    var typeface: Typeface = Typeface.DEFAULT_BOLD
)

class TextItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var config: StackableTextConfig = StackableTextConfig(),
    var position: Offset = Offset(200f, 300f),
    var scale: Float = 1.0f,
    var rotationAngle: Float = 0f
) {
    fun getBounds(): RectF {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.fontSize * scale
            typeface = config.typeface
        }
        val lines = config.text.split("\n")
        var maxW = 0f
        for (line in lines) {
            val w = paint.measureText(line) + (line.length * (config.letterSpacingPx + config.wordSpacingPx) * scale)
            if (w > maxW) maxW = w
        }
        val fm = paint.fontMetrics
        val h = ((fm.descent - fm.ascent) + config.lineSpacingPx) * lines.size * scale
        val padding = (config.outlineWidthPx + config.shadowRadiusPx + config.outerGlowRadiusPx + 32f) * scale
        return RectF(
            position.x - padding,
            position.y + fm.ascent * scale - padding,
            position.x + maxW + padding,
            position.y + h + padding
        )
    }

    fun isHit(p: Offset): Boolean {
        if (rotationAngle == 0f) {
            return getBounds().contains(p.x, p.y)
        }
        val dx = p.x - position.x
        val dy = p.y - position.y
        val rad = -Math.toRadians(rotationAngle.toDouble())
        val rx = (dx * Math.cos(rad) - dy * Math.sin(rad)).toFloat() + position.x
        val ry = (dx * Math.sin(rad) + dy * Math.cos(rad)).toFloat() + position.y
        return getBounds().contains(rx, ry)
    }
}

class FontManager(private val context: Context) {
    private val customFontDir = File(context.filesDir, "custom_fonts")

    init {
        if (!customFontDir.exists()) {
            customFontDir.mkdirs()
        }
    }

    fun getAvailableFonts(): List<Pair<String, Typeface>> {
        val fonts = mutableListOf<Pair<String, Typeface>>(
            "Default Bold" to Typeface.DEFAULT_BOLD,
            "Default Normal" to Typeface.DEFAULT,
            "Serif" to Typeface.SERIF,
            "Monospace" to Typeface.MONOSPACE,
            "Sans Serif" to Typeface.SANS_SERIF
        )

        customFontDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".ttf") || file.name.endsWith(".otf")) {
                try {
                    val tf = Typeface.createFromFile(file)
                    fonts.add(file.nameWithoutExtension to tf)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return fonts
    }

    fun importFontFile(inputStream: java.io.InputStream, fileName: String): Typeface? {
        val destFile = File(customFontDir, fileName)
        destFile.outputStream().use { out -> inputStream.copyTo(out) }
        return try {
            Typeface.createFromFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class TextEngine {

    fun renderTextToCanvas(canvas: Canvas, config: StackableTextConfig, position: Offset, scale: Float = 1.0f, rotationAngle: Float = 0f) {
        canvas.save()
        if (rotationAngle != 0f) {
            canvas.rotate(rotationAngle, position.x, position.y)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.fontSize * scale
            typeface = config.typeface
        }

        val lines = config.text.split("\n")
        val fontMetrics = paint.fontMetrics
        val baseLineHeight = (fontMetrics.descent - fontMetrics.ascent) * scale
        val lineHeight = baseLineHeight + (config.lineSpacingPx * scale)

        // Draw Photoshop Background Banner
        if (config.hasBackgroundBanner) {
            var maxW = 0f
            for (l in lines) {
                val w = paint.measureText(l) + (l.length * config.letterSpacingPx * scale)
                if (w > maxW) maxW = w
            }
            val totalH = lineHeight * lines.size
            val bgRect = RectF(
                position.x - config.backgroundPaddingPx * scale,
                position.y + fontMetrics.ascent * scale - (config.backgroundPaddingPx / 2f) * scale,
                position.x + maxW + config.backgroundPaddingPx * scale,
                position.y + totalH + (config.backgroundPaddingPx / 2f) * scale
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(bgRect, config.backgroundCornerRadiusPx * scale, config.backgroundCornerRadiusPx * scale, bgPaint)
        }

        var currentY = position.y

        for (line in lines) {
            val words = line.split(" ")
            var currentX = position.x
            val spaceWidth = paint.measureText(" ") + (config.wordSpacingPx * scale)

            for (i in words.indices) {
                val word = words[i]

                // Outer Glow Effect
                if (config.hasOuterGlow) {
                    val glowPaint = Paint(paint).apply {
                        color = config.outerGlowColor
                        alpha = (config.outerGlowOpacity * 255).toInt().coerceIn(0, 255)
                        if (config.outerGlowRadiusPx > 0f) {
                            maskFilter = BlurMaskFilter(config.outerGlowRadiusPx * scale, BlurMaskFilter.Blur.OUTER)
                        }
                    }
                    canvas.drawText(word, currentX, currentY, glowPaint)
                }

                // Photoshop Drop Shadow Effect
                if (config.hasShadow) {
                    val shadowPaint = Paint(paint).apply {
                        color = config.shadowColor
                        alpha = (config.shadowOpacity * 255).toInt().coerceIn(0, 255)
                        if (config.shadowRadiusPx > 0f) {
                            maskFilter = BlurMaskFilter(config.shadowRadiusPx * scale, BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    canvas.drawText(word, currentX + config.shadowDxPx * scale, currentY + config.shadowDyPx * scale, shadowPaint)
                }

                // Outer Stroke / Outline Effect
                if (config.hasOutline) {
                    val outlinePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = config.outlineWidthPx * scale
                        color = config.outlineColor
                        alpha = (config.outlineOpacity * 255).toInt().coerceIn(0, 255)
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    canvas.drawText(word, currentX, currentY, outlinePaint)
                }

                // Main Color & Gradient Fill
                val fillPaint = Paint(paint).apply {
                    style = Paint.Style.FILL
                    color = config.textColor
                    if (config.blurRadius > 0f) {
                        maskFilter = BlurMaskFilter(config.blurRadius * scale, BlurMaskFilter.Blur.NORMAL)
                    }
                    if (config.hasGradient) {
                        shader = LinearGradient(
                            currentX, currentY - config.fontSize * scale,
                            currentX, currentY,
                            config.gradientStartColor, config.gradientEndColor,
                            Shader.TileMode.CLAMP
                        )
                    }
                }
                canvas.drawText(word, currentX, currentY, fillPaint)

                // Inner Glow Effect
                if (config.hasInnerGlow) {
                    val innerGlowPaint = Paint(paint).apply {
                        color = config.innerGlowColor
                        alpha = (config.innerGlowOpacity * 255).toInt().coerceIn(0, 255)
                        if (config.innerGlowSizePx > 0f) {
                            maskFilter = BlurMaskFilter(config.innerGlowSizePx * scale, BlurMaskFilter.Blur.INNER)
                        }
                    }
                    canvas.drawText(word, currentX, currentY, innerGlowPaint)
                }

                currentX += paint.measureText(word) + spaceWidth
            }

            currentY += lineHeight
        }

        canvas.restore()
    }

    fun drawTextOnLayer(layer: DrawingLayer, config: StackableTextConfig, position: Offset, scale: Float = 1.0f, rotationAngle: Float = 0f) {
        val textBmp = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textBmp)
        renderTextToCanvas(canvas, config, position, scale, rotationAngle)
        val layerBmp = layer.getPersistentBitmap()
        val layerCanvas = Canvas(layerBmp)
        layerCanvas.drawBitmap(textBmp, 0f, 0f, null)
        layer.tileMap.importFromBitmap(layerBmp)
    }
}
