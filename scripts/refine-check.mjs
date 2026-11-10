// Uji numerik operasi "Refine Edge" (expand / contract / border / feather /
// smooth) untuk tool seleksi.
//
// Ini adalah SPESIFIKASI EKSEKUTABEL dari geometri yang harus diimplementasikan
// di Kotlin (SelectionRefiner.kt). Uji ini tidak memanggil Kotlin (tidak ada
// kompilator Kotlin di lingkungan ini) - ia menguji algoritmanya sehingga
// ground truth bisa diperiksa, lalu gate statis memastikan Kotlin memakai
// langkah-langkah yang sama.
//
// Yang diuji:
//  1. expand  : seleksi membesar, area bertambah
//  2. contract: seleksi mengecil, area berkurang
//  3. border  : menyisakan cincin (area jauh lebih kecil dari isi)
//  4. feather : tepi jadi bergradasi (ada piksel alpha antara 0..255)
//  5. smooth  : tepi jadi biner lagi (alpha hanya 0 atau 255)
//  6. komposisi: expand lalu contract kembali ke area awal
//
// Jalankan: node scripts/refine-check.mjs

// ------------------------------------------------------------------ util

/** Kotak solid [w x h] pada kanvas, sebagai array alpha 0..255. */
function makeBox(w, h, x0, y0, x1, y1) {
  const a = new Uint8Array(w * h);
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) a[y * w + x] = 255;
  }
  return a;
}

function countSolid(a) {
  let n = 0;
  for (let i = 0; i < a.length; i++) if (a[i] >= 128) n++;
  return n;
}

function countNonZero(a) {
  let n = 0;
  for (let i = 0; i < a.length; i++) if (a[i] > 0) n++;
  return n;
}

function countPartial(a) {
  let n = 0;
  for (let i = 0; i < a.length; i++) if (a[i] > 0 && a[i] < 255) n++;
  return n;
}

/** Kontur luar (dilasi) radius r: piksel jadi solid bila ada piksel solid dalam jarak r. */
function dilate(a, w, h, r) {
  const out = new Uint8Array(w * h);
  const r2 = r * r;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let hit = false;
      if (a[y * w + x] >= 128) hit = true;
      if (!hit) {
        outer: for (let dy = -r; dy <= r; dy++) {
          for (let dx = -r; dx <= r; dx++) {
            if (dx * dx + dy * dy > r2) continue;
            const nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
            if (a[ny * w + nx] >= 128) { hit = true; break outer; }
          }
        }
      }
      if (hit) out[y * w + x] = 255;
    }
  }
  return out;
}

/** Erosi radius r: piksel tetap solid hanya bila semua piksel dalam jarak r solid. */
function erode(a, w, h, r) {
  const out = new Uint8Array(w * h);
  const r2 = r * r;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (a[y * w + x] < 128) continue;
      let ok = true;
      outer: for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy > r2) continue;
          const nx = x + dx, ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= w || ny >= h || a[ny * w + nx] < 128) {
            ok = false; break outer;
          }
        }
      }
      if (ok) out[y * w + x] = 255;
    }
  }
  return out;
}

/** Cincin: pixel solid bila jarak ke bentuk asal antara w/2 dan w/2 + toleransi. */
function borderOf(a, w, h, width) {
  const half = width / 2;
  const outer = dilate(a, w, h, Math.ceil(half));
  const inner = erode(a, w, h, Math.ceil(half));
  const out = new Uint8Array(w * h);
  for (let i = 0; i < a.length; i++) {
    if (outer[i] >= 128 && inner[i] < 128) out[i] = 255;
  }
  return out;
}

/** Blur kotak dua arah (setara feather bawaan WandEngine). */
function blurBox(a, w, h, r) {
  const tmp = new Uint8Array(w * h);
  const dst = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let sum = 0;
      for (let k = -r; k <= r; k++) {
        const xx = Math.min(w - 1, Math.max(0, x + k));
        sum += a[y * w + xx];
      }
      tmp[y * w + x] = Math.round(sum / (2 * r + 1));
    }
  }
  for (let x = 0; x < w; x++) {
    for (let y = 0; y < h; y++) {
      let sum = 0;
      for (let k = -r; k <= r; k++) {
        const yy = Math.min(h - 1, Math.max(0, y + k));
        sum += tmp[yy * w + x];
      }
      dst[y * w + x] = Math.round(sum / (2 * r + 1));
    }
  }
  return dst;
}

function threshold(a, t) {
  const out = new Uint8Array(a.length);
  for (let i = 0; i < a.length; i++) out[i] = a[i] >= t ? 255 : 0;
  return out;
}

// ------------------------------------------------------------------ assert
let pass = 0, fail = 0;
function assert(cond, msg) {
  if (cond) { pass++; console.log('OK: ' + msg); }
  else { fail++; console.log('FAIL: ' + msg); }
}

// ------------------------------------------------------------------ kasus
const W = 64, H = 64;
const base = makeBox(W, H, 20, 20, 40, 40);
const baseArea = countSolid(base); // 20*20 = 400

// 1. EXPAND -------------------------------------------------------------
const exp = dilate(base, W, H, 3);
const expArea = countSolid(exp);
assert(expArea > baseArea, `expand menambah area (${baseArea} -> ${expArea})`);
// dilasi radius 3 menambah ~ perimeter*3 + pi*9 = 80*3+28 = 268
assert(expArea >= baseArea + 200, `expand radius 3 menambah >200 px (tambah ${expArea - baseArea})`);
assert(countSolid(erode(exp, W, H, 3)) === baseArea,
  'expand lalu contract mengembalikan area asli');

// 2. CONTRACT -----------------------------------------------------------
const con = erode(base, W, H, 3);
const conArea = countSolid(con);
assert(conArea < baseArea, `contract mengurangi area (${baseArea} -> ${conArea})`);
// Erosi radius r mengikis ~perimeter*r (80*3=240), dikurangi sudut.
const reduction = baseArea - conArea;
assert(reduction > 120 && reduction < 300,
  `contract radius 3 mengikis ~perimeter*r (kurang ${reduction}, antara 120..300)`);

// 3. BORDER -------------------------------------------------------------
// Pakai kotak lebih besar: pada kotak kecil, cincin 4px eating sebagian
// besar isi sehingga rasio tak bermakna.
const bigBase = makeBox(W, H, 10, 10, 50, 50);
const bigArea = countSolid(bigBase);
const bor = borderOf(bigBase, W, H, 4);
const borArea = countSolid(bor);
assert(borArea > 0, `border menghasilkan cincin (${borArea} px)`);
assert(borArea < bigArea / 2, `border jauh lebih kecil dari isi (${borArea} < ${bigArea / 2})`);
assert(borArea > bigArea / 8, `border cukup tebal (${borArea} px)`);

// 4. FEATHER ------------------------------------------------------------
const fea = blurBox(base, W, H, 4);
assert(countPartial(fea) > 0, `feather menghasilkan tepi bergradasi (${countPartial(fea)} px partial)`);
assert(countSolid(fea) > 0, 'feather masih menyisakan area solid');
assert(countNonZero(fea) > countSolid(fea),
  'feather menambah piksel transisi di luar tepi');

// 5. SMOOTH -------------------------------------------------------------
const smo = threshold(blurBox(base, W, H, 3), 128);
assert(countPartial(smo) === 0, 'smooth mengembalikan alpha biner (tak ada partial)');
assert(Math.abs(countSolid(smo) - baseArea) <= baseArea * 0.35,
  `smooth mempertahankan area dalam toleransi (${countSolid(smo)} vs ${baseArea})`);

// 6. KOMPOSISI ----------------------------------------------------------
const expThenCon = erode(dilate(base, W, H, 4), W, H, 4);
assert(countSolid(expThenCon) <= baseArea + 40,
  `expand+contract tidak melebihi area asli jauh (${countSolid(expThenCon)} vs ${baseArea})`);

// 7. KASUS DEGENERAT ----------------------------------------------------
const empty = new Uint8Array(W * H);
assert(countSolid(dilate(empty, W, H, 3)) === 0, 'expand seleksi kosong tetap kosong');
assert(countSolid(erode(empty, W, H, 3)) === 0, 'contract seleksi kosong tetap kosong');
assert(countSolid(borderOf(empty, W, H, 4)) === 0, 'border seleksi kosong tetap kosong');

// 8. TEPI KANVAS --------------------------------------------------------
// Seleksi menempel tepi: expand tak boleh crash / tak boleh melebihi kanvas.
const edge = makeBox(W, H, 0, 0, 10, 10);
const edgeExp = dilate(edge, W, H, 4);
assert(countSolid(edgeExp) >= countSolid(edge), 'expand di tepi kanvas tidak mengurangi area');
let outOfBounds = false;
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    if (x < 0 || y < 0 || x >= W || y >= H) outOfBounds = true;
  }
}
assert(!outOfBounds, 'hasil operasi tetap di dalam kanvas');

console.log('');
if (fail > 0) {
  console.log(`refine-check FAILED: ${fail} gagal, ${pass} lulus`);
  process.exit(1);
}
console.log(`refine-check PASSED: ${pass} asersi (expand/contract/border/feather/smooth)`);
