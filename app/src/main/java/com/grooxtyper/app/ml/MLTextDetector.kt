package com.grooxtyper.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class MLMaskType {
    REFINED_TEXT, // Precise text stroke contour
    BOUNDING_BOX   // Rectangle bounding box
}

data class DetectedTextRegion(
    val text: String,
    val boundingBox: Rect,
    val cornerPoints: Array<android.graphics.Point>?
)

class MLTextDetector {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun detectTextRegions(bitmap: Bitmap): List<DetectedTextRegion> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val regions = mutableListOf<DetectedTextRegion>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
                            regions.add(
                                DetectedTextRegion(
                                    text = line.text,
                                    boundingBox = box,
                                    cornerPoints = line.cornerPoints
                                )
                            )
                        }
                    }
                }
                continuation.resume(regions)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }

    /**
     * Estimasi warna teks asli di dalam [box]: rata-rata piksel yang lebih
     * gelap dari rata-rata (diasumsikan tinta), agar TextBox pengganti
     * langsung mirip warna aslinya. Fallback bila tak ada piksel valid.
     */
    fun estimateRegionTextColor(source: Bitmap, box: Rect, fallback: Int): Int {
        return try {
            val left = box.left.coerceIn(0, source.width - 1)
            val top = box.top.coerceIn(0, source.height - 1)
            val right = box.right.coerceIn(left + 1, source.width)
            val bottom = box.bottom.coerceIn(top + 1, source.height)
            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return fallback
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, left, top, w, h)
            var sumLum = 0L
            var count = 0
            for (p in pixels) {
                if ((p ushr 24) < 16) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sumLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                count++
            }
            if (count == 0) return fallback
            val avg = sumLum / count
            var sr = 0L
            var sg = 0L
            var sb = 0L
            var sc = 0L
            for (p in pixels) {
                if ((p ushr 24) < 16) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                if (lum < avg) {
                    sr += r
                    sg += g
                    sb += b
                    sc++
                }
            }
            if (sc == 0) return fallback
            Color.rgb((sr / sc).toInt(), (sg / sc).toInt(), (sb / sc).toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            fallback
        }
    }

    fun generateMaskBitmap(
        canvasWidth: Int,
        canvasHeight: Int,
        regions: List<DetectedTextRegion>,
        sourceBitmap: Bitmap,
        maskType: MLMaskType
    ): Bitmap {
        val maskBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        for (region in regions) {
            when (maskType) {
                MLMaskType.BOUNDING_BOX -> {
                    val box = region.boundingBox
                    canvas.drawRect(box, fillPaint)
                }
                MLMaskType.REFINED_TEXT -> {
                    // Refined mask: extract text threshold binary mask inside bounding box
                    val box = region.boundingBox
                    val left = maxOf(0, box.left)
                    val top = maxOf(0, box.top)
                    val right = minOf(sourceBitmap.width, box.right)
                    val bottom = minOf(sourceBitmap.height, box.bottom)
                    val boxW = right - left
                    val boxH = bottom - top

                    if (boxW > 0 && boxH > 0) {
                        val pixels = IntArray(boxW * boxH)
                        sourceBitmap.getPixels(pixels, 0, boxW, left, top, boxW, boxH)

                        // Otsu / Luminance thresholding inside text block
                        var sumLum = 0L
                        for (p in pixels) {
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            sumLum += lum
                        }
                        val avgLum = if (pixels.isNotEmpty()) sumLum / pixels.size else 128

                        for (y in 0 until boxH) {
                            for (x in 0 until boxW) {
                                val p = pixels[y * boxW + x]
                                val r = (p shr 16) and 0xFF
                                val g = (p shr 8) and 0xFF
                                val b = p and 0xFF
                                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                                // If pixel is dark text relative to background
                                if (lum < avgLum * 0.95) {
                                    canvas.drawRect(
                                        (left + x).toFloat(),
                                        (top + y).toFloat(),
                                        (left + x + 1).toFloat(),
                                        (top + y + 1).toFloat(),
                                        fillPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return maskBitmap
    }
}
