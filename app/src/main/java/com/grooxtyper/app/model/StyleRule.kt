package com.grooxtyper.app.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Style Rule: jika baris script diawali [prefix] maka pakai style [styleId],
 * dan awalan dihapus dari teks yang dirender bila [stripPrefix] true.
 * Contoh prefix: "() : ", '"": ', "[SFX]", "Narasi: ".
 * Cocok persis di awal baris setelah trimStart (case-sensitive) agar
 * pola seperti '() : ' dan '"": ' tidak tertukar.
 */
data class StyleRule(
    val id: String = UUID.randomUUID().toString(),
    var prefix: String = "",
    var styleId: String = "",
    var stripPrefix: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("prefix", prefix)
        put("styleId", styleId)
        put("stripPrefix", stripPrefix)
    }

    companion object {
        fun fromJson(o: JSONObject): StyleRule = StyleRule(
            id = o.optString("id", UUID.randomUUID().toString()),
            prefix = o.optString("prefix", ""),
            styleId = o.optString("styleId", ""),
            stripPrefix = o.optBoolean("stripPrefix", true)
        )
    }
}

/** Penyimpanan rules di SharedPreferences (tanpa dependency tambahan). */
class StyleRuleManager(context: Context) {
    private val prefs = context.getSharedPreferences("grooxtyper_style_rules", Context.MODE_PRIVATE)

    fun list(): List<StyleRule> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> StyleRule.fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun save(rule: StyleRule): List<StyleRule> {
        val updated = listOf(rule) + list().filter { it.id != rule.id }
        persist(updated)
        return updated
    }

    fun delete(id: String): List<StyleRule> {
        val updated = list().filter { it.id != id }
        persist(updated)
        return updated
    }

    private fun persist(items: List<StyleRule>) {
        val arr = JSONArray()
        items.filter { it.prefix.isNotEmpty() && it.styleId.isNotEmpty() }
            .take(MAX).forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "rules_v1"
        private const val MAX = 100
    }
}
