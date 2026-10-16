/* The phones' stutter reports (#60 Home, #62 game page, #207 Played, #116 blank on Queue) say the app's code was free
   during the hitch ("script 0ms, style+layout 1ms, render 5ms" in a 128ms frame), so the time goes to the drawing side:
   raster (turning layers into pixels), picture decoding, the compositor and GPU. trace-stutter.mjs only reads the main
   thread; this one reads every thread while real touch swipes run, 60 games, CPU 4x:
     - per thread group: main / compositor / raster workers / GPU / viz, ms busy
     - raster tasks, picture decodes (count, ms, biggest), and how many frames were drawn vs. due
     - the layer tree: how many layers, their total area in screen pixels (GPU memory ≈ area × 4 bytes) and the biggest ones
   Headless Chromium rasters on the CPU (SwiftShader), so the numbers are bigger than a phone's GPU but move the same way:
   compare before/after (--file old.html).
     node tools/kit.mjs run tools/scenarios/trace-raster.mjs --widths 859
     ONLY=played,game CPU=1 node tools/kit.mjs run tools/scenarios/trace-raster.mjs --widths 412 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  /* CSS=<rules> adds a style to try a change without editing the app (e.g. CSS='.pnb-s{animation:none}') */
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const cpu = +(process.env.CPU || 4);
  const swipes = async () => { for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 180, 12); await p.page.waitForTimeout(350); }
    for (let k = 0; k < 2; k++) { await p.swipe(p.w * 0.5, p.h * 0.3, p.w * 0.52, p.h * 0.85, 180, 12); await p.page.waitForTimeout(350); } };
  const PHASES = {
    home: `closeFull(); goTab("home")`,
    played: `closeFull(); goTab("games"); goSub("games", "played")`,
    queue: `closeFull(); goTab("games"); goSub("games", "queue")`,
    lib: `closeFull(); goTab("games"); goSub("games", "lib")`,
    game: `goTab("games"); openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)`,
  };
  const only = (process.env.ONLY || Object.keys(PHASES).join(",")).split(",");
  /* the layer tree is asked for once per screen, after the trace (LayerTree left on slows every frame) */
  const layerSnap = async () => { let got = null; const h = e => { if (e.layers) got = e.layers; }; p.cdp.on("LayerTree.layerTreeDidChange", h);
    await p.cdp.send("LayerTree.enable").catch(() => {}); for (let i = 0; i < 20 && !got; i++) await p.page.waitForTimeout(100);
    await p.cdp.send("LayerTree.disable").catch(() => {}); p.cdp.off("LayerTree.layerTreeDidChange", h); return got || []; };
  const out = {};
  for (const name of only) {
    if (!PHASES[name]) continue;
    await p.step(name, PHASES[name], 2600);
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
    const events = [], on = e => events.push(...e.value);
    p.cdp.on("Tracing.dataCollected", on);
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["toplevel", "cc", "gpu", "devtools.timeline", "disabled-by-default-devtools.timeline", "disabled-by-default-devtools.timeline.frame", "__metadata"] }, transferMode: "ReportEvents" });
    console.log(`  ${name}: swiping…`); await swipes();
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
    await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
    /* thread names */
    const tn = {}; for (const e of events) if (e.ph === "M" && e.name === "thread_name") tn[e.pid + ":" + e.tid] = e.args.name;
    const group = k => { const n = tn[k] || ""; return /CrRendererMain/.test(n) ? "main" : /^Compositor$/.test(n) ? "compositor" : /CompositorTileWorker|RasterWorker/.test(n) ? "raster"
      : /CrGpuMain|GpuMain/.test(n) ? "gpu" : /VizCompositor/.test(n) ? "viz" : /ImageDecode|ThreadPool/.test(n) ? "pool" : ""; };
    /* busy = outermost task slices per thread (nested RunTask events counted once) */
    const busy = {}, byT = {};
    for (const e of events) if (e.ph === "X" && e.dur && /RunTask$/.test(e.name)) (byT[e.pid + ":" + e.tid] ||= []).push(e);
    for (const [k, list] of Object.entries(byT)) { const g = group(k); if (!g) continue; list.sort((a, b) => a.ts - b.ts); let end = 0;
      for (const e of list) { if (e.ts >= end) { busy[g] = (busy[g] || 0) + e.dur / 1000; end = e.ts + e.dur; } else if (e.ts + e.dur > end) { busy[g] += (e.ts + e.dur - end) / 1000; end = e.ts + e.dur; } } }
    const span = (Math.max(...events.filter(e => e.ts).map(e => e.ts)) - Math.min(...events.filter(e => e.ts).map(e => e.ts))) / 1000;
    const sum = re => { let n = 0, ms = 0, big = 0; for (const e of events) if (e.ph === "X" && re.test(e.name)) { n++; ms += e.dur / 1000; big = Math.max(big, e.dur / 1000); } return { n, ms: Math.round(ms), big: Math.round(big) }; };
    const raster = sum(/^(RasterTask|TileManager::RasterTask|RasterSource::PlaybackToCanvas)$/), decode = sum(/^(ImageDecodeTask|Decode Image|ImageFrameGenerator::decode|GpuImageDecodeCache::DecodeImage|SoftwareImageDecodeCache::DecodeImageInTask)$/);
    const paint = sum(/^Paint$/), draws = events.filter(e => e.name === "DrawFrame").length, begins = events.filter(e => e.name === "BeginFrame" && group(e.pid + ":" + e.tid) === "compositor").length,
      dropped = events.filter(e => e.name === "DroppedFrame").length;
    /* layers: area in CSS px × dpr² as the GPU sees it */
    const dpr = await p.page.evaluate(() => devicePixelRatio), layers = await layerSnap();
    const L = layers.filter(l => l.drawsContent).map(l => ({ id: l.layerId, a: l.width * l.height, w: Math.round(l.width), h: Math.round(l.height), node: l.backendNodeId }));
    const area = L.reduce((a, l) => a + l.a, 0) * dpr * dpr;
    const big = L.sort((a, b) => b.a - a.a).slice(0, +(process.env.LAYERS || 5));
    const names = [];
    for (const l of big) { let d = "?"; try { const r = await p.cdp.send("DOM.describeNode", { backendNodeId: l.node }); const n = r.node; const at = n.attributes || []; const g = k => { const i = at.indexOf(k); return i >= 0 ? at[i + 1] : ""; };
      let why = ""; try { why = (await p.cdp.send("LayerTree.compositingReasons", { layerId: l.id })).compositingReasonIds.join("+"); } catch (e) {}
      d = (why ? "[" + why + "] " : "") + n.nodeName.toLowerCase() + (g("id") ? "#" + g("id") : "") + (g("class") ? "." + g("class").split(/\s+/).slice(0, 2).join(".") : ""); } catch (e) {} names.push(`${d} ${l.w}×${l.h}`); }
    if (process.env.LAYERS) { const kinds = {}; for (const l of L) { let d = "?"; try { const n = (await p.cdp.send("DOM.describeNode", { backendNodeId: l.node })).node, at = n.attributes || [], i = at.indexOf("class"); d = n.nodeName.toLowerCase() + (i >= 0 ? "." + at[i + 1].split(/\s+/)[0] : ""); } catch (e) {} kinds[d] = (kinds[d] || 0) + 1; }
      console.log("    layers by kind: " + Object.entries(kinds).sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k} ×${n}`).join(" · ")); }
    const r = { busy: Object.fromEntries(Object.entries(busy).map(([k, v]) => [k, Math.round(v)])), raster, decode, paint, draws, begins, dropped, layers: L.length, mpx: +(area / 1e6).toFixed(1) };
    out[name] = r;
    console.log(`\n  [${name}] ${w}px, CPU ${cpu}x, ${Math.round(span)}ms traced: busy ${Object.entries(r.busy).map(([k, v]) => `${k} ${v}`).join(" · ")} ms`);
    console.log(`    raster ${raster.n} tasks ${raster.ms}ms (biggest ${raster.big}) · decode ${decode.n} ${decode.ms}ms (biggest ${decode.big}) · paint ${paint.n} ${paint.ms}ms · frames drawn ${draws}, begun ${begins}, dropped ${dropped}`);
    console.log(`    layers ${L.length}, ${r.mpx} MP of GPU surface (≈${Math.round(area * 4 / 1e6)} MB); biggest: ${names.join(" · ")}`);
  }
  if (process.env.OUT) (await import("node:fs")).writeFileSync(process.env.OUT, JSON.stringify(out, null, 1));
  return true;
};
