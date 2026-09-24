package com.grooxtyper.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renderer jendela reference: menggambar bitmap PINGGIAN per strip.
 *
 * Kenapa tidak `Image(bitmap=...)` seperti versi lama:
 *  - Compose meng-upload bitmap penuh sebagai satu texture. Untuk 720x16000
 *    itu melewati batas texture sebagian besar GPU → gambar tidak tampil,
 *    atau hanya separuh yang muncul di dalam jendela.
 *  - Strip 2048px per drawayun jauh di bawah batas GPU, dan karena Rect
 *    sumber sama tiap frame, tidak ada alokasi pada frame baru (aman 60fps).
 */
object ReferenceStripRenderer {
    /** Panjang strip (px sumber) per draw — jauh di bawah batas texture GPU. */
    const val STRIP = 2048

    /** Skala dasar "pas ke layar" (contain) dikali zoom user. */
    private fun effectiveScale(bmp: Bitmap, destW: Float, destH: Float, zoom: Float): Float {
        val fit = min(destW / bmp.width.toFloat(), destH / bmp.height.toFloat())
        return (fit * zoom).coerceAtLeast(0.0001f)
    }

    /** Ukuran konten setelah zoom — dipakai untuk clamp pan. */
    fun contentSize(bmp: Bitmap, destW: Float, destH: Float, zoom: Float): Pair<Float, Float> {
        val s = effectiveScale(bmp, destW, destH, zoom)
        return bmp.width * s to bmp.height * s
    }

    /**
     * Gambar [bmp] hanya strip yang terlihat. [offsetX/Y] adalah posisi
     * konten dalam px view (sudah termasuk pan).
     */
    fun draw(
        native: android.graphics.Canvas,
        bmp: Bitmap,
        destW: Float,
        destH: Float,
        offsetX: Float,
        offsetY: Float,
        zoom: Float,
        pixelPerfect: Boolean,
        viewW: Float,
        viewH: Float
    ) {
        if (bmp.isRecycled) return
        val s = effectiveScale(bmp, destW, destH, zoom)
        val fullW = bmp.width * s
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = !pixelPerfect
            isAntiAlias = !pixelPerfect
            isDither = !pixelPerfect
        }
        val firstStrip = ((-offsetY) / s / STRIP).toInt().coerceAtLeast(0)
        val lastStrip = ((viewH - offsetY) / s / STRIP).toInt().coerceAtMost(bmp.height / STRIP)
        var y = firstStrip
        while (y <= lastStrip) {
            val srcY = y * STRIP
            val srcH = min(STRIP, bmp.height - srcY)
            if (srcH <= 0) break
            val dstTop = offsetY + srcY * s
            val dst = android.graphics.RectF(offsetX, dstTop, offsetX + fullW, dstTop + srcH * s)
            val src = android.graphics.Rect(0, srcY, bmp.width, srcY + srcH)
            try {
                native.drawBitmap(bmp, src, dst, paint)
            } catch (e: Exception) {
                // Perangkat dengan batas texture lebih ketat: lewati strip ini
                // alih-alih membuat seluruh jendela kosong.
                e.printStackTrace()
            }
            y += STRIP
        }
    }
}

/**
 * Jendela Reference ala ibisPaint / Clip Studio Paint.
 *
 * Perbedaan penting dari versi lama (jendela 220dp, zoom 0.5-4x, pan cuma
 * menggeser jendela):
 *  - Zoom 0.2%..4000% sehingga kanvas 720x16000 bisa di-scroll penuh dan
 *    diperbesar sampai detail piksel.
 *  - Gambar pinggiran dirender per-strip (lihat [ReferenceStripRenderer]) agar
 *    tidak di-upload sebagai satu texture tunggal yang gagal di GPU.
 *  - Jendelanya bisa digeser judulnya, diperbesar dari pojok, dan punya
 *    kontrol zoom, opasitas, tombol "pas ke layar" serta mode piksel (tegas).
 *  - Sumber gambar sudah diturunkan ke budget byte (default 2MB) oleh
 *    ImageImport.decodeForReference, jadi file 4MB tetap boleh dipakai asal
 *    hasil turunnya masih jernih.
 */
@Composable
fun ReferenceWindow(
    referenceBitmap: Bitmap?,
    onClose: () -> Unit,
    onPickImage: () -> Unit,
    isLoading: Boolean = false,
    loadError: String? = null,
    modifier: Modifier = Modifier
) {
    if (referenceBitmap == null && !isLoading && loadError == null) return

    var winX by remember { mutableFloatStateOf(24f) }
    var winY by remember { mutableFloatStateOf(96f) }
    var winW by remember { mutableFloatStateOf(340f) }
    var winH by remember { mutableFloatStateOf(480f) }
    var expanded by remember { mutableStateOf(true) }

    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var pixelPerfect by remember { mutableStateOf(false) }

    // Jaga agar konten tak pernah terkunci di luar jendela.
    fun clampPan(cw: Float, ch: Float, vw: Float, vh: Float): Pair<Float, Float> {
        val margin = 48f
        val nx = if (cw <= vw + margin) min(0f, vw - cw) - margin else panX.coerceIn(vw - cw + margin, 0f)
        val ny = if (ch <= vh + margin) min(0f, vh - ch) - margin else panY.coerceIn(vh - ch + margin, 0f)
        return nx to ny
    }

    Box(
        modifier = modifier
            .offset { IntOffset(winX.roundToInt(), winY.roundToInt()) }
            .width(winW.dp)
            .height(if (expanded) winH.dp else 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF01E1E22))
            .border(1.dp, Color(0xFF4A4A52), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Judul: seret untuk memindahkan jendela, ketuk untuk lipat.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF2A2A31))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, _, _ ->
                            winX += pan.x
                            winY += pan.y
                        }
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reference",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 6.dp)
                )
                IconButton(onClick = onPickImage, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Ganti gambar", tint = Color.LightGray)
                }
                IconButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset zoom", tint = Color.LightGray)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.LightGray)
                }
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF141416))
                        .pointerInput(referenceBitmap) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val bmp = referenceBitmap ?: return@detectTransformGestures
                                val vw = size.width.toFloat()
                                val vh = size.height.toFloat()
                                if (zoomChange != 1f) {
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * zoomChange).coerceIn(0.002f, 40f)
                                    val fit = min(winW / bmp.width.toFloat(), winH / bmp.height.toFloat())
                                    val oldS = (fit * oldZoom).coerceAtLeast(0.0001f)
                                    val newS = (fit * newZoom).coerceAtLeast(0.0001f)
                                    val ratio = newS / oldS
                                    // Tahan titik tengah agar zoom terasa natural
                                    // (tidak "melompat" keluar jendela).
                                    val fx = size.width / 2f
                                    val fy = size.height / 2f
                                    panX = fx - (fx - panX - pan.x) * ratio
                                    panY = fy - (fy - panY - pan.y) * ratio
                                    zoom = newZoom
                                } else {
                                    panX += pan.x
                                    panY += pan.y
                                }
                                val (cw, ch) = ReferenceStripRenderer.contentSize(bmp, winW, winH, zoom)
                                val (nx, ny) = clampPan(cw, ch, vw, vh)
                                panX = nx
                                panY = ny
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Text("Memuat gambar referensi...", color = Color.LightGray, fontSize = 12.sp)
                    } else if (loadError != null) {
                        Text(loadError, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                    if (referenceBitmap != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Opasitas jendela lewat alpha paint.
                            drawContext.canvas.nativeCanvas.saveLayerAlpha(
                                0f, 0f, size.width, size.height,
                                (opacity * 255).roundToInt().coerceIn(0, 255)
                            )
                            ReferenceStripRenderer.draw(
                                native = drawContext.canvas.nativeCanvas,
                                bmp = referenceBitmap,
                                destW = winW,
                                destH = winH,
                                offsetX = panX,
                                offsetY = panY,
                                zoom = zoom,
                                pixelPerfect = pixelPerfect,
                                viewW = size.width,
                                viewH = size.height
                            )
                            drawContext.canvas.nativeCanvas.restore()
                        }
                    }
                }

                // Kontrol zoom / pas-ke-layar / opasitas / mode piksel.
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { zoom = (zoom / 1.5f).coerceAtLeast(0.002f) },
                            modifier = Modifier.size(28.dp)
                        ) { Icon(Icons.Default.Remove, "Perkecil", tint = Color.White) }
                        Text(
                            "${(zoom * 100).roundToInt()}%",
                            color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.width(52.dp)
                        )
                        IconButton(
                            onClick = { zoom = (zoom * 1.5f).coerceAtMost(40f) },
                            modifier = Modifier.size(28.dp)
                        ) { Icon(Icons.Default.Add, "Perbesar", tint = Color.White) }
                        IconButton(
                            onClick = { zoom = 1f; panX = 0f; panY = 0f },
                            modifier = Modifier.size(28.dp)
                        ) { Icon(Icons.Default.CenterFocusStrong, "Pas ke layar", tint = Color.White) }
                        TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) {
                            Text("Pas", color = Color(0xFF00E5FF), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Piksel", color = Color.LightGray, fontSize = 10.sp)
                        Switch(
                            checked = pixelPerfect,
                            onCheckedChange = { pixelPerfect = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Opasitas", color = Color.LightGray, fontSize = 10.sp)
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.1f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }
                    if (referenceBitmap != null) {
                        Text(
                            "${referenceBitmap.width}x${referenceBitmap.height}px • budget 2MB",
                            color = Color(0xFF9E9EA6), fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Pojok kanan-bawah: seret untuk mengubah ukuran jendela.
        if (expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, _, _ ->
                            winW = (winW + pan.x).coerceIn(220f, 1400f)
                            winH = (winH + pan.y).coerceIn(220f, 1800f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF6A6A72))
                )
            }
        }
    }
}
