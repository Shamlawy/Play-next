/* v318: the app starting with Taste on Book but another tab showing mounts nothing hidden (no sounds or turning pages there); going to Taste mounts it; folding/unfolding re-lays it. node tools/kit.mjs run tools/scenarios/skills-book-boot.mjs --widths 412,859 */
export default async ({ p, w, seed, fail }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 30)));
  await p.page.reload(); await p.page.waitForTimeout(2500);
  await p.page.evaluate(() => { goTab("taste"); goSub("taste", "book"); goTab("home"); });
  await p.page.reload(); await p.page.waitForTimeout(3000);
  const a = await p.page.evaluate(() => ({ sub: subOf("taste"), on: !!document.querySelector("#p-taste .skb[data-on]"), intro: SKB.intro }));
  if (a.sub !== "book") fail("sub not kept: " + a.sub);
  if (a.on || a.intro) fail("mounted on a hidden tab: " + JSON.stringify(a));
  await p.page.evaluate(() => goTab("taste")); await p.page.waitForTimeout(800);
  const b = await p.page.evaluate(() => ({ on: !!document.querySelector("#p-taste .skb[data-on]"), stage: !!document.querySelector("#p-taste .skb-stage") }));
  if (!b.on || !b.stage) fail("not mounted after going to Taste: " + JSON.stringify(b));
  await p.page.waitForTimeout(7000);
  const fold0 = await p.page.evaluate(() => SKB.fold);
  await p.page.setViewportSize({ width: fold0 ? 859 : 412, height: 847 }); await p.page.waitForTimeout(1500);
  const c = await p.page.evaluate(() => ({ fold: SKB.fold, cls: document.querySelector("#p-taste .skb").classList.contains("fold") }));
  if (c.fold === fold0 || c.cls !== c.fold) fail("fold change not re-laid: " + JSON.stringify(c));
  console.log(`  ${w}: hidden start ok, mounted on Taste, fold ${fold0} → ${c.fold}`);
  (p.reports || []).filter(r => r.kind !== "slow").forEach(r => fail("Nexi reported: " + JSON.stringify(r).slice(0, 200)));
};
