package com.grooxtyper.app.model

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Mesin tongkat sihir (magic wand) yang mengikuti implementasi nyata, bukan
 * tebakan. Semua keputusan di bawah berasal dari membaca kode sumber:
 *
 *  - **Span scanline flood fill** dengan stack `IntArray` (GIMP
 *    `app/core/gimppickable-contiguous-region.cc`): entri stack adalah satu
 *    RENTANG piksel, bukan satu piksel. Versi lama memakai `ArrayDeque<Int>`
 *    per piksel sehingga boxed Integer dan GC di kanvas besar.
 *  - **Metrik Chebyshev di ruang linear-light** (bukan Euclidean RGB):
 *    `pixel_difference` GIMP memakai max selisih absolut per kanal pada
 *    komposit linear (GIMP_PRECISION_FLOAT_GAMMA). Menghitung
 *    `sqrt(dr^2+dg^2+db^2)` di ruang sRGB memberi batas berbeda.
 *  - **Ramp anti-alias GIMP**: `aa = 1.5 - d/threshold`; hasilnya 0 bila
 *    `aa <= 0`, `aa*2` bila `aa < 0.5`, selain itu 1. Mask ditulis sebagai
 *    coverage 0..255 (bukan boolean), jadi tepi berantian ikut jadi setengah
 *    transparan dan "white fringing" hilang.
 *  - **Fixed-range terhadap warna seed**, bukan floating-range terhadap
 *    tetangga (setara `FLOODFILL_FIXED_RANGE` di OpenCV).
 *  - **Konektivitas 4 arah default**, 8 arah sebagai opsi: GIMP hanya
 *    mendorong baris `y+1` dan `y-1`, dan opsi diagonalnya MEMPERLEBAR span
 *    satu piksel di kedua ujung (bukan membaca piksel diagonal).
 *  - **Feather sebagai operasi terpisah** setelah fill (GIMP menjalankan
 *    `gimp_channel_feather` setelah fill, dan default-nya nonaktif).
 *  - **Pemisahan bubble yang menempel**: distance transform (chamfer 3x3,
 *    diagonal 1.4142) -> puncak = jarak >= 0.7 * max -> watershed multi-sumber.
 *    Resep ini dari tutorial resmi OpenCV watershed. Versi lama mencari
 *    radius erosi lewat pencarian biner dan memaksa tepat 2 hasil.
 *
 * Tidak memakai model segmentasi umum (SAM): untuk kasus bubble, pemisahan
 * cukup berbasis warna dan bentuk, sehingga exact dan berjalan cepat on-device.
 */
object WandEngine {

    /** Tabel linear-light sRGB (inverse EOTF), dihitung sekali. */
    private val LIN = FloatArray(256).also { lut ->
        for (i in 0..255) {
            val c = i / 255.0
            lut[i] = if (c <= 0.04045) (c / 12.92).toFloat()
            else Math.pow((c + 0.055) / 1.055, 2.4).toFloat()
        }
    }

    private const val SQRT2 = 1.41421356f
    private const val BIG = 1e9f

    private fun absF(v: Float): Float = if (v < 0f) -v else v

    /**
     * Ubah ambang "jarak RGB" yang dipakai penggeser UI menjadi ambang di
     * metrik linear-light GIMP.
     *
     * Menyamakan ambang secara naif (ambang x yang sama) akan mengubah rasa
     * tongkat sihir: jarak 32/255 di ruang sRGB berarti jarak linear ~0.26
     * pada warna terang, sehingga ambang yang sama jauh lebih longgar.
     * Jadi ambang dihitung dari nilai seed: ambil kanal seed, turunkan
     * sebesar [maxDist], lalu ukur jarak linear-lightnya.
     */
    fun thresholdForSrgb(maxDist: Float, seedArgb: Int): Int {
        val r = (seedArgb shr 16) and 0xFF
        val g = (seedArgb shr 8) and 0xFF
        val b = seedArgb and 0xFF
        val v = maxOf(r, g, b)
        val lo = (v - maxDist).coerceIn(0f, 255f)
        val d = absF(LIN[v] - LIN[lo.toInt()])
        return (d * 255f).toInt().coerceIn(1, 255)
    }

    /** Setelan tongkat sihir. Ambang default 15/255 mengikuti GIMP. */
    data class Params(
        val threshold: Int = 15,
        val antialias: Boolean = true,
        /** true = hanya piksel terhubung ke seed (Grow Contiguous). */
        val contiguous: Boolean = true,
        val diagonal: Boolean = false,
        /** Radius feather; 0 = mati (default GIMP juga mati). */
        val feather: Float = 0f,
        /** Batas piksel agar tap di latar raksasa tak menggantung UI. */
        val maxPixels: Long = 12_000_000L
    )

    /**
     * Hasil fill: coverage 0..255 per piksel (0 = di luar) plus jumlah piksel.
     * Byte bisa negatif, jadi selalu baca dengan `and 0xFF`.
     */
    class Mask(val w: Int, val h: Int) {
        val cov = ByteArray(w * h)
        var pixels: Int = 0
            internal set

        fun coverage(x: Int, y: Int): Int = cov[y * w + x].toInt() and 0xFF

        /** Piksel dianggap "dalam" pada ambang 50% - dipakai untuk kontur. */
        fun solid(x: Int, y: Int): Boolean = coverage(x, y) >= 128

        /**
         * Binarisasi pada ambang 50%. Inilah yang membuat tepi bersih: piksel
         * tepi ber-coverage parsial diperlakukan sebagai luar, jadi batas
         * seleksi jatuh di tepi asli objek, bukan di blok piksel - tepi
         * bergerigi hilang.
         */
        fun toBinary(threshold: Int = 128): BooleanArray {
            val out = BooleanArray(w * h)
            for (i in out.indices) out[i] = (cov[i].toInt() and 0xFF) >= threshold
            return out
        }

        fun solidCount(): Int {
            var n = 0
            for (v in cov) if ((v.toInt() and 0xFF) >= 128) n++
            return n
        }

        internal fun mark(i: Int, c: Float): Boolean {
            if (cov[i].toInt() != 0) return false
            cov[i] = (c * 255f + 0.5f).toInt().coerceIn(1, 255).toByte()
            pixels++
            return true
        }
    }

    // ------------------------------------------------------------------
    // 1) Selisih warna + ramp anti-alias (replika pixel_difference GIMP)
    // ------------------------------------------------------------------

    /**
     * Coverage piksel [idx] terhadap warna seed: 0 = di luar, 1 = sepenuhnya
     * di dalam, nilai tengah = tepi anti-alias.
     */
    private fun coverageOf(
        px: IntArray, idx: Int,
        seedR: Int, seedG: Int, seedB: Int,
        thr: Float, antialias: Boolean
    ): Float {
        val p = px[idx]
        // Piksel transparan tak pernah terpilih (GIMP: select_transparent).
        if ((p ushr 24) == 0) return 0f
        var d = LIN[(p shr 16) and 0xFF] - LIN[seedR]
        if (d < 0f) d = -d
        val e = LIN[(p shr 8) and 0xFF] - LIN[seedG]
        if (e < 0f) {
            if (-e > d) d = -e
        } else if (e > d) d = e
        val f = LIN[p and 0xFF] - LIN[seedB]
        if (f < 0f) {
            if (-f > d) d = -f
        } else if (f > d) d = f
        // d = jarak Chebyshev di ruang linear-light.
        if (!antialias) return if (d <= thr) 1f else 0f
        if (thr <= 0f) return if (d <= 0f) 1f else 0f
        val aa = 1.5f - (d / thr)
        return when {
            aa <= 0f -> 0f
            aa < 0.5f -> aa * 2f
            else -> 1f
        }
    }

    // ------------------------------------------------------------------
    // 2) Span scanline flood fill (IntArray, tanpa alokasi di dalam loop)
    // ------------------------------------------------------------------

    fun flood(px: IntArray, w: Int, h: Int, seedX: Int, seedY: Int, p: Params): Mask? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        if (seedX !in 0 until w || seedY !in 0 until h) return null
        val seed = px[seedY * w + seedX]
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr = p.threshold.coerceIn(0, 255) / 255f
        val mask = Mask(w, h)
        if (coverageOf(px, seedY * w + seedX, sr, sg, sb, thr, p.antialias) <= 0f) return null

        if (!p.contiguous) {
            // "Sample merged" GIMP: semua piksel dengan warna serupa, tanpa
            // syarat terhubung. Sekali jalan, tanpa stack sama sekali.
            for (i in 0 until w * h) {
                val c = coverageOf(px, i, sr, sg, sb, thr, p.antialias)
                if (c > 0f) mask.mark(i, c)
            }
            return mask
        }

        // Stack span: 3 int per entri (y, dari, sampaiExclusive).
        var stack = IntArray(3 * 4096)
        var sp = 0
        fun push(y: Int, from: Int, to: Int) {
            if (to <= from) return
            if (sp + 3 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[sp++] = y
            stack[sp++] = from
            stack[sp++] = to
        }

        // Seed sengaja TIDAK ditandai di sini. Kalau ditandai dulu, loop
        // span melihatnya sudah terisi lalu melompatinya, dan tidak ada satu
        // piksel pun yang tumbuh: selectionsama sekali. Seed ditandai oleh
        // loop itu sendiri saat cakupannya dihitung.
        push(seedY, seedX, seedX + 1)
        var steps = 0L
        val guardMax = p.maxPixels * 2 + 4096
        while (sp > 0) {
            sp -= 3
            val y = stack[sp]
            val from = stack[sp + 1]
            val to = stack[sp + 2]
            val row = y * w
            var x = from
            while (x < to) {
                if (steps++ > guardMax) return mask
                if (mask.cov[row + x].toInt() != 0) { x++; continue }
                val c = coverageOf(px, row + x, sr, sg, sb, thr, p.antialias)
                if (c <= 0f) { x++; continue }
                mask.mark(row + x, c)
                // Lebar ke kiri satu per satu (mengikuti alur GIMP).
                var start = x
                while (start > 0 && mask.cov[row + start - 1].toInt() == 0) {
                    val cl = coverageOf(px, row + start - 1, sr, sg, sb, thr, p.antialias)
                    if (cl <= 0f) break
                    mask.mark(row + start - 1, cl)
                    start--
                }
                // Lebar ke kanan.
                var end = x + 1
                var guard = w - x
                while (end < w && guard-- > 0 && mask.cov[row + end].toInt() == 0) {
                    val cr = coverageOf(px, row + end, sr, sg, sb, thr, p.antialias)
                    if (cr <= 0f) break
                    mask.mark(row + end, cr)
                    end++
                }
                x = end
                // Opsi diagonal GIMP = LEBARKAN span satu piksel di kedua
                // ujung (bukan membaca piksel diagonal).
                val pushFrom = if (p.diagonal && start > 0) start - 1 else start
                val pushTo = if (p.diagonal && end < w) end + 1 else end
                if (y + 1 < h) push(y + 1, pushFrom, pushTo)
                if (y - 1 >= 0) push(y - 1, pushFrom, pushTo)
            }
        }
        if (p.feather > 0.5f) feather(mask, p.feather)
        return mask
    }

    /**
     * Feather: blur kotak dua arah pada coverage. Terpisah dari anti-alias
     * (GIMP: `gimp_channel_feather` dijalankan setelah fill, default mati).
     */
    fun feather(mask: Mask, radius: Float) {
        val r = radius.toInt().coerceIn(1, 64)
        val w = mask.w
        val h = mask.h
        val src = IntArray(w * h) { mask.cov[it].toInt() and 0xFF }
        val tmp = IntArray(w * h)
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            val a = max(0, -r)
            val b = min(w - 1, r)
            for (x in 0 until w) {
                var sum = 0
                var n = 0
                for (k in a..b) { sum += src[row + (x + k).coerceIn(0, w - 1)]; n++ }
                tmp[row + x] = sum / n
            }
        }
        for (x in 0 until w) {
            val a = max(0, -r)
            val b = min(h - 1, r)
            for (y in 0 until h) {
                var sum = 0
                var n = 0
                for (k in a..b) { sum += tmp[(y + k).coerceIn(0, h - 1) * w + x]; n++ }
                dst[y * w + x] = sum / n
            }
        }
        for (i in dst.indices) mask.cov[i] = dst[i].toByte()
    }

    /** Bungkus mask biner (mis. interior kertas gelembung) jadi [Mask]. */
    fun maskFromBinary(bin: BooleanArray, w: Int, h: Int): Mask {
        val m = Mask(w, h)
        for (i in bin.indices) {
            if (bin[i]) {
                m.cov[i] = 0xFF.toByte()
                m.pixels++
            }
        }
        return m
    }

    // ------------------------------------------------------------------
    // 3) Distance transform chamfer 3x3 (meniru DIST_L2 maskSize 5)
    // ------------------------------------------------------------------

    fun distanceTransform(bin: BooleanArray, w: Int, h: Int): FloatArray {
        val n = w * h
        val d = FloatArray(n) { if (bin[it]) BIG else 0f }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if (!bin[i]) continue
                var m = d[i]
                if (y > 0) {
                    m = min(m, d[i - w] + 1f)
                    if (x > 0) m = min(m, d[i - w - 1] + SQRT2)
                    if (x < w - 1) m = min(m, d[i - w + 1] + SQRT2)
                }
                if (x > 0) m = min(m, d[i - 1] + 1f)
                d[i] = m
            }
        }
        for (y in h - 1 downTo 0) {
            val row = y * w
            for (x in w - 1 downTo 0) {
                val i = row + x
                if (!bin[i]) continue
                var m = d[i]
                if (y < h - 1) {
                    m = min(m, d[i + w] + 1f)
                    if (x < w - 1) m = min(m, d[i + w + 1] + SQRT2)
                    if (x > 0) m = min(m, d[i + w - 1] + SQRT2)
                }
                if (x < w - 1) m = min(m, d[i + 1] + 1f)
                d[i] = m
            }
        }
        return d
    }

    // ------------------------------------------------------------------
    // 4) Labeling komponen 4-ara (BFS di IntArray, tanpa Queue<Int>)
    // ------------------------------------------------------------------

    /** Label 1..N untuk tiap piksel true; 0 = latar. */
    fun labelComponents(
        bin: BooleanArray, w: Int, h: Int, minPixels: Int = 4
    ): Pair<IntArray, Int> {
        val lab = IntArray(w * h)
        val queue = IntArray(w * h)
        var next = 0
        for (start in lab.indices) {
            if (!bin[start] || lab[start] != 0) continue
            val myLabel = next + 1
            var head = 0
            var tail = 0
            queue[tail++] = start
            lab[start] = myLabel
            var size = 0
            while (head < tail) {
                val i = queue[head++]
                size++
                val x = i % w
                val y = i / w
                if (x > 0) { val j = i - 1; if (bin[j] && lab[j] == 0) { lab[j] = myLabel; queue[tail++] = j } }
                if (x < w - 1) { val j = i + 1; if (bin[j] && lab[j] == 0) { lab[j] = myLabel; queue[tail++] = j } }
                if (y > 0) { val j = i - w; if (bin[j] && lab[j] == 0) { lab[j] = myLabel; queue[tail++] = j } }
                if (y < h - 1) { val j = i + w; if (bin[j] && lab[j] == 0) { lab[j] = myLabel; queue[tail++] = j } }
            }
            if (size < minPixels) {
                // Komponen kecil dibuang supaya tak jadi seed watershed.
                for (k in lab.indices) if (lab[k] == myLabel) lab[k] = 0
            } else {
                next = myLabel
            }
        }
        return lab to next
    }

    // ------------------------------------------------------------------
    // 5) Watershed: puncak distance transform -> multi-sumber BFS
    // ------------------------------------------------------------------

    /**
     * Pecah mask yang berisi gumpalan menyatu menjadi beberapa gelembung.
     *
     * Resep resmi OpenCV watershed:
     *  1. distance transform dari mask,
     *  2. ambang di ratio * max -> satu puncak per gelembung,
     *  3. label komponen puncak menjadi marker,
     *  4. tumbuhkan semua marker serentak; garis tempat dua front bertemu
     *     adalah ridge watershed, yaitu titik terakhir gelembung bersentuhan.
     */
    fun splitBubbles(
        mask: Mask,
        ratio: Float = 0.7f,
        minAreaRatio: Float = 0.04f
    ): List<RectF> {
        val w = mask.w
        val h = mask.h
        val bin = mask.toBinary()
        var any = 0
        for (b in bin) if (b) any++
        if (any < 160) return emptyList()
        val dist = distanceTransform(bin, w, h)
        var maxD = 0f
        for (i in bin.indices) if (bin[i] && dist[i] > maxD) maxD = dist[i]
        if (maxD < 2f) return emptyList()
        val thr = max(1f, maxD * ratio.coerceIn(0.2f, 0.95f))
        val peak = BooleanArray(w * h)
        for (i in bin.indices) if (bin[i] && dist[i] >= thr) peak[i] = true
        val minPixels = max(8, (any * minAreaRatio.coerceIn(0.005f, 0.5f)).toInt())
        val (marks, markCount) = labelComponents(peak, w, h, minPixels)
        if (markCount < 2) return emptyList()

        // Multi-sumber BFS: front yang sampai duluan memiliki piksel itu.
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
            if (x > 0) tail = claim(owner, mask, queue, i - 1, me, tail)
            if (x < w - 1) tail = claim(owner, mask, queue, i + 1, me, tail)
            if (y > 0) tail = claim(owner, mask, queue, i - w, me, tail)
            if (y < h - 1) tail = claim(owner, mask, queue, i + w, me, tail)
        }
        // Bounding box tiap wilayah, urut dari yang terbesar.
        val minX = HashMap<Int, Int>()
        val minY = HashMap<Int, Int>()
        val maxX = HashMap<Int, Int>()
        val maxY = HashMap<Int, Int>()
        val sizes = HashMap<Int, Int>()
        for (i in owner.indices) {
            val id = owner[i]
            if (id == 0) continue
            val x = i % w
            val y = i / w
            val a = minX[id]
            minX[id] = if (a == null || x < a) x else a
            val b = minY[id]
            minY[id] = if (b == null || y < b) y else b
            val c = maxX[id]
            maxX[id] = if (c == null || x > c) x else c
            val d = maxY[id]
            maxY[id] = if (d == null || y > d) y else d
            sizes[id] = (sizes[id] ?: 0) + 1
        }
        val out = ArrayList<RectF>(sizes.size)
        for (id in sizes.keys) {
            if ((sizes[id] ?: 0) < minPixels) continue
            val x1 = minX[id] ?: continue
            val y1 = minY[id] ?: continue
            val x2 = maxX[id] ?: continue
            val y2 = maxY[id] ?: continue
            if (x2 - x1 < 6 || y2 - y1 < 6) continue
            out.add(RectF(x1.toFloat(), y1.toFloat(), (x2 + 1).toFloat(), (y2 + 1).toFloat()))
        }
        out.sortByDescending { it.width() * it.height() }
        return out
    }

    /** Klaim piksel [j] untuk front [id] bila kosong dan di dalam mask. */
    private fun claim(
        owner: IntArray, mask: Mask, queue: IntArray, j: Int, id: Int, tailIn: Int
    ): Int {
        if (owner[j] != 0) return tailIn
        if ((mask.cov[j].toInt() and 0xFF) < 128) return tailIn
        owner[j] = id
        queue[tailIn] = j
        return tailIn + 1
    }
}
