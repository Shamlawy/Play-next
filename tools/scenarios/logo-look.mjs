/* Queue row logos: how they look, and the "design eye" on them (Oct 2026, the owner: "the logos look ugly … ensure you
   can pick those ugly design or disproportionate stuff"; v290 shipped look A: an upright slot inside the row).
     node tools/kit.mjs run tools/scenarios/logo-look.mjs --widths 412,859
     LOOK=now|old (default both)   SHOTS=dir (a screenshot per look)   EYEDBG=dir (each logo's pixel mask)
   Gives eight seed games a made-up logo (tools/standin-logos.mjs: the shapes that went wrong on the owner's phone), then
   for each look finds where every logo REALLY lands on screen: a screenshot with all logos hidden, then one per logo, and
   the pixels that changed are that logo (after rotation, fitting, clipping, filters, whatever the CSS did). On those pixels:
     spill   — drawn outside its own row (over the gap, or over the next row)
     edge    — runs off the side of the screen
     words   — sits on the row's words (title, date, hours, tag)
     faint   — its pixels barely differ from what's under them (a near-black logo on a dark row)
     sizes   — the biggest logo on screen is 5x+ the smallest (as Nexi's own check), or one is under 16px / 15% of its row tall
   and on every row: a name squeezed under 90px, a picture squashed under 24px, a date/hours/tag broken over two lines.
   "now" is the app as it is and must come out clean; "old" puts v289's look back (hanging off the row, tilted, one line
   when folded, no fitting) and must be flagged, so a blind eye fails too. The app's own version of these checks (by
   boxes, with the owner's real logos) is v290's extend("lookDesign"). */
import fs from "node:fs";
import { LOGOS } from "../standin-logos.mjs";

/* v289's row logo, put back over the app's CSS (lfRule's per-game sizes are beaten by the extra class) */
const OLD = `#p-queue .row.artrow > .rowin { display: flex; padding-right: 132px; }
  #p-queue .row.artrow .rowart { right: -30px; top: -23px; bottom: -18px; width: 150px; opacity: .72; overflow: visible; display: block; }
  #p-queue .row.artrow .rowart img { transform: rotate(-14deg); transform-origin: 62% 62%; object-position: left center; filter: drop-shadow(0 0 1.5px rgba(226,238,255,.8));
    width: 100% !important; height: 100%; margin: 0; object-view-box: none; object-fit: contain; }
  @media (max-width: 430px) { #p-queue .row.artrow > .rowin { padding-right: 105px; } #p-queue .row.artrow .rowart { width: 117px; } }`;
const LOOKS = { now: "", old: OLD };

export default async ({ p, w }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const looks = (process.env.LOOK || Object.keys(LOOKS).join(",")).split(",");
  let ok = true;
  await p.page.evaluate(L => { L.forEach((l, i) => { const g = S.games[i]; if (g) g.art = l.url; }); S.rowArt = true; S.deckOff = true; saveNow(); }, LOGOS);
  for (const look of looks) {
    await p.page.evaluate(css => {
      let st = document.getElementById("looktry"); if (!st) { st = document.createElement("style"); st.id = "looktry"; document.head.appendChild(st); }
      st.textContent = css; lfBox = null; renderQueue(); goTab("games"); scrollTo(0, 0);
    }, LOOKS[look]);
    await p.page.waitForTimeout(900);
    await p.page.evaluate(() => lfApply());
    await p.page.waitForTimeout(1600);
    /* the app's own logo fitting measures one logo at a time when idle: wait until it's done, or logos resize mid-check */
    await p.page.waitForFunction(() => !lfBusy && !lfQ.length, null, { timeout: 8000 }).catch(() => {});
    await p.page.waitForTimeout(300);
    /* the rows sit under On deck: bring the first logo row near the top, instantly (the app scrolls smoothly, and a page
       still gliding moves every logo between the eye's pictures), then wait until it's still */
    await p.page.evaluate(() => { const r = document.querySelector("#p-queue .row.artrow"); if (!r) return;
      const els = [...document.querySelectorAll("main, .wrap")].filter(e => e.scrollHeight > e.clientHeight + 10 && e.getClientRects().length);
      (els[0] || document.scrollingElement).scrollBy({ top: Math.round(r.getBoundingClientRect().top - 60), behavior: "instant" }); });
    await p.page.waitForFunction(() => new Promise(res => { const r = document.querySelector("#p-queue .row.artrow"), a = r && r.getBoundingClientRect().top;
      setTimeout(() => res(!r || Math.abs(r.getBoundingClientRect().top - a) < .5), 250); }), null, { timeout: 5000 }).catch(() => {});
    if (shots) await p.page.screenshot({ path: `${shots}/${w}-${look}.png` });
    const found = await eye(p.page);
    if (process.env.EYEDBG) console.log(JSON.stringify(found.dbgRows));
    if (process.env.EYEDBG) (found.masks || []).forEach((m, i) => fs.writeFileSync(`${process.env.EYEDBG}/${w}-${look}-row${i + 1}.png`, Buffer.from(m.split(",")[1], "base64")));
    const want = look === "now" ? found.issues.length === 0 && found.n > 3 : found.issues.length > 0;
    if (!want) ok = false;
    console.log(`  ${want ? "✓" : "✗"} ${w}px ${look.padEnd(4)} ${found.n} logos on screen: ${found.issues.length ? found.issues.join(" · ") : "nothing wrong"}`);
  }
  await p.page.evaluate(() => { const st = document.getElementById("looktry"); if (st) st.remove(); });
  if (p.errors.length) { ok = false; console.log("  errors: " + p.errors.join("; ")); }
  return ok;
};

/* the design eye: every on-screen row logo, by its real pixels */
async function eye(page) {
  const rows = await page.evaluate(() => {
    document.getAnimations().forEach(a => a.pause());
    return [...document.querySelectorAll("#p-queue .row.artrow")].map(r => {
      const b = r.getBoundingClientRect(), a = r.querySelector(".rowart"), op = a ? +getComputedStyle(a).opacity : 0;
      const words = [...r.querySelectorAll(".name, .when, .score, .tag, .sortv")].filter(e => getComputedStyle(e).color !== "rgba(0, 0, 0, 0)")
        .map(e => e.getBoundingClientRect()).filter(q => q.width).map(q => [q.left, q.top, q.right, q.bottom]);
      const ib = a && a.querySelector("img") ? a.querySelector("img").getBoundingClientRect() : b;
      return { id: r.dataset.row, box: [b.left, b.top, b.right, b.bottom], ib: [ib.left - 6, ib.top - 6, ib.right + 6, ib.bottom + 6], op, words, shown: !!(a && a.getClientRects().length && getComputedStyle(a).display !== "none") };
    }).filter(r => r.shown && r.box[1] >= 0 && r.box[1] < innerHeight - 90);
  });
  /* floating layers (Nexi, his bubble and rays, the tab bar, the rail, toasts) move on their own: hidden while measuring */
  await page.evaluate(() => { for (const e of document.querySelectorAll("body *")) { const c = getComputedStyle(e);
    if ((c.position === "fixed" || c.position === "sticky") && !e.closest("#p-queue")) e.setAttribute("data-eyehide", ""); } });
  const skip = [];
  /* the row itself: names squeezed to almost nothing, pictures squashed, short words broken over lines */
  const squeezed = await page.evaluate(() => [...document.querySelectorAll("#p-queue .row.artrow")].map((r, i) => {
    const n = r.querySelector(".name"), b = r.getBoundingClientRect(); if (b.top < 0 || b.top > innerHeight - 90) return "";
    const out = [];
    if (n && getComputedStyle(n).color !== "rgba(0, 0, 0, 0)" && n.clientWidth < 90 && n.scrollWidth > n.clientWidth) out.push(`row ${i + 1}: name squeezed to ${n.clientWidth}px`);
    /* a picture beside the name squashed to a sliver (v-next: two-line CSS without max-width: none did that) */
    for (const im of r.querySelectorAll(":scope > div > img")) { const q = im.getBoundingClientRect(); if (q.width < 24 || q.height < 24) out.push(`row ${i + 1}: picture squashed to ${Math.round(q.width)}x${Math.round(q.height)}`); }
    /* short words (a date, "16h", a tag) broken over several lines */
    for (const e of r.querySelectorAll(".when, .score, .tag, .rank, .sortv")) { const c = getComputedStyle(e), t = e.textContent.trim(), lh = parseFloat(c.lineHeight) || parseFloat(c.fontSize) * 1.25;
      const h = e.getBoundingClientRect().height - parseFloat(c.paddingTop) - parseFloat(c.paddingBottom) - parseFloat(c.borderTopWidth) - parseFloat(c.borderBottomWidth);
      if (t && t.length <= 20 && h > lh * 1.6) out.push(`row ${i + 1}: "${t.slice(0, 12)}" breaks onto ${Math.round(h / lh)} lines`); }
    return out.join("\n"); }).filter(Boolean).join("\n").split("\n").filter(Boolean));
  const hide = sel => page.evaluate(sel => {
    /* again before every picture: Nexi's sparks and pop-ups come and go */
    for (const e of document.querySelectorAll("body *")) { const c = getComputedStyle(e);
      if ((c.position === "fixed" || c.position === "sticky") && !e.closest("#p-queue")) e.setAttribute("data-eyehide", ""); }
    document.getAnimations().forEach(a => a.pause());
    let s = document.getElementById("eyehide"); if (!s) { s = document.createElement("style"); s.id = "eyehide"; document.head.appendChild(s); }
    s.textContent = `[data-eyehide], [data-eyehide] * { visibility: hidden !important; } #p-queue .rowart { visibility: hidden !important; } ${sel ? `#p-queue [data-row="${CSS.escape(sel)}"] .rowart { visibility: visible !important; }` : ""}`;
  }, sel);
  const snap = async () => { await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))); await page.waitForTimeout(250); return "data:image/png;base64," + (await page.screenshot({ scale: "css" })).toString("base64"); };
  await hide(null); const base = await snap();
  const each = [];
  for (const r of rows) { await hide(r.id); each.push(await snap()); }
  /* a second "no logos" picture at the end: whatever changed between the two moved on its own and doesn't count */
  await hide(null); const base2 = await snap();
  await page.evaluate(() => { document.getElementById("eyehide").remove(); document.querySelectorAll("[data-eyehide]").forEach(e => e.removeAttribute("data-eyehide")); document.getAnimations().forEach(a => a.play()); });
  return page.evaluate(async ([base, base2, each, rows, skip, dbg]) => {
    const px = async u => { const im = new Image(); im.src = u; await im.decode(); const c = document.createElement("canvas"); c.width = im.width; c.height = im.height;
      const x = c.getContext("2d"); x.drawImage(im, 0, 0); return { w: c.width, h: c.height, d: x.getImageData(0, 0, c.width, c.height).data }; };
    const B = await px(base), B2 = await px(base2), L = (d, i) => (.2126 * d[i] + .7152 * d[i + 1] + .0722 * d[i + 2]) / 255, lin = v => v <= .04 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4;
    const issues = [], sizes = [], masks = [], outs = [];
    for (let k = 0; k < rows.length; k++) {
      const r = rows[k], A = await px(each[k]), thr = Math.max(12, 40 * Math.min(1, r.op / .6)); let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1, n = 0, out = 0, over = 0, words = 0, edge = 0; const con = [];
      for (let y = 0; y < A.h; y += 1) for (let x = 0; x < A.w; x += 1) {
        const i = (y * A.w + x) * 4, a = A.d, b = B.d;
        /* a logo's pixels can only be inside its own picture's box (after rotation); anything else moved on its own */
        if (x < r.ib[0] || x > r.ib[2] || y < r.ib[1] || y > r.ib[3]) continue;
        if (Math.abs(b[i] - B2.d[i]) + Math.abs(b[i + 1] - B2.d[i + 1]) + Math.abs(b[i + 2] - B2.d[i + 2]) >= 40) continue;
        if (skip.some(q => x >= q[0] && x <= q[2] && y >= q[1] && y <= q[3])) continue;
        if (Math.abs(a[i] - b[i]) + Math.abs(a[i + 1] - b[i + 1]) + Math.abs(a[i + 2] - b[i + 2]) < thr) continue;
        n++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        const [l, t, rr, bb] = r.box;
        if (x < l - 3 || x > rr + 3 || y < t - 3 || y > bb + 3) { out++; if (dbg && outs.length < 6) outs.push([x, y]); if (rows.some((o, j) => j !== k && x >= o.box[0] && x <= o.box[2] && y >= o.box[1] && y <= o.box[3])) over++; }
        if (x >= A.w - 2) edge++;
        if (r.words.some(q => x >= q[0] && x <= q[2] && y >= q[1] && y <= q[3])) words++;
        if ((x + y) % 3 === 0) { const la = lin(L(a, i)), lb = lin(L(b, i)); con.push((Math.max(la, lb) + .05) / (Math.min(la, lb) + .05)); }
      }
      if (dbg) { const c = document.createElement("canvas"); c.width = A.w; c.height = A.h; const x = c.getContext("2d"), o = x.createImageData(A.w, A.h);
        for (let i = 0; i < A.d.length; i += 4) { const d = Math.abs(A.d[i] - B.d[i]) + Math.abs(A.d[i + 1] - B.d[i + 1]) + Math.abs(A.d[i + 2] - B.d[i + 2]); o.data[i] = o.data[i + 1] = o.data[i + 2] = d < 40 ? 0 : 255; o.data[i + 3] = 255; }
        x.putImageData(o, 0, 0); masks.push(c.toDataURL()); }
      if (!n) { issues.push(`row ${k + 1}: logo not visible at all`); continue; }
      const rh = r.box[3] - r.box[1], tag = `row ${k + 1}`;
      if (out > 40) issues.push(`${tag}: spills ${Math.round(Math.max(r.box[1] - y0, y1 - r.box[3], x1 - r.box[2], 0))}px out of its row${over > 40 ? " onto the next one" : ""}`);
      if (edge > 6) issues.push(`${tag}: runs off the screen edge`);
      if (r.op >= .5 && words > 60) issues.push(`${tag}: sits on the row's words`);
      con.sort((p, q) => p - q); const med = con[con.length >> 1] || 1;
      if (r.op >= .5 && med < 1.6) issues.push(`${tag}: hard to see (contrast ${med.toFixed(1)}:1)`);
      if ((y1 - y0 + 1) < Math.max(16, rh * .15)) issues.push(`${tag}: too small (${Math.round((y1 - y0 + 1) / rh * 100)}% of the row's height)`);
      sizes.push((x1 - x0 + 1) * (y1 - y0 + 1));
    }
    if (sizes.length > 1) { const k = Math.max(...sizes) / Math.min(...sizes); if (k >= 5) issues.push(`sizes: the biggest logo is ${k.toFixed(1)}x the smallest`); }
    return { n: rows.length, issues, masks, dbgRows: dbg ? [rows.map(r => [r.id, r.box.map(Math.round)]), outs] : 0 };
  }, [base, base2, each, rows, skip, !!process.env.EYEDBG]).then(o => ({ ...o, issues: [...squeezed, ...o.issues] }));
}
