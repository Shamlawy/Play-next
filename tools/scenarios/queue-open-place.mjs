/* The card you tap open stays where your finger is (the owner, Oct 18: "I spent time on the game queue and it lagged a lot and
   the games stopped opening"). Opening a card closes the one above it, so everything below moved up ~440px (unfolded) and the
   card you opened slid off the top; the next tap then landed on another row. With 60 games, real touch, for rows at three
   heights: the opened card's top is on screen (and near where you tapped, unless it slid up to clear the tab bar), its bottom
   is above the tab bar when it fits, and Nexi files nothing. Then the old jump is planted (a scroll after each open) and Nexi
   must report "ended up off the screen".
     node tools/kit.mjs run tools/scenarios/queue-open-place.mjs --widths 412,859 */
export default async ({ p, w, fail, seed }) => {
  const pg = p.page;
  const v = await pg.evaluate(() => APP_V);
  await pg.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await pg.reload(); await pg.waitForTimeout(3000);
  await pg.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  await p.step("queue", `goTab("games")`, 1500);
  const tapRow = async (k, at) => {
    const id = await pg.evaluate(([k, at]) => { const r = [...document.querySelectorAll("#p-queue [data-row]:not(.hero)")][k];
      scrollTo({ top: scrollY + r.getBoundingClientRect().top - innerHeight * at, behavior: "instant" }); return r.dataset.row; }, [k, at]);
    await pg.waitForTimeout(900);
    const b = await pg.locator(`#p-queue [data-row="${id}"]`).boundingBox();
    await p.tap(b.x + b.width / 2, b.y + Math.min(b.height / 2, 40)); await pg.waitForTimeout(1600);
    const h = await pg.evaluate(id => { const h = document.querySelector(`#p-queue .hero[data-row="${CSS.escape(id)}"]`); if (!h) return null; const r = h.getBoundingClientRect(); return { top: r.top, bottom: r.bottom, h: r.height, vh: innerHeight }; }, id);
    return { id, was: b.y, h };
  };
  let k = 1;
  for (const at of [0.25, 0.45, 0.7]) {
    const r = await tapRow(k++, at);
    if (!r.h) { fail(`row tapped at ${Math.round(at * 100)}% didn't open`); continue; }
    const fits = r.h.h < r.h.vh - 160;
    if (r.h.top < 0) fail(`row tapped at ${Math.round(at * 100)}%: the card's top is ${Math.round(-r.h.top)}px above the screen`);
    else if (r.h.top > r.was + 2) fail(`row tapped at ${Math.round(at * 100)}%: the card moved down (${Math.round(r.was)} → ${Math.round(r.h.top)})`);
    if (fits && r.h.bottom > r.h.vh - 80) fail(`row tapped at ${Math.round(at * 100)}%: the card runs under the tab bar (bottom ${Math.round(r.h.bottom)} of ${r.h.vh})`);
    console.log(`  ${w}px tapped at ${Math.round(r.was)}px → card ${Math.round(r.h.top)}..${Math.round(r.h.bottom)} (screen ${r.h.vh})`);
  }
  await p.flush();
  const own = () => p.reports.filter(x => /ended up off the screen/.test(x.msg));
  if (own().length) fail(`Nexi reported a card off the screen without the plant: ${own()[0].at}`);
  /* the old jump, planted: every open is followed by a scroll that throws the card off the top */
  await pg.evaluate(() => document.addEventListener("click", e => { if (e.target.closest("#p-queue [data-row]:not(.hero)")) setTimeout(() => scrollBy({ top: innerHeight, behavior: "instant" }), 300); }, true));
  await tapRow(k++, 0.45);
  await p.flush();
  if (!own().length) fail("planted: a card thrown off the screen wasn't reported");
  else console.log(`  planted jump → reported: ${own()[0].msg}`);
};
