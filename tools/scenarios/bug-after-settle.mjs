/* Nov 16 (L118, M89, M91): Nexi's BUG_AFTER checks that wait before they look (layer-pile, scroll-linked, stuck entrance)
   restarted their wait on every pass and dropped their run when a touch or scroll had come in the last 1.2s, so on a busy
   runner they could never run: the layer-pile plant (tools/plants/layer-pile.js) missed on app-check three times while
   passing locally. Each wait now coalesces: one pending run, never restarted by a newer pass, and a run that lands during
   input waits for the quiet instead of dropping (where.mjs --list-rule=L118 and audit.mjs bugAfterRestart keep it so).
   Per check, with its problem planted on the Queue and the check taken out of BUG_AFTER so only these calls drive it:
     A  a pass every 300ms for 4s (faster than the wait): the report must come while the passes are still coming
     B  one pass, then a touch 500ms later and nothing after: the report must still come once it's quiet
     C  (stuck entrance) a look pending on the Queue, then Library opens with its own stuck card: Library is looked at
     node tools/kit.mjs run tools/scenarios/bug-after-settle.mjs --widths 412      (--file tools/.cache/old.html: the before) */
import fs from "fs";
const plant = f => fs.readFileSync(new URL("../plants/" + f, import.meta.url), "utf8");
/* a card held invisible by an entrance that never started (v284's More for you): paused on its first frame, in view */
const STUCK = `(() => { const r = document.querySelector(".panel.on"), d = document.createElement("div");
  d.id = "zzstuck"; d.style.cssText = "position:fixed;left:40px;top:160px;width:220px;height:160px;background:#fff;pointer-events:none";
  r.appendChild(d); d.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 1000 }).pause(); })()`;
const CHECKS = [
  { name: "layerPileAfter", plant: plant("layer-pile.js"), re: /GPU layers pile up/ },
  { name: "scrollLinkedAfter", plant: plant("scroll-pile.js"), re: /move with the scroll/ },
  { name: "stuckEntranceAfter", plant: STUCK, re: /stayed invisible on screen/, screenChange: true },
];
export default async ({ p, w }) => {
  const pg = p.page; let ok = true;
  await p.step("queue", `goTab("queue")`, 2600);
  for (const c of CHECKS) {
    await pg.evaluate(c.plant);
    const ready = await pg.evaluate(([n, re]) => {
      const i = BUG_AFTER.findIndex(f => f.name === n); if (i < 0) return `no ${n} in BUG_AFTER`;
      window.__f = BUG_AFTER.splice(i, 1)[0]; window.__re = new RegExp(re); window.__hit = [];
      if (!window.__hooked) { window.__hooked = 1; extend("bugReport", { after(o, a) { if (window.__re && window.__re.test(a[1])) window.__hit.push(performance.now()); } }); }
      return "";
    }, [c.name, c.re.source]);
    if (ready) { console.log(`  ✗ ${w}px: ${ready}`); ok = false; continue; }
    await pg.waitForTimeout(1500);   /* anything armed before the check left BUG_AFTER runs out */
    const reset = () => pg.evaluate(() => { window.__hit = []; S.bugSent = {}; if (typeof SCRL !== "undefined") SCRL.seen.clear(); });
    await reset();
    const a = await pg.evaluate(() => new Promise(done => {
      const t0 = performance.now(), iv = setInterval(() => {
        if (performance.now() - t0 > 4000) { clearInterval(iv); done(window.__hit.length ? Math.round(window.__hit[0] - t0) : -1); return; }
        window.__f();
      }, 300);
      window.__f();
    }));
    if (a < 0) { ok = false; console.log(`  ✗ ${w}px ${c.name} A: a pass every 300ms for 4s, and the check never ran`); }
    else console.log(`  ✓ ${w}px ${c.name} A: reported ${a}ms into a pass every 300ms`);
    await pg.waitForTimeout(2500);   /* A's last pending run (old code) lands here, not in B */
    await reset();
    const b = await pg.evaluate(() => new Promise(done => {
      const t0 = performance.now(); window.__f();
      setTimeout(() => dispatchEvent(new WheelEvent("wheel", { deltaY: 40 })), 500);
      setTimeout(() => done(window.__hit.length ? Math.round(window.__hit[0] - t0) : -1), 4000);
    }));
    if (b < 0) { ok = false; console.log(`  ✗ ${w}px ${c.name} B: a touch came while the check waited, and it never ran`); }
    else console.log(`  ✓ ${w}px ${c.name} B: reported ${b}ms after the pass (a touch at 500ms)`);
    if (c.screenChange) {   /* C (review helper): a look pending on the Queue, then Library opens with its own stuck card and no pass comes after */
      await reset();
      const cc = await pg.evaluate(plantNext => new Promise(done => {
        const t0 = performance.now(); window.__f();
        setTimeout(() => { goTab("games"); goSub("games", "lib"); setTimeout(() => { document.getElementById("zzstuck")?.remove(); eval(plantNext); }, 150); }, 200);
        setTimeout(() => done(window.__hit.length ? Math.round(window.__hit[0] - t0) + "ms on " + bugScreen() : ""), 4500);
      }), c.plant);
      if (!cc || !/lib/.test(cc)) { ok = false; console.log(`  ✗ ${w}px ${c.name} C: the screen changed while a look was pending, and the new screen was never looked at${cc ? " (reported " + cc + ")" : ""}`); }
      else console.log(`  ✓ ${w}px ${c.name} C: reported ${cc} after the screen changed under a pending look`);
    }
    await pg.evaluate(f => { BUG_AFTER.push(window.__f); }, null);
    await pg.reload(); await pg.waitForTimeout(2500); await p.step("queue", `goTab("queue")`, 2600);   /* the next plant on a clean page */
  }
  [...new Set(p.errors)].forEach(e => { ok = false; console.log(`  ✗ ${w}px: script error: ${e}`); });
  return ok;
};
