#!/usr/bin/env node
/* Proves .github/scripts/bugsync.mjs's filing rules on made-up reports, offline (DRY=1, BUGS_FILE, ISSUES_FILE):
   a one-off stutter is held until it repeats, a repeated or 400ms+ one is filed, a smoothness check / self-test in the
   normal range only goes to the log, a closed stutter needs 2 new sightings to reopen, everything else files as before.
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
fs.rmSync(dir, { recursive: true, force: true });
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
console.log(bad ? `${bad} case(s) went the wrong way` : `all ${CASES.length} cases went the right way`);
process.exit(bad ? 1 : 0);
