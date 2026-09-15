# Folder Model — Bubble Detector

Dua file bobot hasil training deteksi balon teks manga:

| File asli | Nama di APK (`assets/models/`) | Isi |
|---|---|---|
| `best.pt` (5,3 MB) | `yolo12n_balloon.pt` | Ultralytics **YOLOv12n detection**, 1 kelas (`balloon`) |
| `best (1).pt` (11,5 MB) | `yolo11n_seg_balloon.pt` | Ultralytics **YOLOv11n-seg segmentation**, 1 kelas (`balloon`) |

Keduanya ikut ter-bundle ke dalam APK saat build GitHub (folder `assets/`).

## Status integrasi

**Detektor yang aktif saat ini adalah detektor klasik (tanpa AI)** dengan dua
profil yang bisa dipilih user di dialog Bubble Detector:

- **Cepat** — sekali jalan (skala 768), cocok untuk halaman bersih.
- **Teliti** — multi-skala (768 + 1152) + merge, cocok untuk halaman kompleks.

Alasan: kedua file `.pt` di atas adalah **checkpoint Python Ultralytics**
(pickle `nn.Module`), bukan format mobile — tidak bisa dieksekusi langsung
di Android (butuh runtime Python + pustaka ultralytics).

## Cara mengaktifkan YOLO asli nanti

Export kedua model di PC yang ada Python + torch + ultralytics:

```bash
pip install ultralytics torch torchvision --index-url https://download.pytorch.org/whl/cpu
yolo export model=Model/best.pt format=torchscript        # → best.torchscript
yolo export "Model/best (1).pt" format=torchscript        # → best (1).torchscript
# atau format=onnx bila memakai ONNX Runtime
```

Lalu:

1. Taruh hasil export di `app/src/main/assets/models/`.
2. Tambahkan dependency runtime (`org.pytorch:pytorch_android` atau ONNX Runtime).
3. Implementasikan `YoloBubbleModel` (stub sudah disiapkan di
   `app/src/main/java/com/grooxtyper/app/ml/BubbleDetector.kt`):
   load model → preprocess letterbox → forward → NMS (+ decode DFL untuk
   v12 dan perakitan mask untuk v11-seg) → `DetectedBubble`.
