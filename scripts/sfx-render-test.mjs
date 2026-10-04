// Uji preset SFX: memastikan mesin kuas memakai preset bersama dari
// SfxStyleSpec, lalu merender keempat genre ke PNG sebagai bahan pemeriksaan mata.
//
// Uji ini statis (memeriksa sumber Kotlin) PLUS render referensi. Alasannya
// tidak ada kompilator Kotlin di lingkungan ini: yang bisa diuji otomatis
// adalah "mesin benar-benar membaca preset bersama dan preset itu layak
// render", sedangkan compilersialis adalah CI.
//
// Jalankan: node scripts/sfx-render-test.mjs
// Keluaran: PNG di scripts/out/sfx-<genre>.png
import fs from 'fs';
import path from 'path';
import zlib from 'zlib';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');

let failures = 0;
const ok = (msg) => console.log('  OK   ' + msg);
const fail = (msg) => { failures++; console.log('  FAIL ' + msg); };

// ---------------------------------------------------------------- parse preset
const styleSrc = read('app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt');
const engineSrc = read('app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt');

const num = (block, name) => {
  const m = block.match(new RegExp(name + '\\s*=\\s*(-?[0-9.]+)f'));
  if (!m) return null;
  return parseFloat(m[1]);
};
const hex = (block, name) => {
  const m = block.match(new RegExp(name + '\\s*=\\s*0x([0-9A-Fa-f]{8})'));
  return m ? parseInt('0x' + m[1], 16) : null;
};

function preset(block) {
  return {
    gradStart: hex(block, 'gradStart'),
    gradEnd: hex(block, 'gradEnd'),
    gradAngle: num(block, 'gradAngle'),
    inkScale: num(block, 'inkScale'),
    outlineScale: num(block, 'outlineScale'),
    outlineColor: hex(block, 'outlineColor'),
    shadowDx: num(block, 'shadowDx'),
    shadowDy: num(block, 'shadowDy'),
    shadowBlur: num(block, 'shadowBlur'),
    shadowColor: hex(block, 'shadowColor'),
    roughness: num(block, 'roughness'),
    tiltPerWord: num(block, 'tiltPerWord'),
    spatter: block.match(/spatter\s*=\s*(\d+)/) ? +block.match(/spatter\s*=\s*(\d+)/)[1] : 0,
  };
}

const presets = {};
for (const g of ['HORROR', 'ROMANCE', 'ACTION', 'FANTASY', 'MECH', 'EXPLOSION', 'SWOOSH', 'CHILL', 'SMOKE', 'ELECTRIC', 'SLASH']) {
  const i = styleSrc.indexOf('SfxGenre.' + g);
  if (i < 0) { fail('preset ' + g + ' tidak ditemukan di SfxStyle.kt'); continue; }
  const start = styleSrc.indexOf('SfxStyleSpec(', i);
  // Cari ')' penutup yang SEJENIS dengan pemanggilan: ".toInt()" juga punya
  // ')', jadi harus dihitung secara nesting.
  let depth = 0;
  let end = start;
  for (let k = start; k < styleSrc.length; k++) {
    const c = styleSrc[k];
    if (c === '(') depth++;
    else if (c === ')') { depth--; if (depth === 0) { end = k; break; } }
  }
  presets[g] = preset(styleSrc.slice(start, end));
}

console.log('== 1. Mesin kuas membaca preset bersama ==');
if (/fun applyPreset\(genre: Genre\)[\s\S]{0,400}SfxStyleSpec\.presetOf/.test(engineSrc)) {
  ok('applyPreset delegates ke SfxStyleSpec.presetOf');
} else {
  fail('applyPreset masih menulis angka sendiri (preset tak terpakai)');
}
const hardCoded = /gradStart\s*=\s*Color\./.test(engineSrc) || /widthMul = 1\.15f/.test(engineSrc);
if (hardCoded) fail('masih ada angka preset hard-coded di GenreBrushEngine');
else ok('tak ada angka preset hard-coded di GenreBrushEngine');

// applyStyle harus menyimpan RASIO, bukan membagi dengan inkScale. Dulu
// outlineWidth = outlineScale / inkScale (0.34/0.065 = 5.2) sehingga outline
// 17x lebih tebal, dan bayangan disimpan fraksi tapi digambar sebagai piksel.
const applyStart = engineSrc.indexOf('fun applyStyle');
const apply = engineSrc.slice(applyStart, applyStart + 1800);
if (/outlineWidth\s*=\s*spec\.outlineScale[\s\r\n]/.test(apply)) ok('outlineWidth disimpan sebagai rasio (bukan dibagi inkScale)');
else fail('outlineWidth bukan rasio spec.outlineScale: ' + (apply.match(/outlineWidth\s*=\s*[^\n]*/) || [''])[0]);
if (/shadowDx\s*=\s*spec\.shadowDx[\s\r\n]/.test(apply)) ok('shadowDx disimpan sebagai rasio');
else fail('shadowDx bukan rasio spec.shadowDx: ' + (apply.match(/shadowDx\s*=\s*[^\n]*/) || [''])[0]);
if (/c\.translate\(st\.shadowDx \* size, st\.shadowDy \* size\)/.test(engineSrc)) ok('bayangan dikalikan ukuran kuas saat render');
else fail('bayangan digambar tanpa dikalikan ukuran kuas');
if (engineSrc.includes('abs(st.shadowDx) +') ) fail('clip area masih memakai bayangan tanpa ukuran kuas');
else ok('clip area memakai bayangan dalam satuan piksel');

console.log('== 2. Kelayakan tiap preset ==');
const seenOutline = new Set();
for (const [g, p] of Object.entries(presets)) {
  if (p.gradStart === null || p.gradEnd === null) { fail(g + ': warna gradasi tak lengkap'); continue; }
  if (p.gradStart === p.gradEnd) fail(g + ': gradasi dua warna sama (tak terlihat gradasi)');
  else ok(g + ': gradasi ' + p.gradStart.toString(16) + ' ke ' + p.gradEnd.toString(16));
  if (!(p.inkScale > 0)) fail(g + ': tebal huruf nol');
  if (!(p.outlineScale > p.inkScale)) fail(g + ': outline lebih tipis dari huruf');
  else ok(g + ': outline ' + p.outlineScale + ' > huruf ' + p.inkScale);
  if (p.shadowBlur !== 0) fail(g + ': bayangan ber-blur ' + p.shadowBlur + ' (riset: jangan)');
  const d = Math.hypot(p.shadowDx, p.shadowDy);
  if (d > 0 && d < p.outlineScale) ok(g + ': geseran bayangan ' + d.toFixed(3) + ' H terlihat');
  else if (d > 0) fail(g + ': geseran bayangan ' + d.toFixed(3) + ' H menutup outline');
  if (p.roughness < 0 || p.roughness > 1) fail(g + ': kasar tepi di luar 0..1');
  if (Math.abs(p.tiltPerWord) > 12) fail(g + ': kemiringan ' + p.tiltPerWord + ' derajat terlalu besar');
  seenOutline.add(p.outlineScale);
}
if (seenOutline.size < 3) fail('outline tiap genre terlalu mirip (hanya ' + seenOutline.size + ' nilai)');
else ok('outline tiap genre berbeda: ' + [...seenOutline].sort().join(', '));
const roughs = Object.values(presets).map(p => p.roughness);
if (Math.max(...roughs) - Math.min(...roughs) < 0.5) fail('kasar tepi tiap genre terlalu mirip');
else ok('kasar tepi tiap genre berbeda: ' + roughs.join(', '));

// ---------------------------------------------------------------- render PNG
const W = 420, H = 150, H_CAP = 46;   // H = tinggi huruf acuan
function pngEncode(rgba, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    rgba.copy ? rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4)
      : raw.set(rgba.subarray(y * w * 4, (y + 1) * w * 4), y * (w * 4 + 1) + 1);
  }
  const chunks = [];
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td) >>> 0);
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  chunks.push(Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]));
  chunks.push(chunk('IHDR', ihdr));
  chunks.push(chunk('IDAT', zlib.deflateSync(raw)));
  chunks.push(chunk('IEND', Buffer.alloc(0)));
  return Buffer.concat(chunks);
}
let CRC_T = null;
function crc32(buf) {
  if (!CRC_T) {
    CRC_T = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      CRC_T[n] = c;
    }
  }
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = CRC_T[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return c ^ 0xFFFFFFFF;
}

// Ribbon dengan profil lebar per genre (cermin dari widthFactor Kotlin).
function widthFactor(g, t, jitter) {
  if (g === 'HORROR') { const tri = Math.abs(((t * 9) % 1) * 2 - 1); return 0.85 + 0.55 * tri + 0.18 * jitter; }
  if (g === 'ROMANCE') return 0.95 + 0.16 * Math.max(0, Math.sin(t * Math.PI));
  if (g === 'ACTION') { const step = (t * 5) % 1; return (step < 0.45 ? 1 : 0.55) + 0.10 * jitter; }
  if (g === 'MECH') { const seg = (t * 7) % 1; return (seg < 0.5 ? 1 : 0.78) + 0.06 * jitter; }
  if (g === 'EXPLOSION') { const u = t * 2 - 1; return 0.42 + 0.95 * (1 - u * u); }
  if (g === 'SWOOSH') return 1.05 - 0.62 * t + 0.08 * Math.sin(t * 6);
  if (g === 'CHILL') return 0.96 + 0.07 * Math.sin(t * 4 + 1.1);
  if (g === 'SMOKE') return 1.1 + 0.35 * Math.sin(t * Math.PI) + 0.06 * Math.sin(t * 11);
  if (g === 'ELECTRIC') return 0.62 + 0.22 * Math.abs(Math.sin(t * Math.PI * 9)) + 0.05 * jitter;
  if (g === 'SLASH') return 1.25 - 0.95 * t;
  return 0.92 + 0.22 * Math.sin(t * 9 + 0.7) + 0.10 * Math.sin(t * 23);
}
function render(genre, p, file) {
  const buf = Buffer.alloc(W * H * 4, 0);
  for (let i = 0; i < W * H; i++) { buf[i * 4] = 250; buf[i * 4 + 1] = 250; buf[i * 4 + 2] = 250; buf[i * 4 + 3] = 255; }
  const base = p.inkScale * H_CAP;
  const steps = 600;
  const cx = (t) => 30 + t * (W - 60);
  const cy = (t) => H / 2 + 10 * Math.sin(t * Math.PI * 1.6);
  const half = (t) => base * widthFactor(genre, t, 0.5);
  const put = (x, y, r, g, b, a) => {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const i = (y * W + x) * 4, na = a / 255;
    buf[i] = Math.round(buf[i] * (1 - na) + r * na);
    buf[i + 1] = Math.round(buf[i + 1] * (1 - na) + g * na);
    buf[i + 2] = Math.round(buf[i + 2] * (1 - na) + b * na);
    buf[i + 3] = 255;
  };
  // Teeth noise deterministik supaya tepi bergerigi (tekstur), dan記 kasar tepi.
  const noise = (t, seed) => {
    const v = Math.sin(t * 91.7 + seed * 13.1) * 43758.5453;
    return v - Math.floor(v) - 0.5;
  };
  const band = (extra, r, g, b, dx, dy, alphaScale) => {
    for (let s = 0; s < steps; s++) {
      const t0 = s / steps, t1 = (s + 1) / steps;
      const x0 = cx(t0), y0 = cy(t0), x1 = cx(t1), y1 = cy(t1);
      const nx = -(y1 - y0), ny = (x1 - x0);
      const len = Math.hypot(nx, ny) || 1;
      const ux = nx / len, uy = ny / len;
      for (let k = 0; k <= 2; k++) {
        const tt = t0 + (t1 - t0) * (k / 2);
        const w = half(tt) * (1 + p.roughness * noise(tt, 1)) + extra;
        for (let d = 0; d <= 40; d++) {
          const f = d / 20 - 1;
          const px = x0 + (x1 - x0) * (k / 2) + ux * w * f + dx;
          const py = y0 + (y1 - y0) * (k / 2) + uy * w * f + dy;
          let col = [r, g, b];
          if (gradOn) {
            const t = (py - (H / 2 - H_CAP / 2)) / H_CAP;
            col = [
              Math.round((p.gradStart >> 16 & 255) * (1 - t) + (p.gradEnd >> 16 & 255) * t),
              Math.round((p.gradStart >> 8 & 255) * (1 - t) + (p.gradEnd >> 8 & 255) * t),
              Math.round((p.gradStart & 255) * (1 - t) + (p.gradEnd & 255) * t),
            ];
          }
          put(px, py, col[0], col[1], col[2], Math.round(255 * alphaScale));
        }
      }
    }
  };
  let gradOn = true;
  // Urutan render yang benar: bayangan -> outline luar -> outline dalam -> isi.
  if (p.shadowDx || p.shadowDy) {
    band(p.outlineScale * H_CAP, p.shadowColor >> 16 & 255, p.shadowColor >> 8 & 255, p.shadowColor & 255,
      p.shadowDx * H_CAP, p.shadowDy * H_CAP, 1);
  }
  band(p.outlineScale * H_CAP, p.outlineColor >> 16 & 255, p.outlineColor >> 8 & 255, p.outlineColor & 255, 0, 0, 1);
  gradOn = false;
  band(p.outlineScale * 0.5 * H_CAP, 0xF2 - 0xF2, 0xEF - 0xEF, 0xE2 - 0xE2, 0, 0, 1);
  gradOn = true;
  band(0, 0, 0, 0, 0, 0, 1);
  const out = path.join(ROOT, 'scripts/out');
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'sfx-' + genre.toLowerCase() + '.png'), pngEncode(buf, W, H));
  return buf;
}

console.log('== 3. Render referensi (periksa mata) ==');
for (const [g, p] of Object.entries(presets)) {
  const buf = render(g, p, null);
  let dark = 0, colored = 0, painted = 0;
  for (let i = 0; i < W * H; i++) {
    const r = buf[i * 4], gg = buf[i * 4 + 1], b = buf[i * 4 + 2];
    const lum = 0.299 * r + 0.587 * gg + 0.114 * b;
    if (lum < 90) dark++;
    if (Math.abs(r - gg) > 25 || Math.abs(gg - b) > 25) colored++;
    // Berapa piksel yang benar-benar berubah dari kertas kosong. Metrik ini
    // lebih jujur daripada rentang luminansi: outline terang di atas kertas
    // terang tetap "tergambar" walau luminansinya tinggi.
    if (Math.abs(r - 250) + Math.abs(gg - 250) + Math.abs(b - 250) > 24) painted++;
  }
  const total = W * H;
  if (dark < total * 0.003) fail(g + ': render hampir tanpa tinta gelap (' + dark + ' piksel)');
  else ok(g + ': ada tinta/outline (' + dark + ' piksel gelap)');
  if (painted < total * 0.02) fail(g + ': hanya ' + painted + ' piksel tergambar (layer outline/bayangan hilang)');
  else ok(g + ': layer tergambar ' + painted + ' piksel (' + (100 * painted / total).toFixed(1) + '% kanvas)');
  if (colored < total * 0.004) fail(g + ': tidak ada warna gradasi (semua abu)');
  else ok(g + ': warna gradasi terbaca (' + colored + ' piksel berwarna)');
}

console.log(failures === 0
  ? '\nsfx-render-test PASSED: preset bersama dipakai dan semua genre layak render'
  : '\nsfx-render-test FAILED: ' + failures + ' masalah');
process.exit(failures === 0 ? 0 : 1);
