/* v284: the app's small "you scrolled" jobs share one listener (onScrolled, core script) and their wait-until-still timers
   arm once per scroll instead of on every frame. Checks that the jobs still do theirs:
   1. window has one capture scroll listener of the app's (there were 10, plus the hint bubble's bubbling one)
   2. a real touch swipe on a screen's scroller reaches the jobs (bugLastIn, SCROLLED.last)
   3. while a scroll goes on, the design / stacked-layer / picture checks and the rest timer wait (each re-arms for the rest)
   4. the hint bubble shrinks while the page itself scrolls and grows back 550ms after the last scroll, not before
     node tools/kit.mjs run tools/scenarios/scroll-hub.mjs --widths 412,859 */
export default async ({ p, w }) => {
  let ok = true;
  const say = (good, what) => { console.log(`  ${good ? "✓" : "✗"} ${w}px: ${what}`); if (!good) ok = false; };
  await p.page.waitForTimeout(3000);

  /* 1 */
  const { result } = await p.cdp.send("Runtime.evaluate", { expression: "window" });
  const { listeners } = await p.cdp.send("DOMDebugger.getEventListeners", { objectId: result.objectId });
  const sc = listeners.filter(l => l.type === "scroll");
  say(sc.length === 1 && sc[0].useCapture && sc[0].passive, `window has ${sc.length} scroll listener(s)${sc.length ? " (" + sc.map(l => (l.useCapture ? "capture" : "bubble") + " line " + (l.lineNumber + 1)).join(", ") + ")" : ""}`);

  /* 2 */
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 2600);
  const t0 = await p.page.evaluate(() => performance.now());
  await p.swipe(p.w * 0.5, p.h * 0.8, p.w * 0.52, p.h * 0.3, 250, 12); await p.page.waitForTimeout(300);
  const seen = await p.page.evaluate(() => ({ last: SCROLLED.last, inp: bugLastIn, jobs: SCROLLED.fns.length }));
  say(seen.last > t0 && seen.inp >= seen.last - 1, `a swipe reached the shared listener (${seen.jobs} jobs; bugLastIn follows it)`);

  /* 3: a scroll 1ms ago, then each check is called as its timer would */
  const wait = await p.page.evaluate(async () => {
    const out = {}, before = DRAW.go;
    SCROLLED.last = performance.now(); await drawCheck(); out.draw = DRAW.go === before;
    SCROLLED.last = performance.now(); const sg = STACK.go; await stackCheck(); out.stack = STACK.go === sg;
    SCROLLED.last = performance.now(); const ps = PIC.seen.size; picCheck(); out.pic = PIC.seen.size === ps;
    SCROLLED.last = performance.now(); const ro = REST.on; restGo(); out.rest = REST.on === ro && !window.pnRest;
    /* and once it's still, they run: the picture check files its screen as looked at */
    SCROLLED.last = performance.now() - 5000; PIC.seen.clear(); const n = PIC.n; PIC.n = 0; picCheck(); out.picRan = PIC.seen.size > 0 || !bugOn() || !lagOk() || nxTouring(); PIC.n = n;
    return out; });
  say(wait.draw && wait.stack && wait.pic && wait.rest, `mid-scroll the checks wait: design ${wait.draw}, layers ${wait.stack}, pictures ${wait.pic}, rest ${wait.rest}`);
  say(wait.picRan, `once still, the picture check runs`);

  /* 4: a planted bubble, the page's own scroll events (the bubble only ever heard those) */
  const bub = await p.page.evaluate(async () => {
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    let b = document.getElementById("hintbub"); const planted = !b;
    if (planted) { b = document.createElement("div"); b.id = "hintbub"; document.body.appendChild(b); }
    const mini = () => { const x = document.getElementById("hintbub"); return !!x && x.classList.contains("mini"); };   /* a redraw may replace it */
    await sleep(700);   /* whatever scrolled the page before has settled */
    const out = { start: !mini() };
    const box = document.querySelector(".panel.on") || document.body; box.dispatchEvent(new Event("scroll"));
    out.boxScroll = !mini();   /* a box scrolling never shrank it */
    for (let i = 0; i < 6; i++) { document.dispatchEvent(new Event("scroll")); await sleep(200); }
    out.held = mini();          /* 1.2s of scrolling, every 200ms: still small */
    await sleep(250); out.notYet = mini();   /* 450ms after the last: still small */
    await sleep(350); out.back = !mini();    /* 800ms after: back */
    if (planted) b.remove();
    return out; });
  say(bub.start && bub.boxScroll && bub.held && bub.notYet && bub.back, `hint bubble: box scroll ignored ${bub.boxScroll}, small while scrolling ${bub.held}, still 450ms after ${bub.notYet}, back after 550ms ${bub.back}`);
  return ok;
};
