// Files the app's automatic problem reports (collected by Nexi's helper) as GitHub issues.
// One issue per problem, titled "... [bug:<sig>]" with the label auto-bug. A problem that happens again
// gets a comment on its issue (and the issue reopens if it was closed). Runs from .github/workflows/bugs.yml.
// The helper's BUG_KEY is sha256(CLOUDFLARE_API_TOKEN + ":play-next-bugs"); deploy-helper.yml sets the same value.
// Testing: BUGS_FILE=<json {bugs:[…]}> reads saved reports instead of the helper, ISSUES_FILE=<json [{number, title, state,
// comments:[{body}]}]> stands in for GitHub's issues, and DRY=1 prints what it would file, comment and ack instead of doing it.
import { createHash } from "node:crypto";
import fs from "node:fs";

const HELPER = process.env.HELPER_URL || "https://playnext-helper.hussamnabil48.workers.dev";
const API = process.env.GH_API || "https://api.github.com";
const REPO = process.env.REPO, GH = process.env.GH_TOKEN;
const CF = String(process.env.CF_TOKEN || "").replace(/\s+/g, "");
const LABEL = "auto-bug", MAX_NEW = 15, DRY = !!process.env.DRY;
const fixture = k => process.env[k] ? JSON.parse(fs.readFileSync(process.env[k], "utf8")) : null;
const BUGS = fixture("BUGS_FILE"), ISSUES = fixture("ISSUES_FILE");
if (!CF && !BUGS) { console.log("::warning::No CLOUDFLARE_API_TOKEN secret, so there's no key to read the reports with."); process.exit(0); }
const KEY = createHash("sha256").update(CF + ":play-next-bugs").digest("hex");

const helper = (path, body) => fetch(HELPER + path, { method: body ? "POST" : "GET", headers: { Authorization: "Bearer " + KEY, "Content-Type": "application/json" }, body: body && JSON.stringify(body) });
const gh = async (path, method, body) => {
  if (DRY) {
    if (method && method !== "GET") { console.log(`[dry] ${method} ${path} ` + JSON.stringify(body ? body.title || body.state || String(body.body || "").split("\n")[0] : "")); return { number: "new" }; }
    const c = /\/issues\/(\d+)\/comments/.exec(path);
    if (c) return ((ISSUES || []).find(i => i.number === +c[1]) || {}).comments || [];
    return /[?&]page=1\b/.test(path) ? ISSUES || [] : [];
  }
  const r = await fetch(API + path, { method: method || "GET", headers: { Authorization: "Bearer " + GH, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "play-next-bugs" }, body: body && JSON.stringify(body) });
  if (!r.ok && r.status !== 422) throw new Error(method + " " + path + " → " + r.status + " " + (await r.text()).slice(0, 200));
  return r.status === 204 ? null : r.json();
};

const r = BUGS ? { ok: true, status: 200, json: async () => BUGS } : await helper("/bugs");
if (r.status === 503) { console.log("::warning::The helper has no BUG_KEY yet. It's set when deploy-helper runs (push to worker/** or run it by hand)."); process.exit(0); }
if (!r.ok) { console.log("::error::The helper said " + r.status + " " + (await r.text()).slice(0, 200)); process.exit(1); }
const bugs = (await r.json()).bugs || [];
console.log(bugs.length + " problem(s) with new reports");
if (!bugs.length) process.exit(0);

await gh(`/repos/${REPO}/labels`, "POST", { name: LABEL, color: "d73a4a", description: "Reported automatically by the app" });
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

const acked = {}; let made = 0, held = 0;
for (const b of bugs) {
  const i = bySig.get(b.sig), s = b.sample || {};
  if (!i) {
    const g = gate(b);
    if (g && !g.count) {
      if (g.measure) { console.log(`not filed (${g.why}): ${b.msg}${s.stack ? "\n  " + String(s.stack).replace(/\n/g, "\n  ") : ""}`); acked[b.sig] = b.n; }
      else { console.log(`held [bug:${b.sig}] (${g.why}): ${b.msg}`); held++; }
      continue;
    }
    if (made >= MAX_NEW) continue;
    /* which kind of device: the friend uses an iPhone and an iPad (Safari), the owner an Android Fold (Chrome) */
    const fam = (String(s.dev || "").match(/^(iPhone|iPad|Android|Mac|Windows)/) || [])[1];
    const title = `🐞 ${KIND[b.kind] || b.kind}${fam ? " (" + fam + ")" : ""}: ${String(b.msg).replace(/\s+/g, " ").slice(0, 80)} [bug:${b.sig}]`;
    const n = await gh(`/repos/${REPO}/issues`, "POST", { title, body: body(b), labels: [LABEL] });
    console.log("new issue #" + n.number + " " + title); made++;
  } else {
    const more = b.n - (b.seen || 0);
    /* a closed issue whose latest sighting is from a version older than its fix ("Fixed in vN") stays closed:
       that phone just hadn't updated yet when it happened */
    let old = 0;
    if (i.state === "closed") {
      const cs = await gh(`/repos/${REPO}/issues/${i.number}/comments?per_page=100`);
      const fixV = Math.max(0, ...cs.map(c => +((String(c.body).match(/fixed in v(\d+)/i) || [])[1] || 0)));
      old = fixV && +s.v > 0 && +s.v < fixV ? fixV : 0;
      /* one hitch after a fix proves nothing: a closed stutter needs REOPEN sightings since it was last acked */
      const g = !old && gate(b, more);
      if (g && !g.count) { console.log(`held #${i.number}, closed (${g.why}): ${b.msg}`); held++; continue; }
      if (!old) await gh(`/repos/${REPO}/issues/${i.number}`, "PATCH", { state: "open" });
    }
    await gh(`/repos/${REPO}/issues/${i.number}/comments`, "POST", { body:
      `${old ? `**Seen on an older version (v${s.v}) than the fix (v${old}), so it stays closed.** ` : i.state === "closed" ? "**Happened again after it was closed.** " : ""}Seen ${more} more time${more === 1 ? "" : "s"} (${b.n} in total). Latest: v${s.v} on ${s.dev}, ${s.screen}, ${s.w}×${s.h}.${s.note ? `\n\n> ${s.note}` : ""}${b.kind === "slow" && s.stack ? "\n\nTiming:\n" + fence(s.stack) : ""}` });   /* v283 (M11): what the latest stutter spent its time on */
    console.log("updated #" + i.number);
  }
  acked[b.sig] = b.n;
}
if (held) console.log(held + " stutter(s) held until they repeat");
if (DRY) { console.log("[dry] ack " + JSON.stringify(acked)); process.exit(0); }
const a = await helper("/bugs/ack", { sigs: acked });
console.log("acked", await a.text());
