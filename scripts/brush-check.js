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

for (const [name, text] of [['BrushEngine', brush], ['PatchMatch', patch], ['SeamlessBlender', seamless], ['InpaintingManager', inpaint], ['CanvasEditorScreen', editor], ['ImageImport', imageImport], ['ProjectManager', projectManager]]) {
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
assert(!editor.includes('MiGan'), 'UI bebas referensi MiGan (model dihapus)');
assert(!fs.existsSync(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ml/MiganInpainter.kt')), 'MiganInpainter.kt dihapus');
assert(!fs.existsSync(path.join(ROOT, 'app/src/main/assets/models/mg.onnx')), 'model mg.onnx dihapus dari assets');
assert(inpaint.includes('nativeInpaintPyramid'), 'inpaint seleksi memakai pyramid push-pull native (mask besar)');

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
const textEditor = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/TextEditorPanel.kt'));
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

if (process.exitCode) {
  console.error('brush-check FAILED');
} else {
  console.log('brush-check PASSED: brush anti-delay/crash + hapus objek siap + import 720x16000 heap-aware + ruler/blend/blur sesuai namanya');
}
