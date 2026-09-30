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

    // ------------------------------------------------------------------
    // Area bubble (hasil pipeline wand). Dipisahkan dari seleksi klasik
    // (Oval/Rectangle/Lasso) supaya yang satu tak merusak yang lain: area
    // bubble punya nomor, boleh banyak, dan punya jenis (bubble atau panel).
    // ------------------------------------------------------------------

    /** Daftar area bubble; state supaya overlay ikut digambar ulang. */
    var bubbleAreaList by mutableStateOf(listOf<BubbleAreaPipeline.Area>())
        private set

    /**
     * Teks dan layer tiap area, dikunci dengan "kunci area" (kotak batas
     * dibulatkan). memakai daftar paralel memakai indeks urutan penambahan,
     * sedangkan penomoran memakai urutan baca: begitu user mengetuk area bawah
     * dulu lalu area atas, isinya tidak lagi sejajar. Kunci kotak tidak
     * berubah saat urutan dihitung ulang.
     */
    private val bubbleAreaTexts = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val bubbleAreaLayers = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Kunci stabil sebuah area (kotak batas dalam piksel bulat). */
    fun bubbleAreaKey(area: BubbleAreaPipeline.Area): String {
        val r = area.bounds
        return "${r.left.toInt()},${r.top.toInt()},${r.right.toInt()},${r.bottom.toInt()}"
    }

    /** Teks tiap area dalam urutan baca manga (kosong bila belum terisi). */
    fun bubbleAreaTextsInReadingOrder(): List<String> =
        bubbleAreasInReadingOrder().map { bubbleAreaTexts[bubbleAreaKey(it)] ?: "" }

    /** ID layer teks yang dibuat untuk sebuah area ("" bila belum diisi). */
    fun bubbleAreaLayerId(area: BubbleAreaPipeline.Area): String =
        bubbleAreaLayers[bubbleAreaKey(area)] ?: ""

    /** Catat teks + layer untuk sebuah area (dipanggil setelah layer dibuat). */
    fun setBubbleAreaContent(area: BubbleAreaPipeline.Area, text: String, layerId: String) {
        val k = bubbleAreaKey(area)
        bubbleAreaTexts[k] = text
        bubbleAreaLayers[k] = layerId
    }

    /** Kosongkan catatan sebuah area (dipakai sebelum isi ulang). */
    fun clearBubbleAreaContent(area: BubbleAreaPipeline.Area) {
        val k = bubbleAreaKey(area)
        bubbleAreaTexts.remove(k)
        bubbleAreaLayers.remove(k)
    }

    /** Tambah satu area hasil ketukan wand; mengembalikan nomor urut (1..n). */
    fun addBubbleArea(area: BubbleAreaPipeline.Area): Int {
        bubbleAreaList = bubbleAreaList + area
        return bubbleAreasInReadingOrder().indexOf(area) + 1
    }

    /** Area dalam urutan baca manga: baris atas lebih dulu, kiri ke kanan. */
    fun bubbleAreasInReadingOrder(): List<BubbleAreaPipeline.Area> {
        val list = bubbleAreaList
        if (list.size <= 1) return list
        val rows = mutableListOf<MutableList<BubbleAreaPipeline.Area>>()
        for (a in list.sortedBy { it.bounds.centerY() }) {
            val row = rows.find { r ->
                val h = kotlin.math.min(r[0].bounds.height(), a.bounds.height())
                kotlin.math.abs(r[0].bounds.centerY() - a.bounds.centerY()) < h * 0.6f
            }
            if (row != null) row.add(a) else rows.add(mutableListOf(a))
        }
        return rows.flatMap { it.sortedByDescending { area -> area.bounds.centerX() } }
    }

    /**
     * Hapus area yang tertimpa titik (x, y).
     *
     * Memakai PATH, bukan kotak pembatas: area gelembung bisa berupa L-shape
     * atau diagonal, dan ketuk di pojok kotak yang berada di luar bentuk tak
     * boleh menghapus area.
     */
    fun removeBubbleAreaAt(x: Float, y: Float): Boolean {
        val list = bubbleAreaList
        for (i in list.indices.reversed()) {
            val a = list[i]
            // Path.contains(float,float) tak tersedia di permukaan API yang
            // dipakai build ini; pakai Region seperti removeRegionAt di atas.
            val hit = if (a.bounds.contains(x, y)) {
                val reg = android.graphics.Region()
                reg.setPath(a.path, fullClip)
                reg.contains(x.toInt(), y.toInt())
            } else false
            if (hit) {
                clearBubbleAreaContent(a)
                bubbleAreaList = list.toMutableList().also { it.removeAt(i) }
                return true
            }
        }
        return false
    }

    /** Buang semua area bubble (bukan seleksi klasik). */
    fun clearBubbleAreas() {
        bubbleAreaList = emptyList()
        bubbleAreaTexts.clear()
        bubbleAreaLayers.clear()
    }

    
    
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
            val out = downsampleGrayImpl(px, w, h, dw, dh)
            return WandSample(out, dw, dh, 1f / s)
        }

        fun downsampleGrayImpl(
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
    /**
     * Tongkat sihir (mode normal).
     *
     * Seluruh isi algoritma pindah ke [WandEngine]: span scanline flood fill
     * dengan stack IntArray, metrik Chebyshev di ruang linear-light, dan ramp
     * anti-alias GIMP (`aa = 1.5 - d/threshold`) yang menulis coverage
     * 0..255. Ini menggantikan versi lama yang: (a) memakai
     * `ArrayDeque<Int>` sehingga membungkus tiap piksel menjadi Integer,
     * (b) membandingkan jarak Euclidean di ruang sRGB, (c) menyimpan mask
     * boolean sehingga tepi berantian jadi bergerigi.
     *
     * Parameter baru punya nilai default yang mempertahankan perilaku lama
     * (ambang = jarak RGB maksimum lewat maxDist), sehingga pemanggil yang
     * tak menyetel apa-apa tetap jalan.
     */
    fun selectWand(
        px: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        maxDist: Float,
        outScale: Float = 1f,
        params: WandEngine.Params? = null
    ): Boolean {
        if (sx !in 0 until w || sy !in 0 until h) return false
        if (px.size < w * h) return false
        // Ambang UI dihitung dari jarak RGB; mesin baru membandingkan jarak
        // linear-light, jadi ambangnya dikonversi lewat helper yang memakai
        // nilai seed (bukan faktor tetap) agar rasa penggeser tak berubah.
        val p = params ?: WandEngine.Params(
            threshold = WandEngine.thresholdForSrgb(maxDist, px[sy * w + sx])
        )
        val mask = WandEngine.flood(px, w, h, sx, sy, p) ?: return false
        if (mask.pixels < 4) return false
        val solid = mask.toBinary()
        var count = 0
        for (b in solid) if (b) count++
        if (count < 4) return false
        // [MaskContour], bukan traceContour lama: yang lama menulis tiap ruas
        // marching squares sebagai moveTo+lineTo sehingga terbentuk ribuan
        // sub-path terpisah yang tak bisa di-isi. Seleksi jadi praktis kosong
        // meski flood-nya benar.
        val path = MaskContour.build(solid, w, h, outScale) ?: return false
        addRegion(path)
        return true
    }

    /**
     * Tongkat sihir klasik pada SUATU JENDELA (lihat [WandWindow]).
     *
     * Sama dengan [selectWand] tetapi piksel yang diberi sudah potongan
     * jendela, bukan seluruh halaman, dan ada offset supaya Path hasilnya
     * kembali ke koordinat kanvas penuh. Beda ini yang memperbaiki kanvas
     * webtoon:_downsample_ 480px wrecked gelembung di halaman 16000px.
     */
    fun selectWandWindowed(
        px: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        params: WandEngine.Params,
        outScale: Float = 1f,
        offX: Float = 0f,
        offY: Float = 0f
    ): Boolean {
        if (sx !in 0 until w || sy !in 0 until h) return false
        if (px.size < w * h) return false
        val mask = WandEngine.flood(px, w, h, sx, sy, params) ?: return false
        if (mask.pixels < 4) return false
        val solid = mask.toBinary()
        var count = 0
        for (b in solid) if (b) count++
        if (count < 4) return false
                // PENTING: path harus dari [MaskContour], bukan traceContour lama.
        // Yang lama menulis tiap ruas marching squares sebagai moveTo+lineTo
        // sehingga terbentuk ribuan sub-path terpisah yang tak bisa di-isi;
        // seleksi jadi praktis kosong meski flood-nya benar.
        val path = MaskContour.build(solid, w, h, outScale, offX, offY) ?: return false
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

    /**
     * Inti watershed (dipisah agar OOM/exception tertangani di pemanggil).
     *
     * Sekarang memakai [WandEngine.splitBubbles]: distance transform ->
     * puncak (jarak >= 0.7 * max) -> label marker -> watershed multi-sumber.
     * Versi lama melakukan pencarian biner radius erosi dan memaksa tepat 2
     * hasil, sehingga tiga bubble yang menyatu mustahil dipisah dan gelembung
     * kecil ikut terambil sebagai seed.
     */
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
        val w: Int
        val h: Int
        val dat: IntArray
        if (bw.toLong() * bh > 1_200_000L) {
            scale = 0.5f
            w = maxOf(16, (bw * scale).toInt())
            h = maxOf(16, (bh * scale).toInt())
            dat = downsampleGray(src, bw, bh, w, h)
        } else {
            scale = 1f
            w = bw
            h = bh
            dat = src
        }
        // 2) Mask interior kertas (teks & garis outline = gelap, dikecualikan).
        val binary = BooleanArray(w * h)
        var maskCount = 0
        for (i in 0 until w * h) {
            val p = dat[i]
            val m = minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            if (m > 195) {
                binary[i] = true
                maskCount++
            }
        }
        if (maskCount < 120) return null
        // 3) Watershed multi-sumber -> kotak tiap gelembung (kanvas penuh).
        val parts = WandEngine.splitBubbles(WandEngine.maskFromBinary(binary, w, h))
        if (parts.size < 2) return null
        val pad = 4f
        return parts.map { r ->
            RectF(
                offX + (r.left - pad) / scale,
                offY + (r.top - pad) / scale,
                offX + (r.right + pad) / scale,
                offY + (r.bottom + pad) / scale
            )
        }
    }

    /**
     * Turunkan ukuran area (rata-rata blok) sebelum watershed pada kanvas
     * raksasa. Memakai helper statis yang sama dengan versi wand supaya
     * hasil downsample konsisten.
     */
    private fun downsampleGray(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray =
        downsampleGrayImpl(src, sw, sh, dw, dh)

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
    ): Path? = traceContourOffset(mask, w, h, scale, 0f, 0f)

    /**
     * Kontur dengan offset. Offset diperlukan untuk wand berjendela: piksel
     * berasal dari potongan jendela, jadi koordinatnya harus digeser lagi
     * sebelum jadi Path di kanvas penuh.
     */
    private fun traceContourOffset(
        mask: BooleanArray,
        w: Int,
        h: Int,
        scale: Float = 1f,
        offX: Float = 0f,
        offY: Float = 0f
    ): Path? {
        val path = Path()
        var segs = 0
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(offX + x1 * scale, offY + y1 * scale)
            path.lineTo(offX + x2 * scale, offY + y2 * scale)
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
