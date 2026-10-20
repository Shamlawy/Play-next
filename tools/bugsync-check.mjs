#!/usr/bin/env node
/* Proves .github/scripts/bugsync.mjs's filing rules on made-up reports, offline (DRY=1, BUGS_FILE, ISSUES_FILE):
   a one-off stutter is held until it repeats, a repeated or 400ms+ one is filed, a smoothness check / self-test in the
   normal range only goes to the log, a closed stutter needs 2 new sightings to reopen, everything else files as before.
   A second run (Oct 20 2026) checks the stutter rules: a fixed stutter stays pending proof until 3 clean releases (one from
   the same phone and width), sightings of one family (kind + screen + where the time went) fold into one open issue, and
   every stutter says whether its time was script, style/layout or drawing.
     node tools/bugsync-check.mjs        exit 1 on any case that went the wrong way */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "bugsync-"));
const smp = (v, dev) => ({ v, dev: dev || "Android Chrome 155 (installed)", screen: "games/queue", w: 859, h: 847, dpr: 2.63, stack: "1 long frame, 120ms: script 10ms" });
/* [sig, kind, msg, n, seen, version, device, expected] — expected: file | held | log | comment | reopen | stays */
const CASES = [
  ["a0000001", "slow", "Scrolling stutters on Library (1 hitches, worst 160ms)", 1, 0, "284", "", "held"],
  ["a0000002", "slow", "Scrolling stutters on Queue (2 hitches, worst 180ms)", 3, 0, "284", "", "file"],
  ["a0000003", "slow", "A 470ms stutter on Queue", 1, 0, "284", "", "file"],
  ["a0000004", "slow", "Smoothness check on this phone (v269, 60Hz): frames dropped normally: scroll 8%, sideways 15%, switching 5%. With each one switched off: shadows −5 pts, the moving background −5 pts", 1, 0, "269", "", "log"],
  ["a0000005", "slow", "Smoothness check on this phone (v269, 60Hz): frames dropped normally: scroll 8%, sideways 6%, switching 59%. With each one switched off: shadows −5 pts, the moving background +13 pts", 1, 0, "269", "iPad Safari 26 (installed)", "file"],
  ["a0000006", "slow", "Self-test (v269, 60Hz): Queue fling dropped 3% of frames (worst 31ms); swiping game pages: first 3 14%, last 3 18%. Queue pictures: 14 MP held for 6 MP shown (0 at 4K)", 1, 0, "269", "iPad Safari 26 (installed)", "log"],
  ["a0000007", "slow", "Self-test (v266, 60Hz): Queue fling dropped 53% of frames (worst 600ms); swiping game pages: first 3 6%, last 3 14%. Queue pictures: 372 MP held for 14 MP shown (14 at 4K)", 1, 0, "266", "", "file"],
  ["a0000008", "slow", "Switching to Library stuttered (1 hitch, worst 110ms)", 3, 2, "282", "", "stays"],
  ["a0000009", "slow", "Scrolling stutters on Replay (1 hitches, worst 310ms)", 3, 2, "284", "", "held"],
  ["a000000a", "slow", "Scrolling stutters on Library (1 hitches, worst 160ms)", 9, 7, "284", "", "reopen"],
  ["a000000b", "slow", "Scrolling stutters on Home (1 hitches, worst 170ms)", 17, 16, "284", "", "comment"],
  ["a000000c", "error", "Cannot read properties of undefined (reading 'x')", 1, 0, "284", "", "file"],
  ["a000000d", "slow", "Switching to Queue stuttered (1 hitch, worst 120ms)", 2, 0, "284", "", "held"],
  ["a000000e", "slow", "A tap took 260ms to respond on a game page", 1, 0, "284", "iPad Safari 26 (installed)", "held"],
  ["a000000f", "slow", "A touchmove listener that isn't passive sits on Queue (div.x): every swipe there has to wait", 1, 0, "284", "", "file"],
  ["a0000010", "slow", "Opening the app froze for 300ms", 1, 0, "284", "", "held"],
  ["a0000011", "slow", "A 450ms freeze on Home", 1, 0, "284", "", "file"],
  ["a0000012", "layout", "Part of the screen stayed blank while scrolling", 1, 0, "284", "", "file"]
];
const ISSUES = [[416, "a0000008", "closed"], [344, "a0000009", "closed"], [209, "a000000a", "closed"], [60, "a000000b", "open"]]
  .map(([number, sig, state]) => ({ number, title: `🐞 Slow: x [bug:${sig}]`, state, comments: state === "closed" ? [{ body: "Fixed in v283." }] : [] }));
const bugs = CASES.map(([sig, kind, msg, n, seen, v, dev]) => ({ sig, kind, msg, n, seen, first: 1760000000000, last: 1760500000000, vs: [v], devs: [dev || "Android"], screens: ["x"], sample: smp(v, dev) }));
fs.writeFileSync(path.join(dir, "bugs.json"), JSON.stringify({ bugs }));
fs.writeFileSync(path.join(dir, "issues.json"), JSON.stringify(ISSUES));

const out = execFileSync(process.execPath, [path.join(ROOT, ".github/scripts/bugsync.mjs")], { encoding: "utf8",
  env: { ...process.env, DRY: "1", REPO: "x/y", CF_TOKEN: "", BUGS_FILE: path.join(dir, "bugs.json"), ISSUES_FILE: path.join(dir, "issues.json") } });
const acked = JSON.parse((out.match(/^\[dry\] ack (.*)$/m) || [])[1] || "{}");
const num = sig => (ISSUES.find(i => i.title.includes(sig)) || {}).number;
let bad = 0;
for (const [sig, , msg, , , , , want] of CASES) {
  const n = num(sig);
  const got = new RegExp(`issues "[^"]*\\[bug:${sig}\\]"`).test(out) ? "file"
    : out.includes(`held [bug:${sig}]`) || (n && out.includes(`held #${n},`)) ? "held"
    : n && out.includes(`/issues/${n} "open"`) ? "reopen"
    : n && new RegExp(`/issues/${n}/comments "\\*\\*Seen on an older version`).test(out) ? "stays"
    : n && out.includes(`/issues/${n}/comments`) ? "comment"
    : out.includes("not filed (a measurement") && out.includes(msg) ? "log" : "?";
  /* held ones must not be acked (the helper keeps counting); everything else must be */
  const ackOk = want === "held" ? !(sig in acked) : sig in acked;
  const ok = got === want && ackOk;
  if (!ok) bad++;
  console.log(`${ok ? "ok  " : "FAIL"} ${want.padEnd(7)} ${got !== want ? `(got ${got}) ` : ""}${ackOk ? "" : "(ack wrong) "}${msg.slice(0, 80)}`);
}

/* Stutter families, pending proof, where the time went (Oct 20 2026): a second run with its own issues. Each rule gets
   cases that must go its way (DRY_LOG has every request the run would send). */
const NO_FRAME = "no long frame (50ms+) in this time: the app's code was free, so the hitches were drawing (GPU, pictures, layers)";
const SCRIPT = "1 long frame, 240ms: script 200ms (forced style/layout 5ms), style+layout 2ms, render 3ms\nlongest script 190ms: qPatch ← FrameRequestCallback";
const LAYOUT = "1 long frame, 80ms: script 70ms (forced style/layout 60ms), style+layout 5ms, render 2ms\nlongest script 70ms: look ← FrameRequestCallback";
const note = o => `\n\n<!-- bugsync ${JSON.stringify(o)} -->`;
const pend = (fam, o) => note({ fam, cause: "drawing", proof: { fix: 314, dev: "Android", w: 859, clean: {}, dirty: [], ...o } });
const iss = (number, sig, msg, state, extra) => ({ number, title: `🐞 Slow (Android): ${msg} [bug:${sig}]`, state, body: "**Slow** on **x**", labels: [{ name: "auto-bug" }], comments: [], ...extra });
const ISSUES2 = [
  iss(500, "b0000001", "Scrolling stutters on Played (2 hitches, worst 180ms)", "closed", { closed_at: "2026-10-20T06:00:00Z", state_reason: "completed",
    comments: [{ body: "Seen 1 more time (5 in total). Latest: v313 on Android Chrome 155 (installed), games/played, 859×847.\n\nTiming:\n```\n" + NO_FRAME + "\n```" }, { body: "Fixed in v314." }] }),
  iss(501, "b0000002", "Scrolling stutters on Month (2 hitches, worst 180ms)", "closed", { closed_at: "2026-10-19T06:00:00Z", state_reason: "completed", comments: [{ body: "Fixed in v312." }] }),
  iss(502, "b0000003", "Scrolling stutters on Replay (2 hitches, worst 180ms)", "closed", { closed_at: "2026-10-20T07:00:00Z", state_reason: "not_planned" }),
  iss(510, "b0000010", "Scrolling stutters on Insights (1 hitches, worst 150ms)", "open", { body: "x" + pend("scroll|insights"), labels: ["auto-bug", "pending-proof"] }),
  iss(511, "b0000011", "A tap took 260ms to respond on Library", "open", { body: "x" + pend("tap|library", { clean: { 314: "phone", 315: "phone" } }), labels: ["auto-bug", "pending-proof"] }),
  iss(512, "b0000012", "A tap took 300ms to respond on Queue", "open", { body: "x" + pend("tap|queue", { dev: "iPad", w: 1080, clean: { 314: "phone", 315: "phone" } }), labels: ["auto-bug", "pending-proof"] }),
  iss(513, "b0000013", "Switching to Home stuttered (1 hitch, worst 200ms)", "open", { body: "x" + pend("switch|home"), labels: ["auto-bug", "pending-proof"] }),
  iss(520, "b0000020", "A 470ms stutter on Queue", "open", { body: "x" + note({ fam: "stutter|queue", cause: "drawing" }) }),
  iss(530, "b0000030", "Switching to Settings stuttered (1 hitch, worst 120ms)", "open", { body: "x" + note({ fam: "switch|settings", cause: "drawing" }) }),
  iss(531, "b0000031", "Switching to Settings stuttered (5 hitches, worst 190ms)", "open", { body: "x" + note({ fam: "switch|settings", cause: "drawing" }) }),
  iss(532, "b0000032", "Switching to Settings stuttered (2 hitches, worst 150ms)", "open", { body: "x" + note({ fam: "switch|settings", cause: "unknown" }) }),
  iss(540, "b0000040", "Scrolling stutters on Home (1 hitches, worst 170ms)", "open", { comments: [{ body: "Seen 1 more time (2 in total). Latest: v311 on Android Chrome 155 (installed), home, 859×847.\n\nTiming:\n```\n" + SCRIPT + "\n```" }] })
];
const bug = (sig, kind, msg, n, seen, vs, stack, dev, w) => ({ sig, kind, msg, n, seen, first: 1760000000000, last: 1760500000000, vs, devs: [dev || "Android"], screens: ["x"],
  sample: { v: String(vs[vs.length - 1]), dev: dev || "Android Chrome 155 (installed)", screen: "x", w: w || 859, h: 847, dpr: 2.63, stack } });
const BUGS2 = [
  bug("b0000010", "slow", "Scrolling stutters on Insights (1 hitches, worst 150ms)", 4, 3, [313], NO_FRAME),
  bug("b00000e0", "error", "Cannot read properties of null (reading 'y')", 1, 0, [316], "at x"),
  bug("b0000013", "slow", "Switching to Home stuttered (1 hitch, worst 200ms)", 3, 1, [313, 315], NO_FRAME),
  bug("b0000021", "slow", "A 230ms stutter on Queue", 1, 0, [315], NO_FRAME),
  bug("b0000022", "slow", "A 250ms stutter on Queue", 3, 0, [315], SCRIPT),
  bug("b0000070", "slow", "A 120ms stutter on Library", 2, 0, [315], NO_FRAME),
  bug("b0000071", "slow", "A 140ms stutter on Library", 1, 0, [315], NO_FRAME),
  bug("b0000060", "slow", "Scrolling stutters on Month (2 hitches, worst 80ms)", 3, 0, [315], LAYOUT)
];
fs.writeFileSync(path.join(dir, "bugs2.json"), JSON.stringify({ bugs: BUGS2 }));
fs.writeFileSync(path.join(dir, "issues2.json"), JSON.stringify(ISSUES2));
fs.writeFileSync(path.join(dir, "usage2.json"), JSON.stringify({ usage: [{ id: "u1", dev: "iPad Safari 26 (installed)", v: "316" }] }));
const LOG = path.join(dir, "log2.jsonl");
const out2 = execFileSync(process.execPath, [path.join(ROOT, ".github/scripts/bugsync.mjs")], { encoding: "utf8",
  env: { ...process.env, DRY: "1", REPO: "x/y", CF_TOKEN: "", APP_V: "315", BUGS_FILE: path.join(dir, "bugs2.json"), ISSUES_FILE: path.join(dir, "issues2.json"), USAGE_FILE: path.join(dir, "usage2.json"), DRY_LOG: LOG } });
const reqs = fs.existsSync(LOG) ? fs.readFileSync(LOG, "utf8").trim().split("\n").filter(Boolean).map(l => JSON.parse(l)) : [];
fs.rmSync(dir, { recursive: true, force: true });
const acked2 = JSON.parse((out2.match(/^\[dry\] ack (.*)$/m) || [])[1] || "{}");
const to = (n, m, tail = "") => reqs.filter(r => r.method === m && r.path === `/repos/x/y/issues/${n}${tail}`);
const said = (n, re) => to(n, "POST", "/comments").some(r => re.test(r.body.body));
const noteNow = n => { const b = to(n, "PATCH").map(r => r.body.body).filter(Boolean).pop(); return b ? JSON.parse(/<!-- bugsync (\{.*\}) -->/.exec(b)[1]) : null; };
const filed = sig => reqs.find(r => r.method === "POST" && r.path === "/repos/x/y/issues" && r.body.title.includes(`[bug:${sig}]`));
const RULES = [
  ["pending: a stutter closed as fixed today reopens as pending proof, fix v314 from its comment", () => to(500, "PATCH").some(r => r.body.state === "open") && to(500, "POST", "/labels").some(r => r.body.labels.includes("pending-proof")) && said(500, /Pending proof \(fixed in v314\)/) && noteNow(500)?.proof?.fix === 314 && noteNow(500)?.proof?.w === 859],
  ["pending: one closed before the rule started stays closed", () => !reqs.some(r => r.path.includes("/issues/501"))],
  ["pending: one closed as not planned stays closed", () => !reqs.some(r => r.path.includes("/issues/502"))],
  ["pending: a sighting from a version older than the fix doesn't count (no comment, acked)", () => !said(510, /./) && "b0000010" in acked2 && !to(510, "DELETE", "/labels/pending-proof").length],
  ["pending: 3 clean releases, one from the same phone at the same width → closes for real", () => to(511, "PATCH").some(r => r.body.state === "closed") && said(511, /Proven: fixed in v314/) && to(511, "DELETE", "/labels/pending-proof").length === 1],
  ["pending: 3 clean releases but none at its width → stays pending", () => !to(512, "PATCH").some(r => r.body.state === "closed") && noteNow(512)?.proof?.clean?.["316"] === "phone"],
  ["pending: 2 sightings on the fix or later → the fix didn't hold, label off", () => to(513, "DELETE", "/labels/pending-proof").length === 1 && said(513, /fix in v314 didn't hold:\*\* seen on v315/) && !noteNow(513)?.proof],
  ["family: a new hitch size with the same cause folds into the open issue (no new number, acked)", () => !filed("b0000021") && said(520, /Same family: \[bug:b0000021\]/) && "b0000021" in acked2 && noteNow(520)?.also?.includes("b0000021")],
  ["family: same screen and kind but the time went to script → its own issue", () => !!filed("b0000022")],
  ["family: two open issues of one family fold into the oldest", () => to(531, "PATCH").some(r => r.body.state === "closed" && r.body.state_reason === "not_planned") && said(531, /Folded into #530/) && said(530, /Folded in #531/) && noteNow(530)?.also?.includes("b0000031")],
  ["family: an open one without timing isn't folded into one with a known cause", () => !to(532, "PATCH").some(r => r.body.state === "closed")],
  ["family: an open issue without a note gets its cause from its latest timing", () => noteNow(540)?.cause === "script" && noteNow(540)?.fam === "scroll|home"],
  ["family: one-offs of one family count together (2 + 1 sightings → filed once, the other joins it)", () => !!filed("b0000070") && !filed("b0000071") && reqs.some(r => r.path.endsWith("/issues/new/comments") && /Same family: \[bug:b0000071\]/.test(r.body.body))],
  ["where: a new issue says the time went to style/layout", () => /Where the time went: style and layout/.test(filed("b0000060")?.body.body || "") && out2.includes("where [bug:b0000060]: layout")],
  ["where: script names the longest function; drawing says so", () => /Where the time went: the app's own code \(script\).*Longest script: 190ms: qPatch/.test(filed("b0000022")?.body.body || "") && out2.includes("where [bug:b0000021]: drawing")]
];
for (const [what, test] of RULES) { let ok = false; try { ok = !!test(); } catch (e) {} if (!ok) bad++; console.log(`${ok ? "ok  " : "FAIL"} ${what}`); }
if (bad) console.log(out2);
console.log(bad ? `${bad} case(s) went the wrong way` : `all ${CASES.length + RULES.length} cases went the right way`);
process.exit(bad ? 1 : 0);
