package com.grooxtyper.app.model.selection

import com.grooxtyper.app.model.WandEngine
import kotlin.math.max
import kotlin.math.min

/**
 * Pemisah bubble yang berdempetan atau tumpang tindih.
 *
 * Masalahnya: dua bubble berinterior sama-sama putih. Flood fill warna
 * saja menganggap keduanya satu wilayah. Yang memisahkan hanyalah garis
 * outline di antara mereka.
 *
 * Cara kerja:
 *
 * 1. Dinding outline (piksel gelap) tidak pernah dilewati flood. Dengan
 *    begitu dua bubble yang berbagi dinding tipis langsung terpisah
 *    sejak awal.
 * 2. Bila dindingnya bocor (celah 1-2px karena anti-alias atau noise),
 *    wilayah hasil flood dipecah ulang: distance transform -> puncak
 *    (0,7 x jarak maksimum, resep OpenCV) -> label marker -> tumbuhkan
 *    semua marker serentak. Garis tempat dua front bertemu adalah garis
 *    belah, yaitu titik terakhir kedua bubble bersentuhan.
 * 3. Dari semua bagian, hanya bagian yang memuat titik ketuk yang
 *    dikembalikan. Klik bubble A hanya memilih A, klik B hanya B.
 */
object BubbleSeparator {

    /** Batas luas potongan agar pemisahan tetap cepat (2 juta piksel). */
    const val MAX_SPLIT_AREA = 2_000_000

    /**
     * True bila piksel adalah dinding outline (garis gelap pembatas).
     *
     * @param darkLevel ambang kanal maksimum; makin kecil makin ketat.
     */
    fun isWall(argb: Int, darkLevel: Int = 64): Boolean {
        if ((argb ushr 24) and 0xFF == 0) return true
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return max(r, max(g, b)) < darkLevel
    }

    /**
     * Kepercayaan batas 0..1 dari gradien luminansi lokal.
     *
     * Tepi kuat (outline tegas) -> 1, tepi lemah (noise) -> kecil,
     * tanpa tepi -> 0. Noise kecil tidak boleh membuat wand gagal,
     * jadi hanya tepi kuat yang jadi batas keras.
     */
    fun boundaryConfidence(lumaCenter: Float, lumaAround: FloatArray): Float {
        var g = 0f
        for (v in lumaAround) {
            val d = v - lumaCenter
            g += d * d
        }
        g = kotlin.math.sqrt(g / lumaAround.size.coerceAtLeast(1)) / 255f
        return g.coerceIn(0f, 1f)
    }

    /** Luminansi 0..255 satu piksel ARGB. */
    fun luma(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Dari mask biner, simpan hanya komponen yang memuat (seedX, seedY).
     *
     * Dipakai setelah flood: bila dua bubble menyatu lewat celah, yang
     * bukan tempat diketuk dibuang.
     */
    fun keepSeedComponent(
        bin: BooleanArray, w: Int, h: Int, seedX: Int, seedY: Int
    ): BooleanArray? {
        if (seedX !in 0 until w || seedY !in 0 until h) return null
        if (!bin[seedY * w + seedX]) return null
        val (lab, count) = WandEngine.labelComponents(bin, w, h, 4)
        if (count <= 1) return bin
        val want = lab[seedY * w + seedX]
        if (want == 0) return null
        val out = BooleanArray(w * h)
        for (i in bin.indices) if (lab[i] == want) out[i] = true
        return out
    }

    /**
     * Pecah mask yang berisi gumpalan menyatu, kembalikan hanya bagian
     * milik seed. Null bila tidak terpisah (satu bubble utuh).
     *
     * Potongan dibatasi [MAX_SPLIT_AREA] supaya tetap cepat di HP.
     */
    fun splitKeepSeed(
        bin: BooleanArray, w: Int, h: Int, seedX: Int, seedY: Int
    ): SplitResult? {
        if (w.toLong() * h > MAX_SPLIT_AREA) return null
        if (seedX !in 0 until w || seedY !in 0 until h) return null
        if (!bin[seedY * w + seedX]) return null
        var any = 0
        for (b in bin) if (b) any++
        if (any < 160) return null

        val dist = WandEngine.distanceTransform(bin, w, h)
        var maxD = 0f
        for (i in bin.indices) if (bin[i] && dist[i] > maxD) maxD = dist[i]
        if (maxD < 2f) return null
        val thr = max(1f, maxD * 0.7f)
        val peak = BooleanArray(w * h)
        for (i in bin.indices) if (bin[i] && dist[i] >= thr) peak[i] = true
        val minPixels = max(8, (any * 0.04f).toInt())
        val (marks, markCount) = WandEngine.labelComponents(peak, w, h, minPixels)
        if (markCount < 2) return null

        // Tumbuhkan semua marker serentak; front pertama memiliki piksel.
        val owner = IntArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (i in marks.indices) {
            if (marks[i] != 0) {
                owner[i] = marks[i]
                queue[tail++] = i
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            val me = owner[i]
            if (x > 0) tail = claim(owner, bin, queue, i - 1, me, tail)
            if (x < w - 1) tail = claim(owner, bin, queue, i + 1, me, tail)
            if (y > 0) tail = claim(owner, bin, queue, i - w, me, tail)
            if (y < h - 1) tail = claim(owner, bin, queue, i + w, me, tail)
        }
        val want = owner[seedY * w + seedX]
        if (want == 0) return null
        // Hitung berapa bagian yang hidup.
        val hidup = HashSet<Int>()
        for (v in owner) if (v != 0) hidup.add(v)
        if (hidup.size < 2) return null
        val out = BooleanArray(w * h)
        var n = 0
        for (i in owner.indices) {
            if (owner[i] == want) {
                out[i] = true
                n++
            }
        }
        return SplitResult(out, hidup.size, n)
    }

    /** Hasil pemisahan: mask milik seed + info jumlah bagian. */
    class SplitResult(
        val mask: BooleanArray,
        val partCount: Int,
        val pixelCount: Int
    )

    private fun claim(
        owner: IntArray, bin: BooleanArray, queue: IntArray, j: Int, id: Int, tailIn: Int
    ): Int {
        if (owner[j] != 0) return tailIn
        if (!bin[j]) return tailIn
        owner[j] = id
        queue[tailIn] = j
        return tailIn + 1
    }

    /**
     * Geser titik ketuk ke piksel kertas terdekat.
     *
     * Ketukan user sering jatuh di atas TEKS atau garis di dalam gelembung,
     * bukan di kertasnya. Flood dari teks akan memilih teks itu sendiri
     * (benar secara algoritma, salah untuk kebutuhan), jadi cari piksel
     * terang (luminansi > 200) terdekat dalam radius [radiusMax] dan pakai
     * itu sebagai seed. Null bila tidak ada kertas di sekitar.
     */
    fun snapToPaper(
        provider: CanvasPixelProvider,
        x: Int, y: Int,
        radiusMax: Int = 24
    ): Pair<Int, Int>? {
        if (luma(provider.getPixel(x, y)) > 200f) return x to y
        for (r in 1..radiusMax) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until provider.canvasWidth) continue
                    if (ny !in 0 until provider.canvasHeight) continue
                    if (luma(provider.getPixel(nx, ny)) > 200f) return nx to ny
                }
            }
        }
        return null
    }

    /**
     * Perkiraan kepercayaan 0..1 bahwa wilayah ini bubble tertutup.
     *
     * Bubble tertutup dikelilingi dinding di sebagian besar kelilingnya.
     * [wallHits] = sisi batas yang menyentuh dinding, [perimeter] =
     * perkiraan keliling dari kotak batas.
     */
    fun closedConfidence(wallHits: Int, perimeter: Int): Float {
        if (perimeter <= 0) return 0f
        return (wallHits.toFloat() / perimeter.toFloat()).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------
    // Verifikasi "ini bubble?" — ala TypeR `looksLikeBubble`
    // ------------------------------------------------------------------

    /** Luas maksimum wilayah yang masih dianggap bubble (15% halaman). */
    const val BUBBLE_MOST = 0.15

    /** Batas lebih ketat bila wilayahnya menyentuh tepi halaman (8%). */
    const val BUBBLE_AT_EDGE = 0.08

    /** Lubang tertutup lebih besar dari ini → bukan bubble, tapi panel. */
    const val BUBBLE_HOLE = 0.004

    /** Patch putih tertutup lebih besar dari ini → bukan bubble (wajah/panel). */
    const val BUBBLE_INNER_WHITE = 0.0015

    /** Ambang luminansi untuk dianggap "putih" (ala TypeR: >= 200). */
    private const val WHITE_LEVEL = 200

    /**
     * True bila wilayah hasil wand **memang bubble** — bukan panel, bukan
     * latar, bukan wajah.
     *
     * Mengikuti TypeR (`photoshop.js` → `looksLikeBubble`):
     *
     *  1. Luas wilayah tak boleh melebihi 15% halaman; kalau menyentuh tepi
     *     halaman, batasnya lebih ketat (8%).
     *  2. **Lubang tertutup** yang besar di dalam wilayah means "panel"
     *     (gambar di dalam panel), bukan bubble.
     *  3. **Patch putih tertutup** yang besar di dalam wilayah means wajah
     *     atau area gambar — bukan gelembung.
     *
     * dipakai Bubble Eraser supaya tak menghapus teks di area yang bukan
     * gelembung, dan sebagai pemeriksaan "pemisahan bubble".
     *
     * @param mask peta seleksi (1 = terpilih).
     * @param left koordinat kanvas X kotak mask.
     * @param top koordinat kanvas Y kotak mask.
     * @param canvasWidth lebar halaman.
     * @param canvasHeight tinggi halaman.
     * @param lumaAt luminance piksel kanvas pada (x, y) absolut.
     */
    fun looksLikeBubble(
        mask: SelectionTileMap,
        left: Int,
        top: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        lumaAt: (Int, Int) -> Float
    ): Boolean {
        val bounds = mask.getBounds() ?: return true
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return true
        val page = canvasWidth.toLong() * canvasHeight.toLong()
        if (page <= 0) return true

        // 1) Luas.
        var area = 0L
        mask.forEachTile { _, _, tile ->
            for (v in tile.mask) {
                if ((v.toInt() and 0xFF) > 0) area++
            }
        }
        if (area <= 0) return true
        if (area > page * BUBBLE_MOST) return false
        val atEdge = left <= 0 || top <= 0 ||
            left + w >= canvasWidth || top + h >= canvasHeight
        if (atEdge && area > page * BUBBLE_AT_EDGE) return false

        // Peta lokal (kotak mask) supaya label patch murah.
        val inside = BooleanArray(w * h)
        val white = BooleanArray(w * h)
        mask.forEachTile { tx, ty, tile ->
            val ox = tx * mask.tileSize - left
            val oy = ty * mask.tileSize - top
            for (ty2 in 0 until tile.height) {
                val cy = oy + ty2
                if (cy !in 0 until h) continue
                val row = ty2 * tile.width
                for (tx2 in 0 until tile.width) {
                    if ((tile.mask[row + tx2].toInt() and 0xFF) == 0) continue
                    val cx = ox + tx2
                    if (cx !in 0 until w) continue
                    inside[cy * w + cx] = true
                }
            }
        }
        // Patch putih = piksel DI LUAR seleksi yang terang.
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (inside[y * w + x]) continue
                if (lumaAt(left + x, top + y) >= WHITE_LEVEL) white[y * w + x] = true
            }
        }

        // Stack label dipakai ulang antar patch (satu alokasi saja).
        val stack = IntArray(w * h)

        // 2) Lubang tertutup yang besar → panel, bukan bubble.
        var seen = BooleanArray(w * h)
        var ok = true
        for (i in 0 until w * h) {
            if (inside[i] || seen[i]) continue
            val patch = labelPatch(i, w, h, seen, stack) { !inside[it] }
            if (!patch.open && patch.size > page * BUBBLE_HOLE) { ok = false; break }
        }
        if (!ok) return false

        // 3) Patch putih tertutup yang besar → wajah / gambar, bukan bubble.
        seen = BooleanArray(w * h)
        for (i in 0 until w * h) {
            if (!white[i] || seen[i]) continue
            val patch = labelPatch(i, w, h, seen, stack) { white[it] }
            if (!patch.open && patch.size > page * BUBBLE_INNER_WHITE) return false
        }
        return true
    }

    /** Hasil label satu patch terhubung. */
    private data class Patch(val size: Long, val open: Boolean)

    /**
     * Label satu patch terhubung (4-arah) di kotak w*h.
     *
     * @param within syarat piksel masih bagian patch ini.
     * @param seen penanda piksel yang sudah dilabeli.
     */
    private fun labelPatch(
        start: Int,
        w: Int,
        h: Int,
        seen: BooleanArray,
        stack: IntArray,
        within: (Int) -> Boolean
    ): Patch {
        var sp = 0
        stack[sp++] = start
        seen[start] = true
        var size = 0L
        var open = false
        while (sp > 0) {
            val i = stack[--sp]
            size++
            val x = i % w
            val y = i / w
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) open = true
            if (x > 0) {
                val j = i - 1
                if (!seen[j] && within(j)) { seen[j] = true; stack[sp++] = j }
            }
            if (x < w - 1) {
                val j = i + 1
                if (!seen[j] && within(j)) { seen[j] = true; stack[sp++] = j }
            }
            if (y > 0) {
                val j = i - w
                if (!seen[j] && within(j)) { seen[j] = true; stack[sp++] = j }
            }
            if (y < h - 1) {
                val j = i + w
                if (!seen[j] && within(j)) { seen[j] = true; stack[sp++] = j }
            }
        }
        return Patch(size, open)
    }
}
