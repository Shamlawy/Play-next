/* v324 (the owner: Nintendo games stayed quiet, "maybe other solutions like YouTube"): when Apple has no soundtrack the
   helper finds the game's music on YouTube (official Data API) and the book plays it in YouTube's own player under the
   book. A fake embed page here answers the iframe API messages like YouTube's: the first video can't be embedded (error
   150), so the book moves to the second, fades it in, names it under the player and hides the Apple chip; leaving the
   book pauses and removes the player; with every video failing Nexi files a "music" report. A list kept by v323 (w 1,
   a fan's cover album) must be asked again.
     node tools/kit.mjs run tools/scenarios/skills-book-music-yt.mjs --widths 412,859 */
import fs from "node:fs";
const T = "Made Up Quest";
/* a stand-in for youtube-nocookie.com/embed/<id>: keeps the commands it's sent, answers like the iframe API */
const EMBED = id => `<!doctype html><html><body style="margin:0;background:#000"><script>
  window.cmds = []; const bad = ${JSON.stringify(id)} === "aaaaaaaaaa1" || window.allBad;
  addEventListener("message", e => { let d; try { d = JSON.parse(e.data); } catch (x) { return; }
    if (d.event === "listening" && bad) parent.postMessage(JSON.stringify({ event: "onError", info: 150 }), "*");
    if (d.event === "command") { cmds.push(d.func + (d.args && d.args.length ? ":" + d.args[0] : ""));
      if (d.func === "playVideo" && !bad) parent.postMessage(JSON.stringify({ event: "onStateChange", info: 1 }), "*"); } });
</script></body></html>`;
export default async ({ p, w, seed, fail }) => {
  let allBad = false;
  const H = { "access-control-allow-origin": "*" };
  await p.page.route(/workers\.dev\/ost$/, r => r.request().method() === "OPTIONS" ? r.fulfill({ status: 204, headers: { ...H, "access-control-allow-headers": "content-type" } })
    : r.fulfill({ status: 200, contentType: "application/json", headers: H, body: JSON.stringify({ ok: true, tracks: [] }) }));
  await p.page.route(/itunes\.apple\.com\/search/, r => r.fulfill({ status: 200, contentType: "text/javascript", headers: H, body: JSON.stringify({ resultCount: 0, results: [] }) }));
  await p.page.route(/youtube-nocookie\.com\/embed\//, r => { const id = (r.request().url().match(/embed\/([\w-]{11})/) || [])[1];
    return r.fulfill({ status: 200, contentType: "text/html", body: EMBED(allBad ? "aaaaaaaaaa1" : id) }); });
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99, sound: false })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(T => { const last = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).pop(), g = S.games.find(x => x.id === last.id);
    g.title = T; saveNow();
    localStorage.setItem("gq:book", JSON.stringify({ [bookNorm(T)]: { skills: [{ ic: "time", n: "Patience", lv: 2, txt: "A made-up moment." }] } }));
    /* what v323 kept: a fan's cover album, a week ago */
    localStorage.setItem("gq:ost", JSON.stringify({ [g.id]: { l: [{ n: "Theme (From a game)", u: "https://audio-ssl.itunes.apple.com/fake/9.m4a" }], i: 0, at: Date.now() - 7 * 864e5, w: 1 } }));
    bookStore = null; document.documentElement.classList.add("still"); }, T);
  const box = await p.page.evaluate(() => ({ w: innerWidth }));
  await p.tap(box.w / 2, 40);
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 1500);
  const st = () => p.page.evaluate(() => { const m = SKB_MUS, c = $("#p-taste .skb-yt:not([hidden])"), f = c && c.querySelector("iframe"), r = f && f.getBoundingClientRect();
    const chip = [...document.querySelectorAll("#p-taste [data-ost]")].some(x => !x.hidden);
    return { y: m.yt && m.yt.y, ok: m.yt && m.yt.ok, v: m.yt && m.yt.v, card: !!c, fw: r ? Math.round(r.width) : 0, fh: r ? Math.round(r.height) : 0, name: c && c.querySelector(".skb-ytt b").textContent,
      chip, srcs: m.srcs.length, ost: localStorage.getItem("gq:ost") }; });
  let s = null; for (let i = 0; i < 25 && !((s = await st()).ok && s.v > 20); i++) await p.page.waitForTimeout(300);
  if (!s.card) fail(`no YouTube player under the book (${JSON.stringify(s).slice(0, 300)})`);
  else {
    if (s.y !== "aaaaaaaaaa2") fail(`the video that can't be embedded didn't move on to the next (playing ${s.y})`);
    if (!s.ok) fail("the player never said it was playing");
    if (!(s.v > 20)) fail(`the volume didn't fade in (at ${s.v})`);
    if (s.fw < 200) fail(`YouTube's player is ${s.fw}px wide: it must be at least 200`);
    if (s.chip) fail("the Apple track chip shows over a YouTube video");
    if (s.srcs) fail("an Apple clip plays alongside YouTube");
    if (!/"y":"aaaaaaaaaa2"|"y":"aaaaaaaaaa1"/.test(s.ost || "") || !/"w":2/.test(s.ost || "")) fail("the YouTube list wasn't kept with the v324 mark");
  }
  const fr = p.page.frames().find(f => /youtube-nocookie\.com\/embed\/aaaaaaaaaa2/.test(f.url()));
  const cmds = fr ? await fr.evaluate(() => cmds) : [];
  if (!cmds.includes("setVolume:0") || !cmds.includes("playVideo")) fail(`the player wasn't started silent (commands: ${cmds.slice(0, 8).join(", ")})`);
  /* the card sits under the book, on screen: nothing of it hidden under the tab bar's top or past the side */
  const geo = await p.page.evaluate(() => { const c = $("#p-taste .skb-yt"), r = c && c.getBoundingClientRect(); return r ? { l: r.left, r: r.right, vw: innerWidth } : null; });
  if (geo && (geo.l < 0 || geo.r > geo.vw + .5)) fail(`the player card runs off the screen (${Math.round(geo.l)}..${Math.round(geo.r)} of ${geo.vw})`);
  if (process.env.SHOTS) { fs.mkdirSync(process.env.SHOTS, { recursive: true });
    await p.page.evaluate(() => { const c = $("#p-taste .skb-yt"); if (c) c.scrollIntoView({ block: "center", behavior: "instant" }); }); await p.page.waitForTimeout(500);
    await p.page.screenshot({ path: `${process.env.SHOTS}/book-yt-${w}.png` });
    await p.page.evaluate(() => scrollTo({ top: 0, behavior: "instant" })); await p.page.waitForTimeout(600); }
  /* leaving the book with a real tap on the tab bar (it paints the tab without goTab, L143): paused and gone */
  const hb = await p.page.evaluate(() => { const b = [...document.querySelectorAll('[data-tab="home"]')].find(x => x.getClientRects().length && x.getBoundingClientRect().width);
    if (!b) return null; const r = b.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; });
  if (hb) await p.tap(hb.x, hb.y); else await p.page.evaluate(() => document.querySelector('nav button[data-tab="home"]').click());
  await p.page.waitForTimeout(1500);
  const after = await p.page.evaluate(() => ({ card: !!$("#p-taste .skb-yt"), yt: !!SKB_MUS.yt }));
  const cmds2 = fr && !fr.isDetached() ? await fr.evaluate(() => cmds).catch(() => []) : ["(removed)"];
  if (after.card || after.yt) fail("the YouTube player stayed after leaving the book");
  if (!cmds2.includes("pauseVideo") && cmds2[0] !== "(removed)") fail("the player wasn't paused when leaving");
  /* every video failing: Nexi says why */
  allBad = true;
  await p.page.evaluate(() => { SKB_MUS.g = null; SKB_MUS.ytBad = 0; });
  await p.step("book again", `goTab("taste"); goSub("taste", "book"); skbMusSync()`, 4000);
  await p.flush();
  const rep = p.reports.filter(r => r.kind === "music");
  if (!rep.length) fail(`with every YouTube video failing, no "music" problem report (reports: ${p.reports.map(r => r.kind + ":" + r.msg).join(" | ").slice(0, 300)})`);
  else console.log(`  ${w}: report: ${rep[0].msg}`);
  console.log(`  ${w}: YouTube played "${s && s.name}" (${s && s.y}), player ${s && s.fw}x${s && s.fh}, commands ${cmds.slice(0, 4).join(" ")}`);
};
