package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grooxtyper.app.native.NativeEngine

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

    var mode: InpaintMode = InpaintMode.PATCH_MATCH
    // Heal brush ala Photoshop tapi lebih bagus untuk manga: pilih strategi
    // patch. Default CONTENT_AWARE (pengganti PS Content-Aware Fill).
    // mutableState agar pemilih mode di quick slider langsung recompose.
    var healMode by mutableStateOf(HealMode.CONTENT_AWARE)
    var healFeather by mutableStateOf(true)

    fun inpaintLayerArea(
        layer: DrawingLayer,
        maskBitmap: Bitmap,
        inpaintRadius: Double = 5.0
    ) {
        val srcBitmap = layer.getBitmap()
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
        if (pixels > 4_000_000L) {
            inpaintMaskedRegions(srcBitmap, maskBitmap, inpaintRadius)
        } else {
            NativeEngine.nativeInpaintTelea(srcBitmap, maskBitmap, inpaintRadius)
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
                NativeEngine.nativeInpaintTelea(src, mask, 5.0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { NativeEngine.nativeInpaintTelea(src, mask, 5.0) } catch (_: Exception) {}
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
    }

    /**
     * Heal brush satu sapuan: inpaint hanya di dalam [dirty] (RectF kanvas)
     * agar 720x16000 tidak memindai 46MB penuh. Dipakai commit heal brush
     * interaktif — jauh lebih cepat dari Photoshop yang memproses layer penuh.
     */
    fun inpaintHealDirty(src: Bitmap, mask: Bitmap, dirty: android.graphics.RectF) {
        try {
            val l = dirty.left.toInt().coerceIn(0, src.width - 1)
            val t = dirty.top.toInt().coerceIn(0, src.height - 1)
            val r = dirty.right.toInt().coerceIn(1, src.width)
            val b = dirty.bottom.toInt().coerceIn(1, src.height)
            if (r - l < 4 || b - t < 4) {
                inpaintBitmapDirect(src, mask)
                return
            }
            // Crop sempit di sekitar sapuan + padding agar patch punya sumber.
            val pad = 24
            val cl = maxOf(0, l - pad)
            val ct = maxOf(0, t - pad)
            val cr = minOf(src.width, r + pad)
            val cb = minOf(src.height, b + pad)
            val cw = cr - cl
            val ch = cb - ct
            if (cw <= 8 || ch <= 8) return
            if (mode == InpaintMode.PATCH_MATCH) {
                try {
                    val srcCrop = Bitmap.createBitmap(src, cl, ct, cw, ch)
                    val maskCrop = Bitmap.createBitmap(mask, cl, ct, cw, ch)
                    try {
                        PatchMatchInpainter.inpaint(srcCrop, maskCrop, feather = healFeather, mode = healMode)
                        android.graphics.Canvas(src).drawBitmap(srcCrop, cl.toFloat(), ct.toFloat(), null)
                    } finally {
                        runCatching { srcCrop.recycle() }
                        runCatching { maskCrop.recycle() }
                    }
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    inpaintBitmapDirect(src, mask)
                } catch (e: Exception) {
                    e.printStackTrace()
                    inpaintBitmapDirect(src, mask)
                }
            } else {
                inpaintBitmapDirect(src, mask)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { inpaintBitmapDirect(src, mask) } catch (_: Exception) {}
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
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
                    if (((p ushr 24) and 0xFF) > 30 && (p and 0xFF) > 30) {
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
