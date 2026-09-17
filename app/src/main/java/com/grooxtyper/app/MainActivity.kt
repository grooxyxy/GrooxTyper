package com.grooxtyper.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.grooxtyper.app.ui.CanvasEditorScreen
import com.grooxtyper.app.ui.GalleryScreen

enum class AppScreen {
    GALLERY,
    EDITOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.GALLERY) }
                var activeProjectId by remember { mutableStateOf("${System.currentTimeMillis()}") }
                var activeProjectTitle by remember { mutableStateOf<String?>(null) }
                var activeCanvasWidth by remember { mutableStateOf(1280) }
                var activeCanvasHeight by remember { mutableStateOf(1280) }
                var activeInitialBitmap by remember { mutableStateOf<Bitmap?>(null) }

                when (currentScreen) {
                    AppScreen.GALLERY -> {
                        GalleryScreen(
                            onOpenCanvas = { id, width, height, bitmap, title ->
                                activeProjectId = id
                                activeProjectTitle = title
                                activeCanvasWidth = width
                                activeCanvasHeight = height
                                activeInitialBitmap = bitmap
                                currentScreen = AppScreen.EDITOR
                            }
                        )
                    }
                    AppScreen.EDITOR -> {
                        CanvasEditorScreen(
                            projectId = activeProjectId,
                            initialTitle = activeProjectTitle,
                            canvasWidth = activeCanvasWidth,
                            canvasHeight = activeCanvasHeight,
                            initialBitmap = activeInitialBitmap,
                            onBackToGallery = {
                                currentScreen = AppScreen.GALLERY
                            }
                        )
                    }
                }
            }
        }
    }
}
