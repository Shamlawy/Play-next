/* M40 (Oct 19 2026): timing swings 2x between runs, so a lag fix is judged by interleaved before/after runs and medians.
   Runs tools/scenarios/trace-all.mjs N times on each file, alternating (before, after, before, after…), and prints per phase
   the worst frame (biggest LoAF) and the main-thread total as median [min-max].
     node tools/trace-ab.mjs [--before tools/.cache/old.html] [--after index.html] [--runs 3] [--widths 859] [--only pill-queue,tab-queue]
   With no --before, origin/main's index.html is written to tools/.cache/old.html first. CPU/BUGOFF/CHROME env pass through. */
import { execFileSync, spawnSync } from "node:child_process";
import fs from "node:fs";
const arg = (k, d) => { const i = process.argv.indexOf("--" + k); return i > 0 ? process.argv[i + 1] : d; };
let before = arg("before");
const after = arg("after", "index.html"), runs = +arg("runs", 3), widths = arg("widths", "859"), only = arg("only");
if (!before) {
  fs.mkdirSync("tools/.cache", { recursive: true });
  fs.writeFileSync("tools/.cache/old.html", execFileSync("git", ["show", "origin/main:index.html"], { maxBuffer: 1 << 28 }));
  before = "tools/.cache/old.html";
}
const env = Object.assign({}, process.env, only ? { ONLY: only } : {});
const R = { before: {}, after: {} };
const parse = (out, into) => {
  let ph = null;
  for (const line of out.split("\n")) {
    let m = line.match(/^\s+\[([\w-]+)\] .*main thread (.*?) ms;/);
    if (m) { ph = m[1]; const tot = [...m[2].matchAll(/(\d+)/g)].reduce((a, x) => a + +x[1], 0);
      (into[ph] ||= { worst: [], main: [] }).main.push(tot); continue; }
    m = line.match(/^\s+LoAF: (\d+) frame/);
    if (m && ph && +m[1] === 0) into[ph].worst.push(0);
    m = line.match(/^\s{6}(\d+)ms = script/);
    if (m && ph && into[ph].worst.length < into[ph].main.length) into[ph].worst.push(+m[1]);
  }
};
for (let i = 0; i < runs; i++) for (const [k, f] of [["before", before], ["after", after]]) {
  process.stdout.write(`run ${i + 1}/${runs} ${k} (${f})… `);
  const r = spawnSync(process.execPath, ["tools/kit.mjs", "run", "tools/scenarios/trace-all.mjs", "--widths", widths, "--file", f], { env, encoding: "utf8", maxBuffer: 1 << 28 });
  if (r.status !== 0) { console.log("failed\n" + (r.stdout || "").slice(-2000) + (r.stderr || "").slice(-2000)); process.exit(1); }
  parse(r.stdout, R[k]); console.log("done");
}
const med = a => { if (!a.length) return "–"; const s = a.slice().sort((x, y) => x - y), n = s.length;
  const m = n % 2 ? s[(n - 1) / 2] : Math.round((s[n / 2 - 1] + s[n / 2]) / 2); return `${m} [${s[0]}-${s[n - 1]}]`; };
console.log(`\nworst frame / main thread, ms, median [min-max] of ${runs}, ${widths}px, CPU ${process.env.CPU || 4}x`);
console.log("phase".padEnd(16) + "worst before".padEnd(18) + "worst after".padEnd(18) + "main before".padEnd(20) + "main after");
for (const ph of Object.keys(R.after)) {
  const b = R.before[ph] || { worst: [], main: [] }, a = R.after[ph];
  console.log(ph.padEnd(16) + med(b.worst).padEnd(18) + med(a.worst).padEnd(18) + med(b.main).padEnd(20) + med(a.main));
}
