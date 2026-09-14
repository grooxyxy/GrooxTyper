package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class BrushType(val displayName: String) {
    FELT_TIP_PEN("Felt Tip Pen"),
    DIP_PEN("Dip Pen"),
    AIRBRUSH("Airbrush"),
    DIGITAL_PEN("Digital Pen"),
    PENCIL("Pencil"),
    ERASER("Eraser"),
    BLUR("Blur Brush")
}

enum class RulerType {
    OFF,
    STRAIGHT_LINE,
    CIRCLE
}

class ForceFadeConfig(
    var isEnabled: Boolean = false,
    var startFade: Float = 0.3f,
    var endFade: Float = 0.3f
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
                val t = max(0f, min(1f, ((p.x - startPos.x) * dx + (p.y - startPos.y) * dy) / lenSq))
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

class StrokeStabilizer(
    var isEnabled: Boolean = true,
    var value: Int = 3
) {
    private var lastPos: Offset? = null

    fun filter(input: Offset): Offset {
        if (!isEnabled || value <= 0) return input
        val prev = lastPos ?: input
        val factor = 1.0f / (value + 1f)
        val smoothed = Offset(
            prev.x + (input.x - prev.x) * factor,
            prev.y + (input.y - prev.y) * factor
        )
        lastPos = smoothed
        return smoothed
    }

    fun reset() {
        lastPos = null
    }
}

class BrushEngine {
    var brushType: BrushType = BrushType.FELT_TIP_PEN
    var size: Float = 24f
    var opacity: Float = 1.0f
    var color: Int = Color.BLACK
    var forceFade: ForceFadeConfig = ForceFadeConfig()
    var rulerGuide: RulerGuide = RulerGuide()
    var stabilizer: StrokeStabilizer = StrokeStabilizer()

    private val cachedPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun updatePaint(): Paint {
        cachedPaint.reset()
        cachedPaint.isAntiAlias = true
        cachedPaint.isDither = true
        cachedPaint.style = Paint.Style.STROKE
        cachedPaint.strokeCap = Paint.Cap.ROUND
        cachedPaint.strokeJoin = Paint.Join.ROUND
        cachedPaint.strokeWidth = size
        cachedPaint.color = color
        cachedPaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)

        when (brushType) {
            BrushType.FELT_TIP_PEN, BrushType.DIP_PEN -> {
                cachedPaint.strokeCap = Paint.Cap.ROUND
            }
            BrushType.DIGITAL_PEN -> {
                cachedPaint.isAntiAlias = false
                cachedPaint.strokeCap = Paint.Cap.SQUARE
            }
            BrushType.PENCIL -> {
                cachedPaint.strokeCap = Paint.Cap.ROUND
                cachedPaint.alpha = (opacity * 160).toInt().coerceIn(0, 255)
            }
            BrushType.AIRBRUSH -> {
                cachedPaint.maskFilter = BlurMaskFilter(max(2f, size * 0.4f), BlurMaskFilter.Blur.NORMAL)
                cachedPaint.alpha = (opacity * 100).toInt().coerceIn(0, 255)
            }
            BrushType.ERASER -> {
                cachedPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            BrushType.BLUR -> {
                // Blur handled separately
            }
        }
        return cachedPaint
    }

    fun strokeSegmentOnLayer(layer: DrawingLayer, p1: Offset, p2: Offset, progressFraction: Float = 1.0f) {
        val smoothedP1 = stabilizer.filter(p1)
        val smoothedP2 = stabilizer.filter(p2)

        val sp1 = rulerGuide.snapPoint(smoothedP1)
        val sp2 = rulerGuide.snapPoint(smoothedP2)

        val bmp = layer.getBitmap()
        val canvas = Canvas(bmp)

        if (brushType == BrushType.BLUR) {
            val path = Path()
            path.moveTo(sp1.x, sp1.y)
            path.lineTo(sp2.x, sp2.y)
            applyBlurStroke(layer, path)
            layer.markDirty()
            return
        }

        val paint = updatePaint()
        if (layer.isAlphaLocked) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        if (forceFade.isEnabled || brushType == BrushType.DIP_PEN) {
            val factor = if (progressFraction < forceFade.startFade && forceFade.startFade > 0f) {
                progressFraction / forceFade.startFade
            } else if (progressFraction > (1f - forceFade.endFade) && forceFade.endFade > 0f) {
                (1f - progressFraction) / forceFade.endFade
            } else {
                1.0f
            }.coerceIn(0.1f, 1.0f)

            paint.strokeWidth = size * factor
            paint.alpha = ((this.opacity * 255) * factor).toInt().coerceIn(0, 255)
        }

        canvas.drawLine(sp1.x, sp1.y, sp2.x, sp2.y, paint)
        layer.tileMap.importFromBitmap(bmp)
        layer.markDirty()
    }

    private fun applyBlurStroke(layer: DrawingLayer, path: Path) {
        val bmp = layer.getBitmap()
        val blurred = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(max(3f, size / 2f), BlurMaskFilter.Blur.NORMAL)
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
        val tempLayer = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempLayer)
        tempCanvas.drawBitmap(blurred, 0f, 0f, null)
        val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        tempCanvas.drawBitmap(maskBmp, 0f, 0f, dstIn)

        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }
        layerCanvas.drawBitmap(tempLayer, 0f, 0f, clipPaint)
    }
}
