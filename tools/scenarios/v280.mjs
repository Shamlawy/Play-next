/* v280: (#8) a game page's sticky bar shows only once you've scrolled past the art, Back closes the page, the name scrolls
   back up; (#7) the reveal never leaves a card hidden: cards on screen when a page opens are never animated, and after a
   slow scroll or a fling every card is fully visible; (#9) the tour's ring sits on its target and stays on it while the page
   scrolls; (#1) Steam: link in Settings (fake helper), sessions from /steam/sync land on their games once (a second sync or
   an overlapping timer session doesn't log twice), the status line says so, Unlink clears it.
     CHROME=<path to Chrome 145+> node tools/kit.mjs run tools/scenarios/v280.mjs --widths 412,859
     SHOTS=dir … also saves the bar and the Steam settings (unlinked, linked) as pictures
   With an older Chromium the #7 checks still run (the script reveal); OLD=1 also turns off the bar and the anchored ring
   (as on Safari before 26) and checks the old paths. */
export default async ({ p, w }) => {
  const bad = [], pg = p.page, ev = (f, a) => pg.evaluate(f, a);
  /* OLD=1: as an older browser (Safari before 26): no anchor positioning, scroll-state or scroll triggers in script */
  if (process.env.OLD) { await p.ctx.addInitScript(() => { const s = CSS.supports.bind(CSS); CSS.supports = (...a) => /anchor|scroll-state|trigger/.test(a.join(" ")) ? false : s(...a); });
    await pg.reload(); await pg.waitForTimeout(2500); }
  const feat = await ev(() => ({ trig: PN_TRIG, gdsb: GDSB, anc: NX_ANC }));
  /* ---- #8 sticky bar ---- */
  await p.step("game", `openFull("g1")`, 1600);
  const bar = () => ev(() => { const b = document.querySelector("#full .hub.gpage .gdsb-in"); if (!b) return null; const c = getComputedStyle(b), r = b.getBoundingClientRect();
    return { vis: c.visibility, op: +c.opacity, top: Math.round(r.top), h: Math.round(r.height) }; });
  /* the seeded pages are short: make sure this one can scroll well past its art */
  await ev(() => { const b = document.querySelector("#full .hub.gpage .gd-body"); if (b) { const d = document.createElement("div"); d.style.height = "1600px"; b.appendChild(d); } });
  const b0 = await bar();
  if (feat.gdsb) {
    if (!b0 || b0.vis !== "hidden") bad.push("bar showing at the top of the page: " + JSON.stringify(b0));
    await ev(() => document.querySelector("#full .hub.gpage").scrollTo({ top: 1200 })); await pg.waitForTimeout(700);
    const b1 = await bar();
    if (process.env.SHOTS) await pg.screenshot({ path: `${process.env.SHOTS}/${w}-bar.png` });
    if (!b1 || b1.vis !== "visible" || b1.op < .99 || b1.top !== 0) bad.push("bar not shown after scrolling: " + JSON.stringify(b1));
    const t = await ev(() => { const r = document.querySelector("#full .gdsb-t").getBoundingClientRect(); return [r.left + 20, r.top + r.height / 2]; });
    await p.tap(t[0], t[1]); await pg.waitForTimeout(900);
    const top = await ev(() => document.querySelector("#full .hub.gpage").scrollTop);
    if (top > 2) bad.push("tapping the name didn't scroll up: " + top);
    const b2 = await bar(); if (!b2 || b2.vis !== "hidden") bad.push("bar still shown back at the top: " + JSON.stringify(b2));
    await ev(() => document.querySelector("#full .hub.gpage").scrollTo({ top: 1200 })); await pg.waitForTimeout(600);
    const x = await ev(() => { const r = document.querySelector("#full .gdsb-x").getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; });
    await p.tap(x[0], x[1]); await pg.waitForTimeout(900);
    if (await ev(() => getComputedStyle(document.getElementById("full")).display !== "none")) bad.push("Back in the bar didn't close the page");
  } else if (b0) bad.push("bar drawn without scroll-state support");
  /* ---- #7 reveals: nothing on screen is ever left hidden ---- */
  const hidden = sel => ev(sel => [...document.querySelectorAll(sel)].filter(e => { const r = e.getBoundingClientRect();
    return r.height > 4 && r.bottom > 10 && r.top < innerHeight - 10 && +getComputedStyle(e).opacity < .98; }).map(e => String(e.className).slice(0, 30)), sel);
  const anims = sel => ev(sel => [...document.querySelectorAll(sel)].filter(e => e.getAnimations().some(a => a.animationName === "pnTrig" && a.playState === "running")).length, sel);
  await p.step("game again", `closeFull(); openFull("g2")`, 1500);
  if (feat.trig && (await anims("#full .gd-body > *"))) bad.push("cards on screen animated when the page opened");
  for (let y = 0; y < 2400; y += 60) { await ev(y => { const h = document.querySelector("#full .hub.gpage"); if (h) h.scrollTop = y; }, y); await pg.waitForTimeout(16); }
  await pg.waitForTimeout(900);
  let hid = await hidden("#full .gd-body > *"); if (hid.length) bad.push("game page cards left hidden after a slow scroll: " + hid.join(", "));
  await p.step("insights", `closeFull(); goTab("taste"); goSub("taste", "insights")`, 1600);
  await p.scroll(3000); await pg.waitForTimeout(1000);
  hid = await hidden(".ix-rv"); if (hid.length) bad.push("Insights cards hidden after a fling: " + hid.join(", "));
  await p.step("home", `goTab("home")`, 1600);
  hid = await hidden("#p-home .dash > *"); if (hid.length) bad.push("Home cards hidden: " + hid.join(", "));
  /* ---- #9 tour ring ---- */
  await p.step("tour", `S.lastV = "279"; nxTour("279")`, 2600);
  const ring = () => ev(() => { const r = document.getElementById("nxring"); if (!r || !r.classList.contains("on")) return null;
    const a = r.getBoundingClientRect(); return { anc: r.classList.contains("anc"), x: Math.round(a.left), y: Math.round(a.top), w: Math.round(a.width), h: Math.round(a.height) }; });
  const target = () => ev(() => { const t = document.querySelector("#s-spbox"); if (!t) return null; const a = t.getBoundingClientRect(); return { x: Math.round(a.left) - 6, y: Math.round(a.top) - 6, w: Math.round(a.width) + 12, h: Math.round(a.height) + 12 }; });
  const near = (r, t) => r && t && Math.abs(r.x - t.x) <= 2 && Math.abs(r.y - t.y) <= 2 && Math.abs(r.w - t.w) <= 2 && Math.abs(r.h - t.h) <= 2;
  const r1 = await ring(), t1 = await target();
  if (!near(r1, t1)) bad.push("tour ring not on the Steam settings: " + JSON.stringify({ r1, t1 }));
  if (feat.anc && r1 && !r1.anc) bad.push("ring not anchored although the browser can");
  await p.scroll(-140); await pg.waitForTimeout(250);
  const r2 = await ring(), t2 = await target();
  if (r2 && !near(r2, t2)) bad.push("ring didn't follow the scroll: " + JSON.stringify({ r2, t2 }));
  for (let i = 0; i < 8; i++) { const more = await ev(() => { const b = document.querySelector('#nxtour.on [data-nx="next"]'); if (b) b.click(); return !!document.getElementById("nxtour"); }); if (!more) break; await pg.waitForTimeout(1500); }
  await pg.waitForTimeout(600);
  if (await ev(() => [...document.querySelectorAll("[style*=anchor-name]")].length)) bad.push("anchor-name left on an element after the tour");
  /* ---- #1 Steam ---- */
  const now = Date.now(), calls = [];
  const sessions = [{ k: "g1", a: 1, n: "Game one", s: now - 2 * 36e5, e: now - 36e5 }, { k: "g3", a: 0, n: "Game three", s: now - 5 * 36e5, e: now - 4.5 * 36e5 }, { k: "", a: 9, n: "Not mine", s: now - 3e6, e: now - 2e6 }];
  await p.ctx.route(u => /\/steam\/(me|sync)$/.test(u.pathname), r => { const u = r.request().url(), b = JSON.parse(r.request().postData() || "{}"); calls.push(u.split("/").pop() + (b.off ? ":off" : ""));
    const body = /\/me$/.test(u) ? (b.off ? { ok: true, off: true } : { ok: true, sid: "76561197960000001", name: "Tester", private: false, playing: null })
      : { ok: true, name: "Tester", live: { n: "Game two", k: "g2", s: now - 6e5 }, done: sessions };
    return r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body), headers: { "access-control-allow-origin": "*" } }); });
  await p.step("settings", `closeFull(); dyClose(); setJump({ scope: "app", group: "Steam", text: "Your Steam profile" })`, 1800);
  if (process.env.SHOTS) await pg.screenshot({ path: `${process.env.SHOTS}/${w}-steam-unlinked.png` });
  /* a timer session that overlaps the Game three one: that one must not be logged again */
  await ev(n => { sessAdd("g3", n - 4.8 * 36e5, n - 4.6 * 36e5); }, now);
  const before = await ev(() => ({ n: (S.sessions || []).length, h1: loggedOf(S.games.find(g => g.id === "g1")), h3: loggedOf(S.games.find(g => g.id === "g3")) }));
  await ev(() => { document.getElementById("s-spwho").value = "https://steamcommunity.com/id/tester/"; document.getElementById("s-splink").click(); });
  await pg.waitForTimeout(2200);
  if (process.env.SHOTS) { await ev(() => document.getElementById("s-spbox").scrollIntoView({ block: "center" })); await pg.waitForTimeout(300); await pg.screenshot({ path: `${process.env.SHOTS}/${w}-steam.png` }); }
  const st = await ev(() => ({ me: S.steamMe, txt: (document.getElementById("s-spst") || {}).textContent || "", n: (S.sessions || []).length,
    h1: loggedOf(S.games.find(g => g.id === "g1")), h3: loggedOf(S.games.find(g => g.id === "g3")) }));
  if (!st.me || st.me.sid !== "76561197960000001") bad.push("Steam not linked: " + JSON.stringify(st.me));
  if (st.n !== before.n + 1) bad.push(`expected one Steam session logged, got ${st.n - before.n}`);
  if (Math.abs(st.h1 - before.h1 - 1) > .02) bad.push("game one's hours didn't grow by 1h: " + (st.h1 - before.h1));
  if (Math.abs(st.h3 - before.h3) > .001) bad.push("game three logged twice");
  if (!/Playing .*right now/.test(st.txt)) bad.push("status line: " + st.txt);
  await ev(() => spSync("manual")); await pg.waitForTimeout(800);
  if ((await ev(() => (S.sessions || []).length)) !== st.n) bad.push("a second sync logged again");
  await ev(() => { window.confirm = () => true; document.getElementById("s-spoff").click(); }); await pg.waitForTimeout(800);
  if (await ev(() => !!S.steamMe || !document.getElementById("s-spwho"))) bad.push("Unlink didn't reset the setting");
  if (!calls.includes("me:off")) bad.push("Unlink didn't tell the helper: " + calls.join(","));
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px (trigger ${feat.trig ? "CSS" : "script"}, bar ${feat.gdsb ? "on" : "off"}, ring ${feat.anc ? "anchored" : "measured"}): ${ok ? "sticky bar, reveals, tour ring and Steam sessions all behave" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
