/* What opening Settings costs, by function (Chromium trace, CPU 4x slower): FunctionCall totals with name:line, plus
   style / layout totals. v297 (Settings rail: every group open in one scroll) was measured with it.
     node tools/kit.mjs run tools/scenarios/trace-settings.mjs --widths 412      (--file old.html for the before; SCOPE=games) */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.page.waitForTimeout(1500);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +(process.env.CPU || 4) });
  const events = [];
  p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
  await p.page.evaluate(sc => { openSettings(sc); }, process.env.SCOPE || "app");
  await p.page.waitForTimeout(2500);
  const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
  await p.cdp.send("Tracing.end"); await done;
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const by = {}, kinds = {};
  for (const e of events) {
    if (e.ph !== "X" || !e.dur) continue;
    if (/^(FunctionCall|TimerFire|FireAnimationFrame|EventDispatch|FireIdleCallback|UpdateLayoutTree|Layout|Paint|PrePaint)$/.test(e.name)) kinds[e.name] = (kinds[e.name] || 0) + e.dur / 1000;
    if (e.name !== "FunctionCall") continue;
    const d = (e.args && e.args.data) || {};
    const k = `${d.functionName || "(anon)"}:${d.lineNumber ?? "?"}`;
    by[k] = (by[k] || 0) + e.dur / 1000;
  }
  console.log(`  ${w}px settings: ` + Object.entries(kinds).map(([k, v]) => `${k} ${Math.round(v)}`).join(", "));
  console.log("    " + Object.entries(by).sort((a, b) => b[1] - a[1]).slice(0, 14).map(([k, v]) => `${k} ${Math.round(v)}ms`).join("\n    "));
  return true;
};
