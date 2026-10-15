/* v286 (the owner's picks from the code-check issue): Grumble's HP bar only while Grumble is on, Nexi's sweat drop only for
   a moment when the backlog grows, the Out now shine sweeps 3 times per screen visit.
     node tools/kit.mjs run tools/scenarios/owner-picks-286.mjs --widths 412,859 */
export default async ({ p, w, fail }) => {
  let ok = true; const no = m => { ok = false; fail(`${w}px: ${m}`); };
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  /* 1. the HP bar */
  const add = () => p.page.evaluate(() => { const g = { ...S.games[0], id: "hp" + Math.random().toString(36).slice(2), title: "Test add", state: "backlog", hours: 12, comp: false };
    delete g.played; S.games.push(g); save(); });
  for (const on of [false, true]) {
    await p.page.evaluate(on => { S.grum = on; applyLook(); }, on);
    await add(); await p.page.waitForTimeout(1500);
    const hud = await p.page.evaluate(() => !!document.getElementById("fxhud"));
    if (hud !== on) no(`Grumble ${on ? "on" : "off"}: HP bar ${hud ? "showed" : "didn't show"}`);
    else console.log(`  ✓ ${w}px Grumble ${on ? "on" : "off"}: HP bar ${hud ? "shows" : "stays away"}`);
    await p.page.waitForTimeout(5800);
  }
  await p.page.evaluate(() => { S.grum = false; applyLook(); });
  /* 2. the sweat drop: more than 15 waiting, a game added → stress for 5.4s, three drops */
  const st = () => p.page.evaluate(() => { const b = document.getElementById("pnbub"); const sw = b && b.querySelector(".sweat");
    const a = sw && sw.getAnimations()[0]; return { on: !!b && b.classList.contains("stress"), it: a ? a.effect.getTiming().iterations : null }; });
  await p.page.evaluate(() => { while (S.games.filter(g => !isPlayed(g)).length <= 16) S.games.push({ ...S.games[0], id: "sw" + Math.random().toString(36).slice(2), state: "backlog" });
    pnBubReact(); });   /* the snapshot now holds the big backlog */
  const calm = await st();
  if (calm.on) no("sweating with no new game"); else console.log(`  ✓ ${w}px over 15 waiting, nothing new: no sweat`);
  await p.page.evaluate(() => { S.games.push({ ...S.games[0], id: "sx1", state: "backlog" }); pnBubReact(); });
  const hot = await st();
  if (!hot.on || (hot.it !== null && hot.it === Infinity)) no(`a game added: sweat ${JSON.stringify(hot)}`); else console.log(`  ✓ ${w}px a game added: sweats (${hot.it ?? "not drawn"} drops)`);
  await p.page.waitForTimeout(5800);
  if ((await st()).on) no("still sweating after 5.8s"); else console.log(`  ✓ ${w}px dry again after 5.8s`);
  /* 3. the Out now shine: one game out two days ago, waiting in the backlog */
  await p.step("queue", `goTab("games"); goSub("games", "queue")`, 800);
  await p.page.evaluate(() => { const r = document.querySelector('#p-queue [data-row] .tag.st-backlog').closest("[data-row]"), g = S.games.find(g => g.id === r.dataset.row);
    g.state = "backlog"; g.date = new Date(Date.now() - 2 * 864e5).toLocaleDateString("en-CA"); saveNow(); renderQueue(); });
  const shine = () => p.page.evaluate(() => { const t = document.querySelector(".panel.on .tag.st-fresh"); if (!t) return null;
    return t.getAnimations({ subtree: true }).filter(a => a.animationName === "freshSweep").map(a => [a.playState, a.effect.getTiming().iterations]); });
  for (const [tab, sub] of [["games", "queue"], ["home"], ["games", "queue"]]) {
    await p.step(tab, `goTab("${tab}")${sub ? `; goSub("${tab}", "${sub}")` : ""}`, 600);
    const s0 = await shine(); if (!s0) { console.log(`  · ${w}px ${tab}: no Out now tag on screen`); continue; }
    if (!s0.length || s0.some(([, n]) => n === Infinity)) no(`${tab}: shine on opening ${JSON.stringify(s0)}`);
    await p.page.waitForTimeout(7600);
    const s1 = await shine();
    if (s1.some(([st]) => st === "running")) no(`${tab}: shine still running after 3 sweeps ${JSON.stringify(s1)}`);
    else console.log(`  ✓ ${w}px ${tab}: shine ${JSON.stringify(s0)} on opening → resting after 3 sweeps`);
  }
  return ok && !p.errors.length;
};
