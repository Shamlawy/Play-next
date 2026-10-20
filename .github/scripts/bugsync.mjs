// Files the app's automatic problem reports (collected by Nexi's helper) as GitHub issues.
// One issue per problem, titled "... [bug:<sig>]" with the label auto-bug. A problem that happens again
// gets a comment on its issue (and the issue reopens if it was closed). Runs from .github/workflows/bugs.yml.
// The helper's BUG_KEY is sha256(CLOUDFLARE_API_TOKEN + ":play-next-bugs"); deploy-helper.yml sets the same value.
// Stutters (kind slow) are grouped by family and a fixed one stays "pending proof" until the phones show it: see the
// "Stutter families" note further down.
// Testing: BUGS_FILE=<json {bugs:[…]}> reads saved reports instead of the helper, ISSUES_FILE=<json [{number, title, state,
// body, labels, closed_at, state_reason, comments:[{body}]}]> stands in for GitHub's issues, USAGE_FILE=<json {usage:[…]}> for
// the phones' usage records, APP_V / PROOF_SINCE override those two, and DRY=1 prints what it would file, comment and ack
// instead of doing it (DRY_LOG=<file> also writes every request it would send there, one JSON line each).
import { createHash } from "node:crypto";
import fs from "node:fs";

const HELPER = process.env.HELPER_URL || "https://playnext-helper.hussamnabil48.workers.dev";
const API = process.env.GH_API || "https://api.github.com";
const REPO = process.env.REPO, GH = process.env.GH_TOKEN;
const CF = String(process.env.CF_TOKEN || "").replace(/\s+/g, "");
const LABEL = "auto-bug", PENDING = "pending-proof", MAX_NEW = 15, DRY = !!process.env.DRY;
const fixture = k => process.env[k] ? JSON.parse(fs.readFileSync(process.env[k], "utf8")) : null;
const BUGS = fixture("BUGS_FILE"), ISSUES = fixture("ISSUES_FILE");
if (!CF && !BUGS) { console.log("::warning::No CLOUDFLARE_API_TOKEN secret, so there's no key to read the reports with."); process.exit(0); }
const KEY = createHash("sha256").update(CF + ":play-next-bugs").digest("hex");

const helper = (path, body) => fetch(HELPER + path, { method: body ? "POST" : "GET", headers: { Authorization: "Bearer " + KEY, "Content-Type": "application/json" }, body: body && JSON.stringify(body) });
const gh = async (path, method, body) => {
  if (DRY) {
    if (method && method !== "GET" && process.env.DRY_LOG) fs.appendFileSync(process.env.DRY_LOG, JSON.stringify({ method, path, body }) + "\n");
    if (method && method !== "GET") { console.log(`[dry] ${method} ${path} ` + JSON.stringify(body ? body.title || body.state || (body.labels ? "labels " + body.labels.join(",") : "") || String(body.body || "").split("\n")[0] : "")); return { number: "new" }; }
    const c = /\/issues\/(\d+)\/comments/.exec(path);
    if (c) return ((ISSUES || []).find(i => i.number === +c[1]) || {}).comments || [];
    return /[?&]page=1\b/.test(path) ? ISSUES || [] : [];
  }
  const r = await fetch(API + path, { method: method || "GET", headers: { Authorization: "Bearer " + GH, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "play-next-bugs" }, body: body && JSON.stringify(body) });
  if (!r.ok && r.status !== 422 && !(method === "DELETE" && r.status === 404)) throw new Error(method + " " + path + " → " + r.status + " " + (await r.text()).slice(0, 200));
  return r.status === 204 ? null : r.json();
};

const r = BUGS ? { ok: true, status: 200, json: async () => BUGS } : await helper("/bugs");
if (r.status === 503) { console.log("::warning::The helper has no BUG_KEY yet. It's set when deploy-helper runs (push to worker/** or run it by hand)."); process.exit(0); }
if (!r.ok) { console.log("::error::The helper said " + r.status + " " + (await r.text()).slice(0, 200)); process.exit(1); }
const bugs = (await r.json()).bugs || [];
console.log(bugs.length + " problem(s) with new reports");
/* no early exit when nothing is new: folding open duplicates, pending proofs and recently closed stutters still run */

await gh(`/repos/${REPO}/labels`, "POST", { name: LABEL, color: "d73a4a", description: "Reported automatically by the app" });
await gh(`/repos/${REPO}/labels`, "POST", { name: PENDING, color: "fbca04", description: "A stutter fix waiting for the phones to prove it (bugsync)" });
const issues = [];
for (let page = 1; page <= 5; page++) {
  const got = await gh(`/repos/${REPO}/issues?labels=${LABEL}&state=all&per_page=100&page=${page}`);
  issues.push(...got); if (got.length < 100) break;
}
const bySig = new Map();
for (const i of issues) { const m = /\[bug:([0-9a-f]{8})\]/.exec(i.title); if (m && !bySig.has(m[1])) bySig.set(m[1], i); }

const KIND = { error: "Crash", promise: "Crash", layout: "Cut off", slow: "Slow", helper: "Helper", tour: "Tour", user: "Reported", look: "Design", stuck: "Stuck", review: "Design review" };
const when = t => new Date(t).toISOString().replace("T", " ").slice(0, 16) + " UTC";
const fence = s => "```\n" + String(s || "").replace(/```/g, "ˋˋˋ") + "\n```";
const body = b => {
  const s = b.sample || {};
  return [
    `**${KIND[b.kind] || b.kind}** on **${s.screen || "?"}**${s.note ? `\n\n> ${s.note.replace(/\n/g, "\n> ")}` : ""}`,
    fence(b.msg),
    b.kind === "slow" && famOf(b.msg) ? costOf(s.msg || b.msg, s.stack).line : "",
    s.at ? `Where: \`${s.at}\`` : "",
    s.stack ? (b.kind === "user" ? "What was at the spot they tapped:\n" : b.kind === "slow" ? "Timing:\n" : "Stack:\n") + fence(s.stack) : "",
    `| | |\n|---|---|\n| Times | ${b.n} |\n| Versions | ${(b.vs || []).join(", ")} |\n| Phones | ${(b.devs || []).join(", ")} |\n| Screens | ${(b.screens || []).join(", ")} |\n| Latest | ${s.w}×${s.h} @${s.dpr}x, ${s.style || "clean"} style |\n| First / last | ${when(b.first)} / ${when(b.last)} |`,
    s.crumbs && s.crumbs.length ? "What happened just before:\n" + fence(s.crumbs.join("\n")) : "",
    /^(iPhone|iPad|Mac[^,]*Safari)/.test(s.dev || "") ? "_Safari (WebKit): reproduce with Playwright's WebKit (the WebKit check workflow), not Chromium. Before v229 an iPad reported itself as \"Mac\"._" : "",
    "_Sent automatically by the app. No game titles, scores or notes are included._"
  ].filter(Boolean).join("\n\n");
};

/* Stutters count only when they repeat (inbox clean-up, Oct 15 2026: ~200 of the ~270 automatic reports ever filed were
   "Slow", most of them one 110–300ms hitch that never came back, many from the iPad at 60Hz). A sighting = one phone on one
   day (the app sends each problem at most once a day per phone). A timing report (a stutter, freeze, slow tap, scroll or
   screen-change hitch, a slow start) becomes an issue once it has been seen REPEAT times, or at once when it is clearly over
   the normal range (BIG_MS, the app's own freeze line). Until then it isn't acked, so the helper keeps counting; a one-off
   is forgotten 45 days after its last sighting (BUG_TTL). A closed one reopens only after REOPEN more sightings (or a BIG_MS
   one). A smoothness check / self-test is a measurement run on purpose: an issue only when it shows a problem (MEASURE_PCT
   of frames dropped somewhere, or a MEASURE_MS gap); otherwise its numbers go to this job's log. Everything else (crashes,
   cut-offs, stuck taps, the app's structural speed checks) is filed as before.
   Why here and not in the app: this side sees both phones and every day, works for every installed version (the iPad
   trails behind), and needs no app release. */
const REPEAT = 3, REOPEN = 2, BIG_MS = 400, MEASURE_PCT = 25, MEASURE_MS = 100;
const HITCH = /^(A [\d.]+ms (stutter|freeze) on |A tap took [\d.]+ms to respond|Scrolling stutters on |Opening the app froze for )|\bstuttered \(\d+ hitch/;
const MEASURE = /^(Smoothness check on this phone|Self-test)\b/;
/* null = not a timing report (files as before); else {count: whether it counts yet, why} */
function gate(b, more) {
  const msg = String(b.msg || "");
  if (b.kind !== "slow") return null;
  if (MEASURE.test(msg)) {
    const head = msg.split(/ With each one switched off| Queue pictures:/)[0];
    const pct = Math.max(0, ...[...head.matchAll(/(\d+)%/g)].map(m => +m[1])), gap = Math.max(0, ...[...head.matchAll(/worst (\d+)ms/g)].map(m => +m[1]));
    return pct >= MEASURE_PCT || gap >= MEASURE_MS ? { count: true, why: `a measurement that shows a problem: ${pct}% dropped, worst gap ${gap}ms` }
      : { count: false, measure: true, why: `a measurement in the normal range: at most ${pct}% dropped, worst gap ${gap}ms` };
  }
  if (!HITCH.test(msg)) return null;
  const ms = +((/worst ([\d.]+)ms/.exec(msg) || /([\d.]+)ms/.exec(msg) || [])[1] || 0);
  const need = more == null ? REPEAT : REOPEN, seen = more == null ? b.n : more;
  if (ms >= BIG_MS) return { count: true, why: `${ms}ms, over ${BIG_MS}ms` };
  return seen >= need ? { count: true, why: `seen ${seen} times` } : { count: false, why: `${seen} of ${need} sightings, ${ms}ms` };
}

/* Stutter families, pending proof, where the time went (Oct 20 2026, no version). 220 of 300 automatic reports were
   stutters, and the same few kept coming back: #72, #62, #367, #338, #136, #333 and #112 were closed ("Fixed in vN", or
   "not seen since") and reopened 3-4 times each, because fixes were judged on PC numbers the phones later contradicted, and
   every new hitch size ("A 230ms stutter on Queue" vs "A 470ms …") was a new signature, so one problem became 30 issues.
   (1) Family: a timing report's family is its kind (scroll / switch / tap / stutter / freeze / start / "X stuttered") +
       the place in its message + where the time went (costOf: script, style/layout or drawing; "unknown" without timing,
       which matches any). A report with no issue of its own, or whose issue is closed, folds into an OPEN issue of the same
       family instead of making a new number or reopening an old one (`also` in the issue's bugsync note routes its
       signature there from then on), and open issues of one family fold into the oldest (the others close as duplicates).
   (2) Pending proof: a stutter issue closed (as completed) after PROOF_SINCE is reopened with the label pending-proof and a
       note of its fix version (the highest "Fixed in vN" in its comments, else the APP_V of the day it closed). It closes
       for real only once the phones have run PROOF_N releases from the fix on with no sighting of it, at least one of them
       seen from the same phone family at the same width (±NEAR_W px) as its latest sighting. What a phone ran comes from
       every report it sends (any kind: version, phone, width) and its weekly usage record. A sighting from a version older
       than the fix doesn't count; one from the fix or later (REOPEN sightings, or one 400ms+) means the fix didn't hold:
       the label goes and it's a normal open issue again.
   (3) Where the time went: every new stutter issue and every sighting comment says whether the time was script,
       style/layout or drawing (from the phone's lagSplit timing), so the fix goes after the right thing.
   The bugsync note lives in the issue body as <!-- bugsync {json} --> (bodies come with the issue list, so no extra calls).
   Test: node tools/bugsync-check.mjs (DRY=1 cases for each rule). */
const PROOF_N = 3, PROOF_SINCE = Date.parse(process.env.PROOF_SINCE || "2026-10-20T00:00:00Z"), NEAR_W = 24;
const APP_V = +(process.env.APP_V || (/const APP_V = "(\d+)"/.exec((() => { try { return fs.readFileSync(new URL("../../index.html", import.meta.url), "utf8"); } catch (e) { return ""; } })()) || [])[1] || 0);
const devFam = d => (String(d || "").match(/^(iPhone|iPad|Android|Mac|Windows)/) || [])[1] || "";
const PLACE = [[/^Scrolling stutters on (.+?) \(/, "scroll"], [/^Switching to (.+?) stuttered/, "switch"], [/^A tap took [\d.]+ms to respond on (.+?)\.?$/, "tap"],
  [/^A [\d.]+ms stutter on (.+?)\.?$/, "stutter"], [/^A [\d.]+ms freeze on (.+?)\.?$/, "freeze"], [/^Opening the app froze()/, "start"], [/^(.+?) stuttered \(\d+ hitch/, "stuttered"]];
/* {key: "kind|place"} for a timing report's message, else null */
function famOf(msg) {
  msg = String(msg || "").replace(/\s+/g, " ").trim();
  if (!HITCH.test(msg)) return null;
  for (const [re, kind] of PLACE) { const m = re.exec(msg); if (m) return { key: kind + "|" + String(m[1] || "app").toLowerCase().replace(/^(a|an|the) /, "") }; }
  return null;
}
const CAUSE = { script: "the app's own code (script)", layout: "style and layout (restyling, or code measuring the page right after changing it)", drawing: "drawing (the GPU side: pictures, layers, what the screen paints)", unknown: "not known" };
const AIM = { script: "Fix the function named below, not the pictures.", layout: "Look for a big restyle or a forced layout (a rect / size read right after a change), not the pictures.",
  drawing: "Cut what the screen draws (layers, big pictures, see-through pieces); a PC trace without a long frame doesn't prove a fix.", unknown: "No timing came with it (Safari sends none, and versions before v283 sent none), so it can't tell script from drawing." };
/* where the time of a hitch went, from the phone's lagSplit line ("N long frames, Tms: script Sms (forced style/layout Fms),
   style+layout Lms, render Rms" or "no long frame …"): script = S - F, style/layout = F + L, drawing = the rest of the
   hitch (H, or T when the hitch's size isn't known): render R, the long frames' time outside script and style (waiting on the
   GPU), and the part of the hitch outside any long frame (the main thread was free then) */
function costOf(msg, stack) {
  const t = String(stack || ""), H = +((/worst ([\d.]+)ms/.exec(msg) || /([\d.]+)ms/.exec(msg) || [])[1] || 0);
  let part = null, top = (/longest script (\d+ms: .*)/.exec(t) || [])[1];
  if (/^no long frame/.test(t)) part = { script: 0, layout: 0, drawing: H };
  const m = /long frames?, (\d+)ms: script (\d+)ms \(forced style\/layout (\d+)ms\), style\+layout (\d+)ms, render (\d+)ms/.exec(t);
  if (m) { const [T, S, F, L, R] = m.slice(1).map(Number); part = { script: Math.max(0, S - F), layout: F + L, drawing: Math.max(R, Math.max(H, T) - S - L) }; }
  if (!part) return { cause: "unknown", line: `**Where the time went: ${CAUSE.unknown}.** ${AIM.unknown}` };
  const cause = ["drawing", "layout", "script"].reduce((a, k) => part[k] > part[a] ? k : a, "drawing"), all = part.script + part.layout + part.drawing;
  return { cause, line: `**Where the time went: ${CAUSE[cause]}**${all ? `, ${Math.round(part[cause] / all * 100)}%` : ""} (script ${part.script}ms, style/layout ${part.layout}ms, drawing ${part.drawing}ms${H ? ` of a ${H}ms hitch` : ""}). ${AIM[cause]}${cause === "script" && top ? ` Longest script: ${top}.` : ""}` };
}
const sameFam = (a, b, exact) => !!a && !!b && a.key === b.key && (a.cause === b.cause || !exact && (a.cause === "unknown" || b.cause === "unknown"));
const NOTE = /\n*<!-- bugsync (\{.*?\}) -->/s;
const noteOf = i => { try { return JSON.parse((NOTE.exec(i.body || "") || [])[1] || "{}"); } catch (e) { return {}; } };
const titleMsg = t => (/^🐞 Slow[^:]*: (.*?) \[bug:/.exec(t || "") || [])[1] || "";
const labelsOf = i => (i.labels || []).map(l => typeof l === "string" ? l : l.name);
const vNum = v => +String(v || "").replace(/\D.*$/, "") || 0;
/* the latest sighting written on an issue (body or comments): version, phone, width, and the latest timing (then with no
   message: a comment doesn't carry that sighting's hitch size, and the title has only the first one's) */
function latestOf(i, cs) {
  let out = { v: vNum((/\| Versions \| ([^|]*)\|/.exec(i.body || "") || [])[1]?.split(",").pop()), dev: (/\| Phones \| ([^|,]*)/.exec(i.body || "") || [])[1]?.trim() || "",
    w: +((/\| Latest \| (\d+)×/.exec(i.body || "") || [])[1] || 0), stack: (/Timing:\n```\n([\s\S]*?)\n```/.exec(i.body || "") || [])[1] || "", msg: titleMsg(i.title) };
  for (const c of cs || []) {
    const m = /Latest: v(\d+) on ([^,]+), [^,]*, (\d+)×\d+/.exec(c.body || ""); if (!m) continue;
    const stack = (/Timing:\n```\n([\s\S]*?)\n```/.exec(c.body) || [])[1] || "";
    out = { ...out, v: +m[1], dev: m[2].trim(), w: +m[3], ...(stack ? { stack, msg: "" } : {}) };
  }
  return out;
}

const T = new Map(); /* issue number → {i, note, fam, changed} for the timing issues */
const comments = async i => {
  if (i.comments_list) return i.comments_list;
  if (Array.isArray(i.comments)) return (i.comments_list = i.comments);
  const all = []; for (let p = 1; p <= 5; p++) { const got = await gh(`/repos/${REPO}/issues/${i.number}/comments?per_page=100&page=${p}`) || []; all.push(...got); if (got.length < 100) break; }
  return (i.comments_list = all);
};
/* the fix version a person wrote ("Fixed in vN"), never bugsync's own lines that repeat it */
const OWN = /^\*\*(Pending proof|Proven|The fix in|Folded)/;
const fixIn = cs => Math.max(0, ...cs.filter(c => !OWN.test(String(c.body))).map(c => +((String(c.body).match(/fixed in v(\d+)/i) || [])[1] || 0)));
async function track(i) {
  if (T.has(i.number)) return T.get(i.number);
  const fk = famOf(titleMsg(i.title)); if (!fk) return null;
  const note = noteOf(i); if (note.folded && i.state === "open") delete note.folded;   /* reopened by hand after a fold */
  const t = { i, note, fam: { key: note.fam || fk.key, cause: note.cause || "" }, changed: false };
  if (!t.fam.cause && i.state === "open") {   /* once per issue: its cause from the latest timing written on it */
    const l = latestOf(i, await comments(i)); t.fam.cause = costOf(l.msg, l.stack).cause;
    note.fam = t.fam.key; note.cause = t.fam.cause; t.changed = true;
  }
  T.set(i.number, t); return t;
}
const save = async t => {
  if (!t.changed) return; t.changed = false;
  const bodyNow = String(t.i.body || "").replace(NOTE, "") + `\n\n<!-- bugsync ${JSON.stringify(t.note)} -->`;
  t.i.body = bodyNow; await gh(`/repos/${REPO}/issues/${t.i.number}`, "PATCH", { body: bodyNow });
};
const say = (i, text) => gh(`/repos/${REPO}/issues/${i.number}/comments`, "POST", { body: text });
const isPending = t => t && t.i.state === "open" && t.note.proof && !t.note.proof.done;
const openOf = (fam, exact) => [...T.values()].filter(t => t.i.state === "open" && sameFam(t.fam, fam, exact)).sort((a, b) => (+a.i.number || 1e9) - (+b.i.number || 1e9))[0];

for (const i of issues) await track(i);

/* (1) open issues of one family fold into the oldest */
for (const t of [...T.values()].filter(t => t.i.state === "open").sort((a, b) => a.i.number - b.i.number)) {
  const k = openOf(t.fam, true); if (!k || k === t) continue;   /* same cause only: one without timing mustn't pull different ones together */
  const sig = (/\[bug:([0-9a-f]{8})\]/.exec(t.i.title) || [])[1];
  k.note.also = [...new Set([...(k.note.also || []), sig, ...(t.note.also || [])].filter(Boolean))]; k.changed = true;
  await say(t.i, `**Folded into #${k.i.number}:** same kind of hitch on the same screen, ${t.fam.cause === "unknown" ? "and neither came with timing" : "and the time went to the same place: " + CAUSE[t.fam.cause]}. Its sightings go there from now on.`);
  await gh(`/repos/${REPO}/issues/${t.i.number}`, "PATCH", { state: "closed", state_reason: "not_planned" });
  if (labelsOf(t.i).includes(PENDING)) { await gh(`/repos/${REPO}/issues/${t.i.number}/labels/${PENDING}`, "DELETE"); delete t.note.proof; }
  await say(k.i, `**Folded in #${t.i.number}** (${titleMsg(t.i.title)}): the same family (${t.fam.key.replace("|", " on ")}, ${t.fam.cause}). One issue per problem, not one per hitch size.`);
  t.i.state = "closed"; t.note.folded = k.i.number; t.changed = true;
  console.log(`folded #${t.i.number} into #${k.i.number} (${t.fam.key}, ${t.fam.cause})`);
}

/* (2) a stutter closed as fixed is pending proof until the phones show it */
for (const t of T.values()) {
  const i = t.i, closedAt = Date.parse(i.closed_at || "") || 0;
  if (i.state !== "closed" || t.note.proof || t.note.folded || closedAt < PROOF_SINCE || String(i.state_reason || "completed").toLowerCase() !== "completed") continue;
  const cs = await comments(i), l = latestOf(i, cs);
  const fix = fixIn(cs) || APP_V;
  t.note.proof = { fix, dev: devFam(l.dev), w: l.w, clean: {}, dirty: [...(t.note.dirty || [])] };   /* versions it was seen on stay not-clean for a later fix */ t.changed = true;
  if (!t.fam.cause) { t.fam.cause = t.note.cause = costOf(l.msg, l.stack).cause; t.note.fam = t.fam.key; }
  await gh(`/repos/${REPO}/issues/${i.number}`, "PATCH", { state: "open" });
  await gh(`/repos/${REPO}/issues/${i.number}/labels`, "POST", { labels: [PENDING] });
  await say(i, `**Pending proof (fixed in v${fix}).** A stutter fix counts once the phones show it: this closes by itself after they've used ${PROOF_N} releases from v${fix} on without it, at least one of them ${t.note.proof.dev || "the same phone"}${l.w ? ` at ${l.w}px` : ""}. Sightings from versions before v${fix} don't count; one from v${fix} or later means the fix didn't hold.`);
  i.state = "open"; i.labels = [...labelsOf(i), PENDING];
  console.log(`pending proof #${i.number} (fixed in v${fix}, ${t.note.proof.dev} ${l.w}px)`);
}

const route = new Map();   /* signature → tracked issue: its own title first, then where it was folded */
for (const [sig, i] of bySig) route.set(sig, i);
for (const t of T.values()) for (const sig of t.note.also || []) if (t.i.state === "open" || !route.has(sig)) route.set(sig, t.i);

const acked = {}; let made = 0, held = 0;
const sighting = (b, more, s, folded) => `Seen ${more} more time${more === 1 ? "" : "s"} (${b.n} in total${folded ? " for that signature" : ""}). Latest: v${s.v} on ${s.dev}, ${s.screen}, ${s.w}×${s.h}.${s.note ? `\n\n> ${s.note}` : ""}`
  + (b.kind === "slow" && famOf(s.msg || b.msg) ? "\n\n" + costOf(s.msg || b.msg, s.stack).line : "")
  + (b.kind === "slow" && s.stack ? "\n\nTiming:\n" + fence(s.stack) : "");   /* v283 (M11): what the latest stutter spent its time on */
const famB = b => { const fk = b.kind === "slow" && famOf(b.msg), s = b.sample || {}; return fk ? { key: fk.key, cause: costOf(s.msg || b.msg, s.stack).cause } : null; };
for (const b of bugs) {
  let i = route.get(b.sig);
  const s = b.sample || {}, more = b.n - (b.seen || 0), fam = famB(b);
  if (fam) console.log(`where [bug:${b.sig}]: ${fam.cause} (${fam.key})`);
  /* a report with no open issue of its own joins an open one of its family */
  if (fam && (!i || i.state === "closed")) {
    const k = openOf(fam);
    if (k) { i = k.i; if (!(k.note.also || []).includes(b.sig)) { k.note.also = [...(k.note.also || []), b.sig]; k.changed = true; } route.set(b.sig, i); }
  }
  const t = i && T.get(i.number), folded = !!i && bySig.get(b.sig) !== i;   /* a sighting of another signature in this family */
  if (!i) {
    /* sightings of one family count together: 230ms + 470ms + 310ms on the Queue are three sightings of one stutter */
    const g = gate(fam ? { ...b, n: bugs.filter(x => !route.get(x.sig) && sameFam(famB(x), fam)).reduce((a, x) => a + x.n, 0) } : b);
    if (g && !g.count) {
      if (g.measure) { console.log(`not filed (${g.why}): ${b.msg}${s.stack ? "\n  " + String(s.stack).replace(/\n/g, "\n  ") : ""}`); acked[b.sig] = b.n; }
      else { console.log(`held [bug:${b.sig}] (${g.why}): ${b.msg}`); held++; }
      continue;
    }
    if (made >= MAX_NEW) continue;
    /* which kind of device: the friend uses an iPhone and an iPad (Safari), the owner an Android Fold (Chrome) */
    const df = devFam(s.dev);
    const title = `🐞 ${KIND[b.kind] || b.kind}${df ? " (" + df + ")" : ""}: ${String(b.msg).replace(/\s+/g, " ").slice(0, 80)} [bug:${b.sig}]`;
    const note = fam ? { fam: fam.key, cause: fam.cause } : null;
    const text = body(b) + (note ? `\n\n<!-- bugsync ${JSON.stringify(note)} -->` : "");
    const n = await gh(`/repos/${REPO}/issues`, "POST", { title, body: text, labels: [LABEL] });
    console.log("new issue #" + n.number + " " + title); made++;
    if (fam && n && n.number) { const ni = { number: n.number, title, state: "open", body: text, labels: [LABEL] }; T.set(n.number === "new" ? "new:" + b.sig : n.number, { i: ni, note, fam, changed: false }); route.set(b.sig, ni); }
  } else if (isPending(t)) {
    const p = t.note.proof, vs = [vNum(s.v)].filter(v => v >= p.fix);   /* the latest sighting's own version (b.vs is the signature's whole history) */
    if (!vs.length) { console.log(`#${i.number} pending proof: a sighting from v${s.v}, older than the fix (v${p.fix}), doesn't count`); acked[b.sig] = b.n; continue; }
    for (const v of vs) if (!p.dirty.includes(v) || p.clean[v]) { if (!p.dirty.includes(v)) p.dirty.push(v); delete p.clean[v]; t.changed = true; }   /* a held sighting comes back every hour: write only what's new */
    const g = gate(b, more);
    if (g && !g.count) { console.log(`held #${i.number}, pending proof (${g.why}) on v${vs.join(", v")}: ${b.msg}`); held++; continue; }
    t.note.dirty = [...new Set([...(t.note.dirty || []), ...p.dirty])]; delete t.note.proof;
    await gh(`/repos/${REPO}/issues/${i.number}/labels/${PENDING}`, "DELETE");
    i.labels = labelsOf(i).filter(l => l !== PENDING);
    await say(i, `**The fix in v${p.fix} didn't hold:** seen on v${vs.join(", v")}${folded ? ` ([bug:${b.sig}]: ${String(b.msg).slice(0, 80)})` : ""}. It's a normal open issue again. ` + sighting(b, more, s, folded));
    console.log(`fix didn't hold #${i.number} (v${p.fix}, seen on v${vs.join(", v")})`);
  } else {
    /* a closed issue whose latest sighting is from a version older than its fix ("Fixed in vN") stays closed:
       that phone just hadn't updated yet when it happened */
    let old = 0;
    if (i.state === "closed") {
      const cs = await comments(i);
      const fixV = Math.max(fixIn(cs), t && t.note.proof ? t.note.proof.fix : 0);
      old = fixV && +s.v > 0 && +s.v < fixV ? fixV : 0;
      /* one hitch after a fix proves nothing: a closed stutter needs REOPEN sightings since it was last acked */
      const g = !old && gate(b, more);
      if (g && !g.count) { console.log(`held #${i.number}, closed (${g.why}): ${b.msg}`); held++; continue; }
      if (!old) { await gh(`/repos/${REPO}/issues/${i.number}`, "PATCH", { state: "open" }); if (t && t.note.proof) { t.note.dirty = [...new Set([...(t.note.dirty || []), ...(t.note.proof.dirty || []), vNum(s.v)])]; delete t.note.proof; t.changed = true; } }
    }
    await say(i, `${old ? `**Seen on an older version (v${s.v}) than the fix (v${old}), so it stays closed.** ` : i.state === "closed" ? "**Happened again after it was closed.** " : ""}`
      + (folded ? `Same family: [bug:${b.sig}] (${String(b.msg).replace(/\s+/g, " ").slice(0, 80)}). ` : "") + sighting(b, more, s, folded));
    if (!old) i.state = "open";
    console.log((folded ? "folded [bug:" + b.sig + "] into #" : "updated #") + i.number);
  }
  acked[b.sig] = b.n;
}
if (held) console.log(held + " stutter(s) held until they repeat");

/* (2) proof: which releases the phones ran (from every report they sent, any kind, and their weekly usage record) */
const pend = [...T.values()].filter(isPending);
if (pend.length) {
  const ran = bugs.map(b => b.sample || {}).filter(s => vNum(s.v)).map(s => ({ fam: devFam(s.dev), w: +s.w || 0, v: vNum(s.v) }));
  try {
    const u = process.env.USAGE_FILE ? JSON.parse(fs.readFileSync(process.env.USAGE_FILE, "utf8")) : DRY ? null : await (await helper("/usage")).json();
    for (const p of (u && u.usage) || []) if (vNum(p.v)) ran.push({ fam: devFam(p.dev), w: 0, v: vNum(p.v) });
  } catch (e) { console.log("usage records not read: " + e.message); }
  for (const t of pend) {
    const p = t.note.proof;
    for (const r of ran) {
      if (r.v < p.fix || p.dirty.includes(r.v) || (p.dev && r.fam !== p.dev)) continue;
      const how = !p.w || (r.w && Math.abs(r.w - p.w) <= NEAR_W) ? "width" : "phone";
      if (p.clean[r.v] !== how && p.clean[r.v] !== "width") { p.clean[r.v] = how; t.changed = true; }
    }
    const vs = Object.keys(p.clean).map(Number).sort((a, b) => a - b), atW = vs.filter(v => p.clean[v] === "width");
    if (vs.length >= PROOF_N && atW.length) {
      p.done = true; t.changed = true;
      await say(t.i, `**Proven: fixed in v${p.fix}.** The phones used v${vs.join(", v")} with no sighting of this (${p.dev || "the phone"}${p.w ? ` at ${p.w}px` : ""} on v${atW.join(", v")}). Closing for real.`);
      await gh(`/repos/${REPO}/issues/${t.i.number}/labels/${PENDING}`, "DELETE");
      await gh(`/repos/${REPO}/issues/${t.i.number}`, "PATCH", { state: "closed", state_reason: "completed" });
      t.i.state = "closed";
      console.log(`proven #${t.i.number} (fixed in v${p.fix}: clean on v${vs.join(", v")})`);
    } else console.log(`#${t.i.number} pending proof: ${vs.length} of ${PROOF_N} clean releases${vs.length ? " (v" + vs.join(", v") + ")" : ""}${atW.length ? "" : `, none yet from ${p.dev || "the phone"}${p.w ? ` at ${p.w}px` : ""}`}`);
  }
}
for (const t of T.values()) if (typeof t.i.number === "number") await save(t);

if (DRY) { console.log("[dry] ack " + JSON.stringify(acked)); process.exit(0); }
if (Object.keys(acked).length) { const a = await helper("/bugs/ack", { sigs: acked }); console.log("acked", await a.text()); }
