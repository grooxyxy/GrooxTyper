package com.grooxtyper.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Color picker BARU ("Spectrum Studio") — pengganti color wheel lama.
 *
 * Desain: preview warna lama-vs-baru, bidang gradien Saturasi×Value yang
 * besar, slider spektrum hue, input HEX, palet cepat, dan warna terakhir.
 * Semua hit-test memakai ukuran RIIL komponen (PointerInputScope.size),
 * bukan dp hardcode — tidak ada dead-zone, tidak ada lompatan hue, dan
 * akurat di semua kepadatan layar.
 */
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

    var recents by remember { mutableStateOf(listOf<Int>()) }

    fun syncHex() {
        hexText = String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv)))
    }

    val currentColorInt = android.graphics.Color.HSVToColor(hsv)
    val huePure = Color.hsv(hsv[0], 1f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Warna", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Preview split: warna LAMA (kiri) vs BARU (kanan).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF38383A), RoundedCornerShape(10.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(initialColor))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(currentColorInt))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bidang Saturasi (X) × Value (Y) — ketuk & seret bebas.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, huePure)))
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                hsv = floatArrayOf(
                                    hsv[0],
                                    (pos.x / w).coerceIn(0f, 1f),
                                    (1f - pos.y / h).coerceIn(0f, 1f)
                                )
                                syncHex()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                hsv = floatArrayOf(
                                    hsv[0],
                                    (change.position.x / w).coerceIn(0f, 1f),
                                    (1f - change.position.y / h).coerceIn(0f, 1f)
                                )
                                syncHex()
                                change.consume()
                            }
                        }
                ) {
                    // Lapisan gelap vertikal (value) di atas gradasi saturasi.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    )
                    // Kursor bidang SV.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = hsv[1] * size.width
                        val cy = (1f - hsv[2]) * size.height
                        drawCircle(Color.White, radius = 12f, center = Offset(cx, cy), style = Stroke(width = 4f))
                        drawCircle(Color.Black, radius = 12f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
                        drawCircle(Color(currentColorInt), radius = 8.5f, center = Offset(cx, cy))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Slider spektrum hue — ketuk & seret.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                // 0.9999 (bukan 1f) supaya hue tidak wrap 360->0.
                                val h = (pos.x / w).coerceIn(0f, 0.9999f) * 360f
                                hsv = floatArrayOf(h, hsv[1], hsv[2])
                                syncHex()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = (change.position.x / w).coerceIn(0f, 0.9999f) * 360f
                                hsv = floatArrayOf(h, hsv[1], hsv[2])
                                syncHex()
                                change.consume()
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val spectrum = (0..6).map { Color.hsv(it * 60f, 1f, 1f) }
                        drawRect(brush = Brush.horizontalGradient(spectrum))
                        val hx = (hsv[0] / 360f).coerceIn(0f, 1f) * size.width
                        val cy = size.height / 2f
                        drawCircle(Color.White, radius = 11f, center = Offset(hx, cy), style = Stroke(width = 4f))
                        drawCircle(Color.Black, radius = 11f, center = Offset(hx, cy), style = Stroke(width = 1.5f))
                        drawCircle(huePure, radius = 7f, center = Offset(hx, cy))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input HEX.
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
                    label = { Text("HEX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF101012),
                        unfocusedContainerColor = Color(0xFF101012),
                        cursorColor = Color(0xFFFF5722),
                        focusedLabelColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF38383A)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Warna terakhir dipakai.
                if (recents.isNotEmpty()) {
                    Text("Terakhir dipakai", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        recents.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(1.dp, Color.Gray, CircleShape)
                                    .clickable {
                                        val newHsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(c, newHsv)
                                        hsv = newHsv
                                        syncHex()
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Palet cepat.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(Color.Black, Color.White, Color.Red, Color.Yellow, Color.Green, Color.Blue).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    val newHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c.toArgb(), newHsv)
                                    hsv = newHsv
                                    syncHex()
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    recents = (listOf(currentColorInt) + recents).distinct().take(8)
                    onColorSelected(currentColorInt)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Text("Pilih", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}
