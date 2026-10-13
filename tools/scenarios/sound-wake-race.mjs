/* #215 (iPad Safari): "null is not an object (evaluating 'AC.state')". The daily calendar card (cal) and the release
   card (outNow) ask the effects sound engine to wake (AC.resume()) at load, outside a tap, so Safari leaves the promise
   pending. The first tap's wake (_fxKick) then finds the engine still frozen, closes it and sets AC = null; when the
   old resume() settles, its callback read AC.state off null. This plays that race with a fake AudioContext whose
   resume() settles late, and fails on any script error.
     node tools/kit.mjs run tools/scenarios/sound-wake-race.mjs            (fixed build: passes)
     node tools/kit.mjs run tools/scenarios/sound-wake-race.mjs --file old.html   (before: fails) */
export default async ({ p }) => {
  await p.ctx.addInitScript(() => {
    try { const d = JSON.parse(localStorage.getItem("gq:data") || "{}"); delete d.fxCal; localStorage.setItem("gq:data", JSON.stringify(d)); } catch (e) {}
    window.__raceErr = [];
    addEventListener("unhandledrejection", e => window.__raceErr.push(String(e.reason && e.reason.message || e.reason)));
    addEventListener("error", e => window.__raceErr.push(String(e.message)));
    const N = () => ({ connect: x => x || N(), disconnect() {}, start() {}, stop() {}, gain: { value: 0, setValueAtTime() {}, linearRampToValueAtTime() {}, exponentialRampToValueAtTime() {} }, frequency: { value: 0 }, type: "" });
    class FakeAC {
      constructor() { this.state = "suspended"; this.currentTime = 0; this.destination = N(); window.__made = (window.__made || 0) + 1; }
      resume() { return new Promise(r => setTimeout(r, 900)); }   /* Safari outside a tap: settles late, stays suspended */
      close() { this.state = "closed"; return Promise.resolve(); }
      createGain() { return N(); } createOscillator() { return N(); } createBiquadFilter() { return N(); }
      createBuffer() { return {}; } createBufferSource() { return N(); }
    }
    window.AudioContext = window.webkitAudioContext = FakeAC;
    /* the first tap back lands after cal() asked to wake (300ms) and before that resume() settles */
    setTimeout(() => dispatchEvent(new PointerEvent("pointerdown")), 700);
  });
  await p.page.reload(); await p.page.waitForTimeout(3500);
  const r = await p.page.evaluate(() => ({ errs: window.__raceErr, made: window.__made || 0, card: !!document.getElementById("fxcal") || null }));
  const errs = [...r.errs, ...p.errors].filter(e => /AC|null|state/.test(e));
  console.log(`  sound engines made: ${r.made}; errors: ${errs.length ? errs.join(" | ") : "none"}`);
  if (!r.made) { console.log("  the calendar card didn't ask for sound: the race wasn't played"); return false; }
  return !errs.length;
};
