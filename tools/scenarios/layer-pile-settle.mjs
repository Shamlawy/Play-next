/* Nov 16 (L118, M89): Nexi's v287 layer-pile check (layerPileAfter) restarted its 700ms wait on every BUG_AFTER pass and
   dropped its run when a touch or scroll had come in the last 1.2s, so on a busy runner it could never run: its plant
   (tools/plants/layer-pile.js) missed on app-check three times while passing locally. The wait now coalesces: one pending
   run, never restarted by a newer pass, and a run that lands during input waits for the quiet instead of dropping.
   Two cases with the plant on the Queue, the check taken out of BUG_AFTER so only this scenario's calls drive it:
     A  a pass every 300ms for 4s (faster than the wait): the report must come while the passes are still coming
     B  one pass, then a touch 500ms later and nothing after: the report must still come once it's quiet
     node tools/kit.mjs run tools/scenarios/layer-pile-settle.mjs --widths 412      (--file tools/.cache/old.html: the before) */
import fs from "fs";
const PLANT = fs.readFileSync(new URL("../plants/layer-pile.js", import.meta.url), "utf8");
export default async ({ p, w }) => {
  const pg = p.page;
  await p.step("queue", `goTab("queue")`, 2600);
  await pg.evaluate(PLANT);
  const ready = await pg.evaluate(() => {
    const i = BUG_AFTER.findIndex(f => f.name === "layerPileAfter"); if (i < 0) return "no layerPileAfter in BUG_AFTER";
    window.__lpf = BUG_AFTER.splice(i, 1)[0];
    extend("bugReport", { after(o, a) { if (/GPU layers pile up/.test(a[1])) (window.__lp ||= []).push(performance.now()); } });
    return layerMakers().score >= .25 ? "" : `the plant scores only ${layerMakers().score.toFixed(2)} here`;
  });
  if (ready) { console.log(`  ✗ ${w}px: ${ready}`); return false; }
  await pg.waitForTimeout(1500);   /* anything armed before the check left BUG_AFTER runs out */
  const reset = () => pg.evaluate(() => { window.__lp = []; S.bugSent = {}; });
  let ok = true;

  await reset();
  const a = await pg.evaluate(() => new Promise(done => {
    const t0 = performance.now(), iv = setInterval(() => {
      if (performance.now() - t0 > 4000) { clearInterval(iv); done(window.__lp.length ? Math.round(window.__lp[0] - t0) : -1); return; }
      window.__lpf();
    }, 300);
    window.__lpf();
  }));
  if (a < 0) { ok = false; console.log(`  ✗ ${w}px A: a pass every 300ms for 4s, and the check never ran`); }
  else console.log(`  ✓ ${w}px A: reported ${a}ms into a pass every 300ms`);
  await pg.waitForTimeout(2500);   /* A's last pending run (old code) lands here, not in B */

  await reset();
  const b = await pg.evaluate(() => new Promise(done => {
    const t0 = performance.now(); window.__lpf();
    setTimeout(() => dispatchEvent(new WheelEvent("wheel", { deltaY: 40 })), 500);
    setTimeout(() => done(window.__lp.length ? Math.round(window.__lp[0] - t0) : -1), 4000);
  }));
  if (b < 0) { ok = false; console.log(`  ✗ ${w}px B: a touch came while the check waited, and it never ran`); }
  else console.log(`  ✓ ${w}px B: reported ${b}ms after the pass (a touch at 500ms)`);
  [...new Set(p.errors)].forEach(e => { ok = false; console.log(`  ✗ ${w}px: script error: ${e}`); });
  return ok;
};
