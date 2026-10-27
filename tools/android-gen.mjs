/* The Android app's content from index.html, never typed twice (docs/ANDROID-PLAN.md gate 7, critic G12).
   Reads the web app's own tables (top-level const literals, the :root and html.ui2 colour tokens, the tab bar's icons)
   and writes them as Kotlin into the app's build folder (generated, never committed). Gradle runs it before every
   compile (task pnGen in android/app/build.gradle.kts); a table that disappears or changes shape fails the build here,
   with its name, instead of drifting quietly.
   node tools/android-gen.mjs [--out <dir>] [--page index.html]   (default out: android/app/build/generated/pn) */
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const arg = n => { const i = process.argv.indexOf("--" + n); return i > 0 ? process.argv[i + 1] : null; };
const PAGE = path.resolve(arg("page") || path.join(ROOT, "index.html"));
const OUT = path.resolve(arg("out") || path.join(ROOT, "android/app/build/generated/pn"));
const src = fs.readFileSync(PAGE, "utf8");
const fail = m => { console.error("android-gen: " + m); process.exit(1); };

/* ---------- top-level const statements, evaluated with nothing in scope ---------- */
/* From "const NAME" to its ";" at depth 0, skipping strings, template literals and comments. */
function statement(name) {
  const m = new RegExp("^const (?:[A-Za-z_$][\\w$]* = [^\\n]*?, )*?" + name + " = ", "m").exec(src)
    || new RegExp("^const " + name + " = ", "m").exec(src);
  if (!m) fail(`no top-level const ${name} in index.html`);
  let i = m.index, depth = 0;
  for (; i < src.length; i++) {
    const c = src[i];
    if (c === '"' || c === "'" || c === "`") { for (i++; i < src.length && src[i] !== c; i++) if (src[i] === "\\") i++; continue; }
    if (c === "/" && src[i + 1] === "/") { i = src.indexOf("\n", i); continue; }
    if (c === "/" && src[i + 1] === "*") { i = src.indexOf("*/", i) + 1; continue; }
    if ("([{".includes(c)) depth++;
    else if (")]}".includes(c)) depth--;
    else if (c === ";" && depth === 0) return src.slice(m.index, i + 1);
  }
  fail(`const ${name}: no end`);
}
/* Only literals pass: any name the table refers to is a ReferenceError in the empty context. */
function table(name) {
  const ctx = vm.createContext(Object.create(null));
  try { vm.runInContext(statement(name).replace(/^const /, "var "), ctx, { timeout: 1000 }); }
  catch (e) { fail(`const ${name} is no longer a plain literal (${e.message})`); }
  return JSON.parse(JSON.stringify(ctx[name]));
}

/* ---------- shapes: a change here is a change the app must follow ---------- */
const HEX = /^#[0-9A-Fa-f]{6}$/;
const need = (ok, what) => { if (!ok) fail(what); };
const PALETTE = table("PALETTE");
need(Array.isArray(PALETTE) && PALETTE.length >= 16 && PALETTE.every(h => HEX.test(h)), "PALETTE: a list of #RRGGBB colours (16+)");
const THEMES = table("THEMES");
need(THEMES && THEMES.midnight && Object.values(THEMES).every(t => typeof t.n === "string" && HEX.test(t.a) && HEX.test(t.b)),
  "THEMES: { key: { n, a: #hex, b: #hex } } with midnight");
const BG_BASE = table("BG_BASE"), BG_TINT = table("BG_TINT"), BG_GRAD = table("BG_GRAD"), BG_BASES = table("BG_BASES");
need(HEX.test(BG_BASE) && HEX.test(BG_TINT) && Number.isInteger(BG_GRAD) && BG_GRAD >= 0 && BG_GRAD <= 100, "BG_BASE / BG_TINT / BG_GRAD");
need(Array.isArray(BG_BASES) && BG_BASES.length && BG_BASES.every(h => HEX.test(h)) && BG_BASES[0] === BG_BASE, "BG_BASES: hex list, the default first");
const CB_ALPHA = table("CB_ALPHA");
need(typeof CB_ALPHA === "string" && CB_ALPHA.length === 32 && new Set(CB_ALPHA).size === 32, "CB_ALPHA: 32 different symbols");
const BK_SECRET = table("BK_SECRET");
need(Array.isArray(BK_SECRET) && BK_SECRET.every(k => typeof k === "string") && BK_SECRET.includes("rawgKey"), "BK_SECRET: a list of key names");
const STATES = table("STATES");
need(Array.isArray(STATES) && STATES.every(s => Array.isArray(s) && s.length === 2) && STATES[0][0] === "upcoming", "STATES: [[key, label]…], upcoming first");
const APP_V = (/^const APP_V = "(\d+)"/m.exec(src) || [])[1];
need(APP_V, "APP_V");

/* ---------- colour tokens (CSS custom properties) ---------- */
function cssVars(selector) {
  const m = new RegExp("(^|\\n)\\s*" + selector.replace(/[.]/g, "\\.") + "\\s*\\{([^}]*)\\}").exec(src);
  if (!m) fail(`no "${selector} { … }" block`);
  return Object.fromEntries([...m[2].matchAll(/--([\w-]+):\s*([^;]+);/g)].map(x => [x[1], x[2].trim()]));
}
function argb(v, name) {
  let m = /^#([0-9a-f]{6})$/i.exec(v);
  if (m) return "0xFF" + m[1].toUpperCase();
  m = /^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$/.exec(v);
  if (m) return "0x" + [Math.round(+m[4] * 255), +m[1], +m[2], +m[3]].map(n => n.toString(16).padStart(2, "0")).join("").toUpperCase();
  fail(`colour token --${name}: "${v}" isn't #RRGGBB or rgba()`);
}
const ROOTV = cssVars(":root"), UI2 = cssVars("html.ui2");
const COLOURS = {};
for (const k of ["void", "ink", "steel", "edge", "volt", "volt2", "ice", "mute", "hot"]) { need(ROOTV[k], `:root --${k}`); COLOURS[k] = argb(ROOTV[k], k); }
for (const k of ["u-pan", "u-card", "u-card2", "u-line", "u-line2"]) { need(UI2[k], `html.ui2 --${k}`); COLOURS[k] = argb(UI2[k], k); }
const RADII = {};
for (const k of ["u-r", "u-rs"]) { const m = /^(\d+)px$/.exec(UI2[k] || ""); need(m, `html.ui2 --${k}: Npx`); RADII[k] = +m[1]; }

/* ---------- the tab bar's icons (the web nav's inline SVGs, v247 style) ---------- */
const NAV = (/\n  <nav>([\s\S]*?)<\/nav>/.exec(src) || [])[1];
need(NAV, "the <nav> tab bar");
const num = s => +s;
const r2 = n => +n.toFixed(4);
/* every shape becomes path data, so the app draws them all the same way */
function shapeD(tag, at) {
  if (tag === "path") return at.d;
  if (tag === "circle") {
    const cx = num(at.cx), cy = num(at.cy), r = num(at.r);
    return `M${r2(cx - r)} ${cy}a${r} ${r} 0 1 0 ${r2(2 * r)} 0a${r} ${r} 0 1 0 ${r2(-2 * r)} 0z`;
  }
  if (tag === "rect") {
    const x = num(at.x || 0), y = num(at.y || 0), w = num(at.width), h = num(at.height), rx = Math.min(num(at.rx || at.ry || 0), w / 2, h / 2);
    if (!rx) return `M${x} ${y}h${w}v${h}h${-w}z`;
    return `M${r2(x + rx)} ${y}h${r2(w - 2 * rx)}a${rx} ${rx} 0 0 1 ${rx} ${rx}v${r2(h - 2 * rx)}a${rx} ${rx} 0 0 1 ${-rx} ${rx}` +
      `h${r2(-(w - 2 * rx))}a${rx} ${rx} 0 0 1 ${-rx} ${-rx}v${r2(-(h - 2 * rx))}a${rx} ${rx} 0 0 1 ${rx} ${-rx}z`;
  }
  fail(`tab icon: a <${tag}> (only path, rect and circle are drawn)`);
}
function icon(svg, what) {
  need(/viewBox="0 0 24 24"/.test(svg), `${what}: a 24x24 viewBox`);
  const parts = [...svg.matchAll(/<(path|rect|circle|ellipse|polygon|line|g)\b([^>]*?)\/?>/g)].map(([, tag, a]) => {
    const at = Object.fromEntries([...a.matchAll(/([\w-]+)="([^"]*)"/g)].map(x => [x[1], x[2]]));
    const alpha = at.opacity === undefined ? 1 : +at.opacity;
    need(alpha === 1 || alpha === 0.35, `${what}: a part at opacity ${at.opacity} (1 or .35 only)`);
    return { d: shapeD(tag, at), alpha, evenOdd: at["fill-rule"] === "evenodd" };
  });
  need(parts.length, `${what}: no shapes`);
  return parts;
}
const TABS = [...NAV.matchAll(/<button data-tab="(\w+)"[^>]*>(<svg[\s\S]*?<\/svg>)<span>([^<]+)<\/span><\/button>/g)]
  .map(([, key, svg, label]) => ({ key, label, parts: icon(svg, `tab ${key}`) }));
need(TABS.map(t => t.key).join() === "home,games,plan,taste,add", `tabs: home, games, plan, taste, add (found ${TABS.map(t => t.key)})`);
const GEAR = (/<button id="gear"[^>]*>(<svg[\s\S]*?<\/svg>)/.exec(NAV) || [])[1];
need(GEAR, "the Settings gear in the tab bar");
const ICONS = [...TABS.map(t => [t.key, t.parts]), ["settings", icon(GEAR, "gear")]];

/* ---------- Kotlin ---------- */
const ks = s => JSON.stringify(s).replace(/\$/g, "\\$");   // a JSON string is a valid Kotlin string once $ is escaped
const kl = list => "listOf(" + list.join(", ") + ")";
const lines = [
  "// Generated by tools/android-gen.mjs from index.html (web v" + APP_V + "). Don't edit: change index.html and rebuild.",
  "package io.github.shamlawy.playnext.gen",
  "",
  "/** One drawn part of a 24x24 icon: path data, its alpha (1 or .35, the v247 style) and its fill rule. */",
  "class IconPart(val d: String, val alpha: Float, val evenOdd: Boolean)",
  "",
  "class WebTheme(val key: String, val name: String, val a: Long, val b: Long)",
  "",
  "class WebTab(val key: String, val label: String)",
  "",
  "object Web {",
  `    const val APP_V = ${ks(APP_V)}`,
  "",
  "    // colour tokens: :root and html.ui2 (ARGB)",
  ...Object.entries(COLOURS).map(([k, v]) => `    const val ${k.toUpperCase().replace(/-/g, "_")} = ${v}L`),
  `    const val U_R = ${RADII["u-r"]} // px = dp`,
  `    const val U_RS = ${RADII["u-rs"]}`,
  "",
  "    // the background (v276/v277)",
  `    const val BG_BASE = ${argb(BG_BASE)}L`,
  `    const val BG_TINT = ${argb(BG_TINT)}L`,
  `    const val BG_GRAD = ${BG_GRAD}`,
  `    val BG_BASES: List<Long> = ${kl(BG_BASES.map(h => argb(h) + "L"))}`,
  `    val PALETTE: List<Long> = ${kl(PALETTE.map(h => argb(h) + "L"))}`,
  `    val THEMES: List<WebTheme> = listOf(\n${Object.entries(THEMES).map(([k, t]) => `        WebTheme(${ks(k)}, ${ks(t.n)}, ${argb(t.a)}L, ${argb(t.b)}L),`).join("\n")}\n    )`,
  "",
  "    // the cloud backup (v211)",
  `    const val CB_ALPHA = ${ks(CB_ALPHA)}`,
  `    val BK_SECRET: List<String> = ${kl(BK_SECRET.map(ks))}`,
  `    val STATES: List<Pair<String, String>> = ${kl(STATES.map(([k, n]) => `${ks(k)} to ${ks(n)}`))}`,
  "",
  "    // the tab bar (index.html <nav>): the five tabs in order, then Settings",
  `    val TABS: List<WebTab> = ${kl(TABS.map(t => `WebTab(${ks(t.key)}, ${ks(t.label)})`))}`,
  "    val ICONS: Map<String, List<IconPart>> = mapOf(",
  ...ICONS.map(([k, parts]) => `        ${ks(k)} to listOf(\n${parts.map(p => `            IconPart(${ks(p.d)}, ${p.alpha}f, ${p.evenOdd}),`).join("\n")}\n        ),`),
  "    )",
  "}",
  "",
];
const file = path.join(OUT, "io/github/shamlawy/playnext/gen/Web.kt");
fs.mkdirSync(path.dirname(file), { recursive: true });
const text = lines.join("\n");
if (!fs.existsSync(file) || fs.readFileSync(file, "utf8") !== text) fs.writeFileSync(file, text);
console.log(`android-gen: web v${APP_V} → ${path.relative(ROOT, file)} (${Object.keys(COLOURS).length} colours, ${PALETTE.length} palette, ${Object.keys(THEMES).length} themes, ${ICONS.length} icons)`);
