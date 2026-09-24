package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class LayerBlendMode(val displayName: String, val porterDuffMode: PorterDuff.Mode) {
    NORMAL("Normal", PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", PorterDuff.Mode.SCREEN),
    DARKEN("Darken", PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", PorterDuff.Mode.LIGHTEN),
    ADD("Add", PorterDuff.Mode.ADD),
    CLEAR("Clear", PorterDuff.Mode.CLEAR),
    SRC_ATOP("Alpha Clip", PorterDuff.Mode.SRC_ATOP),
    XOR("XOR", PorterDuff.Mode.XOR)
}

open class LayerItem(
    var name: String = "Layer",
    var isFolder: Boolean = false,
    val id: String = UUID.randomUUID().toString()
) {
    var isVisible by mutableStateOf(true)
    var opacity by mutableFloatStateOf(1.0f)
    var blendMode by mutableStateOf(LayerBlendMode.NORMAL)
    var isAlphaLocked by mutableStateOf(false)
    var isClippingMask by mutableStateOf(false)
    val children = mutableStateListOf<LayerItem>()
    /**
     * Posisi asli saat project dibuka (0 = paling atas). HANYA dipakai untuk
     * menyortir ulang sesaat setelah restore, tidak masuk JSON.
     *
     * Tanpa ini, layer teks dan image yang dipulihkan dari dua sumber berbeda
     * akan saling menimpa urutannya: watermark yang asalnya di bawah bubble
     * teks bisa meloncat ke atas (atau sebaliknya) setiap project ditutup.
     * Default MAX_VALUE = "bukan dari file" → tetap di paling bawah.
     */
    var restoreZ: Float = Float.MAX_VALUE
}

class DrawingLayer(
    val width: Int,
    val height: Int,
    name: String = "Layer"
) : LayerItem(name = name, isFolder = false) {

    val tileMap = LayerTileMap(width, height)
    var compositeBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    fun getPersistentBitmap(): Bitmap {
        return compositeBitmap
    }

    fun getBitmap(): Bitmap {
        return compositeBitmap
    }

    fun markDirty() {
        // Bitmap updated directly
    }

    fun clear() {
        tileMap.clear()
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    fun flipHorizontal() {
        val current = compositeBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = android.graphics.Matrix().apply {
            postScale(-1f, 1f, width / 2f, height / 2f)
        }
        val flipped = Bitmap.createBitmap(current, 0, 0, width, height, matrix, true)
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(flipped, 0f, 0f, null)
        tileMap.importFromBitmap(compositeBitmap)
    }

    fun flipVertical() {
        val current = compositeBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = android.graphics.Matrix().apply {
            postScale(1f, -1f, width / 2f, height / 2f)
        }
        val flipped = Bitmap.createBitmap(current, 0, 0, width, height, matrix, true)
        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(flipped, 0f, 0f, null)
        tileMap.importFromBitmap(compositeBitmap)
    }
}

class TextLayer(
    val box: TextBox,
    name: String = "Text: ${box.text.take(16)}",
    layerId: String = UUID.randomUUID().toString()
) : LayerItem(name = name, isFolder = false, id = layerId)

/**
 * Layer gambar bebas (stiker): menyimpan bitmap sumber + transform yang bisa
 * diubah kapan pun (move/rotate/resize/opacity), mirip TextBox ber-handle.
 * Berbeda dari DrawingLayer yang bitmapnya full-canvas dan dibake permanen.
 */
class ImageLayer(
    var bitmap: Bitmap,
    var centerX: Float,
    var centerY: Float,
    var widthPx: Float,
    var heightPx: Float,
    var rotationDeg: Float = 0f,
    var flipX: Boolean = false,
    var flipY: Boolean = false,
    name: String = "Image"
) : LayerItem(name = name, isFolder = false) {
    var lockedAspect by mutableStateOf(true)

    /** Rasio piksel sumber (dipakai untuk lock aspek & "ukuran asli"). */
    val sourceAspect: Float
        get() = bitmap.width.toFloat().coerceAtLeast(1f) / bitmap.height.coerceAtLeast(1).toFloat()

    fun baseScaleX(): Float = widthPx / bitmap.width.toFloat().coerceAtLeast(1f)
    fun baseScaleY(): Float = heightPx / bitmap.height.toFloat().coerceAtLeast(1f)
    fun scaleX(): Float = if (flipX) -baseScaleX() else baseScaleX()
    fun scaleY(): Float = if (flipY) -baseScaleY() else baseScaleY()

    /** Matrix kanvas: T(center) · R · S(±flip) · T(-half). */
    fun matrix(): android.graphics.Matrix {
        val sx = scaleX()
        val sy = scaleY()
        return android.graphics.Matrix().apply {
            setTranslate(centerX, centerY)
            postRotate(rotationDeg)
            postScale(sx, sy)
            postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        }
    }

    /**
     * Empat sudut hasil transform dalam koordinat kanvas, urutan
     * TL, TR, BR, BL → 8 float (x0,y0,x1,y1,...). Handle skala/rotasi memakai
     * ini, bukan AABB: begitu image diputar, AABB handle meleset jauh dari
     * sudut yang benar-benar tergambar sehingga image "tidak bisa di-resize".
     */
    fun cornerPoints(): FloatArray {
        val m = matrix()
        val pts = floatArrayOf(
            0f, 0f, bitmap.width.toFloat(), 0f,
            bitmap.width.toFloat(), bitmap.height.toFloat(), 0f, bitmap.height.toFloat()
        )
        m.mapPoints(pts)
        return pts
    }

    /** Contain: seluruh gambar muat di dalam kanvas (aspek tetap). */
    fun fitInsideCanvas(canvasW: Float, canvasH: Float) {
        val s = minOf(
            canvasW / bitmap.width.toFloat().coerceAtLeast(1f),
            canvasH / bitmap.height.toFloat().coerceAtLeast(1f)
        )
        widthPx = (bitmap.width * s).coerceAtLeast(8f)
        heightPx = (bitmap.height * s).coerceAtLeast(8f)
    }

    /** Cover: image memenuhi kanvas penuh (aspek tetap, tepi terpotong). */
    fun fillCanvas(canvasW: Float, canvasH: Float) {
        val s = maxOf(
            canvasW / bitmap.width.toFloat().coerceAtLeast(1f),
            canvasH / bitmap.height.toFloat().coerceAtLeast(1f)
        )
        widthPx = (bitmap.width * s).coerceAtLeast(8f)
        heightPx = (bitmap.height * s).coerceAtLeast(8f)
    }

    /** 1:1 piksel sumber (100%). */
    fun setActualSize() {
        widthPx = bitmap.width.toFloat()
        heightPx = bitmap.height.toFloat()
    }

    fun toggleFlipX() { flipX = !flipX }
    fun toggleFlipY() { flipY = !flipY }

    /** Kotak pembungkus axis-aligned hasil transform (untuk overlay/culling). */
    fun bounds(): android.graphics.RectF {
        val m = matrix()
        val pts = floatArrayOf(
            0f, 0f, bitmap.width.toFloat(), 0f,
            bitmap.width.toFloat(), bitmap.height.toFloat(), 0f, bitmap.height.toFloat()
        )
        m.mapPoints(pts)
        var l = pts[0]; var t = pts[1]; var r = pts[0]; var b = pts[1]
        for (i in 2 until 8 step 2) {
            if (pts[i] < l) l = pts[i]
            if (pts[i] > r) r = pts[i]
            if (pts[i + 1] < t) t = pts[i + 1]
            if (pts[i + 1] > b) b = pts[i + 1]
        }
        return android.graphics.RectF(l, t, r, b)
    }

    /** Titik kanvas → ruang lokal bitmap. Null bila matriks tak invertible. */
    fun toLocal(x: Float, y: Float): android.graphics.PointF? {
        val inv = android.graphics.Matrix()
        if (!matrix().invert(inv)) return null
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        return android.graphics.PointF(pts[0], pts[1])
    }

    fun hitTest(x: Float, y: Float): Boolean {
        val p = toLocal(x, y) ?: return false
        return p.x in 0f..bitmap.width.toFloat() && p.y in 0f..bitmap.height.toFloat()
    }
}

/** Snapshot transform image untuk undo (murah: tanpa duplikat bitmap). */
data class ImageTransform(
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val rotationDeg: Float,
    val opacity: Float,
    // Flip ikut disimpan: tanpa ini undo "balik gambar" hanya mengembalikan
    // posisi/ukuran, gambar tetap terbalik.
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val lockedAspect: Boolean = true
) {
    companion object {
        fun of(layer: ImageLayer) = ImageTransform(
            layer.centerX, layer.centerY, layer.widthPx, layer.heightPx,
            layer.rotationDeg, layer.opacity, layer.flipX, layer.flipY, layer.lockedAspect
        )
    }

    fun applyTo(layer: ImageLayer) {
        layer.centerX = centerX
        layer.centerY = centerY
        layer.widthPx = widthPx
        layer.heightPx = heightPx
        layer.rotationDeg = rotationDeg
        layer.opacity = opacity
        layer.flipX = flipX
        layer.flipY = flipY
        layer.lockedAspect = lockedAspect
    }
}

class LayerManager(val width: Int, val height: Int) {
    val layers = mutableStateListOf<LayerItem>()
    var activeLayerId by mutableStateOf<String>("")

    init {
        val initialLayer = DrawingLayer(width, height, "Layer 1")
        layers.add(initialLayer)
        activeLayerId = initialLayer.id
    }

    fun getActiveLayer(): DrawingLayer? {
        fun findLayer(items: List<LayerItem>): DrawingLayer? {
            for (item in items) {
                if (item.id == activeLayerId && item is DrawingLayer) return item
                if (item.isFolder) {
                    val found = findLayer(item.children)
                    if (found != null) return found
                }
            }
            return null
        }
        val active = findLayer(layers)
        if (active != null) return active

        // Fallback: If activeLayerId is a TextLayer, find the first available DrawingLayer
        for (item in layers) {
            if (item is DrawingLayer) {
                activeLayerId = item.id
                return item
            }
        }
        return null
    }

    /** Cari drawing layer berdasarkan id (untuk undo/redo lintas layer). */
    fun findDrawingLayerById(id: String): DrawingLayer? {
        fun find(items: List<LayerItem>): DrawingLayer? {
            for (item in items) {
                if (item.id == id && item is DrawingLayer) return item
                if (item.isFolder) {
                    val found = find(item.children)
                    if (found != null) return found
                }
            }
            return null
        }
        return find(layers)
    }

    fun ensureDrawingLayer(): DrawingLayer {
        val current = getActiveLayer()
        if (current != null) return current
        val newLayer = DrawingLayer(width, height, "Layer ${layers.size + 1}")
        layers.add(0, newLayer)
        activeLayerId = newLayer.id
        return newLayer
    }

    fun addLayer(name: String = "Layer ${layers.size + 1}"): DrawingLayer {
        val layer = DrawingLayer(width, height, name)
        layers.add(0, layer)
        activeLayerId = layer.id
        return layer
    }

    /** Duplikat drawing layer aktif (bitmap + opacity/blend/visibility). Null bila OOM / bukan gambar. */
    fun duplicateLayer(id: String): DrawingLayer? {
        val src = findDrawingLayerById(id) ?: return null
        return try {
            val copy = DrawingLayer(width, height, "${src.name} copy")
            android.graphics.Canvas(copy.getPersistentBitmap()).drawBitmap(src.getPersistentBitmap(), 0f, 0f, null)
            copy.tileMap.importFromBitmap(copy.getPersistentBitmap())
            copy.opacity = src.opacity
            copy.blendMode = src.blendMode
            copy.isVisible = src.isVisible
            copy.isAlphaLocked = src.isAlphaLocked
            val idx = layers.indexOfFirst { it.id == id }.coerceAtLeast(0)
            layers.add((idx + 1).coerceIn(0, layers.size), copy)
            activeLayerId = copy.id
            copy
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun addTextLayer(box: TextBox): TextLayer {
        val textLayer = TextLayer(box, name = "Text: ${box.text.take(16)}")
        layers.add(0, textLayer)
        activeLayerId = textLayer.id
        return textLayer
    }

    /**
     * Tambah image layer bebas (stiker/watermark).
     *
     * PENTING: [bitmap] adalah SUMBER resolusi penuh dan tidak boleh
     * di-raster ulang ke ukuran tampil — layer hanya menyimpan ukuran
     * tampilan terpisah ([displayW] × [displayH]) lewat matrix. Versi lama
     * memanggil ImageImport.scaleTo() sebelum sini, sehingga sumber ikut
     * mengecil permanen: begitu image di-zoom/di-resize naik, hasilnya
     * pecah/buram (keluhan utama di feature ini).
     */
    fun addImageLayer(
        bitmap: Bitmap,
        displayW: Int,
        displayH: Int,
        opacityInit: Float = 1f,
        name: String = "Image ${layers.size + 1}"
    ): ImageLayer {
        val w = displayW.coerceAtLeast(1)
        val h = displayH.coerceAtLeast(1)
        val layer = ImageLayer(
            bitmap = bitmap,
            centerX = width / 2f,
            centerY = height / 2f,
            widthPx = w.toFloat(),
            heightPx = h.toFloat(),
            name = name
        )
        // Opacity boleh 0 (gambar disembunyikan sementara) — slider lama
        // berhenti di 5-10% sehingga watermark samar mustahil dibuat.
        layer.opacity = opacityInit.coerceIn(0f, 1f)
        layers.add(0, layer)
        activeLayerId = layer.id
        return layer
    }

    /** Semua ImageLayer yang terlihat (untuk hit-test topmost-first). */
    fun visibleImageLayers(): List<ImageLayer> {
        val out = mutableListOf<ImageLayer>()
        fun walk(items: List<LayerItem>, ancestorsVisible: Boolean) {
            for (item in items) {
                val vis = ancestorsVisible && item.isVisible
                if (item is ImageLayer) {
                    if (vis) out.add(item)
                } else if (item.isFolder) {
                    walk(item.children, vis)
                }
            }
        }
        walk(layers, true)
        return out
    }

    fun addFolder(name: String = "Folder ${layers.size + 1}"): LayerItem {
        val folder = LayerItem(name = name, isFolder = true)
        layers.add(0, folder)
        return folder
    }

    fun deleteLayer(id: String) {
        removeLayerById(id)
    }

    /** Hapus layer dan kembalikan objeknya (untuk undo); perbaiki layer aktif bila perlu. */
    fun removeLayerById(id: String): LayerItem? {
        fun removeRec(items: MutableList<LayerItem>): LayerItem? {
            val it = items.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.id == id) {
                    it.remove()
                    return item
                }
                if (item.isFolder) {
                    val found = removeRec(item.children)
                    if (found != null) return found
                }
            }
            return null
        }
        val removed = removeRec(layers)
        if (removed != null && activeLayerId == id) {
            activeLayerId = layers.firstOrNull { it is DrawingLayer }?.id
                ?: layers.firstOrNull()?.id ?: ""
        }
        return removed
    }

    /** Sisipkan kembali layer (untuk undo hapus) tanpa merebut seleksi yang masih valid. */
    fun insertLayerAt(index: Int, layer: LayerItem) {
        if (findLayerById(layer.id) != null) return
        layers.add(index.coerceIn(0, layers.size), layer)
        if (findLayerById(activeLayerId) == null && layers.isNotEmpty()) {
            activeLayerId = layers.first().id
        }
    }

    fun indexOfLayer(id: String): Int = layers.indexOfFirst { it.id == id }

    /** Cari layer apa pun (gambar/teks/folder) berdasarkan id, termasuk isi folder. */
    fun findLayerById(id: String): LayerItem? {
        fun find(items: List<LayerItem>): LayerItem? {
            for (item in items) {
                if (item.id == id) return item
                if (item.isFolder) {
                    val found = find(item.children)
                    if (found != null) return found
                }
            }
            return null
        }
        return find(layers)
    }

    /**
     * Urutkan ulang layer berdasarkan [LayerItem.restoreZ] (posisi asli saat
     * project disimpan). Dipanggil setelah restore teks DAN image selesai,
     * supaya z-order gabungan kembali persis seperti waktu disimpan.
     * Aman dipanggil berulang: setiap daftar disalin lalu ditimpa utuh.
     */
    fun sortByRestoreZ() {
        fun sortList(items: MutableList<LayerItem>) {
            val sorted = items.sortedBy { it.restoreZ }
            items.clear()
            items.addAll(sorted)
            for (item in sorted) if (item.isFolder) sortList(item.children)
        }
        sortList(layers)
    }

    /**
     * Semua TextLayer yang EFEKTIF terlihat (rekursif; folder tersembunyi
     * menyembunyikan isinya). Untuk hit-test, frame seleksi, dan blit aman.
     */
    fun visibleTextLayers(): List<TextLayer> {
        val out = mutableListOf<TextLayer>()
        fun walk(items: List<LayerItem>, ancestorsVisible: Boolean) {
            for (item in items) {
                val vis = ancestorsVisible && item.isVisible
                if (item is TextLayer) {
                    if (vis) out.add(item)
                } else if (item.isFolder) {
                    walk(item.children, vis)
                }
            }
        }
        walk(layers, true)
        return out
    }

    /** Cari TextLayer berdasar box id (rekursif, termasuk dalam folder). */
    fun findTextLayerByBoxId(boxId: String): TextLayer? {
        fun walk(items: List<LayerItem>): TextLayer? {
            for (item in items) {
                if (item is TextLayer && item.box.id == boxId) return item
                if (item.isFolder) {
                    walk(item.children)?.let { return it }
                }
            }
            return null
        }
        return walk(layers)
    }

    /** Pindahkan layer top-level ke indeks tujuan (untuk reorder + undo). */
    fun moveLayerTo(id: String, toIndex: Int): Boolean {
        val from = indexOfLayer(id)
        if (from < 0) return false
        val item = layers.removeAt(from)
        layers.add(toIndex.coerceIn(0, layers.size), item)
        return true
    }

    fun moveLayerUp(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = layers.removeAt(index)
            layers.add(index - 1, item)
        }
    }

    fun moveLayerDown(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index >= 0 && index < layers.size - 1) {
            val item = layers.removeAt(index)
            layers.add(index + 1, item)
        }
    }

    fun renderComposite(targetBitmap: Bitmap) {
        renderInternal(targetBitmap, withText = true)
    }

    /**
     * Render HANYA layer gambar (tanpa teks) untuk file basis project.
     * Basis tanpa teks + JSON teks terpisah = teks tetap editable setelah
     * apk ditutup (tidak lagi menyatu/baked ke Layer 1).
     */
    /**
     * Render layer piksel + image (tanpa teks) ke bitmap target.
     *
     * @param includeImage false saat image layer ikut DISIMPAN sebagai layer
     *   editable (lihat ImageLayerStore). Kalau image tetap di-render ke PNG
     *   basis DAN sekaligus dipulihkan sebagai layer, gambarnya jadi dobel.
     */
    fun renderDrawingOnly(targetBitmap: Bitmap, includeImage: Boolean = true) {
        renderInternal(targetBitmap, withText = false, withImage = includeImage)
    }

    private fun renderInternal(targetBitmap: Bitmap, withText: Boolean, withImage: Boolean = true) {
        val canvas = Canvas(targetBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val flatLayers = mutableListOf<LayerItem>()
        fun collectLayers(items: List<LayerItem>) {
            for (item in items) {
                if (item.isVisible) {
                    if (item is DrawingLayer || item is TextLayer ||
                        (item is ImageLayer && withImage)
                    ) {
                        flatLayers.add(item)
                    } else if (item.isFolder) {
                        collectLayers(item.children)
                    }
                }
            }
        }
        collectLayers(layers)

        var baseMaskBitmap: Bitmap? = null

        for (i in flatLayers.indices.reversed()) {
            val layer = flatLayers[i]
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = (layer.opacity * 255).toInt()
            paint.xfermode = PorterDuffXfermode(layer.blendMode.porterDuffMode)

            if (layer is DrawingLayer) {
                val bmp = layer.getBitmap()
                if (layer.isClippingMask && baseMaskBitmap != null) {
                    val tempLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val tempCanvas = Canvas(tempLayer)
                    tempCanvas.drawBitmap(bmp, 0f, 0f, null)

                    val clipPaint = Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    tempCanvas.drawBitmap(baseMaskBitmap, 0f, 0f, clipPaint)
                    canvas.drawBitmap(tempLayer, 0f, 0f, paint)
                    tempLayer.recycle()
                } else {
                    canvas.drawBitmap(bmp, 0f, 0f, paint)
                    if (!layer.isClippingMask) {
                        baseMaskBitmap = bmp
                    }
                }
            } else if (layer is ImageLayer) {
                if (layer.bitmap.isRecycled) continue
                canvas.save()
                canvas.concat(layer.matrix())
                canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
                canvas.restore()
            } else if (layer is TextLayer) {
                if (!withText) continue
                // Jalur cepat: tanpa bitmap intermediate saat opacity penuh & blend normal.
                if (layer.opacity >= 1f && layer.blendMode == LayerBlendMode.NORMAL) {
                    TextRenderer.render(canvas, layer.box)
                } else {
                    renderStyledTextCropped(canvas, layer, paint)
                }
            }
        }
    }

    /**
     * Render teks ber-blend/opacity ke bitmap SEBESAR BOUNDS teks (bukan
     * full-canvas): di 720x16000 versi lama mengalokasi 46MB per teks per
     * render → OOM/crash saat brush memaksa render penuh tiap move.
     * Mode blend eksotis (CLEAR/XOR/DST) tetap pakai jalur full-canvas
     * karena piksel transparan ikut memengaruhi hasil.
     */
    private fun renderStyledTextCropped(
        canvas: Canvas,
        layer: TextLayer,
        paint: Paint
    ) {
        val useCropped = when (layer.blendMode) {
            LayerBlendMode.NORMAL, LayerBlendMode.MULTIPLY, LayerBlendMode.SCREEN,
            LayerBlendMode.DARKEN, LayerBlendMode.LIGHTEN, LayerBlendMode.ADD,
            LayerBlendMode.SRC_ATOP -> true
            else -> false
        }
        if (!useCropped) {
            var full: Bitmap? = null
            try {
                full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                TextRenderer.render(Canvas(full), layer.box)
                canvas.drawBitmap(full, 0f, 0f, paint)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                // Daripada crash: gambar langsung (efek blend dikorbankan).
                runCatching { TextRenderer.render(canvas, layer.box) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runCatching { full?.recycle() }
            }
            return
        }
        val b = layer.box.getBounds()
        val li = b.left.toInt().coerceIn(0, width)
        val ti = b.top.toInt().coerceIn(0, height)
        val ri = (b.right + 0.999f).toInt().coerceIn(0, width)
        val bi = (b.bottom + 0.999f).toInt().coerceIn(0, height)
        if (ri - li < 2 || bi - ti < 2) return
        var cropped: Bitmap? = null
        try {
            cropped = Bitmap.createBitmap(ri - li, bi - ti, Bitmap.Config.ARGB_8888)
            val tc = Canvas(cropped)
            tc.translate(-li.toFloat(), -ti.toFloat())
            TextRenderer.render(tc, layer.box)
            canvas.drawBitmap(cropped, li.toFloat(), ti.toFloat(), paint)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            runCatching { TextRenderer.render(canvas, layer.box) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { cropped?.recycle() }
        }
    }
}
