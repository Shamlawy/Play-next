/* v316: Insights "Shattered Sky". With the seed's ranked games: the shattered hero (1-3 shards, the type's name on the slab),
   the tarot table under it as Your arcana, the S-tier shelf (one tile per S-tier game, a side scroller), Your sky (2-5 genre
   diamonds, dashed untried ones) and the backlog dates (three tickets, one lit). Nothing past the screen edge outside the
   shelf's scroller, short words whole, and Customise › Top of Insights › Classic brings the old top back.
     node tools/kit.mjs run tools/scenarios/insights-shattered.mjs --widths 412,859,360      (SHOTS=dir saves pictures) */
import fs from "node:fs";
import path from "node:path";
export default async ({ p, w, seed, fail }) => {
  const bad = [], shots = process.env.SHOTS;
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } try { if (typeof pnPuEnd === "function") pnPuEnd(); } catch (e) {} });
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 2500);
  /* show every piece at once, as if it had scrolled in */
  await p.page.evaluate(() => document.querySelectorAll("#p-taste .ix-rv").forEach(x => x.classList.add("in", "ix-now")));
  await p.page.waitForTimeout(400);
  const got = await p.page.evaluate(() => {
    const q = s => document.querySelector("#p-taste " + s), W = innerWidth;
    const out = { hero: !!q(".shs-hero"), shards: document.querySelectorAll("#p-taste .shs-sh").length, name: (q(".shs-name") || {}).textContent || "",
      arc: !!q(".ix-hero.shs-arc"), tarot: !!q(".shs-arc .dzc-table"), tiles: document.querySelectorAll("#p-taste .shs-tile").length,
      stars: document.querySelectorAll("#p-taste .shs-st").length, un: document.querySelectorAll("#p-taste .shs-st.shs-un").length,
      tix: document.querySelectorAll("#p-taste .shs-tk").length, lit: document.querySelectorAll("#p-taste .shs-tk.shs-on").length, edge: [], broken: [] };
    const sTier = rankedGames().filter(g => tierOf(rankTable().sc[g.id]) === "S").length; out.sTier = sTier;
    /* past the screen's edge (the shelf's own scroller excused) */
    document.querySelectorAll("#p-taste .shs-hero, #p-taste .shs-say, #p-taste .shs-skyc, #p-taste .shs-bl, #p-taste .shs-shelf, #p-taste .shs-lb, #p-taste .shs-tk").forEach(e => {
      const r = e.getBoundingClientRect(); if (r.width && (r.left < -1 || r.right > W + 1)) out.edge.push(e.className + " " + Math.round(r.left) + ".." + Math.round(r.right)); });
    /* short words in one line */
    document.querySelectorAll("#p-taste .shs-tk b, #p-taste .shs-tk span, #p-taste .shs-stamp, #p-taste .shs-lb small").forEach(e => {
      const lh = parseFloat(getComputedStyle(e).lineHeight) || 16; if (e.getBoundingClientRect().height > lh * 1.6) out.broken.push(e.textContent); });
    /* the sky's labels stay inside the sky */
    const sky = q(".shs-sky"); if (sky) { const sr = sky.getBoundingClientRect();
      sky.querySelectorAll(".shs-lb").forEach(l => { const r = l.getBoundingClientRect(); if (r.left < sr.left - 1 || r.right > sr.right + 1 || r.bottom > sr.bottom + 1) out.edge.push("label " + l.textContent); }); }
    return out;
  });
  if (!got.hero) bad.push("no shattered hero");
  if (got.shards < 1 || got.shards > 3) bad.push(`${got.shards} shards`);
  if (!got.name.trim()) bad.push("no type name on the hero");
  if (!got.arc) bad.push("the tarot table / medal isn't under the hero");
  if (got.sTier && got.tiles !== got.sTier) bad.push(`${got.tiles} shelf tiles for ${got.sTier} S-tier games`);
  if (got.stars && got.stars - got.un < 2) bad.push("fewer than 2 genre stars");
  if (got.tix && (got.tix !== 3 || got.lit !== 1)) bad.push(`backlog: ${got.tix} tickets, ${got.lit} lit`);
  got.edge.forEach(x => bad.push("past the edge: " + x));
  got.broken.forEach(x => bad.push("broken over lines: " + x));
  if (shots) {
    fs.mkdirSync(shots, { recursive: true });
    const snap = async (sel, name) => { const el = await p.page.$("#p-taste " + sel); if (el) { await el.scrollIntoViewIfNeeded(); await p.page.waitForTimeout(250); await p.page.screenshot({ path: path.join(shots, `shs-${w}-${name}.png`) }); } };
    await snap(".shs-hero", "1-hero"); await snap(".shs-shelf", "2-shelf"); await snap(".shs-skyc", "3-sky"); await snap(".shs-bl", "4-backlog");
  }
  /* Classic brings the old top back */
  await p.page.evaluate(() => { S.dzX = Object.assign({}, S.dzX, { shs: 1 }); renderTaste(); });
  await p.page.waitForTimeout(300);
  const classic = await p.page.evaluate(() => ({ hero: !!document.querySelector("#p-taste .shs-hero"), old: !!document.querySelector("#p-taste .ix-hero h2") }));
  if (classic.hero || !classic.old) bad.push("Classic didn't bring the old top back");
  await p.page.evaluate(() => { const X = Object.assign({}, S.dzX); delete X.shs; S.dzX = X; renderTaste(); });
  console.log(`  ${w}: ${got.shards} shards, "${got.name}", shelf ${got.tiles}/${got.sTier}, ${got.stars - got.un} stars + ${got.un} untried, ${got.tix} tickets`);
  bad.forEach(m => fail(m));
};
