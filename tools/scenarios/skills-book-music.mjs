/* v321: the skills book's game music (PS5 style). With the fake helper's made-up tracks: landing on a game's pages, its
   clip plays (a gain going up, a source running) and the chip on the picture page names the track; a tap on the chip plays
   the next track and doesn't turn the page; the contents is quiet; leaving the tab stops it; Sounds off or the book's own
   music switch off = no music; the switch is in Settings › Sound and reactions.
     node tools/kit.mjs run tools/scenarios/skills-book-music.mjs --widths 412,859 */
export default async ({ p, w, seed, fail }) => {
  const bad = [];
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99, sound: true })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { const P = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).slice(-2);
    const sk = n => ({ ic: "time", n, lv: 2, gain: "Better at " + n.toLowerCase(), txt: "A made-up moment.", mind: "A made-up idea.", life: "A made-up change.", tr: "Try a made-up thing." });
    const book = {}; P.forEach(x => { book[bookNorm(S.games.find(g => g.id === x.id).title)] = { skills: [sk("Prioritising"), sk("Patience")] }; });
    localStorage.setItem("gq:book", JSON.stringify(book)); localStorage.removeItem("gq:ost"); bookStore = null; document.documentElement.classList.add("still"); });
  /* a real touch first: the app plays sound only after one (browsers allow audio after a gesture) */
  const box = await p.page.evaluate(() => ({ w: innerWidth, h: innerHeight }));
  await p.tap(box.w / 2, 40);
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 2500);
  const st = () => p.page.evaluate(() => { const c = document.querySelector("#p-taste .skb-pg [data-ost]:not([hidden])");
    return { g: SKB_MUS.g, land: SKB.G && SKB.G(SKB.at) ? SKB.G(SKB.at).g.id : null, at: SKB.at, srcs: SKB_MUS.srcs.length, gain: SKB_MUS.out ? SKB_MUS.out.gain.value : 0,
      chip: c ? c.textContent.trim() : "", ac: typeof AC !== "undefined" && AC ? AC.state : "none" }; });
  let a = await st();
  if (!a.land) bad.push("the book didn't land on a game");
  else if (a.g !== a.land || !a.srcs) bad.push(`no music on the landed game's pages (${JSON.stringify(a)})`);
  else if (a.chip !== "Main Theme") bad.push(`the chip says "${a.chip}", expected the first track`);
  await p.page.waitForTimeout(1500);
  if ((await st()).gain <= .05) bad.push("the music didn't fade in");
  /* the chip shares no corner with the page-number pill (a picture page sits on the left unfolded, so its pill is top-left and the chip top-right; folded the pill stays at the foot) */
  const hit = await p.page.evaluate(() => [...document.querySelectorAll("#p-taste .skb-art [data-ost]:not([hidden])")].filter(c => c.getClientRects().length).map(c => {
    const f = c.closest(".skb-art").parentElement.querySelector(":scope > .skb-fo"); if (!f) return null;
    const a = c.getBoundingClientRect(), b = f.getBoundingClientRect();
    return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom ? `${Math.round(a.left)},${Math.round(a.top)}` : null; }).filter(Boolean));
  if (hit.length) bad.push(`the page number sits on the music chip (${hit.join(" ")})`);
  /* the chip: another track, same page */
  const cb = await p.page.evaluate(() => { const c = document.querySelector("#p-taste .skb-pg [data-ost]:not([hidden])"); if (!c) return null; const b = c.getBoundingClientRect(); return { x: b.left + b.width / 2, y: b.top + b.height / 2 }; });
  if (!cb) bad.push("no music chip on the picture page");
  else { await p.tap(cb.x, cb.y); await p.page.waitForTimeout(1500); const b = await st();
    if (b.at !== a.at) bad.push(`a tap on the chip turned the page (${a.at} → ${b.at})`);
    if (b.chip !== "Harbour at Dusk") bad.push(`after a tap on the chip it says "${b.chip}", expected the next track`);
    /* two quick taps: the chip stays while the next track loads, so the second can't turn the page */
    await p.tap(cb.x, cb.y); await p.page.waitForTimeout(120); await p.tap(cb.x, cb.y); await p.page.waitForTimeout(1500);
    if ((await st()).at !== a.at) bad.push("two quick taps on the chip turned the page"); }
  /* the contents is quiet; back on the game it plays again */
  await p.page.evaluate(() => { SKB.at = 1; skbRest(); }); await p.page.waitForTimeout(300);
  if ((await st()).g) bad.push("music kept playing on the contents");
  await p.page.evaluate(() => { SKB.at = SKB.land; skbRest(); }); await p.page.waitForTimeout(800);
  if (!(await st()).srcs) bad.push("back on the game's pages, no music");
  /* leaving the tab with a real tap on the tab bar (it paints the tab without goTab) */
  const hb = await p.page.evaluate(() => { const b = [...document.querySelectorAll('[data-tab="home"]')].find(x => x.getClientRects().length && x.getBoundingClientRect().width);
    if (!b) return null; const r = b.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; });
  /* unfolded the rail may be tucked away: then a click on the same button (the same handler, still no goTab) */
  if (hb) await p.tap(hb.x, hb.y); else await p.page.evaluate(() => document.querySelector('nav button[data-tab="home"]').click());
  await p.page.waitForTimeout(500);
  if (await p.page.evaluate(() => document.querySelector("#p-taste").classList.contains("on"))) bad.push("tapping Home didn't leave the book");
  if ((await st()).g || (await p.page.evaluate(() => SKB_MUS.srcs.length))) bad.push("music kept playing after tapping Home on the tab bar");
  await p.page.evaluate(() => { goTab("taste"); goSub("taste", "insights"); goSub("taste", "book"); }); await p.page.waitForTimeout(1500);
  if (!(await st()).srcs) bad.push("back in the book, no music");
  /* Settings over the book: quiet; closed again: it comes back */
  await p.page.evaluate(() => openSettings("app")); await p.page.waitForTimeout(500);
  if ((await st()).g) bad.push("music kept playing under Settings");
  await p.page.evaluate(() => closeEdit()); await p.page.waitForTimeout(900);
  if (!(await st()).srcs) bad.push("closing Settings didn't bring the music back");
  /* the book's own switch, then Sounds */
  for (const [k, val, what] of [["bookMusic", false, "the book's music switch off"], ["sound", false, "Sounds off"]]) {
    await p.page.evaluate(([k, val]) => { S.bookMusic = true; S.sound = true; S[k] = val; goTab("taste"); goSub("taste", "insights"); goSub("taste", "book"); }, [k, val]);
    await p.page.waitForTimeout(1500);
    if ((await st()).g) bad.push(`music played with ${what}`);
  }
  await p.page.evaluate(() => { S.sound = true; S.bookMusic = true; openSettings("app"); }); await p.page.waitForTimeout(600);
  if (!(await p.page.evaluate(() => !!document.querySelector("#s-bkmus")))) bad.push("no Game music switch in Settings");
  console.log(`  ${w}: landed on ${a.land}, track "${a.chip}", ${a.srcs} source(s), audio ${a.ac}`);
  (p.reports || []).filter(r => r.kind !== "slow").forEach(r => bad.push("Nexi reported: " + JSON.stringify(r).slice(0, 300)));
  bad.forEach(m => fail(m));
};
