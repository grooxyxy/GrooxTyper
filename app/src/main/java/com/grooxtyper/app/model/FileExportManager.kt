package com.grooxtyper.app.model

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mime: String, val supportsQuality: Boolean) {
    PNG(".png", "image/png", false),
    JPG(".jpg", "image/jpeg", true),
    WEBP(".webp", "image/webp", true)
}

class FileExportManager(private val context: Context) {

    /**
     * Render komposit + tulis ke file kerja (dir privat app). Kembalikan null
     * bila gagal (mis. memori habis di 720x16000) — pemanggil WAJIB
     * menampilkan hasilnya ke user (sebelumnya gagal diam-diam).
     * [quality] 1..100 untuk JPG/WEBP; PNG selalu lossless.
     */
    fun exportArtwork(
        layerManager: LayerManager,
        format: ExportFormat,
        filename: String = "artwork_${System.currentTimeMillis()}",
        quality: Int = 90
    ): File? {
        // 720x16000 = ~46MB sementara; recycle segera setelah kompres.
        var composite: Bitmap? = null
        try {
            composite = Bitmap.createBitmap(layerManager.width, layerManager.height, Bitmap.Config.ARGB_8888)
            layerManager.renderComposite(composite)

            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val file = File(picturesDir, "$filename${format.extension}")

            try {
                val os: OutputStream = FileOutputStream(file)
                val q = quality.coerceIn(1, 100)
                when (format) {
                    ExportFormat.PNG -> composite.compress(Bitmap.CompressFormat.PNG, 100, os)
                    ExportFormat.JPG -> composite.compress(Bitmap.CompressFormat.JPEG, q, os)
                    ExportFormat.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            composite.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, os)
                        } else {
                            @Suppress("DEPRECATION")
                            composite.compress(Bitmap.CompressFormat.WEBP, q, os)
                        }
                    }
                }
                os.flush()
                os.close()
                return file
            } catch (e: Exception) {
                e.printStackTrace()
                runCatching { file.delete() }
                return null
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                runCatching { file.delete() }
                return null
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            runCatching { composite?.recycle() }
        }
    }

    /**
     * Salin file kerja ke galeri publik (Pictures/GrooxTyper) agar mudah
     * ditemukan user. Kembalikan path/uri tampil, atau null bila gagal
     * (file kerja tetap ada sebagai fallback).
     */
    fun publishToGallery(file: File, mime: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/GrooxTyper"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                uri.toString()
            } else {
                @Suppress("DEPRECATION")
                val pub = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "GrooxTyper/${file.name}"
                )
                pub.parentFile?.mkdirs()
                file.copyTo(pub, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(pub.absolutePath), arrayOf(mime), null
                )
                pub.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
