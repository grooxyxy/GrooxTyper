package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SelectionEngine(val width: Int, val height: Int) {
    var hasSelection by mutableStateOf(false)
    /** Jumlah area terpisah dalam seleksi gabungan (untuk label UI). */
    var selectionCount by mutableStateOf(0)
    val selectionPath = Path()
    var clipboardBitmap: Bitmap? = null

    // Multi-seleksi: tiap drag/tap bubble MENAMBAH satu region; union-nya
    // yang dipakai render, mask, dan bounds. Satu-dua area yang tak
    // diinginkan bisa dihapus via tap (removeRegionAt) atau menu.
    private val regions = mutableListOf<Path>()
    private val regionBounds = mutableListOf<RectF>()
    private val fullClip = android.graphics.Region(0, 0, width, height)

    // Alokasi malas: 720x16000 = 46MB. Jangan alokasi sebelum user
    // benar-benar memakai seleksi — hemat permanen bila tak dipakai.
    private var _maskBitmap: Bitmap? = null
    private var _maskCanvas: Canvas? = null
    val selectionMaskBitmap: Bitmap
        get() {
            var b = _maskBitmap
            if (b == null || b.isRecycled) {
                b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                _maskBitmap = b
                _maskCanvas = Canvas(b)
            }
            return b
        }
    private val maskCanvas: Canvas
        get() {
            selectionMaskBitmap
            return _maskCanvas!!
        }

    /** Tambah sketsa lasso bebas sebagai SATU area baru (multi-seleksi). */
    fun setLassoPath(path: Path) {
        val single = Path(path)
        single.close()
        addRegion(single)
    }

    fun clearSelection() {
        regions.clear()
        regionBounds.clear()
        selectionPath.reset()
        hasSelection = false
        selectionCount = 0
        // Jangan alokasi hanya untuk clear — mask yang belum ada sudah kosong.
        _maskBitmap?.let { b ->
            if (!b.isRecycled) {
                (_maskCanvas ?: Canvas(b)).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
    }

    /** Hapus area terakhir yang ditambahkan. True bila ada yang dihapus. */
    fun removeLastRegion(): Boolean {
        if (regions.isEmpty()) return false
        regions.removeAt(regions.lastIndex)
        regionBounds.removeAt(regionBounds.lastIndex)
        rebuildUnion()
        return true
    }

    /**
     * Hapus SATU area yang memuat titik kanvas (x, y) — untuk membuang satu
     * atau dua area yang tidak diinginkan via tap. Cek dari teratas dulu
     * dengan uji geometri tepat (Region), bukan sekadar bounding box.
     */
    fun removeRegionAt(x: Float, y: Float): Boolean {
        for (i in regions.indices.reversed()) {
            if (!regionBounds[i].contains(x, y)) continue
            val hit = android.graphics.Region()
            hit.setPath(regions[i], fullClip)
            if (hit.contains(x.toInt(), y.toInt())) {
                regions.removeAt(i)
                regionBounds.removeAt(i)
                rebuildUnion()
                return true
            }
        }
        return false
    }

    /** Gabungkan ulang semua region menjadi [selectionPath] + mask. */
    private fun rebuildUnion() {
        selectionPath.reset()
        for ((i, r) in regions.withIndex()) {
            if (i == 0) {
                selectionPath.addPath(r)
            } else if (!selectionPath.op(r, Path.Op.UNION)) {
                selectionPath.addPath(r)
            }
        }
        selectionPath.close()
        hasSelection = regions.isNotEmpty()
        selectionCount = regions.size
        if (hasSelection) {
            updateMaskFromPath()
        } else {
            _maskBitmap?.let { b ->
                if (!b.isRecycled) {
                    (_maskCanvas ?: Canvas(b)).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                }
            }
        }
    }

    private fun addRegion(region: Path) {
        val b = RectF()
        region.computeBounds(b, true)
        if (b.isEmpty) return
        regions.add(region)
        regionBounds.add(b)
        rebuildUnion()
    }

    /** Batas seleksi aktif (untuk auto-fit teks ke bubble). Null bila tak ada seleksi. */
    fun selectionBounds(): RectF? {
        if (!hasSelection) return null
        val r = RectF()
        selectionPath.computeBounds(r, true)
        return if (r.isEmpty) null else r
    }

    /**
     * Salinan batas TIAP area terpisah (atas-dulu) — untuk dijadikan satu
     * bubble per area, bukan satu bubble gabungan.
     */
    fun regionBoundsList(): List<RectF> = regionBounds.map { RectF(it) }

    /** Tambah oval sebagai SATU area baru (tap bubble menumpuk, multi-seleksi). */
    fun selectOval(rect: RectF) {
        val oval = Path()
        oval.addOval(rect, Path.Direction.CW)
        addRegion(oval)
    }

    /**
     * Magic Wand: banjir (flood fill) warna mirip dari titik seed, lalu
     * batasnya ditelusur jadi Path (marching squares) dan ditambah sebagai
     * SATU area baru. Konektivitas 4-arah (lebih ketat, tidak bocor lewat
     * diagonal celah line-art).
     *
     * @param px piksel ARGB baris-mayor selebar [w].
     * @param maxDist jarak Euclidean RGB maksimum dari warna seed.
     * @return true bila satu area berhasil ditambah.
     */
    fun selectWand(
        px: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        maxDist: Float
    ): Boolean {
        if (sx !in 0 until w || sy !in 0 until h) return false
        if (px.size < w * h) return false
        val seed = px[sy * w + sx]
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr2 = maxDist * maxDist
        fun close(i: Int): Boolean {
            val p = px[i]
            val dr = (((p shr 16) and 0xFF) - sr).toFloat()
            val dg = (((p shr 8) and 0xFF) - sg).toFloat()
            val db = ((p and 0xFF) - sb).toFloat()
            return dr * dr + dg * dg + db * db <= thr2
        }
        if (!close(sy * w + sx)) return false
        val mask = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        mask[sy * w + sx] = true
        stack.addLast(sy * w + sx)
        var count = 0
        // Batas agar tap di latar raksasa tak menggantung UI selamanya.
        val cap = minOf(w.toLong() * h, 12_000_000L)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            count++
            if (count > cap) return false
            val x = i % w
            val y = i / w
            if (x > 0) {
                val j = i - 1
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (x < w - 1) {
                val j = i + 1
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (y > 0) {
                val j = i - w
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (y < h - 1) {
                val j = i + w
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
        }
        if (count < 4) return false
        val path = traceContour(mask, w, h) ?: return false
        addRegion(path)
        return true
    }

    /**
     * Perkirakan toleransi otomatis dari kontras lokal 9x9 di sekitar seed:
     * area datar → toleransi kecil (ketat), area gradasi → lebih longgar.
     * Dipakai mode Otomatis tongkat sihir.
     */
    fun autoTolerance(px: IntArray, w: Int, h: Int, sx: Int, sy: Int): Float {
        if (px.size < w * h) return 40f
        val seed = px[sy.coerceIn(0, h - 1) * w + sx.coerceIn(0, w - 1)]
        val sr = ((seed shr 16) and 0xFF).toFloat()
        val sg = ((seed shr 8) and 0xFF).toFloat()
        val sb = (seed and 0xFF).toFloat()
        var maxD2 = 0f
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = (sx + dx).coerceIn(0, w - 1)
                val y = (sy + dy).coerceIn(0, h - 1)
                val p = px[y * w + x]
                val dr = (((p shr 16) and 0xFF).toFloat() - sr)
                val dg = (((p shr 8) and 0xFF).toFloat() - sg)
                val db = ((p and 0xFF).toFloat() - sb)
                val d2 = dr * dr + dg * dg + db * db
                if (d2 > maxD2) maxD2 = d2
            }
        }
        return (kotlin.math.sqrt(maxD2) * 1.5f + 12f).coerceIn(18f, 110f)
    }

    /**
     * Telusur batas mask biner jadi Path (marching squares per sel,
     * tiap segmen jadi sub-path sendiri — cukup untuk overlay marching-ants,
     * union, dan uji Region). Koordinat = tepi piksel persis.
     */
    private fun traceContour(mask: BooleanArray, w: Int, h: Int): Path? {
        val path = Path()
        var segs = 0
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            segs++
        }
        for (y in 0 until h - 1) {
            val r0 = y * w
            for (x in 0 until w - 1) {
                val tl = mask[r0 + x]
                val tr = mask[r0 + x + 1]
                val br = mask[r0 + x + 1 + w]
                val bl = mask[r0 + x + w]
                val idx = (if (tl) 1 else 0) or (if (tr) 2 else 0) or
                    (if (br) 4 else 0) or (if (bl) 8 else 0)
                val fx = x.toFloat()
                val fy = y.toFloat()
                when (idx) {
                    1 -> seg(fx + 0.5f, fy, fx, fy + 0.5f)
                    2 -> seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                    3 -> seg(fx, fy + 0.5f, fx + 1f, fy + 0.5f)
                    4 -> seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    5 -> {
                        seg(fx + 0.5f, fy, fx, fy + 0.5f)
                        seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    }
                    6 -> seg(fx + 0.5f, fy, fx + 0.5f, fy + 1f)
                    7 -> seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    8 -> seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    9 -> seg(fx + 0.5f, fy, fx + 0.5f, fy + 1f)
                    10 -> {
                        seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                        seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    }
                    11 -> seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    12 -> seg(fx, fy + 0.5f, fx + 1f, fy + 0.5f)
                    13 -> seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                    14 -> seg(fx + 0.5f, fy, fx, fy + 0.5f)
                }
            }
        }
        return if (segs == 0) null else path
    }

    /**
     * Tambah kotak sebagai SATU area baru (drag menumpuk, multi-seleksi).
     * Koordinat dinormalisasi + dijepit ke kanvas. Dipakai untuk tambah
     * bubble manual dan seleksi area cepat via drag.
     */
    fun selectRect(rect: RectF) {
        val left = minOf(rect.left, rect.right).coerceIn(0f, width.toFloat())
        val top = minOf(rect.top, rect.bottom).coerceIn(0f, height.toFloat())
        val right = maxOf(rect.left, rect.right).coerceIn(0f, width.toFloat())
        val bottom = maxOf(rect.top, rect.bottom).coerceIn(0f, height.toFloat())
        if (right - left < 2f || bottom - top < 2f) return
        val box = Path()
        box.addRect(left, top, right, bottom, Path.Direction.CW)
        addRegion(box)
    }

    private fun updateMaskFromPath() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        maskCanvas.drawPath(selectionPath, fillPaint)
    }

    fun invertSelection() {
        if (!hasSelection) return
        // Alokasi 720x16000 = 46MB sementara, langsung recycle setelah salin.
        val invertedBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val invCanvas = Canvas(invertedBmp)
            invCanvas.drawColor(Color.WHITE)

            val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            invCanvas.drawPath(selectionPath, erasePaint)

            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            maskCanvas.drawBitmap(invertedBmp, 0f, 0f, null)

            val rectPath = Path()
            rectPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            rectPath.op(selectionPath, Path.Op.DIFFERENCE)
            selectionPath.set(rectPath)
            // Sinkronkan daftar region agar tambah/hapus berikutnya tidak
            // menghidupkan kembali bentuk sebelum invert.
            regions.clear()
            regionBounds.clear()
            val single = Path(selectionPath)
            val b = RectF()
            single.computeBounds(b, true)
            if (!b.isEmpty) {
                regions.add(single)
                regionBounds.add(b)
            }
            selectionCount = regions.size
        } finally {
            runCatching { invertedBmp.recycle() }
        }
    }

    fun clearSelectedArea(layer: DrawingLayer) {
        if (!hasSelection) return
        val bmp = layer.getBitmap()
        val canvas = Canvas(bmp)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, clearPaint)
        layer.markDirty()
    }

    fun copySelectedArea(layer: DrawingLayer): Bitmap? {
        if (!hasSelection) return null
        val layerBmp = layer.getBitmap()
        val copyBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(copyBmp)
        canvas.drawBitmap(layerBmp, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, maskPaint)
        clipboardBitmap = copyBmp
        return copyBmp
    }

    fun cutSelectedArea(layer: DrawingLayer): Bitmap? {
        val copied = copySelectedArea(layer)
        if (copied != null) {
            clearSelectedArea(layer)
        }
        return copied
    }

    fun pasteToNewLayer(layerManager: LayerManager): DrawingLayer? {
        val clip = clipboardBitmap ?: return null
        val newLayer = layerManager.addLayer("Pasted Layer")
        val layerBmp = newLayer.getBitmap()
        val canvas = Canvas(layerBmp)
        canvas.drawBitmap(clip, 0f, 0f, null)
        newLayer.markDirty()
        return newLayer
    }
}
