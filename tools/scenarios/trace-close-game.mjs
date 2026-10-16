/* Does closing a game page hitch? (v287 tried hiding the screen under a fully open game page, html.fullup, and showing it
   again as a close starts: this found an 80-130ms frame at the start of every close, against 17-33ms before, so it was dropped.) This opens a game from SCREEN (default played),
   waits until the page is fully open, then closes it while counting frame gaps (requestAnimationFrame) and tracing the
   main thread (style, layout, paint, script) at CPU x (default 4).
     node tools/kit.mjs run tools/scenarios/trace-close-game.mjs --widths 412,859
     node tools/kit.mjs run tools/scenarios/trace-close-game.mjs --file old.html --widths 412,859   (the before) */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  /* CSS=<rules> tries a change without editing the app */
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  const scr = process.env.SCREEN || "played";
  await p.step(scr, `goTab("games"); goSub("games", ${JSON.stringify(scr)})`, 2600);
  const res = [];
  for (let run = 0; run < +(process.env.RUNS || 3); run++) {
    await p.step("open", `openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)`, 2200);
    const up = await p.page.evaluate(() => document.documentElement.classList.contains("fullup"));
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +(process.env.CPU || 4) });
    const events = []; const got = e => events.push(...e.value); p.cdp.on("Tracing.dataCollected", got);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
    const gaps = await p.page.evaluate(() => new Promise(res => { const g = []; let last = performance.now(), t0 = last;
      const f = t => { g.push(t - last); last = t; if (t - t0 < 900) requestAnimationFrame(f); else res(g); };
      requestAnimationFrame(t => { last = t; t0 = t; closeFull(); requestAnimationFrame(f); }); }));
    const done = new Promise(r => p.cdp.once("Tracing.tracingComplete", r));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", got);
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
    const sum = re => Math.round(events.filter(e => e.ph === "X" && re.test(e.name)).reduce((a, e) => a + e.dur, 0) / 1000);
    const r = { up, worst: Math.round(Math.max(...gaps)), over: gaps.filter(x => x > 34).length, frames: gaps.length,
      style: sum(/^UpdateLayoutTree$/), layout: sum(/^Layout$/), paint: sum(/^(Paint|PrePaint|Layerize|Commit)$/), script: sum(/^(FunctionCall|EvaluateScript|TimerFire)$/) };
    res.push(r);
    console.log(`  ${w}px close #${run + 1}${r.up ? " (html.fullup: screen under it hidden)" : ""}: worst frame ${r.worst}ms, ${r.over} over 34ms of ${r.frames}; main thread style ${r.style} · layout ${r.layout} · paint ${r.paint} · script ${r.script} ms`);
    await p.page.waitForTimeout(800);
  }
  const avg = k => Math.round(res.reduce((a, r) => a + r[k], 0) / res.length);
  console.log(`  ${w}px average: worst ${avg("worst")}ms, style ${avg("style")} · layout ${avg("layout")} · paint ${avg("paint")} · script ${avg("script")} ms`);
  return true;
};
