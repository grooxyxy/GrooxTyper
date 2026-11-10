// Generate file SVG ikon tool dari data path yang sama dengan ToolIcons.kt.
//
// Sumber kebenaran: app/src/main/java/com/grooxtyper/app/ui/ToolIcons.kt
// File SVG di app/src/main/assets/tool-icons/ DIHASILKAN oleh skrip ini,
// jadi bentuk ikon di aset dan di kode tak mungkin berbeda.
//
// Jalankan: node scripts/gen-tool-icons.mjs
//           node scripts/gen-tool-icons.mjs --check   (hanya verifikasi)

import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const KT = join(ROOT, "app/src/main/java/com/grooxtyper/app/ui/ToolIcons.kt");
const OUT = join(ROOT, "app/src/main/assets/tool-icons");

const src = readFileSync(KT, "utf8");

// Ambil blok: val <nama> = Def( strokes = listOf( "..." , ... ), fills = listOf( ... ) )
function extractDef(name) {
  const re = new RegExp(
    `val\\s+${name}\\s*=\\s*Def\\(([\\s\\S]*?)\\n    \\)`,
    "m"
  );
  const m = src.match(re);
  if (!m) return null;
  const body = m[1];
  const strokes = [];
  const fills = [];
  // Catatan: tak boleh ada delimiter setelah ")" - whitespace-nya bisa
  // saja jadi ")" penutup blok Def.
  const secRe = /(strokes|fills)\s*=\s*listOf\(([\s\S]*?)\)/g;
  let s;
  while ((s = secRe.exec(body)) !== null) {
    const target = s[1] === "strokes" ? strokes : fills;
    const strRe = /"([^"]+)"/g;
    let q;
    while ((q = strRe.exec(s[2])) !== null) target.push(q[1]);
  }
  return { strokes, fills };
}

// Ambil peta "nama" to Def dari companion `all`.
const allRe = /val\s+all\s*:\s*Map<String, Def>\s*=\s*linkedMapOf\(([\s\S]*?)\n    \)/m;
const allM = src.match(allRe);
if (!allM) {
  console.error("GAGAL: peta `all` tak ditemukan di ToolIcons.kt");
  process.exit(1);
}
const names = [];
const nameRe = /"([A-Za-z]+)"\s+to\s+[A-Za-z]+/g;
let n;
while ((n = nameRe.exec(allM[1])) !== null) names.push(n[1]);

if (names.length === 0) {
  console.error("GAGAL: tak ada ikon di peta `all`");
  process.exit(1);
}

function svgFor(name, def) {
  const lines = [];
  lines.push(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">`);
  for (const d of def.strokes) lines.push(`  <path d="${d}"/>`);
  for (const d of def.fills) {
    lines.push(`  <path d="${d}" fill="currentColor" stroke="none"/>`);
  }
  lines.push("</svg>");
  return lines.join("\n") + "\n";
}

const checkOnly = process.argv.includes("--check");
mkdirSync(OUT, { recursive: true });

let problems = 0;
let written = 0;
for (const name of names) {
  const def = extractDef(name);
  if (!def) {
    console.error(`GAGAL: definisi Def untuk "${name}" tak ditemukan`);
    problems++;
    continue;
  }
  const svg = svgFor(name, def);
  const file = join(OUT, `${name}.svg`);
  if (checkOnly) {
    if (!existsSync(file)) {
      console.error(`HILANG: ${name}.svg tak ada (jalankan tanpa --check)`);
      problems++;
    } else if (readFileSync(file, "utf8") !== svg) {
      console.error(`BEDA: ${name}.svg tak sama dengan ToolIcons.kt`);
      problems++;
    }
  } else {
    writeFileSync(file, svg, "utf8");
    written++;
  }
}

if (checkOnly) {
  if (problems > 0) {
    console.error(`gen-tool-icons: ${problems} masalah`);
    process.exit(1);
  }
  console.log(`gen-tool-icons: ${names.length} ikon SVG sesuai dengan ToolIcons.kt`);
} else {
  console.log(`gen-tool-icons: ${written} ikon SVG ditulis ke assets/tool-icons/`);
}
