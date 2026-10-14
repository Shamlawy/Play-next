#!/usr/bin/env node
/* Claude's lessons on this repo: docs/LESSONS.md, the one file this script owns. The learn mod (.claude/skills/learn)
   asks for lessons at the right moments and shows them again when the code they're about is touched.
     node tools/learn.mjs add --kind=mistake|missed|idea --anchors=openFull,tools/kit.mjs "what happened → what to do instead"
     node tools/learn.mjs done M3 [--note="shipped in v270"]   an idea / missed opportunity was acted on
     node tools/learn.mjs promote L4 --to="tools/where.mjs --check"   a lesson became an automatic check (the best outcome)
     node tools/learn.mjs match --file=index.html --names=openFull,save --cmd="node tools/kit.mjs sweep"   (JSON) lessons for this spot
     node tools/learn.mjs brief       (JSON) what the first message of a chat should know
     node tools/learn.mjs list [--open]
   A lesson says what to DO, names its anchors (functions, files, commands, selectors: how it's found again), and never holds
   game titles, scores or personal data (the repo is public). The same lesson again bumps its ×count instead of adding a copy;
   at ×3 it should become a check (release-guard rule, where.mjs --check, a kit.mjs sweep --plant) instead of a reminder. */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const FILE = process.env.PN_LESSONS || path.join(ROOT, "docs", "LESSONS.md");
const HEAD = `# Lessons

What Claude learned working on this repo: mistakes (and what to do instead), missed opportunities, and ideas. The learn mod
(\`.claude/skills/learn\`) asks for a lesson when a turn had a correction, a failed check, a guard stop or a release, and shows
each lesson again when the code it's about is touched. Written with \`node tools/learn.mjs\` (see its header); hand edits are
fine if the line format stays. Public repo: no game titles, scores or personal data here.
`;
const SECTIONS = { mistake: "## Mistakes", missed: "## Missed opportunities", idea: "## Missed opportunities", promoted: "## Promoted to checks" };

/* ---------- format: one line per entry ----------
   - **L3** ×2 · 2026-10-11 · v267 · `openFull`, `tools/kit.mjs` — text
   - [ ] **M2** · 2026-10-11 · v267 · `anchors` — text            (open idea / missed opportunity)
   - [x] **M1** · 2026-10-11 · v267 · `anchors` — text (done: note)
   - **L5** → `tools/where.mjs --check` · 2026-10-11 · v267 · `anchors` — text   (promoted) */
const LINE = /^- (?:\[( |x)\] )?\*\*([LM]\d+)\*\*(?: ×(\d+))?(?: → `([^`]+)`)? · (\d{4}-\d\d-\d\d) · (v?\d*) · ((?:`[^`]*`(?:, )?)*) — (.*)$/;
export function parse(text) {
  const out = [];
  let sec = "";
  for (const l of String(text).split(/\r?\n/)) {
    if (l.startsWith("## ")) { sec = l.slice(3).trim(); continue; }
    const m = l.match(LINE); if (!m) continue;
    const [, box, id, n, to, date, v, anc, rest] = m;
    const done = (rest.match(/ \(done: ([^)]*)\)$/) || [])[1];
    out.push({ id, kind: id[0] === "L" ? (to || /Promoted/.test(sec) ? "promoted" : "mistake") : "missed", open: box === " ", n: +(n || 1), to: to || "",
      date, v, anchors: [...anc.matchAll(/`([^`]*)`/g)].map(x => x[1]).filter(Boolean), text: rest.replace(/ \(done: [^)]*\)$/, ""), done: done || "" });
  }
  return out;
}
export function render(items) {
  const line = x => `- ${x.kind === "missed" ? `[${x.open ? " " : "x"}] ` : ""}**${x.id}**${x.n > 1 ? ` ×${x.n}` : ""}${x.to ? ` → \`${x.to}\`` : ""} · ${x.date} · ${x.v} · ${x.anchors.map(a => "`" + a + "`").join(", ")} — ${x.text}${x.done ? ` (done: ${x.done})` : ""}`;
  const by = k => items.filter(x => x.kind === k);
  return [HEAD, SECTIONS.mistake, "", ...by("mistake").map(line), "", SECTIONS.missed, "", ...by("missed").filter(x => x.open).map(line), ...by("missed").filter(x => !x.open).map(line),
    "", SECTIONS.promoted, "", ...by("promoted").map(line), ""].join("\n");
}
export const load = () => fs.existsSync(FILE) ? parse(fs.readFileSync(FILE, "utf8")) : [];
const write = items => { fs.mkdirSync(path.dirname(FILE), { recursive: true }); fs.writeFileSync(FILE, render(items)); };

/* ---------- matching ---------- */
const words = t => new Set(String(t).toLowerCase().replace(/[^a-z0-9_.\/-]+/g, " ").split(" ").filter(w => w.length > 2 && !STOP.has(w)));
const STOP = new Set("the and for that this with when what then than from into are was were not but you your its it's has have had all any can will just one two out off don't use instead".split(" "));
export function similar(a, b) {
  const A = words(a), B = words(b); if (!A.size || !B.size) return 0;
  let n = 0; for (const w of A) if (B.has(w)) n++;
  return n / Math.min(A.size, B.size);
}
/* an anchor matches a file by path suffix, a name by exact identifier, a command by substring */
export function relevant(items, { file = "", names = [], cmd = "" } = {}) {
  const nm = new Set(names.map(String));
  const f = String(file).replace(/\\/g, "/");
  return items.filter(x => x.kind !== "missed" || x.open).filter(x => x.anchors.some(a => {
    if (!a) return false;
    if (a.includes("/") || /\.\w{1,5}$/.test(a)) return !!f && (f === a || f.endsWith("/" + a) || (cmd && cmd.includes(a)));
    if (/\s/.test(a)) return !!cmd && cmd.includes(a);
    return nm.has(a) || (!!cmd && new RegExp(`\\b${a.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`).test(cmd));
  }));
}
const appV = () => { try { return "v" + (fs.readFileSync(path.join(ROOT, "index.html"), "utf8").match(/const APP_V = "(\d+)"/) || [])[1]; } catch (e) { return ""; } };
const today = () => new Date().toISOString().slice(0, 10);

/* ---------- commands ---------- */
export function add(items, { kind = "mistake", anchors = [], text, date = today(), v = appV() }) {
  text = String(text || "").replace(/\s+/g, " ").trim();
  if (text.length < 12) throw new Error("say what happened and what to do instead (a sentence, not a word)");
  if (!anchors.length) throw new Error("--anchors= is how the lesson is found again: functions, files, commands or selectors it's about");
  const k = kind === "idea" ? "missed" : kind;
  if (!["mistake", "missed"].includes(k)) throw new Error("--kind is mistake, missed or idea");
  /* the same lesson again: bump it (a repeat is the signal that a reminder isn't enough) */
  const twin = items.find(x => (x.kind === k || (k === "mistake" && x.kind === "promoted")) && (x.kind !== "missed" || x.open) &&
    (similar(x.text, text) >= 0.6 || (x.anchors.some(a => anchors.includes(a)) && similar(x.text, text) >= 0.4)));
  if (twin) {
    twin.n++;
    twin.anchors = [...new Set([...twin.anchors, ...anchors])];
    return { item: twin, merged: true };
  }
  const pre = k === "missed" ? "M" : "L";
  const next = 1 + Math.max(0, ...items.filter(x => x.id[0] === pre).map(x => +x.id.slice(1)));
  const item = { id: pre + next, kind: k, open: k === "missed", n: 1, to: "", date, v, anchors: [...new Set(anchors)], text, done: "" };
  items.push(item);
  return { item, merged: false };
}
export function brief(items, { exists = () => true } = {}) {
  const open = items.filter(x => x.kind === "missed" && x.open);
  return {
    lessons: items.filter(x => x.kind === "mistake").length,
    promoted: items.filter(x => x.kind === "promoted").length,
    open: open.slice(-3).reverse().map(x => ({ id: x.id, text: x.text })),
    openCount: open.length,
    promote: items.filter(x => x.kind === "mistake" && x.n >= 3).map(x => ({ id: x.id, n: x.n, text: x.text })),
    stale: items.filter(x => x.kind !== "missed" && x.anchors.length && x.anchors.every(a => !exists(a))).map(x => x.id),
    big: items.filter(x => x.kind === "mistake").length > 60,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [cmd, ...rest] = process.argv.slice(2);
  const opt = Object.fromEntries(rest.filter(a => a.startsWith("--")).map(a => { const [k, ...v] = a.slice(2).split("="); return [k, v.length ? v.join("=") : true]; }));
  const args = rest.filter(a => !a.startsWith("--"));
  const list = s => String(s || "").split(",").map(x => x.trim()).filter(Boolean);
  const items = load();
  try {
    if (cmd === "add") {
      const { item, merged } = add(items, { kind: opt.kind || "mistake", anchors: list(opt.anchors), text: args.join(" ") || opt.text });
      write(items);
      console.log(merged ? `Same lesson as ${item.id}: now ×${item.n}${item.n >= 3 ? ` — it keeps happening: turn it into an automatic check, then \`node tools/learn.mjs promote ${item.id} --to="<the check>"\`` : ""}`
        : `Learned ${item.id}: ${item.text}`);
    } else if (cmd === "done" || cmd === "promote") {
      const it = items.find(x => x.id === args[0]);
      if (!it) throw new Error(`no ${args[0]} in docs/LESSONS.md`);
      if (cmd === "done") { it.open = false; it.done = String(opt.note || today()).replace(/[()]/g, ""); }   /* the line format ends at ")" */
      else { if (!opt.to) throw new Error('--to="<the check that now catches it>"'); it.kind = "promoted"; it.to = String(opt.to); }
      write(items);
      console.log(cmd === "done" ? `${it.id} done.` : `${it.id} promoted: ${it.to} catches it now.`);
    } else if (cmd === "match") {
      process.stdout.write(JSON.stringify(relevant(items, { file: opt.file || "", names: list(opt.names), cmd: opt.cmd || "" })));
    } else if (cmd === "brief") {
      /* an anchor still exists when it's a file, or a word anywhere in the code */
      let code = "";
      try { code = fs.readFileSync(path.join(ROOT, "index.html"), "utf8") + fs.readFileSync(path.join(ROOT, "worker", "helper.js"), "utf8") + fs.readFileSync(path.join(ROOT, "CLAUDE.md"), "utf8"); } catch (e) {}
      const exists = a => fs.existsSync(path.join(ROOT, a.split(" ")[0].replace(/^node /, ""))) || code.includes(a) || /\s/.test(a);
      process.stdout.write(JSON.stringify(brief(items, { exists })));
    } else if (cmd === "list" || !cmd) {
      const show = items.filter(x => !opt.open || (x.kind === "missed" && x.open));
      console.log(show.length ? show.map(x => `${x.id}${x.n > 1 ? " ×" + x.n : ""}${x.kind === "missed" ? (x.open ? " [open]" : " [done]") : x.kind === "promoted" ? " [check]" : ""} ${x.text}  (${x.anchors.join(", ")})`).join("\n") : "No lessons yet.");
    } else throw new Error("unknown command " + cmd);
  } catch (e) { console.error(String(e.message || e)); process.exit(1); }
}
