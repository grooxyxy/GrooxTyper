package com.grooxtyper.app.model

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/**
 * Pipeline "area bubble": satu ketukan wand di dalam gelembung menghasilkan SATU
 * area yang terlihat, dan gelembung yang bersinggungan otomatis terpisah.
 *
 * Alurnya (setiap langkah punya alasan, bukan tebakan):
 *
 *  1. **Flood dari seed** memakai [WandEngine.flood] (span scanline, metrik
 *     Chebyshev di ruang linear-light, coverage anti-alias).
 *  2. **Tolak kalau bocor ke tepi.** Bila komponen seed menyentuh batas kanvas,
 *     berarti yang terpilih kertas/latar, bukan gelembung. Ini yang menjawab
 *     keluhan "area melebar ke seluruh kertas". Exception: mode "Area Panel".
 *  3. **Batasi ke komponen seed.** Watershed hanya boleh memecah wilayah yang
 *     sedang dikerjakan; kalau tidak, kemampuannya ikut memecah seluruh halaman.
 *  4. **Distance transform + puncak.** Ambil piksel dengan jarak >= 0.7 * max
 *     (resep resmi OpenCV watershed). Tiap gelembung punya satu puncak; bagian
 *     yang saling sentuhan (pinggang di antara dua lingkaran) jaraknya lebih
 *     dangkal, jadi tidak masuk puncak.
 *  5. **Tumbuhkan geodesik.** Semua inti tumbuh serentak DI DALAM mask asli,
 *     jadi area akhir tidak pernah keluar lintas outline walau toleransi awal
 *     longgar. Garis tempat dua front bertemu adalah titik terakhir kedua
 *     gelembung bersentuhan - persis yang terjadi pada dua gelembung yang
 *     saling tumpang tindih.
 *  6. **Kontur** diambil pada ambang 50% supaya tepi jatuh di tepi asli.
 *
 * Mode "Area Panel" ([areaAt] dengan `allowBorder = true`) sengaja TIDAK
 * memecah: aksi itu untuk satu kotak narasi, bukan untuk memotong halaman jadi
 * beberapa bagian.
 *
 * Implementasi ini adalah cermin dari `scripts/wand-check.mjs`, yaitu uji
 * numerik dengan geometri ground truth yang menguji algoritmenya (satu
 * gelembung, dua gelembung bersinggungan, ketuk di luar, gelembung kecil,
 * area panel, halaman kosong).
 */
object BubbleAreaPipeline {

    /** Puncak watershed: fraksi jarak maksimum. Resep OpenCV memakai 0.7. */
    const val PEAK_RATIO = 0.7f

    /** Batas inti minimum: pecahan dari luas wilayah (mencegah noise jadi inti). */
    const val MIN_CORE_DIVISOR = 2000

    /** Luasan minimal satu piksel, supaya area jadi tak nol. */
    const val MIN_AREA_PIXELS = 20

    /** kind: 0 = area gelembung, 1 = area panel. */
    const val KIND_BUBBLE = 0
    const val KIND_PANEL = 1

    class Area(
        val bounds: RectF,
        val path: Path,
        /** Warna teks kontras dengan isi area (0xFF000000 atau 0xFFFFFFFF). */
        val textColor: Int,
        val kind: Int
    )

    /**
     * Area dari satu ketukan.
     *
     * @param outScale pengali koordinat (1f = ukuran kanvas penuh; pakai
     *   1/factor bila piksel berasal dari [com.grooxtyper.app.model.SelectionEngine.downsampleForWand]).
     * @return null bila bukan area gelembung (dan mode panel tidak aktif).
     */
    fun areaAt(
        px: IntArray,
        w: Int,
        h: Int,
        seedX: Int,
        seedY: Int,
        params: WandEngine.Params,
        outScale: Float = 1f,
        offX: Float = 0f,
        offY: Float = 0f,
        allowBorder: Boolean = false
    ): Area? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        if (seedX !in 0 until w || seedY !in 0 until h) return null
        val mask = WandEngine.flood(px, w, h, seedX, seedY, params) ?: return null
        if (mask.pixels < MIN_AREA_PIXELS) return null
        val bin = mask.toBinary()

        // 2) Komponen seed + deteksi tepi.
        val labels = IntArray(w * h)
        val seedLabel = labelComponent(bin, labels, w, h, seedY * w + seedX) ?: return null
        if (!allowBorder && touchesBorder(labels, w, h, seedLabel)) return null

        // 3) Batasi ke komponen seed.
        var count = 0
        for (i in bin.indices) {
            if (labels[i] == seedLabel) count++ else bin[i] = false
        }
        if (count < MIN_AREA_PIXELS) return null

        // 4) Distance transform + puncak.
        val dist = WandEngine.distanceTransform(bin, w, h)
        var maxD = 0f
        var argMax = -1
        for (i in bin.indices) {
            if (!bin[i]) continue
            if (dist[i] > maxD) { maxD = dist[i]; argMax = i }
        }
        if (maxD < 2f || argMax < 0) return null
        val thrPeak = max(1f, maxD * PEAK_RATIO)
        val peak = BooleanArray(w * h)
        for (i in bin.indices) if (bin[i] && dist[i] >= thrPeak) peak[i] = true
        val minCore = max(2, count / MIN_CORE_DIVISOR)
        val cores: IntArray
        val coreCount: Int
        if (allowBorder) {
            // Satu wilayah saja untuk mode panel.
            val one = BooleanArray(w * h)
            one[argMax] = true
            val lab = IntArray(w * h)
            val c = labelComponent(one, lab, w, h, argMax) ?: return null
            cores = lab
            coreCount = c
        } else {
            val lab = IntArray(w * h)
            var n = WandEngine.labelComponents(peak, w, h, minCore)
            if (n.second == 0) {
                // Gelembung kecil: intinya di bawah ambang luas, tapi puncak
                // jarak maksimum adalah inti yang sah.
                val tiny = BooleanArray(w * h)
                tiny[argMax] = true
                lab.fill(0)
                n = WandEngine.labelComponents(tiny, w, h, 1)
            }
            cores = lab
            coreCount = n.second
        }
        if (coreCount < 1) return null

        // 5) Tumbuhkan geodesik di dalam mask asli.
        val owner = IntArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (i in cores.indices) {
            if (cores[i] != 0) {
                owner[i] = cores[i]
                queue[tail++] = i
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            val me = owner[i]
            if (x > 0 && owner[i - 1] == 0 && bin[i - 1]) { owner[i - 1] = me; queue[tail++] = i - 1 }
            if (x < w - 1 && owner[i + 1] == 0 && bin[i + 1]) { owner[i + 1] = me; queue[tail++] = i + 1 }
            if (y > 0 && owner[i - w] == 0 && bin[i - w]) { owner[i - w] = me; queue[tail++] = i - w }
            if (y < h - 1 && owner[i + w] == 0 && bin[i + w]) { owner[i + w] = me; queue[tail++] = i + w }
        }

        // 6) Kumpulkan tiap wilayah jadi satu area.
        val groups = HashMap<Int, IntArray>()
        val sizes = HashMap<Int, Int>()
        for (i in owner.indices) {
            val id = owner[i]
            if (id == 0) continue
            val x = i % w
            val y = i / w
            val b = groups.getOrPut(id) { intArrayOf(w, h, -1, -1) }
            if (x < b[0]) b[0] = x
            if (y < b[1]) b[1] = y
            if (x > b[2]) b[2] = x
            if (y > b[3]) b[3] = y
            sizes[id] = (sizes[id] ?: 0) + 1
        }
        val minArea = if (allowBorder) 4 else minCore
        val boxes = ArrayList<RectF>(groups.size)
        val masks = ArrayList<BooleanArray>(groups.size)
        for (id in groups.keys) {
            if ((sizes[id] ?: 0) < minArea) continue
            val b = groups.getValue(id)
            val m = BooleanArray(w * h)
            for (i in owner.indices) if (owner[i] == id) m[i] = true
            boxes.add(
                RectF(
                    offX + b[0] * outScale,
                    offY + b[1] * outScale,
                    offX + (b[2] + 1) * outScale,
                    offY + (b[3] + 1) * outScale
                )
            )
            masks.add(m)
        }
        if (boxes.isEmpty()) return null
        val order = boxes.indices.sortedByDescending { boxes[it].width() * boxes[it].height() }
        val first = order.first()
        return Area(
            boxes[first],
            traceContour(masks[first], w, h, outScale, offX, offY)
                ?: Path().apply { addRect(boxes[first], Path.Direction.CW) },
            textColorFor(px, masks[first], w, h),
            if (allowBorder) KIND_PANEL else KIND_BUBBLE
        )
    }

    /**
     * Beberapa area dari beberapa seed sekaligus (dipakai untuk menguji
     * banyak gelembung dalam satu gambar). Area yang tumpang tindih dilewati.
     */
    fun areasFrom(
        px: IntArray,
        w: Int,
        h: Int,
        seeds: List<Int>,
        params: WandEngine.Params,
        outScale: Float = 1f,
        offX: Float = 0f,
        offY: Float = 0f,
        allowBorder: Boolean = false
    ): List<Area> {
        val out = ArrayList<Area>(seeds.size)
        for (seed in seeds) {
            val sx = seed % w
            val sy = seed / w
            val a = areaAt(px, w, h, sx, sy, params, outScale, offX, offY, allowBorder) ?: continue
            if (out.any { RectF.intersects(it.bounds, a.bounds) }) continue
            out.add(a)
        }
        return out
    }

    /** Label komponen yang memuat [start]; null bila piksel itu di luar mask. */
    private fun labelComponent(
        bin: BooleanArray,
        labels: IntArray,
        w: Int,
        h: Int,
        start: Int
    ): Int? {
        if (!bin[start]) return null
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        queue[tail++] = start
        labels[start] = 1
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) { val j = i - 1; if (bin[j] && labels[j] == 0) { labels[j] = 1; queue[tail++] = j } }
            if (x < w - 1) { val j = i + 1; if (bin[j] && labels[j] == 0) { labels[j] = 1; queue[tail++] = j } }
            if (y > 0) { val j = i - w; if (bin[j] && labels[j] == 0) { labels[j] = 1; queue[tail++] = j } }
            if (y < h - 1) { val j = i + w; if (bin[j] && labels[j] == 0) { labels[j] = 1; queue[tail++] = j } }
        }
        return 1
    }

    private fun touchesBorder(labels: IntArray, w: Int, h: Int, id: Int): Boolean {
        for (i in labels.indices) {
            if (labels[i] != id) continue
            val x = i % w
            val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return true
        }
        return false
    }

    /**
     * Kontur wilayah pada ambang 50%: Moore boundary tracing 8 arah. Ambang 50%
     * membuat tepi jatuh di tepi asli objek, bukan di blok piksel.
     */
    private fun traceContour(
        m: BooleanArray,
        w: Int,
        h: Int,
        outScale: Float,
        offX: Float,
        offY: Float
    ): Path? {
        fun solid(x: Int, y: Int): Boolean =
            x >= 0 && y >= 0 && x < w && y < h && m[y * w + x]
        var sx = -1
        var sy = -1
        outer@ for (y in 0 until h) {
            for (x in 0 until w) {
                if (m[y * w + x] && !solid(x - 1, y)) { sx = x; sy = y; break@outer }
            }
        }
        if (sx < 0) return null
        val path = Path()
        val dxs = intArrayOf(0, 1, 0, -1, -1, -1, 0, 1)
        val dys = intArrayOf(-1, 0, 1, 1, 0, -1, -1, 0)
        var cx = sx
        var cy = sy
        var dir = 0
        path.moveTo(offX + cx * outScale, offY + cy * outScale)
        var steps = 0
        val maxSteps = w * h + 8
        while (steps++ < maxSteps) {
            var moved = false
            for (k in 0 until 8) {
                val nd = (dir + k) and 7
                val nx = cx + dxs[nd]
                val ny = cy + dys[nd]
                if (solid(nx, ny) != solid(cx, cy)) {
                    cx = nx
                    cy = ny
                    dir = nd
                    path.lineTo(offX + cx * outScale, offY + cy * outScale)
                    moved = true
                    break
                }
            }
            if (!moved) break
            if (cx == sx && cy == sy) break
        }
        path.close()
        return path
    }

    /**
     * Warna teks kontras dengan isi area: luminance rata-rata di atas 128 berarti
     * area terang, jadi teks gelap (dan sebaliknya).
     */
    private fun textColorFor(px: IntArray, m: BooleanArray, w: Int, h: Int): Int {
        var sum = 0L
        var n = 0L
        for (i in m.indices) {
            if (!m[i]) continue
            val p = px[i]
            val lum = 0.299f * ((p shr 16) and 0xFF) +
                0.587f * ((p shr 8) and 0xFF) +
                0.114f * (p and 0xFF)
            sum += lum.toLong()
            n++
        }
        if (n == 0L) return 0xFF000000.toInt()
        return if (sum / n > 128f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** Luas area dalam piksel (untuk panel dan diagnostik). */
    fun areaPixels(m: BooleanArray): Int {
        var n = 0
        for (b in m) if (b) n++
        return n
    }

}
