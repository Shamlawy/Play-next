/* The Queue's closed rows, its control bar and the second look, v301 (the owner picked round 2 A "Strap" + the new bar, with
   F "Character select" as a second option: "A and the new bar, make it and F too as a second option").
     node tools/kit.mjs run tools/scenarios/queue-rows.mjs --widths 412,859      SHOTS=dir (the rows, the menu, the second look)
   Checks:
     - every closed row is the same height (a two-line name doesn't make its row taller)
     - every closed row has the strap, the spot, the box art, the name, the open card's headline and the progress line; nothing in it
       runs past the row; the name keeps room (60px+); the ticket shows unfolded and hides folded, where the date and hours sit under the name
     - the strap is drawn in the game's colour, the cover stands straight, a released game's ticket stub hangs torn and a coming one's doesn't
     - the bar: All / Out / Coming / Played tabs with their counts (+ the lists button); a tab shows only its games
     - Order opens a grouped menu; Most hyped sorts the rows and each says its hype; Make this my order keeps that order as My order
     - Shuffle: a tap lights up a game that's out and says it; a hold opens the full shuffler; Boss shows HP and opens the boss screen
     - Character select: a panel + a cover per game; a tap on another cover moves the panel to it, a tap on the picked one opens its
       card where the panel was, one tap on the card closes it back to the panel */
import fs from "node:fs";

export default async ({ p, w, fail }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const pg = p.page, folded = w <= 500;
  const shot = async n => { if (shots) await pg.screenshot({ path: `${shots}/${n}-${w}.png` }); };
  await pg.evaluate(() => { const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  const center = sel => pg.evaluate(s => { const e = document.querySelector(s); if (!e) return null; e.scrollIntoView({ block: "center", behavior: "instant" });
    const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel);
  const tapOn = async (sel, wait = 500) => { const c = await center(sel); if (!c) { fail("nothing to tap at " + sel); return false; } await pg.waitForTimeout(150); await p.tap(c[0], c[1]); await pg.waitForTimeout(wait); return true; };

  /* the rows */
  await pg.evaluate(() => { S.sort = "manual"; S.qFilter = "all"; delete S.qLook; saveNow(); goTab("games"); collapsed = true; focusId = null; renderQueue(); scrollTo(0, 0); });
  await pg.waitForTimeout(900);
  await pg.evaluate(() => { const r = document.querySelector("#p-queue .row.qs"); if (r) r.scrollIntoView({ block: "start", behavior: "instant" }); scrollBy(0, -140); });
  await pg.waitForTimeout(700); await shot("rows");
  const rows = await pg.evaluate(fold => [...document.querySelectorAll("#p-queue .row.qs")].map(r => {
    const R = r.getBoundingClientRect(), q = s => r.querySelector(s), vis = e => !!e && e.getClientRects().length > 0 && getComputedStyle(e).display !== "none";
    const out = [...r.querySelectorAll(".rowin > *, .rowart, .qstk")].filter(e => vis(e)).filter(e => { const b = e.getBoundingClientRect(); return b.left < R.left - 1 || b.right > R.right + 1 || b.top < R.top - 1 || b.bottom > R.bottom + 1; }).map(e => e.className || e.tagName);
    const tk = q(".qstk"), g = S.games.find(x => x.id === r.dataset.row) || {};
    return { id: r.dataset.row, strap: vis(q(".qsstrap")) && getComputedStyle(q(".qsstrap"), "::before").backgroundImage.includes("gradient") && getComputedStyle(q(".qsstrap"), "::before").transform !== "none", rank: (q(".rank") || {}).textContent,
      cover: vis(q(".rowin > img")) && q(".rowin > img").getBoundingClientRect().width > 30, coverTilt: q(".rowin > img") ? getComputedStyle(q(".rowin > img")).transform : "",
      name: q(".name") ? Math.round(q(".name").getBoundingClientRect().width) : 0, head: (q(".qshd") || {}).textContent || "", pg: !!q(".qspg"),
      ticket: vis(tk), snap: !!(tk && tk.classList.contains("qssnap")), coming: r.classList.contains("qs-coming"), played: r.classList.contains("qs-played"),
      when: vis(q(".rowmeta > .when")), out, h: Math.round(R.height), title: g.title };
  }), folded);
  if (rows.length < 4) fail(`only ${rows.length} closed rows`);
  /* v310 (the owner: "some look bigger than the other while closed"): a two-line name made its row taller */
  const hs = rows.map(r => r.h); if (Math.max(...hs) - Math.min(...hs) > 1) fail(`closed rows differ in height: ${[...new Set(hs)].join(" / ")}px`);
  for (const r of rows) {
    if (!r.strap) fail(`row ${r.rank}: no slanted strap in its colour`);
    if (!r.cover) fail(`row ${r.rank}: the box art is missing or squashed`);
    if (r.coverTilt !== "none") fail(`row ${r.rank}: the box art is tilted (${r.coverTilt})`);
    if (!r.head.trim()) fail(`row ${r.rank}: no headline`);
    if (!r.pg) fail(`row ${r.rank}: no progress line`);
    if (r.name < 60) fail(`row ${r.rank}: the name is squeezed to ${r.name}px`);
    if (r.out.length) fail(`row ${r.rank}: ${r.out.join(", ")} runs past the row`);
    if (folded ? r.ticket : !r.ticket) fail(`row ${r.rank}: the ticket ${folded ? "shows folded" : "is missing unfolded"}`);
    if (folded && !r.when && !r.coming) fail(`row ${r.rank}: folded, the date isn't under the name`);
    if (!folded && r.coming && r.snap) fail(`row ${r.rank}: a coming game's ticket stub is torn`);
    if (!folded && !r.coming && !r.snap) fail(`row ${r.rank}: a released game's ticket stub isn't torn`);
  }

  /* the bar: the tabs */
  const tabs = await pg.evaluate(() => [...document.querySelectorAll("#p-queue .qdtab[data-qfilter]")].map(b => [b.dataset.qfilter, +(b.querySelector("small") || {}).textContent]));
  if (tabs.map(t => t[0]).slice(0, 4).join() !== "all,out,soon,done") fail("the tabs aren't All / Out / Coming / Played: " + tabs.map(t => t[0]).join());
  if (!(await pg.evaluate(() => !!document.querySelector("#p-queue .qdtab.qdlist[data-lists]")))) fail("no lists button in the bar");
  await tapOn('#p-queue .qdtab[data-qfilter="soon"]', 700);
  const soon = await pg.evaluate(() => ({ on: S.qFilter, n: document.querySelectorAll("#p-queue [data-row]").length, bad: [...document.querySelectorAll("#p-queue [data-row]")].filter(r => { const g = S.games.find(x => x.id === r.dataset.row); return !g || isOutNow(g) || isPlayed(g); }).length }));
  const want = (tabs.find(t => t[0] === "soon") || [])[1];
  if (soon.on !== "soon" || soon.bad || soon.n !== want) fail(`Coming tab: filter ${soon.on}, ${soon.n} rows (count said ${want}), ${soon.bad} not coming`);
  await tapOn('#p-queue .qdtab[data-qfilter="all"]', 700);

  /* Order */
  await tapOn("#sortby", 500); await shot("order");
  const menu = await pg.evaluate(() => { const m = document.getElementById("sortpop"); return m && !m.hidden ? { groups: m.querySelectorAll(".qdgh").length, n: m.querySelectorAll("[data-sort]").length,
    inside: (() => { const r = m.getBoundingClientRect(); return r.left >= -1 && r.right <= innerWidth + 1; })() } : null; });
  if (!menu) fail("Order didn't open its menu");
  else { if (menu.groups !== 3 || menu.n < 15) fail(`the Order menu has ${menu.groups} groups and ${menu.n} orders`); if (!menu.inside) fail("the Order menu runs off the screen"); }
  await tapOn('#sortpop [data-sort="hype"]', 900);
  const hyped = await pg.evaluate(() => { const ids = [...document.querySelectorAll("#p-queue [data-row]")].map(r => r.dataset.row), h = ids.map(id => (S.games.find(g => g.id === id) || {}).hype || 0);
    return { sort: S.sort, sorted: h.every((v, i) => !i || v <= h[i - 1]), says: document.querySelectorAll("#p-queue .row.qs .sortv").length, rows: document.querySelectorAll("#p-queue .row.qs").length, label: (document.querySelector("#sortby .qdlb b") || {}).textContent, ids }; });
  if (hyped.sort !== "hype" || !hyped.sorted) fail(`Most hyped didn't sort (${hyped.sort})`);
  if (hyped.says !== hyped.rows) fail(`sorted by hype, ${hyped.says} of ${hyped.rows} rows say their hype`);
  if (hyped.label !== "Most hyped") fail(`the Order button says "${hyped.label}"`);
  await tapOn("#sortby", 500); await tapOn("#qkeep", 900);
  const kept = await pg.evaluate(() => ({ sort: S.sort, ids: [...document.querySelectorAll("#p-queue [data-row]")].map(r => r.dataset.row) }));
  if (kept.sort !== "manual" || kept.ids.join() !== hyped.ids.join()) fail(`Make this my order: sort ${kept.sort}, order ${kept.ids.slice(0, 4)} vs ${hyped.ids.slice(0, 4)}`);

  /* Shuffle and Boss */
  await pg.evaluate(() => { collapsed = true; focusId = null; renderQueue(); scrollTo(0, 0); });
  await tapOn("#p-queue [data-qshuf]", 350);
  const lit = await pg.evaluate(() => { const r = document.querySelector("#p-queue .qslit"), g = r && S.games.find(x => x.id === r.dataset.row); return r ? { out: !!g && isOutNow(g) && !isPlayed(g), t: g && g.title, ring: getComputedStyle(r, "::before").animationName === "qslit" } : null; });
  if (!lit) fail("Shuffle didn't light up a game"); else if (!lit.out) fail(`Shuffle picked ${lit.t}, which isn't out or is played`); else if (!lit.ring) fail("Shuffle's picked row has no light ring");
  await shot("shuffle");
  await pg.waitForTimeout(2400);
  const sh = await center("#p-queue [data-qshuf]");
  const hit = await pg.evaluate(([x, y]) => { const e = document.elementFromPoint(x, y); return e ? (e.closest("[data-qshuf]") ? "ok" : e.outerHTML.slice(0, 120)) : "nothing"; }, sh);
  if (hit !== "ok") console.log("  (Shuffle's spot is under " + hit + ")");
  await p.cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: sh[0], y: sh[1] }] }); await pg.waitForTimeout(700);
  await p.cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] }); await pg.waitForTimeout(700);
  if (!(await pg.evaluate(() => !!document.getElementById("shuf")))) fail("holding Shuffle didn't open the shuffler");
  await pg.evaluate(() => { const o = document.getElementById("shuf"); if (o) o.remove(); document.documentElement.classList.remove("shuffling"); });
  /* v286: the HP only while Grumble is on; off (the default) it's a plain "Boss" */
  const bossTxt = () => pg.evaluate(() => (document.querySelector("#fx-boss .qdhp b") || {}).textContent);
  await pg.evaluate(() => { S.grum = false; renderQueue(); });
  const off = await bossTxt(), bar = await pg.evaluate(() => !!document.querySelector("#fx-boss .qdbar"));
  if (off !== "Boss" || bar) fail(`with Grumble off Boss shows "${off}"${bar ? " and an HP bar" : ""}`);
  await pg.evaluate(() => { S.grum = true; renderQueue(); });
  const hp = await bossTxt();
  if (!/^\d+$/.test(hp || "")) fail(`with Grumble on Boss shows "${hp}" instead of the HP`);
  await pg.evaluate(() => { S.grum = false; renderQueue(); });
  await tapOn("#fx-boss", 700);
  if (!(await pg.evaluate(() => !!document.getElementById("fxboss")))) fail("Boss didn't open the boss screen");
  await pg.evaluate(() => { const o = document.getElementById("fxboss"); if (o) o.remove(); });

  /* the second look */
  await pg.evaluate(() => { S.qLook = "select"; saveNow(); collapsed = true; focusId = null; renderQueue(); scrollTo(0, 0); });
  await pg.waitForTimeout(600);
  await pg.evaluate(() => { const r = document.querySelector("#p-queue .qfsel"); if (r) r.scrollIntoView({ block: "start", behavior: "instant" }); scrollBy(0, -80); });
  await pg.waitForTimeout(500); await shot("select");
  const sel = await pg.evaluate(() => ({ panel: !!document.querySelector("#p-queue .qfsel"), cells: document.querySelectorAll("#p-queue .qfc").length, games: queueList().length,
    on: (document.querySelector("#p-queue .qfc.on") || {}).dataset?.qfpick, second: (document.querySelectorAll("#p-queue .qfc")[1] || {}).dataset?.qfpick,
    out: [...document.querySelectorAll("#p-queue .qfsel .qfin, #p-queue .qfsel .qfcv")].filter(e => { const r = e.getBoundingClientRect(), P = document.querySelector("#p-queue .qfsel").getBoundingClientRect(); return r.right > P.right + 1 || r.left < P.left - 1 || r.bottom > P.bottom + 1; }).length }));
  if (!sel.panel || sel.cells !== sel.games) fail(`Character select: panel ${sel.panel}, ${sel.cells} covers for ${sel.games} games`);
  if (sel.out) fail("Character select: something in the panel runs past it");
  if (sel.second) {
    await tapOn(`#p-queue .qfc[data-qfpick="${sel.second}"]`, 700);
    const moved = await pg.evaluate(id => ({ on: (document.querySelector("#p-queue .qfc.on") || {}).dataset?.qfpick, hero: !!document.querySelector("#p-queue .qfwrap > .hero"),
      name: (document.querySelector("#p-queue .qfsel .qft") || {}).textContent, want: (S.games.find(g => g.id === id) || {}).title }), sel.second);
    if (moved.on !== sel.second || moved.hero || moved.name !== moved.want) fail(`a tap on another cover: picked ${moved.on}, card open ${moved.hero}, panel says ${moved.name} (want ${moved.want})`);
    await tapOn(`#p-queue .qfc[data-qfpick="${sel.second}"]`, 1700);
    const opened = await pg.evaluate(() => (document.querySelector("#p-queue .qfwrap > .hero") || {}).dataset?.row);
    if (opened !== sel.second) fail(`a tap on the picked cover opened ${opened}, not its card`);
    await shot("select-open");
    const box = await pg.evaluate(() => { const f = document.querySelector("#p-queue .qfwrap > .hero .qkhead") || document.querySelector("#p-queue .qfwrap > .hero"); f.scrollIntoView({ block: "center", behavior: "instant" }); const r = f.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; });
    await pg.waitForTimeout(200); await p.tap(box[0], box[1]); await pg.waitForTimeout(1000);
    const closed = await pg.evaluate(() => ({ hero: !!document.querySelector("#p-queue .qfwrap > .hero"), panel: (document.querySelector("#p-queue .qfsel .qft") || {}).textContent }));
    if (closed.hero || !closed.panel) fail(`one tap on the open card: card still open ${closed.hero}, panel ${closed.panel}`);
  }
  /* v302: Shuffle lights a cover on its own ::after; the cover's ::before (the dark foot under its number) stays put */
  await pg.evaluate(() => { collapsed = true; focusId = null; renderQueue(); qdShuffle(); });
  await pg.waitForTimeout(300);
  const lc = await pg.evaluate(() => { const c = document.querySelector("#p-queue .qfc.qslit"); if (!c) return null;
    const b = getComputedStyle(c, "::before"), a = getComputedStyle(c, "::after");
    return { foot: b.backgroundImage.includes("gradient") && b.animationName === "none", ring: a.animationName === "qslit" && a.boxShadow !== "none" }; });
  if (!lc) fail("Character select: Shuffle didn't light a cover");
  else if (!lc.foot || !lc.ring) fail(`Character select: Shuffle's light — foot shading kept ${lc.foot}, ring on ::after ${lc.ring}`);
  await pg.evaluate(() => { delete S.qLook; saveNow(); collapsed = false; focusId = null; renderQueue(); });
};
