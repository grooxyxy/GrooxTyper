package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class BrushType(val displayName: String, val category: String) {
    // Pen
    PEN_HARD("Pen (Hard)", "Pen"),
    PEN_SOFT("Pen (Soft)", "Pen"),
    PENCIL("Pencil", "Pen"),
    INK("Ink", "Pen"),
    // Paint
    WATERCOLOR("Watercolor", "Paint"),
    OIL("Oil", "Paint"),
    MARKER("Marker", "Paint"),
    // Air
    AIRBRUSH("Airbrush", "Air"),
    // Erase
    ERASER("Eraser", "Erase"),
    BLUR("Blur", "Erase")
}

enum class RulerType {
    OFF,
    STRAIGHT_LINE,
    CIRCLE
}

class ForceFadeConfig(
    isEnabled: Boolean = false,
    startFade: Float = 0.3f,
    endFade: Float = 0.3f
) {
    var isEnabled by mutableStateOf(isEnabled)
    var startFade by mutableFloatStateOf(startFade)
    var endFade by mutableFloatStateOf(endFade)
}

class StabilizerConfig(
    isEnabled: Boolean = true,
    strength: Float = 0.5f // 0.0 = raw, 1.0 = max smoothing
) {
    var isEnabled by mutableStateOf(isEnabled)
    var strength by mutableFloatStateOf(strength)
}

class RulerGuide(
    type: RulerType = RulerType.OFF,
    var startPos: Offset = Offset(100f, 100f),
    var endPos: Offset = Offset(500f, 500f),
    var circleCenter: Offset = Offset(300f, 300f),
    var circleRadius: Float = 200f
) {
    var type by mutableStateOf(type)
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

class BrushEngine {
    var brushType by mutableStateOf(BrushType.PEN_HARD)
    var size by mutableFloatStateOf(24f)
    var opacity by mutableFloatStateOf(1.0f)
    var color by mutableIntStateOf(Color.BLACK)
    var forceFade: ForceFadeConfig = ForceFadeConfig()
    var stabilizer: StabilizerConfig = StabilizerConfig()
    var rulerGuide: RulerGuide = RulerGuide()

    // Stroke smoothing state
    private var lastSmoothedPoint: Offset? = null
    private var velocityHistory = mutableListOf<Float>()

    fun beginStroke() {
        lastSmoothedPoint = null
        velocityHistory.clear()
    }

    fun endStroke() {
        lastSmoothedPoint = null
        velocityHistory.clear()
    }

    private fun smoothPoint(current: Offset, previous: Offset?): Offset {
        if (!stabilizer.isEnabled || previous == null) return current
        val factor = stabilizer.strength
        return Offset(
            previous.x + (current.x - previous.x) * (1f - factor),
            previous.y + (current.y - previous.y) * (1f - factor)
        )
    }

    private fun createBasePaint(): Paint {
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            color = this@BrushEngine.color
            alpha = (this@BrushEngine.opacity * 255).toInt().coerceIn(0, 255)
        }

        when (brushType) {
            BrushType.PEN_HARD -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.PEN_SOFT -> {
                paint.maskFilter = BlurMaskFilter(max(1f, size * 0.15f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
            }
            BrushType.PENCIL -> {
                paint.maskFilter = BlurMaskFilter(max(1f, size * 0.08f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 200).toInt().coerceIn(0, 255)
            }
            BrushType.INK -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.WATERCOLOR -> {
                paint.maskFilter = BlurMaskFilter(max(2f, size * 0.25f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 120).toInt().coerceIn(0, 255)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
            BrushType.OIL -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.maskFilter = BlurMaskFilter(max(1f, size * 0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            BrushType.MARKER -> {
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.MITER
                paint.alpha = (this@BrushEngine.opacity * 180).toInt().coerceIn(0, 255)
            }
            BrushType.AIRBRUSH -> {
                paint.maskFilter = BlurMaskFilter(max(3f, size * 0.4f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 100).toInt().coerceIn(0, 255)
            }
            BrushType.ERASER -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            BrushType.BLUR -> {
                // Handled separately
            }
        }
        return paint
    }

    // ibisPaint-style: dip pen pressure simulation based on velocity
    private fun getVelocityFactor(distance: Float): Float {
        velocityHistory.add(distance)
        if (velocityHistory.size > 5) velocityHistory.removeAt(0)
        val avgVelocity = velocityHistory.average().toFloat()
        // Slower = thicker, faster = thinner (like real ink pen)
        return when {
            avgVelocity < 2f -> 1.3f
            avgVelocity > 15f -> 0.6f
            else -> (1.3f - (avgVelocity - 2f) * 0.05f).coerceIn(0.6f, 1.3f)
        }
    }

    fun strokeSegmentOnLayer(layer: DrawingLayer, p1: Offset, p2: Offset, progressFraction: Float = 1.0f) {
        val smoothedP1 = smoothPoint(rulerGuide.snapPoint(p1), lastSmoothedPoint)
        val smoothedP2 = smoothPoint(rulerGuide.snapPoint(p2), smoothedP1)
        lastSmoothedPoint = smoothedP2

        if (brushType == BrushType.BLUR) {
            applyBlurStroke(layer, smoothedP1, smoothedP2)
            return
        }

        val bmp = layer.getPersistentBitmap()
        val canvas = Canvas(bmp)

        val paint = createBasePaint()
        if (layer.isAlphaLocked) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        val distance = hypot(smoothedP2.x - smoothedP1.x, smoothedP2.y - smoothedP1.y)

        // Titik tunggal (tap): pastikan jadi dot bulat, bukan hilang.
        if (distance < 1f) {
            val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(smoothedP2.x, smoothedP2.y, max(0.5f, paint.strokeWidth / 2f), dotPaint)
            layer.markDirty()
            return
        }

        // Apply velocity-based width for Ink/Dip pen style
        if (brushType == BrushType.INK || brushType == BrushType.PEN_SOFT) {
            val velocityFactor = getVelocityFactor(distance)
            paint.strokeWidth = size * velocityFactor
        }

        // Force fade / taper
        if (forceFade.isEnabled || brushType == BrushType.INK) {
            val factor = if (progressFraction < forceFade.startFade && forceFade.startFade > 0f) {
                progressFraction / forceFade.startFade
            } else if (progressFraction > (1f - forceFade.endFade) && forceFade.endFade > 0f) {
                (1f - progressFraction) / forceFade.endFade
            } else {
                1.0f
            }.coerceIn(0.1f, 1.0f)

            paint.strokeWidth = paint.strokeWidth * factor
            paint.alpha = ((paint.alpha / 255f) * factor * 255).toInt().coerceIn(0, 255)
        }

        // Interpolasi stamp agar tidak patah-patah saat jari bergerak cepat.
        // Spacing ~20% dari diameter brush menjamin overlap antar stamp.
        val spacing = max(1.5f, paint.strokeWidth * 0.2f)
        val steps = ceil((distance / spacing).toDouble()).toInt().coerceIn(1, 256)

        var prevX = smoothedP1.x
        var prevY = smoothedP1.y
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val x = smoothedP1.x + (smoothedP2.x - smoothedP1.x) * t
            val y = smoothedP1.y + (smoothedP2.y - smoothedP1.y) * t
            drawDab(canvas, paint, prevX, prevY, x, y)
            prevX = x
            prevY = y
        }

        // TileMap bukan sumber render (renderComposite memakai compositeBitmap),
        // jadi jangan sync per-segmen yang O(W*H). Sync dilakukan di syncTiles()
        // saat stroke selesai / undo / flatten.
        layer.markDirty()
    }

    /** Sinkronisasi tile cache dari bitmap persisten. Panggil saat stroke selesai. */
    fun syncTiles(layer: DrawingLayer) {
        layer.tileMap.importFromBitmap(layer.getPersistentBitmap())
    }

    private fun drawDab(canvas: Canvas, paint: Paint, x1: Float, y1: Float, x2: Float, y2: Float) {
        // Watercolor: draw multiple overlapping strokes for texture
        if (brushType == BrushType.WATERCOLOR) {
            val baseAlpha = paint.alpha
            val baseWidth = paint.strokeWidth
            for (i in 1..3) {
                paint.alpha = (baseAlpha * (0.3f + i * 0.2f)).toInt().coerceIn(0, 255)
                paint.strokeWidth = baseWidth * (0.7f + i * 0.15f)
                val jitterX = (i - 2) * baseWidth * 0.1f
                val jitterY = (i - 2) * baseWidth * 0.08f
                canvas.drawLine(x1 + jitterX, y1 + jitterY, x2 + jitterX, y2 + jitterY, paint)
            }
            paint.alpha = baseAlpha
            paint.strokeWidth = baseWidth
        } else if (brushType == BrushType.OIL) {
            // Oil: draw core + edge highlight
            canvas.drawLine(x1, y1, x2, y2, paint)
            val highlight = Paint(paint).apply {
                alpha = (paint.alpha * 0.4f).toInt()
                strokeWidth = paint.strokeWidth * 0.6f
                color = Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawLine(x1, y1, x2, y2, highlight)
        } else {
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun applyBlurStroke(layer: DrawingLayer, p1: Offset, p2: Offset) {
        val bmp = layer.getPersistentBitmap()
        val radius = max(3f, size / 2f)
        val pad = (size + radius * 2f + 4f)

        val leftF = min(p1.x, p2.x) - pad
        val topF = min(p1.y, p2.y) - pad
        val rightF = max(p1.x, p2.x) + pad
        val bottomF = max(p1.y, p2.y) + pad

        val left = leftF.toInt().coerceIn(0, bmp.width - 1)
        val top = topF.toInt().coerceIn(0, bmp.height - 1)
        val right = rightF.toInt().coerceIn(1, bmp.width)
        val bottom = bottomF.toInt().coerceIn(1, bmp.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return

        // Hanya proses dirty rect, bukan seluruh kanvas (hemat CPU/GC per segmen).
        val region = Bitmap.createBitmap(bmp, left, top, w, h)
        val blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        blurCanvas.drawBitmap(region, 0f, 0f, blurPaint)

        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            color = Color.BLACK
        }
        maskCanvas.drawLine(p1.x - left, p1.y - top, p2.x - left, p2.y - top, strokePaint)

        val tempLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempLayer)
        tempCanvas.drawBitmap(blurred, 0f, 0f, null)
        val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        tempCanvas.drawBitmap(maskBmp, 0f, 0f, dstIn)

        Canvas(bmp).drawBitmap(tempLayer, left.toFloat(), top.toFloat(), null)

        region.recycle()
        blurred.recycle()
        maskBmp.recycle()
        tempLayer.recycle()
        layer.markDirty()
    }
}
