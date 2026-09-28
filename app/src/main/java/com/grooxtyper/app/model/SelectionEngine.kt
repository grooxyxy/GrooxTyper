package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SelectionEngine(val width: Int, val height: Int) {
    var hasSelection by mutableStateOf(false)
    /** Jumlah area terpisah dalam seleksi gabungan (untuk label UI). */
    var selectionCount by mutableStateOf(0)
    val selectionPath = Path()
    var clipboardBitmap: Bitmap? = null

    // Multi-seleksi: tiap drag/tap bubble MENAMBAH satu region; union-nya
    // yang dipakai render, mask, dan bounds. Satu-dua area yang tak
    // diinginkan bisa dihapus via tap (removeRegionAt) atau menu.
    private val regions = mutableListOf<Path>()
    private val regionBounds = mutableListOf<RectF>()
    private val fullClip = android.graphics.Region(0, 0, width, height)

    // Alokasi malas: 720x16000 = 46MB. Jangan alokasi sebelum user
    // benar-benar memakai seleksi — hemat permanen bila tak dipakai.
    private var _maskBitmap: Bitmap? = null
    private var _maskCanvas: Canvas? = null
    val selectionMaskBitmap: Bitmap
        get() {
            var b = _maskBitmap
            if (b == null || b.isRecycled) {
                b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                _maskBitmap = b
                _maskCanvas = Canvas(b)
            }
            return b
        }
    private val maskCanvas: Canvas
        get() {
            selectionMaskBitmap
            return _maskCanvas!!
        }

    /** Tambah sketsa lasso bebas sebagai SATU area baru (multi-seleksi). */
    fun setLassoPath(path: Path) {
        val single = Path(path)
        single.close()
        addRegion(single)
    }

    fun clearSelection() {
        regions.clear()
        regionBounds.clear()
        selectionPath.reset()
        hasSelection = false
        selectionCount = 0
        // Jangan alokasi hanya untuk clear — mask yang belum ada sudah kosong.
        _maskBitmap?.let { b ->
            if (!b.isRecycled) {
                (_maskCanvas ?: Canvas(b)).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
    }

    /** Hapus area terakhir yang ditambahkan. True bila ada yang dihapus. */
    fun removeLastRegion(): Boolean {
        if (regions.isEmpty()) return false
        regions.removeAt(regions.lastIndex)
        regionBounds.removeAt(regionBounds.lastIndex)
        rebuildUnion()
        return true
    }

    /**
     * Hapus SATU area yang memuat titik kanvas (x, y) — untuk membuang satu
     * atau dua area yang tidak diinginkan via tap. Cek dari teratas dulu
     * dengan uji geometri tepat (Region), bukan sekadar bounding box.
     */
    fun removeRegionAt(x: Float, y: Float): Boolean {
        for (i in regions.indices.reversed()) {
            if (!regionBounds[i].contains(x, y)) continue
            val hit = android.graphics.Region()
            hit.setPath(regions[i], fullClip)
            if (hit.contains(x.toInt(), y.toInt())) {
                regions.removeAt(i)
                regionBounds.removeAt(i)
                rebuildUnion()
                return true
            }
        }
        return false
    }

    /** Gabungkan ulang semua region menjadi [selectionPath] + mask. */
    private fun rebuildUnion() {
        selectionPath.reset()
        for ((i, r) in regions.withIndex()) {
            if (i == 0) {
                selectionPath.addPath(r)
            } else if (!selectionPath.op(r, Path.Op.UNION)) {
                selectionPath.addPath(r)
            }
        }
        selectionPath.close()
        hasSelection = regions.isNotEmpty()
        selectionCount = regions.size
        if (hasSelection) {
            updateMaskFromPath()
        } else {
            _maskBitmap?.let { b ->
                if (!b.isRecycled) {
                    (_maskCanvas ?: Canvas(b)).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                }
            }
        }
    }

    private fun addRegion(region: Path) {
        val b = RectF()
        region.computeBounds(b, true)
        if (b.isEmpty) return
        regions.add(region)
        regionBounds.add(b)
        rebuildUnion()
    }

    /** Batas seleksi aktif (untuk auto-fit teks ke bubble). Null bila tak ada seleksi. */
    fun selectionBounds(): RectF? {
        if (!hasSelection) return null
        val r = RectF()
        selectionPath.computeBounds(r, true)
        return if (r.isEmpty) null else r
    }

    /**
     * Salinan batas TIAP area terpisah (atas-dulu) — untuk dijadikan satu
     * bubble per area, bukan satu bubble gabungan.
     */
    fun regionBoundsList(): List<RectF> = regionBounds.map { RectF(it) }

    /** Tambah oval sebagai SATU area baru (tap bubble menumpuk, multi-seleksi). */
    fun selectOval(rect: RectF) {
        val oval = Path()
        oval.addOval(rect, Path.Direction.CW)
        addRegion(oval)
    }

    /**
     * Sampel piksel turunan untuk wand: kanvas 720x16000 = 11.5 juta piksel
     * (46MB) yang membuat wand berat & lambat. Wand cukup presisi di
     * [maxDim] piksel sisi terpanjang, jadi snapshot dikecilkan sekali di
     * sini; hasil path dikembalikan dalam koordinat KANVAS penuh lewat
     * [WandSample.scale].
     */
    class WandSample(
        val px: IntArray,
        val w: Int,
        val h: Int,
        /** Faktor penskalaan: koordinat kanvas = koordinat sampel * [scale]. */
        val scale: Float
    ) {
        fun mapX(x: Int): Int = (x * scale).toInt()
        fun mapY(y: Int): Int = (y * scale).toInt()
        fun toCanvasX(x: Float): Float = x * scale
        fun toCanvasY(y: Float): Float = y * scale
    }

    companion object {
        /** Batas sisi terpanjang snapshot wand (480 = ~230k piksel, cepat). */
        const val WAND_MAX_DIM = 480

        /** Turunkan piksel kanvas ke [maxDim] sisi terpanjang (rata-rata 2x2). */
        fun downsampleForWand(
            px: IntArray,
            w: Int,
            h: Int,
            maxDim: Int = WAND_MAX_DIM
        ): WandSample {
            val longSide = maxOf(w, h)
            if (longSide <= maxDim || px.size < w * h) return WandSample(px, w, h, 1f)
            val s = maxDim.toFloat() / longSide
            val dw = maxOf(8, (w * s).toInt())
            val dh = maxOf(8, (h * s).toInt())
            val out = downsampleGrayStatic(px, w, h, dw, dh)
            return WandSample(out, dw, dh, 1f / s)
        }

        private fun downsampleGrayStatic(
            src: IntArray,
            sw: Int,
            sh: Int,
            dw: Int,
            dh: Int
        ): IntArray {
            val out = IntArray(dw * dh)
            for (y in 0 until dh) {
                val sy0 = (y * sh) / dh
                val sy1 = minOf(maxOf(((y + 1) * sh) / dh, sy0 + 1), sh)
                for (x in 0 until dw) {
                    val sx0 = (x * sw) / dw
                    val sx1 = minOf(maxOf(((x + 1) * sw) / dw, sx0 + 1), sw)
                    var r = 0
                    var g = 0
                    var b = 0
                    var n = 0
                    for (yy in sy0 until sy1) {
                        val base = yy * sw
                        for (xx in sx0 until sx1) {
                            val p = src[base + xx]
                            r += (p shr 16) and 0xFF
                            g += (p shr 8) and 0xFF
                            b += p and 0xFF
                            n++
                        }
                    }
                    val k = maxOf(1, n)
                    out[y * dw + x] = -16777216 or
                        ((r / k) shl 16) or ((g / k) shl 8) or (b / k)
                }
            }
            return out
        }
    }

    /**
     * Magic Wand: banjir (flood fill) warna mirip dari titik seed, lalu
     * batasnya ditelusur jadi Path (marching squares) dan ditambah sebagai
     * SATU area baru. Konektivitas 4-arah (lebih ketat, tidak bocor lewat
     * diagonal celah line-art).
     *
     * @param px piksel ARGB baris-mayor selebar [w].
     * @param maxDist jarak Euclidean RGB maksimum dari warna seed.
     * @param outScale pengali koordinat hasil (1f = Resolution asli; pakai
     *   1/factor bila [px] berasal dari [downsampleForWand]).
     * @return true bila satu area berhasil ditambah.
     */
    fun selectWand(
        px: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        maxDist: Float,
        outScale: Float = 1f
    ): Boolean {
        if (sx !in 0 until w || sy !in 0 until h) return false
        if (px.size < w * h) return false
        val seed = px[sy * w + sx]
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val thr2 = maxDist * maxDist
        fun close(i: Int): Boolean {
            val p = px[i]
            val dr = (((p shr 16) and 0xFF) - sr).toFloat()
            val dg = (((p shr 8) and 0xFF) - sg).toFloat()
            val db = ((p and 0xFF) - sb).toFloat()
            return dr * dr + dg * dg + db * db <= thr2
        }
        if (!close(sy * w + sx)) return false
        val mask = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        mask[sy * w + sx] = true
        stack.addLast(sy * w + sx)
        var count = 0
        // Batas agar tap di latar raksasa tak menggantung UI selamanya.
        val cap = minOf(w.toLong() * h, 12_000_000L)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            count++
            if (count > cap) return false
            val x = i % w
            val y = i / w
            if (x > 0) {
                val j = i - 1
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (x < w - 1) {
                val j = i + 1
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (y > 0) {
                val j = i - w
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
            if (y < h - 1) {
                val j = i + w
                if (!mask[j] && close(j)) {
                    mask[j] = true
                    stack.addLast(j)
                }
            }
        }
        if (count < 4) return false
        val path = traceContour(mask, w, h, outScale) ?: return false
        addRegion(path)
        return true
    }

    /**
     * Pecah bubble GABUNG jadi dua via WATERSHED (mode Otomatis tongkat sihir).
     *
     * Kasus webtoon: dua bubble menyatu (overlap/berbagi dinding) terdeteksi
     * sebagai satu kotak.
     *
     * Cara kerja (sesuai file "magic wand bubble mode"):
     *  1. KONTRAKSI (erosi): mask interior kertas dikontraksikan bertahap
     *     sampai objek yang overlap terpisah menjadi 2+ komponen kecil —
     *     tiap komponen = seed satu bubble.
     *  2. WATERSHED: semua seed ditumbuhkan serentak di dalam mask asli
     *     (BFS multi-sumber); garis tempat dua front bertemu = garis belah,
     *     yaitu titik terakhir kedua bubble bersentuhan.
     * Mengembalikan dua kotak isi (koordinat kanvas). Null bila tak terpisah
     * (satu bubble utuh / erosi habis duluan).
     *
     * @param px piksel ARGB area kotak (baris-mayor, selebar [bw]).
     * @param offX/offY offset kiri-atas kotak dalam koordinat kanvas.
     */
    fun splitMergedBubble(
        px: IntArray,
        bw: Int,
        bh: Int,
        offX: Int,
        offY: Int
    ): List<RectF>? {
        if (bw < 16 || bh < 16 || px.size < bw * bh) return null
        return try {
            splitMergedWatershed(px, bw, bh, offX, offY)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Inti watershed (dipisah agar OOM/exception tertangani di pemanggil). */
    private fun splitMergedWatershed(
        src: IntArray,
        bw: Int,
        bh: Int,
        offX: Int,
        offY: Int
    ): List<RectF>? {
        // 1) Downsample area raksasa (batas 1.2MP) agar transformasi jarak +
        //    labeling + BFS tetap cepat di kanvas 720x16000.
        val scale: Float
        var w: Int
        var h: Int
        var dat = src
        if (bw.toLong() * bh > 1_200_000L) {
            scale = 0.5f
            w = maxOf(16, (bw * scale).toInt())
            h = maxOf(16, (bh * scale).toInt())
            dat = downsampleGray(src, bw, bh, w, h)
        } else {
            scale = 1f
            w = bw
            h = bh
        }
        // 2) Mask interior kertas (teks & garis outline = gelap, dikecualikan).
        val mask = BooleanArray(w * h)
        var maskCount = 0
        for (i in 0 until w * h) {
            val p = dat[i]
            val m = minOf(
                (p shr 16) and 0xFF,
                (p shr 8) and 0xFF,
                p and 0xFF
            )
            if (m > 195) {
                mask[i] = true
                maskCount++
            }
        }
        if (maskCount < 120) return null
        // 3) KONTRAKSI lewat TRANSFORMASI JARAK (chamfer) + pencarian biner.
        //    Versi lama mengikis 1 piksel per iterasi (ratusan iterasi) dan
        //    langsung diterima begitu muncul 2 komponen — termasuk noise kecil,
        //    sehingga seed noise ikut tumbuh dan hasil belah kacau.
        //    Sekarang: erosi radius r = { jarak > r }, cari r TERKECIL yang
        //    menyisakan >=2 komponen besar (masing-masing >=4% isi) → maksimal
        //    pemisahan, hanya ~7 labeling.
        val dist = distanceTransform(mask, w, h)
        var maxD = 0
        for (d in dist) if (d > maxD) maxD = d
        var seedLabels: IntArray? = null
        var seedA = 0
        var seedB = 0
        var lo = 0
        var hi = maxD
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val er = erodeByDistance(dist, mid)
            val (lab, cnt) = labelComponents(er, w, h)
            val top = topLabels(lab, cnt, 2, 4)
            if (top.size == 2) {
                seedLabels = lab
                seedA = top[0]
                seedB = top[1]
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        val seeds = seedLabels ?: return null
        // 4) WATERSHED: tumbuhkan HANYA 2 seed terpilih di dalam mask asli.
        //    Tiap piksel diklaim front yang lebih dulu sampai; garis tempat
        //    dua front bertemu = titik terakhir kedua bubble bersentuhan.
        val final = IntArray(w * h)
        val queue = ArrayDeque<Int>()
        for (i in 0 until w * h) {
            val l = seeds[i]
            if (l == seedA) {
                final[i] = 1
                queue.addLast(i)
            } else if (l == seedB) {
                final[i] = 2
                queue.addLast(i)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            val lab = final[i]
            // 4-arah: mencegah front bocor diagonal lewat celah 1px.
            if (x > 0) growIfFree(final, mask, queue, i - 1, lab)
            if (x < w - 1) growIfFree(final, mask, queue, i + 1, lab)
            if (y > 0) growIfFree(final, mask, queue, i - w, lab)
            if (y < h - 1) growIfFree(final, mask, queue, i + w, lab)
        }
        // 5) Bbox tiap wilayah, kembali ke koordinat kanvas penuh.
        val pad = 4
        fun labelBox(id: Int): RectF? {
            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                val rowBase = y * w
                for (x in 0 until w) {
                    if (final[rowBase + x] != id) continue
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
            if (r < 0 || r - l < 6 || b - t < 6) return null
            return RectF(
                (offX + (l - pad) / scale).toFloat(),
                (offY + (t - pad) / scale).toFloat(),
                (offX + (r + pad) / scale).toFloat(),
                (offY + (b + pad) / scale).toFloat()
            )
        }
        val a = labelBox(1)
        val c = labelBox(2)
        if (a == null || c == null) return null
        return listOf(a, c)
    }

    /** Klaim satu piksel untuk front [lab] bila masih kosong dan di dalam mask. */
    private fun growIfFree(
        final: IntArray,
        mask: BooleanArray,
        queue: ArrayDeque<Int>,
        j: Int,
        lab: Int
    ) {
        if (mask[j] && final[j] == 0) {
            final[j] = lab
            queue.addLast(j)
        }
    }

    /**
     * Jarak piksel ke tepi mask (di dalam = besar). Chamfer 3-4 dua arah:
     * cukup untuk erosi & "{jarak > r}" tanpa filter mahal.
     */
    private fun distanceTransform(mask: BooleanArray, w: Int, h: Int): IntArray {
        val big = w + h + 8
        val d = IntArray(w * h) { i -> if (mask[i]) big else 0 }
        // maju
        for (y in 0 until h) {
            val rowBase = y * w
            for (x in 0 until w) {
                val i = rowBase + x
                if (d[i] == 0) continue
                var m = d[i]
                if (x > 0) m = minOf(m, d[i - 1] + 3)
                if (y > 0) {
                    m = minOf(m, d[i - w] + 3)
                    if (x > 0) m = minOf(m, d[i - w - 1] + 4)
                    if (x < w - 1) m = minOf(m, d[i - w + 1] + 4)
                }
                d[i] = m
            }
        }
        // mundur
        for (y in h - 1 downTo 0) {
            val rowBase = y * w
            for (x in w - 1 downTo 0) {
                val i = rowBase + x
                if (d[i] == 0) continue
                var m = d[i]
                if (x < w - 1) m = minOf(m, d[i + 1] + 3)
                if (y < h - 1) {
                    m = minOf(m, d[i + w] + 3)
                    if (x < w - 1) m = minOf(m, d[i + w + 1] + 4)
                    if (x > 0) m = minOf(m, d[i + w - 1] + 4)
                }
                d[i] = m
            }
        }
        return d
    }

    /** Mask hasil erosi: hanya piksel yang jaraknya lebih besar dari [radius]. */
    private fun erodeByDistance(dist: IntArray, radius: Int): BooleanArray {
        val out = BooleanArray(dist.size)
        for (i in dist.indices) out[i] = dist[i] > radius
        return out
    }

    /**
     * [k] label terbesar pada peta label, tiap-tiapnya minimal [minPct] persen
     * dari total piksel berlabel. Menyaring noise kecil yang tak layak jadi
     * seed bubble.
     */
    private fun topLabels(
        labels: IntArray,
        count: Int,
        k: Int,
        minPct: Int
    ): IntArray {
        if (count < k) return IntArray(0)
        val sizes = IntArray(count + 1)
        var total = 0
        for (v in labels) {
            if (v in 1..count) {
                sizes[v]++
                total++
            }
        }
        if (total <= 0) return IntArray(0)
        val need = total * minPct / 100
        val order = (1..count).sortedByDescending { sizes[it] }
        val out = ArrayList<Int>(k)
        for (id in order) {
            if (out.size >= k) break
            if (sizes[id] >= need) out.add(id) else break
        }
        return out.toIntArray()
    }

    /** Downsample rata-rata (pertahankan kecerahan) untuk area raksasa. */
    private fun downsampleGray(
        src: IntArray,
        sw: Int,
        sh: Int,
        dw: Int,
        dh: Int
    ): IntArray {
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy0 = (y * sh) / dh
            val sy1 = maxOf(((y + 1) * sh) / dh, sy0 + 1)
            for (x in 0 until dw) {
                val sx0 = (x * sw) / dw
                val sx1 = maxOf(((x + 1) * sw) / dw, sx0 + 1)
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (sy in sy0 until minOf(sy1, sh)) {
                    for (sx in sx0 until minOf(sx1, sw)) {
                        val p = src[sy * sw + sx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                out[y * dw + x] = -16777216 or
                    ((r / maxOf(1, n)) shl 16) or
                    ((g / maxOf(1, n)) shl 8) or
                    (b / maxOf(1, n))
            }
        }
        return out
    }

    /**
     * Labeli komponen terhubung (8 arah) pada mask; kembalikan peta label
     * (0 = latar) + jumlah komponen.
     */
    private fun labelComponents(
        mask: BooleanArray,
        w: Int,
        h: Int
    ): Pair<IntArray, Int> {
        val lab = IntArray(w * h)
        var count = 0
        val stack = ArrayDeque<Int>()
        for (s in 0 until w * h) {
            if (!mask[s] || lab[s] != 0) continue
            count++
            lab[s] = count
            stack.addLast(s)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % w
                val y = i / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val j = ny * w + nx
                        if (mask[j] && lab[j] == 0) {
                            lab[j] = count
                            stack.addLast(j)
                        }
                    }
                }
            }
        }
        return lab to count
    }

    /**
     * Perkirakan toleransi otomatis dari kontras lokal 9x9 di sekitar seed:
     * area datar → toleransi kecil (ketat), area gradasi → lebih longgar.
     * Dipakai mode Otomatis tongkat sihir.
     */
    fun autoTolerance(px: IntArray, w: Int, h: Int, sx: Int, sy: Int): Float {
        if (px.size < w * h) return 40f
        val seed = px[sy.coerceIn(0, h - 1) * w + sx.coerceIn(0, w - 1)]
        val sr = ((seed shr 16) and 0xFF).toFloat()
        val sg = ((seed shr 8) and 0xFF).toFloat()
        val sb = (seed and 0xFF).toFloat()
        var maxD2 = 0f
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = (sx + dx).coerceIn(0, w - 1)
                val y = (sy + dy).coerceIn(0, h - 1)
                val p = px[y * w + x]
                val dr = (((p shr 16) and 0xFF).toFloat() - sr)
                val dg = (((p shr 8) and 0xFF).toFloat() - sg)
                val db = ((p and 0xFF).toFloat() - sb)
                val d2 = dr * dr + dg * dg + db * db
                if (d2 > maxD2) maxD2 = d2
            }
        }
        return (kotlin.math.sqrt(maxD2) * 1.5f + 12f).coerceIn(18f, 110f)
    }

    /**
     * Telusur batas mask biner jadi Path (marching squares per sel,
     * tiap segmen jadi sub-path sendiri — cukup untuk overlay marching-ants,
     * union, dan uji Region). Koordinat = tepi piksel persis.
     */
    private fun traceContour(
        mask: BooleanArray,
        w: Int,
        h: Int,
        scale: Float = 1f
    ): Path? {
        val path = Path()
        var segs = 0
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(x1 * scale, y1 * scale)
            path.lineTo(x2 * scale, y2 * scale)
            segs++
        }
        for (y in 0 until h - 1) {
            val r0 = y * w
            for (x in 0 until w - 1) {
                val tl = mask[r0 + x]
                val tr = mask[r0 + x + 1]
                val br = mask[r0 + x + 1 + w]
                val bl = mask[r0 + x + w]
                val idx = (if (tl) 1 else 0) or (if (tr) 2 else 0) or
                    (if (br) 4 else 0) or (if (bl) 8 else 0)
                val fx = x.toFloat()
                val fy = y.toFloat()
                when (idx) {
                    1 -> seg(fx + 0.5f, fy, fx, fy + 0.5f)
                    2 -> seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                    3 -> seg(fx, fy + 0.5f, fx + 1f, fy + 0.5f)
                    4 -> seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    5 -> {
                        seg(fx + 0.5f, fy, fx, fy + 0.5f)
                        seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    }
                    6 -> seg(fx + 0.5f, fy, fx + 0.5f, fy + 1f)
                    7 -> seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    8 -> seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    9 -> seg(fx + 0.5f, fy, fx + 0.5f, fy + 1f)
                    10 -> {
                        seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                        seg(fx, fy + 0.5f, fx + 0.5f, fy + 1f)
                    }
                    11 -> seg(fx + 1f, fy + 0.5f, fx + 0.5f, fy + 1f)
                    12 -> seg(fx, fy + 0.5f, fx + 1f, fy + 0.5f)
                    13 -> seg(fx + 0.5f, fy, fx + 1f, fy + 0.5f)
                    14 -> seg(fx + 0.5f, fy, fx, fy + 0.5f)
                }
            }
        }
        return if (segs == 0) null else path
    }

    /**
     * Tambah kotak sebagai SATU area baru (drag menumpuk, multi-seleksi).
     * Koordinat dinormalisasi + dijepit ke kanvas. Dipakai untuk tambah
     * bubble manual dan seleksi area cepat via drag.
     */
    fun selectRect(rect: RectF) {
        val left = minOf(rect.left, rect.right).coerceIn(0f, width.toFloat())
        val top = minOf(rect.top, rect.bottom).coerceIn(0f, height.toFloat())
        val right = maxOf(rect.left, rect.right).coerceIn(0f, width.toFloat())
        val bottom = maxOf(rect.top, rect.bottom).coerceIn(0f, height.toFloat())
        if (right - left < 2f || bottom - top < 2f) return
        val box = Path()
        box.addRect(left, top, right, bottom, Path.Direction.CW)
        addRegion(box)
    }

    private fun updateMaskFromPath() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        maskCanvas.drawPath(selectionPath, fillPaint)
    }

    fun invertSelection() {
        if (!hasSelection) return
        // Alokasi 720x16000 = 46MB sementara, langsung recycle setelah salin.
        val invertedBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val invCanvas = Canvas(invertedBmp)
            invCanvas.drawColor(Color.WHITE)

            val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            invCanvas.drawPath(selectionPath, erasePaint)

            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            maskCanvas.drawBitmap(invertedBmp, 0f, 0f, null)

            val rectPath = Path()
            rectPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            rectPath.op(selectionPath, Path.Op.DIFFERENCE)
            selectionPath.set(rectPath)
            // Sinkronkan daftar region agar tambah/hapus berikutnya tidak
            // menghidupkan kembali bentuk sebelum invert.
            regions.clear()
            regionBounds.clear()
            val single = Path(selectionPath)
            val b = RectF()
            single.computeBounds(b, true)
            if (!b.isEmpty) {
                regions.add(single)
                regionBounds.add(b)
            }
            selectionCount = regions.size
        } finally {
            runCatching { invertedBmp.recycle() }
        }
    }

    fun clearSelectedArea(layer: DrawingLayer) {
        if (!hasSelection) return
        val bmp = layer.getBitmap()
        val canvas = Canvas(bmp)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, clearPaint)
        layer.markDirty()
    }

    fun copySelectedArea(layer: DrawingLayer): Bitmap? {
        if (!hasSelection) return null
        val layerBmp = layer.getBitmap()
        val copyBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(copyBmp)
        canvas.drawBitmap(layerBmp, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(selectionMaskBitmap, 0f, 0f, maskPaint)
        clipboardBitmap = copyBmp
        return copyBmp
    }

    fun cutSelectedArea(layer: DrawingLayer): Bitmap? {
        val copied = copySelectedArea(layer)
        if (copied != null) {
            clearSelectedArea(layer)
        }
        return copied
    }

    fun pasteToNewLayer(layerManager: LayerManager): DrawingLayer? {
        val clip = clipboardBitmap ?: return null
        val newLayer = layerManager.addLayer("Pasted Layer")
        val layerBmp = newLayer.getBitmap()
        val canvas = Canvas(layerBmp)
        canvas.drawBitmap(clip, 0f, 0f, null)
        newLayer.markDirty()
        return newLayer
    }
}
