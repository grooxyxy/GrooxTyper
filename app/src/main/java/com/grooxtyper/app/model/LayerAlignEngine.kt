package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Auto-Align Layers (ala TypeR / Photoshop).
 *
 * TypeR memakai **korelasi konten**: layer digeser sampai isinya cocok
 * dengan layer acuan ("the locked layer, or the layer that shares the most
 * with the others"). TypeR juga menyediakan proyeksi:
 * Auto / Perspective / Collage / Reposition.
 *
 * Untuk GrooxTyper (lettering manga on-device) yang masuk akal dan
 *:


 * berealisasi:
 *
 *  - **Reposition** (dipakai di sini): layer **hanya digeser** (whole
 *    pixel), isi pikselnya tak disentuh. Ini proyeksi TypeR yang paling
 *    sering dipakai dan satu-satunya yang aman untuk layer teks.
 *  - **Align / Distribute** (ala Photoshop): rapatkan layer ke tepi/
 *    tengah area acuan, atau sebarkan dengan jarak sama.
 *
 * Pencocokan konten memakai peta tepi (gradien luminansi) layer acuan
 * dikorelasikan dengan peta cakupan layer yang digeser. Skor dinormalisasi
 * supaya layer dengan sedikit isi tak menangTelan nakal.
 */
object LayerAlignEngine {

    /** Arah/arah alignment. */
    enum class Align {
        LEFT, CENTER_H, RIGHT, TOP, MIDDLE_V, BOTTOM
    }

    /** Sumbu distribute. */
    enum class Distribute { HORIZONTAL, VERTICAL }

    /** Hasil auto-align berbasis konten. */
    data class AlignResult(
        /** Berapa layer yang benar-benar bergeser. */
        val moved: Int,
        /** Berapa layer yang gagal dicocokkan (terlalu berbeda). */
        val failed: Int
    )

    // ------------------------------------------------------------------
    // Kotak isi layer (koordinat kanvas)
    // ------------------------------------------------------------------

    /**
     * Kotak isi layer yang bisa digeser, atau null bila layer tak bisa
     * digeser (layer gambar penuh kanvas, folder, dsb).
     */
    fun contentRect(layer: LayerItem): RectF? = when (layer) {
        is ImageLayer -> {
            val w = layer.widthPx
            val h = layer.heightPx
            if (w <= 0f || h <= 0f) null
            else RectF(
                layer.centerX - w / 2f,
                layer.centerY - h / 2f,
                layer.centerX + w / 2f,
                layer.centerY + h / 2f
            )
        }
        is TextLayer -> {
            val box = layer.box
            val r = box.frameRectPx()
            if (r != null && r.width() > 1f && r.height() > 1f) {
                r
            } else {
                // Titik teks: kotak kecil di sekitar posisi supaya bisa
                // diratakan dengan layer lain.
                val s = box.fontSize * box.scale
                RectF(
                    box.position.x - s / 2f,
                    box.position.y - s / 2f,
                    box.position.x + s / 2f,
                    box.position.y + s / 2f
                )
            }
        }
        else -> null
    }

    /** Geser layer sebesar (dx, dy) piksel kanvas. */
    fun moveLayer(layer: LayerItem, dx: Float, dy: Float) {
        when (layer) {
            is ImageLayer -> {
                layer.centerX += dx
                layer.centerY += dy
            }
            is TextLayer -> {
                layer.box.position = layer.box.position.copy(
                    x = layer.box.position.x + dx,
                    y = layer.box.position.y + dy
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Align & Distribute (ala Photoshop)
    // ------------------------------------------------------------------

    /**
     * Ratakan layer ke area acuan.
     *
     * @param acuan kotak acuan (canvas) atau null = pakai gabungan kotak
     *   semua layer yang dipilih.
     * @return jumlah layer yang bergeser.
     */
    fun align(
        targets: List<LayerItem>,
        mode: Align,
        acuan: RectF? = null
    ): Int {
        val movable = targets.mapNotNull { l -> contentRect(l)?.let { l to it } }
        if (movable.isEmpty()) return 0
        val target = acuan ?: unionOf(movable.map { it.second })
        var moved = 0
        for ((layer, rect) in movable) {
            val dx: Float
            val dy: Float
            when (mode) {
                Align.LEFT -> dx = target.left - rect.left
                Align.CENTER_H -> dx = target.centerX() - rect.centerX()
                Align.RIGHT -> dx = target.right - rect.right
                Align.TOP -> dy = target.top - rect.top
                Align.MIDDLE_V -> dy = target.centerY() - rect.centerY()
                Align.BOTTOM -> dy = target.bottom - rect.bottom
            }
            if (dx == 0f && dy == 0f) continue
            moveLayer(layer, dx, dy)
            moved++
        }
        return moved
    }

    /**
     * Sebarkan layer dengan jarak sama antar tepi.
     *
     * @return jumlah layer yang bergeser.
     */
    fun distribute(
        targets: List<LayerItem>,
        axis: Distribute
    ): Int {
        val movable = targets.mapNotNull { l -> contentRect(l)?.let { l to it } }
        if (movable.size < 3) return 0
        val horizontal = axis == Distribute.HORIZONTAL
        val sorted = movable.sortedBy { if (horizontal) it.second.left else it.second.top }
        val first = sorted.first().second
        val last = sorted.last().second
        val total = if (horizontal) {
            last.right - first.left
        } else {
            last.bottom - first.top
        }
        val spanSum = sorted.sumOf { if (horizontal) it.second.width() else it.second.height() }
        val gap = (total - spanSum) / (sorted.size - 1)
        var moved = 0
        var cursor = if (horizontal) first.left else first.top
        for ((layer, rect) in sorted) {
            val size = if (horizontal) rect.width() else rect.height()
            val cur = if (horizontal) rect.left else rect.top
            val delta = cursor - cur
            if (abs(delta) > 0.5f) {
                moveLayer(layer, if (horizontal) delta else 0f, if (horizontal) 0f else delta)
                moved++
            }
            cursor += size + gap
        }
        return moved
    }

    private fun unionOf(rects: List<RectF>): RectF {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (rc in rects) {
            l = min(l, rc.left)
            t = min(t, rc.top)
            r = max(r, rc.right)
            b = max(b, rc.bottom)
        }
        return RectF(l, t, r, b)
    }

    // ------------------------------------------------------------------
    // Auto-align berbasis korelasi konten (proyeksi "Reposition")
    // ------------------------------------------------------------------

    /** Ukuran peta tepi (piksel) untuk korelasi. */
    private const val MAP_SIZE = 96

    /**
     * Geser layer terpilih sampai isinya paling cocok dengan [reference]
     * (proyeksi "Reposition" ala TypeR).
     *
     * Yang bisa dicocokkan hanya layer **gambar** (punya bitmap). Layer
     * teks tak punya pikseluntil dirender, jadi dilewati.
     *
     * @param targets layer yang dipilih.
     * @param reference layer acuan (default: layer aktif).
     * @return jumlah yang bergeser & yang gagal.
     */
    fun autoAlignByContent(
        targets: List<LayerItem>,
        reference: LayerItem?
    ): AlignResult {
        val images = targets.filterIsInstance<ImageLayer>()
            .filter { !it.bitmap.isRecycled && it.bitmap.width > 0 && it.bitmap.height > 0 }
        if (images.size < 2) return AlignResult(0, 0)
        val refId = reference?.id
        val ref = images.firstOrNull { it.id == refId } ?: images.first()
        val refRect = contentRect(ref) ?: return AlignResult(0, images.size)
        val refEdge = edgeMap(ref.bitmap, refRect) ?: return AlignResult(0, images.size)

        var moved = 0
        var failed = 0
        for (layer in images) {
            if (layer.id == ref.id) continue
            val rect = contentRect(layer) ?: continue
            val cov = coverageMap(layer.bitmap, rect) ?: continue
            val best = bestShift(refEdge, cov, rect, refRect) ?: run {
                failed++
                continue
            }
            if (best.score <= 0f) {
                failed++
                continue
            }
            moveLayer(layer, best.dx, best.dy)
            moved++
        }
        return AlignResult(moved, failed)
    }

    /** Peta tepi (gradien luminansi) layer acuan, di kotak acuan. */
    private fun edgeMap(bitmap: Bitmap, rect: RectF): FloatArray? {
        val w = MAP_SIZE
        val h = MAP_SIZE
        val luma = lumaMap(bitmap, rect, w, h) ?: return null
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val xm = (x - 1).coerceAtLeast(0)
                val xp = (x + 1).coerceAtMost(w - 1)
                val ym = (y - 1).coerceAtLeast(0)
                val yp = (y + 1).coerceAtMost(h - 1)
                val gx = luma[y * w + xp] - luma[y * w + xm]
                val gy = luma[yp * w + x] - luma[ym * w + x]
                out[y * w + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return out
    }

    /** Peta cakupan (alpha) layer yang digeser, di kotaknya sendiri. */
    private fun coverageMap(bitmap: Bitmap, rect: RectF): FloatArray? {
        val w = MAP_SIZE
        val h = MAP_SIZE
        val out = FloatArray(w * h)
        val sx = (rect.width() / w).coerceAtLeast(0.5f)
        val sy = (rect.height() / h).coerceAtLeast(0.5f)
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = left + (x * sx).toInt()
                val py = top + (y * sy).toInt()
                if (px < 0 || py < 0 || px >= bitmap.width || py >= bitmap.height) continue
                out[y * w + x] = (bitmap.getPixel(px, py) ushr 24) / 255f
            }
        }
        return out
    }

    /** Peta luminansi (abu-abu) dalam kotak, diskalakan ke w x h. */
    private fun lumaMap(bitmap: Bitmap, rect: RectF, w: Int, h: Int): FloatArray? {
        val out = FloatArray(w * h)
        val sx = (rect.width() / w).coerceAtLeast(0.5f)
        val sy = (rect.height() / h).coerceAtLeast(0.5f)
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = left + (x * sx).toInt()
                val py = top + (y * sy).toInt()
                if (px < 0 || py < 0 || px >= bitmap.width || py >= bitmap.height) continue
                val p = bitmap.getPixel(px, py)
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[y * w + x] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }
        }
        return out
    }

    /** Kandidat pergeseran terbaik: skor korelasi tertinggi. */
    private data class Shift(val dx: Float, val dy: Float, val score: Float)

    /**
     * Cari pergeseran (dx, dy) yang paling cocok.
     *
     * Pencarian coarsely di sekitar posisi sekarang, memakai korelasi
     * tepi acuan dengan cakupan layer yang digeser.
     */
    private fun bestShift(
        refEdge: FloatArray,
        cov: FloatArray,
        rect: RectF,
        refRect: RectF
    ): Shift? {
        val w = MAP_SIZE
        val h = MAP_SIZE
        // Posisi acuan dalam koordinat peta = px / (refRect.width / w).
        val scaleX = refRect.width() / w
        val scaleY = refRect.height() / h
        if (scaleX <= 0f || scaleY <= 0f) return null

        // Kandidat: pergeseran dalam piksel kanvas, langkah kasar.
        val step = max(4f, min(refRect.width(), refRect.height()) / 24f)
        val rangeX = refRect.width() * 0.6f
        val rangeY = refRect.height() * 0.6f

        var best: Shift? = null
        var dx = -rangeX
        while (dx <= rangeX) {
            var dy = -rangeY
            while (dy <= rangeY) {
                val score = correlate(refEdge, cov, rect, refRect, dx, dy, scaleX, scaleY)
                val current = best
                if (current == null || score > current.score) {
                    best = Shift(dx, dy, score)
                }
                dy += step
            }
            dx += step
        }
        return best
    }

    /** Korelasi tepi acuan dengan cakupan layer yang digeser. */
    private fun correlate(
        refEdge: FloatArray,
        cov: FloatArray,
        rect: RectF,
        refRect: RectF,
        dx: Float,
        dy: Float,
        scaleX: Float,
        scaleY: Float
    ): Float {
        val w = MAP_SIZE
        val h = MAP_SIZE
        var sum = 0f
        var n = 0
        for (y in 0 until h) {
            // Titik cakupan dipetakan ke koordinat acuan.
            val ry = (rect.top + dy + (y + 0.5f) * (rect.height() / h) - refRect.top) / scaleY
            if (ry < 0f || ry >= h) continue
            for (x in 0 until w) {
                val c = cov[y * w + x]
                if (c <= 0.35f) continue
                val rx = (rect.left + dx + (x + 0.5f) * (rect.width() / w) - refRect.left) / scaleX
                if (rx < 0f || rx >= w) continue
                sum += refEdge[(ry.toInt() * w) + rx.toInt()] * c
                n++
            }
        }
        if (n < 24) return 0f
        return sum / n
    }
}
