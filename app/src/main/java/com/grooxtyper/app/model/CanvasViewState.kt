package com.grooxtyper.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

class CanvasViewState(
    initialWidth: Int = 1280,
    initialHeight: Int = 1280
) {
    var width by mutableIntStateOf(initialWidth)
    var height by mutableIntStateOf(initialHeight)

    var scale by mutableFloatStateOf(1.0f)
    var offsetX by mutableFloatStateOf(0.0f)
    var offsetY by mutableFloatStateOf(0.0f)
    var rotation by mutableFloatStateOf(0.0f)
    var rawRotation by mutableFloatStateOf(0.0f)

    /**
     * Titik tumpu zoom/rotasi sebagai fraksi viewport (0..1).
     * DIKUNCI di tengah (0.5): zoom/rotasi dijangkar ke centroid lewat
     * koreksi offset (anti-teleport). Jangan diubah per event.
     */
    var pivotFracX by mutableFloatStateOf(0.5f)
    var pivotFracY by mutableFloatStateOf(0.5f)

    /**
     * Pindahkan pivot ke [fracX]/[fracY] dengan kompensasi offset sehingga
     * gambar di layar tidak bergeser (mapping dipertahankan persis).
     */
    fun setPivotFraction(fracX: Float, fracY: Float, viewportW: Float, viewportH: Float) {
        if (viewportW <= 0f || viewportH <= 0f) return
        val nx = fracX.coerceIn(0f, 1f)
        val ny = fracY.coerceIn(0f, 1f)
        val ox = pivotFracX * viewportW
        val oy = pivotFracY * viewportH
        val px = nx * viewportW
        val py = ny * viewportH
        val dx = ox - px
        val dy = oy - py
        if (dx == 0f && dy == 0f) {
            pivotFracX = nx
            pivotFracY = ny
            return
        }
        // t' = t + (I - M)(O - O'), M = scale * R(rotation)
        val rad = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        val mx = scale * (c * dx - s * dy)
        val my = scale * (s * dx + c * dy)
        offsetX += dx - mx
        offsetY += dy - my
        pivotFracX = nx
        pivotFracY = ny
    }

    /**
     * Terapkan delta rotasi (dengan snap ke 0/90/180/270).
     * @return delta AKTUAL yang diterapkan setelah snap — WAJIB dipakai untuk
     * koreksi offset jangkar gesture. Bila koreksi memakai delta mentah
     * sementara render memakai sudut tersnap, jangkar meleset → teleport.
     */
    fun applyRotationDelta(deltaRotation: Float): Float {
        val before = rotation
        rawRotation = (rawRotation + deltaRotation) % 360f
        val normRotation = if (rawRotation < 0) rawRotation + 360f else rawRotation

        // Snap thresholds at 0, 90, 180, 270 degrees
        val snapThreshold = 5.0f
        rotation = when {
            abs(normRotation - 0f) < snapThreshold || abs(normRotation - 360f) < snapThreshold -> 0f
            abs(normRotation - 90f) < snapThreshold -> 90f
            abs(normRotation - 180f) < snapThreshold -> 180f
            abs(normRotation - 270f) < snapThreshold -> 270f
            else -> normRotation
        }
        return rotation - before
    }

    fun windowToCanvasCoordinates(
        windowX: Float,
        windowY: Float,
        viewportWidth: Float = width.toFloat(),
        viewportHeight: Float = height.toFloat()
    ): Offset {
        // Inverse graphicsLayer ber-pivot dinamis (pivotFrac), bukan tengah viewport.
        val pivotX = pivotFracX * viewportWidth
        val pivotY = pivotFracY * viewportHeight
        val dx = (windowX - pivotX - offsetX) / scale
        val dy = (windowY - pivotY - offsetY) / scale
        val rad = Math.toRadians((-rotation).toDouble())
        val rx = dx * Math.cos(rad) - dy * Math.sin(rad)
        val ry = dx * Math.sin(rad) + dy * Math.cos(rad)
        return Offset((rx + pivotX).toFloat(), (ry + pivotY).toFloat())
    }
}
