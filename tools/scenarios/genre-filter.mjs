/* A genre picked on a game page (the owner, v303: "I pressed on a genre ... the genre says undefined on top, the deck games are not
   that genre, and on deck some have a tall image and some wide").
     node tools/kit.mjs run tools/scenarios/genre-filter.mjs --widths 412,859      SHOTS=dir
   Checks:
     - a tap on a genre chip on a game page opens the Queue filtered to that genre
     - the bar shows the genre's own name and count on its tab (never "undefined"), with a drawn icon (no emoji), and it's lit
     - nothing in the bar runs into its neighbour, every button keeps its word whole ("My order" not "My ord…")
     - On deck only holds games of that genre and says which ("On deck · Action"); under Played (nothing to play) it's gone
     - the three On deck cards share one shape: the picture the same size and place in each */
import fs from "node:fs";

export default async ({ p, w, fail }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const pg = p.page;
  const shot = async n => { if (shots) await pg.screenshot({ path: `${shots}/${n}-${w}.png` }); };
  await pg.evaluate(() => { const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  /* a game page with a genre chip that not every game shares */
  const pick = await pg.evaluate(() => {
    const all = allGames(), cnt = t => all.filter(x => gdGenreSet(x).has(gdKey(t))).length;
    for (const g of S.games) { const t = platList(g.genres).find(t => cnt(t) > 1 && cnt(t) < all.length); if (t) return { id: g.id, t }; }
    return null; });
  if (!pick) return fail("no game with a genre a few games share");
  await pg.evaluate(id => { S.qFilter = "all"; saveNow(); openFull(id); }, pick.id);
  await pg.waitForTimeout(1200);
  const c = await pg.evaluate(t => { const b = [...document.querySelectorAll("#full [data-gtag]")].find(b => b.dataset.gtag === t);
    if (!b) return null; b.scrollIntoView({ block: "center", behavior: "instant" }); const r = b.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, pick.t);
  if (!c) return fail("no chip for " + pick.t + " on the game page");
  await pg.waitForTimeout(200); await p.tap(c[0], c[1]); await pg.waitForTimeout(1200);
  await pg.evaluate(() => scrollTo(0, 0)); await pg.waitForTimeout(400); await shot("genre");
  const r = await pg.evaluate(t => {
    const bar = document.querySelector("#p-queue .qdeck"), want = allGames().filter(x => gdGenreSet(x).has(gdKey(t)));
    const tab = bar && bar.querySelector(`.qdtab[data-qfilter="G:${CSS.escape(t)}"]`);
    const btns = bar ? [...bar.querySelectorAll(":scope > *, .qdtab")].filter(e => e.getClientRects().length) : [];
    const hits = [];
    for (let i = 0; i < btns.length; i++) for (let j = i + 1; j < btns.length; j++) {
      if (btns[i].contains(btns[j]) || btns[j].contains(btns[i])) continue;
      const a = btns[i].getBoundingClientRect(), b = btns[j].getBoundingClientRect();
      if (Math.min(a.right, b.right) - Math.max(a.left, b.left) > 2 && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 2) hits.push((btns[i].className || "") + " × " + (btns[j].className || ""));
    }
    const cut = bar ? [...bar.querySelectorAll("b, .qdtl, small")].filter(e => e.getClientRects().length && e.scrollWidth > e.clientWidth + 1).map(e => e.textContent) : [];
    const deck = document.querySelector("#p-queue .deck"), cards = deck ? [...deck.querySelectorAll("[data-opg]")] : [];
    const pics = cards.map(x => { const i = x.querySelector("img"), R = x.getBoundingClientRect(), b = i ? i.getBoundingClientRect() : null;
      return b ? [Math.round(b.width / R.width * 100), Math.round(b.height / R.height * 100), Math.round(b.top - R.top)] : null; });
    return { on: S.qFilter, tab: tab ? tab.textContent.trim() : null, lit: tab ? tab.getAttribute("aria-pressed") : null, icon: tab ? !!tab.querySelector("svg") : false,
      undef: /undefined|NaN|null/.test(bar ? bar.textContent : ""), hits, cut, want: want.length, rows: document.querySelectorAll("#p-queue [data-row]").length,
      head: deck ? (deck.querySelector(".deck-h") || {}).textContent : null,
      off: cards.map(x => S.games.find(g => g.id === x.dataset.opg)).filter(g => !g || !gdGenreSet(g).has(gdKey(t))).map(g => g ? g.title : "?").length, n: cards.length, pics };
  }, pick.t);
  if (r.on !== "G:" + pick.t) fail("the Queue isn't filtered to the genre: " + r.on);
  if (r.undef) fail("the bar says undefined / NaN / null");
  if (!r.tab || !r.tab.includes(pick.t)) fail("the genre's tab doesn't name it: " + r.tab);
  if (r.lit !== "true") fail("the genre's tab isn't lit");
  if (!r.icon) fail("the genre's tab has no drawn icon");
  if (r.hits.length) fail("bar buttons overlap: " + r.hits.join(", "));
  if (r.cut.length) fail("bar words cut short: " + r.cut.join(", "));
  if (r.rows !== r.want) fail(`${r.rows} rows for ${r.want} games of the genre`);
  if (r.off) fail(r.off + " On deck card(s) aren't that genre");
  if (r.n && !(r.head || "").includes(pick.t)) fail("On deck doesn't say it's the genre's: " + r.head);
  if (r.pics.some(x => !x)) fail("an On deck card has no picture");
  else if (r.pics.length > 1 && r.pics.some(x => x.join() !== r.pics[0].join())) fail("On deck pictures differ in shape: " + JSON.stringify(r.pics));
  /* Played: nothing there to play next, so no On deck */
  await pg.evaluate(() => { S.qFilter = "done"; saveNow(); renderQueue(); }); await pg.waitForTimeout(500);
  if (await pg.evaluate(() => !!document.querySelector("#p-queue .deck [data-opg]"))) fail("On deck still shows under Played");
  await pg.evaluate(() => { S.qFilter = "all"; saveNow(); renderQueue(); }); await pg.waitForTimeout(500); await shot("all");
  const a = await pg.evaluate(() => [...document.querySelectorAll("#p-queue .deck [data-opg]")].map(x => { const i = x.querySelector("img"), R = x.getBoundingClientRect(), b = i.getBoundingClientRect();
    return [Math.round(b.width / R.width * 100), Math.round(b.height / R.height * 100), Math.round(b.top - R.top)].join(); }));
  if (a.length > 1 && new Set(a).size > 1) fail("On deck pictures differ in shape under All: " + a.join(" | "));
  console.log(`  ${w}: genre ${r.tab} · ${r.rows} rows · deck ${r.n} "${r.head}" · pics ${a.join(" | ")}`);
};
