/* What animates right after the switch to the Queue (finite animations too), which makes layers like div.row.slab.qs 7537px tall.
     CHROME=... node tools/kit.mjs run tools/scenarios/measure-queue-switch.mjs --widths 412,859 */
export default async ({ p, w, seed }) => {
  const pg = p.page;
  const v = await pg.evaluate(() => APP_V);
  await pg.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await pg.reload(); await pg.waitForTimeout(3000);
  await pg.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.step("month", `closeFull(); goTab("month")`, 2200); await pg.evaluate(() => scrollTo(0, 0));
  await pg.evaluate(() => { setSub("games", "queue"); const b = document.querySelector('nav button[data-tab="games"]'); b ? b.click() : goTab("games"); });
  await pg.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  for (const t of [0, 150, 400, 900]) { if (t) await pg.waitForTimeout(t === 150 ? 150 : t === 400 ? 250 : 500);
    const a = await pg.evaluate(() => { const nm = e => e ? e.tagName.toLowerCase() + (e.id ? "#" + e.id : "") + (typeof e.className === "string" && e.className.trim() ? "." + e.className.trim().split(/\s+/).slice(0, 3).join(".") : "") : "?";
      const m = {}; for (const x of document.getAnimations()) { const t = x.effect && x.effect.getComputedTiming(); const e = x.effect && x.effect.target; const r = e && e.getBoundingClientRect(); if (t && t.iterations === Infinity && !(r && r.top < innerHeight && r.bottom > 0)) continue;
        const k = (x.animationName || x.transitionProperty || "waapi") + " <- " + nm(e).slice(0, 50) + (x.effect.pseudoElement || "") + " " + (t ? t.duration + "ms" + (t.iterations === Infinity ? " inf" : "") : ""); m[k] = (m[k] || 0) + 1; }
      return Object.entries(m).sort((a, b) => b[1] - a[1]).slice(0, 14).map(([k, n]) => n + "x " + k); });
    console.log(`  +${t === 0 ? 0 : t === 150 ? 150 : t === 400 ? 400 : 900}ms running animations (${a.length} kinds):`); for (const l of a) console.log("      " + l); }
  const q = await pg.evaluate(() => { const r = [...document.querySelectorAll("#p-queue .row.qs")]; const cs = r[0] && getComputedStyle(r[0]); return { n: r.length, anim: cs && cs.animationName, dur: cs && cs.animationDuration, delay: [0, 1, 2, 30, 58].map(i => r[i] && getComputedStyle(r[i]).animationDelay), wc: cs && cs.willChange, cv: cs && cs.contentVisibility, transform: cs && cs.transform, tr: cs && cs.transition.slice(0, 80) }; });
  console.log("  row style:", JSON.stringify(q));
  return true;
};
