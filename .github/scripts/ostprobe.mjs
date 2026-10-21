// Game music probe (v22 helper, app v321's skills book music). This container can't reach Apple or Cloudflare, so GitHub runs it:
// 1. the helper's own /ost code against Apple's real iTunes Search: the tracks it picks for each game, in order;
// 2. its /ost/clip passing the first preview on (status, type, size, CORS);
// 3. the live helper's /version and /ost, to see that the deploy picked them up.
// Input TITLES: game names, | separated (any well-known games do: the logs of a public repo are public, so never the owner's).
import { copyFileSync } from "node:fs";
copyFileSync("worker/helper.js", "/tmp/helper.mjs");
const mod = (await import("/tmp/helper.mjs")).default;
const O = "https://shamlawy.github.io", LIVE = "https://playnext-helper.hussamnabil48.workers.dev";
const titles = String(process.env.TITLES || "Hollow Knight|Celeste|Final Fantasy VII Remake|Hades|Undertale|NieR:Automata|Octopath Traveler").split("|").map(x => x.trim()).filter(Boolean);
const req = (base, path, body) => new Request(base + path, { method: "POST", headers: { "Content-Type": "application/json", Origin: O }, body: JSON.stringify(body) });
let first = "";
console.log("== helper code → Apple");
for (const t of titles) {
  const r = await mod.fetch(req("https://h", "/ost", { t }), {}); const j = await r.json();
  console.log(` ${t}: ${r.status} ${(j.tracks || []).length} tracks`);
  for (const x of j.tracks || []) console.log(`    ${x.n} | ${x.al} | ${x.a}`);
  if (!first && j.tracks && j.tracks[0]) first = j.tracks[0].u;
}
if (first) { const r = await mod.fetch(new Request("https://h/ost/clip?u=" + encodeURIComponent(first), { headers: { Origin: O } }), {});
  console.log(" clip", r.status, r.headers.get("content-type"), r.headers.get("access-control-allow-origin"), (await r.arrayBuffer()).byteLength, "bytes"); }
console.log("== live helper");
try {
  const v = await (await fetch(LIVE + "/version")).json(); console.log(" version", v.v, "ost:", v.ost);
  const r = await fetch(req(LIVE, "/ost", { t: titles[0] })); console.log(" /ost", r.status, JSON.stringify(await r.json()).slice(0, 300));
} catch (e) { console.log(" live helper:", e.message); }
// v323: how often the live helper finds music (Apple turns Cloudflare's shared addresses away), and whether a phone could
// ask Apple itself: CORS on the search and the clip host, and the JSONP form
console.log("== live helper, every title");
for (const t of titles) { try { const r = await fetch(req(LIVE, "/ost", { t })); const j = await r.json().catch(() => ({}));
  console.log(` ${t}: ${r.status} ${(j.tracks || []).length} tracks${j.error ? " " + j.error : ""}`); } catch (e) { console.log(` ${t}: ${e.message}`); } }
// v324: YouTube for games Apple has no soundtrack of, through the live helper (it holds the key; nokey until YOUTUBE_KEY is set)
console.log("== live helper → YouTube");
for (const t of titles) { try { const r = await fetch(req(LIVE, "/yt", { t })); const j = await r.json().catch(() => ({}));
  console.log(` ${t}: ${r.status} ${j.nokey ? "no YOUTUBE_KEY yet" : (j.videos || []).length + " videos"}${j.error ? " " + j.error : ""}`);
  for (const x of j.videos || []) console.log(`    ${x.y} ${x.d}s | ${x.n} | ${x.a}`); } catch (e) { console.log(` ${t}: ${e.message}`); } }
console.log("== Apple straight from a browser's point of view");
try { const r = await fetch("https://itunes.apple.com/search?term=" + encodeURIComponent(titles[0] + " soundtrack") + "&media=music&entity=song&limit=5", { headers: { Origin: O } });
  console.log(" search", r.status, "ACAO:", r.headers.get("access-control-allow-origin"), "type:", r.headers.get("content-type"));
  const j = await r.json(); const u = j.results && j.results[0] && j.results[0].previewUrl;
  if (u) { const c = await fetch(u, { headers: { Origin: O } }); console.log(" clip", c.status, "ACAO:", c.headers.get("access-control-allow-origin"), "type:", c.headers.get("content-type")); }
} catch (e) { console.log(" search:", e.message); }
try { const r = await fetch("https://itunes.apple.com/search?term=" + encodeURIComponent(titles[0]) + "&media=music&entity=song&limit=2&callback=cb");
  console.log(" jsonp", r.status, r.headers.get("content-type"), (await r.text()).trim().slice(0, 40)); } catch (e) { console.log(" jsonp:", e.message); }
