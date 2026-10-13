/* #127 (the owner, game page unfolded): "swiping sideways but stopping halfway, with half of the next card showing, the
   up/down scrolling isn't locked, so it does both and lags". A drag picks one direction: once it's going sideways the page
   doesn't scroll up or down, and once it's scrolling the game doesn't slide sideways.
     node tools/kit.mjs run tools/scenarios/swipe-axis.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  const pt = (x, y) => [{ x: Math.round(x), y: Math.round(y), radiusX: 4, radiusY: 4, force: 1, id: 1 }];
  const T = (type, x, y) => p.cdp.send("Input.dispatchTouchEvent", { type, touchPoints: type === "touchEnd" ? [] : pt(x, y) });
  /* the open game's own page (the one in the middle while a drag shows its neighbours) */
  const state = () => p.page.evaluate(() => {
    const hubs = [...document.querySelectorAll("#full.on .hub")], h = hubs.find(x => { const r = x.getBoundingClientRect(); return Math.abs(r.left + r.width / 2 - innerWidth / 2) < innerWidth / 3; }) || hubs[0];
    const all = [...document.querySelectorAll("#full.on, #full.on *")].filter(e => e.scrollTop > 0).map(e => (e.id ? "#" + e.id : "." + String(e.className).split(" ")[0]) + ":" + e.scrollTop);
    const t = h ? getComputedStyle(h).transform : "none";
    return { top: (h ? h.scrollTop : 0) + document.scrollingElement.scrollTop, dx: t && t !== "none" ? Math.abs(new DOMMatrix(t).m41) : 0, at: typeof hubAt !== "undefined" ? hubAt : null, all: all.join(" ") }; });
  const drag = async path => { await T("touchStart", ...path[0]); for (const q of path.slice(1)) { await p.page.waitForTimeout(16); await T("touchMove", ...q); } };
  await p.step("open", `openFull("g1")`, 1600);
  await p.scroll(300); await p.page.waitForTimeout(400);
  /* 1. sideways first, halfway, then the finger wanders up and down */
  const x0 = p.w * .8, y0 = p.h * .5, path = [];
  for (let i = 0; i <= 10; i++) path.push([x0 - i * p.w * .035, y0 + i * 2]);
  for (let i = 1; i <= 12; i++) path.push([x0 - p.w * .35 - i * 2, y0 + 20 - i * 18]);
  const s0 = await state();
  await drag(path);
  const s1 = await state();
  await T("touchEnd"); await p.page.waitForTimeout(1200);
  if (s1.top !== s0.top) bad.push(`sideways drag scrolled the page ${s0.top}→${s1.top} (${s0.all} → ${s1.all})`);
  if (s1.dx < 20) bad.push(`sideways drag didn't move the game (dx ${s1.dx | 0})`);
  /* 2. up/down first, then sideways */
  const s2 = await state(), path2 = [];
  for (let i = 0; i <= 10; i++) path2.push([p.w * .5, p.h * .7 - i * 14]);
  for (let i = 1; i <= 12; i++) path2.push([p.w * .5 - i * p.w * .03, p.h * .7 - 140 - i]);
  await drag(path2);
  const s3 = await state();
  await T("touchEnd"); await p.page.waitForTimeout(1200);
  if (s3.dx > 8) bad.push(`scrolling drag slid the game sideways (dx ${s3.dx | 0})`);
  if (s3.top === s2.top) bad.push("scrolling drag didn't scroll");
  const s4 = await state(); if (s4.at !== s2.at) bad.push(`scrolling drag changed the game ${s2.at}→${s4.at}`);
  /* 3. a sideways strip inside the page (screenshots, chips) still scrolls sideways */
  const strip = await p.page.evaluate(() => { const h = document.querySelector("#full.on .hub");
    const e = h && [...h.querySelectorAll("*")].find(x => x.scrollWidth > x.clientWidth + 30 && /auto|scroll/.test(getComputedStyle(x).overflowX));
    if (!e) return null; e.dataset.tstrip = 1; h.scrollTop += e.getBoundingClientRect().top - innerHeight / 2; const r = e.getBoundingClientRect(); return { x: r.left + r.width * .8, y: r.top + r.height / 2, name: e.className };
  });
  if (strip) {
    const at0 = await state();
    await drag(Array.from({ length: 12 }, (_, i) => [strip.x - i * 14, strip.y]));
    await T("touchEnd"); await p.page.waitForTimeout(900);
    const r = await p.page.evaluate(() => document.querySelector("[data-tstrip]").scrollLeft), at1 = await state();
    if (!r) bad.push(`strip .${strip.name} didn't scroll sideways`);
    if (at1.at !== at0.at) bad.push(`swiping strip .${strip.name} changed the game`);
  }
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "a sideways drag doesn't scroll, a scroll doesn't slide sideways" + (strip ? ", strips still scroll sideways" : " (no sideways strip on this page)") : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
