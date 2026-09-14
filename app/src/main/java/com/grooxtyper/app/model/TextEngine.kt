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

    // Outer Outline / Stroke Effect
    var hasOutline: Boolean = true,
    var outlineColor: Int = Color.BLACK,
    var outlineWidth: Float = 10f,
    var outlineOpacity: Float = 1.0f,

    // Drop Shadow Effect (Photoshop-style)
    var hasShadow: Boolean = true,
    var shadowColor: Int = Color.parseColor("#80000000"),
    var shadowRadius: Float = 12f,
    var shadowDx: Float = 6f,
    var shadowDy: Float = 6f,
    var shadowOpacity: Float = 0.8f,

    // Inner Glow Effect
    var hasInnerGlow: Boolean = false,
    var innerGlowColor: Int = Color.YELLOW,
    var innerGlowRadius: Float = 8f,

    // Gradient Fill Effect
    var hasGradient: Boolean = false,
    var gradientStartColor: Int = Color.RED,
    var gradientEndColor: Int = Color.YELLOW,

    // Text Box Background Banner
    var hasBackgroundBanner: Boolean = false,
    var backgroundColor: Int = Color.parseColor("#99000000"),
    var backgroundCornerRadius: Float = 16f,

    // Spacing & Typography (Kerning & Leading)
    var blurRadius: Float = 0f,
    var letterSpacing: Float = 0.05f,
    var wordSpacing: Float = 0.0f,
    var lineSpacingMultiplier: Float = 1.2f,
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
            letterSpacing = config.letterSpacing
        }
        val lines = config.text.split("\n")
        var maxW = 0f
        for (line in lines) {
            val w = paint.measureText(line) + (line.length * config.wordSpacing * scale)
            if (w > maxW) maxW = w
        }
        val fm = paint.fontMetrics
        val h = (fm.descent - fm.ascent) * config.lineSpacingMultiplier * lines.size
        val padding = (config.outlineWidth + config.shadowRadius + 24f) * scale
        return RectF(
            position.x - padding,
            position.y + fm.ascent * scale - padding,
            position.x + maxW + padding,
            position.y + h + padding
        )
    }

    fun isHit(p: Offset): Boolean {
        return getBounds().contains(p.x, p.y)
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

    fun renderTextToCanvas(canvas: Canvas, config: StackableTextConfig, position: Offset, scale: Float = 1.0f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.fontSize * scale
            typeface = config.typeface
            letterSpacing = config.letterSpacing
        }

        val lines = config.text.split("\n")
        val fontMetrics = paint.fontMetrics
        val baseLineHeight = (fontMetrics.descent - fontMetrics.ascent) * config.lineSpacingMultiplier
        val lineHeight = baseLineHeight + (config.lineSpacingMultiplier * 10f * scale)

        // Draw Background Banner if enabled
        if (config.hasBackgroundBanner) {
            var maxW = 0f
            for (l in lines) {
                val w = paint.measureText(l)
                if (w > maxW) maxW = w
            }
            val totalH = lineHeight * lines.size
            val bgRect = RectF(
                position.x - 16f * scale,
                position.y + fontMetrics.ascent * scale - 12f * scale,
                position.x + maxW + 16f * scale,
                position.y + totalH + 12f * scale
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(bgRect, config.backgroundCornerRadius * scale, config.backgroundCornerRadius * scale, bgPaint)
        }

        var currentY = position.y

        for (line in lines) {
            val words = line.split(" ")
            var currentX = position.x
            val spaceWidth = paint.measureText(" ") + (config.wordSpacing * scale)

            for (i in words.indices) {
                val word = words[i]

                // Drop Shadow Effect
                if (config.hasShadow) {
                    val shadowPaint = Paint(paint).apply {
                        color = config.shadowColor
                        alpha = (config.shadowOpacity * 255).toInt().coerceIn(0, 255)
                        if (config.shadowRadius > 0f) {
                            maskFilter = BlurMaskFilter(config.shadowRadius * scale, BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    canvas.drawText(word, currentX + config.shadowDx * scale, currentY + config.shadowDy * scale, shadowPaint)
                }

                // Outer Outline Effect
                if (config.hasOutline) {
                    val outlinePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = config.outlineWidth * scale
                        color = config.outlineColor
                        alpha = (config.outlineOpacity * 255).toInt().coerceIn(0, 255)
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    canvas.drawText(word, currentX, currentY, outlinePaint)
                }

                // Fill Text
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

                currentX += paint.measureText(word) + spaceWidth
            }

            currentY += lineHeight
        }
    }

    fun drawTextOnLayer(layer: DrawingLayer, config: StackableTextConfig, position: Offset, scale: Float = 1.0f) {
        val textBmp = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textBmp)
        renderTextToCanvas(canvas, config, position, scale)
        val layerBmp = layer.getBitmap()
        val layerCanvas = Canvas(layerBmp)
        layerCanvas.drawBitmap(textBmp, 0f, 0f, null)
        layer.tileMap.importFromBitmap(layerBmp)
        layer.markDirty()
    }
}
