/* Why a scroll or tab switch hitches (#134 Month, #136 Insights, #416/#333 switching to Library/Queue): a Chromium trace of
   each long task split into script (function:line) / style / layout / paint, plus the webperf-interaction skill's
   Long-Animation-Frame and Forced-Synchronous-Layout snippets (.claude/skills/webperf-interaction/scripts) running in the
   page. Real touch swipes on the screen's own scroller (the snippets' scroll meter only hears window scroll), 60 games,
   CPU slowed 4x (CPU=1 for none). Run it in the owner's engine when you can (Edge/Chrome 155 on the PC):
     CHROME="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" node tools/kit.mjs run tools/scenarios/trace-stutter.mjs --widths 859
     ONLY=month,switch-lib node tools/kit.mjs run tools/scenarios/trace-stutter.mjs --file old.html */
import fs from "node:fs";
import path from "node:path";
const SNIP = path.resolve(".claude/skills/webperf-interaction/scripts");
const snip = f => { try { return fs.readFileSync(path.join(SNIP, f), "utf8"); } catch (e) { return ""; } };
const KIND = n => /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks|FireIdleCallback|v8\.callFunction)$/.test(n) ? "script"
  : /^(UpdateLayoutTree|RecalculateStyles|ParseAuthorStyleSheet)$/.test(n) ? "style" : n === "Layout" ? "layout"
  : /^(PrePaint|Paint|PaintImage|UpdateLayerTree|Layerize|Commit)$/.test(n) ? "paint" : /^(Decode Image|ImageDecodeTask)$/.test(n) ? "decode" : "";
export default async ({ p, w, seed }) => {
  const html = fs.readFileSync(path.resolve(process.argv.includes("--file") ? process.argv[process.argv.indexOf("--file") + 1] : "index.html"), "utf8");
  const lineAt = c => c == null || c < 0 ? "?" : html.slice(0, c).split("\n").length;
  /* 60 games: the owner's library, not the sweep's 14 */
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.page.evaluate(snip("Long-Animation-Frames.js") + "\n;" + snip("Forced-Synchronous-Layout.js")).catch(e => console.log("  snippets:", e.message));
  /* our own LoAF list: every frame from 30ms, with the script / style+layout / render split M11 wants in field reports */
  await p.page.evaluate(() => { window.__lo = []; new PerformanceObserver(l => window.__lo.push(...l.getEntries().map(f => ({ t: f.startTime, d: f.duration,
    render: f.renderStart ? f.startTime + f.duration - f.renderStart : 0, sl: f.styleAndLayoutStart ? f.startTime + f.duration - f.styleAndLayoutStart : 0,
    scripts: (f.scripts || []).map(s => ({ d: s.duration, fsl: s.forcedStyleAndLayoutDuration, fn: s.sourceFunctionName, inv: s.invoker, c: s.sourceCharPosition })) })))).observe({ type: "long-animation-frame" }); });
  const cpu = +(process.env.CPU || 4);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  const scrollDown = async () => { for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 200, 12); await p.page.waitForTimeout(450); }
    for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.3, p.w * 0.52, p.h * 0.85, 200, 12); await p.page.waitForTimeout(450); } };
  const tapNav = async tab => { const r = await p.page.evaluate(t => { const b = document.querySelector(`nav button[data-tab="${t}"]`); if (!b) return null; const q = b.getBoundingClientRect(); return [q.x + q.width / 2, q.y + q.height / 2]; }, tab);
    if (r) await p.tap(r[0], r[1]); else await p.page.evaluate(t => goTab(t), tab); await p.page.waitForTimeout(1500); };
  const PHASES = {
    month: { prep: `goTab("month")`, act: scrollDown },
    insights: { prep: `goTab("taste"); goSub("taste", "insights")`, act: scrollDown },
    psych: { prep: `goTab("taste"); goSub("taste", "psych")`, act: scrollDown },
    "switch-lib": { prep: `goTab("home")`, act: async () => { await p.page.evaluate(() => setSub("games", "lib")); await tapNav("games"); } },
    "switch-queue": { prep: `goTab("month")`, act: async () => { await p.page.evaluate(() => setSub("games", "queue")); await tapNav("games"); } },
    "pill-lib": { prep: `goTab("queue")`, act: async () => { const r = await p.page.evaluate(() => { const b = document.querySelector('#vpill button[data-view="lib"]'); if (!b) return null; const q = b.getBoundingClientRect(); return [q.x + q.width / 2, q.y + q.height / 2]; });
      if (r) await p.tap(r[0], r[1]); else await p.page.evaluate(() => goSub("games", "lib")); await p.page.waitForTimeout(1500); } },
  };
  const only = (process.env.ONLY || Object.keys(PHASES).join(",")).split(",");
  for (const name of only) {
    const ph = PHASES[name]; if (!ph) continue;
    await p.step(name, ph.prep, 2600);
    await p.page.evaluate(() => { window.__lo.length = 0; if (window.getFSLSummary) { window.stopFSLDetector(); } });
    await p.page.evaluate(snip("Forced-Synchronous-Layout.js")).catch(() => {});
    /* the snippet keeps its stacks to itself: count layout reads by the app function that made them */
    await p.page.evaluate(() => { window.__rd = {}; if (window.__rdOff) window.__rdOff();
      const P = Element.prototype, g = P.getBoundingClientRect;
      P.getBoundingClientRect = function () { const st = (new Error().stack || "").split("\n").slice(1, 5).filter(l => !/getBoundingClientRect|<anonymous>|pptr:|__puppeteer/.test(l)).slice(0, 3)
          .map(l => { const m = l.match(/at (?:(\S+) \()?.*:(\d+):\d+\)?$/); return m ? `${m[1] || "(anon)"}:${m[2]}` : l.trim(); }).join(" < ");
        window.__rd[st] = (window.__rd[st] || 0) + 1; return g.call(this); };
      window.__rdOff = () => { P.getBoundingClientRect = g; }; });
    const events = [], on = e => events.push(...e.value);
    p.cdp.on("Tracing.dataCollected", on);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "toplevel", "v8.execute", "blink.user_timing"] }, transferMode: "ReportEvents" });
    await ph.act();
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    /* long tasks on the main thread: what ran inside each */
    const main = (() => { const c = {}; for (const e of events) if (e.name === "UpdateLayoutTree" || e.name === "Layout") c[e.tid] = (c[e.tid] || 0) + 1; return +Object.entries(c).sort((a, b) => b[1] - a[1])[0]?.[0]; })();
    const evs = events.filter(e => e.tid === main && e.ph === "X" && e.dur).sort((a, b) => a.ts - b.ts);
    const tasks = evs.filter(e => e.name === "RunTask" && e.dur >= 50000 / (4 / cpu));
    const tot = {}; for (const e of evs) { const k = KIND(e.name); if (k) tot[k] = (tot[k] || 0) + e.dur / 1000; }
    console.log(`\n  [${name}] ${w}px, CPU ${cpu}x: main thread ${Object.entries(tot).map(([k, v]) => `${k} ${Math.round(v)}`).join(", ")} ms; ${tasks.length} task(s) ≥${Math.round(50 / (4 / cpu))}ms`);
    /* the whole phase: which functions (outermost calls) and which forced style/layout cost the most, all frames together */
    const all = {}, fcs = evs.filter(e => e.name === "FunctionCall");
    for (const e of evs) {
      if (e.name === "FunctionCall" && !fcs.some(o => o !== e && o.ts <= e.ts && o.ts + o.dur >= e.ts + e.dur && o.dur > e.dur)) {
        const d = (e.args && e.args.data) || {}; const key = `${d.functionName || "(anon)"}:${d.lineNumber != null ? d.lineNumber + 1 : "?"}`; all[key] = (all[key] || 0) + e.dur / 1000; }
      if ((e.name === "Layout" || e.name === "UpdateLayoutTree") && e.args && e.args.beginData && e.args.beginData.stackTrace && e.args.beginData.stackTrace[0]) {
        const st = e.args.beginData.stackTrace[0]; const key = `forced ${e.name === "Layout" ? "layout" : "style"} by ${st.functionName || "(anon)"}:${st.lineNumber + 1}`; all[key] = (all[key] || 0) + e.dur / 1000; }
    }
    if (process.env.STACKS) { const seen = new Set(); for (const e of evs) { const st = (e.name === "Layout" || e.name === "UpdateLayoutTree") && e.args && e.args.beginData && e.args.beginData.stackTrace; if (!st) continue; const k = st.slice(0, 4).map(f => (f.functionName || "(anon)") + ":" + (f.lineNumber + 1) + ":" + (f.columnNumber + 1)).join(" < "); if (seen.has(k) || !new RegExp(process.env.STACKS).test(k)) continue; seen.add(k); console.log("      stack: " + e.name + " " + Math.round(e.dur / 1000) + "ms " + k); } }
    console.log("    whole phase, top: " + Object.entries(all).sort((a, b) => b[1] - a[1]).slice(0, 10).map(([f, v]) => `${f} ${Math.round(v)}`).join(" · "));
    for (const t of tasks.sort((a, b) => b.dur - a.dur).slice(0, 6)) {
      const inside = evs.filter(e => e.ts >= t.ts && e.ts + e.dur <= t.ts + t.dur && e !== t);
      const k = {}, fns = {};
      /* self time per kind: a FunctionCall that forces a Layout counts its layout as layout */
      for (const e of inside) { const kk = KIND(e.name); if (!kk) continue;
        const kids = inside.filter(c => c !== e && c.ts >= e.ts && c.ts + c.dur <= e.ts + e.dur && KIND(c.name) && KIND(c.name) !== kk);
        const top = kids.filter(c => !kids.some(o => o !== c && o.ts <= c.ts && o.ts + o.dur >= c.ts + c.dur && o.dur > c.dur));
        const self = (e.dur - top.reduce((a, c) => a + c.dur, 0)) / 1000;
        const nested = inside.some(o => o !== e && KIND(o.name) === kk && o.ts <= e.ts && o.ts + o.dur >= e.ts + e.dur && o.dur > e.dur);
        if (!nested) k[kk] = (k[kk] || 0) + self;
        if (e.name === "FunctionCall") { const d = (e.args && e.args.data) || {}; const key = `${d.functionName || "(anon)"}:${d.lineNumber != null ? d.lineNumber + 1 : "?"}`; if (!nested || true) fns[key] = Math.max(fns[key] || 0, e.dur / 1000); }
        if (e.name === "Layout" || e.name === "UpdateLayoutTree") { const st = e.args && e.args.beginData && e.args.beginData.stackTrace; if (st && st[0]) { const key = `forced ${e.name === "Layout" ? "layout" : "style"} by ${st[0].functionName || "(anon)"}:${st[0].lineNumber + 1}`; fns[key] = (fns[key] || 0) + e.dur / 1000; } }
      }
      console.log(`    task ${Math.round(t.dur / 1000)}ms at +${Math.round((t.ts - evs[0].ts) / 1000)}ms: ` + Object.entries(k).filter(([, v]) => v >= 1).map(([kk, v]) => `${kk} ${Math.round(v)}`).join(", "));
      const top = Object.entries(fns).sort((a, b) => b[1] - a[1]).slice(0, 6);
      if (top.length) console.log("      " + top.map(([f, v]) => `${f} ${Math.round(v)}`).join(" · "));
    }
    const lo = await p.page.evaluate(() => window.__lo.slice());
    const big = lo.filter(f => f.d >= 50 * cpu / 4).sort((a, b) => b.d - a.d).slice(0, 5);
    console.log(`    LoAF: ${lo.length} frame(s) ≥50ms` + (big.length ? "; biggest:" : ""));
    for (const f of big) { const s = f.scripts.slice().sort((a, b) => b.d - a.d).slice(0, 3);
      console.log(`      ${Math.round(f.d)}ms = script ${Math.round(f.scripts.reduce((a, x) => a + x.d, 0))} (forced style/layout ${Math.round(f.scripts.reduce((a, x) => a + (x.fsl || 0), 0))}), style+layout ${Math.round(f.sl)}, render ${Math.round(f.render)}` + (s.length ? " | " + s.map(x => `${x.fn || "(anon)"}@L${lineAt(x.c)} ${Math.round(x.d)}ms ← ${String(x.inv || "").replace(/https?:\/\/[^\s]+\//g, "").slice(0, 50)}`).join("; ") : "")); }
    const fsl = await p.page.evaluate(() => { if (!window.getFSLSummary) return null; const s = window.getFSLSummary(); return s; }).catch(() => null);
    if (fsl && fsl.count) {
      console.log(`    forced sync layout reads: ${fsl.count} — ` + fsl.items.slice(0, 5).map(i => `${i.property} on ${i.element.slice(0, 40)} ×${i.count} (${i.fastestSinceLastMutationMs}ms after a write)`).join("; "));
      const rd = await p.page.evaluate(() => Object.entries(window.__rd).sort((a, b) => b[1] - a[1]).slice(0, 5));
      console.log("      getBoundingClientRect by caller: " + rd.map(([k, n]) => k + " ×" + n).join(" | "));
    } else console.log("    forced sync layout reads: 0");
  }
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  return true;
};
