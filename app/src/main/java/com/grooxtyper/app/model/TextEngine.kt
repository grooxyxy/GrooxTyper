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

enum class TextEffectPreset(val displayName: String) {
    NONE("Normal"),
    NEON_GLOW("Neon Glow"),
    GOLD_METALLIC("Gold Metallic"),
    COMIC_OUTLINE("Comic Outline"),
    CYBERPUNK("Cyberpunk"),
    SHADOWED_3D("Shadow 3D"),
    MANGA_SHADING("Manga Shading")
}

class TextConfig(
    val text: String = "ibisPaint",
    val fontSize: Float = 56f,
    val textColor: Int = Color.WHITE,
    val preset: TextEffectPreset = TextEffectPreset.NONE,
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
            when (config.preset) {
                TextEffectPreset.NEON_GLOW -> {
                    // Glow background
                    val glowPaint = Paint(paint).apply {
                        color = config.textColor
                        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
                    }
                    canvas.drawText(line, position.x, currentY, glowPaint)

                    // Core bright text
                    val corePaint = Paint(paint).apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                    }
                    canvas.drawText(line, position.x, currentY, corePaint)
                }

                TextEffectPreset.GOLD_METALLIC -> {
                    val goldPaint = Paint(paint).apply {
                        style = Paint.Style.FILL
                        shader = LinearGradient(
                            position.x, currentY - config.fontSize,
                            position.x, currentY,
                            intArrayOf(Color.parseColor("#FFE066"), Color.parseColor("#996600"), Color.parseColor("#FFF5CC")),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    val strokePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 6f
                        color = Color.parseColor("#4D3300")
                    }
                    canvas.drawText(line, position.x, currentY, strokePaint)
                    canvas.drawText(line, position.x, currentY, goldPaint)
                }

                TextEffectPreset.COMIC_OUTLINE -> {
                    // Thick black outline + yellow fill + shadow offset
                    val shadowPaint = Paint(paint).apply {
                        color = Color.BLACK
                    }
                    canvas.drawText(line, position.x + 8f, currentY + 8f, shadowPaint)

                    val outlinePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 14f
                        color = Color.BLACK
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    canvas.drawText(line, position.x, currentY, outlinePaint)

                    val fillPaint = Paint(paint).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#FFD700")
                    }
                    canvas.drawText(line, position.x, currentY, fillPaint)
                }

                TextEffectPreset.CYBERPUNK -> {
                    // Offset cyan and magenta chromatic aberration
                    val cyanPaint = Paint(paint).apply {
                        color = Color.CYAN
                        alpha = 200
                    }
                    canvas.drawText(line, position.x - 4f, currentY - 2f, cyanPaint)

                    val magPaint = Paint(paint).apply {
                        color = Color.MAGENTA
                        alpha = 200
                    }
                    canvas.drawText(line, position.x + 4f, currentY + 2f, magPaint)

                    val mainPaint = Paint(paint).apply {
                        color = Color.YELLOW
                        style = Paint.Style.FILL
                    }
                    canvas.drawText(line, position.x, currentY, mainPaint)
                }

                TextEffectPreset.SHADOWED_3D -> {
                    for (i in 8 downTo 1) {
                        val shadow3d = Paint(paint).apply {
                            color = Color.rgb(30, 30, 30)
                        }
                        canvas.drawText(line, position.x + i, currentY + i, shadow3d)
                    }
                    val topPaint = Paint(paint).apply {
                        color = config.textColor
                    }
                    canvas.drawText(line, position.x, currentY, topPaint)
                }

                TextEffectPreset.MANGA_SHADING -> {
                    val outlinePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 10f
                        color = Color.WHITE
                    }
                    canvas.drawText(line, position.x, currentY, outlinePaint)

                    val mainPaint = Paint(paint).apply {
                        style = Paint.Style.FILL
                        color = Color.BLACK
                    }
                    canvas.drawText(line, position.x, currentY, mainPaint)
                }

                TextEffectPreset.NONE -> {
                    // Custom configurable effect
                    if (config.hasShadow) {
                        val shadowPaint = Paint(paint).apply {
                            color = config.shadowColor
                            if (config.shadowRadius > 0f) {
                                maskFilter = BlurMaskFilter(config.shadowRadius, BlurMaskFilter.Blur.NORMAL)
                            }
                        }
                        canvas.drawText(line, position.x + config.shadowDx, currentY + config.shadowDy, shadowPaint)
                    }

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
                }
            }

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
