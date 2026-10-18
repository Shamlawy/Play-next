/* Insights (#136: scroll hitches, worst 1770ms on the friend's iPad): what is there to draw on this screen.
   Layers at rest and after a scroll (count, MP, screens' worth, top by size with compositing reasons), every picture / canvas /
   svg / background-image (natural vs shown x dpr), expensive effects (backdrop-filter, filter, blur, big shadows, masks,
   blend modes), endless animations, will-change, DOM size, scroll jobs.
     CHROME="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" node tools/kit.mjs run tools/scenarios/measure-insights.mjs --widths 859,412
     node tools/kit.mjs run tools/scenarios/measure-insights.mjs --browser webkit   (the effects part only; layers need Chromium) */
import { countLayers, printLayers } from "../layercount.mjs";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 3500);
  const wk = process.argv.includes("webkit");
  const layers = async label => { if (wk) return; const c = await countLayers(p); printLayers(label, w, c, 15); };
  await layers("insights at rest");
  const scan = async () => p.page.evaluate(() => {
    const dpr = devicePixelRatio, out = { imgs: [], canv: [], svgs: 0, bgs: [], fx: [], will: [], dom: 0, anims: [], insights: 0 };
    const root = document.querySelector("#p-taste");
    out.dom = root ? root.querySelectorAll("*").length : -1; out.domAll = document.querySelectorAll("*").length;
    const sel = e => e.tagName.toLowerCase() + (e.id ? "#" + e.id : "") + (e.className && typeof e.className === "string" ? "." + e.className.trim().split(/\s+/).slice(0, 3).join(".") : "");
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    for (const i of document.images) { if (!vis(i)) continue; const r = i.getBoundingClientRect(); const nat = i.naturalWidth * i.naturalHeight / 1e6, shown = r.width * r.height * dpr * dpr / 1e6;
      out.imgs.push({ s: sel(i), nat: +nat.toFixed(2), shown: +shown.toFixed(2), nw: i.naturalWidth, nh: i.naturalHeight, w: Math.round(r.width), h: Math.round(r.height), src: (i.currentSrc || i.src).slice(0, 40), inTaste: !!(root && root.contains(i)) }); }
    for (const c of document.querySelectorAll("canvas")) if (vis(c)) out.canv.push({ s: sel(c), bw: c.width, bh: c.height, mp: +(c.width * c.height / 1e6).toFixed(2) });
    out.svgs = [...document.querySelectorAll("#p-taste svg")].length;
    for (const e of document.querySelectorAll("*")) {
      const cs = getComputedStyle(e);
      if (cs.display === "none") continue;
      const r = e.getBoundingClientRect();
      const inT = !!(root && root.contains(e));
      const tag = [];
      if (cs.backgroundImage && cs.backgroundImage !== "none" && /url\(/.test(cs.backgroundImage)) out.bgs.push({ s: sel(e), w: Math.round(r.width), h: Math.round(r.height), bg: cs.backgroundImage.slice(0, 50), inT });
      for (const pe of [null, "::before", "::after"]) { if (!pe) continue; const c2 = getComputedStyle(e, pe); if (c2.content !== "none" && c2.backgroundImage && /url\(/.test(c2.backgroundImage)) { const k = c2.width; out.bgs.push({ s: sel(e) + pe, w: k, h: c2.height, bg: c2.backgroundImage.slice(0, 50), inT }); } }
      if (cs.backdropFilter !== "none" || cs.webkitBackdropFilter !== "none" && cs.webkitBackdropFilter) tag.push("backdrop-filter " + (cs.backdropFilter !== "none" ? cs.backdropFilter : cs.webkitBackdropFilter));
      if (cs.filter !== "none") tag.push("filter " + cs.filter);
      if (cs.mixBlendMode !== "normal") tag.push("blend " + cs.mixBlendMode);
      if ((cs.maskImage && cs.maskImage !== "none") || (cs.webkitMaskImage && cs.webkitMaskImage !== "none")) tag.push("mask");
      if (cs.clipPath !== "none") tag.push("clip-path");
      if (cs.willChange !== "auto") tag.push("will-change " + cs.willChange);
      const bs = cs.boxShadow; if (bs !== "none") { const m = [...bs.matchAll(/(-?\d+(?:\.\d+)?)px/g)].map(x => +x[1]); /* blur is third px in each shadow */ const parts = bs.split(/,(?![^(]*\))/); for (const s of parts) { const px = [...s.matchAll(/(-?\d+(?:\.\d+)?)px/g)].map(x => +x[1]); if (px[2] >= 24) tag.push("shadow blur " + px[2] + " spread " + (px[3] || 0)); } }
      const ts = cs.textShadow; if (ts !== "none") tag.push("text-shadow");
      if (tag.length) out.fx.push({ s: sel(e), w: Math.round(r.width), h: Math.round(r.height), tag: tag.join(" | "), inT });
    }
    out.anims = document.getAnimations().filter(a => a.effect && a.effect.getComputedTiming().iterations === Infinity).map(a => ({ t: a.effect.target ? sel(a.effect.target) : "?", n: a.animationName || a.constructor.name, props: (a.effect.getKeyframes ? a.effect.getKeyframes().flatMap(k => Object.keys(k)).filter(k => !/offset|easing|composite|computedOffset/.test(k)) : []).filter((x, i, a) => a.indexOf(x) === i).join(",") }));
    out.runningAll = document.getAnimations().length;
    return out;
  });
  const show = (label, o) => {
    console.log(`\n  == ${label} (${w}px) ==  DOM nodes in Insights page: ${o.dom} (whole document ${o.domAll}); svgs in Insights: ${o.svgs}; running animations (all): ${o.runningAll}`);
    console.log("  endless animations: " + (o.anims.length ? "" : "none"));
    for (const a of o.anims) console.log(`     ${a.n} on ${a.t}  [${a.props}]`);
    const im = o.imgs.filter(i => true); const tot = im.reduce((a, i) => a + i.nat, 0), sh = im.reduce((a, i) => a + i.shown, 0);
    console.log(`  images laid out: ${im.length}, natural ${tot.toFixed(1)} MP vs shown ${sh.toFixed(1)} MP; in Insights: ${im.filter(i => i.inTaste).length}`);
    for (const i of im.sort((a, b) => b.nat - a.nat).slice(0, 8)) console.log(`     ${i.nat} MP nat (${i.nw}x${i.nh}) vs shown ${i.shown} MP (${i.w}x${i.h}) ${i.inTaste ? "[insights]" : ""} ${i.s} ${i.src}`);
    console.log(`  canvases: ${o.canv.length}` + o.canv.map(c => ` ${c.s} ${c.bw}x${c.bh}=${c.mp}MP`).join(";"));
    console.log(`  background-image url()s: ${o.bgs.length}`); for (const b of o.bgs.slice(0, 10)) console.log(`     ${b.s} ${b.w}x${b.h} ${b.bg} ${b.inT ? "[insights]" : ""}`);
    console.log(`  effect elements: ${o.fx.length} (insights ${o.fx.filter(f => f.inT).length})`);
    const by = {}; for (const f of o.fx) for (const t of f.tag.split(" | ")) { const k = t.split(" ")[0] + (f.inT ? " [insights]" : ""); by[k] = (by[k] || 0) + 1; }
    console.log("     counts: " + JSON.stringify(by));
    for (const f of o.fx.filter(f => f.inT).sort((a, b) => b.w * b.h - a.w * a.h).slice(0, 14)) console.log(`     ${f.w}x${f.h} ${f.s}  ${f.tag}`);
  };
  show("at rest", await scan());
  /* scroll the page (Insights is inside a scrolling panel; use real touch where we can) */
  if (!wk) { for (let k = 0; k < 4; k++) { await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .25, 200, 12); await p.page.waitForTimeout(500); } }
  else await p.scroll(1500);
  await p.page.waitForTimeout(800);
  await layers("insights after scroll");
  show("after scroll", await scan());
  /* scroll jobs & observers */
  const jobs = await p.page.evaluate(() => ({ onScrolled: typeof SCROLLJOBS !== "undefined" ? SCROLLJOBS.length : (window.__sj || "?"), io: typeof ixIO !== "undefined" && ixIO ? "ixIO armed" : "no ixIO" }));
  console.log("\n  scroll jobs: " + JSON.stringify(jobs));
  return true;
};
