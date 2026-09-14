package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset

class StackableTextConfig(
    var text: String = "GrooxTyper",
    var fontSize: Float = 56f,
    var textColor: Int = Color.WHITE,
    var hasOutline: Boolean = true,
    var outlineColor: Int = Color.BLACK,
    var outlineWidth: Float = 10f,
    var hasShadow: Boolean = true,
    var shadowColor: Int = Color.parseColor("#80000000"),
    var shadowRadius: Float = 12f,
    var shadowDx: Float = 6f,
    var shadowDy: Float = 6f,
    var hasGradient: Boolean = false,
    var gradientStartColor: Int = Color.RED,
    var gradientEndColor: Int = Color.YELLOW,
    var blurRadius: Float = 0f,
    var letterSpacing: Float = 0.05f,
    var wordSpacing: Float = 0.0f,
    var lineSpacingMultiplier: Float = 1.2f,
    var typeface: Typeface = Typeface.DEFAULT_BOLD
)

class TextItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var config: StackableTextConfig = StackableTextConfig(),
    var position: Offset = Offset(200f, 300f),
    var scale: Float = 1.0f
) {
    fun getBounds(): android.graphics.RectF {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.fontSize * scale
            typeface = config.typeface
        }
        val lines = config.text.split("\n")
        var maxW = 0f
        for (line in lines) {
            val w = paint.measureText(line)
            if (w > maxW) maxW = w
        }
        val fm = paint.fontMetrics
        val h = (fm.descent - fm.ascent) * config.lineSpacingMultiplier * lines.size
        val padding = config.outlineWidth * scale + config.shadowRadius * scale + 20f
        return android.graphics.RectF(
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
        val lineHeight = baseLineHeight + (config.wordSpacing * scale)

        var currentY = position.y

        for (line in lines) {
            val words = line.split(" ")
            var currentX = position.x
            val spaceWidth = paint.measureText(" ") + (config.wordSpacing * scale)

            for (i in words.indices) {
                val word = words[i]

                if (config.hasShadow) {
                    val shadowPaint = Paint(paint).apply {
                        color = config.shadowColor
                        if (config.shadowRadius > 0f) {
                            maskFilter = BlurMaskFilter(config.shadowRadius * scale, BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    canvas.drawText(word, currentX + config.shadowDx * scale, currentY + config.shadowDy * scale, shadowPaint)
                }

                if (config.hasOutline) {
                    val outlinePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = config.outlineWidth * scale
                        color = config.outlineColor
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    canvas.drawText(word, currentX, currentY, outlinePaint)
                }

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

    fun renderTextToBitmap(width: Int, height: Int, config: StackableTextConfig, position: Offset, scale: Float = 1.0f): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderTextToCanvas(canvas, config, position, scale)
        return bitmap
    }

    fun drawTextOnLayer(layer: DrawingLayer, config: StackableTextConfig, position: Offset, scale: Float = 1.0f) {
        val textBmp = renderTextToBitmap(layer.width, layer.height, config, position, scale)
        val layerBmp = layer.getBitmap()
        val canvas = Canvas(layerBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(textBmp, 0f, 0f, paint)
        layer.tileMap.importFromBitmap(layerBmp)
        layer.markDirty()
    }
}
