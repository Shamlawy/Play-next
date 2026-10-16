/* Every animation alive on a screen (document.getAnimations()): name, running/paused, whether the element is drawn at all,
   its size and place, and where it sits. A paused or off-screen transform animation still keeps its element on its own GPU
   layer, and everything drawn above it that overlaps gets a layer too (tools/scenarios/layers.mjs shows those).
     SCREENS=played,queue node tools/kit.mjs run tools/scenarios/live-animations.mjs --widths 859 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  const PH = { home: `closeFull(); goTab("home")`, played: `closeFull(); goTab("games"); goSub("games", "played")`,
    queue: `closeFull(); goTab("games"); goSub("games", "queue")`, lib: `closeFull(); goTab("games"); goSub("games", "lib")`,
    game: `goTab("games"); openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)` };
  for (const n of (process.env.SCREENS || Object.keys(PH).join(",")).split(",")) {
    await p.step(n, PH[n], 2600);
    const r = await p.page.evaluate(() => {
      const path = el => { const a = []; for (let e = el; e && e !== document.body && a.length < 5; e = e.parentElement) a.unshift(e.tagName.toLowerCase() + (e.id ? "#" + e.id : "") + (e.classList[0] ? "." + [...e.classList].slice(0, 2).join(".") : "")); return a.join(">"); };
      const out = {};
      for (const a of document.getAnimations()) { const t = a.effect && a.effect.target; if (!t) continue; const pe = a.effect.pseudoElement || "";
        const vis = t.checkVisibility ? t.checkVisibility({ contentVisibilityAuto: true, visibilityProperty: true }) : true, q = t.getBoundingClientRect();
        const on = vis && q.bottom > 0 && q.top < innerHeight && q.right > 0 && q.left < innerWidth;
        const k = `${a.animationName || a.constructor.name} ${a.playState}${on ? " ON SCREEN" : vis ? " off-screen" : " hidden"} ${Math.round(q.width)}x${Math.round(q.height)} ${path(t)}${pe}`; out[k] = (out[k] || 0) + 1; }
      return Object.entries(out).sort((a, b) => b[1] - a[1]).map(([k, n]) => n + "× " + k);
    });
    console.log(`\n  [${n}] ${w}px: ${r.length} kinds\n    ` + r.join("\n    "));
  }
  return true;
};
