#!/usr/bin/env node
/* The house rules for the Android app's Kotlin (gate 6, android/parity/0-map/2-look.md §6): named checks over
   android/app/src/main, the Compose twin of kit.mjs syntax + design-check.mjs for index.html.
     node tools/android-rules.mjs                 every rule over the app's code; exit 1 on a break
     node tools/android-rules.mjs --self-test     each rule must catch its planted copy and pass its clean one
     node tools/android-rules.mjs --root <dir>    another checkout's android/ (a before/after run)
     node tools/android-rules.mjs --list          the rules and what each guards
   A deliberate exception is a line in android/house-rules-ok.txt: `<rule> <path under …/playnext/> <text on the line> — <why>`
   (matched by the line's text, not its number, so it survives edits above it). An entry nothing matches any more fails
   too, so the list never piles up. android-check runs it on every PR. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PKG = "app/src/main/java/io/github/shamlawy/playnext";

/* Emoji-only text: pictographs, their joiners, variation selectors and keycaps, nothing else. */
const EMOJI_ONLY = /^(?:\p{Extended_Pictographic}|\p{Regional_Indicator}|[‍️⃣\s])+$/u;

/* Each rule: name, the house rule it guards, and a test on one line (with the file's text for context).
   `files` limits it to some files; `presence` rules check the whole app once instead. */
export const RULES = [
  { name: "press-motion", why: "No press animation: nothing shrinks, lifts or fades on touch (#309, v246, T14)",
    line: l => /\bcollectIsPressedAsState\s*\(|\bPressInteraction\b/.test(l) },
  { name: "ripple", why: "No ripple or glow under the finger (v280, T25): indication stays null, LocalIndication is NoIndication",
    line: l => /\brememberRipple\s*\(|\bripple\s*\(|\bindication\s*=\s*(?!null\b)\S/.test(l) || /\bLocalIndication\s+provides\s+(?!NoIndication\b)/.test(l) },
  { name: "hover", why: "No hover looks on touch (v280): phones keep hover on the last thing touched",
    line: l => /\bhoverable\s*\(|\bcollectIsHoveredAsState\s*\(/.test(l) },
  { name: "material-tap", why: "Material buttons, cards and chips ripple: taps go through the house Modifier.tap / clickable(indication = null)",
    line: l => /^\s*import\s+androidx\.compose\.material3?\.(Button|IconButton|TextButton|OutlinedButton|ElevatedButton|FilledTonalButton|FloatingActionButton|ExtendedFloatingActionButton|Card|ElevatedCard|OutlinedCard|Surface|AssistChip|FilterChip|InputChip|SuggestionChip|NavigationBarItem|NavigationRailItem|Tab|ListItem)\b/.test(l) },
  { name: "hex-colour", why: "Colours come from the tokens (v181, T2): Color(0x…) only in ui/Tokens.kt, or a web colour copied with its source named in the line's comment (`// --qsc …`, `// rgba(…)`)",
    skip: f => f.endsWith("ui/Tokens.kt"),
    line: (l, t, i, ls, raw) => /\bColor\s*\(\s*0x[0-9A-Fa-f]/.test(l) && !/\/\/.*(?:--[a-z][\w-]*|\brgba?\s*\()/.test(raw) },
  { name: "emoji-icon", why: "No emoji as icons (v297, T32): draw a GIcon (24x24, fill-only, one part at .35)",
    line: l => { for (const m of l.matchAll(/\bText\s*\(\s*(?:text\s*=\s*)?"((?:[^"\\]|\\.)+)"/g)) if (EMOJI_ONLY.test(m[1])) return true;
      return /\b(?:icon|glyph|emoji)\s*=\s*"((?:[^"\\]|\\.)+)"/i.test(l) && EMOJI_ONLY.test(l.match(/"((?:[^"\\]|\\.)+)"/)[1]); } },
  { name: "italic", why: "Upright headings (v188, T3): italics only for the slash wipe, the celebrations and share pictures",
    files: f => f.startsWith("ui/"),
    line: l => /\bFontStyle\.Italic\b|fontStyle\s*=\s*FontStyle\.Italic/.test(l) },
  { name: "uppercase", why: "Sentence-case words (v188, T3): no .uppercase() on what the screen shows",
    files: f => f.startsWith("ui/"),
    line: l => /\.uppercase\s*\(|\.toUpperCase\s*\(|\bcapitalization\s*=\s*KeyboardCapitalization\.Characters/.test(l) },
  { name: "font-weight", why: "Poppins comes in 400/500/600/700 (v188): Black / ExtraBold / Light / Thin only for Archivo",
    files: f => f.startsWith("ui/"),
    line: l => /\bFontWeight\.(Black|ExtraBold|ExtraLight|Light|Thin|W[1-35-9]00)\b|\bFontWeight\s*\(\s*(?:[1-3]00|800|900)\s*\)/.test(l) && !/\barchivo\b/i.test(l) },
  { name: "animated-layout", why: "Animate only transform and opacity (Speed rule, T20): no animated size, padding, colour or blur",
    line: l => /\banimate(Dp|Color|Size|IntSize|Rect|Value)AsState\s*\(|\.animateContentSize\s*\(|\bAnimatedContent\s*\(|\banimateColor\s*\(|\banimateDp\s*\(/.test(l) },
  { name: "looping-blur", why: "No looping blur (Speed rule, v167): no blur in a file with an endless animation",
    line: (l, t) => /\.blur\s*\(|\bBlurMaskFilter\b|\bcreateBlurEffect\b/.test(l) && /\brememberInfiniteTransition\s*\(|\binfiniteRepeatable\s*\(/.test(t) },
  { name: "scroll-linked", why: "No scroll-linked motion on list rows (v308): rows never move with the scroll offset",
    line: (l, t, i, ls) => /\bfirstVisibleItemScrollOffset\b|\blayoutInfo\.visibleItemsInfo\b/.test(l) && ls.slice(Math.max(0, i - 6), i + 7).some(x => /\bgraphicsLayer\b|\.offset\s*\{|\btranslationY\b|\balpha\s*=/.test(x)) },
  { name: "calm", why: "Calm motion honoured: every animation's function reads calm (LocalCalm, calm(), still) so it can snap",
    line: (l, t, i, ls) => /\bAnimatable\s*\(|\banimate(Float|IntOffset|Offset)AsState\s*\(|\brememberInfiniteTransition\s*\(/.test(l) && !/\bcalm\b|\bstill\b|\bCalm\b/.test(fnAround(ls, i)) },
  { name: "loop-scroll-hold", why: "Loops keep running mid-scroll (v282, T26): never tie a loop to isScrollInProgress",
    line: (l, t) => /\bisScrollInProgress\b/.test(l) && /\brememberInfiniteTransition\s*\(|\binfiniteRepeatable\s*\(/.test(t) },
];

/* Rules about the whole app: something must exist once. */
export const PRESENCE = [
  { name: "no-indication-root", why: "The root gives every tap no indication (LocalIndication provides NoIndication)", re: /\bLocalIndication\s+provides\s+NoIndication\b/ },
  { name: "no-overscroll-root", why: "Overscroll stretch is off at the root (gate 10: LocalOverscrollFactory provides null)", re: /\bLocalOverscrollFactory\s+provides\s+null\b/ },
  { name: "calm-root", why: "One LocalCalm the whole app reads", re: /\bLocalCalm\s+provides\b/ },
];

/* The text of the function around line i: back to the nearest `fun` line, forward to the next one at the same indent or less. */
function fnAround(ls, i) {
  let a = i; while (a > 0 && !/^\s*(?:(?:private|internal|public|inline|override|suspend|@\w+(?:\([^)]*\))?)\s+)*fun\b/.test(ls[a])) a--;
  const ind = (ls[a].match(/^\s*/) || [""])[0].length;
  let b = i + 1; while (b < ls.length && !(ls[b].trim() && (ls[b].match(/^\s*/) || [""])[0].length <= ind && /^\s*(?:(?:private|internal|public|inline|override|suspend|@\w+(?:\([^)]*\))?)\s+)*(?:fun|class|object|val|var)\b/.test(ls[b]))) b++;
  return ls.slice(a, b).join("\n");
}

/* Kotlin text with comments blanked (strings kept: the emoji rule reads them), same line count. */
function strip(src) {
  let out = "", i = 0, n = src.length, str = null;
  while (i < n) {
    const c = src[i], d = src[i + 1];
    if (str) {
      if (str === '"""' ? src.startsWith('"""', i) : c === '"' ) { out += str; i += str.length; str = null; continue; }
      if (c === "\\" && str !== '"""') { out += c + (d || ""); i += 2; continue; }
      out += c; i++; continue;
    }
    if (c === "/" && d === "/") { while (i < n && src[i] !== "\n") { out += " "; i++; } continue; }
    if (c === "/" && d === "*") { let depth = 0; do { if (src.startsWith("/*", i)) { depth++; out += "  "; i += 2; } else if (src.startsWith("*/", i)) { depth--; out += "  "; i += 2; } else { out += src[i] === "\n" ? "\n" : " "; i++; } } while (i < n && depth > 0); continue; }
    if (src.startsWith('"""', i)) { str = '"""'; out += str; i += 3; continue; }
    if (c === '"') { str = '"'; out += c; i++; continue; }
    if (c === "'" ) { const m = src.slice(i).match(/^'(?:\\.|[^\\'])'/); if (m) { out += m[0]; i += m[0].length; continue; } }
    out += c; i++;
  }
  return out;
}

export function scan(files, ok = []) {
  const hits = [], seen = new Set(), all = [];
  for (const [f, src] of files) {
    const t = strip(src), ls = t.split(/\r?\n/), raw = src.split(/\r?\n/);
    all.push(t);
    ls.forEach((l, i) => {
      for (const r of RULES) {
        if (r.skip && r.skip(f)) continue;
        if (r.files && !r.files(f)) continue;
        if (!r.line(l, t, i, ls, raw[i])) continue;
        const k = ok.findIndex(o => o.rule === r.name && o.file === f && raw[i].includes(o.text));
        if (k >= 0) { seen.add(k); continue; }
        hits.push({ rule: r.name, file: f, line: i + 1, text: raw[i].trim().slice(0, 140), why: r.why });
      }
    });
  }
  const whole = all.join("\n");
  for (const p of PRESENCE) if (!p.re.test(whole)) hits.push({ rule: p.name, file: "(the app)", line: 0, text: "missing", why: p.why });
  const stale = ok.filter((o, k) => !seen.has(k));
  return { hits, stale };
}

export function readOk(file) {
  if (!fs.existsSync(file)) return [];
  return fs.readFileSync(file, "utf8").split(/\r?\n/).map(s => s.trim()).filter(s => s && !s.startsWith("#")).map(s => {
    const m = s.match(/^(\S+)\s+(\S+)\s+(.+?)\s+—\s+(.+)$/);
    if (!m) throw new Error("android/house-rules-ok.txt: can't read " + JSON.stringify(s) + " (want: <rule> <path> <text> — <why>)");
    return { rule: m[1], file: m[2], text: m[3], why: m[4], src: s };
  });
}

function kotlinFiles(root) {
  const base = path.join(root, PKG), out = [];
  const walk = d => { for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p); else if (e.name.endsWith(".kt")) out.push([path.relative(base, p).split(path.sep).join("/"), fs.readFileSync(p, "utf8")]);
  } };
  walk(base); return out.sort((a, b) => a[0] < b[0] ? -1 : 1);
}

/* Planted copies: each rule must catch its bad line and pass its good one. */
const PLANTS = {
  "press-motion": ["val p by src.collectIsPressedAsState()", "val p = remember { MutableInteractionSource() }"],
  "ripple": ["Modifier.clickable(src, indication = ripple()) {}", "Modifier.clickable(src, indication = null) {}"],
  "hover": ["Modifier.hoverable(src)", "Modifier.clickable(src, indication = null) {}"],
  "material-tap": ["import androidx.compose.material3.Button", "import androidx.compose.material3.Text"],
  "hex-colour": ["val c = Color(0xFF2F6BFF) // the blue", "val c = Color(0xFF2F6BFF) // --volt (index.html 140)"],
  "emoji-icon": ['Text("🔍")', 'Text("Search 🔍")'],
  "italic": ["TextStyle(fontStyle = FontStyle.Italic)", "TextStyle(fontStyle = FontStyle.Normal)"],
  "uppercase": ['Text(name.uppercase())', 'Text(name)'],
  "font-weight": ["TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Black)", "TextStyle(fontFamily = Fonts.archivo, fontWeight = FontWeight.Black)"],
  "animated-layout": ["val w by animateDpAsState(if (on) 80.dp else 40.dp)", "val a by animateFloatAsState(if (on) 1f else 0f, tween(if (calm) 0 else 180))"],
  "looping-blur": ["val t = rememberInfiniteTransition(label = \"x\")\nBox(Modifier.blur(8.dp))", "Box(Modifier.blur(8.dp))"],
  "scroll-linked": ["val o = state.firstVisibleItemScrollOffset\nBox(Modifier.graphicsLayer { translationY = o * .3f })", "val o = state.firstVisibleItemIndex"],
  "calm": ["fun Spin() {\n    val p = remember { Animatable(0f) }\n}", "fun Spin() {\n    val calm = LocalCalm.current\n    val p = remember { Animatable(if (calm) 1f else 0f) }\n}"],
  "loop-scroll-hold": ["val t = rememberInfiniteTransition(label = \"x\")\nif (state.isScrollInProgress) pause()", "if (state.isScrollInProgress) hide()"],
};
const ROOT_OK = "CompositionLocalProvider(LocalIndication provides NoIndication, LocalOverscrollFactory provides null, LocalCalm provides calm) {}";

function selfTest() {
  let bad = 0;
  for (const r of RULES) {
    const p = PLANTS[r.name];
    if (!p) { console.log(`  ✗ ${r.name}: no plant`); bad++; continue; }
    const f = r.files ? "ui/Plant.kt" : "ui/Plant.kt";
    const caught = scan([[f, p[0]], ["ui/Root.kt", ROOT_OK]]).hits.filter(h => h.rule === r.name).length;
    const clean = scan([[f, p[1]], ["ui/Root.kt", ROOT_OK]]).hits.filter(h => h.rule === r.name).length;
    const ok = caught > 0 && clean === 0;
    if (!ok) bad++;
    console.log(`  ${ok ? "✓" : "✗"} ${r.name}: planted ${caught ? "caught" : "MISSED"}, clean ${clean ? "FLAGGED" : "passes"}`);
  }
  for (const pr of PRESENCE) {
    const missing = scan([["ui/Root.kt", ROOT_OK.replace(pr.re, "")]]).hits.some(h => h.rule === pr.name);
    const present = scan([["ui/Root.kt", ROOT_OK]]).hits.some(h => h.rule === pr.name);
    const ok = missing && !present; if (!ok) bad++;
    console.log(`  ${ok ? "✓" : "✗"} ${pr.name}: taken out ${missing ? "caught" : "MISSED"}, in place ${present ? "FLAGGED" : "passes"}`);
  }
  /* comments never count, an allow-list entry excuses its line, a stale one is named */
  const c = scan([["ui/A.kt", "// Color(0xFF000000) in a comment\n/* Text(\"🔍\") */ val x = 1"], ["ui/Root.kt", ROOT_OK]]).hits.length;
  const okl = [{ rule: "hex-colour", file: "ui/A.kt", text: "Color(0xFF070A14)" }, { rule: "hex-colour", file: "ui/A.kt", text: "Color(0xFF123456)" }];
  const r2 = scan([["ui/A.kt", "val shade = Color(0xFF070A14)"], ["ui/Root.kt", ROOT_OK]], okl);
  const t = [["comments ignored", c === 0], ["allow-list excuses its line", r2.hits.length === 0], ["stale allow-list entry named", r2.stale.length === 1]];
  for (const [n, ok] of t) { if (!ok) bad++; console.log(`  ${ok ? "✓" : "✗"} ${n}`); }
  return bad;
}

const args = process.argv.slice(2);
if (process.argv[1] && import.meta.url.endsWith(path.basename(process.argv[1]))) {
  if (args.includes("--list")) { for (const r of [...RULES, ...PRESENCE]) console.log(`${r.name.padEnd(20)} ${r.why}`); process.exit(0); }
  if (args.includes("--self-test")) { console.log("== android-rules: self-test =="); const b = selfTest(); console.log(b ? `\n${b} failed.` : "\nAll caught."); process.exit(b ? 1 : 0); }
  const ri = args.indexOf("--root"), root = ri >= 0 ? args[ri + 1] : path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "android");
  const okFile = path.join(root, "house-rules-ok.txt");
  const { hits, stale } = scan(kotlinFiles(root), readOk(okFile));
  console.log(`== android-rules: the house rules over ${PKG} ==`);
  for (const h of hits) console.log(`  ✗ ${h.rule}  ${h.file}${h.line ? ":" + h.line : ""}  ${h.text}\n      ${h.why}`);
  for (const s of stale) console.log(`  ✗ stale  android/house-rules-ok.txt: nothing matches "${s.src}" any more: remove the line`);
  if (hits.length || stale.length) console.log("\n(a real break: fix it; meant that way: a line in android/house-rules-ok.txt with the reason)");
  console.log(hits.length + stale.length ? `\n${hits.length + stale.length} problem(s).` : "\nAll clear.");
  process.exit(hits.length + stale.length ? 1 : 0);
}
