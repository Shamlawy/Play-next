/* v288 Nexi's tea break (the owner: a Spy x Family ending where they never get to drink their tea).
   1. a forced try: the cup appears beside Nexi (bubble unfolded, Home's face folded), lifts, something interrupts,
      he says a line, and the cup + every extra are gone afterwards (no fill left behind)
   2. your touch mid-lift is the interruption ("user"), with no wait for the planned one
   3. it starts by itself after 25s still, and not again within 30 min; never with Tea breaks off or Motion off
   4. only transform/opacity animate while it plays
     node tools/kit.mjs run tools/scenarios/tea-break.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  await p.page.evaluate(() => { S.bubDay = new Date().toLocaleDateString("en-CA"); S.nxPartyDay = S.bubDay; S.bubMet = 1; S.railHide = true; saveNow(); localStorage.removeItem("gq:tea"); });
  await p.page.reload(); await p.page.waitForTimeout(1500);
  await p.step("home", `goTab("home")`, 1500);
  const watch = () => p.page.evaluate(() => { window._tea = { said: [], props: new Set(), kinds: [] };
    if (!window._teaHooked) { window._teaHooked = 1; extend("teaSay", { before(l) { window._tea.said.push(l[0]); } }); extend("teaFail", { before(k) { window._tea.kinds.push(k); } }); }
    window._teaPoll = setInterval(() => { for (const a of document.getAnimations()) { const k = a.effect && a.effect.getKeyframes ? a.effect.getKeyframes() : [];
      const t = a.effect && a.effect.target; if (!t || !t.closest || !t.closest("#pnbub, .nxh-face")) continue;
      k.forEach(f => Object.keys(f).forEach(x => { if (!["offset", "easing", "composite", "computedOffset"].includes(x)) window._tea.props.add(x); })); } }, 50); });
  const done = () => p.page.evaluate(() => { clearInterval(window._teaPoll); return { said: window._tea.said, props: [...window._tea.props], cups: document.querySelectorAll(".nxtea, .nxtea-fx").length,
    on: TEA.on, cls: document.querySelectorAll(".nxtea-sip, .nxtea-oh").length }; });
  /* 1 */
  await watch();
  const host = await p.page.evaluate(() => { const ok = teaTry(true); const c = document.querySelector(".nxtea"); return ok && c ? c.parentElement.id || c.parentElement.className : null; });
  if (!host) bad.push("forced try showed no cup");
  await p.page.waitForTimeout(1500);
  const mid = await p.page.evaluate(() => !!document.querySelector(".nxtea"));
  if (!mid) bad.push("cup gone before the lift");
  await p.page.waitForTimeout(5500);
  let r = await done();
  console.log(`  ${w}px: on ${host}, said: ${r.said.join(" / ") || "-"}; animated: ${r.props.join(",")}`);
  if (!r.said.length) bad.push("no line after the interruption");
  if (r.cups || r.on || r.cls) bad.push(`left behind: ${r.cups} cup/fx, on ${r.on}, ${r.cls} classes`);
  if (r.props.some(x => !["transform", "opacity", "translate", "rotate", "scale"].includes(x))) bad.push("animated more than transform/opacity: " + r.props.join(","));
  /* 2 */
  await watch();
  await p.page.evaluate(() => teaTry(true)); await p.page.waitForTimeout(1600);
  /* touch Nexi's own speech box / an empty spot near him: something that doesn't change the screen */
  const at = await p.page.evaluate(() => { const e = document.querySelector("#pnbub.face") && document.getElementById("pnbub").getClientRects().length ? null : document.querySelector("#p-home .nxh-say");
    if (!e) return [Math.round(innerWidth / 2), 8]; const r = e.getBoundingClientRect(); return [r.right - 6, r.bottom - 4]; });
  await p.tap(at[0], at[1]);
  await p.page.waitForTimeout(150);
  const k = await p.page.evaluate(() => window._tea.kinds.join(","));
  if (k !== "user") bad.push("a touch mid-lift didn't interrupt (kinds " + k + ")");
  await p.page.waitForTimeout(4000); r = await done();
  if (!r.said.some(s => /touched|moved|scrolled|didn't want/.test(s))) bad.push("no 'you startled me' line: " + r.said.join(" / "));
  if (r.cups || r.on) bad.push("left behind after a touch");
  /* 3 */
  await p.step("home", `goTab("home"); localStorage.removeItem("gq:tea")`, 500);
  await p.page.waitForFunction(() => performance.now() > 26000, null, { timeout: 30000 });   /* never in the first 25s after opening */
  await p.page.evaluate(() => { bugLastIn = performance.now() - 60000; TEA.nav = 0; });
  await p.page.waitForTimeout(6800);
  const auto = await p.page.evaluate(() => TEA.on || !!teaBook().t);
  if (!auto) console.log("  debug:", await p.page.evaluate(() => JSON.stringify({ on: TEA.on, off: S.teaOff, hid: document.hidden, tour: nxTouring(), now: performance.now(), q: performance.now() - Math.max(bugLastIn, TEA.nav), host: !!teaHost(), open: !!document.querySelector("#sheet.on, #diary.on, #pnbubmsg.on"), tab: curTab() })));
  if (!auto) bad.push("didn't start by itself after a still minute");
  await p.page.waitForTimeout(4000);
  await p.page.evaluate(() => { bugLastIn = performance.now() - 60000; TEA.nav = 0; });
  await p.page.waitForTimeout(6800);
  const again = await p.page.evaluate(() => TEA.on);
  if (again) bad.push("started again within 30 minutes");
  for (const [name, js] of [["Tea breaks off", "S.teaOff = true"], ["Motion off", "S.teaOff = false; S.motion = false; applyLook()"]]) {
    await p.page.evaluate(js => { localStorage.removeItem("gq:tea"); eval(js); bugLastIn = performance.now() - 60000; TEA.nav = 0; }, js);
    await p.page.waitForTimeout(6800);
    if (await p.page.evaluate(() => TEA.on || !!teaBook().t)) bad.push("started with " + name);
  }
  await p.page.evaluate(() => { S.motion = true; applyLook(); });
  if (bad.length) { bad.forEach(b => console.log("  ✗ " + w + "px: " + b)); return false; }
  console.log(`  ✓ ${w}px: tea break works, interrupts, cleans up, waits, and stays off when switched off`);
  return true;
};
