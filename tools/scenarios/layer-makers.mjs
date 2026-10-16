/* What the page itself can see of its GPU layers (calibration for the v287 layer-pile check): on each screen, the
   pieces in view that get their own layer: finished animations still holding their last frame (fill forwards/both),
   endless loops, and will-change: transform/opacity. Prints each with its size (share of the screen) and whether later
   content is drawn over its box (the "overlap" that cuts the page into more layers).
     node tools/kit.mjs run tools/scenarios/layer-makers.mjs --widths 412,859 [--file old.html]
     PLANT=tools/plants/layer-pile.js node tools/kit.mjs run tools/scenarios/layer-makers.mjs   (what a planted problem scores) */
import { LAYER_SCREENS } from "../layercount.mjs";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...seed(v, 60), nxEvo: 99 }));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  /* PLANT=tools/plants/x.js runs a plant first (to see what a planted problem scores) */
  if (process.env.PLANT) await p.page.evaluate((await import("node:fs")).readFileSync(process.env.PLANT, "utf8"));
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  for (const [name, js] of Object.entries(LAYER_SCREENS)) {
    await p.step(name, js, 2600);
    const r = await p.page.evaluate(() => typeof layerMakers === "function" ? layerMakers() : null);
    if (!r) { console.log(`  [${name}] no layerMakers() in this copy`); continue; }
    console.log(`  [${name}] ${w}px: score ${r.score.toFixed(2)} (${r.list.length} makers)`);
    for (const m of r.list.slice(0, +(process.env.TOP || 8))) console.log(`      ${m.why.padEnd(6)} ${m.share.toFixed(2)} ${m.over ? "under" : "     "} ${m.at}`);
  }
  return true;
};
