package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedProject(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val lastModified: Long,
    val imagePath: String
)

class ProjectManager(private val context: Context) {
    private val projectsDir = File(context.filesDir, "projects")
    private val metaFile = File(projectsDir, "projects.json")

    init {
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
    }

    fun loadProjects(): List<SavedProject> {
        if (!metaFile.exists()) return emptyList()
        val list = mutableListOf<SavedProject>()
        try {
            val jsonStr = metaFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val width = obj.getInt("width")
                val height = obj.getInt("height")
                val lastModified = obj.getLong("lastModified")
                val imagePath = obj.getString("imagePath")
                list.add(SavedProject(id, title, width, height, lastModified, imagePath))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.lastModified }
    }

    fun getProject(id: String): SavedProject? = loadProjects().find { it.id == id }

    /**
     * Simpan gambar tanpa mengubah judul/dimensi yang sudah ada
     * (dipakai auto-save agar tidak menimpa judul proyek).
     */
    fun saveArtwork(id: String, bitmap: Bitmap) {
        val existing = getProject(id)
        if (existing == null) {
            saveProject(id, "GrooxTyper Artwork", bitmap.width, bitmap.height, bitmap)
            return
        }
        try {
            java.io.File(existing.imagePath).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val current = loadProjects().toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                current[idx] = current[idx].copy(lastModified = System.currentTimeMillis())
                persistProjects(current)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveProject(id: String, title: String, width: Int, height: Int, bitmap: Bitmap) {
        try {
            val imageFile = File(projectsDir, "proj_$id.png")
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val currentProjects = loadProjects().toMutableList()
            currentProjects.removeAll { it.id == id }
            currentProjects.add(0, SavedProject(id, title, width, height, System.currentTimeMillis(), imageFile.absolutePath))

            persistProjects(currentProjects)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProjectBitmap(imagePath: String): Bitmap? {
        val f = File(imagePath)
        if (!f.exists()) return null
        // Heap-aware + tiled (mendukung 720x16000 tanpa OOM mentah).
        // Fallback ke decode mentah bila helper gagal (kompatibilitas file lama).
        return try {
            ImageImport.decodeFileHeapAware(f.absolutePath)
                ?: BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            try {
                ImageImport.decodeFileSampled(f.absolutePath, ImageImport.MAX_THUMB_DIM)
            } catch (_: Exception) { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteProject(id: String) {
        val current = loadProjects().toMutableList()
        val target = current.find { it.id == id }
        if (target != null) {
            val f = File(target.imagePath)
            if (f.exists()) f.delete()
            runCatching { textsFileFor(id).takeIf { it.exists() }?.delete() }
            runCatching { previewFileFor(id).takeIf { it.exists() }?.delete() }
            current.remove(target)
            persistProjects(current)
        }
    }

    // ---------- State editable (teks tetap bisa diedit setelah apk ditutup) ----------
    // Basis gambar (proj_<id>.png) kini = PNG layer gambar SAJA (tanpa teks
    // bakar); teks disimpan terpisah sebagai JSON. File lama (format lawas)
    // berisi komposit + teks bakar: tetap dibuka via jalur legacy apa adanya.

    /** JSON teks terpisah per project. */
    fun textsFileFor(id: String): File = File(projectsDir, "proj_${id}_texts.json")

    /** Preview kecil komposit + teks untuk thumbnail galeri (opsional). */
    fun previewFileFor(id: String): File = File(projectsDir, "proj_${id}_preview.png")

    fun saveTexts(id: String, json: String) {
        runCatching { textsFileFor(id).writeText(json) }
    }

    /** null bila belum pernah disimpan format baru (project lawas → jalur legacy). */
    fun loadTexts(id: String): String? = runCatching {
        textsFileFor(id).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun previewPathFor(id: String): String? = runCatching {
        previewFileFor(id).takeIf { it.exists() }?.absolutePath
    }.getOrNull()

    fun savePreview(id: String, bmp: Bitmap) {
        runCatching {
            previewFileFor(id).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    private fun persistProjects(projects: List<SavedProject>) {
        try {
            val array = JSONArray()
            projects.forEach { proj ->
                val obj = JSONObject().apply {
                    put("id", proj.id)
                    put("title", proj.title)
                    put("width", proj.width)
                    put("height", proj.height)
                    put("lastModified", proj.lastModified)
                    put("imagePath", proj.imagePath)
                }
                array.put(obj)
            }
            metaFile.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
