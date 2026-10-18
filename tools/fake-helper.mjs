/* A stand-in for Nexi's helper (worker/helper.js) in the test phones (open idea M3). Before this every outside call was
   blocked, so the sweep, the design eye and the screenshots only ever saw the empty states of everything the helper fills
   in: Steam reviews, hours to beat, news, Steam tags and "more like this", prices, the story so far, store pictures, chat.
   Real phones show those cards full, so their look bugs were never tested.

   Every answer has the shape the real route sends (read off worker/helper.js) and is made up (public repo: no real games,
   prices or stores). The same numbers are stored on the seed's games (tools/seed.mjs, filled()), so a sync that runs in
   the middle of a sweep changes nothing on screen. kit.mjs's phone() answers with fakeHelper() for the helper's address;
   FAKE_HELPER=0 blocks the helper again (the old empty states). An unknown route answers 404 and is printed once, so a
   new helper route shows up here instead of silently testing an empty card. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
export const HELPER_HOST = "playnext-helper.hussamnabil48.workers.dev";
const HELPER_V = +((fs.readFileSync(path.join(ROOT, "worker/helper.js"), "utf8").match(/const HELPER_V = (\d+)/) || [])[1] || 20);

/* one made-up game's answers, from its id ("g7" → 7): the same every time */
const num = k => { const m = /(\d+)$/.exec(String(k)); return m ? +m[1] : String(k).length; };
export const steamId = k => 400000 + num(k) * 10;
const TAGS = ["Open World", "Story Rich", "Atmospheric", "RPG", "Exploration", "Turn-Based Tactics", "Singleplayer", "Great Soundtrack", "Fantasy", "Action", "Roguelite", "Cozy", "Strategy", "Adventure", "Difficult"];
export const tagsFor = k => Array.from({ length: 7 + (num(k) % 6) }, (_, i) => TAGS[(num(k) * 3 + i * 4) % TAGS.length]).filter((t, i, a) => a.indexOf(t) === i);
const RV_WORDS = ["Overwhelmingly Positive", "Very Positive", "Mostly Positive", "Mixed", "Very Positive", "Positive"];
export const reviewFor = k => { const i = num(k); return { st: steamId(k), d: RV_WORDS[i % RV_WORDS.length], p: [96, 88, 74, 58, 91, 82][i % 6], n: 240 + i * 977 }; };
export const hoursFor = k => { const i = num(k), m = 9 + (i % 9) * 4; return { id: 90000 + i, n: "", m, x: m + 6 + (i % 4) * 3, c: m * 2 + 11, cnt: 120 + i * 33, sure: 1 }; };
export const criticFor = k => 64 + (num(k) * 7) % 31;
/* news: dated from the start of today (seconds), so the seed and the helper agree within a day */
const DAY = 86400, today = () => Math.floor(Date.now() / 864e5) * DAY;
const NEWS = [["patch", "Patch 1.0.4 is out: smoother frame pacing and new photo mode filters", "Fixes a stutter in the harbour district and adds three filters."],
  ["dlc", "The Frozen Coast expansion arrives next month", "A new region, two companions and a harder ending."],
  ["demo", "Try the first chapter free this weekend", "The demo carries your save over to the full game."]];
export const newsFor = k => { const i = num(k); return NEWS.map(([kind, t, x], j) => ({ id: "n" + i + "-" + j, t, u: "https://store.steampowered.com/news/app/" + steamId(k) + "/view/" + (5e17 + i * 100 + j),
  d: today() - (j * 3 + (i % 2)) * DAY - 3600, k: kind, x })); };
const price = (k, cur, minor) => { const i = num(k), base = [5999, 6999, 3999, 2999, 4999][i % 5], pct = [0, 50, 25, 0, 75][i % 5], now = Math.round(base * (100 - pct) / 100);
  const f = v => (cur === "USD" ? "$" : "") + (v / 100).toFixed(2) + (cur === "USD" ? "" : " " + cur);
  return minor ? { cur, base, now, pct, baseF: f(base), nowF: f(now) } : { cur, base: base / 100, now: now / 100, pct, baseF: f(base), nowF: f(now), end: 0, free: 0, pre: 0 }; };
const RATES = { USD: 1, EUR: .92, GBP: .79, EGP: 48.6, AED: 3.67, SAR: 3.75, JPY: 151, CAD: 1.37, AUD: 1.52 };
/* pictures the store route "finds": files already in the repo, served by the kit's own server */
const PICS = [["cover", "fefw-key-art.jpg", 3445, 3471], ["hero", "FEFT_cast.webp", 1000, 563], ["art", "logo-512.png", 512, 512], ["shot", "FEFT_cast.webp", 1000, 563]];

const unknown = new Set();
/* (path, parsed body or null, the kit's server address) → { status, json } */
export function fakeHelper(p, b, base) {
  b = b || {};
  const items = Array.isArray(b.items) ? b.items : [], ok = json => ({ status: 200, json });
  const res = f => Object.fromEntries(items.filter(x => x && x.k).map(x => [x.k, f(x)]));
  switch (p) {
    case "/version": return ok({ v: HELPER_V, chat: true, similar: true, nudge: true, updates: true, bugs: true, review: true, vault: true, prices: true, recap: true, eshop: true, steam: true, reviews: true, hours: true, fx: true, art: true, news: true, cal: true, steamMe: true });
    case "/chat": return ok({ text: "From what you've scored, the short story game at the top of your queue fits tonight: it's out, it's under 15 hours, and it shares a genre with your favourite. After that, the tactics one coming next month." });
    case "/chat/memory": return ok({ memory: String(b.memory || "").slice(0, 300) || "Likes RPGs and tactics, plays in the evening, finishes short games." });
    case "/why": return ok({ text: "VERDICT: Good fit\n- You scored the other story-led RPGs on your list 8 and up\n- It is turn-based like your top tactics game\n- The slow first hours might drag, like they did in your 6/10" });
    case "/similar": return ok({ games: [] });   /* the app checks every name on RAWG, which the test phones can't reach */
    case "/recap": return ok({ recap: "You reached the harbour town after the storm. The captain owes you a favour, the old map points north, and the rival crew is a day behind you. Your last note says the lighthouse keeper knows more than she lets on.", where: "the harbour town", sure: .8 });
    case "/review": return ok({ issues: [] });   /* the AI design review: the test phone's AI finds nothing (its own checks still run) */
    case "/reviews": return ok({ ok: true, res: res(x => reviewFor(x.k)) });
    case "/hours": return ok({ ok: true, src: "hltb", res: res(x => hoursFor(x.k)) });
    case "/news": return ok({ ok: true, res: res(x => ({ n: newsFor(x.k) })) });
    case "/steam": {
      const out = { ok: true, res: res(x => ({ st: +x.st > 0 ? +x.st : steamId(x.k), name: "", tags: tagsFor(x.k) })) };
      if (+b.more > 0) out.more = { of: { name: "", tags: tagsFor("g" + (+b.more % 97)) }, games: Array.from({ length: 6 }, (_, i) => ({ id: 500000 + i, name: ["Harbour Lights", "Tactics of the Tide", "The Quiet Archive", "Lantern Road", "Glasswing", "Northbound"][i],
        tags: tagsFor("g" + (i + 3)).slice(0, 8), desc: "A made-up game for the test phones: turn-based fights, a long story and a map you fill in yourself." })) };
      return ok(out);
    }
    case "/prices": {
      if (b.off) return ok({ ok: true });
      const cc = String(b.cc || "us");
      return ok({ ok: true, cc, nscc: b.nscc || cc, pscc: b.pscc || cc, at: Date.now(), res: res(x => ({ st: { id: +x.st > 0 ? +x.st : steamId(x.k), name: "", ...price(x.k, "USD", 1) },
        ps: x.ps === "-" ? undefined : { id: "UP0000-PPSA0" + num(x.k) + "_00-TESTGAME0000000", name: "", pcc: cc, ...price(x.k, "USD", 1) },
        ns: x.ns === "-" ? undefined : { id: String(70010000000000 + num(x.k)), name: "", url: "", ncc: cc, ...price(x.k, "USD", 0) } })),
        ...(b.fx ? { fx: { ok: true, base: "USD", r: RATES, t: Date.now(), at: Date.now() } } : {}) });
    }
    case "/fx": return ok({ ok: true, base: "USD", r: RATES, t: Date.now(), at: Date.now() });
    case "/art": return ok({ ok: true, found: { st: { id: steamId(b.st || 7), name: "" }, xb: { err: "not found" }, ps: { err: "not found" }, ns: { err: "not found" } },
      pics: PICS.map(([r, f, w, h]) => ({ s: "st", r, u: base + f, w, h })) });
    case "/nudge": case "/nudge/test": case "/usage": return ok({ ok: true });
    case "/cal/put": return ok({ ok: true, n: 3 });
    case "/steam/me": case "/steam/sync": return ok({ ok: true, games: [], sessions: [] });
  }
  if (p.startsWith("/sgdb/")) return ok({ success: true, data: [] });
  /* the backup vault and push keys stay off in the test phones (the seed has cbOn: false) */
  if (p.startsWith("/vault/") || p === "/nudge/key") return { status: 503, json: { error: "not in the test phones" } };
  if (!unknown.has(p)) { unknown.add(p); console.log(`  · fake helper: no answer for ${p} (add it to tools/fake-helper.mjs)`); }
  return { status: 404, json: { error: "fake helper: no route " + p } };
}
