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

/* in the page: the repeated items on screen and what's wrong with them by their boxes */
function boxEye(ok) {
  const W = innerWidth, H = innerHeight, vis = e => { if (!e.getClientRects().length) return false;
    /* checkVisibility also sees a closed <details>' content (content-visibility: hidden), which still has boxes */
    if (e.checkVisibility && !e.checkVisibility({ contentVisibilityAuto: true, visibilityProperty: true, opacityProperty: true })) return false;
    const c = getComputedStyle(e); return c.visibility !== "hidden" && +c.opacity > .05; };
  const pathOf = e => { const p = []; for (let x = e; x && x !== document.body && p.length < 4; x = x.parentElement) p.unshift(x.id ? "#" + x.id : x.tagName.toLowerCase() + (x.classList[0] ? "." + x.classList[0] : "")); return p.join(" > "); };
  /* what the person sees: the open page on top (game page, sheet, Settings), else the tab */
  const tops = [...document.querySelectorAll("#full.on, .sheet.on, #sheet.on, #edit.on, #set.on, .dy.on")].filter(vis);
  const root = tops.length ? tops[tops.length - 1] : document.querySelector("main") || document.body;
  const issues = [];
  const say = (kind, e, msg) => { const at = pathOf(e); if (ok.some(([k, s]) => k === kind && at.includes(s))) return; issues.push({ kind, at, msg }); };
  /* fixed layers (Nexi, the tab bar, toasts) aren't items */
  const fixedUp = e => { for (let x = e; x && x !== root; x = x.parentElement) { const p = getComputedStyle(x).position; if (p === "fixed" || p === "sticky") return true; } return false; };
  /* a side scroller: running past the screen is how it works */
  const sideScroll = e => { for (let x = e.parentElement; x && x !== document.documentElement; x = x.parentElement) { const c = getComputedStyle(x); if (/auto|scroll|hidden|clip/.test(c.overflowX) && x.scrollWidth > x.clientWidth + 2) return x; } return null; };
  const clipBox = (e, upTo) => { let b = [-1e9, -1e9, 1e9, 1e9]; for (let x = e.parentElement; x && x !== upTo.parentElement; x = x.parentElement) { const c = getComputedStyle(x);
    if (c.overflow !== "visible" || c.overflowX !== "visible" || c.overflowY !== "visible" || c.contain.includes("paint") || c.clipPath !== "none") { const q = x.getBoundingClientRect(); b = [Math.max(b[0], q.left), Math.max(b[1], q.top), Math.min(b[2], q.right), Math.min(b[3], q.bottom)]; } } return b; };
  const onScreen = q => q.bottom > 0 && q.top < H && q.width > 0 && q.height > 0;
  const kindOf = e => e.tagName + "." + (e.classList[0] || "");
  const groups = [];
  for (const par of root.querySelectorAll("*")) {
    if (par.children.length < 3 || !vis(par) || fixedUp(par)) continue;
    const by = {};
    if (!(par instanceof HTMLElement)) continue; // an svg's shapes aren't items
    for (const c of par.children) { if (!(c instanceof HTMLElement) || !vis(c) || /^(SCRIPT|STYLE|BR|svg|path)$/i.test(c.tagName)) continue; (by[kindOf(c)] ||= []).push(c); }
    for (const k in by) if (by[k].length >= 3 && k !== "SPAN." && k !== "B." && k !== "I.") groups.push(by[k]);
  }
  /* colour-mix() leaves oklab(…) / oklch(…) / color(srgb …) in computed styles (v301's rows): read those too, or a light word
     on a dark row reads as black (1.0:1); oklab's L cubed is close to relative luminance */
  const lum = s => { const ok = s.match(/^okl(?:ab|ch)\(\s*([\d.]+)(%?)[^/)]*(?:\/\s*([\d.]+))?/);
    if (ok) { const L = +ok[1] / (ok[2] ? 100 : 1); return { L: L ** 3, a: ok[3] == null ? 1 : +ok[3] }; }
    const cs = s.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)(?:\s*\/\s*([\d.]+))?/);
    if (cs) { const v = cs.slice(1, 4).map(x => { x = +x; return x <= .04 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4; }); return { L: .2126 * v[0] + .7152 * v[1] + .0722 * v[2], a: cs[4] == null ? 1 : +cs[4] }; }
    const m = s.match(/[\d.]+/g); if (!m) return null; const v = m.slice(0, 3).map(x => { x = +x / 255; return x <= .04 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4; }); return { L: .2126 * v[0] + .7152 * v[1] + .0722 * v[2], a: m[3] == null ? 1 : +m[3] }; };
  const bgUnder = e => { for (let x = e; x; x = x.parentElement) { const c = getComputedStyle(x); if (c.backgroundImage !== "none") return null; const l = lum(c.backgroundColor); if (l && l.a > .9) return l.L; } return 0; };
  const opUp = e => { let o = 1; for (let x = e; x; x = x.parentElement) o *= +getComputedStyle(x).opacity; return o; };
  const seen = new Set();
  for (const g of groups) {
    const boxes = g.map(e => e.getBoundingClientRect());
    g.forEach((e, i) => {
      const q = boxes[i]; if (!onScreen(q) || seen.has(e)) return; seen.add(e);
      if (!sideScroll(e) && (q.right > W + 1.5 || q.left < -1.5)) say("edge", e, `runs ${Math.round(Math.max(q.right - W, -q.left))}px past the screen edge`);
      const nb = boxes[i + 1];
      if (nb && onScreen(nb)) { const ox = Math.min(q.right, nb.right) - Math.max(q.left, nb.left), oy = Math.min(q.bottom, nb.bottom) - Math.max(q.top, nb.top);
        if (ox > 6 && oy > 6 && ox * oy > .15 * Math.min(q.width * q.height, nb.width * nb.height)) say("overlap", e, `sits ${Math.round(oy)}px over the next one`); }
      for (const d of e.querySelectorAll("img, svg, canvas, b, span, small, i, p, h1, h2, h3, h4, strong, em, div")) {
        if (!vis(d) || fixedUp(d)) continue;
        const r = d.getBoundingClientRect(); if (r.width < 2 || r.height < 2) continue;
        const cb = clipBox(d, e), pic = /^(IMG|CANVAS|svg)$/i.test(d.tagName);
        const vx0 = Math.max(r.left, cb[0]), vy0 = Math.max(r.top, cb[1]), vx1 = Math.min(r.right, cb[2]), vy1 = Math.min(r.bottom, cb[3]);
        const out = Math.max(q.left - vx0, q.top - vy0, vx1 - q.right, vy1 - q.bottom);
        const leaf = !pic && d.childElementCount === 0 && d.textContent.trim();
        if ((pic || leaf) && out > 6 && vx1 > vx0 && vy1 > vy0 && getComputedStyle(d).position !== "absolute") say("spill", d, `${pic ? "picture" : `"${d.textContent.trim().slice(0, 14)}"`} drawn ${Math.round(out)}px outside its ${kindOf(e).toLowerCase()}`);
        if (d.tagName === "IMG" && d.complete && d.naturalWidth > 4) {
          const c = getComputedStyle(d), nat = d.naturalWidth / d.naturalHeight, got = r.width / r.height;
          if (c.objectFit === "fill" && Math.abs(Math.log(got / nat)) > Math.log(1.3) && !c.objectViewBox?.startsWith?.("inset")) say("squashed", d, `picture stretched to ${Math.round(r.width)}x${Math.round(r.height)} (its shape is ${nat.toFixed(2)}:1)`);
          if (Math.min(r.width, r.height) < 14 && Math.max(r.width, r.height) < 40) say("squashed", d, `picture squashed to ${Math.round(r.width)}x${Math.round(r.height)}`);
        }
        if (leaf) {
          const c = getComputedStyle(d), t = d.textContent.trim(), lh = parseFloat(c.lineHeight) || parseFloat(c.fontSize) * 1.25;
          const h = r.height - parseFloat(c.paddingTop) - parseFloat(c.paddingBottom) - parseFloat(c.borderTopWidth) - parseFloat(c.borderBottomWidth);
          if (c.overflowX !== "visible" && d.scrollWidth > d.clientWidth + 1 && d.clientWidth < 60 && d.clientWidth > 0 && t.length > 3) say("squeezed", d, `"${t.slice(0, 14)}" cut down to ${d.clientWidth}px`);
          /* lines by the text's own boxes: a tall block isn't a broken word */
          const rg = document.createRange(); rg.selectNodeContents(d); const tops = new Set([...rg.getClientRects()].filter(q => q.width > 1).map(q => Math.round(q.top / (lh * .5))));
          /* a date or "16h" in two, or one word split ("S-/tier") */
          if (t.length <= 14 && (/\d/.test(t) || !/\s/.test(t)) && tops.size > 1 && h > lh * 1.6) say("broken", d, `"${t}" breaks onto ${tops.size} lines`);
          const fg = lum(c.color), bg = bgUnder(d);
          if (fg && bg != null && /[\p{L}\p{N}]/u.test(t) && t.length > 1 && parseFloat(c.fontSize) >= 9) { const a = fg.a * opUp(d), L = fg.L * a + bg * (1 - a), k = (Math.max(L, bg) + .05) / (Math.min(L, bg) + .05);
            if (k < 2) say("faint", d, `"${t.slice(0, 14)}" barely shows (${k.toFixed(1)}:1)`); }
        }
      }
    });
    /* side by side, same kind: their pictures should be the same size */
    const lines = {}; g.forEach((e, i) => { if (onScreen(boxes[i])) (lines[Math.round(boxes[i].top / 4)] ||= []).push(e); });
    for (const k in lines) { const row = lines[k]; if (row.length < 3) continue;
      const a = row.map(e => { const im = e.querySelector("img"); if (!im || !vis(im)) return 0; const r = im.getBoundingClientRect(); return r.width * r.height; });
      if (a.every(Boolean)) { const s = Math.max(...a) / Math.min(...a); if (s >= 2.5) say("uneven", row[0].parentElement, `pictures side by side differ ${s.toFixed(1)}x in size`); } }
  }
  /* the pictures the pixel eye should look at: one per repeated item, on screen */
  const pics = [];
  caps: for (const g of groups) for (const e of g) { const q = e.getBoundingClientRect(); if (!onScreen(q) || q.top < 0 || q.bottom > H - 60) continue;
    const im = [...e.querySelectorAll("img")].find(vis); if (!im || !im.complete || !im.naturalWidth) continue;
    const r = im.getBoundingClientRect(); if (r.width < 20 || r.height < 12) continue;
    const words = [...e.querySelectorAll("*")].filter(d => d.childElementCount === 0 && d.textContent.trim() && vis(d) && !d.contains(im)).map(d => d.getBoundingClientRect()).filter(w => w.width).map(w => [w.left, w.top, w.right, w.bottom]);
    pics.push({ id: "eye" + pics.length, at: pathOf(im), box: [q.left, q.top, q.right, q.bottom], ib: [r.left - 40, r.top - 40, r.right + 40, r.bottom + 40], words });
    im.setAttribute("data-eyepic", "eye" + (pics.length - 1)); if (pics.length >= 14) break caps; }
  return { issues, pics, groups: groups.length };
}

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
  const { issues, pics, groups } = await page.evaluate(boxEye, OK);
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
