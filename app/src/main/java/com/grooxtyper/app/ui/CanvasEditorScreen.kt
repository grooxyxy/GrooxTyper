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
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.ml.DetectedTextRegion
import com.grooxtyper.app.ml.MLMaskType
import com.grooxtyper.app.ml.MLTextDetector
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.DrawingLayer
import com.grooxtyper.app.model.ExportFormat
import com.grooxtyper.app.model.FileExportManager
import com.grooxtyper.app.model.InpaintingManager
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.ProjectManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.TextBox
import com.grooxtyper.app.model.FontRegistry
import com.grooxtyper.app.model.TextHandle
import com.grooxtyper.app.model.TextLayer
import com.grooxtyper.app.model.TextRenderer
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveTool {
    PAN,
    BRUSH,
    ERASER,
    LASSO,
    TEXT,
    EYEDROPPER
}

private val Accent = Color(0xFFFF5722)
private val BgDark = Color(0xFF000000)
private val TopBarBg = Color(0xFF1C1C1E)
private val BottomBarBg = Color(0xFF1C1C1E)
private val PanelBg = Color(0xFF2C2C2E)

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
    val fontRegistry = remember { FontRegistry(context) }
    val inpaintingManager = remember { InpaintingManager() }
    val mlTextDetector = remember { MLTextDetector() }
    val undoRedoManager = remember { UndoRedoManager() }
    val exportManager = remember { FileExportManager(context) }
    val viewState = remember { CanvasViewState(canvasWidth, canvasHeight) }

    var activeTool by remember { mutableStateOf(ActiveTool.BRUSH) }

    var compositeBitmap by remember {
        mutableStateOf(Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888))
    }

    var refreshCanvasState by remember { mutableIntStateOf(0) }
    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }

    fun refreshComposite() {
        layerManager.renderComposite(compositeBitmap)
        refreshCanvasState++
    }

    LaunchedEffect(projectId) {
        while (true) {
            delay(15000L)
            projectManager.saveProject(projectId, "GrooxTyper Artwork", canvasWidth, canvasHeight, compositeBitmap)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            projectManager.saveProject(projectId, "GrooxTyper Artwork", canvasWidth, canvasHeight, compositeBitmap)
        }
    }

    LaunchedEffect(initialBitmap) {
        initialBitmap?.let { bmp ->
            layerManager.getActiveLayer()?.let { active ->
                active.tileMap.importFromBitmap(bmp)
                active.markDirty()
                refreshComposite()
            }
        }
    }

    var selectedTextBox by remember { mutableStateOf<TextBox?>(null) }
    var textHandleMode by remember { mutableStateOf(TextHandle.NONE) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showBrushSettings by remember { mutableStateOf(false) }
    var showTextEditor by remember { mutableStateOf(false) }
    var showRulerDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showLassoMenu by remember { mutableStateOf(false) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showQuickSlider by remember { mutableStateOf(true) }

    var showMLInpaintDialog by remember { mutableStateOf(false) }
    var detectedTextRegions by remember { mutableStateOf<List<DetectedTextRegion>>(emptyList()) }
    var selectedMaskType by remember { mutableStateOf(MLMaskType.REFINED_TEXT) }
    var fontList by remember { mutableStateOf(fontRegistry.fonts()) }

    fun flattenSelectedText() {
        val box = selectedTextBox ?: return
        val textLayer = layerManager.layers.filterIsInstance<TextLayer>().find { it.box.id == box.id }
            ?: return
        val target = layerManager.ensureDrawingLayer()
        undoRedoManager.saveSnapshot(target)
        TextRenderer.flatten(target, box)
        layerManager.deleteLayer(textLayer.id)
        layerManager.activeLayerId = target.id
        selectedTextBox = null
        showTextEditor = false
        refreshComposite()
    }

    fun deleteSelectedText() {
        val box = selectedTextBox ?: return
        layerManager.layers.filterIsInstance<TextLayer>().find { it.box.id == box.id }
            ?.let { layerManager.deleteLayer(it.id) }
        selectedTextBox = null
        showTextEditor = false
        refreshComposite()
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val name = "font_${System.currentTimeMillis()}.ttf"
                    val tf = fontRegistry.import(stream, name)
                    if (tf != null) {
                        fontList = fontRegistry.fonts()
                        selectedTextBox?.let { box ->
                            box.typeface = tf
                            box.fontName = name.removeSuffix(".ttf")
                            refreshComposite()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

    fun screenToCanvasCoordinates(screenX: Float, screenY: Float): Offset {
        // Inverse dari Modifier.graphicsLayer(scale, translation, rotationZ)
        // yang pivot-nya di tengah viewport (TransformOrigin.Center).
        // Bitmap digambar di (0,0) Canvas, jadi koordinat layout == koordinat bitmap.
        val pivotX = viewportSize.width / 2f
        val pivotY = viewportSize.height / 2f

        val dx = (screenX - pivotX - viewState.offsetX) / viewState.scale
        val dy = (screenY - pivotY - viewState.offsetY) / viewState.scale

        val rad = Math.toRadians((-viewState.rotation).toDouble())
        val rx = dx * Math.cos(rad) - dy * Math.sin(rad)
        val ry = dx * Math.sin(rad) + dy * Math.cos(rad)

        return Offset((rx + pivotX).toFloat(), (ry + pivotY).toFloat())
    }

    var lastCanvasPoint by remember { mutableStateOf<Offset?>(null) }
    var lastScreenPoint by remember { mutableStateOf<Offset?>(null) }
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }
    var strokeProgress by remember { mutableStateOf(0f) }
    var strokeLength by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .onSizeChanged { viewportSize = it }
    ) {
        // Canvas with gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, selectedTextBox, viewState.scale, viewState.offsetX, viewState.offsetY, viewState.rotation, viewportSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pointerCount = changes.size

                            if (pointerCount >= 2 || activeTool == ActiveTool.PAN) {
                                lastCanvasPoint = null
                                cursorPosition = null
                                strokeLength = 0f
                                var zoom = 1f
                                var pan = Offset.Zero
                                var rotation = 0f

                                if (pointerCount >= 2) {
                                    val p1 = changes[0]
                                    val p2 = changes[1]

                                    if (p1.pressed && p2.pressed) {
                                        val prevP1 = p1.previousPosition
                                        val prevP2 = p2.previousPosition
                                        val curP1 = p1.position
                                        val curP2 = p2.position

                                        val prevCenter = (prevP1 + prevP2) / 2f
                                        val curCenter = (curP1 + curP2) / 2f
                                        pan = curCenter - prevCenter

                                        val prevDist = (prevP1 - prevP2).getDistance()
                                        val curDist = (curP1 - curP2).getDistance()
                                        if (prevDist > 0f) zoom = curDist / prevDist

                                        val prevAngle = Math.toDegrees(kotlin.math.atan2((prevP2.y - prevP1.y).toDouble(), (prevP2.x - prevP1.x).toDouble())).toFloat()
                                        val curAngle = Math.toDegrees(kotlin.math.atan2((curP2.y - curP1.y).toDouble(), (curP2.x - curP1.x).toDouble())).toFloat()
                                        rotation = curAngle - prevAngle

                                        viewState.scale = (viewState.scale * zoom).coerceIn(0.1f, 10.0f)
                                        viewState.offsetX += pan.x
                                        viewState.offsetY += pan.y
                                        viewState.applyRotationDelta(rotation)

                                        p1.consume()
                                        p2.consume()
                                    }
                                } else if (pointerCount == 1 && activeTool == ActiveTool.PAN) {
                                    val change = changes[0]
                                    if (change.pressed) {
                                        if (lastScreenPoint != null) {
                                            val dragDelta = change.position - lastScreenPoint!!
                                            viewState.offsetX += dragDelta.x
                                            viewState.offsetY += dragDelta.y
                                        }
                                        lastScreenPoint = change.position
                                        change.consume()
                                    } else {
                                        lastScreenPoint = null
                                    }
                                }
                            } else if (pointerCount == 1) {
                                val change = changes[0]
                                if (change.pressed) {
                                    cursorPosition = change.position
                                    val touchCanvasPos = screenToCanvasCoordinates(change.position.x, change.position.y)

                                    if (activeTool == ActiveTool.TEXT) {
                                        val grip = 28f / viewState.scale
                                        if (lastCanvasPoint == null) {
                                            // Tekan baru: handle dulu, lalu badan box, lalu kanvas kosong.
                                            val current = selectedTextBox
                                            val handle = current?.hitHandle(touchCanvasPos, grip)
                                                ?: TextHandle.NONE
                                            if (handle != TextHandle.NONE) {
                                                textHandleMode = handle
                                            } else {
                                                val hit = layerManager.layers
                                                    .filterIsInstance<TextLayer>()
                                                    .findLast { it.box.hitTest(touchCanvasPos) }
                                                if (hit != null) {
                                                    selectedTextBox = hit.box
                                                    layerManager.activeLayerId = hit.id
                                                    textHandleMode = TextHandle.BODY
                                                    refreshComposite()
                                                } else {
                                                    val box = TextBox(
                                                        text = "Teks baru",
                                                        position = touchCanvasPos,
                                                        color = brushEngine.color
                                                    )
                                                    layerManager.addTextLayer(box)
                                                    selectedTextBox = box
                                                    textHandleMode = TextHandle.BODY
                                                    showTextEditor = true
                                                    refreshComposite()
                                                }
                                            }
                                        } else {
                                            selectedTextBox?.let { box ->
                                                when (textHandleMode) {
                                                    TextHandle.BODY -> {
                                                        val delta = touchCanvasPos - lastCanvasPoint!!
                                                        box.position = box.position + delta
                                                    }
                                                    TextHandle.SCALE -> {
                                                        val oldDist = (lastCanvasPoint!! - box.position).getDistance()
                                                        val newDist = (touchCanvasPos - box.position).getDistance()
                                                        if (oldDist > 1f && newDist > 1f) {
                                                            box.scale = (box.scale * newDist / oldDist).coerceIn(0.2f, 8f)
                                                        }
                                                    }
                                                    TextHandle.ROTATE -> {
                                                        val aOld = kotlin.math.atan2(
                                                            (lastCanvasPoint!!.y - box.position.y).toDouble(),
                                                            (lastCanvasPoint!!.x - box.position.x).toDouble()
                                                        )
                                                        val aNew = kotlin.math.atan2(
                                                            (touchCanvasPos.y - box.position.y).toDouble(),
                                                            (touchCanvasPos.x - box.position.x).toDouble()
                                                        )
                                                        var d = Math.toDegrees(aNew - aOld).toFloat()
                                                        box.rotation = ((box.rotation + d) % 360f + 360f) % 360f
                                                    }
                                                    TextHandle.NONE -> Unit
                                                }
                                                refreshComposite()
                                            }
                                        }
                                    } else if (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) {
                                        val activeLayer = layerManager.ensureDrawingLayer()
                                        if (lastCanvasPoint == null) {
                                            undoRedoManager.saveSnapshot(activeLayer)
                                            brushEngine.beginStroke()
                                            brushEngine.strokeSegmentOnLayer(activeLayer, touchCanvasPos, touchCanvasPos, 0f)
                                        } else {
                                            if (activeTool == ActiveTool.ERASER) brushEngine.brushType = BrushType.ERASER
                                            val dist = (touchCanvasPos - lastCanvasPoint!!).getDistance()
                                            strokeLength += dist
                                            val progress = if (strokeLength > 0f) (strokeLength / 500f).coerceIn(0f, 1f) else 0f
                                            brushEngine.strokeSegmentOnLayer(activeLayer, lastCanvasPoint!!, touchCanvasPos, progress)
                                        }
                                        refreshComposite()
                                    }
                                    lastCanvasPoint = touchCanvasPos
                                    change.consume()
                                } else {
                                    brushEngine.endStroke()
                                    textHandleMode = TextHandle.NONE
                                    lastCanvasPoint = null
                                    cursorPosition = null
                                    strokeLength = 0f
                                }
                            } else {
                                brushEngine.endStroke()
                                lastCanvasPoint = null
                                lastScreenPoint = null
                                cursorPosition = null
                                strokeLength = 0f
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
                val trigger = refreshCanvasState
                drawContext.canvas.nativeCanvas.drawBitmap(compositeBitmap, 0f, 0f, null)

                layerManager.layers.filterIsInstance<TextLayer>().forEach { textLayer ->
                    val box = textLayer.box
                    if (box.id == selectedTextBox?.id) {
                        val native = drawContext.canvas.nativeCanvas
                        val bounds = box.getBounds()
                        val rad = Math.toRadians(box.rotation.toDouble())
                        val cosR = Math.cos(rad).toFloat()
                        val sinR = Math.sin(rad).toFloat()
                        fun rot(p: Offset): Offset {
                            val dx = p.x - box.position.x
                            val dy = p.y - box.position.y
                            return Offset(
                                (box.position.x + dx * cosR - dy * sinR).toFloat(),
                                (box.position.y + dx * sinR + dy * cosR).toFloat()
                            )
                        }
                        val corners = listOf(
                            rot(Offset(bounds.left, bounds.top)),
                            rot(Offset(bounds.right, bounds.top)),
                            rot(Offset(bounds.right, bounds.bottom)),
                            rot(Offset(bounds.left, bounds.bottom))
                        )
                        val frame = android.graphics.Path().apply {
                            moveTo(corners[0].x, corners[0].y)
                            lineTo(corners[1].x, corners[1].y)
                            lineTo(corners[2].x, corners[2].y)
                            lineTo(corners[3].x, corners[3].y)
                            close()
                        }
                        val framePaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3f / viewState.scale
                            color = android.graphics.Color.CYAN
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                        }
                        native.drawPath(frame, framePaint)

                        val handlePaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.FILL
                            color = android.graphics.Color.CYAN
                        }
                        val ringPaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2f / viewState.scale
                            color = android.graphics.Color.WHITE
                        }
                        val r = 14f / viewState.scale
                        for (h in listOf(box.scaleHandlePosition(), box.rotateHandlePosition())) {
                            native.drawCircle(h.x, h.y, r, handlePaint)
                            native.drawCircle(h.x, h.y, r, ringPaint)
                        }
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

            // Brush cursor overlay
            if (cursorPosition != null && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cursorRadius = (brushEngine.size * viewState.scale) / 2f
                    drawCircle(
                        color = if (activeTool == ActiveTool.ERASER) Color.Red else Color.White,
                        radius = cursorRadius.coerceAtLeast(6f),
                        center = cursorPosition!!,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                }
            }
        }

        // Quick sliders (left side, ibisPaint style)
        if (showQuickSlider && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER)) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xBB1C1C1E))
                    .border(1.dp, Color(0xFF38383A), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Size", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.height(110.dp).width(30.dp)) {
                    Slider(
                        value = brushEngine.size,
                        onValueChange = { brushEngine.size = it },
                        valueRange = 1f..120f,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = 270f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                            }
                            .width(110.dp),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Alpha", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.height(110.dp).width(30.dp)) {
                    Slider(
                        value = brushEngine.opacity,
                        onValueChange = { brushEngine.opacity = it },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = 270f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                            }
                            .width(110.dp),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                }
            }
        }

        // Top bar (ibisPaint style - minimal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(TopBarBg)
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
                onClick = { undoRedoManager.undo(layerManager); refreshComposite() },
                enabled = undoRedoManager.canUndo()
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoRedoManager.canUndo()) Color.White else Color.DarkGray)
            }

            IconButton(
                onClick = { undoRedoManager.redo(layerManager); refreshComposite() },
                enabled = undoRedoManager.canRedo()
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (undoRedoManager.canRedo()) Color.White else Color.DarkGray)
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { showRulerDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = "Ruler", tint = if (brushEngine.rulerGuide.type != RulerType.OFF) Accent else Color.White)
            }

            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = "Import Image", tint = Color.White)
            }

            IconButton(onClick = { showExportMenu = true }) {
                Icon(Icons.Default.Download, contentDescription = "Export", tint = Color.White)
            }

            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
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

        // Floating text toolbar
        selectedTextBox?.let { box ->
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    box.text.take(18).ifEmpty { "Teks" },
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = { showTextEditor = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit teks", tint = Accent)
                }
                IconButton(onClick = { flattenSelectedText() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Layers, contentDescription = "Flatten ke layer", tint = Color.White)
                }
                IconButton(onClick = {
                    box.rotation = (box.rotation + 15f) % 360f
                    refreshComposite()
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Putar 15°", tint = Color.White)
                }
                IconButton(onClick = { deleteSelectedText() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus teks", tint = Color.Red)
                }
            }
        }

        // Bottom toolbar (ibisPaint style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(BottomBarBg)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pan tool
            IconButton(onClick = { activeTool = ActiveTool.PAN }) {
                Icon(Icons.Default.PanTool, contentDescription = "Pan", tint = if (activeTool == ActiveTool.PAN) Accent else Color.White)
            }

            // Brush / Eraser toggle pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelBg)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeTool == ActiveTool.BRUSH) Accent else Color.Transparent)
                        .clickable {
                            activeTool = ActiveTool.BRUSH
                            brushEngine.brushType = BrushType.PEN_HARD
                            layerManager.ensureDrawingLayer()
                            showBrushSettings = true
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = "Brush", tint = if (activeTool == ActiveTool.BRUSH) Color.White else Color.White, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeTool == ActiveTool.ERASER) Accent else Color.Transparent)
                        .clickable {
                            activeTool = ActiveTool.ERASER
                            brushEngine.brushType = BrushType.ERASER
                            layerManager.ensureDrawingLayer()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eraser", tint = if (activeTool == ActiveTool.ERASER) Color.White else Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Eyedropper
            IconButton(onClick = { activeTool = ActiveTool.EYEDROPPER }) {
                Icon(Icons.Default.Colorize, contentDescription = "Eyedropper", tint = if (activeTool == ActiveTool.EYEDROPPER) Accent else Color.White)
            }

            // Color circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(brushEngine.color))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { showColorPicker = true }
            )

            // Lasso
            IconButton(onClick = { activeTool = ActiveTool.LASSO; showLassoMenu = true }) {
                Icon(Icons.Default.SelectAll, contentDescription = "Lasso", tint = if (activeTool == ActiveTool.LASSO) Accent else Color.White)
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

            // Text
            IconButton(onClick = {
                activeTool = ActiveTool.TEXT
                if (selectedTextBox != null) showTextEditor = true
            }) {
                Icon(Icons.Default.TextFields, contentDescription = "Text", tint = if (activeTool == ActiveTool.TEXT) Accent else Color.White)
            }

            // ML Inpaint
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
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Inpaint", tint = Color.White)
            }

            // Layers button with count
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelBg)
                    .clickable { showLayersPanel = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = "Layers", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${layerManager.layers.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Reference window
        ReferenceWindow(
            referenceBitmap = referenceBitmap,
            onClose = { referenceBitmap = null }
        )

        // Dialogs
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = brushEngine.color,
                onColorSelected = { brushEngine.color = it },
                onDismiss = { showColorPicker = false }
            )
        }

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
                                Text(r.name.replace("_", " "), color = if (brushEngine.rulerGuide.type == r) Accent else Color.White, modifier = Modifier.weight(1f))
                                if (brushEngine.rulerGuide.type == r) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Accent)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showRulerDialog = false }) { Text("Close", color = Color.Gray) } },
                containerColor = PanelBg
            )
        }

        if (showBrushSettings) {
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                BrushPanel(brushEngine = brushEngine, onClose = { showBrushSettings = false })
            }
        }

        if (showTextEditor) {
            selectedTextBox?.let { box ->
                // Sinkronkan nama layer agar panel Layers tetap informatif.
                val key = box.id to box.text
                androidx.compose.runtime.LaunchedEffect(key) {
                    layerManager.layers.filterIsInstance<TextLayer>()
                        .find { it.box.id == box.id }
                        ?.let { it.name = "Text: ${box.text.take(16)}" }
                }
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    TextEditorPanel(
                        box = box,
                        fonts = fontList,
                        onImportFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
                        onChange = { refreshComposite() },
                        onFlatten = { flattenSelectedText() },
                        onDelete = { deleteSelectedText() },
                        onClose = { showTextEditor = false }
                    )
                }
            } ?: run { showTextEditor = false }
        }

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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Button(
                                onClick = { selectedMaskType = MLMaskType.REFINED_TEXT },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.REFINED_TEXT) Accent else PanelBg)
                            ) {
                                Text("Refined Contour", color = if (selectedMaskType == MLMaskType.REFINED_TEXT) Color.White else Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { selectedMaskType = MLMaskType.BOUNDING_BOX },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.BOUNDING_BOX) Accent else PanelBg)
                            ) {
                                Text("Bounding Box", color = if (selectedMaskType == MLMaskType.BOUNDING_BOX) Color.White else Color.White, fontSize = 11.sp)
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
                                    val mask = mlTextDetector.generateMaskBitmap(canvasWidth, canvasHeight, detectedTextRegions, active.getBitmap(), selectedMaskType)
                                    withContext(Dispatchers.Default) { inpaintingManager.inpaintLayerArea(active, mask) }
                                    refreshComposite()
                                }
                                showMLInpaintDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Inpaint with Telea C++", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showMLInpaintDialog = false }) { Text("Cancel", color = Color.Gray) }
                },
                containerColor = PanelBg
            )
        }

        if (showLayersPanel) {
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                LayerPanel(
                    layerManager = layerManager,
                    onClose = { showLayersPanel = false },
                    onRefresh = { refreshComposite() }
                )
            }
        }
    }
}
