/* #409: opening a game page and swiping to the next game straight away (inside the flicker check's 750ms) was reported as
   "A game page flickers when it opens": the swipe fades the page under the finger to .4. Old build: reported; new: not.
     node tools/kit.mjs run tools/scenarios/flicker-swipe.mjs --widths 412,859
     git show HEAD~1:index.html > old.html && node tools/kit.mjs run tools/scenarios/flicker-swipe.mjs --file old.html */
export default async ({ p, w }) => {
  let flick = 0;
  for (let k = 0; k < 3; k++) {
    await p.page.evaluate(() => openFull("g" + (1 + Math.floor(Math.random() * 6))));
    await p.page.waitForTimeout(220);
    /* a quick flick that ends past the half-way point: the page under the finger fades to ~.55 before the 750ms are up */
    await p.swipe(p.w * 0.9, p.h * 0.55, p.w * 0.05, p.h * 0.56, 90, 5);
    await p.page.waitForTimeout(1500);
    await p.flush();
    flick = p.reports.filter(r => /flickers/.test(r.msg || "")).length;
    await p.step("close", `closeFull()`, 900);
  }
  const moved = await p.page.evaluate(() => typeof hubTurn !== "undefined" ? hubTurn : -1);
  console.log(`  ${w}px: swipes done (hubTurn ${moved}), flicker reports: ${flick}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return flick === 0 && !p.errors.length;
};
