package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bubble Cleaner (ala TypeR "Bubble Cleaner", shortcut J di sana).
 *
 * Satu ketukan di dalam gelembung → **teksnya hilang**, ditutup dengan
 * **warna gelembung itu sendiri** (bukan transparan, bukan inpaint).
 * Gelembung dan outline-nya tetap utuh.
 *
 * ## Algoritma (port dari `planBubbleClean` TypeR)
 *
 *  1. **Flood interior** dari titik ketuk dengan toleransi → area putih
 *     di dalam gelembung. Outline berwarna lain, jadi flood berhenti di
 *     outline dan outline TIDAK ikut terhapus.
 *  2. **Klik pada huruf?** Kalau hasil flood terlalu tipis (< 6 px),
 *     TypeR mem-cast ke 16 arah untuk mencari area polos di belakang
 *     huruf, lalu memilih wilayah yang paling banyak "suara"-nya.
 *  3. **Tolak yang bocor**: wilayah yang menyentuh 3+ tepi halaman atau
 *     menutup > 60% halaman bukan gelembung.
 *  4. **Lubang**: komponen yang tertutup di dalam wilayah = huruf (atau
 *     gambar). Lubang yang terlalu besar (> 18% area) atau terlalu tebal
 *     (> 4% sisi terkecil) adalah gambar/panel → dibiarkan.
 *  5. **Margin**: huruf dilebarkan `margin` piksel supaya tepi
 *     anti-alias dan halo JPEG ikut tertutup.
 *  6. **Warna**: median kanal + sebaran 90% persentil menentukan
 *     "datar" (satu warna) atau "bergradien". Datar → isi satu warna;
 *     bergradien → warna tiap piksel diisi dari piksek nearby (push-pull).
 *
 * Ini tool TERPISAH: tak digabung dengan alur inpaint/clear yang sudah
 * ada, dan tak memakai mesin wand.
 */
class BubbleCleanerEngine {

    /** Rencana pembersihan satu gelembung. */
    data class Plan(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        /** Piksel ARGB hasil (hanya area yang berubah). */
        val pixels: IntArray,
        /** Berapa piksel yang benar-benar digambar. */
        val painted: Int,
        /** True = gelembung satu warna (isi rata). */
        val flat: Boolean,
        /** Warna median gelembung (untuk laporan). */
        val color: Int
    )

    /** Wilayah hasil flood. */
    private class Region(
        val id: Int,
        val area: Int,
        val box: IntArray   // left, top, right, bottom (exklusif)
    ) {
        /** Tebal wilayah (jarak terbesar dari tepi); 0 = bocor. */
        var thick: Float = 0f
    }

    /**
     * Susun rencana pembersihan untuk ketukan di [x], [y].
     *
     * @param px piksel ARGB halaman (baris-mayor).
     * @return null bila titik bukan gelembung yang bisa dibersihkan.
     */
    fun plan(
        px: IntArray,
        pageW: Int,
        pageH: Int,
        x: Int,
        y: Int,
        tolerance: Int,
        margin: Int
    ): Plan? {
        if (x !in 0 until pageW || y !in 0 until pageH) return null
        if (px.size < pageW * pageH) return null
        val tol = tolerance.coerceIn(0, 255)
        val mrg = margin.coerceIn(0, 20)
        val labels = IntArray(pageW * pageH)

        // 1) Flood pertama dari titik ketuk.
        val first = flood(px, pageW, pageH, x, y, tol, labels, 1) ?: return null
        var region = first
        val holeLimit = 0.18

        // 2) Kalau yang terkena cuma huruf (tipis), cari area polos di
        //    belakang huruf dengan melacak 16 arah.
        if (thickness(labels, 1, pageW, first.box) < THICK) {
            val found = HashMap<Int, Int>()
            val tried = ArrayList<Region>()
            tried.add(first)
            var next = 2
            for (n in 0 until 16) {
                val angle = n * Math.PI / 8.0
                val dx = kotlin.math.cos(angle)
                val dy = kotlin.math.sin(angle)
                var past = -1
                var step = 1
                while (step < 400) {
                    val px2 = (x + dx * step).roundToInt()
                    val py2 = (y + dy * step).roundToInt()
                    if (px2 < 0 || py2 < 0 || px2 >= pageW || py2 >= pageH) break
                    val owner = labels[py2 * pageW + px2]
                    if (past < 0 && owner == 1) {
                        step++
                        continue
                    }
                    if (past < 0) past = step
                    if (step - past > 6) break
                    var r = if (owner != 0) tried.getOrNull(owner - 1) else null
                    if (r == null) {
                        if (next > 255) break
                        r = flood(px, pageW, pageH, px2, py2, tol, labels, next++) ?: break
                        r.thick = if (leaks(r, pageW, pageH)) 0f else thickness(labels, r.id, pageW, r.box)
                        tried.add(r)
                    }
                    if (r.id == first.id || r.thick < THICK) {
                        step++
                        continue
                    }
                    found[r.id] = (found[r.id] ?: 0) + 1
                    break
                }
            }
            var votes = 0
            var bestId = -1
            for ((id, n) in found) {
                if (n > votes) {
                    votes = n
                    bestId = id
                }
            }
            if (bestId < 0) return null
            region = tried.firstOrNull { it.id == bestId } ?: return null
        }

        // 3) Tolak wilayah yang bocor ke tepi halaman.
        if (leaks(region, pageW, pageH)) return null

        // 4) Bentuk mask di kotak wilayah (+1 px margin).
        val rb = region.box
        val ax0 = max(0, rb[0] - 1)
        val ay0 = max(0, rb[1] - 1)
        val ax1 = min(pageW, rb[2] + 1)
        val ay1 = min(pageH, rb[3] + 1)
        val aw = ax1 - ax0
        val ah = ay1 - ay0
        if (aw <= 1 || ah <= 1) return null
        val mask = BooleanArray(aw * ah)
        for (yy in 0 until ah) {
            val row = (ay0 + yy) * pageW + ax0
            for (xx in 0 until aw) {
                if (labels[row + xx] == region.id) mask[yy * aw + xx] = true
            }
        }

        // 5) Lubang: komponen tertutup di dalam wilayah.
        val holes = labelHoles(mask, aw, ah)
        var filled = region.area
        for (i in 1 until holes.sizes.size) filled += holes.sizes[i]
        if (filled > pageW.toLong() * pageH * 0.2) return null

        // Hole yangkept = huruf; hole besar/tebal = gambar (dibiarkan).
        val keep = BooleanArray(holes.sizes.size)
        for (id in holes.sizes.indices) {
            keep[id] = id > 0 && holes.sizes[id] <= filled * holeLimit
        }
        // Untuk ketukan (bukan kotak): hole yang terlalu tebal = gambar.
        val depth = distances(holes.label, aw, ah)
        val thickest = FloatArray(holes.sizes.size)
        for (i in 0 until aw * ah) {
            if (holes.label[i] > 0 && depth[i] > thickest[holes.label[i]]) {
                thickest[holes.label[i]] = depth[i]
            }
        }
        val most = max(12f, min(pageW, pageH) * 0.04f)
        for (id in keep.indices) {
            if (id > 0) keep[id] = keep[id] && thickest[id] <= most
        }

        val unknown = BooleanArray(aw * ah)
        var any = false
        for (i in 0 until aw * ah) {
            val l = holes.label[i]
            if (l > 0 && keep[l]) {
                unknown[i] = true
                any = true
            }
        }
        if (!any) return null

        // Piksel yang menempel ke outline/gambar dibiarkan apa adanya.
        val other = BooleanArray(aw * ah)
        for (i in 0 until aw * ah) {
            other[i] = holes.outside[i] || (holes.label[i] > 0 && !keep[holes.label[i]])
        }
        fun againstOther(cx: Int, cy: Int): Boolean {
            for (ny in max(0, cy - 1)..min(ah - 1, cy + 1)) {
                for (nx in max(0, cx - 1)..min(aw - 1, cx + 1)) {
                    if (other[ny * aw + nx]) return true
                }
            }
            return false
        }

        // 6) Margin: huruf dilebarkan; piksel yang menempel_other tak ikut.
        val grown = grow(unknown, aw, ah, mrg)
        val near = grow(unknown, aw, ah, mrg + 3)
        for (yy in 0 until ah) {
            for (xx in 0 until aw) {
                val i = yy * aw + xx
                if (!unknown[i] && mask[i] && grown[i] && !againstOther(xx, yy)) unknown[i] = true
            }
        }

        // 7) Warna: median kanal + sebaran 90% persentil.
        val hist = Array(3) { IntArray(256) }
        var plainCount = 0
        for (yy in 0 until ah) {
            for (xx in 0 until aw) {
                val i = yy * aw + xx
                if (!mask[i] || near[i] || againstOther(xx, yy)) continue
                val p = px[(ay0 + yy) * pageW + ax0 + xx]
                hist[0][(p shr 16) and 0xFF]++
                hist[1][(p shr 8) and 0xFF]++
                hist[2][p and 0xFF]++
                plainCount++
            }
        }
        if (plainCount == 0) return null
        val median = IntArray(3) { medianOf(hist[it], plainCount) }
        var flat = false
        if (plainCount >= 200) {
            val spread = IntArray(256)
            for (yy in 0 until ah) {
                for (xx in 0 until aw) {
                    val i = yy * aw + xx
                    if (!mask[i] || near[i] || againstOther(xx, yy)) continue
                    val p = px[(ay0 + yy) * pageW + ax0 + xx]
                    val d = max(
                        abs(((p shr 16) and 0xFF) - median[0]),
                        max(abs(((p shr 8) and 0xFF) - median[1]), abs((p and 0xFF) - median[2]))
                    )
                    spread[d.coerceIn(0, 255)]++
                }
            }
            var seen = 0
            var p90 = 255
            for (v in 0..255) {
                seen += spread[v]
                if (seen >= plainCount * 0.9) {
                    p90 = v
                    break
                }
            }
            flat = p90 <= 12
        }
        // Gelembung datar: seluruh area (bukan cuma huruf) ditutup warna
        // bubble, supaya tak ada sisa halo huruf.
        if (flat) {
            for (yy in 0 until ah) {
                for (xx in 0 until aw) {
                    val i = yy * aw + xx
                    if (!unknown[i] && mask[i] && !againstOther(xx, yy)) unknown[i] = true
                }
            }
        }

        // 8) Area yang digambar = yang berubah saja (+ area sekitar untuk baca warna).
        var ux0 = aw
        var uy0 = ah
        var ux1 = -1
        var uy1 = -1
        for (yy in 0 until ah) {
            for (xx in 0 until aw) {
                if (!unknown[yy * aw + xx]) continue
                if (xx < ux0) ux0 = xx
                if (xx > ux1) ux1 = xx
                if (yy < uy0) uy0 = yy
                if (yy > uy1) uy1 = yy
            }
        }
        if (ux1 < ux0) return null
        val pad = if (flat) 0 else 12 + mrg * 2
        val cx0 = max(0, ux0 - pad)
        val cy0 = max(0, uy0 - pad)
        val cw = min(aw, ux1 + 1 + pad) - cx0
        val ch = min(ah, uy1 + 1 + pad) - cy0
        if (cw <= 0 || ch <= 0) return null

        // Warna: datar -> median; bergradien -> warna nearby (push-pull).
        val pixels = IntArray(cw * ch)
        var painted = 0
        if (flat) {
            val c = Color.argb(255, median[0], median[1], median[2])
            for (yy in 0 until ch) {
                for (xx in 0 until cw) {
                    if (!unknown[(yy + cy0) * aw + xx + cx0]) continue
                    pixels[yy * cw + xx] = c
                    painted++
                }
            }
        } else {
            val rgb = FloatArray(cw * ch * 3)
            val known = BooleanArray(cw * ch)
            var knownCount = 0
            for (yy in 0 until ch) {
                for (xx in 0 until cw) {
                    val ai = (yy + cy0) * aw + xx + cx0
                    if (!mask[ai] || near[ai] || againstOther(xx + cx0, yy + cy0)) continue
                    val p = px[(ay0 + yy + cy0) * pageW + ax0 + xx + cx0]
                    val i = yy * cw + xx
                    rgb[i * 3] = ((p shr 16) and 0xFF).toFloat()
                    rgb[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
                    rgb[i * 3 + 2] = (p and 0xFF).toFloat()
                    known[i] = true
                    knownCount++
                }
            }
            if (knownCount == 0) return null
            val colours = pushPull(rgb, known, cw, ch)
            for (yy in 0 until ch) {
                for (xx in 0 until cw) {
                    if (!unknown[(yy + cy0) * aw + xx + cx0]) continue
                    val i = yy * cw + xx
                    pixels[i] = Color.argb(
                        255,
                    colours[i * 3].toInt().coerceIn(0, 255),
                    colours[i * 3 + 1].toInt().coerceIn(0, 255),
                    colours[i * 3 + 2].toInt().coerceIn(0, 255)
                    )
                    painted++
                }
            }
        }
        if (painted == 0) return null
        return Plan(
            left = cx0 + ax0,
            top = cy0 + ay0,
            width = cw,
            height = ch,
            pixels = pixels,
            painted = painted,
            flat = flat,
            color = Color.argb(255, median[0], median[1], median[2])
        )
    }

    /**
     * Terapkan rencana ke layer (ganti piksel dengan warna bubble).
     *
     * @return jumlah piksel yang diganti.
     */
    fun apply(layer: DrawingLayer, plan: Plan): Int {
        val bmp = layer.getPersistentBitmap()
        if (bmp.width < plan.left + plan.width || bmp.height < plan.top + plan.height) return 0
        val buf = IntArray(plan.width * plan.height)
        bmp.getPixels(buf, 0, plan.width, plan.left, plan.top, plan.width, plan.height)
        var changed = 0
        for (i in buf.indices) {
            val c = plan.pixels[i]
            if (c == 0) continue
            if (buf[i] == c) continue
            buf[i] = c
            changed++
        }
        if (changed == 0) return 0
        bmp.setPixels(buf, 0, plan.width, plan.left, plan.top, plan.width, plan.height)
        layer.markDirty()
        return changed
    }

    // ------------------------------------------------------------------
    // Helper (ikut algoritma TypeR)
    // ------------------------------------------------------------------

    /** Tebal minimum agar dianggap gelembung, bukan huruf (TypeR: 6). */
    private val THICK = 6f

    /** Flood span dari seed; menulis label [id] ke [labels]. */
    private fun flood(
        px: IntArray,
        pageW: Int,
        pageH: Int,
        sx: Int,
        sy: Int,
        tolerance: Int,
        labels: IntArray,
        id: Int
    ): Region? {
        val seed = px[sy * pageW + sx]
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        fun close(idx: Int): Boolean {
            val p = px[idx]
            return abs(((p shr 16) and 0xFF) - sr) <= tolerance &&
                abs(((p shr 8) and 0xFF) - sg) <= tolerance &&
                abs((p and 0xFF) - sb) <= tolerance
        }
        val stack = IntArray(pageW * pageH * 2)
        var sp = 0
        stack[sp++] = sx
        stack[sp++] = sy
        var area = 0
        var left = sx
        var right = sx
        var top = sy
        var bottom = sy
        while (sp > 0) {
            val y = stack[--sp]
            var x = stack[--sp]
            while (x >= 0 && labels[y * pageW + x] == 0 && close(y * pageW + x)) x--
            x++
            var up = false
            var down = false
            while (x < pageW) {
                val index = y * pageW + x
                if (labels[index] != 0 || !close(index)) break
                labels[index] = id
                area++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (y > 0) {
                    if (labels[index - pageW] == 0 && close(index - pageW)) {
                        if (!up) {
                            stack[sp++] = x
                            stack[sp++] = y - 1
                            up = true
                        }
                    } else up = false
                }
                if (y < pageH - 1) {
                    if (labels[index + pageW] == 0 && close(index + pageW)) {
                        if (!down) {
                            stack[sp++] = x
                            stack[sp++] = y + 1
                            down = true
                        }
                    } else down = false
                }
                x++
            }
        }
        if (area == 0) return null
        return Region(id, area, intArrayOf(left, top, right + 1, bottom + 1))
    }

    /** True bila wilayah menyentuh >= 3 tepi halaman atau > 60% halaman. */
    private fun leaks(r: Region, pageW: Int, pageH: Int): Boolean {
        val edges = (if (r.box[0] <= 0) 1 else 0) +
            (if (r.box[1] <= 0) 1 else 0) +
            (if (r.box[2] >= pageW) 1 else 0) +
            (if (r.box[3] >= pageH) 1 else 0)
        val area = (r.box[2] - r.box[0]).toLong() * (r.box[3] - r.box[1])
        return edges >= 3 || area > pageW.toLong() * pageH * 6 / 10
    }

    /** Tebal wilayah di titik tertebalnya (jarak dari tepi). */
    private fun thickness(labels: IntArray, id: Int, pageW: Int, box: IntArray): Float {
        val bw = box[2] - box[0]
        val bh = box[3] - box[1]
        if (bw <= 0 || bh <= 0) return 0f
        val inside = BooleanArray(bw * bh)
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                inside[y * bw + x] = labels[(box[1] + y) * pageW + box[0] + x] == id
            }
        }
        val d = distances(inside, bw, bh)
        var most = 0f
        for (v in d) if (v > most) most = v
        return most
    }

    /** Jarak tiap piksel dari tepi bentuknya (0 di luar). */
    private fun distances(inside: BooleanArray, w: Int, h: Int): FloatArray {
        val d = FloatArray(w * h)
        for (i in d.indices) {
            val x = i % w
            d[i] = if (inside[i] && x > 0 && x < w - 1 && i >= w && i < w * (h - 1)) {
                1e9f
            } else if (inside[i]) 1f else 0f
        }
        val diag = 1.4142f
        for (y in 1 until h) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (d[i] > 1f) {
                    d[i] = min(
                        d[i],
                        min(
                            min(d[i - 1] + 1f, d[i - w] + 1f),
                            min(d[i - w - 1] + diag, d[i - w + 1] + diag)
                        )
                    )
                }
            }
        }
        for (y in h - 2 downTo 0) {
            for (x in w - 2 downTo 1) {
                val i = y * w + x
                if (d[i] > 1f) {
                    d[i] = min(
                        d[i],
                        min(
                            min(d[i + 1] + 1f, d[i + w] + 1f),
                            min(d[i + w + 1] + diag, d[i + w - 1] + diag)
                        )
                    )
                }
            }
        }
        return d
    }

    /** Hasil label lubang: luar, label per komponen, ukuran tiap label. */
    private class Holes(
        val outside: BooleanArray,
        val label: IntArray,
        val sizes: IntArray
    )

    /** Label komponen tertutup (lubang) di luar mask. */
    private fun labelHoles(mask: BooleanArray, w: Int, h: Int): Holes {
        val outside = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(index: Int) {
            if (mask[index] || outside[index]) return
            outside[index] = true
            queue[tail++] = index
        }
        for (x in 0 until w) {
            push(x)
            push((h - 1) * w + x)
        }
        for (y in 0 until h) {
            push(y * w)
            push(y * w + w - 1)
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            if (x > 0) push(index - 1)
            if (x < w - 1) push(index + 1)
            if (index >= w) push(index - w)
            if (index < w * (h - 1)) push(index + w)
        }
        val label = IntArray(w * h)
        val sizes = intArrayOf(0)
        for (start in 0 until w * h) {
            if (mask[start] || outside[start] || label[start] != 0) continue
            val id = sizes.size
            var size = 0
            head = 0
            tail = 0
            label[start] = id
            queue[tail++] = start
            while (head < tail) {
                val index = queue[head++]
                size++
                val x = index % w
                if (x > 0 && !mask[index - 1] && !outside[index - 1] && label[index - 1] == 0) {
                    label[index - 1] = id; queue[tail++] = index - 1
                }
                if (x < w - 1 && !mask[index + 1] && !outside[index + 1] && label[index + 1] == 0) {
                    label[index + 1] = id; queue[tail++] = index + 1
                }
                if (index >= w && !mask[index - w] && !outside[index - w] && label[index - w] == 0) {
                    label[index - w] = id; queue[tail++] = index - w
                }
                if (index < w * (h - 1) && !mask[index + w] && !outside[index + w] && label[index + w] == 0) {
                    label[index + w] = id; queue[tail++] = index + w
                }
            }
            sizes = sizes.copyOf(size + 1)
            sizes[id] = size
        }
        return Holes(outside, label, sizes)
    }

    /** Bentuk dilebarkan r piksel ke semua sisi (bujur + tegak). */
    private fun grow(shape: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        if (r <= 0) return shape
        val across = BooleanArray(w * h)
        for (y in 0 until h) {
            var prev = -1_000_000
            for (x in 0 until w) {
                if (shape[y * w + x]) prev = x
                if (x - prev <= r) across[y * w + x] = true
            }
            var nextOn = 1_000_000
            for (x in w - 1 downTo 0) {
                if (shape[y * w + x]) nextOn = x
                if (nextOn - x <= r) across[y * w + x] = true
            }
        }
        val out = BooleanArray(w * h)
        for (x in 0 until w) {
            var prev = -1_000_000
            for (y in 0 until h) {
                if (across[y * w + x]) prev = y
                if (y - prev <= r) out[y * w + x] = true
            }
            var nextOn = 1_000_000
            for (y in h - 1 downTo 0) {
                if (across[y * w + x]) nextOn = y
                if (nextOn - y <= r) out[y * w + x] = true
            }
        }
        return out
    }

    /** Median kanal dari histogram. */
    private fun medianOf(hist: IntArray, total: Int): Int {
        var seen = 0
        for (v in 0..255) {
            seen += hist[v]
            if (seen * 2 >= total) return v
        }
        return 255
    }

    /**
     * Push-pull sederhana: piksel tak dikenal diisi dari warna piksel
     * known di sekitarnya (difusi berulang).
     *
     * Hasilnya halus untuk gelembung bergradien; untuk gelembung datar
     * jalur median (flat) yang dipakai, jadi ini cuma jalur cadangan.
     */
    private fun pushPull(rgb: FloatArray, known: BooleanArray, w: Int, h: Int): FloatArray {
        val outR = FloatArray(w * h)
        val outG = FloatArray(w * h)
        val outB = FloatArray(w * h)
        val filled = BooleanArray(w * h)
        for (i in 0 until w * h) {
            if (!known[i]) continue
            outR[i] = rgb[i * 3]
            outG[i] = rgb[i * 3 + 1]
            outB[i] = rgb[i * 3 + 2]
            filled[i] = true
        }
        var changed = true
        var guard = 0
        while (changed && guard < 128) {
            changed = false
            guard++
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (filled[i]) continue
                    var sr = 0f
                    var sg = 0f
                    var sb = 0f
                    var n = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                            val j = ny * w + nx
                            if (!filled[j]) continue
                            sr += outR[j]
                            sg += outG[j]
                            sb += outB[j]
                            n++
                        }
                    }
                    if (n > 0) {
                        outR[i] = sr / n
                        outG[i] = sg / n
                        outB[i] = sb / n
                        filled[i] = true
                        changed = true
                    }
                }
            }
        }
        val res = FloatArray(w * h * 3)
        System.arraycopy(outR, 0, res, 0, w * h)
        System.arraycopy(outG, 0, res, w * h, w * h)
        System.arraycopy(outB, 0, res, 2 * w * h, w * h)
        return res
    }
}
