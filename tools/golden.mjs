/* The golden file (docs/ANDROID-PLAN.md gate 5, critic G11): the web app's own answers, which the Android app's unit
   tests must match exactly. Runs index.html in Chromium with UAE time and a fixed clock (noon, 11:30 PM and 12:30 AM,
   where JavaScript's UTC reading of plain dates bites), restores a made-up backup through readBackup/applyBackup, lets
   load() do its start-up changes, and writes what came out, plus restore codes sealed by the web app's own cbSeal.
   node tools/golden.mjs [--out android/app/build/golden/golden.json]   (generated, never committed; android-check runs it)
   Phase 0 Piece 2 covers the restore, the load-time changes and the date rules; Piece 3 the header cards (shxData, shxExtra,
   so the ranking, Elo, On deck and hours to beat behind them); later phases add their numbers here. */
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { createRequire } from "node:module";
import { seed } from "./seed.mjs";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const arg = n => { const i = process.argv.indexOf("--" + n); return i > 0 ? process.argv[i + 1] : null; };
const OUT = path.resolve(arg("out") || path.join(ROOT, "android/app/build/golden/golden.json"));
const PAGE = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
const APP_V = (/const APP_V = "(\d+)"/.exec(PAGE) || [])[1];
const ZONE = "Asia/Dubai";

function pw() {
  for (const p of [process.env.PW_PATH, "playwright", "/opt/node22/lib/node_modules/playwright"]) {
    if (!p) continue;
    try { return createRequire(import.meta.url)(p); } catch (e) {}
  }
  throw new Error("Playwright not found (npm i --no-save playwright)");
}

/* Made-up games around the date edges (relative to Oct 27 2026, the clocks below), in a v327 backup's shape. */
const D = "2026-10-";
const FIXTURE = {
  games: [
    { id: "a1", title: "Out today", state: "upcoming", date: D + "27", ord: 1, colour: "#4DA3FF", hype: 7, xUnknown: { keep: [1, "two"] } },
    { id: "a2", title: "Out tomorrow", state: "upcoming", date: D + "28", ord: 2, colour: "#FF5470" },
    { id: "a3", title: "Out yesterday", state: "upcoming", date: D + "26", ord: 3 },
    { id: "a4", title: "Out 29 days ago", state: "upcoming", date: "2026-09-28", ord: 4 },
    { id: "a5", title: "Out 31 days ago", state: "upcoming", date: "2026-09-26", ord: 5 },
    { id: "a6", title: "No date", state: "upcoming", date: "", status: "tba", ord: 6 },
    { id: "a7", title: "No state, out last week", date: D + "20", ord: 7 },
    { id: "a8", title: "Backlog, 13 days", state: "backlog", date: D + "14", ord: 8 },
    { id: "a9", title: "Backlog, 15 days", state: "backlog", date: D + "12", ord: 9 },
    { id: "b1", title: "Playing", state: "playing", date: "2026-03-01", ord: 10, plAt: 1761000000000 },
    { id: "b2", title: "Played twice", state: "backlog", date: "2025-01-10", ord: 11 },
    { id: "b3", title: "Dropped", state: "dropped", date: "2024-05-05", ord: 12, genres: ["RPG", "Action"] },
    { id: "b4", title: "Far away", state: "upcoming", date: "2027-03-15", ord: 13, status: "rumoured" },
    { id: "b5", title: "A date JS can't read", state: "upcoming", date: "soon", ord: 14 },
    { id: "", title: "No id" },
    { id: "c1", title: "" },
    { id: "c2", title: "Unicode — 日本語 العربية 🎮", state: "backlog", date: "2026-08-08", ord: 15 },
  ],
  played: [
    { id: "b2", real: 7.5, t: "2025-03-01", runs: 1 },
    { id: "b2", real: 9, t: "2025-02-01", again: true, runs: 2 },
    { real: 5, t: "2025-01-01" },
    { id: "b3", real: 0, z: 1, t: "2024-06-01" },
  ],
  duels: [{ a: "b2", b: "b3", w: "b2", t: "2025-04-01" }],
  lists: [{ id: "l1", n: "Made-up list", e: "" }],
  theme: "emerald", bgBase: "#0E1A3D", bgTint: "#2F6BFF", bgGrad: 45,
  syncTok: "made-up-sync-token", syncOn: true, rawgKey: "made-up-rawg", v: 11,
  lastV: APP_V, scoreMig: 1, gdMig: 1, rowGlowMig: 1, pxKindMig: 1, pxMig229: 1, genDedup: 1,
  tourFrom: +APP_V, tourRedo: 1, nxAsked: 1, fxSeen: 1, fxCal: 1, pxOff: true, cbOn: false, somethingNew: { x: 1 },
};
/* Made-up games for the header cards (Piece 3: shxData + shxExtra per screen): genres, platforms, hours, added days,
   pictures, scores, a replay, duels (a win, a slight edge, a draw), a dropped and a competitive game. */
const SHX_FIX = {
  games: [
    { id: "s1", title: "Neon Drift", state: "backlog", date: "2026-05-01", genres: "Racing, Action", platforms: "Switch, PC", hype: 8, hours: 12, hoursX: 20, added: "2026-10-25", img: "https://example.com/s1.jpg", colour: "#FF5470", ord: 1 },
    { id: "s2", title: "Moss Garden", state: "playing", date: "2026-01-15", genres: "Puzzle, Adventure", platforms: "PS5", hype: 6, hours: 30, added: "2026-09-10", steamId: 12345, colour: "#4DA3FF", ord: 2 },
    { id: "s3", title: "Star Harbor", state: "upcoming", date: "2026-11-02", genres: ["RPG", "Action"], platforms: "Switch", hype: 9, hours: 60, added: "2026-10-01", ord: 3 },
    { id: "s4", title: "Iron Choir", state: "upcoming", date: "2026-11-20", genres: "Action", platforms: "PC", added: "2026-08-15", hero: "https://example.com/s4-hero.jpg", ord: 4 },
    { id: "s5", title: "Paper Moons", state: "upcoming", date: "2027-02-01", genres: "RPG", added: "2026-07-04", ord: 5 },
    { id: "s6", title: "Old Lantern", state: "backlog", date: "2025-02-02", genres: "RPG, Adventure", added: "2026-03-03", hours: 40, art: "https://example.com/s6-art.jpg", ord: 6 },
    { id: "s7", title: "Glass Tide", state: "backlog", date: "2025-06-06", genres: "Action, RPG", added: "2025-12-12", ord: 7 },
    { id: "s8", title: "Quiet Fields", state: "backlog", date: "2026-04-04", genres: "Puzzle", added: "2026-10-27", bgOff: true, ord: 8 },
    { id: "s9", title: "Rust Belt", state: "dropped", date: "2024-01-01", genres: "Action", ord: 9 },
    { id: "s10", title: "Arena Ten", state: "backlog", comp: true, date: "2023-03-03", genres: "Shooter", hours: 99, ord: 10 },
    { id: "s11", title: "Fog Line", state: "upcoming", date: "", status: "tba", added: "2026-10-26", ord: 11 },
    { id: "s12", title: "Late Train", state: "upcoming", date: "2026-10-27", genres: "Adventure", ord: 12 },
    { id: "s13", title: "Bright Ash", state: "backlog", date: "2026-10-20", genres: "RPG", hype: 4, platforms: "Switch", ord: 13 },
  ],
  played: [
    { id: "s6", real: 9.5, t: "2026-06-10", runs: 1 },
    { id: "s7", real: 8, t: "2026-09-02", again: true, runs: 1 },
    { id: "s8", real: 6.5, t: "2026-10-05", runs: 1 },
    { id: "s6", real: 9, t: "2026-10-10", runs: 2 },
  ],
  duels: [{ a: "s6", b: "s7", w: "s6", t: "2026-10-11" }, { a: "s7", b: "s8", w: "s7", e: 1, t: "2026-09-20" }, { a: "s6", b: "s8", w: "draw", t: "2026-08-01" }],
  curPlat: "Switch", v: 11, lastV: APP_V, tourFrom: +APP_V, tourRedo: 1, nxAsked: 1, fxSeen: 1, fxCal: 1, pxOff: true, cbOn: false,
};
const CLOCKS = [["noon", "2026-10-27T08:00:00Z"], ["late", "2026-10-27T19:30:00Z"], ["early", "2026-10-27T20:30:00Z"]];
const READS = ["", "   ", "GAME QUEUE export", "[1,2]", "{\"games\":[{\"id\":\"x\"", "{\"games\":5}", "{\"games\":[{\"id\":\"x\"}]}",
  "{\"games\":[{\"id\":\"x\",\"title\":\"T\"}],\"rawgKey\":\"k\"}", "{\"games\":[{\"id\":\"x\",\"title\":\"T\"},{\"id\":\"y\"}]}", "{\"games\":[1}"];
const CODES = ["PN-AB2C-DEF3-GH4J-KLM5-NPQ6", "pn-zzzz 2345 6789 abcd efgh", "AAAAAAAAAAAAAAAAAAAA", "PN-0000-1111-2222-3333-4444"];

const srv = http.createServer((q, res) => {
  if (q.url.split("?")[0] === "/") { res.writeHead(200, { "content-type": "text/html" }); return res.end(PAGE); }
  const p = path.join(ROOT, decodeURIComponent(q.url.split("?")[0]));
  if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200); fs.createReadStream(p).pipe(res);
}).listen(0);
const base = `http://localhost:${srv.address().port}/`;
const { chromium } = pw();
const exe = ["/opt/pw-browsers/chromium/chrome-linux/chrome"].find(f => !process.env.CI && fs.existsSync(f));
const browser = await chromium.launch(exe ? { executablePath: exe } : {});
const errors = [];

async function phone(at) {
  const ctx = await browser.newContext({ timezoneId: ZONE, locale: "en-US", viewport: { width: 412, height: 900 }, serviceWorkers: "block" });
  await ctx.route(u => !u.href.startsWith(base) && !/^(blob|data):/.test(u.href), r => r.abort());
  await ctx.clock.setFixedTime(new Date(at));
  /* a phone that already has one game (so the web app makes no made-up ones) and has seen this version's tour */
  const start = { ...seed(APP_V, 1, { bare: true }), bugOff: true };
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("seeded", "1"); } }, JSON.stringify(start));
  const page = await ctx.newPage();
  page.on("pageerror", e => errors.push(String(e.message || e).split("\n")[0]));
  await page.goto(base);
  await page.waitForFunction(() => typeof readBackup === "function" && typeof S === "object" && S.games.length > 0, null, { timeout: 30000 });
  return { ctx, page };
}

const out = { appV: APP_V, zone: ZONE, codes: [], reads: [], cases: [] };
{
  const { ctx, page } = await phone(CLOCKS[0][1]);
  out.reads = await page.evaluate(rs => rs.map(raw => { const r = readBackup(raw); return { raw, ok: r.ok, msg: r.msg }; }), READS);
  out.codes = await page.evaluate(async codes => {
    const res = [];
    for (const code of codes) {
      const norm = cbNorm(code);
      if (!norm) { res.push({ code, norm: "", fmt: "" }); continue; }
      const k = await cbKeys(code);
      const raw = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode("pn-vault-key:" + norm)));
      const text = JSON.stringify({ rk: "made-up-" + norm.slice(0, 4) }) + "\n" + JSON.stringify({ games: [{ id: "g", title: "T" }] });
      res.push({ code, norm, fmt: cbFmt(code), id: k.id, tok: k.tok, key: cbHex(raw), text, sealed: cbHex(await cbSeal(text, k.key)) });
    }
    return res;
  }, CODES);
  await ctx.close();
}
for (const [name, at] of CLOCKS) {
  const { ctx, page } = await phone(at);
  const raw = JSON.stringify(FIXTURE);
  /* the restore, as cbRestoreSheet does it: readBackup, then applyBackup (it saves for the next start) */
  const applied = await page.evaluate(raw => {
    const rb = readBackup(raw); applyBackup(rb.data); saveNow();
    return { read: { ok: rb.ok, msg: rb.msg }, games: S.games.map(g => g.id), played: JSON.parse(JSON.stringify(S.played)), keys: Object.keys(S) };
  }, raw);
  /* the next start: load() makes its changes (played state, the 30-day flip) */
  await page.reload();
  await page.waitForFunction(() => typeof S === "object" && S.games.length > 0, null, { timeout: 30000 });
  await page.waitForTimeout(500);
  const loaded = await page.evaluate(() => ({
    states: Object.fromEntries(S.games.map(g => [g.id, g.state === undefined ? null : g.state])),
    played: JSON.parse(JSON.stringify(S.played)),
    todayLocal: new Date().toLocaleDateString("en-CA"), todayUtc: new Date().toISOString().slice(0, 10), now: Date.now(),
    per: Object.fromEntries(S.games.map(g => [g.id, { days: days(g.date), isUpcoming: isUpcoming(g), isFresh: !!isFresh(g) }])),
  }));
  out.cases.push({ name, at, now: Date.parse(at), raw, ...applied, loaded });
  await ctx.close();
}
/* the header cards: the web app's own numbers, line, genres, mix bar, chart and backdrop for each screen */
out.shx = [];
for (const [name, at] of CLOCKS) {
  const { ctx, page } = await phone(at);
  const raw = JSON.stringify(SHX_FIX);
  await page.evaluate(raw => { const rb = readBackup(raw); applyBackup(rb.data); saveNow(); }, raw);
  await page.reload();
  await page.waitForFunction(() => typeof shxData === "function" && typeof S === "object" && S.games.length > 0, null, { timeout: 30000 });
  await page.waitForTimeout(500);
  const kinds = await page.evaluate(() => Object.fromEntries(["queue", "lib", "played", "replay", "plan", "taste", "add"].map(k => {
    const d = shxData(k), cv = d.c.filter(Boolean).slice(0, 4), x = shxExtra(k, cv[0]);
    return [k, { c: cv.map(g => g.id), st: d.st.map(([v, l]) => [String(v), l]),
      line: x.line && { id: x.line.g.id, k: x.line.k, x: x.line.x }, tags: x.tags,
      bar: x.bar && x.bar.map(([n, l], i) => [n, l, i]).filter(b => b[0] > 0), chart: x.chart && { v: x.chart.v, cap: x.chart.cap },
      art: /^data:image\/svg/.test(x.art || "") ? "drawn" : x.art || "" }];
  })));
  out.shx.push({ name, at, now: Date.parse(at), raw, kinds });
  await ctx.close();
}
await browser.close();
srv.close();
if (errors.length) { console.error("golden: the page threw:\n  " + [...new Set(errors)].join("\n  ")); process.exit(1); }
for (const c of out.cases) if (c.loaded.now !== c.now) { console.error(`golden: the ${c.name} clock didn't hold (${c.loaded.now} vs ${c.now})`); process.exit(1); }
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(`golden: web v${APP_V}, ${out.codes.length} codes, ${out.reads.length} reads, ${out.cases.length} clocks, ${out.shx.length} header-card clocks → ${path.relative(ROOT, OUT)}`);
