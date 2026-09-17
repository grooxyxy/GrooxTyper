package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grooxtyper.app.native.NativeEngine
import kotlin.math.max
import kotlin.math.min

/**
 * Manajer inpainting (Telea native C++).
 *
 * Performa untuk kanvas jangkung 720x16000 (~11,5MP):
 * - Alih-alih memindai grid tile 1024px penuh (meng-copy tile 1024x1024 =
 *   4MB per tile via Bitmap.createBitmap), mask dipindai dulu secara kasar
 *   untuk menemukan KOMPONEN mask sebenarnya, lalu tiap komponen di-inpaint
 *   hanya pada bounding-box-nya (+padding radius). Region teks biasanya
 *   kecil (mis. 300x60) sehingga native hanya menyentuh ~100KB, bukan 4MB.
 * - Komponen yang bersentuhan diagregasi per strip agar tile tidak overlap
 *   ganda.
 */
enum class InpaintMode(val displayName: String) {
    TELEA("Telea (cepat)"),
    PATCH_MATCH("PatchMatch (bagus)")
}

class InpaintingManager {

    /** Dilatasi mask 1px agar anti-alias tepi teks ikut ter-inpaint (anti-halo). */
    var dilateMask: Boolean = true

    /**
     * Konversi mask ke ARGB_8888 putih bila perlu. Native Telea menolak
     * ALPHA_8 (format mask hemat memori untuk 720x16000); PatchMatch membaca
     * alpha saja sehingga ALPHA_8 sudah langsung kompatibel.
     */
    private fun ensureArgbMask(mask: Bitmap): Pair<Bitmap, Boolean> {
        if (mask.config != Bitmap.Config.ALPHA_8) return mask to false
        val w = mask.width
        val h = mask.height
        val argb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(argb)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        c.drawBitmap(mask, 0f, 0f, p)
        return argb to true
    }

    /** Dilatasi ~1px: blur SOLID pada SALINAN mask, lalu komposit OR kembali. */
    private fun dilateMaskAlpha(mask: Bitmap) {
        try {
            // Salinan diburamkan (tepi alpha mengembang ~1-2px).
            val blurred = mask.copy(mask.config ?: Bitmap.Config.ARGB_8888, true) ?: return
            val cb = Canvas(blurred)
            val p = Paint().apply {
                maskFilter = BlurMaskFilter(1.2f, BlurMaskFilter.Blur.SOLID)
            }
            val snap = blurred.copy(blurred.config ?: Bitmap.Config.ARGB_8888, false)
            if (snap != null) {
                cb.drawBitmap(snap, 0f, 0f, p)
                snap.recycle()
            }
            // OR-kan hasil blur ke mask asli (alpha maksimum = gabungan).
            val cm = Canvas(mask)
            cm.drawBitmap(blurred, 0f, 0f, null)
            blurred.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var mode: InpaintMode = InpaintMode.PATCH_MATCH
    // Heal brush ala Photoshop tapi lebih bagus untuk manga: pilih strategi
    // patch. Default MANGA_SEAMLESS (garis + screentone, riset Xie SIGGRAPH21).
    // mutableState agar pemilih mode di quick slider langsung recompose.
    var healMode: HealMode by mutableStateOf(HealMode.MANGA_SEAMLESS)
    var healFeather: Boolean by mutableStateOf(true)

    /**
     * Inpaint area seleksi (lasso/kotak/bubble) memakai MI-GAN on-device pada
     * crop kecil (bounds + pad). HANYA dipanggil setelah area seleksi dibuat.
     * Fallback Telea bila session MiGan gagal agar fitur tak pernah mati diam.
     * @return true bila ada piksel dikerjakan.
     */
    suspend fun inpaintSelection(
        layer: DrawingLayer,
        selectionMask: Bitmap,
        bounds: android.graphics.RectF,
        appContext: android.content.Context? = null
    ): Boolean {
        val src = layer.getPersistentBitmap()
        if (src.width != selectionMask.width || src.height != selectionMask.height) {
            android.util.Log.w("Inpaint", "selection: ukuran mask ${selectionMask.width}x${selectionMask.height} != src ${src.width}x${src.height}")
            return false
        }
        if (bounds.width() < 4f || bounds.height() < 4f) {
            android.util.Log.w("Inpaint", "selection: bounds terlalu kecil")
            return false
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        val pad = 24
        val cl = maxOf(0, bounds.left.toInt() - pad)
        val ct = maxOf(0, bounds.top.toInt() - pad)
        val cr = minOf(src.width, bounds.right.toInt() + pad)
        val cb = minOf(src.height, bounds.bottom.toInt() + pad)
        val cw = cr - cl
        val ch = cb - ct
        if (cw <= 8 || ch <= 8) return false
        var srcCrop: Bitmap? = null
        var maskCrop: Bitmap? = null
        try {
            srcCrop = Bitmap.createBitmap(src, cl, ct, cw, ch)
            maskCrop = Bitmap.createBitmap(selectionMask, cl, ct, cw, ch)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return false
        }
        try {
            // 1) MI-GAN (khusus seleksi).
            if (appContext != null) {
                try {
                    if (com.grooxtyper.app.ml.MiganInpainter.ensureSession(appContext)) {
                        val out = com.grooxtyper.app.ml.MiganInpainter.inpaint(srcCrop!!, maskCrop!!)
                        if (out != null) {
                            android.graphics.Canvas(src).drawBitmap(out, cl.toFloat(), ct.toFloat(), null)
                            runCatching { out.recycle() }
                            android.util.Log.i("Inpaint", "seleksi MiGan ${cw}x${ch} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                            return true
                        }
                        android.util.Log.w("Inpaint", "MiGan skip: ${com.grooxtyper.app.ml.MiganInpainter.lastError}")
                    } else {
                        android.util.Log.w("Inpaint", "MiGan session gagal: ${com.grooxtyper.app.ml.MiganInpainter.lastError}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                }
            }
            // 2) Fallback Telea tunggal pada crop yang sama.
            try {
                val (argb, tmp) = ensureArgbMask(maskCrop!!)
                try {
                    com.grooxtyper.app.native.NativeEngine.nativeInpaintTelea(srcCrop!!, argb, 5.0)
                } finally {
                    if (tmp) runCatching { argb.recycle() }
                }
                android.graphics.Canvas(src).drawBitmap(srcCrop, cl.toFloat(), ct.toFloat(), null)
                android.util.Log.i("Inpaint", "seleksi Telea-fallback ${cw}x${ch} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                return false
            }
        } finally {
            runCatching { srcCrop?.recycle() }
            runCatching { maskCrop?.recycle() }
        }
    }

    fun inpaintLayerArea(
        layer: DrawingLayer,
        maskBitmap: Bitmap,
        inpaintRadius: Double = 5.0
    ) {
        val srcBitmap = layer.getBitmap()
        if (dilateMask) dilateMaskAlpha(maskBitmap)
        if (mode == InpaintMode.PATCH_MATCH) {
            // PatchMatch lebih bagus untuk manga (tekstur garis) — fallback ke Telea bila gagal
            try {
                PatchMatchInpainter.inpaint(srcBitmap, maskBitmap, feather = healFeather, mode = healMode)
                layer.tileMap.importFromBitmap(srcBitmap)
                layer.markDirty()
                return
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.w("Inpaint", "PatchMatch gagal, fallback Telea: $e")
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            }
        }
        val w = srcBitmap.width
        val h = srcBitmap.height
        val pixels = w.toLong() * h.toLong()
        val (argbMask, isTempMask) = ensureArgbMask(maskBitmap)
        try {
            if (pixels > 4_000_000L) {
                inpaintMaskedRegions(srcBitmap, argbMask, inpaintRadius)
            } else {
                NativeEngine.nativeInpaintTelea(srcBitmap, argbMask, inpaintRadius)
            }
        } finally {
            if (isTempMask) runCatching { argbMask.recycle() }
        }
        layer.tileMap.importFromBitmap(srcBitmap)
        layer.markDirty()
    }

    /** Dipakai brush heal interaktif: tanpa tileMap sync per dab */
    fun inpaintBitmapDirect(src: Bitmap, mask: Bitmap) {
        try {
            if (mode == InpaintMode.PATCH_MATCH) {
                PatchMatchInpainter.inpaint(src, mask, feather = healFeather, mode = healMode)
            } else {
                val (argbMask, isTempMask) = ensureArgbMask(mask)
                try {
                    NativeEngine.nativeInpaintTelea(src, argbMask, 5.0)
                } finally {
                    if (isTempMask) runCatching { argbMask.recycle() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val (argbMask, isTempMask) = ensureArgbMask(mask)
                try {
                    NativeEngine.nativeInpaintTelea(src, argbMask, 5.0)
                } finally {
                    if (isTempMask) runCatching { argbMask.recycle() }
                }
            } catch (_: Exception) {}
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
    }

    /** Hitung piksel mask di dalam [dirty] (sampling tiap 4px agar murah di 46MB). */
    fun countMaskPixels(mask: Bitmap, dirty: android.graphics.RectF, step: Int = 4): Int {
        return try {
            val l = dirty.left.toInt().coerceIn(0, mask.width - 1)
            val t = dirty.top.toInt().coerceIn(0, mask.height - 1)
            val r = dirty.right.toInt().coerceIn(l + 1, mask.width)
            val b = dirty.bottom.toInt().coerceIn(t + 1, mask.height)
            var n = 0
            val row = IntArray(r - l)
            var y = t
            while (y < b) {
                mask.getPixels(row, 0, r - l, l, y, r - l, 1)
                var x = 0
                while (x < row.size) {
                    if ((row[x] ushr 24) > 30) n++
                    x += step
                }
                y += step
            }
            n
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    /**
     * Heal brush satu sapuan: inpaint hanya di dalam [dirty] (RectF kanvas)
     * agar 720x16000 tidak memindai 46MB penuh. Dipakai commit heal brush
     * interaktif — jauh lebih cepat dari Photoshop yang memproses layer penuh.
     * Pad adaptif 96 (dari Vasilias CONTEXT_PAD) agar patch punya sumber luas
     * dan tidak noise.
     */
    suspend fun inpaintHealDirty(
        src: Bitmap,
        mask: Bitmap,
        dirty: android.graphics.RectF,
        appContext: android.content.Context? = null
    ): Boolean {
        val t0 = android.os.SystemClock.elapsedRealtime()
        try {
            val l = dirty.left.toInt().coerceIn(0, src.width - 1)
            val t = dirty.top.toInt().coerceIn(0, src.height - 1)
            val r = dirty.right.toInt().coerceIn(1, src.width)
            val b = dirty.bottom.toInt().coerceIn(1, src.height)
            // Fallback: bila dirty salah (mis. hitungan RectF keliru) jangan
            // pindai 46MB penuh — crop area dirty yang diperluas agar tetap aman.
            if (r - l < 4 || b - t < 4) {
                val cx = ((l + r) / 2).coerceIn(0, src.width)
                val cy = ((t + b) / 2).coerceIn(0, src.height)
                val safe = android.graphics.RectF(
                    (cx - 256).toFloat().coerceAtLeast(0f),
                    (cy - 256).toFloat().coerceAtLeast(0f),
                    (cx + 256).toFloat().coerceAtMost(src.width.toFloat()),
                    (cy + 256).toFloat().coerceAtMost(src.height.toFloat())
                )
                if (safe.width() >= 8f && safe.height() >= 8f) {
                    return inpaintHealDirty(src, mask, safe, appContext)
                }
                android.util.Log.w("Inpaint", "heal: dirty terlalu kecil, skip")
                return false
            }
            // Crop sempit di sekitar sapuan + padding adaptif (Vasilias 96-256)
            // Teks manga 12-30px, butuh konteks minimal 96 agar patch menemukan
            // background putih/tekstur kertas, bukan tepi teks sendiri (noise).
            val bw = r - l
            val bh = b - t
            val adaptivePad = max(96, min(max(bw, bh) + 48, 256))
            val cl = maxOf(0, l - adaptivePad)
            val ct = maxOf(0, t - adaptivePad)
            val cr = minOf(src.width, r + adaptivePad)
            val cb = minOf(src.height, b + adaptivePad)
            val cw = cr - cl
            val ch = cb - ct
            if (cw <= 8 || ch <= 8) {
                android.util.Log.w("Inpaint", "heal: crop terlalu kecil ${cw}x${ch}, skip")
                return false
            }
            // Dilatasi mask 1px di dalam crop saja (anti-halo tepi teks).
            if (dilateMask) {
                val maskCropPre = try { Bitmap.createBitmap(mask, cl, ct, cw, ch) } catch (e: Exception) { null }
                if (maskCropPre != null) {
                    dilateMaskAlpha(maskCropPre)
                    try {
                        val dst = Canvas(mask)
                        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                        dst.drawRect(cl.toFloat(), ct.toFloat(), cr.toFloat(), cb.toFloat(), clear)
                        dst.drawBitmap(maskCropPre, cl.toFloat(), ct.toFloat(), null)
                    } catch (e: Exception) { e.printStackTrace() }
                    runCatching { maskCropPre.recycle() }
                }
            }
            // Heal: MI-GAN neural untuk area >=256px (jauh lebih bagus dari Telea
            // untuk area luas manga/webtoon); Telea instan untuk titik/gores kecil.
            // Tanpa opsi/mode: routing otomatis berdasar ukuran crop.
            if (appContext != null && max(cw, ch) >= 256) {
                try {
                    if (com.grooxtyper.app.ml.MiganInpainter.ensureSession(appContext)) {
                        val srcCropM = Bitmap.createBitmap(src, cl, ct, cw, ch)
                        val maskCropM = Bitmap.createBitmap(mask, cl, ct, cw, ch)
                        try {
                            val out = com.grooptyper.app.ml.MiganInpainter.inpaint(srcCropM, maskCropM)
                            if (out != null) {
                                android.graphics.Canvas(src).drawBitmap(out, cl.toFloat(), ct.toFloat(), null)
                                runCatching { out.recycle() }
                                android.util.Log.i("Inpaint", "heal MiGan ${cw}x${ch} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                                return true
                            }
                            android.util.Log.w("Inpaint", "MiGan skip: " + com.grooptyper.app.ml.MiganInpainter.lastError)
                        } finally {
                            runCatching { srcCropM.recycle() }
                            runCatching { maskCropM.recycle() }
                        }
                    } else {
                        android.util.Log.w("Inpaint", "MiGan session gagal: " + com.grooptyper.app.ml.MiganInpainter.lastError)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    return false
                }
            }
            try {
                val srcCrop = Bitmap.createBitmap(src, cl, ct, cw, ch)
                val maskCrop = Bitmap.createBitmap(mask, cl, ct, cw, ch)
                try {
                    val (argb, tmp) = ensureArgbMask(maskCrop)
                    try { NativeEngine.nativeInpaintTelea(srcCrop, argb, 4.0) }
                    finally { if (tmp) runCatching { argb.recycle() } }
                    android.graphics.Canvas(src).drawBitmap(srcCrop, cl.toFloat(), ct.toFloat(), null)
                    android.util.Log.i("Inpaint", "heal Telea ${cw}x${ch} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                    return true
                } finally {
                    runCatching { srcCrop.recycle() }
                    runCatching { maskCrop.recycle() }
                }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                android.util.Log.w("Inpaint", "heal Telea OOM ${cw}x${ch}")
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { inpaintBitmapDirect(src, mask); return true } catch (_: Exception) { return false }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Inpainting bertarget: temukan semua region mask (kotak pembungkus
     * komponen yang saling dekat), lalu inpaint hanya region itu.
     * 10-50x lebih sedikit piksel disentuh dibanding grid tile penuh.
     */
    private fun inpaintMaskedRegions(src: Bitmap, mask: Bitmap, radius: Double) {
        val w = src.width
        val h = src.height
        val pad = radius.toInt().coerceIn(4, 32) + 4

        // 1) Pindai mask secara kasar (tiap 8px) untuk menemukan blok ber-mask.
        //    Blok diagregasi ke strip 512px agar region yang berdekatan
        //    digabung menjadi satu kotak inpaint.
        val coarseStep = 8
        val stripH = 512
        val numStrips = (h + stripH - 1) / stripH
        // Per strip: apakah ada mask + bbox x mask di strip itu.
        val stripHas = BooleanArray(numStrips)
        val stripMinX = IntArray(numStrips) { Int.MAX_VALUE }
        val stripMaxX = IntArray(numStrips) { -1 }

        // Baca mask per strip (hemat memori: tidak load 46MB sekaligus).
        for (sIdx in 0 until numStrips) {
            val sy = sIdx * stripH
            val sh = minOf(stripH, h - sy)
            val buf = IntArray(w * sh)
            mask.getPixels(buf, 0, w, 0, sy, w, sh)
            var i = 0
            for (yy in 0 until sh step coarseStep) {
                val rowOff = yy * w
                for (xx in 0 until w step coarseStep) {
                    val p = buf[rowOff + xx]
                    if (((p ushr 24) and 0xFF) > 30) {
                        stripHas[sIdx] = true
                        if (xx < stripMinX[sIdx]) stripMinX[sIdx] = xx
                        if (xx > stripMaxX[sIdx]) stripMaxX[sIdx] = xx
                    }
                }
                i++
            }
        }

        // 2) Gabungkan strip ber-mask yang berurutan menjadi region besar.
        val regions = mutableListOf<Rect>()
        var y = 0
        while (y < numStrips) {
            if (!stripHas[y]) { y++; continue }
            var endY = y
            var minX = stripMinX[y]
            var maxX = stripMaxX[y]
            // Gabung strip berikutnya bila juga ber-mask (teks berbaris rapat).
            while (endY + 1 < numStrips && stripHas[endY + 1]) {
                endY++
                minX = minOf(minX, stripMinX[endY])
                maxX = maxOf(maxX, stripMaxX[endY])
            }
            val top = y * stripH
            val bottom = minOf(h, (endY + 1) * stripH)
            // Batasi lebar region agar native tidak memproses kolom kosong.
            val left = maxOf(0, minX - pad - coarseStep)
            val right = minOf(w, maxX + pad + coarseStep)
            regions.add(
                Rect(
                    left,
                    maxOf(0, top - pad),
                    right,
                    minOf(h, bottom + pad)
                )
            )
            y = endY + 1
        }

        // 3) Inpaint tiap region via native (crop sempit, bukan tile penuh).
        for (region in regions) {
            val rw = region.width()
            val rh = region.height()
            if (rw <= 0 || rh <= 0) continue
            try {
                val srcCrop = Bitmap.createBitmap(src, region.left, region.top, rw, rh)
                val maskCrop = Bitmap.createBitmap(mask, region.left, region.top, rw, rh)
                try {
                    NativeEngine.nativeInpaintTelea(srcCrop, maskCrop, radius)
                    android.graphics.Canvas(src).drawBitmap(
                        srcCrop, region.left.toFloat(), region.top.toFloat(), null
                    )
                } finally {
                    runCatching { srcCrop.recycle() }
                    runCatching { maskCrop.recycle() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                return
            }
        }
    }
}
