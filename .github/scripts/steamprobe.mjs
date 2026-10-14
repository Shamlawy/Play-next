/* Steam sessions probe (helper v20). This container can't reach Steam, so GitHub runs it.
   Part 1: Steam's keyless public profile data for a few well-known public profiles (or WHO, | separated: profile links,
   custom names or 64-bit ids): the profile XML (?xml=1: name, privacy, in-game, "most played" recent games with hours),
   the old games XML and the mini profile, to see which still answer and what they hold.
   Part 2: the helper's own /steam/me + /steam/sync + cron code against real Steam (fake KV, real fetch).
   Part 3: the live helper's /version and /steam/me, to see that the deploy picked it up. */
import { copyFileSync } from "node:fs";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36";
const WHO = String(process.env.WHO || "gabelogannewell|robinwalker|76561197960287930|76561197960265729|thaliix|shroud|summit1g|xpaw").split("|").map(s => s.trim()).filter(Boolean);
const base = w => /^\d{17}$/.test(w) ? "https://steamcommunity.com/profiles/" + w : /^https?:/.test(w) ? w.replace(/\/+$/, "") : "https://steamcommunity.com/id/" + w;
const cut = (s, n) => String(s).replace(/\s+/g, " ").slice(0, n);
console.log("== 1. keyless profile data");
let good = null;
for (const w of WHO) {
  const b = base(w);
  try {
    const r = await fetch(b + "/?xml=1", { headers: { "User-Agent": UA } }); const t = await r.text();
    const tag = n => { const m = t.match(new RegExp(`<${n}>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</${n}>`)); return m ? m[1].trim() : ""; };
    const games = [...t.matchAll(/<mostPlayedGame>([\s\S]*?)<\/mostPlayedGame>/g)].map(m => m[1]);
    console.log(` ${w}: ${r.status} ${r.headers.get("content-type")} id=${tag("steamID64")} privacy=${tag("privacyState")} vis=${tag("visibilityState")} online=${tag("onlineState")} state="${cut(tag("stateMessage"), 60)}" ingame="${cut(tag("inGameInfo"), 120)}" mostPlayed=${games.length}`);
    if (games[0]) console.log("   first mostPlayedGame:", cut(games[0], 400));
    if (!t.includes("<profile>")) console.log("   body:", cut(t, 200));
    if (games.length && !good) good = { w, id: tag("steamID64") };
  } catch (e) { console.log(` ${w}: profile xml error ${e.message}`); }
}
if (good) {
  const b = base(good.w);
  for (const u of [b + "/games/?tab=all&xml=1", b + "/games/?tab=recent", "https://steamcommunity.com/miniprofile/" + (BigInt(good.id) - 76561197960265728n) + "/json"]) {
    try { const r = await fetch(u, { headers: { "User-Agent": UA } }); const t = await r.text();
      console.log(` ${u.replace(/\d{17}/, "<id>")}: ${r.status} ${r.headers.get("content-type")} ${t.length} chars`);
      const m = t.match(/data-profile-gameslist="([^"]{0,600})/); if (m) console.log("   gameslist attr:", cut(m[1].replace(/&quot;/g, '"'), 500));
      else console.log("   start:", cut(t.replace(/<[^>]+>/g, " "), 300));
      const pt = t.match(/"playtime_forever":\d+/g); if (pt) console.log("   playtime_forever entries:", pt.length);
    } catch (e) { console.log(" ", u, "error", e.message); }
  }
}
console.log("== 2. helper code");
copyFileSync("worker/helper.js", "/tmp/helper.mjs");
const mod = (await import("/tmp/helper.mjs")).default;
const kv = new Map();
const env = { NUDGE: { get: async (k, t) => { const v = kv.get(k); return v == null ? null : t === "json" ? JSON.parse(v) : v; }, put: async (k, v) => kv.set(k, v), delete: async k => kv.delete(k),
  list: async ({ prefix }) => ({ keys: [...kv.keys()].filter(k => k.startsWith(prefix)).map(name => ({ name })), list_complete: true }) } };
const O = "https://shamlawy.github.io", LIVE = "https://playnext-helper.hussamnabil48.workers.dev";
const req = (b, path, body) => new Request(b + path, { method: "POST", headers: { "Content-Type": "application/json", Origin: O }, body: JSON.stringify(body) });
const pid = "probe" + Math.random().toString(36).slice(2, 14).padEnd(14, "x");
for (const w of good ? [good.w, "this-name-does-not-exist-zz9"] : WHO.slice(0, 2)) {
  try {
    const r = await mod.fetch(req("https://h", "/steam/me", { id: pid, who: w, apps: [{ k: "g1", t: "Dota 2", a: 570 }, { k: "g2", t: "Counter-Strike 2" }] }), env);
    console.log(" /steam/me", w, r.status, cut(await r.text(), 600));
  } catch (e) { console.log(" /steam/me error", e.message); }
}
try { const r = await mod.fetch(req("https://h", "/steam/sync", { id: pid, apps: [{ k: "g1", t: "Dota 2", a: 570 }] }), env); console.log(" /steam/sync", r.status, cut(await r.text(), 400)); } catch (e) { console.log(" /steam/sync error", e.message); }
try { await mod.scheduled({}, env, { waitUntil: p => p }); console.log(" cron ok; record:", cut(kv.get("sp:" + pid) || "none", 400)); } catch (e) { console.log(" cron error", e.message); }
console.log("== 3. live helper");
try {
  const v = await (await fetch(LIVE + "/version")).json(); console.log(" version", v.v, "steamMe:", v.steamMe);
  if (v.steamMe && good) { const r = await fetch(req(LIVE, "/steam/me", { id: pid, who: good.w, apps: [] })); console.log(" live /steam/me", r.status, cut(await r.text(), 300));
    await fetch(req(LIVE, "/steam/me", { id: pid, off: 1 })); }
} catch (e) { console.log(" live helper:", e.message); }
