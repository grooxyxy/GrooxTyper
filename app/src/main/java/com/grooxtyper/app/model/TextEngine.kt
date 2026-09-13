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
    val text: String = "ibisPaint",
    val fontSize: Float = 56f,
    val textColor: Int = Color.WHITE,
    val hasOutline: Boolean = true,
    val outlineColor: Int = Color.BLACK,
    val outlineWidth: Float = 10f,
    val hasShadow: Boolean = true,
    val shadowColor: Int = Color.parseColor("#80000000"),
    val shadowRadius: Float = 12f,
    val shadowDx: Float = 6f,
    val shadowDy: Float = 6f,
    val hasGradient: Boolean = false,
    val gradientStartColor: Int = Color.RED,
    val gradientEndColor: Int = Color.YELLOW,
    val blurRadius: Float = 0f,
    val letterSpacing: Float = 0.05f,
    val lineSpacingMultiplier: Float = 1.2f,
    val typeface: Typeface = Typeface.DEFAULT_BOLD
)

class TextEngine {

    fun renderTextToBitmap(width: Int, height: Int, config: StackableTextConfig, position: Offset): Bitmap {
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
            // Stack 1: Drop Shadow
            if (config.hasShadow) {
                val shadowPaint = Paint(paint).apply {
                    color = config.shadowColor
                    if (config.shadowRadius > 0f) {
                        maskFilter = BlurMaskFilter(config.shadowRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                canvas.drawText(line, position.x + config.shadowDx, currentY + config.shadowDy, shadowPaint)
            }

            // Stack 2: Outline
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

            // Stack 3: Main Fill with Gradient & Blur
            val fillPaint = Paint(paint).apply {
                style = Paint.Style.FILL
                color = config.textColor
                if (config.blurRadius > 0f) {
                    maskFilter = BlurMaskFilter(config.blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
                if (config.hasGradient) {
                    shader = LinearGradient(
                        position.x, currentY - config.fontSize,
                        position.x, currentY,
                        config.gradientStartColor, config.gradientEndColor,
                        Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawText(line, position.x, currentY, fillPaint)

            currentY += lineHeight
        }

        return bitmap
    }

    fun drawTextOnLayer(layer: DrawingLayer, config: StackableTextConfig, position: Offset) {
        val textBmp = renderTextToBitmap(layer.width, layer.height, config, position)
        val layerBmp = layer.getBitmap()
        val canvas = Canvas(layerBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(textBmp, 0f, 0f, paint)
        layer.markDirty()
    }
}
