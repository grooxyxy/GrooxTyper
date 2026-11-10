package com.grooxtyper.app.model.selection

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.grooxtyper.app.model.MaskContour

/**
 * Hasil refine satu seleksi.
 *
 * @param crop bitmap berisi coverage (alpha = 0..255) di dalam potongan
 *   [left, top] + ukuran crop. Ini yang jadi mask lembut di engine.
 * @param left koordinat kanvas X potongan.
 * @param top koordinat kanvas Y potongan.
 * @param outline path tertutup hasil operasi (dipakai marching ants + fill).
 */
data class RefinedSelection(
    val crop: Bitmap,
    val left: Int,
    val top: Int,
    val outline: Path
)

/**
 * Refine Edge ala Photoshop/TypeR untuk SEMUA tool seleksi.
 *
 * Kenapa file sendiri: operasi ini butuh buffer bitmap, dan kanvas
 * GrooxTyper bisa 720x16000 (11,5MP). Semua kerja dibatasi ke potongan
 * (crop) area seleksi — bukan bitmap sebesar kanvas — supaya tetap aman
 * di HP kecil.
 *
 * Operasi yang didukung (semua opsional, bisa dikombinasikan):
 *
 *  - **Expand**   : selections diperlebar r px (dilasi).
 *  - **Contract** : selections disempitkan r px (erosi).
 *  - **Border**   : menyisakan cincin setebal w px di tepi seleksi.
 *  - **Feather**  : tepi jadi lembut (alpha bergradasi), dipakai saat
 *                   copy/clear/inpaint sehingga transisi halus.
 *  - **Smooth**   : tepi diratakan (feather kecil + ambang 50%).
 *
 * Implementasi memakai operasi Canvas native (bukan loop piksel Kotlin):
 *  - dilasi  = isi + stroke lebar 2r (ROUND cap/join).
 *  - erosi   = isi dikurangi stroke lebar 2r (PorterDuff CLEAR).
 *  - cincin  = stroke setebal w (tepat di tengah garis batas).
 *  - feather = BlurMaskFilter pada coverage alpha.
 *
 * Semua geometri dibatasi crop, jadi biaya O(area seleksi), bukan
 * O(area kanvas).
 */
object SelectionRefiner {

    /** Batas piksel potongan agar tak OOM di HP kecil. */
    private const val MAX_CROP_PIXELS = 4_000_000L

    /**
     * Terapkan rangkaian operasi refine pada satu [path].
     *
     * @return null bila area kosong atau terlalu besar untuk diproses.
     */
    fun refine(
        path: Path,
        canvasWidth: Int,
        canvasHeight: Int,
        expand: Int = 0,
        contract: Int = 0,
        border: Int = 0,
        feather: Int = 0,
        smooth: Int = 0
    ): RefinedSelection? {
        if (path.isEmpty) return null
        val b = RectF()
        path.computeBounds(b, true)
        if (b.isEmpty) return null

        // Area kerja sedikit lebih besar dari seleksi supaya expand/border
        // tak terpotong tepi crop.
        val pad = 64 + maxOf(expand, contract, border, feather, smooth) * 2
        val left = maxOf(0, b.left.toInt() - pad)
        val top = maxOf(0, b.top.toInt() - pad)
        val right = minOf(canvasWidth, b.right.toInt() + pad + 1)
        val bottom = minOf(canvasHeight, b.bottom.toInt() + pad + 1)
        val w = right - left
        val h = bottom - top
        if (w <= 1 || h <= 1) return null
        if (w.toLong() * h > MAX_CROP_PIXELS) return null

        // Bentuk dasar: path diisi putih, alpha = coverage.
        var shape = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val basePath = Path(path)
        basePath.offset(-left.toFloat(), -top.toFloat())
        drawFilled(shape, basePath)

        // Path geometris untuk stroke (dilasi/erosi/cincin).
        // PENTING: selalu dalam KOORDINAT CROP (relatif 0,0), karena
        // dipakai menggambar di dalam bitmap crop. Konversi ke koordinat
        // kanvas baru dilakukan pada outline akhir.
        var geo = basePath

        if (expand > 0) {
            val grown = unionWithStroke(shape, geo, expand * 2)
            shape.recycle()
            shape = grown
            geo = outlineOfCrop(shape) ?: geo
        }
        if (contract > 0) {
            val shrunk = subtractStroke(shape, geo, contract * 2)
            shape.recycle()
            shape = shrunk
            geo = outlineOfCrop(shape) ?: geo
        }
        if (border > 0) {
            // Cincin = tepat pita lebar `border` di garis batas.
            val ring = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawStroked(ring, geo, border.toFloat())
            shape.recycle()
            shape = ring
            geo = outlineOfCrop(shape) ?: geo
        }
        if (feather > 0) {
            val soft = blurAlpha(shape, feather)
            shape.recycle()
            shape = soft
        }
        if (smooth > 0) {
            // Ratakan: halo kecil lalu ambang 50% → tepi tegas tanpa burr.
            val soft = blurAlpha(shape, smooth)
            shape.recycle()
            shape = thresholdAlpha(soft, 128)
            soft.recycle()
        }

        // Outline akhir digeser ke koordinat kanvas.
        val outline = outlineOfCrop(shape)?.let { cropPath ->
            val p = Path(cropPath)
            p.offset(left.toFloat(), top.toFloat())
            p
        } ?: Path(path)

        return RefinedSelection(shape, left, top, outline)
    }

    /** Isi path (putih, opaque) ke dalam bitmap. */
    private fun drawFilled(target: Bitmap, path: Path) {
        val c = Canvas(target)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        c.drawPath(path, p)
    }

    /** Isi + garis setebal [strokePx] (ROUND) → siluet melebar. */
    private fun unionWithStroke(base: Bitmap, path: Path, strokePx: Int): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(base, 0f, 0f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }
        c.drawPath(path, p)
        val clean = whitenAlpha(out)
        out.recycle()
        return clean
    }

    /** Isi dikurangi pita garis (PorterDuff CLEAR) → siluet menyempit. */
    private fun subtractStroke(base: Bitmap, path: Path, strokePx: Int): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(base, 0f, 0f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        c.drawPath(path, p)
        p.xfermode = null
        val clean = whitenAlpha(out)
        out.recycle()
        return clean
    }

    /** Garis saja (tanpa isi) → pita/cincin. */
    private fun drawStroked(target: Bitmap, path: Path, strokePx: Float) {
        val c = Canvas(target)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }
        c.drawPath(path, p)
    }

    /** Blur coverage alpha (feather) memakai BlurMaskFilter native. */
    private fun blurAlpha(base: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 40).toFloat()
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawBitmap(base, 0f, 0f, p)
        // BlurMaskFilter cuma pasti mengaburkan ALPHA; RGB bisa ternoda
        // di tepi. Mask engine butuh RGB putih + alpha coverage, jadi
        // normalkan ulang (satu pass atas crop, yg batasi 4MP).
        val clean = whitenAlpha(out)
        out.recycle()
        return clean
    }

    /**
     * Normalkan bitmap jadi RGB putih + alpha yang ada.
     *
     * Dipakai supaya mask engine (DST_IN / CLEAR) selalu memakai warna
     * putih dengan gradasi alpha — sama dengan mask bawaan yang digambar
     * dari path putih.
     */
    private fun whitenAlpha(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = (px[i] ushr 24) and 0xFF
            px[i] = (a shl 24) or 0x00FFFFFF
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Ambang alpha → biner (anti-alias kasar).
     *
     * Satu array piksel dipakai ulang (baca → ubah di tempat → tulis
     * balik) supaya tak alokasi 2x ukuran crop.
     */
    private fun thresholdAlpha(base: Bitmap, threshold: Int): Bitmap {
        val w = base.width
        val h = base.height
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = (px[i] ushr 24) and 0xFF
            val v = if (a >= threshold) 255 else 0
            px[i] = (v shl 24) or 0x00FFFFFF
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Kontur tertutup dari alpha bitmap (marching squares lewat
     * [MaskContour]) dalam KOORDINAT CROP (relatif 0,0).
     */
    private fun outlineOfCrop(bmp: Bitmap): Path? {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bin = BooleanArray(w * h)
        var any = false
        for (i in bin.indices) {
            val on = ((px[i] ushr 24) and 0xFF) >= 128
            bin[i] = on
            if (on) any = true
        }
        if (!any) return null
        return MaskContour.build(bin, w, h)
    }
}
