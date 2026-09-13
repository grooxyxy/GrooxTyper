package com.grooxtyper.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

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

    fun resetView(viewWidth: Float, viewHeight: Float) {
        val scaleX = viewWidth / width
        val scaleY = viewHeight / height
        scale = minOf(scaleX, scaleY) * 0.85f
        offsetX = (viewWidth - width * scale) / 2f
        offsetY = (viewHeight - height * scale) / 2f
        rotation = 0.0f
    }

    fun windowToCanvasCoordinates(windowX: Float, windowY: Float): Offset {
        // Reverse translate -> rotate -> scale
        val dx = windowX - offsetX
        val dy = windowY - offsetY
        val rad = -Math.toRadians(rotation.toDouble())
        val rx = dx * Math.cos(rad) - dy * Math.sin(rad)
        val ry = dx * Math.sin(rad) + dy * Math.cos(rad)
        return Offset((rx / scale).toFloat(), (ry / scale).toFloat())
    }
}
