/* The open Queue card, v299 round 9 "Sweep" (the owner: "Perfect"): box art top-left on a strap in the game's own colour (status +
   spot, one headline, platform logos, genres), a ticket bottom-left (a released game's stub has torn off), the logo bottom-right.
   One tap closes the card into its row, two quick taps turn it, the box art opens the game page.
     node tools/kit.mjs run tools/scenarios/queue-card.mjs --widths 412,859      SHOTS=dir (front + back per state)
   Checks, for each state (coming, out now, playing, played):
     - the front has no buttons but the box art, and the strap / kicker / headline / ticket / logo or title are there
     - the ticket's stub has torn off for a released game (out, playing, played) and is whole for a coming one
     - the box art stands straight, the strap is in the game's colour, nothing on the front runs off the card
     - every open card is the same height at a width, whatever the game (v232's rule)
     - two taps turn the card, the back holds Edit + More + the four numbers, two taps on the back turn it back
     - one tap on the front (and on the back) closes the card into its row
     - a tap on the box art opens the game page (and does not turn or close the card)
     - the back (v300, round 13 "Controller"): the score on the controller's little screen, eight round buttons with their numbers inside,
       the four numbers, drawn Edit / More icons (no emoji), nothing past the card's sides, no caption broken over two lines;
       a scored game shows your own numbers, an unscored one Nexi's guess from the games you've scored
     Taps on the back aim at the line under the logo: on a 360px phone the top middle sits near the move arrows (Chrome's touch
     adjustment snaps a tap there onto the button) and the lower half is under the tab bar */
import fs from "node:fs";
import { LOGOS } from "../standin-logos.mjs";

export default async ({ p, w, fail }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const pg = p.page;
  await pg.evaluate(L => { S.games.slice(0, 3).forEach((g, i) => { g.art = L[i].url; }); S.deckOff = true; saveNow(); }, LOGOS);
  /* Nexi's speech bubble pops up on its own now and then (a power-up line) and can sit over the spot a tap aims at */
  await pg.evaluate(() => { const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  const STATES = {
    coming: g => { g.state = "upcoming"; g.date = new Date(Date.now() + 34 * 864e5).toLocaleDateString("en-CA"); S.played = S.played.filter(x => x.id !== g.id); },
    out: g => { g.state = "backlog"; g.date = new Date(Date.now() - 3 * 864e5).toLocaleDateString("en-CA"); S.played = S.played.filter(x => x.id !== g.id); },
    playing: g => { g.state = "playing"; S.played = S.played.filter(x => x.id !== g.id); },
    played: g => { g.state = "played"; g.mine = { story: 8.3, music: 9.3, gameplay: 8.7, replay: 5, visuals: 8.3, world: 8.3, chars: 7.7, pace: 8.7 };
      if (!S.played.some(x => x.id === g.id)) S.played.push({ id: g.id, real: 8, runs: 2, date: "2026-09-01" }); }
  };
  const heights = [];
  for (const [name, set] of Object.entries(STATES)) {
    for (const k of [0, 1]) {
      const info = await pg.evaluate(([src, k]) => {
        const set = eval(src); goTab("games"); renderQueue(); scrollTo(0, 0);
        const ids = [...document.querySelectorAll("#p-queue [data-row]")].map(r => r.dataset.row);
        const g = S.games.find(x => String(x.id) === ids[k]); if (!g) throw new Error("ids " + ids.slice(0, 4).join() + " | games " + S.games.slice(0, 4).map(x => x.id).join()); set(g); saveNow();
        flipId = null; renderQueue();
        const r = document.querySelector(`#p-queue [data-row="${CSS.escape(g.id)}"]`);
        if (!r.classList.contains("hero")) r.click();
        return { id: g.id };
      }, [set.toString(), k]);
      await pg.waitForTimeout(1700);
      const front = await pg.evaluate(() => {
        const h = document.querySelector("#p-queue .hero"), f = h && h.querySelector(".pfront");
        if (!f) return { err: "no open card" };
        const btns = [...f.querySelectorAll("button")].filter(b => !b.matches(".qkcov"));
        const fr = f.getBoundingClientRect(), out = [...f.querySelectorAll(".qkcov, .qkinfo, .qktk, .qkt")].filter(x => { const r = x.getBoundingClientRect(); return r.left < fr.left - 1 || r.right > fr.right + 1 || r.bottom > fr.bottom + 1; }).map(x => x.className);
        const tk = f.querySelector(".qktk"), cov = f.querySelector(".qkcov"), lg = f.querySelector(".qkt").getBoundingClientRect(), tr = tk.getBoundingClientRect();
        return { btns: btns.length, strap: !!f.querySelector(".qkstrap"), kick: (f.querySelector(".qkkick") || {}).textContent || "", head: (f.querySelector(".qkhead") || {}).textContent || "",
          title: !!f.querySelector(".qkt img, .qkt h2"), snap: tk.classList.contains("qksnap"), tilt: getComputedStyle(cov).transform, out,
          overlap: tr.right > lg.left + 2 && !!f.querySelector(".qkt img") && f.querySelector(".qkt img").getBoundingClientRect().left < tr.right - 2,
          strapBg: getComputedStyle(f.querySelector(".qkstrap")).backgroundImage.includes("gradient"), h: Math.round(h.getBoundingClientRect().height) };
      });
      if (front.err) { fail(`${name}: ${front.err}`); continue; }
      if (front.btns) fail(`${name}: ${front.btns} button(s) on the front besides the box art`);
      if (!front.strap || !front.strapBg || !front.title || !front.kick || !front.head) fail(`${name}: front missing strap/logo/kicker/headline (${JSON.stringify(front)})`);
      if (front.tilt !== "none") fail(`${name}: the box art is tilted (${front.tilt})`);
      if (front.out.length) fail(`${name}: runs off the card: ${front.out.join(", ")}`);
      if (front.overlap) fail(`${name}: the ticket runs into the logo`);
      if (front.snap !== (name !== "coming")) fail(`${name}: the ticket ${front.snap ? "tore" : "stayed whole"}`);
      if (k === 0 && !front.kick.toLowerCase().includes({ coming: "coming", out: "out now", playing: "playing", played: "played" }[name])) fail(`${name}: the kicker says "${front.kick}"`);
      heights.push(front.h);
      if (k === 0 && shots) await pg.locator("#p-queue .hero").screenshot({ path: `${shots}/${w}-${name}-front.png` });
      if (k !== 0) continue;
      /* two taps (away from the box art) → it turns */
      const box = await pg.locator("#p-queue .hero .pfront").boundingBox();
      await p.tap(box.x + box.width - 30, box.y + 30); await pg.waitForTimeout(60); await p.tap(box.x + box.width - 30, box.y + 30);
      await pg.waitForTimeout(1200);
      const back = await pg.evaluate(() => {
        const h = document.querySelector("#p-queue .hero .hflip");
        if (!h) return { gone: true };
        const bk = h.querySelector(".pback"), br = bk.getBoundingClientRect(), lines = c => Math.round(c.getBoundingClientRect().height / parseFloat(getComputedStyle(c).lineHeight));
        const caps = [...bk.querySelectorAll(".qbb > span, .qbfour span, .qbfour b, .qbbig > span")].map(c => ({ t: c.textContent, lines: lines(c) }));
        const out = [...bk.querySelectorAll(".qbin > *, .qbb, .qbbig, .qbi")].filter(x => { const r = x.getBoundingClientRect(); return r.width && (r.left < br.left - 1 || r.right > br.right + 1); }).map(x => x.className);
        const inBtn = [...bk.querySelectorAll(".qbv")].every(v => { const a = v.getBoundingClientRect(), b = v.querySelector("b").getBoundingClientRect(); return b.width <= a.width + 1 && b.height <= a.height + 1; });
        const sc = bk.querySelector(".qbscr").getBoundingClientRect(), num = bk.querySelector(".qbnum"), rng = document.createRange(); rng.selectNodeContents(num); const nr = rng.getBoundingClientRect();
        const onScreen = nr.left >= sc.left - 2 && nr.right <= sc.right + 2 && nr.top >= sc.top - 2 && nr.bottom <= sc.bottom + 2;
        const emoji = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(bk.querySelector(".qbh").textContent);
        return { on: h.classList.contains("on"), edit: !!bk.querySelector("[data-edit]"), more: !!bk.querySelector("[data-more]"),
          stats: bk.querySelectorAll(".qbfour > div").length, btns: bk.querySelectorAll(".qbb").length, pad: !!bk.querySelector(".qbbig"),
          guess: bk.querySelector(".qbpad").classList.contains("qbg"), vals: [...bk.querySelectorAll(".qbv b")].map(b => b.textContent), big: num.textContent, caps, out, inBtn, onScreen, emoji };
      });
      if (back.gone) { fail(`${name}: two taps closed the card instead of turning it`); continue; }
      if (!back.on) fail(`${name}: two taps didn't turn the card`);
      if (!back.edit || !back.more || back.stats !== 4 || back.btns !== 8 || !back.pad) fail(`${name}: back lacks Edit/More/4 numbers/controller/8 buttons (${JSON.stringify(back)})`);
      for (const c of back.caps) if (c.lines > 1) fail(`${name}: "${c.t}" on the back broke over ${c.lines} lines`);
      if (back.out.length) fail(`${name}: runs past the back's sides: ${back.out.join(", ")}`);
      if (!back.inBtn) fail(`${name}: a number spills out of its button`);
      if (!back.onScreen) fail(`${name}: the score "${back.big}" isn't on the controller's screen`);
      if (back.emoji) fail(`${name}: an emoji icon on the back's top row`);
      if (name === "played" && (back.guess || back.vals.join() !== "8.3,9.3,8.7,5.0,8.3,8.3,7.7,8.7")) fail(`played: the buttons don't show your own scores (${back.vals.join()})`);
      if (shots) await pg.locator("#p-queue .hero").screenshot({ path: `${shots}/${w}-${name}-back.png` });
      const bb = await pg.locator("#p-queue .hero .pback .qbsub").boundingBox();
      await p.tap(bb.x + 20, bb.y + bb.height / 2); await pg.waitForTimeout(60); await p.tap(bb.x + 20, bb.y + bb.height / 2);
      await pg.waitForTimeout(1200);
      if (await pg.evaluate(() => { const h = document.querySelector("#p-queue .hero .hflip"); return !h || h.classList.contains("on"); })) fail(`${name}: two taps on the back didn't turn it back`);
      /* one tap closes it; then one on the back closes it too */
      await p.tap(box.x + box.width - 30, box.y + 30);
      await pg.waitForTimeout(800);
      if (await pg.evaluate(() => !!document.querySelector("#p-queue .hero"))) fail(`${name}: one tap didn't close the card`);
      if (name === "coming") {
        await pg.evaluate(() => { flipId = null; collapsed = false; renderQueue(); const w = document.querySelector("#p-queue .hero .hflip"); qkTurn(w); });
        await pg.waitForTimeout(1000);
        const b2 = await pg.locator("#p-queue .hero .pback .qbsub").boundingBox();
        await p.tap(b2.x + 20, b2.y + b2.height / 2);
        await pg.waitForTimeout(800);
        if (await pg.evaluate(() => !!document.querySelector("#p-queue .hero"))) fail(`${name}: one tap on the back didn't close the card`);
      }
      await pg.evaluate(() => { flipId = null; collapsed = false; renderQueue(); });
    }
  }
  if (Math.max(...heights) - Math.min(...heights) > 2) fail(`open cards differ in height: ${heights.join(", ")}`);
  /* an unscored game guesses its eight areas from the games you've scored (sharing a genre counts more) */
  const guess = await pg.evaluate(() => {
    goTab("games"); renderQueue();
    const ids = [...document.querySelectorAll("#p-queue [data-row]")].map(r => r.dataset.row), g = S.games.find(x => String(x.id) === ids[0]);
    S.played = S.played.filter(x => x.id !== g.id); g.mine = {}; g.state = "backlog";
    S.games.forEach(x => { if (x.id !== g.id) x.mine = {}; });
    const o = S.games.find(x => x.id !== g.id); o.mine = { story: 9, music: 7, gameplay: 8, replay: 6, visuals: 9, world: 8, chars: 7, pace: 6 };
    saveNow(); flipId = g.id; focusId = g.id; collapsed = false; renderQueue();
    const bk = document.querySelector(`#p-queue .hero[data-row="${CSS.escape(String(g.id))}"] .pback`);
    const res = bk ? { g: bk.querySelector(".qbpad").classList.contains("qbg"), big: bk.querySelector(".qbnum").textContent, vals: [...bk.querySelectorAll(".qbv b")].map(b => b.textContent).join() } : { err: "no open card" };
    flipId = null; renderQueue(); return res;
  });
  if (!guess.g || guess.vals !== "9.0,7.0,8.0,6.0,9.0,8.0,7.0,6.0" || guess.big !== "75") fail(`Nexi's guess on an unscored game: ${JSON.stringify(guess)}`);
  /* the cover opens the game page and leaves the card as it was */
  await pg.evaluate(() => { document.querySelector("#p-queue .hero").scrollIntoView({ block: "center", behavior: "instant" }); });
  await pg.waitForTimeout(400);
  const cb = await pg.locator("#p-queue .hero .qkcov").boundingBox();
  await p.tap(cb.x + cb.width / 2, cb.y + cb.height / 2);
  await pg.waitForTimeout(1200);
  const r = await pg.evaluate(() => ({ full: !!document.querySelector("#full.on, #full[open], .hub.gpage"), flipped: !!document.querySelector("#p-queue .hero .hflip.on") }));
  if (!r.full) fail("tapping the cover didn't open the game page");
  if (r.flipped) fail("tapping the cover turned the card");
  console.log(`  ${w}: card heights ${[...new Set(heights)].join("/")}px`);
};
