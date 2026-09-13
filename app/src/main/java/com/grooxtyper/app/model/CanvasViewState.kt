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

    fun updateRotationWithSnap(deltaRotation: Float) {
        val rawRotation = (rotation + deltaRotation) % 360f
        val normRotation = if (rawRotation < 0) rawRotation + 360f else rawRotation

        // Snap thresholds at 0, 90, 180, 270 degrees
        val snapThreshold = 4.0f
        rotation = when {
            abs(normRotation - 0f) < snapThreshold || abs(normRotation - 360f) < snapThreshold -> 0f
            abs(normRotation - 90f) < snapThreshold -> 90f
            abs(normRotation - 180f) < snapThreshold -> 180f
            abs(normRotation - 270f) < snapThreshold -> 270f
            else -> normRotation
        }
    }

    fun windowToCanvasCoordinates(windowX: Float, windowY: Float): Offset {
        val dx = windowX - offsetX
        val dy = windowY - offsetY
        val rad = -Math.toRadians(rotation.toDouble())
        val rx = dx * Math.cos(rad) - dy * Math.sin(rad)
        val ry = dx * Math.sin(rad) + dy * Math.cos(rad)
        return Offset((rx / scale).toFloat(), (ry / scale).toFloat())
    }
}
