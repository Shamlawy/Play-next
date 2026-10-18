/* The owner's taste calls that no check held yet (node tools/taste.mjs gaps, Oct 18), as checks. Each line names its rule in
   .claude/skills/play-next-design/taste.md, so a failure says which of the owner's calls a change broke:
     T4   a screen's header card carries live content (a number or a chart), not just a title
     T9   a game page uses one skeleton for every state (coming, out, playing, played): only the content changes
     T10  a game page has one main action, chosen by the game's state; the rest are tucked away
     T12  pick lists (the Similar sheet) show pictures big enough to decide from, with a line about each game
     T32  Settings, Customise and Edit groups use drawn icons (gIcon), no emoji: in the titles and the On this page rail
     T33  the open Queue card is rounded everywhere (card, box art, strap, ticket) and its picture fills the whole card
     T35  the open card's strap takes the game's own colour (two games, two colours) and the logo sits in one fixed
          bottom-right slot on every game
   `node tools/kit.mjs taste` runs it at 412 and 859 (in `all`); alone: node tools/kit.mjs run tools/scenarios/taste.mjs --widths 412
   PLANT=tools/plants/taste-broken.mjs breaks every one of them on purpose: then each must fail (app-check runs it, so a check
   that went blind fails the PR). */
import fs from "node:fs";
export default async ({ p, w, fail }) => {
  const planted = process.env.PLANT, missed = [];
  const pg = p.page, ok = (id, good, msg) => { if (planted) { if (good) missed.push(id); else console.log(`  ✓ ${w}px ${id} caught the plant: ${msg}`); return; }
    if (good) console.log(`  ✓ ${w}px ${id}`); else fail(`${w}px ${id}: ${msg}`); };
  if (planted) await pg.evaluate(fs.readFileSync(planted, "utf8"));
  await pg.evaluate(() => { if (typeof extend === "function" && typeof pnPowerUp === "function") extend("pnPowerUp", { before: () => extend.skip() }); });

  /* T4 */
  const bare = [];
  for (const [name, go] of [["plan", `goTab("plan")`], ["taste", `goTab("taste")`], ["add", `goTab("add")`], ["queue", `goTab("games"); goSub("games", "queue")`],
    ["lib", `goTab("games"); goSub("games", "lib")`], ["played", `goTab("games"); goSub("games", "played")`]]) {
    await p.step(name, `closeFull(); ${go}`, 1300);
    const r = await pg.evaluate(() => { const h = document.querySelector(".panel.on .tabbar.shx"); if (!h || !h.getClientRects().length) return null;
      return { num: /\d/.test(h.innerText), chart: [...h.querySelectorAll(".shx-ch, svg, canvas")].some(c => c.getClientRects().length && getComputedStyle(c).visibility !== "hidden") }; });
    if (r && !r.num && !r.chart) bare.push(name);
  }
  ok("T4 header cards carry live content", !bare.length, `the header card on ${bare.join(", ")} has no number or chart, only a title`);

  /* T9 + T10: a coming, an out, a playing and a played game */
  const pages = [];
  for (const pick of [`S.games.find(g => g.status === "upcoming")`, `S.games.find(g => g.status === "out" && g.state === "backlog" && !isPlayed(g))`,
    `S.games.find(g => g.state === "playing")`, `S.games.find(g => isPlayed(g))`]) {
    await p.step("game", `closeFull(); { const g = ${pick}; g && openFull(g.id); }`, 1600);
    pages.push(await pg.evaluate(() => ({ body: [...document.querySelectorAll("#full.on .gd-body > *")].map(e => (e.className || e.tagName).split(" ")[0]).join(" "),
      hot: [...document.querySelectorAll("#full.on .hot")].filter(b => b.getClientRects().length && getComputedStyle(b).visibility !== "hidden").map(b => b.textContent.trim().replace(/[\d.]+/g, "#")) })));
  }
  await p.step("close", `closeFull()`, 600);
  const shapes = [...new Set(pages.map(x => x.body))];
  ok("T9 one game page skeleton for every state", shapes.length === 1 && shapes[0], `the page is built differently by state: ${shapes.map(s => `[${s}]`).join(" vs ")}`);
  const hots = pages.map(x => x.hot.length);
  ok("T10 one main action, by state", hots.every(n => n === 1) && new Set(pages.map(x => x.hot[0])).size > 1,
    `main actions per state (coming, out, playing, played): ${pages.map(x => x.hot.length + " " + JSON.stringify(x.hot)).join(" · ")}`);

  /* T12: the Similar sheet, with the helper's "Steam players also like" (tools/fake-helper.mjs) */
  await p.step("similar", `closeFull(); { const g = S.games.find(x => stIdOf(x) > 0) || S.games[0]; openFull(g.id); }`, 1400);
  await p.step("similar sheet", `{ const b = [...document.querySelectorAll("#full.on button")].find(b => /Similar/.test(b.textContent)); b && b.click(); }`, 3000);
  const sim = await pg.evaluate(async () => {
    const box = document.querySelector("#diary.on .dy-in"); if (!box) return null;
    for (const i of box.querySelectorAll("img")) i.loading = "eager";
    await new Promise(r => setTimeout(r, 800));
    const cards = [...box.querySelectorAll(".sgc, .sg-lib > button")].filter(c => c.getClientRects().length);
    return cards.map(c => { const im = c.querySelector("img"), r = im ? im.getBoundingClientRect() : null;
      return { pic: r ? Math.round(Math.min(r.width, r.height * 1.6)) : 0, shown: !!(im && im.complete && im.naturalWidth && getComputedStyle(im).visibility !== "hidden"),
        words: (c.querySelector(".sgc-w, .sgc-m, small") || {}).textContent || "" }; });
  });
  await p.step("close sheet", `typeof dyClose === "function" && dyClose(); closeFull()`, 700);
  const small = (sim || []).filter(c => c.pic < 72 || !c.shown), dumb = (sim || []).filter(c => c.words.trim().length < 8);
  ok("T12 pick lists: pictures big enough, a line about each", sim && sim.length >= 3 && !small.length && !dumb.length,
    !sim ? "the Similar sheet didn't open" : `${sim.length} cards; ${small.length} with a picture under 72px or not showing (${small.map(c => c.pic + "px").join(", ")}), ${dumb.length} with no line about the game`);

  /* T32 */
  const emo = /\p{Extended_Pictographic}/u;
  const icons = async () => pg.evaluate(src => { const emo = new RegExp(src, "u"); const sum = [...document.querySelectorAll("#sheet.on #sheet-in details > summary")].filter(s => s.getClientRects().length);
    return { n: sum.length, noIcon: sum.filter(s => !s.querySelector(".gi svg")).map(s => s.textContent.trim().slice(0, 24)),
      emoji: sum.filter(s => emo.test(s.textContent)).map(s => s.textContent.trim().slice(0, 24)).concat([...document.querySelectorAll("#sheet.on .srlnav")].filter(n => emo.test(n.textContent)).map(() => "On this page rail")) }; }, emo.source);
  await p.step("settings", `closeFull(); openSettings()`, 1500); const st = await icons();
  await p.step("close", `closeEdit()`, 600);
  await p.step("edit", `openEdit(S.games[2].id)`, 1500); const ed = await icons();
  await p.step("close", `closeEdit()`, 600);
  const noIcon = st.noIcon.concat(ed.noIcon), withEmoji = st.emoji.concat(ed.emoji);
  ok("T32 group icons are drawn, no emoji", st.n > 3 && ed.n > 0 && !noIcon.length && !withEmoji.length,
    `${st.n} Settings and ${ed.n} Edit groups; without a drawn icon: ${noIcon.join(", ") || "none"}; with an emoji: ${withEmoji.join(", ") || "none"}`);

  /* T33 + T35: the open Queue card, for two games with different colours */
  const cards = [];
  for (const k of [0, 1]) {
    await p.step("card", `closeFull(); goTab("games"); goSub("games", "queue"); scrollTo(0, 0); { const h = document.querySelector("#p-queue .hero"); if (h) h.querySelector(".pfront").click(); }`, 900);
    await p.step("card " + k, `{ const g = S.games.filter(x => !isPlayed(x) && x.colour)[${k}], r = g && document.querySelector('#p-queue [data-row="' + CSS.escape(g.id) + '"]'); r && !r.classList.contains("hero") && r.click(); }`, 1800);
    cards.push(await pg.evaluate(() => {
      const f = document.querySelector("#p-queue .hero .pfront"); if (!f) return null;
      const br = e => e ? getComputedStyle(e).borderRadius.split(" ").map(parseFloat) : [0], fr = f.getBoundingClientRect(), cs = getComputedStyle(f);
      const bg = f.querySelector(".qkbg"), bgr = bg && bg.getBoundingClientRect(), lg = f.querySelector(".qkt"), lr = lg && lg.getBoundingClientRect();
      return { card: Math.min(...br(f)), clip: cs.overflow !== "visible", cover: Math.min(...br(f.querySelector(".qkcov img") || f.querySelector(".qkcov"))),
        strap: Math.max(...br(f.querySelector(".qkstrap"))), stub: Math.max(...br(f.querySelector(".qkstub"))), body: Math.max(...br(f.querySelector(".qkbody"))),
        fills: !!bgr && bgr.left <= fr.left + 2 && bgr.top <= fr.top + 2 && bgr.right >= fr.right - 2 && bgr.bottom >= fr.bottom - 2,
        strapBg: getComputedStyle(f.querySelector(".qkstrap")).backgroundImage, logo: lr ? [Math.round(fr.right - lr.right), Math.round(fr.bottom - lr.bottom)] : null,
        colour: (S.games.find(g => g.id === f.closest("[data-row]").dataset.row) || {}).colour };
    }));
  }
  await p.step("close card", `{ const h = document.querySelector("#p-queue .hero"); if (h) h.querySelector(".pfront").click(); }`, 600);
  const [a, b] = cards;
  ok("T33 the open card is rounded everywhere and its picture fills it", a && a.card >= 12 && a.clip && a.cover >= 6 && a.strap > 0 && a.stub > 0 && a.body > 0 && a.fills,
    a ? `card ${a.card}px${a.clip ? "" : " (not clipped)"}, box art ${a.cover}px, strap ${a.strap}px, ticket ${a.stub}/${a.body}px, picture ${a.fills ? "fills" : "doesn't fill"} the card` : "no open card");
  ok("T35 strap in the game's colour, logo in one fixed slot", a && b && a.colour !== b.colour && a.strapBg !== b.strapBg && a.logo && b.logo && Math.abs(a.logo[0] - b.logo[0]) <= 3 && Math.abs(a.logo[1] - b.logo[1]) <= 3,
    !a || !b ? "couldn't open two cards" : `colours ${a.colour} / ${b.colour}: straps ${a.strapBg === b.strapBg ? "the same" : "differ"}; logo slot from the bottom-right corner ${JSON.stringify(a.logo)} vs ${JSON.stringify(b.logo)}`);
  if (planted && missed.length) fail(`${w}px: the plant got past ${missed.join(", ")} (a blind check)`);
  return true;
};
