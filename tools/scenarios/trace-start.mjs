/* Why opening the app freezes (#334: "Opening the app froze for 300ms", 3-4 long frames in the first 8s, ~1.1-1.3s in all,
   mostly "rendering (style/layout/paint)"). A Chromium trace of a cold reload with 60 games and CPU 4x (CPU=1 for none):
   every long task split into script / style / layout / paint, the functions and forced layouts behind them, and the
   app's own boot list of long-animation-frames (what the phone reports).
     node tools/kit.mjs run tools/scenarios/trace-start.mjs --widths 859,412
     node tools/kit.mjs run tools/scenarios/trace-start.mjs --widths 859 --file old.html */
import fs from "node:fs";
import path from "node:path";
const KIND = n => /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks|FireIdleCallback|v8\.callFunction|v8\.compile|CompileScript|ParseHTML)$/.test(n) ? "script"
  : /^(UpdateLayoutTree|RecalculateStyles|ParseAuthorStyleSheet)$/.test(n) ? "style" : n === "Layout" ? "layout"
  : /^(PrePaint|Paint|PaintImage|UpdateLayerTree|Layerize|Commit)$/.test(n) ? "paint" : /^(Decode Image|ImageDecodeTask)$/.test(n) ? "decode" : "";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => { localStorage.setItem("gq:data", d); }, JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload(); await p.page.waitForTimeout(2500);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } if (typeof saveNow === "function") saveNow(); });
  const cpu = +(process.env.CPU || 4), runs = +(process.env.RUNS || 2);
  for (let r = 0; r < runs; r++) {
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
    const events = [], on = e => events.push(...e.value);
    p.cdp.on("Tracing.dataCollected", on);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: process.env.ALLCAT ? ["*", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "disabled-by-default-v8.gc"] : ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "toplevel", "v8.execute", "blink.user_timing"] }, transferMode: "ReportEvents" });
    await p.page.reload({ waitUntil: "load" });
    await p.page.waitForTimeout(8500);
    const boot = await p.page.evaluate(() => performance.getEntriesByType("long-animation-frame").filter(f => f.startTime < 8000).map(f => ({ t: Math.round(f.startTime), d: Math.round(f.duration),
      sl: f.styleAndLayoutStart ? Math.round(f.startTime + f.duration - f.styleAndLayoutStart) : 0, render: f.renderStart ? Math.round(f.startTime + f.duration - f.renderStart) : 0,
      s: [...(f.scripts || [])].sort((a, b) => b.duration - a.duration).slice(0, 3).map(s => `${s.sourceFunctionName || "(anon)"}${s.sourceCharPosition >= 0 ? "" : ""} ${Math.round(s.duration)}ms ← ${String(s.invoker || s.invokerType || "").replace(/https?:\/\/[^\s]+\//g, "").slice(0, 40)}`) })));
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
    const main = (() => { const c = {}; for (const e of events) if (e.name === "UpdateLayoutTree" || e.name === "Layout") c[e.tid] = (c[e.tid] || 0) + 1; return +Object.entries(c).sort((a, b) => b[1] - a[1])[0]?.[0]; })();
    const evs = events.filter(e => e.tid === main && e.ph === "X" && e.dur).sort((a, b) => a.ts - b.ts);
    const t0 = (evs.find(e => e.name === "ParseHTML") || evs[0]).ts;
    const tasks = evs.filter(e => e.name === "RunTask" && e.dur >= 50000 * cpu / 4);
    const tot = {}; for (const e of evs) { const k = KIND(e.name); if (k) tot[k] = (tot[k] || 0) + e.dur / 1000; }
    console.log(`\n  [start ${r + 1}] ${w}px, CPU ${cpu}x: ${tasks.length} task(s) ≥${50 * cpu / 4}ms; main thread ` + Object.entries(tot).map(([k, x]) => `${k} ${Math.round(x)}`).join(", ") + " ms");
    console.log("    app's boot LoAF (≥50ms): " + boot.filter(f => f.d >= 50).map(f => `${f.d}ms@${f.t} (s+l ${f.sl}, render ${f.render})`).join(" · "));
    for (const f of boot.filter(f => f.d >= 100).sort((a, b) => b.d - a.d).slice(0, 4)) console.log(`      ${f.d}ms@${f.t}: ${f.s.join("; ")}`);
    for (const t of tasks.sort((a, b) => b.dur - a.dur).slice(0, 8)) {
      const inside = evs.filter(e => e.ts >= t.ts && e.ts + e.dur <= t.ts + t.dur && e !== t);
      const k = {}, fns = {};
      for (const e of inside) { const kk = KIND(e.name); if (!kk) continue;
        const kids = inside.filter(c => c !== e && c.ts >= e.ts && c.ts + c.dur <= e.ts + e.dur && KIND(c.name) && KIND(c.name) !== kk);
        const top = kids.filter(c => !kids.some(o => o !== c && o.ts <= c.ts && o.ts + o.dur >= c.ts + c.dur && o.dur > c.dur));
        const self = (e.dur - top.reduce((a, c) => a + c.dur, 0)) / 1000;
        const nested = inside.some(o => o !== e && KIND(o.name) === kk && o.ts <= e.ts && o.ts + o.dur >= e.ts + e.dur && o.dur > e.dur);
        if (!nested) k[kk] = (k[kk] || 0) + self;
        if (e.name === "FunctionCall" || e.name === "TimerFire" || e.name === "FireAnimationFrame") { const d = (e.args && e.args.data) || {}; const key = `${e.name === "FunctionCall" ? "" : e.name + " "}${d.functionName || "(anon)"}:${d.lineNumber != null ? d.lineNumber + 1 : "?"}`; fns[key] = Math.max(fns[key] || 0, e.dur / 1000); }
        if (e.name === "Layout" || e.name === "UpdateLayoutTree") { const st = e.args && e.args.beginData && e.args.beginData.stackTrace; const key = st && st[0] ? `forced ${e.name === "Layout" ? "layout" : "style"} by ${st[0].functionName || "(anon)"}:${st[0].lineNumber + 1}` : `${e.name === "Layout" ? "layout" : "style"} (frame)`; fns[key] = (fns[key] || 0) + e.dur / 1000; }
      }
      console.log(`    task ${Math.round(t.dur / 1000)}ms at +${Math.round((t.ts - t0) / 1000)}ms: ` + Object.entries(k).filter(([, x]) => x >= 1).map(([kk, x]) => `${kk} ${Math.round(x)}`).join(", "));
      if (process.env.NAMES && t.dur > 400000) { const nm = {}; for (const e of events) if (e.tid === main && e.ts >= t.ts && e.ts <= t.ts + t.dur && e.ph !== "X") nm[e.name + "/" + e.ph] = (nm[e.name + "/" + e.ph] || 0) + 1; console.log("      other events: " + Object.entries(nm).slice(0, 30).map(([n, x]) => `${n} ×${x}`).join(" · ")); }
      if (process.env.NAMES) { const nm = {}; for (const e of inside) nm[e.name] = (nm[e.name] || 0) + e.dur / 1000; console.log("      events: " + Object.entries(nm).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([n, x]) => `${n} ${Math.round(x)}`).join(" · ")); }
      console.log("      " + Object.entries(fns).sort((a, b) => b[1] - a[1]).slice(0, 7).map(([f, x]) => `${f} ${Math.round(x)}`).join(" · "));
    }
  }
  return true;
};
