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

    /**
     * Proyeksi auto-align (ala TypeR).
     *
     *  - **REPOSITION**: layer hanya digeser (whole pixel), isi pikselnya
     *    tak disentuh. Proyeksi paling aman.
     *  - **COLLAGE**: digeser, diputar, dan diskala (seragam, tak
     *    distort) — seperti "Collage" di Photoshop.
     *  - **PERSPECTIVE**: layer diproyeksikan ke kuad halaman acuan,
     *    untuk halaman foto yang miring.
     */
    enum class Projection { REPOSITION, COLLAGE, PERSPECTIVE }

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
            var dx = 0f
            var dy = 0f
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
        val spanSum = sorted.fold(0f) { acc, e ->
            acc + (if (horizontal) e.second.width() else e.second.height())
        }
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
    // Auto-align dengan transform (proyeksi Collage / Perspective)
    // ------------------------------------------------------------------

    /** Kandidat transform: geser + putar + skala seragam. */
    private data class Xform(
        val dx: Float,
        val dy: Float,
        val angleDeg: Float,
        val scale: Float,
        val score: Float
    )

    /**
     * Cari transform terbaik (geser + putar + skala) yang paling cocok
     * dengan [refEdge].
     *
     * Pencarian dua tahap: kasar (48x48, sudut & skala jarang) lalu
     * halus (96x96, di sekitar terbaik). Semua dihitung dari peta tepi
     * acuan & peta cakupan layer yang digeser.
     */
    private fun bestXform(
        refEdge: FloatArray,
        cov: FloatArray,
        rect: RectF,
        refRect: RectF,
        centerX: Float,
        centerY: Float
    ): Xform? {
        val coarse = searchXform(
            refEdge, cov, rect, refRect, centerX, centerY,
            mapSize = 48,
            angles = floatArrayOf(-30f, -20f, -10f, 0f, 10f, 20f, 30f),
            scales = floatArrayOf(0.85f, 1f, 1.15f),
            steps = 10
        ) ?: return null
        return searchXform(
            refEdge, cov, rect, refRect, centerX, centerY,
            mapSize = 96,
            angles = floatArrayOf(
                coarse.angleDeg - 4f, coarse.angleDeg - 2f, coarse.angleDeg,
                coarse.angleDeg + 2f, coarse.angleDeg + 4f
            ),
            scales = floatArrayOf(
                coarse.scale - 0.06f, coarse.scale, coarse.scale + 0.06f
            ),
            steps = 14,
            around = coarse
        ) ?: coarse
    }

    /** Satu tahap pencarian transform. */
    private fun searchXform(
        refEdge: FloatArray,
        cov: FloatArray,
        rect: RectF,
        refRect: RectF,
        centerX: Float,
        centerY: Float,
        mapSize: Int,
        angles: FloatArray,
        scales: FloatArray,
        steps: Int,
        around: Xform? = null
    ): Xform? {
        val refScaleX = refRect.width() / mapSize
        val refScaleY = refRect.height() / mapSize
        if (refScaleX <= 0f || refScaleY <= 0f) return null
        val spanX = refRect.width() * 0.7f
        val spanY = refRect.height() * 0.7f
        val stepX = spanX / steps
        val stepY = spanY / steps
        val dx0 = around?.dx ?: -spanX
        val dy0 = around?.dy ?: -spanY
        val dxMax = around?.dx?.plus(spanX / 5f) ?: spanX
        val dyMax = around?.dy?.plus(spanY / 5f) ?: spanY

        var best: Xform? = null
        for (angle in angles) {
            for (sc in scales) {
                if (sc <= 0.05f) continue
                var dx = dx0
                while (dx <= dxMax) {
                    var dy = dy0
                    while (dy <= dyMax) {
                        val score = correlateXform(
                            refEdge, cov, rect, refRect, centerX, centerY,
                            angle, sc, dx, dy, refScaleX, refScaleY
                        )
                        val cur = best
                        if (cur == null || score > cur.score) {
                            best = Xform(dx, dy, angle, sc, score)
                        }
                        dy += stepY
                    }
                    dx += stepX
                }
            }
        }
        return best
    }

    /** Korelasi dengan transform (rotasi + skala seragam + geser). */
    private fun correlateXform(
        refEdge: FloatArray,
        cov: FloatArray,
        rect: RectF,
        refRect: RectF,
        centerX: Float,
        centerY: Float,
        angleDeg: Float,
        scale: Float,
        dx: Float,
        dy: Float,
        refScaleX: Float,
        refScaleY: Float
    ): Float {
        val w = MAP_SIZE
        val h = MAP_SIZE
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val sc = scale.coerceAtLeast(0.05f)
        var sum = 0f
        var n = 0
        // Iterasi isi layer yang digeser, transform ke ruang kanvas, lalu
        // ambil nilai tepi acuan di sana.
        for (iy in 0 until h) {
            val ly = rect.top + (iy + 0.5f) * (rect.height() / h)
            for (ix in 0 until w) {
                val c = cov[iy * w + ix]
                if (c <= 0.35f) continue
                val lx = rect.left + (ix + 0.5f) * (rect.width() / w)
                val rx0 = lx - centerX
                val ry0 = ly - centerY
                val ox = (rx0 * cosR - ry0 * sinR) * sc + centerX + dx
                val oy = (rx0 * sinR + ry0 * cosR) * sc + centerY + dy
                val rx = ((ox - refRect.left) / refScaleX).toInt()
                val ry = ((oy - refRect.top) / refScaleY).toInt()
                if (rx < 0 || ry < 0 || rx >= w || ry >= h) continue
                sum += refEdge[ry * w + rx] * c
                n++
            }
        }
        if (n < 24) return 0f
        return sum / n
    }

    // ------------------------------------------------------------------
    // Proyeksi Perspective: kuad empat + homografi
    // ------------------------------------------------------------------

    /**
     * Kuad empat (TL, TR, BR, BL) dari ISI layer, dalam koordinat kanvas.
     *
     * Titik diambil dari sudut-sudut extremal isi (sum x+y dan x-y),
     * jadi kuad mengikuti halaman foto yang miring.
     */
    private fun contentQuad(
        bitmap: Bitmap,
        rect: RectF
    ): FloatArray? {
        val w = MAP_SIZE
        val h = MAP_SIZE
        val sx = rect.width() / w
        val sy = rect.height() / h
        if (sx <= 0f || sy <= 0f) return null
        // Cari titik extremal di atas HANYA piksel yang punya isi.
        var tlScore = Float.MAX_VALUE
        var brScore = -Float.MAX_VALUE
        var trScore = -Float.MAX_VALUE
        var blScore = Float.MAX_VALUE
        var tlX = 0f; var tlY = 0f
        var brX = 0f; var brY = 0f
        var trX = 0f; var trY = 0f
        var blX = 0f; var blY = 0f
        var found = false
        for (y in 0 until h) {
            val py = rect.top + y * sy
            for (x in 0 until w) {
                val px = rect.left + x * sx
                val ix = rect.left.toInt() + (x * sx).toInt()
                val iy = rect.top.toInt() + (y * sy).toInt()
                if (ix < 0 || iy < 0 || ix >= bitmap.width || iy >= bitmap.height) continue
                if ((bitmap.getPixel(ix, iy) ushr 24) < 24) continue
                found = true
                val a = px + py
                val b = px - py
                if (a < tlScore) { tlScore = a; tlX = px; tlY = py }
                if (a > brScore) { brScore = a; brX = px; brY = py }
                if (b > trScore) { trScore = b; trX = px; trY = py }
                if (b < blScore) { blScore = b; blX = px; blY = py }
            }
        }
        if (!found) return null
        return floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
    }

    /**
     * Homografi 3x3 yang memetakan empat titik [src] ke empat titik [dst].
     *
     * Diselesaikan dengan sistem linear 8x8 (h33 = 1) + eliminasi Gauss.
     */
    private fun homography(src: FloatArray, dst: FloatArray): FloatArray? {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val sx = src[i * 2].toDouble()
            val sy = src[i * 2 + 1].toDouble()
            val dx = dst[i * 2].toDouble()
            val dy = dst[i * 2 + 1].toDouble()
            val r = i * 2
            a[r][0] = sx; a[r][1] = sy; a[r][2] = 1.0
            a[r][3] = 0.0; a[r][4] = 0.0; a[r][5] = 0.0
            a[r][6] = -sx * dx; a[r][7] = -sy * dx; a[r][8] = dx
            a[r + 1][0] = 0.0; a[r + 1][1] = 0.0; a[r + 1][2] = 0.0
            a[r + 1][3] = sx; a[r + 1][4] = sy; a[r + 1][5] = 1.0
            a[r + 1][6] = -sx * dy; a[r + 1][7] = -sy * dy; a[r + 1][8] = dy
        }
        val h = solve(a) ?: return null
        return floatArrayOf(
            h[0].toFloat(), h[1].toFloat(), h[2].toFloat(),
            h[3].toFloat(), h[4].toFloat(), h[5].toFloat(),
            h[6].toFloat(), h[7].toFloat(), 1f
        )
    }

    /** Eliminasi Gauss pada sistem augmented 8x9. */
    private fun solve(m: Array<DoubleArray>): DoubleArray? {
        val n = 8
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivot][col])) pivot = r
            }
            if (kotlin.math.abs(m[pivot][col]) < 1e-9) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                if (f == 0.0) continue
                for (c in col until n + 1) {
                    m[r][c] -= f * m[col][c]
                }
            }
        }
        val out = DoubleArray(n)
        for (i in 0 until n) out[i] = m[i][n] / m[i][i]
        return out
    }

    /**
     * Auto-align lapisan dengan proyeksi pilihan (ala TypeR).
     *
     *  - REPOSITION: layer hanya digeser (isi piksel tak disentuh).
     *  - COLLAGE: digeser + diputar + diskala seragam (tak distort).
     *  - PERSPECTIVE: diproyeksikan ke kuad empat halaman acuan.
     *
     * @param targets layer yang dipilih.
     * @param reference layer acuan (default: layer aktif).
     */
    fun autoAlign(
        targets: List<LayerItem>,
        reference: LayerItem?,
        projection: Projection
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
            when (projection) {
                Projection.REPOSITION -> {
                    val cov = coverageMap(layer.bitmap, rect) ?: continue
                    val best = bestShift(refEdge, cov, rect, refRect)
                    if (best == null || best.score <= 0f) {
                        failed++
                        continue
                    }
                    moveLayer(layer, best.dx, best.dy)
                    moved++
                }
                Projection.COLLAGE -> {
                    val cov = coverageMap(layer.bitmap, rect) ?: continue
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val best = bestXform(refEdge, cov, rect, refRect, cx, cy)
                    if (best == null || best.score <= 0f) {
                        failed++
                        continue
                    }
                    moveLayer(layer, best.dx, best.dy)
                    layer.rotationDeg = (layer.rotationDeg + best.angleDeg) % 360f
                    layer.widthPx *= best.scale
                    layer.heightPx *= best.scale
                    moved++
                }
                Projection.PERSPECTIVE -> {
                    val srcQuad = contentQuad(layer.bitmap, rect) ?: continue
                    val dstQuad = contentQuad(ref.bitmap, refRect) ?: continue
                    val h = homography(srcQuad, dstQuad) ?: continue
                    // Homografi memetakan ruang gambar -> kanvas.
                    val m = android.graphics.Matrix().apply { setValues(h) }
                    layer.projective = m
                    // Perkiraan ukuran baru = kotak hasil proyeksi.
                    val pts = floatArrayOf(
                        0f, 0f, layer.bitmap.width.toFloat(), 0f,
                        layer.bitmap.width.toFloat(), layer.bitmap.height.toFloat(),
                        0f, layer.bitmap.height.toFloat()
                    )
                    m.mapPoints(pts)
                    var l = Float.MAX_VALUE
                    var t = Float.MAX_VALUE
                    var r = -Float.MAX_VALUE
                    var b = -Float.MAX_VALUE
                    for (i in 0 until 4) {
                        l = min(l, pts[i * 2]); r = max(r, pts[i * 2])
                        t = min(t, pts[i * 2 + 1]); b = max(b, pts[i * 2 + 1])
                    }
                    layer.widthPx = r - l
                    layer.heightPx = b - t
                    layer.centerX = (l + r) / 2f
                    layer.centerY = (t + b) / 2f
                    moved++
                }
            }
        }
        return AlignResult(moved, failed)
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
     * Shorthand dari [autoAlign] dengan proyeksi REPOSITION.
     */
    fun autoAlignByContent(
        targets: List<LayerItem>,
        reference: LayerItem?
    ): AlignResult = autoAlign(targets, reference, Projection.REPOSITION)

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
