/* Steam sessions (helper v20): the helper's own /steam/me, /steam/sync and cron code against a fake Steam profile page and a
   fake push service, in Node (this container can't reach Steam or Cloudflare; steam-probe.yml checks the real Steam).
     node tools/steam-sessions.mjs
   Covers: linking by link / name / unknown / nonsense, a session seen by the cron start to end (±7 min timing), the push,
   handing it to the phone, a match by name only, a switch between games, a game not in the library, an outage (ends at the
   last sighting), old sessions dropping after 10 days, unlinking, the version flag and the Origin check. */
import { copyFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
const tmp = path.join(mkdtempSync(path.join(tmpdir(), "spt-")), "helper.mjs");
copyFileSync(new URL("../worker/helper.js", import.meta.url), tmp);
const mod = (await import(tmp)).default;
const kv = new Map();
const env = { NUDGE: { get: async (k, t) => { const v = kv.get(k); return v == null ? null : t === "json" ? JSON.parse(v) : v; }, put: async (k, v) => kv.set(k, v), delete: async k => kv.delete(k),
  list: async ({ prefix }) => ({ keys: [...kv.keys()].filter(k => k.startsWith(prefix)).map(name => ({ name })), list_complete: true }) } };
let state = "offline", game = null, pushes = [], now = Date.parse("2026-10-14T18:00:00Z");
Date.now = () => now;
const xml = () => `<?xml version="1.0"?><profile><steamID64>76561197960435530</steamID64><steamID><![CDATA[Robin]]></steamID><onlineState>${state}</onlineState><stateMessage><![CDATA[${state === "in-game" ? "In-Game<br/>" + game.n : "Online"}]]></stateMessage><privacyState>public</privacyState>${state === "in-game" && game.a ? `<inGameInfo><gameName><![CDATA[${game.n}]]></gameName><gameLink><![CDATA[https://steamcommunity.com/app/${game.a}]]></gameLink></inGameInfo>` : ""}</profile>`;
globalThis.fetch = async (u, o) => {
  u = String(u);
  if (u.includes("steamcommunity.com/id/nobody")) return new Response(`<?xml version="1.0"?><response><error><![CDATA[The specified profile could not be found.]]></error></response>`);
  if (u.includes("steamcommunity.com")) return new Response(xml());
  if (u.startsWith("https://push.example")) { pushes.push(o); return new Response("", { status: 201 }); }
  if (u.includes("shamlawy.github.io")) return new Response('const APP_V = "280";');
  throw new Error("unexpected fetch " + u);
};
const O = "https://shamlawy.github.io", id = "phone_abcdefghijklmnop";
const call = async (path, body) => { const r = await mod.fetch(new Request("https://h" + path, { method: "POST", headers: { Origin: O, "Content-Type": "application/json" }, body: JSON.stringify(body) }), env); return [r.status, await r.json()]; };
const cron = async () => { let w; await mod.scheduled({}, env, { waitUntil: p => { w = p; } }); await w; };
const apps = [{ k: "g1", a: 1245620, t: "Starfall Saga" }, { k: "g2", a: 0, t: "Ember Road II" }];
const ok = (c, m) => { console.log((c ? "✓ " : "✗ ") + m); if (!c) process.exitCode = 1; };
// a subscription so pushes go somewhere (keys from a real P-256 pair)
const kp = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
const raw = new Uint8Array(await crypto.subtle.exportKey("raw", kp.publicKey)), b64u = u8 => Buffer.from(u8).toString("base64url");
kv.set("sub:" + id, JSON.stringify({ sub: { endpoint: "https://push.example/x", keys: { p256dh: b64u(raw), auth: b64u(crypto.getRandomValues(new Uint8Array(16))) } }, plan: [] }));
let [st, j] = await call("/steam/me", { id, who: "https://steamcommunity.com/id/robinwalker/", apps });
ok(st === 200 && j.sid === "76561197960435530" && j.name === "Robin" && !j.private && !j.playing, "link by profile link: " + JSON.stringify(j));
[st, j] = await call("/steam/me", { id: "phone_zzzzzzzzzzzzzzzz", who: "nobody" }); ok(st === 404, "unknown name → 404 " + j.error);
[st, j] = await call("/steam/me", { id, who: "!!" }); ok(st === 400, "nonsense → 400");
// starts playing Starfall Saga: next cron sees it
state = "in-game"; game = { a: 1245620, n: "STARFALL SAGA" }; now += 15 * 60e3; await cron();
let rec = JSON.parse(kv.get("sp:" + id)); ok(rec.live && rec.live.a === 1245620, "cron sees the game: live " + JSON.stringify(rec.live));
const s0 = rec.live.s;
for (let i = 0; i < 5; i++) { now += 15 * 60e3; await cron(); }
[st, j] = await call("/steam/sync", { id }); ok(j.live && j.live.k === "g1" && !j.done.length, "sync while playing: live " + JSON.stringify(j.live));
// stops
state = "online"; now += 15 * 60e3; await cron();
rec = JSON.parse(kv.get("sp:" + id)); ok(!rec.live && rec.done.length === 1, "cron ends it: " + JSON.stringify(rec.done[0]));
const d = rec.done[0]; ok(Math.round((d.e - d.s) / 6e4) === 90 && d.k === "g1", "about 1h30 (" + Math.round((d.e - d.s) / 6e4) + " min), matched g1");
ok(pushes.length === 1, "one push sent (" + pushes.length + ")");
[st, j] = await call("/steam/sync", { id, apps }); ok(j.done.length === 1 && j.done[0].k === "g1" && !j.live, "sync hands it over: " + JSON.stringify(j.done));
// a game matched by name only, a short one (no push), a switch from one game to another
state = "in-game"; game = { a: 1145350, n: "Ember Road II" }; now += 60 * 60e3; await cron(); now += 15 * 60e3; await cron();
game = { a: 0, n: "Some non-Steam game" }; now += 15 * 60e3; await cron();
state = "online"; now += 15 * 60e3; await cron();
rec = JSON.parse(kv.get("sp:" + id)); ok(rec.done.length === 3 && rec.done[1].k === "g2" && rec.done[2].k === "", "name match + switch + unmatched: " + rec.done.map(x => x.k + ":" + Math.round((x.e - x.s) / 6e4)).join(" "));
ok(pushes.length === 2, "push for Ember Road II only (" + pushes.length + ")");
// a cron that missed hours: session ends at the last sighting
state = "in-game"; game = { a: 1245620, n: "STARFALL SAGA" }; now += 60 * 60e3; await cron(); now += 15 * 60e3; await cron();
state = "online"; now += 5 * 36e5; await cron();
rec = JSON.parse(kv.get("sp:" + id)); const last = rec.done.at(-1); ok(Math.round((last.e - last.s) / 6e4) === 30, "outage: ends at the last sighting (" + Math.round((last.e - last.s) / 6e4) + " min)");
// old ones drop out after 10 days
state = "in-game"; now += 11 * 864e5; await cron(); state = "online"; now += 30 * 60e3; await cron();
rec = JSON.parse(kv.get("sp:" + id)); ok(rec.done.length === 1, "older than 10 days dropped (" + rec.done.length + ")");
[st, j] = await call("/steam/me", { id, off: 1 }); ok(!kv.has("sp:" + id), "unlink deletes");
[st, j] = await call("/steam/sync", { id }); ok(j.gone, "sync after unlink says gone");
const v = await (await mod.fetch(new Request("https://h/version"), env)).json(); ok(v.v === 20 && v.steamMe, "version 20, steamMe");
let r = await mod.fetch(new Request("https://h/steam/me", { method: "POST", headers: { Origin: "https://evil.example" }, body: "{}" }), env); ok(r.status === 403, "other sites refused");
