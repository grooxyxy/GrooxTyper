# Riset Konvensi Lettering SFX (untuk angka preset GrooxTyper)

Tanggal: 30 September 2026
Tujuan: menentukan angka default gaya SFX (teks dan kuas) tanpa menebak.
Referensi visual user: `c2.webp` (SFX lettering manhwa: gradasi isi, outline
gelap, bayangan keras, tiap kata miring).

## Ringkasan Temuan yang Mengubah Keputusan

1. **Outline SFX yang tebal bukan "stroke"**, melainkan salinan yang
   digeser (offset path). James Campbell, huruf komik profesional:
   teknik stroke+fill "hanya baik untuk dua atau tiga poin tebal"; untuk yang
   lebih tebal harus pakai `Path > Offset Path`. Modul SFX di GrooxTyper sudah
   memakai cara offset (pita yang lebih lebar), jadi arahnya sudah benar.
   [sumber: http://clintflickerlettering.blogspot.com/search/label/SFX]

2. **Outline SFX asli dua lapis dengan rasio 2:1**: lapisan luar hitam tebal
   tanpa isi, lapisan dalam putih setengah lebih tipis tanpa isi. Angka yang
   disebut di tutorial Comicraft: "thick black stroke (8pt or so)" lalu
   "a thinner (about half that) white stroke".
   [sumber: https://www.balloontales.com/articles/tutorial/part2.html]

3. **Bayangan SFX tidak boleh lembut (blur)**. Campbell menulis ENTANGGAL: "One
   word about soft drop shadows. No, seriously, just don't." Alasannya bayangan
   lembutoycekcab bayangan di satu bidang datar dan artis. SFX adalah bunyi,
   bukan benda fisik. Konsekuensi untuk preset: bayangan default harus
   **geseran keras tanpa kabut** (seperti pada `c2.webp`), bukan blur lembut.
   [sumber: http://clintflickerlettering.blogspot.com/search/label/SFX]

4. **Ekstruksi = massa.** "An extrusion supplies apparent mass, which is why
   heavy impact effects almost always carry one." Efek benturan besar hampir
   selalu memakai lapis ekstrusi. Ini alasan ACTION diberi offset bayangan
   tegas, bukan blur.
   [sumber: https://serifsup.com/sound-effects.htm]

5. **Bentuk kontur Replacement = jenis bunyi** (pemetaan yang dipakai untuk
   memisahkan empat genre):
   - keras, sisi lurus, sudut tajam, sambungan mendadak = dampak (logam,
     kerusakan, tembakan) -> ACTION
   - lembut, melengkung terus, goresan meruncing = cairan atau organik ->
     ROMANCE
   - tepi robek, compang-campang = distorsi atau kehancuran -> HORROR
   - melengkung ornamen, berkilau = sihir/fantasi -> FANTASY
   [sumber: https://serifsup.com/sound-effects.htm]

6. **Gradasi dibuat di atas isi solid**, digambar dari atas ke bawah supaya
   gradasi "pop": tutorial Clip Studio mengisi warna solid lalu menimpanya dengan
   gradient `Foreground to transparent` dan menyebut "do a gradient from the top
   to really make the gradient pop". Sudut default yang dipakai: 90 derajat
   (atas ke bawah).
   [sumber: https://tips.clip-studio.com/en-us/articles/5157]
   [sumber: https://tips.clip-studio.com/en-us/articles/3221]

7. **Per huruf, bukan per kata, dan sudutnya kecil**: "The B has been slightly
   rotated counter-clockwise and the M has been slightly rotated clockwise",
   dipakai untuk memberi kesan kacau/energi. Panduan typeset webtoon juga
   menyebut "figure out similar angular degrees to the original" tanpa memberi
   angka. Karena tak ada angka otoritatif, angka dipilih sadar di bawah.
   [sumber: https://tips.clip-studio.com/en-us/articles/5157]
   [sumber: https://guide.totus.pro/03e10b16-f510-4269-8e4f-71c94f649777]

8. **Huruf paling keras dibesarkan**: "Decide where the loudest part of the word
   is, and enlarge/reduce letters accordingly." Ini jadi}}+\mathrm{gradAngle}$ dan
   `inkScale` per genre, bukan gaya rata.
   [sumber: https://www.balloontales.com/articles/tutorial/part2.html]

9. **SFX kosong (hollow) untuk bunyi misterius**; glossary komik menyebut
   "OPEN SFX: all outline and no fill ... allow the artwork inside to show
   through" dan dipakai untuk bunyi penting/misterius.
   [sumber: https://www.balloontales.com/articles/glossary/index.html]

10. **Ukuran SFX webtoon**: praktisi webtoon menyebut ukuran font 15 untuk bunyi
    pelan, 30 sampai 45 untuk bunyi ekstrem, dan jarak antar huruf rapat
    (kerning) untuk pembicara yang bicara panjang. Ini alasan preset memakai
    Lettersmula besar, bukan kecil.
    [sumber: https://www.youtube.com/watch?v=lgV-fYKJqHI]

## Angka Preset dan Asalinya

Semua rasio memakai `H` = tinggi huruf (cap height) dalam piksel, sama seperti
`SfxInk` yang sudah dipakai di app.

| Angka | Nilai | Makna | Sumber |
|---|---|---|---|
| `outlineScale` | 0.30 (horor 0.26, romance 0.16, action 0.34, fantasy 0.24) | lebar outline luar dibagi tebal huruf | rasio 8pt : 4pt pada tutorial Comicraft dipakai sebagai anchor 2:1, lalu dikalikan ukuran visual tiap genre |
| `outlineInnerScale` | setengah `outlineScale` | ketebalan lapis dalam putih | "about half that" (Comicraft) |
| `inkScale` | 0.065 (semua genre) | tebal huruf relatif H | hasil ukur frame video sebelumnya, `/tmp/opencode/sfx-video/LAPORAN-SFX.md` |
| `gradAngle` | 90 derajat (fantasy 60) | arah gradasi isi | "gradient from the top" (Clip Studio) |
| `shadowBlur` | 0 untuk semua genre | bayangan keras | "just don't" soal soft drop shadow (Campbell) |
| `shadowDx` / `shadowDy` | 0.05H / 0.07H (action 0.09H / 0) | geseran bayangan | keputusan sadar: tidak ada sumber angka; rasio dipilih agar bayangan terbaca tanpa menutupi outline |
| `tiltPerWord` | 7 derajat (action 10, horror -6, fantasy 5, romance 3) | kemiringan tiap kata | "slightly rotated" tanpa angka (Clip Studio); dipilih sadar di bawah 12 derajat karena sudut besar membuat huruf gagal dibaca |
| `roughness` | 0.85 horror, 0.12 romance, 0.30 action, 0.45 fantasy | kasar tepi | pemetaan kontur di baris 5 |
| `spatter` | 10 / 0 / 6 / 14 | percikan tepi | "rough or spattered edge treatment supplies texture" (serifsup) |

## Keputusan Sadar (angka yang tidak ada sumbernya)

| Angka | Nilai | Alasan |
|---|---|---|
| `shadowDx`, `shadowDy` | 0.05H dan 0.07H | sumber hanya menyebut "drop shadow tidak baik", tidak ada angka geseran. Dipilih agar bayangan terlihat tanpa menutupi outline (outline luar 0.30 H). ACTION memakai geseran mendatar 0.09H karena benturan sering datang dari samping. |
| `tiltPerWord` | 3 sampai 10 derajat | sumber menyebut "slightly" tanpa angka. Di atas 12 derajat huruf mulai sulit dibaca, jadi dibatasi 10. |
| `roughness` per genre | 0.12 sampai 0.85 | pemetaan kontur memberi arah (keras/ragged/lembut), angka dipilih di ujung rentang agar perbedaan genre terlihat jelas tanpa setelan. |
| `gradAngle` fantasy 60 | 60 derajat | gradasi diagonal dipakai untuk kesan berkilau; sumber hanya menyebut arah atas-bawah sebagai yang paling "pop". |
| `spatter` | 0 sampai 14 | sumber menyebut perlakuan tepi yang diserbati tanpa angka. |

## Yang Tidak Dipakai

- **Soft drop shadow** untuk semua genre: ditolak karena sumber profesional
  melanggarnya secara eksplisit.
- **Stroke tipis 2-3 poin** untuk outline berat: ditolak karena sumber yang sama
  menyatakan itu hanya cocok untuk outline tipis; outline SFX yang terlihat
  "pop" perlu offset.
- **Model/SAM untuk|● determining warna**: warna teks tetap dihitung dari isi
  area (luminansi median), bukan dari model, supaya tidak menambah dependensi.
