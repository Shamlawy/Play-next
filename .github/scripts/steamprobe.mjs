/* Steam sessions probe (helper v20). This container can't reach Steam, so GitHub runs it.
   What it found on Oct 14 2026: a public profile's ?xml=1 page still answers without a key (its 64-bit id, privacy, and
   "in-game" with the game's name and Steam link while you play), but the games list with hours (/games/?tab=all, its old
   xml=1 form and the profile's "most played") now needs a sign-in. So the helper times sessions from "in a game" checks.
   Part 1: Steam's public profile page for WHO (profile links, custom names or 64-bit ids, | separated; default: one
   well-known public test profile). Only what the helper needs is printed: whether it answered, public or not, and
   whether the page has the in-game fields.
   Part 2: the helper's own /steam/me + /steam/sync + cron code against real Steam (fake KV, real fetch).
   Part 3: the live helper's /version (steamMe) and /steam/me, to see that the deploy picked it up. */
import { copyFileSync } from "node:fs";
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36";
const WHO = String(process.env.WHO || "robinwalker").split("|").map(s => s.trim()).filter(Boolean);
const base = w => /^\d{17}$/.test(w) ? "https://steamcommunity.com/profiles/" + w : /^https?:/.test(w) ? w.replace(/\/+$/, "") : "https://steamcommunity.com/id/" + w;
const cut = (s, n) => String(s).replace(/\s+/g, " ").slice(0, n);
console.log("== 1. Steam's public profile page");
for (const w of WHO) {
  try {
    const r = await fetch(base(w) + "/?xml=1", { headers: { "User-Agent": UA } }); const t = await r.text();
    console.log(` profile ${WHO.indexOf(w) + 1}: ${r.status} ${r.headers.get("content-type")} profile=${t.includes("<profile>")} id=${/<steamID64>\d{17}</.test(t)} public=${/<privacyState>public</.test(t)} onlineState=${/<onlineState>/.test(t)} inGameInfo=${/<inGameInfo>/.test(t)}`);
  } catch (e) { console.log(` profile ${WHO.indexOf(w) + 1}: error ${e.message}`); }
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
const brief = j => JSON.stringify({ ok: j.ok, error: j.error, linked: !!j.sid, private: j.private, playing: !!j.playing, live: !!j.live, done: j.done && j.done.length });
for (const w of [WHO[0], "this-name-does-not-exist-zz9"]) {
  try { const r = await mod.fetch(req("https://h", "/steam/me", { id: pid, who: w, apps: [{ k: "g1", t: "Probe game one", a: 570 }, { k: "g2", t: "Probe game two" }] }), env);
    console.log(" /steam/me", w === WHO[0] ? "profile 1" : "unknown name", r.status, brief(await r.json())); } catch (e) { console.log(" /steam/me error", e.message); }
}
try { const r = await mod.fetch(req("https://h", "/steam/sync", { id: pid, apps: [{ k: "g1", t: "Probe game one", a: 570 }] }), env); console.log(" /steam/sync", r.status, brief(await r.json())); } catch (e) { console.log(" /steam/sync error", e.message); }
try { let w; await mod.scheduled({}, env, { waitUntil: p => { w = p; } }); await w; console.log(" cron ok, record kept:", kv.has("sp:" + pid)); } catch (e) { console.log(" cron error", e.message); }
console.log("== 3. live helper");
try {
  const v = await (await fetch(LIVE + "/version")).json(); console.log(" version", v.v, "steamMe:", v.steamMe);
  if (v.steamMe) { const r = await fetch(req(LIVE, "/steam/me", { id: pid, who: WHO[0], apps: [] })); console.log(" live /steam/me", r.status, brief(await r.json()));
    await fetch(req(LIVE, "/steam/me", { id: pid, off: 1 })); }
} catch (e) { console.log(" live helper:", cut(e.message, 120)); }
