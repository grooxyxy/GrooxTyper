package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.ml.MLMaskType
import com.grooxtyper.app.ml.MLTextDetector
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.DrawingLayer
import com.grooxtyper.app.model.ExportFormat
import com.grooxtyper.app.model.FileExportManager
import com.grooxtyper.app.model.ForceFadeConfig
import com.grooxtyper.app.model.InpaintingManager
import com.grooxtyper.app.model.LayerBlendMode
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.TextConfig
import com.grooxtyper.app.model.TextEngine
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.launch

enum class ActiveTool {
    BRUSH,
    LASSO,
    TEXT,
    RULER,
    INPAINT
}

@Composable
fun CanvasEditorScreen(
    canvasWidth: Int,
    canvasHeight: Int,
    onBackToGallery: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val layerManager = remember { LayerManager(canvasWidth, canvasHeight) }
    val brushEngine = remember { BrushEngine() }
    val selectionEngine = remember { SelectionEngine(canvasWidth, canvasHeight) }
    val textEngine = remember { TextEngine() }
    val inpaintingManager = remember { InpaintingManager() }
    val mlTextDetector = remember { MLTextDetector() }
    val undoRedoManager = remember { UndoRedoManager() }
    val exportManager = remember { FileExportManager(context) }
    val viewState = remember { CanvasViewState(canvasWidth, canvasHeight) }

    var activeTool by remember { mutableStateOf(ActiveTool.BRUSH) }

    var compositeBitmap by remember {
        mutableStateOf(Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888))
    }

    var refreshCanvasTrigger by remember { mutableStateOf(0) }

    fun refreshComposite() {
        layerManager.renderComposite(compositeBitmap)
        refreshCanvasTrigger++
    }

    var showColorPicker by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showBrushSettings by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Image Picker Launcher for Reference / Background
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(inputStream)
            bmp?.let { loaded ->
                referenceBitmap = loaded
                val active = layerManager.getActiveLayer()
                if (active != null) {
                    active.tileMap.importFromBitmap(loaded)
                    active.markDirty()
                    refreshComposite()
                }
            }
        }
    }

    // Touch gesture drawing state
    var currentTouchPath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
    ) {
        // Main Interactive Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        viewState.scale = (viewState.scale * zoom).coerceIn(0.1f, 10.0f)
                        viewState.offsetX += pan.x
                        viewState.offsetY += pan.y
                        viewState.rotation += rotation
                    }
                }
                .pointerInput(refreshCanvasTrigger, activeTool) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val activeLayer = layerManager.getActiveLayer() ?: return@detectDragGestures
                            undoRedoManager.saveSnapshot(activeLayer)

                            val canvasOffset = viewState.windowToCanvasCoordinates(offset.x, offset.y)
                            val path = Path()
                            path.moveTo(canvasOffset.x, canvasOffset.y)
                            currentTouchPath = path

                            if (activeTool == ActiveTool.TEXT) {
                                showTextDialog = true
                            }
                        },
                        onDrag = { change, _ ->
                            val canvasOffset = viewState.windowToCanvasCoordinates(
                                change.position.x,
                                change.position.y
                            )
                            currentTouchPath?.lineTo(canvasOffset.x, canvasOffset.y)

                            val activeLayer = layerManager.getActiveLayer()
                            if (activeTool == ActiveTool.BRUSH && activeLayer != null && currentTouchPath != null) {
                                brushEngine.strokePathOnLayer(activeLayer, currentTouchPath!!)
                                refreshComposite()
                            }
                        },
                        onDragEnd = {
                            val activeLayer = layerManager.getActiveLayer()
                            if (activeTool == ActiveTool.LASSO && currentTouchPath != null) {
                                selectionEngine.setLassoPath(currentTouchPath!!)
                            } else if (activeTool == ActiveTool.BRUSH && activeLayer != null && currentTouchPath != null) {
                                brushEngine.strokePathOnLayer(activeLayer, currentTouchPath!!)
                                refreshComposite()
                            }
                            currentTouchPath = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = viewState.scale,
                        scaleY = viewState.scale,
                        translationX = viewState.offsetX,
                        translationY = viewState.offsetY,
                        rotationZ = viewState.rotation
                    )
            ) {
                drawContext.canvas.nativeCanvas.drawBitmap(compositeBitmap, 0f, 0f, null)

                // Render selection outline if active
                if (selectionEngine.hasSelection) {
                    val marchPaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                        color = android.graphics.Color.YELLOW
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    }
                    drawContext.canvas.nativeCanvas.drawPath(selectionEngine.selectionPath, marchPaint)
                }
            }
        }

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF222222))
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToGallery) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    undoRedoManager.undo(layerManager)
                    refreshComposite()
                },
                enabled = undoRedoManager.canUndo()
            ) {
                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (undoRedoManager.canUndo()) Color.White else Color.Gray)
            }

            IconButton(
                onClick = {
                    undoRedoManager.redo(layerManager)
                    refreshComposite()
                },
                enabled = undoRedoManager.canRedo()
            ) {
                Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (undoRedoManager.canRedo()) Color.White else Color.Gray)
            }

            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = "Add Image", tint = Color.White)
            }

            IconButton(onClick = { showExportMenu = true }) {
                Icon(Icons.Default.Flip, contentDescription = "Export", tint = Color.White)
            }

            DropdownMenu(
                expanded = showExportMenu,
                onDismissRequest = { showExportMenu = false }
            ) {
                ExportFormat.values().forEach { fmt ->
                    DropdownMenuItem(
                        text = { Text("Export as ${fmt.name}") },
                        onClick = {
                            showExportMenu = false
                            exportManager.exportArtwork(layerManager, fmt)
                        }
                    )
                }
            }
        }

        // Bottom Tool Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF1A1A1A))
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { activeTool = ActiveTool.BRUSH; showBrushSettings = true }
            ) {
                Icon(
                    Icons.Default.Brush,
                    contentDescription = "Brush",
                    tint = if (activeTool == ActiveTool.BRUSH) Color(0xFFFF9800) else Color.White
                )
            }

            IconButton(
                onClick = { showColorPicker = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(brushEngine.color))
                        .border(1.dp, Color.White, CircleShape)
                )
            }

            IconButton(
                onClick = { activeTool = ActiveTool.LASSO }
            ) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = "Lasso",
                    tint = if (activeTool == ActiveTool.LASSO) Color(0xFFFF9800) else Color.White
                )
            }

            IconButton(
                onClick = { activeTool = ActiveTool.TEXT }
            ) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = "Text",
                    tint = if (activeTool == ActiveTool.TEXT) Color(0xFFFF9800) else Color.White
                )
            }

            IconButton(
                onClick = {
                    scope.launch {
                        val active = layerManager.getActiveLayer()
                        if (active != null) {
                            val detected = mlTextDetector.detectTextRegions(active.getBitmap())
                            val mask = mlTextDetector.generateMaskBitmap(
                                canvasWidth,
                                canvasHeight,
                                detected,
                                active.getBitmap(),
                                MLMaskType.REFINED_TEXT
                            )
                            inpaintingManager.inpaintLayerArea(active, mask)
                            refreshComposite()
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = "Inpaint ML",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { showLayersPanel = true }
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = "Layers",
                    tint = Color.White
                )
            }
        }

        // Floating Reference Window
        ReferenceWindow(
            referenceBitmap = referenceBitmap,
            onClose = { referenceBitmap = null }
        )

        // Color Picker Modal
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = brushEngine.color,
                onColorSelected = { brushEngine.color = it },
                onDismiss = { showColorPicker = false }
            )
        }

        // Brush & Ruler Settings Modal
        if (showBrushSettings) {
            AlertDialog(
                onDismissRequest = { showBrushSettings = false },
                title = { Text("Brush & Tool Settings", color = Color.White) },
                text = {
                    Column {
                        Text("Brush Type", color = Color.LightGray, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BrushType.values().forEach { b ->
                                TextButton(onClick = { brushEngine.brushType = b }) {
                                    Text(
                                        b.displayName.take(4),
                                        color = if (brushEngine.brushType == b) Color(0xFFFF9800) else Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Size (${brushEngine.size.toInt()}px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = brushEngine.size,
                            onValueChange = { brushEngine.size = it },
                            valueRange = 1f..200f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Opacity (${(brushEngine.opacity * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = brushEngine.opacity,
                            onValueChange = { brushEngine.opacity = it },
                            valueRange = 0.05f..1.0f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Force Fade", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = brushEngine.forceFade.isEnabled,
                                onCheckedChange = {
                                    brushEngine.forceFade = ForceFadeConfig(isEnabled = it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ruler Guide", color = Color.LightGray, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            RulerType.values().forEach { r ->
                                TextButton(onClick = { brushEngine.rulerGuide.type = r }) {
                                    Text(
                                        r.name,
                                        color = if (brushEngine.rulerGuide.type == r) Color(0xFFFF9800) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showBrushSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                        Text("Close", color = Color.Black)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Layers Panel Drawer Modal
        if (showLayersPanel) {
            AlertDialog(
                onDismissRequest = { showLayersPanel = false },
                title = { Text("Layer Manager", color = Color.White) },
                text = {
                    Column(modifier = Modifier.height(300.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    layerManager.addLayer()
                                    refreshComposite()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Text("Add Layer", color = Color.Black)
                            }

                            Button(
                                onClick = {
                                    val active = layerManager.getActiveLayer()
                                    if (active != null) {
                                        layerManager.deleteLayer(active.id)
                                        refreshComposite()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(layerManager.layers) { layerItem ->
                                val isSelected = layerItem.id == layerManager.activeLayerId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF3D3D3D) else Color(0xFF222222))
                                        .clickable {
                                            if (layerItem is DrawingLayer) layerManager.activeLayerId = layerItem.id
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(layerItem.name, color = Color.White, modifier = Modifier.weight(1f))
                                    Text("${(layerItem.opacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            layerItem.isVisible = !layerItem.isVisible
                                            refreshComposite()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text(if (layerItem.isVisible) "V" else "H", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showLayersPanel = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                        Text("Done", color = Color.Black)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Add Text Dialog Modal
        if (showTextDialog) {
            var inputString by remember { mutableStateOf("ibisPaint") }
            var textSizeVal by remember { mutableFloatStateOf(48f) }

            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text("Add Text", color = Color.White) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = inputString,
                            onValueChange = { inputString = it },
                            label = { Text("Text String") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Size (${textSizeVal.toInt()}px)", color = Color.LightGray)
                        Slider(
                            value = textSizeVal,
                            onValueChange = { textSizeVal = it },
                            valueRange = 12f..120f
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val active = layerManager.getActiveLayer()
                            if (active != null) {
                                val cfg = TextConfig(
                                    text = inputString,
                                    fontSize = textSizeVal,
                                    textColor = brushEngine.color,
                                    hasOutline = true,
                                    hasShadow = true
                                )
                                textEngine.drawTextOnLayer(active, cfg, Offset(200f, 300f))
                                refreshComposite()
                            }
                            showTextDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Render", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }
    }
}
