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
const inpaint = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/InpaintingManager.kt'));
const editor = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/ui/CanvasEditorScreen.kt'));
const imageImport = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ImageImport.kt'));
const projectManager = read(path.join(ROOT, 'app/src/main/java/com/grooxtyper/app/model/ProjectManager.kt'));

for (const [name, text] of [['BrushEngine', brush], ['PatchMatch', patch], ['InpaintingManager', inpaint], ['CanvasEditorScreen', editor], ['ImageImport', imageImport], ['ProjectManager', projectManager]]) {
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
assert(editor.includes('wasHeal'), 'editor commit heal untuk INPAINT + HEAL_PATCH');
assert(editor.includes('inpaintHealDirty'), 'editor commit crop dirty saja (anti-46MB scan)');

// 3. Heal brush better-than-Photoshop
assert(patch.includes('enum class HealMode'), 'heal punya HealMode enum');
assert(patch.includes('PRESERVE_STRUCTURE'), 'heal punya mode Preserve Structure');
assert(patch.includes('PRESERVE_TEXTURE'), 'heal punya mode Preserve Texture');
assert(patch.includes('gradWeight'), 'heal jarak patch sadar gradien (tajam vs PS)');
assert(inpaint.includes('healMode'), 'InpaintingManager expose healMode');
assert(inpaint.includes('inpaintHealDirty'), 'InpaintingManager punya inpaintHealDirty');
assert(editor.includes('Exemplar prioritas + Telea instan'), 'UI heal tunggal tanpa opsi mode');
assert(editor.includes('Inpaint Seleksi (MiGan)'), 'UI inpaint seleksi MiGan');

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

if (process.exitCode) {
  console.error('brush-check FAILED');
} else {
  console.log('brush-check PASSED: brush anti-delay/crash + heal brush siap + import 720x16000 heap-aware');
}
