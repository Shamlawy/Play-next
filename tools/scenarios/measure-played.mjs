/* Played (games > played): pictures, endless animations, will-change and rows, at rest and mid-scroll.
     node tools/kit.mjs run tools/scenarios/measure-played.mjs --widths 412,859
   Per picture (img + CSS background-image on screen): natural size vs shown x dpr, ratio, decoded MP (natural w*h). */
const MEASURE = () => {
  const vw = innerWidth, vh = innerHeight, dpr = devicePixelRatio, out = [];
  const on = r => r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw;
  const seen = new Map();
  const add = (kind, url, nw, nh, r, el) => {
    const sw = Math.round(r.width), sh = Math.round(r.height), need = Math.max(1, Math.round(sw * dpr) * Math.round(sh * dpr));
    const mp = nw * nh / 1e6, key = url;
    const row = { kind, url: url.slice(-40), nat: nw + "x" + nh, shown: sw + "x" + sh, ratio: +(nw * nh / need).toFixed(1), mp: +mp.toFixed(2), cls: (el.className && el.className.baseVal === undefined ? String(el.className) : "").slice(0, 30) };
    if (!seen.has(key)) seen.set(key, row); else seen.get(key).uses = (seen.get(key).uses || 1) + 1;
  };
  const imgs = [...document.images].filter(i => i.complete && i.naturalWidth && on(i.getBoundingClientRect()) && i.checkVisibility && i.checkVisibility());
  for (const i of imgs) add("img", i.currentSrc || i.src, i.naturalWidth, i.naturalHeight, i.getBoundingClientRect(), i);
  const bgs = [];
  for (const el of document.querySelectorAll("body *, .pnb-row")) {
    for (const ps of [null, "::before", "::after"]) {
      const cs = getComputedStyle(el, ps); if (cs.backgroundImage === "none" || !/url\(/.test(cs.backgroundImage)) continue;
      const r = el.getBoundingClientRect(); if (!on(r) || !el.checkVisibility()) continue;
      if (cs.display === "none") continue;
      for (const m of cs.backgroundImage.matchAll(/url\("?([^")]+)"?\)/g)) bgs.push({ el, ps, url: m[1], r, cls: (ps || "") + (typeof el.className === "string" ? el.className : "") });
    }
  }
  return { imgs, bgs: bgs.map(b => ({ url: b.url, w: b.r.width, h: b.r.height, cls: b.cls })), out, vw, vh, dpr, seenRows: [...seen.values()] };
};
export default async ({ p, w, seed }) => {
  const page = p.page;
  const v = await page.evaluate(() => APP_V);
  await page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await page.reload(); await page.waitForTimeout(3000);
  await page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.step("played", `closeFull(); goTab("games"); goSub("games", "played")`, 2600);
  const snap = async (label) => {
    const r = await page.evaluate(async () => {
      const vw = innerWidth, vh = innerHeight, dpr = devicePixelRatio;
      const on = r => r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw;
      const nat = async url => new Promise(res => { const i = new Image(); i.onload = () => res([i.naturalWidth, i.naturalHeight]); i.onerror = () => res([0, 0]); i.src = url; });
      const pics = new Map();
      const push = (kind, url, nw, nh, w, h, cls) => {
        const need = Math.max(1, Math.round(w * dpr) * Math.round(h * dpr)), k = url;
        if (!pics.has(k)) pics.set(k, { kind, url: url.length > 44 ? "…" + url.slice(-43) : url, nat: nw + "x" + nh, shown: Math.round(w) + "x" + Math.round(h), ratio: +(nw * nh / need).toFixed(1), mp: +(nw * nh / 1e6).toFixed(2), uses: 1, cls });
        else pics.get(k).uses++;
      };
      for (const i of document.images) { const r = i.getBoundingClientRect(); if (i.naturalWidth && on(r) && i.checkVisibility()) push("img", i.currentSrc || i.src, i.naturalWidth, i.naturalHeight, r.width, r.height, String(i.className || "").slice(0, 24)); }
      const todo = [];
      for (const el of document.querySelectorAll("body *")) {
        const r = el.getBoundingClientRect(); if (!on(r)) continue;
        for (const ps of [null, "::before", "::after"]) {
          const cs = getComputedStyle(el, ps); if (!cs.backgroundImage.includes("url(")) continue;
          if (!el.checkVisibility()) continue;
          for (const m of cs.backgroundImage.matchAll(/url\(["']?([^"')]+)["']?\)/g)) todo.push([m[1], r.width, r.height, (ps || "") + " " + (typeof el.className === "string" ? el.className : "").slice(0, 24)]);
        }
      }
      for (const [u, ww, hh, c] of todo) { if (pics.has(u)) { pics.get(u).uses++; continue; } const [nw, nh] = u.startsWith("data:image/svg") ? [0, 0] : await nat(u); push("bg", u, nw, nh, ww, hh, c); }
      const arr = [...pics.values()];
      const endless = document.getAnimations().filter(a => { const t = a.effect && a.effect.getComputedTiming(); return t && t.iterations === Infinity; }).map(a => (a.effect.target ? a.effect.target.tagName.toLowerCase() + "." + String(a.effect.target.className).slice(0, 20) : "?") + ":" + (a.animationName || a.transitionProperty || "wapi"));
      const all = document.getAnimations(), running = all.filter(a => a.playState === "running");
      const wc = [...document.querySelectorAll("body *")].filter(e => /transform|opacity|filter|scroll/.test(getComputedStyle(e).willChange)).map(e => e.tagName.toLowerCase() + "." + String(e.className).slice(0, 20));
      const rows = document.querySelectorAll("#p-played .pnb-row"), onScr = [...rows].filter(r => on(r.getBoundingClientRect())).length;
      const scrollTimeline = all.filter(a => a.timeline && a.timeline.constructor.name === "ViewTimeline").length;
      const sc = document.scrollingElement;
      return { arr, endless, wc, rows: rows.length, onScr, anims: all.length, running: running.length, scrollTimeline, scrollTop: Math.round(sc.scrollTop), scrollH: sc.scrollHeight, domEls: document.getElementsByTagName("*").length };
    });
    const tot = r.arr.reduce((a, x) => a + x.mp, 0), totShown = r.arr.reduce((a, x) => { const [sw, sh] = x.shown.split("x").map(Number); return a + sw * sh * 1; }, 0);
    console.log(`\n  [${label}] ${w}px: scrollTop ${r.scrollTop}/${r.scrollH}, rows in DOM ${r.rows}, on screen ${r.onScr}, DOM elements ${r.domEls}`);
    console.log(`    pictures on screen ${r.arr.length}, decoded ${tot.toFixed(1)} MP (natural size, unshrunk); animations ${r.anims} (${r.running} running, ${r.scrollTimeline} scroll-driven); endless: ${r.endless.length ? r.endless.join(", ") : "none"}; will-change: ${r.wc.length}${r.wc.length ? " (" + r.wc.slice(0, 6).join(", ") + ")" : ""}`);
    for (const x of [...r.arr].sort((a, b) => b.mp - a.mp).slice(0, 10)) console.log(`      ${x.mp.toFixed(2).padStart(6)} MP ${x.kind.padEnd(3)} nat ${x.nat.padEnd(10)} shown ${x.shown.padEnd(9)} x${(x.ratio + "").padEnd(6)} uses ${x.uses}  ${x.cls.trim()}  ${x.url}`);
  };
  await snap("rest");
  await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .25, 200, 10);
  await snap("mid-scroll");
  await page.waitForTimeout(800);
  await snap("scrolled, settled");
  return true;
};
