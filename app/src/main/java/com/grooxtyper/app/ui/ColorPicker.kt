package com.grooxtyper.app.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Color wheel gaya aplikasi gambar (ibisPaint/MediBang):
 * cincin hue di luar + kotak saturasi×value di dalam + warna terakhir.
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

    // Bitmap kotak SV digambar ulang hanya saat hue berubah (hemat).
    val svBmp: Bitmap = remember(hsv[0].toInt()) {
        val n = 144
        val px = IntArray(n * n)
        val tmp = FloatArray(3)
        for (y in 0 until n) {
            val v = 1f - y / (n - 1f)
            for (x in 0 until n) {
                tmp[0] = hsv[0]
                tmp[1] = x / (n - 1f)
                tmp[2] = v
                px[y * n + x] = android.graphics.Color.HSVToColor(tmp)
            }
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Warna", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Preview + HEX
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
                        label = { Text("HEX") },
                        modifier = Modifier.weight(1f),
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Roda hue + kotak SV (ketuk & seret).
                WheelCanvas(
                    hsv = hsv,
                    svBmp = svBmp,
                    onPick = { h, s, v ->
                        hsv = floatArrayOf(h, s, v)
                        syncHex()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    Spacer(modifier = Modifier.height(4.dp))
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

/** Kanvas roda hue + kotak SV dengan gesture ketuk/seret. */
@Composable
private fun WheelCanvas(
    hsv: FloatArray,
    svBmp: Bitmap,
    onPick: (Float, Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val geom = remember(density) {
        with(density) {
            // view 248dp; semua dalam px agar hit-test dan gambar sama persis.
            val viewPx = 248.dp.toPx()
            val radius = viewPx / 2f
            val ringW = 26.dp.toPx()
            val gap = 6.dp.toPx()
            val innerR = radius - ringW - gap
            val half = innerR / sqrt(2f)
            WheelGeom(viewPx, radius, ringW, innerR, half)
        }
    }
    Canvas(
        modifier = Modifier
            .size(248.dp)
            .pointerInput(geom) {
                detectTapGestures { pos ->
                    wheelPick(pos, geom, hsv, onPick)
                }
            }
            .pointerInput(geom) {
                detectDragGestures { change, _ ->
                    wheelPick(change.position, geom, hsv, onPick)
                }
            }
    ) {
        val center = Offset(geom.radius, geom.radius)
        val left = center.x - geom.half
        val top = center.y - geom.half

        // Cincin hue: gradasi HSV per 10° diselaraskan TEPAT dengan
        // wheelPick() (0° = kanan, searah jarum jam) sehingga warna yang
        // disentuh selalu sama dengan warna yang terpilih — tanpa lompatan.
        val hueStops = FloatArray(37) { i -> i / 36f }
        val hueColors = IntArray(37) { i ->
            android.graphics.Color.HSVToColor(floatArrayOf(i * 10f, 1f, 1f))
        }
        val sweep = android.graphics.SweepGradient(center.x, center.y, hueColors, hueStops)
        val ringPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            shader = sweep
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = geom.ringW
        }
        drawContext.canvas.nativeCanvas.drawCircle(
            center.x, center.y, geom.radius - geom.ringW / 2f, ringPaint
        )

        // Kotak SV.
        drawImage(
            image = svBmp.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(svBmp.width, svBmp.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize((geom.half * 2).toInt(), (geom.half * 2).toInt())
        )
        drawRect(
            color = Color(0x66FFFFFF),
            topLeft = Offset(left, top),
            size = Size(geom.half * 2, geom.half * 2),
            style = Stroke(width = 3f)
        )

        // Kursor cincin.
        val rad = Math.toRadians(hsv[0].toDouble())
        val ringR = geom.radius - geom.ringW / 2f
        val rcx = center.x + (ringR * cos(rad)).toFloat()
        val rcy = center.y + (ringR * sin(rad)).toFloat()
        drawCircle(Color.White, radius = 18f, center = Offset(rcx, rcy))
        drawCircle(Color.Black, radius = 14f, center = Offset(rcx, rcy))

        // Kursor kotak.
        val scx = left + hsv[1] * geom.half * 2
        val scy = top + (1f - hsv[2]) * geom.half * 2
        drawCircle(Color.White, radius = 14f, center = Offset(scx, scy))
        drawCircle(
            Color(android.graphics.Color.HSVToColor(hsv)),
            radius = 10f, center = Offset(scx, scy)
        )
    }
}

private data class WheelGeom(
    val viewPx: Float,
    val radius: Float,
    val ringW: Float,
    val innerR: Float,
    val half: Float
)

/**
 * Terjemahkan sentuhan roda menjadi HSV (cincin = hue, kotak = sat/val).
 * Dead-zone antar zona: sentuhan di celah (radius antara tepi luar kotak
 * dan tepi dalam cincin) diabaikan agar saat menggeser saturasi di kotak
 * ke arah tepi, hue tidak ikut berubah (bug lompat ke merah).
 * Cincin dipetakan dengan sistem clamp agar hue mengunci lancar ke nilai
 * terdekat tepat di sekitar ring tanpa pernah melompat ke 0°.
 */
private fun wheelPick(
    pos: Offset,
    geom: WheelGeom,
    hsv: FloatArray,
    onPick: (Float, Float, Float) -> Unit
) {
    val dx = pos.x - geom.radius
    val dy = pos.y - geom.radius
    val dist = hypot(dx, dy)
    val ringInner = geom.radius - geom.ringW
    // Zona: > ringInner = cincin hue; <= innerR*0.98 = kotak SV;
    // di antaranya = dead-zone (tidak melakukan apa-apa).
    when {
        dist > ringInner -> {
            // Sudut atan2: 0° = kanan, searah jarum jam (y ke bawah).
            // SweepGradient juga searah jarum jam dari kanan -> cocok 1:1.
            var hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (hue < 0) hue += 360f
            // Clamp halus agar tidak wrap merah<->magenta: hue tetap 0..359.9
            // (tepat 360 dibawa ke 359.9 agar tidak "kelempar" ke merah).
            if (hue >= 360f) hue = 359.9f
            onPick(hue, hsv[1], hsv[2])
        }
        dist <= geom.innerR * 0.98f -> {
            val s = ((pos.x - (geom.radius - geom.half)) / (geom.half * 2)).coerceIn(0f, 1f)
            val v = (1f - (pos.y - (geom.radius - geom.half)) / (geom.half * 2)).coerceIn(0f, 1f)
            onPick(hsv[0], s, v)
        }
        else -> {
            // Dead-zone: abaikan supaya gesture di celah tidak merusak pilihan.
        }
    }
}
