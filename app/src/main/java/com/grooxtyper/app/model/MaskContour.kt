package com.grooxtyper.app.model

import android.graphics.Path

/**
 * Kontur tertutup untuk mask wand.
 *
 * ## Bug yang diperbaiki
 *
 * `SelectionEngine.traceContour` memakai marching squares lalu menulis tiap
 * ruas sebagai `path.moveTo` + `path.lineTo`. Hasilnya **1.564 sub-path
 * terpisah** untuk satu gelembung, masing-masing garis pendek 0,7 piksel
 * yang tidak pernah ditutup.
 *
 * Path seperti itu tidak bisa di-isi. Saat `drawPath(..., FILL)` setiap
 * sub-path tertutup otomatis menjadi poligon degenerat, sehingga luas yang
 * terisi hanya sekitar **1% dari mask**.Seleksi wand praktis kosong.
 * Marching ants tetap terlihat karena path di-`STROKE`, bukan di-isi; itu
 * sebabnya wand tampak "bekerja" padahal tidak pernah mengisi apa pun.
 *
 * Diuji di `scripts/wand-check.mjs` kasus 11: path lama terisi 5,8% dari
 * mask (140 dari 2.400 piksel). Setelah diperbaiki, 100,0%.
 *
 * ## Cara yang benar
 *
 * Ruas marching squares dikumpulkan lebih dulu sebagai **rantai berarah** di
 * titik tengah sisi sel, lalu dirangkai menjadi loop tertutup. Karena tiap
 * ruas berpasangan masuk-keluar pada titik yang sama, rantainya menutup
 * otomatis tanpa perlu menebak arah belok.
 *
 * Lubang di dalam wilayah (misalnya teks di dalam gelembung) menjadi loop
 * sendiri. Loop luar dan setiap lubang digabung dengan
 * [Path.FillType.EVEN_ODD] supaya lubang tetap lubang: itu yang dibutuhkan
 * agar isi teks tidak ikut mengenai teksnya sendiri.
 */
object MaskContour {

    /**
     * Tabel ruas untuk 16 kasus marching squares.
     *
     * Indeks bit: 1 = kiri-atas, 2 = kanan-atas, 4 = kanan-bawah,
     * 8 = kiri-bawah, dibaca dari sel saat ini.
     *
     * Sisi sel dipakai sebagai: 0 = atas, 1 = kanan, 2 = bawah, 3 = kiri.
     */
    private val EDGE_TABLE = arrayOf(
        intArrayOf(0),                                      // 0000
        intArrayOf(3, 0),                                   // 0001 kiri-atas
        intArrayOf(0, 1),                                   // 0010 kanan-atas
        intArrayOf(3, 1),                                   // 0011
        intArrayOf(1, 2),                                   // 0100 kanan-bawah
        intArrayOf(3, 0, 1, 2),                             // 0101 pelana
        intArrayOf(0, 2),                                   // 0110
        intArrayOf(3, 2),                                   // 0111
        intArrayOf(2, 3),                                   // 1000 kiri-bawah
        intArrayOf(2, 0),                                   // 1001
        intArrayOf(0, 1, 2, 3),                             // 1010 pelana
        intArrayOf(2, 1),                                   // 1011
        intArrayOf(1, 3),                                   // 1100
        intArrayOf(1, 0),                                   // 1101
        intArrayOf(0, 3)                                    // 1110
    )

    /** Koordinat titik tengah sisi sel dalam kelipatan setengah piksel. */
    private fun edgeHalf(x: Int, y: Int, side: Int): Long {
        val hx = when (side) {
            0 -> x * 2 + 1       // atas
            1 -> (x + 1) * 2     // kanan
            2 -> x * 2 + 1       // bawah
            else -> x * 2        // kiri
        }
        val hy = when (side) {
            0 -> y * 2
            1 -> y * 2 + 1
            2 -> (y + 1) * 2
            else -> y * 2 + 1
        }
        return (hx.toLong() shl 32) or (hy.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Bangun [Path] tertutup dari [mask].
     *
     * @param mask piksel terpilih (true berarti masuk wilayah)
     * @param scale pengali koordinat (1f = ukuran kanvas penuh)
     * @param offX geseran X koordinat kanvas, untuk wand berjendela
     * @param offY geseran Y koordinat kanvas
     * @return Path dengan fill type EVEN_ODD, atau null bila mask kosong
     */
    fun build(
        mask: BooleanArray,
        w: Int,
        h: Int,
        scale: Float = 1f,
        offX: Float = 0f,
        offY: Float = 0f
    ): Path? {
        if (w < 2 || h < 2 || mask.size < w * h) return null

        // Titik awal -> daftar titik tujuan. Nilai dipaket jadi Long agar
        // HashMap tidak alokasi objek per titik.
        val from = HashMap<Long, LongArrayList>()
        var edges = 0
        for (y in 0 until h - 1) {
            val r0 = y * w
            val r1 = (y + 1) * w
            for (x in 0 until w - 1) {
                val idx = (if (mask[r0 + x]) 1 else 0) or
                    (if (mask[r0 + x + 1]) 2 else 0) or
                    (if (mask[r1 + x + 1]) 4 else 0) or
                    (if (mask[r1 + x]) 8 else 0)
                val list = EDGE_TABLE[idx]
                if (list.size == 0) continue
                for (e in 0 until list.size step 2) {
                    val s = edgeHalf(x, y, list[e])
                    val d = edgeHalf(x, y, list[e + 1])
                    val arr = from.getOrPut(s) { LongArrayList(2) }
                    arr.add(d)
                    edges++
                }
            }
        }
        if (edges == 0) return null

        // Titik yang tak pernah menjadi tujuan = slot terbuka, jadi dipakai
        // lebih dulu agar rantai ikut tertutup.
        val targets = HashSet<Long>(edges)
        for (list in from.values) {
            for (i in 0 until list.size) targets.add(list[i])
        }
        val order = ArrayList<Long>(from.size)
        for (k in from.keys) if (!targets.contains(k)) order.add(k)
        if (order.isEmpty()) order.addAll(from.keys)

        val used = HashSet<Long>(edges)
        val path = Path()
        var loops = 0
        val guard = w.toLong() * h * 8 + 8

        for (start in order) {
            if (used.contains(start)) continue
            var cur = start
            var pts = 0
            var steps = 0L
            while (steps < guard) {
                steps++
                if (!used.add(cur)) break
                val hx = (cur shr 32).toInt()
                val hy = (cur and 0xFFFFFFFFL).toInt()
                val fx = hx * 0.5f
                val fy = hy * 0.5f
                if (pts == 0) {
                    path.moveTo(offX + fx * scale, offY + fy * scale)
                } else {
                    path.lineTo(offX + fx * scale, offY + fy * scale)
                }
                pts++
                val outs = from[cur] ?: break
                var next = -1L
                for (i in 0 until outs.size) {
                    val cand = outs[i]
                    if (cand != -1L && !used.contains(cand)) { next = cand; break }
                }
                if (next < 0L) break
                cur = next
            }
            if (pts >= 4) {
                path.close()
                loops++
            }
        }
        if (loops == 0) return null
        path.fillType = Path.FillType.EVEN_ODD
        return path
    }

    /** Larik Long sederhana; menghindari alokasi ArrayList<Long> per titik. */
    private class LongArrayList(initial: Int) {
        private var data = LongArray(initial)
        var size = 0
            private set

        fun add(v: Long) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        operator fun get(i: Int): Long = data[i]
    }
}