/* Every plant in tools/plants/ through the sweep (M5: a plain sweep can't tell a detector that still works from one that went
   blind). Each plant's header comment holds what it needs:
     sweep: --widths 412 --screens queue        kit.mjs sweep options that keep it short (one width, the screens it lives on)
     expect: ^\[look\] .*A label says undefined  one line per report it must cause, a regex (case-insensitive) on the report
                                                 line "[kind] screen: msg @ at"; every one must match, so an unrelated report
                                                 can't pass for the plant
   and this runs `node tools/kit.mjs sweep --plant <it> --expect <sweep options> --match <each expect>` one at a time
   (parallel phones starve the CPU and the lag checks file reports).
     node tools/plants/run.mjs                  every *.js plant (app-check runs this on every PR)
     node tools/plants/run.mjs queue-bar        only these (names without .js)
     --full                                     ignore the sweep: lines (every width and screen; a few minutes per plant)
     --file old.html                            passed on to kit.mjs (does an old build's detector see it?)
   A new detector gets its plant here, in the same shape, proven with `--expect` (CLAUDE.md "Problem reports"). eye-broken.css
   is the design eye's plant (tools/scenarios/design-eye.mjs, its own app-check step). Exit 1 when a plant isn't reported. */
import fs from "fs";
import path from "path";
import { spawnSync } from "child_process";
import { fileURLToPath } from "url";

const DIR = path.dirname(fileURLToPath(import.meta.url)), ROOT = path.resolve(DIR, "../..");
const args = process.argv.slice(2), full = args.includes("--full");
const pass = args.flatMap((a, i) => a === "--file" ? [a, args[i + 1]] : []);
const pick = args.filter((a, i) => !a.startsWith("--") && args[i - 1] !== "--file").map(a => path.basename(a).replace(/\.js$/, ""));
const plants = fs.readdirSync(DIR).filter(f => f.endsWith(".js") && (!pick.length || pick.includes(f.replace(/\.js$/, "")))).sort();
if (!plants.length) { console.log(`No plants${pick.length ? " named " + pick.join(", ") : ""} in tools/plants/.`); process.exit(1); }

const secs = ms => (ms / 1000).toFixed(0) + "s", rows = [], t0 = Date.now();
for (const f of plants) {
  const head = (fs.readFileSync(path.join(DIR, f), "utf8").match(/^\s*\/\*[\s\S]*?\*\//) || [""])[0];
  const line = k => [...head.matchAll(new RegExp(`^\\s*${k}:\\s*(.+?)\\s*(?:\\*\\/)?$`, "gm"))].map(m => m[1]);
  const sweep = full ? [] : (line("sweep")[0] || "").split(/\s+/).filter(Boolean), expect = line("expect");
  const bad = expect.filter(e => { try { new RegExp(e); return false; } catch (x) { return true; } });
  const t = Date.now();
  if (bad.length) { rows.push({ f, ok: false, ms: 0, why: "a broken expect: line: " + bad.join(", ") }); continue; }
  console.log(`\n### ${f}${sweep.length ? " (" + sweep.join(" ") + ")" : " (every width and screen)"}`);
  const r = spawnSync(process.execPath, [path.join(ROOT, "tools/kit.mjs"), "sweep", "--plant", path.join(DIR, f), "--expect", ...sweep, ...pass,
    ...expect.flatMap(e => ["--match", e])], { cwd: ROOT, encoding: "utf8", env: process.env, maxBuffer: 1 << 24 });
  const out = (r.stdout + r.stderr).trim().split("\n").filter(l => l && !/^(All clear\.|\d+ problem\(s\)\.)$/.test(l));
  console.log(out.join("\n"));
  rows.push({ f, ok: r.status === 0, ms: Date.now() - t, why: r.status === 0 ? `${expect.length || "any"} expected report${expect.length === 1 ? "" : "s"} seen`
    : (out.filter(l => /✗/.test(l)).map(l => l.replace(/^\s*✗\s*/, "")).join("; ").replace(/;? reported instead:$/, "").slice(0, 300) || `kit.mjs exited ${r.status}`) });
  if (!expect.length) console.log("  · no expect: lines, so any report counts: add the reports it must cause");
}

const ok = rows.filter(r => r.ok).length;
console.log(`\n== plants: ${ok} of ${rows.length} reported (${secs(Date.now() - t0)}) ==`);
for (const r of rows) console.log(`  ${r.ok ? "✓" : "✗"} ${r.f.padEnd(18)} ${secs(r.ms).padStart(4)}  ${r.why}`);
if (ok < rows.length) console.log("\nA detector no longer sees its plant: fix the detector (or, if the screen it lives on changed on purpose, the plant).");
process.exit(ok === rows.length ? 0 : 1);
