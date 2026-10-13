/* #174 (the friend, iPad Safari, v228): "I was in the queue page and Nexi disappeared while the rail was open".
   With the rail open (wide screens) Nexi sits at its foot (html.nxseat). After every common action he must still be
   there: shown, in the rail, on screen, not faded out. (On Home he steps out on purpose: Home has its own Nexi, v274.)
     node tools/kit.mjs run tools/scenarios/rail-nexi.mjs --widths 859,1080 */
export default async ({ p, w }) => {
  if (w < 620) { console.log(`  ✓ ${w}px: no rail at this width`); return true; }
  const bad = [];
  const look = () => p.page.evaluate(() => {
    const H = document.documentElement, b = document.getElementById("pnbub"), r = document.getElementById("hrail");
    if (!H.classList.contains("pnrail") || H.classList.contains("railhide")) return { skip: "rail hidden" };
    if (H.classList.contains("onhome")) return { skip: "home" };
    if (!H.classList.contains("nxseat")) return { bad: "not seated with the rail open" };
    const cs = getComputedStyle(b), br = b.getBoundingClientRect(), rr = r.getBoundingClientRect();
    let op = 1; for (let e = b; e && e.nodeType === 1; e = e.parentElement) op *= +getComputedStyle(e).opacity;
    if (cs.display === "none" || cs.visibility === "hidden") return { bad: `hidden (${cs.display}/${cs.visibility})` };
    if (op < .5) return { bad: "faded out (opacity " + op.toFixed(2) + ")" };
    if (br.width < 20) return { bad: "no size" };
    if (br.bottom < 0 || br.top > innerHeight || br.right < 0 || br.left > innerWidth) return { bad: `off screen at ${br.left | 0},${br.top | 0}` };
    const cx = br.left + br.width / 2, cy = br.top + br.height / 2;
    if (cx < rr.left || cx > rr.right || cy < rr.top || cy > rr.bottom) return { bad: `not in the rail (at ${cx | 0},${cy | 0}, rail ${rr.left | 0}-${rr.right | 0} × ${rr.top | 0}-${rr.bottom | 0})` };
    const hit = document.elementFromPoint(cx, cy);
    if (hit && !hit.closest("#pnbub") && hit.id !== "ssjcatch") return { bad: "covered by " + (hit.id ? "#" + hit.id : hit.className || hit.tagName) };
    return { ok: 1 };
  });
  const check = async what => { await p.page.waitForTimeout(900); const r = await look(); if (r.bad) bad.push(`${what}: ${r.bad}`); };
  await p.page.evaluate(() => { S.railHide = false; S.nxSeatOff = false; }); await p.page.waitForTimeout(1200);
  for (const t of ["queue", "games", "plan", "taste", "lib", "played", "add"]) { await p.step(t, `goTab(${JSON.stringify(t)})`, 600); await check("tab " + t); }
  await p.step("queue", `goTab("queue")`, 600);
  await p.scroll(600); await check("queue scrolled");
  await p.page.evaluate(() => { const g = S.games[0]; if (g) openFull(g.id); }); await check("game page");
  await p.page.evaluate(() => closeFull()); await check("back from the game");
  await p.page.evaluate(() => openSettings()); await check("Settings open");
  await p.page.evaluate(() => closeEdit()); await check("Settings closed");
  await p.page.evaluate(() => pnBubSay(["test line", ""])); await p.page.waitForTimeout(2500); await check("after he spoke");
  await p.page.evaluate(() => { Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true }); document.dispatchEvent(new Event("visibilitychange"));
    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true }); document.dispatchEvent(new Event("visibilitychange")); dispatchEvent(new Event("pageshow")); });
  await check("back in the app");
  /* the iPad turned: portrait (810 wide) and back */
  const vp = p.page.viewportSize();
  await p.page.setViewportSize({ width: vp.height, height: vp.width }); await check("turned");
  await p.page.setViewportSize(vp); await check("turned back");
  /* the rail hidden and shown again (» / a tap on him / floating Nexi) */
  await p.page.evaluate(() => railHideNow()); await p.page.waitForTimeout(1500);
  await p.page.evaluate(() => { document.documentElement.classList.remove("railhide"); S.railHide = false; }); await check("rail shown again");
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "Nexi stays in the open rail through tabs, game, Settings, speaking, coming back, turning, hide/show" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
