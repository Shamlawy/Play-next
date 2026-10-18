/* Does a long stay on the Queue wear the app down? (the owner, Oct 18: "I spent time on the game queue and it lagged a lot and
   the games stopped opening"). soak.mjs (v304) only switches tabs and opens games by code; this one uses the Queue the way a
   hand does, with real touch: tap a row open, two taps to turn the card, two taps back, one tap to close it, a tap on the box art
   to open the game page, back, a scroll. After each round it prints what must stay flat (DOM nodes, listeners, heap after a GC,
   animations, timers still pending) and how long a card and a game page take to open, and fails when a game page stops opening
   or a card stops turning / closing.
     node tools/kit.mjs run tools/scenarios/soak-queue.mjs --widths 859      (ROUNDS=10) */
const ROUNDS = +process.env.ROUNDS || 10;
export default async ({ p, w, fail, seed }) => {
  const pg = p.page;
  /* a long list (GAMES=60), like a real queue */
  const v = await pg.evaluate(() => APP_V);
  await pg.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, +process.env.GAMES || 60)));
  await pg.reload(); await pg.waitForTimeout(3000);
  await pg.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, "");
    const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  /* count pending timers: the page's own setTimeout/setInterval, wrapped once */
  await pg.evaluate(() => { if (window.__tm) return; const live = new Set(); window.__tm = live;
    const st = window.setTimeout, ct = window.clearTimeout, si = window.setInterval, ci = window.clearInterval;
    window.setTimeout = function (f, ms, ...a) { const id = st.call(window, (...b) => { live.delete(id); if (typeof f === "function") f(...b); }, ms, ...a); live.add(id); return id; };
    window.clearTimeout = id => { live.delete(id); ct.call(window, id); };
    window.setInterval = function (...a) { const id = si.apply(window, a); live.add("i" + id); return id; };
    window.clearInterval = id => { live.delete("i" + id); ci.call(window, id); }; });
  /* when each tap really landed (pointerup's timeStamp): under a slowed CPU the test's own two taps can land 300ms+ apart,
     which is two single taps for anyone, so a turn is only checked for taps that were quick */
  await pg.evaluate(() => { window.__up = []; document.addEventListener("pointerup", e => window.__up.push(e.timeStamp), true); });
  await p.cdp.send("Performance.enable");
  if (+process.env.CPU > 1) await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: +process.env.CPU });
  const metrics = async () => {
    await p.cdp.send("HeapProfiler.collectGarbage").catch(() => {});
    const m = Object.fromEntries((await p.cdp.send("Performance.getMetrics")).metrics.map(x => [x.name, x.value]));
    const a = await pg.evaluate(() => ({ anims: document.getAnimations().length, timers: window.__tm.size, busy: document.querySelectorAll("[data-busy]").length }));
    return { nodes: m.Nodes, listeners: m.JSEventListeners, heapMB: +(m.JSHeapUsedSize / 1048576).toFixed(1), ...a };
  };
  const center = async sel => { const l = pg.locator(sel).first(); if (!(await l.count())) return null; const b = await l.boundingBox({ timeout: 2000 }).catch(() => null); return b && [b.x + b.width / 2, b.y + b.height / 2, b]; };
  await p.step("queue", `goTab("games")`, 800);
  const rows = [{ round: 0, ...(await metrics()) }];
  let fails = 0, slowTaps = 0;
  for (let r = 1; r <= ROUNDS; r++) {
    let openMs = [], gameMs = [];
    for (let k = 0; k < 4; k++) {
      await pg.evaluate(() => { document.querySelector("#full.on") && closeFull(); goTab("games"); scrollTo(0, 0); });
      await pg.waitForTimeout(300);
      /* open the k-th closed row */
      const id = await pg.evaluate(k => { const r = [...document.querySelectorAll("#p-queue [data-row]:not(.hero)")][k]; if (!r) return null; r.scrollIntoView({ block: "center", behavior: "instant" }); return r.dataset.row; }, k);
      if (!id) { fail(`round ${r}: no row ${k}`); continue; }
      await pg.waitForTimeout(400);
      const c = await center(`#p-queue [data-row="${id}"]`);
      const t0 = Date.now(); await p.tap(c[0], c[1]);
      const opened = await pg.waitForFunction(id => !!document.querySelector(`#p-queue .hero[data-row="${CSS.escape(id)}"] .pfront`), id, { timeout: 3000 }).then(() => true, () => false);
      openMs.push(Date.now() - t0);
      if (!opened) { fail(`round ${r}: tapping a row didn't open its card`); fails++; continue; }
      await pg.waitForTimeout(700);
      /* two taps → turned, two taps → front again */
      for (const want of [true, false]) {
        const hc = await center("#p-queue .hero .hflip"); if (!hc) { fail(`round ${r}: the open card is gone`); fails++; break; }
        const b = hc[2];
        const x = b.x + b.width - 40, y = b.y + b.height * 0.42;
        await pg.evaluate(() => { window.__up = []; });
        await p.tap(x, y); await pg.waitForTimeout(60); await p.tap(x, y); await pg.waitForTimeout(700);
        const [on, gap] = await pg.evaluate(() => { const h = document.querySelector("#p-queue .hero .hflip"), u = window.__up; return [h ? h.classList.contains("on") : null, u.length > 1 ? u[1] - u[0] : 0]; });
        if (gap > 280) { slowTaps++; if (on === null) break; continue; }
        if (on !== want) { fail(`round ${r}: two taps didn't turn the card ${want ? "over" : "back"} (on=${on})`); fails++; }
      }
      /* the box art opens the game page */
      const cov = await center("#p-queue .hero .qkcov");
      if (cov) {
        const t1 = Date.now(); await p.tap(cov[0], cov[1]);
        const ok = await pg.waitForFunction(() => !!document.querySelector("#full.on"), null, { timeout: 3000 }).then(() => true, () => false);
        gameMs.push(Date.now() - t1);
        if (!ok) { fail(`round ${r}: the box art didn't open the game page`); fails++; }
        await pg.waitForTimeout(600);
        await pg.evaluate(() => typeof closeFull === "function" && closeFull()); await pg.waitForTimeout(500);
      }
      /* one tap closes the card */
      const h = await center("#p-queue .hero .hflip");
      if (h) { const b = h[2]; await p.tap(b.x + b.width - 40, b.y + b.height * 0.42); await pg.waitForTimeout(800);
        const still = await pg.evaluate(id => !!document.querySelector(`#p-queue .hero[data-row="${CSS.escape(id)}"]`), id);
        if (still) { fail(`round ${r}: one tap didn't close the card`); fails++; } }
      await p.swipe ? await p.swipe(w / 2, 600, w / 2, 250) : null; await pg.waitForTimeout(300);
    }
    const avg = a => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : "-";
    rows.push({ round: r, ...(await metrics()), openMs: avg(openMs), gameMs: avg(gameMs) });
    if (fails > 6) break;
  }
  if (slowTaps) console.log(`  (${slowTaps} double tap(s) not checked: the test's own taps landed 280ms+ apart)`);
  console.log(`  ${w}px  round  nodes  listeners  heapMB  anims  timers  busy  card ms  game ms`);
  for (const x of rows) console.log(`        ${String(x.round).padStart(3)}  ${String(x.nodes).padStart(6)}  ${String(x.listeners).padStart(8)}  ${String(x.heapMB).padStart(6)}  ${String(x.anims).padStart(5)}  ${String(x.timers).padStart(6)}  ${String(x.busy).padStart(4)}  ${String(x.openMs ?? "").padStart(7)}  ${String(x.gameMs ?? "").padStart(7)}`);
};
