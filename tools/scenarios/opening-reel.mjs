/* v291 the opening reel ("One pixel plays everything", pnReel on #pnboot's canvas):
   1. under automation it stays away (the kit taps the screens below); gq:reel = "1" brings it back
   2. it paints the opening pixel at once, and every beat of the 15s draws something (no blank beat)
   3. drawing one frame stays cheap with the CPU 4x slower (it runs every frame for 15s on the phone)
   4. it waits on the glowing pixel; a real tap starts it with its sound; a tap while it plays skips it;
      Skip on the waiting screen goes straight in; left alone after the tap it ends by itself after 15s; the tour waits
   also: a resize while it waits repaints the pixel in the middle
   5. Settings › This build › Opening reel = Off stops it
     node tools/kit.mjs run tools/scenarios/opening-reel.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [], pg = p.page;
  if (await pg.evaluate(() => !!document.getElementById("pnboot"))) bad.push("reel showed under automation");
  await pg.evaluate(() => { localStorage.setItem("gq:reel", "1"); S.sound = true; saveNow(); });
  await pg.reload(); await pg.waitForTimeout(600);
  const st = await pg.evaluate(() => { const c = document.querySelector("#pnboot canvas"); if (!c) return null;
    const x = c.getContext("2d"), d = x.getImageData(c.width / 2 - 2, c.height / 2 - 2, 4, 4).data;
    return { w: c.width, h: c.height, mid: d[2] };   /* blue channel of the pixel at the centre */ });
  if (!st) bad.push("no reel canvas on open"); else if (st.mid < 120) bad.push("the opening pixel isn't in the middle");
  /* 2 + 3: every beat, drawn on its own (the clock is paused by drawing at fixed times) */
  const cdp = await pg.context().newCDPSession(pg);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  const r = await pg.evaluate(() => { const c = document.querySelector("#pnboot canvas"), x = c.getContext("2d"), out = { blank: [], ms: [] };
    for (let i = 0; i < 30; i++) pnreelPaint(i * .5 + .3);   /* warm-up: the first pass loads the font and compiles */
    for (let i = 0; i < 30; i++) { const t = i * .5 + .3, a = performance.now(); pnreelPaint(t); out.ms.push(performance.now() - a); }
    /* reading pixels back stalls the canvas, so the blank check is its own pass after the timing */
    for (let i = 0; i < 30; i++) { const t = i * .5 + .3; pnreelPaint(t);
      const d = x.getImageData(0, 0, c.width, c.height).data; let lit = 0; for (let j = 0; j < d.length; j += 4 * 97) if (d[j] + d[j + 1] + d[j + 2] > 60) lit++;
      if (!lit) out.blank.push(t); }
    return out; });
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const worst = Math.max(...r.ms), avg = r.ms.reduce((a, b) => a + b, 0) / r.ms.length;
  console.log(`  ${w}px: canvas ${st && st.w}x${st && st.h}, one frame at 4x slower CPU: avg ${avg.toFixed(1)}ms, worst ${worst.toFixed(1)}ms`);
  if (r.blank.length) bad.push("blank beats at " + r.blank.join(", "));
  if (worst > 16) bad.push(`a frame took ${worst.toFixed(1)}ms at 4x (budget 16)`);
  /* a fold / unfold / turn while it waits repaints the pixel at the new size */
  const vp = pg.viewportSize();
  await pg.setViewportSize({ width: vp.height > vp.width ? 859 : 412, height: vp.height }); await pg.waitForTimeout(300);
  const rs = await pg.evaluate(() => { const c = document.querySelector("#pnboot canvas"), d = Math.min(devicePixelRatio || 1, 2);
    const x = c.getContext("2d"), m = x.getImageData(c.width / 2 - 2, c.height / 2 - 2, 4, 4).data;
    return { fit: c.width === Math.round(innerWidth * d) && c.height === Math.round(innerHeight * d), mid: m[2] }; });
  await pg.setViewportSize(vp); await pg.waitForTimeout(300);
  if (!rs.fit) bad.push("the waiting canvas didn't resize with the screen");
  else if (rs.mid < 120) bad.push("the opening pixel left the middle after a resize");
  /* 4: it waits for a tap */
  await pg.waitForTimeout(900);
  const wait = await pg.evaluate(() => ({ t0: PNREEL.t0, go: !!document.querySelector("#pnboot .pnreel-go"), say: getComputedStyle(document.querySelector(".pnreel-say")).opacity,
    glow: document.querySelector(".pnreel-go").getAnimations({ subtree: true }).flatMap(a => a.effect.getKeyframes().flatMap(k => Object.keys(k))).filter(k => !["offset", "easing", "composite", "computedOffset"].includes(k)) }));
  if (wait.t0) bad.push("started without a tap");
  if (!wait.go || +wait.say < .9) bad.push("no Tap to play on the waiting screen");
  if (wait.glow.some(k => k !== "opacity" && k !== "transform")) bad.push("the glow animates " + wait.glow.join(","));
  const waits = await pg.evaluate(() => { let ran = false; extend("nxTour", { after() { ran = true; } }); S.tourFrom = +APP_V - 1; nxTour(null); return new Promise(res => setTimeout(() => res(!document.getElementById("nxtour")), 300)); });
  if (!waits) bad.push("the tour started over the reel");
  await pg.evaluate(() => { S.tourFrom = +APP_V; });
  await p.tap(w / 2, 300); await pg.waitForTimeout(1200);
  const on = await pg.evaluate(() => ({ playing: !!PNREEL.playing, t0: PNREEL.t0, sound: PNREEL.src ? PNREEL.src.buffer.duration : 0, ac: AC && AC.state }));
  if (!on.playing || !on.t0) bad.push("a tap didn't start it");
  if (Math.abs(on.sound - 15) > .1) bad.push("no 15s soundtrack with Sound on (" + on.sound + ")");
  console.log(`  ${w}px: tapped: playing, sound ${on.sound.toFixed(1)}s, audio context ${on.ac}`);
  await p.tap(w / 2, 300); await pg.waitForTimeout(500);
  if (await pg.evaluate(() => !!document.getElementById("pnboot"))) bad.push("a tap while playing didn't skip it");
  await pg.evaluate(() => { const t = document.getElementById("nxtour"); if (t) t.remove(); });
  /* left alone it ends */
  await pg.evaluate(() => saveNow()); await pg.reload(); await pg.waitForTimeout(600);
  await p.tap(w / 2, 300); await pg.waitForFunction(() => PNREEL.t0 > 0, null, { timeout: 3000 }).catch(() => bad.push("never started after the tap"));
  await pg.evaluate(() => { PNREEL.t0 -= 14600; });
  await pg.waitForTimeout(1200);
  if (await pg.evaluate(() => !!document.getElementById("pnboot"))) bad.push("didn't end by itself after 15s");
  /* Skip on the waiting screen */
  await pg.reload(); await pg.waitForTimeout(600);
  const sk = await pg.evaluate(() => { const r = document.querySelector(".pnreel-skip").getBoundingClientRect(); return [r.x + r.width / 2, r.y + r.height / 2]; });
  await p.tap(sk[0], sk[1]); await pg.waitForTimeout(500);
  if (await pg.evaluate(() => !!document.getElementById("pnboot") || !!PNREEL.playing)) bad.push("Skip didn't go straight in");
  /* 5 */
  await pg.evaluate(() => { S.reelOff = true; saveNow(); }); await pg.reload(); await pg.waitForTimeout(400);
  if (await pg.evaluate(() => !!document.getElementById("pnboot"))) bad.push("played with Opening reel off");
  await pg.evaluate(() => { S.reelOff = false; saveNow(); localStorage.removeItem("gq:reel"); });
  if (bad.length) { bad.forEach(b => console.log("  ✗ " + w + "px: " + b)); return false; }
  console.log(`  ✓ ${w}px: reel waits on the glowing pixel, a tap plays it with sound, draws every beat, stays cheap, skips, ends, switches off`);
  return true;
};
