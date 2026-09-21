package com.grooxtyper.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxJavaType
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MI-GAN on-device (Picsart AI Research, ICCV 2023, MIT) untuk inpainting
 * area SELEKSI. Aset disamarkan (`models/mg.onnx`).
 *
 * Pipeline v2: input `image` (1,3,H,W) uint8 RGB + `mask` (1,1,H,W) uint8
 * (255 = keep, 0 = lubang); output `result` (1,3,H,W) uint8 RGB.
 * Dijalankan SELALU pada crop kecil (bounds seleksi + pad), bukan full
 * 720x16000. Telea dipakai sebagai fallback bila session gagal.
 */
object MiganInpainter {

    const val ASSET = "models/mg.onnx"

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var session: OrtSession? = null
    private val mutex = Mutex()

    suspend fun ensureSession(context: Context): Boolean {
        session?.let { return true }
        return mutex.withLock {
            session?.let { return@withLock true }
            try {
                val bytes = context.assets.open(ASSET).use { it.readBytes() }
                if (bytes.isEmpty()) {
                    lastError = "Aset $ASSET kosong (0 byte)"
                    return@withLock false
                }
                android.util.Log.i("MiGan", "Memuat model ${bytes.size} byte dari $ASSET")
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(bytes, opts).also {
                    session = it
                    lastError = null
                    android.util.Log.i("MiGan", "Session siap: in=${it.inputNames} out=${it.outputNames}")
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                lastError = "Gagal muat $ASSET: ${e.message ?: e.javaClass.simpleName}"
                android.util.Log.e("MiGan", lastError!!)
                false
            }
        }
    }

    /**
     * Inpaint [srcCrop] pada [maskCrop] (alpha>30 = lubang). Kembalikan bitmap
     * BARU hasil inpaint, atau null + [lastError] terisi.
     */
    fun inpaint(srcCrop: Bitmap, maskCrop: Bitmap): Bitmap? {
        val sess = session ?: run {
            lastError = "Session MiGan belum siap"
            return null
        }
        val w = srcCrop.width
        val h = srcCrop.height
        if (w <= 0 || h <= 0 || maskCrop.width != w || maskCrop.height != h) {
            lastError = "Crop kosong/tak sejajar"
            return null
        }
        var imgTensor: OnnxTensor? = null
        var maskTensor: OnnxTensor? = null
        var result: OrtSession.Result? = null
        try {
            val sp = IntArray(w * h)
            srcCrop.getPixels(sp, 0, w, 0, 0, w, h)
            val mp = IntArray(w * h)
            maskCrop.getPixels(mp, 0, w, 0, 0, w, h)
            val img = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder())
            val msk = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
            for (i in sp.indices) {
                val p = sp[i]
                img.put(((p shr 16) and 0xFF).toByte())
                img.put(((p shr 8) and 0xFF).toByte())
                img.put((p and 0xFF).toByte())
                msk.put(if ((mp[i] ushr 24) > 30) 0.toByte() else 255.toByte())
            }
            img.rewind()
            msk.rewind()
            val env = OrtEnvironment.getEnvironment()
            // WAJIB tipe eksplisit UINT8: overload ByteBuffer tanpa tipe
            // selalu dianggap INT8 oleh ORT (dulu inilah yang membuat
            // session.run selalu gagal lalu fallback ke Telea).
            imgTensor = OnnxTensor.createTensor(env, img, longArrayOf(1, 3, h.toLong(), w.toLong()), OnnxJavaType.UINT8)
            maskTensor = OnnxTensor.createTensor(env, msk, longArrayOf(1, 1, h.toLong(), w.toLong()), OnnxJavaType.UINT8)
            val inNames = sess.inputNames.toList()
            val feed = LinkedHashMap<String, OnnxTensor>()
            feed[if (inNames.contains("image")) "image" else inNames[0]] = imgTensor
            feed[if (inNames.contains("mask")) "mask" else inNames[1]] = maskTensor
            result = sess.run(feed)
            // Jalur utama: array multidimensi. Cadangan: buffer mentah bila
            // getValue() gagal/tak sesuai (tetap tanpa crash, error presisi).
            val outTensor = result[0]
            val bmp = try {
                toBitmap(outTensor.value, w, h)
            } catch (e: Exception) {
                e.printStackTrace()
                lastError = "Parse output MiGan gagal: ${e.message}"
                null
            } ?: fromByteBuffer(outTensor as? OnnxTensor, w, h)
            if (bmp == null) return null
            // Guard halusinasi: piksel VALID (di luar lubang) wajib nyaris
            // identik dengan input (pipeline resmi me-blend output). Bila
            // jauh berbeda → model mengarang (khas lubang raksasa di manga
            // yang di luar distribusi Places2) → tolak, fallback.
            if (!sanityOk(sp, mp, bmp, w, h)) {
                runCatching { bmp.recycle() }
                if (lastError == null) lastError = "Hasil MiGan tak wajar (halusinasi), fallback"
                return null
            }
            lastError = null
            return bmp
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Inferensi MiGan gagal: ${e.message ?: e.javaClass.simpleName}"
            return null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            lastError = "MiGan OOM: coba seleksi lebih kecil"
            return null
        } finally {
            runCatching { imgTensor?.close() }
            runCatching { maskTensor?.close() }
            runCatching { result?.close() }
        }
    }

    /**
     * Cek kewarasan output: rata-rata selisih absolut pada piksel VALID
     * (sampling stride 4 agar murah) harus kecil. Sampel valid < 100
     * (lubang menutup nyaris seluruh crop) juga ditolak — GAN tak bisa
     * dipercaya tanpa konteks cukup.
     */
    private fun sanityOk(sp: IntArray, mp: IntArray, bmp: Bitmap, w: Int, h: Int): Boolean {
        return try {
            val op = IntArray(w * h)
            bmp.getPixels(op, 0, w, 0, 0, w, h)
            var sum = 0L
            var n = 0L
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val i = y * w + x
                    if ((mp[i] ushr 24) <= 30) {
                        val a = sp[i]
                        val b = op[i]
                        sum += kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)).toLong() +
                            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)).toLong() +
                            kotlin.math.abs((a and 0xFF) - (b and 0xFF)).toLong()
                        n++
                    }
                    x += 4
                }
                y += 4
            }
            if (n < 100) {
                lastError = "Lubang terlalu besar untuk MiGan (konteks minim), fallback"
                return false
            }
            (sum / (n * 3)) <= 14L
        } catch (e: Exception) {
            e.printStackTrace()
            true // ragu-ragu → jangan blokir; composite lubang membatasi damage
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            true
        }
    }

    /** Cadangan: baca output uint8 langsung dari buffer mentah (row-major). */
    private fun fromByteBuffer(tensor: OnnxTensor?, w: Int, h: Int): Bitmap? {
        return try {
            val bb = tensor?.getByteBuffer()
            if (bb == null || bb.remaining() < w * h * 3) {
                lastError = "Buffer output MiGan invalid"
                return null
            }
            val px = IntArray(w * h)
            for (c in 0 until 3) {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val v = bb.get().toInt() and 0xFF
                        val i = y * w + x
                        px[i] = px[i] or (v shl (16 - c * 8))
                    }
                }
            }
            for (i in px.indices) px[i] = px[i] or (0xFF shl 24)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            lastError = null
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Buffer output MiGan gagal: ${e.message}"
            null
        }
    }

    /** Ubah output (1,3,H,W) uint8 — atau float darurat — menjadi Bitmap. */
    private fun toBitmap(raw: Any?, w: Int, h: Int): Bitmap? {
        try {
            val b0 = (raw as? Array<*>)?.get(0) as? Array<*> ?: run {
                lastError = "Output MiGan tak dikenal"
                return null
            }
            if (b0.size < 3) {
                lastError = "Output MiGan kanal!=3"
                return null
            }
            fun planeToBytes(p: Any?): ByteArray? {
                val rows = p as? Array<*> ?: return null
                if (rows.isEmpty()) return null
                // Layout umum: H baris × ByteArray(W).
                (rows[0] as? ByteArray)?.let { first ->
                    if (rows.size != h || first.size != w) return null
                    val out = ByteArray(w * h)
                    for (y in 0 until h) {
                        val row = rows[y] as? ByteArray ?: return null
                        if (row.size != w) return null
                        System.arraycopy(row, 0, out, y * w, w)
                    }
                    return out
                }
                // Darurat float: H baris × FloatArray(W), skala otomatis.
                (rows[0] as? FloatArray)?.let {
                    var mx = 0f
                    val tmp = Array(h) { y ->
                        (rows[y] as? FloatArray)?.also { r ->
                            for (v in r) { val a = kotlin.math.abs(v); if (a > mx) mx = a }
                        } ?: return null
                    }
                    val k = if (mx <= 1.01f) 255f else if (mx <= 255.01f) 1f else return null
                    val out = ByteArray(w * h)
                    for (y in 0 until h) for (x in 0 until w) {
                        out[y * w + x] = (tmp[y][x] * k).toInt().coerceIn(0, 255).toByte()
                    }
                    return out
                }
                return null
            }
            val r = planeToBytes(b0[0]) ?: run { lastError = "Plane R MiGan invalid"; return null }
            val g = planeToBytes(b0[1]) ?: run { lastError = "Plane G MiGan invalid"; return null }
            val b = planeToBytes(b0[2]) ?: run { lastError = "Plane B MiGan invalid"; return null }
            val px = IntArray(w * h)
            for (i in px.indices) {
                px[i] = (0xFF shl 24) or ((r[i].toInt() and 0xFF) shl 16) or
                    ((g[i].toInt() and 0xFF) shl 8) or (b[i].toInt() and 0xFF)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            lastError = null
            return bmp
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Parse output MiGan gagal: ${e.message}"
            return null
        }
    }
}
