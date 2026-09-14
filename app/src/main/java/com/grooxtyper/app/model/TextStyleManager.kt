package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.io.FileOutputStream

data class SavedTextStyle(
    val id: String,
    val name: String,
    val textConfig: StackableTextConfig
)

class TextStyleManager(private val context: Context) {
    val savedStyles = mutableStateListOf<SavedTextStyle>()
    val customTypefaces = mutableStateListOf<Pair<String, Typeface>>()

    init {
        savedStyles.add(
            SavedTextStyle(
                id = "preset_comic",
                name = "Comic Hero",
                textConfig = StackableTextConfig(
                    text = "Text",
                    fontSize = 64f,
                    textColor = Color.YELLOW,
                    hasOutline = true,
                    outlineColor = Color.BLACK,
                    outlineWidthPx = 12f,
                    hasShadow = true,
                    shadowColor = Color.BLACK,
                    shadowDxPx = 8f,
                    shadowDyPx = 8f
                )
            )
        )
        savedStyles.add(
            SavedTextStyle(
                id = "preset_neon",
                name = "Neon Pink",
                textConfig = StackableTextConfig(
                    text = "Text",
                    fontSize = 64f,
                    textColor = Color.MAGENTA,
                    hasOutline = true,
                    outlineColor = Color.WHITE,
                    outlineWidthPx = 6f,
                    hasGradient = true,
                    gradientStartColor = Color.MAGENTA,
                    gradientEndColor = Color.CYAN
                )
            )
        )

        customTypefaces.add("Default Bold" to Typeface.DEFAULT_BOLD)
        customTypefaces.add("Sans Serif" to Typeface.SANS_SERIF)
        customTypefaces.add("Serif" to Typeface.SERIF)
        customTypefaces.add("Monospace" to Typeface.MONOSPACE)
    }

    fun saveStyle(name: String, config: StackableTextConfig) {
        val style = SavedTextStyle(
            id = "style_${System.currentTimeMillis()}",
            name = name,
            textConfig = config
        )
        savedStyles.add(style)
    }

    fun deleteStyle(id: String) {
        savedStyles.removeAll { it.id == id }
    }

    fun importFontFromUri(context: Context, uri: android.net.Uri, fontName: String): Typeface? {
        return try {
            val fontsDir = File(context.filesDir, "custom_fonts")
            if (!fontsDir.exists()) fontsDir.mkdirs()
            val fontFile = File(fontsDir, "$fontName.ttf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(fontFile).use { output ->
                    input.copyTo(output)
                }
            }

            val tf = Typeface.createFromFile(fontFile)
            if (tf != null) {
                customTypefaces.add(fontName to tf)
            }
            tf
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
