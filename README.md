# GrooxTyper

Editor terjemahan manga on-device (Android): deteksi teks ML Kit, deteksi balon
teks heuristik berbasis `best1.pt`, inpainting Telea native C++, dan kanvas
jangkung hingga 720x16000.

## Fitur utama

- **Bubble Detector** — model `assets/models/best1.pt` (YOLOv11n-seg) sebagai
  referensi; deteksi di perangkat dijalankan via heuristik putih
  tersaturasi-rendah ber-outline gelap (checkpoint .pt PyTorch tidak bisa
  dieksekusi langsung di Android). Untuk kanvas jangkung, gambar dipotong
  jadi tile persegi 720px ber-overlap 15% dengan NMS global untuk menghapus
  duplikat di sambungan tile.
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

## Build

```
./gradlew assembleDebug
```

CI: GitHub Actions (`.github/workflows/android.yml`) mem-build APK debug +
release dan mengunggah artefak.
