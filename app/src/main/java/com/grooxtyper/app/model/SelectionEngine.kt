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

    /** Tambah oval sebagai SATU area baru (tap bubble menumpuk, multi-seleksi). */
    fun selectOval(rect: RectF) {
        val oval = Path()
        oval.addOval(rect, Path.Direction.CW)
        addRegion(oval)
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
