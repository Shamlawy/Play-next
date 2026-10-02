/* Price probe: runs the helper's own price code (worker/helper.js, pxCheck) against the real Steam and PlayStation
   Store from GitHub (this repo's dev container can't reach them) and prints what each game would show. */
import fs from "fs";
const src = fs.readFileSync("worker/helper.js", "utf8");
const px = src.slice(src.indexOf("const PX_TTL"), src.indexOf("async function pxRoute"));
const F = new Function(px + "; return { pxCheck, pxPsFind, pxPsPrice, pxSame, fxRates, fxShow };")();
const FX = await F.fxRates({}).catch(e => ({ ok: false, e: e.message }));
console.log("FX:", FX.ok ? `ok, ${Object.keys(FX.r).length} rates, AED ${FX.r.AED} EGP ${FX.r.EGP} SAR ${FX.r.SAR} EUR ${FX.r.EUR} JPY ${FX.r.JPY}, as of ${new Date(FX.t).toISOString()}` : "FAILED " + JSON.stringify(FX));
const MC = { ae: "AED", eg: "EGP", sa: "SAR", us: "USD", gb: "GBP", jp: "JPY", de: "EUR", kw: "KWD", qa: "QAR" };
const CCS = (process.env.CCS || "us,ae").split(",");
const TITLES = (process.env.TITLES || "Elden Ring|Returnal|Stellar Blade|Final Fantasy VII Rebirth|Metaphor: ReFantazio|Persona 3 Reload|Ghost of Yotei|Death Stranding 2|Monster Hunter Wilds|The Last of Us Part I|Astro Bot|Silent Hill 2|Hollow Knight: Silksong|Marvel's Spider-Man 2|Clair Obscur: Expedition 33").split("|");
const show = p => !p ? "—" : p.none ? "not found" : p.err ? "ERROR " + p.err : p.nosale ? "not sold here" : p.nop ? "no price yet" :
  `${p.name || ""} | ${p.free ? "free" : (p.nowF || "") + (p.pct ? ` (was ${p.baseF}, −${p.pct}%${p.end ? ", ends " + new Date(p.end).toISOString().slice(0, 10) : ""})` : "")}${p.plus ? " | PS Plus tier " + p.plus + " catalog" : ""}${p.plusNowF ? " | Plus price " + p.plusNowF : ""}${p.trial ? " | trial tier " + p.trial : ""}${p.pre ? " | pre-order" : ""}${p.id ? " | " + p.id : ""}${p.cid ? " c" + p.cid : ""}`;
for (const cc of CCS) {
  console.log(`\n===== ${cc.toUpperCase()} =====`);
  for (let i = 0; i < TITLES.length; i += 5) {
    const items = TITLES.slice(i, i + 5).map((t, n) => ({ k: "g" + (i + n), t, st: 0, ps: "", ns: process.env.NS ? "" : "-" }));
    const res = await F.pxCheck(cc, items);
    const mine = (s, p) => p && p.now != null && FX.ok ? ` [raw ${p.cur} ${p.now} → ${F.fxShow(FX, s, p, p.now, MC[cc] || "USD") || "same"}]` : "";
    for (const it of items) console.log(`${it.t.padEnd(32)} Steam: ${show(res[it.k].st)}${mine("st", res[it.k].st)}\n${"".padEnd(32)} PS:    ${show(res[it.k].ps)}${mine("ps", res[it.k].ps)}${res[it.k].ps && res[it.k].ps.pcc ? " (store " + res[it.k].ps.pcc + ")" : ""}${process.env.NS ? `\n${"".padEnd(32)} eShop: ${show(res[it.k].ns)}${mine("ns", res[it.k].ns)}` : ""}`);
    /* second pass the way the phone does it: with the ids it got back (one PS call each) */
    const again = items.map(it => ({ ...it, ns: "-", st: res[it.k].st && res[it.k].st.id || -1, ps: res[it.k].ps && res[it.k].ps.id || "-", pc: res[it.k].ps && res[it.k].ps.cid || "" }));
    const r2 = await F.pxCheck(cc, again);
    const bad = again.filter(it => it.ps !== "-" && !(r2[it.k].ps && r2[it.k].ps.id));
    console.log(`  (re-check with saved ids: ${bad.length ? "FAILED for " + bad.map(b => b.t).join(", ") : "ok"})`);
  }
}
/* DEBUG=1: what the PS website search and Steam search return for each title (to tune matching) */
if (process.env.DEBUG) {
  const G = new Function(px + "; return { psGql, pxSame, pxNorm, PS_JUNK, PS_ED };")();
  for (const cc of CCS) for (const t of TITLES) {
    try {
      const j = await G.psGql(cc, "getSearchResults", { countryCode: cc.toUpperCase(), languageCode: "en", nextCursor: "", pageOffset: 0, pageSize: 24, searchTerm: t });
      const res = (((j || {}).data || {}).universalSearch || {}).results || [];
      console.log(`\n## PS web ${cc} "${t}" → ${res.length} results (keys: ${Object.keys(res[0] || {}).join(",")})`);
      res.slice(0, 10).forEach(r => console.log(`   ${r.__typename} ${r.id} | ${r.name} | ${r.localizedStoreDisplayClassification || r.storeDisplayClassification || ""} | score ${G.pxSame(t, r.name || "").toFixed(2)}`));
    } catch (e) { console.log("PS web error", e.message); }
    try {
      const r = await fetch(`https://store.steampowered.com/api/storesearch/?term=${encodeURIComponent(t)}&cc=${cc}&l=english`);
      const j = await r.json(); console.log(`## Steam ${cc} "${t}":`, (j.items || []).slice(0, 6).map(x => `${x.id} ${x.name} [${x.type}] ${x.price ? JSON.stringify(x.price) : "noprice"}`).join(" ; "));
      const top = (j.items || [])[0];
      if (top) { const d = await (await fetch(`https://store.steampowered.com/api/appdetails?appids=${top.id}&cc=${cc}`)).json(); const x = d[top.id] || {}; console.log(`   appdetails ${top.id}: success=${x.success} free=${x.data && x.data.is_free} release=${JSON.stringify(x.data && x.data.release_date)} price=${JSON.stringify(x.data && x.data.price_overview)} packages=${JSON.stringify(x.data && x.data.packages)}`); }
    } catch (e) { console.log("Steam error", e.message); }
  }
}
