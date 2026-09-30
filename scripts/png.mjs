// Decoder PNG minimal (RGBA/RGB 8-bit, tanpa interlace) tanpa dependensi.
// Dipakai supaya uji wand bisa memakai halaman manga sungguhan sebagai
// fixture, bukan gambar sintetis. Bukan kode produksi: hanya untuk pengujian.
import { inflateSync } from 'node:zlib';
import { readFileSync } from 'node:fs';

const SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/**
 * Baca PNG dan kembalikan { w, h, rgb } dengan rgb = Uint8Array panjang w*h*3.
 * Melempar error kalau di luar subset yang didukung, supaya kegagalan
 * fixture ketahuanInstead of diam-diam memakai data rusak.
 */
export function readPng(file) {
  const buf = readFileSync(file);
  if (!buf.subarray(0, 8).equals(SIG)) throw new Error('bukan PNG: ' + file);
  let off = 8;
  let w = 0, h = 0, depth = 0, ctype = 0, interlace = 0;
  const idat = [];
  let pal = null, trns = null;
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      w = data.readUInt32BE(0);
      h = data.readUInt32BE(4);
      depth = data[8];
      ctype = data[9];
      interlace = data[12];
    } else if (type === 'PLTE') pal = Buffer.from(data);
    else if (type === 'tRNS') trns = Buffer.from(data);
    else if (type === 'IDAT') idat.push(Buffer.from(data));
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (depth !== 8) throw new Error('kedalaman PNG harus 8 bit, dapat ' + depth);
  if (interlace !== 0) throw new Error('PNG interlaced tidak didukung');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype];
  if (!channels) throw new Error('tipe warna PNG tidak didukung: ' + ctype);
  const raw = inflateSync(Buffer.concat(idat));
  const bpp = channels;
  const stride = w * bpp;
  const out = Buffer.alloc(h * stride);
  let pos = 0;
  for (let y = 0; y < h; y++) {
    const filter = raw[pos++];
    const line = raw.subarray(pos, pos + stride);
    pos += stride;
    const cur = out.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? out.subarray((y - 1) * stride, y * stride) : null;
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0;
      const b = prev ? prev[i] : 0;
      const c = prev && i >= bpp ? prev[i - bpp] : 0;
      let v = line[i];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        v += pa <= pb && pa <= pc ? a : (pb <= pc ? b : c);
      }
      cur[i] = v & 0xff;
    }
  }
  const rgb = new Uint8Array(w * h * 3);
  for (let i = 0, n = w * h; i < n; i++) {
    let r, g, b;
    if (ctype === 2 || ctype === 6) {
      r = out[i * bpp]; g = out[i * bpp + 1]; b = out[i * bpp + 2];
    } else if (ctype === 0 || ctype === 4) {
      r = g = b = out[i * bpp];
    } else {
      const p = out[i * bpp] * 3;
      r = pal[p]; g = pal[p + 1]; b = pal[p + 2];
      if (trns && out[i * bpp] < trns.length && trns[out[i * bpp]] === 0) { r = g = b = 0; }
    }
    rgb[i * 3] = r; rgb[i * 3 + 1] = g; rgb[i * 3 + 2] = b;
  }
  return { w, h, rgb };
}
