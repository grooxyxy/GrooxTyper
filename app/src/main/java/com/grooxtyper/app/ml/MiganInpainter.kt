package com.grooxtyper.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
            imgTensor = OnnxTensor.createTensor(env, img, longArrayOf(1, 3, h.toLong(), w.toLong()))
            maskTensor = OnnxTensor.createTensor(env, msk, longArrayOf(1, 1, h.toLong(), w.toLong()))
            val inNames = sess.inputNames.toList()
            val feed = LinkedHashMap<String, OnnxTensor>()
            feed[if (inNames.contains("image")) "image" else inNames[0]] = imgTensor
            feed[if (inNames.contains("mask")) "mask" else inNames[1]] = maskTensor
            result = sess.run(feed)
            val raw = result[0].value
            return toBitmap(raw, w, h)
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
            return bmp
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Parse output MiGan gagal: ${e.message}"
            return null
        }
    }
}
