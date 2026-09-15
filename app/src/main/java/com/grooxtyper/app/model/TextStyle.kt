package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Preset gaya teks: semua atribut visual [TextBox] KECUALI isi teks,
 * posisi, skala, dan rotasi. Disimpan sebagai JSON di SharedPreferences
 * (tanpa dependency tambahan).
 */
data class TextStylePreset(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Style",
    var fontName: String = "Default Bold",
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
    var lineSpacing: Float = 12f
) {
    fun applyTo(box: TextBox, typeface: Typeface?) {
        box.fontName = fontName
        if (typeface != null) box.typeface = typeface
        box.fontSize = fontSize
        box.color = color
        box.bold = bold
        box.italic = italic
        box.align = align
        box.outlineWidth = outlineWidth
        box.outlineColor = outlineColor
        box.strokeOpacity = strokeOpacity
        box.strokePosition = strokePosition
        box.fillType = fillType
        box.gradient = gradient.copy()
        box.shadow = shadow?.copy()
        box.letterSpacing = letterSpacing
        box.wordSpacing = wordSpacing
        box.lineSpacing = lineSpacing
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("fontName", fontName)
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
        put("gradient", JSONObject().apply {
            put("colorStart", gradient.colorStart)
            put("colorEnd", gradient.colorEnd)
            put("angle", gradient.angle.toDouble())
        })
        if (shadow != null) {
            put("shadow", JSONObject().apply {
                put("dx", shadow!!.dx.toDouble())
                put("dy", shadow!!.dy.toDouble())
                put("blur", shadow!!.blur.toDouble())
                put("color", shadow!!.color)
                put("opacity", shadow!!.opacity.toDouble())
                put("spread", shadow!!.spread.toDouble())
            })
        }
        put("letterSpacing", letterSpacing.toDouble())
        put("wordSpacing", wordSpacing.toDouble())
        put("lineSpacing", lineSpacing.toDouble())
    }

    companion object {
        fun fromBox(box: TextBox, name: String): TextStylePreset = TextStylePreset(
            name = name.ifBlank { "Style" },
            fontName = box.fontName,
            fontSize = box.fontSize,
            color = box.color,
            bold = box.bold,
            italic = box.italic,
            align = box.align,
            outlineWidth = box.outlineWidth,
            outlineColor = box.outlineColor,
            strokeOpacity = box.strokeOpacity,
            strokePosition = box.strokePosition,
            fillType = box.fillType,
            gradient = box.gradient.copy(),
            shadow = box.shadow?.copy(),
            letterSpacing = box.letterSpacing,
            wordSpacing = box.wordSpacing,
            lineSpacing = box.lineSpacing
        )

        fun fromJson(o: JSONObject): TextStylePreset {
            val gradient = TextGradientSpec(
                colorStart = o.optJSONObject("gradient")?.optInt(
                    "colorStart", android.graphics.Color.WHITE
                ) ?: android.graphics.Color.WHITE,
                colorEnd = o.optJSONObject("gradient")?.optInt(
                    "colorEnd", android.graphics.Color.parseColor("#FF5722")
                ) ?: android.graphics.Color.parseColor("#FF5722"),
                angle = o.optJSONObject("gradient")?.optDouble("angle", 90.0)?.toFloat() ?: 90f
            )
            val shadowObj = o.optJSONObject("shadow")
            val shadow = if (shadowObj != null) {
                TextShadowSpec(
                    dx = shadowObj.optDouble("dx", 4.0).toFloat(),
                    dy = shadowObj.optDouble("dy", 4.0).toFloat(),
                    blur = shadowObj.optDouble("blur", 8.0).toFloat(),
                    color = shadowObj.optInt("color", 0x80000000.toInt()),
                    opacity = shadowObj.optDouble("opacity", 0.75).toFloat(),
                    spread = shadowObj.optDouble("spread", 0.0).toFloat()
                )
            } else null
            return TextStylePreset(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Style"),
                fontName = o.optString("fontName", "Default Bold"),
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
                gradient = gradient,
                shadow = shadow,
                letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
                wordSpacing = o.optDouble("wordSpacing", 0.0).toFloat(),
                lineSpacing = o.optDouble("lineSpacing", 12.0).toFloat()
            )
        }
    }
}

/** Penyimpanan preset di SharedPreferences (terurut: terbaru di atas). */
class TextStyleManager(context: Context) {
    private val prefs = context.getSharedPreferences("grooxtyper_text_styles", Context.MODE_PRIVATE)

    fun list(): List<TextStylePreset> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> TextStylePreset.fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun save(preset: TextStylePreset): List<TextStylePreset> {
        val updated = listOf(preset) + list().filter { it.id != preset.id }
        persist(updated)
        return updated
    }

    fun delete(id: String): List<TextStylePreset> {
        val updated = list().filter { it.id != id }
        persist(updated)
        return updated
    }

    private fun persist(items: List<TextStylePreset>) {
        val arr = JSONArray()
        items.take(MAX).forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "styles_v1"
        private const val MAX = 60
    }
}
