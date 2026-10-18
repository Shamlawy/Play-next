/* What happens in the first 8s after opening the app (#334: 4 long frames / 1379ms), with the picture decodes named.
   A Chromium trace of a cold reload (60 seeded games, key art = 12 MP): only events from the new page's main thread, from
   its navigationStart to +8s. Prints, per long task (>= 50ms scaled): at what ms, kind split (script / style / layout /
   paint / decode), the functions and forced layouts behind it, the images painted/decoded inside it (url, source size vs
   drawn size), and the app's own long-animation-frames; then every picture painted in the 8s with its decode count.
     CPU=1 node tools/kit.mjs run tools/scenarios/measure-start.mjs --widths 859,412   (CPU=4 default, RUNS=1)
     ALLCAT=1 NAMES=1 add every trace category / the event names inside each long task (lesson L51). */
const KIND = n => /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks|FireIdleCallback|v8\.callFunction|v8\.compile|CompileScript|ParseHTML)$/.test(n) ? "script"
  : /^(UpdateLayoutTree|RecalculateStyles|ParseAuthorStyleSheet)$/.test(n) ? "style" : n === "Layout" ? "layout"
  : /^(PrePaint|Paint|PaintImage|UpdateLayerTree|Layerize|Commit)$/.test(n) ? "paint" : /^(Decode Image|ImageDecodeTask|Decode LazyPixelRef|ImageUploadTask|GPUTask)$/.test(n) ? "decode" : "";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => { localStorage.setItem("gq:data", d); }, JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload(); await p.page.waitForTimeout(2500);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } if (typeof saveNow === "function") saveNow(); });
  const cpu = +(process.env.CPU || 4), runs = +(process.env.RUNS || 1), thr = 50 * cpu / 4;
  for (let r = 0; r < runs; r++) {
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
    const events = [], on = e => events.push(...e.value);
    p.cdp.on("Tracing.dataCollected", on);
    const cats = process.env.ALLCAT ? ["*", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack"]
      : ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "disabled-by-default-devtools.timeline.frame", "cc", "toplevel", "v8.execute", "blink.user_timing", "loading"].concat(process.env.INV ? ["disabled-by-default-devtools.timeline.invalidationTracking"] : []);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: cats }, transferMode: "ReportEvents" });
    await p.page.reload({ waitUntil: "commit" });
    await p.page.waitForTimeout(8500 * (cpu > 1 ? 1 : 1));
    const boot = await p.page.evaluate(() => performance.getEntriesByType("long-animation-frame").filter(f => f.startTime < 8000).map(f => ({ t: Math.round(f.startTime), d: Math.round(f.duration),
      sl: f.styleAndLayoutStart ? Math.round(f.startTime + f.duration - f.styleAndLayoutStart) : 0, render: f.renderStart ? Math.round(f.startTime + f.duration - f.renderStart) : 0,
      s: [...(f.scripts || [])].sort((a, b) => b.duration - a.duration).slice(0, 3).map(s => `${s.sourceFunctionName || "(anon)"} ${Math.round(s.duration)}ms ← ${String(s.invoker || s.invokerType || "").replace(/https?:\/\/[^\s]+\//g, "").slice(0, 40)}`) })));
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
    /* the new page's main thread = whoever fired the last navigationStart of the main frame */
    const navs = events.filter(e => e.name === "navigationStart").sort((a, b) => a.ts - b.ts), nav = navs[navs.length - 1];
    if (!nav) { console.log("  no navigationStart in the trace"); continue; }
    const t0 = nav.ts, end = t0 + 8e6, main = { pid: nav.pid, tid: nav.tid };
    const mine = e => e.pid === main.pid && e.tid === main.tid && e.ts >= t0 && e.ts < end;
    const evs = events.filter(e => mine(e) && e.ph === "X" && e.dur).sort((a, b) => a.ts - b.ts);
    const tasks = evs.filter(e => e.name === "RunTask" && e.dur >= thr * 1000);
    const tot = {}; for (const e of evs) { const k = KIND(e.name); if (k) tot[k] = (tot[k] || 0) + e.dur / 1000; }
    console.log(`\n  [start ${r + 1}] ${w}px, CPU ${cpu}x: ${tasks.length} task(s) >= ${thr}ms in the first 8s; main thread (sum of events) ` + Object.entries(tot).map(([k, x]) => `${k} ${Math.round(x)}`).join(", ") + " ms");
    console.log("    app's boot LoAF (>=50ms): " + boot.filter(f => f.d >= 50).map(f => `${f.d}ms@${f.t} (s+l ${f.sl}, render ${f.render})`).join(" · "));
    for (const f of boot.filter(f => f.d >= 100).sort((a, b) => b.d - a.d).slice(0, 6)) console.log(`      LoAF ${f.d}ms@${f.t}: ${f.s.join("; ")}`);
    /* pictures painted (PaintImage args.data) and decoded (ImageDecodeTask / Decode Image) in the window, any thread of this process */
    const pimg = events.filter(e => e.pid === main.pid && e.name === "PaintImage" && e.ts >= t0 && e.ts < end);
    const byUrl = {};
    for (const e of pimg) { const d = (e.args && e.args.data) || {}; const u = (d.url || "(no url)").replace(/^https?:\/\/[^/]+\//, ""); const k = u.startsWith("data:") ? u.slice(0, 18) + "…" : u.slice(0, 40);
      const o = byUrl[k] || (byUrl[k] = { n: 0, ms: 0, src: `${d.srcWidth}x${d.srcHeight}`, dst: `${Math.round(d.width)}x${Math.round(d.height)}`, first: Math.round((e.ts - t0) / 1000), node: d.nodeName || "" }); o.n++; o.ms += (e.dur || 0) / 1000; }
    console.log("    PaintImage in the first 8s (by file): " + (Object.keys(byUrl).length || 0) + " files, " + pimg.length + " paints");
    for (const [k, o] of Object.entries(byUrl).sort((a, b) => b[1].ms - a[1].ms).slice(0, 8)) console.log(`      ${k.padEnd(42)} src ${o.src.padEnd(10)} drawn ${o.dst.padEnd(9)} ${o.n} paints ${o.ms.toFixed(1)}ms first +${o.first}ms ${o.node}`);
    const dec = events.filter(e => e.pid === main.pid && /^(Decode Image|ImageDecodeTask|Decode LazyPixelRef)$/.test(e.name) && e.ts >= t0 && e.ts < end && e.ph === "X");
    const dsum = {}; for (const e of dec) { const k = e.name + " " + ((e.args && (e.args.imageType || (e.args.data && e.args.data.imageType))) || ""); const o = dsum[k] || (dsum[k] = { n: 0, ms: 0, max: 0 }); o.n++; o.ms += e.dur / 1000; o.max = Math.max(o.max, e.dur / 1000); }
    console.log("    decodes (any thread): " + (Object.entries(dsum).map(([k, o]) => `${k.trim()} ×${o.n} ${o.ms.toFixed(0)}ms (max ${o.max.toFixed(0)}ms)`).join(" · ") || "none seen"));
    for (const t of tasks.sort((a, b) => b.dur - a.dur).slice(0, 10)) {
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
      const imgs = pimg.filter(e => e.ts >= t.ts && e.ts <= t.ts + t.dur).map(e => { const d = e.args.data || {}; return `${(d.url || "?").replace(/^https?:\/\/[^/]+\//, "").slice(0, 24)} ${d.srcWidth}x${d.srcHeight}->${Math.round(d.width)}x${Math.round(d.height)}`; });
      console.log(`    task ${Math.round(t.dur / 1000)}ms at +${Math.round((t.ts - t0) / 1000)}ms: ` + (Object.entries(k).filter(([, x]) => x >= 1).map(([kk, x]) => `${kk} ${Math.round(x)}`).join(", ") || "(no children)"));
      if (process.env.INV) { const inv = {}; for (const e of events) if (e.pid === main.pid && e.tid === main.tid && e.name === "StyleRecalcInvalidationTracking" && e.ts >= t.ts - 400000 && e.ts <= t.ts + t.dur) { const d = (e.args && e.args.data) || {}; const st = d.stackTrace && d.stackTrace[0]; const key = (d.reason || "?") + " " + (d.nodeName || "") + (d.changedClass ? " ." + d.changedClass : "") + (d.changedAttribute ? " [" + d.changedAttribute + "]" : "") + (d.changedId ? " #" + d.changedId : "") + " ← " + (st ? (st.functionName || "(anon)") + ":" + (st.lineNumber + 1) : "(no stack)"); inv[key] = (inv[key] || 0) + 1; }
        console.log("      invalidations (up to 400ms before): " + (Object.entries(inv).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, n]) => k + " ×" + n).join(" · ") || "none")); }
      if (process.env.NAMES) { const nm = {}; for (const e of inside) nm[e.name] = (nm[e.name] || 0) + e.dur / 1000; console.log("      events: " + Object.entries(nm).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([n, x]) => `${n} ${Math.round(x)}`).join(" · ")); }
      console.log("      " + Object.entries(fns).sort((a, b) => b[1] - a[1]).slice(0, 7).map(([f, x]) => `${f} ${Math.round(x)}`).join(" · "));
      if (imgs.length) console.log("      images painted: " + [...new Set(imgs)].slice(0, 5).join(" · ") + (imgs.length > 5 ? ` (+${imgs.length - 5})` : ""));
    }
  }
  return true;
};
