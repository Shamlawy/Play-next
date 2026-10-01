/* Price probe: runs the helper's own price code (worker/helper.js, pxCheck) against the real Steam and PlayStation
   Store from GitHub (this repo's dev container can't reach them) and prints what each game would show. */
import fs from "fs";
const src = fs.readFileSync("worker/helper.js", "utf8");
const px = src.slice(src.indexOf("const PX_TTL"), src.indexOf("async function pxRoute"));
const F = new Function(px + "; return { pxCheck, pxPsFind, pxPsPrice, pxSame };")();
const CCS = (process.env.CCS || "us,ae").split(",");
const TITLES = (process.env.TITLES || "Elden Ring|Returnal|Stellar Blade|Final Fantasy VII Rebirth|Metaphor: ReFantazio|Persona 3 Reload|Ghost of Yotei|Death Stranding 2|Monster Hunter Wilds|The Last of Us Part I|Astro Bot|Silent Hill 2|Hollow Knight: Silksong|Marvel's Spider-Man 2|Clair Obscur: Expedition 33").split("|");
const show = p => !p ? "—" : p.none ? "not found" : p.err ? "ERROR " + p.err : p.nosale ? "not sold here" : p.nop ? "no price yet" :
  `${p.name || ""} | ${p.free ? "free" : (p.nowF || "") + (p.pct ? ` (was ${p.baseF}, −${p.pct}%${p.end ? ", ends " + new Date(p.end).toISOString().slice(0, 10) : ""})` : "")}${p.plus ? " | PS Plus tier " + p.plus + " catalog" : ""}${p.plusNowF ? " | Plus price " + p.plusNowF : ""}${p.trial ? " | trial tier " + p.trial : ""}${p.pre ? " | pre-order" : ""}${p.id ? " | " + p.id : ""}${p.cid ? " c" + p.cid : ""}`;
for (const cc of CCS) {
  console.log(`\n===== ${cc.toUpperCase()} =====`);
  for (let i = 0; i < TITLES.length; i += 5) {
    const items = TITLES.slice(i, i + 5).map((t, n) => ({ k: "g" + (i + n), t, st: 0, ps: "" }));
    const res = await F.pxCheck(cc, items);
    for (const it of items) console.log(`${it.t.padEnd(32)} Steam: ${show(res[it.k].st)}\n${"".padEnd(32)} PS:    ${show(res[it.k].ps)}`);
    /* second pass the way the phone does it: with the ids it got back (one PS call each) */
    const again = items.map(it => ({ ...it, st: res[it.k].st && res[it.k].st.id || -1, ps: res[it.k].ps && res[it.k].ps.id || "-", pc: res[it.k].ps && res[it.k].ps.cid || "" }));
    const r2 = await F.pxCheck(cc, again);
    const bad = again.filter(it => it.ps !== "-" && !(r2[it.k].ps && r2[it.k].ps.id));
    console.log(`  (re-check with saved ids: ${bad.length ? "FAILED for " + bad.map(b => b.t).join(", ") : "ok"})`);
  }
}
