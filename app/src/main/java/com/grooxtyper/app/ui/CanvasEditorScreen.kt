package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import com.grooxtyper.app.ml.DetectedTextRegion
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
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.StackableTextConfig
import com.grooxtyper.app.model.TextEngine
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveTool {
    BRUSH,
    ERASER,
    LASSO,
    TEXT,
    EYEDROPPER
}

@Composable
fun CanvasEditorScreen(
    canvasWidth: Int,
    canvasHeight: Int,
    initialBitmap: Bitmap? = null,
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

    var refreshCanvasTrigger by remember { mutableIntStateOf(0) }

    fun refreshComposite() {
        layerManager.renderComposite(compositeBitmap)
        refreshCanvasTrigger++
    }

    var showColorPicker by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showBrushSettings by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showRulerDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showLassoMenu by remember { mutableStateOf(false) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ML Kit & Inpainting Action State
    var showMLInpaintDialog by remember { mutableStateOf(false) }
    var detectedTextRegions by remember { mutableStateOf<List<DetectedTextRegion>>(emptyList()) }
    var selectedMaskType by remember { mutableStateOf(MLMaskType.REFINED_TEXT) }

    // Rename Layer Dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameLayerTarget by remember { mutableStateOf<LayerItem?>(null) }
    var newLayerNameText by remember { mutableStateOf("") }

        val textStyleManager = remember { com.grooxtyper.app.model.TextStyleManager(context) }

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

    var previousTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var currentLassoPath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Main Interactive Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pointerCount = changes.size

                            if (pointerCount >= 2) {
                                // Multi-finger pan / zoom / rotate gesture
                                val centroid = changes.map { it.position }.reduce { acc, offset -> acc + offset } / pointerCount.toFloat()
                                var zoomChange = 1f
                                var panChange = Offset.Zero

                                val currentCentroid = centroid
                                val previousCentroid = changes.map { it.previousPosition }.reduce { acc, offset -> acc + offset } / pointerCount.toFloat()
                                panChange = currentCentroid - previousCentroid

                                val prevDist = hypot(changes[0].previousPosition.x - changes[1].previousPosition.x, changes[0].previousPosition.y - changes[1].previousPosition.y)
                                val currDist = hypot(changes[0].position.x - changes[1].position.x, changes[0].position.y - changes[1].position.y)
                                if (prevDist > 0f) {
                                    zoomChange = currDist / prevDist
                                }

                                viewState.scale = (viewState.scale * zoomChange).coerceIn(0.1f, 10.0f)
                                viewState.offsetX += panChange.x
                                viewState.offsetY += panChange.y
                                changes.forEach { it.consume() }
                                previousTouchPoint = null
                            } else if (pointerCount == 1) {
                                val change = changes[0]
                                if (change.pressed) {
                                    val canvasOffset = viewState.windowToCanvasCoordinates(change.position.x, change.position.y)

                                    if (previousTouchPoint == null && change.previousPressed.not()) {
                                        // Drag Start
                                        val activeLayer = layerManager.getActiveLayer()
                                        if (activeLayer != null) {
                                            undoRedoManager.saveSnapshot(activeLayer)
                                        }

                                        previousTouchPoint = canvasOffset

                                        if (activeTool == ActiveTool.LASSO) {
                                            val p = Path()
                                            p.moveTo(canvasOffset.x, canvasOffset.y)
                                            currentLassoPath = p
                                        } else if (activeTool == ActiveTool.EYEDROPPER) {
                                            val x = canvasOffset.x.toInt().coerceIn(0, canvasWidth - 1)
                                            val y = canvasOffset.y.toInt().coerceIn(0, canvasHeight - 1)
                                            val pickedColor = compositeBitmap.getPixel(x, y)
                                            brushEngine.color = pickedColor
                                            activeTool = ActiveTool.BRUSH
                                        } else if (activeTool == ActiveTool.TEXT) {
                                            showTextDialog = true
                                        }
                                        change.consume()
                                    } else if (previousTouchPoint != null && change.position != change.previousPosition) {
                                        // On Drag
                                        if (activeTool == ActiveTool.LASSO && currentLassoPath != null) {
                                            currentLassoPath?.lineTo(canvasOffset.x, canvasOffset.y)
                                        } else if (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) {
                                            val activeLayer = layerManager.getActiveLayer()
                                            if (activeLayer != null) {
                                                brushEngine.strokeSegmentOnLayer(activeLayer, previousTouchPoint!!, canvasOffset)
                                                refreshComposite()
                                            }
                                        }
                                        previousTouchPoint = canvasOffset
                                        change.consume()
                                    }
                                } else {
                                    // Touch released
                                    if (activeTool == ActiveTool.LASSO && currentLassoPath != null) {
                                        selectionEngine.setLassoPath(currentLassoPath!!)
                                        currentLassoPath = null
                                        refreshComposite()
                                    }
                                    previousTouchPoint = null
                                }
                            }
                        }
                    }
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
                .height(52.dp)
                .background(Color(0xFF1F1F1F))
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToGallery) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    undoRedoManager.undo(layerManager)
                    refreshComposite()
                },
                enabled = undoRedoManager.canUndo()
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoRedoManager.canUndo()) Color.White else Color.DarkGray)
            }

            IconButton(
                onClick = {
                    undoRedoManager.redo(layerManager)
                    refreshComposite()
                },
                enabled = undoRedoManager.canRedo()
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (undoRedoManager.canRedo()) Color.White else Color.DarkGray)
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { showRulerDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = "Ruler Guides", tint = if (brushEngine.rulerGuide.type != RulerType.OFF) Color(0xFFFF9800) else Color.White)
            }

            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = "Add Image", tint = Color.White)
            }

            IconButton(onClick = { showExportMenu = true }) {
                Icon(Icons.Default.Download, contentDescription = "Export Artwork", tint = Color.White)
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
                .height(56.dp)
                .background(Color(0xFF1A1A1A))
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2C2C2C))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeTool == ActiveTool.BRUSH) Color(0xFFFF9800) else Color.Transparent)
                        .clickable { activeTool = ActiveTool.BRUSH; showBrushSettings = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = "Brush", tint = if (activeTool == ActiveTool.BRUSH) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeTool == ActiveTool.ERASER) Color(0xFFFF9800) else Color.Transparent)
                        .clickable { activeTool = ActiveTool.ERASER }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eraser", tint = if (activeTool == ActiveTool.ERASER) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
            }

            IconButton(onClick = { activeTool = ActiveTool.EYEDROPPER }) {
                Icon(Icons.Default.Colorize, contentDescription = "Eyedropper", tint = if (activeTool == ActiveTool.EYEDROPPER) Color(0xFFFF9800) else Color.White)
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(brushEngine.color))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { showColorPicker = true }
            )

            IconButton(onClick = { activeTool = ActiveTool.LASSO; showLassoMenu = true }) {
                Icon(Icons.Default.SelectAll, contentDescription = "Lasso", tint = if (activeTool == ActiveTool.LASSO) Color(0xFFFF9800) else Color.White)
            }

            DropdownMenu(expanded = showLassoMenu, onDismissRequest = { showLassoMenu = false }) {
                DropdownMenuItem(text = { Text("Clear Area") }, onClick = {
                    layerManager.getActiveLayer()?.let { selectionEngine.clearSelectedArea(it); refreshComposite() }
                    showLassoMenu = false
                })
                DropdownMenuItem(text = { Text("Invert Selection") }, onClick = {
                    selectionEngine.invertSelection(); refreshComposite(); showLassoMenu = false
                })
                DropdownMenuItem(text = { Text("Copy Area") }, onClick = {
                    layerManager.getActiveLayer()?.let { selectionEngine.copySelectedArea(it) }
                    showLassoMenu = false
                })
                DropdownMenuItem(text = { Text("Cut Area") }, onClick = {
                    layerManager.getActiveLayer()?.let { selectionEngine.cutSelectedArea(it); refreshComposite() }
                    showLassoMenu = false
                })
                DropdownMenuItem(text = { Text("Paste Area") }, onClick = {
                    selectionEngine.pasteToNewLayer(layerManager); refreshComposite(); showLassoMenu = false
                })
            }

            IconButton(onClick = { activeTool = ActiveTool.TEXT; showTextDialog = true }) {
                Icon(Icons.Default.TextFields, contentDescription = "Text", tint = if (activeTool == ActiveTool.TEXT) Color(0xFFFF9800) else Color.White)
            }

            IconButton(
                onClick = {
                    scope.launch {
                        val active = layerManager.getActiveLayer()
                        if (active != null) {
                            detectedTextRegions = mlTextDetector.detectTextRegions(active.getBitmap())
                            showMLInpaintDialog = true
                        }
                    }
                }
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Inpaint ML", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2C))
                    .clickable { showLayersPanel = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${layerManager.layers.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Floating Reference Window
        ReferenceWindow(
            referenceBitmap = referenceBitmap,
            onClose = { referenceBitmap = null }
        )

        // Color Picker Dialog
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = brushEngine.color,
                onColorSelected = { brushEngine.color = it },
                onDismiss = { showColorPicker = false }
            )
        }

        // Ruler Guides Dialog
        if (showRulerDialog) {
            AlertDialog(
                onDismissRequest = { showRulerDialog = false },
                title = { Text("Ruler Guides", color = Color.White) },
                text = {
                    Column {
                        RulerType.values().forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        brushEngine.rulerGuide.type = r
                                        showRulerDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.name.replace("_", " "), color = if (brushEngine.rulerGuide.type == r) Color(0xFFFF9800) else Color.White, modifier = Modifier.weight(1f))
                                if (brushEngine.rulerGuide.type == r) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFF9800))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showRulerDialog = false }) { Text("Close", color = Color.Gray) } },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Brush & Force Fade Settings Dialog
        if (showBrushSettings) {
            AlertDialog(
                onDismissRequest = { showBrushSettings = false },
                title = { Text("Brush & Force Fade", color = Color.White) },
                text = {
                    Column {
                        Text("Brush Type", color = Color.LightGray, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BrushType.values().forEach { b ->
                                TextButton(onClick = { brushEngine.brushType = b }) {
                                    Text(
                                        b.displayName.take(8),
                                        color = if (brushEngine.brushType == b) Color(0xFFFF9800) else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Force Fade (Taper)", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = brushEngine.forceFade.isEnabled,
                                onCheckedChange = { brushEngine.forceFade.isEnabled = it }
                            )
                        }

                        if (brushEngine.forceFade.isEnabled) {
                            Text("Start Taper (${(brushEngine.forceFade.startFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = brushEngine.forceFade.startFade, onValueChange = { brushEngine.forceFade.startFade = it }, valueRange = 0.05f..0.8f)
                            Text("End Taper (${(brushEngine.forceFade.endFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = brushEngine.forceFade.endFade, onValueChange = { brushEngine.forceFade.endFade = it }, valueRange = 0.05f..0.8f)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showBrushSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                        Text("Done", color = Color.Black)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // ML Kit Text Detection & Telea Inpaint Modal
        if (showMLInpaintDialog) {
            AlertDialog(
                onDismissRequest = { showMLInpaintDialog = false },
                title = { Text("ML Kit Text Detection", color = Color.White) },
                text = {
                    Column {
                        Text("Detected ${detectedTextRegions.size} text blocks.", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Inpainting Mask Mode:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Button(
                                onClick = { selectedMaskType = MLMaskType.REFINED_TEXT },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.REFINED_TEXT) Color(0xFFFF9800) else Color(0xFF383838))
                            ) {
                                Text("Refined Contour", color = if (selectedMaskType == MLMaskType.REFINED_TEXT) Color.Black else Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { selectedMaskType = MLMaskType.BOUNDING_BOX },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.BOUNDING_BOX) Color(0xFFFF9800) else Color(0xFF383838))
                            ) {
                                Text("Bounding Box", color = if (selectedMaskType == MLMaskType.BOUNDING_BOX) Color.Black else Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val active = layerManager.getActiveLayer()
                                if (active != null && detectedTextRegions.isNotEmpty()) {
                                    val mask = mlTextDetector.generateMaskBitmap(
                                        canvasWidth,
                                        canvasHeight,
                                        detectedTextRegions,
                                        active.getBitmap(),
                                        selectedMaskType
                                    )
                                    withContext(Dispatchers.Default) {
                                        inpaintingManager.inpaintLayerArea(active, mask)
                                    }
                                    refreshComposite()
                                }
                                showMLInpaintDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Inpaint with Telea C++", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMLInpaintDialog = false }) { Text("Cancel", color = Color.Gray) }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Layer Manager Panel Modal
        if (showLayersPanel) {
            AlertDialog(
                onDismissRequest = { showLayersPanel = false },
                title = { Text("Layers", color = Color.White) },
                text = {
                    Column(modifier = Modifier.height(340.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { layerManager.addLayer(); refreshComposite() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Text("New Layer", color = Color.Black, fontSize = 12.sp)
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
                            items(layerManager.layers.size) { index ->
                                val layerItem = layerManager.layers[index]
                                val isSelected = layerItem.id == layerManager.activeLayerId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (layerItem is DrawingLayer) layerManager.activeLayerId = layerItem.id
                                        },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF383838) else Color(0xFF222222))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Reorder buttons (Up/Down)
                                            Column(modifier = Modifier.padding(end = 4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            layerManager.reorderLayers(index, index - 1)
                                                            refreshComposite()
                                                        }
                                                    },
                                                    modifier = Modifier.size(20.dp),
                                                    enabled = index > 0
                                                ) {
                                                    Text("▲", color = if (index > 0) Color(0xFFFF9800) else Color.DarkGray, fontSize = 10.sp)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index < layerManager.layers.size - 1) {
                                                            layerManager.reorderLayers(index, index + 1)
                                                            refreshComposite()
                                                        }
                                                    },
                                                    modifier = Modifier.size(20.dp),
                                                    enabled = index < layerManager.layers.size - 1
                                                ) {
                                                    Text("▼", color = if (index < layerManager.layers.size - 1) Color(0xFFFF9800) else Color.DarkGray, fontSize = 10.sp)
                                                }
                                            }

                                            Text(layerItem.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                                            IconButton(
                                                onClick = {
                                                    renameLayerTarget = layerItem
                                                    newLayerNameText = layerItem.name
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    layerItem.isVisible = !layerItem.isVisible
                                                    refreshComposite()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    if (layerItem.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Visibility",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        if (layerItem is DrawingLayer) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        layerItem.isAlphaLocked = !layerItem.isAlphaLocked
                                                        refreshComposite()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        if (layerItem.isAlphaLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                        contentDescription = "Alpha Lock",
                                                        tint = if (layerItem.isAlphaLocked) Color(0xFFFF9800) else Color.Gray,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(
                                                    onClick = {
                                                        layerItem.isClippingMask = !layerItem.isClippingMask
                                                        refreshComposite()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.FlipToBack,
                                                        contentDescription = "Clipping Mask",
                                                        tint = if (layerItem.isClippingMask) Color(0xFFFF9800) else Color.Gray,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.weight(1f))

                                                IconButton(
                                                    onClick = { layerItem.flipHorizontal(); refreshComposite() },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
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

        // Rename Layer Dialog
        if (showRenameDialog && renameLayerTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Layer", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newLayerNameText,
                        onValueChange = { newLayerNameText = it },
                        label = { Text("Layer Name") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            renameLayerTarget?.name = newLayerNameText
                            showRenameDialog = false
                            refreshComposite()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Save", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color.Gray) }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Overhauled Text Tool Modal with Stackable Effects & Style Manager
        if (showTextDialog) {
            var inputString by remember { mutableStateOf("GrooxTyper") }
            var textSizeVal by remember { mutableFloatStateOf(56f) }
            var textFillColor by remember { mutableIntStateOf(AndroidColor.WHITE) }
            var textOpacityVal by remember { mutableFloatStateOf(1.0f) }

            var enableOutline by remember { mutableStateOf(true) }
            var outlineColorVal by remember { mutableIntStateOf(AndroidColor.BLACK) }
            var outlineWidthVal by remember { mutableFloatStateOf(10f) }
            var outlineOpacityVal by remember { mutableFloatStateOf(1.0f) }

            var enableShadow by remember { mutableStateOf(true) }
            var shadowColorVal by remember { mutableIntStateOf(AndroidColor.parseColor("#80000000")) }
            var shadowDxVal by remember { mutableFloatStateOf(6f) }
            var shadowDyVal by remember { mutableFloatStateOf(6f) }
            var shadowBlurVal by remember { mutableFloatStateOf(12f) }
            var shadowOpacityVal by remember { mutableFloatStateOf(0.8f) }

            var enableGradient by remember { mutableStateOf(false) }
            var gradStartColorVal by remember { mutableIntStateOf(AndroidColor.RED) }
            var gradEndColorVal by remember { mutableIntStateOf(AndroidColor.YELLOW) }
            var gradAngleVal by remember { mutableFloatStateOf(0f) }

            var textBlurVal by remember { mutableFloatStateOf(0f) }

            var showSaveStyleDialog by remember { mutableStateOf(false) }
            var styleNameInput by remember { mutableStateOf("") }

            if (showSaveStyleDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveStyleDialog = false },
                    title = { Text("Save Text Style Preset", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = styleNameInput,
                            onValueChange = { styleNameInput = it },
                            label = { Text("Style Preset Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (styleNameInput.isNotBlank()) {
                                    val cfg = StackableTextConfig(
                                        text = "Preset",
                                        fontSize = 56f,
                                        textColor = textFillColor,
                                        opacity = textOpacityVal,
                                        hasOutline = enableOutline,
                                        outlineColor = outlineColorVal,
                                        outlineWidth = outlineWidthVal,
                                        outlineOpacity = outlineOpacityVal,
                                        hasShadow = enableShadow,
                                        shadowColor = shadowColorVal,
                                        shadowRadius = shadowBlurVal,
                                        shadowDx = shadowDxVal,
                                        shadowDy = shadowDyVal,
                                        shadowOpacity = shadowOpacityVal,
                                        hasGradient = enableGradient,
                                        gradientStartColor = gradStartColorVal,
                                        gradientEndColor = gradEndColorVal,
                                        gradientAngle = gradAngleVal,
                                        blurRadius = textBlurVal
                                    )
                                    textStyleManager.saveStyle(styleNameInput, cfg)
                                    styleNameInput = ""
                                }
                                showSaveStyleDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("Save", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveStyleDialog = false }) { Text("Cancel", color = Color.Gray) }
                    },
                    containerColor = Color(0xFF2A2A2A)
                )
            }


            // Color picker sub-dialog targets
            var colorPickerTarget by remember { mutableStateOf<String?>(null) } // "FILL", "OUTLINE", "SHADOW", "GRAD_START", "GRAD_END"

            if (colorPickerTarget != null) {
                val initCol = when (colorPickerTarget) {
                    "FILL" -> textFillColor
                    "OUTLINE" -> outlineColorVal
                    "SHADOW" -> shadowColorVal
                    "GRAD_START" -> gradStartColorVal
                    "GRAD_END" -> gradEndColorVal
                    else -> AndroidColor.BLACK
                }
                ColorPickerDialog(
                    initialColor = initCol,
                    onColorSelected = { col ->
                        when (colorPickerTarget) {
                            "FILL" -> textFillColor = col
                            "OUTLINE" -> outlineColorVal = col
                            "SHADOW" -> shadowColorVal = col
                            "GRAD_START" -> gradStartColorVal = col
                            "GRAD_END" -> gradEndColorVal = col
                        }
                    },
                    onDismiss = { colorPickerTarget = null }
                )
            }

            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = null, tint = Color(0xFFFF9800))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Text Effect Studio & Layers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .height(420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = inputString,
                            onValueChange = { inputString = it },
                            label = { Text("Teks Konten") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        // Presets & Style Manager Row
                        Text("Style Presets & Style Manager", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            textStyleManager.savedStyles.forEach { styleItem ->
                                Button(
                                    onClick = {
                                        val c = styleItem.textConfig
                                        textSizeVal = c.fontSize
                                        textFillColor = c.textColor
                                        textOpacityVal = c.opacity
                                        enableOutline = c.hasOutline
                                        outlineColorVal = c.outlineColor
                                        outlineWidthVal = c.outlineWidth
                                        outlineOpacityVal = c.outlineOpacity
                                        enableShadow = c.hasShadow
                                        shadowColorVal = c.shadowColor
                                        shadowBlurVal = c.shadowRadius
                                        shadowDxVal = c.shadowDx
                                        shadowDyVal = c.shadowDy
                                        shadowOpacityVal = c.shadowOpacity
                                        enableGradient = c.hasGradient
                                        gradStartColorVal = c.gradientStartColor
                                        gradEndColorVal = c.gradientEndColor
                                        gradAngleVal = c.gradientAngle
                                        textBlurVal = c.blurRadius
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838))
                                ) {
                                    Text(styleItem.name, color = Color.White, fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = { showSaveStyleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text("+ Save Style", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Text Fill & Opacity
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Warna Teks", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(textFillColor))
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable { colorPickerTarget = "FILL" }
                            )
                        }
                        Text("Text Size (${textSizeVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                        Slider(value = textSizeVal, onValueChange = { textSizeVal = it }, valueRange = 16f..160f)
                        Text("Text Opacity (${(textOpacityVal * 100).toInt()}%)", color = Color.LightGray, fontSize = 11.sp)
                        Slider(value = textOpacityVal, onValueChange = { textOpacityVal = it }, valueRange = 0f..1f)

                        Spacer(modifier = Modifier.height(12.dp))
                        // Outline Stack
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Outline / Garis Tepi", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableOutline, onCheckedChange = { enableOutline = it })
                        }
                        if (enableOutline) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Warna Outline", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(outlineColorVal))
                                        .border(1.dp, Color.White, CircleShape)
                                        .clickable { colorPickerTarget = "OUTLINE" }
                                )
                            }
                            Text("Ketebalan (${outlineWidthVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = outlineWidthVal, onValueChange = { outlineWidthVal = it }, valueRange = 1f..40f)
                            Text("Opacity Outline (${(outlineOpacityVal * 100).toInt()}%)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = outlineOpacityVal, onValueChange = { outlineOpacityVal = it }, valueRange = 0f..1f)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Shadow Stack
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Drop Shadow / Bayangan", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableShadow, onCheckedChange = { enableShadow = it })
                        }
                        if (enableShadow) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Warna Bayangan", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(shadowColorVal))
                                        .border(1.dp, Color.White, CircleShape)
                                        .clickable { colorPickerTarget = "SHADOW" }
                                )
                            }
                            Text("Offset X (${shadowDxVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowDxVal, onValueChange = { shadowDxVal = it }, valueRange = -30f..30f)
                            Text("Offset Y (${shadowDyVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowDyVal, onValueChange = { shadowDyVal = it }, valueRange = -30f..30f)
                            Text("Blur Radius (${shadowBlurVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowBlurVal, onValueChange = { shadowBlurVal = it }, valueRange = 1f..40f)
                            Text("Opacity Shadow (${(shadowOpacityVal * 100).toInt()}%)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowOpacityVal, onValueChange = { shadowOpacityVal = it }, valueRange = 0f..1f)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Gradient Stack
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gradasi Warna (Gradient)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableGradient, onCheckedChange = { enableGradient = it })
                        }
                        if (enableGradient) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Warna Awal & Akhir", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(gradStartColorVal))
                                        .border(1.dp, Color.White, CircleShape)
                                        .clickable { colorPickerTarget = "GRAD_START" }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(gradEndColorVal))
                                        .border(1.dp, Color.White, CircleShape)
                                        .clickable { colorPickerTarget = "GRAD_END" }
                                )
                            }
                            Text("Arah Gradient (${gradAngleVal.toInt()}°)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = gradAngleVal, onValueChange = { gradAngleVal = it }, valueRange = 0f..360f)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Text Blur Filter (${textBlurVal.toInt()}px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(value = textBlurVal, onValueChange = { textBlurVal = it }, valueRange = 0f..30f)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val active = layerManager.getActiveLayer()
                            if (active != null) {
                                val cfg = StackableTextConfig(
                                    text = inputString,
                                    fontSize = textSizeVal,
                                    textColor = textFillColor,
                                    opacity = textOpacityVal,
                                    hasOutline = enableOutline,
                                    outlineColor = outlineColorVal,
                                    outlineWidth = outlineWidthVal,
                                    outlineOpacity = outlineOpacityVal,
                                    hasShadow = enableShadow,
                                    shadowColor = shadowColorVal,
                                    shadowRadius = shadowBlurVal,
                                    shadowDx = shadowDxVal,
                                    shadowDy = shadowDyVal,
                                    shadowOpacity = shadowOpacityVal,
                                    hasGradient = enableGradient,
                                    gradientStartColor = gradStartColorVal,
                                    gradientEndColor = gradEndColorVal,
                                    gradientAngle = gradAngleVal,
                                    blurRadius = textBlurVal
                                )
                                textEngine.drawTextOnLayer(active, cfg, Offset(100f, 250f))
                                refreshComposite()
                            }
                            showTextDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Render Text to Layer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) { Text("Cancel", color = Color.Gray) }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }
    }
}
