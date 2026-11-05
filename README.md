# GrooxTyper

Editor terjemahan manga on-device (Android): deteksi teks ML Kit, deteksi balon
teks on-device via model YOLO detect end-to-end (bubble, 2 kelas; kelas 0 dipakai),
inpainting Telea native C++, dan kanvas jangkung hingga 720x16000.

## Fitur utama

- **Bubble Detector (inferensi nyata, on-device)** — model
  `assets/models/bd.onnx` (YOLO end-to-end NMS-free, 2 kelas: 0 = text_bubble,
  1 = text_free; kelas 0 yang dipakai, input 640x640, output (1,300,6) =
  x1,y1,x2,y2,score,cls) dieksekusi langsung di perangkat memakai **ONNX
  Runtime Mobile** (`com.microsoft.onnxruntime:onnxruntime-android`). Decode
  Kotlin: letterbox → threshold skor → box dalam koordinat kanvas (mask=null,
  tanpa NMS karena head sudah one-to-one). Kompatibel mundur dengan varian
  detect klasik (1,6,8400) dan seg legacy (37ch + protos).
  Untuk kanvas jangkung, gambar dipotong jadi tile persegi ber-overlap 30%
  dengan NMS global untuk menghapus duplikat di sambungan tile. Bila session
  ONNX gagal dimuat, deteksi mengembalikan error yang terlihat (tanpa fallback
  heuristik).
- **Pencarian font & style** — kolom cari di panel Teks (tab Tulis dan Style)
  serta di dialog Style Rules. Pencocokan tidak peduli huruf besar/kecil,
  spasi, atau strip, jadi "patrickhand" menemukan "PatrickHand-Regular".
- **Eyedropper tahan-geser** — tahan jari untuk membuka loupe pembesaran,
  geser untuk mengoreksi warna, lepas untuk memakai. HUD menampilkan HEX, RGB,
  nama warna, dan koordinat piksel; geser ke strip atas untuk batal, dan
  banner hasil pipet punya tombol **Salin** HEX.
- **Mask Bentuk Teks** — Otsu biner sesungguhnya per region teks + deteksi
  polaritas otomatis (teks gelap / teks terang di latar gelap) + dilatasi 1px
  agar anti-alias tepi stroke ikut tertutup + pembersihan noise komponen
  kecil. Bila `cornerPoints` ML Kit tersedia, hasil di-clip ke polygon teks
  miring.
- **Performa kanvas 720x16000 (~11,5MP)**:
  - Inpainting bertarget: hanya bounding-box komponen mask yang di-inpaint
    (bukan grid tile penuh) → 10-50x lebih sedikit piksel disentuh.
  - Snapshot undo kanvas raksasa disimpan sebagai PNG terkompresi (~1-4MB),
    bukan bitmap mentah 46MB → history lebih panjang tanpa OOM.
  - Native inpainting memakai `distMap` float + weight 1/d² tanpa `sqrt`.
- Tile cache malas, viewport culling, blit inkremental.
- **Import 720x16000 heap-aware (adaptasi VasiliasTyper `FileManager` +
  `BitmapSafety` dengan modifikasi Groox)**: budget piksel = min(32MP absolut,
  budget heap `maxMemory/12` → 8–32MP) sehingga 720x16000 lolos `sample=1`
  di HP normal (≥256MB heap) tapi otomatis `sample=2` di HP low-end tanpa OOM.
  Bila `sample>4`, decode via `BitmapRegionDecoder` tile 1024px yang dijahit
  + downscale per-tile (lebih tajam daripada `inSampleSize` besar), lalu
  second-stage halving bila masih di atas budget. EXIF tetap dikoreksi
  setelah stitch; buka ulang project via `decodeFileHeapAware`.

## Cara import gambar besar (720x16000)

1. Galeri → Import Picture → pilih JPG/PNG/WebP/BMP (mis. strip 720x16000).
2. Decode memakai budget heap-aware: tidak ada batas sisi terpanjang, hanya
   budget piksel + sisi absolut 16384. 720x16000 = 11,5MP → full-res di HP
   normal; HP RAM kecil otomatis turun setengah (tetap tajam via jahit tile).
3. Kanvas dibuat = ukuran asli (`fitImportDimensions` heap-aware); tidak ada
   resize paksa. Kelebihan di-crop tengah, kekurangan transparan.
4. Bila import gagal/OOM: otomatis fallback `sample=8` (bukan crash).

## Cara pakai brush di 720x16000

1. Zoom-in 100–200% ke area kerja: render hanya jendela terlihat
   (`drawVisibleBitmap` + `BrushHugeGuide.visibleRect`), bukan 11,5MP penuh.
2. Untuk lineart pakai Dip Pen/G Pen/Ink/Blend/Eraser (jenis ala ibisPaint + Blend smudge); hindari sapuan panjang Airbrush/
   Watercolor/Blur radius besar (BlurMaskFilter dimatikan otomatis di huge,
   blur live dibatasi 140k px, steps di-cap 32).
3. Sapuan cepat tidak patah: interpolasi luar max 24 titik + inner 32 steps,
   stabilizer lokal (tanpa recompose storm), clip ke dirty-rect segmen.
4. Hapus Objek (Content-Aware Fill tanpa model tanpa opsi): PatchMatch NNF menyalin tekstur sekitar + SeamlessBlender (koreksi offset Laplace/SOR) menyambung gradasi mulus tanpa mengaburkan. Heal MiGAN (model on-device Picsart MI-GAN MIT, di-bundle saat build CI; guard halusinasi + tempel khusus-lubang). AI Inpaint text-only (Agnes AI `agnes-image-2.5-flash` via jaringan, tanpa dependency baru — tempel API key sekali di pengaturan brush AI, tersimpan di perangkat): sapu untuk kumpulkan
   mask → commit crop dirty + antrean conflate bila sibuk.
   Inpaint Seleksi memakai pyramid push-pull pada crop + fallback Telea.
5. Tips anti-lag: 1 drawing layer (fast-path blit ~50px vs render 46MB),
   kecilkan size <32px bila patah, hindari teks/blend menumpuk di area sapuan.

## Konversi model (detect)

Model aktif bubble detector berasal dari aplikasi **KZKT**
(`kouzen-neo/kzkt`), dibundel di dalam APK rilis sebagai
`assets/models/kzkt.dat` (deflate + XOR satu byte `0x5A`). Repo KZKT hanya
berisi dokumentasi, jadi model diambil dari APK rilis terbaru
(**v2.3.6**, CRC32 `060587A7`, 103.627.861 byte, sha256
`c646d17e…3983`).

Skrip `scripts/fetch-kzkt-model.py` membaca ZIP central directory lewat HTTP
Range (tidak mengunduh 125 MB penuh, offset tidak di-hardcode), memverifikasi
CRC/ukuran/SHA-256, lalu menulis ONNX float32. CI mengonversinya ke fp16/int8
statis dan menguji kontrak sesi ONNX + IoU decoder sebelum dipakai:

```bash
python3 scripts/fetch-kzkt-model.py --tag v2.3.6 --out /tmp/bd_f32.onnx
pip install onnx onnxruntime onnxconverter-common
python3 scripts/make-bubble-model.py fp16 /tmp/bd_f32.onnx /tmp/bd_fp16.onnx
python3 scripts/verify-bubble-model.py /tmp/bd_fp16.onnx
# hasil akhir: app/src/main/assets/models/bd.onnx (<50MB, tidak di-commit)
```

Model disimpan di cache GitHub Actions berdasarkan tag + SHA-256, jadi build
berikutnya tidak mengunduh ulang. Catatan lisensi: repo KZKT tidak
mencantumkan lisensi dan model hanya tersedia di dalam APK; pastikan Anda
punya izin sebelum mendistribusikan ulang APK yang memuat model ini.

## Build

```
./gradlew assembleDebug
```

CI: GitHub Actions (`.github/workflows/android.yml`) mem-build APK debug +
release dan mengunggah artefak `app-debug` / `app-release`. Jalankan lewat
tombol **Run workflow** (`workflow_dispatch`) atau push ke `main` / PR.
Versi saat ini: **1.1.0** (versionCode 2).

## Font komik bawaan

`app/src/main/assets/fonts/` berisi 18 font gaya komik berlisensi SIL Open Font
License 1.1 (lihat `OFL.txt`), cocok untuk dialog/SFX webtoon EN/ID:
Bangers, Bungee, Caveat Brush, Comic Neue (Regular/Bold), Gochi Hand,
Neucha, Patrick Hand, Patrick Hand SC, Kalam (Regular/Bold),
Architects Daughter, Indie Flower, Shadows Into Light, Titan One,
RocknRoll One (mendukung kana/kanji), Short Stack, Sniglet.
Sumber: repo `google/fonts` (masing-masing `ofl/<nama>/`). Font custom
`.ttf`/`.otf` tetap bisa diimpor manual dan muncul setelah font bawaan.

## Mode Script: Style Rules + Auto Typesetting (gabungan)

Panel **Script → Bubble** memakai satu mode gabungan (tanpa pilihan ganda):

- **Style Rules**: prefix baris script (mis. `[SFX]`, `() : `) memilih
  preset style dari Style Manager (font, warna, efek); awalan dihapus
  bila `stripPrefix` true.
- **Auto Typesetting**: ukuran font, line break (wrap paragraph), alignment
  (teks panjang rata kiri, pendek center), padding 12%, dan posisi tengah
  dihitung otomatis agar teks pas di bubble. Engine membandingkan dua
  kandidat wrap (penuh vs 0.8x lebar bubble) dengan skor keterbacaan
  (font terbesar, penalti baris terakhir terlalu pendek). Bila rule cocok
  (`keepStyle = true`), alignment milik Style Rules dipertahankan.
  Implementasi: `app/src/main/java/com/grooxtyper/app/model/AutoTypesetter.kt`.

## Mode Script-Teks: susun naskah di atas teks terdeteksi

Tombol **Teks** di panel Script membuka mode kedua: **Deteksi Teks**
(ML Kit) menggabung teks berdekatan jadi satu bubble, lalu setiap bubble
dipaparkan sebagai kolom per baris (terdeteksi | naskah, bersandingan).
Kolom bisa diedit teksnya, dicoret (checkbox), atau dihapus. Isi naskah
manual atau via **Pasangkan** (dari antrean berdasar urutan); pil
`Sesuai ✓` menyala bila tiap kolom terpilih sudah bernaskah. **Jalankan**
menghapus (inpaint) teks terdeteksi terpilih lalu me-render naskah 1-ke-1
di kolom yang sama dengan ukuran font mengikuti teks aslinya.
