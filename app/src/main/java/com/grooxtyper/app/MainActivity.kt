package com.grooxtyper.app

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
                var activeCanvasWidth by remember { mutableStateOf(1280) }
                var activeCanvasHeight by remember { mutableStateOf(1280) }

                when (currentScreen) {
                    AppScreen.GALLERY -> {
                        GalleryScreen(
                            onOpenCanvas = { width, height ->
                                activeCanvasWidth = width
                                activeCanvasHeight = height
                                currentScreen = AppScreen.EDITOR
                            }
                        )
                    }
                    AppScreen.EDITOR -> {
                        CanvasEditorScreen(
                            canvasWidth = activeCanvasWidth,
                            canvasHeight = activeCanvasHeight,
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
