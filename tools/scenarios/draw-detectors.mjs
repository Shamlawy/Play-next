/* v305: Nexi's two new drawing checks catch their problem, and stay quiet without it.
   1. A url() background unpacking far more than its spot (the Queue's open card, unfolded, drew a 12 MP original in a
      2.9 MP spot): planted as a 12 MP own file whose resize "failed", in a 230px box on Home → "A 12.0 MP picture fills…".
   2. Many scroll-linked animations on one screen (Played had 54, each a GPU layer): 60 planted → "… move with the scroll".
   Also: the stutter report's drawing line counts that background (drawLoad).
     node tools/kit.mjs run tools/scenarios/draw-detectors.mjs --widths 412,859 [--file old.html] */
export default async ({ p, w }) => {
  let ok = true;
  const say = (good, what) => { console.log(`  ${good ? "✓" : "✗"} ${w}px: ${what}`); if (!good) ok = false; };
  const got = re => p.reports.filter(r => re.test(r.msg));
  await p.page.waitForTimeout(4000);
  await p.step("home", `closeFull(); goTab("home")`, 2600);
  await p.flush();
  say(!got(/MP picture fills|pictures on .* hold/).length && !got(/move with the scroll/).length, "nothing reported before the plants");
  /* 1 */
  await p.page.evaluate(() => { FIT.bad.add("fefw-key-art.jpg"); const d = document.createElement("div"); d.id = "plantbg";
    d.style.cssText = "width:230px;height:230px;background:url('fefw-key-art.jpg') center/cover"; document.querySelector(".panel.on").prepend(d);
    scrollTo(0, 0); });
  await p.page.waitForTimeout(800);
  await p.page.evaluate(() => { PIC.seen.clear(); PIC.n = 0; picCheck(); });
  await p.page.waitForTimeout(3500);
  const dl = await p.page.evaluate(() => drawLoad());
  await p.flush();
  const big = got(/MP picture fills/);
  say(big.length === 1 && /plantbg/.test(big[0].at || ""), `a 12 MP background in a small spot is reported (${big.map(r => r.msg.slice(0, 50) + " @ " + (r.at || "").slice(0, 70)).join("; ") || "none"})`);
  say(/MP shown/.test(dl) && +(/\(([\d.]+) MP decoded/.exec(dl) || [0, 0])[1] >= 12, `the stutter report's drawing line counts it: ${dl.slice(0, 90)}`);
  await p.page.evaluate(() => document.getElementById("plantbg").remove());
  /* 2 */
  await p.page.evaluate(() => { const s = document.createElement("style"); s.textContent = `.scrpx{height:6px;margin:2px 0;animation:scrpx linear both;animation-timeline:view()}@keyframes scrpx{from{opacity:.2}}`;
    document.head.appendChild(s); document.querySelector(".panel.on").insertAdjacentHTML("beforeend", Array.from({ length: 60 }, () => '<div class="scrpx"></div>').join("")); bugLayoutSoon(); });
  await p.page.waitForTimeout(4000);
  await p.flush();
  const sl = got(/move with the scroll/);
  say(sl.length === 1, `60 scroll-linked pieces are reported (${sl.map(r => r.msg.slice(0, 60) + " · " + (r.note || "")).join("; ") || "none"})`);
  return ok;
};
