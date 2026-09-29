package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class TextAlignMode { LEFT, CENTER, RIGHT }

enum class TextHandle { NONE, BODY, SCALE, ROTATE, WIDTH_LEFT, WIDTH_RIGHT, FRAME }

/**
 * Perspektif bebas: tiap sudut konten digeser sendiri (satuan dinormalisasi
 * terhadap setengah lebar/tinggi). 0 = kotak lurus, 0.4 = geser 40% dari
 * setengah sisi. Nilai di luar -1..1 tetap diizinkan (bisa 1.6) untuk efek
 * dramatis. Hasilnya homografi 4 titik, jadi ARAH perspektif bisa diatur
 * leluasa - bukan lagi hanya menyempitkan tepi atas/bawah seperti versi lama.
 */
data class PerspSpec(
    var tlX: Float = 0f,
    var tlY: Float = 0f,
    var trX: Float = 0f,
    var trY: Float = 0f,
    var brX: Float = 0f,
    var brY: Float = 0f,
    var blX: Float = 0f,
    var blY: Float = 0f
) {
    /**
     * Jaga agar semua offset tetap FINITE dan di dalam rentang yang dipakai
     * slider panel (-1.2..1.2). Dua hal ini penting:
     *  - NaN/Inf: `coerceIn` mengembalikan NaN apa adanya, lalu Matrix dan
     *    Canvas menerima NaN dan aplikasi bisa menutup paksa.
     *  - Nilai di luar rentang slider: Slider Material melempar exception
     *    saat nilainya di luar valueRange. Inilah sumber "grid perspektif
     *    bikin force close": sudut digeser lewat kanvas (batas 1.2) lalu
     *    panel dibuka dengan nilai lama di luar rentang.
     */
    fun clampAll() {
        tlX = sane(tlX); tlY = sane(tlY)
        trX = sane(trX); trY = sane(trY)
        brX = sane(brX); brY = sane(brY)
        blX = sane(blX); blY = sane(blY)
    }

    /** Nilai aman untuk slider & matriks: bukan NaN/Inf dan dalam batas. */
    private fun sane(v: Float): Float {
        if (!v.isFinite()) return 0f
        return v.coerceIn(MIN_OFFSET, MAX_OFFSET)
    }

    /** Salinan yang sudah dibersihkan (dipakai renderer & overlay). */
    fun sanitized(): PerspSpec {
        val c = PerspSpec()
        c.tlX = sane(tlX); c.tlY = sane(tlY)
        c.trX = sane(trX); c.trY = sane(trY)
        c.brX = sane(brX); c.brY = sane(brY)
        c.blX = sane(blX); c.blY = sane(blY)
        return c
    }

    fun isFlat(): Boolean = tlX == 0f && tlY == 0f && trX == 0f && trY == 0f &&
        brX == 0f && brY == 0f && blX == 0f && blY == 0f

    fun copyFrom(o: PerspSpec) {
        tlX = o.tlX; tlY = o.tlY; trX = o.trX; trY = o.trY
        brX = o.brX; brY = o.brY; blX = o.blX; blY = o.blY
    }

    /** Salinan bebas (dipakai panel saat menggeser satu sudut). */
    fun copyAll(): PerspSpec = PerspSpec(
        tlX, tlY, trX, trY, brX, brY, blX, blY
    )

    companion object {
        /** Batas offset sudut (dalam fraksi setengah lebar/tinggi). */
        const val MIN_OFFSET = -1.2f
        const val MAX_OFFSET = 1.2f

        /** Dari keystone lama (perspX = tepi atas/bawah, perspY = kiri/kanan). */
        fun fromKeystone(perspX: Float, perspY: Float): PerspSpec {
            val x = perspX.coerceIn(-1f, 1f)
            val y = perspY.coerceIn(-1f, 1f)
            return PerspSpec(
                tlX = x, tlY = y,
                trX = -x, trY = -y,
                brX = x, brY = y,
                blX = -x, blY = -y
            ).sanitized()
        }
    }
}

/**
 * Gaya khusus satu rentang karakter di dalam teks (kata demi kata).
 * Semua field nullable: null = pakai gaya kotak (bawaan). Inilah yang
 * membuat "Ayo main sama om" bisa punya kata berbeda-beda: Ayo dan main
 * serif merah bold + shadow putih, sedangkan sama dan om script putih
 * italic + outline hitam.
 */
data class SpanStyle(
    var fontName: String? = null,
    var typeface: Typeface? = null,
    /** Pengali ukuran font (1.2 = 20 persen lebih besar). */
    var fontSizeMul: Float = 1f,
    var color: Int? = null,
    var bold: Boolean? = null,
    var italic: Boolean? = null,
    var outlineWidth: Float? = null,
    var outlineColor: Int? = null,
    var shadowColor: Int? = null,
    var shadowDx: Float? = null,
    var shadowDy: Float? = null,
    var shadowBlur: Float? = null,
    var uppercase: Boolean? = null,
    var underline: Boolean? = null,
    var strikethrough: Boolean? = null
) {
    /** True bila tak ada satu pun override (span tak berpengaruh). */
    fun isBlank(): Boolean = fontName == null && typeface == null &&
        fontSizeMul == 1f && color == null && bold == null && italic == null &&
        outlineWidth == null && outlineColor == null && shadowColor == null &&
        shadowDx == null && shadowDy == null && shadowBlur == null &&
        uppercase == null && underline == null && strikethrough == null

    /** Gabung override di atas [base] (field yang terisi menang). */
    fun over(base: SpanStyle): SpanStyle {
        val a = this
        return SpanStyle(
            fontName = a.fontName ?: base.fontName,
            typeface = a.typeface ?: base.typeface,
            fontSizeMul = if (a.fontSizeMul != 1f) a.fontSizeMul else base.fontSizeMul,
            color = a.color ?: base.color,
            bold = a.bold ?: base.bold,
            italic = a.italic ?: base.italic,
            outlineWidth = a.outlineWidth ?: base.outlineWidth,
            outlineColor = a.outlineColor ?: base.outlineColor,
            shadowColor = a.shadowColor ?: base.shadowColor,
            shadowDx = a.shadowDx ?: base.shadowDx,
            shadowDy = a.shadowDy ?: base.shadowDy,
            shadowBlur = a.shadowBlur ?: base.shadowBlur,
            uppercase = a.uppercase ?: base.uppercase,
            underline = a.underline ?: base.underline,
            strikethrough = a.strikethrough ?: base.strikethrough
        )
    }
}

/**
 * Parameter gaya "tinta SFX" untuk teks. Semua ukuran dalam fraksi tinggi
 * huruf (H) mengikuti hasil ukur video: `strokeW = 0.065 H`,
 * `outlineW = 0.38 strokeW`, `keylineW = 0.14 strokeW`, `teeth = 0.062 H`.
 */
data class SfxInkSpec(
    /** Tulis huruf miring ala brush script (-15..-25 derajat). */
    var tiltDeg: Float = -18f,
    var strokeRatio: Float = 0.065f,
    var outlineRatio: Float = 0.38f,
    var keylineRatio: Float = 0.14f,
    var outlineColor: Int = 0xFFF2EFE2.toInt(),
    var keylineColor: Int = 0xFF1A1A1E.toInt(),
    var roughOutline: Float = 0.34f,
    var teethRatio: Float = 0.062f,
    var gradient: Boolean = true,
    var gradientDarken: Float = 0.42f,
    var spatter: Int = 0,
    /** Seed supaya tepihuruf yang sama selalu sama. */
    var seed: Int = 1
)

/** Rentang [start, end) pada [TextBox.text] yang punya [style] sendiri. */
data class TextSpan(val start: Int, val end: Int, val style: SpanStyle)

enum class TextFillType { SOLID, GRADIENT }

enum class StrokePosition { OUTSIDE, CENTER, INSIDE }

data class TextShadowSpec(
    var dx: Float = 4f,
    var dy: Float = 4f,
    var blur: Float = 8f,
    var color: Int = 0x80000000.toInt(),
    // Photoshop-like: opacity 0..1, spread/choke 0..100
    var opacity: Float = 0.75f,
    var spread: Float = 0f
)

data class TextGradientSpec(
    var colorStart: Int = android.graphics.Color.WHITE,
    var colorEnd: Int = android.graphics.Color.parseColor("#FF5722"),
    // 0° = kiri→kanan, 90° = atas→bawah
    var angle: Float = 90f
)

data class TextGlowSpec(
    var color: Int = 0xFFFFEE58.toInt(),
    var blur: Float = 14f,
    var spread: Float = 0f,
    var opacity: Float = 0.75f
)

data class TextBevelSpec(
    var size: Float = 2f,
    var opacity: Float = 0.8f
)

/**
 * Mode SFX untuk teks: tiap huruf diletakkan di sepanjang busur parabola
 * dengan ukuran & rotasi jitter — alih-alih baris lurus seperti font diketik
 * (lihat teknik lettering SFX manual: WHOOSH melengkung, huruf besar-acak).
 * Semua nilai deterministik dari [seed] agar render ulang/undo identik.
 *
 * @param arc tinggi busur dalam fraksi tinggi font (+ = busur ke atas, - = ke bawah).
 * @param sizeJitter variasi ukuran per huruf, fraksi (0.25 = ±25%).
 * @param rotJitter rotasi acak maksimum per huruf, derajat.
 * @param tilt kemiringan SELURUH kata, derajat (huruf miring serigala ala
 *   coretan ledakan — beda dari busur yang hanya melengkung).
 * @param wave amplitudo gelombang sinus per huruf, fraksi tinggi font
 *   (0 = tidak ada; isi combo dengan arc 0 untuk gaya zigzag "DUAR").
 * @param seed sumber acak deterministik (ganti = susunan baru).
 */
data class SfxSpec(
    var arc: Float = 0.55f,
    var sizeJitter: Float = 0.25f,
    var rotJitter: Float = 10f,
    var tilt: Float = 0f,
    var wave: Float = 0f,
    var seed: Int = 1
)

/**
 * Satu kotak teks yang posisinya adalah TITIK TENGAH (center) blok teks
 * dalam koordinat piksel kanvas. Rotasi berputar mengelilingi [position]
 * sehingga terasa natural saat digeser dengan jari.
 */
class TextBox(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "Teks baru",
    var position: Offset = Offset(640f, 640f),
    var fontSize: Float = 64f,
    var color: Int = android.graphics.Color.WHITE,
    var bold: Boolean = true,
    var italic: Boolean = false,
    var align: TextAlignMode = TextAlignMode.CENTER,
    var outlineWidth: Float = 0f,
    var outlineColor: Int = android.graphics.Color.BLACK,
    var strokeOpacity: Float = 1f,
    var strokePosition: StrokePosition = StrokePosition.OUTSIDE,
    var fillType: TextFillType = TextFillType.SOLID,
    var gradient: TextGradientSpec = TextGradientSpec(),
    var shadow: TextShadowSpec? = TextShadowSpec(),
    var letterSpacing: Float = 0f,
    var wordSpacing: Float = 0f,
    var lineSpacing: Float = 12f,
    var textOpacity: Float = 1f,
    var uppercase: Boolean = false,
    var underline: Boolean = false,
    var strikethrough: Boolean = false,
    var glow: TextGlowSpec? = null,
    var bevel: TextBevelSpec? = null,
    var scale: Float = 1f,
    // Skala horizontal glif (menyempitkan teks TANPA memotong kata).
    // 1f = normal, <1f = sempit (mis. 0.6f). Dipakai otomatis oleh fitToRect
    // dan manual lewat slider "Sempitkan teks" di panel teks.
    var textScaleX: Float = 1f,
    var rotation: Float = 0f,
    // Perspektif teks (keystone ala free-transform): -1f..1f, 0 = datar.
    // perspX > 0: tepi atas menyempit (teks "menjauh" ke atas),
    // perspX < 0: tepi bawah menyempit.
    // perspY > 0: tepi kiri menyempit, perspY < 0: tepi kanan menyempit.
    var perspX: Float = 0f,
    var perspY: Float = 0f,
    var fontName: String = "Default Bold",
    var typeface: Typeface = Typeface.DEFAULT_BOLD,
    /**
     * Lebar kotak paragraph dalam px kanvas pada scale=1 (unscaled).
     * null = point-text (ukuran mengikuti isi, perilaku lama).
     * non-null = paragraph-text ala Photoshop/IbisPaint: teks di-wrap
     * otomatis ke [boxWidth], sehingga menyempitkan box membuat teks
     * berbaris/berparagraph, bukan mengecilkan font.
     * Lebar aktual di kanvas = boxWidth * scale.
     * Kombinasi dengan [textScaleX]: condense mempersempit glif,
     * paragraph mempersempit box + wrap (keduanya bisa aktif bersamaan).
     */
    var boxWidth: Float? = null,
    // Tinggi kotak paragraph (px kanvas pada scale=1). Ada bersama
    // [boxWidth] = KOTAK SELEKSI ala Photoshop: teks di-wrap ke lebar
    // dan (bila [autoFit]) ukuran font dikecilkan sampai muat di tinggi.
    var boxHeight: Float? = null,
    // Auto-fit: font mengecil sampai seluruh teks muat di dalam kotak.
    var autoFit: Boolean = false,
    // Perspektif bebas 4 sudut (null = pakai keystone perspX/perspY).
    var persp: PerspSpec? = null,
    // Gaya per rentang kata (lihat SpanStyle/TextSpan). null/empty = teks
    // satu gaya seperti biasa.
    var spans: List<TextSpan>? = null,
    // Mode SFX (lihat SfxSpec): null = teks baris biasa.
    var sfx: SfxSpec? = null,
    // Gaya "tinta SFX" ala video lettering: isi padat + tepi bergerigi +
    // outline putih yang mengikuti cekungan huruf (lihat SfxInk). null = pakai
    // gaya huruf biasa.
    var inkSfx: SfxInkSpec? = null
) {
    fun isParagraph(): Boolean = boxWidth != null

    /** Kotak seleksi Photoshop (lebar + tinggi + auto-fit)? */
    fun isFrame(): Boolean = boxWidth != null && boxHeight != null && autoFit

    /** True bila ada gaya per kata yang perlu renderer kaya. */
    fun hasSpans(): Boolean = !spans.isNullOrEmpty()

    /** Perspektif aktif: eksplisit bila ada, kalau tidak dari keystone lama. */
    fun activePersp(): PerspSpec =
        persp ?: PerspSpec.fromKeystone(perspX, perspY)

    /** Sinkronkan persp eksplisit dengan keystone lama (dipakai slider cepat). */
    fun setPerspFromKeystone(x: Float, y: Float) {
        perspX = x.coerceIn(-1f, 1f)
        perspY = y.coerceIn(-1f, 1f)
        persp = PerspSpec.fromKeystone(perspX, perspY)
    }

    /** Ubah ke kotak seleksi (lebar x tinggi) di pusat [center]. */
    fun setFrame(center: Offset, wCanvas: Float, hCanvas: Float) {
        val safeScale = scale.coerceAtLeast(0.05f)
        position = center
        boxWidth = (wCanvas / safeScale).coerceIn(24f, 8000f)
        boxHeight = (hCanvas / safeScale).coerceIn(12f, 8000f)
        autoFit = true
    }

    /** Persegi kotak seleksi dalam koordinat kanvas (null bila bukan frame). */
    fun frameRectPx(): RectF? {
        val bw = boxWidth
        val bh = boxHeight
        if (bw == null || bh == null) return null
        val w = bw * scale
        val h = bh * scale
        return RectF(position.x - w / 2f, position.y - h / 2f, position.x + w / 2f, position.y + h / 2f)
    }

    /** Ubah teks dengan memetakan ulang span lewat diff prefix/suffix. */
    fun setTextKeepingSpans(newText: String) {
        if (newText == text) return
        val old = text
        if (spans.isNullOrEmpty()) {
            text = newText
            return
        }
        var pre = 0
        val maxPre = minOf(old.length, newText.length)
        while (pre < maxPre && old[pre] == newText[pre]) pre++
        var suf = 0
        while (suf < maxPre - pre &&
            old[old.length - 1 - suf] == newText[newText.length - 1 - suf]
        ) suf++
        val delta = newText.length - old.length
        val mapped = ArrayList<TextSpan>(spans!!.size)
        for (sp in spans!!) {
            val s = if (sp.start >= old.length - suf) sp.start + delta else sp.start
            val e = if (sp.end >= old.length - suf) sp.end + delta else sp.end
            val ns = s.coerceIn(0, newText.length)
            val ne = e.coerceIn(ns, newText.length)
            if (ne > ns) mapped.add(TextSpan(ns, ne, sp.style))
        }
        spans = mapped
        text = newText
    }

    /** Style pada posisi karakter [i] (null = pakai gaya kotak). */
    fun spanAt(i: Int): SpanStyle? {
        val list = spans ?: return null
        for (sp in list) if (i >= sp.start && i < sp.end) return sp.style
        return null
    }

    /**
     * Terapkan [style] ke rentang [start]..[endExclusive] (kata terpilih),
     * memotong span yang ada. Gaya kotak jadi dasar, jadi "hapus format
     * kata" cukup mengosongkan override.
     */
    fun applySpanStyle(start: Int, endExclusive: Int, style: SpanStyle) {
        val s = start.coerceIn(0, text.length)
        val e = endExclusive.coerceIn(s, text.length)
        if (e <= s) return
        val kept = ArrayList<TextSpan>()
        for (sp in spans ?: emptyList()) {
            // Potong bagian di luar [s, e).
            if (sp.end <= s || sp.start >= e) {
                kept.add(sp)
                continue
            }
            if (sp.start < s) kept.add(TextSpan(sp.start, s, sp.style))
            if (sp.end > e) kept.add(TextSpan(e, sp.end, sp.style))
        }
        if (!style.isBlank()) {
            kept.add(TextSpan(s, e, style))
        }
        kept.sortBy { it.start }
        spans = if (kept.isEmpty()) null else kept
    }

    /** Buang seluruh gaya per kata (kembali satu gaya). */
    fun clearSpans() {
        spans = null
    }

    /** Daftar kata (rentang karakter) dari teks aktif. */
    fun wordRanges(): List<IntRange> {
        val out = ArrayList<IntRange>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == ' ' || c == '　' || c == '\n' || c == '\t') {
                i++
                continue
            }
            val st = i
            while (i < n) {
                val d = text[i]
                if (d == ' ' || d == '　' || d == '\n' || d == '\t') break
                i++
            }
            out.add(st until i)
        }
        return out
    }

    /** Ubah ke paragraph dengan lebar awal yang aman (tidak mengubah tampilan). */
    fun enableParagraph(fallbackWidth: Float = 600f) {
        if (boxWidth != null) return
        val curW = try {
            val (w, _) = contentSizePoint()
            w / scale.coerceAtLeast(0.2f)
        } catch (e: Exception) { fallbackWidth }
        boxWidth = curW.coerceIn(40f, 4000f).takeIf { it.isFinite() } ?: fallbackWidth
        if (boxWidth!! < 80f) boxWidth = 80f
    }

    fun disableParagraph() {
        boxWidth = null
    }
    fun effectiveTypeface(): Typeface {
        val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
        return try {
            Typeface.create(typeface, style) ?: typeface
        } catch (e: Exception) {
            typeface
        }
    }

    fun basePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize * scale
        typeface = effectiveTypeface()
        this.textScaleX = textScaleX.coerceIn(0.3f, 1f)
    }

    /** Teks yang tampil (kapital bila opsi menyala; asli tetap tersimpan). */
    fun displayText(): String = if (uppercase) text.uppercase() else text

    /**
     * Pas-kan box ke dalam [rect] (gaya TypeR auto-fit): posisi ke tengah, lalu
     * ukuran font dikecilkan sampai muat. Bila font sudah mentok di lantai dan
     * teks masih terlalu lebar, teks DISEMPITKAN secara horizontal lewat
     * [textScaleX] (tanpa mematahkan kata/baris). Satu arah (mengecil) saja.
     * Bila paragraph: lebar box mengikuti rect dulu agar wrap,
     * lalu hanya tinggi yang di-fit via font/condense.
     * Dijamin (best-effort): hasil akhir di dalam [rect], termasuk efek
     * outline/shadow/glow (tanpa margin sentuh). Rotasi di-reset ke 0 agar
     * sudut tidak menyembul keluar bubble/seleksi.
     */
    fun fitToRect(
        rect: RectF,
        fill: Float = 0.92f,
        minFont: Float = 10f,
        minCondense: Float = 0.6f
    ) {
        if (rect.width() <= 4f || rect.height() <= 4f) return
        position = Offset(rect.centerX(), rect.centerY())
        rotation = 0f
        // Selalu hitung ulang dari keadaan normal agar hasil-fit konsisten.
        textScaleX = 1f
        val safeScale = scale.coerceAtLeast(0.2f)
        // Ruang usable = rect*fill dikurangi pad visual (efek) di tiap sisi,
        // sehingga glif + outline/shadow/glow tetap di dalam rect.
        val pad = visualPad()
        val usableW = (rect.width() * fill - 2f * pad).coerceAtLeast(20f)
        val usableH = (rect.height() * fill - 2f * pad).coerceAtLeast(20f)
        // Teks panjang multi-kata langsung jadi paragraph agar wrap terbaca
        // (bukan menyusut hingga tak terbaca sebagai satu baris point).
        if (!isParagraph()) {
            val (pw, _) = contentSizePoint()
            if (pw > usableW && (text.contains(' ') || text.contains('　') || text.length > 12)) {
                val target = (usableW / safeScale).coerceIn(40f, 4000f)
                if (target.isFinite()) boxWidth = target
            }
        }
        if (isParagraph()) {
            val targetW = (usableW / safeScale).coerceIn(40f, 4000f)
            if (targetW.isFinite()) boxWidth = targetW
        }
        var guard = 0
        while (guard++ < 160) {
            val (w, h) = contentSize()
            if (w <= usableW && h <= usableH) break
            if (fontSize > minFont) {
                // 1) Kecilkan font dulu.
                fontSize = max(minFont, fontSize * 0.92f)
            } else if (textScaleX > minCondense + 0.01f) {
                // 2) Font mentok: sempitkan glif agar tetap muat (baris utuh).
                textScaleX = max(minCondense, textScaleX * 0.92f)
            } else {
                // 3) Sudah paling sempit pada batas normal; lanjut ke fallback.
                break
            }
        }
        // Fallback terakhir agar teks tidak keluar bubble/seleksi:
        // bila point satu kata sangat panjang, jadikan paragraph; bila masih
        // berlebih (skrip sangat panjang), kecilkan font hingga batas absolut.
        val (w, h) = contentSize()
        if (w <= usableW && h <= usableH) return
        if (!isParagraph()) {
            val target = (usableW / safeScale).coerceIn(40f, 4000f)
            if (target.isFinite()) boxWidth = target
        }
        var guard2 = 0
        while (guard2++ < 80) {
            val (w2, h2) = contentSize()
            if (w2 <= usableW && h2 <= usableH) break
            if (fontSize > 4f) {
                fontSize = max(4f, fontSize * 0.92f)
            } else if (textScaleX > 0.3f) {
                textScaleX = max(0.3f, textScaleX * 0.94f)
            } else {
                break
            }
        }
    }

    /** Tiru gaya visual [o] (kecuali id/teks/posisi/skala/rotasi). */
    fun applyStyleFrom(o: TextBox) {
        fontSize = o.fontSize
        color = o.color
        bold = o.bold
        italic = o.italic
        align = o.align
        outlineWidth = o.outlineWidth
        outlineColor = o.outlineColor
        strokeOpacity = o.strokeOpacity
        strokePosition = o.strokePosition
        fillType = o.fillType
        gradient = o.gradient.copy()
        shadow = o.shadow?.copy()
        letterSpacing = o.letterSpacing
        wordSpacing = o.wordSpacing
        lineSpacing = o.lineSpacing
        textOpacity = o.textOpacity
        uppercase = o.uppercase
        underline = o.underline
        strikethrough = o.strikethrough
        glow = o.glow?.copy()
        bevel = o.bevel?.copy()
        textScaleX = o.textScaleX
        perspX = o.perspX
        perspY = o.perspY
        fontName = o.fontName
        typeface = o.typeface
    }

    /** Tinggi satu baris dalam px kanvas (dijaga >= 4 agar bounds/hit-test valid saat spacing minus). */
    fun lineHeightPx(): Float {
        val fm = basePaint().fontMetrics
        return max(4f, (fm.descent - fm.ascent) + lineSpacing * scale)
    }

    /** Ukuran konten point-text (tanpa wrap), dalam px kanvas. */
    private fun contentSizePoint(): Pair<Float, Float> {
        val paint = basePaint()
        val lines = displayText().split("\n").ifEmpty { listOf("") }
        var maxW = 0f
        for (line in lines) {
            val w = spacedWidth(paint, line, letterSpacing * scale, wordSpacing * scale)
            if (w > maxW) maxW = w
        }
        return maxW to lineHeightPx() * lines.size
    }

    /**
     * Baris efektif untuk render/ukur: bila paragraph, tiap paragraph
     * (\n) di-wrap ke [boxWidth] ala Photoshop/IbisPaint.
     */
    fun wrappedLines(): List<String> {
        val rawParas = displayText().split("\n")
        val bw = boxWidth ?: return rawParas.ifEmpty { listOf("") }
        if (!bw.isFinite() || bw <= 0f) return rawParas.ifEmpty { listOf("") }
        val maxW = bw * scale
        if (maxW <= 10f) return rawParas.ifEmpty { listOf("") }
        val paint = basePaint()
        val extra = letterSpacing * scale
        val wordExtra = wordSpacing * scale
        val out = mutableListOf<String>()
        for (para in rawParas) {
            if (para.isEmpty()) {
                out.add("")
                continue
            }
            out.addAll(wrapParagraph(para, paint, maxW, extra, wordExtra))
        }
        if (out.isEmpty()) out.add("")
        return out
    }

    private fun wrapParagraph(
        para: String,
        paint: Paint,
        maxW: Float,
        extra: Float,
        wordExtra: Float
    ): List<String> {
        if (spacedWidth(paint, para, extra, wordExtra) <= maxW) return listOf(para)
        if (!para.contains(' ') && !para.contains('　')) {
            return breakByChar(para, paint, maxW, extra, wordExtra)
        }
        val words = para.split(' ', '　').filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var cur: String = if (spacedWidth(paint, words[0], extra, wordExtra) > maxW) {
            val broken = breakByChar(words[0], paint, maxW, extra, wordExtra)
            for (i in 0 until broken.size - 1) lines.add(broken[i])
            broken.last()
        } else {
            words[0]
        }
        for (wi in 1 until words.size) {
            val w = words[wi]
            val cand = "$cur $w"
            if (spacedWidth(paint, cand, extra, wordExtra) <= maxW) {
                cur = cand
            } else {
                lines.add(cur)
                cur = if (spacedWidth(paint, w, extra, wordExtra) > maxW) {
                    val broken = breakByChar(w, paint, maxW, extra, wordExtra)
                    for (i in 0 until broken.size - 1) lines.add(broken[i])
                    broken.last()
                } else {
                    w
                }
            }
        }
        lines.add(cur)
        return lines
    }

    private fun breakByChar(
        s: String,
        paint: Paint,
        maxW: Float,
        extra: Float,
        wordExtra: Float
    ): List<String> {
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in s) {
            val test = cur.toString() + ch
            val wExtra = if (ch == ' ' || ch == '　') wordExtra else 0f
            if (cur.isEmpty() || spacedWidth(paint, test, extra, wExtra) <= maxW) {
                cur.append(ch)
            } else {
                lines.add(cur.toString())
                cur.setLength(0)
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty() || lines.isEmpty()) lines.add(cur.toString())
        return lines
    }

    /** Ukuran konten (tanpa padding outline/shadow), dalam px kanvas. */
    fun contentSize(): Pair<Float, Float> {
        // Kotak seleksi: ukuran visual = kotak itu sendiri (seperti PS),
        // bukan isi teks — jadi handle & hit-test stabil walau font mengecil.
        if (isFrame()) {
            val fw = frameRectPx()
            if (fw != null) return fw.width() to fw.height()
        }
        if (hasSpans()) {
            val l = com.grooxtyper.app.model.RichTextLayout.layout(this)
            return l.contentW to l.contentH
        }
        val bw = boxWidth
        if (bw != null && bw.isFinite() && bw > 0f) {
            val w = bw * scale
            return w to lineHeightPx() * wrappedLines().size
        }
        return contentSizePoint()
    }

    /** Padding bounds (outline/shadow/glow + margin sentuh), dalam px kanvas. */
    fun boundsPad(): Float = visualPad() + 16f * scale

    /**
     * Pad visual efek saja (tanpa margin sentuh 16px): outline + blur/shadow
     * + spread + glow + jarak offset bayangan. Dipakai auto-fit agar hasil
     * render (glif + efek) tetap di dalam bubble/seleksi.
     */
    fun visualPad(): Float {
        val glowPad = if (glow != null) (glow!!.blur + glow!!.spread) * scale else 0f
        val s = shadow
        val shadowOffset = if (s != null) hypot(s.dx, s.dy) * scale else 0f
        // SFX: busur mengangkat huruf, gelombang mendongkar, dan tilt memiringkan
        // seluruh kata → pad ekstra agar bounds/hit-test tetap menutupi huruf.
        val sfxPad = sfx?.let {
            (kotlin.math.abs(it.arc) * 1.3f + kotlin.math.abs(it.wave) * 1.3f +
                it.sizeJitter * 0.6f + 0.4f) * fontSize * scale
        } ?: 0f
        return outlineWidth * scale + (s?.blur ?: 0f) * scale +
            (s?.spread ?: 0f) * scale + glowPad + shadowOffset + sfxPad
    }

    /** Ekspansi bounds akibat distorsi perspektif (px kanvas). */
    fun perspectiveExtra(w: Float, h: Float): Float =
        (kotlin.math.abs(perspX) * w + kotlin.math.abs(perspY) * h) / 2f

    /** Bounds lengkap termasuk padding outline/shadow, dalam px kanvas. */
    fun getBounds(): RectF {
        val (w, h) = contentSize()
        val pad = boundsPad() + perspectiveExtra(w, h)
        return RectF(
            position.x - w / 2f - pad,
            position.y - h / 2f - pad,
            position.x + w / 2f + pad,
            position.y + h / 2f + pad
        )
    }

    /** Bounds visual (glif + efek, tanpa margin sentuh) untuk verifikasi muat. */
    fun getVisualBounds(): RectF {
        val (w, h) = contentSize()
        val pad = visualPad() + perspectiveExtra(w, h)
        // Rotasi sudah 0 setelah fitToRect; bila user memutar manual, hitung
        // AABB agar pemeriksaan tetap konservatif (tidak under-estimate).
        if (rotation == 0f) {
            return RectF(
                position.x - w / 2f - pad,
                position.y - h / 2f - pad,
                position.x + w / 2f + pad,
                position.y + h / 2f + pad
            )
        }
        val hw = w / 2f + pad
        val hh = h / 2f + pad
        val rad = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.abs(cos(rad)).toFloat()
        val s = kotlin.math.abs(sin(rad)).toFloat()
        val ew = hw * c + hh * s
        val eh = hw * s + hh * c
        return RectF(
            position.x - ew,
            position.y - eh,
            position.x + ew,
            position.y + eh
        )
    }

    /** True bila seluruh visual (glif + efek) berada di dalam [rect]. */
    fun visualFitsIn(rect: RectF, eps: Float = 1f): Boolean {
        val b = getVisualBounds()
        return b.left >= rect.left - eps && b.top >= rect.top - eps &&
            b.right <= rect.right + eps && b.bottom <= rect.bottom + eps
    }

    /** Inverse-rotasi titik uji mengelilingi [position]. */
    private fun unrotate(p: Offset): Offset {
        val dx = p.x - position.x
        val dy = p.y - position.y
        val rad = Math.toRadians((-rotation).toDouble())
        val rx = dx * cos(rad) - dy * sin(rad)
        val ry = dx * sin(rad) + cos(rad) * dy
        return Offset((rx + position.x).toFloat(), (ry + position.y).toFloat())
    }

    private fun rotatePoint(p: Offset): Offset {
        val dx = p.x - position.x
        val dy = p.y - position.y
        val rad = Math.toRadians(rotation.toDouble())
        val rx = dx * cos(rad) - dy * sin(rad)
        val ry = dx * sin(rad) + dy * cos(rad)
        return Offset((rx + position.x).toFloat(), (ry + position.y).toFloat())
    }

    fun hitTest(p: Offset): Boolean = getBounds().contains(unrotate(p).x, unrotate(p).y)

    fun scaleHandlePosition(): Offset {
        val b = getBounds()
        return rotatePoint(Offset(b.right, b.bottom))
    }

    fun rotateHandlePosition(): Offset {
        val b = getBounds()
        val grip = 56f * scale
        return rotatePoint(Offset((b.left + b.right) / 2f, b.top - grip))
    }

    /** Handle kiri-tengah & kanan-tengah di frame untuk atur lebar paragraph. */
    fun widthHandleLeft(): Offset {
        val b = getBounds()
        return rotatePoint(Offset(b.left, (b.top + b.bottom) / 2f))
    }

    fun widthHandleRight(): Offset {
        val b = getBounds()
        return rotatePoint(Offset(b.right, (b.top + b.bottom) / 2f))
    }

    /** Urutan uji: handle putar -> skala -> lebar paragraph -> badan. */
    fun hitHandle(p: Offset, radiusPx: Float): TextHandle {
        if ((p - rotateHandlePosition()).getDistance() <= radiusPx) return TextHandle.ROTATE
        if ((p - scaleHandlePosition()).getDistance() <= radiusPx) return TextHandle.SCALE
        if ((p - widthHandleRight()).getDistance() <= radiusPx * 1.15f) return TextHandle.WIDTH_RIGHT
        if ((p - widthHandleLeft()).getDistance() <= radiusPx * 1.15f) return TextHandle.WIDTH_LEFT
        if (isFrame()) {
            // Tepi kotak seleksi = gagang ubah ukuran (ala Photoshop);
            // di dalam kotak = geser teks.
            val fr = frameRectPx()
            if (fr != null) {
                val edge = minOf(
                    kotlin.math.abs(p.x - fr.left),
                    kotlin.math.abs(p.x - fr.right),
                    kotlin.math.abs(p.y - fr.top),
                    kotlin.math.abs(p.y - fr.bottom)
                )
                if (edge <= radiusPx * 1.3f) return TextHandle.FRAME
            }
        }
        if (hitTest(p)) return TextHandle.BODY
        return TextHandle.NONE
    }

    /**
     * Seret tepi kotak seleksi (FRAME): ubah lebar DAN tinggi sekaligus
     * mengikuti gerakan jari (bukan hanya satu sumbu).
     */
    fun dragFrameHandle(dxCanvas: Float, dyCanvas: Float) {
        if (boxWidth == null) return
        val safeScale = scale.coerceAtLeast(0.05f)
        val nw = ((boxWidth ?: 0f) * safeScale + dxCanvas).coerceAtLeast(24f)
        val nh = ((boxHeight ?: 0f) * safeScale + dyCanvas).coerceAtLeast(12f)
        boxWidth = (nw / safeScale).coerceIn(24f, 8000f)
        boxHeight = (nh / safeScale).coerceIn(12f, 8000f)
    }

    /**
     * Geser handle lebar paragraph ala Photoshop/IbisPaint: tepi seberang
     * dikunci, lebar konten berubah sehingga teks re-wrap (bukan scale font).
     * Bila masih point-text, otomatis jadi paragraph dulu.
     */
    fun dragWidthHandle(handle: TextHandle, lastCanvas: Offset, curCanvas: Offset) {
        if (handle != TextHandle.WIDTH_LEFT && handle != TextHandle.WIDTH_RIGHT) return
        if (boxWidth == null) enableParagraph()
        val bw = boxWidth ?: return
        if (!bw.isFinite()) return
        val lastLocal = unrotate(lastCanvas)
        val curLocal = unrotate(curCanvas)
        val dx = curLocal.x - lastLocal.x
        if (dx == 0f) return
        val safeScale = scale.coerceAtLeast(0.2f)
        val pad = boundsPad()
        val oldPaddedW = bw * safeScale + pad * 2f
        val newPaddedW = if (handle == TextHandle.WIDTH_RIGHT) {
            oldPaddedW + dx
        } else {
            oldPaddedW - dx
        }
        val minPadded = 40f * safeScale + pad * 2f
        val clampedPadded = newPaddedW.coerceIn(minPadded, 4000f * safeScale + pad * 2f)
        val appliedDx = clampedPadded - oldPaddedW
        if (appliedDx == 0f) return
        val newContentActual = (clampedPadded - pad * 2f).coerceAtLeast(10f)
        boxWidth = (newContentActual / safeScale).coerceIn(40f, 4000f)
        val halfDx = appliedDx / 2f
        val rad = Math.toRadians(rotation.toDouble())
        val cdx = (halfDx * cos(rad)).toFloat()
        val cdy = (halfDx * sin(rad)).toFloat()
        position = Offset(position.x + cdx, position.y + cdy)
    }

    /** Salinan lepas untuk snapshot history (typeface dipakai bersama, aman). */
    fun copy(): TextBox = TextBox(
        id = id,
        text = text,
        position = position.copy(),
        fontSize = fontSize,
        color = color,
        bold = bold,
        italic = italic,
        align = align,
        outlineWidth = outlineWidth,
        outlineColor = outlineColor,
        strokeOpacity = strokeOpacity,
        strokePosition = strokePosition,
        fillType = fillType,
        gradient = gradient.copy(),
        shadow = shadow?.copy(),
        letterSpacing = letterSpacing,
        wordSpacing = wordSpacing,
        lineSpacing = lineSpacing,
        textOpacity = textOpacity,
        uppercase = uppercase,
        underline = underline,
        strikethrough = strikethrough,
        glow = glow?.copy(),
        bevel = bevel?.copy(),
        scale = scale,
        textScaleX = textScaleX,
        rotation = rotation,
        perspX = perspX,
        perspY = perspY,
        fontName = fontName,
        typeface = typeface,
        boxWidth = boxWidth,
        boxHeight = boxHeight,
        autoFit = autoFit,
        persp = persp?.copy(),
        spans = spans?.map { TextSpan(it.start, it.end, it.style.copy()) },
        sfx = sfx?.copy(),
        inkSfx = inkSfx?.copy()
    )

    /** Pulihkan semua field dari [o] tanpa ganti objek (referensi seleksi tetap valid). */
    fun setFrom(o: TextBox) {
        text = o.text
        position = o.position.copy()
        fontSize = o.fontSize
        color = o.color
        bold = o.bold
        italic = o.italic
        align = o.align
        outlineWidth = o.outlineWidth
        outlineColor = o.outlineColor
        strokeOpacity = o.strokeOpacity
        strokePosition = o.strokePosition
        fillType = o.fillType
        gradient = o.gradient.copy()
        shadow = o.shadow?.copy()
        letterSpacing = o.letterSpacing
        wordSpacing = o.wordSpacing
        lineSpacing = o.lineSpacing
        textOpacity = o.textOpacity
        uppercase = o.uppercase
        underline = o.underline
        strikethrough = o.strikethrough
        glow = o.glow?.copy()
        bevel = o.bevel?.copy()
        scale = o.scale
        textScaleX = o.textScaleX
        rotation = o.rotation
        perspX = o.perspX
        perspY = o.perspY
        fontName = o.fontName
        typeface = o.typeface
        boxWidth = o.boxWidth
        boxHeight = o.boxHeight
        autoFit = o.autoFit
        persp = o.persp?.let { p -> PerspSpec().also { it.copyFrom(p) } }
        spans = o.spans?.map { TextSpan(it.start, it.end, it.style.copy()) }
        sfx = o.sfx?.copy()
        inkSfx = o.inkSfx?.copy()
    }

    /** Samakan isi visual (untuk deteksi sesi edit panel). */
    fun contentEquals(o: TextBox): Boolean {
        return text == o.text &&
            position == o.position &&
            fontSize == o.fontSize &&
            color == o.color &&
            bold == o.bold &&
            italic == o.italic &&
            align == o.align &&
            outlineWidth == o.outlineWidth &&
            outlineColor == o.outlineColor &&
            strokeOpacity == o.strokeOpacity &&
            strokePosition == o.strokePosition &&
            fillType == o.fillType &&
            gradient == o.gradient &&
            shadow == o.shadow &&
            letterSpacing == o.letterSpacing &&
            wordSpacing == o.wordSpacing &&
            lineSpacing == o.lineSpacing &&
            textOpacity == o.textOpacity &&
            uppercase == o.uppercase &&
            underline == o.underline &&
            strikethrough == o.strikethrough &&
            glow == o.glow &&
            bevel == o.bevel &&
            scale == o.scale &&
            textScaleX == o.textScaleX &&
            rotation == o.rotation &&
            perspX == o.perspX &&
            perspY == o.perspY &&
            fontName == o.fontName &&
            boxWidth == o.boxWidth &&
            boxHeight == o.boxHeight &&
            autoFit == o.autoFit &&
            persp == o.persp &&
            spans == o.spans &&
            sfx == o.sfx &&
            inkSfx == o.inkSfx
    }

    companion object {
        /**
         * Baca daftar span dari JSON project (gaya per kata). Typeface
         * di-resolve lewat [typefaceFor] karena font kustom hanya hidup
         * sebagai nama file - tanpa ini font kata akan jatuh ke default
         * setelah project ditutup lalu dibuka lagi.
         */
        private fun parseSpans(
            arr: org.json.JSONArray?,
            typefaceFor: (String) -> Typeface
        ): List<TextSpan>? {
            if (arr == null || arr.length() == 0) return null
            val out = ArrayList<TextSpan>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val st = o.optJSONObject("st") ?: continue
                val fname = if (st.has("font")) st.optString("font") else null
                out.add(
                    TextSpan(
                        o.optInt("s", 0),
                        o.optInt("e", 0),
                        SpanStyle(
                            fontName = fname,
                            typeface = fname?.let { name ->
                                runCatching { typefaceFor(name) }.getOrNull()
                            },
                            fontSizeMul = st.optDouble("mul", 1.0).toFloat(),
                            color = if (st.has("color")) st.optInt("color") else null,
                            bold = if (st.has("bold")) st.optBoolean("bold") else null,
                            italic = if (st.has("italic")) st.optBoolean("italic") else null,
                            outlineWidth = if (st.has("ow")) st.optDouble("ow").toFloat() else null,
                            outlineColor = if (st.has("oc")) st.optInt("oc") else null,
                            shadowColor = if (st.has("sc")) st.optInt("sc") else null,
                            shadowDx = if (st.has("sx")) st.optDouble("sx").toFloat() else null,
                            shadowDy = if (st.has("sy")) st.optDouble("sy").toFloat() else null,
                            shadowBlur = if (st.has("sb")) st.optDouble("sb").toFloat() else null,
                            uppercase = if (st.has("up")) st.optBoolean("up") else null,
                            underline = if (st.has("ul")) st.optBoolean("ul") else null,
                            strikethrough = if (st.has("stk")) st.optBoolean("stk") else null
                        )
                    )
                )
            }
            return if (out.isEmpty()) null else out
        }

        fun spacedWidth(paint: Paint, line: String, extraPerChar: Float, wordExtra: Float = 0f): Float {
            if (line.isEmpty()) return 0f
            if (extraPerChar == 0f && wordExtra == 0f) return paint.measureText(line)
            var w = 0f
            for (ch in line) {
                w += paint.measureText(ch.toString()) + extraPerChar
                if (ch == ' ') w += wordExtra
            }
            return w - extraPerChar
        }

        /**
         * Serialisasi untuk penyimpanan project (teks tetap editable setelah
         * apk ditutup: font disimpan sebagai nama, bukan Typeface).
         */
        fun TextBox.toJson(): org.json.JSONObject = org.json.JSONObject().apply {
            put("id", id)
            put("text", text)
            put("x", position.x.toDouble())
            put("y", position.y.toDouble())
            put("fontSize", fontSize.toDouble())
            put("color", color)
            put("bold", bold)
            put("italic", italic)
            put("align", align.name)
            put("outlineWidth", outlineWidth.toDouble())
            put("outlineColor", outlineColor)
            put("strokeOpacity", strokeOpacity.toDouble())
            put("strokePosition", strokePosition.name)
            put("fillType", fillType.name)
            put("gradient", org.json.JSONObject().apply {
                put("colorStart", gradient.colorStart)
                put("colorEnd", gradient.colorEnd)
                put("angle", gradient.angle.toDouble())
            })
            shadow?.let { s ->
                put("shadow", org.json.JSONObject().apply {
                    put("dx", s.dx.toDouble())
                    put("dy", s.dy.toDouble())
                    put("blur", s.blur.toDouble())
                    put("color", s.color)
                    put("opacity", s.opacity.toDouble())
                    put("spread", s.spread.toDouble())
                })
            }
            put("letterSpacing", letterSpacing.toDouble())
            put("wordSpacing", wordSpacing.toDouble())
            put("lineSpacing", lineSpacing.toDouble())
            put("textOpacity", textOpacity.toDouble())
            put("uppercase", uppercase)
            put("underline", underline)
            put("strikethrough", strikethrough)
            glow?.let { g ->
                put("glow", org.json.JSONObject().apply {
                    put("color", g.color)
                    put("blur", g.blur.toDouble())
                    put("spread", g.spread.toDouble())
                    put("opacity", g.opacity.toDouble())
                })
            }
            bevel?.let { b ->
                put("bevel", org.json.JSONObject().apply {
                    put("size", b.size.toDouble())
                    put("opacity", b.opacity.toDouble())
                })
            }
            put("scale", scale.toDouble())
            put("textScaleX", textScaleX.toDouble())
        put("rotation", rotation.toDouble())
        put("perspX", perspX.toDouble())
        put("perspY", perspY.toDouble())
        put("fontName", fontName)
            boxWidth?.let { put("boxWidth", it.toDouble()) }
            boxHeight?.let { put("boxHeight", it.toDouble()) }
            put("autoFit", autoFit)
            persp?.let { p ->
                put("persp", org.json.JSONObject().apply {
                    put("tlX", p.tlX.toDouble()); put("tlY", p.tlY.toDouble())
                    put("trX", p.trX.toDouble()); put("trY", p.trY.toDouble())
                    put("brX", p.brX.toDouble()); put("brY", p.brY.toDouble())
                    put("blX", p.blX.toDouble()); put("blY", p.blY.toDouble())
                })
            }
            spans?.takeIf { it.isNotEmpty() }?.let { list ->
                put("spans", org.json.JSONArray().apply {
                    for (sp in list) {
                        put(org.json.JSONObject().apply {
                            put("s", sp.start); put("e", sp.end)
                            put("st", org.json.JSONObject().apply {
                                val st = sp.style
                                st.fontName?.let { put("font", it) }
                                st.fontSizeMul.let { if (it != 1f) put("mul", it.toDouble()) }
                                st.color?.let { put("color", it) }
                                st.bold?.let { put("bold", it) }
                                st.italic?.let { put("italic", it) }
                                st.outlineWidth?.let { put("ow", it.toDouble()) }
                                st.outlineColor?.let { put("oc", it) }
                                st.shadowColor?.let { put("sc", it) }
                                st.shadowDx?.let { put("sx", it.toDouble()) }
                                st.shadowDy?.let { put("sy", it.toDouble()) }
                                st.shadowBlur?.let { put("sb", it.toDouble()) }
                                st.uppercase?.let { put("up", it) }
                                st.underline?.let { put("ul", it) }
                                st.strikethrough?.let { put("stk", it) }
                            })
                        })
                    }
                })
            }
            sfx?.let { sp ->
                put("sfx", org.json.JSONObject().apply {
                    put("arc", sp.arc.toDouble())
                    put("sizeJitter", sp.sizeJitter.toDouble())
                    put("rotJitter", sp.rotJitter.toDouble())
                    put("tilt", sp.tilt.toDouble())
                    put("wave", sp.wave.toDouble())
                    put("seed", sp.seed)
                })
            }
            inkSfx?.let { ik ->
                put("inkSfx", org.json.JSONObject().apply {
                    put("tiltDeg", ik.tiltDeg.toDouble())
                    put("strokeRatio", ik.strokeRatio.toDouble())
                    put("outlineRatio", ik.outlineRatio.toDouble())
                    put("keylineRatio", ik.keylineRatio.toDouble())
                    put("outlineColor", ik.outlineColor)
                    put("keylineColor", ik.keylineColor)
                    put("roughOutline", ik.roughOutline.toDouble())
                    put("teethRatio", ik.teethRatio.toDouble())
                    put("gradient", ik.gradient)
                    put("gradientDarken", ik.gradientDarken.toDouble())
                    put("spatter", ik.spatter)
                    put("seed", ik.seed)
                })
            }
        }

        /** Pasangan dari [toJson]: typeface dicari via [typefaceFor], fallback bold. */
        fun boxFromJson(o: org.json.JSONObject, typefaceFor: (String) -> Typeface): TextBox {
            val fontName = o.optString("fontName", "Default Bold")
            val gradientObj = o.optJSONObject("gradient")
            val shadowObj = o.optJSONObject("shadow")
            val glowObj = o.optJSONObject("glow")
            val bevelObj = o.optJSONObject("bevel")
            return TextBox(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                text = o.optString("text", "Teks baru"),
                position = Offset(
                    o.optDouble("x", 640.0).toFloat(),
                    o.optDouble("y", 640.0).toFloat()
                ),
                fontSize = o.optDouble("fontSize", 64.0).toFloat(),
                color = o.optInt("color", android.graphics.Color.WHITE),
                bold = o.optBoolean("bold", true),
                italic = o.optBoolean("italic", false),
                align = runCatching { TextAlignMode.valueOf(o.optString("align", "CENTER")) }
                    .getOrDefault(TextAlignMode.CENTER),
                outlineWidth = o.optDouble("outlineWidth", 0.0).toFloat(),
                outlineColor = o.optInt("outlineColor", android.graphics.Color.BLACK),
                strokeOpacity = o.optDouble("strokeOpacity", 1.0).toFloat(),
                strokePosition = runCatching {
                    StrokePosition.valueOf(o.optString("strokePosition", "OUTSIDE"))
                }.getOrDefault(StrokePosition.OUTSIDE),
                fillType = runCatching {
                    TextFillType.valueOf(o.optString("fillType", "SOLID"))
                }.getOrDefault(TextFillType.SOLID),
                gradient = TextGradientSpec(
                    colorStart = gradientObj?.optInt("colorStart", android.graphics.Color.WHITE)
                        ?: android.graphics.Color.WHITE,
                    colorEnd = gradientObj?.optInt(
                        "colorEnd", android.graphics.Color.parseColor("#FF5722")
                    ) ?: android.graphics.Color.parseColor("#FF5722"),
                    angle = gradientObj?.optDouble("angle", 90.0)?.toFloat() ?: 90f
                ),
                shadow = shadowObj?.let { s ->
                    TextShadowSpec(
                        dx = s.optDouble("dx", 4.0).toFloat(),
                        dy = s.optDouble("dy", 4.0).toFloat(),
                        blur = s.optDouble("blur", 8.0).toFloat(),
                        color = s.optInt("color", 0x80000000.toInt()),
                        opacity = s.optDouble("opacity", 0.75).toFloat(),
                        spread = s.optDouble("spread", 0.0).toFloat()
                    )
                },
                letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
                wordSpacing = o.optDouble("wordSpacing", 0.0).toFloat(),
                lineSpacing = o.optDouble("lineSpacing", 12.0).toFloat(),
                textOpacity = o.optDouble("textOpacity", 1.0).toFloat(),
                uppercase = o.optBoolean("uppercase", false),
                underline = o.optBoolean("underline", false),
                strikethrough = o.optBoolean("strikethrough", false),
                glow = glowObj?.let { g ->
                    TextGlowSpec(
                        color = g.optInt("color", 0xFFFFEE58.toInt()),
                        blur = g.optDouble("blur", 14.0).toFloat(),
                        spread = g.optDouble("spread", 0.0).toFloat(),
                        opacity = g.optDouble("opacity", 0.75).toFloat()
                    )
                },
                bevel = bevelObj?.let { b ->
                    TextBevelSpec(
                        size = b.optDouble("size", 2.0).toFloat(),
                        opacity = b.optDouble("opacity", 0.8).toFloat()
                    )
                },
                scale = o.optDouble("scale", 1.0).toFloat(),
                textScaleX = o.optDouble("textScaleX", 1.0).toFloat(),
                rotation = o.optDouble("rotation", 0.0).toFloat(),
                perspX = o.optDouble("perspX", 0.0).toFloat().coerceIn(-1f, 1f),
                perspY = o.optDouble("perspY", 0.0).toFloat().coerceIn(-1f, 1f),
                fontName = fontName,
                typeface = runCatching { typefaceFor(fontName) }.getOrDefault(Typeface.DEFAULT_BOLD),
                boxWidth = if (o.has("boxWidth")) o.optDouble("boxWidth").toFloat() else null,
                boxHeight = if (o.has("boxHeight")) o.optDouble("boxHeight").toFloat() else null,
                autoFit = o.optBoolean("autoFit", false),
                persp = o.optJSONObject("persp")?.let { p ->
                    PerspSpec(
                        p.optDouble("tlX", 0.0).toFloat(), p.optDouble("tlY", 0.0).toFloat(),
                        p.optDouble("trX", 0.0).toFloat(), p.optDouble("trY", 0.0).toFloat(),
                        p.optDouble("brX", 0.0).toFloat(), p.optDouble("brY", 0.0).toFloat(),
                        p.optDouble("blX", 0.0).toFloat(), p.optDouble("blY", 0.0).toFloat()
                    )
                },
                spans = parseSpans(o.optJSONArray("spans"), typefaceFor),
                sfx = o.optJSONObject("sfx")?.let { sp ->
                    SfxSpec(
                        arc = sp.optDouble("arc", 0.55).toFloat(),
                        sizeJitter = sp.optDouble("sizeJitter", 0.25).toFloat(),
                        rotJitter = sp.optDouble("rotJitter", 10.0).toFloat(),
                        tilt = sp.optDouble("tilt", 0.0).toFloat(),
                        wave = sp.optDouble("wave", 0.0).toFloat(),
                        seed = sp.optInt("seed", 1)
                    )
                },
                inkSfx = o.optJSONObject("inkSfx")?.let { ik ->
                    SfxInkSpec(
                        tiltDeg = ik.optDouble("tiltDeg", -18.0).toFloat(),
                        strokeRatio = ik.optDouble("strokeRatio", 0.065).toFloat(),
                        outlineRatio = ik.optDouble("outlineRatio", 0.38).toFloat(),
                        keylineRatio = ik.optDouble("keylineRatio", 0.14).toFloat(),
                        outlineColor = ik.optInt("outlineColor", 0xFFF2EFE2.toInt()),
                        keylineColor = ik.optInt("keylineColor", 0xFF1A1A1E.toInt()),
                        roughOutline = ik.optDouble("roughOutline", 0.34).toFloat(),
                        teethRatio = ik.optDouble("teethRatio", 0.062).toFloat(),
                        gradient = ik.optBoolean("gradient", true),
                        gradientDarken = ik.optDouble("gradientDarken", 0.42).toFloat(),
                        spatter = ik.optInt("spatter", 0),
                        seed = ik.optInt("seed", 1)
                    )
                }
            )
    }
}
}

/** Satu-satunya sumber daftar font: bawaan + folder custom_fonts. */
class FontRegistry(private val context: Context) {
    private val dir = File(context.filesDir, "custom_fonts").apply {
        if (!exists()) mkdirs()
    }

    fun fonts(): List<Pair<String, Typeface>> {
        val list = mutableListOf(
            "Default Bold" to Typeface.DEFAULT_BOLD,
            "Default" to Typeface.DEFAULT,
            "Serif" to Typeface.SERIF,
            "Sans Serif" to Typeface.SANS_SERIF,
            "Monospace" to Typeface.MONOSPACE
        )
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.name.endsWith(".ttf", true) || file.name.endsWith(".otf", true)) {
                try {
                    list.add(file.nameWithoutExtension to Typeface.createFromFile(file))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        // Font komik bawaan (OFL, lihat assets/fonts/OFL.txt).
        runCatching {
            context.assets.list("fonts")
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                ?.sorted()
                ?.forEach { name ->
                    try {
                        list.add(name.substringBeforeLast('.') to Typeface.createFromAsset(context.assets, "fonts/$name"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
        return list
    }

    fun import(input: InputStream, fileName: String): Typeface? {
        val dest = File(dir, fileName)
        dest.outputStream().use { out -> input.copyTo(out) }
        return try {
            Typeface.createFromFile(dest)
        } catch (e: Exception) {
            e.printStackTrace()
            dest.delete()
            null
        }
    }
}
