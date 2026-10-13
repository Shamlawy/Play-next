/* Where does a screen switch spend its frame? Records a Chromium trace of goTab(<to>) (default Queue) and sums the
   rendering work by kind: script, style, layout, paint (record), raster, image decode, compositing.
   The owner's Android reports (#384, #405…) say "rendering (style/layout/paint)" with ~0ms style/layout, so the cost is
   paint/raster/decode: this splits it.
     node tools/kit.mjs run tools/scenarios/trace-queue.mjs --widths 859
     TO=lib FROM=home node tools/kit.mjs run tools/scenarios/trace-queue.mjs --widths 859 */
const KINDS = {
  script: /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks|v8\.callFunction)$/,
  style: /^(UpdateLayoutTree|RecalculateStyles|ParseAuthorStyleSheet)$/,
  layout: /^(Layout|UpdateLayerTree|Layerize|PrePaint)$/,
  paint: /^(Paint|PaintImage|Commit)$/,
  raster: /^(RasterTask|GPUTask|RasterDecoderImpl::DoEndRasterCHROMIUM)$/,
  decode: /^(Decode Image|ImageDecodeTask|Decode LazyPixelRef|DecodeImage)$/,
};
export default async ({ p, w }) => {
  /* the phone's smoothness check runs once per version and switches decorations on and off: finish it so it isn't measured */
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  const to = process.env.TO || "queue", from = process.env.FROM || "home", rounds = +(process.env.N || 3);
  const sum = {}, perRound = [];
  for (let r = 0; r < rounds; r++) {
    await p.step("from", `goTab(${JSON.stringify(from)})`, 1500);
    const events = [];
    p.cdp.on("Tracing.dataCollected", e => events.push(...e.value));
    await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "cc", "blink", "disabled-by-default-devtools.timeline.frame"] }, transferMode: "ReportEvents" });
    await p.step("to", `goTab(${JSON.stringify(to)})`, 1500);
    const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
    await p.cdp.send("Tracing.end"); await done;
    const s = {};
    for (const e of events) {
      if (e.ph !== "X" || !e.dur) continue;
      for (const [k, re] of Object.entries(KINDS)) if (re.test(e.name)) s[k] = (s[k] || 0) + e.dur / 1000;
      const n = "· " + e.name; s[n] = (s[n] || 0) + e.dur / 1000;
    }
    perRound.push(s);
    for (const [k, v] of Object.entries(s)) sum[k] = (sum[k] || 0) + v / rounds;
  }
  const main = Object.keys(KINDS).map(k => `${k} ${Math.round(sum[k] || 0)}`).join(", ");
  console.log(`  ${w}px ${from} → ${to} (avg of ${rounds}, ms): ${main}`);
  const top = Object.entries(sum).filter(([k]) => k.startsWith("· ")).sort((a, b) => b[1] - a[1]).slice(0, 14);
  console.log("    top events: " + top.map(([k, v]) => `${k.slice(2)} ${Math.round(v)}`).join(" | "));
  return true;
};
