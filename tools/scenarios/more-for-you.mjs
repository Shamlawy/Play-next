/* Home's "More for you": the two swipe stacks must show their cards once you scroll down to them (the owner, v284: an
   empty band where they should be, unfolded, Chrome 145+), and Insights' cards on screen are drawn. Scrolls with a real
   finger, then checks each is on screen with full opacity. v281's CSS reveal (timeline-trigger) failed it only in a Chrome
   that has scroll triggers, so run it in one too:
     CHROME=<Chrome for Testing 148> node tools/kit.mjs run tools/scenarios/more-for-you.mjs --widths 412,859 */
export default async ({ p, w, fail }) => {
  await p.step("home", `goTab("home")`, 2500);
  const trig = await p.page.evaluate(() => !!(window.CSS && CSS.supports("timeline-trigger: --t view()")));
  for (let i = 0; i < 12; i++) {
    const at = await p.page.evaluate(() => { const m = document.querySelector("#p-home .dxmore"); return m ? m.getBoundingClientRect().top / innerHeight : -1; });
    if (at < .4) break;
    await p.swipe(Math.round(w * .4), 700, Math.round(w * .4), 250);
    await p.page.waitForTimeout(500);
  }
  await p.page.waitForTimeout(1500);
  const r = await p.page.evaluate(() => {
    const m = document.querySelector("#p-home .dxmore");
    if (!m) return [{ bad: "no .dxmore" }];
    return [...m.children].map(k => {
      const b = k.getBoundingClientRect(), op = +getComputedStyle(k).opacity;
      const an = k.getAnimations().map(a => `${a.animationName || "?"}:${a.playState}@${Math.round(a.currentTime || 0)}`);
      const onScreen = b.bottom > 0 && b.top < innerHeight && b.height > 100;
      return { cls: k.className, top: Math.round(b.top), h: Math.round(b.height), op, an, bad: !onScreen ? "off screen" : op < .99 ? "hidden (opacity " + op + ")" : "" };
    });
  });
  console.log(`${w}px  browser has scroll triggers: ${trig}`);
  for (const x of r) console.log("  ", JSON.stringify(x));
  const bad = r.filter(x => x.bad);
  if (r.length < 2) fail(`More for you has ${r.length} stack(s), expected 2`);
  for (const x of bad) fail(`More for you: ${x.cls} ${x.bad}`);
  /* the same reveal on Insights' cards (.ix-rv): every one on screen after scrolling down is drawn */
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 2500);
  let hid = [];
  for (let i = 0; i < 6; i++) {
    await p.swipe(Math.round(w * .4), 700, Math.round(w * .4), 250); await p.page.waitForTimeout(1200);
    hid = hid.concat(await p.page.evaluate(() => [...document.querySelectorAll(".panel.on .ix-rv")].filter(el => { const b = el.getBoundingClientRect();
      return b.top >= 0 && b.bottom <= innerHeight && b.height > 40 && +getComputedStyle(el).opacity < .99; }).map(el => el.dataset.ix || el.className)));
  }
  console.log(`  Insights: ${hid.length ? "hidden on screen: " + [...new Set(hid)].join(", ") : "every card on screen drawn"}`);
  if (hid.length) fail(`Insights cards stayed hidden: ${[...new Set(hid)].join(", ")}`);
  return r.length >= 2 && !bad.length && !hid.length;
};
