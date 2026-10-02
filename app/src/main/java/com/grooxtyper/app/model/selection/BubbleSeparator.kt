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
}
