/* #136 (v310, Insights: "1 long frame, script 41ms (forced style/layout 39ms) … look ← FrameRequestCallback"): Nexi's
   "old Arcade heading" check runs in a frame 1.8s after each screen change and measured every h1/h2/h3 in the page,
   hidden screens too, forcing their layout. Times every `look` frame callback after a switch (4x CPU, 60 games) and
   counts the headings it reads in hidden screens.
     node tools/kit.mjs run tools/scenarios/heading-look.mjs --widths 859,412   (--file tools/.cache/old.html for the before) */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(4000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const hidden = await p.page.evaluate(() => [...document.querySelectorAll("h1, h2, h3")].filter(h => h.closest(".panel:not(.on)")).length);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  let worst = 0;
  for (const [name, js] of [["insights", `goSub("taste", "insights")`], ["queue", `goTab("games")`], ["home", `goTab("home")`]]) {
    const events = []; const on = e => events.push(...e.value); p.cdp.on("Tracing.dataCollected", on);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
    await p.page.evaluate(js); await p.page.waitForTimeout(3200);
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res)); await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    /* each FireAnimationFrame whose call is `look` (the heading check): its whole time, with the layout it forced */
    const calls = events.filter(e => e.ph === "X" && e.name === "FunctionCall" && e.args?.data?.functionName === "look");
    const ms = calls.map(c => { const end = c.ts + c.dur, inner = events.filter(e => e.ph === "X" && /^(Layout|UpdateLayoutTree)$/.test(e.name) && e.ts >= c.ts && e.ts < end && e.tid === c.tid);
      return { ms: c.dur / 1000, forced: inner.reduce((s, e) => s + e.dur, 0) / 1000, line: c.args.data.lineNumber }; }).filter(x => x.ms > .5);
    const m = ms.reduce((a, x) => Math.max(a, x.ms), 0); worst = Math.max(worst, m);
    console.log(`  ${w}px → ${name}: look ×${ms.length} ${ms.map(x => `${x.ms.toFixed(1)}ms (forced ${x.forced.toFixed(1)}, line ${x.line})`).join(", ") || "none over 0.5ms"}`);
  }
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  console.log(`  ${w}px: ${hidden} headings sit in hidden screens; worst look ${worst.toFixed(1)}ms at 4x`);
  return !p.errors.length;
};
