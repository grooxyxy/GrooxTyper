package com.grooxtyper.app.model

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * SeamlessBlender: sambungan jahitan ala Content-Aware Fill tanpa model.
 *
 * Masalah yang dipecahkan: PatchMatch menyalin tekstur dengan benar, tetapi
 * warna/gradasi patch sumber sering meleset sedikit dari sekitar lubang
 * sehingga jahitan terlihat (plong di gradasi, halo di tekstur).
 * Feather/alpha-blend menutupi jahitan dengan mengaburkan — menghancurkan
 * tekstur (screentone jadi bubur, grain hilang).
 *
 * Solusi: koreksi offset domain-gradien (Poisson membrane, Perez 2003;
 * kompensasi bias/gain ala HaCohen 2011):
 * 1. Hitung selisih warna (dst - filled) pada cincin piksel valid di tepi lubang.
 * 2. Difusikan selisih itu ke dalam lubang dengan relaksasi SOR red-black
 *    (menyelesaikan persamaan Laplace: koreksi mulus mengikuti gradasi,
 *    nol artefak blur karena TEKSTUR hasil PatchMatch tidak disentuh).
 * 3. result = filled + koreksi (clamp).
 *
 * Murni Kotlin + FloatArray (O(n) memori, tanpa native/OpenCV/model),
 * deterministik di semua device. Iterasi dibatasi budget waktu agar
 * lubang raksasa tetap < ~1 detik; warna PatchMatch yang sudah dekat
 * (constraint luminance) membuat iterasi sedikit pun cukup.
 */
object SeamlessBlender {

    /** Lebar cincin Dirichlet (px) di sekeliling lubang. */
    private const val RING_WIDTH = 3

    /** Batas kerja agar lubang raksasa tidak hang (operasi ≈ iters × holePx). */
    private const val OP_BUDGET = 30_000_000L

    /** Over-relaksasi SOR (1.5 ≈ 3-4x lebih cepat dari Jacobi/Gauss-Seidel). */
    private const val OMEGA = 1.5f

    /**
     * Blend [filled] ke [dst] pada [mask] (true = lubang). Piksel di luar
     * lubang TIDAK diubah. Null bila mask kosong/OOM/invalid — caller
     * memakai hasil PatchMatch mentah sebagai fallback.
     */
    fun blend(
        dstPixels: IntArray,
        filledPixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int
    ): IntArray? {
        if (w <= 0 || h <= 0) return null
        if (dstPixels.size != w * h || filledPixels.size != w * h || mask.size != w * h) return null
        var holeCount = 0
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y * w + x]) {
                    holeCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (holeCount == 0) return filledPixels.copyOf()
        return try {
            solveAndApply(dstPixels, filledPixels, mask, w, h, holeCount, minX, minY, maxX, maxY)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun solveAndApply(
        dst: IntArray, filled: IntArray, mask: BooleanArray,
        w: Int, h: Int, holeCount: Int,
        minX: Int, minY: Int, maxX: Int, maxY: Int
    ): IntArray {
        val n = w * h
        // Cincin Dirichlet: piksel valid dalam RING_WIDTH dari lubang.
        val isRing = BooleanArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (mask[i]) continue
                var near = false
                loop@ for (dy in -RING_WIDTH..RING_WIDTH) {
                    for (dx in -RING_WIDTH..RING_WIDTH) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        if (mask[ny * w + nx]) { near = true; break@loop }
                    }
                }
                isRing[i] = near
            }
        }
        // Nilai Dirichlet = selisih dst - filled pada cincin (sudut tetap).
        val cr = FloatArray(n)
        val cg = FloatArray(n)
        val cb = FloatArray(n)
        var ringCount = 0
        for (i in 0 until n) {
            if (!isRing[i]) continue
            val d = dst[i]
            val f = filled[i]
            cr[i] = (Color.red(d) - Color.red(f)).toFloat()
            cg[i] = (Color.green(d) - Color.green(f)).toFloat()
            cb[i] = (Color.blue(d) - Color.blue(f)).toFloat()
            ringCount++
        }
        // Tanpa cincin (lubang menutup seluruh crop) → tak ada acuan, pakai mentah.
        if (ringCount == 0) return filled.copyOf()

        // SOR red-black pada piksel lubang; tetangga di luar crop dilewati
        // (Neumann implisit). Iterasi dibatasi budget.
        val holeW = maxX - minX + 1
        val holeH = maxY - minY + 1
        var iters = max(40, min(holeW, holeH) / 2)
        iters = min(iters, max(30, (OP_BUDGET / max(1, holeCount)).toInt()))
        repeat(iters) {
            // Red pass: (x + y) genap.
            for (y in minY..maxY) {
                var x = minX + ((minX + y) and 1)
                while (x <= maxX) {
                    relaxAt(cr, cg, cb, mask, isRing, w, h, x, y)
                    x += 2
                }
            }
            // Black pass: (x + y) ganjil.
            for (y in minY..maxY) {
                var x = minX + (1 - ((minX + y) and 1))
                while (x <= maxX) {
                    relaxAt(cr, cg, cb, mask, isRing, w, h, x, y)
                    x += 2
                }
            }
        }

        val out = filled.copyOf()
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val i = y * w + x
                if (!mask[i]) continue
                val f = filled[i]
                out[i] = Color.argb(
                    Color.alpha(f),
                    (Color.red(f) + cr[i]).roundToInt().coerceIn(0, 255),
                    (Color.green(f) + cg[i]).roundToInt().coerceIn(0, 255),
                    (Color.blue(f) + cb[i]).roundToInt().coerceIn(0, 255)
                )
            }
        }
        return out
    }

    private fun relaxAt(
        cr: FloatArray, cg: FloatArray, cb: FloatArray,
        mask: BooleanArray, isRing: BooleanArray,
        w: Int, h: Int, x: Int, y: Int
    ) {
        val i = y * w + x
        if (!mask[i]) return
        var sr = 0f; var sg = 0f; var sb = 0f; var cnt = 0
        if (x > 0) { val j = i - 1; if (mask[j] || isRing[j]) { sr += cr[j]; sg += cg[j]; sb += cb[j]; cnt++ } }
        if (x < w - 1) { val j = i + 1; if (mask[j] || isRing[j]) { sr += cr[j]; sg += cg[j]; sb += cb[j]; cnt++ } }
        if (y > 0) { val j = i - w; if (mask[j] || isRing[j]) { sr += cr[j]; sg += cg[j]; sb += cb[j]; cnt++ } }
        if (y < h - 1) { val j = i + w; if (mask[j] || isRing[j]) { sr += cr[j]; sg += cg[j]; sb += cb[j]; cnt++ } }
        if (cnt == 0) return
        val avgR = sr / cnt
        val avgG = sg / cnt
        val avgB = sb / cnt
        cr[i] += OMEGA * (avgR - cr[i])
        cg[i] += OMEGA * (avgG - cg[i])
        cb[i] += OMEGA * (avgB - cb[i])
    }
}
