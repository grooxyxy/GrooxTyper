@file:OptIn(ExperimentalInkCustomBrushApi::class)

package com.grooxtyper.app.model

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as composeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockTextureBitmapStore
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kuas SFX/webtoon bertekstur memakai **Ink API** (androidx.ink 1.0.0).
 *
 * ## Kenapa Ink API
 *
 * Model brush Procreate (dokumentasi resmi): sebuah brush berisi *grain*
 * (tekstur) di dalam *shape*, lalu bentuk dan tekstur itu diseret sepanjang
 * path. Ink API punya ketiganya sebagai kelas sungguhan: [BrushFamily]
 * (gaya/keluarga), [BrushPaint.TextureLayer] (grain), dan
 * `androidx.ink.brush.BrushBehavior` (dynamics). Jadi ini bukan gambar yang
 * meniru efek, tapi brush yang dirender engine resmi Android.
 *
 * Grain adalah **bitmap** yang didaftarkan ke [StockTextureBitmapStore] lalu
 * dirujuk lewat id. Bitmapnya dibuat prosedural di sini dan digambar dengan
 * `androidx.compose.ui.graphics.Brush` (radial/linear gradient) di atas
 * [ImageBitmap]. Jadi kedua API yang diminta benar-benar bertemu: Compose
 * `Brush` menghasilkan grain-nya, Ink API mengonsumsinya lalu merender
 * goresannya ke `android.graphics.Canvas` milik layer.
 *
 * ## Dua cara meletakkan grain (dari enum resmi Ink)
 *
 * - `TextureMapping.TILING`: grain diulang mengikuti panjang goresan
 *   (halftone, arsir). Persis "grain ikut terseret".
 * - `TextureMapping.STAMPING`: satu stempel diulang di tiap sample goresan
 *   (ciprican, air, retak).
 *
 * ## Tiga mode blend (dari enum resmi Ink)
 *
 * - `BlendMode.SRC_OVER`: stempel tinta di atas goresan.
 * - `BlendMode.MODULATE`: grain mengalikan kecerahan tinta, jadi motif
 *   gelap-terang tanpa kehilangan warna kuas.
 * - `BlendMode.DST_OUT`: bagian opaque pada grain MENGHAPUS tinta, jadi
 *   celah putih = cat retak atau kering.
 *
 * ## Batasan yang harus jujur diketahui
 *
 * 1. `ink-brush` 1.0.0 masih berstatus [ExperimentalInkCustomBrushApi];
 *   karena itu ada `@file:OptIn` di baris pertama.
 * 2. Bentuk goresannya adalah mesh Ink (lebar ikut `Brush.size`), bukan pita
 *   lebar-variabel milik [GenreBrushEngine]. Karena itu mesin TIDAK mengganti
 *   langkah tinta, melainkan menumpuk grain di atasnya: gradasi isi tetap utuh
 *   dan yang berubah hanya porinya. Untuk tekstur ber-`DST_OUT` hasilnya celah
 *   putih di dalam huruf; untuk yang bermotif, hurufnya bertitik atau bergaris.
 * 3. [available] dicek sekali. Kalau pustaka native `libink.so` gagal dimuat
 *   (misal ABI tak cocok), mesin diam-diam jatuh ke kuas polos; kuas tidak
 *   pernah membuat aplikasi crash.
 */
object SfxTextureBrush {

    /**
     * Enam tekstur. Namanya diambil dari brush pack yang benar-benar ada:
     *
     * - [HALFTONE]: tone dots/screentone komik, juga lazim untuk mewarnai huruf.
     * - [HATCH]: "Hatch Paint" Clip Studio, goresan berarah.
     * - [CRUNCH]: cat retak, celah putih di dalam goresan.
     * - [SPATTER]: spraypaint Procreate, ciprakan tinta.
     * - [RIBBON]: kuas tinta air/sumi-e, memudar ikut tekanan.
     * - [ERODED]: "Ragged Pastel", tepi compang-campang.
     */
    enum class Texture(val displayName: String) {
        HALFTONE("Halftone"),
        HATCH("Arsir"),
        CRUNCH("Retak"),
        SPATTER("Ciprican"),
        RIBBON("Pita Air"),
        ERODED("Tepi Robek");

        companion object {
            /** Tekstur bawaan tiap genre SFX. */
            fun forGenre(genre: GenreBrushEngine.Genre): Texture = when (genre) {
                GenreBrushEngine.Genre.HORROR -> CRUNCH
                GenreBrushEngine.Genre.ROMANCE -> RIBBON
                GenreBrushEngine.Genre.ACTION -> SPATTER
                GenreBrushEngine.Genre.FANTASY -> HALFTONE
                // Logam: arsir rapat. Arsir dibaca sebagai permukaan logam
                // bergaris, bukan pori cat.
                GenreBrushEngine.Genre.MECH -> HATCH
                GenreBrushEngine.Genre.EXPLOSION -> CRUNCH
                GenreBrushEngine.Genre.SWOOSH -> SPATTER
                GenreBrushEngine.Genre.CHILL -> RIBBON
                // Asap butuh tepi membaur (pita air), listrik butuh pola
                // berarah (arsir), tebasan butuh sapuan kering (ciprican).
                GenreBrushEngine.Genre.SMOKE -> RIBBON
                GenreBrushEngine.Genre.ELECTRIC -> HATCH
                GenreBrushEngine.Genre.SLASH -> SPATTER
            }
        }
    }

    /** Sisi tile grain dalam piksel. Makin kecil, makin rapat motifnya. */
    private const val TILE = 64

    /** Kuadrat bujur sangkar tone dots. */
    private const val CELL = TILE / 2

    /** Generator bilangan acak tetap, supaya hasil sama di semua perangkat. */
    private class Lcg(seed: Int) {
        private var s = seed
        fun next(): Float {
            s = (s * 1103515245 + 12345) and 0x7FFFFFFF
            return (s % 10000) / 10000f
        }
    }

    private val white = Color.White
    private val clear = Color.Transparent

    private var inkStore: StockTextureBitmapStore? = null
    private var inkRenderer: CanvasStrokeRenderer? = null

    /** Cache keluarga kuas per (tekstur, kekuatan), tak dibangun tiap goresan. */
    private val families = HashMap<String, BrushFamily>()

    private val tried = AtomicBoolean(false)

    /** True kalau mesin Ink siap dipakai. */
    @Volatile
    var available: Boolean = false
        private set

    /**
     * Menyiapkan pustaka Ink sekali saja: mendaftarkan enam tile grain lalu
     * membuat renderer. Gagal berarti pustaka native tak termuat, dan kuas
     * polos yang dipakai selamanya setelah itu.
     */
    fun prepare(): Boolean {
        if (available) return true
        if (!tried.compareAndSet(false, true)) return false
        return try {
            val s = StockTextureBitmapStore(Resources.getSystem())
            for (t in Texture.entries) s.addTexture(textureId(t), buildTile(t))
            inkRenderer = CanvasStrokeRenderer.create(s)
            inkStore = s
            available = true
            true
        } catch (t: Throwable) {
            inkRenderer = null
            inkStore = null
            available = false
            false
        }
    }

    private fun textureId(t: Texture): String = "grooxtyper.sfx.$t"

    // ------------------------------------------------------------------
    // 1. Tile grain, digambar dengan androidx.compose.ui.graphics.Brush
    // ------------------------------------------------------------------

    /**
     * Satu tile grain. `ImageBitmap` + `composeCanvas` dipakai supaya penggambar
     *annya benar-benar API Compose; isinya memakai `Brush` resmi (radial
     * gradient untuk titik dan stempel bulat, linear gradient untuk arsir dan
     * goresan retak).
     */
    private fun buildTile(t: Texture): Bitmap {
        val img = ImageBitmap(TILE, TILE)
        // Compose 1.7 tidak punya fungsi Canvas(image) { DrawScope } seperti
        // pada versi Compose yang lebih baru. Jalur resmi yang tersedia di
        // 1.7.6 adalah CanvasDrawScope().draw(density, arah, canvas, size).
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            composeCanvas(img),
            Size(TILE.toFloat(), TILE.toFloat())
        ) {
            when (t) {
                Texture.SPATTER -> drawDab(0.42f, 1f, 0.85f)
                Texture.RIBBON -> drawDab(0.50f, 0.55f, 0.95f)
                Texture.CRUNCH -> {
                    val rnd = Lcg(20090727)
                    repeat(7) {
                        val cx = rnd.next() * size.width
                        val cy = rnd.next() * size.height
                        val r = 3f + rnd.next() * 6f
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(white, white, clear),
                                center = Offset(cx, cy),
                                radius = r
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                    }
                }
                Texture.ERODED -> {
                    val rnd = Lcg(777333)
                    repeat(10) {
                        val x0 = rnd.next() * size.width
                        val y0 = rnd.next() * size.height
                        val rad = (rnd.next() - 0.5f) * 0.7f
                        val len = 8f + rnd.next() * 16f
                        val ex = x0 + cos(rad) * len
                        val ey = y0 + sin(rad) * len
                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(white, clear),
                                start = Offset(x0, y0),
                                end = Offset(ex, ey)
                            ),
                            start = Offset(x0, y0),
                            end = Offset(ex, ey),
                            strokeWidth = 1.5f + rnd.next() * 2f
                        )
                    }
                }
                Texture.HALFTONE -> {
                    for (gy in 0 until TILE / CELL) {
                        for (gx in 0 until TILE / CELL) {
                            val cx = gx * CELL + CELL * 0.5f
                            val cy = gy * CELL + CELL * 0.5f
                            val r = 2.2f + ((gy * (TILE / CELL) + gx) % 4) * 1.1f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(white, white, clear),
                                    center = Offset(cx, cy),
                                    radius = r
                                ),
                                radius = r,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
                Texture.HATCH -> {
                    var k = 0
                    while (k < TILE) {
                        val a = 0.45f + 0.25f * ((k % 4) / 3f)
                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(white.copy(alpha = a), clear),
                                start = Offset(k.toFloat(), 0f),
                                end = Offset(0f, k.toFloat())
                            ),
                            start = Offset(k.toFloat(), 0f),
                            end = Offset(0f, k.toFloat()),
                            strokeWidth = 2f + (k % 3)
                        )
                        k += 6
                    }
                }
            }
        }
        return img.asAndroidBitmap()
    }

    /** Satu stempel bulat halus; dipakai SPATTER dan RIBBON. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDab(
        radiusFrac: Float,
        alpha: Float,
        softness: Float
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.width * radiusFrac
        drawCircle(
            brush = Brush.radialGradient(
                0f to white.copy(alpha = alpha),
                softness to white.copy(alpha = alpha),
                1f to clear,
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
    }

    // ------------------------------------------------------------------
    // 2. BrushFamily per tekstur (shape + grain)
    // ------------------------------------------------------------------

    /**
     * Membangun keluarga kuas bertekstur, lalu menyimpan hasilnya.
     *
     * `particleGapDistanceScale` dan `particleGapDurationMillis` pada [BrushTip]
     * adalah sistem partikel bawaan Ink: menyetel keduanya membuat kuas ini
     * menyisakan celah di sepanjang goresan. Itulah yang dipakai tekstur Retak
     * dan Tepi Robek supaya terlihat patah, bukan sekadar bergaris.
     */
    private fun familyFor(t: Texture, amount: Float): BrushFamily {
        val step = (amount.coerceIn(0f, 1f) * 10f).toInt()
        val key = "$t#$step"
        families[key]?.let { return it }
        val gap = when (t) {
            Texture.CRUNCH -> 0.18f
            Texture.ERODED -> 0.10f
            else -> 0f
        }
        val tip = BrushTip.builder()
            .setScaleX(1f)
            .setScaleY(1f)
            .setCornerRounding(0f)
            .setSlantDegrees(0f)
            .setPinch(0f)
            .setRotationDegrees(0f)
            .setParticleGapDistanceScale(if (gap > 0f) gap else 1f)
            .setParticleGapDurationMillis(if (gap > 0f) 40L else 0L)
            .setBehaviors(emptyList())
            .build()
        val paint = BrushPaint(listOf(layerFor(t, step / 10f)))
        val family = BrushFamily(tip, paint)
        families[key] = family
        return family
    }

    /**
     * Lapisan grain untuk satu tekstur, ditunjuk lewat id di store. Kekuatan
     * [amount] jadiopacity grain: 0,1 masih kelihatan, 1 berarti penuh.
     * Dipisah seperseratus supaya cache keluarga kuas tak meledak saat
     * slider digeser.
     */
    private fun layerFor(t: Texture, amount: Float): BrushPaint.TextureLayer {
        val tiling = t == Texture.HALFTONE || t == Texture.HATCH
        val carve = t == Texture.CRUNCH || t == Texture.ERODED
        return BrushPaint.TextureLayer
            .builder(textureId(t), TILE.toFloat(), TILE.toFloat())
            .setMapping(
                if (tiling) {
                    BrushPaint.TextureMapping.TILING
                } else {
                    BrushPaint.TextureMapping.STAMPING
                }
            )
            .setBlendMode(
                when {
                    carve -> BrushPaint.BlendMode.DST_OUT
                    tiling -> BrushPaint.BlendMode.MODULATE
                    else -> BrushPaint.BlendMode.SRC_OVER
                }
            )
            .setSizeUnit(BrushPaint.TextureSizeUnit.STROKE_SIZE)
            .setOrigin(BrushPaint.TextureOrigin.STROKE_SPACE_ORIGIN)
            .setWrapX(BrushPaint.TextureWrap.REPEAT)
            .setWrapY(BrushPaint.TextureWrap.REPEAT)
            .setOpacity(0.2f + 0.8f * amount.coerceIn(0f, 1f))
            .build()
    }

    /**
     * Kuas siap pakai untuk satu tekstur: warna, lebar, kekuatan grain, dan
     * toleransi geometri. [epsilon] 0.1 piksel sesuai anjuran dokumentasi Ink;
     * makin kecil epsilon, makin dalam zoom sebelum muncul segitiga.
     */
    fun brushFor(
        t: Texture,
        colorInt: Int,
        sizePx: Float,
        amount: Float
    ): androidx.ink.brush.Brush =
        androidx.ink.brush.Brush.createWithColorIntArgb(
            familyFor(t, amount),
            colorInt,
            sizePx.coerceIn(1f, 4000f),
            0.1f
        )

    // ------------------------------------------------------------------
    // 3. Menggambar goresan
    // ------------------------------------------------------------------

    /**
     * Menggambar satu goresan bertekstur ke [canvas] milik layer.
     *
     * Titik dikirim berurutan sebagai [androidx.ink.strokes.StrokeInput] dengan
     * waktu yang dikarang supaya distress jadi monoton, lalu
     * [InProgressStroke] membentuk mesh dan [CanvasStrokeRenderer] yang
     * menggambarnya. Identitas matriks dipakai karena koordinat titik sudah
     * dalam piksel kanvas.
     *
     * Return true kalau benar-benar tergambar; false berarti pemanggil harus
     * memakai kuas polos.
     */
    fun drawStroke(
        canvas: android.graphics.Canvas,
        xs: FloatArray,
        ys: FloatArray,
        n: Int,
        brush: androidx.ink.brush.Brush
    ): Boolean {
        if (!prepare()) return false
        val r = inkRenderer ?: return false
        if (n < 1 || xs.size < n || ys.size < n) return false
        return try {
            val batch = MutableStrokeInputBatch()
            var t = 0L
            for (i in 0 until n) {
                if (i > 0) t += 8L
                batch.add(InputToolType.TOUCH, xs[i], ys[i], t)
            }
            val ips = InProgressStroke()
            ips.start(brush)
            ips.enqueueInputs(batch.toImmutable(), EMPTY_BATCH)
            ips.finishInput()
            ips.updateShape()
            r.draw(canvas, ips.toImmutable(), Matrix())
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Batch kosong untuk parameter input lanjutan; Ink mewajibkannya. */
    private val EMPTY_BATCH by lazy {
        MutableStrokeInputBatch().toImmutable()
    }

    /** Berapa tekstur yang bisa ditawarkan ke pengguna. */
    fun textureCount(): Int = Texture.entries.size

    /** True kalau store grain sudah terdaftar; dipakai uji kontraknya. */
    fun hasStore(): Boolean = inkStore != null
}
