// Usage report (v279, the owner: "remove any junk or things that are not used"). Reads every phone's weekly usage counts
// from the helper (GET /usage, the same key as the problem reports), lists every button, switch and Settings group the app
// has (from index.html), and keeps ONE issue up to date: "Usage: what gets used and what doesn't" (label usage), with what
// nobody has touched. Nothing is removed by this: the owner decides (the daily code check reads the issue).
// Runs from .github/workflows/usage.yml. USAGE_FILE=<json> runs it on a saved answer instead of the helper (for testing);
// DRY=1 prints the issue instead of writing it.
import { createHash } from "node:crypto";
import fs from "node:fs";

const HELPER = process.env.HELPER_URL || "https://playnext-helper.hussamnabil48.workers.dev";
const API = process.env.GH_API || "https://api.github.com";
const REPO = process.env.REPO, GH = process.env.GH_TOKEN, DRY = !!process.env.DRY;
const CF = String(process.env.CF_TOKEN || "").replace(/\s+/g, "");
const LABEL = "usage", TITLE = "Usage: what gets used and what doesn't";

let phones;
if (process.env.USAGE_FILE) phones = JSON.parse(fs.readFileSync(process.env.USAGE_FILE, "utf8")).usage || [];
else {
  if (!CF) { console.log("::warning::No CLOUDFLARE_API_TOKEN secret, so there's no key to read the counts with."); process.exit(0); }
  const KEY = createHash("sha256").update(CF + ":play-next-bugs").digest("hex");
  const r = await fetch(HELPER + "/usage", { headers: { Authorization: "Bearer " + KEY } });
  if (r.status === 503 || r.status === 404) { console.log("::warning::The helper can't give usage yet (deploy-helper sets it up)."); process.exit(0); }
  if (!r.ok) { console.log("::error::The helper said " + r.status + " " + (await r.text()).slice(0, 200)); process.exit(1); }
  phones = (await r.json()).usage || [];
}
console.log(phones.length + " phone(s) sent counts");

/* ---- everything a person can tap or switch, read from the app itself ---- */
const html = fs.readFileSync("index.html", "utf8");
const lineOf = i => html.slice(0, i).split("\n").length;
const clean = t => String(t || "").replace(/\$\{[^}]*\}/g, "…").replace(/<[^>]+>/g, " ").replace(/&[a-z]+;/g, " ").replace(/\s+/g, " ").trim();
const inv = new Map();   /* key → { kind, label, line } */
const addInv = (key, kind, at) => { if (!inv.has(key)) inv.set(key, { kind, line: lineOf(at), label: labelOf(key, kind) }); };
function labelOf(key, kind) {
  if (kind === "button" || kind === "setting") {
    const id = key.slice(1), i = html.indexOf(`id="${id}"`); if (i < 0) return "";
    if (kind === "setting") { const before = html.slice(Math.max(0, i - 400), i), m = [...before.matchAll(/<label[^>]*>([^<]{2,60})<\/label>/g)].pop(); if (m) return clean(m[1]); }
    const after = html.slice(i, i + 400), m = after.match(/>\s*([^<>]*[A-Za-z][^<>]{0,58})</); return m ? clean(m[1]) : "";
  }
  const i = html.indexOf(`data-${key}=`); if (i < 0) return "";
  const after = html.slice(i, i + 400), m = after.match(/>\s*([^<>]*[A-Za-z][^<>]{0,58})</); return m ? clean(m[1]) : "";
}
for (const m of html.matchAll(/closest\(\s*["'`]\[data-([\w-]+)/g)) addInv(m[1], "action", m.index);
for (const m of html.matchAll(/\[data-([\w-]+)[^\]"'`]*\]["'`]\)\.forEach\(\s*\w+\s*=>\s*\w+\.onclick/g)) addInv(m[1], "action", m.index);
for (const m of html.matchAll(/(?:\$\(|getElementById\()\s*["'`]#?([\w-]+)["'`]\)\.(?:onclick|addEventListener\(\s*["'`]click)/g)) addInv("#" + m[1], m[1].startsWith("s-") ? "setting" : "button", m.index);
for (const m of html.matchAll(/closest\(\s*["'`]#([\w-]+)["'`]\)/g)) addInv("#" + m[1], m[1].startsWith("s-") ? "setting" : "button", m.index);
for (const m of html.matchAll(/\b(?:pick|slide)\(\s*["'`]#([\w-]+)["'`]/g)) addInv("#" + m[1], "setting", m.index);
console.log(inv.size + " things to tap or switch in the app");

/* ---- add up every phone ---- */
/* whatever a phone sent is shown in a public issue: plain words and numbers only */
const plain = (x, n) => String(x || "").replace(/[^\w .()-]/g, "").slice(0, n);
for (const p of phones) { p.dev = plain(p.dev, 40); p.v = plain(p.v, 8); p.since = /^\d{4}-\d{2}-\d{2}$/.test(String(p.since)) ? p.since : ""; p.at = +p.at || 0;
  for (const f of ["taps", "screens", "opens"]) { const o = {}; for (const [k, v] of Object.entries(p[f] || {})) if (/^[#\w:/.-]{1,60}$/.test(k) && +v > 0) o[k] = Math.round(+v); p[f] = o; }
  p.on = (Array.isArray(p.on) ? p.on : []).filter(x => /^\w{1,40}$/.test(String(x))); }
const sum = (field) => { const t = {}; for (const p of phones) for (const [k, v] of Object.entries(p[field] || {})) t[k] = (t[k] || 0) + v; return t; };
const taps = sum("taps"), screens = sum("screens");
const since = phones.map(p => p.since).filter(Boolean).sort()[0] || "";
const days = since ? Math.max(1, Math.round((Date.now() - new Date(since + "T00:00:00Z")) / 864e5)) : 0;
const on = new Map(); for (const p of phones) for (const k of p.on || []) on.set(k, (on.get(k) || 0) + 1);
const never = [...inv].filter(([k]) => !taps[k]);
const top = Object.entries(taps).sort((a, b) => b[1] - a[1]).slice(0, 15);
const scr = Object.entries(screens).sort((a, b) => b[1] - a[1]);
const row = ([k, x]) => `| \`${k}\` | ${x.label || "—"} | ${x.line} |`;
const body = [
  `Made by the Usage job (.github/workflows/usage.yml) from the weekly counts the app sends (Settings › Your data › Help tidy the app). Names and numbers only. **Nothing is removed automatically**: the daily code check looks at this list, and anything you can see goes to "Code check: things for the owner to decide" first.`,
  ``,
  `**Phones:** ${phones.length ? phones.map(p => `${p.dev || "?"} (v${p.v}, counting since ${p.since}, last sent ${new Date(p.at).toISOString().slice(0, 10)})`).join(" · ") : "none yet: they send a week after updating"}`,
  days ? `**Counting for:** ${days} day(s). A thing needs about 4 weeks without a tap before it's worth asking about.` : "",
  ``,
  `### Never tapped on any phone (${never.length} of ${inv.size})`,
  never.length ? `| key | what it says | line in index.html |\n|---|---|---|\n` + never.sort((a, b) => a[1].line - b[1].line).map(row).join("\n") : "Everything got used.",
  ``,
  `### Most used`,
  top.length ? top.map(([k, n]) => `- \`${k}\` ${n}× ${inv.get(k) ? "(" + (inv.get(k).label || "") + ")" : ""}`).join("\n") : "—",
  ``,
  `### Screens visited`,
  scr.length ? scr.map(([k, n]) => `${k} ${n}×`).join(" · ") : "—",
  ``,
  `### Switches that are on`,
  on.size ? [...on].map(([k, n]) => `\`${k}\` (${n} phone${n > 1 ? "s" : ""})`).join(" · ") : "—",
  ``, `---`, `_Generated by [Claude Code](https://claude.ai/code)_`
].filter(x => x !== null).join("\n");

if (DRY || !GH) { console.log(body); process.exit(0); }
const gh = async (path, method, b) => {
  const r = await fetch(API + path, { method: method || "GET", headers: { Authorization: "Bearer " + GH, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "play-next-usage" }, body: b && JSON.stringify(b) });
  if (!r.ok && r.status !== 422) throw new Error(method + " " + path + " → " + r.status + " " + (await r.text()).slice(0, 200));
  return r.status === 204 ? null : r.json();
};
await gh(`/repos/${REPO}/labels`, "POST", { name: LABEL, color: "0e8a16", description: "What gets used in the app (weekly counts)" });
const open = (await gh(`/repos/${REPO}/issues?labels=${LABEL}&state=open&per_page=10`)).find(i => i.title === TITLE);
if (open) { await gh(`/repos/${REPO}/issues/${open.number}`, "PATCH", { body }); console.log("updated #" + open.number); }
else { const n = await gh(`/repos/${REPO}/issues`, "POST", { title: TITLE, body, labels: [LABEL] }); console.log("made #" + n.number); }
