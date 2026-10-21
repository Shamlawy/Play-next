/* v317: the skills book (Taste › Book). Two of the seed's played games get made-up pages (localStorage gq:book, the cache the
   sealed entries open into), then the Book tab opens: the opening must land on the game finished last (its picture page on
   the left unfolded, on top folded), with its skills beside it; nothing spills out of a page; short words stay whole; a swipe
   turns a page forward (the page follows the finger), a tap on the left side turns it back, and the book stays one book.
   Calm mode opens straight on the landed page. Also checks the made-up words never reach the cloud backup (S).
     node tools/kit.mjs run tools/scenarios/skills-book.mjs --widths 412,859,360      (SHOTS=dir saves pictures) */
import fs from "node:fs";
import path from "node:path";
export default async ({ p, w, seed, fail }) => {
  const bad = [], shots = process.env.SHOTS, snap = async n => { if (shots) { fs.mkdirSync(shots, { recursive: true }); await p.page.screenshot({ path: path.join(shots, `book-${w}-${n}.png`) }); } };
  const v = await p.page.evaluate(() => APP_V);
  /* nxEvo: no "I evolved" power-up 15s after load (L16): it landed on the tap that turns a page back, 1 run in 4 */
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99 })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } try { if (typeof pnPuEnd === "function") pnPuEnd(); } catch (e) {} });
  /* made-up pages for the three most recently finished seed games */
  const names = await p.page.evaluate(() => {
    const P = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).slice(-3).map(x => S.games.find(g => g.id === x.id));
    const sk = (n, ic, lv) => ({ ic, n, lv, gain: `Better at ${n.toLowerCase()} when it counts`, txt: "A long stretch where every choice costs something else, so you pick on purpose.",
      mind: "You practise trade-offs: every yes is a no to something else.", life: "You get stuck less on what to do first.", tr: "Pick the one thing that matters most today." });
    /* five skills, two, then four (the last): every skill gets a page unfolded (5+ used to be dropped) and an odd page
       count gets a blank so each game's picture still lands on a left page */
    /* the first skill's words are long (a page's words must shrink to fit, not get cut off at its foot) */
    const long = Object.assign(sk("Thinking ahead", "tactic", 3), { txt: "One careless move in a long battle loses someone for good, the rewinds run out, and every turn you weigh what could go wrong before you act.",
      mind: "You practise imagining the failure before you act, a pre-mortem: real stakes make you check the risky step twice.", life: "Fewer avoidable mistakes in plans, money and work, because you look for the weak spot first." });
    const all = [long, sk("Relationships", "bond", 2), sk("Problem solving", "tactic", 2), sk("Meaning", "heart", 3), sk("Patience", "clock", 1)];
    const book = {}; [5, 2, 4].forEach((n, i) => { book[bookNorm(P[i].title)] = { skills: all.slice(0, n) }; });
    /* the first one finished twice: its pages come once */
    S.played.push({ id: P[0].id, real: 0, t: "1999-01-01" });
    localStorage.setItem("gq:book", JSON.stringify(book)); bookStore = null; return P.map(g => g.title);
  });
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 1200);
  await snap("1-cover");
  await p.page.waitForTimeout(6500);
  const look = () => p.page.evaluate(() => {
    const r = document.querySelector("#p-taste .skb"), W = innerWidth, out = { on: !!r, at: SKB.at, land: SKB.land, n: SKB.L.length, fold: SKB.fold, intro: SKB.intro, turns: SKB.turns.length, spill: [], edge: [], broken: [] };
    if (!r) return out;
    const art = r.querySelector(SKB.fold ? ".skb-pg.r .skb-art .t" : ".skb-pg.l .skb-art .t"); out.art = art ? art.textContent : "";
    out.skills = r.querySelectorAll(".skb-pg.r .skb-sk").length;
    const cnt = (h, re) => (h.match(re) || []).length, sides = SKB.L.flatMap(x => [x.f, x.b]);
    out.tiles = sides.reduce((n, h) => n + cnt(h, /class="skb-sk"/g), 0); out.games = SKB.B.length;
    out.folios = sides.reduce((n, h) => n + cnt(h, /class="skb-fo"/g), 0);
    /* page n (cover = 0): folded each leaf is a page, unfolded leaf j holds pages 2j and 2j+1. The contents' "p. N" opens on
       that game's picture, and every page number shown is the page's own */
    const sideAt = n => SKB.fold ? (SKB.L[n] || {}).f : (SKB.L[n >> 1] || {})[n % 2 ? "b" : "f"];
    out.badNo = [];
    const cont = sides.find(h => h.includes(">Contents<")) || "";
    for (const [, t, n] of cont.matchAll(/<b>([^<]*)<\/b><span>p\. (\d+)<\/span>/g)) { const h = sideAt(+n) || "";
      if (!h.includes('class="skb-art') || !h.includes(t)) out.badNo.push(`contents: ${t} → p. ${n} isn't its picture`); }
    for (let n = 0; n < sides.length; n++) { const m = (sideAt(n) || "").match(/class="skb-fo">(\d+)</); if (m && +m[1] !== n) out.badNo.push(`page ${n} shows ${m[1]}`); }
    out.oddArt = SKB.fold ? [] : SKB.L.map((x, j) => x.f.includes('class="skb-art') ? "leaf " + j : "").filter(Boolean);
    /* words that run out of their page (a page is a fixed box: overflow is hidden, so measure the content) */
    r.querySelectorAll(".skb-pg .skb-in").forEach(x => { if (x.scrollHeight > x.clientHeight + 2) out.spill.push(`${x.scrollHeight}>${x.clientHeight}: ${x.textContent.trim().slice(0, 30)}`); });
    /* folded, the book's left half sits off the screen on purpose: only the visible page counts */
    r.querySelectorAll(SKB.fold ? ".skb-pg.r" : ".skb-pg").forEach(x => { const b = x.getBoundingClientRect(); if (b.width && (b.left < -1 || b.right > W + 1)) out.edge.push(x.className + " " + Math.round(b.left) + ".." + Math.round(b.right)); });
    r.querySelectorAll(".skb-pg .skb-kk, .skb-pg .skb-pips span, .skb-pg .skb-sk dt, .skb-pg .skb-art .m").forEach(e => {
      const lh = parseFloat(getComputedStyle(e).lineHeight) || 16; if (e.getBoundingClientRect().height > lh * 1.6) out.broken.push(e.textContent.trim()); });
    return out;
  });
  const a = await look();
  if (!a.on) bad.push("no book on the Book tab");
  else {
    if (a.intro || a.turns) bad.push(`the opening hadn't finished after 7s (intro ${a.intro}, ${a.turns} pages moving)`);
    if (a.at !== a.land) bad.push(`landed at leaf ${a.at}, expected ${a.land}`);
    if (a.art !== names[2]) bad.push(`landed on "${a.art}", expected the last finished game`);
    if (a.tiles !== 11) bad.push(`${a.tiles} skill tiles in the book, expected 11 (5 + 2 + 4)`);
    if (a.games !== 3) bad.push(`${a.games} games in the book, expected 3 (one finished twice counts once)`);
    if (a.oddArt.length) bad.push("a game's picture isn't on a left page: " + a.oddArt.join(", "));
    if (a.folios < 4) bad.push(`only ${a.folios} page numbers in the book`);
    a.badNo.forEach(x => bad.push("page numbers: " + x));
    if (a.skills < 1) bad.push("no skills beside the landed game's picture");
    a.spill.forEach(x => bad.push("words run out of a page: " + x)); a.edge.forEach(x => bad.push("past the screen edge: " + x)); a.broken.forEach(x => bad.push("broken over lines: " + x));
  }
  /* every page of the book, not only the landed one: words that run out of a page */
  const allSpill = await p.page.evaluate(async () => { const out = [], at = SKB.at;
    for (let k = 0; k <= SKB.L.length; k++) { SKB.at = k; skbRest(); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      document.querySelectorAll("#p-taste .skb-pg .skb-in").forEach(x => { if (x.scrollHeight > x.clientHeight + 2) out.push(`leaf ${k}: ${x.scrollHeight}>${x.clientHeight} ${x.textContent.trim().slice(0, 30)}`); }); }
    SKB.at = at; skbRest(); return out; });
  allSpill.forEach(x => bad.push("words run out of a page: " + x));
  await snap("2-landed");
  /* a swipe from the right edge to the left turns forward, following the finger */
  const box = await p.page.evaluate(() => { const b = document.querySelector("#p-taste .skb-stage").getBoundingClientRect(); return { x: b.left, y: b.top, w: b.width, h: b.height }; });
  const y = box.y + 120;   /* high on the page: lower down, the floating tab pill covers the book on short screens */
  const hit = await p.page.evaluate(([x, y]) => { const e = document.elementFromPoint(x, y); return e && e.closest(".skb-stage") ? "" : (e ? e.tagName + "#" + e.id + "." + String(e.className).slice(0, 40) : "nothing"); }, [box.x + box.w * .92, y]);
  if (hit) bad.push("the swipe's start point is covered by " + hit);
  await p.swipe(box.x + box.w * .92, y, box.x + box.w * .08, y, 320, 16);
  await p.page.waitForTimeout(1200);
  const b = await look();
  if (b.at !== a.at + 1) bad.push(`a swipe left didn't turn forward (leaf ${a.at} → ${b.at})`);
  b.spill.forEach(x => bad.push("after a turn, words run out of a page: " + x));
  await snap("3-turned");
  /* a tap on the left side turns back */
  await p.tap(box.x + box.w * (a.fold ? .12 : .2), box.y + 90);
  await p.page.waitForTimeout(1300);
  const c = await look();
  if (c.at !== b.at - 1) bad.push(`a tap on the left didn't turn back (leaf ${b.at} → ${c.at})`);
  if (await p.page.evaluate(() => document.querySelectorAll("#p-taste .skb-leaf").length)) bad.push("a turning page was left behind");
  /* a redraw while reading keeps the place */
  await p.page.evaluate(() => renderTaste()); await p.page.waitForTimeout(200);
  if ((await look()).at !== c.at) bad.push("a redraw sent the book back to its cover");
  /* the words never reach S (the cloud backup) */
  if (await p.page.evaluate(() => JSON.stringify(S).includes("every choice costs something"))) bad.push("book words leaked into S");
  /* calm: straight to the page, nothing moving */
  await p.page.evaluate(() => { document.documentElement.classList.add("still"); goSub("taste", "insights"); goSub("taste", "book"); });
  await p.page.waitForTimeout(500);
  const d = await look();
  if (d.at !== d.land || d.turns) bad.push(`calm: at ${d.at} (want ${d.land}), ${d.turns} pages moving`);
  await p.page.evaluate(() => document.documentElement.classList.remove("still"));
  /* leaving the tab while its pages are still being opened: nothing runs on the hidden screen, and it opens when you're back */
  await p.page.evaluate(() => { goSub("taste", "insights"); goSub("taste", "book"); goTab("home"); });
  await p.page.waitForTimeout(2500);
  const hid = await p.page.evaluate(() => ({ ms: document.querySelectorAll("#p-taste .skb-pg[style*=hidden]").length, intro: SKB.intro, turns: SKB.turns.length }));
  if (hid.ms || hid.intro || hid.turns) bad.push(`left the tab mid-opening: still running on the hidden screen (${JSON.stringify(hid)})`);
  await p.page.evaluate(() => goTab("taste")); await p.page.waitForTimeout(1500);
  if (!(await p.page.evaluate(() => !!document.querySelector("#p-taste .skb[data-on] .skb-stage")))) bad.push("back on the tab, the book didn't open");
  console.log(`  ${w}: ${a.fold ? "folded" : "spread"}, ${a.n} leaves, landed ${a.at}/${a.land} on the last game, ${a.skills} skill tiles; swipe → ${b.at}, tap back → ${c.at}`);
  (p.reports || []).filter(r => r.kind !== "slow").forEach(r => bad.push("Nexi reported: " + JSON.stringify(r).slice(0, 300)));
  bad.forEach(m => fail(m));
};
