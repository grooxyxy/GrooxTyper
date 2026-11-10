package com.grooxtyper.app.model

import android.graphics.Color
import com.grooxtyper.app.model.selection.CanvasPixelProvider
import com.grooxtyper.app.model.selection.HugeWandEngine

/**
 * Bubble Eraser (ala TypeR "Bubble Cleaner") untuk manga lettering.
 *
 * Satu ketukan di dalam gelembung → **teks di dalamnya hilang**, diganti
 * dengan **warna gelembung itu sendiri** (bukan transparan, bukan
 * inpaint). Gelembung dan outline-nya tetap utuh.
 *
 * ## Algoritma (mengikuti perilaku TypeR, diimplementasikan ulang)
 *
 *  1. **Bocor interior**: flood fill dari titik ketuk dengan toleransi →
 *     area putih di dalam gelembung. Outline gelembung berwarna lain,
 *     jadi flood berhenti di outline dan outline TIDAK ikut terhapus.
 *  2. **Warna gelembung**: warna paling sering di interior (histogram
 *     5-bit per kanal), persis seperti TypeR memilih warna bubble.
 *  3. **Tinta (teks)**: piksel di dalam interior yang warnanya berbeda
 *     jauh dari warna bubble → itu huruf. TypeR memakai analisis lubang
 *     (hole) berlabel; di sini selisih warna dipakai karena jauh lebih
 *     sederhana dan hasilnya sama untuk lettering biasa.
 *  4. **Margin**: tinta dilebarkan `margin` piksel supaya tepi
 *     anti-alias dan halo JPEG ikut tertutup.
 *  5. **Isi**: piksel tinta diganti warna bubble.
 *
 * Flood memakai [HugeWandEngine] yang bekerja di atas peta tile **sparse**,
 * jadi tidak pernah mengalokasi buffer sebesar kanvas — penting untuk
 * kanvas jangkung 720x16000 (11,5MP).
 */
class BubbleEraserEngine {

    /** Hasil rencana pembersihan satu gelembung. */
    data class Plan(
        /** Area piksel yang harus diganti (koordinat kanvas). */
        val mask: BooleanArray,
        val width: Int,
        val height: Int,
        val left: Int,
        val top: Int,
        /** Warna bubble (ARGB) untuk dipakai mengisi. */
        val color: Int,
        /** Jumlah piksel yang akan diganti. */
        val pixels: Int
    )

    /**
     * Susun rencana pembersihan untuk titik ketuk [x], [y].
     *
     * @return null bila titik bukan interior gelembung yang bisa dibersihkan.
     */
    suspend fun plan(
        provider: CanvasPixelProvider,
        canvasWidth: Int,
        canvasHeight: Int,
        x: Int,
        y: Int,
        tolerance: Int,
        margin: Int,
        inkThreshold: Int = 48
    ): Plan? {
        if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) return null

        // 1. Flood interior gelembung (peta tile sparse, anti-alias dimatikan
        //    supaya batasnya tegas untuk pencarian tinta).
        val engine = HugeWandEngine(provider, canvasWidth, canvasHeight)
        val result = engine.select(
            seedX = x, seedY = y,
            tolerance = tolerance.coerceIn(0, 255),
            contiguous = true,
            bubbleAware = false,
            separateBubble = false,
            antialias = false
        ) ?: return null
        val map = result.selection
        val bounds = result.bounds
        if (bounds.isEmpty) return null

        val w = bounds.width()
        val h = bounds.height()
        if (w <= 2 || h <= 2) return null

        // 2. Warna bubble = warna paling sering di interior (histogram 5-bit).
        val bins = IntArray(32768)
        var interiorCount = 0
        val ts = map.tileSize
        map.forEachTile { tx0, ty0, tile ->
            val ox = tx0 * ts
            val oy = ty0 * ts
            for (ty in 0 until tile.height) {
                val row = ty * tile.width
                for (tx in 0 until tile.width) {
                    if (tile.mask[row + tx].toInt() and 0xFF == 0) continue
                    interiorCount++
                    val p = provider.getPixel(ox + tx, oy + ty)
                    val key = (((p shr 16) and 0xFF) shr 3 shl 10) or
                        (((p shr 8) and 0xFF) shr 3 shl 5) or
                        ((p and 0xFF) shr 3)
                    bins[key]++
                }
            }
        }
        if (interiorCount < 64) return null

        // Verifikasi "ini bubble?" ala TypeR (`looksLikeBubble`): wilayah
        // yang terlalu luas, menyentuh tepi halaman, berlubang besar
        // (panel), atau berisi patch putih besar (wajah) BUKAN gelembung.
        // Tanpa cek ini, bubble eraser bisa menghapus teks di area gambar.
        val looksBubble = com.grooxtyper.app.model.selection.BubbleSeparator.looksLikeBubble(
            mask = map,
            left = bounds.left,
            top = bounds.top,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            lumaAt = { cx, cy ->
                com.grooxtyper.app.model.selection.BubbleSeparator.luma(provider.getPixel(cx, cy))
            }
        )
        if (!looksBubble) return null

        var bestKey = -1
        var bestCount = 0
        for (i in bins.indices) {
            if (bins[i] > bestCount) {
                bestCount = bins[i]
                bestKey = i
            }
        }
        if (bestKey < 0) return null
        val br = (((bestKey shr 10) and 31) shl 3) or 4
        val bg = (((bestKey shr 5) and 31) shl 3) or 4
        val bb = ((bestKey and 31) shl 3) or 4
        val bubbleColor = Color.argb(255, br, bg, bb)

        // 3. Tinta = piksel interior yang warnanya beda jauh dari bubble.
        //    Peta "interior" disimpan agar pena tidak keluar dari gelembung.
        val left = bounds.left
        val top = bounds.top
        val inside = BooleanArray(w * h)
        map.forEachTile { tx0, ty0, tile ->
            val ox = tx0 * ts
            val oy = ty0 * ts
            for (ty in 0 until tile.height) {
                val cy = oy + ty - top
                if (cy !in 0 until h) continue
                val row = ty * tile.width
                for (tx in 0 until tile.width) {
                    if (tile.mask[row + tx].toInt() and 0xFF == 0) continue
                    val cx = ox + tx - left
                    if (cx !in 0 until w) continue
                    inside[cy * w + cx] = true
                }
            }
        }

        val ink = BooleanArray(w * h)
        var inkCount = 0
        for (yy in 0 until h) {
            val row = (top + yy) * canvasWidth + left
            for (xx in 0 until w) {
                if (!inside[yy * w + xx]) continue
                val p = provider.getPixel(left + xx, top + yy)
                val dr = absInt(((p shr 16) and 0xFF) - br)
                val dg = absInt(((p shr 8) and 0xFF) - bg)
                val db = absInt((p and 0xFF) - bb)
                if (maxOf(dr, dg, db) >= inkThreshold) {
                    ink[yy * w + xx] = true
                    inkCount++
                }
            }
        }
        if (inkCount == 0) return null

        // 4. Margin: lebarkan tinta, tapi jangan keluar dari interior.
        val grown = growInside(ink, inside, w, h, margin.coerceIn(0, 20))
        var finalCount = 0
        for (v in grown) if (v) finalCount++
        if (finalCount == 0) return null

        return Plan(grown, w, h, left, top, bubbleColor, finalCount)
    }

    /**
     * Terapkan rencana ke layer: piksel tinta diganti warna bubble.
     *
     * Pakai blok piksel (getPixels/setPixels) satu kali, bukan setPixel
     * per piksel — yang latter lambat banget lewat JNI.
     *
     * @return jumlah piksel yang diganti.
     */
    fun apply(layer: DrawingLayer, plan: Plan): Int {
        val bmp = layer.getPersistentBitmap()
        if (bmp.width < plan.left + plan.width || bmp.height < plan.top + plan.height) return 0
        val w = plan.width
        val h = plan.height
        val r = plan.color
        val buf = IntArray(w * h)
        bmp.getPixels(buf, 0, w, plan.left, plan.top, w, h)
        var changed = 0
        for (i in buf.indices) {
            if (!plan.mask[i]) continue
            if (buf[i] == r) continue
            buf[i] = r
            changed++
        }
        if (changed == 0) return 0
        bmp.setPixels(buf, 0, w, plan.left, plan.top, w, h)
        layer.markDirty()
        return changed
    }

    /** Lebarkan mask [radius] piksel, tapi hanya ke area yang masih interior. */
    private fun growInside(
        ink: BooleanArray,
        inside: BooleanArray,
        w: Int,
        h: Int,
        radius: Int
    ): BooleanArray {
        if (radius <= 0) return ink
        var cur = ink
        repeat(radius) {
            val next = cur.copyOf()
            for (yy in 0 until h) {
                for (xx in 0 until w) {
                    if (cur[yy * w + xx]) continue
                    var hit = false
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = xx + dx
                            val ny = yy + dy
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                            if (!cur[ny * w + nx]) continue
                            // Boleh tumbuh hanya ke dalam interior.
                            if (!inside[ny * w + nx]) continue
                            hit = true
                        }
                        if (hit) break
                    }
                    if (hit) next[yy * w + xx] = true
                }
            }
            cur = next
        }
        return cur
    }

    private fun absInt(v: Int): Int = if (v < 0) -v else v
}
