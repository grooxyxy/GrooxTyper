package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesin kuas SFX BERGENRE: empat gaya yang bentuknya benar-benar berbeda satu
 * sama lain (bukan tujuh kuas yang hanya beda tekstur stamp):
 *
 *  - [Genre.HORROR]  siluet bergerigi tajam + tetesan tinta yang menggantung
 *    di tepi bawah, kasar kuat.
 *  - [Genre.ROMANCE] pita lebar yang lembut, tepi nyaris licin, ujung bulat.
 *  - [Genre.ACTION]  lebar berepersi (sudut runcing) + garis kecepatan tipis
 *    yang mengekor di belakang sapuan.
 *  - [Genre.FANTASY] lebar bergelombang + kilau bintang di sepanjang goresan.
 *
 * Semuanya memakai satu teknik yang sama dan sudah diuji: **pita (ribbon)
 * dengan profil half-width yang di-sample pada panjang busur** (lihat
 * [SfxInk]) sehingga tekstur tidak "berenang" saat goresan diputar, dan tiap
 * goresan dirender utuh saat selesai sehingga **outline, bayangan, dan gradasi
 * bisa ditumpuk dengan urutan benar** (bayangan -> outline -> tinta).
 *
 * Semua parameter tampilan (gradasi, opacity, outline, shadow, tekstur) ada di
 * [Settings] dan bisa diubah user dari panel. Mesin tidak butuh preview
 * realtime: nilai yang baru diset langsung dipakai pada render berikutnya.
 */
object GenreBrushEngine {

    /** Empat genre SFX. Nama ditampilkan apa adanya di daftar kuas. */
    enum class Genre(val displayName: String) {
        HORROR("SFX Horror"),
        ROMANCE("SFX Romance"),
        ACTION("SFX Action"),
        FANTASY("SFX Fantasy");

        companion object {
            fun of(brushType: BrushType): Genre? = when (brushType) {
                BrushType.GENRE_HORROR -> HORROR
                BrushType.GENRE_ROMANCE -> ROMANCE
                BrushType.GENRE_ACTION -> ACTION
                BrushType.GENRE_FANTASY -> FANTASY
                else -> null
            }
        }
    }

    /**
     * Pengaturan tampilan satu genre. Semua nilai bisa diubah user; bawaan
     * tiap genre sudah berbeda sehingga hasilnya langsung terlihat berbeda
     * sejak dipakai tanpa harus menggodoreng setelan dulu.
     */
    class Settings() {
        /** Pengali lebar (1 = lebar kuas apa adanya). */
        var widthMul by mutableFloatStateOf(1f)
        /** Isi pakai gradasi; arahnya ditentukan [gradAngle]. */
        var gradient by mutableStateOf(false)
        var gradStart by mutableStateOf(Color.BLACK)
        var gradEnd by mutableStateOf(Color.WHITE)
        var gradAngle by mutableFloatStateOf(90f)
        /** Opacity 0..1 untuk seluruh goresan. */
        var opacity by mutableFloatStateOf(1f)
        /** Outline di sekeliling goresan (fraksi lebar kuas, 0 = mati). */
        var outlineWidth by mutableFloatStateOf(0f)
        var outlineColor by mutableStateOf(Color.WHITE)
        /** Warna outline lapis dalam (terang), setengah lebar lapis luar. */
        var outlineInnerColor by mutableStateOf(Color.WHITE)
        /** Bayangan: geseran (px kanvas) + radius blur. */
        var shadowOn by mutableStateOf(false)
        var shadowDx by mutableFloatStateOf(3f)
        var shadowDy by mutableFloatStateOf(4f)
        var shadowBlur by mutableFloatStateOf(6f)
        var shadowColor by mutableStateOf(0x66000000)
        /** Tekstur 0..1: seberapa bergerigi tepi (0 = licin, 1 = sangat kasar). */
        var texture by mutableFloatStateOf(0.5f)
        /** Percikan kecil di sekitar goresan. */
        var spatter by mutableIntStateOf(0)
        /**
         * Kekuatan grain bertekstur 0..1, dibaca dari preset [SfxStyleSpec].
         * 0 berarti langkah tinta digambar polos seperti biasa; di atas 0 mesin
         * menumpuk satu langkah bertekstur (Ink API) di atas tinta itu.
         */
        var grain by mutableFloatStateOf(0f)
        /** Jenis grain; ikut diubah oleh [applyStyle] dari preset. */
        var textureKind by mutableStateOf(SfxTextureBrush.Texture.CRUNCH)
        /**
         * Preset gaya yang terakhir dipakai (null = gaya manual penuh).
         * Disimpan supaya panel bisa menampilkan angka preset asli ketika
         * pengali lebar berubah, dan tombol "Bawaan" bisa memulihkannya.
         */
        var styleRef: SfxStyleSpec? = null

        fun copyFrom(o: Settings) {
            widthMul = o.widthMul; gradient = o.gradient
            gradStart = o.gradStart; gradEnd = o.gradEnd; gradAngle = o.gradAngle
            opacity = o.opacity
            outlineWidth = o.outlineWidth; outlineColor = o.outlineColor
            outlineInnerColor = o.outlineInnerColor
            shadowOn = o.shadowOn; shadowDx = o.shadowDx; shadowDy = o.shadowDy
            shadowBlur = o.shadowBlur; shadowColor = o.shadowColor
            texture = o.texture; spatter = o.spatter
            grain = o.grain; textureKind = o.textureKind
            styleRef = o.styleRef
        }

        /** Bawaan per genre: profil lebar + warna + tekstur sudah beda. */
        /**
         * Bawaan tiap genre diambil dari preset bersama [SfxStyleSpec], bukan
         * angka yang ditulis di sini. Jadi gaya kuas dan gaya TEKS SFX selalu
         * satu bahasa visual. Angka dan sumbernya ada di
         * `docs/sfx-lettering-research.md`.
         *
         * `outlineWidth` dan `shadowDx/Dy` di Settings adalah fraksi LEBAR
         * KUAS (bukan tinggi huruf), jadi angka preset dikonversi sekali di
         * sini lewat [applyStyle]. Bayangan preset sengaja blur 0: soft drop
         * shadow ditolak oleh letterer profesional, dan rujukan `c2.webp`
         * memakai geseran keras.
         */
        fun applyPreset(genre: Genre) {
            applyStyle(SfxStyleSpec.presetOf(SfxGenre.of(genre)))
            // Lebar sapuan per genre: horror lebar, romance paling lebar,
            // action ramping supaya ujungnya runcing.
            widthMul = when (genre) {
                Genre.HORROR -> 1.15f
                Genre.ROMANCE -> 1.3f
                Genre.ACTION -> 0.95f
                Genre.FANTASY -> 1.1f
            }
            opacity = if (genre == Genre.ROMANCE) 0.95f else 1f
        }

        /** Terapkan preset gaya bersama ke setelan kuas ini. */
        fun applyStyle(spec: SfxStyleSpec) {
            gradient = true
            gradStart = spec.gradStart
            gradEnd = spec.gradEnd
            gradAngle = spec.gradAngle
            outlineColor = spec.outlineColor
            outlineInnerColor = spec.outlineInnerColor
            shadowOn = spec.shadowBlur == 0f &&
                (spec.shadowDx != 0f || spec.shadowDy != 0f)
            shadowColor = spec.shadowColor
            texture = spec.roughness
            spatter = spec.spatter
            grain = spec.grain
            textureKind = spec.texture
            // Semua ukuran disimpan sebagai RASIO terhadap ukuran kuas (sama
            // seperti outlineWidth), lalu dikalikan ukuran kuas saat render.
            // Dulu outlineWidth dibagi inkScale sehingga jadi 0.34/0.065 = 5.2
            // (outline 17x lebih tebal dari seharusnya) dan bayangan yang
            // disimpan sebagai fraksi justru digambar sebagai piksel mentah
            // (geserannya jadi tak terlihat).
            styleRef = spec
            outlineWidth = spec.outlineScale
            shadowDx = spec.shadowDx
            shadowDy = spec.shadowDy
        }
    }

    /** Pemegang setelan keempat genre (dipakai panel dan engine). */
    class SettingsStore() {
        private val map = HashMap<Genre, Settings>()

        fun of(genre: Genre): Settings = map.getOrPut(genre) {
            Settings().apply { applyPreset(genre) }
        }

        fun resetAll() {
            map.clear()
        }
    }

    // ------------------------------------------------------------------
    // Profil per genre (ini yang bikin ke-empatnya jelas berbeda).
    // ------------------------------------------------------------------

    /**
     * Pengali lebar pada posisi [t] (0..1 sepanjang goresan).
     * [jitter] menambah denyut acak kecil supaya lebarnya tidak persis
     * simetris antar goresan.
     */
    private fun widthFactor(genre: Genre, t: Float, jitter: Float): Float =
        when (genre) {
            Genre.HORROR -> {
                // Gigi kasar: gelombang segitiga cepat + denyut acak. Selalu
                // di atas 0.55 supaya goresan tidak pernah putus.
                val tri = abs(((t * 9f) % 1f) * 2f - 1f)
                0.85f + 0.55f * tri + 0.18f * jitter
            }
            Genre.ROMANCE -> {
                // Lembut: sedikit denyut sinus, ujung mengembang.
                0.95f + 0.16f * max(0f, sin(t * PI.toFloat()))
            }
            Genre.ACTION -> {
                // Persegi: melebar/tipis mendadak tiap seperlima goresan
                // sehingga sudutnya runcing seperti glyph komik.
                val step = (t * 5f) % 1f
                (if (step < 0.45f) 1f else 0.55f) + 0.10f * jitter
            }
            Genre.FANTASY -> {
                // Gelombang halus + denyut lambat.
                0.92f + 0.22f * sin(t * 9f + 0.7f) + 0.10f * sin(t * 23f)
            }
        }

    /** Kasar tepi per genre dalam fraksi half-width. */
    private fun roughnessOf(genre: Genre, texture: Float): Float = when (genre) {
        Genre.HORROR -> 0.16f + 0.42f * texture
        Genre.ROMANCE -> 0.02f + 0.08f * texture
        Genre.ACTION -> 0.05f + 0.16f * texture
        Genre.FANTASY -> 0.07f + 0.24f * texture
    }

    /** Jumlah hiasan khas tiap genre (0 = tanpa). */
    private fun extrasCount(genre: Genre, texture: Float): Int = when (genre) {
        Genre.HORROR -> 3 + (texture * 12f).toInt()
        Genre.ACTION -> 3
        Genre.FANTASY -> 2 + (texture * 6f).toInt()
        Genre.ROMANCE -> 0
    }

    // ------------------------------------------------------------------
    // Goresan berjalan.
    // ------------------------------------------------------------------

    /**
     * Satu goresan yang sedang digambar: titik terkumpul di buffer, isi
     * digambar langsung biar responsif, lalu saat stroke selesai seluruh
     * goresan dirender ulang berlapis (bayangan -> outline -> tinta) supaya
     * urutannya benar dan gradasi ikut terbaca.
     */
    class Stroke(private val store: SettingsStore) {
        private val xs = ArrayList<Float>(256)
        private val ys = ArrayList<Float>(256)
        private val arc = FloatArray(MAX_PTS)
        private var nPts = 0
        private var length = 0f

        private var genre: Genre = Genre.HORROR
        private var size = 10f
        private var inkColor = Color.BLACK
        private var alphaLocked = false
        private var active = false
        private var seed = 1L

        fun isActive(): Boolean = active && nPts > 0

        /**
         * True bila goresan masih perlu dirender utuh saat ditutup. Butuh
         * minimal 2 titik: di kanvas jumbo (>4MP) BrushEngine menggambar satu
         * path polos per segmen dan tak pernah memanggil [push], jadi goresan
         * hanya punya 1 titik dan tidak boleh dirender ulang (nanti muncul
         * titik nyasar di posisi awal).
         */
        fun hasTail(): Boolean = active && nPts >= 2

        fun begin(start: Offset, g: Genre, col: Int, brushSize: Float, alphaLockedLayer: Boolean) {
            xs.clear(); ys.clear(); nPts = 0; length = 0f
            genre = g
            size = max(1f, brushSize)
            inkColor = col
            alphaLocked = alphaLockedLayer
            seed = (abs(start.x.toInt() * 31 + start.y.toInt() * 17) + 1).toLong()
            active = true
            xs.add(start.x); ys.add(start.y)
            arc[0] = 0f; nPts = 1
        }

        fun discard() {
            xs.clear(); ys.clear(); nPts = 0; length = 0f
            active = false
        }

        /** Titik tunggal (tap): jadi goresan pendek, bukan hilang. */
        fun dot(canvas: Canvas, p: Offset) {
            if (nPts >= 1) {
                if (nPts >= MAX_PTS) compactPoints()
                xs.add(p.x); ys.add(p.y)
                length = sqrt((p.x - xs[0]) * (p.x - xs[0]) + (p.y - ys[0]) * (p.y - ys[0]))
                arc[nPts] = length
                nPts++
            }
            renderFinal(canvas)
            discard()
        }

        /**
         * Tambah titik hasil interpolasi kurva TANPA menggambar apa pun.
         *
         * Dipakai di kanvas besar: per_segmen, pita SFX digambar ulang sebagai
         * lusta path kecil, jadi biayanya terlalu mahal. Titik tetap dikumpulkan di
         * sini, lalu [renderFinal] menggambar goresan utuh (bayangan, outline,
         * gradasi, pori) sekali saat jari lifted. Hasil akhirnya sama dengan
         * kanvas biasa, hanya selesai sedikit lebih lambat.
         */
        fun pushNoLive(pts: List<Offset>) {
            if (!active) return
            if (nPts == 0) {
                val f = pts.firstOrNull() ?: return
                xs.add(f.x); ys.add(f.y); arc[0] = 0f; nPts = 1
            }
            for (k in 1 until pts.size) {
                val p = pts[k]
                if (nPts >= MAX_PTS) compactPoints()
                val lx = xs[nPts - 1]
                val ly = ys[nPts - 1]
                val dx = p.x - lx
                val dy = p.y - ly
                val d = sqrt(dx * dx + dy * dy)
                if (d < 0.4f) continue
                xs.add(p.x); ys.add(p.y)
                length += d
                arc[nPts] = length
                nPts++
            }
        }

        /** Tambah titik hasil interpolasi kurva, lalu gambar isi goresan. */
        fun push(canvas: Canvas, pts: List<Offset>, speed: Float) {
            if (!active) return
            if (nPts == 0) {
                val f = pts.firstOrNull() ?: return
                xs.add(f.x); ys.add(f.y); arc[0] = 0f; nPts = 1
            }
            for (k in 1 until pts.size) {
                val p = pts[k]
                if (nPts >= MAX_PTS) compactPoints()
                val lx = xs[nPts - 1]
                val ly = ys[nPts - 1]
                val dx = p.x - lx
                val dy = p.y - ly
                val d = sqrt(dx * dx + dy * dy)
                if (d < 0.4f) continue
                xs.add(p.x); ys.add(p.y)
                length += d
                arc[nPts] = length
                nPts++
            }
            paintInkLive(canvas, speed)
        }

        /** Buang setiap titik ganjil: panjang busur ikut dihitung ulang. */
        private fun compactPoints() {
            var w = 0
            var i = 0
            while (i < nPts) {
                xs[w] = xs[i]; ys[w] = ys[i]
                w++
                i += 2
            }
            nPts = w
            var acc = 0f
            for (j in 1 until nPts) {
                val dx = xs[j] - xs[j - 1]
                val dy = ys[j] - ys[j - 1]
                acc += sqrt(dx * dx + dy * dy)
                arc[j] = acc
            }
            length = acc
        }

        // ---------------- geometri ----------------

        /** Half-width tiap titik: profil genre + tekstur ber-noise. */
        private fun halfWidths(extra: Float): FloatArray {
            val st = store.of(genre)
            val out = FloatArray(nPts)
            val base = size * 0.5f * st.widthMul.coerceIn(0.2f, 4f)
            val rough = roughnessOf(genre, st.texture.coerceIn(0f, 1f))
            // Panjang busur per indeks profil: noise di-sample pada jarak
            // busur, bukan indeks titik (tidak "berenang" saat goresan memutar).
            val profSize = (nPts + 8).coerceIn(8, 4096)
            val ds = if (nPts > 1) max(0.4f, length / profSize) else 1f
            val teeth = max(1, (size * 0.35f / ds).toInt())
            val prof = SfxInk.EdgeProfile.build(profSize, seed xor 0x5DEECE66DL, teeth)
            val invLen = if (length > 0.5f) 1f / length else 0f
            val rnd = SfxInk.XorShift64(seed xor 0x9E3779B9L)
            for (i in 0 until nPts) {
                val t = arc[i] * invLen
                // Ujung meruncing halus di 3% awal dan 6% akhir.
                val taper = if (t < 0.03f) 0.45f + 0.55f * (t / 0.03f)
                else if (t > 0.94f) 0.35f + 0.65f * ((1f - t) / 0.06f)
                else 1f
                val w = base * widthFactor(genre, t, rnd.nextFloat()) * taper
                val noise = SfxInk.EdgeProfile.sample(prof, arc[i], ds)
                out[i] = max(0.6f, w * (1f + rough * noise) + extra)
            }
            return out
        }

        /** Pita tinta dari seluruh titik goresan (extra menambah setengah lebar). */
        private fun inkPath(extra: Float): Path? {
            if (nPts < 1) return null
            val half = size * 0.5f * store.of(genre).widthMul.coerceIn(0.2f, 4f)
            if (nPts == 1) {
                return Path().apply { addCircle(xs[0], ys[0], max(1f, half + extra), Path.Direction.CW) }
            }
            return SfxInk.strokeRibbon(xsArr(), ysArr(), halfWidths(extra), 0.35f)
        }

        /** Salinan koordinat titik jadi array (dipakai peregang pita). */
        private fun xsArr(): FloatArray {
            val a = FloatArray(nPts)
            for (i in 0 until nPts) a[i] = xs[i]
            return a
        }

        private fun ysArr(): FloatArray {
            val a = FloatArray(nPts)
            for (i in 0 until nPts) a[i] = ys[i]
            return a
        }

        private fun boundsOf(pad: Float): RectF {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in 0 until nPts) {
                if (xs[i] < minX) minX = xs[i]
                if (ys[i] < minY) minY = ys[i]
                if (xs[i] > maxX) maxX = xs[i]
                if (ys[i] > maxY) maxY = ys[i]
            }
            return RectF(minX - pad, minY - pad, maxX + pad, maxY + pad)
        }

        /** Unit normal di titik [i] (arah kiri dari arah sapuan). */
        private fun nx(i: Int): Float {
            val k = if (i == 0) 0 else if (i == nPts - 1) nPts - 2 else i + 1
            val j = if (i == 0) 1 else i
            val dx = xs[j] - xs[k]
            val dy = ys[j] - ys[k]
            val len = sqrt(dx * dx + dy * dy)
            return if (len < 0.001f) 0f else -dy / len
        }

        private fun ny(i: Int): Float {
            val k = if (i == 0) 0 else if (i == nPts - 1) nPts - 2 else i + 1
            val j = if (i == 0) 1 else i
            val dx = xs[j] - xs[k]
            val dy = ys[j] - ys[k]
            val len = sqrt(dx * dx + dy * dy)
            return if (len < 0.001f) 0f else dx / len
        }

        // ---------------- gambar langsung (responsif) ----------------

        /**
         * Gambar isi goresan saat masih berjalan: satu pita per pasangan titik
         * memakai half-width profil. Outline/bayangan sengaja tidak digambar
         * di sini - keduanya dirender utuh di [renderFinal] supaya urutannya
         * benar (dan tidak menimpa tinta).
         */
        private fun paintInkLive(canvas: Canvas, speed: Float) {
            if (nPts < 2) return
            val st = store.of(genre)
            val col = inkColor
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = col
                alpha = (st.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            val base = size * 0.5f * st.widthMul.coerceIn(0.2f, 4f)
            // Sapuan cepat hanya sedikit dikecilkan supaya huruf tetap terbaca.
            val speedK = 1f - 0.25f * speed.coerceIn(0f, 1f)
            val invLen = if (length > 0.5f) 1f / length else 0f
            val path = Path()
            for (i in 1 until nPts) {
                val i0 = i - 1
                val nx0 = nx(i0); val ny0 = ny(i0)
                val nx1 = nx(i); val ny1 = ny(i)
                val w0 = base * widthFactor(genre, arc[i0] * invLen, 0.5f) * speedK
                val w1 = base * widthFactor(genre, arc[i] * invLen, 0.5f) * speedK
                path.rewind()
                path.moveTo(xs[i0] + nx0 * w0, ys[i0] + ny0 * w0)
                path.lineTo(xs[i] + nx1 * w1, ys[i] + ny1 * w1)
                path.lineTo(xs[i] - nx1 * w1, ys[i] - ny1 * w1)
                path.lineTo(xs[i0] - nx0 * w0, ys[i0] - ny0 * w0)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        // ---------------- render akhir berlapis ----------------

        /**
         * Render seluruh goresan: bayangan -> outline -> tinta (solid/gradasi),
         * lalu[DST_IN] dengan pita tinta supaya bayangan/outline hanya ada di
         * LUAR tinta, barulah tinta digambar ulang di atasnya. Urutan ini
         * penting; kalau dibalik, outline menutupi tinta.
         */
        fun renderFinal(canvas: Canvas) {
            val st = store.of(genre)
            if (nPts < 1) return
            val outlineW = st.outlineWidth.coerceAtLeast(0f) * size
            val ink = inkPath(0f) ?: return
            val outline = if (outlineW > 0.5f) inkPath(outlineW) else null
            val shadowOn = st.shadowOn &&
                (st.shadowBlur > 0.5f || abs(st.shadowDx) > 0.001f || abs(st.shadowDy) > 0.001f)
            val extras = extrasCount(genre, st.texture.coerceIn(0f, 1f)) > 0 || st.spatter > 0
            // Grain bertekstur juga butuh jalur berlapis: langkah tinta polos
            // digambar lebih dulu, lalu pori ditumpuk di atasnya. Kalau jalur
            // cepat di atas tetap mengambil cases tanpa grain, pori hilang
            // tepat pada kanvas besar yang paling butuh pori.
            val grainOn = st.grain.coerceIn(0f, 1f) >= 0.02f
            if (!shadowOn && outline == null && !st.gradient && !extras && !grainOn) {
                // Kasus sederhana: tanpa layering -> gambar langsung (cepat).
                canvas.drawPath(ink, fillPaintFor(st, boundsOf(2f)))
                return
            }
            val clip = boundsOf(
                size * st.widthMul.coerceIn(0.2f, 4f) * 1.3f + outlineW * 2f +
                    st.shadowBlur * 2f + abs(st.shadowDx * size) + abs(st.shadowDy * size) + 8f
            )
            val l = clip.left.toInt().coerceAtLeast(0)
            val t = clip.top.toInt().coerceAtLeast(0)
            val r = clip.right.toInt()
            val b = clip.bottom.toInt()
            if (r <= l || b <= t) return
            val w = r - l
            val h = b - t
            // Goresan raksasa (sapu diagonal panjang di kanvas besar) tak boleh
            // dialokasikan lewat: 4096^2 = 64MB akan memicu OOM. Fallback:
            // gambar langsung berurutan (bayangan tanpa kabut -> outline -> tinta).
            if (w > 4096 || h > 4096 || w.toLong() * h.toLong() > MAX_TEMP_PIXELS) {
                drawDirect(canvas, st, ink, outline, shadowOn, true)
                return
            }
            if (w < 2 || h < 2) return
            val tmp = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                // Memori habis untuk buffer sementara: tetap render (tanpa
                // kabut) daripada meninggalkan goresan tanpa outline.
                drawDirect(canvas, st, ink, outline, shadowOn, true)
                return
            }
            try {
                val c = Canvas(tmp)
                c.translate(-l.toFloat(), -t.toFloat())
                if (shadowOn) {
                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = st.shadowColor
                        if (st.shadowBlur > 0.5f) {
                            maskFilter = BlurMaskFilter(
                                st.shadowBlur.coerceIn(0.5f, 80f), BlurMaskFilter.Blur.NORMAL
                            )
                        }
                    }
                    c.save()
                    c.translate(st.shadowDx * size, st.shadowDy * size)
                    c.drawPath(if (outline != null) outline else ink, sp)
                    c.restore()
                }
                if (outline != null) {
                    val oc = st.outlineColor
                    c.drawPath(
                        outline,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = oc
                            alpha = (st.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                        }
                    )
                    // Lapis dalam: setengah lebar luar, warna terang. inilah
                    // outline SFX dua lapis yang lazim (luar gelap sebagai
                    // pemisah artwork, dalam terang sebagai pemisah gradasi).
                    val inner = inkPath(outlineW * 0.5f)
                    if (inner != null) {
                        val ic = st.outlineInnerColor
                        c.drawPath(
                            inner,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                color = ic
                                alpha = (st.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                            }
                        )
                    }
                }
                if (outline != null || shadowOn) {
                    // Lubangi bagian dalam supaya bayangan/outline tak menimpa
                    // tinta live yang sudah ada di layer.
                    val mask = try {
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) { null }
                    if (mask != null) {
                        val mc = Canvas(mask)
                        mc.translate(-l.toFloat(), -t.toFloat())
                        mc.drawPath(
                            ink,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                        )
                        c.drawBitmap(
                            mask, 0f, 0f,
                            Paint().apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                            }
                        )
                        mask.recycle()
                    }
                }
                c.drawPath(ink, fillPaintFor(st, clip))
                drawGrain(c, st, l, t)
                drawExtras(c, st, clip)
                canvas.drawBitmap(tmp, l.toFloat(), t.toFloat(), null)
            } finally {
                tmp.recycle()
            }
        }

        /**
         * Render langsung ke kanvas (tanpa bitmap sementara) untuk goresan
         * yang terlalu besar. Urutan tetap bayangan -> outline -> tinta, jadi
         * hasilnya sama; hanya kabut bayangan yang dilewati karena
         * BlurMaskFilter pada raster besar sangat berat.
         */
        private fun drawDirect(
            canvas: Canvas, st: Settings, ink: Path,
            outline: Path?, shadowOn: Boolean, noBlur: Boolean
        ) {
            val clip = boundsOf(size)
            if (shadowOn) {
                val col = st.shadowColor
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = col
                    if (!noBlur && st.shadowBlur > 0.5f) {
                        maskFilter = BlurMaskFilter(
                            st.shadowBlur.coerceIn(0.5f, 80f), BlurMaskFilter.Blur.NORMAL
                        )
                    }
                }
                val saved = canvas.save()
                canvas.translate(st.shadowDx * size, st.shadowDy * size)
                canvas.drawPath(if (outline != null) outline else ink, sp)
                canvas.restoreToCount(saved)
            }
            if (outline != null) {
                val oc = st.outlineColor
                canvas.drawPath(
                    outline,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = oc
                        alpha = (st.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                    }
                )
            }
            canvas.drawPath(ink, fillPaintFor(st, clip))
            drawGrain(canvas, st, 0, 0)
            drawExtras(canvas, st, clip)
        }

        /**
         * Menumpuk satu goresan bertekstur di atas tinta memakai Ink API.
         *
         * Ini murni TAMBAHAN: tinta polos (solid atau gradasi) tetap digambar
         * lebih dulu oleh pemanggil, lalu langkah ini menumpuk grain di
         * atasnya. Untuk tekstur ber-`DST_OUT` (retak, tepi robek) hasilnya
         * celah putih di dalam huruf, persis cat kering. Untuk tekstur
         * bermotif (halftone, arsir) hasilnya huruf bertitik atau bergaris.
         *
         * [offX] dan [offY] adalah geseran kanvas yang sudah dipasang pemanggil
         * pada [c]; koordinat titik ikut digeser agar tidak meleset. Mengembalikan
         * false berarti tidak ada yang digambar (grain dimatikan atau pustaka
         * native Ink gagal dimuat) dan pemanggil boleh finishing seperti biasa.
         */
        private fun drawGrain(c: Canvas, st: Settings, offX: Int, offY: Int): Boolean {
            val g = st.grain.coerceIn(0f, 1f)
            if (g < 0.02f || nPts < 2) return false
            if (!SfxTextureBrush.prepare()) return false
            val brush = SfxTextureBrush.brushFor(
                st.textureKind,
                inkColor,
                size * st.widthMul.coerceIn(0.2f, 4f),
                g
            )
            val tx = FloatArray(nPts)
            val ty = FloatArray(nPts)
            for (i in 0 until nPts) {
                tx[i] = xs[i] - offX
                ty[i] = ys[i] - offY
            }
            return SfxTextureBrush.drawStroke(c, tx, ty, nPts, brush)
        }

        /** Paint isi: warna solid atau gradasi sesuai sudut setelan. */
        private fun fillPaintFor(st: Settings, clip: RectF): Paint {
            val start = st.gradStart
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = start
                alpha = (st.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                if (st.gradient) {
                    val rad = Math.toRadians(st.gradAngle.toDouble())
                    val cx = clip.centerX(); val cy = clip.centerY()
                    val r = max(clip.width(), clip.height()) * 0.6f
                    val dx = (cos(rad) * r).toFloat()
                    val dy = (sin(rad) * r).toFloat()
                    shader = LinearGradient(
                        cx - dx, cy - dy, cx + dx, cy + dy,
                        start, st.gradEnd, Shader.TileMode.CLAMP
                    )
                }
            }
        }

        /**
         * Hiasan khas tiap genre, digambar di atas tinta: tetesan (horror),
         * garis kecepatan (action), kilau bintang (fantasy), percikan (semua).
         * Semua posisi acak tetapi deterministik (seed dari titik mulai) agar
         * goresan yang sama selalu menghasilkan bentuk yang sama.
         */
        private fun drawExtras(canvas: Canvas, st: Settings, clip: RectF) {
            val nEx = extrasCount(genre, st.texture.coerceIn(0f, 1f))
            if (nEx <= 0 && st.spatter <= 0) return
            val rnd = SfxInk.XorShift64(seed xor 0x2545F4914F6CDD1DL)
            val base = size * 0.5f * st.widthMul.coerceIn(0.2f, 4f)
            val col = inkColor
            val opa = st.opacity.coerceIn(0f, 1f)
            if (nEx > 0 && nPts >= 2) {
                when (genre) {
                    Genre.HORROR -> drawDrips(canvas, rnd, base, col, opa, nEx)
                    Genre.ACTION -> drawSpeedLines(canvas, rnd, base, col, opa, nEx)
                    Genre.FANTASY -> drawSparkles(canvas, rnd, base, col, opa, nEx)
                    Genre.ROMANCE -> Unit
                }
            }
            if (st.spatter > 0) drawSpatter(canvas, rnd, base, col, opa, st.spatter, clip)
        }

        /** Tetesan tinta horror: menggantung di tepi "bawah" goresan. */
        private fun drawDrips(
            canvas: Canvas, rnd: SfxInk.XorShift64,
            base: Float, col: Int, opa: Float, nEx: Int
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = col
                alpha = (opa * 235f).toInt().coerceIn(0, 255)
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            for (i in 0 until nEx) {
                val idx = (rnd.nextFloat() * (nPts - 1)).toInt().coerceIn(0, nPts - 1)
                val ux = nx(idx); val uy = ny(idx)
                if (uy <= 0f) continue
                val w = base * (0.5f + rnd.nextFloat() * 0.5f)
                val cx = xs[idx] + ux * w * 0.9f
                val cy = ys[idx] + uy * w * 0.9f
                val rr = base * (0.16f + rnd.nextFloat() * 0.22f)
                canvas.drawCircle(cx, cy, rr, p)
                val tail = rr * (1.6f + rnd.nextFloat() * 2.4f)
                val path = Path().apply {
                    moveTo(cx - rr, cy)
                    lineTo(cx + rr, cy)
                    lineTo(cx + rr * 0.25f, cy + tail)
                    lineTo(cx - rr * 0.25f, cy + tail)
                    close()
                }
                canvas.drawPath(path, p)
            }
        }

        /** Garis kecepatan action: pita tipis paralel di belakang sapuan. */
        private fun drawSpeedLines(
            canvas: Canvas, rnd: SfxInk.XorShift64,
            base: Float, col: Int, opa: Float, nEx: Int
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                color = col
                alpha = (opa * 110f).toInt().coerceIn(0, 255)
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            for (i in 0 until nEx) {
                val side = if (i % 2 == 0) 1f else -1f
                val off = base * (0.9f + rnd.nextFloat() * 1.1f) * side
                p.strokeWidth = max(1f, base * (0.12f + rnd.nextFloat() * 0.14f))
                val path = Path()
                for (k in 0 until nPts) {
                    val px = xs[k] + nx(k) * off
                    val py = ys[k] + ny(k) * off
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, p)
            }
        }

        /** Kilau bintang fantasy: bintang 4 sudut + titik kecil di samping. */
        private fun drawSparkles(
            canvas: Canvas, rnd: SfxInk.XorShift64,
            base: Float, col: Int, opa: Float, nEx: Int
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = col
                alpha = (opa * 230f).toInt().coerceIn(0, 255)
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            for (i in 0 until nEx) {
                val idx = (rnd.nextFloat() * (nPts - 1)).toInt().coerceIn(0, nPts - 1)
                val r = base * (0.35f + rnd.nextFloat() * 0.5f)
                val cx = xs[idx] + nx(idx) * base * (0.4f + rnd.nextFloat())
                val cy = ys[idx] + ny(idx) * base * (0.4f + rnd.nextFloat())
                canvas.drawPath(star4(cx, cy, r, r * 0.30f), p)
                canvas.drawCircle(cx + r, cy - r, r * 0.18f, p)
            }
        }

        /** Percikan kecil di sekitar goresan (jumlah dari setelan spatter). */
        private fun drawSpatter(
            canvas: Canvas, rnd: SfxInk.XorShift64,
            base: Float, col: Int, opa: Float, count: Int, clip: RectF
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = col
                if (alphaLocked) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            val reach = max(clip.width(), clip.height()) * 0.5f + base
            for (i in 0 until count) {
                val th = rnd.nextFloat() * 2f * PI.toFloat()
                val rad = reach * rnd.nextFloat()
                val cx = clip.centerX() + cos(th) * rad
                val cy = clip.centerY() + sin(th) * rad
                p.alpha = (opa * 200f * (1f - rad / max(1f, reach))).toInt().coerceIn(0, 255)
                if (p.alpha <= 3) continue
                canvas.drawCircle(cx, cy, max(0.7f, base * 0.10f * rnd.nextFloat()), p)
            }
        }

        /** Bintang 4 sudut (bentuk kilau anime/manga). */
        private fun star4(cx: Float, cy: Float, rOuter: Float, rInner: Float): Path {
            val p = Path()
            for (i in 0 until 8) {
                val a = (i * PI.toFloat() / 4f) - PI.toFloat() / 2f
                val rr = if (i % 2 == 0) rOuter else rInner
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            return p
        }
    }

    /** Batas titik per goresan (selaras dengan buffer panjang busur). */
    const val MAX_PTS = 4096

    /** Batas piksel render sementara (4MP = 16MB) sebelum pakai jalur langsung. */
    const val MAX_TEMP_PIXELS = 4_000_000L

    /** Peta kuas ke genre (null kalau bukan kuas genre). */
    fun genreOf(brushType: BrushType): Genre? = Genre.of(brushType)

    /** Pabrik stroke (dipakai BrushEngine). */
    fun newStroke(store: SettingsStore): Stroke = Stroke(store)
}
