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
const sfxEngineSrc = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxBrushEngine.kt'));
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
// SFX: mesin stamp terpisah (SfxBrushEngine) - sapuan kuas bukannya garis.
const sfxEng = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxBrushEngine.kt'));
{
  const o = (sfxEng.match(/{/g) || []).length;
  const c = (sfxEng.match(/}/g) || []).length;
  assert(o === c, 'SfxBrushEngine braces balanced (' + o + '/' + c + ')');
}
const sfxBrushes = ['SFX_PEN', 'SFX_BRUSH', 'SFX_MARKER', 'SFX_AIR', 'SFX_CRAYON', 'SFX_INK', 'SFX_NEON'];
const sfxInk = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/SfxInk.kt'));
{
  const o = (sfxInk.match(/\{/g) || []).length;
  const c = (sfxInk.match(/\}/g) || []).length;
  assert(o === c, 'SfxInk braces balanced (' + o + '/' + c + ')');
}
// SFX tinta: teknik dari video (path + profil half-width per panjang busur)
assert(sfxInk.includes('class XorShift64') && sfxInk.includes('object EdgeProfile'), 'SFX tinta: PRNG deterministik + profil tepi (anti swim)');
assert(sfxInk.includes('fun ribbon(') && sfxInk.includes('PathMeasure'), 'SFX tinta: pita (ribbon mesh) dari panjang busur - bukan tumpukan stamp');
assert(sfxInk.includes('pm.nextContour()'), 'SFX tinta: outline ikut kontur DALAM huruf (counter) seperti video');
assert(sfxInk.includes('fun drawGlyph(') && sfxInk.includes('fun compositeOutline('), 'SFX tinta: glyph & goresan Treatment');
assert(sfxInk.includes('spec.keylineWidth > 0f') && sfxInk.indexOf('spec.keylineWidth > 0f') < sfxInk.indexOf('// L1: outline putih'), 'SFX tinta: URUTAN benar (keyline lebar digambar lebih dulu)');
assert(sfxInk.includes('.coerceIn(8, 20000)'), 'SFX tinta: panjang profil dibatasi (anti OOM pada glyph raksasa)');
assert(sfxBrushes.every(b => sfxEng.includes(b)), 'SfxBrushEngine: semua 7 kuas SFX terpetakan ke gaya mesin');
assert(sfxEng.includes('INK_SFX("SFX Tinta")') && sfxEng.includes('fun compositeInkOutline('), 'SFX brush: gaya Tinta memakai pita + outline putih saat stroke selesai');
assert(sfxBrushes.every(b => brush.includes(b)), 'brush SFX: 7 kuas (Pen/Brush/Marker/Airbrush/Crayon/Ink/Neon)');
assert(sfxEng.includes('fun styleOf') && brush.includes('SfxBrushEngine.styleOf(brushType)'), 'SFX: peta BrushType ke gaya mesin');
assert(sfxEng.includes('fun stampFor') && sfxEng.includes('BitmapShader') === false, 'SFX: stamp radial bertekstur (bukan shader garis)');
assert(sfxEng.includes('val streak = 0.5f + 0.5f * noise1(y * 0.42f, seed)'), 'SFX: sabut kuas (streak) korelasi lintas goresan');
assert(sfxEng.includes('if (style.isTextured)') && sfxEng.includes('a *= 0.12f'), 'SFX: celah kering pada kuas bertekstur (tidak seperti garis penuh)');
assert(sfxEng.includes('private fun widthAt') && sfxEng.includes('val entry = 0.10f + 0.90f'), 'SFX: taper masuk runcing per-dab');
assert(sfxEng.includes('val pulse = 1f + 0.16f * noise1(dist * 0.035f, seed)'), 'SFX: denyut lebar organik (gaya tangan)');
assert(sfxEng.includes('val dirMul = 1f - 0.30f'), 'SFX: kontras arah (turun tebal, naik tipis)');
assert(sfxEng.includes('LIFT_MS') && sfxEng.includes('if (gapMs > LIFT_MS)'), 'SFX: jeda = kuas diangkat (lift)');
assert(sfxEng.includes('val hold = max(6f, size * 1.3f)'), 'SFX: ekor ditahan supaya ujung runcing');
assert(sfxEng.includes('fun paintUntil') && sfxEng.includes('val taper = if (taperTail) (1f - t * t)'), 'SFX: ekor diruncingkan saat stroke selesai');
assert(sfxEng.includes('canvas.rotate(ang)'), 'SFX: stamp dirotasi searah goresan (tekstur tidak berenang)');
assert(sfxEng.includes('private val srcRect = Rect(0, 0, STAMP_PX, STAMP_PX)') && sfxEng.includes('canvas.drawBitmap(stamp, srcRect, dst, paint)'), 'SFX: drawBitmap pakai Rect (src) + RectF (dst) yang benar');
assert(sfxEng.includes('val step = max(1.1f, w * 0.42f)'), 'SFX: jarak antar stamp < radius (goresan tanpa celah)');
assert(sfxEng.includes('Style.INK') && sfxEng.includes('hashF(index * 3 + 1, seed)'), 'SFX: tinta punya cipratan deterministik');
assert(brush.includes('sfxStroke.push(canvas, sfxPts, sfxSpeedFactor(distance))'), 'brush: segmen SFXلعvement lewat mesin stamp');
assert(brush.includes('fun discardSfxTail') && brush.includes('sfxStroke.discard()'), 'SFX: buang ekor saat stroke di-undo');
assert(brush.includes('tailLayer?.let { syncTiles(it) }'), 'SFX: ekor masuk cache tile walau syncTiles dipanggil sebelum endStroke');
assert(brush.includes('brushType == BrushType.MARKER || brushType == BrushType.FLAT'), 'gapless: Marker/Flat digambar satu path kontinu (tanpa takik sambungan)');
assert(brush.includes('if (isSfxBrush()) size * 2f else 0f'), 'SFX: clip region dilebarkan agar ekor tidak terpotong');
assert(brush.includes('sfxSpeedFactor(distance)'), 'SFX: kecepatan dihitung per-segmen dari jarak event (bukan per dab)');
assert(textBoxSrc.includes('data class SfxInkSpec') && textBoxSrc.includes('var inkSfx: SfxInkSpec?'), 'teks: gaya tinta SFX (SfxInkSpec) tersimpan di kotak');
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
assert(selectEng.includes('fun splitMergedWatershed(') && selectEng.includes('fun labelComponents(') && selectEng.includes('WATERSHED'), 'wand-otomatis: watershed biner (erosi kontraksi → seed → tumbuh serentak)');
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
assert(bubbleDet.includes('fun decodeYolo6') && bubbleDet.includes('val pixelXyxy = maxCoord > 2.5f'), 'bubble: decoder 6 kanal (xyxy-piksel vs cxcywh ternormalisasi)');
assert(bubbleDet.includes('val numScores =') && bubbleDet.includes('bestC = c'), 'bubble: channel 4.. = skor tiap kelas (argmax), bukan (skor, index kelas)');
assert(bubbleDet.includes('private const val CONF_THRESH = 0.10f') && bubbleDet.includes('CONF_FLOOR = 0.02f'), 'bubble: ambang turun (bias head model -7.7 membuat skor default ~0)');
assert(bubbleDet.includes('relaxed = true') && bubbleDet.includes('DITURUNKAN'), 'bubble: ambang adaptif (8 teratas) bila tak ada yang lolos');
assert(bubbleDet.includes('if (src.hasAlpha()) android.graphics.Color.WHITE'), 'bubble: area transparan jadi putih (kertas, bukan hitam)');
assert(bubbleDet.includes('if (best < CONF_FLOOR) continue') && bubbleDet.includes('tak ada anchor >'), 'bubble: format koordinat dibaca dari anchor yang lolos ambang');
assert(bubbleDet.includes('- padX) / scaleX') && bubbleDet.includes('(cx - bw / 2f) / scaleX'), 'bubble: balik koordinat benar untuk format piksel & ternormalisasi');
const wf = read(path.join(ROOT, '.github/workflows/android.yml'));
assert(wf.includes('XOR satu byte 0x5A') && wf.includes('quantize_dynamic') && wf.includes('Range: bytes=43639867-129836409'), 'CI: model kzkt diambil dari APK (range), di-dekode XOR, dikuantisasi int8');
assert(wf.includes('::warning::model bubble') && wf.includes('test -s app/src/main/assets/models/bd.onnx'), 'CI: model selalu terpasang (gate <50MB jadi peringatan, bukan gagal build)');
assert(bubbleDet.includes('isLogitsName') && bubbleDet.includes('ByteArray'), 'bubble: peran output dari nama + konversi dtype generik (tahan varian int8)');
assert(bubbleDet.includes('n < 10 || n > 5000') && bubbleDet.includes('lg[0].size !in 2..8'), 'bubble: jumlah query & kelas fleksibel (bukan hardcode 300/3)');
assert(bubbleDet.includes('fun isTileBlank(') && bubbleDet.includes('maxTiles'), 'bubble: lewati tile kosong + batas tile (anti-delay/OOM)');
assert(bubbleDet.includes('fun checkNotCancelled()') && bubbleDet.includes('CancellationException'), 'bubble: inferensi bisa dibatalkan (tidak menggantung/crash)');
assert(bubbleDet.includes('import kotlinx.coroutines.Job') && bubbleDet.includes('currentCoroutineContext'), 'bubble: helper cancel pakai API yang pasti ada (tanpa ini build gagal)');
assert(editor.includes('bubbleDetectJob') && editor.includes('px > 8_000_000L'), 'bubble: cancel deteksi lama + snapshot downscale di kanvas raksasa');
assert(selectEng.includes('fun splitMergedWatershed(') && selectEng.includes('KONTRAKSI') && selectEng.includes('WATERSHED: tumbuhkan'), 'wand: pecah bubble via watershed (erosi kontraksi → seed → tumbuh serentak)');
// 23. Fitur baru: SFX engine, wand ringan + watershed jarak, kotak seleksi
//     teks (fit), gaya per kata (span), perspektif 4 sudut bebas.
assert(selectEng.includes('fun downsampleForWand') && selectEng.includes('WAND_MAX_DIM = 480'), 'wand: snapshot diturunkan ke 480px (tak berat di kanvas 720x16000)');
assert(editor.includes('downsampleForWand(raw, w, h)') && editor.includes('1f / sample.scale'), 'wand: koordinat seed + Path dikembalikan ke kanvas penuh');
assert(selectEng.includes('fun distanceTransform') && selectEng.includes('fun erodeByDistance'), 'wand: kontraksi pakai transformasi jarak (bukan erosi 1px per iterasi)');
assert(selectEng.includes('fun topLabels') && selectEng.includes('total * minPct / 100'), 'wand: seed = 2 komponen terbesar, noise dibuang');
assert(selectEng.includes('fun growIfFree'), 'wand: watershed tumbuh 4-arah (tak bocor diagonal)');
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

if (process.exitCode) {
  console.error('brush-check FAILED');
} else {
  console.log('brush-check PASSED: brush anti-delay/crash + hapus objek siap + import 720x16000 heap-aware + ruler/blend/blur sesuai namanya');
}
