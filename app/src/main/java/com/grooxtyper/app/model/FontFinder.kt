package com.grooxtyper.app.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Font Finder — cari & unduh font untuk lettering manga.
 *
 * ## Apa bedanya dari TypeR?
 *
 * TypeR punya "Font FindeR": cari font yang mirip lalu unduh. GrooxTyper
 * sebelumnya hanya bisa **cari font yang sudah terpasang** (18 font komik
 * bawaan + font custom) dan **impor manual** file .ttf.
 *
 * Tool ini menambah: **cari di katalog publik** lalu **unduh + pasang**
 * dengan satu ketukan.
 *
 * ## Katalog
 *
 * Memakai **Google Fonts** (`fonts.google.com/metadata/fonts`), yang:
 *
 *  - publik, tanpa API key, tanpa biaya;
 *  - semua font berlisensi OFL (SIL Open Font License) — sama seperti
 *    font bawaan GrooxTyper, jadi aman didistribusikan ulang di APK;
 *  - TIDAK memakai layanan/API pihak ketiga milik TypeR; katalog & kode
 *    TypeR tak dipakai atau disalin sama sekali.
 *
 * Metadata (2,7MB) di-cache di cacheDir dan dipakai ulang offline.
 *
 * ## Pencocokan (mengikuti perilaku TypeR)
 *
 * TypeR membersihkan nama font dari sufiks gaya ("Regular", "Bold",
 * "Condensed", ...), lalu mem Ratings kecocokan dengan:
 *
 *  - prefiks sama → 80
 *  - substring → 80
 *  - koefisien Sørensen–Dice (bigram huruf) → s/d 85
 *  - nama persis → 95
 *
 * Skor minimum 70. Versi ini menambah bonus kategori: font dengan
 * kategori sama (mis. "Handwriting" untuk "Patrick Hand") naik-skor,
 * karena itulah yang dicari user lettering komik.
 */
class FontFinder(private val context: Context) {

    private val cacheFile: File
        get() = File(context.cacheDir, "google-fonts-metadata.json")

    /** Satu keluarga font dari katalog. */
    data class Family(
        val family: String,
        val displayName: String,
        val category: String,
        /** Nama file TTF varian regular. */
        val fileName: String
    )

    /** Hasil pencarian + skor kecocokan. */
    data class Result(
        val family: Family,
        val score: Int
    )

    /**
     * Cari font yang namanya mirip [query].
     *
     * @param limit jumlah hasil maksimum.
     */
    suspend fun search(query: String, limit: Int = 40): List<Result> =
        withContext(Dispatchers.IO) {
            val families = loadFamilies() ?: return@withContext emptyList()
            val core = coreName(query)
            if (core.isEmpty()) return@withContext emptyList()
            families.map { fam -> Result(fam, scoreFamily(core, fam)) }
                .filter { it.score >= MIN_SCORE }
                .sortedByDescending { it.score }
                .take(limit)
        }

    /**
     * Cari font yang mirip [fontName] (mis. font yang sedang dipakai).
     *
     * Selain kecocokan nama, kategori yang sama diberi bonus -- inilah
     * cara paling efektif menemukan font komik yang mirip.
     */
    suspend fun similarTo(fontName: String, limit: Int = 40): List<Result> =
        withContext(Dispatchers.IO) {
            val families = loadFamilies() ?: return@withContext emptyList()
            val core = coreName(fontName)
            if (core.isEmpty()) return@withContext emptyList()
            val source = families.firstOrNull {
                coreName(it.family) == core || it.family.equals(fontName.trim(), true)
            }
            val category = source?.category
            families.map { fam ->
                var s = scoreFamily(core, fam)
                if (category != null && fam.category.equals(category, true)) {
                    s = min(100, s + 8)
                }
                Result(fam, s)
            }
                .filter { it.score >= MIN_SCORE }
                .sortedByDescending { it.score }
                .take(limit)
        }

    /**
     * Unduh file TTF varian regular dari [family].
     *
     * Alur: minta CSS Google Fonts (User-Agent polos supaya Google
     * mengembalikan .ttf, bukan .woff2 yang tak didukung Android), ambil
     * URL-nya, lalu unduh binary-nya.
     *
     * @return byte TTF, atau null bila gagal.
     */
    suspend fun download(family: Family): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val cssUrl = "https://fonts.googleapis.com/css2?family=" +
                URLEncoder.encode(family.family, "UTF-8").replace("+", "%20")
            val css = URL(cssUrl).openStream().use { it.readBytes().toString(Charsets.UTF_8) }
            val ttfUrl = Regex("url\\((https://[^)]+\\.ttf)\\)").find(css)?.groupValues?.get(1)
                ?: return@withContext null
            URL(ttfUrl).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Log.w("FontFinder", "gagal unduh ${family.family}: ${e.message}")
            null
        }
    }

    // ---------------- cache metadata ----------------

    private suspend fun loadFamilies(): List<Family>? {
        val cached = readCache()
        if (cached != null) return cached
        // Unduh SEKALI: JSON mentah dipakai untuk parse sekaligus ditulis
        // ke cache (tak perlu unduh ulang 2,7MB).
        return try {
            val text = URL(METADATA_URL).openStream().use { it.readBytes().toString(Charsets.UTF_8) }
            writeCache(text)
            parseFamilies(org.json.JSONObject(text))
        } catch (e: Exception) {
            Log.w("FontFinder", "gagal ambil metadata: ${e.message}")
            null
        }
    }

    private fun readCache(): List<Family>? {
        val f = cacheFile
        if (!f.exists()) return null
        // Segarkan setelah 7 hari agar daftar font baru ikut terambil.
        if (System.currentTimeMillis() - f.lastModified() > CACHE_TTL_MS) return null
        return try {
            parseFamilies(org.json.JSONObject(f.readText()))
        } catch (e: Exception) {
            Log.w("FontFinder", "cache rusak: ${e.message}")
            null
        }
    }

    /** Simpan JSON mentah server supaya cache == respons asli. */
    private fun writeCache(rawJson: String) {
        try {
            val f = cacheFile
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(rawJson)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        } catch (e: Exception) {
            Log.w("FontFinder", "gagal menulis cache: ${e.message}")
        }
    }

    private fun parseFamilies(root: org.json.JSONObject): List<Family> {
        val out = ArrayList<Family>()
        val list = root.optJSONArray("familyMetadataList") ?: return out
        for (i in 0 until list.length()) {
            val fm = list.optJSONObject(i) ?: continue
            val family = fm.optString("family").trim()
            if (family.isEmpty()) continue
            val fonts = fm.optJSONArray("fonts") ?: continue
            // Varian "Regular" (weight 400, bukan italic) adalah yang paling
            // berguna untuk diunduh: bold/italic sintetis diterapkan app.
            var fileName = ""
            var best = Int.MAX_VALUE
            for (j in 0 until fonts.length()) {
                val fo = fonts.optJSONObject(j) ?: continue
                val weight = fo.optInt("weight", 400)
                val style = fo.optString("style", "normal")
                val fname = fo.optString("filename")
                if (fname.isEmpty()) continue
                val penalty = if (style.equals("normal", true)) weight else 1000 + weight
                if (penalty < best) {
                    best = penalty
                    fileName = fname
                }
            }
            if (fileName.isEmpty()) continue
            out.add(
                Family(
                    family = family,
                    displayName = fm.optString("displayName", family),
                    category = fm.optString("category", ""),
                    fileName = fileName
                )
            )
        }
        return out
    }

    // ---------------- pencocokan nama ----------------

    /**
     * Bersihkan nama font dari sufiks gaya + token noise, lalu gabungkan
     * sisa kata. "Patrick Hand-Regular" -> "patrickhand".
     */
    private fun coreName(name: String): String {
        var text = name.trim()
        val lastDash = text.lastIndexOf('-')
        if (lastDash > 0 && STYLE_SUFFIX.contains(text.substring(lastDash + 1).lowercase(Locale.US))) {
            text = text.substring(0, lastDash)
        }
        val words = text.split(' ', '_', '-')
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.US) }
            .filter { w -> !NOISE_TOKENS.contains(w) && !NOISE_PATTERN.matches(w) }
        return words.joinToString("")
    }

    /** Skor kecocokan sebuah keluarga terhadap core name. */
    private fun scoreFamily(core: String, family: Family): Int {
        if (core.isEmpty()) return 0
        val candidate = coreName(family.family)
        if (candidate.isEmpty()) return 0
        if (candidate == core) return EXACT_SCORE
        val shorter = if (core.length <= candidate.length) core else candidate
        val longer = if (shorter === core) candidate else core
        val ratio = shorter.length.toFloat() / longer.length
        var score = 0
        if (longer.startsWith(shorter) && ratio >= 0.65f) score = 80
        else if (longer.contains(shorter) && ratio >= 0.75f) score = 80
        val dice = diceSimilarity(core, candidate)
        if (dice >= 0.85f) score = max(score, (dice * 85).toInt())
        return score
    }

    /** Koefisien Sørensen–Dice atas bigram huruf (0..1). */
    private fun diceSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        if (a.length < 2 || b.length < 2) return 0f
        val pairs = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val p = a.substring(i, i + 2)
            pairs[p] = (pairs[p] ?: 0) + 1
        }
        var common = 0
        for (i in 0 until b.length - 1) {
            val p = b.substring(i, i + 2)
            val c = pairs[p] ?: 0
            if (c > 0) {
                pairs[p] = c - 1
                common++
            }
        }
        return (2f * common) / (a.length + b.length - 2)
    }

    companion object {
        private const val METADATA_URL = "https://fonts.google.com/metadata/fonts"
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val EXACT_SCORE = 95
        private const val MIN_SCORE = 70

        /** Sufiks gaya yang dibuang saat mencocokkan nama. */
        private val STYLE_SUFFIX = setOf(
            "regular", "reg", "bold", "bd", "italic", "ital", "it", "oblique", "obl",
            "light", "lt", "medium", "med", "semi", "demi", "extra", "ultra",
            "black", "blk", "heavy", "thin", "book", "roman", "condensed", "cond",
            "narrow", "wide", "expanded", "plain", "normal", "mt", "ps"
        )

        /** Token generik yang tak membedakan font satu sama lain. */
        private val NOISE_TOKENS = setOf(
            "bb", "std", "pro", "lt", "mt", "ot", "tt", "otf", "ttf", "u", "e"
        )

        /** Pola seperti "w12" (weight dalam nama file). */
        private val NOISE_PATTERN = Regex("^w\\d{2}$")
    }
}
