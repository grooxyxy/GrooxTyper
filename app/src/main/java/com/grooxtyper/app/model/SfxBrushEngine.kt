package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesin goresan SFX (lettering komik) - SAPUAN KUAS, bukan garis font.
 *
 * Mengapa mesin terpisah? Goresan SFX yang benar bukan "garis tebal": ia
 * hasil dari kuas yang ditekan ke kertas. Ciri yang wajib ada:
 *  1. Taper masuk - mulai dari ujung runcing, bukan garis tumpul.
 *  2. Lebar hidup - denyut lebar yang tak beraturan (gaya tangan), bukan
 *     lebar konstan.
 *  3. Tekstur sabut kuas (streak) yang SEJAJAR dengan arah goresan - inilah
 *     pembeda kuas sungguhan dari stempel radial biasa.
 *  4. Kontras arah - turun tebal, naik tipis (tekanan natural).
 *  5. Lift - jeda lebih dari 110ms = kuas diangkat, goresan menipis.
 *  6. Ekor runcing - ujung belakang ditutup taper, bukan berhenti tumpul.
 *
 * Implementasi: jalur di-resample jadi deretan stamp (bitmap radial plus
 * tekstur) yang dirotasi mengikuti arah goresan. Jarak antar stamp di bawah
 * radius sehingga raster selalu sambung (tanpa celah), dan tekstur tidak
 * "berenang" karena tiap stamp ikut berputar.
 *
 * Ekor ditahan: dab terakhir belum digambar sampai stroke selesai, sehingga
 * ujung runcing benar-benar terbentuk (bukan overlay di atas cat).
 */
class SfxBrushEngine {

    /** Gaya kuas SFX (dipetakan dari enum BrushType). */
    enum class Style(val displayName: String) {
        /** Kuas pena tebal: tepi keras, denyut lebar halus. */
        PEN("SFX Pen"),

        /** Kuas kering: streak kuas jelas, sedikit rontok (klasik SFX). */
        BRUSH("SFX Brush"),

        /** Spidol lebar: tepi keras, sedikit lebih lebar dari tinggi. */
        MARKER("SFX Marker"),

        /** Semprotan udara: lembut, tanpa streak, alpha menumpuk. */
        AIR("SFX Airbrush"),

        /** Krayon: streak kasar plus alpha lebih tipis. */
        CRAYON("SFX Crayon"),

        /** Tinta pekat dengan cipratan (spatter) di sepanjang goresan. */
        INK("SFX Ink"),

        /** Neon: halo lembut warna cat dengan inti terang. */
        NEON("SFX Neon");

        /** Kuas ini perlu sapuan bertekstur (bukan isi polos). */
        val isTextured: Boolean
            get() = this == BRUSH || this == CRAYON
    }

    companion object {
        /** Resolusi stamp (96px) - diskalakan ke lebar dab saat digambar. */
        private const val STAMP_PX = 96

        /** Jeda (ms) yang dianggap "kuas diangkat". */
        private const val LIFT_MS = 110L

        private val stampCache = HashMap<Style, Bitmap>()

        /** Peta enum BrushType ke gaya mesin SFX (null = bukan kuas SFX). */
        fun styleOf(t: BrushType): Style? = when (t) {
            BrushType.SFX_PEN -> Style.PEN
            BrushType.SFX_BRUSH -> Style.BRUSH
            BrushType.SFX_MARKER -> Style.MARKER
            BrushType.SFX_AIR -> Style.AIR
            BrushType.SFX_CRAYON -> Style.CRAYON
            BrushType.SFX_INK -> Style.INK
            BrushType.SFX_NEON -> Style.NEON
            else -> null
        }

        /** Hash deterministik ke -1..1 (goresan sama bila seed sama). */
        private fun hashF(i: Int, seed: Int): Float {
            var k = i * 374761393 + seed * 668265263
            k = k xor (k ushr 13)
            k *= 1274126177
            k = k xor (k ushr 16)
            return (((k ushr 8) and 0xFFFF) / 32767.5f) - 1f
        }

        /** Noise nilai 1D halus (interpolasi smoothstep) ke -1..1. */
        private fun noise1(x: Float, seed: Int): Float {
            val i = floor(x).toInt()
            val f = x - i
            val u = f * f * (3f - 2f * f)
            return hashF(i, seed) * (1f - u) + hashF(i + 1, seed) * u
        }

        /**
         * Stamp radial plus tekstur (RGB putih, alpha membawa bentuk).
         * Streak kuas berkorelasi kuat pada sumbu Y (lintas goresan) sehingga
         * saat stamp dirotasi, seratnya sejajar arah goresan.
         */
        fun stampFor(style: Style): Bitmap {
            stampCache[style]?.let { return it }
            val bmp = Bitmap.createBitmap(STAMP_PX, STAMP_PX, Bitmap.Config.ARGB_8888)
            val px = IntArray(STAMP_PX * STAMP_PX)
            val c = (STAMP_PX - 1) / 2f
            val seed = style.ordinal * 7919 + 13
            for (y in 0 until STAMP_PX) {
                for (x in 0 until STAMP_PX) {
                    val dx = x - c
                    val dy = y - c
                    val d = hypot(dx, dy) / c
                    var a: Float = when (style) {
                        Style.PEN, Style.NEON ->
                            if (d < 0.9f) 1f else (1f - (d - 0.9f) / 0.1f)
                        Style.MARKER ->
                            if (d < 0.94f) 1f else (1f - (d - 0.94f) / 0.06f)
                        Style.INK ->
                            if (d < 0.86f) 1f else (1f - (d - 0.86f) / 0.14f)
                        Style.AIR -> (1f - d * d).coerceAtLeast(0f)
                        Style.BRUSH, Style.CRAYON -> {
                            val core = if (d < 0.88f) 1f else (1f - (d - 0.88f) / 0.12f)
                            val streak = 0.5f + 0.5f * noise1(y * 0.42f, seed)
                            val grain = 0.78f + 0.22f * noise1(x * 0.13f, seed + 7)
                            core * (0.30f + 0.70f * streak) * grain
                        }
                    }
                    a = a.coerceIn(0f, 1f)
                    // Celah kering: sebagian piksel hilang total (kuas tak
                    // menyentuh kertas) - hanya untuk kuas bertekstur.
                    if (style.isTextured) {
                        val gap = hashF(x * 7 + y * 131, seed + 3)
                        if (gap > 0.86f) a *= 0.12f
                    }
                    val av = (a * 255f).toInt().coerceIn(0, 255)
                    px[y * STAMP_PX + x] = (av shl 24) or 0x00FFFFFF
                }
            }
            bmp.setPixels(px, 0, STAMP_PX, 0, 0, STAMP_PX, STAMP_PX)
            stampCache[style] = bmp
            return bmp
        }

        /** Campur warna isi dengan putih (0 = asli, 1 = putih). */
        fun mixWhite(fill: Int, f: Float): Int = Color.rgb(
            (Color.red(fill) + (255 - Color.red(fill)) * f).toInt().coerceIn(0, 255),
            (Color.green(fill) + (255 - Color.green(fill)) * f).toInt().coerceIn(0, 255),
            (Color.blue(fill) + (255 - Color.blue(fill)) * f).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Satu goresan SFX berjalan. Siklus hidup: [begin] lalu [push] berulang,
     * ditutup [finish] (atau [discard] saat undo).
     */
    class Stroke(private val engine: SfxBrushEngine) {
        var style: Style = Style.PEN
        var color: Int = Color.BLACK
        var size: Float = 40f
        var opacity: Float = 1f
        var alphaLocked: Boolean = false

        private val pts = ArrayList<Offset>(64)
        private val cum = ArrayList<Float>(64)
        private var paintedLen = 0f
        private var totalLen = 0f
        private var curDist = 0f
        private var lastMs = 0L
        private var dabCount = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dst = RectF()
        // Src WAJIB android.graphics.Rect (bukan RectF) untuk drawBitmap.
        private val srcRect = Rect(0, 0, STAMP_PX, STAMP_PX)

        /** Benih acak stroke ini (sama = goresan identik saat undo/redo). */
        private var seed: Int = 1

        /** Mulai goresan baru di titik [p]. */
        fun begin(
            p: Offset,
            style: Style,
            color: Int,
            size: Float,
            opacity: Float,
            alphaLocked: Boolean
        ) {
            this.style = style
            this.color = color
            this.size = max(1f, size)
            this.opacity = opacity.coerceIn(0f, 1f)
            this.alphaLocked = alphaLocked
            seed = (abs(p.x.toInt() * 31 + p.y.toInt() * 17 + engine.tick++)) or 1
            pts.clear()
            cum.clear()
            pts.add(p)
            cum.add(0f)
            resetCursor()
            lastMs = System.currentTimeMillis()
            dabCount = 0
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            if (alphaLocked) {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
        }

        /** Buang state tanpa menggambar (dipakai saat stroke di-undo). */
        fun discard() {
            pts.clear()
            cum.clear()
            resetCursor()
        }

        /** True bila ada goresan yang sedang berjalan. */
        fun isActive(): Boolean = pts.isNotEmpty()

        /** True bila masih ada ekor yang belum digambar. */
        fun hasTail(): Boolean = pts.size > 1 && totalLen - paintedLen > 0.5f

        /**
         * Tambahkan titik hasil sampling kurva lalu gambar dab sepanjang
         * bagian yang sudah aman (ekor belakang ditahan untuk runcing akhir).
         *
         * @param speed faktor kecepatan 0..1 (1 = lambat sehingga tebal).
         */
        fun push(canvas: Canvas, newPts: List<Offset>, speed: Float) {
            for (p in newPts) {
                if (pts.isEmpty()) {
                    pts.add(p)
                    cum.add(0f)
                    continue
                }
                val last = pts[pts.size - 1]
                val d = hypot(p.x - last.x, p.y - last.y)
                if (d < 0.01f) continue
                totalLen += d
                pts.add(p)
                cum.add(totalLen)
            }
            if (pts.size < 2) return
            val now = System.currentTimeMillis()
            val gapMs = if (lastMs > 0) now - lastMs else 0
            lastMs = now
            // Kuas diangkat: jeda panjang = tekanan hilang.
            val lift = if (gapMs > LIFT_MS) {
                (1f - (gapMs - LIFT_MS) / 260f).coerceIn(0.22f, 1f)
            } else 1f
            // Ekor ditahan: sisakan 1.3x ukuran sebagai cadangan runcing.
            val hold = max(6f, size * 1.3f)
            paintUntil(canvas, (totalLen - hold).coerceAtLeast(paintedLen), speed, lift, false)
        }

        /** Tutup goresan: gambar ekor ditahan dengan taper kuadratik. */
        fun finish(canvas: Canvas) {
            if (pts.size < 2) {
                discard()
                return
            }
            paintUntil(canvas, totalLen, 1f, 1f, true)
            discard()
        }

        /** Ketukan tunggal = satu sentuhan kuas (bukan garis nol). */
        fun dot(canvas: Canvas, p: Offset) {
            dab(canvas, p, 0f, max(0.8f, size * 0.5f), 1f, 1f, dabCount++)
        }

        private fun resetCursor() {
            paintedLen = 0f
            totalLen = 0f
            curDist = 0f
        }

        /**
         * Gambar dab dari posisi kursor sampai [untilLen].
         * [taperTail] benar untuk ekor runcing, salah untuk badan goresan.
         */
        private fun paintUntil(
            canvas: Canvas,
            untilLen: Float,
            speed: Float,
            lift: Float,
            taperTail: Boolean
        ) {
            if (pts.size < 2) return
            val tailStart = paintedLen
            val tailSpan = (untilLen - tailStart).coerceAtLeast(0.001f)
            var d = max(curDist, paintedLen)
            var guard = 0
            while (d <= untilLen && guard < 3000) {
                guard++
                val t = if (taperTail) ((d - tailStart) / tailSpan).coerceIn(0f, 1f) else 0f
                val taper = if (taperTail) (1f - t * t).coerceIn(0.03f, 1f) else 1f
                val w = max(0.6f, widthAt(d, speed, lift) * taper)
                dab(canvas, pointAt(d), angleAt(d), w, speed, lift, dabCount++)
                val step = max(1.1f, w * 0.42f)
                d += step
                // Jaga agar tak looping tanpa batas pada goresan sangat
                // panjang: setiap 200 dab, lonjakan langkah lebih kasar.
                if (guard % 200 == 0) d += step * 2f
            }
            paintedLen = max(paintedLen, untilLen)
            curDist = paintedLen
        }

        /** Lebar satu dab: taper masuk + denyut + kontras arah + kecepatan + lift. */
        private fun widthAt(dist: Float, speed: Float, lift: Float): Float {
            val base = size
            val entryLen = max(4f, base * 0.55f)
            val e = (dist / entryLen).coerceIn(0f, 1f)
            val entry = 0.10f + 0.90f * (e * e * (3f - 2f * e))
            // Kontras arah: turun (dirY positif) tebal, naik tipis.
            val dirY = sin(Math.toRadians(angleAt(dist).toDouble())).toFloat()
            val dirMul = 1f - 0.30f * (-dirY).coerceIn(0f, 1f)
            // Denyut organik (gaya tangan), dua skala agar tak beraturan.
            val pulse = 1f + 0.16f * noise1(dist * 0.035f, seed) +
                0.08f * noise1(dist * 0.11f, seed + 5)
            val w = base * entry * dirMul * pulse * speed * lift
            return w.coerceIn(base * 0.06f, base * 1.65f)
        }

        private fun indexAt(dist: Float): Int {
            var lo = 0
            var hi = cum.size - 1
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (cum[mid] < dist) lo = mid + 1 else hi = mid
            }
            return lo
        }

        private fun pointAt(dist: Float): Offset {
            if (pts.isEmpty()) return Offset.Zero
            if (dist <= 0f) return pts[0]
            val i = indexAt(dist)
            if (i <= 0) return pts[0]
            if (i >= pts.size) return pts[pts.size - 1]
            val c0 = cum[i - 1]
            val c1 = cum[i]
            val span = (c1 - c0).coerceAtLeast(0.0001f)
            val t = ((dist - c0) / span).coerceIn(0f, 1f)
            val a = pts[i - 1]
            val b = pts[i]
            return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }

        /** Sudut arah goresan di posisi d (derajat, untuk rotasi stamp). */
        private fun angleAt(d: Float): Float {
            if (pts.size < 2) return 0f
            val a = pointAt(max(0f, d - 1.2f))
            val b = pointAt(d + 1.2f)
            val dx = b.x - a.x
            val dy = b.y - a.y
            if (abs(dx) < 0.001f && abs(dy) < 0.001f) return 0f
            return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        /** Gambar satu stamp: rotasi ikut arah goresan, ukuran = lebar dab. */
        private fun dab(
            canvas: Canvas,
            p: Offset,
            ang: Float,
            width: Float,
            speed: Float,
            lift: Float,
            index: Int
        ) {
            val r = max(0.4f, width * 0.5f)
            val a = (opacity * 255f).toInt().coerceIn(0, 255)
            if (a <= 0) return
            // Spidol: sedikit lebih lebar dari tinggi (efek chisel).
            val stretch = if (style == Style.MARKER) 1.22f else 1f
            if (style == Style.NEON) {
                // Halo neon: stamp lembut lebih besar, warna penuh.
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                paint.alpha = (a * 0.45f).toInt().coerceIn(0, 255)
                drawStamp(canvas, p, ang, r * 2.1f, stretch)
            }
            val main = if (style == Style.NEON) mixWhite(color, 0.6f) else color
            paint.colorFilter = PorterDuffColorFilter(main, PorterDuff.Mode.SRC_IN)
            paint.alpha = a
            drawStamp(canvas, p, ang, r, stretch)
            // Cipratan tinta: titisan kecil di sisi goresan (deterministik).
            if (style == Style.INK) {
                val h = hashF(index * 3 + 1, seed)
                if (h > 0.72f) {
                    val rad = Math.toRadians(ang.toDouble())
                    val px = (-sin(rad)).toFloat()
                    val py = cos(rad).toFloat()
                    val off = r * (0.9f + 3.0f * (h - 0.72f))
                    val side = if (index % 2 == 0) 1f else -1f
                    paint.alpha = (a * 0.85f).toInt().coerceIn(0, 255)
                    drawStamp(
                        canvas,
                        Offset(p.x + px * off * side, p.y + py * off * side),
                        ang, r * 0.22f, 1f
                    )
                }
            }
        }

        private fun drawStamp(canvas: Canvas, p: Offset, ang: Float, r: Float, stretch: Float) {
            val stamp = stampFor(if (style == Style.NEON) Style.AIR else style)
            canvas.save()
            canvas.translate(p.x, p.y)
            if (ang != 0f) canvas.rotate(ang)
            if (stretch != 1f) canvas.scale(stretch, 1f)
            dst.set(-r, -r, r, r)
            canvas.drawBitmap(stamp, srcRect, dst, paint)
            canvas.restore()
        }
    }

    /** Penghitung sederhana supaya tiap goresan punya benih berbeda. */
    var tick: Int = 1
        private set

    fun newStroke(): Stroke = Stroke(this)
}
