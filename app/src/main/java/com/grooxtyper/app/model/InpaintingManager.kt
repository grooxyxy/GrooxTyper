package com.grooxtyper.app.model

import android.graphics.Bitmap
import com.grooxtyper.app.native.NativeEngine

class InpaintingManager {

    fun inpaintLayerArea(
        layer: DrawingLayer,
        maskBitmap: Bitmap,
        inpaintRadius: Double = 5.0
    ) {
        val srcBitmap = layer.getBitmap()
        // Kanvas jangkung (mis. 720x16000): native Telea alokasi
        // maskState + distMap raksasa (~100MB) → OOM. Proses per tile
        // 1024px yang bersinggungan dengan mask saja.
        val w = srcBitmap.width
        val h = srcBitmap.height
        if (w.toLong() * h.toLong() > 4_000_000L) {
            inpaintTiled(srcBitmap, maskBitmap, inpaintRadius)
        } else {
            NativeEngine.nativeInpaintTelea(srcBitmap, maskBitmap, inpaintRadius)
        }
        layer.tileMap.importFromBitmap(srcBitmap)
        layer.markDirty()
    }

    /**
     * Inpainting berubin: bagi kanvas jadi tile 1024px (overlap = radius),
     * lewati tile kosong (tanpa mask), proses tile berisi via native.
     */
    private fun inpaintTiled(src: Bitmap, mask: Bitmap, radius: Double) {
        val w = src.width
        val h = src.height
        val tile = 1024
        val pad = radius.toInt().coerceIn(4, 32) + 4
        var y = 0
        while (y < h) {
            var x = 0
            val tileH = minOf(tile, h - y)
            while (x < w) {
                val tileW = minOf(tile, w - x)
                if (hasMask(mask, x, y, tileW, tileH)) {
                    // Perluas dengan padding agar tepi tile mulus.
                    val px0 = maxOf(0, x - pad)
                    val py0 = maxOf(0, y - pad)
                    val px1 = minOf(w, x + tileW + pad)
                    val py1 = minOf(h, y + tileH + pad)
                    val pw = px1 - px0
                    val ph = py1 - py0
                    try {
                        val srcCrop = Bitmap.createBitmap(src, px0, py0, pw, ph)
                        val maskCrop = Bitmap.createBitmap(mask, px0, py0, pw, ph)
                        try {
                            NativeEngine.nativeInpaintTelea(srcCrop, maskCrop, radius)
                            // Salin kembali hasil tile ke kanvas utama.
                            val c = android.graphics.Canvas(src)
                            c.drawBitmap(
                                srcCrop, px0.toFloat(), py0.toFloat(), null
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
                x += tile
            }
            y += tile
        }
    }

    /** True bila area [x,y,w,h] pada [mask] mengandung piksel mask (alpha>30). */
    private fun hasMask(mask: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        return try {
            // Sampling kasar tiap 4px agar cek 1024px tile murah.
            val step = 4
            val bw = (w + step - 1) / step
            val bh = (h + step - 1) / step
            val pixels = IntArray(bw * bh)
            for (row in 0 until bh) {
                for (col in 0 until bw) {
                    val px = minOf(x + col * step, mask.width - 1)
                    val py = minOf(y + row * step, mask.height - 1)
                    pixels[row * bw + col] = mask.getPixel(px, py)
                }
            }
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) > 30 && (p and 0xFF) > 30) return true
            }
            false
        } catch (e: Exception) {
            true
        }
    }
}
