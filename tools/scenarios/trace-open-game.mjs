/* What does opening a game page spend its time on? Traces openFull(<id>) (default g1) at 4x CPU and lists every layout and
   style recalc over 2ms, with the JS that forced it (a stack = forced inside a script) and how many objects it touched.
     node tools/kit.mjs run tools/scenarios/trace-open-game.mjs --widths 859
     GAME=g3 CPU=1 node tools/kit.mjs run tools/scenarios/trace-open-game.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.step("home", `goTab("games")`, 2500);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +(process.env.CPU || 4) });
  const events = [];
  p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
  await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.stack"] }, transferMode: "ReportEvents" });
  await p.step("open", `openFull(${JSON.stringify(process.env.GAME || "g1")})`, 2500);
  const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
  await p.cdp.send("Tracing.end"); await done;
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const t0 = Math.min(...events.filter(e => e.ph === "X" && e.name === "FunctionCall").map(e => e.ts));
  const rows = events.filter(e => e.ph === "X" && /^(Layout|UpdateLayoutTree)$/.test(e.name) && e.dur > 2000).sort((a, b) => a.ts - b.ts);
  let tot = { Layout: 0, UpdateLayoutTree: 0 };
  for (const e of rows) { tot[e.name] += e.dur / 1000;
    const d = (e.args && (e.args.beginData || e.args.data)) || {}, st = (d.stackTrace || []).slice(0, 3).map(f => `${f.functionName || "(anon)"}:${f.lineNumber}`).join(" < ");
    console.log(`    +${String(Math.round((e.ts - t0) / 1000)).padStart(5)}ms ${e.name.padEnd(16)} ${(e.dur / 1000).toFixed(0).padStart(4)}ms ${d.dirtyObjects != null ? "objects " + d.dirtyObjects + "/" + d.totalObjects : d.elementCount != null ? "elements " + d.elementCount : ""} ${st ? "FORCED by " + st : ""}`); }
  console.log(`  ${w}px open game: layout ${Math.round(tot.Layout)}ms, style ${Math.round(tot.UpdateLayoutTree)}ms (events over 2ms)`);
  return true;
};
