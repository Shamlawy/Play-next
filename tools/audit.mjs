#!/usr/bin/env node
/* Code health audit for index.html (the owner, Oct 2026: "create something that can audit the code so it doesn't pile up
   in a bad way"). It counts the things that pile up when a feature is patched on top of another, and keeps a ratchet:
   tools/audit-budget.txt holds the highest number allowed for each. A change may lower a number, never raise it.

     node tools/audit.mjs                 the report: every measure, its budget, and the worst offenders
     node tools/audit.mjs --check         exit 1 if any measure is over its budget (kit.mjs all and GitHub run this)
     node tools/audit.mjs --lower         after a cleanup: set every budget to today's number where that's lower
     node tools/audit.mjs --list <name>   every offender for one measure (e.g. --list cssOverridden)
     node tools/audit.mjs --fix-css       removes CSS declarations that can never apply (an identical selector later in the
                                          same @media sets the same property); then run kit.mjs all
   Raising a budget is a decision, not a fix: edit audit-budget.txt by hand and say why in the commit. */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2), opt = k => { const i = args.indexOf("--" + k); return i >= 0 ? args[i + 1] : undefined; };
const FILE = path.resolve(ROOT, opt("file") || "index.html"), BUDGET = path.join(ROOT, "tools", "audit-budget.txt");
let html = fs.readFileSync(FILE, "utf8");

/* ---------- blocks: <script> / <style> in order (a <style> inside a script's string stays part of the script) ---------- */
function blocksOf(src) {
  const out = [];
  for (const m of src.matchAll(/<(script|style)(\s[^>]*)?>([\s\S]*?)<\/\1>/g))
    out.push({ kind: m[1], body: m[3], at: m.index + m[0].indexOf(m[3], m[1].length + 2) });
  return out;
}
const lineAt = i => html.slice(0, i).split("\n").length;

/* ---------- CSS: a small parser that keeps positions ---------- */
/* every style rule with its @media/@supports/@container context and each declaration's place in the file */
function cssRules(css, base) {
  /* positions are read from a masked copy: comments, quoted strings and anything inside (...) (url(data:…;…), calc) are
     blanked to the same length, so a ; { or } in them never splits a rule */
  let clean = "";
  for (let i = 0, depth = 0, q = ""; i < css.length; i++) {
    const c = css[i];
    if (q) { clean += c === "\n" ? "\n" : "x"; if (c === "\\") { clean += "x"; i++; } else if (c === q) q = ""; continue; }
    if (!depth && c === "/" && css[i + 1] === "*") { const e = css.indexOf("*/", i + 2), j = e < 0 ? css.length : e + 2; clean += css.slice(i, j).replace(/[^\n]/g, " "); i = j - 1; continue; }
    if (c === '"' || c === "'") { q = c; clean += "x"; continue; }
    if (c === "(") { depth++; clean += "("; continue; }
    if (c === ")") { depth = Math.max(0, depth - 1); clean += ")"; continue; }
    clean += depth && c !== "\n" ? "x" : c;
  }
  const rules = [];
  const walk = (from, to, ctx) => {
    let i = from;
    while (i < to) {
      const open = clean.indexOf("{", i); if (open < 0 || open >= to) return;
      const head = clean.slice(i, open), semi = head.lastIndexOf(";");   /* a stray `@import x;` before the rule */
      const sel = (semi >= 0 ? head.slice(semi + 1) : head).trim();
      let depth = 1, j = open + 1; while (j < to && depth) { if (clean[j] === "{") depth++; else if (clean[j] === "}") depth--; j++; }
      const close = j - 1;
      if (sel.startsWith("@")) {
        if (/^@(media|supports|container)\b/.test(sel)) walk(open + 1, close, ctx + " " + css.slice(i + (semi >= 0 ? semi + 1 : 0), open).replace(/\/\*[\s\S]*?\*\//g, "").trim().replace(/\s+/g, " "));
        /* @keyframes / @font-face / @property / @layer: not cascaded like rules here */
      } else if (sel) {
        const decls = []; let k = open + 1;
        for (const part of clean.slice(open + 1, close).split(";")) {
          const c = part.indexOf(":");
          if (c > 0) {
            const prop = part.slice(0, c).trim().toLowerCase(), val = css.slice(k + c + 1, k + part.length).trim();
            if (/^[-a-z]+$/.test(prop)) decls.push({ prop, val, imp: /!\s*important\s*$/i.test(val), start: base + k, end: base + Math.min(k + part.length + 1, close) });   /* never the rule's own } */
          }
          k += part.length + 1;
        }
        rules.push({ ctx: ctx.trim(), sel: css.slice(i + (semi >= 0 ? semi + 1 : 0), open).replace(/\/\*[\s\S]*?\*\//g, "").trim().replace(/\s+/g, " "), decls, start: base + i + (semi >= 0 ? semi + 1 : 0) + Math.max(0, clean.slice(i + (semi >= 0 ? semi + 1 : 0), open).search(/\S/)), open: base + open, close: base + close });
      }
      i = close + 1;
    }
  };
  walk(0, clean.length, "");
  return rules;
}
/* a later value that only newer browsers understand is a fallback pair, not an override (100vh then 100dvh) */
const MODERN = /dvh|svh|lvh|dvw|svw|lvw|cq[whib]|color-mix|oklch|oklab|\blab\(|\blch\(|round\(|env\(|-webkit-|-moz-|text-wrap|:has/;

/* ---------- the measures ---------- */
function measure() {
  const blocks = blocksOf(html), scripts = blocks.filter(b => b.kind === "script"), styles = blocks.filter(b => b.kind === "style");
  const js = scripts.map(b => b.body).join("\n"), css = styles.map(b => b.body).join("\n");
  const markup = html.replace(/<(script|style)(\s[^>]*)?>[\s\S]*?<\/\1>/g, "");
  const words = new Map(); for (const w of (js + markup).match(/[A-Za-z_$][\w$]*/g) || []) words.set(w, (words.get(w) || 0) + 1);
  const M = {};
  const add = (name, what, list, about) => { M[name] = { what, n: list.length, list, about }; };

  /* wraps: `const _x = fn; fn = function` — every layer is one more place a change can hide */
  const wraps = new Map();
  for (const m of html.matchAll(/const\s+([\w$]+)\s*=\s*([A-Za-z_$][\w$]*)\s*;(?:\s*let\s+[^;]+;)?\s*(?:window\.)?\2\s*=\s*(?:async\s+)?(?:function|\(|[\w$]+\s*=>)/g))
    (wraps.get(m[2]) || wraps.set(m[2], []).get(m[2])).push(lineAt(m.index));
  add("wraps", "wrap layers on functions (a later script replacing a function with one that calls the old one)",
    [...wraps].sort((a, b) => b[1].length - a[1].length).flatMap(([f, ls]) => ls.map(l => `${f} @${l} (${ls.length} layers)`)),
    "fold a wrap into the function it wraps, or use a hook (onNav / onSettings)");
  add("deepWraps", "functions wrapped 4 or more times", [...wraps].filter(([, ls]) => ls.length >= 4).map(([f, ls]) => `${f}: ${ls.length} layers (${ls.join(", ")})`),
    "fold the layers into one function");

  /* dead code */
  const dead = [];
  for (const m of js.matchAll(/(?:^|[\s;{}])(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g)) if ((words.get(m[1]) || 0) <= 1) dead.push(m[1] + "()");
  for (const m of js.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/^\s{0,2}(?:const|let|var)\s+([A-Za-z_$][\w$]{2,})\s*=/gm)) if ((words.get(m[1]) || 0) <= 1) dead.push(m[1]);
  add("deadCode", "functions and top-level consts nothing uses", dead, "delete them");
  const textOut = js + markup, seen = new Set(), deadCss = [];
  for (const m of css.matchAll(/\.(-?[A-Za-z_][\w-]*)/g)) {
    const c = m[1]; if (seen.has(c) || /^\d/.test(c)) continue; seen.add(c);
    if (textOut.includes(c)) continue;
    const parts = c.split("-"); let built = false;
    for (let i = parts.length - 1; i > 0 && !built; i--) { const pre = parts.slice(0, i).join("-") + "-"; if (pre.length > 2 && textOut.includes(pre)) built = true; }
    /* built from a template: k-${kind}, n${count} */
    for (let i = 1; i < c.length && !built; i++) if (textOut.includes(c.slice(0, i) + "${")) built = true;
    if (!built) deadCss.push("." + c);
  }
  add("deadCss", "CSS classes nothing in the page or the scripts uses", deadCss, "delete their rules");

  /* CSS that can never apply: an identical selector later in the same context sets the same property */
  const rules = styles.flatMap(b => cssRules(b.body, b.at)), last = new Map(), over = [];
  rules.forEach((r, ri) => r.decls.forEach((d, di) => { const k = r.ctx + "|" + r.sel + "|" + d.prop; (last.get(k) || last.set(k, []).get(k)).push({ r, ri, d, di }); }));
  for (const [k, occ] of last) {
    if (occ.length < 2) continue;
    const imp = occ.filter(o => o.d.imp), win = (imp.length ? imp : occ).at(-1);
    for (const o of occ) {
      if (o === win || o.ri === win.ri) continue;                   /* fallback pairs inside one rule stay */
      if (MODERN.test(win.d.val) || MODERN.test(o.d.val)) continue;
      over.push({ ...o, key: k, by: lineAt(win.d.start) });
    }
  }
  add("cssOverridden", "CSS declarations that never apply (the same selector sets the same property again later)",
    over.map(o => `line ${lineAt(o.d.start)}: ${o.r.ctx ? o.r.ctx + " " : ""}${o.r.sel.slice(0, 70)} { ${o.d.prop} } → beaten at line ${o.by}`), "node tools/audit.mjs --fix-css");
  M.cssOverridden._fix = over;
  /* the same selector defined in many places: one look spread over many patches */
  const bySel = new Map(); rules.forEach(r => { const k = r.ctx + "|" + r.sel; bySel.set(k, (bySel.get(k) || 0) + 1); });
  add("cssScattered", "selectors defined in 4 or more separate rules", [...bySel].filter(([, n]) => n >= 4).sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k.replace(/^\|/, "")} ×${n}`),
    "merge them into one rule");
  add("important", "!important in CSS", [...css.matchAll(/!\s*important/g)].map(m => "!important"), "a more specific selector instead");
  const hex = []; for (const r of rules) for (const d of r.decls) if (!d.prop.startsWith("--") && /#[0-9a-f]{3,8}\b/i.test(d.val)) hex.push(`line ${lineAt(d.start)}: ${r.sel.slice(0, 50)} { ${d.prop}: ${d.val.slice(0, 40)} }`);
  add("hexColours", "colours written as hex in CSS rules (house rule v181: var(--volt) / rgba(var(--vrgb), a) / the --u-* tokens)", hex, "use the tokens");

  /* things that keep running */
  add("intervals", "setInterval timers", [...js.matchAll(/setInterval\(/g)].map(m => `line ${lineAt(html.indexOf(m[0], 0))}`).map((x, i) => "setInterval #" + (i + 1)), "a timer that runs only while it's needed");
  const inf = [...css.matchAll(/animation[^;{}]*\binfinite\b/g)].length + [...js.matchAll(/iterations:\s*Infinity/g)].length;
  add("endlessAnimations", "animations that never stop (CSS `infinite`, Web Animations `Infinity`)", Array.from({ length: inf }, (_, i) => "#" + (i + 1)), "stop them when nothing should move (speed rule)");
  add("subtreeObservers", "MutationObservers watching a whole subtree", [...js.matchAll(/subtree:\s*true/g)].map((m, i) => "#" + (i + 1)), "watch the one element that changes");

  /* settings that point at nothing */
  const gk = (html.match(/const GROUP_KEYS = \{([\s\S]*?)\n\};/) || [])[1] || "", orphan = [];
  const rest = html.replace(gk, "");
  for (const k of new Set([...gk.matchAll(/"([A-Za-z]\w*)"(?=[,\]])/g)].map(m => m[1])))
    if (!new RegExp(`\\bS\\.${k}\\b|S\\[["']${k}["']\\]|["']${k}["']`).test(rest)) orphan.push(k);
  add("orphanSettings", "Settings keys listed in GROUP_KEYS that nothing reads", orphan, "remove them from GROUP_KEYS (and the switch)");

  M._size = { lines: html.split("\n").length, kb: Math.round(html.length / 1024), scripts: scripts.length, styles: styles.length };
  return M;
}

/* ---------- budget (the ratchet) ---------- */
const readBudget = () => { const b = {}; if (fs.existsSync(BUDGET)) for (const l of fs.readFileSync(BUDGET, "utf8").split("\n")) { const m = l.match(/^(\w+)\s+(\d+)/); if (m) b[m[1]] = +m[2]; } return b; };
function writeBudget(b, M) {
  const lines = ["# Code health budget for tools/audit.mjs: the highest number allowed for each measure.",
    "# `node tools/audit.mjs --lower` lowers these after a cleanup. Raising one is a decision: edit by hand and say why in the commit.", ""];
  for (const k of Object.keys(M).filter(k => !k.startsWith("_"))) lines.push(`${k.padEnd(18)} ${b[k]}`.padEnd(26) + `# ${M[k].what}`);
  fs.writeFileSync(BUDGET, lines.join("\n") + "\n");
}

/* ---------- verify: the CSS that wins is the same in two copies (after --fix-css) ---------- */
function winners(src) {
  const w = new Map();
  for (const b of blocksOf(src).filter(b => b.kind === "style")) for (const r of cssRules(b.body, b.at)) for (const d of r.decls) {
    const k = r.ctx + "|" + r.sel + "|" + d.prop, cur = w.get(k);
    if (!cur || d.imp || !cur.imp) w.set(k, { v: d.val.replace(/\s+/g, " "), imp: d.imp });
  }
  return w;
}
if (opt("verify")) {
  const a = winners(fs.readFileSync(opt("verify"), "utf8")), b = winners(html), diff = [];
  for (const [k, v] of a) { const n = b.get(k); if (!n || n.v !== v.v || n.imp !== v.imp) diff.push(`${k}: ${v.v} → ${n ? n.v : "(gone)"}`); }
  for (const k of b.keys()) if (!a.has(k)) diff.push(`${k}: new`);
  const keyframes = s => (s.match(/@keyframes\s+[\w-]+/g) || []).sort().join();
  if (keyframes(fs.readFileSync(opt("verify"), "utf8")) !== keyframes(html)) diff.push("@keyframes changed");
  console.log(diff.length ? diff.slice(0, 40).join("\n") + `\n${diff.length} difference(s) in what the CSS applies` : `same CSS applies (${a.size} selector/property pairs)`);
  process.exit(diff.length ? 1 : 0);
}

/* ---------- fix: drop CSS declarations that never apply ---------- */
if (args.includes("--fix-css")) {
  const M = measure(), over = M.cssOverridden._fix;
  const byRule = new Map(); for (const o of over) (byRule.get(o.r) || byRule.set(o.r, []).get(o.r)).push(o.d);
  const cuts = [];
  for (const [r, ds] of byRule) {
    if (ds.length === r.decls.length) {   /* nothing left: the whole rule goes (selector to closing brace) */
      let s = r.start; while (s > 0 && /[ \t]/.test(html[s - 1])) s--;
      let e = r.close + 1; if (html[e] === "\n" && (s === 0 || html[s - 1] === "\n")) e++;
      cuts.push([s, e]);
    } else for (const d of ds) { let s = d.start; while (/[ \t\n]/.test(html[s]) && s < d.end) s++; cuts.push([s, d.end]); }
  }
  cuts.sort((a, b) => b[0] - a[0]);
  let prev = Infinity; for (const [s, e] of cuts) { if (e > prev) continue; html = html.slice(0, s) + html.slice(e).replace(/^[ \t]+(?=\})/, ""); prev = s; }
  fs.writeFileSync(FILE, html);
  console.log(`removed ${over.length} CSS declarations that never applied (${cuts.length} cuts). Now: node tools/kit.mjs all`);
  process.exit(0);
}

const M = measure(), B = readBudget(), names = Object.keys(M).filter(k => !k.startsWith("_"));
if (opt("list")) { const m = M[opt("list")]; if (!m) { console.log("measures: " + names.join(", ")); process.exit(1); } console.log(m.list.join("\n")); process.exit(0); }
if (args.includes("--lower") || !fs.existsSync(BUDGET)) {
  const nb = {}; for (const k of names) nb[k] = B[k] == null ? M[k].n : Math.min(B[k], M[k].n);
  writeBudget(nb, M); console.log("tools/audit-budget.txt: " + names.map(k => `${k} ${nb[k]}`).join(", "));
  process.exit(0);
}
const s = M._size; let bad = 0;
console.log(`\n== code health (${path.basename(FILE)}: ${s.lines} lines, ${s.kb} KB, ${s.scripts} scripts, ${s.styles} styles) ==`);
for (const k of names) {
  const m = M[k], b = B[k], over = b != null && m.n > b; if (over) bad++;
  const mark = over ? "✗" : b != null && m.n < b ? "↓" : "·";
  console.log(`  ${mark} ${k.padEnd(17)} ${String(m.n).padStart(5)}${b != null ? " / " + b : ""}  ${m.what}`);
  if (over || (!args.includes("--check") && m.n && args.includes("--all"))) {
    for (const x of m.list.slice(0, over ? 12 : 5)) console.log(`        ${x}`);
    if (over) console.log(`        → ${m.about} (or: node tools/audit.mjs --list ${k})`);
  }
}
if (names.some(k => B[k] != null && M[k].n < B[k])) console.log(`\n  ↓ = better than the budget: lock it in with node tools/audit.mjs --lower`);
console.log(bad ? `\n${bad} measure(s) got worse. Fix them, or raise the budget by hand with a reason.` : "\nNothing piled up.");
process.exit(args.includes("--check") && bad ? 1 : 0);
