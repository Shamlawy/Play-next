/* one-off diagnosis of the reel in WebKit (covers 0, Skip, resize); not kept */
export default async ({ p, w }) => {
  const pg = p.page;
  pg.on("console", m => console.log("  [console] " + m.type() + " " + m.text().slice(0, 200)));
  await pg.evaluate(() => { localStorage.setItem("gq:reel", "1"); saveNow(); });
  await pg.reload(); await pg.waitForTimeout(3000);
  const a = await pg.evaluate(async () => {
    const out = { oc: typeof OffscreenCanvas, wk: typeof Worker, fq: typeof fitQueue, art: PNREEL.art && { n: PNREEL.art.covers.filter(Boolean).length, done: PNREEL.art.done } };
    out.imgs = S.games.filter(g => g.img).slice(0, 9).map(g => String(g.img).slice(0, 40));
    const u = (S.games.find(g => g.img && !/^https?:/.test(g.img)) || {}).img;
    out.u = u && String(u).slice(0, 60);
    if (u) {
      const src = `onmessage = async e => { const r = {}; try { const res = await fetch(e.data); r.status = res.status; const bl = await res.blob(); r.size = bl.size; r.type = bl.type;
        const bm = await createImageBitmap(bl); r.bm = [bm.width, bm.height]; const o = new OffscreenCanvas(60, 60), g = o.getContext("2d"); r.ctx = !!g; g.imageSmoothingQuality = "high"; g.drawImage(bm, 0, 0, 60, 60);
        const c = new OffscreenCanvas(30, 30), x = c.getContext("2d"); x.drawImage(o, 0, 0, 30, 30); const t = c.transferToImageBitmap(); r.out = [t.width, t.height]; postMessage(r, []); } catch (x) { r.err = String(x); postMessage(r); } };`;
      const wk = new Worker(URL.createObjectURL(new Blob([src], { type: "text/javascript" })));
      out.worker = await new Promise(res => { wk.onmessage = e => res(e.data); wk.onerror = e => res({ onerror: String(e.message || e) }); setTimeout(() => res("timeout"), 8000); wk.postMessage(new URL(u, location.href).href); });
      try { out.fit = await Promise.race([fitQueue(() => Promise.resolve("ran")), new Promise(r => setTimeout(() => r("fitQueue timeout"), 4000))]); } catch (e) { out.fit = "threw " + e; }
    }
    PNREEL.art = null; const t = performance.now(); try { await pnreelArt(); } catch (e) { out.artErr = String(e); }
    out.art2 = PNREEL.art && { n: PNREEL.art.covers.filter(Boolean).length, done: PNREEL.art.done, ms: Math.round(performance.now() - t) };
    return out; });
  console.log("  covers: " + JSON.stringify(a));
  const vp = pg.viewportSize();
  await pg.evaluate(() => { window.__rs = 0; addEventListener("resize", () => __rs++); window.__ro = 0; new ResizeObserver(() => __ro++).observe(document.getElementById("pnboot")); window.__vv = 0; visualViewport && visualViewport.addEventListener("resize", () => __vv++); });
  const r0 = await pg.evaluate(() => { const c = document.querySelector("#pnboot canvas"); return { iw: innerWidth, ih: innerHeight, cw: c.width, ch: c.height, dpr: devicePixelRatio, vv: visualViewport && visualViewport.width }; });
  await pg.setViewportSize({ width: 859, height: vp.height }); await pg.waitForTimeout(600);
  const r1 = await pg.evaluate(() => { const c = document.querySelector("#pnboot canvas"); return { rs: __rs, ro: __ro, vv2: __vv, iw: innerWidth, ih: innerHeight, cw: c.width, ch: c.height, dpr: devicePixelRatio, vv: visualViewport && visualViewport.width }; });
  console.log("  resize: before " + JSON.stringify(r0) + " after " + JSON.stringify(r1));
  await pg.setViewportSize(vp); await pg.waitForTimeout(600);
  await pg.reload(); await pg.waitForTimeout(800);
  const sk = await pg.evaluate(() => { const s = document.querySelector(".pnreel-skip"); if (!s) return null; const r = s.getBoundingClientRect(), x = r.x + r.width / 2, y = r.y + r.height / 2, at = document.elementFromPoint(x, y);
    window.__ev = []; ["touchstart", "touchend", "pointerdown", "pointerup", "click"].forEach(t => addEventListener(t, e => __ev.push(t + ":" + (e.target.className || e.target.tagName)), true));
    return { x, y, r: [r.x, r.y, r.width, r.height], at: at && (at.className || at.tagName), pe: getComputedStyle(s).pointerEvents, op: getComputedStyle(s).opacity }; });
  console.log("  skip: " + JSON.stringify(sk));
  if (sk) { await pg.touchscreen.tap(sk.x, sk.y); await pg.waitForTimeout(600);
    console.log("  after tap: " + JSON.stringify(await pg.evaluate(() => ({ ev: __ev, boot: !!document.getElementById("pnboot"), playing: !!PNREEL.playing })))); }
  return true;
};
