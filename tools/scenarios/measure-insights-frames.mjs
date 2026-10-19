/* Insights scroll, one suspect switched off at a time (CSS injected, index.html untouched): rAF gaps over 24ms, long
   animation frames (render part), main-thread raster/decode/paint from a trace, during real touch swipes, CPU 4x (CPU=1 none).
   The variants answer "which part of the page makes the swipe heavy". Headless Chromium rasters in software, so read the
   numbers as relative (variant vs base), not as an iPad's.
     CHROME="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" node tools/kit.mjs run tools/scenarios/measure-insights-frames.mjs --widths 859,412
     VARIANTS=base,reveal-first,nohero node ... ROUNDS=3 */
const VARIANTS = {
  base: "",
  "revealed-first": "REVEAL",   /* every .ix-rv is already "in" before the swipe: no reveals during it */
  noreveal: ".ix-rv, .ix-rv * { transition: none !important; opacity: 1 !important; transform: none !important; }",
  nohero: ".ix-hero::before { display: none !important; }",
  noheroanim: ".ix-shine, .ix-medal, .ix-medal * { animation: none !important; }",
  noring: ".ix-ring { -webkit-mask: none !important; mask: none !important; }",
  noshadow: "* { box-shadow: none !important; text-shadow: none !important; }",
  nocourt: ".dzc-table { display: none !important; }",
  noclip: ".ix-card, .ix-hero { overflow: visible !important; }",
  "rv-noscale": ".ix-rv { transform: translateY(16px) !important; }",
  "rv-opacity": ".ix-rv { transform: none !important; transition: opacity .35s ease !important; }",
  noendless: "*, *::before, *::after { animation-iteration-count: 1 !important; animation-duration: .001s !important; }",
};
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const cpu = +(process.env.CPU || 4), rounds = +(process.env.ROUNDS || 2);
  const names = (process.env.VARIANTS || Object.keys(VARIANTS).join(",")).split(",");
  const swipes = async () => { for (let k = 0; k < 4; k++) { await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .25, 200, 12); await p.page.waitForTimeout(350); }
    for (let k = 0; k < 4; k++) { await p.swipe(p.w * .5, p.h * .3, p.w * .52, p.h * .85, 200, 12); await p.page.waitForTimeout(350); } };
  const KIND = { raster: /^RasterTask$/, decode: /^(Decode Image|ImageDecodeTask)$/, paint: /^(Paint|PaintImage)$/, prepaint: /^(PrePaint|UpdateLayerTree|Layerize)$/, style: /^(UpdateLayoutTree|RecalculateStyles)$/, layout: /^Layout$/, commit: /^Commit$/ };
  for (const name of names) {
    const css = VARIANTS[name]; if (css == null) continue;
    const res = [];
    for (let r = 0; r < rounds; r++) {
      await p.page.evaluate(() => { document.querySelectorAll("style[data-mi]").forEach(s => s.remove()); });
      await p.step("insights", `goTab("home"); goTab("taste"); goSub("taste", "insights")`, 2500);   /* leaving the tab lets the reveals play again */
      if (css && css !== "REVEAL") await p.page.evaluate(c => { const s = document.createElement("style"); s.dataset.mi = 1; s.textContent = c; document.head.appendChild(s); }, css);
      if (css === "REVEAL") await p.page.evaluate(() => document.querySelectorAll(".ix-rv").forEach(x => x.classList.add("in", "ix-now")));
      await p.page.waitForTimeout(1500);
      await p.page.evaluate(() => { window.__g = []; window.__lf = []; let last = 0; const f = t => { if (last) window.__g.push(t - last); last = t; window.__raf = requestAnimationFrame(f); }; window.__raf = requestAnimationFrame(f);
        new PerformanceObserver(l => window.__lf.push(...l.getEntries().map(e => ({ d: e.duration, render: e.renderStart ? e.startTime + e.duration - e.renderStart : 0 })))).observe({ type: "long-animation-frame" }); });
      await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
      const events = [], on = e => events.push(...e.value); p.cdp.on("Tracing.dataCollected", on);
      await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "cc", "blink"] }, transferMode: "ReportEvents" });
      await swipes();
      const done = new Promise(r2 => p.cdp.once("Tracing.tracingComplete", r2)); await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
      await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
      const g = await p.page.evaluate(() => { cancelAnimationFrame(window.__raf); return { g: window.__g, lf: window.__lf }; });
      const sum = {}; for (const e of events) { if (e.ph !== "X" || !e.dur) continue; for (const [k, re] of Object.entries(KIND)) if (re.test(e.name)) sum[k] = (sum[k] || 0) + e.dur / 1000; }
      const gaps = g.g.filter(x => x > 24);
      res.push({ frames: g.g.length, gaps24: gaps.length, worst: Math.round(Math.max(0, ...g.g)), p95: Math.round([...g.g].sort((a, b) => a - b)[Math.floor(g.g.length * .95)] || 0), lf: g.lf.length, lfRender: Math.round(g.lf.reduce((a, x) => a + x.render, 0)), sum });
    }
    const avg = k => Math.round(res.reduce((a, x) => a + (x.sum[k] || 0), 0) / res.length);
    console.log(`  [${w}px ${name.padEnd(15)}] frames ${Math.round(res.reduce((a, x) => a + x.frames, 0) / res.length)} · gaps>24ms ${res.map(x => x.gaps24).join("/")} · worst ${res.map(x => x.worst).join("/")}ms · p95 ${res.map(x => x.p95).join("/")} · LoAF ${res.map(x => x.lf).join("/")} (render ${res.map(x => x.lfRender).join("/")}ms) · raster ${avg("raster")} decode ${avg("decode")} paint ${avg("paint")} prepaint ${avg("prepaint")} style ${avg("style")} layout ${avg("layout")} commit ${avg("commit")} ms`);
  }
  return true;
};
