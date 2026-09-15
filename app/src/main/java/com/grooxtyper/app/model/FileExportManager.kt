package com.grooxtyper.app.model

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class ExportFormat(val extension: String) {
    PNG(".png"),
    JPG(".jpg"),
    WEBP(".webp"),
    PSD(".psd")
}

class FileExportManager(private val context: Context) {

    fun exportArtwork(
        layerManager: LayerManager,
        format: ExportFormat,
        filename: String = "artwork_${System.currentTimeMillis()}"
    ): File? {
        // 720x16000 = ~46MB sementara; recycle segera setelah kompres.
        val composite = Bitmap.createBitmap(layerManager.width, layerManager.height, Bitmap.Config.ARGB_8888)
        try {
            layerManager.renderComposite(composite)

            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val file = File(picturesDir, "$filename${format.extension}")

            return try {
                val os: OutputStream = FileOutputStream(file)
                when (format) {
                    ExportFormat.PNG -> composite.compress(Bitmap.CompressFormat.PNG, 100, os)
                    ExportFormat.JPG -> composite.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    ExportFormat.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            composite.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
                        } else {
                            @Suppress("DEPRECATION")
                            composite.compress(Bitmap.CompressFormat.WEBP, 100, os)
                        }
                    }
                    ExportFormat.PSD -> writeMockPsd(os, composite, layerManager)
                }
                os.flush()
                os.close()
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                null
            }
        } finally {
            runCatching { composite.recycle() }
        }
    }

    private fun writeMockPsd(os: OutputStream, composite: Bitmap, layerManager: LayerManager) {
        val width = layerManager.width
        val height = layerManager.height

        val header = ByteArray(26)
        "8BPS".toByteArray().copyInto(header, 0)
        header[4] = 0; header[5] = 1
        header[12] = 0; header[13] = 4
        header[14] = (height shr 24).toByte()
        header[15] = (height shr 16).toByte()
        header[16] = (height shr 8).toByte()
        header[17] = height.toByte()
        header[18] = (width shr 24).toByte()
        header[19] = (width shr 16).toByte()
        header[20] = (width shr 8).toByte()
        header[21] = width.toByte()
        header[22] = 0; header[23] = 8
        header[24] = 0; header[25] = 3

        os.write(header)

        val baos = ByteArrayOutputStream()
        composite.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val imgData = baos.toByteArray()

        val len = imgData.size
        os.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()))
        os.write(imgData)
    }
}
