# GrooxTyper

Editor terjemahan manga on-device (Android): deteksi teks ML Kit, deteksi balon
teks on-device via model YOLOv11n-seg (`best1.onnx`, dikonversi dari
`best1.pt`), inpainting Telea native C++, dan kanvas jangkung hingga 720x16000.

## Fitur utama

- **Bubble Detector (inferensi nyata, on-device)** — model
  `assets/models/best1.onnx` (YOLOv11n-seg, 1 kelas `balloon`, input 640x640)
  dieksekusi langsung di perangkat memakai **ONNX Runtime Mobile**
  (`com.microsoft.onnxruntime:onnxruntime-android`). Output deteksi
  (1,37,8400) + prototipe mask (1,32,160,160) didecode penuh di Kotlin:
  letterbox → NMS → sigmoid(protos·koefisien) → mask segmentasi ALPHA_8 per
  bubble dalam koordinat kanvas. Untuk kanvas jangkung, gambar dipotong jadi
  tile persegi ber-overlap 15% dengan NMS global untuk menghapus duplikat di
  sambungan tile. Checkpoint latih `best1.pt` tetap disertakan sebagai
  referensi/arsip; bila session ONNX gagal dimuat, otomatis fallback ke
  heuristik putih tersaturasi-rendah ber-outline gelap.
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

## Konversi model (best1.pt → best1.onnx)

```bash
pip install ultralytics onnx onnxruntime
python -c "from ultralytics import YOLO; YOLO('app/src/main/assets/models/best1.pt').export(format='onnx', imgsz=640, opset=17, simplify=True)"
# hasil: app/src/main/assets/models/best1.onnx
```

## Build

```
./gradlew assembleDebug
```

CI: GitHub Actions (`.github/workflows/android.yml`) mem-build APK debug +
release dan mengunggah artefak.
