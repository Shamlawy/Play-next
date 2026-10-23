#!/usr/bin/env node
/* Claude's time estimates against the real time (the owner, Oct 23: "when you tell me it takes about something to finish
   do monitor that and see how well you do and learn"). docs/ETA.md is the record (git, so every chat learns from it).
   The learn mod starts an entry by itself when a reply says "⏱ About N min" and ends it at the end of that turn, or
   reminds Claude to end it when the work runs in the background; the first message of a chat gets the calibration.
     node tools/eta.mjs start --min=25 [--kind=research] [--bg] "what was promised"   prints the id (E3) and past accuracy
     node tools/eta.mjs more --min=5 [E3]       a "Taking longer: about 5 more min" re-estimate
     node tools/eta.mjs end [E3] [--note=…]     the promised thing was delivered (latest open one by default)
     node tools/eta.mjs drop [E3]               abandoned (the person changed course): not counted
     node tools/eta.mjs add --min=25 --took=9 [--kind=…] [--date="2026-10-23 3:47 PM"] "what"   backfill a closed one
     node tools/eta.mjs tip | list | open [--json]
   --auto (the mod) scrubs the words first: a capitalised word that isn't the app's own (Queue, Nexi, …) becomes ▢, so a
   game title never lands in the public repo.
   Kinds (guessed from the words when not given): research, kit, ci, video, release, build, plan, other. */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const FILE = process.env.PN_ETA || path.join(ROOT, "docs", "ETA.md");
const HEAD = `# Time estimates

Every "⏱ About N min" Claude gives, against the time it really took (the owner, Oct 23: "do monitor that and see how
well you do and learn"). Written by \`node tools/eta.mjs\` (the learn mod starts and ends entries by itself); × is
took ÷ said, so under 1 means faster than promised. The first message of each chat gets the calibration per kind.

| id | started (UAE) | kind | said | took | × | what |
|---|---|---|---|---|---|---|
`;
const ROW = /^\| (E\d+) \| ([^|]+) \| (\w+) \| ([^|]+) \| ([^|]+) \| ([^|]*) \| (.*) \|(?: <!-- (.*) -->)?$/;

const uae = d => new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Dubai", year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit", hour12: true })
  .formatToParts(d).reduce((o, p) => (o[p.type] = p.value, o), {});
const stamp = d => { const p = uae(d); return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute} ${p.dayPeriod}`; };
const fromStamp = s => { // "2026-10-23 3:47 PM" in UAE time
  const m = String(s).trim().match(/^(\d{4})-(\d\d)-(\d\d) (\d{1,2}):(\d\d) (AM|PM)$/i); if (!m) return NaN;
  let h = +m[4] % 12; if (/pm/i.test(m[6])) h += 12;
  return Date.UTC(+m[1], +m[2] - 1, +m[3], h - 4, +m[5]);
};
const mins = s => { const m = String(s).match(/(\d+(?:\.\d+)?)\s*(h|hour|hours|min|mins|minutes)?/i); return m ? +m[1] * (/^h/i.test(m[2] || "") ? 60 : 1) : NaN; };
const fmt = n => n >= 90 ? `${Math.round(n / 6) / 10} h` : `${Math.round(n)} min`;

export function guessKind(t) {
  t = String(t).toLowerCase();
  if (/helper|research|search|look(ing)? (for|into)|gap|audit|review lens|ultracode|investigat/.test(t)) return "research";
  if (/video|film|render|demo|screenshot/.test(t)) return "video";
  if (/app-check|\bci\b|github|workflow|probe|deploy/.test(t)) return "ci";
  if (/kit\.mjs|kit all|sweep|scenario|\btest/.test(t)) return "kit";
  if (/release|fix|bug|merge/.test(t)) return "release";
  if (/build|\bmod\b|tool|feature|screen|tracker/.test(t)) return "build";
  if (/plan|doc|write.?up|brief/.test(t)) return "plan";
  return "other";
}

const OWN = new Set("I I'll I'm I've UAE AM PM Nexi Queue Home Settings Taste Library Played Replay Plan Insights Duels Book Android GitHub PR CI Phase Chromium WebKit Playwright Claude".split(" "));
// Plain sentence starts that stay (a capitalised first word is masked like any other unless it's one of these).
const STARTS = new Set("A An The Then Now Next First One Two Three Four Five Six Each Every All Both This That These Those It We You Your My Running Building Fixing Checking Filming Making Writing Reading Testing Looking Searching Sending Merging Releasing Recording".split(" "));
export function scrub(s) {
  return String(s).split(/(\s+)/).map((w, i) => {
    const bare = w.replace(/^[^\p{L}\d]+|[^\p{L}\d']+$/gu, "");
    return !/^\p{Lu}/u.test(bare) || OWN.has(bare) || STARTS.has(bare) ? w : w.replace(bare, "▢");
  }).join("").replace(/▢(\s+▢)+/g, "▢").slice(0, 140);
}

export function parse(text) {
  const out = [];
  for (const l of String(text).split(/\r?\n/)) {
    const m = l.match(ROW); if (!m) continue;
    const [, id, started, kind, said, took, , what, meta = ""] = m;
    const e = { id, started: started.trim(), kind, said: said.trim(), took: took.trim(), what: what.trim() };
    for (const kv of meta.split(/\s+/)) { const [k, v] = kv.split("="); if (k && v !== undefined) e[k] = v; }
    out.push(e);
  }
  return out;
}
const ratio = e => { const s = mins(e.said.replace(/\s*\(.*\)$/, "")) + (+e.more || 0), t = mins(e.took); return s > 0 && t >= 0 ? t / s : NaN; };
export function render(list) {
  return HEAD + list.map(e => {
    const r = e.took === "open" ? "" : ratio(e).toFixed(2);
    const meta = ["at", "bg", "more", "note"].filter(k => e[k] !== undefined).map(k => `${k}=${String(e[k]).replace(/\s+/g, "_")}`).join(" ");
    return `| ${e.id} | ${e.started} | ${e.kind} | ${e.said} | ${e.took} | ${r} | ${e.what.replace(/\|/g, "/")} |${meta ? ` <!-- ${meta} -->` : ""}`;
  }).join("\n") + "\n";
}
export const load = () => fs.existsSync(FILE) ? parse(fs.readFileSync(FILE, "utf8")) : [];
const write = list => fs.writeFileSync(FILE, render(list));
const nextId = list => `E${list.reduce((n, e) => Math.max(n, +e.id.slice(1)), 0) + 1}`;
const median = a => { const s = [...a].sort((x, y) => x - y); return s.length ? (s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2) : NaN; };

export function stats(list) {
  const closed = list.filter(e => e.took !== "open" && Number.isFinite(ratio(e)));
  const by = {};
  for (const e of closed) (by[e.kind] ||= []).push(ratio(e));
  return { n: closed.length, all: median(closed.map(ratio)), kinds: Object.fromEntries(Object.entries(by).map(([k, a]) => [k, { n: a.length, x: median(a) }])) };
}
const word = x => x < 0.85 ? `${x.toFixed(1)}× (faster than said)` : x > 1.15 ? `${x.toFixed(1)}× (slower than said)` : "about right";
export function tip(list, kind) {
  const s = stats(list);
  if (!s.n) return "";
  if (kind) {
    const k = s.kinds[kind];
    return k ? `past ${kind} tasks took ${word(k.x)} (${k.n} recorded)` : `no ${kind} tasks recorded yet; overall ${word(s.all)}`;
  }
  const parts = Object.entries(s.kinds).sort((a, b) => b[1].n - a[1].n).map(([k, v]) => `${k} ${v.x.toFixed(1)}× (${v.n})`);
  const open = list.filter(e => e.took === "open").map(e => e.id);
  return `[learn] Time estimates (docs/ETA.md): ${s.n} recorded; real time ÷ what you said: ${parts.join(", ")}; overall ${s.all.toFixed(1)}×. Scale your next "⏱ About N min" by its kind's number.${open.length ? ` Still open: ${open.join(", ")} (end with node tools/eta.mjs end, or drop).` : ""}`;
}

function args(rest) {
  const o = { _: [] };
  for (const a of rest) { const m = a.match(/^--([\w-]+)(?:=(.*))?$/); if (m) o[m[1]] = m[2] ?? true; else o._.push(a); }
  return o;
}
const pick = (list, id) => id ? list.find(e => e.id === id) : [...list].reverse().find(e => e.took === "open");

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [cmd, ...rest] = process.argv.slice(2);
  const a = args(rest), list = load(), now = new Date(+(process.env.PN_ETA_NOW || Date.now()));
  const id = a._.find(x => /^E\d+$/.test(x));
  const what = a._.filter(x => x !== id).join(" ").trim();
  if (cmd === "start" || cmd === "add") {
    const said = mins(a.min);
    if (!(said > 0) || !what) { console.error('usage: eta.mjs start --min=N "what"'); process.exit(2); }
    const kind = a.kind || guessKind(what);
    const at = cmd === "add" && a.date ? fromStamp(a.date) : now.getTime();
    if (!Number.isFinite(at)) { console.error('--date must look like "2026-10-23 3:47 PM"'); process.exit(2); }
    const e = { id: nextId(list), started: stamp(new Date(at)), kind, said: fmt(said), took: "open", what: a.auto ? scrub(what) : what.slice(0, 140), at: new Date(at).toISOString() };
    if (a.bg) e.bg = "1";
    if (cmd === "add") { const t = mins(a.took); if (!(t >= 0)) { console.error("add needs --took=N"); process.exit(2); } e.took = fmt(t); }
    write([...list, e]);
    const t = tip(list, kind);
    console.log(`${e.id} ${cmd === "add" ? "added" : "started"}: about ${e.said}, ${kind}${t ? `; ${t}` : ""}`);
  } else if (cmd === "more") {
    const e = pick(list, id); if (!e) { console.error("no open estimate"); process.exit(1); }
    e.more = String((+e.more || 0) + (mins(a.min) || 0)); write(list);
    console.log(`${e.id}: +${fmt(+e.more)} on top of ${e.said}`);
  } else if (cmd === "end") {
    const e = pick(list, id); if (!e || e.took !== "open") { console.error("no open estimate"); process.exit(1); }
    const from = e.at ? Date.parse(e.at) : fromStamp(e.started);
    const took = Math.max(1, (now - from) / 60000);
    if (!Number.isFinite(took)) { console.error(`${e.id} has no start time: end it by hand (add --took)`); process.exit(1); }
    e.took = fmt(took); if (a.note) e.note = a.note; write(list);
    console.log(`${e.id} done: took ${e.took} vs ${e.said}${e.more ? ` (+${fmt(+e.more)})` : ""} said (${ratio(e).toFixed(2)}×)`);
  } else if (cmd === "drop") {
    const e = pick(list, id); if (!e) { console.error("no open estimate"); process.exit(1); }
    write(list.filter(x => x !== e)); console.log(`${e.id} dropped`);
  } else if (cmd === "open") {
    const o = list.filter(e => e.took === "open");
    console.log(a.json ? JSON.stringify(o.map(e => ({ id: e.id, said: e.said, at: e.at, bg: !!e.bg, what: e.what }))) : o.map(e => `${e.id} ${e.started} ${e.said} ${e.what}`).join("\n") || "none open");
  } else if (cmd === "tip") {
    console.log(tip(list));
  } else if (cmd === "list") {
    console.log(list.map(e => `${e.id} ${e.started} ${e.kind} said ${e.said} took ${e.took} ${e.what}`).join("\n") || "none yet");
  } else {
    console.error("usage: eta.mjs start|more|end|drop|add|open|tip|list (see the header)"); process.exit(2);
  }
}
