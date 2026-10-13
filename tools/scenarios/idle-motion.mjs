/* What keeps moving on each screen when nobody touches it: every running endless animation (CSS or script), grouped by
   the element and its keyframes. The owner (Oct 2026): "keep checking … stacked features causing issues or things I don't
   want": the list to look at when a screen feels busy.
     node tools/kit.mjs run tools/scenarios/idle-motion.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  for (const [name, js] of [["home", `goTab("home")`], ["queue", `goTab("games")`], ["plan", `goTab("plan")`], ["taste", `goTab("taste")`], ["game page", `openFull("g1")`]]) {
    await p.step(name, js, 2500);
    const r = await p.page.evaluate(() => {
      const out = {};
      for (const a of document.getAnimations()) {
        const t = a.effect && a.effect.target, tm = a.effect && a.effect.getComputedTiming();
        if (!t || a.playState !== "running" || !tm || tm.iterations !== Infinity) continue;
        if (!t.checkVisibility || !t.checkVisibility({ opacityProperty: true, visibilityProperty: true, contentVisibilityAuto: true })) continue;
        const r = t.getBoundingClientRect(); if (r.bottom < 0 || r.top > innerHeight || r.width * r.height < 4) continue;
        const own = t.closest("[id]"), k = `${a.animationName || "script"} on ${t.tagName.toLowerCase()}${t.classList[0] ? "." + t.classList[0] : ""}${a.effect.pseudoElement || ""} (in #${own ? own.id : "?"})`;
        out[k] = (out[k] || 0) + 1;
      }
      return Object.entries(out).sort((a, b) => b[1] - a[1]);
    });
    console.log(`  ${w}px ${name}: ${r.reduce((a, x) => a + x[1], 0)} endless animations on screen` + (r.length ? "\n      " + r.map(([k, n]) => `${n}× ${k}`).join("\n      ") : ""));
    if (name === "game page") await p.step("close", `closeFull()`, 700);
  }
  return true;
};
