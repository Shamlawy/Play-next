/* The open Queue card, v296 look D "Stamp" (the owner picked it from four demos: the picture fills the card, the front shows
   the status, logo, platforms and genres; tap the cover = the game page, tap anywhere else = turn it over).
     node tools/kit.mjs run tools/scenarios/queue-card.mjs --widths 412,859      SHOTS=dir (front + back per state)
   Checks, for each state (coming, out now, playing, played):
     - the front has no buttons but the cover, and the stamp / status line / logo or title / chips are there
     - every open card is the same height at a width, whatever the game (v232's rule)
     - a tap on the card turns it, a tap on the back turns it back, and the back holds Edit + More + the four numbers
     - a tap on the cover opens the game page (and does not turn the card)
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
        return { btns: btns.length, stamp: !!f.querySelector(".qkst"), sw: (f.querySelector(".qksw") || {}).textContent || "",
          title: !!f.querySelector(".qkt img, .qkt h2"), chips: f.querySelectorAll(".qkc").length, h: Math.round(h.getBoundingClientRect().height) };
      });
      if (front.err) { fail(`${name}: ${front.err}`); continue; }
      if (front.btns) fail(`${name}: ${front.btns} button(s) on the front besides the cover`);
      if (!front.stamp || !front.title || !front.sw) fail(`${name}: front missing stamp/title/status (${JSON.stringify(front)})`);
      if (k === 0 && name !== "out" && !front.sw.toLowerCase().includes({ coming: "coming", playing: "playing", played: "played" }[name])) fail(`${name}: status line says "${front.sw}"`);
      heights.push(front.h);
      if (k === 0 && shots) await pg.locator("#p-queue .hero").screenshot({ path: `${shots}/${w}-${name}-front.png` });
      if (k !== 0) continue;
      /* tap the card (away from the cover) → it turns */
      const box = await pg.locator("#p-queue .hero .pfront").boundingBox();
      await p.tap(box.x + box.width - 30, box.y + 30);
      await pg.waitForTimeout(1100);
      const back = await pg.evaluate(() => {
        const h = document.querySelector("#p-queue .hero .hflip");
        const caps = [...h.querySelectorAll(".pback .sepone .cap")].map(c => ({ t: c.textContent, lines: Math.round(c.getBoundingClientRect().height / parseFloat(getComputedStyle(c).lineHeight)) }));
        return { on: h.classList.contains("on"), edit: !!h.querySelector(".pback [data-edit]"), more: !!h.querySelector(".pback [data-more]"),
          stats: h.querySelectorAll(".pback .stat").length, caps };
      });
      if (!back.on) fail(`${name}: tapping the card didn't turn it`);
      if (!back.edit || !back.more || back.stats !== 4) fail(`${name}: back lacks Edit/More/4 numbers (${JSON.stringify(back)})`);
      for (const c of back.caps) if (c.lines > 1) fail(`${name}: ring caption "${c.t}" broke over ${c.lines} lines`);
      if (shots) await pg.locator("#p-queue .hero").screenshot({ path: `${shots}/${w}-${name}-back.png` });
      const bb = await pg.locator("#p-queue .hero .pback").boundingBox();
      await p.tap(bb.x + bb.width / 2, bb.y + 12);
      await pg.waitForTimeout(1100);
      if (await pg.evaluate(() => document.querySelector("#p-queue .hero .hflip").classList.contains("on"))) fail(`${name}: tapping the back didn't turn it back`);
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
