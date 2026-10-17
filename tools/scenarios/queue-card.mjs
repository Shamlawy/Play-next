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
     - the back's ring captions never break inside a word (the owner's "ST OR Y") */
import fs from "node:fs";
import { LOGOS } from "../standin-logos.mjs";

export default async ({ p, w, fail }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const pg = p.page;
  await pg.evaluate(L => { S.games.slice(0, 3).forEach((g, i) => { g.art = L[i].url; }); S.deckOff = true; saveNow(); }, LOGOS);
  const STATES = {
    coming: g => { g.state = "upcoming"; g.date = new Date(Date.now() + 34 * 864e5).toLocaleDateString("en-CA"); S.played = S.played.filter(x => x.id !== g.id); },
    out: g => { g.state = "backlog"; g.date = new Date(Date.now() - 3 * 864e5).toLocaleDateString("en-CA"); S.played = S.played.filter(x => x.id !== g.id); },
    playing: g => { g.state = "playing"; S.played = S.played.filter(x => x.id !== g.id); },
    played: g => { g.state = "played"; g.mine = { story: 8.3, music: 9.3, gameplay: 8.7, replay: 5, visuals: 8.3, world: 8.3, characters: 7.7, pacing: 8.7 };
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
        const caps = [...h.querySelectorAll(".pback .sepone .cap")].map(c => ({ t: c.textContent, lines: Math.round(c.getBoundingClientRect().height / parseFloat(getComputedStyle(c).lineHeight)) }));
        return { on: h.classList.contains("on"), edit: !!h.querySelector(".pback [data-edit]"), more: !!h.querySelector(".pback [data-more]"),
          stats: h.querySelectorAll(".pback .stat").length, caps };
      });
      if (back.gone) { fail(`${name}: two taps closed the card instead of turning it`); continue; }
      if (!back.on) fail(`${name}: two taps didn't turn the card`);
      if (!back.edit || !back.more || back.stats !== 4) fail(`${name}: back lacks Edit/More/4 numbers (${JSON.stringify(back)})`);
      for (const c of back.caps) if (c.lines > 1) fail(`${name}: ring caption "${c.t}" broke over ${c.lines} lines`);
      if (shots) await pg.locator("#p-queue .hero").screenshot({ path: `${shots}/${w}-${name}-back.png` });
      const bb = await pg.locator("#p-queue .hero .pback").boundingBox();
      await p.tap(bb.x + bb.width / 2, bb.y + 12); await pg.waitForTimeout(60); await p.tap(bb.x + bb.width / 2, bb.y + 12);
      await pg.waitForTimeout(1200);
      if (await pg.evaluate(() => { const h = document.querySelector("#p-queue .hero .hflip"); return !h || h.classList.contains("on"); })) fail(`${name}: two taps on the back didn't turn it back`);
      /* one tap closes it; then one on the back closes it too */
      await p.tap(box.x + box.width - 30, box.y + 30);
      await pg.waitForTimeout(800);
      if (await pg.evaluate(() => !!document.querySelector("#p-queue .hero"))) fail(`${name}: one tap didn't close the card`);
      if (name === "coming") {
        await pg.evaluate(() => { flipId = null; collapsed = false; renderQueue(); const w = document.querySelector("#p-queue .hero .hflip"); qkTurn(w); });
        await pg.waitForTimeout(1000);
        const b2 = await pg.locator("#p-queue .hero .pback").boundingBox();
        await p.tap(b2.x + b2.width / 2, b2.y + 12);
        await pg.waitForTimeout(800);
        if (await pg.evaluate(() => !!document.querySelector("#p-queue .hero"))) fail(`${name}: one tap on the back didn't close the card`);
      }
      await pg.evaluate(() => { flipId = null; collapsed = false; renderQueue(); });
    }
  }
  if (Math.max(...heights) - Math.min(...heights) > 2) fail(`open cards differ in height: ${heights.join(", ")}`);
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
