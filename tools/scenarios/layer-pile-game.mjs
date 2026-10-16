/* v287 review: Nexi's layer-pile check measured the screen UNDER an open game page (#full covers it, so everything there
   looked "drawn over") and filed a false "GPU layers pile up" on a scrolled Played (38%) or Queue (35%). It now measures
   only the screen on top. Passes when a game page opened over scrolled Played/Queue scores under 0.25 and files nothing.
     node tools/kit.mjs run tools/scenarios/layer-pile-game.mjs --widths 412,859 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...seed(v, 60), nxEvo: 99 }));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  let ok = true;
  for (const tab of ["played", "queue"]) {
    await p.step(tab, `closeFull(); goTab("games"); goSub("games", "${tab}")`, 2600);
    await p.page.evaluate(() => scrollBy(0, 600)); await p.page.waitForTimeout(1500);
    await p.step(tab + " + game", `openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)`, 4000);
    const sc = await p.page.evaluate(() => layerMakers().score);
    const good = sc < .25; if (!good) ok = false;
    console.log(`  ${good ? "✓" : "✗"} ${w}px: game page over scrolled ${tab} scores ${sc.toFixed(2)}`);
  }
  const rep = p.reports.filter(r => /GPU layers pile up/.test(r.msg));
  if (rep.length) { ok = false; rep.forEach(r => console.log(`  ✗ ${w}px: reported ${r.screen}: ${r.msg}`)); } else console.log(`  ✓ ${w}px: nothing filed`);
  return ok;
};
