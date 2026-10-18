/* Insights, the FIRST scroll after opening it (cold: no raster/decode caches): which main-thread tasks run while the finger
   moves, and which run after it stops. Marks "swipe-start"/"swipe-end" in the trace; no webperf snippets installed (they
   add their own cost). Lists every main-thread task >=40ms (as measured, CPU 4x) with what ran in it, and the busiest
   raster / decode / GPU / compositor tasks. Run it at 859 and 412.
     CHROME="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" node tools/kit.mjs run tools/scenarios/measure-insights-cold.mjs --widths 859,412 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const cpu = +(process.env.CPU || 4);
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 3500);
  await p.page.evaluate(() => { window.__g = []; let last = 0; const f = t => { if (last) window.__g.push([Math.round(t), Math.round(t - last)]); last = t; window.__raf = requestAnimationFrame(f); }; window.__raf = requestAnimationFrame(f); });
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  const events = [], on = e => events.push(...e.value); p.cdp.on("Tracing.dataCollected", on);
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "toplevel", "v8.execute", "cc", "blink", "blink.user_timing", "gpu"] }, transferMode: "ReportEvents" });
  await p.page.evaluate(() => performance.mark("swipe-start"));
  for (let k = 0; k < 5; k++) { await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .25, 200, 12); await p.page.waitForTimeout(350); }
  await p.page.evaluate(() => performance.mark("swipe-end"));
  await p.page.waitForTimeout(2500);   /* what happens after the finger stops */
  const done = new Promise(r => p.cdp.once("Tracing.tracingComplete", r)); await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const gaps = await p.page.evaluate(() => { cancelAnimationFrame(window.__raf); return window.__g; });
  const mk = n => (events.find(e => e.name === n && e.cat && /user_timing/.test(e.cat)) || {}).ts;
  const t0 = mk("swipe-start"), t1 = mk("swipe-end");
  const main = (() => { const c = {}; for (const e of events) if (e.name === "UpdateLayoutTree" || e.name === "Layout") c[e.tid] = (c[e.tid] || 0) + 1; return +Object.entries(c).sort((a, b) => b[1] - a[1])[0]?.[0]; })();
  const evs = events.filter(e => e.ph === "X" && e.dur).sort((a, b) => a.ts - b.ts), me = evs.filter(e => e.tid === main);
  const rel = ts => Math.round((ts - t0) / 1000);
  console.log(`\n  [cold insights ${w}px, CPU ${cpu}x] swipe window ${rel(t1)}ms; rAF gaps >40ms: ${gaps.filter(g => g[1] > 40).length} (worst ${Math.max(0, ...gaps.map(g => g[1]))}ms)`);
  const kind = n => /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks|v8\.callFunction)$/.test(n) ? "script" : /^(UpdateLayoutTree|RecalculateStyles)$/.test(n) ? "style" : n === "Layout" ? "layout" : /^(PrePaint|Paint|PaintImage|UpdateLayerTree|Layerize|Commit)$/.test(n) ? "paint/commit" : /^(Decode Image|ImageDecodeTask|Decode LazyPixelRef)$/.test(n) ? "decode" : /^(RasterTask|GpuRasterTask)$/.test(n) ? "raster" : "";
  const tasks = me.filter(e => e.name === "RunTask" && e.dur >= 40000).sort((a, b) => b.dur - a.dur).slice(0, 14);
  for (const t of tasks.sort((a, b) => a.ts - b.ts)) {
    const inside = me.filter(e => e.ts >= t.ts && e.ts + e.dur <= t.ts + t.dur && e !== t), k = {}, fns = {};
    for (const e of inside) { const kk = kind(e.name); if (!kk) continue; const nested = inside.some(o => o !== e && kind(o.name) === kk && o.ts <= e.ts && o.ts + o.dur >= e.ts + e.dur && o.dur > e.dur); if (!nested) k[kk] = (k[kk] || 0) + e.dur / 1000;
      if (e.name === "FunctionCall") { const d = (e.args && e.args.data) || {}; const key = `${d.functionName || "(anon)"}:${d.lineNumber != null ? d.lineNumber + 1 : "?"}`; fns[key] = Math.max(fns[key] || 0, e.dur / 1000); } }
    const when = t.ts < t0 ? "before" : t.ts > t1 ? "AFTER swipe" : "DURING swipe";
    console.log(`    ${String(Math.round(t.dur / 1000)).padStart(5)}ms +${String(rel(t.ts)).padStart(6)}ms ${when.padEnd(12)} ` + Object.entries(k).map(([a, b]) => `${a} ${Math.round(b)}`).join(", ") + " | " + Object.entries(fns).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([f, x]) => `${f} ${Math.round(x)}`).join(" · "));
  }
  const byThread = {}; for (const e of evs) { if (e.tid === main) continue; const kk = kind(e.name); if (!kk || e.ts < t0) continue; (byThread[kk] ||= []).push(e); }
  for (const [kk, list] of Object.entries(byThread)) { const tot = list.reduce((a, e) => a + e.dur / 1000, 0); const big = list.sort((a, b) => b.dur - a.dur).slice(0, 3).map(e => `${Math.round(e.dur / 1000)}ms@+${rel(e.ts)}`);
    console.log(`    other threads ${kk}: ${list.length} tasks, ${Math.round(tot)}ms; biggest ${big.join(", ")}`); }
  console.log("    rAF gaps >40ms: " + gaps.filter(g => g[1] > 40).slice(0, 12).map(g => `${g[1]}ms`).join(" "));
  return true;
};
