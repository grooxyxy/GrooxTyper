package com.grooxtyper.app.model

/**
 * Aturan kecocokan warna tongkat sihir — gaya **TypeR / Photoshop**.
 *
 * ## Kenapa metrik lama diganti?
 *
 * Sebelumnya ada **dua metrik berbeda di dua mesin**, dan itu membuat
 * slider "Toleransi" tak bisa diprediksi:
 *
 *  - [WandEngine] (jalur kecil/sekunder): Chebyshev di ruang
 *    **linear-light**, dengan ambang yang diturunkan dari kanal seed
 *    paling terang. Untuk seed gelap (mis. garis arang di 0,0,0) ambang
 *    itu jatuh ke ~1/255 — jadi mengetuk garis hitam dengan toleransi
 *    32 praktis **tidak milih apa pun**. Ini sumber keluhan "wand jelek".
 *  - [HugeWandEngine] (jalur yang dipakai UI, kanvas 720x16000):
 *    **Euclidean RGB**, jadi angka 32 berarti "jarak 32 di ruang
 *    Euclidean" — bukan "tiap kanal boleh beda 32".
 *
 * Dua aturan berbeda untuk slider yang sama = hasil tak konsisten
 * tergantung warna seed dan tergantung mesin mana yang aktif.
 *
 * ## Aturan baru
 *
 * TypeR (`photoshop.js` → `floodSelect`) memakai selisih absolut
 * **per-kanal terhadap warna seed**:
 *
 * ```
 * |dr| <= tol  &&  |dg| <= tol  &&  |db| <= tol
 * ```
 *
 * Jadi toleransi 32 berarti "tiap kanal boleh beda paling jauh 32" —
 * persis seperti yang diinema pengguna Photoshop, dan bisa diprediksi.
 *
 * Rumus itu sama dengan jarak **Chebyshev di ruang sRGB 0..255**, jadi
 * implementasinya murah: tanpa akar kuadrat, tanpa LUT linear-light.
 *
 * ## Anti-alias
 *
 * Batas keras tepat di `tol` (ala TypeR). Tepi 40% terakhir diberi
 * gradasi supaya tepi seleksi tak bergerigi — jadi tetep ada coverage
 * 0..255 seperti GIMP, cuma bentuk ramp-nya mengikuti batas keras yang
 * sama dengan aturan warna.
 */
object WandColorMatch {

    /**
     * Jarak per-kanal 0..255 antara piksel [p] dan warna seed.
     *
     * Equivalent dengan max(|dr|, |dg|, |db|) di ruang sRGB.
     */
    fun distance(p: Int, sr: Int, sg: Int, sb: Int): Int {
        val dr = ((p shr 16) and 0xFF) - sr
        val dg = ((p shr 8) and 0xFF) - sg
        val db = (p and 0xFF) - sb
        val adr = if (dr < 0) -dr else dr
        val adg = if (dg < 0) -dg else dg
        val adb = if (db < 0) -db else db
        return maxOf(adr, adg, adb)
    }

    /** Sama seperti [distance], tapi dari komponen warna terpisah. */
    fun distance(r: Int, g: Int, b: Int, sr: Int, sg: Int, sb: Int): Int {
        val adr = kotlin.math.abs(r - sr)
        val adg = kotlin.math.abs(g - sg)
        val adb = kotlin.math.abs(b - sb)
        return maxOf(adr, adg, adb)
    }

    /** True bila jarak piksel <= [tol]. */
    fun matches(d: Int, tol: Int): Boolean = d <= tol

    /**
     * Coverage 0..255 untuk jarak warna [d].
     *
     * - `d > tol` → 0 (di luar seleksi; batas keras ala TypeR).
     * - `d <= 0.6 * tol` → 1 (inti seleksi).
     * - di antaranya → gradasi dari 1 turun ke **0.6**.
     *
     * Kenapa turun ke 0.6 dan bukan 0: coverage 0.6 = 153, masih di atas
     * ambang binar 128, jadi **semua piksel dengan jarak <= tol tetap
     * terpilih** — persis aturan TypeR. Kalau ramp-nya turun ke 0,
     * piksel di batas toleransi ikut hilang dan hasilnya lebih ketat
     * dari yang user minta.
     *
     * @param antialias false → binar (1 atau 0).
     */
    fun coverage(d: Int, tol: Int, antialias: Boolean): Float {
        if (tol <= 0) return if (d == 0) 1f else 0f
        if (d > tol) return 0f
        if (!antialias) return 1f
        val edge = tol * 0.4f
        val solidLimit = tol - edge
        if (d <= solidLimit) return 1f
        // 1.0 di solidLimit -> 0.6 di toleransi, lalu 0 di luar toleransi.
        return (0.6f + ((tol - d) / edge) * 0.4f).coerceIn(0f, 1f)
    }
}
