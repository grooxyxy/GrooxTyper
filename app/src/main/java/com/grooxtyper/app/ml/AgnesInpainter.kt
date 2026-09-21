package com.grooxtyper.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Penyimpanan API key Agnes di perangkat (SharedPreferences, tidak di-commit).
 * User menempelkan key sekali di pengaturan brush AI.
 */
object AgnesKeyStore {
    private const val PREFS = "grooxtyper_agnes"
    private const val KEY = "api_key"

    fun getKey(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun saveKey(context: Context, key: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, key.trim()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * AI Inpaint via Agnes AI (OpenAI-compatible gateway, tanpa dependency baru:
 * HttpURLConnection + org.json bawaan Android).
 *
 * Endpoint: POST {BASE_URL}/images/generations dengan image-to-image:
 * dua referensi (crop asli + crop bertanda merah di area mask) + prompt
 * hapus objek. Output diminta `b64_json`, lalu di-scale kembali ke ukuran
 * crop (server boleh menormalisasi dimensi).
 *
 * API key TIDAK di-commit: user menempelkannya sekali di pengaturan brush
 * (disimpan di SharedPreferences perangkat). Tanpa key → error jelas.
 */
object AgnesInpainter {

    const val BASE_URL = "https://apihub.agnes-ai.com/v1"
    const val MODEL = "agnes-image-2.5-flash"

    /** Sisi terpanjang crop yang dikirim (hemat kuota/waktu; hasil di-scale balik). */
    private const val MAX_SEND_SIDE = 1024

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    @Volatile
    var lastError: String? = null
        private set

    private val netMutex = Mutex()

    /**
     * Hapus objek bertopeng: [maskCrop] (alpha>30 = lubang) menandai area.
     * Kembalikan bitmap BARU seukuran [srcCrop], atau null + [lastError].
     * Wajib dipanggil dari coroutine (IO).
     */
    suspend fun inpaint(
        apiKey: String,
        srcCrop: Bitmap,
        maskCrop: Bitmap,
        onProgress: ((Float) -> Unit)? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            lastError = "API key Agnes kosong — isi dulu di pengaturan brush AI"
            return@withContext null
        }
        val w = srcCrop.width
        val h = srcCrop.height
        if (w <= 0 || h <= 0 || maskCrop.width != w || maskCrop.height != h) {
            lastError = "Crop kosong/tak sejajar"
            return@withContext null
        }
        netMutex.withLock {
            try {
                onProgress?.invoke(0.2f)
                // Downscale bila crop raksasa (hasil akhir di-scale balik).
                val scale = min(1f, MAX_SEND_SIDE / max(w, h).toFloat())
                val sw = max(16, (w * scale).toInt())
                val sh = max(16, (h * scale).toInt())
                val sendSrc = if (scale < 1f) Bitmap.createScaledBitmap(srcCrop, sw, sh, true) else srcCrop
                val sendMask = if (scale < 1f) Bitmap.createScaledBitmap(maskCrop, sw, sh, true) else maskCrop
                try {
                    val marked = markMask(sendSrc, sendMask) ?: run {
                        lastError = "Gagal menandai mask"
                        return@withContext null
                    }
                    try {
                        onProgress?.invoke(0.35f)
                        val b64 = requestEdit(apiKey, sendSrc, marked, sw, sh, onProgress)
                            ?: return@withContext null
                        onProgress?.invoke(0.9f)
                        val raw = decodeB64(b64) ?: run {
                            lastError = "Decode hasil AI gagal"
                            return@withContext null
                        }
                        val out = if (raw.width != w || raw.height != h) {
                            val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                            runCatching { raw.recycle() }
                            scaled
                        } else raw
                        lastError = null
                        onProgress?.invoke(1f)
                        out
                    } finally {
                        runCatching { marked.recycle() }
                    }
                } finally {
                    if (scale < 1f) {
                        runCatching { sendSrc.recycle() }
                        runCatching { sendMask.recycle() }
                    }
                }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                lastError = "AI OOM: coba sapuan lebih kecil"
                null
            } catch (e: Exception) {
                e.printStackTrace()
                if (lastError == null) lastError = "AI gagal: ${e.message ?: e.javaClass.simpleName}"
                null
            }
        }
    }

    /** Tandai lubang dengan overlay merah agar model tahu objek yang dihapus. */
    private fun markMask(src: Bitmap, mask: Bitmap): Bitmap? {
        return try {
            val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val cv = Canvas(out)
            val w = src.width
            val h = src.height
            val mp = IntArray(w * h)
            mask.getPixels(mp, 0, w, 0, 0, w, h)
            val mark = IntArray(w * h)
            for (i in mp.indices) {
                mark[i] = if ((mp[i] ushr 24) > 30) Color.argb(140, 255, 0, 0) else 0
            }
            val markBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            markBmp.setPixels(mark, 0, w, 0, 0, w, h)
            cv.drawBitmap(markBmp, 0f, 0f, null)
            markBmp.recycle()
            out
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    private fun requestEdit(
        apiKey: String,
        src: Bitmap,
        marked: Bitmap,
        w: Int,
        h: Int,
        onProgress: ((Float) -> Unit)?
    ): String? {
        var conn: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("model", MODEL)
                put(
                    "prompt",
                    "Remove ONLY the red-marked text and lettering completely. Reconstruct the " +
                        "background behind it to match the surrounding texture, gradient, lighting " +
                        "and manga art style seamlessly. Every pixel outside the red mark must stay " +
                        "pixel-identical: do not redraw, restyle, add, or move anything else. " +
                        "Same dimensions. No new objects, no new text, no watermark."
                )
                put("size", "${w}x$h")
                put("extra_body", JSONObject().apply {
                    put("image", JSONArray().apply {
                        put(dataUri(pngBytes(src)))
                        put(dataUri(pngBytes(marked)))
                    })
                    put("response_format", "b64_json")
                })
            }.toString()
            conn = (URL("$BASE_URL/images/generations").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            onProgress?.invoke(0.6f)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                lastError = "Agnes HTTP $code: ${text.take(200)}"
                return null
            }
            val data = JSONObject(text).optJSONArray("data") ?: run {
                lastError = "Respons Agnes tanpa data"
                return null
            }
            if (data.length() == 0) {
                lastError = "Respons Agnes kosong"
                return null
            }
            val b64 = data.getJSONObject(0).optString("b64_json", "")
            if (b64.isBlank()) {
                val url = data.getJSONObject(0).optString("url", "")
                if (url.isBlank()) {
                    lastError = "Agnes tanpa url/b64_json"
                    return null
                }
                lastError = "Agnes mengembalikan URL (butuh unduhan) — ulangi"
                return null
            }
            return b64
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Jaringan Agnes gagal: ${e.message ?: e.javaClass.simpleName}"
            return null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun pngBytes(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    private fun dataUri(png: ByteArray): String =
        "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)

    private fun decodeB64(b64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }
}
