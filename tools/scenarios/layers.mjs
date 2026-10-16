/* Which elements get their own GPU layer on a screen, and why (Chromium's compositing reasons). Every layer is a separate
   surface the phone's GPU rasters and blends each frame; "Overlap" ones exist only because they sit above another layer, and
   one stray layer at the bottom (a full-screen background with a 3D transform) can drag everything above it onto the GPU.
     SCREENS=played,game node tools/kit.mjs run tools/scenarios/layers.mjs --widths 859
   Prints per screen: layers, MP of surface (area × dpr²), by reason, and the 15 biggest with their reasons. */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  /* CSS=<rules> adds a style to try a change without editing the app (e.g. CSS='.pnb-s{animation:none}') */
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const PH = {
    home: `closeFull(); goTab("home")`, played: `closeFull(); goTab("games"); goSub("games", "played")`,
    queue: `closeFull(); goTab("games"); goSub("games", "queue")`, lib: `closeFull(); goTab("games"); goSub("games", "lib")`,
    game: `goTab("games"); openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)`,
  };
  const res = {};
  for (const name of (process.env.SCREENS || Object.keys(PH).join(",")).split(",")) {
    await p.step(name, PH[name], 2600);
    if (process.env.SCROLL) await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .3, 200, 10), await p.page.waitForTimeout(600);
    let got = null; const h = e => { if (e.layers) got = e.layers; }; p.cdp.on("LayerTree.layerTreeDidChange", h);
    await p.cdp.send("LayerTree.enable"); for (let i = 0; i < 30 && !got; i++) await p.page.waitForTimeout(100);
    const dpr = await p.page.evaluate(() => devicePixelRatio);
    const L = (got || []).filter(l => l.drawsContent);
    const rows = [];
    for (const l of L) { let why = "?", d = "?";
      try { why = (await p.cdp.send("LayerTree.compositingReasons", { layerId: l.layerId })).compositingReasonIds.join("+"); } catch (e) {}
      try { const n = (await p.cdp.send("DOM.describeNode", { backendNodeId: l.backendNodeId })).node, at = n.attributes || [], g = k => { const i = at.indexOf(k); return i >= 0 ? at[i + 1] : ""; };
        const par = n.parentId ? "" : ""; d = (n.pseudoType ? "::" + n.pseudoType + " of " : "") + n.nodeName.toLowerCase() + (g("id") ? "#" + g("id") : "") + (g("class") ? "." + g("class").trim().split(/\s+/).slice(0, 3).join(".") : "") + par;
        if (n.pseudoType && l.backendNodeId) { /* pseudo: name its host */ }
      } catch (e) {}
      rows.push({ why, d, w: Math.round(l.width), h: Math.round(l.height), mp: l.width * l.height * dpr * dpr / 1e6, scr: Math.min(l.width, p.w) * Math.min(l.height, p.h) / (p.w * p.h) }); }
    await p.cdp.send("LayerTree.disable"); p.cdp.off("LayerTree.layerTreeDidChange", h);
    const by = {}; for (const r of rows) { const k = r.why.split("+")[0]; by[k] = by[k] || { n: 0, mp: 0 }; by[k].n++; by[k].mp += r.mp; }
    /* screens = what the GPU blends per frame at most: each layer's size cut to the screen, in screens (an upper bound) */
    const mp = rows.reduce((a, r) => a + r.mp, 0), scr = rows.reduce((a, r) => a + r.scr, 0);
    res[name] = { n: rows.length, mp: +mp.toFixed(1), screens: +scr.toFixed(1) };
    console.log(`\n  [${name}] ${w}px: ${rows.length} layers, ${mp.toFixed(1)} MP, ${scr.toFixed(1)} screens`);
    console.log("    by reason: " + Object.entries(by).sort((a, b) => b[1].mp - a[1].mp).map(([k, x]) => `${k} ${x.n} (${x.mp.toFixed(1)} MP)`).join(" · "));
    for (const r of rows.sort((a, b) => b.mp - a.mp).slice(0, +(process.env.TOP || 15))) console.log(`      ${r.mp.toFixed(2).padStart(6)} MP ${String(r.w + "×" + r.h).padEnd(10)} ${r.why.padEnd(28)} ${r.d}`);
    if (process.env.ALL) for (const r of rows.filter(r => !/Overlap|Squash/.test(r.why))) console.log(`      direct: ${r.why} ${r.w}×${r.h} ${r.d}`);
  }
  if (process.env.OUT) (await import("node:fs")).writeFileSync(process.env.OUT, JSON.stringify(res));
  return true;
};
