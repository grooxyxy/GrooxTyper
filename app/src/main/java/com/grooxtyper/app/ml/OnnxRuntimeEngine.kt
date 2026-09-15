package com.grooxtyper.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine ONNX Runtime on-device untuk menjalankan model segmentasi bubble
 * `assets/models/koharu/koharu-yolo26s-seg.onnx` (Ultralytics YOLO26s-seg,
 * input 1024x1024, kelas: 0=frame, 1=dialogue_text, 2=balloon, 3=onomatopoeia).
 *
 * Detail model (terverifikasi via metadata ONNX):
 * - Output0: [1, 300, 38] — end2end: (cx, cy, w, h, score, classId, 32 koefisien mask)
 *   dalam koordinat piksel kanvas input 1024.
 * - Output1: [1, 32, 256, 256] — prototype mask (ruang 1024/4).
 * - Mask instan: sigmoid(proto @ coef) dipotong ke bbox (ruang 1024).
 *
 * Model di-copy dari assets ke cacheDir SEKALI (session dimuat via file path,
 * lebih cepat & hemat RAM dibanding buffer byte[] penuh), lalu session dipakai
 * ulang antar tile/strip.
 */
object OnnxRuntimeEngine {

    private const val MODEL_ASSET = "models/koharu/koharu-yolo26s-seg.onnx"
    private const val MODEL_CACHE = "koharu-yolo26s-seg.onnx"
    const val INPUT_SIZE = 1024
    const val NUM_MASK_COEF = 32
    const val MAX_DET = 300
    /** Kelas 'balloon' pada model koharu (lihat metadata names). */
    const val CLASS_BALLOON = 2

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    private val initFailed = AtomicBoolean(false)

    /** True bila engine siap dipakai (model valid + runtime tersedia). */
    fun isAvailable(): Boolean = !initFailed.get()

    /** Tutup session (dipanggil saat aplikasi dihancurkan). */
    fun close() {
        synchronized(this) {
            runCatching { session?.close() }
            session = null
            runCatching { env?.close() }
            env = null
        }
    }

    /** Ambil session (lazy init + thread-safe). Null bila model tak tersedia. */
    fun getSession(context: Context): OrtSession? {
        session?.let { return it }
        if (initFailed.get()) return null
        synchronized(this) {
            session?.let { return it }
            if (initFailed.get()) return null
            return try {
                val modelFile = java.io.File(context.cacheDir, MODEL_CACHE)
                if (!modelFile.exists() || modelFile.length() < 1_000_000L) {
                    context.assets.open(MODEL_ASSET).use { input ->
                        modelFile.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                    }
                }
                val environment = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Sisakan 1 core untuk UI agar tetap responsif saat inferensi.
                    val cores = Runtime.getRuntime().availableProcessors()
                    setIntraOpNumThreads((cores - 1).coerceIn(1, 4))
                    setInterOpNumThreads(1)
                }
                session = environment.createSession(modelFile.absolutePath, opts)
                env = environment
                session
            } catch (e: Throwable) {
                e.printStackTrace()
                initFailed.set(true)
                null
            }
        }
    }

    /** Hasil inferensi satu tile: deteksi mentah + prototype mask. */
    class InferenceResult(
        /** [MAX_DET][38] float: cx, cy, w, h, score, classId, maskCoef[32] (koordinat 1024). */
        val detections: Array<FloatArray>,
        /** [32][256*256] float prototype mask (sigmoid belum diterapkan). */
        val proto: Array<FloatArray>,
        val protoDim: Int
    )

    /**
     * Jalankan inferensi pada bitmap tile (akan di-letterbox ke 1024x1024).
     * Mengembalikan deteksi mentah; pemanggil bertugas mem-filter skor/kelas
     * dan memetakan koordinat kembali.
     */
    fun run(context: Context, tile: Bitmap): InferenceResult? {
        val session = getSession(context) ?: return null
        val environment = env ?: return null
        val inputName = session.inputNames.firstOrNull() ?: return null
        val tensor = bitmapToTensor(environment, tile)
        return try {
            val outputs = session.run(mapOf(inputName to tensor))
            outputs.use { result ->
                val detTensor = result.get(0) as OnnxTensor
                val protoTensor = result.get(1) as OnnxTensor

                val detBuf = (detTensor.value as Array<*>)[0] as Array<*>
                val detections = Array(detBuf.size) { i -> (detBuf[i] as FloatArray) }

                val protoArr = (protoTensor.value as Array<*>)[0] as Array<*>
                val protoDim = (protoArr[0] as Array<*>).size.let {
                    // [32][256][256] → dim sisi = sqrt(length channel0)
                    val ch0 = protoArr[0] as Array<*>
                    val row0 = ch0[0] as FloatArray
                    row0.size // 256
                }
                val proto = Array(protoArr.size) { c ->
                    val channel = protoArr[c] as Array<*>
                    FloatArray(protoDim * protoDim).also { flat ->
                        for (y in 0 until protoDim) {
                            val row = channel[y] as FloatArray
                            System.arraycopy(row, 0, flat, y * protoDim, protoDim)
                        }
                    }
                }
                InferenceResult(detections, proto, protoDim)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        } finally {
            runCatching { tensor.close() }
        }
    }

    /** Bitmap → tensor [1,3,1024,1024] float32 0..1, letterbox (tanpa stretch). */
    private fun bitmapToTensor(environment: OrtEnvironment, src: Bitmap): OnnxTensor {
        val size = INPUT_SIZE
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled !== src) scaled.recycle()
        val data = FloatArray(3 * size * size)
        val plane = size * size
        val inv = 1f / 255f
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) * inv            // R
            data[plane + i] = ((p shr 8) and 0xFF) * inv     // G
            data[2 * plane + i] = (p and 0xFF) * inv         // B
        }
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(data),
            longArrayOf(1, 3, size.toLong(), size.toLong())
        )
    }

    /**
     * Rakit mask instan [protoDim x protoDim] untuk satu deteksi:
     * sigmoid(sum_k coef[k] * proto[k][p]), dipotong ke bbox (ruang 1024,
     * bbox dibagi 4 ke ruang proto). Alpha dikodekan 0..255.
     */
    fun buildInstanceMask(
        result: InferenceResult,
        detIndex: Int,
        boxCx: Float, boxCy: Float, boxW: Float, boxH: Float,
        out: ByteArray
    ) {
        val det = result.detections[detIndex]
        val dim = result.protoDim
        val scale = dim.toFloat() / INPUT_SIZE
        val x0 = ((boxCx - boxW / 2f) * scale).toInt().coerceIn(0, dim - 1)
        val y0 = ((boxCy - boxH / 2f) * scale).toInt().coerceIn(0, dim - 1)
        val x1 = ((boxCx + boxW / 2f) * scale).toInt().coerceIn(x0 + 1, dim)
        val y1 = ((boxCy + boxH / 2f) * scale).toInt().coerceIn(y0 + 1, dim)
        val coefBase = 6
        for (y in y0 until y1) {
            val rowOff = y * dim
            for (x in x0 until x1) {
                val p = rowOff + x
                var s = 0f
                for (k in 0 until NUM_MASK_COEF) {
                    s += det[coefBase + k] * result.proto[k][p]
                }
                // sigmoid cepat
                val sig = 1f / (1f + kotlin.math.exp(-s.coerceIn(-20f, 20f)))
                out[p] = (sig * 255f).toInt().toByte()
            }
        }
    }
}
