package com.grooxtyper.app.model.selection

import android.graphics.Rect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mesin wand untuk kanvas berapa pun (termasuk 720x16000).
 *
 * Beda dengan wand lama yang mengecilkan seluruh halaman ke 480px lalu
 * membaca jendela tetap 512/1200px: mesin ini bekerja per tile 256px.
 * Region boleh melintasi tile berapa pun tanpa batas artifisial, dan
 * hanya tile yang diperlukan yang dibaca dari [CanvasPixelProvider].
 *
 * Tidak ada `BooleanArray` sebesar kanvas, tidak ada bitmap seleksi
 * sebesar kanvas, tidak ada `Bitmap.createBitmap` selebar kanvas hanya
 * untuk wand. Hasilnya [SelectionTileMap] yang jarang.
 *
 * Flood fill memakai span scanline (satu entri stack = satu rentang
 * piksel, bukan satu piksel) supaya antrean kecil di area besar.
 * Bisa dibatalkan: tiap 2048 span dicek `ensureActive`, jadi seleksi
 * raksasa tidak membuat UI hang.
 */
class HugeWandEngine(
    private val provider: CanvasPixelProvider,
    val canvasWidth: Int = provider.canvasWidth,
    val canvasHeight: Int = provider.canvasHeight,
    private val tileSize: Int = 256
) {

    /** Batas piksel seleksi agar ketuk di latar raksasa tetap aman. */
    var maxPixels: Long = 12_000_000L

    /** Radius penutup celah outline 0..3 (1 menutup celah 1-2px). */
    var closeGap: Int = 1

    /** Ambang dinding outline 0..255, makin kecil makin ketat. */
    var wallLevel: Int = 64

    /**
     * Pilih wilayah dari titik seed.
     *
     * @param tolerance jarak warna 0..255 dalam sRGB
     * @param contiguous true = hanya yang terhubung seed; false = semua
     *   piksel cocok di seluruh kanvas ikut terpilih (lebih mahal)
     * @param bubbleAware true = outline diperlakukan sebagai batas keras
     *   dan gelembung yang menyatu dipisah (hanya milik seed yang kembali)
     * @param separateBubble true = pecah gumpalan menyatu via watershed
     *   lalu simpan milik seed; false = biarkan apa adanya
     * @param antialias true = tepi diberi coverage bertingkat (0..255)
     */
    suspend fun select(
        seedX: Int,
        seedY: Int,
        tolerance: Int,
        contiguous: Boolean = true,
        bubbleAware: Boolean = false,
        separateBubble: Boolean = true,
        antialias: Boolean = true
    ): WandResult? {
        if (seedX !in 0 until canvasWidth || seedY !in 0 until canvasHeight) return null
        if (canvasWidth <= 0 || canvasHeight <= 0) return null
        val tol = tolerance.coerceIn(0, 255)
        val seed = provider.getPixel(seedX, seedY)
        if ((seed ushr 24) and 0xFF == 0) return null

        val out = if (contiguous) {
            floodContiguous(seedX, seedY, seed, tol, bubbleAware)
        } else {
            floodGlobal(seed, tol, bubbleAware)
        } ?: return null
        if (out.pixelCount <= 0L) return null

        // Pemisahan bubble: hanya milik seed yang kembali.
        var map = out.map
        var parts = 1
        var confidence: Float? = null
        if (separateBubble || bubbleAware) {
            val bounds = map.getBounds() ?: return null
            val pisah = splitToSeed(map, bounds, seedX, seedY)
            if (pisah != null) {
                map = pisah.first
                parts = pisah.second
                confidence = pisah.third
            } else if (bubbleAware) {
                confidence = BubbleSeparator.closedConfidence(
                    countWalls(map, bounds), 2 * (bounds.width() + bounds.height())
                )
            }
        }

        // Anti-alias: tepi diberi coverage bertingkat dari jarak warna.
        if (antialias) {
            rampEdges(map, seed, tol)
        }

        val bounds = map.getBounds() ?: return null
        val regions = listOf(
            SelectionRegion(SelectionRegion.freshId(), Rect(bounds), map)
        )
        return WandResult(map, Rect(bounds), map.pixelCount(), regions, confidence)
    }

    /** Hasil flood: peta + jumlah piksel. */
    private class FloodOut(val map: SelectionTileMap, val pixelCount: Long)

    /**
     * Flood fill terhubung lintas tile (span scanline).
     *
     * Peta menyimpan 2 = terpilih, 1 = pernah dikunjungi tapi ditolak,
     * 0 = belum dikunjungi. Nilai 1 dibersihkan di akhir.
     */
    private suspend fun floodContiguous(
        seedX: Int, seedY: Int, seed: Int, tol: Int, bubbleAware: Boolean
    ): FloodOut? {
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr2 = tol * tol
        val map = SelectionTileMap(tileSize)
        // Span stack: 3 int per entri (y, dari, sampaiEksklusif).
        var stack = IntArray(3 * 1024)
        var sp = 0
        fun pushSpan(y: Int, dari: Int, sampai: Int) {
            if (sampai <= dari) return
            if (y !in 0 until canvasHeight) return
            if (sp + 3 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[sp++] = y
            stack[sp++] = dari.coerceIn(0, canvasWidth)
            stack[sp++] = sampai.coerceIn(0, canvasWidth)
        }
        pushSpan(seedY, seedX, seedX + 1)
        var spans = 0
        var count = 0L
        // Cache baris supaya piksel tidak dibaca berulang dari provider.
        val rows = RowCache()
        while (sp > 0) {
            sp -= 3
            val y = stack[sp]
            val dari = stack[sp + 1]
            val sampai = stack[sp + 2]
            if (y !in 0 until canvasHeight) continue
            if (++spans % 2048 == 0) currentCoroutineContext().ensureActive()
            var x = dari
            while (x < sampai) {
                if (count > maxPixels) return FloodOut(map, count)
                val sudah = map.getPixel(x, y)
                if (sudah != 0) {
                    x++
                    continue
                }
                if (!matchRow(rows, y, x, sr, sg, sb, thr2, bubbleAware)) {
                    map.setPixel(x, y, 1, canvasWidth, canvasHeight)
                    x++
                    continue
                }
                // Lebar ke kiri.
                var kiri = x
                while (kiri > 0 && map.getPixel(kiri - 1, y) == 0 &&
                    matchRow(rows, y, kiri - 1, sr, sg, sb, thr2, bubbleAware)
                ) {
                    kiri--
                }
                // Lebar ke kanan.
                var kanan = x + 1
                while (kanan < canvasWidth && map.getPixel(kanan, y) == 0 &&
                    matchRow(rows, y, kanan, sr, sg, sb, thr2, bubbleAware)
                ) {
                    kanan++
                }
                for (xx in kiri until kanan) {
                    map.setPixel(xx, y, 255, canvasWidth, canvasHeight)
                    count++
                }
                if (y + 1 < canvasHeight) pushSpan(y + 1, kiri, kanan)
                if (y - 1 >= 0) pushSpan(y - 1, kiri, kanan)
                x = kanan
            }
        }
        // Bersihkan tanda "pernah dikunjungi tapi ditolak" (nilai 1 -> 0).
        map.forEachTile { _, _, t ->
            for (i in t.mask.indices) {
                if ((t.mask[i].toInt() and 0xFF) == 1) t.mask[i] = 0
            }
            t.recount()
        }
        map.removeEmptyTiles()
        return FloodOut(map, count)
    }

    /**
     * Flood tak-terhubung: semua piksel cocok di seluruh kanvas.
     *
     * Berjalan tile per tile supaya tidak ada alokasi sebesar kanvas.
     * Lebih mahal dari contiguous; dipakai untuk mode Contiguous OFF.
     */
    private suspend fun floodGlobal(seed: Int, tol: Int, bubbleAware: Boolean): FloodOut? {
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr2 = tol * tol
        val map = SelectionTileMap(tileSize)
        var count = 0L
        val tilesX = (canvasWidth + tileSize - 1) / tileSize
        val tilesY = (canvasHeight + tileSize - 1) / tileSize
        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                currentCoroutineContext().ensureActive()
                val x0 = tx * tileSize
                val y0 = ty * tileSize
                val w = min(tileSize, canvasWidth - x0)
                val h = min(tileSize, canvasHeight - y0)
                if (w <= 0 || h <= 0) continue
                val px = provider.getPixels(x0, y0, w, h)
                for (i in px.indices) {
                    if (count > maxPixels) return FloodOut(map, count)
                    val p = px[i]
                    if ((p ushr 24) and 0xFF == 0) continue
                    val dr = ((p shr 16) and 0xFF) - sr
                    val dg = ((p shr 8) and 0xFF) - sg
                    val db = (p and 0xFF) - sb
                    if (dr * dr + dg * dg + db * db > thr2) continue
                    if (bubbleAware && isBlocked(p, x0 + i % w, y0 + i / w)) continue
                    map.setPixel(x0 + i % w, y0 + i / w, 255, canvasWidth, canvasHeight)
                    count++
                }
            }
        }
        return FloodOut(map, count)
    }

    /** Cache baris piksel: satu baris penuh dibaca sekali lalu dipakai ulang. */
    private inner class RowCache {
        private val simpan = LinkedHashMap<Int, IntArray>()
        fun row(y: Int): IntArray? {
            val ada = simpan[y]
            if (ada != null) return ada
            if (y !in 0 until canvasHeight) return null
            val baris = provider.getPixels(0, y, canvasWidth, 1)
            simpan[y] = baris
            while (simpan.size > 64) {
                val buang = simpan.keys.first()
                simpan.remove(buang)
            }
            return baris
        }
    }

    /** True bila piksel baris cocok (warna mirip dan bukan dinding saat bubbleAware). */
    private fun matchRow(
        rows: RowCache, y: Int, x: Int,
        sr: Int, sg: Int, sb: Int, thr2: Int, bubbleAware: Boolean
    ): Boolean {
        val baris = rows.row(y) ?: return false
        if (x !in baris.indices) return false
        val p = baris[x]
        if ((p ushr 24) and 0xFF == 0) return false
        val dr = ((p shr 16) and 0xFF) - sr
        val dg = ((p shr 8) and 0xFF) - sg
        val db = (p and 0xFF) - sb
        if (dr * dr + dg * dg + db * db > thr2) return false
        if (bubbleAware && isBlocked(p, x, y)) return false
        return true
    }

    /**
     * True bila piksel adalah dinding yang tak boleh dilewati saat
     * bubbleAware: outline gelap, diperlebar [closeGap] supaya celah
     * 1-2px tidak menjadi jalan bocor.
     */
    private fun isBlocked(p: Int, x: Int, y: Int): Boolean {
        if (BubbleSeparator.isWall(p, wallLevel)) return true
        if (closeGap <= 0) return false
        // Perlebar dinding: cek tetangga dalam radius closeGap.
        for (dy in -closeGap..closeGap) {
            for (dx in -closeGap..closeGap) {
                if (dx == 0 && dy == 0) continue
                val q = provider.getPixel(x + dx, y + dy)
                if (BubbleSeparator.isWall(q, wallLevel)) return true
            }
        }
        return false
    }

    /**
     * Pecah gumpalan menyatu, kembalikan hanya milik seed.
     *
     * Potongan dibatasi area peta supaya tetap cepat: di luar itu
     * wilayah dibiarkan apa adanya.
     */
    private fun splitToSeed(
        map: SelectionTileMap, bounds: Rect, seedX: Int, seedY: Int
    ): Triple<SelectionTileMap, Int, Float>? {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0 || w.toLong() * h > BubbleSeparator.MAX_SPLIT_AREA) return null
        val bin = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                bin[y * w + x] = map.getPixel(bounds.left + x, bounds.top + y) != 0
            }
        }
        val lx = seedX - bounds.left
        val ly = seedY - bounds.top
        val hasil = BubbleSeparator.splitKeepSeed(bin, w, h, lx, ly) ?: return null
        val baru = SelectionTileMap(tileSize)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (hasil.mask[y * w + x]) {
                    baru.setPixel(bounds.left + x, bounds.top + y, 255, canvasWidth, canvasHeight)
                }
            }
        }
        val penuh = w * h
        val confidence = 0.5f + 0.5f * (hasil.partCount - 1).toFloat() / hasil.partCount.toFloat()
        return Triple(baru, hasil.partCount, confidence.coerceIn(0f, 1f) * (hasil.pixelCount.toFloat() / penuh.toFloat()).coerceIn(0.5f, 1f))
    }

    /** Hitung sisi batas yang menyentuh dinding (untuk kepercayaan). */
    private fun countWalls(map: SelectionTileMap, bounds: Rect): Int {
        var n = 0
        val l = bounds.left.coerceIn(0, canvasWidth - 1)
        val t = bounds.top.coerceIn(0, canvasHeight - 1)
        val r = bounds.right.coerceIn(0, canvasWidth)
        val b = bounds.bottom.coerceIn(0, canvasHeight)
        for (x in l until r) {
            if (BubbleSeparator.isWall(provider.getPixel(x, t), wallLevel)) n++
            if (b - 1 != t && BubbleSeparator.isWall(provider.getPixel(x, b - 1), wallLevel)) n++
        }
        for (y in t until b) {
            if (BubbleSeparator.isWall(provider.getPixel(l, y), wallLevel)) n++
            if (r - 1 != l && BubbleSeparator.isWall(provider.getPixel(r - 1, y), wallLevel)) n++
        }
        return n
    }

    /**
     * Tepi anti-alias: piksel batas (punya tetangga tak terpilih) diberi
     * coverage dari jarak warna, interior tetap 255.
     */
    private fun rampEdges(map: SelectionTileMap, seed: Int, tol: Int) {
        if (tol <= 0) return
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val bounds = map.getBounds() ?: return
        val l = bounds.left.coerceIn(0, canvasWidth)
        val t = bounds.top.coerceIn(0, canvasHeight)
        val r = bounds.right.coerceIn(0, canvasWidth)
        val b = bounds.bottom.coerceIn(0, canvasHeight)
        if ((r - l).toLong() * (b - t) > 4_000_000L) return
        for (y in t until b) {
            for (x in l until r) {
                if (map.getPixel(x, y) == 0) continue
                val tetanggaLuar = map.getPixel(x - 1, y) == 0 || map.getPixel(x + 1, y) == 0 ||
                    map.getPixel(x, y - 1) == 0 || map.getPixel(x, y + 1) == 0
                if (!tetanggaLuar) continue
                val p = provider.getPixel(x, y)
                val dr = ((p shr 16) and 0xFF) - sr
                val dg = ((p shr 8) and 0xFF) - sg
                val db = (p and 0xFF) - sb
                val d = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
                val v = ((1f - d / tol.coerceAtLeast(1)) * 255f).toInt().coerceIn(64, 255)
                map.setPixel(x, y, v, canvasWidth, canvasHeight)
            }
        }
    }

    /** Jarak warna RGB Euclidean 0..442 antara dua piksel. */
    fun colorDistance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
    }
}
