#!/usr/bin/env node
/* Play next test kit. One command per check every release used to rebuild by hand (see tools/README.md):
     node tools/pn.mjs sweep  [--w=412,859,360] [--plant=file.js|"js"] [--expect] [--big] [--slow=1]
     node tools/pn.mjs tour   [--from=<prev APP_V>] [--w=412,900] [--all] [--plant=...]
     node tools/pn.mjs bench  [--w=859] [--runs=3] [--throttle=4] [--ab=origin/main] [--big]
     node tools/pn.mjs shots  [--w=412] [--screens=home,queue,...]
     node tools/pn.mjs run    my.mjs [--w=412]     (my.mjs: export default async ({ t, lib, browser, base }) => {})
   Exit code 1 when a check fails, so it can gate a push. Detector sweeps run one width at a time and without screenshots
   (a screenshot forces a full repaint that the lag check reports as a stutter; parallel runs starve the CPU). */
import fs from "fs";
import path from "path";
import * as lib from "./lib.mjs";

const [cmd, ...rest] = process.argv.slice(2);
const opt = Object.fromEntries(rest.filter(a => a.startsWith("--")).map(a => { const [k, ...v] = a.slice(2).split("="); return [k, v.length ? v.join("=") : true]; }));
const args = rest.filter(a => !a.startsWith("--"));
const widths = def => String(opt.w || def).split(",").map(Number);
const V = lib.appV();
let failed = 0;
const say = s => console.log(s);

if (!cmd || /^(-h|--help|help)$/.test(cmd)) {
  say(fs.readFileSync(new URL(import.meta.url), "utf8").split("\n").slice(1, 9).join("\n").replace(/^\/\* ?| ?\*\/$/gm, ""));
  process.exit(0);
}

const browser = await lib.chromium({ gpu: !!opt.gpu });
await lib.pictures(browser);
const server = lib.serve({ oldRef: opt.ab || opt.old });
const base = server.base;
const done = async () => { await browser.close(); server.close(); process.exit(failed ? 1 : 0); };

/* code to plant before a sweep: a file path or inline JS, run in the page after it loads (a planted copy of a bug must be reported) */
const plantCode = () => !opt.plant ? "" : fs.existsSync(opt.plant) ? fs.readFileSync(opt.plant, "utf8") : String(opt.plant);

try {
  if (cmd === "sweep") {
    for (const w of widths("412,859,360")) {
      const t = await lib.open(browser, { w, base, state: lib.seed({ base, big: !!opt.big }) });
      if (opt.plant) await lib.run(t, "plant", plantCode(), 600);
      await lib.walk(t, { slow: +(opt.slow || 1) });
      await lib.flush(t);
      const { real, timing, noise } = lib.sortReports(t.reports);
      say(`\n== sweep ${w}px (v${V})${opt.plant ? " with plant" : ""} ==`);
      say(t.errors.length ? "SCRIPT ERRORS:\n  " + [...new Set(t.errors)].join("\n  ") : "No script errors.");
      say(real.length ? `Problem reports (${real.length}):\n  ` + real.map(lib.line).join("\n  ") : "Problem reports: 0");
      if (timing.length) say(`Timing reports (headless has no GPU, only a hint): ${timing.length}\n  ` + timing.map(lib.line).join("\n  "));
      if (noise.length) say(`Harness noise (helper blocked here): ${noise.length}`);
      const bad = opt.expect ? real.length === 0 : real.length > 0 || t.errors.length > 0;
      if (bad) { failed++; say(opt.expect ? "FAIL: the plant was NOT reported" : "FAIL"); } else say(opt.expect ? "OK: the plant was reported" : "OK");
      await t.close();
    }
  }

  else if (cmd === "tour") {
    /* the phone had the previous version: the update runs the tour of every WHATS_NEW entry newer than that */
    const from = +(opt.from || V - 1);
    for (const w of widths("412,900")) {
      const st = lib.seed({ base, v: from, extra: { tourFrom: from } });
      const t = await lib.open(browser, { w, base, state: st, wait: 1500 });
      if (opt.plant) await lib.run(t, "plant", plantCode(), 100);   /* e.g. a WHATS_NEW step pointing at nothing must fail */
      if (opt.all) await lib.run(t, "replay all", `nxTour(null, true)`, 400);
      const plan = await t.page.evaluate(([from, all]) => WHATS_NEW.filter(x => (all || x.v > from) && x.v <= +APP_V).sort((a, b) => a.v - b.v)
        .map(x => ({ v: x.v, at: typeof x.at === "function" ? "fn" : x.at || null, hasGo: !!x.go, hasTry: !!x.tryIt, say: String(x.say || "").replace(/<[^>]+>/g, "").slice(0, 70) })), [from, !!opt.all]);
      say(`\n== tour ${w}px: v${from} → v${V}, ${plan.length} step(s) ==`);
      if (!plan.length) { say("No WHATS_NEW entries in that range" + (opt.all ? "" : " (does this release add one?)")); failed++; await t.close(); continue; }
      const seen = [];
      for (let k = 0; k < 400; k++) {
        const got = await t.page.waitForFunction(prev => { const c = document.querySelector("#nxtour.on"); if (!c) return null;
          const n = (c.querySelector(".row2 i") || {}).textContent || ""; return n && n !== prev ? n : null; }, seen[seen.length - 1]?.n || "", { timeout: 9000 }).then(h => h.jsonValue()).catch(() => null);
        if (!got) { if (!seen.length) { say("FAIL: the tour never appeared"); failed++; } break; }
        await new Promise(r => setTimeout(r, 650));
        const s = await t.page.evaluate(() => {
          const c = document.querySelector("#nxtour"), r = c.getBoundingClientRect(), ring = document.querySelector("#nxring.on"), rr = ring && ring.getBoundingClientRect();
          const b = document.querySelector("#pnbub"), br = b && b.getBoundingClientRect();
          return { n: (c.querySelector(".row2 i") || {}).textContent, head: (c.querySelector("small") || {}).textContent, ring: !!ring,
            cardIn: r.top >= -1 && r.left >= -1 && r.bottom <= innerHeight + 1 && r.right <= innerWidth + 1,
            ringIn: !rr || (rr.bottom > 0 && rr.top < innerHeight), nexi: !!br && br.width > 0 && br.bottom > 0 && br.top < innerHeight && br.right > 0 && br.left < innerWidth,
            text: c.querySelector("p") ? c.querySelector("p").textContent.slice(0, 80) : "" };
        });
        seen.push(s);
        const [i, n] = String(s.n).split("/").map(x => +x.trim());
        const intro = n > plan.length, step = plan[i - 1 - (intro ? 1 : 0)];
        const want = step && step.at;
        const probs = [];
        if (want && !s.ring) probs.push("target not found / not lit");
        if (!s.cardIn) probs.push("card off screen");
        if (!s.ringIn) probs.push("ring off screen");
        if (!s.nexi) probs.push("Nexi not on screen");
        say(`  ${s.n.padEnd(7)} ${step ? "v" + step.v : "intro"} ${probs.length ? "✗ " + probs.join(", ") : "✓"}  ${s.text}`);
        if (probs.length) failed++;
        if (i >= n) { await lib.tapEl(t, "#nxtour [data-nx=next]"); break; }
        if (!(await lib.tapEl(t, "#nxtour [data-nx=next]"))) { say("FAIL: no Next button"); failed++; break; }
      }
      const missing = plan.filter(p => !p.hasGo || !p.hasTry);
      if (!opt.all && missing.length) { say("  entries missing go/tryIt: " + missing.map(p => "v" + p.v).join(", ")); failed++; }
      await new Promise(r => setTimeout(r, 1500));
      await lib.flush(t);
      const tourReps = t.reports.filter(r => r.kind === "tour");
      if (tourReps.length) { say("  the app reported:\n    " + tourReps.map(lib.line).join("\n    ")); failed++; }
      if (t.errors.length) { say("  SCRIPT ERRORS:\n    " + [...new Set(t.errors)].join("\n    ")); failed++; }
      const after = await t.page.evaluate(() => ({ from: S.tourFrom, card: !!document.querySelector("#nxtour") }));
      if (after.from !== V) { say(`  FAIL: S.tourFrom is ${after.from} after the tour (want ${V})`); failed++; }
      await t.close();
    }
    say(failed ? "\nFAIL" : "\nOK");
  }

  else if (cmd === "bench") {
    /* A/B against another build (default: none). 4x CPU like the owner's slower moments; medians of --runs */
    const runs = +(opt.runs || 3), throttle = +(opt.throttle || 4), builds = opt.ab ? [["old", true], ["new", false]] : [["new", false]];
    for (const w of widths("859")) {
      const res = {};
      for (let r = 0; r < runs; r++) for (const [name, old] of builds) {
        const t = await lib.open(browser, { w, base, old, throttle, state: lib.seed({ base, big: !!opt.big }), wait: 4000 });
        await lib.frames(t);
        const acts = [
          ...["games", "plan", "taste", "add", "home"].map(s => [`tab ${s}`, () => lib.run(t, s, `goTab("${s}")`, 0)]),
          ["open game", () => lib.run(t, "open", `openFull("g1")`, 0)],
          ["scroll game", () => lib.scroll(t, 700)],
          ["close game", () => lib.run(t, "close", `closeFull()`, 0)],
          ["settings", () => lib.run(t, "gear", `document.querySelector("#gear").click()`, 0)],
          ["close settings", () => lib.run(t, "close", `closeEdit()`, 0)],
          ["queue scroll", async () => { await lib.run(t, "q", `goTab("queue")`, 900); await lib.scroll(t, 800); }],
        ];
        for (const [label, fn] of acts) (res[label] ||= {})[name] = [...((res[label] || {})[name] || []), await lib.measure(t, label, fn)];
        await t.close();
      }
      say(`\n== bench ${w}px, ${throttle}x CPU, median of ${runs} (main = main-thread ms, worst = longest frame ms, >33 = frames over 33ms) ==`);
      const cols = builds.map(b => b[0]);
      say("action".padEnd(16) + cols.map(c => `${c}: main worst >33`.padEnd(26)).join(""));
      for (const [label, by] of Object.entries(res)) {
        say(label.padEnd(16) + cols.map(c => { const a = by[c] || []; return `${lib.median(a.map(x => x.main))}`.padStart(9) + `${lib.median(a.map(x => x.worst))}`.padStart(6) + `${lib.median(a.map(x => x.over33))}`.padStart(5) + " ".repeat(6); }).join(""));
      }
      if (opt.ab) say("(noise between runs is often ±10-20%; trust repeated differences, not one run)");
    }
  }

  else if (cmd === "shots") {
    for (const w of widths("412")) {
      const t = await lib.open(browser, { w, base, state: lib.seed({ base, big: !!opt.big }) });
      await lib.walk(t, { shots: true, tag: String(w), screens: opt.screens ? String(opt.screens).split(",") : lib.SCREENS, slow: 0.6 });
      await t.close();
    }
    say("Screenshots in " + path.relative(process.cwd(), lib.OUT));
  }

  else if (cmd === "run") {
    if (!args[0]) throw new Error("usage: node tools/pn.mjs run <scenario.mjs> [--w=412]");
    const mod = await import(path.resolve(args[0]));
    for (const w of widths("412")) {
      const t = await lib.open(browser, { w, base, state: lib.seed({ base, big: !!opt.big }) });
      const r = await mod.default({ t, lib, browser, base, w, opt });
      if (r === false) failed++;
      await t.close();
    }
  }

  else { say("Unknown command: " + cmd); failed++; }
} catch (e) { console.error(e); failed++; }
await done();
