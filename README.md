# GrooxTyper

Editor terjemahan manga on-device (Android): deteksi teks ML Kit, deteksi balon
teks on-device via model YOLO detect (bubble, 2 kelas),
inpainting Telea native C++, dan kanvas jangkung hingga 720x16000.

## Fitur utama

- **Bubble Detector (inferensi nyata, on-device)** — model
  `assets/models/bd.onnx` (YOLO detect 2 kelas,
  input 640x640, single output (1,6,8400)) dieksekusi langsung di perangkat
  memakai **ONNX Runtime Mobile**
  (`com.microsoft.onnxruntime:onnxruntime-android`). Decode Kotlin:
  letterbox → argmax kelas → NMS global → box dalam koordinat kanvas
  (mask=null untuk varian detect). Kompatibel mundur dengan checkpoint seg
  legacy (37ch + protos) bila model lama dipakai. Untuk kanvas jangkung,
  gambar dipotong jadi tile persegi ber-overlap 15% dengan NMS global untuk
  menghapus duplikat di sambungan tile. Bila session ONNX gagal dimuat,
  otomatis fallback ke heuristik putih tersaturasi-rendah ber-outline gelap.
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
2. Untuk lineart pakai Pen Hard/Ink/Eraser; hindari sapuan panjang Airbrush/
   Watercolor/Blur radius besar (BlurMaskFilter dimatikan otomatis di huge,
   blur live dibatasi 140k px, steps di-cap 32).
3. Sapuan cepat tidak patah: interpolasi luar max 24 titik + inner 32 steps,
   stabilizer lokal (tanpa recompose storm), clip ke dirty-rect segmen.
4. Heal Patch: sapu untuk kumpulkan mask → commit `inpaintHealDirty` hanya
   crop dirty → mask 46MB di-recycle (`recycleInpaintMask`).
5. Tips anti-lag: 1 drawing layer (fast-path blit ~50px vs render 46MB),
   kecilkan size <32px bila patah, hindari teks/blend menumpuk di area sapuan.

## Konversi model (detect)

Model aktif bubble detector adalah YOLO detect 2-class, imgsz 640,
task detect. Contoh export bila melatih ulang via Ultralytics:

```bash
pip install ultralytics onnx onnxruntime
python -c "from ultralytics import YOLO; YOLO('best.pt').export(format='onnx', imgsz=640, opset=17, simplify=True)"
# hasil: app/src/main/assets/models/bd.onnx (nama disamarkan)
```

## Build

```
./gradlew assembleDebug
```

CI: GitHub Actions (`.github/workflows/android.yml`) mem-build APK debug +
release dan mengunggah artefak.

## Font komik bawaan

`app/src/main/assets/fonts/` berisi 18 font gaya komik berlisensi SIL Open Font
License 1.1 (lihat `OFL.txt`), cocok untuk dialog/SFX webtoon EN/ID:
Bangers, Bungee, Caveat Brush, Comic Neue (Regular/Bold), Gochi Hand,
Neucha, Patrick Hand, Patrick Hand SC, Kalam (Regular/Bold),
Architects Daughter, Indie Flower, Shadows Into Light, Titan One,
RocknRoll One (mendukung kana/kanji), Short Stack, Sniglet.
Sumber: repo `google/fonts` (masing-masing `ofl/<nama>/`). Font custom
`.ttf`/`.otf` tetap bisa diimpor manual dan muncul setelah font bawaan.
