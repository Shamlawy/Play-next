/* v313 (lag hunt round 3): while the Queue is hidden its rows may skip drawing (.qcold) and are drawn again a few per frame
   once it shows. This checks that it changes nothing you can see:
   (1) the Queue reopens at the top (goTab scrolls to 0), so skipped rows are only ever below the screen: the first row's
       place right after showing and 1.2s later is printed (main moves it ~15px as the screen settles too: 948→933 at 412),
       also when the Queue was redrawn while hidden (rows never drawn yet);
   (2) every row is drawn (not skipped) within ~1s, and at once when a scroll starts;
   (3) Nexi's blank-while-scrolling check still sees a row that really is skipped (it asks the row's first child).
     node tools/kit.mjs run tools/scenarios/queue-cold.mjs --widths 412,859 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { bugOff: true })));
  await p.page.reload(); await p.page.waitForTimeout(3500);
  let bad = 0;
  const top = () => p.page.evaluate(() => { const rows = [...document.querySelectorAll("#p-queue > [data-row]")];
    const r = rows.find(x => x.getBoundingClientRect().bottom > 80); return r ? { id: r.dataset.row, y: Math.round(r.getBoundingClientRect().top), sy: Math.round(scrollY) } : null; });
  const skipped = () => p.page.evaluate(() => [...document.querySelectorAll("#p-queue > [data-row]")].filter(r => r.firstElementChild && !r.firstElementChild.checkVisibility({ contentVisibilityAuto: true })).length);
  for (const redraw of [false, true]) {
    await p.page.evaluate(() => goTab("queue")); await p.page.waitForTimeout(1500);
    await p.page.evaluate(() => scrollTo({ top: document.documentElement.scrollHeight * .5, behavior: "instant" })); await p.page.waitForTimeout(600);
    const a = await top();
    await p.page.evaluate(() => goTab("home")); await p.page.waitForTimeout(1200);
    if (redraw) await p.page.evaluate(() => { S.games.slice(0, 40).forEach(g => { g.hype = ((g.hype || 3) % 5) + 1; }); renderQueue(); });
    const cold = await p.page.evaluate(() => document.querySelector("#p-queue").classList.contains("qcold"));
    await p.page.evaluate(() => goTab("queue"));
    await p.page.waitForTimeout(60);
    const b = await top(), sk0 = await skipped();
    await p.page.waitForTimeout(1200);
    const c = await top(), sk1 = await skipped();
    const jump = b && c && b.id === c.id ? Math.abs(c.y - b.y) : 999;
    const ok = cold && sk1 === 0;
    if (!ok) bad++;
    console.log(`  ${ok ? "✓" : "✗"} ${w}px ${redraw ? "redrawn while hidden" : "as left"}: cold while hidden ${cold}; top row ${a && a.id}/${b && b.id}/${c && c.id} at ${a && a.y}/${b && b.y}/${c && c.y} (scroll ${a && a.sy}/${b && b.sy}/${c && c.sy}), moved ${jump}px while the rest were drawn; skipped right after ${sk0}, after 1.2s ${sk1}`);
  }
  /* a scroll right after showing draws every row at once */
  await p.page.evaluate(() => goTab("home")); await p.page.waitForTimeout(1000);
  await p.page.evaluate(() => { goTab("queue"); scrollBy({ top: 300, behavior: "instant" }); });
  await p.page.waitForTimeout(80);
  { const n = await skipped(), cold = await p.page.evaluate(() => document.querySelector("#p-queue").classList.contains("qcold"));
    if (cold || n) bad++; console.log(`  ${!cold && !n ? "✓" : "✗"} ${w}px scroll right after showing: cold ${cold}, skipped ${n}`); }
  /* the #89 check's own test on a row that really is skipped (the last row, off screen, made content-visibility: auto) */
  const sees = await p.page.evaluate(() => { const rows = [...document.querySelectorAll("#p-queue > [data-row]")], r = rows[rows.length - 1];
    r.style.contentVisibility = "auto"; scrollTo({ top: 0, behavior: "instant" }); document.body.offsetHeight;
    return new Promise(ok => requestAnimationFrame(() => requestAnimationFrame(() => { const k = r.firstElementChild;
      const out = { own: r.checkVisibility({ contentVisibilityAuto: true }), child: k.checkVisibility({ contentVisibilityAuto: true }) }; r.style.contentVisibility = ""; ok(out); }))); });
  if (sees.child) bad++;
  console.log(`  ${!sees.child ? "✓" : "✗"} ${w}px a skipped row: its own checkVisibility says ${sees.own}, its first child's says ${sees.child} (the check asks the child)`);
  return bad === 0;
};
