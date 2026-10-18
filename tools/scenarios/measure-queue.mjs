/* Queue picture + layer census (stutter hunt, #72 #116 #333 #347). Read-only: changes nothing in the app.
     CHROME=... node tools/kit.mjs run tools/scenarios/measure-queue.mjs --widths 412,859
   For each moment (rest, mid-scroll, 1 frame / 300ms / 1s after switching to the Queue from Month): every <img> and every element
   with a CSS background-image url within one screen of the viewport: natural size, shown size x dpr, ratio, MP decoded vs needed;
   endless animations, will-change, row count, content-visibility, and the biggest layers. TOP=n sets the table length. */
import { countLayers } from "../layercount.mjs";
const MEASURE = (every) => {
  const dpr = devicePixelRatio, vh = innerHeight, vw = innerWidth, out = [];
  const path = e => { const a = []; for (let n = e; n && n.nodeType === 1 && a.length < 4; n = n.parentElement) a.unshift(n.tagName.toLowerCase() + (n.id ? "#" + n.id : "") + (typeof n.className === "string" && n.className.trim() ? "." + n.className.trim().split(/\s+/).slice(0, 2).join(".") : "")); return a.join(" > "); };
  const near = r => r.width > 0 && r.height > 0 && (every || (r.bottom > -vh && r.top < vh * 2)) && r.right > 0 && r.left < vw, on = r => r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw;
  for (const e of document.querySelectorAll("img")) { const r = e.getBoundingClientRect(); if (!near(r) || getComputedStyle(e).visibility === "hidden") continue;
    out.push({ k: "img", p: path(e), src: (e.currentSrc || e.src || "").slice(-40), nw: e.naturalWidth, nh: e.naturalHeight, w: r.width, h: r.height, on: on(r), done: e.complete }); }
  for (const e of document.querySelectorAll("*")) { for (const ps of [null, "::before", "::after"]) { const cs = getComputedStyle(e, ps); const bi = cs.backgroundImage; if (!bi || bi === "none" || !/url\(/.test(bi)) continue;
    if (ps && cs.content === "none") continue; const r = e.getBoundingClientRect(); if (!near(r)) continue;
    const urls = [...bi.matchAll(/url\(["']?([^"')]+)["']?\)/g)].map(m => m[1]); for (const u of urls) out.push({ k: "bg" + (ps || ""), p: path(e), src: u.slice(-40), url: u, w: r.width, h: r.height, on: on(r) }); } }
  return { dpr, out };
};
const ANIMS = () => {
  const res = []; const name = e => e ? (e.tagName.toLowerCase() + (e.id ? "#" + e.id : "") + (typeof e.className === "string" && e.className.trim() ? "." + e.className.trim().split(/\s+/).slice(0, 2).join(".") : "")) : "?";
  for (const a of document.getAnimations()) { const t = a.effect && a.effect.getComputedTiming ? a.effect.getComputedTiming() : null; if (!t || t.iterations !== Infinity) continue;
    const e = a.effect.target; const r = e && e.getBoundingClientRect(); const props = a.effect.getKeyframes().flatMap(k => Object.keys(k).filter(x => !["offset", "easing", "composite", "computedOffset"].includes(x)));
    res.push({ el: name(e) + (a.effect.pseudoElement || ""), name: a.animationName || "(waapi)", onScreen: !!r && r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth, props: [...new Set(props)].join(","), state: a.playState }); }
  const wc = []; for (const e of document.querySelectorAll("*")) { const w = getComputedStyle(e).willChange; if (w && w !== "auto") { const r = e.getBoundingClientRect(); wc.push({ el: name(e), w, onScreen: r.bottom > 0 && r.top < innerHeight && r.width > 0, size: Math.round(r.width) + "x" + Math.round(r.height) }); } }
  return { loops: res, wc };
};
export default async ({ p, w, seed }) => {
  const pg = p.page, TOP = +(process.env.TOP || 12);
  const v = await pg.evaluate(() => APP_V);
  await pg.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await pg.reload(); await pg.waitForTimeout(3000);
  await pg.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const sizes = {};
  const natural = async urls => { const need = urls.filter(u => !(u in sizes)); if (!need.length) return;
    const r = await pg.evaluate(async us => Promise.all(us.map(u => new Promise(res => { const i = new Image(); i.onload = () => res([u, i.naturalWidth, i.naturalHeight]); i.onerror = () => res([u, 0, 0]); i.src = u; }))), need);
    for (const [u, a, b] of r) sizes[u] = [a, b]; };
  const census = async (label, every) => {
    const m = await pg.evaluate(MEASURE, !!every); await natural([...new Set(m.out.filter(o => o.url).map(o => o.url))]);
    const rows = m.out.map(o => { const [nw, nh] = o.url ? sizes[o.url] : [o.nw, o.nh]; const dw = o.w * m.dpr, dh = o.h * m.dpr; const dec = nw * nh / 1e6, need = Math.min(nw * nh, dw * dh) / 1e6;
      return { ...o, nw, nh, dec, need, ratio: nw * nh / Math.max(1, dw * dh), dw: Math.round(dw), dh: Math.round(dh) }; });
    const uniq = new Map(); for (const r of rows) { const k = (r.url || r.src) + "|" + r.p; if (!uniq.has(k)) uniq.set(k, r); }
    const all = [...uniq.values()], on = all.filter(r => r.on);
    const bySrc = new Map(); for (const r of all) { const k = r.url || r.src; if (!bySrc.has(k)) bySrc.set(k, r); }
    const sum = (a, f) => a.reduce((s, r) => s + f(r), 0);
    const dpr = m.dpr, vp = p.w * p.h * dpr * dpr / 1e6, decd = sum([...bySrc.values()], r => r.dec);
    console.log(`\n  [${label}] ${w}px dpr ${dpr}: ${all.length} picture uses within 1 screen (${on.length} on screen), ${bySrc.size} distinct files`);
    console.log(`     decoded (distinct files): ${decd.toFixed(1)} MP = ${(decd / vp).toFixed(1)} screens · needed at shown size: ${sum(all, r => r.need).toFixed(1)} MP · on-screen uses: decoded ${sum(on, r => r.dec).toFixed(1)} MP, needed ${sum(on, r => r.need).toFixed(1)} MP`);
    for (const r of [...all].sort((a, b) => b.dec - a.dec).slice(0, TOP)) console.log(`       ${r.dec.toFixed(2).padStart(6)} MP ${(r.nw + "x" + r.nh).padEnd(11)} shown ${(r.dw + "x" + r.dh).padEnd(10)} ratio ${r.ratio.toFixed(1).padStart(5)} ${r.on ? "ON " : "off"} ${r.k.padEnd(9)} ${r.p.slice(-95)}`);
    return all;
  };
  const animsOf = async () => { const a = await pg.evaluate(ANIMS);
    const on = a.loops.filter(l => l.onScreen); console.log(`     endless animations: ${a.loops.length} (${on.length} on screen): ` + on.map(l => `${l.el}[${l.name}:${l.props}]`).join(" | "));
    const wc = a.wc.filter(x => x.onScreen); console.log(`     will-change: ${a.wc.length} (${wc.length} on screen): ` + wc.slice(0, 12).map(x => `${x.el}{${x.w}} ${x.size}`).join(" | ")); };
  const layers = async () => { const c = await countLayers(p); console.log(`     LAYERS: ${c.n}, ${c.mp} MP, ${c.screens} screens; by reason: ` + Object.entries(c.by).map(([k, x]) => `${k || "-"} ${x.n}`).join(" / "));
    for (const r of [...c.rows].sort((a, b) => b.mp - a.mp).slice(0, 10)) console.log(`         ${r.mp.toFixed(2).padStart(6)} MP ${String(r.w + "x" + r.h).padEnd(9)} ${r.why.padEnd(40)} ${r.d}`); };
  const structure = async () => { const s = await pg.evaluate(() => { const q = document.getElementById("p-queue") || document; const rows = [...q.querySelectorAll(".row.qs")]; const vh = innerHeight;
      const rect = rows.map(r => r.getBoundingClientRect()); const inView = rect.filter(r => r.bottom > 0 && r.top < vh).length, nearV = rect.filter(r => r.bottom > -vh && r.top < vh * 2).length;
      const cv = [...document.querySelectorAll("*")].filter(e => /auto|hidden/.test(getComputedStyle(e).contentVisibility || "")).length;
      const first = rows[0]; const kids = first ? first.querySelectorAll("*").length : 0;
      const cs = first && getComputedStyle(first); return { rows: rows.length, inView, nearV, cv, kids, rowH: first ? Math.round(rect[0].height) : 0, docH: document.documentElement.scrollHeight, vh, rowCV: cs ? cs.contentVisibility : "", rowContain: cs ? cs.contain : "", rowWill: cs ? cs.willChange : "", domEls: document.getElementsByTagName("*").length, queueEls: q.getElementsByTagName("*").length }; });
    console.log(`     STRUCTURE: ${s.rows} rows in DOM (${s.inView} on screen, ${s.nearV} within a screen), ${s.kids} elements per row, row height ${s.rowH}, doc ${s.docH}px (viewport ${s.vh}), content-visibility elements: ${s.cv} (row: ${s.rowCV}, contain: ${s.rowContain}, will-change: ${s.rowWill}), DOM ${s.domEls} total / ${s.queueEls} in the Queue`); };
  const go = `goTab("games"); goSub("games","queue")`;
  /* 1. at rest */
  await p.step("queue", `closeFull(); ${go}`, 3000);
  await census("rest, Queue top"); await animsOf(); await structure(); await layers();
  /* 2. mid-scroll: the finger still down on a swipe */
  await pg.evaluate(() => scrollTo(0, 0)); await pg.waitForTimeout(400);
  const sw = p.swipe(p.w * .5, p.h * .85, p.w * .52, p.h * .2, 1200, 60); await pg.waitForTimeout(500);
  await census("mid-scroll"); await animsOf(); await layers(); await sw; await pg.waitForTimeout(500);
  await census("scrolled and settled");
  await census("WHOLE LIST (every picture, near or not)", true);
  /* 3. switch from Month to the Queue */
  for (const [label, delay] of [["1 frame after switch", 0], ["300ms after switch", 300], ["1s after switch", 1000]]) {
    await p.step("month", `closeFull(); goTab("month")`, 2200); await pg.evaluate(() => scrollTo(0, 0));
    await pg.evaluate(() => { setSub("games", "queue"); const b = document.querySelector('nav button[data-tab="games"]'); b ? b.click() : goTab("games"); });
    await pg.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))); if (delay) await pg.waitForTimeout(delay);
    await census(label); if (delay === 0) { await animsOf(); await layers(); } if (delay === 1000) await structure();
  }
  return true;
};
