// Spec numerik aturan warna Magic Wand gaya TypeR/Photoshop.
//
// Ini SPESIFIKASI EKSEKUTABEL dari aturan yang diimplementasikan di Kotlin
// (WandColorMatch.kt + HugeWandEngine.kt). Uji ini tidak memanggil Kotlin
// (tidak ada kompilator Kotlin di lingkungan ini) - ia menguji algoritmanya
// supaya ground truth bisa diperiksa, lalu gate statis di brush-check.js
// memastikan Kotlin memakai langkah-langkah yang sama.
//
// Yang diuji:
//  1. Aturan TypeR: |dr| <= tol && |dg| <= tol && |db| <= tol (per-kanal).
//  2. Aturan ini = max(|dr|,|dg|,|db|) -> Chebyshev di ruang sRGB.
//  3. Batas keras tepat di toleransi (bukan 1.5x seperti ramp GIMP lama).
//  4. Tepi 40% terakhir diberi gradasi (coverage 0..255).
//  5. Regresi yang diperbaiki: seed gelap (0,0,0) dengan toleransi 32
//     harus tetap memilih piksel gelap yang mirip (versi lama ambangnya
//     runtuh ke ~1/255 sehingga tak milih apa pun).
//
// Jalankan: node scripts/wand-match-check.mjs

// ------------------------------------------------------------------ util
const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);

/** Aturan TypeR: selisih absolut per-kanal vs seed. */
const typeRDistance = (r, g, b, sr, sg, sb) =>
  Math.max(Math.abs(r - sr), Math.abs(g - sg), Math.abs(b - sb));

/** Versi Kotlin (WandColorMatch.distance) - harus identik. */
const kotlinDistance = (p, sr, sg, sb) => {
  const r = (p >> 16) & 255;
  const g = (p >> 8) & 255;
  const b = p & 255;
  const adr = Math.abs(r - sr);
  const adg = Math.abs(g - sg);
  const adb = Math.abs(b - sb);
  return Math.max(adr, adg, adb);
};

/** Coverage Kotlin (WandColorMatch.coverage). */
const kotlinCoverage = (d, tol, antialias) => {
  if (tol <= 0) return d === 0 ? 1 : 0;
  if (d > tol) return 0;
  if (!antialias) return 1;
  const edge = tol * 0.4;
  const solidLimit = tol - edge;
  if (d <= solidLimit) return 1;
  return clamp(0.6 + ((tol - d) / edge) * 0.4, 0, 1);
};

/** Versi LAMA (linear-light GIMP): ambang diturunkan dari kanal seed TERANG. */
const linearLight = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
const oldThresholdFor = (maxDist, seed) => {
  const r = (seed >> 16) & 255;
  const g = (seed >> 8) & 255;
  const b = seed & 255;
  const v = Math.max(r, g, b);
  const lo = clamp(v - maxDist, 0, 255);
  const d = Math.abs(linearLight(v) - linearLight(Math.floor(lo)));
  return clamp(Math.floor(d * 255), 1, 255);
};
const oldDistance = (p, sr, sg, sb) => {
  const dr = linearLight((p >> 16) & 255) - linearLight(sr);
  const dg = linearLight((p >> 8) & 255) - linearLight(sg);
  const db = linearLight(p & 255) - linearLight(sb);
  return Math.max(Math.abs(dr), Math.abs(dg), Math.abs(db));
};

// ------------------------------------------------------------------ assert
let pass = 0, fail = 0;
function assert(cond, msg) {
  if (cond) { pass++; console.log('OK: ' + msg); }
  else { fail++; console.log('FAIL: ' + msg); }
}

// ------------------------------------------------------------------ kasus
const TOL = 32;

// 1. Kesetaraan aturan TypeR dengan jarak Chebyshev sRGB ---------------------
{
  const seeds = [
    [255, 255, 255], [0, 0, 0], [128, 128, 128],
    [250, 240, 220], [12, 40, 90], [200, 30, 30]
  ];
  const probes = [
    [255, 255, 255], [0, 0, 0], [255, 255, 224], [223, 255, 255],
    [255, 224, 255], [32, 32, 32], [31, 31, 31], [33, 33, 33],
    [240, 250, 235], [10, 45, 95], [100, 100, 100]
  ];
  let same = 0, total = 0;
  for (const [sr, sg, sb] of seeds) {
    for (const [r, g, b] of probes) {
      total++;
      const a = typeRDistance(r, g, b, sr, sg, sb);
      const c = kotlinDistance(((r << 16) | (g << 8) | b) >>> 0, sr, sg, sb);
      if (a === c) same++;
    }
  }
  assert(same === total, `jarak Kotlin == aturan TypeR untuk semua probed (${same}/${total})`);
}

// 2. Batas keras tepat di toleransi ---------------------------------------
{
  const sr = 255, sg = 255, sb = 255;
  const d32 = typeRDistance(223, 255, 255, sr, sg, sb); // 32
  const d33 = typeRDistance(222, 255, 255, sr, sg, sb); // 33
  assert(d32 === 32, `jarak 32 = ${d32}`);
  assert(d33 === 33, `jarak 33 = ${d33}`);
  assert(kotlinCoverage(d33, TOL, true) === 0, 'piksel di luar toleransi = 0 (batas keras, bukan 1.5x)');
  assert(kotlinCoverage(d32, TOL, true) > 0, 'piksel tepat di toleransi masih > 0 (tepi lembut)');
  assert(kotlinCoverage(0, TOL, true) === 1, 'warna identik = coverage penuh');
  assert(kotlinCoverage(19, TOL, true) === 1, 'inti seleksi (d <= 0.6*tol) tetap solid');
  // Semua piksel d <= tol harus tetap lolos ambang binar 128 (ala TypeR).
  let binaryKeepsAll = true;
  for (let d = 0; d <= TOL; d++) {
    if (kotlinCoverage(d, TOL, true) * 255 < 128) binaryKeepsAll = false;
  }
  assert(binaryKeepsAll, 'semua piksel dalam toleransi tetap terpilih (batas binar 128 tidak memotong)');
}

// 3. Sifat coverage: monoton turun ------------------------------------------
{
  let mono = true;
  let prev = 1.1;
  for (let d = 0; d <= TOL; d++) {
    const c = kotlinCoverage(d, TOL, true);
    if (c > prev + 1e-6) mono = false;
    prev = c;
  }
  assert(mono, 'coverage monoton turun dari 1 ke 0 seiring jarak naik');
  let allInRange = true;
  for (let d = 0; d < 300; d++) {
    const c = kotlinCoverage(d, TOL, true);
    if (c < 0 || c > 1) allInRange = false;
  }
  assert(allInRange, 'coverage selalu di rentang 0..1');
}

// 4. Anti-alias OFF = biner ------------------------------------------------
{
  assert(kotlinCoverage(0, TOL, false) === 1, 'tanpa anti-alias: warna sama = 1');
  assert(kotlinCoverage(TOL, TOL, false) === 1, 'tanpa anti-alias: tepat di toleransi = 1');
  assert(kotlinCoverage(TOL + 1, TOL, false) === 0, 'tanpa anti-alias: di luar toleransi = 0');
}

// 5. Toleransi 0 = hanya warna persis -------------------------------------
{
  assert(kotlinCoverage(0, 0, true) === 1, 'toleransi 0: warna persis terpilih');
  assert(kotlinCoverage(1, 0, true) === 0, 'toleransi 0: beda 1 tak terpilih');
}

// 6. REGRESI YANG DIPERBAIKI: seed gelap ----------------------------------
// Versi lama: seed (0,0,0) -> ambang linear-light runtuh ke ~1/255, jadi
// mengetuk garis/area gelap dengan toleransi 32 praktis tak milih apa pun.
{
  const seed = 0x000000;
  const oldThr = oldThresholdFor(32, seed);
  assert(oldThr <= 3, `regresi lama terReproduksi: ambang seed gelap = ${oldThr}/255 (hampir nol)`);
  const near = 0x1e1e1e; // 30,30,30 -> jauh dalam toleransi 32
  const oldD = oldDistance(near, 0, 0, 0) * 255;
  const oldMatch = oldD <= oldThr;
  assert(oldMatch === false, 'regresi lama: piksel gelap yang mirip TAK terpilih');
  const newD = kotlinDistance(near, 0, 0, 0);
  assert(newD === 30, `jarak baru per-kanal = ${newD}`);
  assert(kotlinCoverage(newD, TOL, true) > 0 && kotlinCoverage(newD, TOL, true) * 255 >= 128,
    'perbaikan: piksel gelap yang mirip SEKARANG terpilih');
}

// 7. Seed terang tetap berperilaku wajar ----------------------------------
{
  const seed = 0xffffff;
  const paper = 0xf5f2ea; // kertas sedikit kekuningan
  const d = kotlinDistance(paper, 255, 255, 255);
  assert(d === 21, `kertas vs putih: jarak per-kanal = ${d}`);
  assert(kotlinCoverage(d, TOL, true) * 255 >= 128,
    'kertas vs putih tetap terpilih pada toleransi 32');
}

// 8. Warna jenuh ----------------------------------------------------------
{
  // Merah (255,0,0) vs oranye (255,140,0): beda 140 di kanal hijau.
  const d = kotlinDistance(0xff8c00, 255, 0, 0);
  assert(d === 140, `merah vs oranye: jarak = ${d} (bukan Euclidean 242)`);
  assert(kotlinCoverage(d, TOL, true) === 0, 'merah vs oranye tak terpilih pada toleransi 32');
  // Hijau (0,255,0) vs biru (0,0,255): beda 255 di dua kanal.
  const d2 = kotlinDistance(0x0000ff, 0, 255, 0);
  assert(d2 === 255, `hijau vs biru: jarak = ${d2}`);
}

console.log('');
if (fail > 0) {
  console.log(`wand-match-check FAILED: ${fail} gagal, ${pass} lulus`);
  process.exit(1);
}
console.log(`wand-match-check PASSED: ${pass} asersi (aturan warna wand ala TypeR)`);
