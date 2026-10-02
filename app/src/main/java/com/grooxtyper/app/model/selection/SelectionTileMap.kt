package com.grooxtyper.app.model.selection

import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Peta seleksi jarang (sparse): hanya tile yang tersentuh yang dialokasi.
 *
 * Tile yang tidak terkena seleksi tidak perlu dialokasi sama sekali. Semua
 * operasi boolean (gabung, kurang, iris) dan morfolofi (melebar, menyempit,
 * feather, border) berjalan per tile di dalam kotak batas, tanpa pernah
 * membuat `BooleanArray` sebesar kanvas.
 *
 * Kunci tile dipaket jadi Long: 32 bit atas = tileX, 32 bit bawah = tileY.
 */
class SelectionTileMap(
    val tileSize: Int = 256
) {
    private val tiles = HashMap<Long, SelectionTile>()

    /** Banyak tile yang dialokasi saat ini. */
    fun tileCount(): Int = tiles.size

    /** Ambil tile bila ada, null bila tile itu kosong. */
    fun get(tileX: Int, tileY: Int): SelectionTile? =
        tiles[key(tileX, tileY)]

    /**
     * Ambil tile, buat bila belum ada. Ukuran tile tepi dipotong mengikuti
     * sisa kanvas supaya tidak keluar batas.
     */
    fun getOrCreate(tileX: Int, tileY: Int, canvasWidth: Int, canvasHeight: Int): SelectionTile {
        val k = key(tileX, tileY)
        val ada = tiles[k]
        if (ada != null) return ada
        val w = min(tileSize, canvasWidth - tileX * tileSize).coerceAtLeast(1)
        val h = min(tileSize, canvasHeight - tileY * tileSize).coerceAtLeast(1)
        val baru = SelectionTile(w, h)
        tiles[k] = baru
        return baru
    }

    /** Nilai 0..255 pada koordinat kanvas, 0 bila tile kosong. */
    fun getPixel(x: Int, y: Int): Int {
        val t = tiles[key(x / tileSize, y / tileSize)] ?: return 0
        return t.get(x % tileSize, y % tileSize)
    }

    /** Tulis nilai 0..255 pada koordinat kanvas. */
    fun setPixel(x: Int, y: Int, value: Int, canvasWidth: Int, canvasHeight: Int) {
        if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) return
        val tx = x / tileSize
        val ty = y / tileSize
        if (value == 0) {
            val t = tiles[key(tx, ty)] ?: return
            t.set(x % tileSize, y % tileSize, 0)
            if (t.isEmpty()) tiles.remove(key(tx, ty))
            return
        }
        getOrCreate(tx, ty, canvasWidth, canvasHeight).set(x % tileSize, y % tileSize, value)
    }

    /** Buang tile yang kosong supaya peta tetap jarang. */
    fun removeEmptyTiles() {
        val buang = ArrayList<Long>()
        for ((k, t) in tiles) if (t.isEmpty()) buang.add(k)
        for (k in buang) tiles.remove(k)
    }

    /** Kosongkan seluruh seleksi. */
    fun clear() {
        tiles.clear()
    }

    /** True bila tidak ada satu pun piksel terpilih. */
    fun isEmpty(): Boolean {
        for (t in tiles.values) if (!t.isEmpty()) return false
        return true
    }

    /** Jumlah piksel terpilih di semua tile. */
    fun pixelCount(): Long {
        var n = 0L
        for (t in tiles.values) n += t.count
        return n
    }

    /** Iterasi tiap tile beserta koordinat tile-nya. */
    fun forEachTile(op: (tileX: Int, tileY: Int, tile: SelectionTile) -> Unit) {
        for ((k, t) in tiles) op(tileX(k), tileY(k), t)
    }

    /**
     * Kotak batas seleksi dalam koordinat kanvas, null bila kosong.
     *
     * Dihitung dari tile yang ada saja, jadi murah walau kanvas raksasa.
     */
    fun getBounds(): Rect? {
        var kiri = Int.MAX_VALUE
        var atas = Int.MAX_VALUE
        var kanan = Int.MIN_VALUE
        var bawah = Int.MIN_VALUE
        var ada = false
        for ((k, t) in tiles) {
            if (t.isEmpty()) continue
            val ox = tileX(k) * tileSize
            val oy = tileY(k) * tileSize
            var l = t.width
            var r = -1
            var u = t.height
            var d = -1
            for (y in 0 until t.height) {
                val row = y * t.width
                for (x in 0 until t.width) {
                    if ((t.mask[row + x].toInt() and 0xFF) == 0) continue
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < u) u = y
                    if (y > d) d = y
                }
            }
            if (r < 0) continue
            ada = true
            if (ox + l < kiri) kiri = ox + l
            if (oy + u < atas) atas = oy + u
            if (ox + r + 1 > kanan) kanan = ox + r + 1
            if (oy + d + 1 > bawah) bawah = oy + d + 1
        }
        if (!ada) return null
        return Rect(kiri, atas, kanan, bawah)
    }

    /**
     * Gabungkan [lain] ke peta ini sesuai [mode].
     *
     * NEW mengganti, ADD mengambil nilai terbesar, SUBTRACT mengosongkan
     * yang ada di [lain], INTERSECT hanya menyisakan irisan.
     */
    fun combine(lain: SelectionTileMap, mode: SelectionMode, canvasWidth: Int, canvasHeight: Int) {
        when (mode) {
            SelectionMode.NEW -> {
                tiles.clear()
                lain.forEachTile { tx, ty, t ->
                    val salin = t.copy()
                    tiles[key(tx, ty)] = salin
                }
            }
            SelectionMode.ADD -> {
                lain.forEachTile { tx, ty, t ->
                    if (!t.isEmpty()) {
                        val milik = getOrCreate(tx, ty, canvasWidth, canvasHeight)
                        for (i in t.mask.indices) {
                            val v = t.mask[i].toInt() and 0xFF
                            if (v == 0) continue
                            val lama = milik.mask[i].toInt() and 0xFF
                            if (v > lama) {
                                if (lama == 0) milik.count++
                                milik.mask[i] = v.toByte()
                            }
                        }
                    }
                }
            }
            SelectionMode.SUBTRACT -> {
                lain.forEachTile { tx, ty, t ->
                    val milik = tiles[key(tx, ty)] ?: return@forEachTile
                    for (i in t.mask.indices) {
                        if ((t.mask[i].toInt() and 0xFF) == 0) continue
                        if (i >= milik.mask.size) continue
                        if ((milik.mask[i].toInt() and 0xFF) != 0) {
                            milik.mask[i] = 0
                            milik.count--
                        }
                    }
                    if (milik.isEmpty()) tiles.remove(key(tx, ty))
                }
            }
            SelectionMode.INTERSECT -> {
                val buang = ArrayList<Long>()
                for ((k, milik) in tiles) {
                    val t = lain.tiles[k]
                    if (t == null) {
                        buang.add(k)
                        continue
                    }
                    val n = min(milik.mask.size, t.mask.size)
                    for (i in 0 until n) {
                        if ((t.mask[i].toInt() and 0xFF) == 0 && (milik.mask[i].toInt() and 0xFF) != 0) {
                            milik.mask[i] = 0
                            milik.count--
                        }
                    }
                    for (i in n until milik.mask.size) {
                        if ((milik.mask[i].toInt() and 0xFF) != 0) {
                            milik.mask[i] = 0
                            milik.count--
                        }
                    }
                    if (milik.isEmpty()) buang.add(k)
                }
                for (k in buang) tiles.remove(k)
            }
        }
    }

    /** Materialkan potongan [bounds] jadi larik coverage untuk operasi morfologi. */
    fun readCrop(bounds: Rect, canvasWidth: Int, canvasHeight: Int): ByteArray {
        val l = bounds.left.coerceIn(0, canvasWidth)
        val t = bounds.top.coerceIn(0, canvasHeight)
        val r = bounds.right.coerceIn(0, canvasWidth)
        val b = bounds.bottom.coerceIn(0, canvasHeight)
        val w = max(0, r - l)
        val h = max(0, b - t)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] = getPixel(l + x, t + y).toByte()
            }
        }
        return out
    }

    /** Tulis kembali potongan [bounds] dari larik coverage. */
    fun writeCrop(bounds: Rect, data: ByteArray, canvasWidth: Int, canvasHeight: Int) {
        val l = bounds.left.coerceIn(0, canvasWidth)
        val t = bounds.top.coerceIn(0, canvasHeight)
        val r = bounds.right.coerceIn(0, canvasWidth)
        val b = bounds.bottom.coerceIn(0, canvasHeight)
        val w = max(0, r - l)
        val h = max(0, b - t)
        if (data.size < w * h) return
        for (y in 0 until h) {
            for (x in 0 until w) {
                setPixel(l + x, t + y, data[y * w + x].toInt() and 0xFF, canvasWidth, canvasHeight)
            }
        }
        removeEmptyTiles()
    }

    /** Salinan dalam untuk riwayat dan macro. */
    fun deepCopy(): SelectionTileMap {
        val out = SelectionTileMap(tileSize)
        for ((k, t) in tiles) out.tiles[k] = t.copy()
        return out
    }

    private fun key(tileX: Int, tileY: Int): Long =
        (tileX.toLong() shl 32) or (tileY.toLong() and 0xFFFFFFFFL)

    private fun tileX(k: Long): Int = (k shr 32).toInt()

    private fun tileY(k: Long): Int = (k and 0xFFFFFFFFL).toInt()
}
