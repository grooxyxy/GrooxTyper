package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mesin "tinta SFX" ala video lettering komik: huruf digambar sebagai bentuk
 * dengan **isi padat + tepi bergerigi + outline putih yang mengikuti kontur
 * luar DAN cekungan (counter) huruf**.
 *
 * Ini BUKAN engine stamp. Hasil pengukuran pada video & riset (MyPaint,
 * Krita bristle, Rough.js, Procreate) menunjukkan bahwa tampilan itu hanya
 * bisa didapat dengan:
 *  1. Resample path memakai **panjang busur** (`s`), bukan koordinat layar -
 *     inilah alasan noise tidak "berenang" saat goresan/huruf diputar.
 *  2. **Profil half-width 1-D ber-noise** (white noise di-boxblur 2 skala)
 *     yang menghasilkan gigi tepi (deckle edge) dengan panjang gelombang
 *     ~0.062 x tinggi huruf, persis seperti di video.
 *  3. **Ribbon mesh**: dari tiap titik busur dibuat dua titik offset normal
 *     lalu dirangkai jadi pita tertutup. Karena paturnya ikut, pita otomatis
 *     menutup cekungan huruf.
 *
 * Angka default berasal dari pengukuran frame video (tinggi huruf H):
 *  `strokeW = 0.065 H`, `outlineW = 0.38 strokeW`, `keylineW = 0.14 strokeW`,
 *  `teeth = 0.062 H`, `roughOutline = 0.34`, `roughInk = 0.06`.
 */
object SfxInk {

    // ------------------------------------------------------------------
    // PRNG deterministik: goresan/huruf yang sama SELALU menghasilkan
    // tepi yang sama (undo/redo & render ulang tak mengubah tampilan).
    // ------------------------------------------------------------------
    class XorShift64(seed: Long) {
        private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

        fun nextLong(): Long {
            var x = s
            x = x xor (x shl 13)
            x = x xor (x ushr 7)
            x = x xor (x shl 17)
            s = x
            return x
        }

        fun nextFloat(): Float =
            ((nextLong() ushr 11).toDouble() / 9007199254740992.0).toFloat()

        fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()
    }

    // ------------------------------------------------------------------
    // Profil tepi: white noise dihaluskan 2 skala (gigi besar + kecil).
    // ------------------------------------------------------------------
    object EdgeProfile {
        fun build(n: Int, seed: Long, teeth: Int): FloatArray {
            if (n <= 0) return FloatArray(0)
            val rnd = XorShift64(seed)
            val raw = FloatArray(n) { rnd.range(-1f, 1f) }
            val k = max(1, teeth)
            val fine = boxBlur(raw, k)
            val coarse = boxBlur(raw, k * 3)
            return FloatArray(n) { 0.62f * coarse[it] + 0.38f * fine[it] }
        }

        private fun boxBlur(src: FloatArray, k: Int): FloatArray {
            val n = src.size
            if (k <= 1) return src.copyOf()
            val out = FloatArray(n)
            val half = k / 2
            var acc = 0f
            for (i in -half..half) acc += src[i.coerceIn(0, n - 1)]
            val inv = 1f / (2 * half + 1)
            for (i in 0 until n) {
                out[i] = acc * inv
                acc -= src[(i - half).coerceIn(0, n - 1)]
                acc += src[(i + half + 1).coerceIn(0, n - 1)]
            }
            return out
        }

        fun sample(p: FloatArray, s: Float, ds: Float): Float {
            if (p.isEmpty() || ds <= 0f) return 0f
            val x = (s / ds).coerceIn(0f, (p.size - 1).toFloat())
            val i = x.toInt()
            val f = x - i
            val a = p[i]
            val b = p[min(i + 1, p.size - 1)]
            return a + (b - a) * f
        }
    }

    /**
     * Parameter tampilan tinta SFX. Semua nilai dalam **piksel kanvas**
     * (bukan relatif) supaya perhitungannyacheap dan deterministik.
     */
    data class InkSpec(
        /** Lebar badan goresan / tebal huruf. */
        val strokeWidth: Float,
        /** Tebal outline putih di luar tepi (0 = tanpa outline). */
        val outlineWidth: Float = 0f,
        val outlineColor: Int = 0xFFF2EFE2.toInt(),
        /** Keyline gelap tipis tepat di luar outline (0 = tanpa). */
        val keylineWidth: Float = 0f,
        val keylineColor: Int = 0xFF1A1A1E.toInt(),
        val inkColor: Int = Color.BLACK,
        /** Amplitudo gigi tepi outline (fraksi dari outlineWidth). */
        val roughOutline: Float = 0.34f,
        /** Amplitudo gigi tepi isi (kecil: isi tetap SOLID ala video). */
        val roughInk: Float = 0.06f,
        /** Panjang gelombang gigi tepi dalam piksel. */
        val teeth: Float = 6f,
        /** Jarak sampling (fraksi dari strokeWidth). */
        val spacing: Float = 0.10f,
        /** Isi pakai gradien vertikal (Gradient Overlay di video). */
        val gradient: Boolean = false,
        val gradientDarken: Float = 0.42f,
        /** Jumlah percikan (spatter); 0 = mati. */
        val spatter: Int = 0,
        val alpha: Float = 1f
    ) {
        /** Skalakan semua ukuran ke huruf acuan (tinggi huruf H). */
        fun scaled(h: Float, mul: Float = 1f): InkSpec {
            if (h <= 0f || strokeWidth <= 1e-3f) return this
            val k = (0.065f * h * mul) / strokeWidth
            return copy(
                strokeWidth = strokeWidth * k,
                outlineWidth = outlineWidth * k,
                keylineWidth = keylineWidth * k,
                teeth = teeth * k
            )
        }
    }

    // ------------------------------------------------------------------
    // Pita (ribbon) dari Path + profil half-width.
    // ------------------------------------------------------------------

    /**
     * Bangun pita tertutup di sekitar [path] dengan half-width
     * `halfBase * (1 + rough * profile(s))`. Mengikuti SETIAP kontur, jadi
     * cekungan huruf (counter) ikut diberi outline seperti di video.
     * Mengembalikan null bila path terlalu pendek.
     */
    fun ribbon(
        path: Path,
        halfBase: Float,
        rough: Float,
        profile: FloatArray,
        ds: Float,
        minRatio: Float = 0.35f
    ): Path? {
        if (halfBase <= 0f) return null
        val out = Path()
        val pm = PathMeasure(path, false)
        if (pm.length < 0.5f) return null
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        var contour = 0
        do {
            val len = pm.length
            if (len > 0.5f) {
                val n = (ceil(len / max(0.5f, ds)).toInt()).coerceIn(4, 4096)
                val step = len / n
                val leftX = FloatArray(n + 1)
                val leftY = FloatArray(n + 1)
                val rightX = FloatArray(n + 1)
                val rightY = FloatArray(n + 1)
                for (i in 0..n) {
                    val s = (i * step).coerceAtMost(len - 1e-3f)
                    pm.getPosTan(s, pos, tan)
                    val tx = tan[0]
                    val ty = tan[1]
                    val nx = -ty
                    val ny = tx
                    val p = EdgeProfile.sample(profile, s, ds)
                    val h = max(halfBase * minRatio, halfBase * (1f + rough * p))
                    val px = pos[0]
                    val py = pos[1]
                    leftX[i] = px + nx * h
                    leftY[i] = py + ny * h
                    rightX[i] = px - nx * h
                    rightY[i] = py - ny * h
                }
                out.moveTo(leftX[0], leftY[0])
                for (i in 1..n) out.lineTo(leftX[i], leftY[i])
                for (i in n downTo 0) out.lineTo(rightX[i], rightY[i])
                out.close()
                contour++
            }
        } while (pm.nextContour())
        return if (contour > 0) out else null
    }

    // ------------------------------------------------------------------
    // Render layered untuk TEKS (glyph path).
    // ------------------------------------------------------------------

    /**
     * Gambar satu glyph SFX lengkap: outline putih (tepi bergerigi) ->
     * keyline gelap -> isi tinta (solid / gradien) -> spatter.
     * Path diasumsikan sudah dalam koordinat lokal & sudah diputar caller.
     */
    fun drawGlyph(
        canvas: Canvas,
        glyph: Path,
        spec: InkSpec,
        seed: Long,
        bounds: RectF
    ) {
        val ds = max(0.5f, spec.strokeWidth * spec.spacing)
        val teeth = max(1, spec.teeth.toInt())
        val profOutline = EdgeProfile.build(
            (max(8f, bounds.width() + bounds.height()) / ds).toInt() + 8,
            seed xor 0x5DEECE66DL,
            teeth
        )
        val profInk = EdgeProfile.build(
            (max(8f, bounds.width() + bounds.height()) / ds).toInt() + 8,
            seed xor 0x9E3779B9L,
            teeth
        )
        val a = (spec.alpha * 255f).toInt().coerceIn(0, 255)
        if (a <= 0) return

        // URUTAN PENTING (diperbaiki lewat uji render): pita paling lebar
        // digambar LEBIH DAHULU. Kalau keyline digambar setelah outline putih,
        // keyline menutupi outline dan efek "kertas sobek" hilang.
        // Urutan benar di video: keyline gelap -> outline putih -> isi tinta.
        if (spec.keylineWidth > 0f && spec.outlineWidth > 0f) {
            val rk = ribbon(
                glyph,
                (spec.strokeWidth + spec.outlineWidth * 2f + spec.keylineWidth * 2f) * 0.5f,
                spec.roughOutline * 0.7f, profOutline, ds
            )
            if (rk != null) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = spec.keylineColor
                    this.alpha = (a * 0.85f).toInt().coerceIn(0, 255)
                }
                canvas.drawPath(rk, p)
            }
        }
        // L1: outline putih di luar (pita lebar, tepi bergerigi).
        if (spec.outlineWidth > 0f) {
            val r = ribbon(
                glyph, (spec.strokeWidth + spec.outlineWidth * 2f) * 0.5f,
                spec.roughOutline, profOutline, ds
            )
            if (r != null) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = spec.outlineColor
                    this.alpha = a
                }
                canvas.drawPath(r, p)
            }
        }
        // L0: isi tinta SOLID (video: nol celah di tengah huruf).
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = spec.inkColor
            this.alpha = a
            if (spec.gradient && bounds.height() > 1f) {
                shader = LinearGradient(
                    bounds.left, bounds.top, bounds.left, bounds.bottom,
                    darken(spec.inkColor, spec.gradientDarken), spec.inkColor,
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawPath(glyph, fill)
        // Spatter tipis di sekitar huruf (video: 14-40 titik, sparse).
        if (spec.spatter > 0) {
            drawSpatter(canvas, glyph, spec, seed, bounds, a)
        }
    }

    /** Percikan tinta di sekitar glyph, center-biased (ala Krita Spray). */
    private fun drawSpatter(
        canvas: Canvas,
        glyph: Path,
        spec: InkSpec,
        seed: Long,
        bounds: RectF,
        alpha: Int
    ) {
        val rnd = XorShift64(seed xor 0x2545F4914F6CDD1DL)
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val rMax = max(6f, max(bounds.width(), bounds.height()) * 0.55f)
        val region = android.graphics.Region()
        val clip = android.graphics.Region(0, 0, 1, 1)
        val ok = runCatching { region.setPath(glyph, clip) }.isSuccess
        val dotMin = max(0.6f, spec.strokeWidth * 0.08f)
        val dotMax = max(1.5f, spec.strokeWidth * 0.34f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = spec.inkColor
        }
        var placed = 0
        var guard = 0
        while (placed < spec.spatter && guard < spec.spatter * 6) {
            guard++
            val th = rnd.nextFloat() * 2f * PI.toFloat()
            // cluster > 1 = cenderung ke tengah
            val rad = rMax * rnd.nextFloat().pow(1f / 2.2f)
            val px = cx + rad * cos(th)
            val py = cy + rad * sin(th)
            if (ok && region.contains(px.toInt(), py.toInt())) continue
            val rr = dotMin + (dotMax - dotMin) * rnd.nextFloat() * rnd.nextFloat()
            val fade = (1f - rad / rMax).coerceIn(0f, 1f)
            p.alpha = (alpha * 0.85f * fade * (0.45f + 0.55f * rnd.nextFloat()))
                .toInt().coerceIn(0, 255)
            if (p.alpha <= 2) continue
            canvas.drawCircle(px, py, rr, p)
            placed++
        }
    }

    // ------------------------------------------------------------------
    // Render untuk KUAS (coretan yang sedang digambar).
    // ------------------------------------------------------------------

    /**
     * Pita tinta untuk satu goresan bebas (titik-titik yang sudah terkumpul).
     * Dipakai BrushEngine supaya badan goresan SFX berpola pita, bukan
     * tumpukan lingkaran. Lebar tiap titik boleh berbeda (dinamika sapuan).
     */
    fun strokeRibbon(
        xs: FloatArray,
        ys: FloatArray,
        halfAt: FloatArray,
        minRatio: Float = 0.3f
    ): Path? {
        val n = min(xs.size, ys.size)
        if (n < 2 || halfAt.size < n) return null
        val out = Path()
        var drawn = false
        for (i in 0 until n) {
            val px: Float
            val py: Float
            if (i == 0) {
                val dx = xs[1] - xs[0]
                val dy = ys[1] - ys[0]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            } else if (i == n - 1) {
                val dx = xs[n - 1] - xs[n - 2]
                val dy = ys[n - 1] - ys[n - 2]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            } else {
                val dx = xs[i + 1] - xs[i - 1]
                val dy = ys[i + 1] - ys[i - 1]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            }
            val h = max(halfAt[i] * minRatio, halfAt[i])
            val bx = xs[i] + px * h
            val by = ys[i] + py * h
            if (!drawn) {
                out.moveTo(bx, by)
                drawn = true
            } else {
                out.lineTo(bx, by)
            }
        }
        for (i in n - 1 downTo 0) {
            val px: Float
            val py: Float
            if (i == 0) {
                val dx = xs[1] - xs[0]
                val dy = ys[1] - ys[0]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            } else if (i == n - 1) {
                val dx = xs[n - 1] - xs[n - 2]
                val dy = ys[n - 1] - ys[n - 2]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            } else {
                val dx = xs[i + 1] - xs[i - 1]
                val dy = ys[i + 1] - ys[i - 1]
                val len = hypot(dx, dy).coerceAtLeast(1e-3f)
                px = -dy / len
                py = dx / len
            }
            val h = max(halfAt[i] * minRatio, halfAt[i])
            out.lineTo(xs[i] - px * h, ys[i] - py * h)
        }
        out.close()
        return if (drawn) out else null
    }

    /**
     * Komposit outline di ATAS goresan yang sudah terlukis: pita putih lebar
     * digambar ke bitmap sementara, lalu bagian dalam goresan dilubangi
     * (DST_IN) supaya hanya cincin outline yang tersisa. Hasilnya ditempel ke
     * kanvas - inilah teknik "Mesh Redraw" (layer style Stroke) di video.
     */
    fun compositeOutline(
        canvas: Canvas,
        inkPath: Path,
        spec: InkSpec,
        seed: Long,
        clip: RectF,
        /** Pita dalam yang sudah jadi (hemat rebuild; opsional). */
        innerHole: Path? = null
    ) {
        if (spec.outlineWidth <= 0f) return
        val pad = (spec.strokeWidth + spec.outlineWidth * 2f + spec.keylineWidth * 2f) * 1.5f
        val l = (clip.left - pad).toInt().coerceAtLeast(0)
        val t = (clip.top - pad).toInt().coerceAtLeast(0)
        val r = (clip.right + pad).toInt()
        val b = (clip.bottom + pad).toInt()
        if (r <= l || b <= t) return
        val w = (r - l).coerceAtMost(6000)
        val h = (b - t).coerceAtMost(6000)
        if (w < 2 || h < 2) return
        val tmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            val c = Canvas(tmp)
            c.translate(-l.toFloat(), -t.toFloat())
            val local = RectF(clip.left, clip.top, clip.right, clip.bottom)
            // 1) Keyline gelap (paling luar) dulu.
            if (spec.keylineWidth > 0f) {
                val ds = max(0.5f, spec.strokeWidth * spec.spacing)
                val prof = EdgeProfile.build(
                    ((max(8f, clip.width() + clip.height()) / ds).toInt() + 8).coerceIn(8, 20000),
                    seed xor 0x5DEECE66DL, max(1, spec.teeth.toInt())
                )
                val rk = ribbon(
                    inkPath,
                    (spec.strokeWidth + spec.outlineWidth * 2f + spec.keylineWidth * 2f) * 0.5f,
                    spec.roughOutline * 0.7f, prof, ds
                )
                if (rk != null) {
                    c.drawPath(rk, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = spec.keylineColor
                    })
                }
            }
            // 2) Outline putih.
            val ds2 = max(0.5f, spec.strokeWidth * spec.spacing)
            val prof2 = EdgeProfile.build(
                ((max(8f, clip.width() + clip.height()) / ds2).toInt() + 8).coerceIn(8, 20000),
                seed xor 0x5DEECE66DL, max(1, spec.teeth.toInt())
            )
            val ro = ribbon(
                inkPath,
                (spec.strokeWidth + spec.outlineWidth * 2f) * 0.5f,
                spec.roughOutline, prof2, ds2
            )
            if (ro != null) {
                c.drawPath(ro, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = spec.outlineColor
                })
            }
            // 3) Lubangi bagian dalam goresan (hanya cincin yang tersisa).
            val hole = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (hole != null) {
                val hc = Canvas(hole)
                hc.translate(-l.toFloat(), -t.toFloat())
                val ri: Path? = innerHole ?: run {
                    val ds3 = max(0.5f, spec.strokeWidth * spec.spacing)
                    val prof3 = EdgeProfile.build(
                        ((max(8f, clip.width() + clip.height()) / ds3).toInt() + 8)
                            .coerceIn(8, 20000),
                        seed xor 0x9E3779B9L, max(1, spec.teeth.toInt())
                    )
                    ribbon(
                        inkPath,
                        (spec.strokeWidth * 0.5f + 0.5f) * 1.02f,
                        spec.roughInk, prof3, ds3
                    )
                }
                if (ri != null) {
                    hc.drawPath(ri, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    })
                }
                c.drawBitmap(
                    hole, 0f, 0f,
                    Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                )
                hole.recycle()
            }
            canvas.drawBitmap(tmp, l.toFloat(), t.toFloat(), null)
        } finally {
            tmp.recycle()
        }
    }

    /** Gelapkan warna (untuk Gradient Overlay di video). */
    fun darken(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * (1f - f)).toInt().coerceIn(0, 255),
        (Color.green(color) * (1f - f)).toInt().coerceIn(0, 255),
        (Color.blue(color) * (1f - f)).toInt().coerceIn(0, 255)
    )

    /** hitung tinggi huruf (cap height) dari sebuah path. */
    fun capHeightOf(path: Path): Float {
        val r = RectF()
        path.computeBounds(r, true)
        return max(1f, r.height())
    }

    /** Panjang busur total semua kontur. */
    fun arcLengthOf(path: Path): Float {
        var total = 0f
        val pm = PathMeasure(path, false)
        do {
            total += pm.length
        } while (pm.nextContour())
        return total
    }

    /** Jarak titik ke segmen (untuk sebaran spatter). */
    fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-6f) return hypot(px - ax, py - ay)
        var t = ((px - ax) * vx + (py - ay) * vy) / len2
        t = t.coerceIn(0f, 1f)
        return hypot(px - (ax + vx * t), py - (ay + vy * t))
    }

}
