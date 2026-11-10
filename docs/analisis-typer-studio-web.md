# Analisis TypeR Studio for Web

**Status:** analisis statis dengan REA, bukan uji runtime.
**Evidence:** `ev_0bebf2e5e39b1bbde5d52d3c97de17a59868aca79196b555bf8ef7ed33660a3f`
**Cakupan:** 8 modul inti (`psd.js`, `session.js`, `workspace.js`, `recent.js`, `kit.js`, `fonts.js`, `shell.js`, `i18n.js`) + pemeriksaan manual `app.js`, `photoshop.js`, `indesign.js`, `generative.js`, `textmask.js`, dan worker. Panel iframe/bridge/vendor tidak ikut terunduh.

---

## 1. Ringkasan eksekutif

TypeR Studio for Web adalah editor lettering manga/comic berbasis browser yang meniru alur Photoshop untuk kebutuhan typesetter: PSD/PSB, IDML/INDD, teks, SFX, masking, cleanup balon, dan generative fill.

Kekuatan utamanya:

- PSD yang tidak disentuh dipertahankan pixel, mask, effect, dan data teksnya.
- IDML ditulis kembali secara parsial; XML yang tidak berubah tetap utuh.
- Pekerjaan berat dipindah ke worker.
- Ada AI lokal (LaMa) sebagai alternatif cloud.
- Shell PWA bisa dibuka offline.

Kelemahan utama:

- `app.js` sangat monolitik (15.600+ baris).
- Detect Text aktif default dan mengirim halaman ke server.
- PSD selalu disimpan 8-bit.
- INDD hanya bisa dibaca, tidak ditulis.
- Lisensi `ScanR/TypeR-Studio` tidak jelas.

| Aspek | Nilai | Alasan |
|---|---:|---|
| Kelengkapan fitur | 9/10 | Sangat lengkap untuk lettering manga |
| Interoperabilitas | 8/10 | PSD/PSB dan IDML kuat; INDD read-only |
| Arsitektur | 7/10 | Modul jelas, worker bagus, tetapi monolitik |
| Performa | 7/10 | Worker/cache baik; kanvas besar terbatas browser |
| Privasi | 5/10 | Local-first, tetapi upload default untuk Detect Text |
| Maintainability | 5/10 | Global state, tanpa type-check/build |
| Lisensi | 2/10 | TypeR-Studio tidak punya lisensi |

---

## 2. Arsitektur

```text
index.html
├─ shell.js / keyclick.js / touch.js     → perangkat, input, gesture
├─ app.js                                → UI, tool, command, alur kerja
├─ iframe panel-frame                    → panel properti (bundle terpisah)
├─ photoshop.js                          → model dokumen/layer/selection/history
├─ psd.js + psd-worker.js                → PSD via ag-psd
├─ indesign.js                           → IDML + INDD via indd.wasm
├─ inpaint.js                            → content-aware fill di worker
├─ generative.js + lama-worker.js        → Generative Fill lokal/cloud
├─ textmask.js                           → Detect Text / Remove Text
├─ fonts.js + fontfinder.js              → font lokal, IndexedDB, Font FindeR
├─ session.js / recent.js / workspace.js → persistensi dan layout
└─ sw.js                                 → service worker/PWA
```

Poin penting:

- `photoshop.js` adalah inti domain: dokumen, layer, mask, selection, history, render, transform.
- Panel berada di iframe terpisah; detail bundle panel/bridge tidak diverifikasi.
- Tidak ada bundler; semua dimuat lewat `<script>` dan objek global `window.TypeR*`.
- PSD, inpaint, auto-align, dan LaMa berjalan di worker dengan fallback ke main thread.
- Dokumen disimpan di IndexedDB; preferensi di localStorage; aset di Cache Storage.

---

## 3. Fitur utama

### Dokumen dan format

- Buka/simpan PSD dan PSB.
- Buka/simpan IDML; buka INDD/INDT tetapi hanya bisa disimpan sebagai IDML.
- Impor PNG/JPEG/WebP; ekspor PNG/JPEG/WebP.
- Multi-dokumen/multi-halaman dalam tab.
- Batch save dan batch export.
- Layer yang tidak diedit dipertahankan raw.

### Editing

- Layer, group, mask, vector mask, clipping mask.
- Blend mode, opacity, layer effects.
- Selection: rectangle, ellipse, lasso, polygon, magic wand, quick mask.
- Transform, crop, flip, rotate, free transform.
- Text warp/perspective.
- History/undo-redo.
- Clone stamp, healing brush, magic eraser, brush, gradient.
- Bubble Reshaper (eksperimental).
- Auto-Align Layers.

### Tipografi

- Point text dan paragraph text.
- Style runs dalam satu layer.
- Font, size, tracking, kerning, leading, baseline shift.
- Horizontal/vertical scale, faux bold/italic, all caps.
- Ligatures, hyphenation, line-break panel.
- Vertical alignment.
- Konversi teks ke SFX dan pemisahan huruf SFX.

### Cleanup dan AI

- Detect Text.
- Remove Text.
- Content-Aware Fill berbasis coarse-to-fine + PatchMatch.
- Generative Fill lokal (LaMa) atau cloud (Gemini/fal.ai).
- Remove Text on Several Pages.
- Spot Healing, Clone Stamp, Magic Eraser.
- Font FindeR untuk mencari/mengunduh font mirip.

### PWA dan UX

- Service worker untuk membuka aplikasi saat offline.
- Cache aset aplikasi.
- 18 bahasa.
- Keyboard shortcut ala Photoshop.
- Workspace layout tersimpan.
- Touch/gesture.
- Onboarding dan panduan bawaan.

---

## 4. Format file dan kompatibilitas

### PSD/PSB

- Membaca PSD 8/16/32-bit.
- Kanvas internal bekerja pada 8-bit.
- Saat save, `bitsPerChannel` selalu 8.
- Layer yang tidak disentuh dikembalikan dari raw channel aslinya.
- Layer teks yang diedit dapat membuat Photoshop menganggap ulang text layer.
- Smart object/linked file dipertahankan hanya jika masih dipakai.
- PSD otomatis menjadi PSB bila nama `.psb` atau dimensi >30.000 px.
- Efek kompleks yang tidak dikenali bisa hilang tanpa peringatan (`logMissingFeatures: false`).

**Implikasi:** cukup untuk manga 8-bit RGB; kurang untuk komik 16-bit. Wajib uji round-trip dengan file PSD nyata.

### IDML/INDD

- IDML dibaca sebagai ZIP + XML.
- Halaman, spread, text frame, story, style, dan gambar terhubung dipetakan ke model internal.
- Saat save, hanya bagian yang berubah ditulis ulang.
- Gambar tetap menjadi link, bukan ditanam, kecuali export package.
- INDD dikonversi ke IDML lewat `indd.wasm`; INDD tidak bisa ditulis balik.
- Perubahan gambar tidak otomatis masuk ke IDML; TypeR memberi peringatan.

**Implikasi:** cocok untuk alur typesetting yang berakhir di InDesign, bukan pengganti penuh InDesign.

---

## 5. AI, jaringan, dan privasi

| Layanan | Fungsi | Data dikirim | Default | Butuh internet |
|---|---|---|---|---|
| `temizle.saficizgi.com` | Detect Text | Gambar halaman, ~14,5 MB/halaman | Aktif | Ya |
| LaMa Manga lokal | Generative Fill | Tidak ada; model diunduh sekali | Opsional | Saat unduh |
| Google Gemini | Generative Fill | Selection + konteks | Opsional, key sendiri | Ya |
| fal.ai | Generative Fill | Selection + konteks | Opsional, key sendiri | Ya |
| Font FindeR | Cari/unduh font | Query + file font | Opsional | Ya |
| `feedback.teamscanr.fr` | Feedback | Rating + teks | Opsional | Ya |

Detail:

- Detect Text aktif default; instalasi mendapat token anonim dari `/v1/device`.
- API key cloud disimpan di localStorage dalam JSON biasa.
- LaMa lokal: model ~207 MB + runtime ~27 MB, cache `typer-models-1`, 512×512, WebGPU/WASM.
- Tanpa cross-origin isolation, WASM turun ke single-thread.
- Tidak terlihat meta CSP di `index.html`; header server belum diverifikasi.
- Dokumen tidak diunggah oleh fitur penyimpanan; yang dikirim adalah fitur AI, font finder, dan feedback.

**Rekomendasi:** jadikan Detect Text opt-in, tampilkan data yang dikirim, sediakan self-host/endpoint sendiri, dan jangan simpan API key di localStorage untuk produksi.

---

## 6. Penyimpanan dan PWA

- IndexedDB `typer-web-documents` — record dokumen dan pixel canvas.
- IndexedDB `typer-web-recent` — dokumen terakhir.
- IndexedDB `typer-web` — font impor.
- IndexedDB `typer-web-font-info` — metadata font.
- Cache Storage `typer-web-1` — shell aplikasi.
- Cache Storage `typer-models-1` — model LaMa.
- localStorage — preferensi, shortcut, theme, API key, token Detect Text, onboarding.

Service worker memakai network-first dengan fallback cache setelah 1,5 detik. Dokumen pengguna tidak disimpan di service worker. Ada lock antar-tab (`navigator.locks`) agar dua tab tidak mengedit dokumen yang sama.

**Risiko:** kuota browser, eviction, mode private, dan halaman sangat besar.

---

## 7. Kualitas implementasi

### Kekuatan

- Pemisahan modul secara fungsional jelas.
- Kommentar source kaya dan menjelaskan alasan desain.
- Worker offload + fallback.
- Graceful degradation saat worker/WASM/font gagal.
- Strategi preserve untouched PSD layers tepat untuk typesetting.
- Local-first.
- Dukungan keyboard, touch, i18n, onboarding.

### Masalah

- `app.js` ±15.651 baris; `photoshop.js` ±9.627 baris.
- Global state dan `window.TypeR*`; sulit diuji dan diisolasi.
- Tidak ada build/type-check yang terlihat.
- Tidak ada test suite di artifact web.
- Iframe bridge menambah kompleksitas.
- Banyak error hanya `console.warn`.
- PSD selalu 8-bit saat save; efek kompleks bisa hilang.
- Ketergantungan eksternal: Detect Text, cloud AI, Font FindeR, CDN.
- Bubble Reshaper masih eksperimental.

---

## 8. Lisensi

- `ScanR/TypeR-Studio` tidak punya lisensi. GitHub API `license: null`; tidak ada LICENSE di artifact. Secara default all rights reserved.
- `ScanR/TypeR` (repo terpisah) MIT.
- `ag-psd` MIT.
- `indd-utils` MIT/Apache-2.0 menurut komentar source.
- Lucide ISC.
- Lisensi model LaMa Manga dan font Wildjess perlu dicek terpisah.

**Jangan memakai ulang kode, aset, font, atau desain TypeR-Studio tanpa izin.** Gunakan hanya sebagai referensi perilaku.

---

## 9. Yang layak ditiru untuk GrooxTyper

1. PSD/IDML sebagai jalur interoperabilitas.
2. Pertahankan data yang tidak diedit; jangan re-encode semua layer.
3. Worker + fallback untuk inpaint, deteksi, dan render.
4. AI lokal sebagai default; cloud opt-in.
5. Hindari monolitik dan cloud-default TypeR.
6. Pisahkan state editor, renderer, dan format adapter.

---

## 10. Batasan analisis

Belum diverifikasi tanpa runtime:

- Performa halaman 720×16000 atau PSD berlapis banyak.
- Fidelity PSD round-trip pada file Photoshop nyata.
- Perilaku panel iframe/bridge.
- Retensi data di Temizle, Google, dan fal.ai.
- Header keamanan server (CSP, COOP/COEP).
- Apakah semua efek PSD benar-benar dipertahankan.

---

## Kesimpulan

TypeR Studio for Web adalah referensi kaya untuk aplikasi lettering manga: PSD/IDML, model teks, SFX, worker, dan AI lokalnya menunjukkan pola yang matang. Namun kode/asetnya tidak berlisensi untuk dipakai ulang, arsitekturnya terlalu monolitik untuk fondasi jangka panjang, dan fitur cloud-nya membawa risiko privasi.
