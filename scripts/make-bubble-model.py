#!/usr/bin/env python3
"""
Buat varian model bubble yang bisa dijalankan onnxruntime Android.

Dua varian, urut dari yang paling ideal:

1. `fp16` - bobot diubah ke float16 (setengah ukuran float32, ~49.5MB, masih
   di bawah batas 50MB) dengan SEMUA op tetap Float/Conv/Reshape. Akurasinya
   nyaris identik dengan float32.
2. `int8` - kuantisasi statis format QOperator (QLinearConv, BUKAN
   ConvInteger). Format lama quantize_dynamic menghasilkan op ConvInteger yang
   tidak punya kernel di AAR onnxruntime-android, sehingga sesi gagal dibuat
   dan aplikasi menampilkan ORT_NOT_IMPLEMENTED. QLinearConv didukung penuh.

Cara pakai: make-bubble-model.py <fp16|int8> <masukan.onnx> <keluaran.onnx>
"""

import sys

import numpy as np


def sintetis(varian=0):
    """Halaman manga sintetis untuk kalibrasi (bukan untuk menilai akurasi).

    Penugasan ditulis langsung ke larik gambar: pembacaan fancy index
    (``img[yy, xx]``) mengembalikan salinan, jadi menulis ke hasil bacaan
    tidak mengubah apa pun.
    """
    rng = np.random.RandomState(1234 + varian)
    img = np.ones((640, 640, 3), dtype=np.float32)
    yy, xx = np.mgrid[0:640, 0:640]
    for i in range(3 + varian):
        cx = int(rng.randint(90, 550))
        cy = int(rng.randint(90, 550))
        rx = int(rng.randint(60, 170))
        ry = max(20, int(rx * rng.uniform(0.55, 0.95)))
        edge = int(rng.randint(4, 9))
        d = ((xx - cx) / float(rx)) ** 2 + ((yy - cy) / float(ry)) ** 2
        img[d <= 1.0] = 1.0
        inner = (1.0 - edge / float(min(rx, ry))) ** 2
        img[(d <= 1.0) & (d > inner)] = 0.05
        for line in range(int(rng.randint(2, 5))):
            ly = cy - 30 + line * 26
            half = int(rx * 0.6)
            y0, y1 = max(0, ly - 4), min(640, ly + 5)
            x0, x1 = max(0, cx - half), min(640, cx + half)
            if y1 > y0 and x1 > x0:
                img[y0:y1, x0:x1] = 0.05
    # sedikit derau seperti hasil scan
    img += rng.normal(0, 0.01, img.shape).astype(np.float32)
    return np.clip(img, 0.0, 1.0)


def ke_fp16(src, dst):
    import onnx
    from onnxconverter_common import float16

    model = onnx.load(src)
    out = float16.convert_float_to_float16(model, keep_io_types=True, disable_shape_infer=True)
    # Konverter kadang meninggalkan deklarasi value_info antar-node bertipe
    # float32 padahal node-nya sudah fp16; itu bikin onnxruntime menolak model
    # ("Type Error: ... does not match expected type"). Semua tipe antar-node
    # dihitung ulang sendiri oleh runtime, jadi aman dihapus.
    del out.graph.value_info[:]
    onnx.checker.check_model(out)
    onnx.save(out, dst)


def ke_int8(src, dst):
    import onnx
    from onnxruntime.quantization import (
        CalibrationDataReader,
        QuantFormat,
        QuantType,
        quantize_static,
    )

    model = onnx.load(src)
    name = model.graph.input[0].name

    class Pembaca(CalibrationDataReader):
        def __init__(self, samples):
            self.samples = samples
            self.i = 0

        def get_next(self):
            if self.i >= len(self.samples):
                return None
            x = np.transpose(self.samples[self.i], (2, 0, 1))[None].astype(np.float32)
            self.i += 1
            return {name: x}

        def rewind(self):
            self.i = 0

    # 6 halaman variasi: jumlah dan posisi gelembung berbeda supaya rentang
    # aktivasi mencakup interior putih, garis tepi hitam, dan derau scan.
    samples = [sintetis(i) for i in range(6)]
    quantize_static(
        model,
        dst,
        Pembaca(samples),
        quant_format=QuantFormat.QOperator,
        activation_type=QuantType.QUInt8,
        weight_type=QuantType.QInt8,
        per_channel=True,
        reduce_range=False,
    )


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 1
    varian, src, dst = sys.argv[1], sys.argv[2], sys.argv[3]
    if varian == "fp16":
        ke_fp16(src, dst)
    elif varian == "int8":
        ke_int8(src, dst)
    else:
        print("varian tak dikenal: %s" % varian)
        return 1
    print("varian %s siap: %s" % (varian, dst))
    return 0


if __name__ == "__main__":
    sys.exit(main())
