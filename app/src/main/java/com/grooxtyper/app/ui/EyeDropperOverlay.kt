package com.grooxtyper.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private val EyePanel = Color(0xF21C1C1E)
private val EyeBorder = Color(0xFF3A3A3C)
private val EyeAccent = Color(0xFFFF5722)
private val EyeCancel = Color(0xFFEF5350)

/** Posisi & warna yang sedang disorot pipet. */
data class EyeDropperState(
    val color: Int,
    val canvasX: Int,
    val canvasY: Int,
    val screen: Offset,
    val cancel: Boolean = false,
    val outOfCanvas: Boolean = false
)

/**
 * Sesi pipet: mulai saat jari menekan, perbarui selama digeser, commit saat
 * dilepas. Warna TIDAK diterapkan selama jari masih menempel supaya salah
 * tekan bisa dikoreksi dengan menggeser.
 */
class EyeDropperController {
    var state by mutableStateOf<EyeDropperState?>(null)
        private set
    private var target: ((Int) -> Unit)? = null
    private var sampler: ((Int, Int) -> Int?)? = null
    private var toCanvas: ((Float, Float) -> Offset)? = null
    private var cancelZonePx = 0f
    private var startY = 0f

    val active: Boolean get() = state != null

    fun begin(
        screen: Offset,
        cancelZonePx: Float,
        toCanvas: (Float, Float) -> Offset,
        sampler: (Int, Int) -> Int?,
        target: (Int) -> Unit
    ): Boolean {
        val cp = toCanvas(screen.x, screen.y)
        val x = cp.x.toInt()
        val y = cp.y.toInt()
        val color = sampler(x, y) ?: return false
        this.target = target
        this.sampler = sampler
        this.toCanvas = toCanvas
        this.cancelZonePx = cancelZonePx
        this.startY = screen.y
        state = EyeDropperState(color, x, y, screen)
        return true
    }

    fun update(screen: Offset) {
        val current = state ?: return
        val cp = toCanvas?.invoke(screen.x, screen.y) ?: return
        val x = cp.x.toInt()
        val y = cp.y.toInt()
        val color = sampler?.invoke(x, y)
        val cancel = startY > cancelZonePx && screen.y < cancelZonePx
        state = if (color == null) {
            current.copy(screen = screen, cancel = cancel, outOfCanvas = true)
        } else {
            current.copy(
                color = color,
                canvasX = x,
                canvasY = y,
                screen = screen,
                cancel = cancel,
                outOfCanvas = false
            )
        }
    }

    /** Commit warna terakhir. Null bila dibatalkan / di luar kanvas. */
    fun finish(): Int? {
        val current = state ?: return null
        val consumer = target
        reset()
        if (current.cancel || current.outOfCanvas) return null
        consumer?.invoke(current.color)
        return current.color
    }

    fun abort() = reset()

    private fun reset() {
        state = null
        target = null
        sampler = null
        toCanvas = null
        cancelZonePx = 0f
        startY = 0f
    }
}

/** HEX uppercase 6 digit, mis. #FF5722. */
fun hexOfColor(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

private data class BasicColor(val name: String, val value: Int)

private val basicColors = listOf(
    BasicColor("Hitam", 0xFF000000.toInt()),
    BasicColor("Abu-abu gelap", 0xFF424242.toInt()),
    BasicColor("Abu-abu", 0xFF9E9E9E.toInt()),
    BasicColor("Abu-abu muda", 0xFFD6D6D6.toInt()),
    BasicColor("Putih", 0xFFFFFFFF.toInt()),
    BasicColor("Merah", 0xFFE53935.toInt()),
    BasicColor("Merah muda", 0xFFF06292.toInt()),
    BasicColor("Oranye", 0xFFFF7043.toInt()),
    BasicColor("Coklat", 0xFF8D6E63.toInt()),
    BasicColor("Emas", 0xFFFFC107.toInt()),
    BasicColor("Kuning", 0xFFFFEB3B.toInt()),
    BasicColor("Hijau muda", 0xFF9CCC65.toInt()),
    BasicColor("Hijau", 0xFF43A047.toInt()),
    BasicColor("Tosca", 0xFF26C6DA.toInt()),
    BasicColor("Biru muda", 0xFF64B5F6.toInt()),
    BasicColor("Biru", 0xFF1E88E5.toInt()),
    BasicColor("Ungu", 0xFF8E24AA.toInt()),
    BasicColor("Perak", 0xFFB0BEC5.toInt())
)

/** Nama warna terdekat (bahasa Indonesia) untuk HUD pipet. */
fun colorNameOf(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    var best = basicColors.first()
    var bestDistance = Int.MAX_VALUE
    for (c in basicColors) {
        val cr = (c.value shr 16) and 0xFF
        val cg = (c.value shr 8) and 0xFF
        val cb = c.value and 0xFF
        // Bobot mata manusia: hijau paling dominan.
        val dr = r - cr
        val dg = g - cg
        val db = b - cb
        val distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db
        if (distance < bestDistance) {
            bestDistance = distance
            best = c
        }
    }
    return best.name
}

/** Simpan warna hasil pipet ke palet ColorPicker (maks 32, tanpa duplikat). */
fun savePickedColorToPalette(context: Context, color: Int) {
    runCatching {
        val prefs = context.getSharedPreferences("color_palette", Context.MODE_PRIVATE)
        val current = (prefs.getString("colors", "") ?: "")
            .split(',')
            .mapNotNull { it.toIntOrNull() }
        val next = (listOf(color) + current.filter { it != color }).take(32)
        prefs.edit().putString("colors", next.joinToString(",")).apply()
    }
}

/**
 * HUD pipet: loupe pembesaran di atas jari + HEX/RGB/nama warna + strip batal.
 * Tidak menangkap sentuhan; gesture tetap di kanvas.
 */
@Composable
fun EyeDropperOverlay(
    state: EyeDropperState,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val paint = remember { Paint().apply { isFilterBitmap = false; isAntiAlias = false } }
    val swatch = Color(state.color)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val panelWidthPx = with(density) { 176.dp.toPx() }
        val loupePx = with(density) { 132.dp.toPx() }
        val infoPx = with(density) { 96.dp.toPx() }
        val gapPx = with(density) { 26.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val totalH = loupePx + with(density) { 8.dp.toPx() } + infoPx
        val above = state.screen.y > totalH + gapPx + marginPx
        val rawTop = if (above) {
            state.screen.y - totalH - gapPx
        } else {
            state.screen.y + gapPx + with(density) { 42.dp.toPx() }
        }
        val top = rawTop.coerceIn(marginPx, (maxH - totalH - marginPx).coerceAtLeast(marginPx))
        val left = (state.screen.x - panelWidthPx / 2f)
            .coerceIn(marginPx, (maxW - panelWidthPx - marginPx).coerceAtLeast(marginPx))

        // Strip batal di atas layar.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (state.cancel) EyeCancel else EyePanel)
                .border(
                    1.dp,
                    if (state.cancel) Color.White else EyeBorder,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (state.cancel) Color.White else Color.Gray,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        state.cancel -> "Lepas untuk batal"
                        state.outOfCanvas -> "Di luar kanvas"
                        else -> "Geser ke atas untuk batal"
                    },
                    color = if (state.cancel) Color.White else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = if (state.cancel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(176.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(
                        3.dp,
                        if (state.cancel) EyeCancel else swatch,
                        CircleShape
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLoupe(bitmap, state, paint)
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EyePanel)
                    .border(1.dp, EyeBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(swatch)
                            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            hexOfColor(state.color),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            colorNameOf(state.color),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "R ${(state.color shr 16) and 0xFF} · G ${(state.color shr 8) and 0xFF} · B ${state.color and 0xFF}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
                Text(
                    "x ${state.canvasX} · y ${state.canvasY}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Text(
                    if (state.cancel) "Lepas untuk batal"
                    else if (state.outOfCanvas) "Geser kembali ke kanvas"
                    else "Lepas untuk pakai warna",
                    color = if (state.cancel) EyeCancel else EyeAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun DrawScope.drawLoupe(bitmap: Bitmap?, state: EyeDropperState, paint: Paint) {
    if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
        drawRect(Color(0xFF2C2C2E))
        return
    }
    val sourcePx = 24
    val half = sourcePx / 2
    val left = (state.canvasX - half).coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
    val top = (state.canvasY - half).coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
    val srcW = min(sourcePx, bitmap.width - left).coerceAtLeast(1)
    val srcH = min(sourcePx, bitmap.height - top).coerceAtLeast(1)
    val src = Rect(left, top, left + srcW, top + srcH)
    val dst = RectF(0f, 0f, size.width, size.height)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(bitmap, src, dst, paint)
    }

    // Grid bantu + kotak piksel tengah.
    val step = size.width / 8f
    for (i in 1..7) {
        val p = step * i
        drawLine(Color.White.copy(alpha = 0.18f), Offset(p, 0f), Offset(p, size.height), 1f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, p), Offset(size.width, p), 1f)
    }
    val cx = size.width / 2f
    val cy = size.height / 2f
    val cell = size.width / sourcePx.toFloat()
    drawRect(
        color = Color.White,
        topLeft = Offset(cx - cell / 2f, cy - cell / 2f),
        size = Size(cell, cell),
        style = Stroke(width = 2f)
    )
    drawLine(Color.Black.copy(alpha = 0.8f), Offset(cx - 10f, cy), Offset(cx + 10f, cy), 3f)
    drawLine(Color.Black.copy(alpha = 0.8f), Offset(cx, cy - 10f), Offset(cx, cy + 10f), 3f)
    drawLine(Color.White, Offset(cx - 9f, cy), Offset(cx + 9f, cy), 1.2f)
    drawLine(Color.White, Offset(cx, cy - 9f), Offset(cx, cy + 9f), 1.2f)
}

/** Banner hasil pipet: HEX + nama warna + tombol salin. */
data class ColorPickBanner(val color: Int)

@Composable
fun ColorPickBannerBar(
    banner: ColorPickBanner,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember(banner.color) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EyePanel)
            .border(1.dp, EyeBorder, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(banner.color))
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Warna diambil", color = Color.Gray, fontSize = 10.sp)
            Text(
                "${hexOfColor(banner.color)} · ${colorNameOf(banner.color)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("Warna", hexOfColor(banner.color))
                )
                copied = true
            }
        ) {
            Text(
                if (copied) "Disalin" else "Salin",
                color = if (copied) Color(0xFF81C784) else EyeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Tutup",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
