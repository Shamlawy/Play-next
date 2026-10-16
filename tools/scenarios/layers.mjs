/* Which elements get their own GPU layer on a screen, and why (Chromium's compositing reasons). Every layer is a separate
   surface the phone's GPU rasters and blends each frame; "Overlap" ones exist only because they sit above another layer, and
   one stray layer at the bottom (a full-screen background with a 3D transform) can drag everything above it onto the GPU.
     SCREENS=played,game node tools/kit.mjs run tools/scenarios/layers.mjs --widths 859
   Prints per screen: layers, MP of surface (area × dpr²), by reason, and the 15 biggest with their reasons.
   `node tools/kit.mjs layers` is the pass/fail version (tools/layers-budget.txt). */
import { countLayers, LAYER_SCREENS, printLayers } from "../layercount.mjs";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  /* CSS=<rules> adds a style to try a change without editing the app (e.g. CSS='.pnb-s{animation:none}') */
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const res = {};
  for (const name of (process.env.SCREENS || Object.keys(LAYER_SCREENS).join(",")).split(",")) {
    await p.step(name, LAYER_SCREENS[name], 2600);
    if (process.env.SCROLL) await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .3, 200, 10), await p.page.waitForTimeout(600);
    const c = await countLayers(p);
    res[name] = { n: c.n, mp: c.mp, screens: c.screens };
    printLayers(name, w, c, +(process.env.TOP || 15));
    if (process.env.ALL) for (const r of c.rows.filter(r => !/Overlap|Squash/.test(r.why))) console.log(`      direct: ${r.why} ${r.w}×${r.h} ${r.d}`);
  }
  if (process.env.OUT) (await import("node:fs")).writeFileSync(process.env.OUT, JSON.stringify(res));
  return true;
};
