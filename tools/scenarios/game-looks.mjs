/* How a game looks (v317, the owner: "I love games with good graphics … I dont like the graphic of octopath but like persona
   and modern HD stuff").
     node tools/kit.mjs run tools/scenarios/game-looks.mjs --widths 412,859      SHOTS=dir
   Checks:
     - looksFromTags: Realistic / 3D → Modern HD, Pixel Graphics + 2.5D stays out of Modern HD, a pick in Edit (g.look) wins
     - lookLean learns from scores: HD games scored high and pixel games scored low → Modern HD leans up, Pixel art down,
       On deck ranks an HD game above an otherwise equal pixel one, and What I've learned says so
     - the game page shows a Looks row with "Your kind of look"; a tap on a look chip filters the Queue to it
     - Edit › How it looks picks a look and Save keeps it */
import fs from "node:fs";

export default async ({ p, w, fail }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const pg = p.page;
  const shot = async n => { if (shots) await pg.screenshot({ path: `${shots}/${n}-${w}.png` }); };
  await pg.evaluate(() => { const m = document.getElementById("pnbubmsg"); if (m) m.style.display = "none"; });
  const r = await pg.evaluate(() => {
    const o = {};
    o.hd = looksFromTags(["RPG", "Realistic", "3D"]).join("/");
    o.px = looksFromTags(["Pixel Graphics", "2.5D", "3D"]).join("/");
    o.manual = lookOf({ look: "Pixel art", stTags: ["Realistic"] }).join("/");
    o.none = lookOf({ look: "", stTags: ["Realistic"] }).length;
    /* a made-up taste: the HD games scored 9+, the pixel ones 4 */
    S.games.forEach((g, i) => { delete g.look; g.stTags = i % 2 ? ["RPG", "Pixel Graphics", "2D"] : ["RPG", "Realistic", "3D"]; });
    S.played.forEach(p => { const g = S.games.find(x => x.id === p.id); if (g) p.real = /Pixel/.test(g.stTags.join()) ? 4 : 9.5; });
    const ln = lookLean(); o.hdLean = ln["Modern HD"] && ln["Modern HD"].lean; o.pxLean = ln["Pixel art"] && ln["Pixel art"].lean;
    /* two otherwise equal queued games */
    const live = S.games.filter(g => !isPlayed(g) && !isDropped(g) && !g.comp), a = live[0], b = live[1];
    if (a && b) { Object.assign(b, { genres: a.genres, platforms: a.platforms, hype: a.hype, state: a.state, date: a.date, status: a.status });
      a.stTags = ["RPG", "Realistic", "3D"]; b.stTags = ["RPG", "Pixel Graphics", "2D"];
      const s = onDeckScores(); o.deck = s[a.id] > s[b.id]; o.hdId = a.id; }
    o.ins = tasteInsights().map(x => x.txt).join(" | ");
    saveNow(); return o;
  });
  if (r.hd !== "Modern HD/Realistic") fail("Realistic + 3D should read Modern HD/Realistic, got " + r.hd);
  if (/Modern HD/.test(r.px) || !/Pixel art/.test(r.px) || !/2\.5D/.test(r.px)) fail("Pixel Graphics + 2.5D read " + r.px);
  if (r.manual !== "Pixel art" || r.none !== 0) fail("a pick in Edit should win: " + r.manual + " / " + r.none);
  if (!(r.hdLean > .2) || !(r.pxLean < -.1)) fail(`leans: Modern HD ${r.hdLean}, Pixel art ${r.pxLean}`);
  if (r.deck !== true) fail("On deck should rank the HD game above the equal pixel one");
  if (!/Modern HD/.test(r.ins) || !/Pixel art<\/b> isn't your look/.test(r.ins)) fail("What I've learned: " + r.ins);

  /* the game page */
  await pg.evaluate(id => { S.qFilter = "all"; saveNow(); openFull(id); }, r.hdId);
  await pg.waitForTimeout(1200);
  const gp = await pg.evaluate(() => {
    const c = document.querySelector("#full .gd-tags .gtag.lk"), f = document.querySelector("#full .gd-lkfit");
    if (!c) return null; c.scrollIntoView({ block: "center", behavior: "instant" });
    const b = c.getBoundingClientRect(); return { t: c.dataset.gtag, fit: f && f.textContent, x: b.left + b.width / 2, y: b.top + b.height / 2, over: b.right > innerWidth + 1 };
  });
  if (!gp) return fail("no Looks chip on the game page");
  if (gp.fit !== "Your kind of look") fail("game page fit line: " + gp.fit);
  if (gp.over) fail("a Looks chip runs off the screen");
  await pg.waitForTimeout(400); await shot("page");
  await p.tap(gp.x, gp.y); await pg.waitForTimeout(1200);
  const q = await pg.evaluate(t => ({ f: S.qFilter, n: queueList().length, ok: queueList().every(g => lookOf(g).includes(t)) }), gp.t);
  if (q.f !== "G:" + gp.t || !q.n || !q.ok) fail("a tap on " + gp.t + " should filter the Queue to it: " + JSON.stringify(q));
  await shot("queue");

  /* Edit › How it looks */
  const ed = await pg.evaluate(async id => {
    S.qFilter = "all"; saveNow(); openEdit(id); await new Promise(r => setTimeout(r, 600));
    const box = document.getElementById("e-lookpick"); if (!box) return "no picker";
    const b = box.querySelector('[data-p="Anime"]'); b.click();
    document.getElementById("e-save").click(); await new Promise(r => setTimeout(r, 400));
    return S.games.find(x => x.id === id).look;
  }, r.hdId);
  if (ed !== "Modern HD, Realistic, Anime") fail("Edit › How it looks saved " + JSON.stringify(ed));
};
