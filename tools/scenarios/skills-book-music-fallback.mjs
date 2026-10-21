/* v323 (the owner, after v322: "still not working"): the helper asks Apple from Cloudflare's shared addresses and Apple turns
   them away at times, so the phone asks Apple itself and the helper only ranks what it found. Here the helper's own search
   fails (503) and a "no music" kept by v322 for the game (no w mark) must not stop it: the phone's Apple search, ranked by
   the helper's real ostRank, plays the main theme. Then with Apple down too, Nexi files a "music" problem report.
     node tools/kit.mjs run tools/scenarios/skills-book-music-fallback.mjs --widths 412 */
import fs from "node:fs";
fs.mkdirSync("tools/.cache", { recursive: true }); fs.copyFileSync("worker/helper.js", "tools/.cache/helper-rank.mjs");
const { ostRank } = await import(new URL("../.cache/helper-rank.mjs", import.meta.url));
const T = "Made Up Quest";
const SONGS = ["Main Theme", "Harbour at Dusk", "The Long Road"].map((n, i) => ({ trackId: i + 1, collectionId: 9, trackName: n, collectionName: T + " (Original Soundtrack)",
  artistName: "Test Orchestra", previewUrl: "https://audio-ssl.itunes.apple.com/fake/" + i + ".m4a", trackNumber: i + 1, discNumber: 1, trackTimeMillis: 120000 }));
export default async ({ p, w, seed, fail }) => {
  let apple = "ok", rawSeen = 0;
  await p.page.route(/workers\.dev\/ost$/, async r => { let b = {}; try { b = JSON.parse(r.request().postData() || "{}"); } catch (e) {}
    const H = { "access-control-allow-origin": "*" };
    if (r.request().method() === "OPTIONS") return r.fulfill({ status: 204, headers: { ...H, "access-control-allow-headers": "content-type" } });
    if (Array.isArray(b.r)) { rawSeen++; return r.fulfill({ status: 200, contentType: "application/json", headers: H, body: JSON.stringify({ ok: true, tracks: ostRank(b.t, b.r) }) }); }
    return r.fulfill({ status: 503, contentType: "application/json", headers: H, body: JSON.stringify({ error: "Apple's search is busy, try again soon" }) }); });
  await p.page.route(/itunes\.apple\.com\/search/, r => apple === "ok"
    ? r.fulfill({ status: 200, contentType: "text/javascript", headers: { "access-control-allow-origin": "*" }, body: JSON.stringify({ resultCount: SONGS.length, results: SONGS }) })
    : r.fulfill({ status: 403, body: "no" }));
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99, sound: false })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  const setup = () => p.page.evaluate(T => { const last = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).pop(), g = S.games.find(x => x.id === last.id);
    g.title = T; saveNow();
    localStorage.setItem("gq:book", JSON.stringify({ [bookNorm(T)]: { skills: [{ ic: "time", n: "Patience", lv: 2, txt: "A made-up moment." }] } }));
    /* what v321/v322 kept after a refusal: "no music", just now */
    localStorage.setItem("gq:ost", JSON.stringify({ [g.id]: { l: [], i: 0, at: Date.now() } }));
    bookStore = null; document.documentElement.classList.add("still"); }, T);
  await setup();
  const box = await p.page.evaluate(() => ({ w: innerWidth }));
  await p.tap(box.w / 2, 40);
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 1500);
  const st = () => p.page.evaluate(() => ({ srcs: SKB_MUS.srcs.length, tr: SKB_MUS.tr && SKB_MUS.tr.n, ost: localStorage.getItem("gq:ost") }));
  let s = null; for (let i = 0; i < 20 && !(s = await st()).srcs; i++) await p.page.waitForTimeout(400);
  if (!s.srcs) fail(`helper busy + an old "no music": nothing played (${JSON.stringify(s).slice(0, 300)})`);
  else if (s.tr !== "Main Theme") fail(`played "${s.tr}", expected the main theme`);
  if (!rawSeen) fail("the phone never handed Apple's songs to the helper");
  if (!/"w":1/.test(s.ost || "")) fail("the found list wasn't kept with the v323 mark");
  /* Apple down too: no music, and Nexi says why */
  apple = "down";
  await p.page.evaluate(() => { localStorage.removeItem("gq:ost"); SKB_MUS.g = null; skbMusStop(0); goTab("home"); }); await p.page.waitForTimeout(300);
  await p.step("book again", `goTab("taste"); goSub("taste", "book"); skbMusSync()`, 3000);
  await p.flush();
  const rep = p.reports.filter(r => r.kind === "music");
  if (!rep.length) fail(`with the helper and Apple both failing, no "music" problem report (reports: ${p.reports.map(r => r.kind + ":" + r.msg).join(" | ").slice(0, 300)})`);
  else console.log(`  ${w}: report: ${rep[0].msg}`);
  console.log(`  ${w}: fallback played "${s.tr}" (${rawSeen} ranking call${rawSeen === 1 ? "" : "s"})`);
};
