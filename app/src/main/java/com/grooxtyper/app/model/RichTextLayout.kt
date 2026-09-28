package com.grooxtyper.app.model

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max

/**
 * Layout teks kaya: dipakai bila teks punya gaya per KATA (span) atau teks
 * berada di dalam KOTAK SELEKSI yang auto-fit.
 *
 * Dua masalah yang diselesaikan di sini:
 *  1. **Gaya per kata** - tiap rentang karakter boleh punya font/warna/
 *     shadow/outline sendiri, tapi harus tetap satu baris rapi. Jadi
 *     pengukuran dilakukan per RUN (potongan teks berstyle sama), lalu
 *     pembungkusan barisagawa-garis ruang.
 *  2. **Auto-fit ke kotak** - font diperkecil (bukan dipotong) sampai
 *     seluruh teks muat di dalam [TextBox.boxWidth] x [TextBox.boxHeight],
 *     persis seperti "Fit to Frame" di Photoshop.
 *
 * Semua hasilDepends pada cache signature supaya tidak dihitung ulang tiap
 * frame (getBounds dipanggil saat hit-test tiap ketukan).
 */
object RichTextLayout {

    /** Batas bawah auto-fit (jangan terlalu kecil agar tetap terbaca). */
    const val MIN_FIT = 0.18f

    /** Satu potongan teks berstyle seragam dalam satu baris. */
    class Run(
        val text: String,
        /** Indeks karakter awal di [TextBox.text]. */
        val start: Int,
        /** Pengali ukuran font yang dipakai run ini. */
        val sizeMul: Float,
        /** Paint terukur (textSize sudah termasuk skala & pengali). */
        val paint: Paint,
        /** Gaya override aktif (null = bawaan kotak). */
        val style: SpanStyle?,
        /** True bila run ini menutupi satu kata (untuk wordSpacing). */
        var wordEnd: Boolean
    ) {
        var width: Float = 0f
    }

    /** Satu baris hasil pembungkusan. */
    class Line(val runs: List<Run>) {
        var width: Float = 0f
        var ascent: Float = 0f
        var descent: Float = 0f
    }

    /** Hasil layout lengkap. */
    class Layout(
        val lines: List<Line>,
        /** Faktor auto-fit yang dipakai (1f = tanpa perkecilan). */
        val fit: Float,
        val contentW: Float,
        val contentH: Float
    ) {
        val maxLineW: Float get() {
            var m = 0f
            for (l in lines) if (l.width > m) m = l.width
            return m
        }
    }

    // Cache kecil: kunci signature -> hasil layout. Mencegah layout dihitung
    // ulang berkali-kali dalam satu frame (bounds + render + hit-test).
    private val cache = HashMap<String, Layout>()
    private const val CACHE_MAX = 24

    private fun signature(box: TextBox): String {
        val sp = box.spans
        val spSig = if (sp.isNullOrEmpty()) "-" else buildString {
            for (s in sp) {
                append(s.start).append(':').append(s.end).append(':')
                append(s.style.color ?: 0).append(',')
                append(s.style.fontName ?: "-").append(',')
                append(s.style.fontSizeMul).append(',')
                append(s.style.bold).append(',')
                append(s.style.italic).append(',')
                append(s.style.outlineWidth ?: -1f).append(',')
                append(s.style.outlineColor ?: 0).append(',')
                append(s.style.shadowColor ?: 0).append(',')
                append(s.style.uppercase)
            }
        }
        return box.id + "|" + box.text.length + "|" + box.text.hashCode() + "|" +
            box.fontSize + "|" + box.scale + "|" + (box.boxWidth ?: -1f) + "|" +
            (box.boxHeight ?: -1f) + "|" + box.lineSpacing + "|" + box.letterSpacing + "|" +
            box.wordSpacing + "|" + box.uppercase + "|" + box.fontName + "|" +
            (box.bold) + "|" + (box.italic) + "|" + (box.typeface?.hashCode() ?: 0) + "|" + spSig
    }

    /** Layout final (dengan cache) untuk [box]. */
    fun layout(box: TextBox): Layout {
        val key = signature(box)
        cache[key]?.let { return it }
        val out = build(box, fitOf(box))
        if (cache.size >= CACHE_MAX) cache.clear()
        cache[key] = out
        return out
    }

    /** Kosongkan cache (dipanggil bila font custom berubah). */
    fun clearCache() = cache.clear()

    /** Faktor auto-fit: 1f bila bukan kotak auto-fit atau sudah muat. */
    private fun fitOf(box: TextBox): Float {
        if (!box.isFrame()) return 1f
        val bw = box.boxWidth ?: return 1f
        val bh = box.boxHeight ?: return 1f
        val pad = box.visualPad()
        val maxW = bw * box.scale - 2f * pad
        val maxH = bh * box.scale - 2f * pad
        if (maxW <= 8f || maxH <= 8f) return 1f
        if (build(box, 1f).let { it.maxLineW <= maxW && it.contentH <= maxH }) return 1f
        var lo = MIN_FIT
        var hi = 1f
        repeat(12) {
            val mid = (lo + hi) * 0.5f
            val l = build(box, mid)
            if (l.maxLineW <= maxW && l.contentH <= maxH) lo = mid else hi = mid
        }
        return lo
    }

    /** Bangun layout dengan faktor auto-fit tertentu. */
    private fun build(box: TextBox, fit: Float): Layout {
        val src = box.text
        val maxW = (box.boxWidth ?: 0f) * box.scale
        val limit = if (maxW > 8f) maxW else Float.MAX_VALUE
        val letterExtra = box.letterSpacing * box.scale
        val wordExtra = box.wordSpacing * box.scale
        val baseStyle = baseStyleOf(box)
        val lines = ArrayList<Line>(8)

        // Tokenisasi per kata (spasi sebagai pemisah), lalu per kata dipecah
        // lagi di batas span agar tiap run berstyle seragam.
        val words = ArrayList<List<Run>>(16)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (isSep(c)) {
                i++
                continue
            }
            val runs = ArrayList<Run>(2)
            val st = i
            while (i < n && !isSep(src[i])) i++
            var j = st
            while (j < i) {
                val sp = box.spanAt(j)
                var e = j + 1
                while (e < i && box.spanAt(e) === sp) e++
                val merged = sp?.over(baseStyle)
                val r = makeRun(box, src, j, e, merged, fit, false)
                runs.add(r)
                j = e
            }
            runs[runs.size - 1].wordEnd = true
            words.add(runs)
        }

        var cur = ArrayList<Run>(8)
        var curW = 0f
        for (wordRuns in words) {
            var ww = 0f
            for (r in wordRuns) ww += r.width
            if (cur.isNotEmpty() && curW + ww > limit) {
                lines.add(finishLine(cur, curW - wordExtra))
                cur = ArrayList(8)
                curW = 0f
            }
            // Kata tunggal lebih lebar dari kotak: patah per karakter.
            if (ww > limit && cur.isEmpty()) {
                for (r in wordRuns) {
                    val single = ArrayList<Run>(1)
                    var acc = 0f
                    for (ch in r.text.indices) {
                        val sub = makeRun(
                            box, r.text, ch, ch + 1, r.style, fit, ch == r.text.length - 1
                        )
                        if (acc + sub.width > limit && single.isNotEmpty()) {
                            single[single.size - 1].wordEnd = false
                            lines.add(finishLine(single, acc))
                            single.clear()
                            acc = 0f
                        }
                        single.add(sub)
                        acc += sub.width
                    }
                    for (s in single) {
                        cur.add(s)
                        curW += s.width
                    }
                }
                continue
            }
            for (r in wordRuns) {
                cur.add(r)
                curW += r.width
            }
            curW += wordExtra
        }
        if (cur.isNotEmpty()) lines.add(finishLine(cur, (curW - wordExtra).coerceAtLeast(0f)))
        if (lines.isEmpty()) lines.add(Line(ArrayList(1)))

        var contentW = 0f
        var contentH = 0f
        for (l in lines) {
            if (l.width > contentW) contentW = l.width
            contentH += (l.descent - l.ascent) + box.lineSpacing * box.scale
        }
        val bw = box.boxWidth
        if (bw != null && bw * box.scale > contentW) contentW = bw * box.scale
        return Layout(lines, fit, contentW, contentH)
    }

    private fun finishLine(runs: List<Run>, width: Float): Line {
        val l = Line(runs)
        l.width = width
        var a = 0f
        var d = 0f
        for (r in runs) {
            val fm = r.paint.fontMetrics
            if (fm.ascent < a) a = fm.ascent
            if (fm.descent > d) d = fm.descent
        }
        if (runs.isEmpty()) {
            a = -1f
            d = 1f
        }
        l.ascent = a
        l.descent = d
        return l
    }

    private fun isSep(c: Char): Boolean = c == ' ' || c == '　' || c == '\n' || c == '\t'

    /** Gaya dasar (bawaan) sebagai SpanStyle agar bisa di-override. */
    private fun baseStyleOf(box: TextBox): SpanStyle = SpanStyle(
        fontName = box.fontName,
        typeface = box.typeface,
        fontSizeMul = 1f,
        color = box.color,
        bold = box.bold,
        italic = box.italic,
        outlineWidth = box.outlineWidth,
        outlineColor = box.outlineColor,
        shadowColor = box.shadow?.color,
        shadowDx = box.shadow?.dx,
        shadowDy = box.shadow?.dy,
        shadowBlur = box.shadow?.blur,
        uppercase = box.uppercase,
        underline = box.underline,
        strikethrough = box.strikethrough
    )

    /** Paint untuk satu run (ukuran sudah jadi piksel kanvas). */
    fun paintFor(box: TextBox, st: SpanStyle?, fit: Float): Paint {
        val sizeMul = (st?.fontSizeMul ?: 1f) * fit
        val tf = when {
            st?.typeface != null -> st.typeface
            st?.fontName != null -> Typeface.create(st.fontName, Typeface.NORMAL)
            else -> box.typeface
        }
        val bold = st?.bold ?: box.bold
        val italic = st?.italic ?: box.italic
        val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
        val eff = try {
            Typeface.create(tf, style) ?: tf
        } catch (e: Exception) {
            tf
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = max(1f, box.fontSize * box.scale * sizeMul)
            typeface = eff
            this.textScaleX = box.textScaleX.coerceIn(0.3f, 1f)
        }
    }

    private fun makeRun(
        box: TextBox,
        source: String,
        start: Int,
        end: Int,
        st: SpanStyle?,
        fit: Float,
        wordEnd: Boolean
    ): Run {
        var raw = source.substring(start, end)
        if (st?.uppercase == true) raw = raw.uppercase()
        val paint = paintFor(box, st, fit)
        val extra = box.letterSpacing * box.scale
        val w = if (raw.isEmpty()) 0f else TextBox.spacedWidth(paint, raw, extra, 0f)
        return Run(raw, start, (st?.fontSizeMul ?: 1f) * fit, paint, st, wordEnd).also {
            it.width = w
        }
    }

    /** Lebar satu run dalam piksel kanvas (dipakai renderer untuk posisi). */
    fun runWidth(box: TextBox, r: Run): Float = r.width

    /** Warna isi run (gradient kotak dipakai bila tak ada override warna). */
    fun fillColorOf(box: TextBox, r: Run): Int? = r.style?.color

    /** Lebar outline run (piksel kanvas), 0 = tanpa outline. */
    fun outlineWidthOf(box: TextBox, r: Run): Float =
        (r.style?.outlineWidth ?: box.outlineWidth) * box.scale
}
