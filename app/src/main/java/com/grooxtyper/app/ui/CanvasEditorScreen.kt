package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.grooxtyper.app.ml.MLScript
import com.grooxtyper.app.ml.MLTextDetector
import com.grooxtyper.app.ml.BubbleDetector
import com.grooxtyper.app.ml.BubbleModel
import com.grooxtyper.app.ml.readingOrder
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.DrawingLayer
import com.grooxtyper.app.model.ExportFormat
import com.grooxtyper.app.model.FileExportManager
import com.grooxtyper.app.model.InpaintingManager
import com.grooxtyper.app.model.ImageImport
import com.grooxtyper.app.model.FontRegistry
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.ProjectManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.TextBox
import com.grooxtyper.app.model.TextHandle
import com.grooxtyper.app.model.TextLayer
import com.grooxtyper.app.model.TextRenderer
import com.grooxtyper.app.model.TextStyleManager
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

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

/** Kecilkan bitmap untuk jendela referensi (hemat memori, sumber di-recycle). */
private fun downscaleForReference(src: Bitmap, maxSide: Int = 1024): Bitmap {
    val longSide = max(src.width, src.height)
    if (longSide <= maxSide) return src
    val s = maxSide / longSide.toFloat()
    return ImageImport.scaleTo(
        src,
        max(1, (src.width * s).toInt()),
        max(1, (src.height * s).toInt())
    )
}

/**
 * Gambar bitmap jangkung per potongan 2048px agar lolos batas tekstur GPU
 * (banyak GPU gagal untuk bitmap 720x16000 sekali jalan).
 */
private fun drawTallBitmap(
    native: android.graphics.Canvas,
    bmp: Bitmap,
    paint: android.graphics.Paint?,
    tileH: Int = 2048
) {
    if (bmp.height <= tileH) {
        native.drawBitmap(bmp, 0f, 0f, paint)
        return
    }
    var y = 0
    while (y < bmp.height) {
        val h = minOf(tileH, bmp.height - y)
        val slice = try {
            Bitmap.createBitmap(bmp, 0, y, bmp.width, h)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
        if (slice != null) {
            try {
                native.drawBitmap(slice, 0f, y.toFloat(), paint)
            } finally {
                runCatching { slice.recycle() }
            }
        }
        y += h
    }
}

/** Gambar papan catur berubin via BitmapShader (hemat: tile 64px, bukan 46MB). */
private fun drawCheckerTiled(
    native: android.graphics.Canvas,
    w: Int,
    h: Int,
    tile: Bitmap
) {
    val shader = android.graphics.BitmapShader(
        tile,
        android.graphics.Shader.TileMode.REPEAT,
        android.graphics.Shader.TileMode.REPEAT
    )
    val paint = android.graphics.Paint().apply { this.shader = shader }
    native.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
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

    // Papan catur transparansi berubin + paint berfilter (tajam saat zoom-out,
    // hemat memori untuk 720x16000: tile 64px, bukan full-bitmap 46MB).
    val checkerTile = remember {
        ImageImport.makeCheckerTile()
    }
    DisposableEffect(checkerTile) {
        onDispose { runCatching { checkerTile.recycle() } }
    }
    val filteredPaint = remember {
        android.graphics.Paint().apply { isFilterBitmap = true }
    }

    var refreshCanvasState by remember { mutableIntStateOf(0) }
    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    // Pelacakan perubahan kanvas untuk auto-save hemat (hanya simpan jika kotor).
    var dirtyVersion by remember { mutableIntStateOf(0) }
    var lastSavedVersion by remember { mutableIntStateOf(-1) }
    // Dibaca agar tombol Undo/Redo ikut recompose saat history berubah.
    val historyTick = undoRedoManager.historyVersion

    fun refreshComposite() {
        layerManager.renderComposite(compositeBitmap)
        refreshCanvasState++
        dirtyVersion++
    }

    LaunchedEffect(projectId) {
        while (true) {
            delay(15000L)
            if (dirtyVersion != lastSavedVersion) {
                // Salin di Main (aman dari race dengan render), kompres di IO.
                val snap = runCatching {
                    compositeBitmap.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                if (snap != null) {
                    val dv = dirtyVersion
                    withContext(Dispatchers.IO) {
                        runCatching { projectManager.saveArtwork(projectId, snap) }
                        runCatching { snap.recycle() }
                    }
                    lastSavedVersion = dv
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Simpan terakhir tanpa memblokir navigasi.
            val snap = runCatching {
                compositeBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching { projectManager.saveArtwork(projectId, snap) }
                    runCatching { snap.recycle() }
                }
            }
        }
    }

    LaunchedEffect(initialBitmap) {
        initialBitmap?.let { bmp ->
            layerManager.getActiveLayer()?.let { active ->
                // Import TANPA resize: 1:1 no-scale agar 720x16000 tidak diubah.
                // (Kanvas sudah = ukuran asli dari GalleryScreen.)
                ImageImport.drawBitmapCenterNoScale(active.getPersistentBitmap(), bmp)
                active.tileMap.importFromBitmap(active.getPersistentBitmap())
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
    var makeEditableText by remember { mutableStateOf(true) }
    var mlScripts by remember { mutableStateOf(setOf(MLScript.LATIN, MLScript.CHINESE, MLScript.JAPANESE, MLScript.KOREAN)) }
    var mlDetecting by remember { mutableStateOf(false) }
    var fontList by remember { mutableStateOf(fontRegistry.fonts()) }

    // Style preset untuk pencocokan prefix otomatis ala TypeR.
    val textStyleManager = remember { TextStyleManager(context) }
    var lastAutoPrefix by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Mode multi-bubble: antrean baris teks untuk ditaruh berurutan.
    var showMultiBubbleDialog by remember { mutableStateOf(false) }
    var multiBubbleDraft by remember { mutableStateOf("") }
    var multiBubbleLines by remember { mutableStateOf(listOf<String>()) }
    var multiBubbleIndex by remember { mutableIntStateOf(0) }
    var multiBubbleTemplate by remember { mutableStateOf<TextBox?>(null) }
    fun isMultiBubbleActive() = multiBubbleLines.isNotEmpty() && multiBubbleIndex < multiBubbleLines.size

    // Bubble detector: dua profil model yang bisa dipilih user.
    val bubbleDetector = remember { BubbleDetector() }
    var detectedBubbles by remember { mutableStateOf(listOf<com.grooxtyper.app.ml.DetectedBubble>()) }
    var showBubbleDialog by remember { mutableStateOf(false) }
    var bubbleModel by remember { mutableStateOf(BubbleModel.FAST) }
    var bubbleDetecting by remember { mutableStateOf(false) }
    var showBubbleOverlay by remember { mutableStateOf(true) }
    var lassoPath by remember { mutableStateOf<Path?>(null) }

    fun runBubbleDetection() {
        scope.launch {
            val snap = runCatching {
                compositeBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull() ?: return@launch
            bubbleDetecting = true
            val found = bubbleDetector.detect(snap, bubbleModel)
            runCatching { snap.recycle() }
            detectedBubbles = found
            bubbleDetecting = false
            showBubbleOverlay = true
            refreshComposite()
        }
    }

    /** Terapkan style preset berdasar prefix teks ("[SFX]..."). True bila diterapkan. */
    fun applyPrefixStyleTo(box: TextBox): Boolean {
        val t = box.text.trimStart()
        if (!t.startsWith("[")) {
            if (lastAutoPrefix?.first == box.id) lastAutoPrefix = null
            return false
        }
        val match = textStyleManager.list().firstOrNull { preset ->
            preset.prefix.isNotBlank() && t.startsWith(preset.prefix, ignoreCase = true)
        } ?: return false
        if (lastAutoPrefix?.first == box.id && lastAutoPrefix?.second == match.id) return false
        val tf = fontList.find { it.first == match.fontName }?.second
        match.applyTo(box, tf)
        lastAutoPrefix = box.id to match.id
        return true
    }

    fun runMLDetection() {
        scope.launch {
            val active = layerManager.getActiveLayer()
            if (active != null) {
                mlDetecting = true
                detectedTextRegions = mlTextDetector.detectTextRegions(active.getBitmap(), mlScripts)
                mlDetecting = false
                showMLInpaintDialog = true
            }
        }
    }

    /** Isi semua bubble terdeteksi dari antrean multi-bubble (urutan baca manga). */
    fun autoFillBubbles() {
        if (multiBubbleLines.isEmpty() || detectedBubbles.isEmpty()) return
        val ordered = readingOrder(detectedBubbles)
        val n = minOf(ordered.size, multiBubbleLines.size)
        if (n <= 0) return
        val template = selectedTextBox?.copy()
        var last: TextBox? = null
        var lastLayerId = ""
        for (i in 0 until n) {
            val b = ordered[i].boundingBox
            val inset = RectF(
                b.left + b.width() * 0.12f,
                b.top + b.height() * 0.12f,
                b.right - b.width() * 0.12f,
                b.bottom - b.height() * 0.12f
            )
            val box = TextBox(
                text = multiBubbleLines[i],
                position = Offset(inset.centerX(), inset.centerY()),
                color = brushEngine.color
            )
            template?.let { box.applyStyleFrom(it) }
            applyPrefixStyleTo(box)
            box.fitToRect(inset)
            val created = layerManager.addTextLayer(box)
            undoRedoManager.pushLayerAdd(created.id)
            last = box
            lastLayerId = created.id
        }
        last?.let {
            selectedTextBox = it
            layerManager.activeLayerId = lastLayerId
            activeTool = ActiveTool.TEXT
            showTextEditor = true
        }
        refreshComposite()
    }

    fun flattenSelectedText() {
        val box = selectedTextBox ?: return
        val textLayer = layerManager.layers.filterIsInstance<TextLayer>().find { it.box.id == box.id }
            ?: return
        val target = layerManager.ensureDrawingLayer()
        undoRedoManager.saveSnapshot(target)
        TextRenderer.flatten(target, box)
        val idx = layerManager.indexOfLayer(textLayer.id)
        undoRedoManager.pushLayerRemove(textLayer, idx)
        layerManager.removeLayerById(textLayer.id)
        layerManager.activeLayerId = target.id
        selectedTextBox = null
        showTextEditor = false
        refreshComposite()
    }

    fun deleteSelectedText() {
        val box = selectedTextBox ?: return
        layerManager.layers.filterIsInstance<TextLayer>().find { it.box.id == box.id }
            ?.let {
                val idx = layerManager.indexOfLayer(it.id)
                undoRedoManager.pushLayerRemove(it, idx)
                layerManager.removeLayerById(it.id)
            }
        selectedTextBox = null
        showTextEditor = false
        refreshComposite()
    }

    /** Taruh baris antrean multi-bubble berikutnya di titik ketuk. */
    fun placeNextBubble(at: Offset) {
        if (!isMultiBubbleActive()) return
        val line = multiBubbleLines[multiBubbleIndex]
        val box = TextBox(
            text = line,
            position = at,
            color = brushEngine.color
        )
        multiBubbleTemplate?.let { box.applyStyleFrom(it) }
        applyPrefixStyleTo(box)
        // Bila ada seleksi bubble, langsung pas-kan ukurannya.
        selectionEngine.selectionBounds()?.let { box.fitToRect(it) }
        val created = layerManager.addTextLayer(box)
        undoRedoManager.pushLayerAdd(created.id)
        selectedTextBox = box
        layerManager.activeLayerId = created.id
        textHandleMode = TextHandle.BODY
        multiBubbleIndex++
        if (!isMultiBubbleActive()) {
            // Antrean habis: buka editor untuk hasil terakhir.
            showTextEditor = true
        }
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
            scope.launch {
                // Decode di IO (budget piksel + koreksi EXIF), gambar HQ di Main.
                val loaded = withContext(Dispatchers.IO) {
                    ImageImport.decodeContentUri(context.contentResolver, it)
                } ?: return@launch
                try {
                    val active = layerManager.ensureDrawingLayer()
                    undoRedoManager.saveSnapshot(active)
                    // Import TANPA resize: 1:1 no-scale (kelebihan di-crop,
                    // kekurangan transparan) agar tidak mengubah piksel asli.
                    ImageImport.drawBitmapCenterNoScale(active.getPersistentBitmap(), loaded)
                    active.tileMap.importFromBitmap(active.getPersistentBitmap())
                    active.markDirty()
                    // Referensi cukup versi kecil agar hemat memori.
                    referenceBitmap = downscaleForReference(loaded)
                    refreshComposite()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun screenToCanvasCoordinates(screenX: Float, screenY: Float): Offset {
        // Inverse dari Modifier.graphicsLayer(scale, translation, rotationZ)
        // yang pivot-nya DINAMIS mengikuti titik tengah jari (pivotFrac).
        // Bitmap digambar di (0,0) Canvas, jadi koordinat layout == koordinat bitmap.
        val pivotX = viewState.pivotFracX * viewportSize.width
        val pivotY = viewState.pivotFracY * viewportSize.height

        val dx = (screenX - pivotX - viewState.offsetX) / viewState.scale
        val dy = (screenY - pivotY - viewState.offsetY) / viewState.scale

        val rad = Math.toRadians((-viewState.rotation).toDouble())
        val rx = dx * Math.cos(rad) - dy * Math.sin(rad)
        val ry = dx * Math.sin(rad) + dy * Math.cos(rad)

        return Offset((rx + pivotX).toFloat(), (ry + pivotY).toFloat())
    }

    /** Ambil warna dari kanvas pada posisi layar (untuk eyedropper). */
    fun pickColorAt(screenPos: Offset) {
        val cp = screenToCanvasCoordinates(screenPos.x, screenPos.y)
        val x = cp.x.toInt().coerceIn(0, canvasWidth - 1)
        val y = cp.y.toInt().coerceIn(0, canvasHeight - 1)
        runCatching { brushEngine.color = compositeBitmap.getPixel(x, y) }
    }

    var lastCanvasPoint by remember { mutableStateOf<Offset?>(null) }
    var lastScreenPoint by remember { mutableStateOf<Offset?>(null) }
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }
    var strokeProgress by remember { mutableStateOf(0f) }
    var strokeLength by remember { mutableStateOf(0f) }
    // Layer yang dipakai selama satu stroke (disimpan agar tidak lookup per-move
    // dan tidak ganti layer di tengah goresan).
    var strokeLayer by remember { mutableStateOf<DrawingLayer?>(null) }
    // Eyedropper sementara via tahan jari (tanpa meninggalkan titik cat).
    var colorPickActive by remember { mutableStateOf(false) }
    var pressId by remember { mutableIntStateOf(0) }
    var pressStartScreen by remember { mutableStateOf(Offset.Zero) }
    var pressMoved by remember { mutableStateOf(false) }
    // Sesi dua-jari aktif (untuk settle event pertama + menahan aksi satu jari).
    var twoFingerActive by remember { mutableStateOf(false) }

    // Tahan jari 600ms tanpa geser = eyedropper sementara.
    // Titik awal dibatalkan via undo agar kanvas tetap bersih.
    LaunchedEffect(pressId) {
        if (pressId == 0) return@LaunchedEffect
        delay(600L)
        if ((activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) &&
            !pressMoved && !colorPickActive && strokeLayer != null
        ) {
            val sl = strokeLayer
            if (sl != null) {
                undoRedoManager.undo(layerManager)
                brushEngine.syncTiles(sl)
                brushEngine.endStroke()
                strokeLayer = null
            }
            colorPickActive = true
            pickColorAt(cursorPosition ?: pressStartScreen)
            refreshComposite()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .onSizeChanged { viewportSize = it }
    ) {
        // Canvas with gestures
        // NOTE: kunci pointerInput disengaja minimal (activeTool, selectedTextBox,
        // viewportSize) agar pinch-zoom tidak me-restart gesture di tengah jalan
        // (viewState.scale/offset/rotation berubah kontinu saat pinch).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, selectedTextBox, viewportSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pointerCount = changes.size

                            if (pointerCount >= 2 || activeTool == ActiveTool.PAN) {
                                // Gesture berubah jadi pan/zoom: akhiri stroke yang tertunda.
                                strokeLayer?.let { brushEngine.syncTiles(it) }
                                strokeLayer = null
                                brushEngine.endStroke()
                                colorPickActive = false
                                pressId++
                                lassoPath = null
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

                                        val vw = viewportSize.width.toFloat().coerceAtLeast(1f)
                                        val vh = viewportSize.height.toFloat().coerceAtLeast(1f)

                                        if (!twoFingerActive) {
                                            // Event pertama dua-jari: previous tak stabil,
                                            // hanya tetapkan pivot agar tidak lompat.
                                            twoFingerActive = true
                                            lastScreenPoint = null
                                            viewState.setPivotFraction(curCenter.x / vw, curCenter.y / vh, vw, vh)
                                            p1.consume()
                                            p2.consume()
                                        } else {
                                            pan = curCenter - prevCenter

                                            val prevDist = (prevP1 - prevP2).getDistance()
                                            val curDist = (curP1 - curP2).getDistance()
                                            // Jari bersilang (jarak ~0) membuat zoom meledak → abaikan.
                                            if (prevDist > 20f) {
                                                zoom = (curDist / prevDist).coerceIn(0.8f, 1.25f)
                                            }

                                            val prevAngle = Math.toDegrees(kotlin.math.atan2((prevP2.y - prevP1.y).toDouble(), (prevP2.x - prevP1.x).toDouble())).toFloat()
                                            val curAngle = Math.toDegrees(kotlin.math.atan2((curP2.y - curP1.y).toDouble(), (curP2.x - curP1.x).toDouble())).toFloat()
                                            // Normalisasi delta ke [-180,180] agar tak spin 358°
                                            // saat melewati batas -180/180.
                                            rotation = ((curAngle - prevAngle + 540f) % 360f) - 180f

                                            // Pivot zoom/rotasi = titik tengah dua jari (dengan
                                            // kompensasi offset) agar tidak teleport ke satu jari.
                                            viewState.setPivotFraction(curCenter.x / vw, curCenter.y / vh, vw, vh)

                                            viewState.scale = (viewState.scale * zoom).coerceIn(0.1f, 10.0f)
                                            viewState.offsetX += pan.x
                                            viewState.offsetY += pan.y
                                            viewState.applyRotationDelta(rotation)

                                            p1.consume()
                                            p2.consume()
                                        }
                                    } else {
                                        lastScreenPoint = null
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
                                    if (twoFingerActive) {
                                        // Sisa satu jari setelah pinch: tahan semua aksi
                                        // sampai jari diangkat (mencegah titik/goresan liar).
                                        lastCanvasPoint = null
                                        cursorPosition = null
                                        change.consume()
                                    } else {
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
                                                // Satu langkah undo untuk seluruh gestur ubah teks ini.
                                                current?.let { undoRedoManager.pushTextBox(it.id, it.copy()) }
                                            } else {
                                                val hit = layerManager.layers
                                                    .filterIsInstance<TextLayer>()
                                                    .findLast { it.box.hitTest(touchCanvasPos) }
                                                if (hit != null) {
                                                    selectedTextBox = hit.box
                                                    layerManager.activeLayerId = hit.id
                                                    textHandleMode = TextHandle.BODY
                                                    undoRedoManager.pushTextBox(hit.box.id, hit.box.copy())
                                                    refreshComposite()
                                                } else {
                                                    if (isMultiBubbleActive()) {
                                                        placeNextBubble(touchCanvasPos)
                                                    } else {
                                                        val box = TextBox(
                                                            text = "Teks baru",
                                                            position = touchCanvasPos,
                                                            color = brushEngine.color
                                                        )
                                                        val created = layerManager.addTextLayer(box)
                                                        undoRedoManager.pushLayerAdd(created.id)
                                                        selectedTextBox = box
                                                        textHandleMode = TextHandle.BODY
                                                        showTextEditor = true
                                                        refreshComposite()
                                                    }
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
                                    } else if (activeTool == ActiveTool.EYEDROPPER) {
                                        // Eyedropper tool: ketuk/seret untuk ambil warna kanvas.
                                        pickColorAt(change.position)
                                    } else if (activeTool == ActiveTool.LASSO) {
                                        if (lastCanvasPoint == null) {
                                            // Ketuk bubble terdeteksi → jadikan seleksi oval;
                                            // bila kosong mulai gambar lasso bebas.
                                            val hit = detectedBubbles
                                                .filter { it.boundingBox.contains(touchCanvasPos.x, touchCanvasPos.y) }
                                                .minByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                            if (hit != null) {
                                                selectionEngine.selectOval(hit.boundingBox)
                                                lassoPath = null
                                                refreshComposite()
                                            } else {
                                                lassoPath = Path().apply {
                                                    moveTo(touchCanvasPos.x, touchCanvasPos.y)
                                                }
                                                refreshComposite()
                                            }
                                        } else {
                                            lassoPath?.lineTo(touchCanvasPos.x, touchCanvasPos.y)
                                            refreshComposite()
                                        }
                                    } else if (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) {
                                        if (colorPickActive) {
                                            // Mode tahan-jari: ambil warna, jangan melukis.
                                            pickColorAt(change.position)
                                        } else if (lastCanvasPoint == null) {
                                            pressId++
                                            pressStartScreen = change.position
                                            pressMoved = false
                                            val activeLayer = layerManager.ensureDrawingLayer()
                                            strokeLayer = activeLayer
                                            // Kunci tipe brush sekali saat stroke dimulai,
                                            // bukan per-move (menghindari recompose tiap event).
                                            if (activeTool == ActiveTool.ERASER) {
                                                brushEngine.brushType = BrushType.ERASER
                                            } else if (brushEngine.brushType == BrushType.ERASER) {
                                                brushEngine.brushType = BrushType.PEN_HARD
                                            }
                                            undoRedoManager.saveSnapshot(activeLayer)
                                            brushEngine.beginStroke()
                                            brushEngine.strokeSegmentOnLayer(activeLayer, touchCanvasPos, touchCanvasPos, 0f)
                                        } else {
                                            if ((change.position - pressStartScreen).getDistance() > 16f) {
                                                pressMoved = true
                                            }
                                            val target = strokeLayer ?: layerManager.ensureDrawingLayer()
                                            if (strokeLayer == null) strokeLayer = target
                                            val dist = (touchCanvasPos - lastCanvasPoint!!).getDistance()
                                            strokeLength += dist
                                            val progress = if (strokeLength > 0f) (strokeLength / 500f).coerceIn(0f, 1f) else 0f
                                            brushEngine.strokeSegmentOnLayer(target, lastCanvasPoint!!, touchCanvasPos, progress)
                                        }
                                        refreshComposite()
                                    }
                                    lastCanvasPoint = touchCanvasPos
                                    change.consume()
                                    } // else twoFingerActive
                                } else {
                                    strokeLayer?.let { brushEngine.syncTiles(it) }
                                    strokeLayer = null
                                    brushEngine.endStroke()
                                    colorPickActive = false
                                    pressId++
                                    twoFingerActive = false
                                    // Kunci sketsa lasso bebas menjadi seleksi.
                                    lassoPath?.let { selectionEngine.setLassoPath(it); lassoPath = null }
                                    textHandleMode = TextHandle.NONE
                                    lastCanvasPoint = null
                                    cursorPosition = null
                                    strokeLength = 0f
                                }
                            } else {
                                strokeLayer?.let { brushEngine.syncTiles(it) }
                                strokeLayer = null
                                brushEngine.endStroke()
                                twoFingerActive = false
                                lassoPath?.let { selectionEngine.setLassoPath(it); lassoPath = null }
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
                        rotationZ = viewState.rotation,
                        transformOrigin = TransformOrigin(
                            viewState.pivotFracX,
                            viewState.pivotFracY
                        )
                    )
            ) {
                val trigger = refreshCanvasState
                // Grid transparansi berubin di bawah artwork (hemat untuk
                // 720x16000), lalu komposit per slice 2048px agar lolos
                // batas tekstur GPU + tetap tajam saat zoom-out.
                val nativeMain = drawContext.canvas.nativeCanvas
                drawCheckerTiled(nativeMain, canvasWidth, canvasHeight, checkerTile)
                drawTallBitmap(nativeMain, compositeBitmap, filteredPaint)

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

                if (showBubbleOverlay && detectedBubbles.isNotEmpty()) {
                    val bubblePaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f / viewState.scale
                        color = android.graphics.Color.CYAN
                    }
                    val indexPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f / viewState.scale
                        isFakeBoldText = true
                        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val native = drawContext.canvas.nativeCanvas
                    detectedBubbles.forEachIndexed { i, b ->
                        val r = b.boundingBox
                        native.drawRoundRect(r.left, r.top, r.right, r.bottom, 24f, 24f, bubblePaint)
                        native.drawText(
                            "${i + 1}",
                            r.left + 8f,
                            r.top + 38f / viewState.scale,
                            indexPaint
                        )
                    }
                }

                lassoPath?.let { sketch ->
                    val previewPaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f / viewState.scale
                        color = android.graphics.Color.CYAN
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                    }
                    drawContext.canvas.nativeCanvas.drawPath(sketch, previewPaint)
                }
            }

            // Brush cursor overlay
            if (cursorPosition != null && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER || activeTool == ActiveTool.EYEDROPPER)) {
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

        // Quick sliders (bottom, ala ibisPaint X)
        if (showQuickSlider && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 72.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xBB1C1C1E))
                    .border(1.dp, Color(0xFF38383A), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sizeFrac = (brushEngine.size / 120f * 0.38f + 0.07f).coerceIn(0.07f, 0.45f)
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(30.dp)) {
                            drawCircle(
                                color = if (activeTool == ActiveTool.ERASER) Color.Red else Color.White,
                                radius = sizeFrac * size.minDimension,
                                alpha = brushEngine.opacity
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Size", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Slider(
                        value = brushEngine.size,
                        onValueChange = { brushEngine.size = it },
                        valueRange = 1f..120f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text(
                        "${brushEngine.size.toInt()}",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(38.dp))
                    Text("Alpha", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Slider(
                        value = brushEngine.opacity,
                        onValueChange = { brushEngine.opacity = it },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text(
                        "${(brushEngine.opacity * 100).toInt()}%",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }

        // Top bar (ibisPaint style - minimal)
        // clickable noop agar tap di area kosong bar tidak tembus ke kanvas.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(TopBarBg)
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // Simpan di background agar tombol Back tidak macet; judul proyek dipertahankan.
                val snap = runCatching {
                    compositeBitmap.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                if (snap != null) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { projectManager.saveArtwork(projectId, snap) }
                        runCatching { snap.recycle() }
                    }
                }
                onBackToGallery()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { undoRedoManager.undo(layerManager); refreshComposite() },
                enabled = historyTick.let { undoRedoManager.canUndo() }
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoRedoManager.canUndo()) Color.White else Color.DarkGray)
            }

            IconButton(
                onClick = { undoRedoManager.redo(layerManager); refreshComposite() },
                enabled = historyTick.let { undoRedoManager.canRedo() }
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (undoRedoManager.canRedo()) Color.White else Color.DarkGray)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Fitur layer di top bar (cermin tombol layer bawah + jumlah layer).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
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
        // clickable noop agar tap di celah tombol tidak tembus ke kanvas.
        selectedTextBox?.let { box ->
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
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

        // Hint bar mode multi-bubble (di atas toolbar bawah).
        if (isMultiBubbleActive()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bubble ${multiBubbleIndex + 1}/${multiBubbleLines.size} — ketuk kanvas",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { multiBubbleLines = emptyList(); multiBubbleIndex = 0 }
                ) { Text("Batal", color = Color.Red, fontSize = 12.sp) }
            }
        }

        // Bottom toolbar (ibisPaint style)
        // clickable noop agar tap di celah tombol tidak tembus ke kanvas.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(BottomBarBg)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pan tool
            IconButton(onClick = { activeTool = ActiveTool.PAN; showBrushSettings = false }) {
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
                            if (activeTool == ActiveTool.BRUSH) {
                                // Toggle: tap ikon brush saat aktif untuk buka/tutup panel.
                                showBrushSettings = !showBrushSettings
                            } else {
                                activeTool = ActiveTool.BRUSH
                                if (brushEngine.brushType == BrushType.ERASER) {
                                    brushEngine.brushType = BrushType.PEN_HARD
                                }
                                layerManager.ensureDrawingLayer()
                                showBrushSettings = true
                            }
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
                            showBrushSettings = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eraser", tint = if (activeTool == ActiveTool.ERASER) Color.White else Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Eyedropper
            IconButton(onClick = { activeTool = ActiveTool.EYEDROPPER; showBrushSettings = false }) {
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
            IconButton(onClick = { activeTool = ActiveTool.LASSO; showBrushSettings = false; showLassoMenu = true }) {
                Icon(Icons.Default.SelectAll, contentDescription = "Lasso", tint = if (activeTool == ActiveTool.LASSO) Accent else Color.White)
            }

            DropdownMenu(expanded = showLassoMenu, onDismissRequest = { showLassoMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Deteksi Bubble…") },
                    onClick = {
                        showLassoMenu = false
                        showBubbleDialog = true
                        if (detectedBubbles.isEmpty()) runBubbleDetection()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Fit Text to Selection") },
                    enabled = selectedTextBox != null && selectionEngine.hasSelection,
                    onClick = {
                        val box = selectedTextBox
                        val bounds = selectionEngine.selectionBounds()
                        if (box != null && bounds != null) {
                            undoRedoManager.pushTextBox(box.id, box.copy())
                            box.fitToRect(bounds)
                            refreshComposite()
                        }
                        showLassoMenu = false
                    }
                )
                DropdownMenuItem(text = { Text("Clear Area") }, onClick = {
                    layerManager.getActiveLayer()?.let {
                        undoRedoManager.saveSnapshot(it)
                        selectionEngine.clearSelectedArea(it)
                        refreshComposite()
                    }
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
                    layerManager.getActiveLayer()?.let {
                        undoRedoManager.saveSnapshot(it)
                        selectionEngine.cutSelectedArea(it)
                        refreshComposite()
                    }
                    showLassoMenu = false
                })
                DropdownMenuItem(text = { Text("Paste Area") }, onClick = {
                    selectionEngine.pasteToNewLayer(layerManager)?.let {
                        undoRedoManager.pushLayerAdd(it.id)
                    }
                    refreshComposite(); showLassoMenu = false
                })
            }

            // Text
            IconButton(onClick = {
                activeTool = ActiveTool.TEXT
                showBrushSettings = false
                if (selectedTextBox != null) showTextEditor = true
            }) {
                Icon(Icons.Default.TextFields, contentDescription = "Text", tint = if (activeTool == ActiveTool.TEXT) Accent else Color.White)
            }

            // ML Inpaint
            IconButton(
                onClick = { runMLDetection() }
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
                        onPushTextHistory = { before ->
                            undoRedoManager.pushTextBox(box.id, before)
                        },
                        onCheckPrefix = { applyPrefixStyleTo(it) },
                        onOpenMultiBubble = {
                            multiBubbleDraft = ""
                            showMultiBubbleDialog = true
                        },
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
                        Text(
                            if (mlDetecting) "Mendeteksi teks…" else "Detected ${detectedTextRegions.size} text blocks.",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Bahasa deteksi:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MLScript.values().forEach { script ->
                                val on = script in mlScripts
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (on) Accent else PanelBg)
                                        .clickable {
                                            mlScripts = if (on) mlScripts - script else mlScripts + script
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(script.displayName, color = Color.White, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        TextButton(onClick = { runMLDetection() }) {
                            Text("Deteksi ulang", color = Accent, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jadikan teks editable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Buat TextBox per baris terdeteksi", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = makeEditableText,
                                onCheckedChange = { makeEditableText = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Accent)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                                    // Estimasi warna teks ASLI dulu (sebelum di-inpaint).
                                    val wantsEditable = makeEditableText
                                    val estimates = if (wantsEditable) {
                                        detectedTextRegions.take(40).map { region ->
                                            val b = region.boundingBox
                                            val cx = (b.left + b.right) / 2f
                                            val cy = (b.top + b.bottom) / 2f
                                            val h = (b.bottom - b.top).toFloat()
                                            Triple(
                                                region,
                                                Offset(cx, cy),
                                                mlTextDetector.estimateRegionTextColor(
                                                    active.getBitmap(), b, brushEngine.color
                                                )
                                            )
                                        }
                                    } else emptyList()
                                    undoRedoManager.saveSnapshot(active)
                                    val mask = mlTextDetector.generateMaskBitmap(canvasWidth, canvasHeight, detectedTextRegions, active.getBitmap(), selectedMaskType)
                                    withContext(Dispatchers.Default) { inpaintingManager.inpaintLayerArea(active, mask) }
                                    // Ganti tiap baris terdeteksi jadi TextBox editable.
                                    var firstBox: TextBox? = null
                                    for ((region, center, textColor) in estimates) {
                                        val h = (region.boundingBox.bottom - region.boundingBox.top).toFloat()
                                        val box = TextBox(
                                            text = region.text.ifBlank { "Teks" },
                                            position = center,
                                            fontSize = (h * 0.75f).coerceIn(20f, 220f),
                                            color = textColor,
                                            bold = true
                                        )
                                        applyPrefixStyleTo(box)
                                        val created = layerManager.addTextLayer(box)
                                        undoRedoManager.pushLayerAdd(created.id)
                                        if (firstBox == null) firstBox = box
                                    }
                                    firstBox?.let {
                                        selectedTextBox = it
                                        activeTool = ActiveTool.TEXT
                                        showTextEditor = true
                                    }
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
                    undoManager = undoRedoManager,
                    refreshTick = refreshCanvasState,
                    onClose = { showLayersPanel = false },
                    onRefresh = { refreshComposite() }
                )
            }
        }

        if (showBubbleDialog) {
            AlertDialog(
                onDismissRequest = { showBubbleDialog = false },
                title = { Text("Bubble Detector", color = Color.White) },
                text = {
                    Column {
                        Text(
                            if (bubbleDetecting) "Mendeteksi bubble…"
                            else "Ditemukan ${detectedBubbles.size} bubble.",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Model:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        BubbleModel.values().forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { bubbleModel = model }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = bubbleModel == model,
                                    onClick = { bubbleModel = model }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(model.desc, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { runBubbleDetection() },
                                colors = ButtonDefaults.buttonColors(containerColor = PanelBg),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Deteksi", color = Color.White, fontSize = 12.sp) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Overlay", color = Color.White, fontSize = 12.sp)
                            }
                            Switch(
                                checked = showBubbleOverlay,
                                onCheckedChange = {
                                    showBubbleOverlay = it
                                    refreshComposite()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Accent)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ketuk bubble di mode Lasso untuk jadikan seleksi.",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        if (multiBubbleLines.isEmpty()) {
                            Text(
                                "Isi draft multi-bubble dulu untuk pakai Isi Otomatis.",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            autoFillBubbles()
                            showBubbleDialog = false
                        },
                        enabled = multiBubbleLines.isNotEmpty() && detectedBubbles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Isi Otomatis", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showBubbleDialog = false }) {
                        Text("Tutup", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
        }

        if (showMultiBubbleDialog) {
            AlertDialog(
                onDismissRequest = { showMultiBubbleDialog = false },
                title = { Text("Multi-bubble", color = Color.White) },
                text = {
                    Column {
                        Text(
                            "Satu baris per bubble. Ketuk kanvas berurutan untuk menaruh teks.",
                            color = Color.Gray, fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = multiBubbleDraft,
                            onValueChange = { multiBubbleDraft = it },
                            label = { Text("Script (satu baris per bubble)") },
                            minLines = 4,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val lines = multiBubbleDraft.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(60)
                            if (lines.isNotEmpty()) {
                                multiBubbleLines = lines
                                multiBubbleIndex = 0
                                multiBubbleTemplate = selectedTextBox?.copy()
                                activeTool = ActiveTool.TEXT
                                showTextEditor = false
                                showMultiBubbleDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Mulai", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showMultiBubbleDialog = false }) {
                        Text("Batal", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
        }
    }
}
