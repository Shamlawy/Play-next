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
