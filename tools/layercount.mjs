/* The GPU layer census, shared by tools/scenarios/layers.mjs and `node tools/kit.mjs layers` (v287).
   Every layer is a surface the phone's GPU rasters and blends each frame. "screens" = each drawing layer's size cut to the
   viewport, summed, in viewports: an upper bound on what one frame blends. A Played stutter on the phone said "script 0,
   style+layout 1, render 5" in a 128ms frame: the time was drawing, and Played was 14.5 screens of layers. */
export async function countLayers(p) {
  let got = null; const h = e => { if (e.layers) got = e.layers; };
  p.cdp.on("LayerTree.layerTreeDidChange", h);
  await p.cdp.send("LayerTree.enable"); for (let i = 0; i < 30 && !got; i++) await p.page.waitForTimeout(100);
  const dpr = await p.page.evaluate(() => devicePixelRatio);
  const rows = [];
  for (const l of (got || []).filter(l => l.drawsContent)) { let why = "?", d = "?";
    try { why = (await p.cdp.send("LayerTree.compositingReasons", { layerId: l.layerId })).compositingReasonIds.join("+"); } catch (e) {}
    try { const n = (await p.cdp.send("DOM.describeNode", { backendNodeId: l.backendNodeId })).node, at = n.attributes || [], g = k => { const i = at.indexOf(k); return i >= 0 ? at[i + 1] : ""; };
      d = (n.pseudoType ? "::" + n.pseudoType + " of " : "") + n.nodeName.toLowerCase() + (g("id") ? "#" + g("id") : "") + (g("class") ? "." + g("class").trim().split(/\s+/).slice(0, 3).join(".") : "");
    } catch (e) {}
    rows.push({ why, d, w: Math.round(l.width), h: Math.round(l.height), mp: l.width * l.height * dpr * dpr / 1e6, scr: Math.min(l.width, p.w) * Math.min(l.height, p.h) / (p.w * p.h) }); }
  await p.cdp.send("LayerTree.disable"); p.cdp.off("LayerTree.layerTreeDidChange", h);
  const by = {}; for (const r of rows) { const k = r.why.split("+")[0]; by[k] = by[k] || { n: 0, mp: 0 }; by[k].n++; by[k].mp += r.mp; }
  return { rows, by, n: rows.length, mp: +rows.reduce((a, r) => a + r.mp, 0).toFixed(1), screens: +rows.reduce((a, r) => a + r.scr, 0).toFixed(1) };
}
/* the screens the census walks; a game page opens the first game being played (else the first game) */
export const LAYER_SCREENS = {
  home: `closeFull(); goTab("home")`, played: `closeFull(); goTab("games"); goSub("games", "played")`,
  queue: `closeFull(); goTab("games"); goSub("games", "queue")`, lib: `closeFull(); goTab("games"); goSub("games", "lib")`,
  game: `goTab("games"); openFull((S.games.find(g => g.status === "playing") || S.games[0]).id)`,
  insights: `closeFull(); goTab("taste"); goSub("taste", "insights")`,
};
export const printLayers = (name, w, c, top = 15) => {
  console.log(`\n  [${name}] ${w}px: ${c.n} layers, ${c.mp} MP, ${c.screens} screens`);
  console.log("    by reason: " + Object.entries(c.by).sort((a, b) => b[1].mp - a[1].mp).map(([k, x]) => `${k} ${x.n} (${x.mp.toFixed(1)} MP)`).join(" · "));
  for (const r of [...c.rows].sort((a, b) => b.mp - a.mp).slice(0, top)) console.log(`      ${r.mp.toFixed(2).padStart(6)} MP ${String(r.w + "×" + r.h).padEnd(10)} ${r.why.padEnd(28)} ${r.d}`);
};
