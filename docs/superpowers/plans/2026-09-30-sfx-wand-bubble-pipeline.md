# Rencana Implementasi: Rombak SFX, Perbaikan Panel SFX, Pipeline Area Bubble

> **Untuk pekerja agen:** WAJIB memakai sub-skill `executing-plans` (atau
> `subagent-driven-development`) untuk mengerjakan rencana ini tugas per tugas.
> Langkah memakai checkbox (`- [ ]`) agar bisa dilacak.

**Tujuan:** membuat panel SFX benar-benar bisa disentuh, menulis ulang gaya SFX
teks dan kuas agar sesuai referensi `c2.webp`, dan membuat pipeline area bubble
(multi-area, bernomor, isi otomatis dari script) yang menangani gelembung
bersinggungan seperti pada `image.webp`.

**Arsitektur:** dua subsistem yang terpisah dan bisa diuji sendiri.
(1) SFX: satu sumber angka gaya bersama (`SfxStyle.kt`) dipakai oleh kuas genre
(`GenreBrushEngine`) dan teks SFX (`TextBox` + `TextRenderer`), dengan panel
yang membaca Compose state. (2) Area bubble: algoritma murni
(`BubbleAreaPipeline.kt`) yang berjalan di atas `WandEngine` yang sudah ada,
diketuk manual per gelembung, hasilnya area bernomor dalam urutan baca manga,
lalu diisi teks dari script.

**Stack:** Kotlin, Jetpack Compose, Android Canvas/Path, onnxruntime (tidak
berubah), Node untuk gate dan uji numerik.

**Spec:** `docs/superpowers/specs/2026-09-30-sfx-wand-bubble-pipeline-design.md`

## Batasan Global

- Batas keras metode JVM 64KB: tidak boleh menambah UI di dalam
  `CanvasEditorScreen` atau `TextEditorPanel`; pakai file/composable baru.
- Fungsi lokal Kotlin hanya melihat deklarasi di atasnya: urutan deklarasi
  state harus benar.
- Komentar, nama, dan pesan UI memakai bahasa Indonesia; tidak boleh ada
  karakter CJK, Siril, atau Arab.
- Setiap push: `node scripts/brush-check.js` harus PASSED, lalu commit, push,
  dan CI hijau.
- Tidak ada model baru, tidak ada dependensi baru, tidak ada file di atas 50MB.
- API Agnes tidak boleh masuk commit.

## Catatan Penting Soal Uji

Mesin ini tidak punya kompilator Kotlin lokal dan tidak bisa menjalankan
Android di sini. Karena itu "uji" di rencana ini berarti:

1. `node scripts/brush-check.js` - gate statis, wajib PASSED.
2. `node scripts/wand-check.mjs` - uji numerik algoritma area bubble dengan
   geometri ground truth.
3. `node scripts/sfx-render-test.mjs` - uji render preset SFX ke PNG.
4. CI GitHub - satu-satunya kompilator; wajib hijau sebelum tugas dianggap selesai.

## Fokus Tinjau

Lima kelas masukan yang paling mungkin menggigit orang yang memakai aplikasi
ini, di urutan kemungkinan:

1. **Ketukan wand di kertas putih yang bukan gelembung** (misalnya gutter antar
   panel): harus ditolak, bukan membuat area raksasa.
2. **Dua gelembung bersinggungan** (`image.webp`): harus jadi dua area yang tidak
   tumpang tindih, bukan satu.
3. **Gelembung sangat kecil** (kurang dari 40 piksel): kontraksi bisa habis; hasil
   harus tetap satu area yang wajar, bukan null.
4. **Kanvas besar (720x16000)**: area pipeline tidak boleh OOM atau menggantung
   UI; area besar di luar batas piksel harus ditolak dengan pesan.
5. **Ketuk area yang sudah ada**: harus menghapus area itu, bukan menambah duplikat
   di tempat sama.

Setiap kelas di atas punya langkah uji di tugas yang memiliki kodenya (lihat
Tugas 6, 7, dan 8).

---

### Tugas 1: Panel SFX Bisa Disentuh

**Berkas:**
- Ubah: `app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Menghasilkan: `GenreBrushEngine.Settings` dengan seluruh properti sebagai
  Compose state, sehingga `GenreBrushPanel(brushEngine: BrushEngine)` menjadi
  komposabel yang benar-benar bereaksi. Tidak ada konsumen lain yang berubah.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  Tambah di `scripts/brush-check.js` pada blok SFX genre:

  ```js
  for (const st of ['var widthMul by mutableFloatStateOf', 'var gradient by mutableStateOf',
    'var opacity by mutableFloatStateOf', 'var outlineWidth by mutableFloatStateOf',
    'var shadowOn by mutableStateOf', 'var texture by mutableFloatStateOf',
    'var spatter by mutableIntStateOf']) {
    assert(sfxEng.includes(st), 'GenreBrushEngine: setelan jadi Compose state -> ' + st);
  }
  assert(!/var (widthMul|opacity|texture)\s*:\s*Float\s*=/.test(sfxEng), 'GenreBrushEngine: tak ada setelan SFX berupa var biasa (UI beku)');
  ```

  Jalankan: `node scripts/brush-check.js`
  Harus gagal pada asersi pertama.

- [ ] **Langkah 2: Ubah Settings menjadi state Compose**

  Di `GenreBrushEngine.Settings`, ubah semua deklarasi `var x: T = v` menjadi
  `var x by mutableStateOf(v)` untuk `Boolean` dan `Int` warna, serta
  `by mutableFloatStateOf(v)` untuk `Float`, dan `by mutableIntStateOf(v)` untuk
  `Int` biasa. Tambahkan import
  `androidx.compose.runtime.getValue`, `mutableStateOf`, `mutableFloatStateOf`,
  `mutableIntStateOf`, `setValue`.

  `copyFrom` dan `applyPreset` tetap sama (menulis ke state memicu gambar ulang).

- [ ] **Langkah 3: Jalankan gate**

  Jalankan: `node scripts/brush-check.js`
  Harus PASSED.

- [ ] **Langkah 4: Commit**

  ```bash
  git add app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt scripts/brush-check.js
  git commit -m "Panel SFX bisa disentuh: setelan genre jadi Compose state"
  ```

### Tugas 2: Riset Konvensi Lettering SFX

**Berkas:**
- Buat: `docs/sfx-lettering-research.md`

**Antarmuka:**
- Menghasilkan: daftar angka preset yang dipakai Tugas 3, lengkap dengan sumber.

- [ ] **Langkah 1: Riset, jangan menebak**

  Cari rujukan nyata (baca commit video sebelumnya di
  `/tmp/opencode/sfx-video/LAPORAN-SFX.md` sebagai titik awal) untuk hal berikut,
  dan catat URL setiap angka:
  1. Rasio tebal outline terhadap tebal huruf pada lettering komik.
  2. Rasio jarak bayangan terhadap tinggi huruf.
  3. Sudut kemiringan tipikal per kata pada SFX lettering.
  4. Kombinasi gradasi isi yang lazim untuk SFX (mis. putih ke kuning/oranye).
  5. Perbedaan visual empat genre: horror, romance, action, fantasy.

- [ ] **Langkah 2: Tulis hasil ke dokumen**

  `docs/sfx-lettering-research.md` memuat: tabel angka dengan kolom
  `nilai`, `makna`, `sumber`, dan bagian "keputusan sadar" untuk angka yang tidak
  ada sumbernya. Tidak boleh ada angka tanpa sumber atau tanpa alasan.

- [ ] **Langkah 3: Commit**

  ```bash
  git add docs/sfx-lettering-research.md
  git commit -m "Catat riset konvensi lettering SFX sebagai sumber angka preset"
  ```

### Tugas 3: Sumber Gaya SFX Bersama

**Berkas:**
- Buat: `app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Menghasilkan (dipakai Tugas 4 dan 5):

  ```kotlin
  enum class SfxGenre(val displayName: String) { HORROR, ROMANCE, ACTION, FANTASY }

  data class SfxStyleSpec(
      val gradStart: Int, val gradEnd: Int, val gradAngle: Float,
      val inkScale: Float, val outlineScale: Float, val outlineColor: Int,
      val shadowDx: Float, val shadowDy: Float, val shadowBlur: Float,
      val shadowColor: Int, val roughness: Float, val tiltPerWord: Float,
      val spatter: Int
  ) {
      fun copy(): SfxStyleSpec
      companion object { fun presetOf(genre: SfxGenre): SfxStyleSpec }
  }
  ```

  `presetOf` memuat angka dari hasil riset Tugas 2. `GenreBrushEngine.Genre`
  yang sudah ada dipetakan ke `SfxGenre` lewat fungsi
  `SfxGenre.of(genre: GenreBrushEngine.Genre): SfxGenre`.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  ```js
  const sfxStyle = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt'));
  assert(sfxStyle.includes('data class SfxStyleSpec') && sfxStyle.includes('fun presetOf(genre: SfxGenre)'), 'SFX: satu sumber angka gaya bersama');
  for (const f of ['inkScale', 'outlineScale', 'outlineColor', 'shadowDx', 'shadowBlur', 'shadowColor', 'roughness', 'tiltPerWord', 'spatter']) {
    assert(sfxStyle.includes('val ' + f), 'SFX: preset bersama punya field ' + f);
  }
  assert(sfxStyle.includes('SfxGenre.HORROR') && sfxStyle.includes('SfxGenre.ROMANCE') && sfxStyle.includes('SfxGenre.ACTION') && sfxStyle.includes('SfxGenre.FANTASY'), 'SFX: keempat genre punya preset');
  ```

  Jalankan: `node scripts/brush-check.js` - harus gagal.

- [ ] **Langkah 2: Tulis `SfxStyle.kt`**

  Isi `presetOf` dengan angka dari `docs/sfx-lettering-research.md`. Sertakan
  komentar yang menunjuk baris tabel sumber untuk tiap angka preset.

- [ ] **Langkah 3: Jalankan gate**

  Jalankan: `node scripts/brush-check.js` - harus PASSED.

- [ ] **Langkah 4: Commit**

  ```bash
  git add app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt scripts/brush-check.js
  git commit -m "Tambah sumber gaya SFX bersama dari hasil riset"
  ```

### Tugas 4: Kuas Genre Pakai Gaya Bersama

**Berkas:**
- Ubah: `app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt`
- Buat: `scripts/sfx-render-test.mjs`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `SfxStyleSpec.presetOf(genre)`, `SfxGenre.of(genre)`.
- Menghasilkan: `GenreBrushEngine.Settings` mulai diisi dari preset bersama
  (`applyPreset` memakai `SfxStyleSpec`), dan renderer memakai
  `SfxStyleSpec.inkScale`, `outlineScale`, dan `roughness` yang sudah ada.

- [ ] **Langkah 1: Tulis uji render yang gagal**

  `scripts/sfx-render-test.mjs` membuat PNG untuk keempat genre memakai spec
  yang disalin dari `SfxStyle.kt` (versi JS sebagai spesifikasi eksekutable) dan
  memeriksa: isi punya gradasi (dua warna berbeda di dalam area), outline lebih
  lebar dari isi, dan bayangan terlihat. Jalankan:
  `node scripts/sfx-render-test.mjs` - harus gagal (preset lama tidak gradasi
  untuk action dan horror).

- [ ] **Langkah 2: Sambungkan preset**

  Di `GenreBrushEngine.Settings.applyPreset(genre)`, isi setiap field dari
  `SfxStyleSpec.presetOf(SfxGenre.of(genre))` alih-alih angka yang ditulis
  tangan.

- [ ] **Langkah 3: Jalankan uji render dan gate**

  Jalankan: `node scripts/sfx-render-test.mjs` lalu
  `node scripts/brush-check.js` - keduanya harus lulus.

- [ ] **Langkah 4: Commit**

  ```bash
  git add app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt scripts/sfx-render-test.mjs scripts/brush-check.js
  git commit -m "Kuas SFX genre memakai preset gaya bersama"
  ```

### Tugas 5: Teks SFX Memakai Gaya Bersama

**Berkas:**
- Ubah: `app/src/main/java/com/grooxtyper/app/model/TextBox.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/model/SfxInk.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/model/TextRenderer.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/ui/InkSfxPanel.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `SfxStyleSpec`.
- Menghasilkan: `TextBox.sfxStyle: SfxStyleSpec?` (ikut `copy`, `setFrom`,
  `contentEquals`, dan JSON project dengan kunci `"sfxStyle"`), dan
  `TextRenderer.renderInkSfx` memakai gradasi, outline, bayangan, dan
  kemiringan per kata dari spec itu.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  ```js
  assert(textBoxSrc.includes('var sfxStyle: SfxStyleSpec?') && textBoxSrc.includes('put("sfxStyle"') && textBoxSrc.includes('optJSONObject("sfxStyle")'), 'teks: gaya SFX bersama ikut copy dan JSON project');
  assert(textRenderer.includes('box.sfxStyle?.tiltPerWord') || textRenderer.includes('tiltPerWord'), 'teks: kemiringan per kata dipakai renderer');
  assert(inkPanel.includes('Bawaan') && inkPanel.includes('SfxStyleSpec.presetOf('), 'panel teks: gaya SFX punya tombol Bawaan dari preset bersama');
  ```

  Jalankan gate - harus gagal.

- [ ] **Langkah 2: Tambahkan field dan JSON di `TextBox`**

  Tambahkan `sfxStyle` ke konstruktor, `copy`, `setFrom`, `contentEquals`, dan
  blok JSON tulis dan baca, mengikuti pola `inkSfx` yang sudah ada.

- [ ] **Langkah 3: Pakai spec di renderer**

  Di `renderInkSfx`, bila `box.sfxStyle` tidak null pakai gradasi, rasio
  outline, bayangan, dan kemiringan per kata dari spec; bila null, pakai nilai
  `SfxInkSpec` seperti sekarang agar tidak merusak teks yang sudah ada.

- [ ] **Langkah 4: Panel**

  Di `InkSfxPanel`, tambahkan tombol "Bawaan" yang mengisi `box.sfxStyle` dari
  `SfxStyleSpec.presetOf(...)` untuk genre yang dipilih.

- [ ] **Langkah 5: Gate dan commit**

  ```bash
  node scripts/brush-check.js
  git add app/src/main/java/com/grooxtyper/app/model/TextBox.kt app/src/main/java/com/grooxtyper/app/model/SfxInk.kt app/src/main/java/com/grooxtyper/app/model/TextRenderer.kt app/src/main/java/com/grooxtyper/app/ui/InkSfxPanel.kt scripts/brush-check.js
  git commit -m "Teks SFX memakai preset gaya bersama (gradasi, outline, bayangan, miring per kata)"
  ```

### Tugas 6: Algoritma Area Bubble

**Berkas:**
- Buat: `app/src/main/java/com/grooxtyper/app/model/BubbleAreaPipeline.kt`
- Buat: `scripts/wand-check.mjs`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `WandEngine.flood(px, w, h, seedX, seedY, params): Mask?`,
  `WandEngine.maskFromBinary`, `WandEngine.distanceTransform`,
  `WandEngine.labelComponents`, `WandEngine.splitBubbles(mask, ratio, minAreaRatio)`.
- Menghasilkan:

  ```kotlin
  object BubbleAreaPipeline {
      /** kind: 0 = area bubble, 1 = area panel (tombol Area Panel). */
      class Area(val bounds: RectF, val path: Path, val textColor: Int, val kind: Int)

      fun areaAt(
          px: IntArray, w: Int, h: Int, seedX: Int, seedY: Int,
          params: WandEngine.Params, outScale: Float,
          allowBorder: Boolean = false
      ): Area?

      fun areasFrom(px: IntArray, w: Int, h: Int, seeds: List<Int>,
          params: WandEngine.Params, outScale: Float): List<Area>
  }
  ```

  Semua koordinat hasil dalam koordinat kanvas penuh.

- [ ] **Langkah 1: Tulis uji numerik yang gagal**

  `scripts/wand-check.mjs` adalah spesifikasi eksekutable algoritma dengan
  geometri ground truth, terdiri dari lima kasus:

  | Kasus | Geometri | Harapan |
  |---|---|---|
  | satu gelembung | dua lingkaran jarring-jaring r=60 | 1 area, IoU dengan lingkaran >= 0.90 |
  | dua bersinggungan | dua lingkaran r=60, pusat berjarak 100 (saling tumpang tindih seperti `image.webp`) | tepat 2 area, tumpang tindih < 3% tiap area |
  | ketuk di luar | seed di kertas putih lepas | hasil null |
  | gelembung kecil | r=18 | 1 area, IoU >= 0.75 |
  | area panel | persegi 200x120, seed di tengah, `allowBorder=true` | 1 area, IoU >= 0.90 |

  Jalankan: `node scripts/wand-check.mjs` - harus gagal (implementasi belum ada).

- [ ] **Langkah 2: Tulis `BubbleAreaPipeline.kt`**

  Ikuti urutan di spec: flood, tolak bila komponen seed menyentuh tepi kecuali
  `allowBorder`, kontraksi dengan `dist >= r` dan pencarian biner `r` dari 0
  sampai `0.55 * maxDist` (null bila tidak ada `r` yang memisahkan), watershed
  bila masih lebih dari satu inti, tumbuhkan geodesik di dalam mask asli, lalu
  kontur pada ambang 50%.

  `textColor` dihitung sekali dari piksel median area: gelap berarti teks
  terang, dan sebaliknya (ambang 128 pada luminansi rata-rata).

- [ ] **Langkah 3: Jalankan uji dan gate**

  Jalankan: `node scripts/wand-check.mjs` (lima kasus lulus) lalu
  `node scripts/brush-check.js` - harus PASSED.

- [ ] **Langkah 4: Tambah asersi gate**

  ```js
  const areaPipe = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/BubbleAreaPipeline.kt'));
  assert(areaPipe.includes('fun areaAt(') && areaPipe.includes('fun areasFrom('), 'area bubble: API areaAt dan areasFrom ada');
  assert(areaPipe.includes('allowBorder: Boolean = false'), 'area bubble: mode panel harus opting-in eksplisit');
  assert(areaPipe.includes('0.55f') && areaPipe.includes('distanceTransform('), 'area bubble: kontraksi dibatasi 0.55 jarak maksimum');
  assert(areaPipe.includes('splitBubbles('), 'area bubble: watershed dipakai untuk gelembung bersinggungan');
  assert(areaPipe.includes('lo <= hi') || areaPipe.includes('while (lo <= hi)'), 'area bubble: pencarian biner radius kontraksi');
  ```

  Catatan: baris kelima memakai asersi nyata, bukan placeholder:
  `assert(areaPipe.includes('lo <= hi'), 'area bubble: pencarian biner radius kontraksi')`.

- [ ] **Langkah 5: Commit**

  ```bash
  git add app/src/main/java/com/grooxtyper/app/model/BubbleAreaPipeline.kt scripts/wand-check.mjs scripts/brush-check.js
  git commit -m "Pipeline area bubble: flood, kontraksi, watershed, tumbuhkan geodesik"
  ```

### Tugas 7: Multi-Area Bernomor di Editor

**Berkas:**
- Ubah: `app/src/main/java/com/grooxtyper/app/model/SelectionEngine.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `BubbleAreaPipeline.areaAt`.
- Menghasilkan: tiga fungsi baru di `SelectionEngine`:

  ```kotlin
  fun addBubbleArea(area: BubbleAreaPipeline.Area): Int   // mengembalikan nomor urut
  fun bubbleAreas(): List<BubbleAreaPipeline.Area>        // sudah urut baca manga
  fun removeBubbleAreaAt(x: Float, y: Float): Boolean
  fun clearBubbleAreas()
  ```

  `SelectionEngine` menyimpan daftar area sendiri; seleksi klasik tidak
  berubah, sehingga Oval, Rectangle, dan Lasso tidak terpengaruh.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  ```js
  assert(selEng.includes('fun addBubbleArea(') && selEng.includes('fun bubbleAreas()') && selEng.includes('fun removeBubbleAreaAt('), 'area bubble: multi-area dengan tambah dan hapus per ketukan');
  assert(selEng.includes('readingOrder') || selEng.includes('urutanArea'), 'area bubble: penomoran ikut urutan baca manga');
  assert(editor.includes('BubbleAreaPipeline.areaAt('), 'editor: ketukan wand memakai pipeline area bubble');
  assert(editor.includes('"tambah"') || editor.includes('wandAddMode'), 'editor: mode tambah area tersedia (multi tanpa menimpa)');
  ```

  Jalankan gate - harus gagal.

- [ ] **Langkah 2: Simpan area di `SelectionEngine`**

  Tambahkan daftar `MutableList<BubbleAreaPipeline.Area>` (Compose state
  `mutableStateOf(listOf())` supaya overlay ikut berubah) dan tiga fungsi di
  atas. Nomor urut dihitung dari posisi dalam daftar yang sudah diurutkan ulang
  dengan `readingOrder` (dipakai ulang dari `BubbleDetector.kt`).

- [ ] **Langkah 3: Sambungkan ketukan wand**

  Di `runWandAt`, bila mode bubble aktif, panggil `BubbleAreaPipeline.areaAt`
  dengan `allowBorder = modePanel` yang sedang aktif; hasilnya ditambahkan
  lewat `addBubbleArea`. Bila null, tampilkan pesan "bukan area bubble" tanpa
  mengubah dokumen.

- [ ] **Langkah 4: Gate dan commit**

  ```bash
  node scripts/brush-check.js
  git add app/src/main/java/com/grooxtyper/app/model/SelectionEngine.kt app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt scripts/brush-check.js
  git commit -m "Multi-area bubble bernomor urutan baca manga dari satu kanvas"
  ```

### Tugas 8: Panel Area Bubble dan Overlay

**Berkas:**
- Buat: `app/src/main/java/com/grooxtyper/app/ui/BubbleAreaPanel.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/ui/EditorOverlays.kt`
- Ubah: `app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `SelectionEngine.bubbleAreas()`, `addBubbleArea`,
  `removeBubbleAreaAt`, `clearBubbleAreas`.
- Menghasilkan:

  ```kotlin
  @Composable fun BubbleAreaPanel(
      areas: List<BubbleAreaPipeline.Area>,
      onClear: () -> Unit,
      onRemoveAt: (Float, Float) -> Unit,
      onTogglePanelMode: (Boolean) -> Unit,
      onFillFromScript: () -> Unit
  )
  ```

  Overlay menggambar nomor area di dalam kontur, dengan gaya berbeda untuk
  `kind = 1` (area panel) supaya user tahu mana yang bukan bubble.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  ```js
  const areaPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BubbleAreaPanel.kt'));
  assert(areaPanel.includes('fun BubbleAreaPanel(') && areaPanel.includes('onFillFromScript'), 'panel area: komposable terpisah dengan aksi isi script');
  assert(areaPanel.includes('"Area Panel"') && areaPanel.includes('onTogglePanelMode'), 'panel area: tombol Area Panel ada');
  assert(overlays.includes('bubbleAreas') || overlays.includes('area.nomor'), 'overlay: nomor area digambar di dalam kontur');
  ```

  Jalankan gate - harus gagal.

- [ ] **Langkah 2: Tulis `BubbleAreaPanel.kt`**

  Panel berisi: jumlah area, daftar bernomor (dengan cuplikan teks bila sudah
  terisi), tombol "Isi Area", tombol "Area Panel" (sakelar), tombol "Hapus
  Semua". Semua label memakai bahasa Indonesia.

- [ ] **Langkah 3: Overlay dan pemasangan**

  Di `EditorOverlays`, gambar nomor area memakai ukuran tetap terhadap skala
  layar supaya tetap terbaca saat zoom. Pasang panel di editor lewat
  `if (showBubbleAreaPanel)` dengan `align(Alignment.BottomCenter)`.

- [ ] **Langkah 4: Gate dan commit**

  ```bash
  node scripts/brush-check.js
  git add app/src/main/java/com/grooxtyper/app/ui/BubbleAreaPanel.kt app/src/main/java/com/grooxtyper/app/ui/EditorOverlays.kt app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt scripts/brush-check.js
  git commit -m "Panel dan overlay area bubble"
  ```

### Tugas 9: Isi Otomatis dari Script

**Berkas:**
- Ubah: `app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt`
- Ubah: `scripts/brush-check.js`

**Antarmuka:**
- Mengonsumsi: `SelectionEngine.bubbleAreas()`, `multiBubbleLines` yang sudah
  ada, `TextBox.BubbleSpec`, `TextBox.fitToRect`, `TextBox.bubbleLineWidths`.
- Menghasilkan: `fun fillAreasFromScript(): Int` yang mengembalikan jumlah area
  yang terisi, dipakai tombol "Isi Area" di `BubbleAreaPanel`.

- [ ] **Langkah 1: Tulis asersi gate yang gagal**

  ```js
  assert(editor.includes('fun fillAreasFromScript()'), 'editor: aksi isi area dari script ada');
  assert(editor.includes('areas.size > i') || editor.includes('ordered.take('), 'editor: baris script dipetakan ke area bernomor');
  assert(editor.includes('undoRedoManager.pushLayerAdd('), 'editor: isi area punya satu langkah undo');
  ```

  Jalankan gate - harus gagal.

- [ ] **Langkah 2: Implementasikan**

  Untuk setiap area bernomor yang mendapat baris script: buat `TextBox` dengan
  `BubbleSpec` sesuai bentuk area, posisi di pusat area, `boxWidth` dan
  `boxHeight` dari persegi terpanjang di dalam bentuk (pakai
  `bubbleInnerRectPx`), `autoFit = true`, warna teks dari `area.textColor`, lalu
  `fitToRect`. Tambahkan sebagai layer teks dengan satu langkah undo.

- [ ] **Langkah 3: Gate dan commit**

  ```bash
  node scripts/brush-check.js
  git add app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt scripts/brush-check.js
  git commit -m "Isi area bubble otomatis dari script dengan teks yang mengikuti kurva"
  ```

### Tugas 10: Rilis

**Berkas:**
- Ubah: `scripts/brush-check.js` (lengkapi asersiiggabungan bila ada)

- [ ] **Langkah 1: Gate penuh**

  Jalankan: `node scripts/brush-check.js` - harus PASSED.

- [ ] **Langkah 2: Uji numerik dan render**

  Jalankan: `node scripts/wand-check.mjs` dan
  `node scripts/sfx-render-test.mjs` - keduanya harus lulus.

- [ ] **Langkah 3: Push dan CI**

  ```bash
  git push "https://x-access-token:$(cat /tmp/opencode/.gh_token)@github.com/grooxyxy/GrooxTyper.git" main
  node /tmp/opencode/ci-watch.mjs <sha> 200 25
  ```

  Build harus hijau. Bila merah, ambil log job dan perbaiki berdasarkan pesan
  kompilator, bukan menebak.
