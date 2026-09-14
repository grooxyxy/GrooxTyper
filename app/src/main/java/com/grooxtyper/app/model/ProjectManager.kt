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

    fun saveProject(id: String, title: String, width: Int, height: Int, bitmap: Bitmap) {
        try {
            val imageFile = File(projectsDir, "proj_$id.png")
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val currentProjects = loadProjects().toMutableList()
            currentProjects.removeAll { it.id == id }
            currentProjects.add(0, SavedProject(id, title, width, height, System.currentTimeMillis(), imageFile.absolutePath))

            val array = JSONArray()
            currentProjects.forEach { proj ->
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

    fun loadProjectBitmap(imagePath: String): Bitmap? {
        val f = File(imagePath)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun deleteProject(id: String) {
        val current = loadProjects().toMutableList()
        val target = current.find { it.id == id }
        if (target != null) {
            val f = File(target.imagePath)
            if (f.exists()) f.delete()
            current.remove(target)
            val array = JSONArray()
            current.forEach { proj ->
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
        }
    }
}
