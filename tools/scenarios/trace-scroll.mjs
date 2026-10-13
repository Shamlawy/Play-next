/* What a finger scroll costs with and without a page part, by kind (script, style, layout, paint, raster, decode,
   composite). The owner's smoothness check (#438, v269) dropped 8% of scroll frames with 1085ms of long frames, 3% and
   0ms with "the moving background" off: this measures that switch (html.prb-<arm>, the check's own classes) here.
     node tools/kit.mjs run tools/scenarios/trace-scroll.mjs --widths 859
     ARMS=base,nobg,noshadow TAB=lib node tools/kit.mjs run tools/scenarios/trace-scroll.mjs --widths 859 */
const KINDS = {
  script: /^(FunctionCall|EvaluateScript|TimerFire|FireAnimationFrame|EventDispatch|RunMicrotasks)$/,
  style: /^(UpdateLayoutTree|RecalculateStyles)$/,
  layout: /^(Layout)$/,
  prepaint: /^(PrePaint|UpdateLayerTree|Layerize)$/,
  paint: /^(Paint|PaintImage)$/,
  commit: /^(Commit)$/,
  raster: /^(RasterTask)$/,
  decode: /^(Decode Image|ImageDecodeTask)$/,
  draw: /^(ProxyImpl::ScheduledActionDraw|DrawFrame)$/,
};
export default async ({ p, w }) => {
  /* the phone's smoothness check runs once per version and switches decorations on and off: finish it so it isn't measured */
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  const tab = process.env.TAB || "queue", arms = (process.env.ARMS || "base,nobg").split(","), rounds = +(process.env.N || 3);
  await p.step("tab", `goTab(${JSON.stringify(tab)})`, 2500);
  for (const arm of arms) {
    await p.page.evaluate(a => { document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); if (a !== "base") document.documentElement.classList.add("prb-" + a); }, arm);
    await p.page.waitForTimeout(800);
    const sum = {};
    for (let r = 0; r < rounds; r++) {
      const events = [];
      const on = e => events.push(...e.value);
      p.cdp.on("Tracing.dataCollected", on);
      await p.cdp.send("Tracing.start", { traceConfig: { includedCategories: ["devtools.timeline", "disabled-by-default-devtools.timeline", "cc", "blink"] }, transferMode: "ReportEvents" });
      for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.25, 240, 14); await p.page.waitForTimeout(500); }
      for (let k = 0; k < 3; k++) { await p.swipe(p.w * 0.5, p.h * 0.25, p.w * 0.52, p.h * 0.8, 240, 14); await p.page.waitForTimeout(500); }
      const done = new Promise(res => p.cdp.once("Tracing.tracingComplete", res));
      await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", on);
      for (const e of events) {
        if (e.ph !== "X" || !e.dur) continue;
        for (const [k, re] of Object.entries(KINDS)) if (re.test(e.name)) sum[k] = (sum[k] || 0) + e.dur / 1000 / rounds;
      }
    }
    console.log(`  ${w}px ${tab} scroll, ${arm.padEnd(8)} (ms, avg of ${rounds}): ` + Object.keys(KINDS).map(k => `${k} ${Math.round(sum[k] || 0)}`).join(", "));
  }
  return true;
};
