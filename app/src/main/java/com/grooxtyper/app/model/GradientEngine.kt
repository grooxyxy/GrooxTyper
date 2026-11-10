package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Gradient tool ala Photoshop/TypeR.
 *
 * Seret dari titik A ke titik B → isi area (seleksi aktif kalau ada,
 * atau seluruh layer) dengan gradasi antara dua warna:
 *
 *  - **Linear** : mendatar/miring mengikuti arah seret.
 *  - **Radial** : berpusat di titik awal, melebar mengikuti radius seret.
 *
 * Warna awal = warna brush aktif; warna akhir = warna sekunder yang
 * dipilih di options bar (default putih).
 *
 * Bekerja pada level layer (bukan komposit) supaya bisa di-undo seperti
 * operasi lain, dan mengikuti mask seleksi bila ada (ala isi seleksi).
 */
class GradientEngine {

    /** Mode gradasi. */
    enum class Mode { LINEAR, RADIAL }

    var mode by mutableStateOf(Mode.LINEAR)
    /** Warna akhir gradasi (RGB). */
    var endColor by mutableIntStateOf(Color.WHITE)
    /** Reversed: warna brush di ujung yang jauh. */
    var reversed by mutableStateOf(false)
    /** Ikuti woodlands seleksi bila ada. */
    var respectSelection by mutableStateOf(true)
    /** Opacity hasil gradasi (0..1). */
    var opacity by mutableFloatStateOf(1f)

    /**
     * Isi gradasi pada [layer] dari [start] ke [end].
     *
     * @return true bila ada yang tergambar.
     */
    fun apply(
        layer: DrawingLayer,
        start: Offset,
        end: Offset,
        startColor: Int,
        selectionPath: android.graphics.Path?,
        hasSelection: Boolean
    ): Boolean {
        val bmp = layer.getPersistentBitmap()
        if (bmp.width <= 0 || bmp.height <= 0) return false

        val useSelection = respectSelection && hasSelection && selectionPath != null
        val target: RectF = if (useSelection) {
            val b = RectF()
            selectionPath.computeBounds(b, true)
            if (b.isEmpty) RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()) else b
        } else {
            RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        }

        val c1 = if (reversed) endColor else startColor
        val c2 = if (reversed) startColor else endColor

        val shader: Shader = when (mode) {
            Mode.LINEAR -> LinearGradient(
                start.x, start.y, end.x, end.y,
                c1, c2, Shader.TileMode.CLAMP
            )
            Mode.RADIAL -> {
                val r = hypot(end.x - start.x, end.y - start.y).coerceAtLeast(1f)
                RadialGradient(
                    start.x, start.y, r,
                    c1, c2, Shader.TileMode.CLAMP
                )
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            this.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        }

        val canvas = Canvas(bmp)
        return try {
            val save = canvas.save()
            if (useSelection) {
                // Salinan path: selectionPath milik engine dan bisa berubah
                // saat render; clip aman dengan snapshot sendiri.
                val clipPath = android.graphics.Path(selectionPath!!)
                canvas.clipPath(clipPath)
            }
            // Isi seluruh area target supaya gradasi penuh (bukan cuma
            // garis Start-End seperti Photoshop).
            canvas.drawRect(target, paint)
            canvas.restoreToCount(save)
            layer.markDirty()
            true
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sudut arah gradasi (derajat) dari dua titik — dipakai UI untuk
     * menampilkan pratinjau arah.
     */
    fun angleDeg(start: Offset, end: Offset): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (hypot(dx, dy) < 0.001f) return 0f
        var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }
}
