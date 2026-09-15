package com.grooxtyper.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Pilihan mesin deteksi teks di dialog deteksi.
 * - ML_KIT: bawaan GMS, tanpa file tambahan.
 * - PPOCR_V6: PP-OCR v6 small (deteksi + rec Inggris/Korea/China) via ONNX
 *   Runtime (dependensi sudah ada untuk bubble detector). Model .onnx
 *   DIUNDUH SAAT BUILD di GitHub Action ke assets (repo tetap ramping);
 *   dict teks ikut ter-commit. Bila model tak ikut ter-build, opsi ini
 *   otomatis fallback ke ML Kit. Korea mencakup Inggris, China mencakup
 *   Inggris; Jepang tetap pakai ML Kit.
 */
enum class TextEngine(val displayName: String, val desc: String) {
    ML_KIT("ML Kit", "Bawaan, tanpa file tambahan"),
    PPOCR_V6("PP-OCR v6 small", "Det+rec EN/KO/ZH, unduh saat build")
}

enum class PpocrLang(val code: String, val displayName: String) {
    EN("en", "Inggris"),
    KO("ko", "Korea"),
    ZH("zh", "China")
}

object PpocrFiles {
    const val ASSET_DIR = "models/ppocr"
    const val DET = "det.onnx"
    fun recFor(lang: PpocrLang) = "rec_${lang.code}.onnx"
    fun dictFor(lang: PpocrLang) = "dict_${lang.code}.txt"
    fun asset(name: String) = "$ASSET_DIR/$name"
}

private data class ScoredBox(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val score: Float
)

class PpocrDetector(context: android.content.Context) {
    private val appContext = context.applicationContext

    private fun assetNames(): Set<String> = runCatching {
        appContext.assets.list(PpocrFiles.ASSET_DIR)?.toSet() ?: emptySet()
    }.getOrElse { emptySet() }

    private fun hasAsset(name: String): Boolean = runCatching {
        appContext.assets.open(PpocrFiles.asset(name)).use { it.read() != -1 }
    }.getOrElse { false }

    private val inferMutex = Mutex()
    private var detSession: OrtSession? = null
    private val recSessions = mutableMapOf<PpocrLang, OrtSession?>()
    private val dictCache = mutableMapOf<PpocrLang, List<String>>()

    /** Bahasa rec yang SIAP (onnx + dict sama-sama ikut ter-build). */
    fun availableLangs(): List<PpocrLang> {
        val names = assetNames()
        return PpocrLang.values().filter {
            names.contains(PpocrFiles.recFor(it)) && names.contains(PpocrFiles.dictFor(it))
        }
    }

    fun isAvailable(): Boolean {
        val names = assetNames()
        return names.contains(PpocrFiles.DET) && availableLangs().isNotEmpty()
    }

    /** Ringkasan status file untuk ditampilkan di dialog (tanpa download). */
    fun modelStatus(): String {
        val names = assetNames()
        if (names.isEmpty()) return "model belum ikut ter-build"
        val parts = mutableListOf<String>()
        parts.add(if (names.contains(PpocrFiles.DET)) "det ✓" else "det ✗")
        for (lang in PpocrLang.values()) {
            val ok = names.contains(PpocrFiles.recFor(lang)) &&
                names.contains(PpocrFiles.dictFor(lang))
            parts.add("${lang.code} ${if (ok) "✓" else "✗"}")
        }
        return parts.joinToString(" • ")
    }

    private suspend fun ensureDet(): OrtSession? {
        detSession?.let { return it }
        return inferMutex.withLock {
            detSession?.let { return@withLock it }
            try {
                val bytes = appContext.assets.open(PpocrFiles.asset(PpocrFiles.DET)).use { it.readBytes() }
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(bytes, opts).also { detSession = it }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun ensureRec(lang: PpocrLang): OrtSession? {
        if (recSessions.containsKey(lang)) return recSessions[lang]
        return inferMutex.withLock {
            if (recSessions.containsKey(lang)) return@withLock recSessions[lang]
            try {
                val bytes = appContext.assets.open(PpocrFiles.asset(PpocrFiles.recFor(lang))).use { it.readBytes() }
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(bytes, opts).also { recSessions[lang] = it }
            } catch (e: Exception) {
                e.printStackTrace()
                recSessions[lang] = null
                null
            }
        }
    }

    private fun loadDict(lang: PpocrLang): List<String>? {
        dictCache[lang]?.let { return it }
        return try {
            val lines = appContext.assets.open(PpocrFiles.asset(PpocrFiles.dictFor(lang)))
                .bufferedReader().readLines()
            if (lines.isEmpty()) return null
            dictCache[lang] = lines
            lines
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Pilih SATU model rec untuk run ini (Korea⊃Inggris, China⊃Inggris). */
    private fun chooseLang(scripts: Set<MLScript>, avail: List<PpocrLang>): PpocrLang? {
        if (MLScript.KOREAN in scripts && PpocrLang.KO in avail) return PpocrLang.KO
        if (MLScript.CHINESE in scripts && PpocrLang.ZH in avail) return PpocrLang.ZH
        if (MLScript.LATIN in scripts) {
            return listOf(PpocrLang.KO, PpocrLang.ZH, PpocrLang.EN).firstOrNull { it in avail }
        }
        // Jepang tidak didukung PP-OCR di sini (pakai ML Kit).
        return null
    }

    private fun scriptOf(lang: PpocrLang): MLScript = when (lang) {
        PpocrLang.KO -> MLScript.KOREAN
        PpocrLang.ZH -> MLScript.CHINESE
        PpocrLang.EN -> MLScript.LATIN
    }

    suspend fun detect(bitmap: Bitmap, scripts: Set<MLScript>): List<DetectedTextRegion> =
        withContext(Dispatchers.Default) {
            if (!isAvailable() || scripts.isEmpty()) return@withContext emptyList()
            val avail = availableLangs()
            val lang = chooseLang(scripts, avail) ?: return@withContext emptyList()
            val det = ensureDet() ?: return@withContext emptyList()
            val rec = ensureRec(lang) ?: return@withContext emptyList()
            val dict = loadDict(lang) ?: return@withContext emptyList()
            try {
                val boxes = detectBoxesTiled(det, bitmap)
                val kept = dedupeBoxes(boxes)
                val out = mutableListOf<DetectedTextRegion>()
                for (b in kept) {
                    val (text, score) = recognize(rec, bitmap, b, dict)
                    if (text.isBlank() || score < 0.4f) continue
                    out.add(
                        DetectedTextRegion(
                            text = text,
                            boundingBox = Rect(
                                b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()
                            ),
                            cornerPoints = null,
                            script = scriptOf(lang)
                        )
                    )
                }
                out
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /** Deteksi box per strip (gambar jangkung) lalu offset ke global. */
    private suspend fun detectBoxesTiled(det: OrtSession, bitmap: Bitmap): List<ScoredBox> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()
        val out = mutableListOf<ScoredBox>()
        if (h <= 1100) {
            out += runDet(det, bitmap, 0, 0, w, h)
            return out
        }
        val stripH = 960
        val overlap = 96
        var top = 0
        while (top < h) {
            val bottom = min(h, top + stripH)
            val curTop = if (bottom >= h) max(0, bottom - stripH) else top
            val curH = bottom - curTop
            if (curH <= 0) break
            var crop: Bitmap? = null
            try {
                crop = Bitmap.createBitmap(bitmap, 0, curTop, w, curH)
                for (b in runDet(det, crop, 0, curTop, w, curH)) {
                    out.add(b.copy(top = b.top + curTop, bottom = b.bottom + curTop))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runCatching { crop?.recycle() }
            }
            if (bottom >= h) break
            top += stripH - overlap
        }
        return out
    }

    /**
     * Deteksi DB pada region [l,t,w,h] dari [src] (koordinat dikembalikan
     * relatif terhadap region; pemanggil yang meng-offset).
     */
    private suspend fun runDet(
        det: OrtSession, src: Bitmap, l: Int, t: Int, w: Int, h: Int
    ): List<ScoredBox> {
        if (w <= 8 || h <= 8) return emptyList()
        val scale = min(1f, 960f / max(w, h).toFloat())
        val nw = max(8, (w * scale).toInt())
        val nh = max(8, (h * scale).toInt())
        // Padding ke kelipatan 32 (arsitektur det downsample 4x).
        val padW = ((nw + 31) / 32) * 32
        val padH = ((nh + 31) / 32) * 32
        var scaled: Bitmap? = null
        var full: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            full = Bitmap.createBitmap(src, l, t, w, h)
            scaled = Bitmap.createScaledBitmap(full, nw, nh, true)
            val px = IntArray(nw * nh)
            scaled.getPixels(px, 0, nw, 0, 0, nw, nh)
            val plane = padW * padH
            val data = FloatArray(3 * plane) { 0.5f }
            for (y in 0 until nh) {
                val row = y * nw
                val out = y * padW
                for (x in 0 until nw) {
                    val p = px[row + x]
                    val o = out + x
                    data[o] = ((((p shr 16) and 0xFF) / 255f) - 0.485f) / 0.229f
                    data[plane + o] = ((((p shr 8) and 0xFF) / 255f) - 0.456f) / 0.224f
                    data[2 * plane + o] = (((p and 0xFF) / 255f) - 0.406f) / 0.225f
                }
            }
            val env = OrtEnvironment.getEnvironment()
            inputTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, padH.toLong(), padW.toLong())
            )
            val out = inferMutex.withLock {
                results = det.run(mapOf(det.inputNames.first() to inputTensor))
                @Suppress("UNCHECKED_CAST")
                (results!![0].value as Array<Array<Array<FloatArray>>>)[0][0]
            }
            return decodeDbMap(out, padW, padH, nw, nh, w, h)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        } finally {
            runCatching { inputTensor?.close() }
            runCatching { results?.close() }
            runCatching { scaled?.recycle() }
            runCatching { full?.recycle() }
        }
    }

    /**
     * Decode peta probabilitas DB: ambang 0.3 → dilatasi → komponen
     * terhubung → kotak + unclip 1.8x → skor rerata. Koordinat dikembalikan
     * dalam piksel region [w,h].
     */
    private fun decodeDbMap(
        prob: Array<FloatArray>, padW: Int, padH: Int,
        nw: Int, nh: Int, w: Int, h: Int
    ): List<ScoredBox> {
        val oH = prob.size
        if (oH <= 0) return emptyList()
        val oW = prob[0].size
        if (oW <= 0) return emptyList()
        // 1) Biner + dilatasi 3x3 satu iterasi (tutup celah antar huruf).
        val bin = BooleanArray(oW * oH)
        for (y in 0 until oH) {
            val row = prob[y]
            val off = y * oW
            for (x in 0 until oW) {
                if (row[x] >= 0.3f) bin[off + x] = true
            }
        }
        val dil = BooleanArray(oW * oH)
        for (y in 0 until oH) {
            for (x in 0 until oW) {
                if (!bin[y * oW + x]) continue
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= oH) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= oW) continue
                        dil[yy * oW + xx] = true
                    }
                }
            }
        }
        // 2) Komponen terhubung (flood fill) + skor rerata probabilitas.
        val label = IntArray(oW * oH) { -1 }
        val stack = IntArray(oW * oH)
        val out = mutableListOf<ScoredBox>()
        var cur = 0
        val sx = w.toFloat() / nw.toFloat()
        val sy = h.toFloat() / nh.toFloat()
        // Peta output 1/4 dari input pad → faktor ke koordinat nw,nh.
        val mx = nw.toFloat() / (oW.toFloat())
        val my = nh.toFloat() / (oH.toFloat())
        for (i in dil.indices) {
            if (!dil[i] || label[i] != -1) continue
            var sp = 0
            stack[sp++] = i
            label[i] = cur
            var area = 0
            var sum = 0.0
            var minX = oW; var minY = oH; var maxX = -1; var maxY = -1
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % oW
                val y = p / oW
                area++
                sum += prob[y][x]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                if (x > 0) { val n = p - 1; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                if (x < oW - 1) { val n = p + 1; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                if (y > 0) { val n = p - oW; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
                if (y < oH - 1) { val n = p + oW; if (dil[n] && label[n] == -1) { label[n] = cur; stack[sp++] = n } }
            }
            cur++
            if (area < 16) continue
            val score = (sum / area).toFloat()
            if (score < 0.45f) continue
            // 3) Unclip: kembangkan ~1.8x dari tengah (kompensasi susut ambang).
            var x1 = (minX * mx - 0.4f * (maxX - minX + 1) * mx) * sx
            var y1 = (minY * my - 0.4f * (maxY - minY + 1) * my) * sy
            var x2 = ((maxX + 1) * mx + 0.4f * (maxX - minX + 1) * mx) * sx
            var y2 = ((maxY + 1) * my + 0.4f * (maxY - minY + 1) * my) * sy
            x1 = x1.coerceIn(0f, w.toFloat())
            y1 = y1.coerceIn(0f, h.toFloat())
            x2 = x2.coerceIn(0f, w.toFloat())
            y2 = y2.coerceIn(0f, h.toFloat())
            if (x2 - x1 < 6f || y2 - y1 < 6f) continue
            out.add(ScoredBox(x1, y1, x2, y2, score))
        }
        return out
    }

    private fun dedupeBoxes(boxes: List<ScoredBox>): List<ScoredBox> {
        val out = mutableListOf<ScoredBox>()
        for (b in boxes.sortedByDescending { it.score }) {
            if (out.none { iou(it, b) > 0.5f }) out.add(b)
        }
        return out
    }

    private fun iou(a: ScoredBox, b: ScoredBox): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        if (inter <= 0f) return 0f
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Rekognisi CTC greedy satu box: crop → tinggi 48 jaga rasio →
     * normalisasi (x-0.5)/0.5 → argmax runtut (buang blank & repetisi).
     */
    private suspend fun recognize(
        rec: OrtSession, src: Bitmap, b: ScoredBox, dict: List<String>
    ): Pair<String, Float> {
        val l = b.left.toInt().coerceIn(0, src.width - 1)
        val t = b.top.toInt().coerceIn(0, src.height - 1)
        val r = b.right.toInt().coerceIn(l + 1, src.width)
        val bo = b.bottom.toInt().coerceIn(t + 1, src.height)
        val bw = r - l
        val bh = bo - t
        if (bw < 4 || bh < 4) return "" to 0f
        val w = (48f * bw / bh).toInt().coerceIn(12, 640)
        var crop: Bitmap? = null
        var scaled: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            crop = Bitmap.createBitmap(src, l, t, bw, bh)
            scaled = Bitmap.createScaledBitmap(crop, w, 48, true)
            val px = IntArray(w * 48)
            scaled.getPixels(px, 0, w, 0, 0, w, 48)
            val data = FloatArray(3 * w * 48)
            val plane = w * 48
            for (i in px.indices) {
                val p = px[i]
                data[i] = ((((p shr 16) and 0xFF) / 255f) - 0.5f) / 0.5f
                data[plane + i] = ((((p shr 8) and 0xFF) / 255f) - 0.5f) / 0.5f
                data[2 * plane + i] = (((p and 0xFF) / 255f) - 0.5f) / 0.5f
            }
            val env = OrtEnvironment.getEnvironment()
            inputTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, 48, w.toLong())
            )
            val logits: Array<FloatArray> = inferMutex.withLock {
                results = rec.run(mapOf(rec.inputNames.first() to inputTensor))
                @Suppress("UNCHECKED_CAST")
                (results!![0].value as Array<Array<FloatArray>>)[0]
            }
            return ctcGreedy(logits, dict)
        } catch (e: Exception) {
            e.printStackTrace()
            return "" to 0f
        } finally {
            runCatching { inputTensor?.close() }
            runCatching { results?.close() }
            runCatching { crop?.recycle() }
            runCatching { scaled?.recycle() }
        }
    }

    /** CTC greedy + softmax-mean sebagai skor keyakinan. */
    private fun ctcGreedy(logits: Array<FloatArray>, dict: List<String>): Pair<String, Float> {
        val sb = StringBuilder()
        var prev = -1
        var sumConf = 0.0
        var count = 0
        for (row in logits) {
            var best = 0
            var bestV = row[0]
            var maxV = row[0]
            for (k in 1 until row.size) {
                val v = row[k]
                if (v > maxV) maxV = v
                if (v > bestV) {
                    bestV = v
                    best = k
                }
            }
            if (best != 0 && best != prev) {
                // Softmax parsial: exp(best-max) / Σ exp(v-max).
                var denom = 0.0
                for (v in row) denom += exp((v - maxV).toDouble())
                val conf = exp((bestV - maxV).toDouble()) / denom
                val ch = dict.getOrNull(best - 1) ?: ""
                if (ch.isNotEmpty()) {
                    sb.append(ch)
                    sumConf += conf
                    count++
                }
            }
            prev = best
        }
        val text = sb.toString()
        return text to if (count == 0 || text.isEmpty()) 0f else (sumConf / count).toFloat()
    }
}
