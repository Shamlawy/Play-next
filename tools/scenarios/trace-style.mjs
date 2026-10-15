/* What makes the browser recalculate styles during an action: every style invalidation in a Chromium trace, grouped by
   the script line that caused it (with the call stack), plus the number and cost of UpdateLayoutTree passes.
   Default action: three finger swipes up and down on Queue.
     node tools/kit.mjs run tools/scenarios/trace-style.mjs --widths 859 */
export default async ({ p, w }) => {
  /* the phone's smoothness check runs once per version and switches decorations on and off: finish it so it isn't measured */
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  const tab = process.env.TAB || "queue";
  /* HOLD=off: the v280 scroll hold switched off (loops keep running mid-swipe) */
  if (process.env.HOLD === "off") await p.page.evaluate(() => { if (typeof mvhMove === "function") for (const t of ["touchmove", "scroll", "wheel"]) removeEventListener(t, mvhMove, { capture: true }); });
  await p.step("tab", `goTab(${JSON.stringify(tab)})`, 3000);
  const events = [];
  p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack", "disabled-by-default-devtools.timeline.invalidationTracking"] }, transferMode: "ReportEvents" });
  for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 240, 14); await p.page.waitForTimeout(500); }
  for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.25, p.w * 0.52, p.h * 0.8, 240, 14); await p.page.waitForTimeout(500); }
  const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
  await p.cdp.send("Tracing.end"); await done;
  const ult = events.filter(e => e.name === "UpdateLayoutTree" && e.ph === "X");
  console.log(`  ${w}px ${tab}: ${ult.length} style passes, ${Math.round(ult.reduce((a, e) => a + (e.dur || 0), 0) / 1000)}ms; biggest: ` +
    ult.map(e => Math.round(e.dur / 1000)).sort((a, b) => b - a).slice(0, 8).join(", ") + "ms");
  const by = {};
  for (const e of events) {
    if (!/^(ScheduleStyleRecalculation|StyleRecalcInvalidationTracking|StyleInvalidatorInvalidationTracking)$/.test(e.name)) continue;
    const d = (e.args && e.args.data) || {};
    const st = (d.stackTrace || []).slice(0, 4).map(f => `${f.functionName || "(anon)"}:${f.lineNumber}`).join(" < ");
    const k = `${e.name.replace("InvalidationTracking", "")} ${d.reason || ""} ${d.nodeName || ""} ${d.subtree ? "subtree " : ""}${d.extraData || ""} | ${st || "(no stack)"}`;
    by[k] = (by[k] || 0) + 1;
  }
  console.log("    " + Object.entries(by).sort((a, b) => b[1] - a[1]).slice(0, 18).map(([k, v]) => `${v}× ${k.slice(0, 200)}`).join("\n    "));
  return true;
};
