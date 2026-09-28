package com.grooxtyper.app.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.grooxtyper.app.model.PerspectiveGrid
import com.grooxtyper.app.model.TextBox
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay kanvas untuk mode perspektif & kotak seleksi teks. Dipisah dari
 * badan [CanvasEditorScreen] karena fungsi utama itu sudah menyentuh batas
 * method JVM 64KB (build gagal "Method too large" kalau isinya ditambah).
 */
object EditorOverlays {

    /**
     * Grid perspektif: 2 garis penanda + garis tepi + 4 titik handle sudut
     * (titik yang sama persis dengan yang dipakai hit-test gestur).
     */
    @Composable
    fun perspectiveGrid(box: TextBox, toScreen: (Offset) -> Offset) {
        val area = PerspectiveGrid.contentRect(box)
        val dst = PerspectiveGrid.dstPoints(box, area.width(), area.height())
        val corners = PerspectiveGrid.cornersCanvas(box)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Titik lokal (u,v) di dalam trapesium -> koordinat kanvas.
            fun toCanvas(u: Float, v: Float): Offset {
                val p = PerspectiveGrid.bilerp(dst, u, v)
                val rad = Math.toRadians(box.rotation.toDouble())
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                return Offset(
                    area.centerX() + p.x * c - p.y * s,
                    area.centerY() + p.x * s + p.y * c
                )
            }
            fun scr(u: Float, v: Float): Offset = toScreen(toCanvas(u, v))
            val grid = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.2f
                color = 0x88FFFFFF.toInt()
            }
            val va = scr(0.5f, 0f)
            val vb = scr(0.5f, 1f)
            drawContext.canvas.nativeCanvas.drawLine(va.x, va.y, vb.x, vb.y, grid)
            val ha = scr(0f, 0.5f)
            val hb = scr(1f, 0.5f)
            drawContext.canvas.nativeCanvas.drawLine(ha.x, ha.y, hb.x, hb.y, grid)
            val border = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.5f
                color = 0xFF00E5FF.toInt()
            }
            val scr2 = corners.map { toScreen(it) }
            for (i in 0..3) {
                val a = scr2[i]
                val c = scr2[(i + 1) % 4]
                drawContext.canvas.nativeCanvas.drawLine(a.x, a.y, c.x, c.y, border)
            }
            val dot = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = 0xFF00E5FF.toInt()
            }
            scr2.forEach { drawContext.canvas.nativeCanvas.drawCircle(it.x, it.y, 15f, dot) }
        }
    }

    /** Panel gaya per kata di dasar layar (dibungkus agar call-site ramping). */
    @Composable
    fun richTextPanel(
        box: TextBox,
        fonts: List<Pair<String, android.graphics.Typeface>>,
        defaultColor: Int,
        onApply: (TextBox) -> Unit,
        onClose: () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            RichTextPanel(
                box = box,
                fonts = fonts,
                defaultColor = defaultColor,
                onApply = { onApply(box) },
                onClose = onClose
            )
        }
    }

    /** Pratinjau kotak seleksi teks yang sedang diseret (tool Teks). */
    @Composable
    fun textFramePreview(rect: RectF?, toScreen: (Offset) -> Offset) {
        if (rect == null || rect.width() <= 4f) return
        Canvas(modifier = Modifier.fillMaxSize()) {
            val a = toScreen(Offset(rect.left, rect.top))
            val b = toScreen(Offset(rect.right, rect.bottom))
            val p = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
                color = 0xFF00E5FF.toInt()
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 6f), 0f)
            }
            drawContext.canvas.nativeCanvas.drawRect(a.x, a.y, b.x, b.y, p)
        }
    }
}

/**
 * Dua tombol tambahan tool Teks di toolbar utama: Edit Teks Massal (ubah
 * banyak kotak teks sekaligus) dan Gaya Per Kata (beda font/warna per kata).
 */
@Composable
fun TextToolsExtra(
    bulkActive: Boolean,
    richActive: Boolean,
    onBulk: () -> Unit,
    onRich: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        IconButton(onClick = onBulk) {
            Icon(
                Icons.Default.FormatSize,
                contentDescription = "Edit Teks Massal",
                tint = if (bulkActive) Color(0xFFFF5722) else Color.White
            )
        }
        IconButton(onClick = onRich) {
            Icon(
                Icons.Default.FormatColorText,
                contentDescription = "Gaya Per Kata",
                tint = if (richActive) Color(0xFFFF5722) else Color.White
            )
        }
    }
}
