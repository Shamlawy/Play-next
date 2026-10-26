#!/usr/bin/env node
/* Proves .github/scripts/bugsync.mjs's filing rules on made-up reports, offline (DRY=1, BUGS_FILE, ISSUES_FILE):
   a one-off stutter is held until it repeats, a repeated or 400ms+ one is filed, a smoothness check / self-test in the
   normal range only goes to the log, a closed stutter needs 2 new sightings to reopen, everything else files as before.
   A second run (Oct 20 2026) checks the stutter rules: a fixed stutter stays pending proof until 3 clean releases (one from
   the same phone and width), sightings of one family (kind + screen + where the time went) fold into one open issue, and
   every stutter says whether its time was script, style/layout or drawing.
   A third run (Oct 26 2026) checks the native Android app's reports: they get their own android-bug issues (never
   auto-bug), routing, families, "Fixed in android-vN" versions (the newest android-vN release, not index.html's APP_V) and
   proof from the app's own reports only; its ran / probe / killed kinds only go to the log.
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
let bad2 = 0;
for (const [what, test] of RULES) { let ok = false; try { ok = !!test(); } catch (e) {} if (!ok) bad2++; console.log(`${ok ? "ok  " : "FAIL"} ${what}`); }
if (bad2) console.log(out2);
bad += bad2;

/* The native Android app (Oct 26 2026): a third run with its own issues, releases and reports. The app marks its reports
   app "android" (or, before the helper keeps that field, a phone line starting "App "), sends its versionCode as v, and is
   released as GitHub releases android-vN. The web app runs at APP_V 330 here, far above any app version, so mixing the two
   up shows. */
const dir3 = fs.mkdtempSync(path.join(os.tmpdir(), "bugsync3-"));
const APPDEV = "App Android 16 SM-F9xx unfolded", WEBDEV = "Android Chrome 155 (installed)";
const table = (v, dev, w) => `**Slow** on **x**\n\n| | |\n|---|---|\n| Times | 3 |\n| Versions | ${v} |\n| Phones | ${dev} |\n| Screens | x |\n| Latest | ${w}×847 @2.63x, clean style |`;
const iss3 = (number, sig, title, state, labels, extra) => ({ number, title: `🐞 ${title} [bug:${sig}]`, state, body: "x", labels: labels.map(name => ({ name })), comments: [], ...extra });
const today = { closed_at: "2026-10-25T06:00:00Z", state_reason: "completed" };
const ISSUES3 = [
  iss3(600, "c0000002", "Crash (Android): Cannot read properties of null (reading 'q')", "open", ["auto-bug"]),
  iss3(601, "c0000003", "Crash (App): IllegalStateException in QueueList", "open", ["android-bug"]),
  iss3(610, "c0000010", "Slow (Android): A 470ms stutter on Queue", "open", ["auto-bug"], { body: "x" + note({ fam: "stutter|queue", cause: "drawing" }) }),
  iss3(611, "c0000014", "Slow (Android): Switching to Settings stuttered (1 hitch, worst 120ms)", "open", ["auto-bug"], { body: "x" + note({ fam: "switch|settings", cause: "drawing" }) }),
  iss3(612, "c0000015", "Slow (App): Switching to Settings stuttered (2 hitches, worst 150ms)", "open", ["android-bug"], { body: "x" + note({ fam: "switch|settings", cause: "drawing" }) }),
  iss3(620, "c0000020", "Crash (App): NullPointerException in GamePage", "closed", ["android-bug"], { ...today, comments: [{ body: "Fixed in android-v12." }] }),
  iss3(621, "c0000021", "Crash (App): IndexOutOfBoundsException in Shelf", "closed", ["android-bug"], { ...today, comments: [{ body: "Fixed in v12." }] }),
  iss3(623, "c0000023", "Crash (App): ConcurrentModificationException in Sync", "closed", ["android-bug"], { ...today, comments: [{ body: "Fixed in android-v12." }] }),
  iss3(630, "c0000030", "Slow (App): A 260ms stutter on Home", "closed", ["android-bug"], { ...today, body: table("9, 10", APPDEV, 859) }),
  iss3(640, "c0000040", "Slow (App): Scrolling stutters on Insights (1 hitches, worst 150ms)", "open", ["android-bug", "pending-proof"],
    { body: "x" + note({ fam: "scroll|insights", cause: "drawing", proof: { fix: 20, dev: "App", w: 859, clean: {}, dirty: [], app: "android" } }) }),
  iss3(641, "c0000041", "Slow (App): Scrolling stutters on Played (2 hitches, worst 180ms)", "open", ["android-bug", "pending-proof"],
    { body: "x" + note({ fam: "scroll|played", cause: "drawing", proof: { fix: 12, dev: "App", w: 859, clean: {}, dirty: [], app: "android" } }) })
];
/* app: true = marked app "android" (after the helper deploy), "dev" = only the "App " phone line (before it), false = the web app */
const bug3 = (sig, kind, msg, n, seen, v, o = {}) => ({ sig, kind, msg, n, seen, first: 1760000000000, last: 1760500000000, vs: [String(v)], devs: [o.dev || (o.app === false ? WEBDEV : APPDEV)], screens: ["x"],
  sample: { kind, msg, v: String(v), dev: o.dev || (o.app === false ? WEBDEV : APPDEV), screen: "queue", w: o.w || 600, h: 847, dpr: 2.63, stack: o.stack || "", note: o.note || "", ...(o.app === true ? { app: "android" } : {}) } });
const PROBE_MSG = "120 Hz test: 118.6 fps over a 10 s fling, 0.4% janky frames, worst frame 21ms", PROBE_NOTE = "40 cards, one loop on top; idle 5 s: 120.0 fps";
const BUGS3 = [
  bug3("c0000001", "error", "IllegalArgumentException in Settings", 1, 0, 3, { app: true }),
  bug3("c0000004", "stuck", "A tap on Queue did nothing for 3 s", 1, 0, 3, { app: "dev", dev: "App Android 16 SM-F9xx folded" }),
  bug3("c0000002", "error", "Cannot read properties of null (reading 'q')", 1, 0, 11, { app: true }),
  bug3("c0000003", "error", "IllegalStateException in QueueList", 1, 0, 330, { app: false }),
  bug3("c0000011", "slow", "A 480ms stutter on Queue", 1, 0, 13, { app: true, stack: NO_FRAME }),
  bug3("c0000012", "slow", "A 230ms stutter on Library", 2, 0, 330, { app: false, stack: NO_FRAME, w: 859 }),
  bug3("c0000013", "slow", "A 240ms stutter on Library", 1, 0, 11, { app: true, stack: NO_FRAME }),
  bug3("c0000020", "error", "NullPointerException in GamePage", 3, 2, 11, { app: true }),
  bug3("c0000021", "error", "IndexOutOfBoundsException in Shelf", 2, 1, 11, { app: true }),
  bug3("c0000023", "error", "ConcurrentModificationException in Sync", 2, 1, 12, { app: true }),
  /* the web app in Chrome on the same Fold, same width, versions far above both fixes: held one-offs, never proof for the app */
  bug3("c0000050", "slow", "Scrolling stutters on Library (1 hitches, worst 160ms)", 1, 0, 316, { app: false, w: 859, stack: NO_FRAME }),
  bug3("c0000051", "slow", "Scrolling stutters on Month (1 hitches, worst 160ms)", 1, 0, 317, { app: false, w: 859, stack: NO_FRAME }),
  bug3("c0000052", "slow", "Scrolling stutters on Replay (1 hitches, worst 160ms)", 1, 0, 318, { app: false, w: 859, stack: NO_FRAME }),
  /* the app's log-only kinds */
  bug3("d0000001", "ran", "This version ran", 1, 0, 12, { app: true }),
  bug3("d0000002", "ran", "This version ran", 1, 0, 13, { app: "dev" }),
  bug3("d0000003", "ran", "This version ran", 1, 0, 14, { app: true, w: 859 }),
  bug3("d0000004", "probe", PROBE_MSG, 1, 0, 14, { app: true, w: 859, note: PROBE_NOTE, stack: "frame p50 8.3ms, p95 9.1ms, p99 12.4ms" }),
  bug3("d0000005", "killed", "Android ended the app: low memory", 1, 0, 14, { app: true })
];
const RELEASES3 = [{ tag_name: "android-v20", draft: true, prerelease: false }, { tag_name: "android-v19", draft: false, prerelease: true },
  { tag_name: "android-v13", draft: false, prerelease: false }, { tag_name: "android-v12", draft: false, prerelease: false }, { tag_name: "v400", draft: false, prerelease: false }];
fs.writeFileSync(path.join(dir3, "bugs3.json"), JSON.stringify({ bugs: BUGS3 }));
fs.writeFileSync(path.join(dir3, "issues3.json"), JSON.stringify(ISSUES3));
fs.writeFileSync(path.join(dir3, "releases3.json"), JSON.stringify(RELEASES3));
fs.writeFileSync(path.join(dir3, "usage3.json"), JSON.stringify({ usage: [316, 317, 318].map(v => ({ id: "u" + v, dev: WEBDEV, v: String(v) })) }));
const LOG3 = path.join(dir3, "log3.jsonl");
const env3 = { ...process.env, DRY: "1", REPO: "x/y", CF_TOKEN: "", APP_V: "330", ANDROID_V: "", BUGS_FILE: path.join(dir3, "bugs3.json"), ISSUES_FILE: path.join(dir3, "issues3.json"),
  RELEASES_FILE: path.join(dir3, "releases3.json"), USAGE_FILE: path.join(dir3, "usage3.json"), DRY_LOG: LOG3 };
const out3 = execFileSync(process.execPath, [path.join(ROOT, ".github/scripts/bugsync.mjs")], { encoding: "utf8", env: env3 });
/* ANDROID_V overrides the releases: the same closed app stutter, nothing else */
fs.writeFileSync(path.join(dir3, "issues3b.json"), JSON.stringify(ISSUES3.filter(i => i.number === 630)));
fs.writeFileSync(path.join(dir3, "bugs3b.json"), JSON.stringify({ bugs: [] }));
const out3b = execFileSync(process.execPath, [path.join(ROOT, ".github/scripts/bugsync.mjs")], { encoding: "utf8",
  env: { ...env3, ANDROID_V: "14", ISSUES_FILE: path.join(dir3, "issues3b.json"), BUGS_FILE: path.join(dir3, "bugs3b.json"), DRY_LOG: path.join(dir3, "log3b.jsonl") } });
const reqs3 = fs.readFileSync(LOG3, "utf8").trim().split("\n").filter(Boolean).map(l => JSON.parse(l));
const reqs3b = fs.readFileSync(path.join(dir3, "log3b.jsonl"), "utf8").trim().split("\n").filter(Boolean).map(l => JSON.parse(l));
fs.rmSync(dir3, { recursive: true, force: true });
const acked3 = JSON.parse((out3.match(/^\[dry\] ack (.*)$/m) || [])[1] || "{}");
const to3 = (n, m, tail = "") => reqs3.filter(r => r.method === m && r.path === `/repos/x/y/issues/${n}${tail}`);
const said3 = (n, re) => to3(n, "POST", "/comments").some(r => re.test(r.body.body));
const touched3 = n => reqs3.some(r => r.path.startsWith(`/repos/x/y/issues/${n}/`) || r.path === `/repos/x/y/issues/${n}`);
const noteNow3 = n => { const b = to3(n, "PATCH").map(r => r.body.body).filter(Boolean).pop(); return b ? JSON.parse(/<!-- bugsync (\{.*\}) -->/.exec(b)[1]) : null; };
const filed3 = sig => reqs3.filter(r => r.method === "POST" && r.path === "/repos/x/y/issues" && r.body.title.includes(`[bug:${sig}]`));
const one3 = sig => { const f = filed3(sig); return f.length === 1 ? f[0].body : null; };
const appIssue = sig => { const b = one3(sig); return !!b && JSON.stringify(b.labels) === '["android-bug"]' && /^🐞 \w+ \(App\): /.test(b.title); };
const noComment = sig => !reqs3.some(r => r.path.endsWith("/comments") && r.body.body.includes(`[bug:${sig}]`));
const RULES3 = [
  ["app: the android-bug label is made next to auto-bug and pending-proof", () => reqs3.some(r => r.path === "/repos/x/y/labels" && r.body.name === "android-bug") && reqs3.some(r => r.path === "/repos/x/y/labels" && r.body.name === "auto-bug")],
  ["app: a report marked app android is filed with android-bug only, title (App), its versionCode in the table, no Safari hint", () => appIssue("c0000001")
    && /\| Android app \| android-v3 \(versionCode 3\) \|/.test(one3("c0000001").body) && !/Safari/.test(one3("c0000001").body) && "c0000001" in acked3],
  ["app: before the helper keeps app, a phone line starting \"App \" is enough", () => appIssue("c0000004")],
  ["app: an app report whose signature equals an open web issue's gets its own issue (no comment on the web one)", () => appIssue("c0000002") && !touched3(600)],
  ["app: and a web report whose signature equals an open app issue's gets its own auto-bug issue", () => JSON.stringify(one3("c0000003")?.labels) === '["auto-bug"]' && /\(Android\)/.test(one3("c0000003").title) && !touched3(601)],
  ["family: an app stutter doesn't fold into an open web issue of the same family (its own issue)", () => appIssue("c0000011") && !touched3(610) && /"fam":"stutter\|queue"/.test(one3("c0000011").body)],
  ["family: an open app issue and an open web issue of one family don't fold into each other", () => !touched3(611) && !touched3(612) && !/folded #61[12]/.test(out3)],
  ["family: web and app one-offs of one family don't count together (2 web + 1 app sightings → both held, not acked)", () => out3.includes("held [bug:c0000012]") && out3.includes("held [bug:c0000013]")
    && !filed3("c0000012").length && !filed3("c0000013").length && !("c0000012" in acked3) && !("c0000013" in acked3)],
  ["version: a closed app issue \"Fixed in android-v12\" seen on versionCode 11 stays closed", () => said3(620, /^\*\*Seen on an older version \(android-v11\) than the fix \(android-v12\), so it stays closed/) && !to3(620, "PATCH").length && "c0000020" in acked3],
  ["version: plain \"Fixed in v12\" on an app issue is the versionCode too", () => said3(621, /Seen on an older version \(android-v11\) than the fix \(android-v12\)/) && !to3(621, "PATCH").length],
  ["version: seen on the fix's own versionCode it reopens (web APP_V 330 plays no part)", () => to3(623, "PATCH").some(r => r.body.state === "open") && said3(623, /Happened again after it was closed/)],
  ["version: an app stutter closed with no fix line is pending proof from the newest android-vN release (13: not a draft, prerelease or web tag), not v330",
    () => said3(630, /^\*\*Pending proof \(fixed in android-v13\)\.\*\* .*the Android app has used 3 releases from android-v13 on/) && noteNow3(630)?.proof?.fix === 13 && noteNow3(630)?.proof?.app === "android"
      && to3(630, "POST", "/labels").some(r => r.body.labels.includes("pending-proof")) && out3.includes("android app: newest release android-v13")],
  ["version: ANDROID_V overrides the releases", () => reqs3b.some(r => r.path === "/repos/x/y/issues/630/comments" && /^\*\*Pending proof \(fixed in android-v14\)/.test(r.body.body)) && out3b.includes("android-v14 (ANDROID_V)")],
  ["proof: web Android Chrome reports and usage records at the same width don't count for an app fix", () => !touched3(640) && out3.includes("#640 pending proof: 0 of 3 clean releases")],
  ["proof: the app's ran reports for 3 versions (one at its width) prove it: closed, only app versions counted", () => to3(641, "PATCH").some(r => r.body.state === "closed")
    && said3(641, /^\*\*Proven: fixed in android-v12\.\*\* The Android app ran android-v12, android-v13, android-v14 with no sighting of this \(the same phone at 859px on android-v14\)/)
    && JSON.stringify(Object.keys(noteNow3(641)?.proof?.clean || {})) === '["12","13","14"]' && to3(641, "DELETE", "/labels/pending-proof").length === 1],
  ["log only: ran, probe and killed make no issue and no comment, and are acked", () => ["d0000001", "d0000002", "d0000003", "d0000004", "d0000005"].every(s => !filed3(s).length && noComment(s) && s in acked3 && !out3.includes(`held [bug:${s}]`))
    && !reqs3.some(r => r.method === "POST" && r.path === "/repos/x/y/issues" && /\[bug:d/.test(r.body.title))],
  ["log only: the probe's whole message and note are in the run's log", () => out3.includes(PROBE_MSG) && out3.includes(PROBE_NOTE) && out3.includes("p99 12.4ms") && out3.includes("killed [bug:d0000005]")]
];
let bad3 = 0;
for (const [what, test] of RULES3) { let ok = false; try { ok = !!test(); } catch (e) {} if (!ok) bad3++; console.log(`${ok ? "ok  " : "FAIL"} ${what}`); }
if (bad3) console.log(out3);
bad += bad3;
console.log(bad ? `${bad} case(s) went the wrong way` : `all ${CASES.length + RULES.length + RULES3.length} cases went the right way`);
process.exit(bad ? 1 : 0);
