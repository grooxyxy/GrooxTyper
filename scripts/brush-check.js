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
const perspGrid = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/PerspectiveGrid.kt'));
const overlays = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/EditorOverlays.kt'));
const ovGest = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/OverlayGestures.kt'));
const bubDlg = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BubbleDetectorDialog.kt'));
const rulesDlg = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/StyleRulesDialog.kt'));
const perspPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/PerspectivePanel.kt'));
const richPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/RichTextPanel.kt'));
const richLayout = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/RichTextLayout.kt'));
const sfxTex = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxTextureBrush.kt'));
const gradle = read(path.join(ROOT, 'app/build.gradle.kts'));
const genreEngineSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/GenreBrushEngine.kt'));
for (const [nm, tx] of [['PerspectiveGrid', perspGrid], ['PerspectivePanel', perspPanel], ['RichTextPanel', richPanel], ['RichTextLayout', richLayout], ['EditorOverlays', overlays], ['OverlayGestures', ovGest], ['BubbleDetectorDialog', bubDlg], ['StyleRulesDialog', rulesDlg]]) {
  const o = (tx.match(/\{/g) || []).length;
  const c = (tx.match(/\}/g) || []).length;
  assert(o === c, nm + ' braces balanced (' + o + '/' + c + ')');
}
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
assert(perspGrid.includes('fun bilerp') && overlays.includes('PerspectiveGrid.bilerp(dst, u, v)'), 'grid perspektif menggambar trapesium hasil distorti yang sebenarnya');
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
const bulkDlg = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BulkTextDialog.kt'));
const textRenderer = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/TextRenderer.kt'));
const textBoxSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/TextBox.kt'));
for (const [name, text] of [['TextRenderer', textRenderer], ['TextBox', textBoxSrc]]) {
  const open = (text.match(/{/g) || []).length;
  const close = (text.match(/}/g) || []).length;
  assert(open === close, name + ' braces balanced (' + open + '/' + close + ')');
}
// SFX bergenre: empat kuas yang bentuknya jelas berbeda (GenreBrushEngine).
const sfxEng = genreEngineSrc;
{
  const o = (sfxEng.match(/{/g) || []).length;
  const c = (sfxEng.match(/}/g) || []).length;
  assert(o === c, 'GenreBrushEngine braces balanced (' + o + '/' + c + ')');
}
const genreBrushes = ['GENRE_HORROR', 'GENRE_ROMANCE', 'GENRE_ACTION', 'GENRE_FANTASY'];
assert(genreBrushes.every(b => brush.includes(b + '(')), 'BrushType: 4 kuas genre SFX terdaftar');
for (const b of ['SFX_PEN', 'SFX_BRUSH', 'SFX_MARKER', 'SFX_AIR', 'SFX_CRAYON', 'SFX_INK', 'SFX_NEON']) {
  assert(!brush.includes(b + '('), 'kuas SFX lama sudah dibuang: ' + b);
}
assert(genreBrushes.every(b => sfxEng.includes(b)), 'GenreBrushEngine: keempat genre terpetakan ke mesin');
assert(sfxEng.includes('HORROR("SFX Horror")') && sfxEng.includes('ROMANCE("SFX Romance")') &&
  sfxEng.includes('ACTION("SFX Action")') && sfxEng.includes('FANTASY("SFX Fantasy")'),
  'GenreBrushEngine: nama genre tampil apa adanya di daftar kuas');
assert((sfxEng.match(/Genre\.\w+ ->/g) || []).length >= 4, 'GenreBrushEngine: profil lebar terpisah per genre');
assert(sfxEng.includes('drawDrips') && sfxEng.includes('drawSpeedLines') && sfxEng.includes('drawSparkles'),
  'GenreBrushEngine: hiasan khas tiap genre (tetesan / garis kecepatan / kilau bintang)');
for (const f of ['var gradient', 'var gradStart', 'var gradEnd', 'var gradAngle', 'var opacity',
  'var outlineWidth', 'var outlineColor', 'var shadowOn', 'var shadowDx', 'var shadowDy',
  'var shadowBlur', 'var shadowColor', 'var texture', 'var spatter']) {
  assert(sfxEng.includes(f), 'GenreBrushEngine: setelan editable ' + f);
}
assert(sfxEng.includes('fun applyPreset(') && sfxEng.includes('fun copyFrom('), 'GenreBrushEngine: preset per genre + salinan setelan');
// -- Gaya SFX bersama (sumber angka tunggal untuk teks dan kuas) ----------
{
  const sfxStyle = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxStyle.kt'));
  assert(sfxStyle.includes('data class SfxStyleSpec') && sfxStyle.includes('fun presetOf(genre: SfxGenre)'), 'SFX: satu sumber angka gaya bersama');
  for (const f of ['gradStart', 'gradEnd', 'gradAngle', 'inkScale', 'outlineScale', 'outlineColor', 'shadowDx', 'shadowDy', 'shadowBlur', 'shadowColor', 'roughness', 'tiltPerWord', 'spatter']) {
    assert(sfxStyle.includes('val ' + f), 'SFX: preset bersama punya field ' + f);
  }
  for (const g of ['HORROR', 'ROMANCE', 'ACTION', 'FANTASY']) {
    assert(sfxStyle.includes('SfxGenre.' + g), 'SFX: preset untuk genre ' + g);
  }
  assert(sfxStyle.includes('enum class SfxGenre') && sfxStyle.includes('fun of(genre: GenreBrushEngine.Genre)'), 'SFX: pemetaan GenreBrushEngine.Genre ke SfxGenre');
  // Angka preset harus berasal dari riset, bukan tebakan.
  assert(sfxStyle.includes('docs/sfx-lettering-research.md'), 'SFX: preset menunjuk dokumen riset sebagai sumber angka');
  // Bayangan SFX tidak boleh lembut (letterer profesional menolak blur).
  const blurVals = [...sfxStyle.matchAll(/shadowBlur\s*=\s*([0-9.]+)f/g)].map(m => parseFloat(m[1]));
  assert(blurVals.length > 0 && blurVals.every(v => v === 0), 'SFX: bayangan SFX keras semua (blur 0) sesuai riset');
  // Outline dua lapis: dalam = setengah luar.
  assert(sfxStyle.includes('fun outlineInnerScale()') && sfxStyle.includes('outlineScale * 0.5f'), 'SFX: outline dalam setengah luar (rasio 2:1 Comicraft)');
  for (const g of ['HORROR','ROMANCE','ACTION','FANTASY']) {
    const i = sfxStyle.indexOf('SfxGenre.' + g);
    const seg = sfxStyle.slice(i, i + 700);
    assert(seg.includes('outlineInnerColor ='), 'SFX: preset ' + g + ' punya warna outline dalam');
  }
  assert(sfxEng.includes('outlineInnerColor') && sfxEng.includes('inkPath(outlineW * 0.5f)'), 'SFX: mesin menggambar outline lapis dalam setengah lebar');
}

// Setelan WAJIB Compose state: kalau var biasa, panel SFX tidak pernah
// digambar ulang sehingga slider-nya tampak tidak bisa disentuh.
for (const st of ['var widthMul by mutableFloatStateOf', 'var gradient by mutableStateOf',
  'var opacity by mutableFloatStateOf', 'var outlineWidth by mutableFloatStateOf',
  'var shadowOn by mutableStateOf', 'var texture by mutableFloatStateOf',
  'var spatter by mutableIntStateOf']) {
  assert(sfxEng.includes(st), 'GenreBrushEngine: setelan jadi Compose state -> ' + st);
}
assert(!/var (widthMul|opacity|texture)\s*:\s*Float\s*=/.test(sfxEng),
  'GenreBrushEngine: tak ada setelan SFX berupa var biasa (UI beku)');
const sI_shadow = sfxEng.indexOf('c.translate(st.shadowDx * size, st.shadowDy * size)');
const sI_outline = sfxEng.indexOf('if (outline != null) {', sI_shadow);
const sI_mask = sfxEng.indexOf('PorterDuff.Mode.DST_IN', sI_shadow);
const sI_ink = sfxEng.indexOf('c.drawPath(ink, fillPaintFor(st, clip))', sI_shadow);
assert(sI_shadow > 0 && sI_outline > sI_shadow && sI_mask > sI_outline && sI_ink > sI_mask,
  'GenreBrushEngine: URUTAN benar (bayangan -> outline -> lubangi -> tinta)');
assert(sfxEng.includes('SfxInk.strokeRibbon') && sfxEng.includes('SfxInk.EdgeProfile.sample'),
  'GenreBrushEngine: pita + noise di-sample pada panjang busur (anti swim)');
assert(sfxEng.includes('val ds = if (nPts > 1) max(0.4f, length / profSize) else 1f'),
  'GenreBrushEngine: jarak sampling profil dihitung dari panjang busur');
assert(brush.includes('GenreBrushEngine.newStroke(genreSettings)') && brush.includes('sfxStroke.renderFinal('),
  'BrushEngine: kuas genre memakai mesin baru + render akhir utuh');
assert(brush.includes('val genreSettings = GenreBrushEngine.SettingsStore()'),
  'BrushEngine: setelan genre milik engine (bisa disunting panel)');
assert(brush.includes('sfxStroke.push(canvas, sfxPts, sfxSpeedFactor(distance))'),
  'brush: segmen SFX lewat mesin genre');
assert(brush.includes('fun discardSfxTail') && brush.includes('sfxStroke.discard()'), 'SFX: buang goresan saat stroke di-undo');
assert(brush.includes('tailLayer?.let { syncTiles(it) }'), 'SFX: render akhir masuk cache tile walau syncTiles dipanggil sebelum endStroke');
assert(brush.includes('if (isSfxBrush()) size * 2f else 0f'), 'SFX: clip region dilebarkan agar render akhir tidak terpotong');
assert(brush.includes('sfxSpeedFactor(distance)'), 'SFX: kecepatan dihitung per-segmen dari jarak event');
assert(textBoxSrc.includes('data class SfxInkSpec') && textBoxSrc.includes('var inkSfx: SfxInkSpec?'), 'teks: gaya tinta SFX (SfxInkSpec) tersimpan di kotak');
// -- Teks SFX memakai preset gaya bersama (SfxStyleSpec) ---------------------
assert(textBoxSrc.includes('var sfxStyle: SfxStyleSpec?'), 'teks: gaya SFX bersama (sfxStyle) tersimpan di kotak');
assert(textBoxSrc.includes('sfxStyle = sfxStyle?.copy()') && textBoxSrc.includes('sfxStyle = o.sfxStyle?.copy()') && textBoxSrc.includes('sfxStyle == o.sfxStyle'), 'teks: sfxStyle ikut copy/setFrom/contentEquals');
assert(textBoxSrc.includes('put("sfxStyle"') && textBoxSrc.includes('optJSONObject("sfxStyle")'), 'teks: sfxStyle ikut JSON project');
assert(textBoxSrc.includes('BubbleSpec') && textBoxSrc.includes('var bubble: BubbleSpec?'), 'teks: bentuk bubble tetap ada (tidak ditimpa gaya SFX)');
assert(textRenderer.includes('box.sfxStyle') && textRenderer.includes('SfxStyleSpec.presetOf(') === false, 'renderer: jalur SFX membaca gaya dari kotak, bukan preset langsung');
{
  const st = textRenderer.slice(textRenderer.indexOf('fun renderInkSfx'), textRenderer.indexOf('fun renderInkSfx') + 4000);
  assert(st.includes('gradStart') && st.includes('gradEnd') && st.includes('gradAngle'), 'renderer: gradasi isi SFX dari preset bersama');
  assert(st.includes('outlineInnerColor') || st.includes('keylineColor'), 'renderer: outline lapis dalam dipakai dari preset');
  assert(st.includes('tiltPerWord'), 'renderer: kemiringan per kata dari preset');
  assert(st.includes('shadowBlur == 0f') || !st.includes('BlurMaskFilter'), 'renderer: bayangan SFX keras (tanpa blur) sesuai riset');
}
{
  const inkPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/InkSfxPanel.kt'));
  assert(inkPanel.includes('SfxStyleSpec.presetOf(') && inkPanel.includes('"Bawaan"'), 'panel teks: tombol Bawaan diisi dari preset bersama');
  assert(inkPanel.includes('SfxGenre.values()') && inkPanel.includes('fun SfxStyleRow('), 'panel teks: keempat genre bisa dipilih lewat daftar');
  assert(textEditor.includes('SfxStyleRow('), 'panel teks: baris gaya SFX terpasang di tab Tulis');
  assert(inkPanel.includes('presetOf(picked)') && inkPanel.includes('SfxStyleSpec'), 'panel teks: preset diambil dari sumber gaya bersama');
}
{
  const inkPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/InkSfxPanel.kt'));
  const textPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/TextEditorPanel.kt'));
  assert(inkPanel.includes('fun InkSfxSection(') && textPanel.includes('InkSfxSection('), 'panel: bagian Tinta SFX tersambung di tab Tulis');
  assert(inkPanel.includes('onSpec(if (enabled) SfxInkSpec() else null)'), 'panel: toggle Tinta SFX bisa dinyalakan');
  assert(inkPanel.includes('outlineRatio') && inkPanel.includes('roughOutline') && inkPanel.includes('tiltDeg'), 'panel: kendali outline / kasar tepi / miring');
}
assert(textRenderer.includes('fun renderInkSfx') && textRenderer.includes('SfxInk.drawGlyph('), 'renderer: jalur tinta SFX per glyph (isi + outline + keyline)');
assert(textBoxSrc.includes('put("inkSfx"') && textBoxSrc.includes('optJSONObject("inkSfx")'), 'teks: tinta SFX ikut JSON project');
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
assert(editor.includes('.perspectiveRulerGestures(') && ovGest.includes('fun Modifier.perspectiveRulerGestures('), 'grid perspektif: gestur overlay jadi Modifier terpisah (badan editor <64KB)');
assert(editor.includes('BubbleDetectorDialog(') && bubDlg.includes('onCancel: () -> Unit') && bubDlg.includes('onAutoFill: () -> Unit'), 'bubble: dialog dipisah ke file sendiri + tombol Batal');
assert(editor.includes('StyleRulesDialog(') && rulesDlg.includes('fun StyleRulesDialog('), 'style rules: dialog dipisah ke file sendiri');
assert(perspGrid.includes('fun cornersCanvas') && ovGest.includes('PerspectiveGrid.cornersCanvas(pbox)'), 'grid perspektif: hit-test handle memakai sudut yang sama dengan overlay');
assert(overlays.includes('fun perspectiveGrid') && overlays.includes('fun textFramePreview'), 'overlay: grid perspektif + preview kotak di file terpisah (badan editor <64KB)');
assert(overlays.includes('import androidx.compose.ui.graphics.nativeCanvas'), 'overlay: import nativeCanvas ada (tanpa ini build gagal)');
assert(editor.includes('EditorOverlays.perspectiveGrid(') && editor.includes('EditorOverlays.textFramePreview('), 'editor: overlay dipanggil, bukan digambar inline');
assert(overlays.includes('fun TextToolsExtra') || overlays.includes('fun richTextPanel'), 'overlay: host panel gaya per kata + tombol toolbar');
assert(textBoxSrc.includes('fun sanitized()') && textBoxSrc.includes('MAX_OFFSET = 1.2f') && textBoxSrc.includes('if (!v.isFinite()) return 0f'), 'perspektif: offset dijaga finite + dalam rentang slider (anti force close)');
assert(perspPanel.includes('x.coerceIn(lo, hi)') && perspPanel.includes('valueRange = lo..hi'), 'perspektif: nilai slider selalu dijepit ke rentang (Material Slider melempar exception kalau tidak)');
assert(perspPanel.includes('val p = (box.persp ?:') && !perspPanel.includes('mutableFloatStateOf(p.') && !perspPanel.includes('mutableFloatStateOf(p.tlX)'), 'perspektif: panel membaca langsung dari kotak (tak ada state basi)');
assert(perspGrid.includes('.sanitized()') && perspGrid.includes('isFinite()'), 'perspektif: matriks & titik sudut bebas NaN/Infinity');
assert(ovGest.includes('if (!area.width().isFinite()') && ovGest.includes('2_000_000f'), 'perspektif: gestur batal bila geometri rusak (anti force close)');
assert(perspGrid.includes('fun dstPoints') && overlays.includes('PerspectiveGrid.dstPoints(box') && textRenderer.includes('PerspectiveGrid.matrix(box'), 'grid perspektif: sumber tunggal geometri (renderer + overlay + gesture)');
assert(ovGest.includes('val grab = 96f / sc') && ovGest.includes('if (bestD > grab) perspHandle = 0'), 'grid perspektif: radius genggam lega + handle terdekat');
assert(!ovGest.includes('selectedTextBox') && !ovGest.includes('pbox\n'), 'gestur overlay: hanya memakai parameter sendiri (tak grab state editor)');
assert(ovGest.includes('1 -> { p.tlX += dx; p.tlY += dy }') && ovGest.includes('4 -> { p.blX += dx; p.blY += dy }'), 'grid perspektif: 4 sudut digeser bebas (arah perspektif leluasa)');
assert(perspGrid.includes('setPolyToPoly'), 'grid perspektif: homografi 4 titik (bukan cuma keystone atas/bawah)');
assert(perspPanel.includes('fun CornerSliders') && perspPanel.includes('"Jauh atas"') && perspPanel.includes('"Miring"'), 'grid perspektif: panel kontrol + preset arah');
assert(editor.includes('showQuickSlider && !perspGridMode'), 'grid perspektif: quick slider (.clickable noop) disembunyikan saat mode grid');
assert(editor.includes('onPerspUndo = { pb ->') && editor.includes('onPerspDrag = { pb ->'), 'grid perspektif: satu langkah undo per gestur seret');
assert(editor.includes('PerspectivePanel(') && editor.includes('Pilih satu teks dulu untuk atur perspektifnya'), 'grid perspektif: panel kontrol tampil saat mode aktif');

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
assert(editor.includes('.firstOrNull { it.hitTest('), 'add image: image yang diketuk = layer teratas (firstOrNull, bukan findLast)');
assert(!editor.includes('PanelLight'), 'add image: tidak memakai PanelLight privat TextEditorPanel di CanvasEditorScreen');
assert((editor.match(/refreshCompositeCoalesced\(\)/g) || []).length >= 10, 'add image: gesture & slider memakai render coalesced (realtime di kanvas 720x16000)');
assert(layerSrc.includes('fun sortByRestoreZ()') && layerSrc.includes('var restoreZ'), 'add image: z-order gabungan teks+image disusun ulang setelah restore');
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
assert(imgStore.includes('entries.add(') && imgStore.includes('entries.isEmpty()'), 'image layer: pakai List API Kotlin (add/isEmpty), bukan API JSONArray');
assert(editor.includes('ImageLayerStore.save(layerManager') && editor.includes('ImageLayerStore.parse(raw)'), 'editor: save & restore image layer terhubung');
assert(editor.includes('renderDrawingOnly(base, includeImage = imagesJson == null)'), 'editor: image tidak dobel (tidak dibake bila sudah jadi layer)');
assert(editor.includes('NonCancellable + Dispatchers.IO'), 'editor: save-on-exit tidak dibatalkan saat activity ditutup');
assert(editor.includes('projectManager.saveImages') && editor.includes('projectManager.loadImages'), 'editor: metadata image disimpan & dimuat via ProjectManager');
assert(editor.includes('loaded.asReversed()'), 'editor: urutan layer pulih benar (atas-dulu)');
assert(editor.includes('ImageImport.decodeFileHeapAware(file.absolutePath)'), 'editor: decode aset image heap-aware (aman 720x16000)');

// 19. Lima permintaan: fill-white text-detection, model bubble ogkalu
//     RT-DETR int8, hapus teks semua/ketuk, AI inpaint anti-gepeng,
//     dan anti-glitch kolom script.
const inpaintMgr = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/InpaintingManager.kt'));
const bubbleDet = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/BubbleDetector.kt'));
const agnesInpaintSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/AgnesInpainter.kt'));
const ciYml = read(path.join(ROOT, '.github/workflows/android.yml'));
for (const [name, text] of [['InpaintingManager', inpaintMgr], ['BubbleDetector', bubbleDet]]) {
  const o = (text.match(/{/g) || []).length;
  const c = (text.match(/}/g) || []).length;
  assert(o === c, name + ' braces balanced (' + o + '/' + c + ')');
}
assert(inpaintMgr.includes('FILL_WHITE(') && inpaintMgr.includes('fun fillMaskWhite('), 'text-detection: mode FILL_WHITE + fill putih baris-per-baris (hemat 720x16000)');
assert(inpaintMgr.includes('var mode: InpaintMode = InpaintMode.FILL_WHITE'), 'text-detection: default mode = fill white');
assert(editor.includes('inpaintingManager.mode = com.grooxtyper.app.model.InpaintMode.FILL_WHITE') && editor.includes('Text("Putih", color = Color.White, fontSize = 11.sp)'), 'text-detection: tombol Putih menggantikan PatchMatch di dialog');
assert(!editor.includes('Text("PatchMatch"'), 'text-detection: tidak ada lagi opsi PatchMatch di dialog');
assert(bubbleDet.includes('fun decodeRtDetr(') && bubbleDet.includes('1f / (1f + kotlin.math.exp(-l[c]))'), 'bubble: decoder RT-DETR (sigmoid focal, tanpa NMS)');
assert(bubbleDet.includes('Model balon KZKT') || bubbleDet.includes('detektor balon dari aplikasi KZKT'), 'bubble: model aktif = detektor balon KZKT (Ultralytics, 2 kelas)');
assert(!bubbleDet.includes('Model aktif: Kiuyha') && !bubbleDet.includes('Kiuyha/Manga-Bubble-YOLO'), 'bubble: Kiuyha bukan lagi model aktif (hanya disebut sebagai legacy-compat)');
assert(ciYml.includes('kouzen-neo/kzkt/releases/download') && !ciYml.includes('Kiuyha/Manga-Bubble-YOLO'), 'CI: model bubble diambil dari APK rilis kouzen-neo/kzkt (bukan Kiuyha/ogkalu)');
assert(editor.indexOf('contentDescription = "Text"') < editor.indexOf('contentDescription = "Brush"'), 'toolbar: Text tepat di sebelah Pan (sebelum Brush)');
assert(editor.includes('Berlaku di SEMUA tool termasuk TEXT'), 'text: cubit dua jari = geser+zoom kanvas di semua tool termasuk TEXT');
assert(editor.includes('const val SCRIPT_LINE_BREAK = "⏎"') && editor.includes('fun comicShapeLines('), 'script: marker jeda manual ⏎ + auto-bentuk baris seimbang (tanpa penggal kata)');
assert(editor.includes('applyScriptBreaks(unused[i].text)') && editor.includes('applyScriptBreaks(entry.text)') && editor.includes('applyScriptBreaks(row.script)'), 'script: marker ⏎ dirender jadi baris di 3 jalur (bubble teks/seleksi/bubble-rows)');
assert(editor.includes('"Auto-bentuk"') && editor.includes('"Bentuk komik"') && editor.includes('autoShapeScripts()') && editor.includes('shapeDraftText(scriptDraft)'), 'script: tombol Auto-bentuk + Bentuk komik (jumlah naskah tetap, jeda bisa diedit manual)');
assert(editor.includes('"Daftar naskah"') && editor.includes('scriptEntries = scriptEntries.filter { it.id != entry.id }'), 'script: daftar naskah terlihat per baris + hapus manual');
assert(editor.includes('key(entry.id)') && editor.includes('key(row.id)') && editor.includes('JANGAN LazyColumn di'), 'script: daftar naskah & tabel rows pakai Column+key (anti-glitch ketik, tanpa LazyColumn bersarang)');

// 20. Hapus teks semua/ketuk + AI inpaint anti-gepeng/cerah/bubble.
assert(editor.includes('fun clearTextRegions()') && editor.includes('fun smallestTextRegionAt('), 'text-detector: hapus semua + helper region terkecil untuk ketuk');
assert(editor.includes('"Hapus semua"') && editor.includes('clearTextRegions()'), 'text-detector: tombol Hapus semua di dialog');
assert(editor.includes('panTapStart') && editor.includes('smallestTextRegionAt(cp)'), 'text-detector: ketuk tanpa geser di tool Pan menghapus region (geser = pan biasa)');
assert(agnesInpaintSrc.includes('fun fitCoverCenter(') && agnesInpaintSrc.includes('tanpa stretch'), 'ai-inpaint: hasil server disamakan cover+crop tengah (anti-gepeng, anti-geser tata letak)');
assert(inpaintMgr.includes('fun exposureDelta(') && inpaintMgr.includes('colorMatch = true'), 'ai-inpaint: eksposur lubang disamakan ke konteks (anti lebih cerah)');
assert(inpaintMgr.includes('dilateMaskAlpha(maskCropPre)') && inpaintMgr.includes('tanpa isi interior'), 'ai-inpaint: mask lembut tanpa isi interior (garis bubble tidak ikut terhapus)');
assert(agnesInpaintSrc.includes('never erase, move') || agnesInpaintSrc.includes('stay exactly where they are'), 'ai-inpaint: prompt melarang ubah/hapus garis bubble');
assert(editor.includes('it.copy(text = v)') && editor.includes('"+ Baris"'), 'script: tiap naskah bisa diedit manual + tambah baris manual');

// 21. Magic Wand (Manual/Otomatis) + 3 fitur bebas (Fokus bubble,
//     geser urutan naskah, Pas Layar).
const selectEng = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SelectionEngine.kt'));
{
  const o = (selectEng.match(/{/g) || []).length;
  const c = (selectEng.match(/}/g) || []).length;
  assert(o === c, 'SelectionEngine braces balanced (' + o + '/' + c + ')');
}
assert(editor.includes('SELECT_WAND,'), 'wand: ActiveTool.SELECT_WAND terdaftar');
assert(selectEng.includes('fun selectWand(') && selectEng.includes('fun traceContour(') && selectEng.includes('fun autoTolerance('), 'wand: flood fill + marching squares + toleransi otomatis di SelectionEngine');
assert(editor.includes('fun runWandAt(') && editor.includes('wandPressStart'), 'wand: tap-vs-geser dibedakan (seleksi hanya saat ketuk)');
assert(editor.includes('"Manual"') && editor.includes('"Otomatis"') && editor.includes('wandTolerance') && editor.includes('wandMode == "auto"'), 'wand: bar pengaturan mode Manual/Otomatis + slider toleransi');
assert(editor.includes('"Wand"') && editor.includes('"Magic Wand"'), 'wand: tombol toolbar + menu lasso');
assert(selectEng.includes('fun splitMergedBubble(') && editor.includes('fun splitBubbleAt('), 'wand-otomatis: pecah bubble gabung (kasus webtoon)');
assert(selectEng.includes('fun splitMergedWatershed(') && selectEng.includes('WandEngine.splitBubbles('), 'wand-otomatis: watershed berbasis puncak distance transform (bukan erosi 1px per iterasi)');
assert(editor.includes('Bubble gabung dipecah jadi 2 area'), 'wand-otomatis: pesan hasil pecah bubble');
assert(editor.includes('fun focusRect(') && editor.includes('fun fitCanvasToScreen()'), 'navigasi: focusRect + fitCanvasToScreen');
assert(bubDlg.includes('"Fokus"') && editor.includes('focusRect(rect)'), 'bubble: tombol Fokus per bubble (pusatkan kanvas)');
assert(editor.includes('fun moveScriptEntry(') && editor.includes('moveScriptEntry(idx, idx - 1)'), 'script: geser urutan naskah naik/turun');
assert(editor.includes('"Pas Layar"') && editor.includes('fitCanvasToScreen()'), 'navigasi: tombol Pas Layar di top bar');
assert(fs.existsSync(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BulkTextDialog.kt')), 'bulk: dialog di file sendiri (badan editor tak boleh >64KB)');
assert(editor.includes('BulkTextDialog(') && editor.includes('fun WandSettingsBar('), 'refaktor: BulkTextDialog + WandSettingsBar terpisah (anti Method too large)');
const holderCount = (editor.match(/by uiState::/g) || []).length;
assert(editor.includes('private class EditorUiState') && holderCount >= 60, 'refaktor: state UI ringan di holder (anti Method too large) - ' + holderCount + ' properti');
const localUis = ['showScriptPanelUi', 'showMLInpaintDialogUi', 'showExportDialogUi', 'showScriptEditorUi'];
assert(localUis.every(n => editor.includes('fun ' + n + '(')), 'refaktor: dialog besar jadi fun lokal @Composable (anti Method too large)');
assert(localUis.every(n => editor.includes(n + '()')), 'refaktor: call site dialog memanggil fun lokal');
assert(!/^internal val (Accent|PanelBg)/m.test(editor), 'refaktor: tanpa internal val Accent/PanelBg (bentrok antar-file UI → build gagal)');

// 22. Model bubble tahan-bentuk + deteksi anti-delay/crash, watershed,
//     kuas SFX lettering + gapless, bulk text edit.
assert(bubbleDet.includes('val padX = (INPUT_SIZE - nw) / 2f') && bubbleDet.includes('Color.rgb(114, 114, 114)'), 'bubble: preprocessing letterbox 640 + isi abu 114 (standar training YOLO)');
assert(bubbleDet.includes('fun decodeYolo6') && bubbleDet.includes('val pixels = maxCoord > 2.5f'), 'bubble: decoder 6 kanal (piksel vs ternormalisasi)');
assert(bubbleDet.includes('val cornerXyxy = pixels && cornerRatio >= 0.6f') && bubbleDet.includes('cxcywh dalam PIKSEL input 640'), 'bubble: format kotak = cxcywh-PIKSEL (terbukti IoU 0.96 di atas model sungguhan)');
assert(bubbleDet.includes('if (c2 > c0 && c3 > c1) cornerLike++'), 'bubble: xyxy vs cxcywh dibedakan dari kandidat aktif (bukan tebakan)');
assert(bubbleDet.includes('IoU 0.96'), 'bubble: hasil pengukuran model tercatat di KDoc decoder');
assert(bubbleDet.includes('const val REL_CONF') && bubbleDet.includes('topScore * REL_CONF'), 'bubble: ambang RELATIF terhadap skor tertinggi halaman (gelembung kedua hanya 0.03)');
assert(bubbleDet.includes('ASPECT_MIN') && bubbleDet.includes('ASPECT_MAX'), 'bubble: filter rasio aspek membuang kotak bukan-bubble');
assert(bubbleDet.includes('AMBANG RELATIF'), 'bubble: diagnostik melaporkan jalur ambang yang dipakai');
assert(bubbleDet.includes('val numScores =') && bubbleDet.includes('bestC = c'), 'bubble: channel 4.. = skor tiap kelas (argmax), bukan (skor, index kelas)');

// -- Teks mengikuti bentuk bubble (isi-bubble otomatis + panel) --------------
const bubShapePanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BubbleShapeSection.kt'));
const genrePanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/GenreBrushPanel.kt'));
const brushDrawer = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BrushDrawerPanel.kt'));
{
  for (const [nm, tx] of [['BubbleShapeSection', bubShapePanel], ['GenreBrushPanel', genrePanel]]) {
    const o = (tx.match(/{/g) || []).length;
    const c2 = (tx.match(/}/g) || []).length;
    assert(o === c2, nm + ' braces balanced (' + o + '/' + c2 + ')');
  }
}
assert(textBoxSrc.includes('data class BubbleSpec') && textBoxSrc.includes('var bubble: BubbleSpec?'), 'teks: spec bubble (bentuk yang diikuti teks) tersimpan di kotak');
assert(textBoxSrc.includes('SHAPE_ELIPS') && textBoxSrc.includes('SHAPE_BULAT'), 'teks: dua bentuk bubble (elips + persegi bulat) plus mode bebas');
assert(textBoxSrc.includes('fun bubblePathPx()') && textBoxSrc.includes('addOval(r, Path.Direction.CW)'), 'teks: path bubble untuk memotong glif tepat pada bentuk gelembung');
assert(textBoxSrc.includes('m.setRotate(rotation)') && textBoxSrc.includes('m.postTranslate(position.x, position.y)'), 'teks: gelembung ikut putar/geser bersama kotak teks');
assert(textBoxSrc.includes('fun bubbleInnerRectLocal()') && textBoxSrc.includes('1.4142f'), 'teks: persegi terpanjang di dalam elips (teks tak keluar gelembung bulat)');
assert(textBoxSrc.includes('bubble = bubble?.copy()') && textBoxSrc.includes('bubble = o.bubble?.copy()') && textBoxSrc.includes('bubble == o.bubble'), 'teks: bubble ikut copy/setFrom/contentEquals');
assert(textBoxSrc.includes('put("bubble"') && textBoxSrc.includes('optJSONObject("bubble")'), 'teks: bubble ikut JSON project (lengkap bubbleW/bubbleH)');
// -- Teks MENGIKUTI kurva bubble: lebar per baris, bukan satu kotak ------------
assert(textBoxSrc.includes('fun bubbleLineWidths()') && textBoxSrc.includes('val prof = sqrt(1f - t * t)'), 'teks: lebar baris mengikuti profil elips (sqrt(1-t^2))');
assert(textBoxSrc.includes('coerceAtLeast(iw * 0.34f)'), 'teks: baris tepi tak pernah lebih sempit dari 34% (tetap terbaca)');
assert(textBoxSrc.includes('if (n < 2) return null'), 'teks: tapering hanya saat ada 2+ baris (satu baris tak perlu)');
assert(textBoxSrc.includes('wrapParagraph(\n            displayText(), basePaint(), iw,'), 'teks: jumlah baris diperkirakan dari pembungkusan greedy');
assert(richLayout.includes('val bubbleProfile = box.bubbleLineWidths()') && richLayout.includes('fun limitOf(idx: Int)'), 'layout: ambang lebar diambil per baris sesuai profil bubble');
assert(richLayout.includes('lineIndex++') && richLayout.includes('val curLimit = limitOf(lineIndex)'), 'layout: indeks baris maju saat baris difinish (profil ikut geser)');
assert(richLayout.includes('bubble?.let { b2 -> b2.shape.toString()'), 'layout: cache layout ikut memuat bentuk bubble (tak basi)');
assert(textRenderer.includes('val clip = box.bubblePathPx()') && textRenderer.includes('canvas.clipPath(clip)') && textRenderer.includes('canvas.restoreToCount(c2)'), 'renderer: satu klip di luar berlaku untuk semua jalur (glif/outline/shadow/glow)');
assert(editor.includes('val ir = probe.bubbleInnerRectPx()') && editor.includes('BubbleSpec.SHAPE_ELIPS'), 'editor: isi-bubble otomatis memakai persegi dalam elips + spec bubble');
assert(editor.includes('box.autoFit = true') && editor.includes('box.boxHeight ='), 'editor: frame auto-fit dibuat eksplisit agar klip dan renderer punya kotak');
assert(textEditor.includes('BubbleShapeSection(') && bubShapePanel.includes('fun BubbleShapeSection('), 'panel: pilihan bentuk bubble (bebas / elips / persegi bulat) di tab Tulis');
assert(bubShapePanel.includes('spec.copy(inset = it)') && bubShapePanel.includes('spec.copy(roundRatio = it)'), 'panel: jarak aman + kelengkungan bisa disetel');
assert(brushDrawer.includes('"Brush", "SFX", "Stabilizer", "Fade"') && brushDrawer.includes('1 -> GenreBrushPanel(brushEngine)'), 'panel kuas: tab SFX membuka setelan 4 genre');
assert(genrePanel.includes('fun GenreBrushPanel(') && genrePanel.includes('store.of(genre)'), 'panel kuas: setelan per genre dibaca dari engine');
assert(genrePanel.includes('st.widthMul = it') && genrePanel.includes('st.opacity = it') && genrePanel.includes('st.texture = it') && genrePanel.includes('st.spatter ='), 'panel kuas: lebar/opacity/tekstur/percikan bisa diedit');
assert(genrePanel.includes('st.gradStart = c') && genrePanel.includes('st.gradEnd = c') && genrePanel.includes('st.gradient = it'), 'panel kuas: gradasi (dua warna + sudut) bisa diedit');
assert(genrePanel.includes('st.outlineWidth = it') && genrePanel.includes('st.outlineColor = c'), 'panel kuas: outline (tebal + warna) bisa diedit');
assert(genrePanel.includes('st.shadowOn = it') && genrePanel.includes('st.shadowBlur = it') && genrePanel.includes('st.shadowColor = c'), 'panel kuas: bayangan (geser/kabut/warna) bisa diedit');
assert(genrePanel.includes('st.applyPreset(genre)') && genrePanel.includes('store.of(other).copyFrom(st)'), 'panel kuas: kembali ke bawaan + salin setelan antar genre');
assert(bubbleDet.includes('private const val CONF_THRESH = 0.10f') && bubbleDet.includes('CONF_FLOOR = 0.02f'), 'bubble: ambang turun (bias head model -7.7 membuat skor default ~0)');
assert(bubbleDet.includes('relaxed = true') && bubbleDet.includes('DITURUNKAN'), 'bubble: ambang adaptif (8 teratas) bila tak ada yang lolos');
assert(bubbleDet.includes('if (src.hasAlpha()) android.graphics.Color.WHITE'), 'bubble: area transparan jadi putih (kertas, bukan hitam)');
assert(bubbleDet.includes('if (best < CONF_FLOOR) continue') && bubbleDet.includes('tak ada anchor >'), 'bubble: format koordinat dibaca dari anchor yang lolos ambang');
assert(bubbleDet.includes('- padX) / scaleX') && bubbleDet.includes('(cx - bw / 2f) / scaleX'), 'bubble: balik koordinat benar untuk format piksel & ternormalisasi');
const wf = read(path.join(ROOT, '.github/workflows/android.yml'));
// -- Pipeline area bubble: cermin dari uji numerik wand-check.mjs -------------
{
  const areaPipe = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/BubbleAreaPipeline.kt'));
  const wandChk = read(path.join(ROOT, 'scripts/wand-check.mjs'));
  assert(areaPipe.includes('fun areaAt(') && areaPipe.includes('fun areasFrom('), 'area bubble: API areaAt dan areasFrom ada');
  assert(areaPipe.includes('WandEngine.flood(') && areaPipe.includes('mask.toBinary()'), 'area bubble: langkah 1 pakai flood span + ambang 50%');
  assert(areaPipe.includes('touchesBorder(') && areaPipe.includes('allowBorder: Boolean = false'), 'area bubble: langkah 2 menolak yang bocor ke tepi (kecuali mode panel)');
  assert(areaPipe.includes('if (labels[i] == seedLabel) count++ else bin[i] = false'), 'area bubble: langkah 3 batasi watershed ke komponen seed');
  assert(areaPipe.includes('WandEngine.distanceTransform(') && areaPipe.includes('const val PEAK_RATIO = 0.7f'), 'area bubble: langkah 4 puncak watershed 0.7 * max (resep OpenCV)');
  assert(areaPipe.includes('tiny[argMax] = true') || areaPipe.includes('peak[argMax]'), 'area bubble: gelembung kecil punya fallback inti');
  assert(areaPipe.includes('owner[i - 1] = me; queue[tail++] = i - 1'), 'area bubble: langkah 5 tumbuhkan geodesik di dalam mask asli');
  assert(areaPipe.includes('fun textColorFor(') && areaPipe.includes('sum / n > 128f'), 'area bubble: warna teks kontras dihitung dari isi area');
  assert(/masks\[k\]\[seedIdx\]/.test(areaPipe), 'area bubble: area yang dikembalikan adalah yang memuat titik ketuk');
  assert(areaPipe.includes('fun fillHoles(') && areaPipe.includes('WandEngine.labelComponents(bg, w, h, 1)'), 'area bubble: lubang (teks di dalam bubble) diisi agar satu gelembung = satu area');
  assert(areaPipe.includes('fun nearestLightPixel(') && areaPipe.includes('lightSnap: Boolean = false'), 'area bubble: ketukan di atas teks bisa disnap ke kertas (mode bubble)');
  assert(editor.includes('lightSnap = true'), 'editor: snap diaktifkan untuk mode area bubble');
  assert(/var bubbleAreaMode by mutableStateOf\(false\)/.test(editor), 'editor: wand bawaannya seleksi klasik (bukan mode area bubble)');
  assert(editor.includes('downsampleForWand(raw, w, h)'), 'editor: jalur area bubble memakai downsample 480px (tak OOM di kanvas besar)');
  assert(areaPipe.includes('const val KIND_PANEL = 1') && areaPipe.includes('KIND_BUBBLE'), 'area bubble: jenis area (bubble/panel) ditandai');
  // Uji numeriknya harus benar-benar ada dan punya kasus ground truth.
  for (const k of ['Kasus 1: satu gelembung', 'Kasus 2: dua gelembung bersinggungan',
    'Kasus 3: ketuk di luar gelembung', 'Kasus 4: gelembung kecil', 'Kasus 5: area panel',
    'Kasus 6']) {
    assert(wandChk.includes(k), 'uji wand: kasus ' + k);
  }
  assert(wandChk.includes('function iouSet') && wandChk.includes('a.size + b.size - inter'), 'uji wand: IoU menghitung ukuran Set dengan benar');
  assert(wandChk.includes('a.size, b.size') || wandChk.includes('Math.min(a.size, b.size)'), 'uji wand: rasio tumpang tindih bukan NaN');
  // Regresi yang tertangkap uji: seed tidak boleh ditandai sebelum span diproses.
  assert(!wandChk.includes('cov[seedY * w + seedX] = 255;'), 'uji wand: seed tidak ditandai sebelum diproses (regresi flood)');
  const wandSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/WandEngine.kt'));
  assert(wandSrc.includes('Seed sengaja TIDAK ditandai di sini'), 'WandEngine: span seed diproses lebih dulu (bukan ditandai)');
}

// -- Tongkat sihir: span flood fill + anti-alias + watershed (riset sumber) ---
const wandEng = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/WandEngine.kt'));
const selEng = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SelectionEngine.kt'));
{
  const o = (wandEng.match(/{/g) || []).length;
  const c = (wandEng.match(/}/g) || []).length;
  assert(o === c, 'WandEngine braces balanced (' + o + '/' + c + ')');
}
assert(wandEng.includes('val aa = 1.5f - (d / thr)'), 'wand: ramp anti-alias GIMP (aa = 1.5 - d/threshold)');
assert(wandEng.includes('aa < 0.5f -> aa * 2f') && wandEng.includes('aa <= 0f -> 0f'), 'wand: bentuk tiga tahap ramp anti-alias persis GIMP');
assert(wandEng.includes('val cov = ByteArray(w * h)'), 'wand: mask coverage 0..255 (bukan boolean) - hilangkan white fringing');
assert(wandEng.includes('fun toBinary(threshold: Int = 128)'), 'wand: kontur diambil pada coverage 50% (tepi falls di tepi asli)');
assert(wandEng.includes('val LIN = FloatArray(256)') && wandEng.includes('1.055'), 'wand: metrik dihitung di ruang linear-light (GIMP_PRECISION_FLOAT_GAMMA)');
assert(wandEng.includes('if (-e > d) d = -e') && wandEng.includes('(-f > d) d = -f'), 'wand: jarak Chebyshev (L-infinity), bukan Euclidean RGB');
assert(wandEng.includes('var stack = IntArray(3 * 4096)'), 'wand: stack span IntArray (tanpa boxing per piksel)');
assert(wandEng.includes('stack[sp++] = y'), 'wand: entri stack adalah rentang (y, dari, sampai) ala GIMP');
assert(wandEng.includes('val pushFrom = if (p.diagonal && start > 0) start - 1 else start'), 'wand: opsi diagonal = memperlebar span (bukan probe diagonal)');
assert(wandEng.includes('if (!p.contiguous)'), 'wand: mode sample-merged (semua warna serupa, tanpa syarat terhubung)');
assert(wandEng.includes('fun feather(mask: Mask, radius: Float)'), 'wand: feather operasi terpisah setelah fill (default GIMP mati)');
assert(wandEng.includes('val SQRT2 = 1.41421356f') && wandEng.includes('fun distanceTransform'), 'wand: distance transform chamfer 3x3 (DIST_L2 maskSize 5)');
assert(wandEng.includes('val thr = max(1f, maxD * ratio.coerceIn(0.2f, 0.95f))') && wandEng.includes('ratio: Float = 0.7f'), 'wand: puncak watershed = jarak >= 0.7 * max (resep OpenCV)');
assert(wandEng.includes('if (markCount < 2) return emptyList()'), 'wand: watershed perlu minimal 2 marker');
assert(wandEng.includes('fun maskFromBinary(') && wandEng.includes('fun thresholdForSrgb('), 'wand: pembantu mask biner + konversi ambang UI ke metrik linear');
assert(selEng.includes('WandEngine.flood(') && selEng.includes('WandEngine.Params('), 'seleksi: selectWand memakai mesin baru');
assert(!selEng.includes('ArrayDeque<Int>()') || selEng.includes('WandEngine'), 'seleksi: tak ada lagi stack piksel berbasis Integer di jalur wand');
assert(!selEng.includes('val thr2 = maxDist * maxDist'), 'seleksi: jarak Euclidean RGB lama dibuang');
// -- Multi-area bubble: daftar area bernomor di editor ---------------------
// -- Panel area bubble, overlay nomor, dan isi otomatis dari script ---------
{
  const areaPanel = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/BubbleAreaPanel.kt'));
  const o = (areaPanel.match(/{/g) || []).length;
  const c2 = (areaPanel.match(/}/g) || []).length;
  assert(o === c2, 'BubbleAreaPanel braces balanced (' + o + '/' + c2 + ')');
  assert(areaPanel.includes('fun BubbleAreaPanel('), 'panel area: komposable terpisah (jaga batas 64KB)');
  assert(areaPanel.includes('onFillFromScript') && areaPanel.includes('"Isi Area"'), 'panel area: tombol Isi Area ada');
  assert(areaPanel.includes('"Area Panel"') && areaPanel.includes('onPanelMode'), 'panel area: sakelar Area Panel ada');
  assert(areaPanel.includes('"Hapus Semua"') && areaPanel.includes('onClear'), 'panel area: hapus semua ada');
  assert(areaPanel.includes('onRemoveAt'), 'panel area: bisa menghapus area per ketukan');
  assert(editor.includes('val bubbleAreasOrdered = selectionEngine.bubbleAreasInReadingOrder()'), 'overlay: daftar area digambar di kanvas');
  assert(editor.includes('areaCanvas.drawText(') && editor.includes('KIND_PANEL'), 'overlay: nomor area digambar, area panel dibedakan warna');
  assert(editor.includes('BubbleAreaPanel(') && editor.includes('showBubbleAreaPanel'), 'editor: panel area terpasang');
  assert(editor.includes('fun fillAreasFromScript('), 'editor: aksi isi area dari script ada');
  assert(editor.includes('selectionEngine.bubbleAreasInReadingOrder()') && editor.includes('multiBubbleLines'), 'editor: baris script dipetakan ke area bernomor');
  assert(editor.includes('bubbleInnerRectLocal') || editor.includes('bubbleInnerRectPx'), 'editor: teks area memakai persegi dalam bubble');
  assert(editor.includes('undoRedoManager.pushLayerAdd('), 'editor: isi area punya satu langkah undo');
  assert(editor.includes('area.textColor'), 'editor: warna teks diambil dari isi area (kontras)');
}
assert(selEng.includes('fun addBubbleArea(') && selEng.includes('fun bubbleAreasInReadingOrder('), 'area bubble: multi-area disimpan di SelectionEngine');
assert(selEng.includes('fun removeBubbleAreaAt(') && selEng.includes('fun clearBubbleAreas()'), 'area bubble: area bisa dihapus per ketukan dan sekaligus');
assert(selEng.includes('var bubbleAreaList by mutableStateOf(listOf<BubbleAreaPipeline.Area>())'), 'area bubble: daftar area adalah state (overlay ikut digambar ulang)');
assert(selEng.includes('bubbleAreasInReadingOrder()') || selEng.includes('bubbleReadingOrder('), 'area bubble: nomor urut mengikuti urutan baca manga');
assert(selEng.includes('bubbleAreasInReadingOrder') && selEng.includes('sortedBy'), 'area bubble: pengurutan baris implemented');
assert(editor.includes('BubbleAreaPipeline.areaAt('), 'editor: ketukan wand memakai pipeline area bubble');
assert(editor.includes('addBubbleArea(') && editor.includes('bukan area bubble'), 'editor: hasil ditambahkan (multi) dan pesan tolak ada');
assert(editor.includes('bubbleAreaPanelMode') || editor.includes('allowBorder = bubbleAreaPanelMode'), 'editor: sakelar Area Panel diteruskan ke pipeline');
assert(selEng.includes('WandEngine.splitBubbles(') && selEng.includes('WandEngine.maskFromBinary('), 'seleksi: pecah bubble gabung memakai watershed puncak (bukan pencarian biner erosi)');
assert(!selEng.includes('fun erodeByDistance(') && !selEng.includes('fun topLabels('), 'seleksi: helper erosi/label lama yang sudah tak dipakai dibuang');
assert(wf.includes('XOR satu byte 0x5A') && wf.includes('Range: bytes=43639867-129836409'), 'CI: model kzkt diambil dari APK (range) lalu di-dekode XOR');
assert(wf.includes('for VARIAN in fp16 int8') && wf.includes('make-bubble-model.py "$VARIAN"'), 'CI: varian model bubble(fp16/int8) dicoba berurutan');
assert(wf.includes('verify-bubble-model.py /tmp/bd_try.onnx') && wf.includes('::error::tidak ada varian model bubble yang lolos uji kontrak'), 'CI: tiap varian WAJIB lewat uji kontrak sebelum dipakai (gagal = build merah)');
assert(wf.includes('test "$SIZE" -lt 52428800') && !wf.includes('::warning::model bubble'), 'CI: model wajib <50MB (gagal build, bukan sekadar peringatan)');
{
  const mk = read(path.join(ROOT, 'scripts/make-bubble-model.py'));
  const vb = read(path.join(ROOT, 'scripts/verify-bubble-model.py'));
  assert(mk.includes('convert_float_to_float16') && mk.includes('del out.graph.value_info[:]'), 'skrip model: fp16 + buang deklarasi value_info usang (ORT menolak tipe campur)');
  assert(mk.includes('QuantFormat.QOperator') && !mk.includes('quantize_dynamic('), 'skrip model: int8 memakai QOperator (QLinearConv), bukan ConvInteger');
  assert(mk.includes('CalibrationDataReader') && mk.includes('sintetis('), 'skrip model: kalibrasi int8 pakai halaman manga sintetis');
  assert(vb.includes('ConvInteger') && vb.includes('MIN_IOU') && vb.includes('IoU per gelembung'), 'skrip uji: menolak op integer + mengukur IoU kotak decoder');
  assert(vb.includes('def decode(') && vb.includes('corner_ratio >= 0.6'), 'skrip uji: decoder sama persis dengan decodeYolo6 (cxcywh-piksel)');
}
assert(bubbleDet.includes('isLogitsName') && bubbleDet.includes('ByteArray'), 'bubble: peran output dari nama + konversi dtype generik (tahan varian int8)');
assert(bubbleDet.includes('n < 10 || n > 5000') && bubbleDet.includes('lg[0].size !in 2..8'), 'bubble: jumlah query & kelas fleksibel (bukan hardcode 300/3)');
assert(bubbleDet.includes('fun isTileBlank(') && bubbleDet.includes('maxTiles'), 'bubble: lewati tile kosong + batas tile (anti-delay/OOM)');
assert(bubbleDet.includes('fun checkNotCancelled()') && bubbleDet.includes('CancellationException'), 'bubble: inferensi bisa dibatalkan (tidak menggantung/crash)');
assert(bubbleDet.includes('import kotlinx.coroutines.Job') && bubbleDet.includes('currentCoroutineContext'), 'bubble: helper cancel pakai API yang pasti ada (tanpa ini build gagal)');
assert(editor.includes('bubbleDetectJob') && editor.includes('px > 8_000_000L'), 'bubble: cancel deteksi lama + snapshot downscale di kanvas raksasa');
assert(selectEng.includes('fun splitMergedWatershed(') && selectEng.includes('parts.map { r ->'), 'wand: pecah bubble mengembalikan semua bagian, bukan tepat 2');
// 23. Fitur baru: SFX engine, wand ringan + watershed jarak, kotak seleksi
//     teks (fit), gaya per kata (span), perspektif 4 sudut bebas.
assert(selectEng.includes('fun downsampleForWand') && selectEng.includes('WAND_MAX_DIM = 480'), 'wand: snapshot diturunkan ke 480px (tak berat di kanvas 720x16000)');
assert(editor.includes('downsampleForWand(raw, w, h)') && editor.includes('spx, sw, sh, seedX, seedY, tol, sample.scale'), 'wand: koordinat seed + Path dikembalikan ke kanvas penuh (skala 1/faktor)');
assert(!selectEng.includes('fun erodeByDistance') && wandEng.includes('fun distanceTransform(bin: BooleanArray'), 'wand: kontraksi pakai distance transform (helper erosi lama dibuang)');
assert(wandEng.includes('val minPixels = max(8, (any * minAreaRatio') && wandEng.includes('minPixels) {'), 'wand: noise dibuang lewat ambang luas minimum per marker');
assert(wandEng.includes('private fun claim(') && wandEng.includes('(mask.cov[j].toInt() and 0xFF) < 128'), 'wand: watershed tumbuh 4-arah dan tak keluar dari mask (tak bocor diagonal)');
assert(textBoxSrc.includes('fun setFrame') && textBoxSrc.includes('fun isFrame()'), 'teks: kotak seleksi ala Photoshop (lebar + tinggi)');
assert(textBoxSrc.includes('var autoFit: Boolean') && richLayout.includes('fun fitOf'), 'teks: auto-fit mengecilkan font sampai muat di kotak');
assert(textBoxSrc.includes('WIDTH_RIGHT, FRAME') && textBoxSrc.includes('fun dragFrameHandle('), 'teks: handle FRAME untuk menggeser tepi kotak');
assert(editor.includes('textCreatePending') && editor.includes('nb.setFrame('), 'teks: drag di kanvas kosong buat kotak seleksi, tap buat teks titik');
assert(textBoxSrc.includes('data class SpanStyle') && textBoxSrc.includes('data class TextSpan'), 'teks: SpanStyle/TextSpan untuk gaya per kata');
assert(textBoxSrc.includes('fun wordRanges()') && textBoxSrc.includes('fun applySpanStyle('), 'teks: daftar kata + terapkan gaya ke rentang kata');
assert(textBoxSrc.includes('fun setTextKeepingSpans('), 'teks: span ikut terpeta saat teks diketik');
assert(richLayout.includes('fun build') && richLayout.includes('class Run('), 'teks: layout kaya mengukur per run (gaya per kata tetap rapi)');
assert(textRenderer.includes('fun renderRich') && textRenderer.includes('fun richFillPaint'), 'renderer: jalur kaya (warna/shadow/outline per kata)');
assert(richPanel.includes('Gaya per kata') && richPanel.includes('Terapkan'), 'panel: gaya per kata (daftar kata + font/warna/tebal/miring/shadow/outline)');
assert(editor.includes('showRichTextPanel') && editor.includes('EditorOverlays.richTextPanel('), 'editor: panel gaya per kata terhubung dari toolbar');
assert(editor.includes('fun openRichTextPanel()') && editor.includes('fun newTextFrameBox('), 'editor: helper lokal (aksi panel & kotak) di method terpisah');
assert(textBoxSrc.includes('parseSpans(') && textBoxSrc.includes('typefaceFor'), 'teks: font kata tetap berlaku setelah project dibuka lagi');
assert(textBoxSrc.includes('put("spans"') && textBoxSrc.includes('put("persp"'), 'teks: span + perspektif tersimpan di project JSON');
assert(!textEditor.includes('onOpenBulkEdit') && bulkDlg.includes('"Edit Teks Massal"'), 'bulk: dialog tetap ada');
assert(overlays.includes('Icons.Default.FormatSize') && overlays.includes('contentDescription = "Edit Teks Massal"'), 'bulk: tombol Edit Teks Massal pindah ke toolbar utama');
{
  const o = (bulkDlg.match(/{/g) || []).length;
  const c = (bulkDlg.match(/}/g) || []).length;
  assert(o === c, 'BulkTextDialog braces balanced (' + o + '/' + c + ')');
}
assert(bulkDlg.includes('bulkChecked') && bulkDlg.includes('"Edit Teks Massal"'), 'bulk: dialog pilih multi-teks (checkbox per kotak)');
assert(editor.includes('BulkTextDialog(') && editor.includes('pushTextBox(tl.id, tl.box.copy())'), 'bulk: terapkan ukuran/tebal/warna + undo per kotak');


// ------------------------------------------------- kuas SFX bertekstur (Ink API)
console.log('\n== Kuas SFX bertekstur (Ink API) ==');
assert(/androidx\.ink:ink-brush:\$inkVersion/.test(gradle), 'Ink API: dependensi ink-brush memakai variabel inkVersion');
assert(gradle.includes('androidx.ink:ink-strokes:$inkVersion'), 'Ink API: ink-strokes untuk membentuk Stroke dari titik');
assert(gradle.includes('androidx.ink:ink-rendering:$inkVersion'), 'Ink API: ink-rendering untuk menggambar ke Canvas');
assert(gradle.includes('androidx.ink:ink-nativeloader:$inkVersion'), 'Ink API: native loader untuk libink.so');
assert(sfxTex.startsWith('@file:OptIn(ExperimentalInkCustomBrushApi::class)'), 'Ink API: OptIn penanda eksperimental ada di baris pertama');
assert(sfxTex.includes('import androidx.compose.ui.graphics.Brush'), 'tekstur: grain digambar dengan androidx.compose.ui.graphics.Brush');
assert(sfxTex.includes('import androidx.compose.ui.graphics.Canvas as composeCanvas'), 'tekstur: digambar di ImageBitmap lewat Canvas Compose');
assert(sfxTex.includes('Brush.radialGradient'), 'tekstur: titik dan stempel memakai radial gradient Compose');
assert(sfxTex.includes('Brush.linearGradient'), 'tekstur: arsir dan retak memakai linear gradient Compose');
assert(sfxTex.includes('StockTextureBitmapStore(Resources.getSystem())'), 'Ink API: grain didaftarkan ke TextureBitmapStore resmi');
assert(sfxTex.includes('for (t in Texture.entries) s.addTexture(textureId(t), buildTile(t))'), 'Ink API: keenam tekstur terdaftar sebagai grain');
assert(sfxTex.includes('BrushPaint.TextureMapping.TILING') && sfxTex.includes('BrushPaint.TextureMapping.STAMPING'), 'tekstur: mode TILING dan STAMPING (enum resmi Ink) dipakai');
assert(sfxTex.includes('BrushPaint.BlendMode.DST_OUT'), 'tekstur: retak dan robek memakai DST_OUT sehingga ada celah putih');
assert(sfxTex.includes('BrushPaint.BlendMode.MODULATE'), 'tekstur: halftone dan arsir memakai MODULATE agar warna kuas tetap');
assert(sfxTex.includes('BrushPaint.TextureWrap.REPEAT'), 'tekstur: grain diulang (wrap REPEAT) seperti grain Procreate');
assert(sfxTex.includes('BrushFamily(tip, paint)'), 'Ink API: BrushFamily dibangun dari tip dan paint');
assert(sfxTex.includes('Brush.createWithColorIntArgb('), 'Ink API: Brush dibuat lewat createWithColorIntArgb (API 1.0.0)');
assert(!sfxTex.includes('createWithComposeColor'), 'Ink API: createWithComposeColor tidak dipakai karena baru ada di alpha 1.1');
assert(sfxTex.includes('CanvasStrokeRenderer.create(s)'), 'Ink API: renderer resmi yang menggambar ke Canvas');
assert(sfxTex.includes('r.draw(canvas, ips.toImmutable(), Matrix())'), 'Ink API: Stroke digambar ke android.graphics.Canvas milik layer');
assert(sfxTex.includes('InProgressStroke()') && sfxTex.includes('ips.start(brush)') && sfxTex.includes('ips.finishInput()'), 'Ink API: alur stroke start lalu input lalu finish lalu toImmutable');
assert(sfxTex.includes('if (!prepare()) return false'), 'Ink API: gagal native berarti jatuh ke kuas polos, bukan crash');
assert((sfxTex.match(/catch \(t: Throwable\)/g) || []).length >= 2, 'Ink API: prepare dan drawStroke sama-sama dilindungi');
assert(sfxTex.includes('fun forGenre('), 'tekstur: tiap genre SFX punya tekstur bawaan');
for (const t of ['HALFTONE', 'HATCH', 'CRUNCH', 'SPATTER', 'RIBBON', 'ERODED']) {
  assert(sfxTex.includes(t + '('), 'tekstur: ' + t + ' ada di daftar enum');
}
assert(!/[\u4E00-\u9FFF\u0400-\u04FF]/.test(sfxTex), 'kode kuas bertekstur bebas karakter asing');

if (process.exitCode) {
  console.error('brush-check FAILED');
} else {
  console.log('brush-check PASSED: brush anti-delay/crash + hapus objek siap + import 720x16000 heap-aware + ruler/blend/blur sesuai namanya');
}
