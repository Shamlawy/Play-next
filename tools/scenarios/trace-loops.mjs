/* Which endless loops cost main-thread work while a finger scrolls, and why: Chromium's own "could not run on the GPU"
   reasons (the trace's Animation events, compositeFailed / unsupportedProperties, like Lighthouse's non-composited
   animations audit), then the style passes and long frames of six swipes. Arms (HOLD=): off = loops running, still = every
   loop paused (the floor), x:<name> = one loop paused, on = v280's hold (only in builds before v282: --file old.html).
     node tools/kit.mjs run tools/scenarios/trace-loops.mjs --widths 859
     TAB=lib HOLD=off node tools/kit.mjs run tools/scenarios/trace-loops.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  const cpu = +(process.env.CPU || 4);
  if (cpu > 1) await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  const tab = process.env.TAB || "queue", arms = (process.env.HOLD || "on,off").split(",");
  await p.step("tab", tab === "game" ? `openFull(S.games[0].id)` : `goTab(${JSON.stringify(tab)})`, 2500);
  const loops = await p.page.evaluate(() => document.getAnimations().filter(a => a.playState === "running" && a.effect && a.effect.getTiming().iterations === Infinity)
    .map(a => `${a.animationName || a.id || "(script)"}@${(a.effect.target && (a.effect.target.id || a.effect.target.className && String(a.effect.target.className).split(" ")[0] || a.effect.target.tagName)) || "?"}${a.effect.pseudoElement || ""}`));
  console.log(`  ${w}px ${tab}: ${loops.length} endless loops running: ${loops.join(", ")}`);
  for (const arm of arms) {
    /* on = the v280 hold, off = loops run while scrolling, still = every endless loop stopped (the floor) */
    await p.page.evaluate(arm => {
      if (typeof mvhMove === "function") for (const t of ["touchmove", "scroll", "wheel"]) { removeEventListener(t, mvhMove, { capture: true }); if (arm === "on") addEventListener(t, mvhMove, { capture: true, passive: true }); }
      /* x:<name> = that loop paused (by animation name, "script" for a script loop), the rest running */
      for (const a of document.getAnimations()) if (a.effect && a.effect.getTiming().iterations === Infinity) {
        const n = a.animationName || "script";
        if (arm === "still" || arm === "x:" + n) a.pause(); else if (a.playState === "paused" && !/^(on)$/.test(arm)) a.play();
      }
    }, arm);
    await p.page.waitForTimeout(700);
    const events = [];
    const on = e => events.push(...e.value);
    p.cdp.on("Tracing.dataCollected", on);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.frame", "blink.animations", "cc", "benchmark"] }, transferMode: "ReportEvents" });
    for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 240, 14); await p.page.waitForTimeout(500); }
    for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.25, p.w * 0.52, p.h * 0.8, 240, 14); await p.page.waitForTimeout(500); }
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    const main = events.find(e => e.ph === "M" && e.name === "thread_name" && e.args && e.args.name === "CrRendererMain");
    const mine = main ? events.filter(e => e.pid === main.pid && e.tid === main.tid) : events;
    const ult = mine.filter(e => e.name === "UpdateLayoutTree" && e.ph === "X");
    const tasks = mine.filter(e => e.name === "RunTask" && e.ph === "X" && e.dur > 16000 * Math.max(1, cpu) / 4);
    if (process.env.ULT) console.log("    " + ult.slice(40, 44).map(e => JSON.stringify(e.args)).join("\n    "));
    const ms = a => Math.round(a.reduce((s, e) => s + (e.dur || 0), 0) / 1000);
    console.log(`  ${w}px ${tab} hold ${arm.padEnd(3)}: ${ult.length} style passes ${ms(ult)}ms (biggest ${ult.map(e => Math.round(e.dur / 1000)).sort((a, b) => b - a).slice(0, 5).join(", ")}), ` +
      `tasks over 16ms: ${tasks.length} (${tasks.map(e => Math.round(e.dur / 1000)).sort((a, b) => b - a).slice(0, 6).join(", ")}ms)`);
    const kinds = { paint: /^(Paint|PaintImage)$/, raster: /^(RasterTask)$/, decode: /^(Decode Image|ImageDecodeTask)$/, draw: /^(ProxyImpl::ScheduledActionDraw|DrawFrame)$/ }, sum = {};
    for (const e of events) if (e.ph === "X" && e.dur) for (const [k, re] of Object.entries(kinds)) if (re.test(e.name)) sum[k] = (sum[k] || 0) + e.dur / 1000;
    const bmf = mine.filter(e => e.name === "ProxyMain::BeginMainFrame" && e.ph === "X"), lz = mine.filter(e => /^(Layerize|PrePaint|Commit|LocalFrameView::RunPaintLifecyclePhase)$/.test(e.name) && e.ph === "X");
    console.log(`    main frames: ${bmf.length}, ${ms(bmf)}ms (${(ms(bmf) / Math.max(1, bmf.length)).toFixed(2)}ms each); prepaint+paint+layerize+commit ${ms(lz)}ms`);
    console.log("    all threads: " + Object.keys(kinds).map(k => `${k} ${Math.round(sum[k] || 0)}ms`).join(", "));
    /* what the longest main-thread tasks were made of */
    for (const t of tasks.sort((a, b) => b.dur - a.dur).slice(0, +(process.env.TOP || 3))) {
      const by = {};
      for (const e of mine) if (e !== t && e.ph === "X" && e.ts >= t.ts && e.ts + (e.dur || 0) <= t.ts + t.dur && e.dur > 500) {
        const d = e.args && e.args.data || {};
        const k = e.name + (d.functionName ? ` ${d.functionName}:${d.lineNumber}` : d.type ? ` ${d.type}` : d.url ? "" : "");
        by[k] = (by[k] || 0) + e.dur / 1000;
      }
      console.log(`    ${Math.round(t.dur / 1000)}ms task: ` + Object.entries(by).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([k, v]) => `${k} ${Math.round(v)}`).join(", "));
    }
    if (process.env.ANIM) console.log([...new Set(events.filter(e => /nimation/.test(e.name)).map(e => e.name + " " + e.ph + " " + JSON.stringify(e.args).slice(0, 160)))].slice(0, 30).join("\n"));
    if (arm === arms[0]) {
      const fail = {};
      for (const e of events) {
        if (e.name !== "Animation" || !e.args || !e.args.data) continue;
        const d = e.args.data;
        if (d.compositeFailed === undefined) continue;
        const k = `${d.name || d.id} fail=${d.compositeFailed} props=${(d.unsupportedProperties || []).join("|")}`;
        fail[k] = (fail[k] || 0) + 1;
      }
      console.log("    not on the GPU: " + (Object.keys(fail).filter(k => !/fail=0 /.test(k)).join("\n      ") || "none"));
    }
  }
  return true;
};
