package com.grooxtyper.app.model.selection

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.grooxtyper.app.model.SelectionEngine

/**
 * API wand untuk script dan automation.
 *
 * UI tool dan script memakai mesin yang sama ([HugeWandEngine]) dan
 * peta yang sama ([TiledSelectionAdapter]), jadi hasilnya konsisten.
 * Wand bukan hanya tool klik, tetapi juga API yang bisa dipanggil.
 */
interface WandScriptApi {

    /** Pilih wilayah warna mirip di titik kanvas. */
    suspend fun selectAt(x: Int, y: Int, tolerance: Int = 32): WandResult?

    /**
     * Pilih satu bubble di titik kanvas: outline jadi batas keras dan
     * gelembung yang menyatu dipisah (hanya milik titik yang kembali).
     */
    suspend fun selectBubbleAt(x: Int, y: Int, tolerance: Int = 32): WandResult?

    /** Tambah hasil terakhir ke seleksi (gabungan). */
    fun addSelection(): BubbleInfo?

    /** Kurangi seleksi dengan hasil terakhir. */
    fun subtractSelection(): BubbleInfo?

    /** Iris seleksi dengan hasil terakhir. */
    fun intersectSelection(): BubbleInfo?

    /** Hapus seluruh seleksi. */
    fun clearSelection()

    /** Batas seleksi saat ini, null bila kosong. */
    fun bounds(): Rect?

    /** Jumlah piksel seleksi saat ini. */
    fun pixelCount(): Long

    /** Metadata bubble untuk keputusan script. */
    fun bubbleInfo(): BubbleInfo?

    /** Daftar region per identitas (bubble A, B, C, ...). */
    fun regions(): List<SelectionRegion>

    /** Riwayat operasi seleksi. */
    fun history(): List<SelectionHistory.Entry>
}

/**
 * Implementasi [WandScriptApi] di atas mesin dan adapter yang sama
 * dengan UI tool.
 */
class HugeWandScriptApi(
    private val engine: HugeWandEngine,
    private val adapter: TiledSelectionAdapter,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    private val pushToEngine: ((TiledSelectionAdapter) -> Unit)? = null
) : WandScriptApi {

    private var last: WandResult? = null

    override suspend fun selectAt(x: Int, y: Int, tolerance: Int): WandResult? {
        val r = engine.select(x, y, tolerance, contiguous = true,
            bubbleAware = false, separateBubble = false)
        if (r != null) {
            last = r
            adapter.apply(r, SelectionMode.NEW, canvasWidth, canvasHeight, "Script $x,$y")
            pushToEngine?.invoke(adapter)
        }
        return r
    }

    override suspend fun selectBubbleAt(x: Int, y: Int, tolerance: Int): WandResult? {
        val r = engine.select(x, y, tolerance, contiguous = true,
            bubbleAware = true, separateBubble = true)
        if (r != null) {
            last = r
            adapter.apply(r, SelectionMode.NEW, canvasWidth, canvasHeight, "Bubble $x,$y")
            pushToEngine?.invoke(adapter)
        }
        return r
    }

    override fun addSelection(): BubbleInfo? {
        val r = last ?: return bubbleInfo()
        adapter.apply(r, SelectionMode.ADD, canvasWidth, canvasHeight, "Tambah")
        pushToEngine?.invoke(adapter)
        return bubbleInfo()
    }

    override fun subtractSelection(): BubbleInfo? {
        val r = last ?: return bubbleInfo()
        adapter.apply(r, SelectionMode.SUBTRACT, canvasWidth, canvasHeight, "Kurang")
        pushToEngine?.invoke(adapter)
        return bubbleInfo()
    }

    override fun intersectSelection(): BubbleInfo? {
        val r = last ?: return bubbleInfo()
        adapter.apply(r, SelectionMode.INTERSECT, canvasWidth, canvasHeight, "Iris")
        pushToEngine?.invoke(adapter)
        return bubbleInfo()
    }

    override fun clearSelection() {
        last = null
        adapter.clear()
        pushToEngine?.invoke(adapter)
    }

    override fun bounds(): Rect? = adapter.bounds()

    override fun pixelCount(): Long = adapter.pixelCount()

    override fun bubbleInfo(): BubbleInfo? {
        val b = adapter.bounds() ?: return null
        return BubbleInfo(
            bounds = Rect(b),
            pixelCount = adapter.pixelCount(),
            aspectRatio = b.width().toFloat().coerceAtLeast(1f) / b.height().toFloat().coerceAtLeast(1f),
            regionCount = adapter.regions.size.coerceAtLeast(1),
            confidence = 0.5f
        )
    }

    override fun regions(): List<SelectionRegion> = ArrayList(adapter.regions)

    override fun history(): List<SelectionHistory.Entry> = adapter.history.list()
}

/**
 * Perekam macro: merekam perintah kanvas lalu menjalankannya ulang.
 *
 * Contoh alur: klik bubble -> wand -> lebarkan 2px -> hapus -> isi
 * putih. Macro menyimpan perintahnya, bukan pikselnya, jadi bisa
 * dijalankan ulang di tempat lain.
 */
class MacroRecorder {
    private val items = ArrayList<CanvasCommand>()
    var recording: Boolean = false
        private set

    /** Mulai merekam (membuang rekaman lama). */
    fun start() {
        items.clear()
        recording = true
    }

    /** Berhenti merekam, kembalikan salinan perintah. */
    fun stop(): List<CanvasCommand> {
        recording = false
        return ArrayList(items)
    }

    /** Rekam satu perintah bila sedang merekam. */
    fun record(cmd: CanvasCommand) {
        if (recording) items.add(cmd)
    }

    /** Banyak perintah terekam. */
    fun size(): Int = items.size
}

/**
 * Potongan bitmap sebatas batas seleksi untuk OCR.
 *
 * Tidak pernah membuat bitmap sebesar kanvas: hanya membaca tile yang
 * diperlukan dari [CanvasPixelProvider].
 */
object OcrCropHelper {
    /**
     * Baca potongan [bounds] jadi Bitmap ARGB. Null bila di luar kanvas
     * atau terlalu besar (>8 juta piksel).
     */
    fun cropBounds(
        provider: CanvasPixelProvider, bounds: Rect
    ): Bitmap? {
        val l = bounds.left.coerceIn(0, provider.canvasWidth)
        val t = bounds.top.coerceIn(0, provider.canvasHeight)
        val r = bounds.right.coerceIn(0, provider.canvasWidth)
        val b = bounds.bottom.coerceIn(0, provider.canvasHeight)
        val w = r - l
        val h = b - t
        if (w < 2 || h < 2 || w.toLong() * h > 8_000_000L) return null
        return try {
            val px = provider.getPixels(l, t, w, h)
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        } catch (t2: Throwable) {
            null
        }
    }

    /**
     * Pilih region lalu potong untuk OCR dalam satu panggilan.
     * Mengembalikan null bila seleksi kosong.
     */
    suspend fun selectAndCrop(
        api: WandScriptApi, provider: CanvasPixelProvider,
        x: Int, y: Int, tolerance: Int, bubble: Boolean
    ): Bitmap? {
        val r = if (bubble) api.selectBubbleAt(x, y, tolerance)
        else api.selectAt(x, y, tolerance)
        val b = r?.bounds ?: return null
        return cropBounds(provider, b)
    }
}

/**
 * Pelaksana perintah kanvas: UI, script, dan macro lewat sini.
 *
 * Operasi piksel (isi, hapus, salin) didelegasikan ke [SelectionEngine]
 * yang sudah ada; operasi seleksi ke [TiledSelectionAdapter].
 */
class CanvasCommandRunner(
    private val provider: CanvasPixelProvider,
    private val engineFactory: () -> HugeWandEngine,
    private val adapter: TiledSelectionAdapter,
    private val selectionEngine: SelectionEngine,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    /** Layer gambar aktif untuk operasi piksel (isi, hapus). */
    private val activeLayer: () -> com.grooxtyper.app.model.DrawingLayer?
) {
    /**
     * Viewport untuk overlay; null berarti seluruh seleksi. Bila diisi,
     * Path yang didorong ke engine lama hanya mencakup yang terlihat,
     * jadi kanvas 720x16000 tidak me-render 16000px saat user hanya
     * melihat 720x900.
     */
    var viewport: RectF? = null

    /** Jalankan satu perintah. Mengembalikan info bubble bila ada. */
    suspend fun run(cmd: CanvasCommand): BubbleInfo? {
        return when (cmd) {
            is CanvasCommand.WandSelectCommand -> {
                val e = engineFactory()
                val r = e.select(cmd.x, cmd.y, cmd.tolerance,
                    contiguous = cmd.contiguous, bubbleAware = cmd.bubbleAware,
                    separateBubble = cmd.bubbleAware, antialias = cmd.antialias)
                if (r == null) null
                else {
                    val info = adapter.apply(r, cmd.mode, canvasWidth, canvasHeight, "Wand")
                    pushViewport()
                    info
                }
            }
            is CanvasCommand.AddSelectionCommand -> {
                val e = engineFactory()
                val r = e.select(cmd.x, cmd.y, cmd.tolerance)
                if (r == null) null
                else {
                    val info = adapter.apply(r, SelectionMode.ADD, canvasWidth, canvasHeight, "Tambah")
                    pushViewport()
                    info
                }
            }
            is CanvasCommand.SubtractSelectionCommand -> {
                val e = engineFactory()
                val r = e.select(cmd.x, cmd.y, cmd.tolerance)
                if (r == null) null
                else {
                    val info = adapter.apply(r, SelectionMode.SUBTRACT, canvasWidth, canvasHeight, "Kurang")
                    pushViewport()
                    info
                }
            }
            is CanvasCommand.IntersectSelectionCommand -> {
                val e = engineFactory()
                val r = e.select(cmd.x, cmd.y, cmd.tolerance)
                if (r == null) null
                else {
                    val info = adapter.apply(r, SelectionMode.INTERSECT, canvasWidth, canvasHeight, "Iris")
                    pushViewport()
                    info
                }
            }
            is CanvasCommand.ClearSelectionCommand -> {
                adapter.clear()
                selectionEngine.clearSelection()
                null
            }
            is CanvasCommand.FillSelectionCommand -> {
                activeLayer()?.let { layer ->
                    fillOnto(layer.getPersistentBitmap(), cmd.color)
                    layer.markDirty()
                }
                null
            }
            is CanvasCommand.DeleteSelectionCommand -> {
                activeLayer()?.let { layer ->
                    selectionEngine.clearSelectedArea(layer)
                }
                null
            }
            is CanvasCommand.ExpandSelectionCommand -> {
                adapter.expand(cmd.pixels, canvasWidth, canvasHeight)
                pushViewport()
                null
            }
            is CanvasCommand.ContractSelectionCommand -> {
                adapter.contract(cmd.pixels, canvasWidth, canvasHeight)
                pushViewport()
                null
            }
            is CanvasCommand.FeatherSelectionCommand -> {
                adapter.feather(cmd.radius, canvasWidth, canvasHeight)
                pushViewport()
                null
            }
        }
    }

    /** Dorong adapter ke engine lama, dibatasi viewport bila ada. */
    private fun pushViewport() {
        val vp = viewport
        if (vp == null) {
            adapter.pushToEngine(selectionEngine, canvasWidth, canvasHeight)
            return
        }
        val path: Path = adapter.toPath(vp, canvasWidth, canvasHeight)
        val b = RectF()
        path.computeBounds(b, true)
        selectionEngine.clearSelection()
        if (!b.isEmpty) selectionEngine.addRegionPath(path, b)
    }

    /** Isi seleksi pada bitmap [target] dengan warna (dipakai script fill). */
    fun fillOnto(target: Bitmap, color: Int) {
        if (adapter.tileMap.isEmpty()) return
        val path = adapter.toPath(null, canvasWidth, canvasHeight)
        val canvas = android.graphics.Canvas(target)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(color)
        canvas.restore()
    }

}
