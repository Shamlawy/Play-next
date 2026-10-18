/* What pictures Home holds, and how big they are against what is shown (stutter hunt, Oct 2026).
   Seeds 60 games (art = the 12 MP key art, like the owner's real big art), then reports for Home:
   at rest and after scrolling 1-2 screens, and 1s / 3s / 8s after a cold reload:
     - every <img> and every element with a CSS background-image url (also ::before / ::after): natural size
       (a CSS background is loaded in an Image) vs shown size x devicePixelRatio, ratio, MP decoded (natural, the worst case
       a decode can cost; Chrome may decode smaller), on screen or not
     - totals (count, MP natural vs MP needed), endless animations (document.getAnimations(), Infinity) and will-change.
   Note: the test phone blocks sw.js, so pictures here are the full files; on the real phone sw.js shrinks some of them.
     node tools/kit.mjs run tools/scenarios/measure-home.mjs --widths 412,859
   TOP=15 (rows), SCREEN=home|queue|lib|played (default home), SKIPCOLD=1 (skip the 1/3/8s part). */
const SCREENS = { home: `closeFull(); goTab("home")`, queue: `closeFull(); goTab("games"); goSub("games", "queue")`,
  lib: `closeFull(); goTab("games"); goSub("games", "lib")`, played: `closeFull(); goTab("games"); goSub("games", "played")` };

const collect = async page => {
  const raw = await page.evaluate(() => {
    const dpr = devicePixelRatio, vw = innerWidth, vh = innerHeight;
    const path = el => { const out = []; for (let e = el, i = 0; e && e.nodeType === 1 && i < 4; e = e.parentElement, i++) out.push(e.nodeName.toLowerCase() + (e.id ? "#" + e.id : "") + (e.className && typeof e.className === "string" ? "." + e.className.trim().split(/\s+/).slice(0, 2).join(".") : "")); return out.join(" < "); };
    /* is this rect actually visible: inside the viewport and not clipped away by an ancestor scroller/overflow */
    const visible = (el, r) => {
      if (r.right <= 0 || r.bottom <= 0 || r.left >= vw || r.top >= vh) return false;
      for (let a = el.parentElement; a && a !== document.documentElement; a = a.parentElement) {
        const cs = getComputedStyle(a); if (cs.overflowX === "visible" && cs.overflowY === "visible") continue;
        const ar = a.getBoundingClientRect(); if (r.right <= ar.left || r.left >= ar.right || r.bottom <= ar.top || r.top >= ar.bottom) return false;
      }
      return true;
    };
    const rows = [];
    for (const el of document.querySelectorAll("*")) {
      if (el.closest("svg") && el.nodeName.toLowerCase() !== "svg") continue;
      const r = el.getBoundingClientRect(), shown = getComputedStyle(el).display !== "none" && r.width > 0 && r.height > 0;
      if (el.nodeName === "IMG") {
        rows.push({ kind: "img", url: el.currentSrc || el.src, nw: el.naturalWidth, nh: el.naturalHeight, w: r.width, h: r.height, shown, on: shown && visible(el, r), path: path(el), lazy: el.loading || "", dec: el.decoding || "" });
        continue;
      }
      for (const ps of [null, "::before", "::after"]) {
        const cs = getComputedStyle(el, ps);
        if (ps && (cs.content === "none" || cs.content === "normal")) continue;
        const m = cs.backgroundImage && cs.backgroundImage.match(/url\((["']?)(.*?)\1\)/g);
        if (!m) continue;
        for (const u of m) rows.push({ kind: "bg" + (ps || ""), url: u.replace(/^url\((["']?)/, "").replace(/(["']?)\)$/, ""), w: r.width, h: r.height,
          shown: shown || (!!ps && r.width > 0 && r.height > 0), on: (shown || !!ps) && visible(el, r), path: path(el) + (ps || ""), size: cs.backgroundSize });
      }
    }
    const anims = document.getAnimations().filter(a => { const t = a.effect && a.effect.getComputedTiming && a.effect.getComputedTiming(); return t && t.iterations === Infinity; })
      .map(a => { const t = a.effect.target; return (a.animationName || a.transitionProperty || "(waapi)") + " on " + (t ? (t.nodeName.toLowerCase() + (t.id ? "#" + t.id : "") + (t.className && typeof t.className === "string" ? "." + t.className.trim().split(/\s+/).slice(0, 2).join(".") : "")) : "?") + (a.playState !== "running" ? " [" + a.playState + "]" : ""); });
    const wc = [...document.querySelectorAll("*")].filter(e => getComputedStyle(e).willChange !== "auto").map(e => path(e).split(" < ")[0] + " (" + getComputedStyle(e).willChange + ")");
    return { rows, anims, wc, dpr, doc: [document.documentElement.scrollHeight, innerHeight], res: performance.getEntriesByType("resource").filter(r => r.initiatorType === "img" || r.initiatorType === "css" || /\.(jpe?g|png|webp|avif|gif|svg)/i.test(r.name)).map(r => r.name.split("/").pop().slice(0, 40)) };
  });
  /* natural size of CSS backgrounds: load each url once in an Image */
  const need = [...new Set(raw.rows.filter(r => r.kind !== "img" && r.nw == null).map(r => r.url))];
  const nat = await page.evaluate(async urls => Object.fromEntries(await Promise.all(urls.map(u => new Promise(res => {
    if (/^data:image\/svg/.test(u)) return res([u, [0, 0]]);
    const i = new Image(); i.onload = () => res([u, [i.naturalWidth, i.naturalHeight]]); i.onerror = () => res([u, [0, 0]]); i.src = u; setTimeout(() => res([u, [-1, -1]]), 4000); })))), need);
  for (const r of raw.rows) if (r.nw == null) [r.nw, r.nh] = nat[r.url] || [0, 0];
  for (const r of raw.rows) {
    r.nmp = r.nw * r.nh / 1e6; r.need = r.shown ? r.w * r.h * raw.dpr * raw.dpr / 1e6 : 0;
    r.ratio = r.need > 0 ? r.nmp / r.need : 0; r.name = r.url.startsWith("data:") ? "data:" + r.url.slice(5, 20) + "…" : r.url.split("/").pop().slice(0, 36);
  }
  return raw;
};
const report = (label, d, top) => {
  const rows = d.rows.filter(r => r.nw > 0), on = rows.filter(r => r.on), off = rows.filter(r => !r.on);
  const sum = (a, k) => a.reduce((x, r) => x + r[k], 0);
  /* the same url used several times is decoded once by Chrome: count unique urls for the decode bill */
  const uniq = a => { const m = new Map(); for (const r of a) if (!m.has(r.url)) m.set(r.url, r); return [...m.values()]; };
  console.log(`\n  --- ${label} (doc ${d.doc[0]}px tall, viewport ${d.doc[1]}, dpr ${d.dpr}) ---`);
  console.log(`  pictures: ${rows.length} uses, ${uniq(rows).length} unique files · on screen ${on.length} uses (${uniq(on).length} files, natural ${sum(uniq(on), "nmp").toFixed(1)} MP, needed ${sum(on, "need").toFixed(1)} MP) · off screen ${off.length} uses (${uniq(off.filter(r => !on.some(o => o.url === r.url))).length} files only off-screen, natural ${sum(uniq(off.filter(r => !on.some(o => o.url === r.url))), "nmp").toFixed(1)} MP)`);
  console.log(`  total natural (unique files) ${sum(uniq(rows), "nmp").toFixed(1)} MP vs needed (all uses) ${sum(rows, "need").toFixed(1)} MP`);
  console.log("  top by natural MP (unique file, biggest use):");
  const per = new Map(); for (const r of rows) { const o = per.get(r.url); if (!o) per.set(r.url, { ...r, uses: 1, usesOn: r.on ? 1 : 0 }); else { o.uses++; if (r.on) o.usesOn++; if (r.need > o.need) Object.assign(o, { w: r.w, h: r.h, need: r.need, ratio: r.ratio, path: r.path, kind: r.kind, shown: r.shown }); } }
  for (const r of [...per.values()].sort((a, b) => b.nmp - a.nmp).slice(0, top))
    console.log(`    ${r.nmp.toFixed(2).padStart(6)} MP ${(r.nw + "x" + r.nh).padEnd(10)} shown ${Math.round(r.w)}x${Math.round(r.h)} x${d.dpr} = ${r.need.toFixed(2)} MP ratio ${r.ratio.toFixed(1)}x · ${r.uses} uses (${r.usesOn} on screen) · ${r.kind} ${r.name} · ${r.path}${r.lazy ? " loading=" + r.lazy : ""}`);
  const worst = [...rows].filter(r => r.need > 0).sort((a, b) => b.ratio * b.nmp - a.ratio * a.nmp).slice(0, 3);
  console.log("  worst waste (natural MP x ratio): " + worst.map(r => `${r.name} ${r.ratio.toFixed(0)}x (${r.path.split(" < ")[0]}${r.on ? ", on screen" : ""})`).join(" · "));
  console.log(`  endless animations: ${d.anims.length}` + (d.anims.length ? " · " + [...new Set(d.anims)].slice(0, 14).join(" · ") : ""));
  console.log(`  will-change: ${d.wc.length}` + (d.wc.length ? " · " + [...new Set(d.wc)].slice(0, 12).join(" · ") : ""));
};
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V), top = +(process.env.TOP || 10), scr = SCREENS[process.env.SCREEN || "home"];
  const prep = () => p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload(); await p.page.waitForTimeout(3000); await prep();
  await p.step("screen", scr, 2600);
  report(`${process.env.SCREEN || "home"} at rest`, await collect(p.page), top);
  for (const n of [1, 2]) {
    await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .25, 220, 12); await p.page.waitForTimeout(1200);
    report(`${process.env.SCREEN || "home"} after scrolling ${n} swipe(s)`, await collect(p.page), top);
  }
  if (process.env.SKIPCOLD) return true;
  /* cold reloads: pictures present 1s / 3s / 8s after the page starts (what start decodes) */
  for (const at of [1000, 3000, 8000]) {
    await p.page.reload({ waitUntil: "commit" }); const t0 = Date.now();
    await p.page.waitForFunction(() => document.readyState !== "loading" || true).catch(() => {});
    await p.page.waitForTimeout(Math.max(0, at - (Date.now() - t0)));
    const d = await collect(p.page);
    report(`cold start +${at}ms`, d, 5);
    const first = d.rows.filter(r => r.nw > 0 && !r.on), seen = new Set(first.map(r => r.url));
    console.log(`  loaded but NOT on the first screen: ${seen.size} files, ${[...seen].reduce((a, u) => a + first.find(r => r.url === u).nmp, 0).toFixed(1)} MP natural · fetched so far: ${d.res.length} image requests`);
  }
  return true;
};
