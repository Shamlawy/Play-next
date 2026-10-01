/* Probe (v216): runs the helper's own Nintendo eShop and Steam code (worker/helper.js) against the real services from GitHub
   and prints what the app would get. */
import fs from "fs";
let src = fs.readFileSync("worker/helper.js", "utf8").replace("export default {", "const __def = {");
src += "\nexport { pxCheck, pxNsFind, pxNsPrices, stItems, stMore, stTagNames, stRoute };\n";
fs.writeFileSync("/tmp/hx.mjs", src);
const H = await import("/tmp/hx.mjs");
const env = {};   /* no KV: the tag list is fetched each run */
const show = p => !p ? "—" : p.none ? "not found" : p.err ? "ERROR " + p.err : p.nosale ? "not sold here" : p.nop ? "no price yet" :
  `${p.name || ""} | ${p.free ? "free" : (p.nowF || "") + (p.pct ? ` (was ${p.baseF}, −${p.pct}%${p.end ? ", ends " + new Date(p.end).toISOString().slice(0, 10) : ""})` : "")}${p.pre ? " | pre-order" : ""} | ${p.id || ""} | ${p.ncc || ""} | ${p.url || ""}`;
const SW = (process.env.NSTITLES || "Super Mario Odyssey|The Legend of Zelda: Tears of the Kingdom|Hollow Knight|Fire Emblem: Three Houses|Metroid Prime 4: Beyond|Persona 5 Royal|Mario Kart World|Hades II|Pokemon Legends: Z-A|Xenoblade Chronicles 3|Octopath Traveler II|Animal Crossing: New Horizons").split("|");
for (const nscc of ["us", "gb", "au", "ae"]) {
  console.log(`\n===== eShop ${nscc.toUpperCase()} =====`);
  for (let i = 0; i < SW.length; i += 6) {
    const items = SW.slice(i, i + 6).map((t, n) => ({ k: "g" + (i + n), t, st: -1, ps: "-", ns: "", sw2: /Mario Kart World/.test(t) }));
    const res = await H.pxCheck(nscc, items, nscc);
    for (const it of items) console.log(`${it.t.padEnd(42)} ${show(res[it.k].ns)}`);
    const again = items.map(it => ({ ...it, ns: res[it.k].ns && res[it.k].ns.id || "-" }));
    const r2 = await H.pxCheck(nscc, again, nscc);
    const bad = again.filter(it => it.ns !== "-" && !(r2[it.k].ns && r2[it.k].ns.now != null));
    console.log(`  (re-check with saved ids: ${bad.length ? "no price for " + bad.map(b => b.t).join(", ") : "ok"})`);
  }
}
console.log("\n===== Steam tags (stRoute) =====");
const mk = body => new Request("https://x/steam", { method: "POST", headers: { Origin: "https://shamlawy.github.io", "Content-Type": "application/json" }, body: JSON.stringify(body) });
const ST = (process.env.STTITLES || "Elden Ring|Hollow Knight|Persona 5 Royal|Metaphor: ReFantazio|Clair Obscur: Expedition 33|Stardew Valley|Hades II|Baldur's Gate 3|Resident Evil 4").split("|");
{ const r = await H.stRoute(mk({ cc: "us", items: ST.map((t, i) => ({ k: "s" + i, t, st: 0 })) }), env, {}); const j = await r.json();
  ST.forEach((t, i) => { const x = j.res["s" + i]; console.log(t.padEnd(30), x ? (x.none ? "not found" : x.err ? "ERR " + x.err : `${x.st} ${x.name} | ${x.tags.join(", ")}`) : "—"); }); }
console.log("\n===== Steam more like this =====");
for (const id of [1245620, 1687950]) { const r = await H.stRoute(mk({ cc: "us", items: [], more: id }), env, {}); const j = await r.json();
  console.log(id, j.more && j.more.of && j.more.of.name, "→", j.more ? j.more.games.slice(0, 12).map(g => g.name + " [" + g.tags.slice(0, 3).join("/") + "]").join(" ; ") : JSON.stringify(j).slice(0, 300)); }
