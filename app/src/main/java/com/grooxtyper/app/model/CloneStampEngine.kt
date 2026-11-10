package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Clone stamp ala Photoshop/TypeR.
 *
 * Alur di layar sentuh (tanpa tombol Alt seperti di desktop):
 *
 *  1. Ketuk kanvas pada tool Clone Stamp → titik itu jadi **sumber**.
 *  2. Geser untuk menyalin piksel sumber ke posisi baru.
 *
 * Offset sumber tetap selama satu sapuan (mode *aligned*), sehingga
 * sapuan panjang tidak "mengejar" sumber sendiri seperti mode tak
 * selaras. Sumber dibaca langsung dari layer aktif, jadi tidak ada
 * snapshot 46MB seperti kanvas 720x16000.
 *
 * Ujung sapuan bulat dengan tepi lembut (radial gradient), ukuran dan
 * opacity bisa diatur; semua buffer kerja di-reuse supaya tidak alokasi
 * bitmap tiap event.
 */
class CloneStampEngine {

    /** Titik sumber di koordinat kanvas; null = belum ditetapkan. */
    var sourcePoint by mutableStateOf<Offset?>(null)
        private set

    /** Diameter stamp (piksel kanvas). */
    var stampSize by mutableFloatStateOf(48f)

    /** 0 = tepi keras, 1 = tepi sangat lembut. */
    var hardness by mutableFloatStateOf(0.5f)

    /** Opacity hasil stamp (0..1). */
    var opacity by mutableFloatStateOf(1f)

    /** Geser offset sumber selama sapuan berjalan. */
    private var strokeOffset: Offset? = null

    // Buffer kerja (dialokasi ulang hanya bila ukuran berubah).
    private var scratchA: Bitmap? = null
    private var scratchB: Bitmap? = null
    private var scratchCanvasA: Canvas? = null
    private var scratchCanvasB: Canvas? = null
    private var maskBmp: Bitmap? = null
    private var scratchDim = 0
    private val copyPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val stampPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Tetapkan titik sumber baru (ketukan pertama pada tool clone). */
    fun setSource(p: Offset) {
        sourcePoint = p
        strokeOffset = null
    }

    /** Hapus sumber (tool diganti / reset). */
    fun clearSource() {
        sourcePoint = null
        strokeOffset = null
    }

    /** Awal sapuan: offset dihitung dari titik sentuh pertama. */
    fun beginStroke() {
        strokeOffset = null
    }

    /** Akhir sapuan: offset dilepas supaya sapuan berikutnya mulai baru. */
    fun endStroke() {
        strokeOffset = null
    }

    /**
     * Salin piksel dari sumber ke [p2] (dari [p1]) pada layer [layer].
     *
     * @return true bila ada piksel yang tersalin.
     */
    fun stampOnLayer(layer: DrawingLayer, p1: Offset, p2: Offset): Boolean {
        val src = sourcePoint ?: return false
        val bmp = layer.getPersistentBitmap()
        if (bmp.width <= 0 || bmp.height <= 0) return false

        // Offset sumber selama sapuan ini: tetap sebesar (sumber - titik
        // sentuh pertama) supaya area yang disalin bergeser bersama sapuan.
        val off = strokeOffset ?: (src - p1).also { strokeOffset = it }

        val radius = (stampSize / 2f).coerceIn(2f, 256f)
        val dim = (radius * 2f).toInt().coerceIn(4, 512)
        if (!ensureScratch(dim)) return false
        if (ensureMask(dim, radius) == null) return false
        val bufA = scratchA ?: return false
        val bufB = scratchB ?: return false
        val bufCanvasA = scratchCanvasA ?: return false
        val bufCanvasB = scratchCanvasB ?: return false

        val dist = hypot(p2.x - p1.x, p2.y - p1.y)
        val spacing = max(1.5f, radius * 0.35f)
        val steps = ceil((dist / spacing).toDouble()).toInt().coerceIn(1, 256)
        val canvas = Canvas(bmp)
        stampPaint.alpha = (opacity * 255).toInt().coerceIn(1, 255)

        var copied = false
        try {
            for (i in 0..steps) {
                val t = if (steps == 0) 0f else i / steps.toFloat()
                val x = p1.x + (p2.x - p1.x) * t
                val y = p1.y + (p2.y - p1.y) * t
                // Posisi sumber = tujuan + offset.
                val sx = x + off.x
                val sy = y + off.y
                val left = (sx - radius).toInt()
                val top = (sy - radius).toInt()
                // Sumber di luar kanvas → tak ada yang bisa disalin.
                if (left < 0 || top < 0 ||
                    left + dim > bmp.width || top + dim > bmp.height
                ) continue
                val destLeft = (x - radius).toInt()
                val destTop = (y - radius).toInt()

                // 1) Potretkan sumber ke buffer A.
                bufA.eraseColor(0)
                bufCanvasA.drawBitmap(
                    bmp,
                    Rect(left, top, left + dim, top + dim),
                    Rect(0, 0, dim, dim),
                    copyPaint
                )
                // 2) Terapkan mask bulat (tepi lembut sesuai hardness).
                val mask = maskBmp ?: continue
                bufB.eraseColor(0)
                bufCanvasB.drawBitmap(bufA, 0f, 0f, null)
                bufCanvasB.drawBitmap(mask, 0f, 0f, maskPaint)
                // 3) Tempel ke layer.
                canvas.drawBitmap(bufB, destLeft.toFloat(), destTop.toFloat(), stampPaint)
                copied = true
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (copied) layer.markDirty()
        return copied
    }

    /** Gradien radial untuk mask bulat; di-cache per ukuran+hardness. */
    private var maskKey = ""

    /**
     * Bangun bitmap mask bulat (putih opaque di tengah, transparan di tepi).
     *
     * Dibuat ulang hanya bila ukuran atau hardness berubah, jadi tidak
     * alokasi tiap event pointer.
     */
    private fun ensureMask(dim: Int, radius: Float): Bitmap? {
        val key = "$dim:$radius:$hardness"
        if (key == maskKey) return maskBmp
        return try {
            val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // hardness 1 = tepi keras (softness kecil), 0 = tepi sangat lembut.
            val softness = ((1f - hardness) * radius * 0.9f).coerceAtLeast(0.5f)
            val center = dim / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    center, center, radius.coerceAtLeast(1f),
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(
                        0f,
                        ((radius - softness) / radius).coerceIn(0f, 0.99f),
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(center, center, radius, p)
            runCatching { maskBmp?.recycle() }
            maskBmp = bmp
            maskKey = key
            bmp
        } catch (e: OutOfMemoryError) {
            maskBmp = null
            maskKey = ""
            null
        } catch (e: Exception) {
            maskBmp = null
            maskKey = ""
            null
        }
    }

    /** Alokasikan buffer kerja sized [dim] (dibuat ulang bila berubah). */
    private fun ensureScratch(dim: Int): Boolean {
        if (scratchDim == dim && scratchA != null && scratchB != null) return true
        runCatching { scratchA?.recycle() }
        runCatching { scratchB?.recycle() }
        return try {
            scratchA = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            scratchB = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            scratchCanvasA = Canvas(scratchA!!)
            scratchCanvasB = Canvas(scratchB!!)
            scratchDim = dim
            true
        } catch (e: OutOfMemoryError) {
            scratchA = null
            scratchB = null
            scratchCanvasA = null
            scratchCanvasB = null
            scratchDim = 0
            false
        } catch (e: Exception) {
            scratchA = null
            scratchB = null
            scratchCanvasA = null
            scratchCanvasB = null
            scratchDim = 0
            false
        }
    }

    /** Buang buffer (dipakai saat layer ditutup). */
    fun release() {
        runCatching { scratchA?.recycle() }
        runCatching { scratchB?.recycle() }
        runCatching { maskBmp?.recycle() }
        scratchA = null
        scratchB = null
        scratchCanvasA = null
        scratchCanvasB = null
        maskBmp = null
        scratchDim = 0
        maskKey = ""
    }
}
