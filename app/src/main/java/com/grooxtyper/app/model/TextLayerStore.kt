package com.grooxtyper.app.model

import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistensi layer teks per project (teks tetap editable setelah apk
 * ditutup). Layer gambar di-flatten ke PNG basis TANPA teks; teks disimpan
 * sebagai JSON kecil. Format v1, toleran (opt + default) agar file lama/
 * rusak tidak meledakkan restore (fallback: lewati node rusak).
 */
object TextLayerStore {
    const val VERSION = 1

    /** Seluruh teks (+ folder penampungnya) dalam urutan atas-dulu (indeks 0 = teratas). */
    fun LayerManager.textLayersToJson(): String {
        val root = JSONObject().apply {
            put("version", VERSION)
            put("activeLayerId", activeLayerId)
        }
        val arr = JSONArray()
        // Hanya node teks & folder (gambar sudah di PNG basis).
        for (item in layers) {
            nodeToJson(item)?.let { arr.put(it) }
        }
        root.put("layers", arr)
        return root.toString()
    }

    private fun nodeToJson(item: LayerItem): JSONObject? {
        return when {
            item is TextLayer -> textNodeToJson(item)
            item.isFolder -> {
                val children = JSONArray()
                for (child in item.children) {
                    nodeToJson(child)?.let { children.put(it) }
                }
                JSONObject().apply {
                    put("id", item.id)
                    put("kind", "folder")
                    put("name", item.name)
                    putLayerProps(this, item)
                    put("children", children)
                }
            }
            else -> null // DrawingLayer: sudah di PNG basis, lewati.
        }
    }

    private fun textNodeToJson(layer: TextLayer): JSONObject = JSONObject().apply {
        put("id", layer.id)
        put("kind", "text")
        put("name", layer.name)
        putLayerProps(this, layer)
        put("box", with(TextBox) { layer.box.toJson() })
    }

    private fun putLayerProps(o: JSONObject, item: LayerItem) {
        o.put("isVisible", item.isVisible)
        o.put("opacity", item.opacity.toDouble())
        o.put("blendMode", item.blendMode.name)
        o.put("isAlphaLocked", item.isAlphaLocked)
        o.put("isClippingMask", item.isClippingMask)
    }

    data class RestoredLayers(
        /** Node atas-dulu, siap disisipkan balik via add(0) dari belakang. */
        val topFirst: List<LayerItem>,
        val activeLayerId: String?
    )

    /**
     * Parse JSON teks. Node rusak dilewati satu-per-satu (bukan gagal total).
     * ID layer dipertahankan agar undo/seleksi konsisten.
     */
    fun parseLayers(
        raw: String?,
        typefaceFor: (String) -> Typeface
    ): RestoredLayers {
        if (raw.isNullOrBlank()) return RestoredLayers(emptyList(), null)
        return try {
            val root = JSONObject(raw)
            val active = root.optString("activeLayerId", "").ifBlank { null }
            val arr = root.optJSONArray("layers") ?: JSONArray()
            val out = mutableListOf<LayerItem>()
            for (i in 0 until arr.length()) {
                runCatching { nodeFromJson(arr.getJSONObject(i), typefaceFor) }.getOrNull()
                    ?.let { out.add(it) }
            }
            RestoredLayers(out, active)
        } catch (e: Exception) {
            e.printStackTrace()
            RestoredLayers(emptyList(), null)
        }
    }

    private fun nodeFromJson(o: JSONObject, typefaceFor: (String) -> Typeface): LayerItem? {
        return when (o.optString("kind", "")) {
            "text" -> {
                val boxObj = o.optJSONObject("box") ?: return null
                val box = runCatching {
                    with(TextBox) { boxFromJson(boxObj, typefaceFor) }
                }.getOrNull() ?: return null
                TextLayer(
                    box = box,
                    name = o.optString("name", "Text: ${box.text.take(16)}"),
                    layerId = o.optString("id", java.util.UUID.randomUUID().toString())
                ).apply { applyLayerProps(o, this) }
            }
            "folder" -> {
                LayerItem(
                    name = o.optString("name", "Folder"),
                    isFolder = true,
                    id = o.optString("id", java.util.UUID.randomUUID().toString())
                ).apply {
                    applyLayerProps(o, this)
                    val children = o.optJSONArray("children") ?: JSONArray()
                    for (i in 0 until children.length()) {
                        runCatching { nodeFromJson(children.getJSONObject(i), typefaceFor) }
                            .getOrNull()?.let { child ->
                                // Hanya teks/folder yang dipertahankan (gambar di basis).
                                if (child is TextLayer || child.isFolder) this.children.add(child)
                            }
                    }
                }
            }
            else -> null
        }
    }

    private fun applyLayerProps(o: JSONObject, item: LayerItem) {
        item.isVisible = o.optBoolean("isVisible", true)
        item.opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
        item.blendMode = runCatching {
            LayerBlendMode.valueOf(o.optString("blendMode", "NORMAL"))
        }.getOrDefault(LayerBlendMode.NORMAL)
        item.isAlphaLocked = o.optBoolean("isAlphaLocked", false)
        item.isClippingMask = o.optBoolean("isClippingMask", false)
    }

    /**
     * Pasang hasil restore ke manager BARU (isi 1 drawing layer awal).
     * Kembalikan box terpilih bila activeId cocok teks hasil restore.
     */
    fun LayerManager.restoreTextLayers(
        restored: RestoredLayers,
        selectBox: (TextBox?) -> Unit = {}
    ) {
        if (restored.topFirst.isEmpty()) return
        // Sisipkan agar urutan atas-dulu pulih (add(0) dari belakang).
        for (node in restored.topFirst.asReversed()) {
            if (findLayerById(node.id) == null) {
                layers.add(0, node)
            }
        }
        val active = restored.activeLayerId
        if (!active.isNullOrBlank() && findLayerById(active) != null) {
            activeLayerId = active
            selectBox((findLayerById(active) as? TextLayer)?.box)
        }
    }
}
