/* The design eye (Oct 2026, the owner: "ensure you can pick those ugly design or disproportionate stuff"): looks at the screen
   the way a person would and names what looks broken. v290's Queue logo checker (tools/scenarios/logo-look.mjs) grown to
   every screen. Used by `node tools/kit.mjs eye` (in `all`, so every PR) and tools/scenarios/design-eye.mjs.

   It finds the repeated things on screen by itself (rows, cards, tiles, chips: 3+ visible siblings of the same kind) and checks
     by boxes, for every repeated item:
       edge      — runs past the side of the screen (not inside a side scroller)
       overlap   — sits on top of its neighbour
       spill     — a picture or words inside it drawn outside the item (and nothing clips them)
       squeezed  — words cut off in a box under 60px wide
       broken    — a short word (a date, "16h", a tag, "S-tier") broken over two lines or more
       squashed  — a picture stretched out of its shape, or under 16px
       uneven    — items side by side whose pictures differ 2.5x+ in size
       faint     — words that barely stand out from what's under them (under 2:1)
     by pixels, for every picture in a repeated item (the logo checker's way: a screenshot with all of them hidden, then one per
     picture; the pixels that changed are where it REALLY landed, after rotation, filters and clipping):
       spill, edge, words (sits on the item's words), faint (on-screen contrast under 1.4:1)
   tools/eye-ok.txt lists what is meant to look that way (one `<kind> <selector part> — <why>` a line). */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
export const OK = (() => {
  const f = path.join(ROOT, "tools/eye-ok.txt");
  if (!fs.existsSync(f)) return [];
  return fs.readFileSync(f, "utf8").split(/\r?\n/).map(l => l.match(/^(\w+)\s+(\S+)\s+—/)).filter(Boolean).map(m => [m[1], m[2]]);
})();

/* in the page: the repeated items on screen and what's wrong with them by their boxes. The code lives in index.html
   (lookEye, between the eye:start and eye:end comments, Oct 18) because the phone runs it too (Nexi files what it finds there, with
   the owner's own games); it is read from the repo's index.html each run, so an older copy (--file) gets today's eye. */
const EYE_SRC = (() => {
  const m = fs.readFileSync(path.join(ROOT, "index.html"), "utf8").match(/\/\* eye:start[^*]*\*\/\s*([\s\S]*?)\/\* eye:end \*\//);
  if (!m || !/^function lookEye\(/.test(m[1])) throw new Error("tools/eye.mjs: no lookEye between eye:start and eye:end in index.html");
  return m[1].trim();
})();

/* hide floating layers and every marked picture except one, then take a still picture */
async function snap(page, show) {
  await page.evaluate(show => {
    for (const e of document.querySelectorAll("body *")) { const c = getComputedStyle(e); if ((c.position === "fixed" || c.position === "sticky") && !e.closest("#full.on, .sheet.on, #sheet.on, #edit.on, #set.on, .dy.on") && !e.matches("#full.on, .sheet.on, #sheet.on, #edit.on, #set.on, .dy.on")) e.setAttribute("data-eyehide", ""); }
    eyeHold();
    let s = document.getElementById("eyehide"); if (!s) { s = document.createElement("style"); s.id = "eyehide"; document.head.appendChild(s); }
    s.textContent = `[data-eyehide], [data-eyehide] * { visibility: hidden !important; } [data-eyepic] { visibility: hidden !important; } ${show ? `[data-eyepic="${show}"] { visibility: visible !important; }` : ""}`;
  }, show);
  await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))); await page.waitForTimeout(200);
  return "data:image/png;base64," + (await page.screenshot({ scale: "css" })).toString("base64");
}

/* everything wrong on the screen as it is now */
export async function look(page) {
  /* entrances and fades first finish (a paused fade-in reads as faint words, a paused slide as a spill); endless loops pause */
  await page.waitForFunction(() => !document.getAnimations().some(a => a.playState === "running" && a.effect && a.effect.getComputedTiming().iterations !== Infinity), null, { timeout: 4000 }).catch(() => {});
  /* pause only what is running, and later play only those: play() on a finished or CSS-paused animation would restart it */
  await page.evaluate(() => { const held = window.eyeHeld = new Set();
    window.eyeHold = () => document.getAnimations().forEach(a => { if (a.playState === "running") { held.add(a); a.pause(); } }); eyeHold(); });
  const { issues, pics, groups } = await page.evaluate(([src, ok]) => (0, eval)("(" + src + ")")(null, ok), [EYE_SRC, OK]);
  if (pics.length) {
    let base, base2; const each = [];
    try { base = await snap(page, null); for (const p of pics) each.push(await snap(page, p.id)); base2 = await snap(page, null); }
    finally { await page.evaluate(() => { document.getElementById("eyehide")?.remove(); document.querySelectorAll("[data-eyehide]").forEach(e => e.removeAttribute("data-eyehide")); }); }
    const px = await page.evaluate(async ([base, base2, each, pics]) => {
      const load = async u => { const im = new Image(); im.src = u; await im.decode(); const c = document.createElement("canvas"); c.width = im.width; c.height = im.height;
        const x = c.getContext("2d"); x.drawImage(im, 0, 0); return { w: c.width, h: c.height, d: x.getImageData(0, 0, c.width, c.height).data }; };
      const B = await load(base), B2 = await load(base2), L = (d, i) => (.2126 * d[i] + .7152 * d[i + 1] + .0722 * d[i + 2]) / 255, lin = v => v <= .04 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4;
      const out = [];
      for (let k = 0; k < pics.length; k++) {
        const r = pics[k], A = await load(each[k]), a = A.d, b = B.d; let n = 0, spill = 0, edge = 0, words = 0, far = 0, bx0 = 1e9, by0 = 1e9, bx1 = -1, by1 = -1; const con = [];
        const [x0, y0, x1, y1] = r.ib.map(Math.round);
        for (let y = Math.max(0, y0); y < Math.min(y1, A.h); y++) for (let x = Math.max(0, x0); x < Math.min(A.w, x1); x++) {
          const i = (y * A.w + x) * 4;
          if (Math.abs(b[i] - B2.d[i]) + Math.abs(b[i + 1] - B2.d[i + 1]) + Math.abs(b[i + 2] - B2.d[i + 2]) >= 40) continue;
          if (Math.abs(a[i] - b[i]) + Math.abs(a[i + 1] - b[i + 1]) + Math.abs(a[i + 2] - b[i + 2]) < 24) continue;
          n++; const [l, t, rr, bb] = r.box; if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y;
          if (x < l - 3 || x > rr + 3 || y < t - 3 || y > bb + 3) { spill++; far = Math.max(far, t - y, y - bb, x - rr, l - x); }
          if (x >= A.w - 2) edge++;
          if (r.words.some(q => x >= q[0] && x <= q[2] && y >= q[1] && y <= q[3])) words++;
          if ((x + y) % 3 === 0) { const la = lin(L(a, i)), lb = lin(L(b, i)); con.push((Math.max(la, lb) + .05) / (Math.min(la, lb) + .05)); }
        }
        if (!n) continue;
        if (spill > 40) out.push({ kind: "spill", at: r.at, msg: `picture drawn ${Math.round(far)}px out of its item (by pixels)` });
        if (edge > 6) out.push({ kind: "edge", at: r.at, msg: "picture runs off the screen edge (by pixels)" });
        /* a shape (a logo with see-through parts) fills little of its own box; a photo fills all of it. Words on a photo are
           a caption (the Library's date chips); words on a logo, or a logo the colour of the row, look broken */
        const shape = n / ((bx1 - bx0 + 1) * (by1 - by0 + 1)) < .7;
        if (shape && words > 60) out.push({ kind: "words", at: r.at, msg: "picture sits on its item's words (by pixels)" });
        con.sort((p, q) => p - q); const med = con[Math.floor(con.length * .9)] || 1;
        if (shape && n > 80 && med < 1.4) out.push({ kind: "faint", at: r.at, msg: `picture barely shows (contrast ${med.toFixed(1)}:1)` });
      }
      return out;
    }, [base, base2, each, pics]);
    await page.evaluate(() => { document.getElementById("eyehide")?.remove(); document.querySelectorAll("[data-eyehide]").forEach(e => e.removeAttribute("data-eyehide")); });
    for (const i of px) if (!OK.some(([k, s]) => k === i.kind && i.at.includes(s))) issues.push(i);
  }
  await page.evaluate(() => { document.querySelectorAll("[data-eyepic]").forEach(e => e.removeAttribute("data-eyepic")); (window.eyeHeld || []).forEach(a => a.play()); window.eyeHeld = null; });
  const uniq = new Map(); for (const i of issues) uniq.set(i.kind + i.at + i.msg, i);
  return { issues: [...uniq.values()], pics: pics.length, groups };
}

/* the page's own scroller, scrolled instantly, then wait until things stop moving (L53) */
export async function still(page, dy) {
  await page.evaluate(dy => { const els = [...document.querySelectorAll("#full.on .hub, #full.on, .sheet.on, #sheet-in, main, .wrap")].filter(e => e.scrollHeight > e.clientHeight + 10 && e.getClientRects().length);
    const s = els[0] || document.scrollingElement; if (dy === 0) s.scrollTo({ top: 0, behavior: "instant" }); else s.scrollBy({ top: dy, behavior: "instant" }); }, dy);
  await page.waitForTimeout(500);
}
