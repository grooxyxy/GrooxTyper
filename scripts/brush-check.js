const fs = require('fs');
const path = require('path');

function read(p) {
  return fs.readFileSync(p, 'utf8');
}
function assert(cond, msg) {
  if (!cond) {
    console.error('FAIL: ' + msg);
    process.exitCode = 1;
  } else {
    console.log('OK: ' + msg);
  }
}

const ROOT = path.resolve(__dirname, '..');
const brush = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/BrushEngine.kt'));
const patch = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/PatchMatchInpainter.kt'));
const seamless = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SeamlessBlender.kt'));
const inpaint = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/InpaintingManager.kt'));
const editor = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt'));
const imageImport = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ImageImport.kt'));
const projectManager = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ProjectManager.kt'));
const migan = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/MiganInpainter.kt'));
const textEditor = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/TextEditorPanel.kt'));
const colorPicker = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/ColorPicker.kt'));

for (const [name, text] of [['BrushEngine', brush], ['PatchMatch', patch], ['SeamlessBlender', seamless], ['InpaintingManager', inpaint], ['CanvasEditorScreen', editor], ['ImageImport', imageImport], ['ProjectManager', projectManager], ['MiganInpainter', migan], ['TextEditorPanel', textEditor], ['ColorPicker', colorPicker]]) {
  const open = (text.match(/{/g) || []).length;
  const close = (text.match(/}/g) || []).length;
  assert(open === close, name + ' braces balanced (' + open + '/' + close + ')');
}

// 1. Brush delay/crash fixes for 720x16000
assert(!brush.includes('stabilizer.isEnabled = false'), 'brush tidak mematikan stabilizer global per segmen');
assert(brush.includes('useStabilizer'), 'brush pakai flag stabilizer lokal (anti-recompose storm)');
assert(brush.includes('oilHighlight'), 'brush cache highlight OIL per segmen (anti-GC churn)');
assert(brush.includes('140_000L'), 'brush batasi luas blur live di huge canvas');
assert(brush.includes('OutOfMemoryError'), 'brush blur OOM-safe dengan finally recycle');
assert(brush.includes('maxSteps = if (isHuge) 32'), 'brush cap steps 32 di huge canvas');

// 2. Editor batching + mask OOM
assert(editor.includes('deferRefresh'), 'editor blit mendukung deferRefresh (batch recompose)');
assert(editor.includes('didBlit'), 'editor batch 1 recompose per touch event');
assert(editor.includes('maxInterp'), 'editor batasi interpolasi luar di huge canvas');
assert(editor.includes('recycleInpaintMask'), 'editor bebaskan mask 46MB setelah commit');
assert(editor.includes('wasHeal'), 'editor commit hapus-objek untuk INPAINT + OBJECT_ERASER');
assert(editor.includes('inpaintObjectDirty'), 'editor commit crop dirty saja (anti-46MB scan)');

// 3. Hapus Objek: Content-Aware Fill tunggal tanpa model tanpa opsi
assert(brush.includes('OBJECT_ERASER'), 'brush Hapus Objek terdaftar (HEAL_PATCH dibuang)');
assert(!brush.includes('HEAL_PATCH'), 'brush Heal Patch dihapus');
assert(!patch.includes('enum class HealMode'), 'HealMode enum dihapus (satu pipeline)');
assert(!patch.includes('HealMethod'), 'PatchMatch bebas HealMethod');
assert(patch.includes('SeamlessBlender'), 'jahitan diblend mulus via SeamlessBlender (gradasi+tekstur)');
assert(patch.includes('fillCropBitmap'), 'PatchMatch sediakan fillCropBitmap untuk commit crop');
assert(!editor.includes('HealMethod.values()'), 'pemilih opsi heal dihapus dari UI');
assert(inpaint.includes('inpaintObjectDirty'), 'InpaintingManager punya inpaintObjectDirty');
assert(!inpaint.includes('HealMethod') && !inpaint.includes('healMode'), 'InpaintingManager bebas opsi heal');
assert(!inpaint.includes('nativeInpaintNS'), 'Navier-Stokes native dihapus');
assert(!inpaint.includes('nativeGaussianBlurArea') && !inpaint.includes('nativeGuidedHealArea'), 'native Blur/Guided area dihapus');
assert(editor.includes('Inpaint Seleksi'), 'UI punya aksi Inpaint Seleksi');
assert(fs.existsSync(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/MiganInpainter.kt')), 'MiganInpainter.kt ada');
assert(!fs.existsSync(path.join(ROOT, 'app/src/main/assets/models/mg.onnx')), 'model mg.onnx tidak di-commit (bundle via CI)');
assert(inpaint.includes('nativeInpaintPyramid'), 'inpaint seleksi memakai pyramid push-pull native (mask besar)');

// 3b. Heal brush model MiGAN (download saat build CI, <50MB)
const miganSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/MiganInpainter.kt'));
const agnesSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/AgnesInpainter.kt'));
const workflow = read(path.join(ROOT, '.github/workflows/android.yml'));
const gitignore = read(path.join(ROOT, '.gitignore'));
assert(brush.includes('HEAL_MIGAN'), 'brush Heal MiGAN terdaftar');
assert(editor.includes('HEAL_MIGAN'), 'editor routing Heal MiGAN');
assert(workflow.includes('andraniksargsyan/migan/resolve/main/migan_pipeline_v2.onnx'), 'CI download MiGAN pipeline v2');
assert(workflow.includes('models/mg.onnx'), 'CI bundle mg.onnx ke assets');
assert(gitignore.includes('app/src/main/assets/models/mg.onnx'), 'mg.onnx tidak di-commit (bundle via CI)');
assert(inpaint.includes('inpaintMiganDirty'), 'InpaintingManager punya inpaintMiganDirty');
assert(miganSrc.includes('sanityOk'), 'MiGAN tolak halusinasi via sanity check piksel valid');
assert(inpaint.includes('compositeHoleOnly'), 'commit tempel khusus-lubang (valid tak tersentuh)');
assert(agnesSrc.includes('red-marked text'), 'prompt AI khusus hapus teks');

// 3c. AI Inpaint via Agnes AI (tanpa dependency baru, key di perangkat)
assert(fs.existsSync(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/AgnesInpainter.kt')), 'AgnesInpainter.kt ada');
assert(brush.includes('AI_INPAINT'), 'brush AI Inpaint terdaftar');
assert(editor.includes('strokeIsAi') && editor.includes('inpaintAiDirty'), 'editor routing AI Inpaint');
const manifest = read(path.join(ROOT, 'app/src/main/AndroidManifest.xml'));
assert(manifest.includes('android.permission.INTERNET'), 'manifest izin INTERNET untuk AI');
const srcFiles = ['app/src/main/java/com/grooxtyper/app/ml/AgnesInpainter.kt',
  'app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt',
  'app/src/main/java/com/grooxtyper/app/model/InpaintingManager.kt',
  '.github/workflows/android.yml'].map(p => read(path.join(ROOT, p)).includes('sk-'));
assert(!srcFiles.some(Boolean), 'tidak ada API key ter-commit di sumber');

// 4. Import gambar besar 720x16000 (adaptasi Vasilias FileManager/BitmapSafety)
assert(imageImport.includes('canvasPixelBudget'), 'import heap-aware via canvasPixelBudget (adaptasi BitmapSafety)');
assert(imageImport.includes('heapImportBudgetBytes'), 'import hitung budget byte heap (720x16000 lolos di HP normal)');
assert(imageImport.includes('effectivePixelBudget'), 'import pakai effectivePixelBudget (min absolut+heap)');
assert(imageImport.includes('BitmapRegionDecoder'), 'import pakai BitmapRegionDecoder tiled (adaptasi FileManager)');
assert(imageImport.includes('decodeTiledContent'), 'import punya jalur decodeTiledContent 1024px');
assert(imageImport.includes('decodeTiledFile'), 'import punya jalur decodeTiledFile untuk buka ulang project');
assert(imageImport.includes('TILE_DECODE_SIZE'), 'import tile 1024px untuk jahit berubin');
assert(imageImport.includes('FALLBACK_SAMPLE'), 'import fallback sample=8 saat OOM');
assert(imageImport.includes('decodeFileHeapAware'), 'import sediakan decodeFileHeapAware untuk project 720x16000');
assert(imageImport.includes('loadFallbackContent'), 'import fallback konservatif anti-crash');
assert(imageImport.includes('ExifInterface(path)'), 'import koreksi EXIF untuk file (foto tidak miring)');
assert(projectManager.includes('decodeFileHeapAware'), 'ProjectManager buka ulang via decodeFileHeapAware (anti-OOM 46MB)');

// 5. Brush di kanvas 720x16000 (adaptasi Vasilias viewport+culling+throttle)
assert(brush.includes('BrushHugeGuide'), 'brush punya BrushHugeGuide cara pakai 720x16000');
assert(brush.includes('HUGE_BRUSH_THROTTLE_MS'), 'brush throttle 16ms untuk huge canvas');
assert(brush.includes('visibleRect'), 'brush dukung viewport culling via visibleRect');
assert(brush.includes('BrushFrameThrottle'), 'brush punya BrushFrameThrottle anti-jank');
assert(editor.includes('BrushHugeGuide.visibleRect'), 'editor oper visibleRect ke brush saat huge');
assert(editor.includes('brushVisible'), 'editor hitung brushVisible sekali per event');

// 6. Penggaris (preview + gores) dan brush Effect (blend/blur/dodge/burn) benar-benar berfungsi
assert((brush.match(/applySmudgeStroke/g) || []).length >= 2, 'blend = smudge sejati (cap kanvas dari dab sebelumnya, bukan tint warna)');
assert(brush.includes('smudgeBuf') && brush.includes('smudgeHasInk'), 'smudge punya buffer cap + status ink');
assert(brush.includes('boxBlurBitmap') && brush.includes('boxBlurH') && brush.includes('boxBlurV'), 'blur = box blur 3 pass (piksel benar-benar diblur)');
assert(!brush.includes('blurred!!, 0f, 0f, blurPaint'), 'blur tidak lagi memakai BlurMaskFilter untuk bitmap (no-op di Skia)');
assert(brush.includes('BrushType.DODGE') && brush.includes('BrushType.BURN'), 'ada brush Dodge (Lighten) & Burn (Darken)');
assert(brush.includes('PorterDuffXfermode(PorterDuff.Mode.ADD)'), 'dodge memakai ADD (menambah cahaya)');
assert(brush.includes('PorterDuff.Mode.MULTIPLY'), 'burn/marker memakai MULTIPLY (menggelapkan)');
assert(brush.includes('rulerLocked') && brush.includes('shouldLock'), 'ruler: kunci per-stroke (gores mengikuti penggaris)');
assert(brush.includes('fun project('), 'ruler: proyeksi garis menerus (tidak dijepit ujung seperti versi lama)');
assert(brush.includes('placeInRect'), 'ruler: bisa ditaruh di area yang terlihat');
assert(brush.includes('rulerAdjustMode'), 'ruler: mode atur (geser) tanpa menggambar');
assert(editor.includes('canvasToScreenPos'), 'overlay penggaris/grid pivot-aware (preview tampil saat zoom)');
assert((textEditor.match(/onOpenPerspectiveGrid = onOpenPerspectiveGrid/g) || []).length >= 2, 'tombol grid perspektif terhubung di tab Efek (dulu no-op)');
assert(editor.includes('fun bilerp'), 'grid perspektif menggambar trapesium keystone yang sebenarnya');
assert(editor.includes('refreshCompositeCoalesced()') && editor.includes('perspGridMode = true'), 'grid perspektif aktif dari panel teks + preview live');

// 7. MiGAN (heal brush): tensor CHW planar + sanityOk longgarkan di tepi lubang.
assert(migan.includes('planar'), 'MiGAN isi tensor CHW planar sesuai deklarasi (1,3,h,w)');
assert(migan.includes('nearHole') && migan.includes('&& !nearHole(x, y)'), 'MiGAN sanityOk izinkan piksel ~3px dari tepi lubang');
// 8. Teks: slider ukuran min 1px + sinkron ukuran EFEKTIF (fontSize * scale).
assert(textEditor.includes('valueRange = 1f..400f'), 'slider ukuran teks min 1px (bukan 20)');
assert(!textEditor.includes('valueRange = 20f..220f'), 'slider ukuran lama 20f..220f sudah dibuang');
assert(textEditor.includes('box.fontSize * box.scale'), 'panel tampilkan ukuran efektif fontSize * scale');
assert(textEditor.includes('geomTick'), 'panel punya geomTick sinkronisasi dari kanvas');
assert(editor.includes('textGeomTick'), 'editor bump textGeomTick saat handle SCALE/lebar/perspektif');
assert(editor.includes('coerceIn(1f, 220f)'), 'TextBox dari region terdeteksi boleh 1px (bukan coerceIn 20f)');
// 9. Alur Bubble (Script): deteksi → tabel → pilih → INPAINT → isi naskah →
//    render; baris TETAP di tabel setelah inpaint (tak dihapus).
assert(editor.includes('fun runBubbleScriptDetect'), 'Bubble: fungsi deteksi teks (kolom script kosong)');
assert(editor.includes('fun inpaintBubbleRows'), 'Bubble: fungsi inpaint baris terpilih (mask deteksi-teks)');
assert(editor.includes('fun fillBubbleScripts'), 'Bubble: fungsi isi naskah + aturan baris lebih panjang');
assert(editor.includes('fun runBubbleScriptRender'), 'Bubble: render terpisah (Style Rules + fit tengah + size deteksi)');
assert(editor.includes('else runBubbleScriptRender()'), 'confirm dialog Script jalankan runBubbleScriptRender mode Bubble');
assert(editor.includes('bubbleWarn'), 'Bubble: peringatan naskah lebih panjang ditampilkan');
// 10. PatchMatch non-AI: prefill push-pull (gradasi/tekstur) ganti guide BFS.
assert(patch.includes('pushPullPrefill'), 'PatchMatch: prefill push-pull NON-AI (gradasi/tekstur)');
assert(!patch.includes('buildNearestBackgroundGuide'), 'PatchMatch: guide BFS-nearest diganti push-pull');
// 11. Script → Teks: TANPA deteksi teks — cukup samakan jumlah naskah dengan
//     jumlah bubble/seleksi; render via runScript (Style Rules + cek latar +
//     fit terbesar + center).
assert(editor.includes('TANPA deteksi teks'), 'Script Teks: runTextScript langsung render (tanpa deteksi)');
assert(!editor.includes('fun runScriptTextDetect'), 'Script Teks: fungsi deteksi teks dibuang');
assert(!editor.includes('fun pairScriptsToRows'), 'Script Teks: fungsi pasangkan dibuang');
assert(!editor.includes('runScriptTextDetect()'), 'Script Teks: tombol Deteksi Teks dibuang dari mode Teks');
assert(editor.includes('if (scriptTextMode) textMatchOk'), 'Script Teks: Jalankan aktif bila jumlah naskah sesuai target');
assert(editor.includes('unusedCount == textTargetN'), 'Script Teks: gate jumlah naskah == jumlah bubble');
assert(editor.includes('Target Render'), 'Script Teks: panel Target (bubble/seleksi) ganti tabel deteksi');
assert(editor.includes('Deteksi Bubble'), 'Script Teks: jalan pintas Deteksi Bubble tersedia');
// 12. Inpaint PatchMatch anti-buram: pecah mask per region connected +
//     blit HANYA piksel lubang (crop tak pernah ditimpa hasil resize).
assert(patch.includes('maskConnectedComponents'), 'PatchMatch: mask dipecah per region connected (bukan 1 crop raksasa)');
assert(patch.includes('fun inpaintRegion'), 'PatchMatch: tiap region diproses terpisah (resolusi penuh bila muat)');
assert(patch.includes('Blit HANYA piksel lubang'), 'PatchMatch: blit hanya piksel lubang (area valid tetap tajam)');
assert(!patch.includes('Canvas(src).drawBitmap(toBlit'), 'PatchMatch: drawBitmap atas seluruh crop (penyebab blur) dihapus');
// 13. Color picker: palet tersimpan (persisten) + eyedropper dari kanvas di
//     SEMUA dialog warna (brush & 6 target panel teks lewat ColorPickerDialog).
assert(colorPicker.includes('color_palette'), 'ColorPicker: palet warna tersimpan (SharedPreferences)');
assert(colorPicker.includes('Palet saya'), 'ColorPicker: section Palet saya (ketuk=pakai, tahan=hapus)');
assert(colorPicker.includes('onPickFromCanvas'), 'ColorPicker: tombol Pipet (eyedropper) di dialog');
assert(colorPicker.includes('color_palette') && colorPicker.includes('Colorize'), 'ColorPicker: ikon pipet tampil di title dialog');
assert(editor.includes('eyedropConsumer'), 'editor: mode pipet dari dialog warna (ketuk kanvas = sampel)');
assert(editor.includes('onPickFromCanvas = {'), 'editor: dialog warna brush terhubung ke mode pipet');
assert(textEditor.includes('onStartEyedrop'), 'panel teks: eyedropper terhubung ke tiap target warna');
// 14. SFX (dari video NOOB vs PRO): brush taper + outline ganda, mode SFX
//     per-huruf di busur dengan jitter deterministik, preset & seed di panel.
const textRenderer = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/TextRenderer.kt'));
const textBoxSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/TextBox.kt'));
for (const [name, text] of [['TextRenderer', textRenderer], ['TextBox', textBoxSrc]]) {
  const open = (text.match(/{/g) || []).length;
  const close = (text.match(/}/g) || []).length;
  assert(open === close, name + ' braces balanced (' + open + '/' + close + ')');
}
assert(brush.includes('SFX_LETTER') && brush.includes('SFX_TAPER') && brush.includes('SFX_OUTLINE'), 'brush SFX: 3 kuas (Lettering utama + Taper + Outline)');
// Rombakan sesuai video: kuas keras, lebar per-dab (taper awal, kecepatan,
// kontras arah turun/atas, lift), outline yang mengikuti lebar, dan ujung
// runcing di akhir stroke lewat buffer ekor.
assert(brush.includes('fun sfxDabWidth'), 'SFX: lebar dihitung per-dab (sfxDabWidth)');
assert(brush.includes('val contrast = 0.72f'), 'SFX: kontras arah — turun tebal, atas tipis ala kaligrafi video');
assert(brush.includes('val dir = 0.5f + 0.5f * (dy / len)'), 'SFX: arah goresan (dy) menentukan tebal/tipis');
assert(brush.includes('fun sfxLiftFactor'), 'SFX: jeda = kuas diangkat (sfxLiftFactor)');
assert(brush.includes('BrushType.SFX_LETTER -> Color.WHITE'), 'SFX Lettering: outline PUTIH seperti layer di-duplicate di video');
assert(brush.includes('private val sfxPending = ArrayList<Offset>'), 'SFX: buffer ekor untuk runcing akhir stroke');
assert(brush.includes('fun flushSfxTail'), 'SFX: ekor diruncingkan saat stroke selesai (flushSfxTail)');
assert(brush.includes('tailLayer?.let { syncTiles(it) }'), 'SFX: ekor masuk cache tile walau syncTiles dipanggil sebelum endStroke');
assert(brush.includes('fun discardSfxTail'), 'SFX: buang ekor saat stroke di-undo (tidak menggambar ulang di atas undo)');
assert(brush.includes('it.strokeWidth = paint.strokeWidth + sfxOutlineWidth'), 'SFX: outline mengikuti lebar goresan (bukan lebar tetap)');
assert(brush.includes('if (isSfxBrush()) size * 2f else 0f'), 'SFX: clip region dilebarkan agar ekor tidak terpotong');
assert(brush.includes('sfxSpeedFactor(distance)'), 'SFX: kecepatan dihitung per-segmen dari jarak event (bukan per dab)');
assert(textBoxSrc.includes('data class SfxSpec'), 'TextBox: SfxSpec (arc + jitter + seed) ada');
assert(textBoxSrc.includes('var sfx: SfxSpec?'), 'TextBox: field mode SFX');
assert(textBoxSrc.includes('put("sfx"') && textBoxSrc.includes('optJSONObject("sfx")'), 'TextBox: SfxSpec tersimpan di project JSON');
assert(textBoxSrc.includes('sfxPad'), 'TextBox: bounds/hit-test ikut busur SFX (sfxPad)');
assert(textRenderer.includes('renderSfx'), 'TextRenderer: render SFX per-huruf terpisah');
assert(textRenderer.includes('box.sfx != null'), 'TextRenderer: mode SFX cabang render sendiri');
assert(textRenderer.includes('fun h(i: Int, salt: Int)'), 'TextRenderer: jitter deterministik dari seed (render ulang identik)');
assert(textEditor.includes('fun SfxSection'), 'panel teks: bagian Mode SFX');
const sfxPresets = ['"Naik"', '"Turun"', '"Lurus"', '"Acak Pro"', '"WHOOSH"', '"Zigzag"', '"Ledakan"', '"Miring"'];
assert(sfxPresets.every(p => textEditor.includes(p)), 'panel teks: 8 preset SFX (Naik/Turun/Lurus/Acak Pro/WHOOSH/Zigzag/Ledakan/Miring)');
assert(textBoxSrc.includes('var tilt: Float = 0f') && textBoxSrc.includes('var wave: Float = 0f'), 'TextBox: SfxSpec punya tilt (miring kata) + wave (gelombang)');
assert(textRenderer.includes('spec.tilt') && textRenderer.includes('spec.wave'), 'TextRenderer: tilt + wave ikut dirender per huruf');
assert(textEditor.includes('Acak Ulang') && textEditor.includes('"Reset"'), 'panel teks: tombol Acak Ulang + Reset SFX');
assert(textEditor.includes('box.sfx = newSpec'), 'panel teks: ubah SFX langsung menerapkan ke box + push undo');

// 15. Grid perspektif: dulu "terbuka tapi tidak bisa disentuh". Tiga sebab:
//     (a) loop gesture utama consume event down sebelum detectDragGestures
//         sempat menunggu down yang belum consumed, (b) hit-test memakai
//         sudut kotak lurus sedangkan overlay menggambar titik keystone,
//     (c) quick slider full-width (.clickable noop) menutupi handle bawah.
const gridBlock = editor.slice(editor.indexOf('if (rulerAdjustMode || (perspGridMode && selectedTextBox != null))'));
const gridBranch = gridBlock.slice(0, gridBlock.indexOf('} else {'));
assert(!gridBranch.includes('change.consume()'), 'grid perspektif: branch mode overlay TIDAK consume (detectDragGestures butuh down belum consumed)');
assert(editor.includes('val dxT = pbox.perspX.coerceIn(-1f, 1f) * hw') && editor.includes('val dyL = pbox.perspY.coerceIn(-1f, 1f) * hh'), 'grid perspektif: hit-test handle memakai titik keystone yang sama dengan overlay');
assert(editor.includes('val grab = 84f / sc') && editor.includes('inside && near(best, dTop) -> 1'), 'grid perspektif: radius genggam lega + fallback handle terdekat di dalam grid');
assert(editor.includes('fun near(d: Float, ref: Float) = abs(d - ref) < 0.01f'), 'grid perspektif: pilih handle pakai toleransi (bukan == float yang bisa salah pilih)');
assert(editor.includes('showQuickSlider && !perspGridMode'), 'grid perspektif: quick slider (.clickable noop) disembunyikan saat mode grid');
assert(editor.includes('undoRedoManager.pushTextBox(\n                                            textLayerIdOf(pbox)'), 'grid perspektif: satu langkah undo per gestur seret');
assert(editor.includes('Seret titik biru untuk ubah sudut'), 'grid perspektif: petunjuk cara pakai saat mode aktif');

// 16. Add image / watermark: sumber WAJIB utuh (tidak di-raster ke ukuran
//     dialog), panel properti terpisah dari seleksi (kalau tidak, dialog
//     memakan sentuhan → image tak bisa digeser), opacity 0-100%, handle dari
//     sudut hasil rotasi, render coalesced, dan pemilihan layer teratas benar.
const layerSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/Layer.kt'));
const imageImportSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ImageImport.kt'));
for (const [name, text] of [['Layer', layerSrc], ['ImageImport', imageImportSrc], ['ReferenceWindow', read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/ReferenceWindow.kt'))]]) {
  const open = (text.match(/{/g) || []).length;
  const close = (text.match(/}/g) || []).length;
  assert(open === close, name + ' braces balanced (' + open + '/' + close + ')');
}
assert(!/ImageImport\.scaleTo\(src, w, h\)/.test(editor), 'add image: sumber TIDAK di-raster ke ukuran dialog (sebelumnya membuat gambar pecah saat zoom)');
assert(imageImportSrc.includes('fun capLayerSource'), 'add image: sumber dibatasi 12MP/8192px saja (720x16000 tetap utuh)');
assert(editor.includes('ImageImport.capLayerSource(raw)'), 'add image: sumber dibatasi sharpness-aware sebelum jadi layer');
assert(layerSrc.includes('opacityInit: Float = 1f') && layerSrc.includes('layer.opacity = opacityInit.coerceIn(0f, 1f)'), 'add image: opacity layer boleh 0-100% (bukan dijepit minimal 10%)');
assert(editor.includes('if (showImageProps) selectedImage()?.let { img ->') && editor.includes('if (activeTool == ActiveTool.IMAGE && selectedImage() != null)'), 'add image: panel properti terpisah dari seleksi (tidak memblokir drag kanvas)');
assert(editor.includes('fun oppositeCorner(') && editor.includes('imageAnchorPoint'), 'add image: skala beranchor di sudut lawannya (tidak meluncur)');
assert(editor.includes('img.cornerPoints()') && editor.includes('val cp = img.cornerPoints()'), 'add image: handle + bingkai ikut rotasi (cornerPoints), bukan AABB');
assert(editor.includes('.findFirst { it.hitTest('), 'add image: image yang diketuk = layer teratas (findFirst, bukan findLast)');
assert((editor.match(/refreshCompositeCoalesced\(\)/g) || []).length >= 10, 'add image: gestre & slider memakai render coalesced (realtime di kanvas 720x16000)');
assert(editor.includes('Icons.Default.Preview'), 'add image: tombol Reference Window ada di top bar');
assert(editor.includes('activeTool = ActiveTool.IMAGE') && editor.includes('showImageProps = true'), 'add image: layer baru langsung aktif + tool IMAGE + panel properti terbuka');

// 17. Jendela Reference (ibisPaint/Clip Studio): zoom luas, pan, render
//     per-strip agar 720x16000 tidak gagal texture, dan budget byte <=2MB.
const refWin = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/ReferenceWindow.kt'));
assert(refWin.includes('coerceIn(0.002f, 40f)'), 'reference: zoom 0.2%-4000% (bisa zoom keluar untuk halaman 16000px)');
assert(refWin.includes('fun contentSize(') && refWin.includes('fun draw('), 'reference:_contentSize + draw per-strip (scroll + clamp pan)');
assert(refWin.includes('const val STRIP = 2048') && refWin.includes('BitmapRegionDecoder') === false, 'reference: strip 2048px per draw (aman batas texture GPU)');
assert(refWin.includes('pixelPerfect'), 'reference: mode piksel (nearest) untuk garis detail');
assert(refWin.includes('onPickImage') && editor.includes('referencePickerLauncher.launch'), 'reference: tombol ganti gambar dari dalam jendela');
assert(imageImportSrc.includes('fun decodeForReference') && imageImportSrc.includes('REFERENCE_TARGET_BYTES = 2_000_000L'), 'reference: decode dengan budget 2MB (file 4MB diturunkan, tetap jernih)');
assert(imageImportSrc.includes('estimateJpegBytes') && imageImportSrc.includes('bmp.compress'), 'reference: hasil diverifikasi kompresi JPEG nyata, bukan asal perkiraan');
assert(editor.includes('loadReferenceBitmap') && editor.includes('showReferenceWindow'), 'reference: jendela punya status buka/muat/error sendiri (tidak auto-open saat add image)');

// 18. Persistensi image layer (watermark tidak boleh "bakar" jadi piksel).
//     Termasuk jebakan compile yang pernah menggagalkan CI: nativeCanvas adalah
//     EXTENSION PROPERTY dan wajib di-import di file yang memakainya.
const imgStore = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ImageLayerStore.kt'));
{
  const o = (imgStore.match(/{/g) || []).length;
  const c = (imgStore.match(/}/g) || []).length;
  assert(o === c, 'ImageLayerStore braces balanced (' + o + '/' + c + ')');
}
assert(refWin.includes('import androidx.compose.ui.graphics.nativeCanvas'), 'reference: import nativeCanvas ada (extension property, tanpa ini build gagal)');
assert(imgStore.includes('fun save(') && imgStore.includes('fun parse(') && imgStore.includes('fun buildLayer('), 'image layer: simpan/parse/build utuh (layer editable, bukan bake piksel)');
assert(editor.includes('ImageLayerStore.save(layerManager') && editor.includes('ImageLayerStore.parse(raw)'), 'editor: save & restore image layer terhubung');
assert(editor.includes('renderDrawingOnly(base, includeImage = imagesJson == null)'), 'editor: image tidak dobel (tidak dibake bila sudah jadi layer)');
assert(editor.includes('NonCancellable + Dispatchers.IO'), 'editor: save-on-exit tidak dibatalkan saat activity ditutup');
assert(editor.includes('projectManager.saveImages') && editor.includes('projectManager.loadImages'), 'editor: metadata image disimpan & dimuat via ProjectManager');
assert(editor.includes('loaded.asReversed()'), 'editor: urutan layer pulih benar (atas-dulu)');
assert(editor.includes('ImageImport.decodeFileHeapAware(file.absolutePath)'), 'editor: decode aset image heap-aware (aman 720x16000)');

if (process.exitCode) {
  console.error('brush-check FAILED');
} else {
  console.log('brush-check PASSED: brush anti-delay/crash + hapus objek siap + import 720x16000 heap-aware + ruler/blend/blur sesuai namanya');
}
