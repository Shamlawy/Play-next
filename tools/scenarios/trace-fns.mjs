/* Which functions run during an action, by total ms (Chromium trace, FunctionCall / TimerFire / EventDispatch / rAF with
   their function name and line in index.html). Default action: three finger swipes up and down on Queue.
     node tools/kit.mjs run tools/scenarios/trace-fns.mjs --widths 859
     TAB=home node tools/kit.mjs run tools/scenarios/trace-fns.mjs --widths 412 */
export default async ({ p, w }) => {
  /* the phone's smoothness check runs once per version and switches decorations on and off: finish it so it isn't measured */
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  const tab = process.env.TAB || "queue";
  await p.step("tab", `goTab(${JSON.stringify(tab)})`, 3000);
  const events = [];
  p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
  for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 240, 14); await p.page.waitForTimeout(500); }
  for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.25, p.w * 0.52, p.h * 0.8, 240, 14); await p.page.waitForTimeout(500); }
  const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
  await p.cdp.send("Tracing.end"); await done;
  const by = {}, kinds = {};
  for (const e of events) {
    if (e.ph !== "X" || !e.dur) continue;
    if (/^(FunctionCall|TimerFire|FireAnimationFrame|EventDispatch|FireIdleCallback|RunMicrotasks|UpdateLayoutTree|Layout)$/.test(e.name)) kinds[e.name] = (kinds[e.name] || 0) + e.dur / 1000;
    if (e.name !== "FunctionCall") continue;
    const d = (e.args && e.args.data) || {};
    const k = `${d.functionName || "(anon)"}:${d.lineNumber ?? "?"}`;
    by[k] = (by[k] || 0) + e.dur / 1000;
  }
  console.log(`  ${w}px ${tab}: ` + Object.entries(kinds).map(([k, v]) => `${k} ${Math.round(v)}`).join(", "));
  console.log("    " + Object.entries(by).sort((a, b) => b[1] - a[1]).slice(0, 15).map(([k, v]) => `${k} ${Math.round(v)}ms`).join("\n    "));
  return true;
};
