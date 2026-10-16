/* Which timer callbacks run long after a tab switch? #333 (v283): "longest script 109ms: (anonymous) ← TimerHandler:setTimeout"
   right after switching to Queue. Switches FROM → TO (default home → games) at 4x CPU and lists every setTimeout /
   setInterval / idle callback over 8ms in the next 5s, with the function and line it ran.
     node tools/kit.mjs run tools/scenarios/trace-timers.mjs --widths 859
     FROM=games TO=home CPU=4 node tools/kit.mjs run tools/scenarios/trace-timers.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.step("from", `goTab(${JSON.stringify(process.env.FROM || "home")})`, 4000);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +(process.env.CPU || 4) });
  const ev = [];
  p.cdp.on("Tracing.dataCollected", e => ev.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
  await p.step("to", `goTab(${JSON.stringify(process.env.TO || "games")})`, 5000);
  const done = new Promise(r => p.cdp.once("Tracing.tracingComplete", r));
  await p.cdp.send("Tracing.end"); await done;
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const X = ev.filter(e => e.ph === "X" && e.dur), t0 = Math.min(...X.filter(e => e.name === "FunctionCall").map(e => e.ts));
  const fns = X.filter(e => e.name === "FunctionCall");
  const rows = X.filter(e => /^(TimerFire|FireIdleCallback)$/.test(e.name) && e.dur > 8000).map(e => {
    const inner = fns.filter(f => f.tid === e.tid && f.ts >= e.ts && f.ts + f.dur <= e.ts + e.dur).sort((a, b) => b.dur - a.dur)[0];
    const d = inner && inner.args && inner.args.data || {};
    const lay = X.filter(f => f.tid === e.tid && /^(Layout|UpdateLayoutTree)$/.test(f.name) && f.ts >= e.ts && f.ts + f.dur <= e.ts + e.dur).reduce((a, f) => a + f.dur, 0);
    return { at: Math.round((e.ts - t0) / 1000), ms: Math.round(e.dur / 1000), lay: Math.round(lay / 1000), kind: e.name, fn: (d.functionName || "(anonymous)") + ":" + (d.lineNumber ?? "?") };
  }).sort((a, b) => b.ms - a.ms);
  rows.forEach(r => console.log(`    +${String(r.at).padStart(5)}ms ${String(r.ms).padStart(4)}ms (forced style/layout ${r.lay}ms) ${r.kind.padEnd(16)} ${r.fn}`));
  console.log(`  ${w}px ${process.env.FROM || "home"} → ${process.env.TO || "games"}: ${rows.length} timer callbacks over 8ms, longest ${rows[0] ? rows[0].ms + "ms " + rows[0].fn : "none"}`);
  return true;
};
