package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max

enum class BrushType(val displayName: String) {
    FELT_TIP_PEN("Felt Tip Pen"),
    DIP_PEN("Dip Pen"),
    AIRBRUSH("Airbrush"),
    ERASER("Eraser"),
    BLUR("Blur Brush")
}

enum class RulerType {
    OFF,
    STRAIGHT_LINE,
    CIRCLE
}

class ForceFadeConfig(
    val isEnabled: Boolean = false,
    val startFade: Float = 0.2f, // 0.0 to 1.0
    val endFade: Float = 0.2f     // 0.0 to 1.0
)

class RulerGuide(
    var type: RulerType = RulerType.OFF,
    var startPos: Offset = Offset(100f, 100f),
    var endPos: Offset = Offset(500f, 500f),
    var circleCenter: Offset = Offset(300f, 300f),
    var circleRadius: Float = 200f
) {
    fun snapPoint(p: Offset): Offset {
        return when (type) {
            RulerType.STRAIGHT_LINE -> {
                val dx = endPos.x - startPos.x
                val dy = endPos.y - startPos.y
                val lenSq = dx * dx + dy * dy
                if (lenSq == 0f) return p
                val t = max(0f, minOf(1f, ((p.x - startPos.x) * dx + (p.y - startPos.y) * dy) / lenSq))
                Offset(startPos.x + t * dx, startPos.y + t * dy)
            }
            RulerType.CIRCLE -> {
                val dx = p.x - circleCenter.x
                val dy = p.y - circleCenter.y
                val dist = hypot(dx, dy)
                if (dist == 0f) return p
                Offset(circleCenter.x + (dx / dist) * circleRadius, circleCenter.y + (dy / dist) * circleRadius)
            }
            RulerType.OFF -> p
        }
    }
}

class BrushEngine {
    var brushType: BrushType = BrushType.FELT_TIP_PEN
    var size: Float = 24f
    var opacity: Float = 1.0f
    var color: Int = Color.BLACK
    var forceFade: ForceFadeConfig = ForceFadeConfig()
    var rulerGuide: RulerGuide = RulerGuide()

    fun createPaint(): Paint {
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            color = this@BrushEngine.color
            alpha = (this@BrushEngine.opacity * 255).toInt()
        }

        when (brushType) {
            BrushType.FELT_TIP_PEN -> {
                paint.strokeCap = Paint.Cap.ROUND
            }
            BrushType.DIP_PEN -> {
                paint.strokeCap = Paint.Cap.SQUARE
            }
            BrushType.AIRBRUSH -> {
                paint.maskFilter = BlurMaskFilter(max(1f, size / 2f), BlurMaskFilter.Blur.NORMAL)
            }
            BrushType.ERASER -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            BrushType.BLUR -> {
                // Blur brush handled via special tile sampling
            }
        }
        return paint
    }

    fun strokePathOnLayer(layer: DrawingLayer, rawPath: Path) {
        val snappedPath = Path()
        val measure = PathMeasure(rawPath, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val length = measure.length

        if (length == 0f) return

        if (rulerGuide.type != RulerType.OFF) {
            var step = 0f
            var first = true
            while (step <= length) {
                measure.getPosTan(step, pos, tan)
                val snapped = rulerGuide.snapPoint(Offset(pos[0], pos[1]))
                if (first) {
                    snappedPath.moveTo(snapped.x, snapped.y)
                    first = false
                } else {
                    snappedPath.lineTo(snapped.x, snapped.y)
                }
                step += 4f
            }
        } else {
            snappedPath.addPath(rawPath)
        }

        val layerBmp = layer.getBitmap()
        val baseCanvas = Canvas(layerBmp)

        if (brushType == BrushType.BLUR) {
            applyBlurStroke(layer, snappedPath)
            layer.markDirty()
            return
        }

        val paint = createPaint()

        if (layer.isAlphaLocked) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        if (forceFade.isEnabled && length > 0) {
            drawFadedPathSegments(baseCanvas, snappedPath, paint, forceFade)
        } else {
            baseCanvas.drawPath(snappedPath, paint)
        }

        layer.markDirty()
    }

    private fun drawFadedPathSegments(canvas: Canvas, path: Path, basePaint: Paint, fade: ForceFadeConfig) {
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 0) return

        val step = max(2f, size / 4f)
        var distance = 0f
        val pos = FloatArray(2)
        var prevX = 0f
        var prevY = 0f
        var first = true

        val startFadeLen = length * fade.startFade
        val endFadeLen = length * fade.endFade

        while (distance <= length) {
            measure.getPosTan(distance, pos, null)
            val curX = pos[0]
            val curY = pos[1]

            if (!first) {
                var factor = 1.0f
                if (distance < startFadeLen && startFadeLen > 0) {
                    factor = distance / startFadeLen
                } else if (distance > (length - endFadeLen) && endFadeLen > 0) {
                    factor = (length - distance) / endFadeLen
                }
                factor = max(0.05f, minOf(1.0f, factor))

                val segPaint = Paint(basePaint).apply {
                    strokeWidth = basePaint.strokeWidth * factor
                    alpha = (basePaint.alpha * factor).toInt()
                }
                canvas.drawLine(prevX, prevY, curX, curY, segPaint)
            } else {
                first = false
            }

            prevX = curX
            prevY = curY
            distance += step
        }
    }

    private fun applyBlurStroke(layer: DrawingLayer, path: Path) {
        val bmp = layer.getBitmap()
        val blurred = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(max(2f, size / 2f), BlurMaskFilter.Blur.NORMAL)
        }
        blurCanvas.drawBitmap(bmp, 0f, 0f, blurPaint)

        val maskBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            color = Color.BLACK
        }
        maskCanvas.drawPath(path, strokePaint)

        val layerCanvas = Canvas(bmp)
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }
        // Composite blurred result inside path bounds
        val tempLayer = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempLayer)
        tempCanvas.drawBitmap(blurred, 0f, 0f, null)
        val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        tempCanvas.drawBitmap(maskBmp, 0f, 0f, dstIn)

        layerCanvas.drawBitmap(tempLayer, 0f, 0f, clipPaint)
    }
}
