# Desain: Rombak SFX, Perbaikan Panel SFX, dan Pipeline Area Bubble

Tanggal: 30 September 2026
Status: disetujui di chat, menunggu implementasi
Lingkup: aplikasi Android GrooxTyper (Kotlin + Compose)

## 1. Masalah yang Diselesaikan

1. **Panel SFX tidak bisa disentuh.** Slider dan tombol di tab SFX bergerak secara
   logika, tetapi layarnya tidak pernah digambar ulang, sehingga dari sisi user
   tampak seperti tidak bisa disentuh.
2. **Brush SFX tidak sesuai keinginan.** Gaya bawaan kuas SFX dan teks SFX tidak
   terlihat seperti lettering SFX komik/manhwa yang menjadi referensi (`c2.webp`):
   gradasi isi, outline gelap tebal, bayangan lembut, huruf besar kapital, dan
   kemiringan per kata.
3. **Magic wand tidak bisa memilih area di dalam bubble dari satu gambar.**
   Area di dalam bubble yang terpilih meluber ke seluruh kertas putih, dan tidak
   ada cara membuat beberapa area sekaligus dari satu kanvas.

## 2. Batasan yang Berlaku

- Batas keras metode JVM 64KB. UI baru wajib di file/composable/holder terpisah,
  bukan ditambahkan di `CanvasEditorScreen` atau `TextEditorPanel`.
- Fungsi lokal Kotlin hanya melihat deklarasi di atasnya.
- Komentar dan kode memakai bahasa Indonesia tanpa karakter asing (CJK/Sirik).
- Setiap push harus lulus `node scripts/brush-check.js`, lalu commit, push, dan
  CI hijau.
- Tanpa model baru dan tanpa dependensi baru (pilihan C murni algoritma).
- API Agnes hanya di aplikasi, tidak pernah di-commit.

## 3. Bagian 1: Perbaikan Panel SFX

### Akar masalah

`GenreBrushEngine.Settings` adalah class biasa dengan properti `var` biasa.
Compose hanya menggambar ulang bila state yang dibaca (`mutableStateOf`,
`mutableIntStateOf`, `mutableFloatStateOf`, atau state turunan) berubah.
`GenreBrushPanel(brushEngine)` menerima argumen stabil, jadi setelah komposisi
pertama tidak ada sinyal invalidasi sama sekali. Gejalanya persis dilaporkan
user: kontrol ada, tapi "tidak bisa disentuh".

Bukti pola yang benar sudah ada di repo: `ForceFadeConfig` dan
`StabilizerConfig` di `BrushEngine.kt` memakai `by mutableStateOf(...)`.

### Perubahan

- Semua properti `Settings` menjadi state Compose:
  `by mutableStateOf(Boolean)`, `by mutableFloatStateOf(Float)`,
  `by mutableIntStateOf(Int)`, `by mutableStateOf(Int)` untuk warna.
- `copyFrom` dan `applyPreset` tetap bekerja (menulis ke state memicu redraw).
- Tidak ada perubahan pada algoritma render.

### Berkas

- `app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt`
- `scripts/brush-check.js` (asersi baru)

## 4. Bagian 2: Rombak SFX (teks dan kuas satu sistem)

### Referensi

- `c2.webp` (referensi visual user): SFX lettering Korea/manhwa - gradasi isi
  putih ke kuning/oranye, outline gelap, bayangan, tiap kata pada baseline dan
  sudut sendiri, huruf besar kapital.
- Laporan riset SFX sebelumnya: `/tmp/opencode/sfx-video/LAPORAN-SFX.md`
  (teknik lettering dari frame video).

### Syarat wajib

Angka default tidak boleh ditebak. Sebelum menulis angka, riset konvensi
lettering SFX nyata (web/GitHub/HuggingFace) dan catat sumbernya di
`docs/sfx-lettering-research.md`. Hasil riset menentukan angka default; yang
tidak ditemukan didokumentasikan sebagai keputusan sadar, bukan dikarang.

### Preset bersama

Satu `SfxStyle` dipakai dua konsumen: `TextBox` (teks SFX) dan
`GenreBrushEngine.Settings` (kuas SFX). Field yang sama, sumber angka yang
sama, supaya "gaya SFX" di app ini punya satu bahasa visual.

| Field | Arti |
|---|---|
| `gradStart`, `gradEnd`, `gradAngle` | gradasi isi |
| `inkScale` | tebal huruf relatif tinggi huruf (H) |
| `outlineScale` | tebal outline relatif `inkScale` |
| `outlineColor` | warna outline |
| `shadowDx`, `shadowDy`, `shadowBlur` | bayangan |
| `shadowColor` | warna bayangan |
| `roughness` | kasar tepi 0..1 |
| `tiltPerWord` | kemiringan tiap kata |
| `spatter` | percikan |

### Preset per genre

Empat genre dipertahankan (horror, romance, action, fantasy) karena sudah jadi
pemilihan user, tetapi bentuk, warna, dan angka defaultnya ditulis ulang dari
hasil riset. Tiap genre tetap punya setelan yang bisa diedit (lebar, gradasi,
opacity, outline, bayangan, tekstur, percikan) plus tombol "Bawaan".

Perbedaan yang wajib terlihat tanpaedisBMoga setelan:

| Genre | Ciri bentuk | Ciri warna |
|---|---|---|
| Horror | bergerigi tajam, tetesan menggantung, teks Condensed | gelap,outline terang, bayangan pekat |
| Romance | lembut, ujung bulat, sapuan lebar | gradasi hangat, outline tipis, bayangan lembut |
| Action | berepersi, runcing, garis kecepatan | kontras tinggi, outline tebal, offset bayangan tegas |
| Fantasy | bergelombang, kilau bintang, ornamen | gradasi berkilau, outline bersih, glow |

### Teks SFX

`TextBox` menerima `SfxStyleSpec` sehingga teks SFX memakai angka yang sama
dengan kuas SFX: gradasi isi, outline mengikuti cekungan huruf, bayangan, dan
kemiringan per kata (bukan per kotak).

### Berkas

- `app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt` (baru, preset bersama)
- `app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt`
- `app/src/main/java/com/grooxtyper/app/model/SfxInk.kt`
- `app/src/main/java/com/grooxtyper/app/model/TextBox.kt`
- `app/src/main/java/com/grooxtyper/app/ui/GenreBrushPanel.kt`
- `docs/sfx-lettering-research.md` (baru)

## 5. Bagian 3: Pipeline Area Bubble

### Keputusan

Opsi C: kontraksi, watershed, tumbuhkan geodesik. Tanpa model baru, tanpa
dependensi baru, berjalan di perangkat dalam orde belasan milidetik.

### Tahapan

Dimasukkan pada `WandEngine` sebagai satu fungsi tingkat satu yang murni,
`bubbleAreaAt(px, w, h, seedX, seedY, params): BubbleArea?`.

1. **Flood dari seed.** Pakai `WandEngine.flood` yang sudah ada: span scanline,
   metrik Chebyshev di ruang linear-light, coverage 0..255. Ambil biner pada
   ambang 128.
2. **Tolak kalau bocor ke tepi.** Bila komponen seed menyentuh batas kanvas,
   area dianggap bukan bubble dan hasilnya null (user mengetuk di luar bubble).
   Ini yang menjawab keluhan "area melebar ke seluruh kertas".
3. **Kontraksi.** Distance transform chamfer 3x3 pada biner, lalu ambil
   `dist >= r` dengan `r` naik dari 0 sampai komponen seed terpisah dari
   komponen terbesar. Ambang batas atas: `r` tidak boleh melebihi 0.55 *
   jarak maksimum, jika tidak hasilnya null.
4. **Pemisah bila bersinggungan.** Kalau setelah kontraksi komponen seed masih
   memuat lebih dari satu inti, jalankan watershed pada inti tersebut
   (`splitBubbles`). Ini kasus dua gelembung yang bersinggungan pada
   `image.webp`: garis pemisah keluar dari titik terakhir keduanya bersentuhan.
5. **Tumbuhkan geodesik.** Masing-masing inti ditumbuhkan di dalam mask asli
   (BFS multi-sumber) sehingga area akhir tidak pernah keluar lintas outline
   hitam, walau toleransi awal longgar.
6. **Kontur.** `SelectionEngine.traceContour` pada biner akhir, disederhanakan
   ambang 50% agar tepi jatuh di tepi asli objek.
7. **Kotak.** `RectF` per area dalam koordinat kanvas, dipakai untuk penomoran,
   urutan baca, dan pembuatan teks.

### Wongkeeping (jaga-jaga)

- Batas piksel 12 juta, sama seperti wand sekarang; lebih besar ditolak.
- Alokasi array dihitung sekali per pemanggilan; tidak ada alokasi objek di
  dalam loop piksel.
- Semua koordinat dikembalikan dalam koordinat kanvas penuh (keluar dari
  `downsampleForWand` dikalikan balik).

### Berkas

- `app/src/main/java/com/grooxtyper/app/model/WandEngine.kt`
- `app/src/main/java/com/grooxtyper/app/model/SelectionEngine.kt`
- `scripts/wand-check.mjs` (baru, uji numerik lokal)

## 6. Bagian 4: Multi-area

- Setiap ketukan wand **menambah** area, tidak menimpa. Sakelar "Tambah" tetap
  ada, dan ketuk area yang sudah ada menghapus area itu.
- Area bernomor **urutan baca manga**: baris pertama dari atas, dalam satu baris dari
  kiri ke kanan, dengan toleransi baris berdasarkan tinggi rata-rata area.
  Fungsi urutan ini sudah ada (`readingOrder` di `BubbleDetector.kt`) dan
  dipakai ulang.
- Overlay menggambar nomor area di dalam kontur, gaya visual sama dengan
  gelembung terdeteksi, agar keduanya tidak tertukar.

## 7. Bagian 5: Perilaku di Luar Gelembung

- Mode bubble: ketuk di luar gelembung ditolak dengan pesan singkat
  "bukan area bubble", tanpa mengubah dokumen.
- Tombol **"Area Panel"** untuk kasus caption dan narasi: membuat area dari
  wilayah panel/background dengan algoritma yang sama tanpa syarat tolak tepi,
  dan tidak menimpa area bubble.
- Area hasil "Area Panel" diberi tanda berbeda di overlay agar jelas bukan
  bubble.

## 8. Bagian 6: Isi Otomatis dari Script

- Memakai `BulkTextDialog` yang sudah ada untuk menempel daftar dialog.
- Tombol "Isi Area": baris script ke-1 masuk ke area bernomor 1, dan seterusnya
  dalam urutan baca.
- Per area: `TextBox` dengan `BubbleSpec` mengikuti bentuk area, `fitToRect`
  pada persegi terpanjang di dalam bentuk, auto-fit ukuran font, warna teks
  kontras terhadap isi area (hitung sekali saat area dibuat), dan teks yang
  lebar per baris mengikuti kurva bubble.
- Area yang tidak mendapat baris script tetap ada sebagai area kosong dan
  ditandai di overlay agar user tahu mana yang belum terisi.
- Tombol "Isi Ulang" menimpa isi area yang sudah terisi, dengan satu langkah
  undo.

## 9. Verifikasi

1. `scripts/brush-check.js` asersi statis untuk setiap keputusan di atas,
   wajib PASSED sebelum push.
2. `scripts/wand-check.mjs` uji numerik lokal: dua gelembung bersinggungan
   (geometri sama persis dengan `image.webp`) harus menghasilkan tepat dua area
   yang tidak saling tumpang tindih dan total luasnya menutup 90-100% interior.
   Area dalam satu gelembung menghasilkan satu area. Ketuk di luar gelembung
   menghasilkan null.
3. Uji render lokal untuk preset SFX baru: PNG keluaran untuk keempat genre,
   diperiksa manual (gradasi, outline, bayangan, bentuk).
4. CI hijau (Brush Check + Android CI).

## 10. Risiko dan Mitigasi

| Risiko | Mitigasi |
|---|---|
| `WandEngine` membesar melewati batas yang nyaman | Fungsi baru di file terpisah `BubbleAreaPipeline.kt`, bukan di dalam `WandEngine` |
| Kontraksi terlalu agresif pada gelembung kecil | Batas atas `r` 0.55 * jarak maksimum, dan minimal 3% piksel inti |
| Anti-alias coverage membuat biner bolak-balik | Ambang binar tetap 128 (titik tengah ramp) |
| SFX default meleset dariOpsinion user | Riset dulu, angka default dicatat bersumber; tombol Bawaan selalu mengembalikan ke preset |
| UI menumpuk dan menekan batas 64KB | Panel bubble area di file `BubbleAreaPanel.kt` terpisah |

## 11. Yang Tidak Termasuk

- Model segmenter bubble baru (opsi B).
- Deteksi gelembung otomatis massal tanpa ketukan (memang bukan jalur yang
  dipilih user).
- Huwalah teks otomatis dari gambar (OCR).
- Pengubahan format ekspor.
