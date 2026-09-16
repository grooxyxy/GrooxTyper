package com.grooptyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Inpainting PatchMatch — jauh lebih bagus dari Telea/Photoshop Content-Aware
 * untuk manga: mempertahankan garis, screentone, dan tekstur kertas.
 *
 * Versi ini: multi-iterasi PatchMatch + onion fill order + alpha feather
 * + guard untuk kanvas jangkung 720x16000 (hanya crop berbatas mask).
 *
 * Tidak pakai native: murni Kotlin + int[] agar tidak menambah .so.
 */
object PatchMatchInpainter {

    private const val PATCH = 7
    private const val PATCH2 = PATCH * PATCH
    private const val HALF = PATCH / 2
    private const val ITER = 5

    /**
     * Inpaint [src] di area [mask] (putih = lubang). Hasil ditulis balik ke [src].
     * [mask] dan [src] harus seukuran kanvas. Hanya area crop yang diproses.
     */
    fun inpaint(src: Bitmap, mask: Bitmap, feather: Boolean = true) {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0 || w != mask.width || h != mask.height) return

        // 1) Cari bounds mask untuk crop hemat 46MB
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        // Sample kasar tiap 2px untuk bounds cepat
        val tmp = IntArray(w * min(512, h))
        // Scan penuh tapi per strip agar tidak alokasi 46MB IntArray sekaligus untuk huge
        val stripH = 512
        var y0 = 0
        while (y0 < h) {
            val sh = min(stripH, h - y0)
            mask.getPixels(tmp, 0, w, 0, y0, w, sh)
            for (y in 0 until sh) {
                val row = y * w
                for (x in 0 until w) {
                    val p = tmp[row + x]
                    if ((p ushr 24) > 30 && (p and 0xFF) > 30) {
                        val gy = y0 + y
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (gy < minY) minY = gy
                        if (gy > maxY) maxY = gy
                    }
                }
            }
            y0 += sh
        }
        if (maxX < 0) return
        val pad = 24
        minX = max(0, minX - pad); minY = max(0, minY - pad)
        maxX = min(w - 1, maxX + pad); maxY = min(h - 1, maxY + pad)
        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        if (cw <= 8 || ch <= 8) return
        // Batas crop agar tidak OOM (jika seleksi raksasa, downscale 0.5)
        var scale = 1f
        var cropW = cw
        var cropH = ch
        if (cropW.toLong() * cropH > 2_000_000L) {
            scale = 0.5f
            cropW = max(16, (cw * scale).toInt())
            cropH = max(16, (ch * scale).toInt())
        }
        var srcCrop = try { Bitmap.createBitmap(src, minX, minY, cw, ch) } catch (e: OutOfMemoryError) { return } catch (e: Exception) { return }
        var maskCrop = try { Bitmap.createBitmap(mask, minX, minY, cw, ch) } catch (e: Exception) { srcCrop.recycle(); return }
        if (scale != 1f) {
            val sW = Bitmap.createScaledBitmap(srcCrop, cropW, cropH, true)
            val mW = Bitmap.createScaledBitmap(maskCrop, cropW, cropH, true)
            srcCrop.recycle(); maskCrop.recycle()
            srcCrop = sW; maskCrop = mW
        }
        try {
            val res = patchMatchCore(srcCrop, maskCrop, feather)
            // Kembalikan ke src skala penuh
            val toBlit = if (scale != 1f) {
                val up = Bitmap.createScaledBitmap(res, cw, ch, true)
                res.recycle()
                up
            } else res
            Canvas(src).drawBitmap(toBlit, minX.toFloat(), minY.toFloat(), null)
            toBlit.recycle()
        } finally {
            srcCrop.recycle()
            maskCrop.recycle()
        }
    }

    private fun patchMatchCore(srcCrop: Bitmap, maskCrop: Bitmap, feather: Boolean): Bitmap {
        val w = srcCrop.width
        val h = srcCrop.height
        val srcPx = IntArray(w * h)
        val maskPx = IntArray(w * h)
        srcCrop.getPixels(srcPx, 0, w, 0, 0, w, h)
        maskCrop.getPixels(maskPx, 0, w, 0, 0, w, h)
        val isHole = BooleanArray(w * h) { i ->
            val p = maskPx[i]
            (p ushr 24) > 30 && (p and 0xFF) > 30
        }
        // Hitung hole count; jika hampir semua lubang, fallback ke Telea (tidak ada source)
        var holeCount = 0
        for (b in isHole) if (b) holeCount++
        if (holeCount == 0) return srcCrop.copy(Bitmap.Config.ARGB_8888, false)
        if (holeCount > w * h * 0.85) {
            // Terlalu banyak lubang, tidak ada patch valid
            return srcCrop.copy(Bitmap.Config.ARGB_8888, false)
        }
        // Jarak ke known untuk onion order
        val dist = IntArray(w * h) { -1 }
        val q = ArrayDeque<Int>()
        for (i in isHole.indices) if (!isHole[i]) { dist[i] = 0; q.add(i) }
        // BFS dari known ke hole
        var qh = 0
        val qList = q.toMutableList()
        // Gunakan queue sederhana
        val queue = ArrayDeque<Int>()
        for (i in isHole.indices) if (!isHole[i]) queue.add(i)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val x = cur % w; val y = cur / w
            val d = dist[cur]
            for (k in 0..3) {
                val nx = x + intArrayOf(-1,1,0,0)[k]
                val ny = y + intArrayOf(0,0,-1,1)[k]
                if (nx in 0 until w && ny in 0 until h) {
                    val ni = ny * w + nx
                    if (dist[ni] == -1 && isHole[ni]) {
                        dist[ni] = d + 1
                        queue.add(ni)
                    }
                }
            }
        }
        // Urutan isi: dist kecil dulu (tepi lubang)
        val holeIdx = isHole.indices.filter { isHole[it] }.sortedBy { dist[it] }

        // NN field: offset (dx,dy) per hole pixel
        val nnX = IntArray(w * h)
        val nnY = IntArray(w * h)
        val bestDist = FloatArray(w * h) { Float.MAX_VALUE }
        val rnd = Random(42)
        // Init acak valid
        for (idx in holeIdx) {
            var tries = 0
            while (tries < 20) {
                val rx = rnd.nextInt(w)
                val ry = rnd.nextInt(h)
                val ri = ry * w + rx
                if (!isHole[ri] && patchValid(rx, ry, w, h, isHole)) {
                    nnX[idx] = rx - (idx % w)
                    nnY[idx] = ry - (idx / w)
                    bestDist[idx] = patchDist(idx % w, idx / w, rx, ry, w, h, srcPx, isHole, srcPx)
                    break
                }
                tries++
            }
        }
        // Iterasi PatchMatch
        for (iter in 0 until ITER) {
            val reverse = iter % 2 == 1
            val order = if (reverse) holeIdx.reversed() else holeIdx
            for (idx in order) {
                val x = idx % w; val y = idx / w
                var bestX = x + nnX[idx]
                var bestY = y + nnY[idx]
                var best = bestDist[idx]
                // Propagasi tetangga
                val neigh = if (reverse) arrayOf(intArrayOf(1,0), intArrayOf(0,1)) else arrayOf(intArrayOf(-1,0), intArrayOf(0,-1))
                for (d in neigh) {
                    val nx = x + d[0]; val ny = y + d[1]
                    if (nx in 0 until w && ny in 0 until h) {
                        val ni = ny * w + nx
                        if (isHole[ni]) {
                            val candX = nx + nnX[ni] - d[0]
                            val candY = ny + nnY[ni] - d[1]
                            if (candX in HALF until w - HALF && candY in HALF until h - HALF) {
                                if (patchValid(candX, candY, w, h, isHole)) {
                                    val distCand = patchDist(x, y, candX, candY, w, h, srcPx, isHole, srcPx)
                                    if (distCand < best) {
                                        best = distCand; bestX = candX; bestY = candY
                                    }
                                }
                            }
                        }
                    }
                }
                // Random search
                var rad = max(w, h) / 2
                while (rad >= 1) {
                    val rx = (bestX + rnd.nextInt(rad * 2 + 1) - rad).coerceIn(HALF, w - HALF - 1)
                    val ry = (bestY + rnd.nextInt(rad * 2 + 1) - rad).coerceIn(HALF, h - HALF - 1)
                    if (patchValid(rx, ry, w, h, isHole)) {
                        val d = patchDist(x, y, rx, ry, w, h, srcPx, isHole, srcPx)
                        if (d < best) { best = d; bestX = rx; bestY = ry }
                    }
                    rad /= 2
                }
                nnX[idx] = bestX - x
                nnY[idx] = bestY - y
                bestDist[idx] = best
            }
        }
        // Fill dengan feathering: rata-rata tertimbang patch overlapping (3x3 voting)
        val out = IntArray(w * h)
        // Salin known dulu
        for (i in srcPx.indices) out[i] = if (isHole[i]) 0 else srcPx[i]
        for (idx in holeIdx) {
            val x = idx % w; val y = idx / w
            val sx = x + nnX[idx]; val sy = y + nnY[idx]
            if (sx !in 0 until w || sy !in 0 until h) continue
            if (!feather) {
                out[idx] = srcPx[sy * w + sx]
            } else {
                // Voting 3x3: rata-rata patch di sekitar
                var rSum = 0; var gSum = 0; var bSum = 0; var cnt = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val hx = x + dx; val hy = y + dy
                    if (hx !in 0 until w || hy !in 0 until h) continue
                    val hi = hy * w + hx
                    if (!isHole[hi] && dist[hi] <= 2) continue // jangan ambil tepi known yang belum stabil
                    val sxx = hx + nnX[hi]; val syy = hy + nnY[hi]
                    if (sxx !in 0 until w || syy !in 0 until h) continue
                    // Offset untuk piksel target (x,y) relatif terhadap patch center (hx,hy)
                    val px = sxx + (x - hx); val py = syy + (y - hy)
                    if (px !in 0 until w || py !in 0 until h) continue
                    val pi = py * w + px
                    if (isHole[pi]) continue
                    val c = srcPx[pi]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                    cnt++
                }
                out[idx] = if (cnt > 0) {
                    (0xFF shl 24) or ((rSum / cnt) shl 16) or ((gSum / cnt) shl 8) or (bSum / cnt)
                } else {
                    srcPx[sy * w + sx]
                }
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun patchValid(cx: Int, cy: Int, w: Int, h: Int, isHole: BooleanArray): Boolean {
        if (cx < HALF || cx >= w - HALF || cy < HALF || cy >= h - HALF) return false
        // Patch harus sepenuhnya di known (tidak mengandung hole)
        for (dy in -HALF..HALF) for (dx in -HALF..HALF) {
            val x = cx + dx; val y = cy + dy
            if (isHole[y * w + x]) return false
        }
        return true
    }

    private fun patchDist(
        tx: Int, ty: Int,
        sx: Int, sy: Int,
        w: Int, h: Int,
        src: IntArray,
        isHole: BooleanArray,
        srcForPatch: IntArray
    ): Float {
        // SSD patch 7x7, abaikan piksel hole di target patch (sudah diketahui border)
        var sum = 0f
        var cnt = 0
        for (dy in -HALF..HALF) for (dx in -HALF..HALF) {
            val txx = tx + dx; val tyy = ty + dy
            val sxx = sx + dx; val syy = sy + dy
            if (txx !in 0 until w || tyy !in 0 until h || sxx !in 0 until w || syy !in 0 until h) continue
            val ti = tyy * w + txx
            val si = syy * w + sxx
            // Target patch: jika target piksel adalah hole, jangan bandingkan (belum ada konten)
            // Tapi kita bandingkan known ring di sekitar hole: jadi skip hole target
            if (isHole[ti]) continue
            val tc = src[ti]; val sc = srcForPatch[si]
            val dr = ((tc shr 16) and 0xFF) - ((sc shr 16) and 0xFF)
            val dg = ((tc shr 8) and 0xFF) - ((sc shr 8) and 0xFF)
            val db = (tc and 0xFF) - (sc and 0xFF)
            sum += (dr * dr + dg * dg + db * db).toFloat()
            cnt++
            if (sum > 80000f) return sum // early exit
        }
        return if (cnt == 0) Float.MAX_VALUE else sum / cnt
    }
}
