package com.grooxtyper.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, array)
        mutableStateOf(array)
    }

    var hexText by remember {
        mutableStateOf(String.format("#%06X", (0xFFFFFF and initialColor)))
    }

    val currentColorInt = android.graphics.Color.HSVToColor(hsv)

    val paletteColors = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.LightGray, Color.White,
        Color.Red, Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4CAF50),
        Color(0xFF009688), Color(0xFF00BCD4), Color(0xFF2196F3), Color(0xFF3F51B5), Color(0xFF9C27B0)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Picker", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Color Preview Box & Hex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentColorInt))
                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = {
                            hexText = it
                            try {
                                val parsed = android.graphics.Color.parseColor(it)
                                val newHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, newHsv)
                                hsv = newHsv
                            } catch (_: Exception) {}
                        },
                        label = { Text("HEX Code") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Wheel
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = change.position.x - center.x
                                val dy = change.position.y - center.y
                                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val hue = if (angle < 0) angle + 360f else angle
                                val dist = kotlin.math.hypot(dx, dy)
                                val maxRadius = size.width / 2f
                                val sat = (dist / maxRadius).coerceIn(0f, 1f)
                                hsv = floatArrayOf(hue, sat, hsv[2])
                                hexText = String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv)))
                            }
                        }
                ) {
                    val radius = size.width / 2f
                    val center = Offset(radius, radius)
                    val sweepShader = android.graphics.SweepGradient(
                        center.x, center.y,
                        intArrayOf(
                            android.graphics.Color.RED,
                            android.graphics.Color.YELLOW,
                            android.graphics.Color.GREEN,
                            android.graphics.Color.CYAN,
                            android.graphics.Color.BLUE,
                            android.graphics.Color.MAGENTA,
                            android.graphics.Color.RED
                        ),
                        null
                    )
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = sweepShader
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)

                    // Draw selected cursor
                    val rad = Math.toRadians(hsv[0].toDouble())
                    val cursorDist = hsv[1] * radius
                    val cx = center.x + (cursorDist * cos(rad)).toFloat()
                    val cy = center.y + (cursorDist * sin(rad)).toFloat()
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color.Black, radius = 6.dp.toPx(), center = Offset(cx, cy))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Value Slider
                Text("Brightness (V)", color = Color.LightGray, fontSize = 12.sp)
                Slider(
                    value = hsv[2],
                    onValueChange = {
                        hsv = floatArrayOf(hsv[0], hsv[1], it)
                        hexText = String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv)))
                    },
                    valueRange = 0f..1f
                )

                // Color Palette
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(paletteColors.size) { idx ->
                        val c = paletteColors[idx]
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    val newHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c.toArgb(), newHsv)
                                    hsv = newHsv
                                    hexText = String.format("#%06X", (0xFFFFFF and c.toArgb()))
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentColorInt)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Select", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}
