/* v296 (the owner: "double tapping on nexi shows emoji and other commands, remove the emoji and shorten them and organise them better",
   "return nexi on the home", #469 "drop 2"): Nexi's menu is one card in three groups of plain words, fully on screen, every
   button works; unfolded, he sits in the rail on Home (Home's own face steps aside) and rests with no endless loop but his blink (kept since #447).
     node tools/kit.mjs run tools/scenarios/nexi-wheel.mjs --widths 412,859   (SHOTS=dir saves pictures) */
export default async ({ p, w }) => {
  const bad = [], D = process.env.SHOTS;
  await p.page.evaluate(() => { S.railHide = false; S.nxSeatOff = false; goTab("home"); }); await p.page.waitForTimeout(1800);
  if (D) await p.page.screenshot({ path: `${D}/home-${w}.png` });
  if (w >= 620) {
    const r = await p.page.evaluate(() => { const H = document.documentElement, b = document.getElementById("pnbub"), f = document.querySelector("#p-home .nxh-face");
      const loops = document.getAnimations().filter(a => { const t = a.effect && a.effect.target, tm = a.effect && a.effect.getComputedTiming();
        return t && b.contains(t) && a.playState === "running" && tm && tm.iterations === Infinity && a.animationName !== "pnBlink"; }).map(a => a.animationName || "script");
      return { seat: H.classList.contains("nxseat"), shown: !!b && b.checkVisibility(), face: !!f && f.checkVisibility(), loops }; });
    if (!r.seat || !r.shown) bad.push("Nexi isn't in the rail on Home");
    if (r.face) bad.push("two Nexis on Home (the face is still shown)");
    if (r.loops.length) bad.push("seated Nexi still loops: " + r.loops.join(", "));
    /* still at rest, but his reactions play: a wobble on the seated Nexi animates his core */
    const wob = await p.page.evaluate(async () => { const b = document.getElementById("pnbub"); b.classList.add("wob");
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      const n = getComputedStyle(b.querySelector(".core")).animationName; b.classList.remove("wob"); return n; });
    if (!/pnWob/.test(wob)) bad.push("seated Nexi's wobble reaction doesn't play (" + wob + ")");
  }
  await p.page.evaluate(() => pnBubWheel()); await p.page.waitForTimeout(500);
  if (D) await p.page.screenshot({ path: `${D}/wheel-${w}.png` });
  const m = await p.page.evaluate(() => { const m = document.querySelector("#bubwheel .nxm"); if (!m) return null; const r = m.getBoundingClientRect();
    const bs = [...m.querySelectorAll("button")];
    return { groups: m.querySelectorAll(".nxm-g").length, n: bs.length, words: bs.map(b => b.textContent),
      emoji: bs.filter(b => /\p{Extended_Pictographic}/u.test(b.textContent)).length,
      off: r.left < 0 || r.top < 0 || r.right > innerWidth || r.bottom > innerHeight,
      long: bs.filter(b => b.textContent.length > 12).map(b => b.textContent),
      cut: bs.filter(b => b.scrollWidth > b.clientWidth + 1).map(b => b.textContent) }; });
  if (!m) bad.push("no menu");
  else { if (m.groups !== 3) bad.push(m.groups + " groups");
    if (m.emoji) bad.push(m.emoji + " emoji buttons");
    if (m.n > 12) bad.push(m.n + " buttons");
    if (m.off) bad.push("menu runs off the screen");
    if (m.long.length) bad.push("long labels: " + m.long.join(", "));
    if (m.cut.length) bad.push("cut labels: " + m.cut.join(", ")); }
  /* a tap on "Add game" goes to Add (and the double tap's own click in the first 350ms doesn't close it, #69) */
  await p.page.waitForTimeout(400);
  await p.page.evaluate(() => document.querySelector('#bubwheel [data-a="add"]').click()); await p.page.waitForTimeout(600);
  const went = await p.page.evaluate(() => !document.getElementById("bubwheel") && document.querySelector("#p-add.on, #p-add:not([hidden])") !== null);
  if (!went) bad.push("Add game didn't go to Add");
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? `menu: ${m.words.join(" · ")}` : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
