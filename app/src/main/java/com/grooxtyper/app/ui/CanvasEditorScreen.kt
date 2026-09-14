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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.ml.DetectedTextRegion
import com.grooxtyper.app.ml.MLMaskType
import com.grooxtyper.app.ml.MLTextDetector
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.ExportFormat
import com.grooxtyper.app.model.FileExportManager
import com.grooxtyper.app.model.InpaintingManager
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.ProjectManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.StackableTextConfig
import com.grooxtyper.app.model.TextEngine
import com.grooxtyper.app.model.TextItem
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    projectId: String,
    canvasWidth: Int,
    canvasHeight: Int,
    initialBitmap: Bitmap? = null,
    onBackToGallery: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val projectManager = remember { ProjectManager(context) }
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

    // Auto-Save background worker
    LaunchedEffect(projectId) {
        while (true) {
            delay(15000L) // Auto-save every 15 seconds
            projectManager.saveProject(projectId, "GrooxTyper Artwork", canvasWidth, canvasHeight, compositeBitmap)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            projectManager.saveProject(projectId, "GrooxTyper Artwork", canvasWidth, canvasHeight, compositeBitmap)
        }
    }

    // Populate initial bitmap if imported from gallery
    LaunchedEffect(initialBitmap) {
        initialBitmap?.let { bmp ->
            layerManager.getActiveLayer()?.let { active ->
                active.tileMap.importFromBitmap(bmp)
                active.markDirty()
                refreshComposite()
            }
        }
    }

    // Interactive Text Nodes
    val textItems = remember { mutableStateListOf<TextItem>() }
    var selectedTextItem by remember { mutableStateOf<TextItem?>(null) }
    var editingTextConfig by remember { mutableStateOf<StackableTextConfig?>(null) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showBrushSettings by remember { mutableStateOf(false) }
    var showTextPanel by remember { mutableStateOf(false) }
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
    var activeStrokePath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Main Interactive Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, selectedTextItem) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount >= 2) {
                                // Two-finger touches: Canvas transform (Pan, Zoom, Rotate) strictly
                                previousTouchPoint = null
                                activeStrokePath = null
                                val change1 = event.changes[0]
                                val change2 = event.changes[1]

                                if (change1.pressed && change2.pressed) {
                                    val prevP1 = change1.previousPosition
                                    val prevP2 = change2.previousPosition
                                    val curP1 = change1.position
                                    val curP2 = change2.position

                                    val prevCenter = (prevP1 + prevP2) / 2f
                                    val curCenter = (curP1 + curP2) / 2f
                                    val pan = curCenter - prevCenter

                                    val prevDist = (prevP1 - prevP2).getDistance()
                                    val curDist = (curP1 - curP2).getDistance()
                                    val zoom = if (prevDist > 0f) curDist / prevDist else 1f

                                    val prevAngle = Math.toDegrees(kotlin.math.atan2((prevP2.y - prevP1.y).toDouble(), (prevP2.x - prevP1.x).toDouble())).toFloat()
                                    val curAngle = Math.toDegrees(kotlin.math.atan2((curP2.y - curP1.y).toDouble(), (curP2.x - curP1.x).toDouble())).toFloat()
                                    val rotationDelta = curAngle - prevAngle

                                    viewState.scale = (viewState.scale * zoom).coerceIn(0.1f, 10.0f)
                                    viewState.offsetX += pan.x
                                    viewState.offsetY += pan.y
                                    viewState.applyRotationDelta(rotationDelta)

                                    change1.consume()
                                    change2.consume()
                                }
                            } else if (pointerCount == 1) {
                                // One-finger touch: Brush / Eraser / Text interaction
                                val change = event.changes[0]
                                if (change.pressed) {
                                    val canvasOffset = viewState.windowToCanvasCoordinates(change.position.x, change.position.y)

                                    if (activeTool == ActiveTool.TEXT) {
                                        if (previousTouchPoint == null) {
                                            val hit = textItems.findLast { it.isHit(canvasOffset) }
                                            if (hit != null) {
                                                selectedTextItem = hit
                                                editingTextConfig = hit.config
                                            } else {
                                                val newConfig = StackableTextConfig(textColor = brushEngine.color)
                                                val newItem = TextItem(config = newConfig, position = canvasOffset)
                                                textItems.add(newItem)
                                                selectedTextItem = newItem
                                                editingTextConfig = newConfig
                                                showTextPanel = true
                                            }
                                        } else {
                                            // Move selected text item
                                            selectedTextItem?.let { item ->
                                                val delta = canvasOffset - previousTouchPoint!!
                                                item.position = item.position + delta
                                            }
                                        }
                                    } else if (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) {
                                        if (previousTouchPoint == null) {
                                            val activeLayer = layerManager.getActiveLayer()
                                            if (activeLayer != null) undoRedoManager.saveSnapshot(activeLayer)
                                            activeStrokePath = Path().apply { moveTo(canvasOffset.x, canvasOffset.y) }
                                        } else {
                                            val activeLayer = layerManager.getActiveLayer()
                                            if (activeLayer != null) {
                                                if (activeTool == ActiveTool.ERASER) {
                                                    brushEngine.brushType = BrushType.ERASER
                                                }
                                                brushEngine.strokeSegmentOnLayer(activeLayer, previousTouchPoint!!, canvasOffset)
                                                activeStrokePath?.lineTo(canvasOffset.x, canvasOffset.y)
                                                refreshComposite()
                                            }
                                        }
                                    }
                                    previousTouchPoint = canvasOffset
                                    change.consume()
                                } else {
                                    previousTouchPoint = null
                                    activeStrokePath = null
                                }
                            } else {
                                previousTouchPoint = null
                                activeStrokePath = null
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
                // Render Composite Layers Bitmap
                drawContext.canvas.nativeCanvas.drawBitmap(compositeBitmap, 0f, 0f, null)

                // Render Interactive Text Objects
                textItems.forEach { item ->
                    textEngine.renderTextToCanvas(
                        drawContext.canvas.nativeCanvas,
                        item.config,
                        item.position,
                        item.scale
                    )
                    // Draw bounding box if selected
                    if (item == selectedTextItem) {
                        val bounds = item.getBounds()
                        val boxPaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3f
                            color = android.graphics.Color.CYAN
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                        }
                        drawContext.canvas.nativeCanvas.drawRect(bounds, boxPaint)
                    }
                }

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
            IconButton(onClick = {
                projectManager.saveProject(projectId, "GrooxTyper Artwork", canvasWidth, canvasHeight, compositeBitmap)
                onBackToGallery()
            }) {
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
                        .clickable {
                            activeTool = ActiveTool.BRUSH
                            brushEngine.brushType = BrushType.FELT_TIP_PEN
                            showBrushSettings = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = "Brush", tint = if (activeTool == ActiveTool.BRUSH) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeTool == ActiveTool.ERASER) Color(0xFFFF9800) else Color.Transparent)
                        .clickable {
                            activeTool = ActiveTool.ERASER
                            brushEngine.brushType = BrushType.ERASER
                        }
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

            IconButton(onClick = { activeTool = ActiveTool.TEXT; showTextPanel = true }) {
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

        // Brush Drawer Panel
        if (showBrushSettings) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                BrushDrawerPanel(
                    brushEngine = brushEngine,
                    onClose = { showBrushSettings = false }
                )
            }
        }

        // Redesigned Text Panel Drawer
        if (showTextPanel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                TextPanel(
                    initialConfig = editingTextConfig ?: StackableTextConfig(textColor = brushEngine.color),
                    onConfirm = { updatedConfig ->
                        val item = selectedTextItem
                        if (item != null) {
                            item.config = updatedConfig
                        } else {
                            val newItem = TextItem(config = updatedConfig, position = Offset(canvasWidth / 4f, canvasHeight / 3f))
                            textItems.add(newItem)
                        }
                        showTextPanel = false
                    },
                    onClose = { showTextPanel = false }
                )
            }
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
                            items(layerManager.layers) { layerItem ->
                                val isSelected = layerItem.id == layerManager.activeLayerId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (layerItem is com.grooxtyper.app.model.DrawingLayer) layerManager.activeLayerId = layerItem.id
                                        },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF383838) else Color(0xFF222222))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                                        if (layerItem is com.grooxtyper.app.model.DrawingLayer) {
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
    }
}
