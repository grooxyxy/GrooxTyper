package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
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
import com.grooxtyper.app.ml.PpocrDetector
import com.grooxtyper.app.ml.TextEngine
import com.grooxtyper.app.ml.BubbleDetector
import com.grooxtyper.app.ml.BubbleModel
import com.grooxtyper.app.ml.readingOrder
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushHugeGuide
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.DrawingLayer
import com.grooxtyper.app.model.ExportFormat
import com.grooxtyper.app.model.FileExportManager
import com.grooxtyper.app.model.HealMode
import com.grooxtyper.app.model.InpaintMode
import com.grooxtyper.app.model.InpaintingManager
import com.grooxtyper.app.model.ImageImport
import com.grooxtyper.app.model.FontRegistry
import com.grooxtyper.app.model.LayerBlendMode
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.ProjectManager
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.SelectionEngine
import com.grooxtyper.app.model.TextBox
import com.grooxtyper.app.model.TextHandle
import com.grooxtyper.app.model.TextLayer
import com.grooxtyper.app.model.TextLayerStore
import com.grooxtyper.app.model.TextLayerStore.parseLayers
import com.grooxtyper.app.model.TextLayerStore.restoreTextLayers
import com.grooxtyper.app.model.TextLayerStore.textLayersToJson
import com.grooxtyper.app.model.TextRenderer
import com.grooxtyper.app.model.TextStyleManager
import com.grooxtyper.app.model.StyleRule
import com.grooxtyper.app.model.StyleRuleManager
import com.grooxtyper.app.model.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class ActiveTool {
    PAN,
    BRUSH,
    ERASER,
    INPAINT,
    LASSO,
    SELECT_BOX,
    TEXT,
    EYEDROPPER
}

/** Satu baris script (dialog) untuk fitur Script kombo seleksi/bubble. */
data class ScriptEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    var used: Boolean = false
)

/** Parse teks mentah script menjadi daftar baris (filter Page header kosong). */
private fun parseScriptRaw(raw: String): List<ScriptEntry> {
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.startsWith("Page", ignoreCase = true) }
        .filterNot { it.equals("Terjemahan", ignoreCase = true) }
        .map { ScriptEntry(text = it) }
}

/** Pil status kecil di kepala dialog script. */
@Composable
private fun StatusPill(label: String, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight) Accent else Color(0xFF38383A))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
    }
}

/** Tombol aksi sumber naskah (ikon + label) di dialog script. */
@Composable
private fun ScriptActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = PanelBg),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp, vertical = 10.dp
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = Color.White, fontSize = 11.sp)
        }
    }
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
 * HEMAT: tanpa alokasi slice bitmap (pakai src/dst Rect) agar per-frame
 * 60fps tidak GC thrash — 720x16000 lama bikin 8×5.6MB slice per frame.
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
    // Tanpa alokasi: gambar per strip via src/dst rect (lanjaya, zero-GC).
    val src = android.graphics.Rect()
    val dst = android.graphics.RectF()
    var y = 0
    while (y < bmp.height) {
        val h = minOf(tileH, bmp.height - y)
        src.set(0, y, bmp.width, y + h)
        dst.set(0f, y.toFloat(), bmp.width.toFloat(), (y + h).toFloat())
        try {
            native.drawBitmap(bmp, src, dst, paint)
        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
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

/**
 * Checker berubin untuk REGIO TERLIHAT saja (viewport culling).
 * Shader tetap sejajar origin kanvas karena digambar di koordinat kanvas.
 */
private fun drawCheckerTiledRegion(
    native: android.graphics.Canvas,
    tile: Bitmap,
    l: Float,
    t: Float,
    r: Float,
    b: Float
) {
    val shader = android.graphics.BitmapShader(
        tile,
        android.graphics.Shader.TileMode.REPEAT,
        android.graphics.Shader.TileMode.REPEAT
    )
    val paint = android.graphics.Paint().apply { this.shader = shader }
    native.drawRect(l, t, r, b, paint)
}

/**
 * Gambar HANYA regio terlihat dari bitmap jangkung (viewport culling).
 * Saat zoom-in strip 720x16000, yang di-upload/diproses GPU hanya jendela
 * ~720x1000, bukan 11,5MP penuh per frame → pan/brush lanjaya.
 * Tanpa alokasi bitmap (src/dst Rect kecil di stack) — zero-GC.
 */
private fun drawVisibleBitmap(
    native: android.graphics.Canvas,
    bmp: Bitmap,
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    paint: android.graphics.Paint?
) {
    val li = l.toInt().coerceIn(0, bmp.width)
    val ti = t.toInt().coerceIn(0, bmp.height)
    val ri = (r + 0.999f).toInt().coerceIn(0, bmp.width)
    val bi = (b + 0.999f).toInt().coerceIn(0, bmp.height)
    if (ri - li < 1 || bi - ti < 1) return
    // Hampir seluruh kanvas terlihat → pakai jalur strip anti-limit GPU.
    val coverW = (ri - li).toFloat() / bmp.width.toFloat()
    val coverH = (bi - ti).toFloat() / bmp.height.toFloat()
    if (coverW > 0.98f && coverH > 0.98f) {
        drawTallBitmap(native, bmp, paint)
        return
    }
    val src = android.graphics.Rect(li, ti, ri, bi)
    val dst = android.graphics.RectF(li.toFloat(), ti.toFloat(), ri.toFloat(), bi.toFloat())
    try {
        native.drawBitmap(bmp, src, dst, paint)
    } catch (e: Exception) {
        e.printStackTrace()
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
    }
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
    // PP-OCR v6 small (ORT, model .onnx diunduh saat build ke assets).
    val ppocrDetector = remember { PpocrDetector(context) }
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

    // Flag dialog keluar (dideklarasikan awal agar auto-save/dispose bisa baca).
    var showExitDialog by remember { mutableStateOf(false) }
    var isSavingExit by remember { mutableStateOf(false) }
    var discardOnExit by remember { mutableStateOf(false) }
    // Flatten bersifat destruktif (teks jadi piksel, tak bisa diedit lagi)
    // sehingga selalu lewat konfirmasi — teks tetap editable selama mungkin.
    var showFlattenConfirm by remember { mutableStateOf(false) }

    fun refreshComposite() {
        layerManager.renderComposite(compositeBitmap)
        refreshCanvasState++
        dirtyVersion++
    }

    /** Refresh ringan: hanya tandai kotor (composite sudah disinkron inkremental). */
    fun refreshCanvasLight() {
        refreshCanvasState++
        dirtyVersion++
    }

    // Panel teks (slider/switch/warna) menembak tiap tick; di kanvas besar
    // render penuh 46MB per tick = lag & perubahan tampak "macet". Gabungkan
    // (trailing 120ms) agar UI responsif; nilai akhir selalu ter-render.
    var panelRefreshJob by remember { mutableStateOf<Job?>(null) }
    fun refreshCompositeCoalesced() {
        panelRefreshJob?.cancel()
        panelRefreshJob = scope.launch {
            delay(120)
            refreshComposite()
        }
    }

    /** Paksa render tweak panel yang tertunda (sebelum cek kotor/keluar). */
    fun flushPanelRefresh() {
        if (panelRefreshJob?.isActive == true) {
            panelRefreshJob?.cancel()
            refreshComposite()
        }
        panelRefreshJob = null
    }

    /**
     * Simpan project SEKARANG: PNG basis = layer gambar SAJA (tanpa teks
     * bakar) + JSON teks terpisah. Render basis di thread pemanggil (Main
     * saat auto-save/keluar, setara copy 46MB yang sudah ada), tulis file
     * di IO. Kembalikan true bila basis+teks tertulis.
     */
    suspend fun persistProjectNow(withPreview: Boolean): Boolean {
        val dv = dirtyVersion
        val base = try {
            Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            // Memori mepet (kanvas 46MB): lewati siklus ini daripada crash.
            e.printStackTrace()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        var preview: Bitmap? = null
        return try {
            layerManager.renderDrawingOnly(base)
            val textsJson = with(TextLayerStore) { layerManager.textLayersToJson() }
            if (withPreview) {
                preview = runCatching {
                    val longest = max(canvasWidth, canvasHeight).coerceAtLeast(1)
                    val s = (1024f / longest).coerceAtMost(1f)
                    ImageImport.scaleTo(
                        compositeBitmap,
                        max(1, (canvasWidth * s).toInt()),
                        max(1, (canvasHeight * s).toInt())
                    )
                }.getOrNull()
            }
            withContext(Dispatchers.IO) {
                runCatching { projectManager.saveArtwork(projectId, base) }
                runCatching { projectManager.saveTexts(projectId, textsJson) }
                preview?.let { pv -> runCatching { projectManager.savePreview(projectId, pv) } }
            }
            lastSavedVersion = dv
            true
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { base.recycle() }
            runCatching { preview?.recycle() }
        }
    }

    /**
     * Jalur cepat brush: true bila composite == 1 drawing layer saja
     * (tanpa folder/blend/opacity/clip) sehingga dab bisa di-blit langsung
     * ke composite tanpa render ulang 46MB per move. Layer TEKS boleh ada:
     * [blitLayerToComposite] menolak blit bila regio kotor menyentuh teks.
     */
    fun isSingleLayerFastPath(): Boolean {
        var drawings = 0
        for (item in layerManager.layers) {
            if (!item.isVisible) continue
            if (item.isFolder) {
                // Folder tanpa isi gambar/teks terlihat tetap boleh cepat.
                if (item.children.isNotEmpty()) return false
                continue
            }
            when (item) {
                is TextLayer -> continue
                is DrawingLayer -> {
                    drawings++
                    if (drawings > 1) return false
                    if (item.opacity < 1f) return false
                    if (item.blendMode != LayerBlendMode.NORMAL) return false
                    if (item.isClippingMask) return false
                }
                else -> return false
            }
        }
        return drawings == 1
    }

    /**
     * Blit regio kotor kecil (sekitar segmen brush) dari layer ke composite.
     * 1 blit ~50x50px vs render penuh 720x16000 (11,5MP) → ~200× lebih murah.
     * Kembalikan false bila pemanggil harus render penuh: struktur tak cocok
     * ATAU regio menyentuh teks tampil (teks vector tak ikut ter-blit).
     * Clear-then-draw agar penghapus ikut tampil live (bukan stale).
     * [deferRefresh]: true = jangan recompose per blit (batch 1x per event).
     * WAJIB untuk 720x16000: 64 blit × recompose per move = delay parah.
     */
    fun blitLayerToComposite(layer: DrawingLayer, p1: Offset?, p2: Offset, deferRefresh: Boolean = false): Boolean {
        if (!isSingleLayerFastPath()) return false
        val rad = brushEngine.size * 1.5f + 16f
        val ax = p1?.x ?: p2.x
        val ay = p1?.y ?: p2.y
        val l = minOf(ax, p2.x, canvasWidth.toFloat(), 0f).coerceIn(0f, canvasWidth.toFloat())
        val t = minOf(ay, p2.y, canvasHeight.toFloat(), 0f).coerceIn(0f, canvasHeight.toFloat())
        val r = maxOf(ax, p2.x, 0f).coerceIn(0f, canvasWidth.toFloat())
        val b = maxOf(ay, p2.y, 0f).coerceIn(0f, canvasHeight.toFloat())
        val li = (l - rad).toInt().coerceIn(0, canvasWidth)
        val ti = (t - rad).toInt().coerceIn(0, canvasHeight)
        val ri = (r + rad + 1f).toInt().coerceIn(0, canvasWidth)
        val bi = (b + rad + 1f).toInt().coerceIn(0, canvasHeight)
        if (ri - li < 1 || bi - ti < 1) return true
        // Teks yang tampil di dalam regio kotor → blit tak aman, render penuh.
        // (Rekursif: termasuk teks di dalam folder.)
        for (tl in layerManager.visibleTextLayers()) {
            val tb = tl.box.getBounds()
            if (tb.left < ri && tb.right > li && tb.top < bi && tb.bottom > ti) return false
        }
        try {
            val src = android.graphics.Rect(li, ti, ri, bi)
            val canvas = android.graphics.Canvas(compositeBitmap)
            // Batasi kerja ke regio kotor (tanpa alokasi bitmap).
            canvas.save()
            canvas.clipRect(src)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(layer.getPersistentBitmap(), src, src, null)
            canvas.restore()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return false
        }
        if (!deferRefresh) refreshCanvasLight()
        return true
    }

    LaunchedEffect(projectId) {
        // Auto-save hemat: kanvas jangkung 720x16000 render 46MB tiap 15s berat,
        // jadi interval adaptif. Basis = gambar saja (teks di JSON terpisah).
        val isHuge = canvasWidth.toLong() * canvasHeight > 4_000_000L
        val interval = if (isHuge) 30_000L else 15_000L
        while (true) {
            delay(interval)
            if (dirtyVersion != lastSavedVersion) {
                runCatching { persistProjectNow(withPreview = false) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Jaring pengaman: simpan bila masih kotor, kecuali user eksplisit
            // memilih "Keluar tanpa menyimpan" di dialog konfirmasi.
            flushPanelRefresh()
            if (!discardOnExit && dirtyVersion != lastSavedVersion) {
                val base = runCatching {
                    Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                }.getOrNull()
                if (base != null) {
                    try {
                        layerManager.renderDrawingOnly(base)
                        val textsJson = with(TextLayerStore) { layerManager.textLayersToJson() }
                        scope.launch(Dispatchers.IO) {
                            runCatching { projectManager.saveArtwork(projectId, base) }
                            runCatching { projectManager.saveTexts(projectId, textsJson) }
                            runCatching { base.recycle() }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runCatching { base.recycle() }
                    }
                }
            }
        }
    }

    LaunchedEffect(initialBitmap) {
        initialBitmap?.let { bmp ->
            // Bitmap 46MB di thread background agar buka kanvas tidak freeze.
            withContext(Dispatchers.Default) {
                layerManager.getActiveLayer()?.let { active ->
                    // Import TANPA resize: 1:1 no-scale agar 720x16000 tidak diubah.
                    // (Kanvas sudah = ukuran asli dari GalleryScreen.)
                    ImageImport.drawBitmapCenterNoScale(active.getPersistentBitmap(), bmp)
                    active.tileMap.importFromBitmap(active.getPersistentBitmap())
                    active.markDirty()
                }
            }
            refreshComposite()
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
    // Dialog export: pilih format + atur kualitas, ada progres & hasil.
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var exportQuality by remember { mutableFloatStateOf(90f) }
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var showLassoMenu by remember { mutableStateOf(false) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Inpaint PatchMatch brush: mask akumulasi selama stroke
    var inpaintMask by remember { mutableStateOf<Bitmap?>(null) }
    var inpaintMaskCanvas by remember { mutableStateOf<android.graphics.Canvas?>(null) }
    var inpaintDirty by remember { mutableStateOf<RectF?>(null) }
    // Mask heal 720x16000 = 46MB. Alokasi dilindungi OOM (return null bila
    // memori mepet) agar sapuan heal tidak crash. Dipakai ulang selama stroke,
    // dibebaskan via recycleInpaintMask() setelah commit agar tidak resident.
    fun ensureInpaintMask(): Pair<Bitmap, android.graphics.Canvas>? {
        var bmp = inpaintMask
        var cv = inpaintMaskCanvas
        if (bmp == null || bmp.isRecycled || bmp.width != canvasWidth || bmp.height != canvasHeight) {
            // Bebaskan sisa lama dulu sebelum alokasi jumbo.
            runCatching { bmp?.takeIf { !it.isRecycled }?.recycle() }
            try {
                bmp = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                inpaintMask = null
                inpaintMaskCanvas = null
                inpaintDirty = null
                return null
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
            cv = android.graphics.Canvas(bmp!!)
            inpaintMask = bmp
            inpaintMaskCanvas = cv
            inpaintDirty = null
        }
        val b = bmp ?: return null
        val c = cv ?: return null
        return b to c
    }
    fun clearInpaintMask() {
        inpaintMask?.let { bm ->
            if (!bm.isRecycled) {
                runCatching {
                    android.graphics.Canvas(bm).drawColor(AndroidColor.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                }
            }
        }
        inpaintDirty = null
    }
    // Bebaskan 46MB mask setelah commit heal (anti-OOM crash di 720x16000).
    // Alokasi berikutnya dibuat ulang di ensureInpaintMask().
    fun recycleInpaintMask() {
        runCatching { inpaintMask?.takeIf { !it.isRecycled }?.recycle() }
        inpaintMask = null
        inpaintMaskCanvas = null
        inpaintDirty = null
    }

    var showQuickSlider by remember { mutableStateOf(true) }

    var showMLInpaintDialog by remember { mutableStateOf(false) }
    var detectedTextRegions by remember { mutableStateOf<List<DetectedTextRegion>>(emptyList()) }
    var selectedMaskType by remember { mutableStateOf(MLMaskType.MASK_KOTAK) }
    var makeEditableText by remember { mutableStateOf(true) }
    var mlScripts by remember { mutableStateOf(setOf(MLScript.LATIN, MLScript.CHINESE, MLScript.JAPANESE, MLScript.KOREAN)) }
    var textEngine by remember { mutableStateOf(TextEngine.ML_KIT) }
    var mlDetecting by remember { mutableStateOf(false) }
    var fontList by remember { mutableStateOf(fontRegistry.fonts()) }

    // Konfirmasi keluar: cegah ketekan Back tak sengaja langsung terlempar.
    // (Flag showExitDialog/isSavingExit/discardOnExit dideklarasikan di atas.)
    fun hasUnsavedChanges(): Boolean = dirtyVersion != lastSavedVersion
    fun requestExit() {
        // Panel bawah didahulukan (tutup dulu, bukan keluar).
        if (showTextEditor || showLayersPanel || showBrushSettings) {
            showTextEditor = false
            showLayersPanel = false
            showBrushSettings = false
            return
        }
        flushPanelRefresh()
        if (hasUnsavedChanges()) {
            showExitDialog = true
        } else {
            onBackToGallery()
        }
    }
    fun doExitWithoutSaving() {
        discardOnExit = true
        showExitDialog = false
        onBackToGallery()
    }
    fun doSaveAndExit() {
        if (isSavingExit) return
        isSavingExit = true
        scope.launch {
            // Simpan basis + teks + preview, TUNGGU selesai baru keluar
            // agar tidak hilang bila proses mati tepat setelah navigasi.
            flushPanelRefresh()
            runCatching { persistProjectNow(withPreview = true) }
            isSavingExit = false
            showExitDialog = false
            onBackToGallery()
        }
    }

    BackHandler(enabled = !isSavingExit) {
        if (showExitDialog) {
            showExitDialog = false
        } else {
            requestExit()
        }
    }

    // Restore teks editable (format baru). Basis gambar tetap via initialBitmap.
    // File teks absen = project lawas (teks bakar) → jalur legacy apa adanya.
    LaunchedEffect(projectId) {
        val raw = withContext(Dispatchers.IO) {
            runCatching { projectManager.loadTexts(projectId) }.getOrNull()
        }
        if (!raw.isNullOrBlank()) {
            val resolver: (String) -> android.graphics.Typeface = { name ->
                fontList.find { it.first == name }?.second
                    ?: android.graphics.Typeface.DEFAULT_BOLD
            }
            val restored = parseLayers(raw, resolver)
            if (restored.topFirst.isNotEmpty()) {
                with(TextLayerStore) {
                    layerManager.restoreTextLayers(restored) { box ->
                        selectedTextBox = box
                    }
                }
                refreshComposite()
            }
        }
    }

    // Style preset untuk pencocokan prefix otomatis ala TypeR.
    val textStyleManager = remember { TextStyleManager(context) }
    var lastAutoPrefix by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Style Rules: prefix script -> style preset + hapus awalan saat render.
    // Terhubung langsung ke Style (TextStyleManager) dan Script (runScript).
    val styleRuleManager = remember { StyleRuleManager(context) }
    var styleRules by remember { mutableStateOf(styleRuleManager.list()) }
    var showStyleRules by remember { mutableStateOf(false) }
    var newRulePrefix by remember { mutableStateOf("") }
    var newRuleStyleId by remember { mutableStateOf("") }
    var newRuleStrip by remember { mutableStateOf(true) }
    var stylePresetsTick by remember { mutableIntStateOf(0) }
    // Dibaca ulang tiap dialog dibuka agar style yang baru disimpan ikut muncul.
    fun stylePresets(): List<com.grooxtyper.app.model.TextStylePreset> {
        stylePresetsTick.let { }
        return textStyleManager.list()
    }

    // Mode multi-bubble: antrean baris teks untuk ditaruh berurutan.
    var showMultiBubbleDialog by remember { mutableStateOf(false) }
    var multiBubbleDraft by remember { mutableStateOf("") }
    var multiBubbleLines by remember { mutableStateOf(listOf<String>()) }
    var multiBubbleIndex by remember { mutableIntStateOf(0) }
    var multiBubbleTemplate by remember { mutableStateOf<TextBox?>(null) }
    fun isMultiBubbleActive() = multiBubbleLines.isNotEmpty() && multiBubbleIndex < multiBubbleLines.size

    // === Fitur Script: kombo seleksi + bubble ===
    // User import/ketik script -> muncul kolom baris -> jalankan ke seleksi/bubble
    // urutan manga (atas->bawah, kanan->kiri), tanda sudah terpakai + bisa reset.
    var showScriptPanel by remember { mutableStateOf(false) }
    var showScriptEditor by remember { mutableStateOf(false) }
    var scriptDraft by remember { mutableStateOf("") }
    var scriptEntries by remember { mutableStateOf(listOf<ScriptEntry>()) }
    fun unusedScriptEntries(): List<ScriptEntry> = scriptEntries.filter { !it.used }
    fun resetScriptUsage() { scriptEntries = scriptEntries.map { it.copy(used = false) } }

    // Bubble detector: model ONNX (YOLOv11n-seg) yang bisa dipilih user.
    // Inferensi on-device via ONNX Runtime; heuristik hanya fallback.
    val bubbleDetector = remember { BubbleDetector() }
    var detectedBubbles by remember { mutableStateOf(listOf<com.grooxtyper.app.ml.DetectedBubble>()) }
    var showBubbleDialog by remember { mutableStateOf(false) }
    var bubbleModel by remember { mutableStateOf(BubbleModel.BEST1_ONNX) }
    var bubbleDetecting by remember { mutableStateOf(false) }
    var showBubbleOverlay by remember { mutableStateOf(true) }
    // Bila true, ketuk bubble di kanvas menghapusnya (bukan seleksi).
    var bubbleEraseMode by remember { mutableStateOf(false) }
    var lassoPath by remember { mutableStateOf<Path?>(null) }
    // Drag kotak seleksi (tool SELECT_BOX): titik awal/akhir kanvas.
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxCurrent by remember { mutableStateOf<Offset?>(null) }

    fun runBubbleDetection() {
        scope.launch {
            val snap = runCatching {
                compositeBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull() ?: return@launch
            bubbleDetecting = true
            // Jalankan deteksi bubble heuristik on-device.
            val found = bubbleDetector.detect(snap, bubbleModel, context.applicationContext)
            runCatching { snap.recycle() }
            detectedBubbles = found
            bubbleDetecting = false
            showBubbleOverlay = true
            refreshComposite()
        }
    }

    /** Hapus bubble hasil deteksi berdasar indeks (edit manual). */
    fun removeBubbleAt(index: Int) {
        if (index < 0 || index >= detectedBubbles.size) return
        detectedBubbles = detectedBubbles.toMutableList().also { it.removeAt(index) }
        refreshComposite()
    }

    /** Inti tambah bubble dari [rect] (dijepit ke kanvas). True bila jadi. */
    fun addBubbleRect(rect: RectF): Boolean {
        val left = minOf(rect.left, rect.right).coerceIn(0f, canvasWidth.toFloat())
        val top = minOf(rect.top, rect.bottom).coerceIn(0f, canvasHeight.toFloat())
        val right = maxOf(rect.left, rect.right).coerceIn(0f, canvasWidth.toFloat())
        val bottom = maxOf(rect.top, rect.bottom).coerceIn(0f, canvasHeight.toFloat())
        if (right - left < 4f || bottom - top < 4f) return false
        detectedBubbles = detectedBubbles + com.grooxtyper.app.ml.DetectedBubble(
            RectF(left, top, right, bottom), 1f
        )
        return true
    }

    /** Tambah bubble manual dari kotak [rect] (dijepit ke kanvas). */
    fun addBubbleFromRect(rect: RectF) {
        if (addBubbleRect(rect)) {
            showBubbleOverlay = true
            refreshComposite()
        }
    }

    /**
     * Tambah SATU bubble per AREA seleksi: 3 area terpisah → 3 bubble
     * (bukan satu bubble gabungan dari bounds union). Kembalikan jumlah
     * bubble yang ditambahkan.
     */
    fun addBubblesFromSelection(): Int {
        val list = selectionEngine.regionBoundsList()
        if (list.isEmpty()) {
            // Fallback: seleksi lawas tanpa region (mis. hasil invert).
            val bounds = selectionEngine.selectionBounds() ?: return 0
            return if (addBubbleRect(bounds)) 1 else 0
        }
        var n = 0
        for (b in list) {
            if (addBubbleRect(b)) n++
        }
        if (n > 0) {
            showBubbleOverlay = true
            refreshComposite()
        }
        return n
    }

    /** Tambah bubble dari seleksi aktif (kotak/lasso/oval). */
    fun addBubbleFromSelection(): Boolean = addBubblesFromSelection() > 0

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

    /**
     * Terapkan Style Rule ke [box]: cocokkan awalan persis (case-sensitive,
     * setelah trimStart) dengan rule terpanjang dulu agar '() : ' tidak
     * kalah oleh '(' . Bila cocok: pakai style preset + hapus awalan bila
     * stripPrefix true. True bila ada rule yang diterapkan.
     */
    fun applyStyleRulesTo(box: TextBox): Boolean {
        val trimmed = box.text.trimStart()
        if (trimmed.isEmpty() || styleRules.isEmpty()) return false
        val rule = styleRules
            .filter { it.prefix.isNotEmpty() && trimmed.startsWith(it.prefix) }
            .maxByOrNull { it.prefix.length } ?: return false
        val preset = textStyleManager.list().find { it.id == rule.styleId } ?: return false
        val tf = fontList.find { it.first == preset.fontName }?.second
        preset.applyTo(box, tf)
        if (rule.stripPrefix) {
            box.text = trimmed.removePrefix(rule.prefix).trimStart()
            if (box.text.isEmpty()) box.text = trimmed
        }
        lastAutoPrefix = box.id to preset.id
        return true
    }

    /** Terapkan rules dulu, fallback ke prefix lama "[SFX]". */
    fun applyAllStylesTo(box: TextBox): Boolean {
        if (applyStyleRulesTo(box)) return true
        return applyPrefixStyleTo(box)
    }

    var ppocrError by remember { mutableStateOf<String?>(null) }

    fun runMLDetection() {
        scope.launch {
            val active = layerManager.getActiveLayer()
            if (active != null) {
                mlDetecting = true
                ppocrError = null
                val wantPpocr = textEngine == TextEngine.PPOCR_V6
                val ppocrReady = ppocrDetector.isAvailable()
                android.util.Log.i("RunML", "wantPpocr=$wantPpocr ready=$ppocrReady status=${ppocrDetector.modelStatus()} dump=${ppocrDetector.debugAssetDump()}")
                detectedTextRegions = try {
                    if (wantPpocr) {
                        if (ppocrReady) {
                            val r = ppocrDetector.detect(active.getBitmap(), mlScripts)
                            if (r.isEmpty()) {
                                android.util.Log.w("RunML", "PPOCR empty -> fallback MLKit")
                                ppocrError = "PP-OCR tidak menemukan teks (0 box) — fallback ke ML Kit. Cek logcat PpocrDetector."
                                mlTextDetector.detectTextRegions(active.getBitmap(), mlScripts)
                            } else r
                        } else {
                            ppocrError = "Model PP-OCR belum ada (${ppocrDetector.modelStatus()}) — pakai ML Kit. Build CI harus hijau."
                            android.util.Log.w("RunML", "PPOCR not available, fallback MLKit")
                            mlTextDetector.detectTextRegions(active.getBitmap(), mlScripts)
                        }
                    } else {
                        mlTextDetector.detectTextRegions(active.getBitmap(), mlScripts)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("RunML", "detect exception", e)
                    ppocrError = "Error deteksi: ${e.message}"
                    emptyList()
                } catch (e: OutOfMemoryError) {
                    ppocrError = "OOM deteksi — coba gambar lebih kecil / tutup app lain"
                    emptyList()
                }
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
            applyAllStylesTo(box)
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

    /** Jalankan script: render baris belum terpakai ke bubble/seleksi urut manga. */
    fun runScript(): Int {
        val unused = unusedScriptEntries()
        if (unused.isEmpty()) return 0
        val hasBubbles = detectedBubbles.isNotEmpty()
        val hasSelection = selectionEngine.hasSelection
        if (!hasBubbles && !hasSelection) return 0

        var createdCount = 0
        val template = selectedTextBox?.copy()
        var last: TextBox? = null
        var lastLayerId = ""

        if (hasBubbles) {
            val ordered = readingOrder(detectedBubbles)
            val n = minOf(unused.size, ordered.size)
            if (n <= 0) return 0
            for (i in 0 until n) {
                val b = ordered[i].boundingBox
                val inset = RectF(
                    b.left + b.width() * 0.12f,
                    b.top + b.height() * 0.12f,
                    b.right - b.width() * 0.12f,
                    b.bottom - b.height() * 0.12f
                )
                val box = TextBox(
                    text = unused[i].text,
                    position = Offset(inset.centerX(), inset.centerY()),
                    color = brushEngine.color
                )
                template?.let { box.applyStyleFrom(it) }
                applyAllStylesTo(box)
                box.fitToRect(inset)
                val created = layerManager.addTextLayer(box)
                undoRedoManager.pushLayerAdd(created.id)
                // Tandai terpakai berdasar id
                val srcIdx = scriptEntries.indexOfFirst { it.id == unused[i].id }
                if (srcIdx >= 0) scriptEntries[srcIdx].used = true
                last = box
                lastLayerId = created.id
                createdCount++
            }
            // Trigger recompose untuk daftar kolom
            scriptEntries = scriptEntries.toList()
            last?.let {
                selectedTextBox = it
                layerManager.activeLayerId = lastLayerId
                activeTool = ActiveTool.TEXT
            }
            refreshComposite()
        } else if (hasSelection) {
            val bounds = selectionEngine.selectionBounds() ?: return 0
            val entry = unused.first()
            val box = TextBox(
                text = entry.text,
                position = Offset(bounds.centerX(), bounds.centerY()),
                color = brushEngine.color
            )
            template?.let { box.applyStyleFrom(it) }
            applyAllStylesTo(box)
            box.fitToRect(bounds)
            val created = layerManager.addTextLayer(box)
            undoRedoManager.pushLayerAdd(created.id)
            val srcIdx = scriptEntries.indexOfFirst { it.id == entry.id }
            if (srcIdx >= 0) scriptEntries[srcIdx].used = true
            scriptEntries = scriptEntries.toList()
            selectedTextBox = box
            layerManager.activeLayerId = created.id
            activeTool = ActiveTool.TEXT
            showTextEditor = true
            refreshComposite()
            createdCount = 1
        }
        return createdCount
    }

    /** Layer id untuk snapshot undo teks (undo mencari berdasar layer id). */
    fun textLayerIdOf(box: TextBox): String =
        layerManager.findTextLayerByBoxId(box.id)?.id ?: box.id

    fun flattenSelectedText() {
        val box = selectedTextBox ?: return
        val textLayer = layerManager.findTextLayerByBoxId(box.id)
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
        layerManager.findTextLayerByBoxId(box.id)
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
        applyAllStylesTo(box)
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
        uri?.let { u ->
            try {
                // Pakai nama file asli (bukan font_<timestamp>) agar daftar
                // font menampilkan nama yang sesuai.
                val displayName: String? = runCatching {
                    context.contentResolver.query(u, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )
                        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                    }
                }.getOrNull()
                val rawBase = (displayName ?: "font_${System.currentTimeMillis()}.ttf")
                    .substringAfterLast('/').substringAfterLast('\\').trim()
                val withExt = if (rawBase.contains('.')) rawBase else "$rawBase.ttf"
                val safe = withExt.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .take(64).ifBlank { "font_${System.currentTimeMillis()}.ttf" }
                val customDir = java.io.File(context.filesDir, "custom_fonts")
                val fileName = if (java.io.File(customDir, safe).exists()) {
                    "${System.currentTimeMillis()}_$safe"
                } else safe
                context.contentResolver.openInputStream(u)?.use { stream ->
                    val tf = fontRegistry.import(stream, fileName)
                    if (tf != null) {
                        fontList = fontRegistry.fonts()
                        selectedTextBox?.let { box ->
                            box.typeface = tf
                            box.fontName = fileName.substringBeforeLast('.')
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
                    // Import TANPA resize di background (46MB) agar UI tidak freeze.
                    withContext(Dispatchers.Default) {
                        // Import TANPA resize: 1:1 no-scale (kelebihan di-crop,
                        // kekurangan transparan) agar tidak mengubah piksel asli.
                        ImageImport.drawBitmapCenterNoScale(active.getPersistentBitmap(), loaded)
                        active.tileMap.importFromBitmap(active.getPersistentBitmap())
                        active.markDirty()
                    }
                    // Referensi cukup versi kecil agar hemat memori.
                    referenceBitmap = downscaleForReference(loaded)
                    refreshComposite()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val scriptImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val raw = stream.bufferedReader().readText()
                    val parsed = parseScriptRaw(raw)
                    if (parsed.isNotEmpty()) {
                        scriptEntries = parsed
                        scriptDraft = raw
                        showScriptPanel = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    /**
     * "To Canvas": kembalikan view agar seluruh kanvas pas & terpusat di
     * layar (dipakai saat user tak sengaja terlempar / kanvas hilang).
     * Dengan pivot 0.5/0.5 & rotasi 0: layar = pivot + offset +
     * scale*(kanvas - pivot) → offset = scale*(viewport - kanvas)/2.
     */
    fun canvasToScreen() {
        val vw = viewportSize.width.toFloat().coerceAtLeast(1f)
        val vh = viewportSize.height.toFloat().coerceAtLeast(1f)
        val fit = (minOf(vw / canvasWidth.toFloat(), vh / canvasHeight.toFloat()) * 0.92f)
            .coerceIn(0.05f, 10f)
        viewState.pivotFracX = 0.5f
        viewState.pivotFracY = 0.5f
        viewState.rotation = 0f
        viewState.rawRotation = 0f
        viewState.scale = fit
        viewState.offsetX = fit * (vw - canvasWidth) / 2f
        viewState.offsetY = fit * (vh - canvasHeight) / 2f
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
        // Grid pada area void (latar di luar kanvas, sebelumnya hitam polos):
        // membantu orientasi spasial sehingga posisi kanvas selalu terbaca.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 32.dp.toPx()
            val gridColor = Color(0xFF3A3A41)
            var gx = 0f
            while (gx <= size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy <= size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                gy += gridStep
            }
        }

        // Canvas with gestures
        // NOTE: kunci pointerInput disengaja minimal agar pinch-zoom tidak
        // me-restart gesture di tengah jalan (viewState.scale/offset/rotation
        // berubah kontinu saat pinch).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, selectedTextBox, viewportSize, bubbleEraseMode) {
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
                                boxStart = null
                                boxCurrent = null
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
                                                current?.let { undoRedoManager.pushTextBox(textLayerIdOf(it), it.copy()) }
                                            } else {
                                                val hit = layerManager.visibleTextLayers()
                                                    .findLast { it.box.hitTest(touchCanvasPos) }
                                                if (hit != null) {
                                                    selectedTextBox = hit.box
                                                    layerManager.activeLayerId = hit.id
                                                    textHandleMode = TextHandle.BODY
                                                    undoRedoManager.pushTextBox(hit.id, hit.box.copy())
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
                                                    TextHandle.WIDTH_LEFT, TextHandle.WIDTH_RIGHT -> {
                                                        // Paragraph-resize ala PS/IbisPaint: sempitkan
                                                        // tepi kiri/kanan agar teks re-wrap berbaris.
                                                        box.dragWidthHandle(
                                                            textHandleMode,
                                                            lastCanvasPoint!!,
                                                            touchCanvasPos
                                                        )
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
                                            // Ketuk bubble terdeteksi: mode hapus → buang bubble,
                                            // mode normal → jadikan seleksi oval. Bila kosong
                                            // mulai gambar lasso bebas.
                                            val hitIdx = detectedBubbles
                                                .mapIndexedNotNull { idx, b ->
                                                    if (b.boundingBox.contains(touchCanvasPos.x, touchCanvasPos.y)) idx to (b.boundingBox.width() * b.boundingBox.height()) else null
                                                }
                                                .minByOrNull { it.second }?.first
                                            if (hitIdx != null) {
                                                if (bubbleEraseMode) {
                                                    removeBubbleAt(hitIdx)
                                                } else {
                                                    selectionEngine.selectOval(detectedBubbles[hitIdx].boundingBox)
                                                    lassoPath = null
                                                    refreshComposite()
                                                }
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
                                    } else if (activeTool == ActiveTool.SELECT_BOX) {
                                        // Kotak seleksi: drag untuk bentuk persegi.
                                        // Ketuk bubble saat mode hapus → hapus bubble itu.
                                        if (boxStart == null) {
                                            val hitIdx = detectedBubbles
                                                .mapIndexedNotNull { idx, b ->
                                                    if (b.boundingBox.contains(touchCanvasPos.x, touchCanvasPos.y)) idx to (b.boundingBox.width() * b.boundingBox.height()) else null
                                                }
                                                .minByOrNull { it.second }?.first
                                            if (bubbleEraseMode && hitIdx != null) {
                                                removeBubbleAt(hitIdx)
                                            } else {
                                                boxStart = touchCanvasPos
                                                boxCurrent = touchCanvasPos
                                            }
                                        } else {
                                            boxCurrent = touchCanvasPos
                                        }
                                        refreshComposite()
                                    } else if (activeTool == ActiveTool.INPAINT) {
                                        if (lastCanvasPoint == null) {
                                            val activeLayer = layerManager.ensureDrawingLayer()
                                            strokeLayer = activeLayer
                                            pressId++
                                            pressStartScreen = change.position
                                            pressMoved = false
                                            undoRedoManager.saveSnapshot(activeLayer)
                                            brushEngine.beginStroke()
                                            val maskPair = ensureInpaintMask()
                                            if (maskPair != null) {
                                                val (bmp, cv) = maskPair
                                                val p = android.graphics.Paint().apply {
                                                    isAntiAlias = true; style = android.graphics.Paint.Style.FILL
                                                    color = AndroidColor.WHITE
                                                }
                                                cv.drawCircle(touchCanvasPos.x, touchCanvasPos.y, brushEngine.size / 2f, p)
                                                val r = brushEngine.size
                                                val rect = RectF(touchCanvasPos.x - r, touchCanvasPos.y - r, touchCanvasPos.x + r, touchCanvasPos.y + r)
                                                inpaintDirty = if (inpaintDirty == null) rect else RectF(minOf(inpaintDirty!!.left, rect.left), minOf(inpaintDirty!!.top, rect.top), maxOf(inpaintDirty!!.right, rect.right), maxOf(inpaintDirty!!.bottom, rect.bottom))
                                                refreshCanvasState++
                                            }
                                        } else {
                                            val maskPair = ensureInpaintMask()
                                            if (maskPair != null) {
                                                val (bmp, cv) = maskPair
                                                val p = android.graphics.Paint().apply {
                                                    isAntiAlias = true; style = android.graphics.Paint.Style.STROKE
                                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                                    strokeJoin = android.graphics.Paint.Join.ROUND
                                                    strokeWidth = brushEngine.size; color = AndroidColor.WHITE
                                                }
                                                val prev = lastCanvasPoint!!
                                                cv.drawLine(prev.x, prev.y, touchCanvasPos.x, touchCanvasPos.y, p)
                                                val r = brushEngine.size
                                                val rect = RectF(minOf(prev.x, touchCanvasPos.x) - r, minOf(prev.y, touchCanvasPos.y) - r, maxOf(prev.x, touchCanvasPos.x) + r, maxOf(prev.y, touchCanvasPos.y) + r)
                                                inpaintDirty = if (inpaintDirty == null) rect else RectF(minOf(inpaintDirty!!.left, rect.left), minOf(inpaintDirty!!.top, rect.top), maxOf(inpaintDirty!!.right, rect.right), maxOf(inpaintDirty!!.bottom, rect.bottom))
                                                if ((change.position - pressStartScreen).getDistance() > 16f) pressMoved = true
                                                refreshCanvasState++
                                            } else {
                                                if ((change.position - pressStartScreen).getDistance() > 16f) pressMoved = true
                                            }
                                        }
                                    } else if (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER) {
                                        // Heal brush via tool BRUSH (tipe HEAL_PATCH): alihkan ke
                                        // akumulasi mask inpaint agar berfungsi di kedua tool.
                                        if (brushEngine.brushType == BrushType.HEAL_PATCH) {
                                            if (lastCanvasPoint == null) {
                                                val activeLayer = layerManager.ensureDrawingLayer()
                                                strokeLayer = activeLayer
                                                pressId++
                                                pressStartScreen = change.position
                                                pressMoved = false
                                                undoRedoManager.saveSnapshot(activeLayer)
                                                brushEngine.beginStroke()
                                                val maskPair = ensureInpaintMask()
                                                if (maskPair != null) {
                                                    val (bmp, cv) = maskPair
                                                    val p = android.graphics.Paint().apply {
                                                        isAntiAlias = true; style = android.graphics.Paint.Style.FILL
                                                        color = AndroidColor.WHITE
                                                    }
                                                    cv.drawCircle(touchCanvasPos.x, touchCanvasPos.y, brushEngine.size / 2f, p)
                                                    val r = brushEngine.size
                                                    val rect = RectF(touchCanvasPos.x - r, touchCanvasPos.y - r, touchCanvasPos.x + r, touchCanvasPos.y + r)
                                                    inpaintDirty = if (inpaintDirty == null) rect else RectF(minOf(inpaintDirty!!.left, rect.left), minOf(inpaintDirty!!.top, rect.top), maxOf(inpaintDirty!!.right, rect.right), maxOf(inpaintDirty!!.bottom, rect.bottom))
                                                    refreshCanvasState++
                                                }
                                            } else {
                                                val maskPair = ensureInpaintMask()
                                                if (maskPair != null) {
                                                    val (bmp, cv) = maskPair
                                                    val p = android.graphics.Paint().apply {
                                                        isAntiAlias = true; style = android.graphics.Paint.Style.STROKE
                                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                                        strokeWidth = brushEngine.size; color = AndroidColor.WHITE
                                                    }
                                                    val prev = lastCanvasPoint!!
                                                    cv.drawLine(prev.x, prev.y, touchCanvasPos.x, touchCanvasPos.y, p)
                                                    val r = brushEngine.size
                                                    val rect = RectF(minOf(prev.x, touchCanvasPos.x) - r, minOf(prev.y, touchCanvasPos.y) - r, maxOf(prev.x, touchCanvasPos.x) + r, maxOf(prev.y, touchCanvasPos.y) + r)
                                                    inpaintDirty = if (inpaintDirty == null) rect else RectF(minOf(inpaintDirty!!.left, rect.left), minOf(inpaintDirty!!.top, rect.top), maxOf(inpaintDirty!!.right, rect.right), maxOf(inpaintDirty!!.bottom, rect.bottom))
                                                    if ((change.position - pressStartScreen).getDistance() > 16f) pressMoved = true
                                                    refreshCanvasState++
                                                }
                                            }
                                        } else if (colorPickActive) {
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
                                            // Jalur cepat: blit dot kecil, bukan render 46MB.
                                            // False (mis. menyentuh teks) → render penuh.
                                            if (!blitLayerToComposite(activeLayer, null, touchCanvasPos)) {
                                                refreshComposite()
                                            }
                                        } else {
                                            // Goresan mulus di kanvas 720x16000: bila lompatan
                                            // antar-event besar (frame berat → event move terjeda),
                                            // interpolasi titik perantara di RUANG KANVAS per
                                            // ~setengah diameter brush agar stroke tidak
                                            // patah-patah. Stabil di semua versi Compose
                                            // (tanpa API historis eksperimental).
                                            val target = strokeLayer ?: layerManager.ensureDrawingLayer()
                                            val startPt = lastCanvasPoint ?: touchCanvasPos
                                            val jump = (touchCanvasPos - startPt).getDistance()
                                            val stepLen = (brushEngine.size * 0.5f).coerceIn(4f, 24f)
                                            val movePts = ArrayList<Offset>()
                                            // Huge 720x16000: batasi interpolasi luar 24 titik
                                            // (bukan 64) + inner 32 steps → cegah 2000+ drawLine
                                            // per event yang bikin delay + GC thrash.
                                            val isHugeCanvas = canvasWidth.toLong() * canvasHeight > 4_000_000L
                                            val maxInterp = if (isHugeCanvas) 24 else 64
                                            if (jump > stepLen * 2f) {
                                                val segments = (jump / stepLen).toInt().coerceAtMost(maxInterp)
                                                for (si in 1..segments) {
                                                    val t = si / (segments + 1f)
                                                    movePts.add(
                                                        Offset(
                                                            startPt.x + (touchCanvasPos.x - startPt.x) * t,
                                                            startPt.y + (touchCanvasPos.y - startPt.y) * t
                                                        )
                                                    )
                                                }
                                            }
                                            movePts.add(touchCanvasPos)
                                            if (strokeLayer == null) strokeLayer = target
                                            // Viewport culling brush di kanvas jangkung (adaptasi
                                            // Vasilias viewportRect): hitung jendela terlihat
                                            // sekali per event, oper ke engine agar segmen
                                            // off-screen dilewati tanpa raster.
                                            val brushVisible = if (isHugeCanvas) {
                                                runCatching {
                                                    BrushHugeGuide.visibleRect(
                                                        viewportSize.width.toFloat().coerceAtLeast(1f),
                                                        viewportSize.height.toFloat().coerceAtLeast(1f),
                                                        viewState.scale,
                                                        viewState.offsetX,
                                                        viewState.offsetY
                                                    )
                                                }.getOrNull()
                                            } else null
                                            var needFullRefresh = false
                                            var didBlit = false
                                            for (pt in movePts) {
                                                // pt sudah di ruang kanvas; cek gerakan layar pakai titik event.
                                                if ((change.position - pressStartScreen).getDistance() > 16f) {
                                                    pressMoved = true
                                                }
                                                val cpt = pt
                                                val prev = lastCanvasPoint ?: continue
                                                val dist = (cpt - prev).getDistance()
                                                strokeLength += dist
                                                val progress = if (strokeLength > 0f) (strokeLength / 500f).coerceIn(0f, 1f) else 0f
                                                brushEngine.strokeSegmentOnLayer(target, prev, cpt, progress, brushVisible)
                                                // Batch: tunda recompose per titik (deferRefresh)
                                                // agar 1 touch event = 1 recompose, bukan N.
                                                if (!blitLayerToComposite(target, prev, cpt, deferRefresh = true)) {
                                                    needFullRefresh = true
                                                } else {
                                                    didBlit = true
                                                }
                                                lastCanvasPoint = cpt
                                            }
                                            if (needFullRefresh) {
                                                refreshComposite()
                                            } else if (didBlit) {
                                                refreshCanvasLight()
                                            }
                                        }
                                    }
                                    lastCanvasPoint = touchCanvasPos
                                    change.consume()
                                    } // else twoFingerActive
                                } else {
                                    val hadStroke = strokeLayer != null
                                    val wasBrush = activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER
                                    val wasInpaint = activeTool == ActiveTool.INPAINT
                                    val wasHealBrush = wasBrush && brushEngine.brushType == BrushType.HEAL_PATCH
                                    val wasHeal = wasInpaint || wasHealBrush
                                    strokeLayer?.let { brushEngine.syncTiles(it) }
                                    strokeLayer = null
                                    brushEngine.endStroke()
                                    // Commit heal PatchMatch jika ada mask (crop dirty saja).
                                    if (wasHeal && inpaintMask != null && inpaintDirty != null) {
                                        val maskToUse = inpaintMask
                                        val dirty = inpaintDirty
                                        val targetLayer = layerManager.getActiveLayer()
                                        if (maskToUse != null && dirty != null && targetLayer != null) {
                                            scope.launch(Dispatchers.Default) {
                                                try {
                                                    val l = dirty.left.toInt().coerceIn(0, canvasWidth)
                                                    val t = dirty.top.toInt().coerceIn(0, canvasHeight)
                                                    val r = dirty.right.toInt().coerceIn(0, canvasWidth)
                                                    val b = dirty.bottom.toInt().coerceIn(0, canvasHeight)
                                                    if (r > l && b > t) {
                                                        inpaintingManager.inpaintHealDirty(targetLayer.getPersistentBitmap(), maskToUse, dirty)
                                                        targetLayer.markDirty()
                                                        withContext(Dispatchers.Main) { refreshComposite() }
                                                    }
                                                } catch (e: Exception) { e.printStackTrace() } catch (e: OutOfMemoryError) { e.printStackTrace() }
                                                finally {
                                                    withContext(Dispatchers.Main) {
                                                        recycleInpaintMask()
                                                        refreshCanvasState++
                                                    }
                                                }
                                            }
                                        } else {
                                            clearInpaintMask()
                                        }
                                    }
                                    colorPickActive = false
                                    pressId++
                                    twoFingerActive = false
                                    // Kunci sketsa lasso bebas menjadi SATU area baru (multi).
                                    // Sketsa super-kecil = tap → hapus area yang diketuk.
                                    lassoPath?.let { sketch ->
                                        val lb = RectF()
                                        sketch.computeBounds(lb, true)
                                        val lp = lastCanvasPoint
                                        if (lb.width() < 8f && lb.height() < 8f && lp != null) {
                                            selectionEngine.removeRegionAt(lp.x, lp.y)
                                        } else {
                                            selectionEngine.setLassoPath(sketch)
                                        }
                                        lassoPath = null
                                    }
                                    // Kunci drag kotak menjadi SATU area persegi baru (multi).
                                    // Tap tanpa drag = hapus area yang diketuk.
                                    val s = boxStart
                                    val e = boxCurrent
                                    if (s != null && e != null) {
                                        val rect = RectF(
                                            minOf(s.x, e.x), minOf(s.y, e.y),
                                            maxOf(s.x, e.x), maxOf(s.y, e.y)
                                        )
                                        if (rect.width() >= 8f && rect.height() >= 8f) {
                                            selectionEngine.selectRect(rect)
                                        } else {
                                            selectionEngine.removeRegionAt(s.x, s.y)
                                        }
                                        boxStart = null
                                        boxCurrent = null
                                    }
                                    // Satu render penuh per stroke menutup jalur cepat
                                    // inkremental (menjamin konsisten bila ada teks/layer).
                                    // Heal brush menunggu commit async (recycle mask) agar
                                    // tidak render sebelum hasil PatchMatch kembali.
                                    if (hadStroke && wasBrush && !wasHealBrush) refreshComposite()
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
                                lassoPath?.let { sketch ->
                                    val lb = RectF()
                                    sketch.computeBounds(lb, true)
                                    val lp = lastCanvasPoint
                                    if (lb.width() < 8f && lb.height() < 8f && lp != null) {
                                        selectionEngine.removeRegionAt(lp.x, lp.y)
                                    } else {
                                        selectionEngine.setLassoPath(sketch)
                                    }
                                    lassoPath = null
                                }
                                boxStart = null
                                boxCurrent = null
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
                // Viewport culling: saat zoom-in strip jangkung, gambar HANYA
                // regio terlihat (mis. 720x1000) bukan 11,5MP penuh per frame.
                // Rotasi ≠ 0 → fallback full (matematika pivot-rotasi kompleks).
                val nativeMain = drawContext.canvas.nativeCanvas
                if (viewState.rotation == 0f) {
                    val vw = viewportSize.width.toFloat().coerceAtLeast(1f)
                    val vh = viewportSize.height.toFloat().coerceAtLeast(1f)
                    val pivX = viewState.pivotFracX * vw
                    val pivY = viewState.pivotFracY * vh
                    val sc = viewState.scale.coerceAtLeast(0.05f)
                    val xa = ((0f - pivX - viewState.offsetX) / sc) + pivX
                    val ya = ((0f - pivY - viewState.offsetY) / sc) + pivY
                    val xb = ((vw - pivX - viewState.offsetX) / sc) + pivX
                    val yb = ((vh - pivY - viewState.offsetY) / sc) + pivY
                    val visL = minOf(xa, xb).coerceIn(0f, canvasWidth.toFloat())
                    val visT = minOf(ya, yb).coerceIn(0f, canvasHeight.toFloat())
                    val visR = maxOf(xa, xb).coerceIn(0f, canvasWidth.toFloat())
                    val visB = maxOf(ya, yb).coerceIn(0f, canvasHeight.toFloat())
                    if (visR - visL > 1f && visB - visT > 1f) {
                        drawCheckerTiledRegion(nativeMain, checkerTile, visL, visT, visR, visB)
                        drawVisibleBitmap(nativeMain, compositeBitmap, visL, visT, visR, visB, filteredPaint)
                    } else {
                        drawCheckerTiled(nativeMain, canvasWidth, canvasHeight, checkerTile)
                        drawTallBitmap(nativeMain, compositeBitmap, filteredPaint)
                    }
                } else {
                    // Grid transparansi berubin di bawah artwork (hemat untuk
                    // 720x16000), lalu komposit per slice 2048px agar lolos
                    // batas tekstur GPU + tetap tajam saat zoom-out.
                    drawCheckerTiled(nativeMain, canvasWidth, canvasHeight, checkerTile)
                    drawTallBitmap(nativeMain, compositeBitmap, filteredPaint)
                }

                // Overlay mask inpaint (pink) — viewport culled, di atas komposit tapi di bawah teks
                if (inpaintMask != null && activeTool == ActiveTool.INPAINT) {
                    val maskBmp = inpaintMask
                    if (maskBmp != null && !maskBmp.isRecycled) {
                        val maskPaint = android.graphics.Paint().apply {
                            // Tint putih mask jadi pink semi-transparan
                            colorFilter = android.graphics.PorterDuffColorFilter(
                                android.graphics.Color.parseColor("#FF4081"),
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                            alpha = 140
                        }
                        if (viewState.rotation == 0f) {
                            val vw2 = viewportSize.width.toFloat().coerceAtLeast(1f)
                            val vh2 = viewportSize.height.toFloat().coerceAtLeast(1f)
                            val pivX2 = viewState.pivotFracX * vw2
                            val pivY2 = viewState.pivotFracY * vh2
                            val sc2 = viewState.scale.coerceAtLeast(0.05f)
                            val xa2 = ((0f - pivX2 - viewState.offsetX) / sc2) + pivX2
                            val ya2 = ((0f - pivY2 - viewState.offsetY) / sc2) + pivY2
                            val xb2 = ((vw2 - pivX2 - viewState.offsetX) / sc2) + pivX2
                            val yb2 = ((vh2 - pivY2 - viewState.offsetY) / sc2) + pivY2
                            val visL2 = minOf(xa2, xb2).coerceIn(0f, canvasWidth.toFloat())
                            val visT2 = minOf(ya2, yb2).coerceIn(0f, canvasHeight.toFloat())
                            val visR2 = maxOf(xa2, xb2).coerceIn(0f, canvasWidth.toFloat())
                            val visB2 = maxOf(ya2, yb2).coerceIn(0f, canvasHeight.toFloat())
                            drawVisibleBitmap(nativeMain, maskBmp, visL2, visT2, visR2, visB2, maskPaint)
                        } else {
                            drawTallBitmap(nativeMain, maskBmp, maskPaint)
                        }
                    }
                }

                layerManager.visibleTextLayers().forEach { textLayer ->
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
                        val widthPaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.FILL
                            color = android.graphics.Color.parseColor("#FF9800")
                        }
                        val r = 14f / viewState.scale
                        for (h in listOf(box.scaleHandlePosition(), box.rotateHandlePosition())) {
                            native.drawCircle(h.x, h.y, r, handlePaint)
                            native.drawCircle(h.x, h.y, r, ringPaint)
                        }
                        val wr = 12f / viewState.scale
                        val half = 1.6f * wr
                        for (h in listOf(box.widthHandleLeft(), box.widthHandleRight())) {
                            native.drawRect(h.x - half, h.y - half, h.x + half, h.y + half, widthPaint)
                            val ring = android.graphics.Paint().apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 2f / viewState.scale
                                color = android.graphics.Color.WHITE
                            }
                            native.drawRect(h.x - half, h.y - half, h.x + half, h.y + half, ring)
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

                // Pratinjau drag kotak seleksi (tool Kotak Seleksi).
                val bs = boxStart
                val bc = boxCurrent
                if (bs != null && bc != null) {
                    val boxPaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f / viewState.scale
                        color = android.graphics.Color.GREEN
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                    }
                    val fillPaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.FILL
                        color = 0x3344FF44
                    }
                    val l = minOf(bs.x, bc.x)
                    val t = minOf(bs.y, bc.y)
                    val r = maxOf(bs.x, bc.x)
                    val b = maxOf(bs.y, bc.y)
                    drawContext.canvas.nativeCanvas.drawRect(l, t, r, b, fillPaint)
                    drawContext.canvas.nativeCanvas.drawRect(l, t, r, b, boxPaint)
                }
            }

            // Brush cursor overlay
            if (cursorPosition != null && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER || activeTool == ActiveTool.INPAINT || activeTool == ActiveTool.EYEDROPPER)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cursorRadius = (brushEngine.size * viewState.scale) / 2f
                    val col = when (activeTool) {
                        ActiveTool.ERASER -> Color.Red
                        ActiveTool.INPAINT -> Color(0xFFFF4081)
                        else -> Color.White
                    }
                    drawCircle(
                        color = col,
                        radius = cursorRadius.coerceAtLeast(6f),
                        center = cursorPosition!!,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                    if (activeTool == ActiveTool.INPAINT) {
                        drawCircle(color = Color(0x66FF4081), radius = cursorRadius.coerceAtLeast(6f), center = cursorPosition!!)
                    }
                }
            }
        }

        // Quick sliders (bottom, ala ibisPaint X)
        if (showQuickSlider && (activeTool == ActiveTool.BRUSH || activeTool == ActiveTool.ERASER || activeTool == ActiveTool.INPAINT)) {
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
                // Heal brush modes — melampaui Photoshop Content-Aware untuk manga.
                if (activeTool == ActiveTool.INPAINT || brushEngine.brushType == BrushType.HEAL_PATCH) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Spacer(modifier = Modifier.width(38.dp))
                        Text("Heal", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        for (hm in com.grooxtyper.app.model.HealMode.values()) {
                            val sel = inpaintingManager.healMode == hm
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Accent else Color(0xFF2C2C2E))
                                    .clickable { inpaintingManager.healMode = hm }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when (hm) {
                                        com.grooxtyper.app.model.HealMode.CONTENT_AWARE -> "Aware"
                                        com.grooxtyper.app.model.HealMode.PRESERVE_STRUCTURE -> "Structure"
                                        com.grooxtyper.app.model.HealMode.PRESERVE_TEXTURE -> "Texture"
                                    },
                                    color = Color.White, fontSize = 10.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Text(
                        when (inpaintingManager.healMode) {
                            com.grooxtyper.app.model.HealMode.CONTENT_AWARE -> "Content-Aware: seimbang (pengganti PS)."
                            com.grooxtyper.app.model.HealMode.PRESERVE_STRUCTURE -> "Structure: garis manga tetap tajam."
                            com.grooxtyper.app.model.HealMode.PRESERVE_TEXTURE -> "Texture: screentone/kertas mulus."
                        },
                        color = Color.Gray, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 78.dp)
                    )
                }
            }
        }

        // Top bar (ibisPaint style - minimal) — BISA DI-SLIDE/SCROLL horizontal
        // agar tombol Export dkk tak terpotong di layar sempit.
        // clickable noop agar tap di area kosong bar tidak tembus ke kanvas.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(TopBarBg)
                .align(Alignment.TopCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val topBarScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(topBarScroll)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            IconButton(onClick = { requestExit() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

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

            // "To Canvas": kembalikan kanvas ke tengah layar bila user
            // tak sengaja terlempar / kanvas hilang dari pandangan.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelBg)
                    .clickable { canvasToScreen() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "To Canvas", tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("To Canvas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

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
                        text = { Text("Export ${fmt.name}") },
                        onClick = {
                            exportFormat = fmt
                            exportResult = null
                            showExportMenu = false
                            showExportDialog = true
                        }
                    )
                }
            }
        }
        }

        // Dialog export: format + kualitas + progres + hasil (tak lagi gagal diam-diam).
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { if (!isExporting) showExportDialog = false },
                title = { Text("Export Artwork", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "${canvasWidth} x ${canvasHeight} px • teks & layer ikut ter-render",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Format", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFormat.values().forEach { fmt ->
                                val sel = exportFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Accent else PanelBg)
                                        .clickable(enabled = !isExporting) { exportFormat = fmt }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        fmt.name, color = Color.White, fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (exportFormat.supportsQuality) "Kualitas ${exportQuality.toInt()}%"
                            else "Kualitas: lossless (PNG mengabaikan slider)",
                            color = Color.Gray, fontSize = 12.sp
                        )
                        Slider(
                            value = exportQuality,
                            onValueChange = { exportQuality = it },
                            enabled = !isExporting && exportFormat.supportsQuality,
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                        )
                        if (isExporting) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Mengekspor… jangan tutup aplikasi.", color = Accent, fontSize = 12.sp)
                        }
                        exportResult?.let { msg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            isExporting = true
                            exportResult = null
                            val fmt = exportFormat
                            val q = exportQuality.toInt()
                            scope.launch {
                                val msg = withContext(Dispatchers.Default) {
                                    val file = exportManager.exportArtwork(layerManager, fmt, quality = q)
                                    if (file == null) {
                                        "Gagal export (memori habis?). Coba tutup aplikasi lain / kualitas lebih rendah."
                                    } else {
                                        val shown = withContext(Dispatchers.IO) {
                                            exportManager.publishToGallery(file, fmt.mime)
                                        } ?: file.absolutePath
                                        "Tersimpan: $shown"
                                    }
                                }
                                exportResult = msg
                                isExporting = false
                            }
                        },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(if (isExporting) "Mengekspor…" else "Export", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!isExporting) showExportDialog = false }) {
                        Text("Tutup", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
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
                IconButton(onClick = { showFlattenConfirm = true }, modifier = Modifier.size(32.dp)) {
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

        // Hint bar multi-seleksi (di atas toolbar bawah, gantian dengan bubble).
        if (!isMultiBubbleActive() &&
            (activeTool == ActiveTool.LASSO || activeTool == ActiveTool.SELECT_BOX) &&
            selectionEngine.hasSelection
        ) {
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
                    "${selectionEngine.selectionCount} area — drag tambah, ketuk hapus",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { selectionEngine.clearSelection(); refreshComposite() }
                ) { Text("Bersihkan", color = Color.Red, fontSize = 12.sp) }
            }
        }

        // Bottom toolbar (ibisPaint style) — BISA DI-SLIDE/SCROLL horizontal
        // agar semua tool muat di layar sempit dan Text Detector tidak hilang.
        // clickable noop agar tap di celah tombol tidak tembus ke kanvas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(BottomBarBg)
                .align(Alignment.BottomCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            val toolbarScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(toolbarScroll)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // Inpaint PatchMatch (heal) — brush yg menghapus objek & isi tekstur sekitar
            IconButton(onClick = {
                activeTool = ActiveTool.INPAINT
                showBrushSettings = false
                brushEngine.brushType = BrushType.HEAL_PATCH
                layerManager.ensureDrawingLayer()
            }) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Heal Brush", tint = if (activeTool == ActiveTool.INPAINT) Accent else Color.White)
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

            // Kotak Seleksi: drag persegi untuk seleksi cepat / tambah bubble.
            IconButton(onClick = { activeTool = ActiveTool.SELECT_BOX; showBrushSettings = false }) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Kotak Seleksi", tint = if (activeTool == ActiveTool.SELECT_BOX) Accent else Color.White)
            }

            // Text
            IconButton(onClick = {
                activeTool = ActiveTool.TEXT
                showBrushSettings = false
                if (selectedTextBox != null) showTextEditor = true
            }) {
                Icon(Icons.Default.TextFields, contentDescription = "Text", tint = if (activeTool == ActiveTool.TEXT) Accent else Color.White)
            }

            // Text Detector (ML Kit lokal, tanpa download model) — dikembalikan ke toolbar.
            IconButton(
                onClick = { runMLDetection() }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Text Detector", tint = Color.White)
            }

            // ML Inpaint (pakai hasil deteksi teks terakhir)
            IconButton(
                onClick = { runMLDetection() }
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Inpaint", tint = Color.White)
            }

            // Script: kombo seleksi + bubble (import/ketik -> kolom baris -> jalankan)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showScriptPanel) Accent else PanelBg)
                    .clickable { showScriptPanel = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = "Script", tint = Color.White, modifier = Modifier.size(16.dp))
                    if (scriptEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val remaining = scriptEntries.count { !it.used }
                        Text(
                            "$remaining/${scriptEntries.size}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }
                }
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
                    text = { Text("Kotak Seleksi") },
                    onClick = {
                        activeTool = ActiveTool.SELECT_BOX
                        showBrushSettings = false
                        showLassoMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        val n = selectionEngine.selectionCount.coerceAtLeast(1)
                        Text(if (n > 1) "Jadikan $n Bubble dari Seleksi" else "Jadikan Bubble dari Seleksi")
                    },
                    enabled = selectionEngine.hasSelection,
                    onClick = {
                        addBubbleFromSelection()
                        showLassoMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Fit Text to Selection") },
                    enabled = selectedTextBox != null && selectionEngine.hasSelection,
                    onClick = {
                        val box = selectedTextBox
                        val bounds = selectionEngine.selectionBounds()
                        if (box != null && bounds != null) {
                            undoRedoManager.pushTextBox(textLayerIdOf(box), box.copy())
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
                DropdownMenuItem(
                    text = { Text("Hapus area terakhir (${selectionEngine.selectionCount})") },
                    enabled = selectionEngine.selectionCount > 0,
                    onClick = {
                        selectionEngine.removeLastRegion()
                        refreshComposite()
                        showLassoMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Bersihkan seleksi") },
                    enabled = selectionEngine.hasSelection,
                    onClick = {
                        selectionEngine.clearSelection()
                        refreshComposite()
                        showLassoMenu = false
                    }
                )
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
                    layerManager.findTextLayerByBoxId(box.id)
                        ?.let { it.name = "Text: ${box.text.take(16)}" }
                }
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    TextEditorPanel(
                        box = box,
                        fonts = fontList,
                        onImportFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
                        onChange = { refreshCompositeCoalesced() },
                        onPushTextHistory = { before ->
                            undoRedoManager.pushTextBox(textLayerIdOf(box), before)
                        },
                        onCheckPrefix = { applyAllStylesTo(it) },
                        onOpenMultiBubble = {
                            multiBubbleDraft = ""
                            showMultiBubbleDialog = true
                        },
                        onFlatten = { showFlattenConfirm = true },
                        onDelete = { deleteSelectedText() },
                        onClose = { showTextEditor = false }
                    )
                }
            } ?: run { showTextEditor = false }
        }

        // Konfirmasi keluar agar tombol Back tak sengaja tidak menutup editor.
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { if (!isSavingExit) showExitDialog = false },
                title = { Text("Keluar dari editor?", color = Color.White) },
                text = {
                    Text(
                        if (isSavingExit) "Menyimpan project…"
                        else "Ada perubahan yang belum disimpan. Simpan dulu sebelum keluar?",
                        color = Color.LightGray, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { doSaveAndExit() },
                        enabled = !isSavingExit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text(if (isSavingExit) "Menyimpan…" else "Simpan & keluar", color = Color.Black)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showExitDialog = false },
                            enabled = !isSavingExit
                        ) {
                            Text("Batal", color = Color.Gray)
                        }
                        TextButton(
                            onClick = { doExitWithoutSaving() },
                            enabled = !isSavingExit
                        ) {
                            Text("Keluar tanpa menyimpan", color = Color.Red)
                        }
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Konfirmasi flatten: bakar teks ke layer = tak bisa diedit lagi.
        if (showFlattenConfirm) {
            AlertDialog(
                onDismissRequest = { showFlattenConfirm = false },
                title = { Text("Bakar teks ke layer?", color = Color.White) },
                text = {
                    Text(
                        "Teks \"${selectedTextBox?.text?.take(24) ?: ""}\" akan menjadi piksel dan tidak bisa diedit lagi. Batalkan lewat Undo bila berubah pikiran.",
                        color = Color.LightGray, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFlattenConfirm = false
                            flattenSelectedText()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Bakar", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFlattenConfirm = false }) {
                        Text("Batal", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        if (showMLInpaintDialog) {
            AlertDialog(
                onDismissRequest = { showMLInpaintDialog = false },
                title = { Text("Deteksi Teks", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            if (mlDetecting) "Mendeteksi teks…" else "Detected ${detectedTextRegions.size} text blocks.",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Mesin deteksi:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextEngine.values().forEach { engine ->
                                val on = textEngine == engine
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (on) Accent else PanelBg)
                                        .clickable { textEngine = engine }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(engine.displayName, color = Color.White, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        Text(
                            if (textEngine == TextEngine.PPOCR_V6) {
                                val st = ppocrDetector.modelStatus()
                                if (ppocrDetector.isAvailable()) "PP-OCR siap ($st). Korea⊃Inggris • China⊃Inggris."
                                else "Model belum ikut ter-build ($st). Jalankan (otomatis fallback ML Kit)."
                            } else {
                                "ML Kit bawaan, tanpa file tambahan."
                            },
                            color = if (textEngine == TextEngine.PPOCR_V6 && !ppocrDetector.isAvailable()) Color(0xFFFF6B6B) else Color.Gray, fontSize = 11.sp
                        )
                        if (ppocrError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ppocrError!!, color = Color(0xFFFFCC00), fontSize = 11.sp)
                            Text("Logcat: adb logcat -s PpocrDetector,RunML", color = Color.Gray, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Bahasa deteksi:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "PP-OCR: Jepang tak didukung (pakai ML Kit untuk Jepang).",
                            color = Color.Gray, fontSize = 11.sp
                        )
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
                        Text("Variasi Mask (2 pilihan):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Mask Kotak = persegi solid. Mask Bentuk Teks = mengikuti huruf.",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Button(
                                onClick = { selectedMaskType = MLMaskType.MASK_KOTAK },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.MASK_KOTAK) Accent else PanelBg)
                            ) {
                                Text(MLMaskType.MASK_KOTAK.displayName, color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { selectedMaskType = MLMaskType.MASK_BENTUK_TEKS },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMaskType == MLMaskType.MASK_BENTUK_TEKS) Accent else PanelBg)
                            ) {
                                Text(MLMaskType.MASK_BENTUK_TEKS.displayName, color = Color.White, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mode Inpaint:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "PatchMatch = pelestari tekstur (lebih bagus dari Photoshop Content-Aware). Telea = halus cepat.",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Button(
                                onClick = { inpaintingManager.mode = com.grooxtyper.app.model.InpaintMode.PATCH_MATCH },
                                colors = ButtonDefaults.buttonColors(containerColor = if (inpaintingManager.mode == com.grooxtyper.app.model.InpaintMode.PATCH_MATCH) Accent else PanelBg)
                            ) {
                                Text("PatchMatch", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { inpaintingManager.mode = com.grooxtyper.app.model.InpaintMode.TELEA },
                                colors = ButtonDefaults.buttonColors(containerColor = if (inpaintingManager.mode == com.grooxtyper.app.model.InpaintMode.TELEA) Accent else PanelBg)
                            ) {
                                Text("Telea", color = Color.White, fontSize = 11.sp)
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
                                        applyAllStylesTo(box)
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
                    onRefresh = { refreshComposite() },
                    onEditText = { tl ->
                        selectedTextBox = tl.box
                        activeTool = ActiveTool.TEXT
                        showLayersPanel = false
                        showTextEditor = true
                        refreshComposite()
                    }
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
                            else "Ditemukan ${detectedBubbles.size} bubble (mentah, tanpa refine).",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Model (pilih satu):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    Text(model.asset, color = Color.Gray, fontSize = 10.sp)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mode hapus (ketuk bubble)", color = Color.White, fontSize = 12.sp)
                            }
                            Switch(
                                checked = bubbleEraseMode,
                                onCheckedChange = { bubbleEraseMode = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Accent)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { addBubbleFromSelection() },
                                enabled = selectionEngine.hasSelection,
                                colors = ButtonDefaults.buttonColors(containerColor = PanelBg),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Dari Seleksi", color = Color.White, fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    activeTool = ActiveTool.SELECT_BOX
                                    showBubbleDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PanelBg),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Kotak Seleksi", color = Color.White, fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    detectedBubbles = emptyList()
                                    refreshComposite()
                                },
                                enabled = detectedBubbles.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = PanelBg),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Hapus Semua", color = Color.White, fontSize = 11.sp) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Daftar bubble (ketuk ikon hapus untuk buang):", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (detectedBubbles.isEmpty()) {
                            Text("Belum ada bubble. Jalankan Deteksi atau tambah via Kotak Seleksi.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                itemsIndexed(detectedBubbles) { idx, b ->
                                    val r = b.boundingBox
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("#${idx + 1} (${r.left.toInt()},${r.top.toInt()} ${r.width().toInt()}x${r.height().toInt()})", color = Color.White, fontSize = 12.sp)
                                        }
                                        IconButton(
                                            onClick = { removeBubbleAt(idx) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus bubble ${idx + 1}", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ketuk bubble di mode Lasso/Kotak untuk TAMBAH ke seleksi (multi). Ketuk area terseleksi untuk menghapusnya. Drag di tool Kotak Seleksi untuk tambah area persegi.",
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

        // === Panel Script: kolom baris + jalankan ke seleksi/bubble ===
        if (showScriptPanel) {
            val unusedCount = scriptEntries.count { !it.used }
            val canRun = unusedCount > 0 && (detectedBubbles.isNotEmpty() || selectionEngine.hasSelection)
            AlertDialog(
                onDismissRequest = { showScriptPanel = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Script → Bubble", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        // Status ringkas berupa pil.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("${scriptEntries.size} baris")
                            StatusPill("$unusedCount sisa", highlight = unusedCount > 0)
                            StatusPill("${detectedBubbles.size} bubble")
                            StatusPill(
                                if (selectionEngine.hasSelection) "seleksi ✓" else "tanpa seleksi",
                                highlight = selectionEngine.hasSelection
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Progres pemakaian.
                        if (scriptEntries.isNotEmpty()) {
                            val done = scriptEntries.size - unusedCount
                            val frac = done.toFloat() / scriptEntries.size.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF38383A))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Accent)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "$done/${scriptEntries.size} baris terpakai",
                                color = Color.Gray, fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            Text(
                                "Belum ada script. Import file atau ketik manual.",
                                color = Color.LightGray, fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            "Sumber naskah",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScriptActionButton(
                                label = "Import",
                                icon = Icons.Default.FileUpload,
                                modifier = Modifier.weight(1f),
                                onClick = { scriptImportLauncher.launch(arrayOf("text/plain", "*/*")) }
                            )
                            ScriptActionButton(
                                label = "Contoh",
                                icon = Icons.Default.Description,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Muat contoh bawaan dari assets (offline, tanpa download).
                                    runCatching {
                                        context.assets.open("contoh_script.txt").bufferedReader().readText()
                                    }.getOrNull()?.let { raw ->
                                        val parsed = parseScriptRaw(raw)
                                        if (parsed.isNotEmpty()) {
                                            scriptEntries = parsed
                                            scriptDraft = raw
                                        }
                                    }
                                }
                            )
                            ScriptActionButton(
                                label = "Ketik",
                                icon = Icons.Default.Edit,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scriptDraft = scriptEntries.joinToString("\n") { it.text }
                                    showScriptEditor = true
                                }
                            )
                            IconButton(
                                onClick = { resetScriptUsage() },
                                enabled = scriptEntries.any { it.used },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanelBg)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset terpakai", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Kartu style rules.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PanelBg)
                                .clickable {
                                    stylePresetsTick++
                                    newRuleStyleId = stylePresets().firstOrNull()?.id ?: ""
                                    showStyleRules = true
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Style Rules", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (styleRules.isEmpty()) "Nonaktif — ketuk untuk atur"
                                    else "${styleRules.size} aturan: ${styleRules.take(2).joinToString { "'${it.prefix}'" }}${if (styleRules.size > 2) "…" else ""}",
                                    color = Color.Gray, fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (scriptEntries.isEmpty()) {
                            Text(
                                "Contoh format: satu baris = satu bubble. Header 'Page X' otomatis diabaikan. Lihat folder contoh/script.txt.",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        } else {
                            Text(
                                "Daftar baris — ketuk untuk tandai/batalkan terpakai",
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(scriptEntries) { idx, e ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (e.used) Color(0xFF1F3D2B) else PanelBg)
                                            .border(
                                                1.dp,
                                                if (e.used) Color(0xFF2E7D32) else Color(0xFF38383A),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                // Toggle manual: tandai / batalkan terpakai per baris.
                                                val i = scriptEntries.indexOfFirst { it.id == e.id }
                                                if (i >= 0) {
                                                    scriptEntries[i].used = !scriptEntries[i].used
                                                    scriptEntries = scriptEntries.toList()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (e.used) Color(0xFF2E7D32) else Accent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${idx + 1}",
                                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            e.text,
                                            color = if (e.used) Color.Gray else Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (e.used) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = "Terpakai", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Urutan render: atas→bawah, dalam baris kanan→kiri. Prioritas bubble bila ada.",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { runScript() },
                        enabled = canRun,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Jalankan", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScriptPanel = false }) {
                        Text("Tutup", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
        }

        // === Editor Script: ketik / tempel manual ===
        if (showScriptEditor) {
            val draftLines = scriptDraft.lines().count { it.trim().isNotEmpty() }
            AlertDialog(
                onDismissRequest = { showScriptEditor = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ketik Script", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("$draftLines baris", highlight = draftLines > 0)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Satu baris = satu bubble.",
                                color = Color.Gray, fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Baris 'Page …' dan 'Terjemahan' otomatis diabaikan.",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = scriptDraft,
                            onValueChange = { scriptDraft = it },
                            label = { Text("Tempel / ketik naskah di sini") },
                            minLines = 8,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = parseScriptRaw(scriptDraft)
                            if (parsed.isNotEmpty()) {
                                scriptEntries = parsed
                                showScriptEditor = false
                                showScriptPanel = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Simpan", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showScriptEditor = false }) {
                        Text("Batal", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
        }

        // === Style Rules: prefix script -> style + hapus awalan ===
        if (showStyleRules) {
            val presets = stylePresets()
            AlertDialog(
                onDismissRequest = { showStyleRules = false },
                title = { Text("Style Rules", color = Color.White) },
                text = {
                    Column {
                        Text(
                            "Jika baris diawali prefix, pakai style tsb dan awalan dihapus saat render. Contoh: '() : ' -> Style A, '\"\": ' -> Style B.",
                            color = Color.Gray, fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newRulePrefix,
                            onValueChange = { newRulePrefix = it },
                            label = { Text("Awalan, cth: () : ") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (presets.isEmpty()) {
                            Text(
                                "Belum ada Style. Buat dulu di panel Teks > tab Style > Simpan.",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        } else {
                            Text("Pilih style:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(110.dp)
                            ) {
                                itemsIndexed(presets) { _, p ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (newRuleStyleId == p.id) Accent else PanelBg)
                                            .clickable { newRuleStyleId = p.id }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(p.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        if (p.prefix.isNotBlank()) {
                                            Text("[${p.prefix}]", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hapus awalan saat render", color = Color.White, fontSize = 12.sp)
                            }
                            Switch(
                                checked = newRuleStrip,
                                onCheckedChange = { newRuleStrip = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Accent)
                            )
                        }
                        Button(
                            onClick = {
                                val pre = newRulePrefix
                                if (pre.isNotEmpty() && newRuleStyleId.isNotEmpty()) {
                                    styleRules = styleRuleManager.save(
                                        StyleRule(prefix = pre, styleId = newRuleStyleId, stripPrefix = newRuleStrip)
                                    )
                                    newRulePrefix = ""
                                }
                            },
                            enabled = newRulePrefix.isNotEmpty() && newRuleStyleId.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Tambah Rule", color = Color.White) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Daftar rules (${styleRules.size}) — ketuk ikon hapus untuk buang:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (styleRules.isEmpty()) {
                            Text("Belum ada rule.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            ) {
                                itemsIndexed(styleRules) { _, r ->
                                    val sName = presets.find { it.id == r.styleId }?.name ?: "(style terhapus)"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("'${r.prefix}' → $sName", color = Color.White, fontSize = 12.sp)
                                            Text(
                                                if (r.stripPrefix) "awalan dihapus" else "awalan dipertahankan",
                                                color = Color.Gray, fontSize = 10.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { styleRules = styleRuleManager.delete(r.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus rule", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showStyleRules = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Selesai", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showStyleRules = false }) {
                        Text("Tutup", color = Color.Gray)
                    }
                },
                containerColor = PanelBg
            )
        }
    }
}
