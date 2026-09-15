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
