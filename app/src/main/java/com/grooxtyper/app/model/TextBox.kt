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

enum class TextHandle { NONE, BODY, SCALE, ROTATE, WIDTH_LEFT, WIDTH_RIGHT }

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
    var boxWidth: Float? = null
) {
    fun isParagraph(): Boolean = boxWidth != null

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
        return outlineWidth * scale + (s?.blur ?: 0f) * scale +
            (s?.spread ?: 0f) * scale + glowPad + shadowOffset
    }

    /** Bounds lengkap termasuk padding outline/shadow, dalam px kanvas. */
    fun getBounds(): RectF {
        val (w, h) = contentSize()
        val pad = boundsPad()
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
        val pad = visualPad()
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
        if (hitTest(p)) return TextHandle.BODY
        return TextHandle.NONE
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
        fontName = fontName,
        typeface = typeface,
        boxWidth = boxWidth
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
        fontName = o.fontName
        typeface = o.typeface
        boxWidth = o.boxWidth
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
            fontName == o.fontName &&
            boxWidth == o.boxWidth
    }

    companion object {
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
            put("fontName", fontName)
            boxWidth?.let { put("boxWidth", it.toDouble()) }
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
                fontName = fontName,
                typeface = runCatching { typefaceFor(fontName) }.getOrDefault(Typeface.DEFAULT_BOLD),
                boxWidth = if (o.has("boxWidth")) o.optDouble("boxWidth").toFloat() else null
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
