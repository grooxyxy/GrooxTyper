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
import kotlin.math.max
import kotlin.math.sin

enum class TextAlignMode { LEFT, CENTER, RIGHT }

enum class TextHandle { NONE, BODY, SCALE, ROTATE }

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
    var rotation: Float = 0f,
    var fontName: String = "Default Bold",
    var typeface: Typeface = Typeface.DEFAULT_BOLD
) {
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
    }

    /** Teks yang tampil (kapital bila opsi menyala; asli tetap tersimpan). */
    fun displayText(): String = if (uppercase) text.uppercase() else text

    /** Tinggi satu baris dalam px kanvas (dijaga >= 4 agar bounds/hit-test valid saat spacing minus). */
    fun lineHeightPx(): Float {
        val fm = basePaint().fontMetrics
        return max(4f, (fm.descent - fm.ascent) + lineSpacing * scale)
    }

    /** Ukuran konten (tanpa padding outline/shadow), dalam px kanvas. */
    fun contentSize(): Pair<Float, Float> {
        val paint = basePaint()
        val lines = displayText().split("\n").ifEmpty { listOf("") }
        var maxW = 0f
        for (line in lines) {
            val w = spacedWidth(paint, line, letterSpacing * scale, wordSpacing * scale)
            if (w > maxW) maxW = w
        }
        return maxW to lineHeightPx() * lines.size
    }

    /** Bounds lengkap termasuk padding outline/shadow, dalam px kanvas. */
    fun getBounds(): RectF {
        val (w, h) = contentSize()
        val glowPad = if (glow != null) (glow!!.blur + glow!!.spread) * scale else 0f
        val pad = outlineWidth * scale + (shadow?.blur ?: 0f) * scale +
            (shadow?.spread ?: 0f) * scale + glowPad + 16f * scale
        return RectF(
            position.x - w / 2f - pad,
            position.y - h / 2f - pad,
            position.x + w / 2f + pad,
            position.y + h / 2f + pad
        )
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

    /** Urutan uji: handle putar -> handle skala -> badan. */
    fun hitHandle(p: Offset, radiusPx: Float): TextHandle {
        if ((p - rotateHandlePosition()).getDistance() <= radiusPx) return TextHandle.ROTATE
        if ((p - scaleHandlePosition()).getDistance() <= radiusPx) return TextHandle.SCALE
        if (hitTest(p)) return TextHandle.BODY
        return TextHandle.NONE
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
        rotation = rotation,
        fontName = fontName,
        typeface = typeface
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
        rotation = o.rotation
        fontName = o.fontName
        typeface = o.typeface
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
            rotation == o.rotation &&
            fontName == o.fontName
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
