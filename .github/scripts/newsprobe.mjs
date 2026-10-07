// Game news + calendar probe (v18 helper). This container can't reach Steam or Cloudflare, so GitHub runs it:
// 1. the helper's own /news code against Steam's real news API (fake KV, real fetch);
// 2. a calendar round trip (/cal/put → /cal/<id>.ics) through the same code;
// 3. the live helper's /version, /news and calendar, to see that the deploy picked them up.
// Input IDS: Steam app ids, | separated (any well-known games do).
import { copyFileSync } from "node:fs";
copyFileSync("worker/helper.js", "/tmp/helper.mjs");
const mod = (await import("/tmp/helper.mjs")).default;
const kv = new Map();
const env = { NUDGE: { get: async (k, t) => { const v = kv.get(k); return v == null ? null : t === "json" ? JSON.parse(v) : v; }, put: async (k, v) => kv.set(k, v), delete: async k => kv.delete(k) } };
const O = "https://shamlawy.github.io", LIVE = "https://playnext-helper.hussamnabil48.workers.dev";
const ids = String(process.env.IDS || "1145360|413150|367520|1086940").split("|").map(x => +x.trim()).filter(x => x > 0);
const req = (base, path, body, method = "POST") => new Request(base + path, { method, headers: { "Content-Type": "application/json", Origin: O }, body: method === "POST" ? JSON.stringify(body) : undefined });
const show = j => { for (const [k, r] of Object.entries(j.res || {})) { console.log(" ", k, r.err ? "ERROR " + r.err : (r.n || []).length + " posts");
  for (const x of (r.n || []).slice(0, 3)) console.log("    ", new Date(x.d * 1000).toISOString().slice(0, 10), x.k.padEnd(7), x.t.slice(0, 70), "|", x.x.slice(0, 50)); } };
console.log("== helper code → Steam news");
let r = await mod.fetch(req("https://h", "/news", { items: ids.map((st, i) => ({ k: "g" + i + ":" + st, st })) }), env);
show(await r.json());
console.log("== calendar round trip (helper code)");
const id = "probe" + Math.random().toString(36).slice(2, 20).padEnd(18, "x");
r = await mod.fetch(req("https://h", "/cal/put", { id, ev: [{ k: "p1", t: "Probe game, one", d: "2026-12-01", p: "PC" }] }), env); console.log(" put", r.status, await r.text());
r = await mod.fetch(new Request("https://h/cal/" + id + ".ics"), env); const ics = await r.text(); console.log(" get", r.status, r.headers.get("content-type"), ics.split("\r\n").length, "lines");
console.log("== live helper");
try {
  const v = await (await fetch(LIVE + "/version")).json(); console.log(" version", v.v, "news:", v.news, "cal:", v.cal);
  r = await fetch(req(LIVE, "/news", { items: ids.slice(0, 2).map((st, i) => ({ k: "g" + i + ":" + st, st })) })); console.log(" /news", r.status); show(await r.json());
  r = await fetch(req(LIVE, "/cal/put", { id, ev: [{ k: "p1", t: "Probe game", d: "2026-12-01" }] })); console.log(" /cal/put", r.status, await r.text());
  r = await fetch(LIVE + "/cal/" + id + ".ics"); const t = await r.text(); console.log(" /cal get", r.status, r.headers.get("content-type"), t.includes("BEGIN:VEVENT") ? "has the event" : t.slice(0, 80));
  r = await fetch(req(LIVE, "/cal/put", { id, off: 1 })); console.log(" /cal off", r.status);
} catch (e) { console.log(" live helper:", e.message); }
