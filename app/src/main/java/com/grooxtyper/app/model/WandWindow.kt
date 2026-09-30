package com.grooxtyper.app.model

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wand pada kanvas sebesar apa pun: bukan mengecilkan SELURUH halaman, tapi
 * memotong **jendela di sekitar titik ketukan** dan menjalankan pipeline di
 * dalamnya.
 *
 * ## Kenapa jendela, bukan downsample global
 *
 * Versi lama memakai `downsampleForWand`: seluruh halaman dikecilkan ke 480 px
 * sisi terpanjang. Itu benar untuk halaman normal, tetapi fatal pada kanvas
 * webtoon. Halaman 663x16000 jadi sampel **20x480**; outline gelembung 3 px
 * berubah jadi 0,07 px, hilang saat dirata-ratakan, lalu flood keluar dari
 * gelembung dan menyatu dengan kertas halaman. Akibatnya wilayah menyentuh tepi
 * kanvas dan ditolak, dan user melihat "bukan area bubble - ketuk di dalam
 * gelembung" padahal dia sudah mengetuk di dalam gelembung.
 *
 * Angka di atas bukan tebakan: `scripts/wand-check.mjs` kasus 10 memakai
 * halaman manga asli (potret tangkapan layar di `scripts/fixtures/`) yang
 * ditempel pada kanvas 16000 px, lalu membuktikan cara lama mengembalikan null
 * sedangkan jendela ini mengembalikan tepat satu area yang berpusat di ketukan.
 *
 * ## Keuntungan kedua: memori
 *
 * Jendela membaca hanya persegi yang dibutuhkan lewat
 * [Bitmap.getPixels], jadi kanvas 720x16000 tidak pernah allocates 11,5 juta
 * integer (46MB) seperti sebelumnya. Path `downsampleForWand` masih dipakai
 * oleh pemanggil lain yang butuh seluruh halaman.
 */
object WandWindow {

    /** Panjang sisi jendela dalam piksel kanvas. */
    const val MAX_DIM = 640

    /**
     * Jendela yang dipotong. [scale] adalah faktor pengali koordinat sampel
     * ke koordinat kanvas: 1f bila jendela tidak perlu dikecilkan.
     */
    class Window(
        val px: IntArray,
        val w: Int,
        val h: Int,
        /** Koordinat kanvas pikel kiri atas jendela. */
        val x0: Int,
        val y0: Int,
        val scale: Float
    )

    /**
     * Baca satu jendela dari [Bitmap] dan, kalau jendela terlalu besar,
     * mengecilkannya dengan rata-rata kotak (menjaga garis tipis tetap ada
     * alih-alih hilang).
     */
    fun carve(bmp: Bitmap, cx: Int, cy: Int, pad: Int, maxDim: Int = MAX_DIM): Window? {
        val W = bmp.width
        val H = bmp.height
        if (W <= 0 || H <= 0 || cx !in 0 until W || cy !in 0 until H) return null
        val rx = (cx - pad).coerceIn(0, W - 1)
        val ry = (cy - pad).coerceIn(0, H - 1)
        val rw = min(pad * 2, W - rx)
        val rh = min(pad * 2, H - ry)
        if (rw < 2 || rh < 2) return null
        val buf = IntArray(rw * rh)
        bmp.getPixels(buf, 0, rw, rx, ry, rw, rh)
        return shrink(Window(buf, rw, rh, rx, ry, 1f), maxDim)
    }

    /** Bentuk jendela dari larik piksel penuh (dipakai uji dan pemarshal). */
    fun carveFromArray(
        px: IntArray, w: Int, h: Int, cx: Int, cy: Int, pad: Int, maxDim: Int = MAX_DIM
    ): Window? {
        if (w <= 0 || h <= 0 || cx !in 0 until w || cy !in 0 until h) return null
        if (px.size < w * h) return null
        val rx = (cx - pad).coerceIn(0, w - 1)
        val ry = (cy - pad).coerceIn(0, h - 1)
        val rw = min(pad * 2, w - rx)
        val rh = min(pad * 2, h - ry)
        if (rw < 2 || rh < 2) return null
        val buf = IntArray(rw * rh)
        for (y in 0 until rh) {
            System.arraycopy(px, (ry + y) * w + rx, buf, y * rw, rw)
        }
        return shrink(Window(buf, rw, rh, rx, ry, 1f), maxDim)
    }

    private fun shrink(win: Window, maxDim: Int): Window {
        val longSide = max(win.w, win.h)
        if (longSide <= maxDim) return win
        val s = maxDim.toFloat() / longSide
        val dw = max(8, (win.w * s).roundToInt())
        val dh = max(8, (win.h * s).roundToInt())
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy0 = (y * win.h) / dh
            val sy1 = max((y + 1) * win.h / dh, sy0 + 1)
            for (x in 0 until dw) {
                val sx0 = (x * win.w) / dw
                val sx1 = max((x + 1) * win.w / dw, sx0 + 1)
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (yy in sy0 until sy1) {
                    val base = yy * win.w
                    for (xx in sx0 until sx1) {
                        val p = win.px[base + xx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                val k = max(1, n)
                out[y * dw + x] = -16777216 or ((r / k) shl 16) or ((g / k) shl 8) or (b / k)
            }
        }
        return Window(out, dw, dh, win.x0, win.y0, win.scale / s)
    }

    /**
     * Pilih satu area di sekitar ketukan, memperbesar jendela bila perlu.
     *
     * Perbesarannya beralasannya jelas: kalau wilayah hasil menyentuh tepi
     * jendela, itu menandakan jendela terlalu kecil (atau wilayahnya memang
     * sampai tepi kanvas). Menambah pad memberi gelembung ruang bernapas dan
     * biasanya mengubah "ditolak" jadi "ditemukan".
     *
     * Hasil [BubbleAreaPipeline.Area] sudah memakai koordinat KANVAS.
     */
    fun areaAt(
        bmp: Bitmap,
        cx: Int,
        cy: Int,
        params: WandEngine.Params,
        allowBorder: Boolean = false,
        lightSnap: Boolean = false,
        pads: IntArray = DEFAULT_PADS,
        maxDim: Int = MAX_DIM
    ): BubbleAreaPipeline.Area? {
        for (pad in pads) {
            val win = carve(bmp, cx, cy, pad, maxDim) ?: continue
            val lx = ((cx - win.x0) * win.scale).roundToInt()
            val ly = ((cy - win.y0) * win.scale).roundToInt()
            if (lx < 0 || ly < 0 || lx >= win.w || ly >= win.h) continue
            val area = BubbleAreaPipeline.areaAt(
                win.px, win.w, win.h, lx, ly, params,
                outScale = win.scale, offX = win.x0.toFloat(), offY = win.y0.toFloat(),
                allowBorder = allowBorder, lightSnap = lightSnap
            )
            if (area != null) return area
        }
        return null
    }

    /**
     * Serupa dengan [areaAt] tapi untuk tongkat sihir klasik: hasilnya
     * langsung ditambahkan sebagai region seleksi.
     */
    fun selectWand(
        engine: SelectionEngine,
        bmp: Bitmap,
        cx: Int,
        cy: Int,
        params: WandEngine.Params,
        pads: IntArray = DEFAULT_PADS,
        maxDim: Int = MAX_DIM
    ): Boolean {
        for (pad in pads) {
            val win = carve(bmp, cx, cy, pad, maxDim) ?: continue
            val lx = ((cx - win.x0) * win.scale).roundToInt()
            val ly = ((cy - win.y0) * win.scale).roundToInt()
            if (lx < 0 || ly < 0 || lx >= win.w || ly >= win.h) continue
            val ok = engine.selectWandWindowed(
                win.px, win.w, win.h, lx, ly, params, win.scale, win.x0.toFloat(), win.y0.toFloat()
            )
            if (ok) return true
        }
        return false
    }

    /**
     * Tiga ukuran jendela dalam piksel kanvas. Yang pertama cukup untuk
     * gelembung biasa; yang kedua untuk gelembung besar atau panel; yang
     * ketiga untuk wilayah yang sangat luas. Sisi 640 (=2 x 300) menjaga
     * memori tetap kecil: 640x640 = 410 ribu piksel = 1,6MB.
     */
    val DEFAULT_PADS = intArrayOf(300, 700, 1500)
}
