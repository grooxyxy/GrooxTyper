package com.grooxtyper.app.model

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistensi image layer per project.
 *
 * SEBELUMNYA image layer (stiker/watermark) ikut di-bake ke PNG dasar saat
 * autosave, sehingga begitu project ditutup dan dibuka lagi gambarnya
 * menjadi bagian piksel biasa: tidak bisa dipilih, digeser, di-resize, diubah
 * opacity, atau dihapus lagi. Untuk watermark itu fatal — satu klik keliru
 * berarti layer hilang.
 *
 * Sekarang bitmap sumber tiap image layer ditulis sebagai file PNG terpisah
 * (`img_<layerId>.png`) plus metadata JSON, persis pola yang sudah dipakai
 * TextLayerStore untuk layer teks. Restore mengembalikan image sebagai
 * [ImageLayer] utuh dengan posisi/ukuran/rotasi/opacity/flip yang sama.
 *
 * Catatan kompatibilitas:
 *  - Project lama (tanpa file `.images.json`) → restore kosong, perilaku lama
 *    (gambar sudah baked di PNG) tetap berlaku.
 *  - PNG losless dipilih, bukan JPEG: screenshot/manga punya garis tipis dan
 *    warna rata; JPEG memunculkan artefak di tepi garis. File per layer
 *    biasanya kecil karena gambar disimpan pada resolusi yang dipakai user.
 */
object ImageLayerStore {
    const val VERSION = 1
    private const val ASSET_DIR = "images"
    private const val ASSET_EXT = ".png"

    /** Direktori aset image untuk satu project. */
    fun assetDir(projectDir: File): File = File(projectDir, ASSET_DIR)

    fun assetFileFor(projectDir: File, layerId: String): File =
        File(assetDir(projectDir), "img_$layerId$ASSET_EXT")

    /**
     * Tulis bitmap sumber setiap image layer ke disk lalu kembalikan JSON
     * metadata. Kembalikan null bila tidak ada image layer (tidak perlu
     * menulis file sama sekali).
     *
     * Node yang gagal ditulis TIDAK ikut masuk JSON — pemanggil tahu bahwa
     * ada image yang gagal dan bisa membakar sisanya ke PNG dasar sebagai
     * fallback (lihat CanvasEditorScreen.saveProjectInternal), sehingga user
     * tidak pernah kehilangan gambar.
     */
    fun save(
        manager: LayerManager,
        projectDir: File
    ): String? {
        val entries = mutableListOf<JSONObject>()
        var count = 0

        fun writeNode(item: LayerItem, parentId: String?, z: Int) {
            if (item is ImageLayer) {
                count++
                if (item.bitmap.isRecycled) return
                val target = assetFileFor(projectDir, item.id)
                val ok = runCatching {
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, target.name + ".tmp")
                    var written = false
                    try {
                        tmp.outputStream().use { out ->
                            written = item.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                        }
                        if (!written || !tmp.exists() || tmp.length() < 64L) {
                            runCatching { tmp.delete() }
                            false
                        } else if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            runCatching { tmp.delete() }
                            true
                        } else true
                    } catch (e: Exception) {
                        runCatching { tmp.delete() }
                        false
                    }
                }.getOrDefault(false)
                if (!ok) return // gagal → jangan daftarkan, caller akan fallback bake
                entries.add(
                    JSONObject().apply {
                        put("id", item.id)
                        put("parentId", parentId ?: JSONObject.NULL)
                        put("z", z)
                        put("name", item.name)
                        put("asset", target.name)
                        put("centerX", item.centerX.toDouble())
                        put("centerY", item.centerY.toDouble())
                        put("widthPx", item.widthPx.toDouble())
                        put("heightPx", item.heightPx.toDouble())
                        put("rotationDeg", item.rotationDeg.toDouble())
                        put("opacity", item.opacity.toDouble())
                        put("flipX", item.flipX)
                        put("flipY", item.flipY)
                        put("lockedAspect", item.lockedAspect)
                        put("isVisible", item.isVisible)
                        put("blendMode", item.blendMode.name)
                        put("isAlphaLocked", item.isAlphaLocked)
                        put("isClippingMask", item.isClippingMask)
                    }
                )
            } else if (item.isFolder) {
                for ((index, child) in item.children.withIndex()) writeNode(child, item.id, index)
            }
        }
        for ((index, item) in manager.layers.withIndex()) writeNode(item, null, index)

        if (count == 0) {
            // Tak ada image: hapus file lama supaya tidak menumpuk sia-sia.
            runCatching { assetDir(projectDir).deleteRecursively() }
            return null
        }
        if (entries.isEmpty()) {
            // Semua gagal ditulis → kembalikan null agar caller bake ke PNG.
            return null
        }
        return JSONObject().apply {
            put("version", VERSION)
            put("images", entries)
        }.toString()
    }

    /** Baca JSON metadata (tanpa memuat bitmap). Null bila belum ada. */
    fun parse(raw: String?): List<ImageLayerSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("images") ?: JSONArray()
            val out = mutableListOf<ImageLayerSpec>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val asset = o.optString("asset", "")
                if (asset.isBlank()) continue
                out.add(
                    ImageLayerSpec(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        parentId = if (o.isNull("parentId")) null else o.optString("parentId"),
                        z = o.optDouble("z", Float.MAX_VALUE.toDouble()).toFloat(),
                        name = o.optString("name", "Image"),
                        assetFile = asset,
                        centerX = o.optDouble("centerX", 0.0).toFloat(),
                        centerY = o.optDouble("centerY", 0.0).toFloat(),
                        widthPx = o.optDouble("widthPx", 64.0).toFloat(),
                        heightPx = o.optDouble("heightPx", 64.0).toFloat(),
                        rotationDeg = o.optDouble("rotationDeg", 0.0).toFloat(),
                        opacity = o.optDouble("opacity", 1.0).toFloat(),
                        flipX = o.optBoolean("flipX", false),
                        flipY = o.optBoolean("flipY", false),
                        lockedAspect = o.optBoolean("lockedAspect", true),
                        isVisible = o.optBoolean("isVisible", true),
                        blendMode = o.optString("blendMode", "NORMAL"),
                        isAlphaLocked = o.optBoolean("isAlphaLocked", false),
                        isClippingMask = o.optBoolean("isClippingMask", false)
                    )
                )
            }
            out
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    data class ImageLayerSpec(
        val id: String,
        val parentId: String?,
        val z: Float,
        val name: String,
        val assetFile: String,
        val centerX: Float,
        val centerY: Float,
        val widthPx: Float,
        val heightPx: Float,
        val rotationDeg: Float,
        val opacity: Float,
        val flipX: Boolean,
        val flipY: Boolean,
        val lockedAspect: Boolean,
        val isVisible: Boolean,
        val blendMode: String,
        val isAlphaLocked: Boolean,
        val isClippingMask: Boolean
    )

    /** Bangun [ImageLayer] dari spec + bitmap yang sudah di-decode. */
    fun buildLayer(spec: ImageLayerSpec, bitmap: Bitmap): ImageLayer = ImageLayer(
        bitmap = bitmap,
        centerX = spec.centerX,
        centerY = spec.centerY,
        widthPx = spec.widthPx,
        heightPx = spec.heightPx,
        rotationDeg = spec.rotationDeg,
        flipX = spec.flipX,
        flipY = spec.flipY,
        name = spec.name
    ).apply {
        lockedAspect = spec.lockedAspect
        opacity = spec.opacity.coerceIn(0f, 1f)
        restoreZ = spec.z
        isVisible = spec.isVisible
        blendMode = runCatching { LayerBlendMode.valueOf(spec.blendMode) }
            .getOrDefault(LayerBlendMode.NORMAL)
        isAlphaLocked = spec.isAlphaLocked
        isClippingMask = spec.isClippingMask
    }
}
