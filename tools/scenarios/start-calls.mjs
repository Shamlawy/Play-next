/* How many times each big draw runs while the app opens (#334: the start redrew the Queue and Taste a second time when
   a later script loaded). V8's precise coverage counts every call from the reload to 8s; prints the draws and the
   functions called most often. RAW=1 prints the top 40 of everything.
     node tools/kit.mjs run tools/scenarios/start-calls.mjs --widths 859 */
const WATCH = /^(render|renderHome|renderQueue|renderLib|renderPlayed|renderTaste|renderPlan|applyLook|lfApply|lfSheet|dzPlay|qPatch|paintTab|goTab|goSub|fitRun|bugLayout|picCheck)$/;
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload(); await p.page.waitForTimeout(2500);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } if (typeof saveNow === "function") saveNow(); });
  await p.cdp.send("Profiler.enable");
  await p.cdp.send("Profiler.startPreciseCoverage", { callCount: true, detailed: false });
  await p.page.reload({ waitUntil: "load" });
  await p.page.waitForTimeout(+(process.env.MS || 8000));
  const { result } = await p.cdp.send("Profiler.takePreciseCoverage");
  await p.cdp.send("Profiler.stopPreciseCoverage");
  const by = new Map();
  for (const s of result) { if (!/index\.html|\/$/.test(s.url)) continue;
    for (const f of s.functions) { const n = f.functionName || "(anon)"; const c = f.ranges[0].count; if (!c) continue; by.set(n, (by.get(n) || 0) + c); } }
  const draws = [...by].filter(([n]) => WATCH.test(n)).sort((a, b) => b[1] - a[1]);
  console.log(`  ${w}px, first ${(+(process.env.MS || 8000)) / 1000}s: ` + draws.map(([n, c]) => `${n} ×${c}`).join(" · "));
  if (process.env.RAW) for (const [n, c] of [...by].sort((a, b) => b[1] - a[1]).slice(0, 40)) console.log(`    ${String(c).padStart(6)} ${n}`);
  return !p.errors.length;
};
