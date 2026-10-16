#!/usr/bin/env node
/* The design check (.claude/skills/play-next-design, Oct 2026): what this branch adds to index.html, held against the house style.
     node tools/design-check.mjs                 lines added since the branch left origin/main (committed or not)
     node tools/design-check.mjs --base=HEAD~3   against another point
   "must" lines break a rule (exit 1); "note" lines are for you to judge. It also runs `kit.mjs syntax`, which checks the
   whole file for press animation, sticky :hover and extra backgrounds. Line rules are release-guard's (hooks/rules.ts),
   so both say the same thing. */
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { scanLines, scanBlock } from "../.claude/skills/release-guard/hooks/rules.ts";
import { forText } from "./taste.mjs";

const arg = process.argv.find(a => a.startsWith("--base="));
const git = (...a) => execFileSync("git", a, { encoding: "utf8", maxBuffer: 64 << 20 });
const ref = arg ? arg.slice(7) : "origin/main";
let base;
try { base = git("merge-base", ref, "HEAD").trim(); }
catch { // a shallow clone may not reach the fork point (git fetch origin main fixes it): compare with the ref itself
  console.log(`(no merge-base with ${ref}: comparing with ${ref} itself; \`git fetch origin main\` if that looks wrong)`);
  base = git("rev-parse", ref).trim();
}
const diff = git("diff", "--unified=0", base, "--", "index.html");
const html = readFileSync("index.html", "utf8").split("\n");
const old = git("show", `${base}:index.html`);

// Added lines with their line numbers in the new file, and removed lines.
const added = [], removed = [];
let at = 0;
for (const l of diff.split("\n")) {
  const h = l.match(/^@@ -\d+(?:,\d+)? \+(\d+)/);
  if (h) { at = +h[1]; continue; }
  if (l.startsWith("+++") || l.startsWith("---")) continue;
  if (l.startsWith("+")) added.push({ n: at++, t: l.slice(1) });
  else if (l.startsWith("-")) removed.push(l.slice(1));
}

// Which lines of the new file sit inside a <style> block.
const inStyle = new Set();
for (let i = 0, on = false; i < html.length; i++) {
  if (/<style\b/i.test(html[i])) on = true;
  if (on) inStyle.add(i + 1);
  if (/<\/style>/i.test(html[i])) on = false;
}

const must = [], note = [];
const say = (list, n, text) => list.push(`  line ${n}: ${text}`);
// Shared pieces every screen restyles on purpose (html.ui2 .btn …): reusing them is the point, not a clash.
const SHARED = new Set(["ui2", "paper", "btn", "ghost", "danger", "chip", "warn", "hot", "row", "stat", "empty", "hit", "tag", "on", "open", "wrap", "tabbar", "grp"]);
const clash = new Set();
const MUST = new Set(["speed", "press", "date", "blur", "zindex", "heading", "doctype"]);
for (const a of added) {
  for (const f of scanLines([a.t])) {
    if (f.rule === "heading" && /html:not\(\.ui2\)/.test(a.t)) continue; // the Arcade look keeps its caps on purpose
    say(MUST.has(f.rule) ? must : note, a.n, f.text);
  }
  if (!inStyle.has(a.n)) continue;
  const font = a.t.match(/font-family\s*:\s*([^;}]+)/);
  if (font && !/^\s*(inherit|"Poppins"|var\()/.test(font[1]))
    say(note, a.n, `font ${font[1].trim()}: new UI is Poppins ("Poppins", system-ui, sans-serif), weights 500/600/700 (v188)`);
  const bg = a.t.replace(/var\([^()]*\)/g, "").match(/background(?:-color)?\s*:[^;{}]*?(#[0-9a-fA-F]{3,8})\b/);
  if (bg) say(note, a.n, `surface ${bg[1]}: use var(--u-pan) / var(--u-card) / var(--u-card2) so it matches the rest`);
  const sel = a.t.includes("{") ? a.t.slice(0, a.t.indexOf("{")) : /^\s*[^:;]+,\s*$/.test(a.t) ? a.t : "";
  for (const m of sel.replace(/\[[^\]]*\]|\([^)]*\)/g, "").matchAll(/\.([a-zA-Z_][\w-]*)/g)) {
    const c = m[1], re = new RegExp(`\\.${c}\\b|class(?:Name)?\\s*=\\s*["'\`][^"'\`]*\\b${c}\\b`);
    if (SHARED.has(c) || clash.has(c)) continue;
    clash.add(c);
    if (re.test(old) && !removed.some(r => r.includes(c)))
      say(note, a.n, `.${c} already exists before this change: reusing it on purpose? (grep \\.${c}\\b; clashes: .aw v164, .dash v195, .pl v204)`);
  }
}
for (const f of scanBlock(added.map(a => a.t).join("\n"))) note.push(`  ${f.text}`);

let syntax = "";
try { execFileSync(process.execPath, ["tools/kit.mjs", "syntax"], { encoding: "utf8", stdio: "pipe" }); }
catch (e) { syntax = `${e.stdout || ""}${e.stderr || ""}`.trim(); }

const uniq = l => [...new Set(l)];
console.log(`design check: ${added.length} added line(s) in index.html since ${base.slice(0, 8)}`);
if (must.length) console.log(`\nmust change (${uniq(must).length}):\n${uniq(must).join("\n")}`);
if (syntax) console.log(`\nkit.mjs syntax failed:\n${syntax.split("\n").map(l => "  " + l).join("\n")}`);
if (note.length) console.log(`\nnotes (${uniq(note).length}):\n${uniq(note).join("\n")}`);
if (!must.length && !syntax && !note.length) console.log("nothing against the house style");
/* the owner's past calls about the screens this change touches (taste.md): read them before calling it done */
const taste = added.length ? forText(added.map(a => a.t).join("\n")) : [];
if (taste.length) console.log(`\nthe owner said this about these screens (node tools/taste.mjs list):\n${taste.map(t => `  ${t.id} ${t.screen}: ${t.rule}${t.check && !/^none/.test(t.check) ? ` [${t.check}]` : ""}`).join("\n")}`);
console.log("\nThen look at it: node tools/kit.mjs eye (the design eye on every screen), screenshots at 412 and 859, touch-empty.mjs, kit.mjs perf (see the skill's step 7).");
process.exit(must.length || syntax ? 1 : 0);
