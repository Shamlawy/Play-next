/* What does a game page (openFull) decode and keep alive while it is scrolled? Lists every <img> and every CSS background-image
   url on the page: natural size, shown size × dpr, ratio, MP decoded (natural w×h) vs MP needed (shown w×h × dpr²) and whether
   it is on screen; at rest and after scrolling down 1, 2, 3 screens. Also endless animations, will-change elements and the GPU layers.
     node tools/kit.mjs run tools/scenarios/measure-game.mjs --widths 412,859
     GAME=g0 (default: key art, i%4==0) / GAME=g1 (no key art); TOP=10 rows per table; REAL=1 scrolls with real touch swipes */
import { countLayers } from "../layercount.mjs";
const SNAP = `(async () => {
  const dpr = devicePixelRatio, vh = innerHeight, vw = innerWidth, out = [];
  const path = e => { const a = []; for (let n = e; n && n.nodeType === 1 && a.length < 4; n = n.parentElement) a.unshift(n.tagName.toLowerCase() + (n.id ? "#" + n.id : "") + (n.className && typeof n.className === "string" ? "." + n.className.trim().split(/\\s+/).slice(0, 2).join(".") : "")); return a.join(" > "); };
  const vis = r => r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw;
  const bgs = new Map();
  const root = document.querySelector("#full") || document.body;
  for (const e of root.querySelectorAll("*")) {
    for (const ps of [null, "::before", "::after"]) {
      const cs = getComputedStyle(e, ps), bi = cs.backgroundImage;
      if (bi && bi !== "none" && /url\\(/.test(bi)) { for (const m of bi.matchAll(/url\\(["']?([^"')]+)["']?\\)/g)) { const k = e; bgs.set(k + "|" + (ps || "") + "|" + m[1], { e, ps, url: m[1], cs }); } }
    }
  }
  for (const img of root.querySelectorAll("img")) {
    const r = img.getBoundingClientRect(), cs = getComputedStyle(img);
    out.push({ kind: "img", path: path(img), url: (img.currentSrc || img.src).split("/").pop().slice(0, 40), nw: img.naturalWidth, nh: img.naturalHeight, sw: r.width, sh: r.height,
      on: vis(r), hidden: cs.display === "none" || cs.visibility === "hidden" || +cs.opacity === 0, loading: img.loading, decoding: img.decoding });
  }
  const loads = [...bgs.values()].map(b => new Promise(res => { const im = new Image(); im.onload = () => res({ b, nw: im.naturalWidth, nh: im.naturalHeight }); im.onerror = () => res({ b, nw: 0, nh: 0 }); im.src = b.url; setTimeout(() => res({ b, nw: -1, nh: -1 }), 1500); }));
  for (const { b, nw, nh } of await Promise.all(loads)) {
    const r = b.e.getBoundingClientRect(), bs = b.cs.backgroundSize;
    out.push({ kind: "bg" + (b.ps || ""), path: path(b.e), url: b.url.split("/").pop().slice(0, 40).replace(/^data:image\\/(\\w+).*/, "data:$1"), nw, nh, sw: r.width, sh: r.height, size: bs,
      on: vis(r), hidden: b.cs.display === "none" || b.cs.visibility === "hidden" || +b.cs.opacity === 0 });
  }
  const anims = document.getAnimations().filter(a => { const t = a.effect && a.effect.getComputedTiming(); return t && (t.iterations === Infinity); })
    .map(a => ({ el: a.effect.target ? path(a.effect.target) + (a.effect.pseudoElement || "") : "?", name: a.animationName || a.id || "waapi", state: a.playState }));
  const wc = [...document.querySelectorAll("*")].filter(e => /transform|opacity|filter|scroll|left|top/.test(getComputedStyle(e).willChange)).map(e => ({ el: path(e), wc: getComputedStyle(e).willChange }));
  const sc = [...document.querySelectorAll("#full, #full *")].filter(e => e.scrollHeight > e.clientHeight + 10 && /auto|scroll/.test(getComputedStyle(e).overflowY)).map(e => path(e) + " " + e.clientHeight + "/" + e.scrollHeight);
  return { dpr, vh, imgs: out, anims, wc, sc };
})()`;
const report = (w, label, s, top) => {
  const dpr = s.dpr; let dec = 0, need = 0, decOn = 0, n = 0;
  const rows = s.imgs.filter(x => x.nw > 0).map(x => { const d = x.nw * x.nh / 1e6, nd = x.sw * x.sh * dpr * dpr / 1e6; return { ...x, d, nd, ratio: nd ? d / nd : 0 }; });
  for (const r of rows) { n++; dec += r.d; need += r.nd; if (r.on && !r.hidden) decOn += r.d; }
  const uniq = new Map(); for (const r of rows) { const k = r.url + r.nw; if (!uniq.has(k)) uniq.set(k, r.d); }
  const uniqMP = [...uniq.values()].reduce((a, b) => a + b, 0);
  console.log(`\n  [${w}px ${label}] ${s.imgs.length} picture refs (${n} loaded, ${uniq.size} distinct files): decoded ${dec.toFixed(1)} MP (distinct ${uniqMP.toFixed(1)} MP, on-screen ${decOn.toFixed(1)} MP), needed at shown size ${need.toFixed(1)} MP, ${rows.filter(r => r.on && !r.hidden).length} on screen, ${rows.filter(r => r.hidden).length} hidden/0-opacity`);
  for (const r of rows.sort((a, b) => b.d - a.d).slice(0, top))
    console.log(`     ${r.d.toFixed(2).padStart(6)} MP ${String(r.nw + "x" + r.nh).padEnd(10)} -> ${String(Math.round(r.sw * dpr) + "x" + Math.round(r.sh * dpr)).padEnd(10)} x${r.ratio.toFixed(1).padStart(5)} ${(r.on ? "ON " : "off") + (r.hidden ? "/hid" : "    ")} ${r.kind.padEnd(8)} ${r.url.padEnd(22)} ${r.path.slice(-90)}`);
  console.log(`     endless animations: ${s.anims.length}` + (s.anims.length ? " " + [...new Set(s.anims.map(a => a.name + "@" + a.el.split(" > ").pop()))].slice(0, 8).join(" | ") : "") + `; will-change: ${s.wc.length}` + (s.wc.length ? " " + s.wc.slice(0, 8).map(x => x.el.split(" > ").pop() + ":" + x.wc).join(" | ") : ""));
  console.log(`     scrollers: ${s.sc.slice(0, 5).join(" | ")}`);
};
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const top = +(process.env.TOP || 10);
  /* "rich" = key art plus 14 screenshots (SHOT=big uses the 12 MP key art as every screenshot, the worst case a host shotThumb does not shrink) */
  for (const [name, gid] of [["key art", process.env.GAME || "g0"], ["no key art", "g1"], ["rich: key art + 14 shots", "g4"]]) {
    await p.step("close", `closeFull()`, 500);
    if (name.startsWith("rich")) await p.page.evaluate(([id, f]) => { const g = S.games.find(x => x.id === id); g.key = "fefw-key-art.jpg"; g.shots = Array.from({ length: 14 }, (_, i) => ({ u: f + "?i=" + i })); g.shotsSrc = "test"; saveNow(); }, [gid, process.env.SHOT === "big" ? "fefw-key-art.jpg" : "FEFT_cast.webp"]);
    await p.step("open " + gid, `goTab("games"); openFull(${JSON.stringify(gid)})`, 3000);
    const key = await p.page.evaluate(id => (S.games.find(g => g.id === id) || {}).key, gid);
    console.log(`\n=== ${w}px game ${gid} (${name}; key=${key ? "yes" : "no"}) ===`);
    for (let k = 0; k <= 3; k++) {
      if (k) {
        if (process.env.REAL) { await p.swipe(p.w * .5, p.h * .85, p.w * .52, p.h * .15, 260, 12); }
        else await p.page.evaluate(() => { const els = [...document.querySelectorAll("#full.on .hub, #full.on")].filter(e => e.scrollHeight > e.clientHeight + 10); (els[0] || document.scrollingElement).scrollBy(0, innerHeight); });
        await p.page.waitForTimeout(1200);
      }
      const s = await p.page.evaluate(SNAP);
      report(w, k ? `scrolled ${k} screen${k > 1 ? "s" : ""}` : "at rest", s, top);
      const c = await countLayers(p);
      const by = Object.entries(c.by).sort((a, b) => b[1].mp - a[1].mp).map(([r, x]) => `${r} ${x.n} (${x.mp.toFixed(1)}MP)`).join(" · ");
      console.log(`     GPU layers: ${c.n}, ${c.mp} MP, ${c.screens} screens; ${by}`);
      if (k === 0 || k === 3) for (const r of [...c.rows].sort((a, b) => b.mp - a.mp).slice(0, 8)) console.log(`        ${r.mp.toFixed(2).padStart(6)} MP ${String(r.w + "x" + r.h).padEnd(10)} ${r.why.padEnd(30)} ${r.d}`);
    }
  }
  return true;
};
