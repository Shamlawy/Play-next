/* v283: the three problem checks touched by the scroll-stutter fix still catch their problem, with real touch:
   1. "Tapped something twice and nothing happened": a dead button tapped twice is reported; the Queue/Library pill isn't.
   2. "Part of the screen stayed blank while scrolling": a row on screen that says it's skipped (a planted
      contentvisibilityautostatechange) is reported; one in a hidden screen is passed over without measuring.
   3. M11: a scroll stutter report says what the long frames spent their time on, and the slow handler (a "named" function
      run through page.evaluate shows up as (anonymous) ← DOMWindow.onscroll; the app's own functions keep their names).
     node tools/kit.mjs run tools/scenarios/stutter-detectors.mjs --widths 412,859 */
export default async ({ p, w }) => {
  let ok = true;
  const say = (good, what) => { console.log(`  ${good ? "✓" : "✗"} ${w}px: ${what}`); if (!good) ok = false; };
  const got = re => p.reports.filter(r => re.test(r.msg));
  await p.page.waitForTimeout(6000);   /* the lag checks start 8s after opening */

  /* 1. stuck taps, first: once Nexi reacts to a report the screen keeps changing, and on Queue something you can see changes every second */
  await p.step("month", `goTab("month")`, 2600);
  const at = await p.page.evaluate(() => { const b = document.createElement("button"); b.className = "btn"; b.id = "plantdead"; b.textContent = "Dead";
    b.style.cssText = "position:fixed;left:40%;top:45%;z-index:9999;padding:14px 22px"; document.body.appendChild(b);
    const q = b.getBoundingClientRect(); return [q.x + q.width / 2, q.y + q.height / 2]; });
  await p.tap(...at); await p.page.waitForTimeout(1100); await p.tap(...at); await p.page.waitForTimeout(1100);
  await p.page.evaluate(() => document.getElementById("plantdead").remove());
  const pill = async v => { const r = await p.page.evaluate(v => { const b = document.querySelector(`#vpill button[data-view="${v}"]`); if (!b) return null; const q = b.getBoundingClientRect(); return [q.x + q.width / 2, q.y + q.height / 2]; }, v); if (r) { await p.tap(...r); await p.page.waitForTimeout(1100); } return !!r; };
  await p.step("queue", `goTab("queue")`, 2600);
  const hasPill = await pill("lib"); await pill("queue"); await pill("lib");
  await p.flush();
  const stuck = got(/Tapped something twice/);
  say(stuck.length === 1 && /plantdead/.test(stuck[0].at), `a dead button tapped twice is reported, the pill isn't${hasPill ? "" : " (no pill here)"} (${stuck.map(r => r.at.slice(0, 60)).join("; ")})`);

  /* 2. blank while scrolling */
  await p.step("month", `goTab("month")`, 2600);
  await p.page.evaluate(() => {
    const fake = el => el.dispatchEvent(new ContentVisibilityAutoStateChangeEvent("contentvisibilityautostatechange", { skipped: true, bubbles: true }));
    /* something big in the middle of the screen (a Month day cell, a row...) */
    let vis = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
    while (vis && vis.parentElement && !vis.parentElement.classList.contains("panel") && vis.getBoundingClientRect().width < 120) vis = vis.parentElement;
    if (vis && vis.closest(".panel.on")) { vis.id = "plantvis"; fake(vis); }
    document.querySelectorAll("#p-lib .row, #p-played .row").forEach(fake);
  });
  for (let k = 0; k < 4; k++) { await p.swipe(p.w * 0.5, p.h * 0.6, p.w * 0.5, p.h * 0.55, 400, 20); await p.page.waitForTimeout(60); }
  await p.flush();
  const blank = got(/stayed blank while scrolling/);
  say(blank.length === 1 && /plantvis/.test(blank[0].at), `a skipped row on screen is reported (${blank.length}: ${blank.map(r => r.at.slice(0, 60)).join("; ")})`);

  /* 3. M11: the scroll stutter report says why */
  await p.step("month", `goTab("month")`, 2600);
  const lo = await p.page.evaluate(() => typeof LAG_LOAF !== "undefined" && LAG_LOAF);
  await p.page.evaluate(() => { let last = 0; addEventListener("scroll", function slowScrollPlant() { const t = performance.now(); if (t - last < 150) return; last = t; while (performance.now() - t < 130); }, { capture: true, passive: true }); });
  /* both ways, so it scrolls wherever the screen was left */
  for (let k = 0; k < 4; k++) { const [a, b] = k % 2 ? [0.3, 0.8] : [0.8, 0.3]; await p.swipe(p.w * 0.5, p.h * a, p.w * 0.5, p.h * b, 500, 25); await p.page.waitForTimeout(80); }
  await p.page.waitForTimeout(800); await p.flush();
  const sc = got(/Scrolling stutters/);
  if (!lo) say(sc.length >= 1, `scroll stutter reported (no long-animation-frame here: no split)`);
  else say(sc.length >= 1 && /script \d+ms/.test(sc[0].stack) && /longest script 1\d\dms: .*onscroll/.test(sc[0].stack), `the scroll stutter report says why: ${(sc[0] && sc[0].stack || "none").replace(/\n/g, " | ")}`);
  return ok;
};
