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

class TextConfig(
    val text: String = "Sample Text",
    val fontSize: Float = 48f,
    val textColor: Int = Color.BLACK,
    val hasOutline: Boolean = false,
    val outlineColor: Int = Color.WHITE,
    val outlineWidth: Float = 8f,
    val hasShadow: Boolean = false,
    val shadowColor: Int = Color.GRAY,
    val shadowRadius: Float = 10f,
    val shadowDx: Float = 5f,
    val shadowDy: Float = 5f,
    val hasGradient: Boolean = false,
    val gradientColorStart: Int = Color.RED,
    val gradientColorEnd: Int = Color.BLUE,
    val blurRadius: Float = 0f,
    val letterSpacing: Float = 0.05f, // Extra spacing fraction
    val lineSpacingMultiplier: Float = 1.2f,
    val typeface: Typeface = Typeface.DEFAULT_BOLD
)

class TextEngine {
    fun renderTextToBitmap(width: Int, height: Int, config: TextConfig, position: Offset): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.fontSize
            typeface = config.typeface
            letterSpacing = config.letterSpacing
        }

        val lines = config.text.split("\n")
        val fontMetrics = paint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * config.lineSpacingMultiplier

        var currentY = position.y

        for (line in lines) {
            // Draw Shadow
            if (config.hasShadow) {
                val shadowPaint = Paint(paint).apply {
                    color = config.shadowColor
                    if (config.shadowRadius > 0) {
                        maskFilter = BlurMaskFilter(config.shadowRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                canvas.drawText(line, position.x + config.shadowDx, currentY + config.shadowDy, shadowPaint)
            }

            // Draw Outline
            if (config.hasOutline) {
                val outlinePaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = config.outlineWidth
                    color = config.outlineColor
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawText(line, position.x, currentY, outlinePaint)
            }

            // Draw Fill / Gradient / Blur
            val fillPaint = Paint(paint).apply {
                style = Paint.Style.FILL
                color = config.textColor
                if (config.blurRadius > 0) {
                    maskFilter = BlurMaskFilter(config.blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
                if (config.hasGradient) {
                    shader = LinearGradient(
                        position.x, currentY - config.fontSize,
                        position.x, currentY,
                        config.gradientColorStart, config.gradientColorEnd,
                        Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawText(line, position.x, currentY, fillPaint)

            currentY += lineHeight
        }

        return bitmap
    }

    fun drawTextOnLayer(layer: DrawingLayer, config: TextConfig, position: Offset) {
        val textBmp = renderTextToBitmap(layer.width, layer.height, config, position)
        val layerBmp = layer.getBitmap()
        val canvas = Canvas(layerBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(textBmp, 0f, 0f, paint)
        layer.markDirty()
    }
}
