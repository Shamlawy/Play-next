/* v285 (#177): "Play sounds in Silent mode" (Settings › Sound and reactions) on iPhone/iPad. Without navigator.audioSession
   (Android, the Fold) the switch doesn't exist; with it (a stub that records every type set), the switch is off by default
   and the type stays "auto"; On sets "playback" at once, again on a tap or on coming back after iOS reset it, and at load
   (before any sound); sounds off or the switch off puts "auto" back. Then the what's-new tour from v284 points at the switch.
     node tools/kit.mjs run tools/scenarios/silent-sound.mjs --widths 412,900
     (WebKit: webkit-check.yml with scenario = tools/scenarios/silent-sound.mjs) */
export default async ({ p, w }) => {
  const page = p.page, bad = [];
  const ok = (cond, what) => { console.log(`  ${cond ? "✓" : "✗"} ${what}`); if (!cond) bad.push(what); };
  const S = () => page.evaluate(() => ({ type: window.__as ? window.__as.type : null, sets: window.__as ? window.__as.sets.slice() : [], silentPlay: !!S.silentPlay, sound: !!S.sound }));
  const openSound = () => page.evaluate(() => setJump({ scope: "app", group: "Sound and reactions", text: "Sounds" })).then(() => page.waitForTimeout(900));
  const chip = (id, v) => page.evaluate(([id, v]) => { const b = document.querySelector(`#${id} .chip[data-v="${v}"]`); if (!b) return false; b.click(); return true; }, [id, v]).then(async r => { await page.waitForTimeout(300); return r; });
  /* on the live S (the app saves it again as the page unloads) */
  const data = fn => page.evaluate(fn => { new Function("d", fn)(S); saveNow(); }, fn);

  /* 1. no audioSession: nothing new */
  const native = await page.evaluate(() => !!navigator.audioSession);
  console.log(`  this browser has navigator.audioSession: ${native}`);
  if (!native) {
    await openSound();
    ok(await page.evaluate(() => !document.getElementById("s-silent") && !!document.getElementById("s-sound")), "no audioSession: Sound shows, the Silent switch doesn't");
  }

  /* 2. iPhone/iPad: a stub audioSession that records what the app sets */
  await page.context().addInitScript(() => {
    const as = { sets: [], _t: "auto", get type() { return this._t; }, set type(v) { this._t = v; this.sets.push(v); } };
    window.__as = as;
    Object.defineProperty(Navigator.prototype, "audioSession", { configurable: true, get: () => as });
  });
  await data("d.sound = true; delete d.silentPlay;");
  await page.reload(); await page.waitForTimeout(2500);
  let s = await S();
  ok(s.type === "auto" && !s.sets.length && s.sound, `off by default: ${JSON.stringify(s)}`);
  await openSound();
  ok(await page.evaluate(() => { const c = document.getElementById("s-silent"); return !!c && /Silent mode/.test(c.previousElementSibling.previousElementSibling.textContent) && c.querySelector('[data-v="off"]').getAttribute("aria-pressed") === "true"; }), "the switch shows under Sounds, set to Off");
  ok(await chip("s-silent", "on"), "tap On");
  s = await S();
  ok(s.type === "playback" && s.silentPlay, `On: type "${s.type}"`);
  ok(await page.evaluate(() => JSON.parse(localStorage.getItem("gq:data")).silentPlay === true), "saved");
  await page.evaluate(() => { window.__as._t = "auto"; document.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true })); });
  ok((await S()).type === "playback", "set again on the next tap");
  await page.evaluate(() => { window.__as._t = "auto"; document.dispatchEvent(new Event("visibilitychange")); });
  ok((await S()).type === "playback", "set again on coming back to the app");

  await page.reload(); await page.waitForTimeout(2500);
  s = await S();
  ok(s.type === "playback" && s.sets[0] === "playback", `at load, before any tap: type "${s.type}"`);
  await openSound();
  await chip("s-sound", "off");
  await page.evaluate(() => document.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true })));
  ok((await S()).type === "auto", "sounds off: back to auto");
  await chip("s-sound", "on");
  await page.evaluate(() => document.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true })));
  ok((await S()).type === "playback", "sounds on again: playback");
  await chip("s-silent", "off");
  s = await S();
  ok(s.type === "auto" && !s.silentPlay, `switch Off: type "${s.type}"`);

  /* 3. the what's-new tour from v284 points at the switch */
  await data(`d.silentPlay = false; d.lastV = "284"; d.tourFrom = 284; delete d.tourAt;`);
  await page.reload();
  let step = null;
  for (let k = 0; k < 12 && !step; k++) {
    await page.waitForTimeout(1300);
    step = await page.evaluate(() => {
      const c = document.querySelector("#nxtour.on"); if (!c) return null;
      const ring = document.querySelector("#nxring.on"), t = document.getElementById("s-silent");
      if (!/Silent mode/.test(c.textContent)) { const b = c.querySelector('[data-nx="next"]'); if (b) b.click(); return null; }
      const a = ring && ring.getBoundingClientRect(), b = t && t.getBoundingClientRect();
      return { ring: !!ring, target: !!t, near: !!(a && b && Math.abs((a.top + a.bottom) / 2 - (b.top + b.bottom) / 2) < 40) };
    });
  }
  ok(step && step.ring && step.target && step.near, `tour step points at the switch: ${JSON.stringify(step)}`);
  /* WebKit logs "… due to access control checks" for the old page's picture blob: URLs (index.html makes them from data
     URLs) still loading when the page reloads: one per reload here, none on Chromium; not the app's */
  const reload = p.errors.filter(e => /due to access control checks/.test(e)), errs = p.errors.filter(e => !reload.includes(e));
  if (reload.length) console.log(`  (${reload.length} picture load(s) cut off by this test's 3 reloads, WebKit only)`);
  ok(!errs.length && reload.length <= 3, `no script errors${errs.length ? ": " + errs.join(" | ") : ""}`);
  return !bad.length;
};
