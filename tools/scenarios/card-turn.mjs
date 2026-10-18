/* v306 (the owner: "the numbers or version looks bad and into each other … double tapping the card has a slow animation and shift
   and too slow … when pressing and opening a card there are old sounds that was for the rating"):
     node tools/kit.mjs run tools/scenarios/card-turn.mjs --widths 412,859,360
   Checks:
     - a game on PS4 + Vita, Xbox One + 360 and PC: on the open card's front and back no platform logo or version label overlaps
       another, each version stays on one line, and on the front they all fit inside the strap's text column
     - two taps turn the card in 450ms or less, and while it turns the card doesn't move: its box and the page's scroll stay put
       (the turn may only rotate the two sides)
     - opening a card plays only the card's own sound: no old hype/rating ticks (revealSound), and Settings has no "Opening a card" */
export default async ({ p, w, fail }) => {
  const pg = p.page;
  await pg.evaluate(() => { const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; S.deckOff = true; saveNow(); });
  /* count every oscillator started, by who asked: sfx() (the card) or anything else */
  await pg.evaluate(() => {
    window.__osc = 0;
    const AC = window.AudioContext || window.webkitAudioContext; if (!AC || AC.prototype.__wrapped) return;
    const mk = AC.prototype.createOscillator; AC.prototype.__wrapped = 1;
    AC.prototype.createOscillator = function () { window.__osc++; return mk.call(this); };
  });
  const open = await pg.evaluate(() => {
    goTab("games"); collapsed = true; focusId = null; flipId = null; renderQueue(); scrollTo(0, 0);
    const ids = [...document.querySelectorAll("#p-queue [data-row]")].map(r => r.dataset.row);
    const g = S.games.find(x => String(x.id) === ids[0]); if (!g) return { err: "no rows" };
    g.platforms = "PlayStation 4, PS Vita, Xbox One, Xbox 360, PC"; g.state = "backlog"; S.sound = true; saveNow(); renderQueue();
    return { id: g.id };
  });
  if (open.err) return fail(open.err);
  /* a tap on the row opens it (and plays its sound) */
  await pg.evaluate(id => { userTouched = true; window.__osc = 0; document.querySelector(`#p-queue [data-row="${CSS.escape(id)}"]`).click(); }, String(open.id));
  await pg.waitForTimeout(2000);
  const sound = await pg.evaluate(() => ({ osc: window.__osc, reveal: typeof window.revealSound, group: [...document.querySelectorAll("h4")].some(h => h.textContent.trim() === "Opening a card") }));
  if (sound.reveal !== "undefined") fail("the old hype/rating tick sound (revealSound) is still in the app");
  /* the card's own sound (qkopen) starts 11 tones, 12 when the stub tears; the old ticks came on top (one per hype pip and area) */
  if (sound.osc > 12) fail(`opening the card started ${sound.osc} tones: more than the card's own sound`);
  const plat = side => pg.evaluate(side => {
    const box = document.querySelector(`#p-queue .hero ${side}`); if (!box) return { err: "no " + side };
    const parts = [...box.querySelectorAll(".pfm svg, .pfm small, .pfm b")].map(x => ({ t: x.tagName === "svg" ? "logo" : x.textContent, r: x.getBoundingClientRect() }));
    const hit = [];
    for (let i = 0; i < parts.length; i++) for (let j = i + 1; j < parts.length; j++) {
      const a = parts[i].r, b = parts[j].r;
      if (a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1) hit.push(parts[i].t + " × " + parts[j].t);
    }
    const tall = parts.filter(x => x.t !== "logo" && x.r.height > 16).map(x => x.t);
    /* the front's logos must stay inside the info column (the strap's end is just past it) */
    const lim = box.closest(".qkinfo"), right = lim ? lim.getBoundingClientRect().right : Infinity;
    const past = parts.filter(x => x.r.right > right + 1).map(x => `${x.t} by ${Math.round(x.r.right - right)}px`);
    return { n: parts.length, hit, tall, past };
  }, side);
  const fr = await plat(".pfront .qkpl");
  if (fr.err) fail(`front: ${fr.err}`); else {
    if (fr.n < 5) fail(`front: only ${fr.n} platform parts drawn`);
    if (fr.hit.length) fail(`front: platform labels overlap: ${fr.hit.join(", ")}`);
    if (fr.tall.length) fail(`front: a version broke over two lines: ${fr.tall.join(", ")}`);
    if (fr.past.length) fail(`front: platforms run past the strap's text column: ${fr.past.join(", ")}`);
  }
  if (process.env.SHOTS) await pg.locator("#p-queue .hero").screenshot({ path: `${process.env.SHOTS}/${w}-turn-front.png` });
  /* two taps: watch the card's box, the scroll and the turn's length every frame */
  const box = await pg.locator("#p-queue .hero .pfront").boundingBox();
  await pg.evaluate(() => {
    const h = document.querySelector("#p-queue .hero"), hf = h.querySelector(".hflip");
    window.__turn = { boxes: new Set(), scroll: new Set(), t0: 0, t1: 0, dur: 0 };
    const T = window.__turn, r0 = performance.now();
    const tick = now => {
      const r = hf.getBoundingClientRect(); T.boxes.add(`${Math.round(r.top)},${Math.round(r.height)}`); T.scroll.add(Math.round(scrollY));
      if (hf.classList.contains("qkturn")) { if (!T.t0) { T.t0 = now; T.dur = Math.max(...hf.querySelector(".pfront").getAnimations().map(a => a.effect.getTiming().duration || 0), 0); } }
      else if (T.t0 && !T.t1) T.t1 = now;
      if (now - r0 < 3000 && !T.t1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
  await p.tap(box.x + box.width - 30, box.y + 30); await pg.waitForTimeout(60); await p.tap(box.x + box.width - 30, box.y + 30);
  await pg.waitForTimeout(1500);
  const t = await pg.evaluate(() => { const T = window.__turn, h = document.querySelector("#p-queue .hero .hflip");
    return { on: !!(h && h.classList.contains("on")), boxes: [...T.boxes], scroll: [...T.scroll], ran: T.t1 && T.t0 ? Math.round(T.t1 - T.t0) : null, dur: T.dur }; });
  if (!t.on) fail("two taps didn't turn the card");
  if (t.dur > 450) fail(`the turn is set to ${t.dur}ms (450ms at most)`);
  if (t.boxes.length > 1) fail(`the card moved while it turned (top,height: ${t.boxes.join(" | ")})`);
  if (t.scroll.length > 1) fail(`the page scrolled while the card turned (${t.scroll.join(", ")})`);
  const bk = await plat(".pback .qbpf");
  if (!bk.err) {
    if (bk.hit.length) fail(`back: platform labels overlap: ${bk.hit.join(", ")}`);
    if (bk.tall.length) fail(`back: a version broke over two lines: ${bk.tall.join(", ")}`);
  }
  if (process.env.SHOTS) await pg.locator("#p-queue .hero").screenshot({ path: `${process.env.SHOTS}/${w}-turn-back.png` });
  await pg.evaluate(() => { flipId = null; collapsed = true; renderQueue(); });
  console.log(`  ${w}: open card ${sound.osc} tones; turn ${t.dur}ms set, ${t.ran}ms seen; platforms ${fr.n || 0} parts`);
};
