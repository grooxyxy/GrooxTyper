package com.grooxtyper.app.model

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sumber tunggal untuk geometri perspe teks: dipakai renderer (hasil
 * akhir), overlay grid (pratinjau) dan gesture (hit-test handle). Dulu
 * ketiganya menghitung sendiri-sendiri sehingga titik biru yang terlihat
 * tak sama dengan area sentuhnya.
 *
 * Perspektif = homografi 4 titik (tiap sudut digeser sendiri), jadi arah
 * perspektif benar-benar bebas: bukan cuma menyempitkan tepi atas/bawah.
 */
object PerspectiveGrid {

    /** Area konten teks dalam koordinat kanvas (kotak frame bila ada). */
    fun contentRect(box: TextBox): RectF {
        val fr = box.frameRectPx()
        if (fr != null && fr.width().isFinite() && fr.height().isFinite()) return fr
        val (w0, h0) = box.contentSize()
        val w = if (w0.isFinite()) w0 else 0f
        val h = if (h0.isFinite()) h0 else 0f
        val rad = Math.toRadians(box.rotation.toDouble())
        val c = kotlin.math.abs(cos(rad)).toFloat()
        val s = kotlin.math.abs(sin(rad)).toFloat()
        val hw = w / 2f
        val hh = h / 2f
        val ew = hw * c + hh * s
        val eh = hw * s + hh * c
        return RectF(
            box.position.x - ew, box.position.y - eh,
            box.position.x + ew, box.position.y + eh
        )
    }

    /**
     * Titik tujuan 4 sudut konten dalam RUANG LOKAL (pusat kotak = 0,0),
     * urutan: kiri-atas, kanan-atas, kanan-bawah, kiri-bawah.
     */
    fun dstPoints(box: TextBox, w: Float, h: Float): FloatArray {
        // Lebar/tinggi dibatasi finite: kanvas HUGE atau box rusak tak boleh
        // mengirim NaN/Infinity ke Matrix/Canvas (bisa menutup paksa aplikasi).
        val hw = (if (w.isFinite()) w else 0f) / 2f
        val hh = (if (h.isFinite()) h else 0f) / 2f
        val p = box.activePersp().sanitized()
        return floatArrayOf(
            -hw + p.tlX * hw, -hh + p.tlY * hh,
            hw + p.trX * hw, -hh + p.trY * hh,
            hw + p.brX * hw, hh + p.brY * hh,
            -hw + p.blX * hw, hh + p.blY * hh
        )
    }

    /** Matrix perspektif (null bila tak ada distorsi). */
    fun matrix(box: TextBox, w: Float, h: Float): Matrix? {
        val p = box.activePersp().sanitized()
        if (p.isFlat()) return null
        if (w <= 0.5f || h <= 0.5f) return null
        val hw = w / 2f
        val hh = h / 2f
        val src = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
        val dst = dstPoints(box, w, h)
        val m = Matrix()
        return if (m.setPolyToPoly(src, 0, dst, 0, 4)) m else null
    }

    /** Keempat sudut konten dalam koordinat KANVAS (rotasi ikut teks). */
    fun cornersCanvas(box: TextBox): Array<Offset> {
        val r = contentRect(box)
        val w = if (r.width().isFinite()) r.width() else 0f
        val h = if (r.height().isFinite()) r.height() else 0f
        val p = box.activePersp().sanitized()
        val cx = r.centerX()
        val cy = r.centerY()
        val hw = w / 2f
        val hh = h / 2f
        val pts = arrayOf(
            Offset(cx - hw + p.tlX * hw, cy - hh + p.tlY * hh),
            Offset(cx + hw + p.trX * hw, cy - hh + p.trY * hh),
            Offset(cx + hw + p.brX * hw, cy + hh + p.brY * hh),
            Offset(cx - hw + p.blX * hw, cy + hh + p.blY * hh)
        )
        val rad = Math.toRadians(box.rotation.toDouble())
        if (box.rotation == 0f) return pts
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return Array(4) {
            val d = pts[it]
            val dx = d.x - box.position.x
            val dy = d.y - box.position.y
            Offset(
                box.position.x + dx * c - dy * s,
                box.position.y + dx * s + dy * c
            )
        }
    }

    /** Titik di dalam trapesium (u = kiri-kanan, v = atas-bawah). */
    fun bilerp(dst: FloatArray, u: Float, v: Float): Offset {
        if (dst.size < 16) return Offset.Zero
        val topX = dst[0] + (dst[2] - dst[0]) * u
        val topY = dst[1] + (dst[3] - dst[1]) * u
        val botX = dst[12] + (dst[14] - dst[12]) * u
        val botY = dst[13] + (dst[15] - dst[13]) * u
        return Offset(topX + (botX - topX) * v, topY + (botY - topY) * v)
    }
}
