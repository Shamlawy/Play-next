/* Does opening many different Queue cards in a row wear the app down? (the owner, Oct 20: "I opened many cards in the game queue,
   after the 7th or 8th it lagged and the animations got worse and worse, then it got fixed alone"). Real touch: tap a closed
   row (that closes the open card and opens this one), wait, next. Per open it prints: worst frame gap, frames over 40ms, running
   animations at 0.4s and 2.4s (the entrance is done by then: they must fall back to ~5), GPU layers, DOM nodes, JS heap, style /
   layout / script ms and the renderer's memory (ps). On Oct 20 nothing grew in headless Chromium (frames, animations, layers,
   nodes, heap flat over 14 opens; per-open raster/decode flat); the renderer's memory rose ~200 MB over the first 5-8 opens
   with 3840 px heroes and then levelled off, which is where a phone's picture cache would start to push back. Nexi's
   power-up (ssjEmber...) fires once on a fresh seed and shows as one spike: not the cards.
     HERO=1  every game gets a 3840x1240 hero (make them: see tools/.cache/heroes/h0..h13.jpg, PIL noise, git-ignored)
     TRACE=1 per-open timeline sums (RasterTask, ImageDecodeTask, Layerize, GCs); CPU=2 or the run is very slow
     CPU=4 OPENS=14 GAMES=40
     node tools/kit.mjs run tools/scenarios/open-many.mjs --widths 412,859 */
const OPENS = +process.env.OPENS || 14, GAP = +process.env.GAP || 1200;
export default async ({ p, w, fail, seed }) => {
  const pg = p.page;
  const v = await pg.evaluate(() => APP_V);
  await pg.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify((() => { const d = seed(v, +process.env.GAMES || 40); if (process.env.HERO) d.games.forEach((g, i) => { g.hero = "tools/.cache/heroes/h" + (i % 14) + ".jpg"; }); return d; })()));
  await pg.reload(); await pg.waitForTimeout(3000);
  await pg.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  await p.cdp.send("Performance.enable");
  await p.cdp.send("LayerTree.enable").catch(() => {});
  let layers = 0; p.cdp.on("LayerTree.layerTreeDidChange", e => { if (e.layers) layers = e.layers.length; });
  if (+process.env.CPU > 1) await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +process.env.CPU });
  await p.step("queue", `goTab("games")`, 800);
  await pg.evaluate(() => { window.__gaps = []; let last = performance.now(); const f = t => { window.__gaps.push(t - last); last = t; requestAnimationFrame(f); }; requestAnimationFrame(f); });
  const rss = async () => { try { const { execSync } = await import("node:child_process");
      return Math.max(...execSync("ps -eo rss,args").toString().split("\n").filter(l => /--type=renderer/.test(l)).map(l => parseInt(l.trim(), 10))) / 1024 | 0; } catch (e) { return -1; } };
  const snap = async () => Object.fromEntries((await p.cdp.send("Performance.getMetrics")).metrics.map(x => [x.name, x.value]));
  console.log(`  ${w}px open  maxGap  >40ms  anims@.4s anims@2.4s  layers  nodes  heapMB  styleMs  layoutMs  scriptMs  rssMB`);
  let prev = await snap();
  let evs = []; p.cdp.on("Tracing.dataCollected", e => evs.push(...e.value));
  const tr = async fn => { evs = []; await p.cdp.send("Tracing.start", { categories: "devtools.timeline,disabled-by-default-devtools.timeline,gpu,cc,viz", transferMode: "ReportEvents" });
    await fn(); const done = new Promise(r => p.cdp.once("Tracing.tracingComplete", r)); await p.cdp.send("Tracing.end"); await done;
    const sum = {}; for (const e of evs) if (e.ph === "X" && e.dur) { const n = e.name; if (/^(RasterTask|ImageDecodeTask|Decode LazyPixelRef|Decode Image|GPUTask|DrawFrame|Layerize|PrePaint|Paint|Commit|UpdateLayer|Layout|UpdateLayoutTree|FunctionCall|EvaluateScript|MinorGC|MajorGC|V8.GC_MC_BACKGROUND_MARKING|ImageUploadTask|RunTask)$/.test(n)) sum[n] = (sum[n] || 0) + e.dur / 1000; }
    return sum; };
  const TR = process.env.TRACE ? (process.env.TRACE + "").split(",") : null;
  for (let k = 0; k < OPENS; k++) {
    const id = await pg.evaluate(k => { const rs = [...document.querySelectorAll("#p-queue [data-row]:not(.hero)")]; const r = rs[Math.min(k + 1, rs.length - 1)]; if (!r) return null;
      r.scrollIntoView({ block: "center", behavior: "instant" }); return r.dataset.row; }, k);
    await pg.waitForTimeout(450);
    const b = await pg.locator(`#p-queue [data-row="${id}"]`).first().boundingBox();
    await pg.evaluate(() => { window.__gaps = []; });
    let a1 = 0, sum = null;
    const act = async () => { await p.tap(b.x + b.width / 2, b.y + Math.min(b.height / 2, 40));
      await pg.waitForTimeout(400); a1 = await pg.evaluate(() => document.getAnimations().length); await pg.waitForTimeout(2000); };
    if (TR) sum = await tr(act); else await act();
    if (sum) console.log("      trace:", Object.entries(sum).map(([k, v]) => k + "=" + v.toFixed(0)).join(" "));
    const a2 = await pg.evaluate(() => document.getAnimations().length);
    if (a2 > 8) console.log("      anims:", JSON.stringify(await pg.evaluate(() => document.getAnimations().map(a => (a.animationName || (a.effect && a.effect.target && a.effect.target.className) || "?") + "@" + ((a.effect && a.effect.target && (a.effect.target.id || a.effect.target.tagName)) || "")).slice(0, 30))));
    const gaps = await pg.evaluate(() => window.__gaps.slice());
    const m = await snap();
    const d = n => ((m[n] - prev[n]) * 1000).toFixed(0);
    console.log(`        ${String(k + 1).padStart(4)}  ${Math.max(...gaps).toFixed(0).padStart(6)}  ${String(gaps.filter(x => x > 40).length).padStart(5)}  ${String(a1).padStart(9)} ${String(a2).padStart(10)}  ${String(layers).padStart(6)}  ${String(m.Nodes).padStart(5)}  ${(m.JSHeapUsedSize / 1048576).toFixed(1).padStart(6)}  ${d("RecalcStyleDuration").padStart(7)}  ${d("LayoutDuration").padStart(8)}  ${d("ScriptDuration").padStart(8)}  ${String(await rss()).padStart(5)}`);
    prev = m;
    await pg.waitForTimeout(Math.max(0, GAP - 2400));
  }
};
