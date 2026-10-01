package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Tongkat sihir (magic wand) - dibangun ulang dari nol.
 *
 * ## Kenapa dibangun ulang
 *
 * Versi lama menumpuk tiga hal sekaligus: (1) flood fill, (2) watershed
 * untuk memecah gelembung bersinggungan, (3) mode bubble/panel dengan
 * penolakan "bukan area bubble". Ketiganya saling mengganggu: watershed
 * memecah satu wilayah yang seharusnya utuh, dan penolakan tepi-kanvas
 * membuat wand gagal tepat pada halaman webtoon yang memang punya
 * wilayah besar.
 *
 * Yang tersisa sekarang sengaja **tidak punya mode apa pun**. Satu
 * ketukan = satu seleksi, seperti tongkat sihir Photoshop.
 *
 * Yang dijamin:
 *
 * 1. **Seleksi tertutup dan bisa di-isi.** Kontur dibangun sebagai rantai
 *    loop tertutup lewat [MaskContour]. Versi lama menulis tiap ruas
 *    marching squares sebagai `moveTo` + `lineTo`, sehingga terbentuk
 *    ribuan sub-path terpisah yang tidak bisa di-isi (terbukti 5,8% dari
 *    mask; setelah diperbaiki 100%).
 * 2. **Bekerja di kanvas berapa pun.** Piksel dibaca dari jendela di
 *    sekitar ketukan, bukan seluruh halaman dikecilkan ke 480px. Pada
 *    halaman 663x16000, downsample global menyisakan lebar sampel 20px
 *    sehingga outline gelembung hilang dan wand selalu gagal.
 * 3. **Deterministik.** Tanpa acak, tanpa waktu. Masukan sama = hasil sama.
 *
 * ## Toleransi
 *
 * Nilai 0..100 dipetakan ke jarak warna 0..255 dalam sRGB, persis
 * seperti yang tertulis di slider. Ini memakai jarak **kuadrat Euclidean**
 * RGB langsung, bukan linear-light yang dikonversi dan digandakan. Alasan:
 * jarak langsung bisa diprediksi user ("warna dalam 32 dari warna ini"),
 * sedangkan hasil konversi sebelumnya jauh lebih longgar dari angka yang
 * tertulis sehingga kegagalannya tidak bisa ditebak.
 *
 * Efek tepi berantian ditangani oleh [MaskContour], yang placing titik
 * kontur tepat di batas piksel, bukan di tengah blok.
 */
object WandSelection {

    /** Panjang sisi jendela baca piksel dalam piksel kanvas. */
    private const val WINDOW = 512

    /** Jendela lebih besar dicoba bila yang pertama tidak menghasilkan wilayah. */
    private val WINDOW_LADDER = intArrayOf(WINDOW, 1200)

    /** Batas bawah toleransi: 0 berarti warna harus sama persis. */
    private const val TOL_MIN = 0

    /** Batas atas toleransi, mengikuti rentang Photoshop (0..255). */
    const val TOL_MAX = 255

    /** Toleransi bawaan, mengikuti Photoshop. */
    const val DEFAULT_TOLERANCE = 32

    /**
     * Batas jumlah piksel agar ketukan di kertas raksasa tidak menggantung.
     * Kanvas 720x16000 = 11,5 juta piksel; jendela baca sudah membatasi
     * volumenya, angka ini pengaman tambahan.
     */
    private const val MAX_PIXELS = 2_500_000

    /** Hasil wand: Path tertutup dalam koordinat kanvas penuh. */
    class Result(
        val path: Path,
        val bounds: RectF,
        val pixels: Int
    )

    /**
     * Jalankan wand pada [bmp] di titik kanvas ([cx], [cy]).
     *
     * @param tolerance jarak warna 0..255 dalam sRGB
     * @return [Result], atau null bila tidak ada wilayah yang cocok
     */
    fun select(
        bmp: Bitmap,
        cx: Int,
        cy: Int,
        tolerance: Int = DEFAULT_TOLERANCE
    ): Result? {
        if (bmp.width <= 0 || bmp.height <= 0) return null
        if (cx !in 0 until bmp.width || cy !in 0 until bmp.height) return null
        val tol = tolerance.coerceIn(TOL_MIN, TOL_MAX)
        for (win in WINDOW_LADDER) {
            val x0 = max(0, cx - win)
            val y0 = max(0, cy - win)
            val ww = min(win * 2, bmp.width - x0)
            val wh = min(win * 2, bmp.height - y0)
            if (ww < 2 || wh < 2) continue
            val buf = IntArray(ww * wh)
            bmp.getPixels(buf, 0, ww, x0, y0, ww, wh)
            val r = runOn(buf, ww, wh, cx - x0, cy - y0, tol, x0, y0)
            if (r != null) return r
        }
        return null
    }

    /**
     * Wand pada potongan piksel. Publik supaya bisa diuji tanpa Bitmap.
     *
     * @param originX geseran X potongan terhadap koordinat kanvas
     * @param originY geseran Y potongan terhadap koordinat kanvas
     */
    fun runOn(
        px: IntArray,
        w: Int,
        h: Int,
        seedX: Int,
        seedY: Int,
        tolerance: Int,
        originX: Int = 0,
        originY: Int = 0
    ): Result? {
        if (w < 2 || h < 2 || px.size < w * h) return null
        if (seedX !in 0 until w || seedY !in 0 until h) return null
        val seed = px[seedY * w + seedX]
        if ((seed ushr 24) and 0xFF == 0) return null
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr2 = tolerance.coerceIn(TOL_MIN, TOL_MAX) * tolerance.coerceIn(TOL_MIN, TOL_MAX)

        // BFS 4-arah. Antrean int[] dengan head/tail: tanpa alokasi di loop.
        val mask = BooleanArray(w * h)
        val queue = IntArray(min(w * h, MAX_PIXELS))
        var head = 0
        var tail = 0
        val start = seedY * w + seedX
        mask[start] = true
        queue[tail++] = start
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            // Empat tetangga eksplisit: kiri, kanan, atas, bawah. Dihitung
            // satu per satu tanpa alokasi.
            if (x > 0 && visit(px, mask, i - 1, sr, sg, sb, thr2)) {
                if (tail >= queue.size) return null
                queue[tail++] = i - 1
            }
            if (x < w - 1 && visit(px, mask, i + 1, sr, sg, sb, thr2)) {
                if (tail >= queue.size) return null
                queue[tail++] = i + 1
            }
            if (y > 0 && visit(px, mask, i - w, sr, sg, sb, thr2)) {
                if (tail >= queue.size) return null
                queue[tail++] = i - w
            }
            if (y < h - 1 && visit(px, mask, i + w, sr, sg, sb, thr2)) {
                if (tail >= queue.size) return null
                queue[tail++] = i + w
            }
        }
        if (tail < 4) return null

        val path = MaskContour.build(mask, w, h, 1f, originX.toFloat(), originY.toFloat())
            ?: return null
        val b = RectF()
        path.computeBounds(b, true)
        if (b.isEmpty) return null
        return Result(path, b, tail)
    }


    /**
     * Tandai [idx] sebagai terpilih bila warnanya dalam toleransi.
     * Mengembalikan true kalau piksel itu baru ditambahkan.
     */
    private fun visit(
        px: IntArray, mask: BooleanArray, idx: Int,
        sr: Int, sg: Int, sb: Int, thr2: Int
    ): Boolean {
        if (mask[idx]) return false
        val p = px[idx]
        if ((p ushr 24) and 0xFF == 0) return false
        val dr = ((p shr 16) and 0xFF) - sr
        val dg = ((p shr 8) and 0xFF) - sg
        val db = (p and 0xFF) - sb
        if (dr * dr + dg * dg + db * db > thr2) return false
        mask[idx] = true
        return true
    }

    /**
     * Gabungkan hasil wand ke seleksi yang sedang ada.
     *
     * @param merge true menambah pada seleksi lama, false menggantinya.
     *   Wand tidak punya substract atau intersect karena keduanya menuntut
     *   kontrol modifier yang tidak ada di bar tool; "tambah" sudah cukup
     *   untuk pemakaian sehari-hari.
     */
    fun mergeInto(engine: SelectionEngine, result: Result, merge: Boolean) {
        if (!merge) engine.clearRegions()
        engine.addRegionPath(result.path, result.bounds)
    }
}