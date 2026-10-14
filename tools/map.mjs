/* Code map of index.html. The app grew as one core plus a patch per release, and later patches wrap earlier
   functions (openFull went through 16 layers): this writes down where everything is so nobody has to rediscover it.

     node tools/map.mjs            writes docs/MAP.md (blocks, what each is for, every wrapped function and its layers)
     node tools/map.mjs --dead     prints what looks unused: functions nobody calls, CSS classes / ids nothing uses
                                   (candidates only: check each by hand, some names are built from strings) */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const FILE = process.argv.includes("--file") ? process.argv[process.argv.indexOf("--file") + 1] : "index.html";
const html = fs.readFileSync(path.join(ROOT, FILE), "utf8");
const lineAt = i => html.slice(0, i).split("\n").length;

const blocks = [];
for (const m of html.matchAll(/<(script|style)(\s[^>]*)?>([\s\S]*?)<\/\1>/g)) {
  const body = m[3], start = lineAt(m.index);
  const c = (body.match(/^\s*\/\*([\s\S]*?)\*\//) || [])[1] || "";
  const what = c.replace(/\s+/g, " ").replace(/^=+\s*|\s*=+$/g, "").trim();
  blocks.push({ kind: m[1], core: body.length > 150000, start, end: start + body.split("\n").length, kb: Math.round(body.length / 1024), what, body, at: m.index + m[0].indexOf(body) });
}
const scripts = blocks.filter(b => b.kind === "script"), styles = blocks.filter(b => b.kind === "style");
const blockOf = line => blocks.find(b => line >= b.start && line <= b.end);
const short = (t, n) => t.length > n ? t.slice(0, n - 1).replace(/\s+\S*$/, "") + "…" : t;

/* every wrap: `const _x = fn; fn = function` / `fn = (...) =>` / `window.fn = ` after an earlier definition */
const wraps = new Map();
for (const m of html.matchAll(/const\s+([\w$]+)\s*=\s*([A-Za-z_$][\w$]*)\s*;(?:\s*let\s+[^;]+;)?\s*(?:window\.)?\2\s*=\s*(?:async\s+)?(?:function|\(|[\w$]+\s*=>)/g)) {
  const l = lineAt(m.index); (wraps.get(m[2]) || wraps.set(m[2], []).get(m[2])).push(l);
}
const defs = new Map();
for (const m of html.matchAll(/^(?:\s*)(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) if (!defs.has(m[1])) defs.set(m[1], lineAt(m.index));

if (process.argv.includes("--dead")) {
  const js = scripts.map(b => b.body).join("\n"), css = styles.map(b => b.body).join("\n");
  const markup = html.replace(/<(script|style)(\s[^>]*)?>[\s\S]*?<\/\1>/g, "");
  const count = (t, w) => (t.match(new RegExp("(?<![\\w$])" + w.replace(/\$/g, "\\$") + "(?![\\w$])", "g")) || []).length;
  const words = new Map(); for (const w of (js + markup).match(/[A-Za-z_$][\w$]*/g)) words.set(w, (words.get(w) || 0) + 1);
  console.log("== functions declared and never mentioned again ==");
  for (const m of js.matchAll(/(?:^|[\s;{}])(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g))
    if ((words.get(m[1]) || 0) <= 1) console.log(`  ${m[1]}  (line ${lineAt(html.indexOf(m[0].trim()))})`);
  const bare = js.replace(/\/\*[\s\S]*?\*\//g, "");
  for (const m of bare.matchAll(/^\s{0,2}(?:const|let|var)\s+([A-Za-z_$][\w$]{2,})\s*=/gm))
    if ((words.get(m[1]) || 0) <= 1) console.log(`  ${m[1]}  (a const/let)`);
  console.log("\n== CSS classes that nothing outside CSS mentions ==");
  const textOut = js + markup, seen = new Set();
  for (const m of css.matchAll(/\.(-?[A-Za-z_][\w-]*)/g)) {
    const c = m[1]; if (seen.has(c)) continue; seen.add(c);
    if (/^\d/.test(c)) continue;
    if (textOut.includes(c)) continue;
    /* built from strings, e.g. "shx-" + k: some prefix of it appears in the JS */
    const parts = c.split("-"); let built = false;
    for (let i = parts.length - 1; i > 0 && !built; i--) { const pre = parts.slice(0, i).join("-") + "-"; if (pre.length > 2 && textOut.includes(pre)) built = true; }
    console.log(`  .${c}${built ? "   (maybe built: " + "a prefix appears in the code)" : ""}`);
  }
  console.log("\n== ids in CSS that nothing outside CSS mentions ==");
  const seenI = new Set();
  for (const m of css.matchAll(/#([A-Za-z_][\w-]*)(?=[\s.:,{\[>~+)])/g)) { const c = m[1]; if (seenI.has(c) || /^[0-9a-f]{3,8}$/i.test(c)) continue; seenI.add(c); if (!textOut.includes(c)) console.log(`  #${c}`); }
  process.exit(0);
}

const out = [];
out.push(`# Code map of index.html`, ``,
  `Made by \`node tools/map.mjs\` (run it again after a release; line numbers move). ${html.split("\n").length} lines, ` +
  `${Math.round(html.length / 1024)} KB, ${scripts.length} scripts, ${styles.length} styles.`, ``,
  `How the file is built: one core style (line ${styles[0].start}) and one core script (the biggest block below), then one ` +
  `patch per release, oldest first. A patch adds its own code and often wraps a core function ` +
  `(\`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }\`), so the **last** wrap runs first. ` +
  `To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.`, ``,
  `## Wrapped functions (most layers first)`, ``, `| function | defined | layers (line: block) |`, `|---|---|---|`);
for (const [fn, ls] of [...wraps].sort((a, b) => b[1].length - a[1].length)) {
  if (ls.length < 2 && !["save", "closeFull", "gdMarkup"].includes(fn)) continue;
  const tag = l => { const b = blockOf(l); const v = b && (b.core ? "core" : (b.what.match(/\bv\d{3}\b/) || [])[0]); return `${l}${v ? " " + v : ""}`; };
  out.push(`| \`${fn}\` | ${defs.get(fn) || "?"} | ${ls.length}: ${ls.map(tag).join(", ")} |`);
}
out.push(``, `## Screen-change hooks (onNav / onSettings, since v269)`, ``,
  `goTab / goSub / openFull run every \`onNav("before"|"after", fn)\` hook and openSettings every \`onSettings(...)\` hook ` +
  `(one wrap each, defined at line ${lineAt(html.indexOf("function onNav("))}, in registration order below). New code registers here; ` +
  `\`kit.mjs syntax\` fails on a new wrap of those four.`, ``);
for (const m of html.matchAll(/^\s*on(?:Nav|Settings)\("(before|after)", *(?:\(|\w+ *=>).*$/gm)) { const l = lineAt(m.index + m[0].search(/on(?:Nav|Settings)\(/)); const b = blockOf(l); out.push(`- ${l}${b && !b.core ? " " + ((b.what.match(/\bv\d{3}\b/) || [""])[0]) : ""}: \`${m[0].trim().slice(0, 110)}\``); }
out.push(``, `## Blocks`, ``, `| lines | kind | KB | what |`, `|---|---|---|---|`);
for (const b of blocks) out.push(`| ${b.start}–${b.end} | ${b.kind} | ${b.kb} | ${b.core ? "**CORE** — " : ""}${short(b.what, 150).replace(/\|/g, "/") || "—"} |`);
fs.writeFileSync(path.join(ROOT, "docs/MAP.md"), out.join("\n") + "\n");
console.log(`docs/MAP.md: ${blocks.length} blocks, ${[...wraps.values()].reduce((a, l) => a + l.length, 0)} wraps of ${wraps.size} functions`);
