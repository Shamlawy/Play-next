/* What the app keeps doing while nobody touches it, and whether anyone can see it (step 3b of the Oct 2026 clean-up, the
   owner: "keep checking … something switched off or hidden that still runs timers/listeners/redraws").
   For each screen, after it settles and with nobody touching:
   - every running endless animation, sorted into: on screen (drawn, in view, not under a full-screen layer), hidden (its
     screen or row isn't drawn), off screen (drawn but scrolled out of view), covered (under the game page, a sheet, chat…);
     "slow" = it animates more than transform/opacity
   - every setInterval, subtree MutationObserver and requestAnimationFrame caller that ran in the idle window: calls and ms
   Then the same with the switch-off features off (rail hidden, Grumble off, motion off) and with the app in the background.
     node tools/kit.mjs run tools/scenarios/idle-work.mjs --widths 412,859      (IDLE=8000 sets the idle window, ms) */
const IDLE = +process.env.IDLE || 6000;
const TOT = { n: 0, ms: 0 };
export default async ({ p, w }) => {
  /* count every timer, observer and frame callback by the line that made it (inline scripts report page lines) */
  await p.ctx.addInitScript(() => {
    const W = window.__iw = { on: false, t: {}, sizes: {} };
    const line = () => { const s = (new Error().stack || "").split("\n").slice(3).find(l => /:\d+:\d+\)?$/.test(l) && !/__iw/.test(l)) || "";
      const m = s.match(/:(\d+):\d+\)?$/); return m ? "line " + m[1] : "?"; };
    const rec = (k, ms) => { if (!W.on) return; const o = W.t[k] || (W.t[k] = { n: 0, ms: 0 }); o.n++; o.ms += ms; };
    const si = setInterval;
    window.setInterval = function (fn, ms, ...a) { const k = `setInterval ${line()} (${ms}ms)`;
      return si.call(this, typeof fn === "function" ? function () { const t = performance.now(); try { return fn.apply(this, arguments); } finally { rec(k, performance.now() - t); } } : fn, ms, ...a); };
    const MO = MutationObserver;
    window.MutationObserver = class extends MO { constructor(cb) { const k = "observer " + line();
      super(function (ms, o) { const t = performance.now(); try { return cb.call(this, ms, o); } finally { rec(k, performance.now() - t); } }); this.__k = k; }
      observe(t, o) { if (o && o.subtree) W.sizes[this.__k] = "subtree of " + (t.id ? "#" + t.id : t.tagName ? t.tagName.toLowerCase() : "?") + (o.attributes ? " +attributes" : "") + (o.characterData ? " +text" : ""); return super.observe(t, o); } };
    const raf = requestAnimationFrame;
    window.requestAnimationFrame = function (fn) { const k = "frame " + line();
      return raf.call(this, function (ts) { const t = performance.now(); try { return fn(ts); } finally { rec(k, performance.now() - t); } }); };
  });
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });

  const loops = () => p.page.evaluate(() => {
    const kill = document.createElement("style"); kill.textContent = "*,*::before,*::after{pointer-events:auto!important}"; document.head.appendChild(kill);
    /* a layer that covers most of the screen with something solid */
    const solid = el => { const cs = getComputedStyle(el), r = el.getBoundingClientRect();
      if (r.width * r.height < innerWidth * innerHeight * .6 || cs.visibility === "hidden" || +cs.opacity < .9) return false;
      const bg = cs.backgroundColor.match(/[\d.]+/g); return (bg && (bg.length < 4 || +bg[3] > .85)) || cs.backgroundImage !== "none" || cs.backdropFilter !== "none"; };
    const out = {};
    for (const a of document.getAnimations()) {
      const t = a.effect && a.effect.target, tm = a.effect && a.effect.getTiming();
      if (!t || a.playState !== "running" || !tm || tm.iterations !== Infinity) continue;
      const props = new Set(); for (const k of a.effect.getKeyframes()) for (const x of Object.keys(k)) if (!/^(offset|easing|composite|computedOffset)$/.test(x)) props.add(x);
      const slow = [...props].filter(x => !/^(transform|opacity|translate|scale|rotate)$/.test(x));
      let where;
      if (!t.checkVisibility({ opacityProperty: true, visibilityProperty: true, contentVisibilityAuto: true })) where = "hidden";
      else { const r = t.getBoundingClientRect();
        if (r.bottom < 0 || r.top > innerHeight || r.right < 0 || r.left > innerWidth || r.width * r.height < 1) where = "off screen";
        else { const x = Math.min(innerWidth - 1, Math.max(0, r.left + r.width / 2)), y = Math.min(innerHeight - 1, Math.max(0, r.top + r.height / 2));
          const stack = document.elementsFromPoint(x, y), mine = e => e === t || t.contains(e) || e.contains(t);
          const ti = stack.findIndex(mine), oi = stack.findIndex(e => !mine(e) && solid(e));
          where = oi >= 0 && (ti < 0 || oi < ti) ? "covered by " + (stack[oi].id ? "#" + stack[oi].id : stack[oi].tagName.toLowerCase() + "." + (stack[oi].classList[0] || "")) : "on screen"; } }
      const own = t.closest("[id]");
      const k = `${a.animationName || "script"} on ${t.tagName.toLowerCase()}${t.classList[0] ? "." + t.classList[0] : ""}${a.effect.pseudoElement || ""} (in #${own ? own.id : "?"})${slow.length ? " SLOW:" + slow.join(",") : ""}`;
      const o = out[where] || (out[where] = {}); o[k] = (o[k] || 0) + 1;
    }
    kill.remove();
    return out;
  });
  const idle = async () => {
    await p.page.evaluate(() => { __iw.t = {}; __iw.on = true; });
    await p.page.waitForTimeout(IDLE);
    return p.page.evaluate(() => { __iw.on = false; return { t: __iw.t, sizes: __iw.sizes }; });
  };
  const show = (name, l, r) => {
    const n = o => Object.values(o || {}).reduce((a, x) => a + x, 0);
    console.log(`  ${w}px ${name}: ` + Object.entries(l).map(([k, o]) => `${n(o)} ${k}`).join(", "));
    for (const [where, o] of Object.entries(l)) if (where !== "on screen" || process.env.ALL || /motion off/.test(name)) for (const [k, c] of Object.entries(o).sort((a, b) => b[1] - a[1])) console.log(`      ${where}: ${c}× ${k}`);
    if (r) { const rows = Object.entries(r.t).sort((a, b) => b[1].ms - a[1].ms);
      const tn = rows.reduce((a, [, o]) => a + o.n, 0), tm = rows.reduce((a, [, o]) => a + o.ms, 0); TOT.n += tn; TOT.ms += tm;
      console.log(`      idle ${IDLE / 1000}s (${tn} calls, ${tm.toFixed(1)}ms): ` + (rows.length ? rows.map(([k, o]) => `${k}${r.sizes[k] ? " [" + r.sizes[k] + "]" : ""} ×${o.n} ${o.ms.toFixed(1)}ms`).join("; ") : "nothing ran")); }
  };
  const screens = [["home", `goTab("home")`], ["queue", `goTab("games")`], ["library", `goTab("lib")`], ["plan", `goTab("plan")`], ["taste", `goTab("taste")`],
    ["game page", `openFull("g1")`, `closeFull()`], ["settings", `openSettings()`, `closeEdit()`], ["chat", `nxChat()`, `dyClose()`]];
  for (const [name, js, close] of screens) {
    await p.step(name, js, 2500);
    const r = await idle(); show(name, await loops(), r);
    if (name === "home") { await p.scroll(1400); await p.page.waitForTimeout(800); show("home scrolled down", await loops()); await p.scroll(-3000); }
    if (close) await p.step("close", close, 900);
  }
  /* background: the app is in another tab / the phone is off */
  await p.step("home", `goTab("home")`, 1500);
  await p.page.evaluate(() => { Object.defineProperty(document, "hidden", { configurable: true, get: () => true }); Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" }); document.dispatchEvent(new Event("visibilitychange")); });
  const bg = await idle(); show("in the background (document.hidden)", {}, bg);
  await p.page.evaluate(() => { delete document.hidden; delete document.visibilityState; document.dispatchEvent(new Event("visibilitychange")); });
  /* switched off: motion off (still), the rail hidden, Grumble off (default) */
  await p.step("motion off", `S.motion = false; applyLook()`, 1500);
  show(`home, motion off (html.still: ${await p.page.evaluate(() => document.documentElement.classList.contains("still"))})`, await loops(), await idle());
  await p.step("motion on", `S.motion = true; applyLook()`, 800);
  console.log(`  ${w}px all idle windows: ${TOT.n} timer/observer/frame calls, ${TOT.ms.toFixed(1)}ms`); TOT.n = 0; TOT.ms = 0;
  if (p.errors.length) console.log("  script errors: " + [...new Set(p.errors)].join(" | "));
  return !p.errors.length;
};
