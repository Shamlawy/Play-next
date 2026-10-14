/* Which functions run during one tab switch (default Home → Queue), by total ms (Chromium trace, FunctionCall with name and
   line), plus style/layout totals. Compare two builds with --file:
     node tools/kit.mjs run tools/scenarios/trace-switch.mjs --widths 859
     FROM=home TO=taste node tools/kit.mjs run tools/scenarios/trace-switch.mjs --file old.html */
export default async ({ p }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  await p.step("from", `goTab(${JSON.stringify(process.env.FROM || "home")})`, 3000);
  const events = [];
  p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] }, transferMode: "ReportEvents" });
  await p.step("to", `goTab(${JSON.stringify(process.env.TO || "queue")})`, 2500);
  const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
  await p.cdp.send("Tracing.end"); await done;
  const by = {}, kinds = {};
  for (const e of events) {
    if (e.ph !== "X" || !e.dur) continue;
    if (/^(FunctionCall|TimerFire|FireAnimationFrame|EventDispatch|FireIdleCallback|UpdateLayoutTree|Layout|Paint)$/.test(e.name)) kinds[e.name] = (kinds[e.name] || 0) + e.dur / 1000;
    if (e.name !== "FunctionCall") continue;
    const d = (e.args && e.args.data) || {}, k = `${d.functionName || "(anon)"}:${d.lineNumber ?? "?"}`;
    by[k] = (by[k] || 0) + e.dur / 1000;
  }
  console.log("  totals:", Object.entries(kinds).map(([k, v]) => `${k} ${Math.round(v)}`).join(", "));
  console.log("  top functions:", Object.entries(by).sort((a, b) => b[1] - a[1]).slice(0, 12).map(([k, v]) => `${k} ${Math.round(v)}`).join(" · "));
};
