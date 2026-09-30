// Uji numerik algoritma "area bubble" dengan geometri ground truth.
//
// Ini adalah SPESIFIKASI EKSEKUTABEL dari pipeline yang harus diimplementasikan
// ulang di Kotlin (BubbleAreaPipeline.kt). Uji ini tidak memanggil Kotlin
// (tidak ada kompilator Kotlin di lingkungan ini) - ia menguji algoritmanya
// sehingga ground truth bisa diperiksa, lalu gate statis memastikan Kotlin
// memakai langkah-langkah yang sama.
//
// Yang diuji (lima kasus dari rencana):
//  1. satu gelembung            -> tepat 1 area, IoU tinggi
//  2. dua gelembung bersinggungan-> tepat 2 area, tumpang tindih kecil
//                                   (kasus image.webp)
//  3. ketuk di luar gelembung   -> null
//  4. gelembung sangat kecil    -> tetap 1 area
//  5. area panel (tombol panel) -> 1 area, IoU tinggi
//
// Jalankan: node scripts/wand-check.mjs

const SQRT2 = 1.41421356;
const LIN = new Float32Array(256);
for (let i = 0; i < 256; i++) {
  const c = i / 255;
  LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

// ------------------------------------------------------------------ util
const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);

function distanceTransform(bin, w, h) {
  const BIG = 1e9;
  const d = new Float32Array(w * h);
  for (let i = 0; i < d.length; i++) d[i] = bin[i] ? BIG : 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * w + x;
      if (!bin[i]) continue;
      let m = d[i];
      if (y > 0) {
        m = Math.min(m, d[i - w] + 1);
        if (x > 0) m = Math.min(m, d[i - w - 1] + SQRT2);
        if (x < w - 1) m = Math.min(m, d[i - w + 1] + SQRT2);
      }
      if (x > 0) m = Math.min(m, d[i - 1] + 1);
      d[i] = m;
    }
  }
  for (let y = h - 1; y >= 0; y--) {
    for (let x = w - 1; x >= 0; x--) {
      const i = y * w + x;
      if (!bin[i]) continue;
      let m = d[i];
      if (y < h - 1) {
        m = Math.min(m, d[i + w] + 1);
        if (x < w - 1) m = Math.min(m, d[i + w + 1] + SQRT2);
        if (x > 0) m = Math.min(m, d[i + w - 1] + SQRT2);
      }
      if (x < w - 1) m = Math.min(m, d[i + 1] + 1);
      d[i] = m;
    }
  }
  return d;
}

function labelComponents(bin, w, h, minPixels) {
  const lab = new Int32Array(w * h);
  const queue = new Int32Array(w * h);
  let next = 0;
  for (let start = 0; start < lab.length; start++) {
    if (!bin[start] || lab[start] !== 0) continue;
    const my = ++next;
    let head = 0, tail = 0, size = 0;
    queue[tail++] = start;
    lab[start] = my;
    while (head < tail) {
      const i = queue[head++];
      size++;
      const x = i % w, y = (i / w) | 0;
      if (x > 0 && bin[i - 1] && !lab[i - 1]) { lab[i - 1] = my; queue[tail++] = i - 1; }
      if (x < w - 1 && bin[i + 1] && !lab[i + 1]) { lab[i + 1] = my; queue[tail++] = i + 1; }
      if (y > 0 && bin[i - w] && !lab[i - w]) { lab[i - w] = my; queue[tail++] = i - w; }
      if (y < h - 1 && bin[i + w] && !lab[i + w]) { lab[i + w] = my; queue[tail++] = i + w; }
    }
    if (size < minPixels) {
      for (let k = 0; k < lab.length; k++) if (lab[k] === my) lab[k] = 0;
      next--;
    }
  }
  return { lab, count: next };
}

// ------------------------------------------------------------------Algoritma
/**
 * @param px piksel grayscale (0..255, 255 = kertas putih)
 * @returns {null | Array<{pixels:Set<number>, box:number[]}>}
 */
function bubbleAreaAt(px, w, h, seedX, seedY, { threshold = 15, allowBorder = false, atSeed = false } = {}) {
  const n = w * h;
  const seed = px[seedY * w + seedX];
  const thr = threshold / 255;
  const sR = LIN[seed], sG = LIN[seed], sB = LIN[seed];
  const covOf = (i) => {
    const p = px[i];
    let d = Math.abs(LIN[p] - sR);
    const e = Math.abs(LIN[p] - sG);
    const f = Math.abs(LIN[p] - sB);
    if (e > d) d = e;
    if (f > d) d = f;
    const aa = 1.5 - d / thr;
    if (aa <= 0) return 0;
    if (aa < 0.5) return aa * 2;
    return 1;
  };
  if (covOf(seedY * w + seedX) <= 0) return null;

  // 1) Flood span (4-ara), sama seperti WandEngine.flood.
  const cov = new Uint8Array(n);
  // Seed TIDAK boleh ditandai sebelum span-nya diproses: kalau sudah
  // nonzero, loop langsung melompatinya dan tidak ada yang tumbuh.
  const stack = [seedY, seedX, seedX + 1];
  while (stack.length) {
    const to = stack.pop(), from = stack.pop(), y = stack.pop();
    const row = y * w;
    let x = from;
    while (x < to) {
      if (cov[row + x] !== 0) { x++; continue; }
      const c = covOf(row + x);
      if (c <= 0) { x++; continue; }
      cov[row + x] = Math.max(1, Math.round(c * 255));
      let start = x, end = x + 1;
      while (start > 0 && cov[row + start - 1] === 0 && covOf(row + start - 1) > 0) {
        cov[row + start - 1] = Math.max(1, Math.round(covOf(row + start - 1) * 255));
        start--;
      }
      while (end < w && cov[row + end] === 0 && covOf(row + end) > 0) {
        cov[row + end] = Math.max(1, Math.round(covOf(row + end) * 255));
        end++;
      }
      x = end;
      if (y + 1 < h) stack.push(y + 1, start, end);
      if (y - 1 >= 0) stack.push(y - 1, start, end);
    }
  }
  const bin = new Uint8Array(n);
  let count = 0;
  for (let i = 0; i < n; i++) {
    bin[i] = cov[i] >= 128 ? 1 : 0;
    if (bin[i]) count++;
  }
  if (count < 20) return null;

  // 2) Komponen seed + cek tepi (menolak "bukan area bubble").
  const { lab, count: labCount } = labelComponents(bin, w, h, 4);
  if (labCount < 1) return null;
  const seedLabel = lab[seedY * w + seedX];
  if (seedLabel === 0) return null;
  let touches = false;
  for (let i = 0; i < n; i++) {
    if (lab[i] !== seedLabel) continue;
    const x = i % w, y = (i / w) | 0;
    if (x === 0 || y === 0 || x === w - 1 || y === h - 1) { touches = true; break; }
  }
  if (touches && !allowBorder) return null;

  // 2b) Batasi mask ke komponen seed saja. Tanpa ini, watershed memecah
  //     seluruh halaman (mis. saat mode panel menyalakan allowBorder), dan
  //     gelembung di luar jalur ikut terpecah.
  for (let i = 0; i < n; i++) if (lab[i] !== seedLabel) bin[i] = 0;

  // 3) Distance transform + puncak (0.7 * max) = satu inti per gelembung.
  const dist = distanceTransform(bin, w, h);
  let maxD = 0, argMax = -1;
  for (let i = 0; i < n; i++) if (bin[i] && dist[i] > maxD) { maxD = dist[i]; argMax = i; }
  if (maxD < 2) return null;
  const thrPeak = Math.max(1, maxD * 0.7);
  const peak = new Uint8Array(n);
  for (let i = 0; i < n; i++) peak[i] = bin[i] && dist[i] >= thrPeak ? 1 : 0;
  const minPix = Math.max(2, Math.floor(count / 2000));
  let cores;
  if (allowBorder) {
    // Mode "Area Panel": satu wilayah tempat user mengetuk, tanpa pemecahan.
    // Memecah panel/background jadi beberapa area tidak pernah diinginkan di
    // sini: aksi ini untuk satu kotak narasi atau satu area jadi.
    const one = new Uint8Array(n);
    if (argMax >= 0) one[argMax] = 1;
    cores = labelComponents(one, w, h, 1);
  } else {
    cores = labelComponents(peak, w, h, minPix);
  }
  if (cores.count === 0 && argMax >= 0) {
    // Gelembung kecil: inti terlalu kecil untuk ambah luas minimum, tapi
    // puncak jarak maksimum adalah inti yang sah. Pakai satu inti.
    peak[argMax] = 1;
    cores = labelComponents(peak, w, h, 1);
  }
  if (cores.count === 0) return null;

  // 4) Tumbuhkan geodesik di dalam mask asli: front pertama yang sampai
  //    memiliki piksel. Garis tempat dua front bertemu = titik terakhir
  //    gelembung bersentuhan (persis yang terjadi pada image.webp).
  const owner = new Int32Array(n);
  const queue = new Int32Array(n);
  let head = 0, tail = 0;
  for (let i = 0; i < n; i++) {
    if (cores.lab[i] !== 0) { owner[i] = cores.lab[i]; queue[tail++] = i; }
  }
  while (head < tail) {
    const i = queue[head++];
    const x = i % w, y = (i / w) | 0;
    const me = owner[i];
    if (x > 0 && owner[i - 1] === 0 && bin[i - 1]) { owner[i - 1] = me; queue[tail++] = i - 1; }
    if (x < w - 1 && owner[i + 1] === 0 && bin[i + 1]) { owner[i + 1] = me; queue[tail++] = i + 1; }
    if (y > 0 && owner[i - w] === 0 && bin[i - w]) { owner[i - w] = me; queue[tail++] = i - w; }
    if (y < h - 1 && owner[i + w] === 0 && bin[i + w]) { owner[i + w] = me; queue[tail++] = i + w; }
  }

  // 5) Kumpulkan area per pemilik.
  const groups = new Map();
  for (let i = 0; i < n; i++) {
    const id = owner[i];
    if (id === 0) continue;
    let g = groups.get(id);
    if (!g) { g = { pixels: [], x1: w, y1: h, x2: -1, y2: -1 }; groups.set(id, g); }
    g.pixels.push(i);
    const x = i % w, y = (i / w) | 0;
    if (x < g.x1) g.x1 = x; if (y < g.y1) g.y1 = y;
    if (x > g.x2) g.x2 = x; if (y > g.y2) g.y2 = y;
  }
  const out = [...groups.values()].filter((g) => g.pixels.length >= minPix);
  if (out.length === 0) return null;
  out.sort((a, b) => b.pixels.length - a.pixels.length);
  const list = out.map((g) => ({ pixels: g.pixels, box: [g.x1, g.y1, g.x2 + 1, g.y2 + 1] }));
  if (atSeed) {
    // API produksi (BubbleAreaPipeline.areaAt) mengembalikan SATU area: yang
    // memuat titik ketuk. Mengembalikan kelompok terbesar selalu salah, karena
    // mengetuk gelembung kedua akan mendapat gelembung pertama.
    const seedIdx = seedY * w + seedX;
    const own = list.find((g) => g.pixels.indexOf(seedIdx) >= 0);
    if (own) return [own];
  }
  return list;
}

// ------------------------------------------------------------------ geometri
function page(w, h) { return new Uint8Array(w * h).fill(255); }
function ring(px, w, h, cx, cy, r, thick) {
  for (let y = Math.max(0, cy - r - thick - 1); y < Math.min(h, cy + r + thick + 2); y++) {
    for (let x = Math.max(0, cx - r - thick - 1); x < Math.min(w, cx + r + thick + 2); x++) {
      const d = Math.hypot(x - cx, y - cy);
      if (d <= r && d > r - thick) px[y * w + x] = 0;
    }
  }
}
function ringRect(px, w, h, x0, y0, x1, y1, thick) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const onEdge = x - x0 < thick || x1 - x < thick || y - y0 < thick || y1 - y < thick;
    if (onEdge) px[y * w + x] = 0;
  }
}
function discMask(w, h, cx, cy, r) {
  const m = new Set();
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (Math.hypot(x - cx, y - cy) <= r) m.add(y * w + x);
  }
  return m;
}
function iouSet(a, b) {
  let inter = 0;
  for (const v of a) if (b.has(v)) inter++;
  const uni = a.size + b.size - inter;
  return uni > 0 ? inter / uni : 0;
}
const overlapRatio = (a, b) => {
  let inter = 0;
  for (const v of a) if (b.has(v)) inter++;
  return inter / Math.max(1, Math.min(a.size, b.size));
};

// ------------------------------------------------------------------ kasus
let fails = 0;
const ok = (m) => console.log('  OK   ' + m);
const bad = (m) => { fails++; console.log('  FAIL ' + m); };

console.log('== Kasus 1: satu gelembung ==');
{
  const w = 400, h = 400, r = 60, thick = 5;
  const px = page(w, h);
  ring(px, w, h, 200, 200, r, thick);
  const res = bubbleAreaAt(px, w, h, 200, 200, { threshold: 15 });
  if (!res) bad('tidak menghasilkan area');
  else {
    const truth = discMask(w, h, 200, 200, r - thick);
    const i = iouSet(new Set(res[0].pixels), truth);
    if (res.length !== 1) bad('harus tepat 1 area, dapat ' + res.length);
    else ok('1 area');
    if (i < 0.9) bad('IoU ' + i.toFixed(3) + ' < 0.90');
    else ok('IoU ' + i.toFixed(3));
  }
}

console.log('== Kasus 2: dua gelembung bersinggungan (image.webp) ==');
{
  const w = 400, h = 400, r = 60, thick = 5, d = 100;
  const px = page(w, h);
  ring(px, w, h, 150, 200, r, thick);
  ring(px, w, h, 150 + d, 200, r, thick);
  // Dua gelembung yang benar-benar bersinggungan: dinding yang berada di
  // DALAM lingkaran lain dihapus, sehingga interiornya menyatu menjadi satu
  // wilayah. Inilah yang terjadi pada gambar image.webp: satu ketukan wand
  // akan mengambil KEDUA gelembung, lalu watershed harus memisahkannya.
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (px[y * w + x] !== 0) continue;
      const inA = Math.hypot(x - 150, y - 200) < r - thick;
      const inB = Math.hypot(x - (150 + d), y - 200) < r - thick;
      if (inA || inB) px[y * w + x] = 255;
    }
  }
  const res = bubbleAreaAt(px, w, h, 150, 200, { threshold: 15 });
  if (!res) bad('tidak menghasilkan area');
  else {
    if (res.length !== 2) bad('harus tepat 2 area, dapat ' + res.length + ' ' + JSON.stringify(res.map(a => a.box)));
    else ok('2 area, kotak ' + JSON.stringify(res.map(a => a.box)));
    if (res.length === 2) {
      const s0 = new Set(res[0].pixels), s1 = new Set(res[1].pixels);
      const ov = overlapRatio(s0, s1);
      if (ov > 0.03) bad('tumpang tindih ' + (ov * 100).toFixed(1) + '% > 3%');
      else ok('tumpang tindih ' + (ov * 100).toFixed(2) + '% (batas 3%)');
      const t0 = discMask(w, h, 150, 200, r - thick), t1 = discMask(w, h, 150 + d, 200, r - thick);
      const i0 = iouSet(s0, t0), i1 = iouSet(s1, t1);
      const best0 = Math.max(i0, iouSet(s0, t1)), best1 = Math.max(i1, iouSet(s1, t0));
      if (best0 < 0.75 || best1 < 0.75) bad('IoU per gelembung ' + best0.toFixed(2) + '/' + best1.toFixed(2) + ' < 0.75');
      else ok('IoU per gelembung ' + best0.toFixed(2) + '/' + best1.toFixed(2));
      // Pemisahan harus terjadi di antara dua lingkaran, bukan di tepi luar.
      const sep = res[1].box[0] - res[0].box[2];
      if (sep < -thick) bad('garis pemisah di luar kedua lingkaran (tumpang tindih kotak)');
      else ok('garis pemisah di antara kedua lingkaran');
    }
  }
}

console.log('== Kasus 2b: ketuk gelembung kedua (harus area kedua) ==');
{
  const w = 400, h = 400, r = 60, thick = 5, d = 100;
  const px = page(w, h);
  ring(px, w, h, 150, 200, r, thick);
  ring(px, w, h, 150 + d, 200, r, thick);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (px[y * w + x] !== 0) continue;
      const inA = Math.hypot(x - 150, y - 200) < r - thick;
      const inB = Math.hypot(x - (150 + d), y - 200) < r - thick;
      if (inA || inB) px[y * w + x] = 255;
    }
  }
  const kiri = bubbleAreaAt(px, w, h, 150, 200, { threshold: 15, atSeed: true });
  const kanan = bubbleAreaAt(px, w, h, 150 + d, 200, { threshold: 15, atSeed: true });
  if (!kiri || !kanan) bad('salah satu ketukan tidak menghasilkan area');
  else {
    if (kiri[0].box[0] === kanan[0].box[0]) bad('kedua ketukan mengembalikan kotak yang SAMA ' + JSON.stringify(kiri[0].box));
    else ok('kotak berbeda: ' + JSON.stringify(kiri[0].box) + ' vs ' + JSON.stringify(kanan[0].box));
    const tKiri = discMask(w, h, 150, 200, r - thick);
    const tKanan = discMask(w, h, 150 + d, 200, r - thick);
    const iKiri = iouSet(new Set(kiri[0].pixels), tKiri);
    const iKanan = iouSet(new Set(kanan[0].pixels), tKanan);
    if (iKiri < 0.9 || iKanan < 0.9) bad('IoU kiri ' + iKiri.toFixed(2) + ' kanan ' + iKanan.toFixed(2) + ' (harus >= 0.90 masing-masing)');
    else ok('IoU kiri ' + iKiri.toFixed(2) + ' / kanan ' + iKanan.toFixed(2));
  }
}

console.log('== Kasus 3: ketuk di luar gelembung ==');
{
  const w = 400, h = 400;
  const px = page(w, h);
  ring(px, w, h, 120, 120, 60, 5);
  const res = bubbleAreaAt(px, w, h, 330, 330, { threshold: 15 });
  if (res === null) ok('ditolak (null) seperti yang diminta');
  else bad('harusnya null, dapat ' + res.length + ' area');
  // Dengan mode panel, area yang sama harus diterima.
  const resPanel = bubbleAreaAt(px, w, h, 330, 330, { threshold: 15, allowBorder: true });
  if (resPanel === null) bad('mode panel harus menerima area');
  else if (resPanel.length !== 1) bad('mode panel harus menghasilkan SATU area, dapat ' + resPanel.length);
  else ok('mode panel menerima tepat 1 area');
}

console.log('== Kasus 4: gelembung kecil ==');
{
  const w = 400, h = 400, r = 18, thick = 4;
  const px = page(w, h);
  ring(px, w, h, 200, 200, r, thick);
  const res = bubbleAreaAt(px, w, h, 200, 200, { threshold: 15 });
  if (!res) bad('gelembung kecil hilang');
  else {
    const truth = discMask(w, h, 200, 200, r - thick);
    const i = iouSet(new Set(res[0].pixels), truth);
    if (res.length !== 1) bad('harus 1 area, dapat ' + res.length);
    else ok('1 area (' + res[0].pixels.length + ' piksel)');
    if (i < 0.75) bad('IoU ' + i.toFixed(3) + ' < 0.75');
    else ok('IoU ' + i.toFixed(3));
  }
}

console.log('== Kasus 5: area panel (tombol Area Panel) ==');
{
  const w = 400, h = 400;
  const px = page(w, h);
  // Panel: kotak 200x120 dengan outline, di dalam kertas polos.
  ringRect(px, w, h, 100, 140, 300, 260, 5);
  // Kotak viel: tidak ada gelembung, jadi flood di dalam panel tidak menyentuh tepi kanvas.
  const res = bubbleAreaAt(px, w, h, 200, 200, { threshold: 15, allowBorder: true });
  if (!res) bad('area panel tidak terdeteksi');
  else {
    const truth = new Set();
    for (let y = 145; y < 260; y++) for (let x = 105; x < 300; x++) truth.add(y * w + x);
    const i = iouSet(new Set(res[0].pixels), truth);
    if (res.length !== 1) bad('harus 1 area, dapat ' + res.length);
    else ok('1 area');
    if (i < 0.9) bad('IoU ' + i.toFixed(3) + ' < 0.90');
    else ok('IoU ' + i.toFixed(3));
  }
}

console.log('== Kasus 6 (tambahan): halaman tanpa gelembung ==');
{
  const w = 300, h = 300;
  const px = page(w, h);
  const res = bubbleAreaAt(px, w, h, 150, 150, { threshold: 15 });
  if (res === null) ok('kertas polos ditolak (tidak ada area ngawur)');
  else bad('kertas polos seharusnya ditolak');
}

console.log(fails === 0
  ? '\nwand-check PASSED: 6 kasus pipeline area bubble sesuai ground truth'
  : '\nwand-check FAILED: ' + fails + ' masalah');
process.exit(fails === 0 ? 0 : 1);
