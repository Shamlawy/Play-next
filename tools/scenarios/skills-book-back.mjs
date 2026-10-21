/* v323 (the owner: "the book end should have a cover and closes when I scroll to the end"): past the last page the book shuts
   on its back cover (unfolded: the back of the last leaf, the shut book slides to the middle; folded: the last page, no
   turning past it), by real swipes. Pictures: tools/.cache/skb-back-<w>.png
     node tools/kit.mjs run tools/scenarios/skills-book-back.mjs --widths 412,859 */
export default async ({ p, w, seed, fail }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99, sound: false, bookMusic: false })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { const P = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).slice(-2);
    const book = {}; P.forEach(x => { book[bookNorm(S.games.find(g => g.id === x.id).title)] = { skills: [{ ic: "time", n: "Patience", lv: 2, txt: "A made-up moment." }] }; });
    localStorage.setItem("gq:book", JSON.stringify(book)); bookStore = null; document.documentElement.classList.add("still"); });
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 1500);
  await p.page.evaluate(() => skbSkip());
  const st = () => p.page.evaluate(() => { const r = SKB.el, bk = r.querySelector(".skb-book"), vis = el => el && el.getClientRects().length && el.getBoundingClientRect().width > 0;
    const back = [...r.querySelectorAll(".skb-pg .skb-cv.bc")].find(vis), sr = r.querySelector(".skb-stage").getBoundingClientRect(), b = back ? back.getBoundingClientRect() : null;
    return { at: SKB.at, n: SKB.L.length, fold: SKB.fold, back: !!back, tf: bk.style.transform, off: b ? Math.round((b.left + b.width / 2) - (sr.left + sr.width / 2)) : null, turns: SKB.turns.length }; });
  const box = await p.page.evaluate(() => { const b = SKB.el.querySelector(".skb-book").getBoundingClientRect(), x0 = Math.max(b.left, 8), x1 = Math.min(b.right, innerWidth - 8); return { x: x0, y: b.top, w: x1 - x0, h: Math.min(b.height, innerHeight - 120 - b.top) };   /* the part on screen (folded only the right page shows) */ });
  /* real swipes to the end, and one more */
  let s = await st();
  for (let i = 0; i < s.n + 2; i++) {
    await p.swipe(box.x + box.w * .85, box.y + box.h * .5, box.x + box.w * .15, box.y + box.h * .5);
    await p.page.waitForTimeout(950);
  }
  s = await st();
  const end = s.fold ? s.n - 1 : s.n;
  if (s.at !== end) fail(`after swiping to the end, at ${s.at} of ${s.n} (expected ${end})`);
  if (!s.back) fail(`no back cover showing at the end (${JSON.stringify(s)})`);
  else if (Math.abs(s.off) > 12) fail(`the shut book isn't centred on its back cover (off by ${s.off}px)`);
  if (!s.fold && !/translateX\(25%\)/.test(s.tf)) fail(`the shut book didn't slide to the middle (${s.tf})`);
  await p.page.evaluate(() => SKB.el.scrollIntoView({ block: "center", behavior: "instant" })); await p.page.waitForTimeout(300);
  await p.page.screenshot({ path: `tools/.cache/skb-back-${w}.png` });
  /* and back: one swipe the other way opens it on the last pages again */
  await p.swipe(box.x + box.w * .15, box.y + box.h * .5, box.x + box.w * .85, box.y + box.h * .5); await p.page.waitForTimeout(1100);
  const t = await st();
  if (t.at !== end - 1 || t.back && !t.fold) fail(`swiping back from the back cover didn't open the book (${JSON.stringify(t)})`);
  console.log(`  ${w}: end at ${s.at}/${s.n}, back cover ${s.back ? "showing" : "missing"}, centred ${s.off}px off, ${s.fold ? "folded" : s.tf}`);
};
