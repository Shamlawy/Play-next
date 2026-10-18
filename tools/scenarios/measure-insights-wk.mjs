/* Insights scroll in WebKit (the friend's iPad is Safari): programmatic wheel scrolling (WebKit has no CDP touch), rAF gaps
   per variant, each in a fresh page load so the first-scroll costs (layer creation, decode, raster) are in every number.
   Relative numbers only (desktop WebKit, no iPad GPU).
     node tools/kit.mjs run tools/scenarios/measure-insights-wk.mjs --browser webkit --widths 810 */
const V = {
  base: "", noreveal: ".ix-rv, .ix-rv * { transition: none !important; opacity: 1 !important; transform: none !important; }",
  nohero: ".ix-hero::before, .ix-hero::after { display: none !important; }", noheroanim: ".ix-shine, .ix-medal, .ix-medal * { animation: none !important; }",
  noring: ".ix-ring { -webkit-mask: none !important; mask: none !important; }", noshadow: "* { box-shadow: none !important; text-shadow: none !important; }",
  nocourt: ".dzc-table { display: none !important; }", noclip: ".ix-card, .ix-hero { overflow: visible !important; }",
  noendless: "*, *::before, *::after { animation: none !important; }",
};
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V), data = JSON.stringify(seed(v, 60));
  const rounds = +(process.env.ROUNDS || 3), names = (process.env.VARIANTS || Object.keys(V).join(",")).split(",");
  const out = {};
  for (let r = 0; r < rounds; r++) for (const name of names) {
    await p.page.evaluate(d => localStorage.setItem("gq:data", d), data);
    await p.page.reload(); await p.page.waitForTimeout(2500);
    await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
    await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 2500);
    if (V[name]) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, V[name]);
    await p.page.waitForTimeout(800);
    await p.page.evaluate(() => { window.__g = []; let last = 0; const f = t => { if (last) window.__g.push(t - last); last = t; window.__raf = requestAnimationFrame(f); }; window.__raf = requestAnimationFrame(f); });
    for (let i = 0; i < 40; i++) { await p.scroll(90); await p.page.waitForTimeout(30); }
    for (let i = 0; i < 40; i++) { await p.scroll(-90); await p.page.waitForTimeout(30); }
    const g = await p.page.evaluate(() => { cancelAnimationFrame(window.__raf); return window.__g; });
    (out[name] ||= []).push({ n: g.length, gaps: g.filter(x => x > 24).length, big: g.filter(x => x > 50).length, worst: Math.round(Math.max(0, ...g)) });
  }
  for (const [k, a] of Object.entries(out)) console.log(`  [${w}px WebKit ${k.padEnd(11)}] frames ${a.map(x => x.n).join("/")} · gaps>24ms ${a.map(x => x.gaps).join("/")} · >50ms ${a.map(x => x.big).join("/")} · worst ${a.map(x => x.worst).join("/")}ms`);
  return true;
};
