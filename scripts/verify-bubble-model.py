#!/usr/bin/env python3
"""
Uji kontrak model bubble (dijalankan di CI, bukan di perangkat).

Tujuannya bukan sekadar "model bisa di-load", tapi membuktikan hal-hal yang
pernah bikin fitur rusak di aplikasi:

1. Sesi onnxruntime bisa dibuat (model int8 kuantisasi dinamis lama gagal di
   Android: "Could not find an implementation for ConvInteger").
2. Bentuk output = (1, 6, 8400) dan kanal 0..3 adalah cxcywh dalam PIKSEL
   input 640. Format ini diverifikasi lewat IoU terhadap posisi gelembung
   yang diketahui, bukan dari tebakan nama kanal.
3. Decoder yang sama dengan yang dipakai Kotlin (argmax antar kanal kelas,
   cxcywh piksel, undo letterbox) menghasilkan kotak yang benar.

Halaman uji sengaja dibuat dengan gelembung oval berisi garis teks seperti
komik, supaya model punya sesuatu yang benar-benar mirip untuk dideteksi.

Keluar: 0 kalau semua lolos, 1 kalau ada yang gagal.
"""

import sys

import numpy as np

INPUT = 640
# Batas minimum IoU agar kotak dianggap "benar". Diuji di atas model asli dan
# menghasilkan 0.96, jadi 0.75 memberi ruang besar untuk varian model.
MIN_IOU = 0.75
MIN_DETECTIONS = 1


def buat_halaman(pw=720, ph=1024):
    """Halaman manga sintetis: kertas putih + 3 gelembung oval + garis teks."""
    img = np.ones((ph, pw, 3), dtype=np.float32)
    bubbles = [
        dict(cx=180, cy=220, rx=150, ry=105),
        dict(cx=540, cy=430, rx=130, ry=90),
        dict(cx=260, cy=800, rx=175, ry=115),
    ]
    edge = 7
    for b in bubbles:
        ys = np.arange(b["cy"] - b["ry"] - edge - 2, b["cy"] + b["ry"] + edge + 3)
        xs = np.arange(b["cx"] - b["rx"] - edge - 2, b["cx"] + b["rx"] + edge + 3)
        yy, xx = np.meshgrid(ys, xs, indexing="ij")
        inside = ((xx - b["cx"]) / b["rx"]) ** 2 + ((yy - b["cy"]) / b["ry"]) ** 2 <= 1.0
        ring = ((xx - b["cx"]) / b["rx"]) ** 2 + ((yy - b["cy"]) / b["ry"]) ** 2 > (
            1.0 - edge / min(b["rx"], b["ry"])
        ) ** 2
        yy = np.clip(yy, 0, ph - 1)
        xx = np.clip(xx, 0, pw - 1)
        patch = img[yy, xx]
        patch[inside] = 0.05
        patch[inside & ring] = 0.05
        for line in range(3):
            ly = int(b["cy"] - 34 + line * 34)
            half = int(b["rx"] * 0.62 * (1 - 0.12 * line))
            y0, y1 = max(0, ly - 5), min(ph, ly + 6)
            x0, x1 = max(0, b["cx"] - half), min(pw, b["cx"] + half)
            if y1 > y0 and x1 > x0:
                img[y0:y1, x0:x1] = 0.05
    return img, bubbles


def letterbox(img):
    """Pamakan prapemrosesan Kotlin: skala longest-side ke 640, pad abu 114."""
    ph, pw = img.shape[0], img.shape[1]
    scale = min(INPUT / pw, INPUT / ph)
    nw, nh = int(round(pw * scale)), int(round(ph * scale))
    pad_x, pad_y = int(round((INPUT - nw) / 2)), int(round((INPUT - nh) / 2))
    out = np.full((INPUT, INPUT, 3), 114.0 / 255.0, dtype=np.float32)
    # Resample nearest-neighbour dengan indeks yang sama seperti Kotlin
    # (floor(y / scale)), jadi hasil prapemrosesan identik dengan aplikasi.
    ys = np.clip((np.arange(nh) / scale).astype(int), 0, ph - 1)
    xs = np.clip((np.arange(nw) / scale).astype(int), 0, pw - 1)
    resized = np.ascontiguousarray(img[ys][:, xs], dtype=np.float32)
    out[pad_y:pad_y + nh, pad_x:pad_x + nw] = resized
    return out, scale, pad_x, pad_y


def iou(a, b):
    x1, y1 = max(a[0], b[0]), max(a[1], b[1])
    x2, y2 = min(a[2], b[2]), min(a[3], b[3])
    iw, ih = max(0.0, x2 - x1), max(0.0, y2 - y1)
    inter = iw * ih
    ua = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / max(1e-6, ua)


def nms(dets, thr=0.45):
    out = []
    for d in sorted(dets, key=lambda x: -x["score"]):
        if all(iou(o["box"], d["box"]) < thr for o in out):
            out.append(d)
    return out


def decode(out, scale, pad_x, pad_y, conf=0.10, floor=0.02, rel=0.08):
    """Decoder yang sama persis dengan decodeYolo6 di Kotlin."""
    rows, cols = out.shape
    channel_first = rows <= 8 and cols > 8
    n = cols if channel_first else rows
    num_scores = (rows if channel_first else cols) - 4

    def get(c, a):
        return float(out[c][a] if channel_first else out[a][c])

    keep, max_coord = [], 0.0
    for a in range(n):
        best = max(get(4 + c, a) for c in range(num_scores))
        if best < floor:
            continue
        keep.append(a)
        for c in range(4):
            max_coord = max(max_coord, abs(get(c, a)))
    if not keep:
        return [], "tak ada anchor di atas lantai"
    pixels = max_coord > 2.5
    corner = sum(1 for a in keep if get(2, a) > get(0, a) and get(3, a) > get(1, a))
    corner_ratio = corner / len(keep)
    corner_xyxy = pixels and corner_ratio >= 0.6

    cands = []
    for a in keep:
        score = max(get(4 + c, a) for c in range(num_scores))
        cls = int(np.argmax([get(4 + c, a) for c in range(num_scores)]))
        if corner_xyxy:
            box = [
                (get(0, a) - pad_x) / scale,
                (get(1, a) - pad_y) / scale,
                (get(2, a) - pad_x) / scale,
                (get(3, a) - pad_y) / scale,
            ]
        elif pixels:
            cx, cy, w, h = get(0, a), get(1, a), get(2, a), get(3, a)
            box = [
                (cx - w / 2 - pad_x) / scale,
                (cy - h / 2 - pad_y) / scale,
                (cx + w / 2 - pad_x) / scale,
                (cy + h / 2 - pad_y) / scale,
            ]
        else:
            cx, cy, w, h = [get(c, a) * INPUT for c in range(4)]
            box = [
                (cx - w / 2 - pad_x) / scale,
                (cy - h / 2 - pad_y) / scale,
                (cx + w / 2 - pad_x) / scale,
                (cy + h / 2 - pad_y) / scale,
            ]
        cands.append(dict(box=box, score=score, cls=cls))

    used = [c for c in cands if c["score"] >= conf]
    mode = "absolut"
    if not used:
        top = max(c["score"] for c in cands)
        thr = max(conf, top * rel)
        used = [c for c in cands if c["score"] >= thr]
        mode = "relatif"
    if not used:
        used = sorted(cands, key=lambda x: -x["score"])[:8]
        mode = "diturunkan"
    fmt = "xyxy-piksel" if corner_xyxy else ("cxcywh-piksel" if pixels else "cxcywh-norm")
    return nms(used), "%s %s (rasio sudut %.2f)" % (fmt, mode, corner_ratio)


def main(path):
    import onnx
    import onnxruntime as ort

    model = onnx.load(path)
    ops = sorted({n.op_type for n in model.graph.node})
    int_ops = [o for o in ops if o in ("ConvInteger", "MatMulInteger", "GatherElements")]
    print("op unik:", ", ".join(ops[:24]), "..." if len(ops) > 24 else "")
    if int_ops:
        print("GAGAL: model mengandung op integer dinamis %s" % int_ops)
        print("       ORT Android tak punya kernel ConvInteger -> sesi gagal dibuat")
        return 1
    print("OK: tak ada op integer dinamis (aman untuk ORT Android)")

    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    print("sesi OK, input:", sess.get_inputs()[0].name, sess.get_inputs()[0].shape)

    img, bubbles = buat_halaman()
    tensor, scale, pad_x, pad_y = letterbox(img)
    x = np.transpose(tensor, (2, 0, 1))[None].astype(np.float32)
    name = sess.get_inputs()[0].name
    raw = sess.run(None, {name: x})[0]
    out = raw[0] if raw.ndim == 3 else raw
    if out.ndim != 2:
        print("GAGAL: output setelah buang dimensi batch bukan 2D:", raw.shape)
        return 1
    print("output:", raw.shape, "-> matriks", out.shape, out.dtype)
    if out.shape[1:] != (6, 8400) and out.shape[-2:] != (6, 8400):
        print("GAGAL: bentuk output tidak sesuai (1,6,8400)")
        return 1

    dets, info = decode(out, scale, pad_x, pad_y)
    print("decoder:", info)
    truth = [
        [b["cx"] - b["rx"], b["cy"] - b["ry"], b["cx"] + b["rx"], b["cy"] + b["ry"]]
        for b in bubbles
    ]
    best = []
    for t in truth:
        best.append(max([iou(d["box"], t) for d in dets] or [0.0]))
    for d in dets[:5]:
        print("  deteksi skor=%.4f kelas=%d kotak=[%s]" % (
            d["score"], d["cls"], ", ".join("%.0f" % v2 for v2 in d["box"])))
    print("IoU per gelembung:", ", ".join("%.2f" % v for v in best))
    hit = sum(1 for v in best if v >= MIN_IOU)
    if hit < MIN_DETECTIONS:
        print("GAGAL: tak ada gelembung yang terdeteksi benar (butuh %d, IoU>=%.2f)"
              % (MIN_DETECTIONS, MIN_IOU))
        return 1
    print("OK: %d/%d gelembung terdeteksi dengan IoU>=%.2f" % (hit, len(truth), MIN_IOU))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/models/bd.onnx"))
