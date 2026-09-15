# GrooxTyper

Editor terjemahan manga on-device (Android): deteksi teks ML Kit, deteksi balon
teks via **ONNX Runtime** (koharu-yolo26s-seg), inpainting Telea native C++,
dan kanvas jangkung hingga 720x16000.

## Fitur utama

- **Bubble Detector ONNX Runtime** — model `assets/models/koharu/koharu-yolo26s-seg.onnx`
  (Ultralytics YOLO26s-seg, end2end) dieksekusi nyata di perangkat: letterbox
  1024 → forward → filter kelas `balloon` → NMS → bbox + **mask segmentasi**
  per bubble. Opsi `best1.pt` tetap tersedia sebagai fallback heuristik.
- **Mask Bentuk Teks (ditulis ulang)** — Otsu biner sesungguhnya per region
  teks + deteksi polaritas otomatis (teks gelap / teks terang di latar gelap)
  + dilatasi 1px agar anti-alias tepi stroke ikut tertutup + pembersihan
  noise komponen kecil. Bila `cornerPoints` ML Kit tersedia, hasil di-clip ke
  polygon teks miring.
- **Performa kanvas 720x16000 (~11,5MP)**:
  - Inpainting bertarget: hanya bounding-box komponen mask yang di-inpaint
    (bukan grid tile penuh) → 10-50x lebih sedikit piksel disentuh.
  - Snapshot undo kanvas raksasa disimpan sebagai PNG terkompresi (~1-4MB),
    bukan bitmap mentah 46MB → history lebih panjang tanpa OOM.
  - Native inpainting memakai `distMap` float + weight 1/d² tanpa `sqrt`.
  - Deteksi bubble ONNX per tile persegi 720px ber-overlap 15% dengan NMS
    global untuk menghapus duplikat di sambungan tile.
- Tile cache malas, viewport culling, blit inkremental (dari commit sebelumnya).

## Build

```
./gradlew assembleDebug
```

CI: GitHub Actions (`.github/workflows/android.yml`) mem-build APK debug +
release dan mengunggah artefak.
