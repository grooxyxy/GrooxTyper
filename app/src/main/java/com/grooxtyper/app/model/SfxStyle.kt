package com.grooxtyper.app.model

import android.graphics.Color

/**
 * Sumber angka gaya SFX yang tunggal, dipakai dua konsumen:
 *
 *  - [GenreBrushEngine.Settings] untuk KUAS goresan SFX,
 *  - `TextBox.sfxStyle` untuk TEKS SFX.
 *
 * Dulu tiap sisi punya angka sendiri sehingga kuas dan teks SFX terlihat
 * berbedaerna dari satu sama lain. Sekarang keduanya membaca preset yang sama.
 *
 * SEMUA angka di bawah berasal dari `docs/sfx-lettering-research.md`, yang
 * mencatat sumber tiap angka (Comicraft, James Campbell, serifsup, tutorial
 * Clip Studio, panduan typeset webtoon). Angka yang tidak punya sumber dicatat
 * di sana sebagai keputusan sadar, bukan karangan. Ringkas:
 *
 *  - Outline SFX yang tebal harus lewat OFFSET (salinan digeser), bukan stroke
 *    tipis. Modul SFX di app ini memang sudah memakai offset (pita lebih lebar).
 *  - Outline asli dua lapis rasio 2:1: luar tebal, dalam setengahnya.
 *  - Bayangan SFX TIDAK boleh lembut. Letterer profesional menolaknya eksplisit
 *    ("just don't"), jadi semua preset memakai blur 0 (geseran keras), seperti
 *    pada referensi `c2.webp`.
 *  - Bentuk kontur menentukan jenis bunyi: keras/sudut tajam = action, tepi
 *    robek = horror, melengkung lembut = romance, ornamen = fantasy.
 */
enum class SfxGenre(val displayName: String) {
    HORROR("SFX Horror"),
    ROMANCE("SFX Romance"),
    ACTION("SFX Action"),
    FANTASY("SFX Fantasy");

    companion object {
        /** Petakan genre mesin kuas ke genre gaya. */
        fun of(genre: GenreBrushEngine.Genre): SfxGenre = when (genre) {
            GenreBrushEngine.Genre.HORROR -> HORROR
            GenreBrushEngine.Genre.ROMANCE -> ROMANCE
            GenreBrushEngine.Genre.ACTION -> ACTION
            GenreBrushEngine.Genre.FANTASY -> FANTASY
        }
    }
}

/**
 * Gaya SFX dalam satuan relatif terhadap [H] tinggi huruf, bukan piksel absolut,
 * supaya preset tetap benar pada ukuran huruf berapa pun.
 */
data class SfxStyleSpec(
    /** Warna gradasi isi: warna atas dan bawah. */
    val gradStart: Int,
    val gradEnd: Int,
    /** Sudut gradasi (derajat). 90 = atas ke bawah, paling "pop". */
    val gradAngle: Float,
    /** Tebal huruf relatif H. Angka hasil ukur video lettering sebelumnya. */
    val inkScale: Float,
    /** Lebar outline luar relatif H. */
    val outlineScale: Float,
    val outlineColor: Int,
    /** Bayangan keras (blur selalu 0), geseran relatif H. */
    val shadowDx: Float,
    val shadowDy: Float,
    val shadowBlur: Float,
    val shadowColor: Int,
    /** Kasar tepi 0..1: horror paling robek, romance paling licin. */
    val roughness: Float,
    /** Kemiringan tiap kata (derajat). "Slightly rotated" = kecil. */
    val tiltPerWord: Float,
    /** Percikan tepi; sumber menyebut tepi diserbati tanpa angka. */
    val spatter: Int
) {
    /**
     * Lebar outline lapis dalam (warna terang) = setengah lapis luar.
     * Angka 8pt : 4pt pada tutorial Comicraft dipakai sebagai jangkar 2:1.
     */
    fun outlineInnerScale(): Float = outlineScale * 0.5f

    fun copy(): SfxStyleSpec = SfxStyleSpec(
        gradStart, gradEnd, gradAngle, inkScale, outlineScale, outlineColor,
        shadowDx, shadowDy, shadowBlur, shadowColor, roughness, tiltPerWord, spatter
    )

    companion object {
        /**
         * Preset bawaan tiap genre. Angka taken dari
         * `docs/sfx-lettering-research.md` dengan alasan per genre:
         *
         *  - HORROR: tepi robek (distorsi), outline gelap tapi rujukan
         *    `c2.webp` memakai outline terang agar huruf gelap terbaca di atas
         *    kertas; geseran bayangan ke bawah.
         *  - ROMANCE: gradasi hangat, kontur melengkung (cairan/organik),
         *    outline paling tipis, tanpa percikan.
         *  - ACTION: kontur keras + sudut tajam (dampak/logam), outline paling
         *    tebal, bayangan geser mendatar (benturan datang dari samping).
         *  - FANTASY: gradasi diagonal + rombakan, outline bersih, percikan
         *    paling banyak.
         */
        fun presetOf(genre: SfxGenre): SfxStyleSpec = when (genre) {
            SfxGenre.HORROR -> SfxStyleSpec(
                gradStart = 0xFF1C1C22.toInt(),
                gradEnd = 0xFF6B1F2B.toInt(),
                gradAngle = 90f,
                inkScale = 0.065f,
                outlineScale = 0.26f,
                outlineColor = 0xFFE8DFCE.toInt(),
                shadowDx = 0.0f,
                shadowDy = 0.07f,
                shadowBlur = 0f,
                shadowColor = 0xB3000000.toInt(),
                roughness = 0.85f,
                tiltPerWord = -6f,
                spatter = 10
            )
            SfxGenre.ROMANCE -> SfxStyleSpec(
                gradStart = 0xFFFFF2F5.toInt(),
                gradEnd = 0xFFFF5E8A.toInt(),
                gradAngle = 90f,
                inkScale = 0.065f,
                outlineScale = 0.16f,
                outlineColor = 0xFFFFFFFF.toInt(),
                shadowDx = 0.0f,
                shadowDy = 0.05f,
                shadowBlur = 0f,
                shadowColor = 0x8C7A1B3A.toInt(),
                roughness = 0.12f,
                tiltPerWord = 3f,
                spatter = 0
            )
            SfxGenre.ACTION -> SfxStyleSpec(
                gradStart = 0xFFFFF0B3.toInt(),
                gradEnd = 0xFFE64A19.toInt(),
                gradAngle = 90f,
                inkScale = 0.065f,
                outlineScale = 0.34f,
                outlineColor = 0xFF141418.toInt(),
                shadowDx = 0.09f,
                shadowDy = 0.0f,
                shadowBlur = 0f,
                shadowColor = 0xE6000000.toInt(),
                roughness = 0.30f,
                tiltPerWord = 10f,
                spatter = 6
            )
            SfxGenre.FANTASY -> SfxStyleSpec(
                gradStart = 0xFFFFFDE7.toInt(),
                gradEnd = 0xFF7C4DFF.toInt(),
                gradAngle = 60f,
                inkScale = 0.065f,
                outlineScale = 0.24f,
                outlineColor = 0xFF2B1140.toInt(),
                shadowDx = 0.0f,
                shadowDy = 0.06f,
                shadowBlur = 0f,
                shadowColor = 0x994A148C.toInt(),
                roughness = 0.45f,
                tiltPerWord = 5f,
                spatter = 14
            )
        }

        /** Warna netral untuk teks yang belum memilih genre. */
        val NETRAL: SfxStyleSpec = SfxStyleSpec(
            gradStart = Color.WHITE,
            gradEnd = Color.LTGRAY,
            gradAngle = 90f,
            inkScale = 0.065f,
            outlineScale = 0.24f,
            outlineColor = Color.BLACK,
            shadowDx = 0f,
            shadowDy = 0.06f,
            shadowBlur = 0f,
            shadowColor = 0x80000000.toInt(),
            roughness = 0.25f,
            tiltPerWord = 0f,
            spatter = 0
        )
    }
}
