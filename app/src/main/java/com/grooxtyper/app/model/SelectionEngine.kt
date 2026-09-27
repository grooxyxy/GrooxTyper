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
     * Magic Wand: banjir (flood fill) warna mirip dari titik seed, lalu
     * batasnya ditelusur jadi Path (marching squares) dan ditambah sebagai
     * SATU area baru. Konektivitas 4-arah (lebih ketat, tidak bocor lewat
     * diagonal celah line-art).
     *
     * @param px piksel ARGB baris-mayor selebar [w].
     * @param maxDist jarak Euclidean RGB maksimum dari warna seed.
     * @return true bila satu area berhasil ditambah.
     */
    fun selectWand(
        px: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        maxDist: Float
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
        val path = traceContour(mask, w, h) ?: return false
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
        // Downsample area raksasa (batas 1.5MP) agar erosi+BFS tetap cepat.
        var scale = 1f
        var dat = src
        var w = bw
        var h = bh
        if (bw.toLong() * bh > 1_500_000L) {
            scale = 0.5f
            w = maxOf(16, (bw * scale).toInt())
            h = maxOf(16, (bh * scale).toInt())
            dat = downsampleGray(src, bw, bh, w, h)
        }
        // Mask interior kertas (teks & garis outline = gelap, dikecualikan).
        val mask = BooleanArray(w * h)
        var maskCount = 0
        for (i in 0 until w * h) {
            val p = dat[i]
            val m = minOf(
                (p shr 16) and 0xFF,
                (p shr 8) and 0xFF,
                p and 0xFF
            )
            if (m > 200) {
                mask[i] = true
                maskCount++
            }
        }
        if (maskCount < 200) return null
        // 1) KONTRAKSI: erosi 8-neighborhood sampai mask terpisah ≥2.
        var cur = mask
        var labels: IntArray? = null
        var numLabels = 0
        // Iterasi dibatasi: leher tipis pecah dalam puluhan iterasi;
        // lebih dari itu berarti satu blob (berhenti, jangan habiskan CPU).
        val maxIter = minOf(200, minOf(w, h) / 2)
        var iter = 0
        var split = false
        while (iter < maxIter) {
            val next = BooleanArray(w * h)
            var any = false
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (!cur[i]) continue
                    var all = true
                    outer@ for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until w || ny !in 0 until h || !cur[ny * w + nx]) {
                                all = false
                                break@outer
                            }
                        }
                    }
                    if (all) {
                        next[i] = true
                        any = true
                    }
                }
            }
            if (!any) return null // habis sebelum terpisah = satu blob utuh
            val (lab, cnt) = labelComponents(next, w, h)
            if (cnt >= 2) {
                labels = lab
                numLabels = cnt
                split = true
                break
            }
            cur = next
            iter++
        }
        val seedLabels = labels ?: return null
        if (!split || numLabels < 2) return null
        // 2) WATERSHED: tumbuhkan semua seed serentak di dalam mask asli.
        // Tiap piksel diklaim seed yang pertama sampai (BFS lapis); garis
        // tempat dua front bertemu = titik terakhir bubble bersentuhan.
        val final = IntArray(w * h)
        val queue = ArrayDeque<Int>()
        for (i in 0 until w * h) {
            if (seedLabels[i] > 0 && mask[i]) {
                final[i] = seedLabels[i]
                queue.addLast(i)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            val lab = final[i]
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (mask[j] && final[j] == 0) {
                        final[j] = lab
                        queue.addLast(j)
                    }
                }
            }
        }
        // Ambil 2 label terbesar; tiap-tiapnya wajib >5% isi (bukan noise).
        val counts = IntArray(numLabels + 1)
        for (v in final) if (v in 1..numLabels) counts[v]++
        val top = (1..numLabels).sortedByDescending { counts[it] }.take(2)
        if (top.size < 2 || counts[top[1]] * 20 < maskCount) return null
        // Bbox tiap label + pad, kembali ke koordinat kanvas penuh.
        fun labelBox(id: Int): RectF? {
            var l = w
            var t = h
            var r = -1
            var b = -1
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (final[y * w + x] != id) continue
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
            if (r < 0 || r - l < 8 || b - t < 8) return null
            val pad = 4
            return RectF(
                (offX + (l - pad) / scale).toFloat(),
                (offY + (t - pad) / scale).toFloat(),
                (offX + (r + pad) / scale).toFloat(),
                (offY + (b + pad) / scale).toFloat()
            )
        }
        val a = labelBox(top[0])
        val c = labelBox(top[1])
        if (a == null || c == null) return null
        return listOf(a, c)
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
    private fun traceContour(mask: BooleanArray, w: Int, h: Int): Path? {
        val path = Path()
        var segs = 0
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
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
