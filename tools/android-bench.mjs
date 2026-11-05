// Gate 3's verdict (android-bench.yml): reads the four Macrobenchmark runs tools/android-bench.sh pulled off the emulator
// (main-1, pr-1, main-2, pr-2) and says whether the PR is clearly slower than main. An emulator on a shared runner is
// noisy, so a slowdown counts only when the PR is worse in BOTH rounds by more than a share AND an amount (LIMITS).
// Prints a table (and adds it to the job summary); exit 1 on a clear slowdown, 2 on missing data.
//   node tools/android-bench.mjs <dir>      node tools/android-bench.mjs --self-test
import { readFileSync, appendFileSync, existsSync } from "node:fs";
import { join } from "node:path";

/** What is compared: [test, metric, which number, worse by more than this share, and by more than this amount]. */
export const LIMITS = [
  ["startup", "timeToInitialDisplayMs", "median", 0.15, 60],
  ["queueScroll", "frameDurationCpuMs", "P50", 0.2, 2],
  ["queueScroll", "frameDurationCpuMs", "P90", 0.2, 3],
  ["queueScroll", "frameOverrunMs", "P90", 0.25, 4],
];

/** One number out of a run's JSON: metrics hold median/min/max, sampledMetrics hold P50/P90/P95/P99. */
export function pick(data, test, metric, which) {
  const b = (data.benchmarks || []).find(x => x.name === test || (x.name || "").startsWith(test + "["));
  if (!b) return null;
  const m = (b.metrics || {})[metric] || (b.sampledMetrics || {})[metric];
  const v = m ? m[which] : undefined;
  return typeof v === "number" ? v : null;
}

/** Each limit with both rounds' numbers and whether it is a clear slowdown. */
export function judge(runs) {
  return LIMITS.map(([test, metric, which, share, amount]) => {
    const rounds = [1, 2].map(r => {
      const a = pick(runs[`main-${r}`], test, metric, which);
      const b = pick(runs[`pr-${r}`], test, metric, which);
      const worse = a != null && b != null && b - a > amount && b > a * (1 + share);
      return { a, b, worse };
    });
    const missing = rounds.some(x => x.a == null || x.b == null);
    return { name: `${test} ${metric} ${which}`, share, amount, rounds, missing, slow: !missing && rounds.every(x => x.worse) };
  });
}

const f = v => (v == null ? "–" : v.toFixed(1));

export function table(rows) {
  const lines = ["| What | Main 1 | PR 1 | Main 2 | PR 2 | Verdict |", "|---|---|---|---|---|---|"];
  for (const r of rows) {
    const [x, y] = r.rounds;
    const verdict = r.missing ? "missing" : r.slow ? `**slower** (over +${r.share * 100}% and +${r.amount} ms in both rounds)` : "ok";
    lines.push(`| ${r.name} | ${f(x.a)} | ${f(x.b)} | ${f(y.a)} | ${f(y.b)} | ${verdict} |`);
  }
  return lines.join("\n");
}

function selfTest() {
  const run = (start, p50, p90, over) => ({
    benchmarks: [
      { name: "startup", metrics: { timeToInitialDisplayMs: { median: start } } },
      { name: "queueScroll", metrics: { frameCount: { median: 300 } }, sampledMetrics: { frameDurationCpuMs: { P50: p50, P90: p90 }, frameOverrunMs: { P90: over } } },
    ],
  });
  const same = { "main-1": run(400, 5, 9, -2), "pr-1": run(410, 5.2, 9.5, -1.5), "main-2": run(405, 5.1, 9, -2), "pr-2": run(400, 5, 9.2, -2) };
  const slowOnce = { ...same, "pr-1": run(700, 5, 9, -2) };
  const slowTwice = { ...same, "pr-1": run(700, 5, 9, -2), "pr-2": run(690, 5, 9, -2) };
  const tinyButLarge = { ...same, "pr-1": run(400, 2.5, 9, -2), "pr-2": run(400, 2.5, 9, -2), "main-1": run(400, 1, 9, -2), "main-2": run(400, 1, 9, -2) };
  const framesSlow = { ...same, "pr-1": run(400, 9, 16, 5), "pr-2": run(400, 9, 15, 4) };
  const checks = [
    ["noise passes", !judge(same).some(r => r.slow || r.missing)],
    ["one slow round passes", !judge(slowOnce).some(r => r.slow)],
    ["slow in both rounds fails", judge(slowTwice).find(r => r.name.startsWith("startup")).slow],
    ["+150% but under +2 ms passes", !judge(tinyButLarge).some(r => r.slow)],
    ["slower frames in both rounds fail", judge(framesSlow).filter(r => r.slow).length === 3],
    ["a missing test is missing", judge({ ...same, "pr-2": { benchmarks: [] } }).every(r => r.missing)],
  ];
  for (const [name, ok] of checks) console.log(`${ok ? "ok  " : "FAIL"} ${name}`);
  if (checks.some(c => !c[1])) process.exit(1);
  console.log("All caught.");
}

function main(dir) {
  const runs = {};
  for (const k of ["main-1", "pr-1", "main-2", "pr-2"]) {
    const p = join(dir, `${k}.json`);
    if (!existsSync(p)) { console.log(`::error::No ${k}.json in ${dir}`); process.exit(2); }
    runs[k] = JSON.parse(readFileSync(p, "utf8"));
  }
  const rows = judge(runs);
  const out = `### Speed: main vs this PR on one emulator (A/B/A/B)\n\n${table(rows)}\n\nTimes in ms. A slowdown fails only when the PR is worse in both rounds by more than both limits.`;
  console.log(out);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, out + "\n");
  if (rows.some(r => r.missing)) { console.log("::error::A benchmark number is missing (see the table)"); process.exit(2); }
  const slow = rows.filter(r => r.slow);
  if (slow.length) { console.log(`::error::Clearly slower than main: ${slow.map(r => r.name).join(", ")}`); process.exit(1); }
  console.log("No clear slowdown.");
}

const arg = process.argv[2];
if (arg === "--self-test") selfTest();
else if (arg) main(arg);
