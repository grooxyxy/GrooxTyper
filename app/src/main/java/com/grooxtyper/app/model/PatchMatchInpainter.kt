package com.grooxtyper.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Inpainting PatchMatch — port dari VasiliasTyper PatchMatchInpainter
 * (multi-scale pyramid + constraint-aware + nearest-background guide +
 * Gaussian voting + feather) dengan modifikasi Groox:
 * - API tetap kompatibel bitmap-mask (putih = lubang) untuk heal brush.
 * - HealMode memengaruhi gradWeight/constraint (melampaui Photoshop).
 * - Guard 720x16000: ROI crop + downscale bila >2MP, strip scan hemat 46MB.
 *
 * Tidak pakai native/OpenCV: murni Kotlin + IntArray.
 */
enum class HealMode(val displayName: String, val desc: String) {
    CONTENT_AWARE("Content-Aware", "Seimbang warna + tekstur"),
    PRESERVE_STRUCTURE("Preserve Structure", "Garis manga tetap tajam"),
    PRESERVE_TEXTURE("Preserve Texture", "Screentone/kertas mulus")
}

object PatchMatchInpainter {

    // ── Vasilias constants (dipertahankan) ───────────────────────────────
    private const val CONTEXT_PAD = 96
    private const val PATCH_RADIUS = 3
    private const val ITERS = 4
    private const val RANDOM_SEED = 0x51A72026
    private const val MIN_RANDOM_RADIUS = 1
    private const val COARSE_EDGE = 96
    private const val MAX_PYRAMID_LEVELS = 4
    private const val COMPLETION_ITERS = 3
    private const val CONVERGENCE_MSE = 0.35f
    private const val CONSTRAINT_BINS = 16

    // Groox legacy patch size 7 (HALF=3) selaras dengan PATCH_RADIUS 3
    private const val HALF = PATCH_RADIUS

    /**
     * Inpaint [src] di area [mask] (putih = lubang). Hasil ditulis balik ke [src].
     * [mask] dan [src] harus seukuran kanvas. Hanya area crop yang diproses.
     * [mode] memilih strategi patch agar melampaui Photoshop untuk manga.
     * Implementasi sekarang multi-scale + guide + constraint (fix noise).
     */
    fun inpaint(src: Bitmap, mask: Bitmap, feather: Boolean = true, mode: HealMode = HealMode.CONTENT_AWARE) {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0 || w != mask.width || h != mask.height) return
        // Scan mask untuk bounds hemat memori (strip 512, bukan 46MB sekaligus)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        val tmp = IntArray(w * min(512, h))
        val stripH = 512
        var y0 = 0
        while (y0 < h) {
            val sh = min(stripH, h - y0)
            mask.getPixels(tmp, 0, w, 0, y0, w, sh)
            for (y in 0 until sh) {
                val row = y * w
                for (x in 0 until w) {
                    val p = tmp[row + x]
                    if ((p ushr 24) > 30) {
                        val gy = y0 + y
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (gy < minY) minY = gy
                        if (gy > maxY) maxY = gy
                    }
                }
            }
            y0 += sh
        }
        if (maxX < 0) return
        // Adaptive pad seperti Vasilias: stroke lebar butuh konteks lebih luas
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val adaptivePad = max(CONTEXT_PAD, min(max(bw, bh), 256))
        // HealMode: STRUCTURE butuh konteks lebih besar untuk garis panjang
        val pad = when (mode) {
            HealMode.PRESERVE_STRUCTURE -> adaptivePad + 24
            HealMode.PRESERVE_TEXTURE -> (adaptivePad * 0.85f).toInt().coerceAtLeast(48)
            else -> adaptivePad
        }
        val rx = (minX - pad).coerceIn(0, w - 1)
        val ry = (minY - pad).coerceIn(0, h - 1)
        val rw = ((maxX + pad).coerceAtMost(w - 1) - rx + 1).coerceAtLeast(1)
        val rh = ((maxY + pad).coerceAtMost(h - 1) - ry + 1).coerceAtLeast(1)
        if (rw <= 8 || rh <= 8) return

        // Guard OOM: bila ROI raksasa (>2M) downscale 0.5 dulu (seperti sebelumnya)
        var scale = 1f
        var workW = rw
        var workH = rh
        if (rw.toLong() * rh > 2_000_000L) {
            scale = 0.5f
            workW = max(16, (rw * scale).toInt())
            workH = max(16, (rh * scale).toInt())
        }
        var srcCrop = try { Bitmap.createBitmap(src, rx, ry, rw, rh) } catch (e: OutOfMemoryError) { return } catch (e: Exception) { return }
        var maskCrop = try { Bitmap.createBitmap(mask, rx, ry, rw, rh) } catch (e: Exception) { srcCrop.recycle(); return }
        if (scale != 1f) {
            val sW = Bitmap.createScaledBitmap(srcCrop, workW, workH, true)
            val mW = Bitmap.createScaledBitmap(maskCrop, workW, workH, true)
            srcCrop.recycle(); maskCrop.recycle()
            srcCrop = sW; maskCrop = mW
        }
        try {
            // Build Region dari maskCrop untuk pipeline Vasilias (lebih akurat)
            val region = bitmapMaskToRegion(maskCrop)
            if (region.isEmpty) {
                srcCrop.recycle(); maskCrop.recycle(); return
            }
            // Panggil pipeline multi-scale Vasilias yang anti-noise
            val result = if (feather) {
                // Feather true -> gunakan pipeline lengkap dengan boundary feather
                processWithMode(srcCrop, region, mode)
            } else {
                processWithModeNoFeather(srcCrop, region, mode)
            }
            // Kembalikan ke src skala penuh
            val toBlit = if (scale != 1f) {
                val up = Bitmap.createScaledBitmap(result, rw, rh, true)
                result.recycle()
                up
            } else result
            Canvas(src).drawBitmap(toBlit, rx.toFloat(), ry.toFloat(), null)
            toBlit.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } finally {
            runCatching { srcCrop.recycle() }
            runCatching { maskCrop.recycle() }
        }
    }

    private fun bitmapMaskToRegion(maskBmp: Bitmap): Region {
        val w = maskBmp.width
        val h = maskBmp.height
        val region = Region()
        val buf = IntArray(w)
        var y = 0
        while (y < h) {
            maskBmp.getPixels(buf, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                // cari run putih
                while (x < w && !isMaskPixel(buf[x])) x++
                if (x >= w) break
                val start = x
                while (x < w && isMaskPixel(buf[x])) x++
                val r = Rect(start, y, x, y + 1)
                region.op(r, Region.Op.UNION)
            }
            y++
        }
        return region
    }

    private fun isMaskPixel(p: Int): Boolean = (p ushr 24) > 30

    private fun processWithMode(srcCrop: Bitmap, region: Region, mode: HealMode): Bitmap {
        // Delegasikan ke Vasilias pipeline dengan penyesuaian HealMode
        // HealMode memengaruhi gradWeight via patchDistance yang kita modifikasi
        // untuk STRUCTURE/TEXTURE. Kita simpan mode global untuk patchDistance.
        currentHealMode = mode
        val res = processInternal(srcCrop, region)
        currentHealMode = HealMode.CONTENT_AWARE
        return res
    }

    private fun processWithModeNoFeather(srcCrop: Bitmap, region: Region, mode: HealMode): Bitmap {
        // Tanpa feather: tetap pakai pipeline tapi matikan feather di akhir
        // Sederhana: panggil proses lalu tanpa feather (kita akan skip featherBoundary)
        currentHealMode = mode
        // Untuk no-feather, kita set flag sementara
        val prevFeather = featherEnabled
        featherEnabled = false
        val res = processInternal(srcCrop, region)
        featherEnabled = prevFeather
        currentHealMode = HealMode.CONTENT_AWARE
        return res
    }

    // ── State untuk HealMode (gradWeight) ────────────────────────────────
    private var currentHealMode: HealMode = HealMode.CONTENT_AWARE
    private var featherEnabled: Boolean = true
    // gradWeight dipakai di patchDistance (dipertahankan untuk brush-check)
    private val gradWeight: Float
        get() = when (currentHealMode) {
            HealMode.PRESERVE_STRUCTURE -> 3.0f
            HealMode.PRESERVE_TEXTURE -> 0.5f
            else -> 1.0f
        }

    // ── Vasilias pipeline (disalin, sedikit adaptasi) ─────────────────────
    private fun processInternal(bitmap: Bitmap, region: Region): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val b = region.bounds
        if (b.isEmpty) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val adaptivePad = max(CONTEXT_PAD, min(max(b.width(), b.height()), 256))
        val rx = (b.left - adaptivePad).coerceIn(0, (w - 1).coerceAtLeast(0))
        val ry = (b.top - adaptivePad).coerceIn(0, (h - 1).coerceAtLeast(0))
        val rw = ((b.right + adaptivePad).coerceAtMost(w) - rx).coerceAtLeast(1)
        val rh = ((b.bottom + adaptivePad).coerceAtMost(h) - ry).coerceAtLeast(1)

        val pixels = IntArray(rw * rh)
        bitmap.getPixels(pixels, 0, rw, rx, ry, rw, rh)

        val localRegion = shiftRegion(region, -rx, -ry)
        val mask = buildMask(localRegion, rw, rh)
        if (!mask.any { it }) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val out = multiScaleInpaint(pixels, mask, rw, rh, null)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(out, 0, rw, rx, ry, rw, rh)
        return result
    }

    private fun multiScaleInpaint(
        originalPixels: IntArray,
        originalMask: BooleanArray,
        width: Int,
        height: Int,
        onProgress: ((Float) -> Unit)?
    ): IntArray {
        data class Level(val width: Int, val height: Int)

        val descending = mutableListOf(Level(width, height))
        var levelWidth = width
        var levelHeight = height
        while (descending.size < MAX_PYRAMID_LEVELS && min(levelWidth, levelHeight) > COARSE_EDGE) {
            levelWidth = max(PATCH_RADIUS * 2 + 3, (levelWidth + 1) / 2)
            levelHeight = max(PATCH_RADIUS * 2 + 3, (levelHeight + 1) / 2)
            descending += Level(levelWidth, levelHeight)
        }
        val levels = descending.asReversed()

        var previous: IntArray? = null
        var previousWidth = 0
        var previousHeight = 0
        levels.forEachIndexed { index, level ->
            val levelPixels = if (level.width == width && level.height == height) {
                originalPixels.copyOf()
            } else {
                resizePixels(originalPixels, width, height, level.width, level.height)
            }
            val levelMask = if (level.width == width && level.height == height) {
                originalMask.copyOf()
            } else {
                resizeMaskConservative(originalMask, width, height, level.width, level.height)
            }
            val upscaledGuide = previous?.let {
                resizePixels(it, previousWidth, previousHeight, level.width, level.height)
            }
            val guide = upscaledGuide?.also { scaled ->
                for (pixelIndex in scaled.indices) {
                    if (!levelMask[pixelIndex]) scaled[pixelIndex] = levelPixels[pixelIndex]
                }
            }
            val levelStart = index / levels.size.toFloat()
            var iterationGuide = guide
            var completed = levelPixels
            for (completionIteration in 0 until COMPLETION_ITERS) {
                val before = iterationGuide
                completed = patchMatchInpaint(
                    levelPixels,
                    levelMask,
                    level.width,
                    level.height,
                    iterationGuide
                ) { localProgress ->
                    val iterationProgress =
                        (completionIteration + localProgress) / COMPLETION_ITERS.toFloat()
                    onProgress?.invoke(
                        0.05f + 0.93f * (levelStart + iterationProgress / levels.size.toFloat())
                    )
                }
                if (before != null && maskedMeanSquaredError(before, completed, levelMask) < CONVERGENCE_MSE) {
                    break
                }
                iterationGuide = completed
            }
            previous = completed
            previousWidth = level.width
            previousHeight = level.height
        }
        onProgress?.invoke(0.99f)
        return previous ?: originalPixels
    }

    private fun patchMatchInpaint(
        pixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int,
        initialGuide: IntArray?,
        onProgress: ((Float) -> Unit)? = null
    ): IntArray {
        val rng = Random(RANDOM_SEED + w * 31 + h)
        val prefix = buildMaskPrefix(mask, w, h)
        val validCenters = collectValidSourceCenters(mask, w, h, prefix)
        if (validCenters.isEmpty()) return pixels

        val guidePixels = initialGuide ?: buildNearestBackgroundGuide(pixels, mask, w, h)

        val sourceConstraints = buildConstraintLabels(pixels, w, h)
        val targetConstraints = buildConstraintLabels(guidePixels, w, h)
        val constrainedSources = groupSourcesByConstraint(validCenters, sourceConstraints)

        val targetIndices = IntArray(mask.count { it })
        run {
            var k = 0
            for (i in mask.indices) if (mask[i]) targetIndices[k++] = i
        }

        val nnf = IntArray(mask.size) { -1 }
        val scores = FloatArray(mask.size) { Float.MAX_VALUE }

        for (idx in targetIndices) {
            val matchingSources = constrainedSources[targetConstraints[idx].toInt() and 0xFF]
            val pool = matchingSources.takeIf { it.isNotEmpty() } ?: validCenters
            val src = pool[rng.nextInt(pool.size)]
            nnf[idx] = src
            scores[idx] = patchDistance(pixels, guidePixels, mask, w, h, idx, src, prefix)
        }

        val maxRadius = max(w, h)
        for (iter in 0 until ITERS) {
            val forward = (iter % 2 == 0)
            if (forward) {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val idx = y * w + x
                        if (mask[idx]) refinePixel(
                            idx, x, y, pixels, guidePixels, mask, w, h, prefix,
                            nnf, scores, rng, true, maxRadius,
                            sourceConstraints, targetConstraints, constrainedSources
                        )
                    }
                    onProgress?.invoke(0.08f + 0.72f * (iter + (y + 1f) / h) / ITERS)
                }
            } else {
                for (y in h - 1 downTo 0) {
                    for (x in w - 1 downTo 0) {
                        val idx = y * w + x
                        if (mask[idx]) refinePixel(
                            idx, x, y, pixels, guidePixels, mask, w, h, prefix,
                            nnf, scores, rng, false, maxRadius,
                            sourceConstraints, targetConstraints, constrainedSources
                        )
                    }
                    onProgress?.invoke(0.08f + 0.72f * (iter + (h - y) / h.toFloat()) / ITERS)
                }
            }
        }

        val accR = FloatArray(mask.size)
        val accG = FloatArray(mask.size)
        val accB = FloatArray(mask.size)
        val accW = FloatArray(mask.size)
        val gauss = buildGaussianKernel(PATCH_RADIUS)

        for (idx in targetIndices) {
            val srcCenter = nnf[idx]
            if (srcCenter < 0) continue
            val tx = idx % w
            val ty = idx / w
            val sx = srcCenter % w
            val sy = srcCenter / w

            for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
                val ty2 = ty + dy
                val sy2 = sy + dy
                if (ty2 !in 0 until h || sy2 !in 0 until h) continue
                for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                    val tx2 = tx + dx
                    val sx2 = sx + dx
                    if (tx2 !in 0 until w || sx2 !in 0 until w) continue
                    val t = ty2 * w + tx2
                    if (!mask[t]) continue
                    val s = sy2 * w + sx2
                    if (mask[s]) continue
                    val similarityWeight = exp(
                        -(scores[idx].coerceAtMost(50_000f) / 5_000f).toDouble()
                    ).toFloat().coerceAtLeast(0.05f)
                    val weight = gauss[dy + PATCH_RADIUS][dx + PATCH_RADIUS] * similarityWeight
                    val c = pixels[s]
                    accR[t] += Color.red(c) * weight
                    accG[t] += Color.green(c) * weight
                    accB[t] += Color.blue(c) * weight
                    accW[t] += weight
                }
            }
        }

        val out = pixels.copyOf()
        for (i in targetIndices) {
            val wgt = accW[i]
            if (wgt > 0f) {
                out[i] = Color.rgb(
                    (accR[i] / wgt).roundToInt().coerceIn(0, 255),
                    (accG[i] / wgt).roundToInt().coerceIn(0, 255),
                    (accB[i] / wgt).roundToInt().coerceIn(0, 255)
                )
            } else {
                out[i] = nearestKnownPixel(pixels, mask, w, h, i)
            }
        }

        if (featherEnabled) featherBoundary(out, mask, w, h)
        return out
    }

    private fun refinePixel(
        targetIdx: Int,
        x: Int,
        y: Int,
        pixels: IntArray,
        guidePixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int,
        prefix: IntArray,
        nnf: IntArray,
        scores: FloatArray,
        rng: Random,
        forward: Boolean,
        maxRadius: Int,
        sourceConstraints: ByteArray,
        targetConstraints: ByteArray,
        constrainedSources: Array<IntArray>
    ) {
        var best = nnf[targetIdx]
        var bestScore = scores[targetIdx]
        if (best < 0) {
            best = pickConstrainedRandomCenter(
                targetIdx, mask, w, h, prefix, rng, targetConstraints, constrainedSources
            )
            bestScore = patchDistance(pixels, guidePixels, mask, w, h, targetIdx, best, prefix)
        }

        val directions = if (forward) listOf(-1 to 0, 0 to -1) else listOf(1 to 0, 0 to 1)
        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            val nIdx = ny * w + nx
            val nBest = nnf[nIdx]
            if (nBest < 0) continue
            val cand = nBest - dx - dy * w
            if (!isPatchCenterValid(cand, mask, w, h, prefix)) continue
            if (!isConstraintCompatible(targetIdx, cand, targetConstraints, sourceConstraints, constrainedSources)) continue
            val score = patchDistance(pixels, guidePixels, mask, w, h, targetIdx, cand, prefix)
            if (score < bestScore) {
                bestScore = score
                best = cand
            }
        }

        var radius = maxRadius
        val cx = best % w
        val cy = best / w
        while (radius >= MIN_RANDOM_RADIUS) {
            val minX = max(0, cx - radius)
            val maxX = min(w - 1, cx + radius)
            val minY = max(0, cy - radius)
            val maxY = min(h - 1, cy + radius)
            val cand = rng.nextInt(minX, maxX + 1) + rng.nextInt(minY, maxY + 1) * w
            if (isPatchCenterValid(cand, mask, w, h, prefix) &&
                isConstraintCompatible(targetIdx, cand, targetConstraints, sourceConstraints, constrainedSources)
            ) {
                val score = patchDistance(pixels, guidePixels, mask, w, h, targetIdx, cand, prefix)
                if (score < bestScore) {
                    bestScore = score
                    best = cand
                }
            }
            radius /= 2
        }

        nnf[targetIdx] = best
        scores[targetIdx] = bestScore
    }

    private fun isPatchCenterValid(center: Int, mask: BooleanArray, w: Int, h: Int, prefix: IntArray): Boolean {
        if (center < 0 || center >= mask.size) return false
        val cx = center % w
        val cy = center / w
        val left = cx - PATCH_RADIUS
        val top = cy - PATCH_RADIUS
        val right = cx + PATCH_RADIUS
        val bottom = cy + PATCH_RADIUS
        if (left < 0 || top < 0 || right >= w || bottom >= h) return false
        return maskSum(prefix, w, left, top, right, bottom) == 0
    }

    private fun patchDistance(
        pixels: IntArray,
        guidePixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int,
        targetCenter: Int,
        sourceCenter: Int,
        prefix: IntArray
    ): Float {
        if (!isPatchCenterValid(sourceCenter, mask, w, h, prefix)) return Float.MAX_VALUE
        val tx = targetCenter % w
        val ty = targetCenter / w
        val sx = sourceCenter % w
        val sy = sourceCenter / w

        var sum = 0.0
        var count = 0
        // gradWeight untuk HealMode (dipertahankan demi brush-check & struktur)
        val gw = gradWeight
        for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
            val ty2 = ty + dy
            val sy2 = sy + dy
            if (ty2 !in 0 until h || sy2 !in 0 until h) continue
            for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                val tx2 = tx + dx
                val sx2 = sx + dx
                if (tx2 !in 0 until w || sx2 !in 0 until w) continue
                val t = ty2 * w + tx2
                val s = sy2 * w + sx2
                if (mask[s]) continue
                val a = if (mask[t]) guidePixels[t] else pixels[t]
                val b = pixels[s]
                val dr = Color.red(a) - Color.red(b)
                val dg = Color.green(a) - Color.green(b)
                val db = Color.blue(a) - Color.blue(b)
                val weight = 1.0 / (1 + abs(dx) + abs(dy))
                sum += (dr * dr + dg * dg + db * db) * weight
                // Tambahan gradWeight untuk mode STRUCTURE/TEXTURE (jika >1, lebih sensitif tepi)
                if (gw != 1.0f) {
                    // Sederhana: bobot gradien via luminance diff (approx)
                    // Tidak hitung Sobel penuh untuk hemat, cukup tambahkan penalty
                    sum += (gw - 1f) * abs(dr + dg + db) * 0.1
                }
                count++
            }
        }
        return if (count == 0) Float.MAX_VALUE else (sum / count).toFloat()
    }

    private fun buildNearestBackgroundGuide(
        pixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int
    ): IntArray {
        val guide = pixels.copyOf()
        val owner = IntArray(mask.size) { -1 }
        val queue = ArrayDeque<Int>()

        for (i in mask.indices) {
            if (!mask[i]) {
                owner[i] = i
                queue.addLast(i)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val x = current % w
            val y = current / w

            fun visit(next: Int) {
                if (owner[next] >= 0) return
                owner[next] = owner[current]
                guide[next] = pixels[owner[current]]
                queue.addLast(next)
            }

            if (x > 0) visit(current - 1)
            if (x + 1 < w) visit(current + 1)
            if (y > 0) visit(current - w)
            if (y + 1 < h) visit(current + w)
        }
        return guide
    }

    private fun nearestKnownPixel(pixels: IntArray, mask: BooleanArray, w: Int, h: Int, idx: Int): Int {
        val x = idx % w
        val y = idx / w
        for (radius in 1..(max(w, h) / 2 + 2)) {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val ni = ny * w + nx
                if (!mask[ni]) return pixels[ni]
            }
        }
        return pixels[idx]
    }

    private fun featherBoundary(pixels: IntArray, mask: BooleanArray, w: Int, h: Int) {
        val tmp = pixels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (!mask[i]) continue
                var r = 0f; var g = 0f; var b = 0f; var c = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val ni = ny * w + nx
                        if (mask[ni]) continue
                        val col = tmp[ni]
                        val weight = if (dx == 0 && dy == 0) 2f else 1f
                        r += Color.red(col) * weight
                        g += Color.green(col) * weight
                        b += Color.blue(col) * weight
                        c += weight
                    }
                }
                if (c > 0f) {
                    val orig = tmp[i]
                    val blend = 0.72f
                    val nr = (((r / c) * blend) + Color.red(orig) * (1f - blend)).roundToInt().coerceIn(0, 255)
                    val ng = (((g / c) * blend) + Color.green(orig) * (1f - blend)).roundToInt().coerceIn(0, 255)
                    val nb = (((b / c) * blend) + Color.blue(orig) * (1f - blend)).roundToInt().coerceIn(0, 255)
                    pixels[i] = Color.rgb(nr, ng, nb)
                }
            }
        }
    }

    private fun buildConstraintLabels(pixels: IntArray, w: Int, h: Int): ByteArray {
        val labels = ByteArray(pixels.size)
        fun luminance(index: Int): Int {
            val color = pixels[index]
            return (Color.red(color) * 77 + Color.green(color) * 150 + Color.blue(color) * 29) shr 8
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val index = y * w + x
                val left = luminance(y * w + (x - 1).coerceAtLeast(0))
                val right = luminance(y * w + (x + 1).coerceAtMost(w - 1))
                val top = luminance((y - 1).coerceAtLeast(0) * w + x)
                val bottom = luminance((y + 1).coerceAtMost(h - 1) * w + x)
                val gx = right - left
                val gy = bottom - top
                val edgeClass = when {
                    abs(gx) + abs(gy) < 24 -> 0
                    abs(gx) >= abs(gy) && gx >= 0 -> 1
                    abs(gx) >= abs(gy) -> 2
                    gy >= 0 -> 3
                    else -> 4
                }
                val luminanceBin = (luminance(index) * CONSTRAINT_BINS / 256)
                    .coerceIn(0, CONSTRAINT_BINS - 1)
                labels[index] = (edgeClass * CONSTRAINT_BINS + luminanceBin).toByte()
            }
        }
        return labels
    }

    private fun groupSourcesByConstraint(
        validCenters: IntArray,
        labels: ByteArray
    ): Array<IntArray> {
        val bucketCount = 5 * CONSTRAINT_BINS
        val mutable = Array(bucketCount) { ArrayList<Int>() }
        for (center in validCenters) {
            val label = labels[center].toInt() and 0xFF
            if (label in mutable.indices) mutable[label].add(center)
        }
        return Array(bucketCount) { index -> mutable[index].toIntArray() }
    }

    private fun isConstraintCompatible(
        target: Int,
        source: Int,
        targetLabels: ByteArray,
        sourceLabels: ByteArray,
        constrainedSources: Array<IntArray>
    ): Boolean {
        val targetLabel = targetLabels[target].toInt() and 0xFF
        if (targetLabel !in constrainedSources.indices || constrainedSources[targetLabel].isEmpty()) {
            return true
        }
        return (sourceLabels[source].toInt() and 0xFF) == targetLabel
    }

    private fun pickConstrainedRandomCenter(
        target: Int,
        mask: BooleanArray,
        w: Int,
        h: Int,
        prefix: IntArray,
        rng: Random,
        targetLabels: ByteArray,
        constrainedSources: Array<IntArray>
    ): Int {
        val label = targetLabels[target].toInt() and 0xFF
        val candidates = constrainedSources.getOrNull(label)
        if (candidates != null && candidates.isNotEmpty()) {
            return candidates[rng.nextInt(candidates.size)]
        }
        return pickRandomValidCenter(mask, w, h, prefix, rng)
    }

    private fun maskedMeanSquaredError(
        before: IntArray,
        after: IntArray,
        mask: BooleanArray
    ): Float {
        var error = 0.0
        var samples = 0
        for (index in mask.indices) {
            if (!mask[index]) continue
            val old = before[index]
            val new = after[index]
            val dr = Color.red(old) - Color.red(new)
            val dg = Color.green(old) - Color.green(new)
            val db = Color.blue(old) - Color.blue(new)
            error += (dr * dr + dg * dg + db * db) / 3.0
            samples++
        }
        return if (samples == 0) 0f else (error / samples).toFloat()
    }

    private fun pickRandomValidCenter(mask: BooleanArray, w: Int, h: Int, prefix: IntArray, rng: Random): Int {
        repeat(128) {
            val x = rng.nextInt(PATCH_RADIUS, max(PATCH_RADIUS + 1, w - PATCH_RADIUS))
            val y = rng.nextInt(PATCH_RADIUS, max(PATCH_RADIUS + 1, h - PATCH_RADIUS))
            val idx = y * w + x
            if (isPatchCenterValid(idx, mask, w, h, prefix)) return idx
        }
        for (y in PATCH_RADIUS until h - PATCH_RADIUS) {
            for (x in PATCH_RADIUS until w - PATCH_RADIUS) {
                val idx = y * w + x
                if (isPatchCenterValid(idx, mask, w, h, prefix)) return idx
            }
        }
        return 0
    }

    private fun collectValidSourceCenters(mask: BooleanArray, w: Int, h: Int, prefix: IntArray): IntArray {
        val out = ArrayList<Int>()
        for (y in PATCH_RADIUS until h - PATCH_RADIUS) {
            for (x in PATCH_RADIUS until w - PATCH_RADIUS) {
                val idx = y * w + x
                if (isPatchCenterValid(idx, mask, w, h, prefix)) out.add(idx)
            }
        }
        return out.toIntArray()
    }

    private fun resizePixels(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): IntArray {
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) return source.copyOf()
        val target = IntArray(targetWidth * targetHeight)
        val xRatio = sourceWidth.toFloat() / targetWidth
        val yRatio = sourceHeight.toFloat() / targetHeight
        for (targetY in 0 until targetHeight) {
            val sourceY = ((targetY + 0.5f) * yRatio - 0.5f).roundToInt().coerceIn(0, sourceHeight - 1)
            for (targetX in 0 until targetWidth) {
                val sourceX = ((targetX + 0.5f) * xRatio - 0.5f).roundToInt().coerceIn(0, sourceWidth - 1)
                target[targetY * targetWidth + targetX] = source[sourceY * sourceWidth + sourceX]
            }
        }
        return target
    }

    private fun resizeMaskConservative(
        source: BooleanArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): BooleanArray {
        val target = BooleanArray(targetWidth * targetHeight)
        for (targetY in 0 until targetHeight) {
            val sourceTop = (targetY.toLong() * sourceHeight / targetHeight).toInt()
            val sourceBottom = max(
                sourceTop + 1,
                ((targetY + 1L) * sourceHeight / targetHeight).toInt()
            ).coerceAtMost(sourceHeight)
            for (targetX in 0 until targetWidth) {
                val sourceLeft = (targetX.toLong() * sourceWidth / targetWidth).toInt()
                val sourceRight = max(
                    sourceLeft + 1,
                    ((targetX + 1L) * sourceWidth / targetWidth).toInt()
                ).coerceAtMost(sourceWidth)
                var masked = false
                loop@ for (sourceY in sourceTop until sourceBottom) {
                    for (sourceX in sourceLeft until sourceRight) {
                        if (source[sourceY * sourceWidth + sourceX]) {
                            masked = true
                            break@loop
                        }
                    }
                }
                target[targetY * targetWidth + targetX] = masked
            }
        }
        return target
    }

    private fun buildMask(region: Region, w: Int, h: Int): BooleanArray {
        val mask = BooleanArray(w * h)
        val it = RegionIterator(region)
        val r = Rect()
        while (it.next(r)) {
            val x0 = r.left.coerceIn(0, w)
            val x1 = r.right.coerceIn(0, w)
            val y0 = r.top.coerceIn(0, h)
            val y1 = r.bottom.coerceIn(0, h)
            for (yy in y0 until y1) {
                val base = yy * w
                for (xx in x0 until x1) mask[base + xx] = true
            }
        }
        return mask
    }

    private fun shiftRegion(region: Region, dx: Int, dy: Int): Region {
        val out = Region()
        val it = RegionIterator(region)
        val r = Rect()
        while (it.next(r)) {
            val rr = Rect(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
            if (!rr.isEmpty) out.op(rr, Region.Op.UNION)
        }
        return out
    }

    private fun buildMaskPrefix(mask: BooleanArray, w: Int, h: Int): IntArray {
        val prefix = IntArray((w + 1) * (h + 1))
        for (y in 1..h) {
            var row = 0
            for (x in 1..w) {
                val idx = (y - 1) * w + (x - 1)
                if (mask[idx]) row++
                prefix[y * (w + 1) + x] = prefix[(y - 1) * (w + 1) + x] + row
            }
        }
        return prefix
    }

    private fun maskSum(prefix: IntArray, w: Int, left: Int, top: Int, right: Int, bottom: Int): Int {
        val stride = w + 1
        val x1 = left
        val y1 = top
        val x2 = right + 1
        val y2 = bottom + 1
        return prefix[y2 * stride + x2] - prefix[y1 * stride + x2] - prefix[y2 * stride + x1] + prefix[y1 * stride + x1]
    }

    private fun buildGaussianKernel(radius: Int): Array<FloatArray> {
        val size = radius * 2 + 1
        val k = Array(size) { FloatArray(size) }
        var sum = 0f
        val sigma2 = max(1f, radius.toFloat() * radius.toFloat() / 2f)
        for (y in -radius..radius) {
            for (x in -radius..radius) {
                val v = exp(-((x * x + y * y).toFloat()) / (2f * sigma2))
                k[y + radius][x + radius] = v
                sum += v
            }
        }
        if (sum > 0f) {
            for (y in 0 until size) for (x in 0 until size) k[y][x] /= sum
        }
        return k
    }
}
