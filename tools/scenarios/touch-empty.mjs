/* What a finger touch sets off. Taps (real touch through CDP) on empty spots of every main screen, a game page and
   Settings, and on one row/tile/button per screen, and lists anything that moves or appears in the next 0.8s: animations
   and transitions that start (by name and element), elements added, and looks that change on the touched element's
   chain. The owner (#309, v246: "when I touch anything an animation happened, delete that"; v280: "remove the touch glow
   and any touch effects when I touch an empty spot"). Fails on any effect from a touch on an empty spot, anything drawn
   into a touched row/tile/chip (v68's glow, removed in v280), and a :hover look that would stick after a touch.
     node tools/kit.mjs run tools/scenarios/touch-empty.mjs --widths 412,859 */
export default async ({ p, w }) => {
  /* finish the smoothness check; and Nexi's "I evolved" power-up (15s after the first load, on a timer, not a touch) */
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } S.nxEvo = 99; document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  /* a spot is empty when nothing under it can be tapped and it holds no text of its own */
  const TAPPABLE = ".nxh-say, button, a, input, select, textarea, label, summary, [role=button], [onclick], [tabindex], .row, .tile, .chip, .btn, .un, .pick, #pnbub, #hrail, nav, [class*=card], [data-hub], [data-opg], [data-open], [data-home-open], [data-tab], [data-sub]";
  const spots = () => p.page.evaluate(sel => {
    const out = [], hasData = el => { for (let x = el; x && x !== document.body; x = x.parentElement) for (const a of x.attributes) if (a.name.startsWith("data-") && !/^data-(v|sig|k|n)$/.test(a.name)) return true; return false; };
    for (let y = innerHeight * .12; y < innerHeight * .9 && out.length < 3; y += 37) for (let x = 14; x < innerWidth - 14 && out.length < 3; x += 41) {
      const el = document.elementFromPoint(x, y); if (!el || el.closest(sel) || hasData(el)) continue;
      if ([...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) continue;
      if (out.some(o => Math.hypot(o.x - x, o.y - y) < 120)) continue;
      out.push({ x: Math.round(x), y: Math.round(y), el: el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (el.classList[0] ? "." + el.classList[0] : "") });
    }
    return out;
  }, TAPPABLE);
  const thing = () => p.page.evaluate(() => {
    const el = document.querySelector(".panel.on .row, .panel.on .tile, .panel.on .chip, .panel.on .btn, #full .btn, #full .chip");
    if (!el) return null; const r = el.getBoundingClientRect(); if (r.width < 8 || r.bottom < 0 || r.top > innerHeight) return null;
    return { x: Math.round(r.left + Math.min(r.width / 2, 30)), y: Math.round(r.top + r.height / 2), el: el.tagName.toLowerCase() + (el.classList[0] ? "." + el.classList[0] : "") };
  });
  /* watch everything that starts after the finger lands; taps are swallowed so nothing opens */
  const touch = async s => {
    await p.page.evaluate(({ x, y, swallow }) => {
      const L = window.__touch = { anims: [], added: [], looks: [] };
      const name = el => el && el.tagName ? el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (el.classList && el.classList[0] ? "." + el.classList[0] : "") : "?";
      const KEYS = ["backgroundColor", "opacity", "transform", "scale", "translate", "filter", "boxShadow", "outlineColor", "color"];
      const chain = []; for (let x2 = document.elementFromPoint(x, y); x2 && x2 !== document.documentElement; x2 = x2.parentElement) chain.push(x2);
      L.chain = chain.map(el => ({ el, was: KEYS.map(k => getComputedStyle(el)[k]).join("|") }));
      /* Nexi's own parts (span.face, span.orb… inside #pnbub) don't carry his id in their names: skip them by where they sit.
         Seated he wakes only when he talks, and that comes from timers (the "I evolved" power-up 15s after load, a heads-up line), not the touch */
      L.on = e => e.target.closest && e.target.closest("#pnbub, #pnbubmsg") ? 0 : L.anims.push(`${e.type.replace("start", "")} ${e.animationName || e.propertyName} on ${name(e.target)}${e.pseudoElement || ""}`);
      ["animationstart", "transitionstart"].forEach(t => addEventListener(t, L.on, true));
      L.mo = new MutationObserver(ms => { for (const m of ms) for (const n of m.addedNodes) if (n.nodeType === 1) L.added.push(name(n) + " in " + name(m.target)); });
      L.mo.observe(document.body, { childList: true, subtree: true });
      L.eat = e => { e.stopPropagation(); e.preventDefault(); };
      if (swallow) addEventListener("click", L.eat, true);
    }, { x: s.x, y: s.y, swallow: true });
    await p.tap(s.x, s.y);
    await p.page.waitForTimeout(800);
    return p.page.evaluate(() => {
      const L = window.__touch, KEYS = ["backgroundColor", "opacity", "transform", "scale", "translate", "filter", "boxShadow", "outlineColor", "color"];
      ["animationstart", "transitionstart"].forEach(t => removeEventListener(t, L.on, true)); removeEventListener("click", L.eat, true); L.mo.disconnect();
      for (const c of L.chain) if (c.el.isConnected && KEYS.map(k => getComputedStyle(c.el)[k]).join("|") !== c.was) L.looks.push(c.el.tagName.toLowerCase() + (c.el.classList[0] ? "." + c.el.classList[0] : ""));
      /* not set off by the touch: Nexi (his eyes follow the finger, his moods run on timers) and the live countdown's digits */
      const own = s => !/#pnbub|\.eye|\.pupil|\bnx|\.aura|\.core|\.mouth|\.flame|\bssj|\.cd[ng]\b/.test(s);
      return { anims: [...new Set(L.anims)].filter(own), added: [...new Set(L.added)].filter(own), looks: [...new Set(L.looks)] };
    });
  };
  let bad = 0;
  for (const [name, js, close] of [["home", `goTab("home")`], ["queue", `goTab("games")`], ["library", `goTab("lib")`], ["plan", `goTab("plan")`],
    ["taste", `goTab("taste")`], ["game page", `openFull("g1")`, `closeFull()`], ["settings", `openSettings()`, `closeEdit()`]]) {
    await p.step(name, js, 2000);
    for (const s of await spots()) {
      const r = await touch(s);
      const fx = [...r.anims, ...r.added.map(x => "added " + x), ...r.looks.map(x => "look changed " + x)];
      if (fx.length) { bad++; console.log(`  ✗ ${w}px ${name}: empty spot ${s.el} (${s.x},${s.y}): ${fx.join("; ")}`); }
      else console.log(`  ✓ ${w}px ${name}: empty spot ${s.el}: nothing`);
      await p.step("back", (close ? close + ";" : "") + js, 900);
    }
    const t = await thing();
    if (t) {
      const r = await touch(t);
      /* something drawn into what you touched (v68's glow was a span.pnrip added to the row/tile/chip) */
      const glow = r.added.filter(x => x.endsWith(" in " + t.el) || /pnrip/.test(x));
      if (glow.length) { bad++; console.log(`  ✗ ${w}px ${name}: ${t.el}: a touch effect drawn into it: ${glow.join("; ")}`); }
      else console.log(`  ✓ ${w}px ${name}: ${t.el}: nothing drawn into it` + (r.anims.length ? ` (also: ${r.anims.join("; ")})` : ""));
    }
    if (close) await p.step("close", close, 700);
  }
  /* phones keep :hover on the last thing touched: every :hover rule, incl. CSS added by scripts, needs @media (hover: hover) */
  const sticky = await p.page.evaluate(() => { const out = [];
    const walk = (rules, ok) => { for (const r of rules) {
      if (r instanceof CSSMediaRule) walk(r.cssRules, ok || /\(\s*hover\s*:\s*hover\s*\)/.test(r.conditionText || r.media.mediaText));
      else if (r.cssRules && !(r instanceof CSSStyleRule)) walk(r.cssRules, ok);
      else if (r.selectorText && /:hover\b/.test(r.selectorText) && !ok) out.push(r.selectorText); } };
    for (const sh of document.styleSheets) try { walk(sh.cssRules, false); } catch (e) {}
    return out; });
  if (sticky.length) { bad++; console.log(`  ✗ ${w}px: :hover outside @media (hover: hover), sticks after a touch: ${sticky.join(" | ")}`); }
  else console.log(`  ✓ ${w}px: every :hover look is mouse-only`);
  return bad === 0;
};
