package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class BrushType(val displayName: String, val category: String) {
    // Pen (ala ibisPaint: pena garis tegas untuk lineart manga)
    PEN_HARD("Pen (Hard)", "Pen"),
    PEN_SOFT("Pen (Soft)", "Pen"),
    DIP_HARD("Dip Pen Hard", "Pen"),
    DIP_SOFT("Dip Pen Soft", "Pen"),
    FELT_HARD("Felt Tip Hard", "Pen"),
    FELT_SOFT("Felt Tip Soft", "Pen"),
    BALLPOINT("Ballpoint", "Pen"),
    G_PEN("G Pen", "Pen"),
    PENCIL("Pencil", "Pen"),
    INK("Ink", "Pen"),
    // Paint
    WATERCOLOR("Watercolor", "Paint"),
    OIL("Oil", "Paint"),
    MARKER("Marker", "Paint"),
    FLAT("Flat Brush", "Paint"),
    ROUND("Round Brush", "Paint"),
    CRAYON("Crayon", "Paint"),
    // Effect: brush olah-piksel yang benar-benar sesuai namanya.
    // BLEND = smudge (menyeret cat), BLUR = blur nyata, DODGE/BURN = tonal.
    BLEND("Blend (Smudge)", "Effect"),
    BLUR("Blur", "Effect"),
    DODGE("Dodge (Lighten)", "Effect"),
    BURN("Burn (Darken)", "Effect"),
    // Air
    AIRBRUSH("Airbrush", "Air"),
    AIR_FAN("Fan Brush", "Air"),
    // Erase
    ERASER("Eraser", "Erase"),
    ERASER_SOFT("Soft Eraser", "Erase"),
    // Hapus objek (Content-Aware Fill tanpa model)
    OBJECT_ERASER("Hapus Objek", "Hapus"),
    // Heal model (MiGAN on-device, di-bundle saat build CI)
    HEAL_MIGAN("Heal MiGAN", "Hapus")
}

enum class RulerType {
    OFF,
    STRAIGHT_LINE,
    CIRCLE
}

class ForceFadeConfig(
    isEnabled: Boolean = false,
    startFade: Float = 0.3f,
    endFade: Float = 0.3f
) {
    var isEnabled by mutableStateOf(isEnabled)
    var startFade by mutableFloatStateOf(startFade)
    var endFade by mutableFloatStateOf(endFade)
}

class StabilizerConfig(
    isEnabled: Boolean = true,
    strength: Float = 0.5f // 0.0 = raw, 1.0 = max smoothing
) {
    var isEnabled by mutableStateOf(isEnabled)
    var strength by mutableFloatStateOf(strength)
}

private const val MAX_BLUR_SIDE = 512f

class RulerGuide(
    type: RulerType = RulerType.OFF,
    startPos: Offset = Offset(100f, 100f),
    endPos: Offset = Offset(500f, 500f),
    circleCenter: Offset = Offset(300f, 300f),
    circleRadius: Float = 200f,
    rotationDeg: Float = 0f
) {
    var type by mutableStateOf(type)
    var rotationDeg by mutableFloatStateOf(rotationDeg)
    // FIX: posisi penggaris WAJIB state Compose. Sebelumnya plain var sehingga
    // preview tidak pernah ter-recompose (penggaris terasa "tidak ada").
    var startPos by mutableStateOf(startPos)
    var endPos by mutableStateOf(endPos)
    var circleCenter by mutableStateOf(circleCenter)
    var circleRadius by mutableFloatStateOf(circleRadius)

    /** Toleransi kunci (px kanvas): stroke yang mulai dekat penggaris mengikuti
     *  penggaris; yang jauh tetap bebas (perilaku penggaris fisik). */
    var snapTolerance: Float = 90f

    fun midPoint(): Offset = Offset((startPos.x + endPos.x) / 2f, (startPos.y + endPos.y) / 2f)

    fun move(dx: Float, dy: Float) {
        startPos = Offset(startPos.x + dx, startPos.y + dy)
        endPos = Offset(endPos.x + dx, endPos.y + dy)
        circleCenter = Offset(circleCenter.x + dx, circleCenter.y + dy)
    }

    /** Taruh penggaris di dalam area yang terlihat (dipanggil saat fitur nyala)
     *  supaya penggaris langsung tampil, bukan tersembunyi di luar viewport. */
    fun placeInRect(r: RectF) {
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val halfW = (r.width() * 0.32f).coerceAtLeast(60f)
        val rad = Math.toRadians(rotationDeg.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        startPos = Offset(cx - halfW * c, cy - halfW * s)
        endPos = Offset(cx + halfW * c, cy + halfW * s)
        circleCenter = Offset(cx, cy)
        circleRadius = (minOf(r.width(), r.height()) * 0.3f).coerceAtLeast(40f)
    }

    /** Proyeksi TANPA syarat ke penggaris (garis dianggap menerus, ujung tidak
     *  dijepit) — dipakai setelah stroke dikunci, jadi tidak ada goresan yang
     *  "tertahan" di ujung penggaris seperti versi lama. */
    fun project(p: Offset): Offset = when (type) {
        RulerType.STRAIGHT_LINE -> {
            val dx = endPos.x - startPos.x
            val dy = endPos.y - startPos.y
            val lenSq = dx * dx + dy * dy
            if (lenSq == 0f) p else {
                val t = ((p.x - startPos.x) * dx + (p.y - startPos.y) * dy) / lenSq
                Offset(startPos.x + t * dx, startPos.y + t * dy)
            }
        }
        RulerType.CIRCLE -> {
            val dx = p.x - circleCenter.x
            val dy = p.y - circleCenter.y
            val dist = hypot(dx, dy)
            if (dist == 0f) p
            else Offset(circleCenter.x + (dx / dist) * circleRadius, circleCenter.y + (dy / dist) * circleRadius)
        }
        RulerType.OFF -> p
    }

    /** True bila titik awal stroke cukup dekat penggaris untuk dikunci. */
    fun shouldLock(p: Offset, tolerance: Float = snapTolerance): Boolean {
        if (type == RulerType.OFF) return false
        if (type == RulerType.CIRCLE) return true
        return (p - project(p)).getDistance() <= tolerance
    }

    /** Rotasi ruler (derajat): STRAIGHT_LINE memutar garis di titik tengah,
     *  CIRCLE memutar titik awal pegangan radius (visual). */
    fun rotate(deltaDeg: Float) {
        rotationDeg = ((rotationDeg + deltaDeg) % 360f + 360f) % 360f
        if (type == RulerType.STRAIGHT_LINE) {
            val cx = (startPos.x + endPos.x) / 2f
            val cy = (startPos.y + endPos.y) / 2f
            val rad = Math.toRadians(deltaDeg.toDouble())
            val cosR = kotlin.math.cos(rad).toFloat(); val sinR = kotlin.math.sin(rad).toFloat()
            fun rot(o: Offset): Offset {
                val dx = o.x - cx; val dy = o.y - cy
                return Offset(cx + dx * cosR - dy * sinR, cy + dx * sinR + dy * cosR)
            }
            startPos = rot(startPos); endPos = rot(endPos)
        }
    }
    /** Kompatibilitas: snap dengan gerbang toleransi (dipakai pemanggil lama). */
    fun snapPoint(p: Offset, tolerance: Float = snapTolerance): Offset =
        if (shouldLock(p, tolerance)) project(p) else p
}

class BrushEngine {
    var brushType by mutableStateOf(BrushType.PEN_HARD)
    var size by mutableFloatStateOf(24f)
    var opacity by mutableFloatStateOf(1.0f)
    var color by mutableIntStateOf(Color.BLACK)
    var forceFade: ForceFadeConfig = ForceFadeConfig()
    var stabilizer: StabilizerConfig = StabilizerConfig()
    var rulerGuide: RulerGuide = RulerGuide()

    // Stroke smoothing state
    private var lastSmoothedPoint: Offset? = null
    // Kecepatan lowpass ala MyPaint (fac=exp(-dt/T)) untuk dynamics halus.
    private var velocityEma = 0f
    // One-Euro filter per sumbu (studi stroke-stabilizer): lambat = halus,
    // cepat = responsif. Reset tiap stroke agar tak ada lompatan awal.
    private var euroX = 0f
    private var euroY = 0f
    private var euroDx = 0f
    private var euroInit = false
    // Warna pickup smudge (dipertahankan sepanjang stroke, reset tiap stroke).
    private var blendPickup: Int? = null

    /** Mode atur penggaris: saat true semua sapuan brush diabaikan supaya user
     *  bisa menggeser/memutar penggaris tanpa tidak sengaja menggambar. */
    var rulerAdjustMode by mutableStateOf(false)

    // ---- Smudge (Blend): cap = potongan kanvas dari dab sebelumnya ----
    private var smudgeBuf: Bitmap? = null
    private var smudgeMask: Bitmap? = null
    private var smudgeTemp: Bitmap? = null
    private var smudgeDim = 0
    private var smudgeHasInk = false
    private var smudgeStamp = Paint(Paint.ANTI_ALIAS_FLAG)
    private var smudgeRefill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskDstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    // ---- Blur nyata (box blur 3 pass ≈ Gaussian) ----
    private var blurScratch: IntArray? = null
    private var blurScratchB: IntArray? = null

    // Kunci penggaris per-stroke (ala ibisPaint/Procreate): diputuskan saat
    // stroke dimulai, dipertahankan sampai jari diangkat.
    private var rulerLocked = false

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val av = (a shr shift) and 0xFF
            val bv = (b shr shift) and 0xFF
            return (av + (bv - av) * tt).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Prediksi titik berikut dari kecepatan (Ink prediction, murah, tanpa API baru).
     * Sapuan 720x16000 terasa responsif walau event touch jarang (low-latency
     * front-buffered idea dari androidx.graphics.lowlatency). */
    private var lastVelocity = Offset.Zero
    // Tile-dirty 64px ala MyPaint TiledSurface: hanya tile tersentuh yang
    // ditandai untuk composite inkremental (hemat vs render 46MB penuh).
    private val dirtyTiles = LinkedHashSet<Long>()
    private var dirtyTileBounds = RectF()

    // Titik hasil smoothing segmen sebelumnya — untuk interpolasi kuadratik
    // antar segmen (menghilangkan sudut "patah" saat jari bergerak cepat
    // di kanvas jangkung 720x16000 di mana event touch jarang).
    private var prevCurvePoint: Offset? = null

    // Cache paint: alokasi BlurMaskFilter BARU tiap segmen sangat mahal
    // (native blur mask) dan memicu GC churn — penyebab utama brush
    // tersendat/patah di kanvas 46MB. Master disimpan sekali per konfigurasi,
    // tiap segmen cukup clone dangkal (berbagi referensi maskFilter immutable).
    private var cachedPaint: Paint? = null
    private var cachedPaintKey: Int = 0

    // Cache Canvas & Path: alokasi Canvas(bmp) baru per segmen = objek native
    // baru per titik interpolasi (hingga 24/event di 720x16000) → GC churn
    // dan mikro-jank. Path dipakai ulang untuk raster sekali-jalan (drawPath).
    private var cachedCanvas: Canvas? = null
    private var cachedCanvasBitmap: Bitmap? = null
    private val strokePath = Path()

    private fun getCanvasFor(bmp: Bitmap): Canvas {
        val c = cachedCanvas
        if (c != null && cachedCanvasBitmap === bmp) return c
        val nc = Canvas(bmp)
        cachedCanvas = nc
        cachedCanvasBitmap = bmp
        return nc
    }

    fun beginStroke(start: Offset? = null) {
        lastSmoothedPoint = null
        prevCurvePoint = null
        velocityEma = 0f
        euroX = 0f
        euroY = 0f
        euroDx = 0f
        euroInit = false
        blendPickup = null
        lastVelocity = Offset.Zero
        dirtyTiles.clear()
        dirtyTileBounds.setEmpty()
        smudgeHasInk = false
        // FORCE ala ibisPaint: penggaris nyala = SEMUA stroke menempel penuh ke
        // penggaris (bukan cuma yang kebetulan mulai di dekatnya — dulu gerbang
        // toleransi 90px kanvas membuat penggaris terasa "nyala tapi tidak
        // berguna, hanya visual"). Mode atur penggaris tetap bebas untuk UI.
        rulerLocked = rulerGuide.type != RulerType.OFF && !rulerAdjustMode
    }

    fun endStroke() {
        lastSmoothedPoint = null
        prevCurvePoint = null
        velocityEma = 0f
        euroInit = false
        blendPickup = null
        lastVelocity = Offset.Zero
        smudgeHasInk = false
        rulerLocked = false
    }

    /** Snap penggaris (per-stroke). Bila stroke dikunci, proyeksi dipakai penuh
     *  sehingga goresan lurus rapi; bila tidak, kanvas bebas. */
    private fun snapGuided(p: Offset): Offset =
        if (rulerLocked) rulerGuide.project(p) else p

    /** Prediksi titik berikut dari kecepatan (Ink prediction, murah, tanpa API baru). */
    fun predictNext(current: Offset, previous: Offset?): Offset {
        if (previous == null) return current
        val v = Offset(current.x - previous.x, current.y - previous.y)
        lastVelocity = Offset(
            lastVelocity.x * 0.6f + v.x * 0.4f,
            lastVelocity.y * 0.6f + v.y * 0.4f
        )
        return Offset(current.x + lastVelocity.x * 0.35f, current.y + lastVelocity.y * 0.35f)
    }

    /** Tandai tile 64px tersentuh (MyPaint) + gabungkan bounds kotor. */
    fun markDirtyTiles(l: Float, t: Float, r: Float, b: Float) {
        val tile = 64
        val x0 = (l / tile).toInt().coerceAtLeast(0)
        val y0 = (t / tile).toInt().coerceAtLeast(0)
        val x1 = (r / tile).toInt().coerceAtLeast(x0)
        val y1 = (b / tile).toInt().coerceAtLeast(y0)
        for (ty in y0..y1) for (tx in x0..x1) {
            dirtyTiles.add((tx.toLong() shl 32) or ty.toLong())
        }
        if (dirtyTileBounds.isEmpty) dirtyTileBounds.set(l, t, r, b)
        else dirtyTileBounds.union(l, t, r, b)
        if (dirtyTiles.size > 4096) {
            dirtyTiles.clear()
            dirtyTiles.add((x0.toLong() shl 32) or y0.toLong())
        }
    }

    fun consumeDirtyTiles(): Set<Long> {
        val out = dirtyTiles.toSet()
        dirtyTiles.clear()
        return out
    }

    fun consumeDirtyBounds(): RectF {
        val out = RectF(dirtyTileBounds)
        dirtyTileBounds.setEmpty()
        return out
    }

    /**
     * Stabilizer One-Euro: lowpass adaptif (lambat = smoothing kuat, cepat =
     * mengikuti). strength 0..1 dipetakan ke minCutoff 2.5..0.6.
     * Plus noise gate 2px agar jitter sensor tak jadi goresan.
     */
    private fun smoothPoint(current: Offset, previous: Offset?, enabled: Boolean): Offset {
        if (!enabled || previous == null) {
            if (enabled && !euroInit) {
                euroX = current.x
                euroY = current.y
                euroDx = 0f
                euroInit = true
            }
            return current
        }
        if (!euroInit) {
            euroX = previous.x
            euroY = previous.y
            euroDx = 0f
            euroInit = true
        }
        // Noise gate: abaikan mikro-jitter di bawah 2px.
        val dxRaw = current.x - previous.x
        val dyRaw = current.y - previous.y
        if (dxRaw * dxRaw + dyRaw * dyRaw < 4f) {
            return Offset(euroX, euroY)
        }
        val minCutoff = 2.5f - stabilizer.strength.coerceIn(0f, 1f) * 1.9f
        val beta = 0.02f
        val speed = kotlin.math.hypot(dxRaw, dyRaw)
        euroDx = euroDx + 0.25f * (speed - euroDx)
        val cutoff = minCutoff + beta * euroDx
        val alpha = cutoff / (cutoff + 1f)
        euroX += alpha * (current.x - euroX)
        euroY += alpha * (current.y - euroY)
        return Offset(euroX, euroY)
    }

    private fun createBasePaint(isHuge: Boolean = false): Paint {
        val key = 31 * (31 * (31 * brushType.ordinal + size.toBits()) + color) + opacity.toBits() + (if (isHuge) 1 else 0)
        cachedPaint?.let { if (cachedPaintKey == key) return Paint(it) }
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            color = this@BrushEngine.color
            alpha = (this@BrushEngine.opacity * 255).toInt().coerceIn(0, 255)
        }

        // Huge canvas (>4MP) : hindari BlurMaskFilter yang bikin rasterisasi
        // 46MB melebar → delay & OOM. Fallback ke hard edge di path jumbo.

        when (brushType) {
            BrushType.PEN_HARD -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.PEN_SOFT -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.15f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
            }
            BrushType.DIP_HARD -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.DIP_SOFT -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.12f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.FELT_HARD -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 230).toInt().coerceIn(0, 255)
            }
            BrushType.FELT_SOFT -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.18f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.alpha = (this@BrushEngine.opacity * 220).toInt().coerceIn(0, 255)
            }
            BrushType.BALLPOINT -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = size * 0.7f
            }
            BrushType.G_PEN -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.PENCIL -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.08f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 200).toInt().coerceIn(0, 255)
            }
            BrushType.INK -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            BrushType.WATERCOLOR -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(2f, size * 0.25f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 120).toInt().coerceIn(0, 255)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
            BrushType.OIL -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            BrushType.MARKER -> {
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.MITER
                paint.alpha = (this@BrushEngine.opacity * 180).toInt().coerceIn(0, 255)
                // Marker asli makin gelap saat tumpang tindih → MULTIPLY.
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            BrushType.FLAT -> {
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.BEVEL
                paint.alpha = (this@BrushEngine.opacity * 200).toInt().coerceIn(0, 255)
            }
            BrushType.ROUND -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.12f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 210).toInt().coerceIn(0, 255)
            }
            BrushType.CRAYON -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 150).toInt().coerceIn(0, 255)
                // Grain: lihat drawDab (beberapa garis tipis ber-jitter tetap).
            }
            BrushType.BLEND -> {
                // Cap smudge diambil dari kanvas (applySmudgeStroke); paint ini
                // hanya jalur dot/tap agar tetap terasa.
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 220).toInt().coerceIn(0, 255)
            }
            BrushType.DODGE -> {
                // Dodge (Lighten): menambah cahaya (ADD) → area makin terang.
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.2f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 90).toInt().coerceIn(0, 255)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            BrushType.BURN -> {
                // Burn (Darken): menggelapkan bertumpuk seperti pensil shading.
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(1f, size * 0.2f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (this@BrushEngine.opacity * 150).toInt().coerceIn(0, 255)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            BrushType.AIRBRUSH -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(3f, size * 0.4f), BlurMaskFilter.Blur.NORMAL)
                paint.alpha = (this@BrushEngine.opacity * 100).toInt().coerceIn(0, 255)
            }
            BrushType.AIR_FAN -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(3f, size * 0.5f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = size * 1.5f
                paint.alpha = (this@BrushEngine.opacity * 70).toInt().coerceIn(0, 255)
            }
            BrushType.ERASER -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            BrushType.ERASER_SOFT -> {
                if (!isHuge) paint.maskFilter = BlurMaskFilter(max(2f, size * 0.3f), BlurMaskFilter.Blur.NORMAL)
                paint.strokeCap = Paint.Cap.ROUND
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            BrushType.BLUR -> {
                // Handled separately
            }
            BrushType.OBJECT_ERASER -> {
                // Ditangani di CanvasEditorScreen via Content-Aware Fill, bukan draw langsung
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                paint.alpha = 0
            }
            BrushType.HEAL_MIGAN -> {
                // Ditangani di CanvasEditorScreen via MiGAN on-device, bukan draw langsung
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                paint.alpha = 0
            }
        }
        cachedPaint = Paint(paint)
        cachedPaintKey = key
        return paint
    }

    // ibisPaint-style: dip pen pressure simulation based on velocity.
    // Lowpass eksponensial ala MyPaint agar lebar tak bergetar per-event.
    // G Pen memakai rentang lebih lebar (dinamika manga tegas).
    private fun getVelocityFactor(distance: Float, wide: Boolean = false): Float {
        velocityEma += 0.35f * (distance - velocityEma)
        val v = velocityEma
        val lo = if (wide) 0.4f else 0.6f
        val hi = if (wide) 1.6f else 1.3f
        // Slower = thicker, faster = thinner (like real ink pen)
        return when {
            v < 2f -> hi
            v > 15f -> lo
            else -> (hi - (v - 2f) * (hi - lo) / 13f).coerceIn(lo, hi)
        }
    }

    /** Taper buatan kepala/ekor untuk jari/mouse (Touch Taper ala Procreate). */
    private fun fingerTaper(progress: Float, taperLen: Float = 0.12f): Float {
        if (progress <= 0f || progress >= 1f) return 0.35f
        val head = (progress / taperLen).coerceIn(0f, 1f)
        val tail = ((1f - progress) / taperLen).coerceIn(0f, 1f)
        // smoothstep agar transisi halus
        fun ss(t: Float) = t * t * (3f - 2f * t)
        return (0.35f + 0.65f * minOf(ss(head), ss(tail))).coerceIn(0.35f, 1f)
    }

    fun strokeSegmentOnLayer(layer: DrawingLayer, p1: Offset, p2: Offset, progressFraction: Float = 1.0f, visibleRect: RectF? = null) {
        // Hapus Objek / Heal MiGAN tidak menggambar langsung; akumulasi mask di CanvasEditorScreen
        if (brushType == BrushType.OBJECT_ERASER || brushType == BrushType.HEAL_MIGAN) return

        val bmp = layer.getPersistentBitmap()
        val isHuge = bmp.width.toLong() * bmp.height > HUGE_CANVAS_PIXELS
        // Huge: jangan matikan stabilizer global (mutableState picu recompose tiap
        // segmen + nonaktifkan permanen). Pakai flag lokal saja agar hemat CPU
        // tanpa efek samping UI.
        // Stabilizer dimatikan saat terkunci ke penggaris supaya garis TEPAT
        // menempel pada penggaris (tanpa lag/belok dari filter smoothing).
        val useStabilizer = stabilizer.isEnabled && !isHuge && !rulerLocked

        val smoothedP1 = smoothPoint(snapGuided(p1), lastSmoothedPoint, useStabilizer)
        val smoothedP2 = smoothPoint(snapGuided(p2), smoothedP1, useStabilizer)
        lastSmoothedPoint = smoothedP2

        if (brushType == BrushType.BLUR) {
            // Blur NYATA per segmen (box blur 3 pass). Region dibatasi keras di
            // applyBlurStroke sehingga tetap responsif di 720x16000; hanya
            // lompatan antar-event yang ekstrem yang dilewati.
            val jump = hypot(smoothedP2.x - smoothedP1.x, smoothedP2.y - smoothedP1.y)
            if (!(isHuge && jump > 160f)) {
                applyBlurStroke(layer, smoothedP1, smoothedP2, isHuge)
            }
            prevCurvePoint = smoothedP2
            return
        }
        if (brushType == BrushType.BLEND) {
            // Smudge sejati: cap diambil dari kanvas pada dab sebelumnya
            // (referensi: losingfight.com "How to implement smudge and stamp
            // tools"). Versi lama hanya menint warna → bukan smudge.
            applySmudgeStroke(layer, smoothedP1, smoothedP2, isHuge)
            prevCurvePoint = smoothedP2
            return
        }

        // Interpolasi kuadratik antar segmen (midpoint quadratic Bezier):
        // kurva dari titik tengah segmen sebelumnya ke titik tengah segmen
        // ini dengan kontrol di smoothedP1 — menghaluskan sambungan antar
        // segmen lurus sehingga goresan tidak terlihat "patah-patah".
        val curveStart: Offset
        val curveControl: Offset?
        val curveEnd: Offset
        val prevCurve = prevCurvePoint
        if (prevCurve != null && !rulerLocked) {
            curveControl = smoothedP1
            curveStart = Offset((prevCurve.x + smoothedP1.x) / 2f, (prevCurve.y + smoothedP1.y) / 2f)
            curveEnd = Offset((smoothedP1.x + smoothedP2.x) / 2f, (smoothedP1.y + smoothedP2.y) / 2f)
        } else {
            curveControl = null
            curveStart = smoothedP1
            curveEnd = smoothedP2
        }
        prevCurvePoint = smoothedP2

        // Viewport culling brush (diadaptasi dari CanvasView.drawLayers Vasilias:
        // lewati raster bila segmen sepenuhnya di luar jendela terlihat).
        // Modifikasi Groox: padding = radius brush + clipPad agar tepi tidak
        // terpotong; null = tanpa culling (kompatibel pemanggil lama).
        // Cara pakai di 720x16000: oper visibleRect dari CanvasEditorScreen
        // (hitung dari viewState.scale/offset + viewportSize) saat zoom-in
        // agar sapuan di luar layar tidak membebani CPU/GPU.
        visibleRect?.let { vr ->
            val pad = size * 1.5f + 12f
            val segL = min(min(curveStart.x, curveEnd.x), curveControl?.x ?: curveStart.x) - pad
            val segT = min(min(curveStart.y, curveEnd.y), curveControl?.y ?: curveStart.y) - pad
            val segR = max(max(curveStart.x, curveEnd.x), curveControl?.x ?: curveEnd.x) + pad
            val segB = max(max(curveStart.y, curveEnd.y), curveControl?.y ?: curveEnd.y) + pad
            if (segR < vr.left || segL > vr.right || segB < vr.top || segT > vr.bottom) {
                layer.markDirty()
                return
            }
        }

        val canvas = getCanvasFor(bmp)

        val paint = createBasePaint(isHuge)
        if (layer.isAlphaLocked) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        // (BLEND/smudge ditangani applySmudgeStroke di atas, bukan tint warna.)

        val distance = hypot(smoothedP2.x - smoothedP1.x, smoothedP2.y - smoothedP1.y)

        // Titik tunggal (tap): pastikan jadi dot bulat, bukan hilang.
        if (distance < 1f) {
            val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(smoothedP2.x, smoothedP2.y, max(0.5f, paint.strokeWidth / 2f), dotPaint)
            layer.markDirty()
            return
        }

        // Apply velocity-based width for Ink/Dip pen style
        if (brushType == BrushType.INK || brushType == BrushType.PEN_SOFT) {
            val velocityFactor = getVelocityFactor(distance)
            paint.strokeWidth = size * velocityFactor
        } else if (brushType == BrushType.G_PEN) {
            paint.strokeWidth = size * getVelocityFactor(distance, wide = true)
        }

        // Force fade / taper
        if (forceFade.isEnabled || brushType == BrushType.INK) {
            val factor = if (progressFraction < forceFade.startFade && forceFade.startFade > 0f) {
                progressFraction / forceFade.startFade
            } else if (progressFraction > (1f - forceFade.endFade) && forceFade.endFade > 0f) {
                (1f - progressFraction) / forceFade.endFade
            } else {
                1.0f
            }.coerceIn(0.1f, 1.0f)

            paint.strokeWidth = paint.strokeWidth * factor
            paint.alpha = ((paint.alpha / 255f) * factor * 255).toInt().coerceIn(0, 255)
        } else if ((brushType == BrushType.PEN_HARD || brushType == BrushType.PENCIL) &&
            progressFraction > 0f && progressFraction < 1f
        ) {
            // Taper jari ringan agar goresan pena terasa kaligrafi.
            val t = fingerTaper(progressFraction)
            paint.strokeWidth = paint.strokeWidth * (0.6f + 0.4f * t)
        }

        // Interpolasi stamp agar tidak patah-patah saat jari bergerak cepat.
        // Huge: spacing lebih renggang + cap steps lebih kecil → hemat drawCall.
        // Cap 32 (bukan 64) agar 720x16000 tidak menumpuk drawLine per event.
        val spacing = if (isHuge) max(3f, paint.strokeWidth * 0.35f) else max(1.5f, paint.strokeWidth * 0.2f)
        // Panjang jalur: chord + deviasi kurva bila smoothing kuadratik aktif.
        val pathLen = if (curveControl != null) {
            distance + hypot(curveControl.x - curveStart.x, curveControl.y - curveStart.y) * 0.5f
        } else distance
        val maxSteps = if (isHuge) 32 else 256
        val steps = ceil((pathLen / spacing).toDouble()).toInt().coerceIn(1, maxSteps)

        // Batasi rasterisasi ke dirty rect segmen. Tanpa clip, pada kanvas
        // 720x16000 mask-filter blur meraster area jauh lebih besar dari
        // yang terlihat → segmen lambat & goresan terasa patah.
        val clipPad = paint.strokeWidth * 1.5f + 12f
        val ctrl = curveControl
        val cl = (min(min(curveStart.x, curveEnd.x), ctrl?.x ?: curveStart.x) - clipPad)
            .coerceIn(0f, bmp.width.toFloat())
        val ct = (min(min(curveStart.y, curveEnd.y), ctrl?.y ?: curveStart.y) - clipPad)
            .coerceIn(0f, bmp.height.toFloat())
        val cr = (max(max(curveStart.x, curveEnd.x), ctrl?.x ?: curveEnd.x) + clipPad)
            .coerceIn(0f, bmp.width.toFloat())
        val cb = (max(max(curveStart.y, curveEnd.y), ctrl?.y ?: curveEnd.y) + clipPad)
            .coerceIn(0f, bmp.height.toFloat())
        if (cr - cl < 1f || cb - ct < 1f) {
            layer.markDirty()
            return
        }
        // MyPaint 64px tiles: catat tile kotor untuk composite inkremental.
        markDirtyTiles(cl, ct, cr, cb)
        canvas.save()
        canvas.clipRect(cl, ct, cr, cb)

        // OIL highlight dibuat SEKALI per segmen lalu dipakai ulang untuk semua
        // dab. Versi lama mengalokasi Paint baru per dab (hingga 32x per segmen)
        // → GC churn + delay di kanvas jumbo. Huge: highlight dimatikan
        // (single-pass) agar 3x lebih sedikit drawCall.
        val oilHighlight: Paint? = if (brushType == BrushType.OIL && !isHuge) {
            Paint(paint).apply {
                alpha = (paint.alpha * 0.4f).toInt()
                strokeWidth = paint.strokeWidth * 0.6f
                color = Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
        } else null
        if (isHuge) {
            // Huge 720x16000: SATU drawPath per segmen menggantikan loop 32
            // drawLine. Skia me-raster seluruh kurva kuadratik dalam satu pass
            // (presisi penuh, tanpa sampling dab) → 10-30x lebih sedikit
            // draw-call per event; sapuan cepat tidak lagi delay/patah.
            // Bonus: alpha tipis (Watercolor/Marker/Airbrush) tidak menumpuk
            // di overlap antar-dab → warna konsisten di sepanjang goresan.
            // BlurMaskFilter sudah nonaktif di huge sehingga tepi tetap tajam.
            strokePath.rewind()
            strokePath.moveTo(curveStart.x, curveStart.y)
            if (ctrl != null) {
                strokePath.quadTo(ctrl.x, ctrl.y, curveEnd.x, curveEnd.y)
            } else {
                strokePath.lineTo(curveEnd.x, curveEnd.y)
            }
            canvas.drawPath(strokePath, paint)
        } else {
            var prevX = curveStart.x
            var prevY = curveStart.y
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x: Float
                val y: Float
                if (ctrl != null) {
                    val u = 1f - t
                    x = u * u * curveStart.x + 2f * u * t * ctrl.x + t * t * curveEnd.x
                    y = u * u * curveStart.y + 2f * u * t * ctrl.y + t * t * curveEnd.y
                } else {
                    x = curveStart.x + (curveEnd.x - curveStart.x) * t
                    y = curveStart.y + (curveEnd.y - curveStart.y) * t
                }
                drawDab(canvas, paint, prevX, prevY, x, y, isHuge, oilHighlight)
                prevX = x
                prevY = y
            }
        }
        canvas.restore()

        // TileMap bukan sumber render (renderComposite memakai compositeBitmap),
        // jadi jangan sync per-segmen yang O(W*H). Sync dilakukan di syncTiles()
        // saat stroke selesai / undo / flatten.
        layer.markDirty()
    }

    /** Sinkronisasi tile cache dari bitmap persisten. Panggil saat stroke selesai. */
    fun syncTiles(layer: DrawingLayer) {
        layer.tileMap.importFromBitmap(layer.getPersistentBitmap())
    }

    private fun drawDab(canvas: Canvas, paint: Paint, x1: Float, y1: Float, x2: Float, y2: Float, isHuge: Boolean = false, oilHighlight: Paint? = null) {
        // Watercolor: draw multiple overlapping strokes for texture.
        // Huge: single-pass agar 3x lebih hemat drawCall di 720x16000.
        if (brushType == BrushType.WATERCOLOR) {
            if (isHuge) {
                canvas.drawLine(x1, y1, x2, y2, paint)
            } else {
                val baseAlpha = paint.alpha
                val baseWidth = paint.strokeWidth
                for (i in 1..3) {
                    paint.alpha = (baseAlpha * (0.3f + i * 0.2f)).toInt().coerceIn(0, 255)
                    paint.strokeWidth = baseWidth * (0.7f + i * 0.15f)
                    val jitterX = (i - 2) * baseWidth * 0.1f
                    val jitterY = (i - 2) * baseWidth * 0.08f
                    canvas.drawLine(x1 + jitterX, y1 + jitterY, x2 + jitterX, y2 + jitterY, paint)
                }
                paint.alpha = baseAlpha
                paint.strokeWidth = baseWidth
            }
        } else if (brushType == BrushType.AIR_FAN) {
            // Fan: 3 garis menyebar (kipas) dengan alpha rendah per helai.
            // Huge: single-pass agar hemat drawCall di 720x16000.
            if (isHuge) {
                canvas.drawLine(x1, y1, x2, y2, paint)
            } else {
                val baseAlpha = paint.alpha
                val baseWidth = paint.strokeWidth
                val fanPaint = Paint(paint).apply { alpha = (baseAlpha / 3).coerceIn(1, 255) }
                val spread = baseWidth * 0.35f
                val dx = x2 - x1
                val dy = y2 - y1
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                val nx = -dy / len * spread
                val ny = dx / len * spread
                canvas.drawLine(x1, y1, x2, y2, fanPaint)
                canvas.drawLine(x1 + nx, y1 + ny, x2 + nx, y2 + ny, fanPaint)
                canvas.drawLine(x1 - nx, y1 - ny, x2 - nx, y2 - ny, fanPaint)
            }
        } else if (brushType == BrushType.OIL) {
            // Oil: draw core + edge highlight (highlight dipakai ulang per segmen).
            // Huge: core saja (hemat 2x drawCall + tanpa alokasi).
            canvas.drawLine(x1, y1, x2, y2, paint)
            if (!isHuge && oilHighlight != null) {
                canvas.drawLine(x1, y1, x2, y2, oilHighlight)
            } else if (!isHuge && oilHighlight == null) {
                // Fallback bila dipanggil tanpa cache (kanvas kecil, path lama).
                val highlight = Paint(paint).apply {
                    alpha = (paint.alpha * 0.4f).toInt()
                    strokeWidth = paint.strokeWidth * 0.6f
                    color = Color.WHITE
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                }
                canvas.drawLine(x1, y1, x2, y2, highlight)
            }
        } else if (brushType == BrushType.CRAYON || brushType == BrushType.PENCIL) {
            // Grain krayon/pensil: beberapa garis tipis ber-jitter, jitter-nya
            // deterministik dari posisi (hash) supaya goresan tidak "berkedip".
            val grain = if (brushType == BrushType.CRAYON) 4 else 3
            val baseW = paint.strokeWidth
            val baseA = paint.alpha
            val amp = if (brushType == BrushType.CRAYON) baseW * 0.35f else baseW * 0.22f
            paint.strokeWidth = baseW * (if (brushType == BrushType.CRAYON) 0.55f else 0.45f)
            for (g in 0 until grain) {
                val jx = jitter(x1, y1, g * 2 + 1) * amp
                val jy = jitter(x2, y2, g * 2 + 2) * amp
                paint.alpha = (baseA * (if (g == 0) 0.8f else 0.45f)).toInt().coerceIn(1, 255)
                canvas.drawLine(x1 + jx, y1 + jy, x2 + jx, y2 + jy, paint)
            }
            paint.strokeWidth = baseW
            paint.alpha = baseA
        } else {
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    /** Jitter deterministik (-0.5..0.5) dari posisi; bukan Random agar hasil
     *  goresan stabil saat kanvas di-render ulang / undo. */
    private fun jitter(x: Float, y: Float, seed: Int): Float {
        var h = (x.toInt() * 374761393) xor (y.toInt() * 668265263) xor (seed * 1274126177)
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        return (((h ushr 8) and 0xFF) / 255f) - 0.5f
    }

    private fun applyBlurStroke(layer: DrawingLayer, p1: Offset, p2: Offset, isHuge: Boolean = false) {
        val bmp = layer.getPersistentBitmap()
        // Radius blur yang benar-benar terlihat.
        // CATATAN PENTING: Skia BlurMaskFilter TIDAK mem-blur piksel bitmap
        // (hanya alpha mask geometri) — inilah sebab brush Blur versi lama
        // terlihat "tidak berfungsi" (hasilnya identik dengan aslinya).
        // Di sini piksel benar-benar diblur: box blur 3 pass ≈ Gaussian.
        val radius = max(2f, size * 0.6f)
        val pad = (size + radius * 2f + 4f)

        // Region dibatasi (cap sisi). Batas lama 140_000L px² membatalkan
        // sapuan normal di kanvas 720x16000; cap sisi ini setara tapi tidak
        // pernah menelan sapuan wajar.
        val maxSide = if (isHuge) 256f else MAX_BLUR_SIDE
        val mx = (p1.x + p2.x) / 2f
        val my = (p1.y + p2.y) / 2f
        val leftF = max(min(p1.x, p2.x) - pad, mx - maxSide / 2f)
        val topF = max(min(p1.y, p2.y) - pad, my - maxSide / 2f)
        val rightF = min(max(p1.x, p2.x) + pad, mx + maxSide / 2f)
        val bottomF = min(max(p1.y, p2.y) + pad, my + maxSide / 2f)

        val left = leftF.toInt().coerceIn(0, bmp.width - 1)
        val top = topF.toInt().coerceIn(0, bmp.height - 1)
        val right = rightF.toInt().coerceIn(1, bmp.width)
        val bottom = bottomF.toInt().coerceIn(1, bmp.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return

        // Hanya proses dirty rect, bukan seluruh kanvas (hemat CPU/GC per segmen).
        // Semua alokasi dilindungi OOM + recycle di finally agar tidak bocor.
        var region: Bitmap? = null
        var blurred: Bitmap? = null
        var maskBmp: Bitmap? = null
        var tempLayer: Bitmap? = null
        try {
            region = Bitmap.createBitmap(bmp, left, top, w, h)
            blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // Blur piksel nyata (bukan BlurMaskFilter pada bitmap = no-op).
            boxBlurBitmap(region!!, blurred!!, radius.toInt().coerceIn(1, 64))

            maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(maskBmp!!)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = size * 1.2f
                color = Color.BLACK
                // MaskFilter pada GEOMETRI (garis) memang bekerja → tepi mask lembut.
                maskFilter = BlurMaskFilter(max(1f, size * 0.25f), BlurMaskFilter.Blur.NORMAL)
            }
            maskCanvas.drawLine(p1.x - left, p1.y - top, p2.x - left, p2.y - top, strokePaint)

            tempLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(tempLayer!!)
            tempCanvas.drawBitmap(blurred!!, 0f, 0f, null)
            val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            tempCanvas.drawBitmap(maskBmp!!, 0f, 0f, dstIn)

            Canvas(bmp).drawBitmap(tempLayer!!, left.toFloat(), top.toFloat(), null)
            layer.markDirty()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { region?.recycle() }
            runCatching { blurred?.recycle() }
            runCatching { maskBmp?.recycle() }
            runCatching { tempLayer?.recycle() }
        }
    }

    /**
     * Blur piksel nyata (box blur 3 pass ≈ Gaussian) untuk region kecil.
     * Rata-rata dihitung di ruang premultiplied supaya piksel transparan tidak
     * memunculkan pinggiran gelap. Scratch array dipakai ulang (tanpa alokasi
     * per segmen) agar tidak ada GC churn saat menggores panjang.
     */
    private fun boxBlurBitmap(src: Bitmap, dst: Bitmap, radius: Int) {
        val w = src.width
        val h = src.height
        val n = w * h
        if (n <= 0 || dst.width != w || dst.height != h) return
        var a = blurScratch
        if (a == null || a.size < n) { a = IntArray(n); blurScratch = a }
        var b = blurScratchB
        if (b == null || b.size < n) { b = IntArray(n); blurScratchB = b }
        val px = a
        val tmp = b
        src.getPixels(px, 0, w, 0, 0, w, h)
        val r = radius.coerceIn(1, 64)
        repeat(3) {
            boxBlurH(px, tmp, w, h, r)
            boxBlurV(tmp, px, w, h, r)
        }
        dst.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            val row = y * w
            var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                val al = ((c ushr 24) and 0xFF).toFloat()
                sa += al
                sr += ((c ushr 16) and 0xFF) * al
                sg += ((c ushr 8) and 0xFF) * al
                sb += (c and 0xFF) * al
            }
            for (x in 0 until w) {
                val alo = sa / div
                dst[row + x] = if (alo <= 0.5f) 0 else {
                    val ao = alo.coerceIn(0f, 255f).toInt()
                    val ro = (sr / sa).coerceIn(0f, 255f).toInt()
                    val go = (sg / sa).coerceIn(0f, 255f).toInt()
                    val bo = (sb / sa).coerceIn(0f, 255f).toInt()
                    (ao shl 24) or (ro shl 16) or (go shl 8) or bo
                }
                val outC = src[row + (x - r).coerceIn(0, w - 1)]
                val inC = src[row + (x + r + 1).coerceIn(0, w - 1)]
                val ao1 = ((outC ushr 24) and 0xFF).toFloat()
                val ai1 = ((inC ushr 24) and 0xFF).toFloat()
                sa += ai1 - ao1
                sr += ((inC ushr 16) and 0xFF) * ai1 - ((outC ushr 16) and 0xFF) * ao1
                sg += ((inC ushr 8) and 0xFF) * ai1 - ((outC ushr 8) and 0xFF) * ao1
                sb += (inC and 0xFF) * ai1 - (outC and 0xFF) * ao1
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = (2 * r + 1).toFloat()
        for (x in 0 until w) {
            var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                val al = ((c ushr 24) and 0xFF).toFloat()
                sa += al
                sr += ((c ushr 16) and 0xFF) * al
                sg += ((c ushr 8) and 0xFF) * al
                sb += (c and 0xFF) * al
            }
            for (y in 0 until h) {
                val alo = sa / div
                dst[y * w + x] = if (alo <= 0.5f) 0 else {
                    val ao = alo.coerceIn(0f, 255f).toInt()
                    val ro = (sr / sa).coerceIn(0f, 255f).toInt()
                    val go = (sg / sa).coerceIn(0f, 255f).toInt()
                    val bo = (sb / sa).coerceIn(0f, 255f).toInt()
                    (ao shl 24) or (ro shl 16) or (go shl 8) or bo
                }
                val outC = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inC = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val ao1 = ((outC ushr 24) and 0xFF).toFloat()
                val ai1 = ((inC ushr 24) and 0xFF).toFloat()
                sa += ai1 - ao1
                sr += ((inC ushr 16) and 0xFF) * ai1 - ((outC ushr 16) and 0xFF) * ao1
                sg += ((inC ushr 8) and 0xFF) * ai1 - ((outC ushr 8) and 0xFF) * ao1
                sb += (inC and 0xFF) * ai1 - (outC and 0xFF) * ao1
            }
        }
    }

    /** Siapkan (sekali per ukuran) cap smudge + mask lingkaran + temp. */
    private fun ensureSmudgeScratch(dim: Int): Boolean {
        if (dim <= 0) return false
        val b0 = smudgeBuf; val m0 = smudgeMask; val t0 = smudgeTemp
        if (smudgeDim == dim && b0 != null && !b0.isRecycled && m0 != null && !m0.isRecycled && t0 != null && !t0.isRecycled) return true
        runCatching { b0?.recycle() }
        runCatching { m0?.recycle() }
        runCatching { t0?.recycle() }
        smudgeBuf = null; smudgeMask = null; smudgeTemp = null; smudgeDim = 0
        return try {
            val buf = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val mask = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val temp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val r = dim / 2f
            val rg = android.graphics.RadialGradient(
                r, r, r * 0.95f,
                0xFFFFFFFF.toInt(), 0x00FFFFFF,
                android.graphics.Shader.TileMode.CLAMP
            )
            Canvas(mask).drawCircle(r, r, r * 0.95f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rg })
            smudgeBuf = buf; smudgeMask = mask; smudgeTemp = temp; smudgeDim = dim
            true
        } catch (e: OutOfMemoryError) {
            e.printStackTrace(); false
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    /**
     * SMUDGE (brush "Blend"): cap = potongan kanvas sebesar ujung brush pada dab
     * sebelumnya, distempel ke dab sekarang dengan alpha = kekuatan, lalu cap
     * diperbarui dari kanvas (rate tetap) untuk dab berikutnya. Ini algoritma
     * smudge standar: kekuatan/tekanan ⇔ transparansi cap, semakin kuat semakin
     * banyak cat terseret (losingfight.com, "How to implement smudge").
     */
    private fun applySmudgeStroke(layer: DrawingLayer, p1: Offset, p2: Offset, isHuge: Boolean = false) {
        val bmp = layer.getPersistentBitmap()
        val radius = (size / 2f).coerceIn(4f, 128f)
        val dim = (radius.toInt() * 2).coerceAtLeast(8)
        if (!ensureSmudgeScratch(dim)) return
        val buf = smudgeBuf ?: return
        val mask = smudgeMask ?: return
        val temp = smudgeTemp ?: return
        val bufCanvas = Canvas(buf)
        val tempCanvas = Canvas(temp)
        val canvas = Canvas(bmp)
        val strength = (opacity * 0.65f).coerceIn(0.05f, 1f)
        smudgeStamp.alpha = (strength * 255).toInt().coerceIn(1, 255)
        smudgeRefill.alpha = 128

        val dist = hypot(p2.x - p1.x, p2.y - p1.y)
        val spacing = max(1.5f, radius * 0.5f)
        val steps = ceil((dist / spacing).toDouble()).toInt().coerceIn(1, if (isHuge) 48 else 256)
        if (bmp.width < dim || bmp.height < dim) return
        try {
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x = p1.x + (p2.x - p1.x) * t
                val y = p1.y + (p2.y - p1.y) * t
                val left = (x - radius).toInt().coerceIn(0, bmp.width - dim)
                val top = (y - radius).toInt().coerceIn(0, bmp.height - dim)
                if (!smudgeHasInk) {
                    // Dab pertama hanya mengambil cap (belum melukis) — sesuai
                    // perilaku smudge standar.
                    bufCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
                    bufCanvas.drawBitmap(bmp, Rect(left, top, left + dim, top + dim), Rect(0, 0, dim, dim), null)
                    smudgeHasInk = true
                    continue
                }
                // 1) cap × mask lingkaran → temp, lalu stempel ke kanvas.
                tempCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
                tempCanvas.drawBitmap(buf, 0f, 0f, null)
                tempCanvas.drawBitmap(mask, 0f, 0f, maskDstIn)
                canvas.drawBitmap(temp, left.toFloat(), top.toFloat(), smudgeStamp)
                // 2) segarkan cap dari kanvas agar smear berlanjut ke dab berikut.
                bufCanvas.drawBitmap(bmp, Rect(left, top, left + dim, top + dim), Rect(0, 0, dim, dim), smudgeRefill)
            }
            layer.markDirty()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Panduan + helper brush untuk kanvas jangkung 720x16000.
 * Diadaptasi dari VasiliasTyper `view/CanvasView.kt` (viewport culling,
 * invalidate throttling 16ms, reusable Paint) + `engine/ProcessingConfig.kt`
 * dengan modifikasi ambang Groox (HUGE=4MP, blur cap 140k, steps 32).
 *
 * Cara pakai brush di 720x16000 (ringkas):
 * 1. Zoom-in 100-200% ke area kerja (viewport culling: hanya jendela
 *    ~720x1000 yang di-upload GPU, bukan 11,5MP penuh).
 * 2. Pakai Pen Hard/Ink/Eraser untuk lineart; hindari Airbrush/Watercolor/
 *    Blur radius besar dalam sekali sapu panjang (matikan BlurMaskFilter
 *    otomatis di huge, blur live dibatasi 140_000px).
 * 3. Sapuan panjang: sistem interpolasi max 24 titik luar + 32 steps dalam
 *    (tidak menumpuk 2000+ drawLine per event).
 * 4. Hapus Objek: sapu untuk akumulasi mask, commit Content-Aware Fill
 *    hanya crop dirty (bukan scan 46MB), mask 46MB di-recycle setelah commit.
 * 5. Bila patah-patah: kecilkan size (<32px), pakai 1 layer (fast-path blit
 *    ~50x50px vs render 46MB), tutup teks/blend di luar area.
 */
object BrushHugeGuide {
    /** Interval throttle recompose saat drag di kanvas huge (diadaptasi Vasilias 16ms). */
    const val HUGE_BRUSH_THROTTLE_MS = 16L

    fun isHugeCanvas(w: Int, h: Int): Boolean =
        w.toLong() * h.toLong() > HUGE_CANVAS_PIXELS

    /**
     * Hitung jendela kanvas terlihat dari viewState (untuk culling brush).
     * Diadaptasi dari CanvasEditorScreen viewport culling (pivot-aware).
     * Rotasi !=0 -> kembalikan null (skip culling, gambar penuh) karena
     * matematika pivot-rotasi kompleks; konservatif aman.
     */
    fun visibleRect(
        viewportW: Float, viewportH: Float,
        scale: Float, offsetX: Float, offsetY: Float,
        pivotFracX: Float = 0.5f, pivotFracY: Float = 0.5f,
        rotation: Float = 0f
    ): RectF? {
        if (viewportW <= 1f || viewportH <= 1f || scale <= 0f) return null
        if (rotation != 0f) return null // skip culling saat rotate, aman
        // Inverse dari graphicsLayer(pivot, scale, offset) — sama persis dengan
        // screenToCanvasCoordinates & drawVisibleBitmap di CanvasEditorScreen.
        val pivX = pivotFracX * viewportW
        val pivY = pivotFracY * viewportH
        val sc = scale.coerceAtLeast(0.05f)
        val xa = ((0f - pivX - offsetX) / sc) + pivX
        val ya = ((0f - pivY - offsetY) / sc) + pivY
        val xb = ((viewportW - pivX - offsetX) / sc) + pivX
        val yb = ((viewportH - pivY - offsetY) / sc) + pivY
        val l = minOf(xa, xb)
        val t = minOf(ya, yb)
        val r = maxOf(xa, xb)
        val b = maxOf(ya, yb)
        // Jika viewport hampir mencakup seluruh kanvas, jangan cull (biar stroke
        // di tepi tidak terpotong akibat pembulatan).
        // Caller akan clamp ke [0, canvas] sendiri.
        return RectF(l, t, r, b)
    }
}

/**
 * Throttle 16ms untuk refresh brush di kanvas huge (port
 * CanvasView.invalidateDrag Vasilias, modifikasi untuk Compose:
 * pemanggil memutuskan refreshCanvasLight vs tunda).
 */
class BrushFrameThrottle(private val intervalMs: Long = BrushHugeGuide.HUGE_BRUSH_THROTTLE_MS) {
    private var lastMs: Long = 0L
    /** True bila boleh refresh sekarang (sekaligus update timestamp). */
    fun shouldRefreshNow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMs >= intervalMs) {
            lastMs = now
            return true
        }
        return false
    }
    fun reset() { lastMs = 0L }
}
