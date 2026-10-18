/* Settings leak finder: opens + closes Settings N times; compares live nodes (in the page) with the engine's total
   (live + detached-but-kept), counts what gets added outside the sheet, observers and listeners made per open.
     node tools/kit.mjs run tools/scenarios/soak-settings.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.cdp.send("Performance.enable");
  await p.page.evaluate(() => { window.__mo = 0; window.__io = 0; window.__ael = 0;
    const MO = MutationObserver; window.MutationObserver = class extends MO { constructor(c) { super(c); window.__mo++; } };
    const IO = IntersectionObserver; window.IntersectionObserver = class extends IO { constructor(c, o) { super(c, o); window.__io++; } };
    const a = EventTarget.prototype.addEventListener; EventTarget.prototype.addEventListener = function (...x) { window.__ael++; return a.apply(this, x); }; });
  const snap = async () => { await p.cdp.send("HeapProfiler.collectGarbage"); const x = Object.fromEntries((await p.cdp.send("Performance.getMetrics")).metrics.map(y => [y.name, y.value]));
    const l = await p.page.evaluate(() => ({ live: document.getElementsByTagName("*").length, sheet: document.getElementById("sheet-in").getElementsByTagName("*").length, body: [...document.body.children].length, mo: __mo, io: __io, ael: __ael, st: document.querySelectorAll("style").length, ids: document.querySelectorAll("[id]").length }));
    return { total: x.Nodes, ...l }; };
  const rows = [await snap()];
  for (let i = 1; i <= 6; i++) { await p.step("o", "openSettings()", 600); await p.step("c", "closeEdit()", 400); rows.push(await snap()); }
  console.log(`  ${w}px  engine-total live sheet bodyKids styleTags ids  newObservers(MO/IO)  addEventListener`);
  rows.forEach((r, i) => console.log(`   ${i}  ${r.total} ${r.live} ${r.sheet} ${r.body} ${r.st} ${r.ids}  ${r.mo}/${r.io}  ${r.ael}`));
};
