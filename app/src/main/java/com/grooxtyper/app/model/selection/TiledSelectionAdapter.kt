package com.grooxtyper.app.model.selection

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.grooxtyper.app.model.MaskContour
import com.grooxtyper.app.model.SelectionEngine
import kotlin.math.max
import kotlin.math.min

/**
 * Adapter antara sistem seleksi berubin dan [SelectionEngine] lama.
 *
 * Peta tile adalah sumber kebenaran, Path hanya representasi untuk
 * rendering (dibuat dari peta lewat [MaskContour] saat dibutuhkan).
 * Fitur seleksi lama (lasso, oval, salin, tempel) tidak perlu dirombak
 * sekaligus: adapter yang menjembatani keduanya.
 */
class TiledSelectionAdapter(
    private val tileSize: Int = 256
) {
    /** Peta gabungan saat ini (hasil semua mode). */
    val tileMap = SelectionTileMap(tileSize)

    /** Region per identitas untuk script (bubble A, B, C, ...). */
    val regions = ArrayList<SelectionRegion>()

    /** Riwayat operasi seleksi. */
    val history = SelectionHistory()

    /** Mode terakhir yang dipakai (untuk label riwayat). */
    var lastMode: SelectionMode = SelectionMode.NEW
        private set

    /**
     * Terapkan hasil wand sesuai [mode], lalu catat riwayat.
     *
     * @return info bubble untuk script dan auto-layout teks.
     */
    fun apply(
        result: WandResult,
        mode: SelectionMode,
        canvasWidth: Int,
        canvasHeight: Int,
        label: String? = null
    ): BubbleInfo {
        lastMode = mode
        when (mode) {
            SelectionMode.NEW -> {
                tileMap.combine(result.selection, SelectionMode.NEW, canvasWidth, canvasHeight)
                regions.clear()
                regions.addAll(result.regions.ifEmpty {
                    val b = result.bounds
                    listOf(SelectionRegion(SelectionRegion.freshId(), Rect(b), result.selection.deepCopy()))
                })
            }
            SelectionMode.ADD -> {
                tileMap.combine(result.selection, SelectionMode.ADD, canvasWidth, canvasHeight)
                regions.addAll(result.regions.ifEmpty {
                    val b = result.bounds
                    listOf(SelectionRegion(SelectionRegion.freshId(), Rect(b), result.selection.deepCopy()))
                })
            }
            SelectionMode.SUBTRACT -> {
                tileMap.combine(result.selection, SelectionMode.SUBTRACT, canvasWidth, canvasHeight)
                dropCoveredRegions(result)
            }
            SelectionMode.INTERSECT -> {
                tileMap.combine(result.selection, SelectionMode.INTERSECT, canvasWidth, canvasHeight)
                dropOutsideRegions()
            }
        }
        tileMap.removeEmptyTiles()
        history.push(label ?: mode.name, tileMap)
        return BubbleInfo.from(result)
    }

    /** Kosongkan seleksi dan region, catat riwayat. */
    fun clear() {
        tileMap.clear()
        regions.clear()
        history.push("Bersih", tileMap)
    }

    /** Batas gabungan, null bila kosong. */
    fun bounds(): Rect? = tileMap.getBounds()

    /** Jumlah piksel gabungan. */
    fun pixelCount(): Long = tileMap.pixelCount()

    /**
     * Bangun Path untuk rendering, dibatasi [viewport] bila ada.
     *
     * Hanya tile yang terlihat yang diubah jadi kontur, jadi kanvas
     * 720x16000 tidak me-render seleksi 16000px saat user hanya
     * melihat 720x900.
     */
    fun toPath(viewport: RectF?, canvasWidth: Int, canvasHeight: Int): Path =
        tileMapToPath(tileMap, viewport, canvasWidth, canvasHeight)

    /**
     * Dorong keadaan adapter ke [SelectionEngine] lama (satu Path gabungan
     * + mask), supaya overlay, salin, tempel, dan hapus yang sudah ada
     * langsung bekerja tanpa dirombak.
     */
    fun pushToEngine(engine: SelectionEngine, canvasWidth: Int, canvasHeight: Int) {
        engine.clearSelection()
        if (tileMap.isEmpty()) return
        val path = toPath(null, canvasWidth, canvasHeight)
        val b = RectF()
        path.computeBounds(b, true)
        if (b.isEmpty) return
        engine.addRegionPath(path, b)
    }

    /**
     * Isi peta tile dari satu [Path] (seleksi non-wand: lasso, kotak,
     * elips, poligon).
     *
     * Jembatan ini bikin operasi refine (expand/contract/feather/border/
     * smooth) berlaku untuk SEMUA tool seleksi, bukan hanya Magic Wand.
     * Path di-raster memakai coverage alpha supaya tepi anti-alias tetap
     * jadi 0..255 (bukan boolean), lalu peta tile jadi sumber kebenaran.
     *
     * Batas 4MP sama seperti operasi morfologi: seleksi raksasa di kanvas
     * 720x16000 tidak boleh.alokasi bitmap sebesar kanvas.
     *
     * @return true bila berhasil, false bila area terlalu besar/kosong.
     */
    fun rasterizePath(path: Path, canvasWidth: Int, canvasHeight: Int): Boolean {
        val b = RectF()
        path.computeBounds(b, true)
        if (b.isEmpty) return false
        val left = max(0, b.left.toInt())
        val top = max(0, b.top.toInt())
        val right = min(canvasWidth, b.right.toInt() + 1)
        val bottom = min(canvasHeight, b.bottom.toInt() + 1)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return false
        if (w.toLong() * h > 4_000_000L) return false
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val cv = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.WHITE
            }
            cv.translate(-left.toFloat(), -top.toFloat())
            cv.drawPath(path, p)
            cv.translate(left.toFloat(), top.toFloat())
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            tileMap.clear()
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val a = (px[row + x] ushr 24) and 0xFF
                    if (a > 0) tileMap.setPixel(left + x, top + y, a, canvasWidth, canvasHeight)
                }
            }
        } finally {
            runCatching { bmp.recycle() }
        }
        tileMap.removeEmptyTiles()
        history.push("Raster path", tileMap)
        return !tileMap.isEmpty()
    }

    /**
     * Operasi morfologi dalam batas seleksi + radius, tanpa bitmap
     * sebesar kanvas.
     */
    fun expand(pixels: Int, canvasWidth: Int, canvasHeight: Int) {
        morph(pixels, canvasWidth, canvasHeight, forExpand = true)
    }

    /** Sempitkan seleksi sejauh [pixels] piksel. */
    fun contract(pixels: Int, canvasWidth: Int, canvasHeight: Int) {
        morph(pixels, canvasWidth, canvasHeight, forExpand = false)
    }

    /** Samarkan tepi sejauh [radius] piksel (blur kotak dua arah). */
    fun feather(radius: Int, canvasWidth: Int, canvasHeight: Int) {
        val r = radius.coerceIn(1, 40)
        val bounds = tileMap.getBounds() ?: return
        val gl = max(0, bounds.left - r)
        val gt = max(0, bounds.top - r)
        val gr = min(canvasWidth, bounds.right + r)
        val gb = min(canvasHeight, bounds.bottom + r)
        val w = gr - gl
        val h = gb - gt
        if (w <= 0 || h <= 0 || w.toLong() * h > 4_000_000L) return
        val crop = tileMap.readCrop(android.graphics.Rect(gl, gt, gr, gb), canvasWidth, canvasHeight)
        val src = IntArray(w * h) { crop[it].toInt() and 0xFF }
        val tmp = IntArray(w * h)
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0
                var n = 0
                for (k in -r..r) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    sum += src[row + xx]
                    n++
                }
                tmp[row + x] = sum / n
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sum = 0
                var n = 0
                for (k in -r..r) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    sum += tmp[yy * w + x]
                    n++
                }
                dst[y * w + x] = sum / n
            }
        }
        val back = ByteArray(w * h) { dst[it].toByte() }
        tileMap.writeCrop(android.graphics.Rect(gl, gt, gr, gb), back, canvasWidth, canvasHeight)
        history.push("Feather $radius", tileMap)
    }

    /** Seleksi border: melebar lalu kurangi yang menyempit. */
    fun border(widthPx: Int, canvasWidth: Int, canvasHeight: Int) {
        val half = max(1, widthPx / 2)
        val salin = tileMap.deepCopy()
        expand(half, canvasWidth, canvasHeight)
        val luar = tileMap.deepCopy()
        tileMap.combine(salin, SelectionMode.NEW, canvasWidth, canvasHeight)
        contract(half, canvasWidth, canvasHeight)
        val dalam = tileMap.deepCopy()
        tileMap.combine(luar, SelectionMode.NEW, canvasWidth, canvasHeight)
        tileMap.combine(dalam, SelectionMode.SUBTRACT, canvasWidth, canvasHeight)
        history.push("Border $widthPx", tileMap)
    }

    /** Ratakan tepi: feather kecil lalu ambang 128. */
    fun smooth(radius: Int, canvasWidth: Int, canvasHeight: Int) {
        feather(radius.coerceAtLeast(1), canvasWidth, canvasHeight)
        val bounds = tileMap.getBounds() ?: return
        val crop = tileMap.readCrop(bounds, canvasWidth, canvasHeight)
        for (i in crop.indices) {
            crop[i] = if ((crop[i].toInt() and 0xFF) >= 128) 255.toByte() else 0
        }
        tileMap.writeCrop(bounds, crop, canvasWidth, canvasHeight)
        history.push("Smooth", tileMap)
    }

    private fun morph(pixels: Int, canvasWidth: Int, canvasHeight: Int, forExpand: Boolean) {
        val r = pixels.coerceIn(1, 30)
        val bounds = tileMap.getBounds() ?: return
        val gl = max(0, bounds.left - r)
        val gt = max(0, bounds.top - r)
        val gr = min(canvasWidth, bounds.right + r)
        val gb = min(canvasHeight, bounds.bottom + r)
        val w = gr - gl
        val h = gb - gt
        if (w <= 0 || h <= 0 || w.toLong() * h > 4_000_000L) return
        val crop = tileMap.readCrop(android.graphics.Rect(gl, gt, gr, gb), canvasWidth, canvasHeight)
        val solid = BooleanArray(w * h) { (crop[it].toInt() and 0xFF) >= 128 }
        val out = BooleanArray(w * h)
        val r2 = r * r
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (forExpand) {
                    var kena = solid[y * w + x]
                    if (!kena) {
                        outer@ for (dy in -r..r) {
                            for (dx in -r..r) {
                                if (dx * dx + dy * dy > r2) continue
                                val nx = x + dx
                                val ny = y + dy
                                if (nx in 0 until w && ny in 0 until h && solid[ny * w + nx]) {
                                    kena = true
                                    break@outer
                                }
                            }
                        }
                    }
                    out[y * w + x] = kena
                } else {
                    var kena = !solid[y * w + x]
                    if (!kena) {
                        outer@ for (dy in -r..r) {
                            for (dx in -r..r) {
                                if (dx * dx + dy * dy > r2) continue
                                val nx = x + dx
                                val ny = y + dy
                                val luar = nx !in 0 until w || ny !in 0 until h || !solid[ny * w + nx]
                                if (luar) {
                                    kena = true
                                    break@outer
                                }
                            }
                        }
                    }
                    out[y * w + x] = !kena && solid[y * w + x]
                }
            }
        }
        val back = ByteArray(w * h) { if (out[it]) 255.toByte() else 0 }
        tileMap.writeCrop(android.graphics.Rect(gl, gt, gr, gb), back, canvasWidth, canvasHeight)
        history.push(if (forExpand) "Expand $pixels" else "Contract $pixels", tileMap)
    }

    private fun dropCoveredRegions(result: WandResult) {
        val b = result.bounds
        val sisa = regions.filterNot { r ->
            r.bounds.left >= b.left && r.bounds.top >= b.top &&
                r.bounds.right <= b.right && r.bounds.bottom <= b.bottom
        }
        regions.clear()
        regions.addAll(sisa)
    }

    private fun dropOutsideRegions() {
        val box = tileMap.getBounds() ?: run {
            regions.clear()
            return
        }
        val sisa = regions.filter { r ->
            android.graphics.Rect.intersects(r.bounds, box)
        }
        regions.clear()
        regions.addAll(sisa)
    }
}

/**
 * Bangun Path dari peta tile mana pun (bukan hanya milik adapter).
 *
 * Dipakai mode bubble script: hasil [HugeWandEngine] diubah jadi Path
 * area tanpa menyentuh keadaan seleksi wand yang sedang aktif.
 */
fun tileMapToPath(
    map: SelectionTileMap,
    viewport: RectF?,
    canvasWidth: Int,
    canvasHeight: Int
): Path {
    val out = Path()
    val box: Rect? = if (viewport == null) {
        map.getBounds()
    } else {
        val l = viewport.left.toInt().coerceIn(0, canvasWidth)
        val t = viewport.top.toInt().coerceIn(0, canvasHeight)
        val r = viewport.right.toInt().coerceIn(0, canvasWidth)
        val b = viewport.bottom.toInt().coerceIn(0, canvasHeight)
        if (r - l < 2 || b - t < 2) null else Rect(l, t, r, b)
    }
    if (box == null) return out
    // Kontur per tile: tiap tile jadi loop tertutup sendiri-sendiri
    // supaya tidak ada BooleanArray sebesar batas seleksi.
    map.forEachTile { tx, ty, tile ->
        if (tile.isEmpty()) return@forEachTile
        val ox = tx * map.tileSize
        val oy = ty * map.tileSize
        if (ox + tile.width <= box.left || ox >= box.right) return@forEachTile
        if (oy + tile.height <= box.top || oy >= box.bottom) return@forEachTile
        val bin = BooleanArray(tile.width * tile.height)
        for (i in bin.indices) bin[i] = (tile.mask[i].toInt() and 0xFF) != 0
        val p = MaskContour.build(bin, tile.width, tile.height, 1f, ox.toFloat(), oy.toFloat())
        if (p != null) out.addPath(p)
    }
    return out
}
