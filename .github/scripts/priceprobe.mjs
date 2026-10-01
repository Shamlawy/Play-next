/* Price probe: fetches real Steam / PlayStation Store data (this repo's dev container can't reach them), runs the
   helper's own price reader on it, and asks the live helper the same, so the parser can be checked on real pages.
   Raw pages are saved as the "probe" artifact. Run by hand from the Actions tab (or on a push that changes it). */
import fs from "fs";
const OUT = "probe"; fs.mkdirSync(OUT, { recursive: true });
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en" };
const src = fs.readFileSync("worker/helper.js", "utf8");
const px = src.slice(src.indexOf("const PX_TTL"), src.indexOf("async function pxRoute"));
const F = new Function(px + "; return { pxPsParse, pxCheck, pxSteamFind, pxPsFind, pxSame };")();
const CCS = (process.env.CCS || "us,ae,kw").split(",");
const TITLES = (process.env.TITLES || "Elden Ring|Ghost of Tsushima|Stellar Blade|Final Fantasy VII Rebirth|Metaphor: ReFantazio|Persona 3 Reload|Hollow Knight: Silksong|Death Stranding 2|Ghost of Yotei|Monster Hunter Wilds").split("|");
const get = async (name, url) => {
  try {
    const r = await fetch(url, { headers: UA, redirect: "follow" }), t = await r.text();
    fs.writeFileSync(`${OUT}/${name}`, t);
    console.log(`${name}: ${r.status} ${t.length}B basePriceValue×${(t.match(/basePriceValue/g) || []).length} productIds×${(t.match(/[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/g) || []).length} concept×${(t.match(/\/concept\/\d+/g) || []).length} final ${r.url}`);
    return t;
  } catch (e) { console.log(`${name}: FAILED ${e.message}`); return ""; }
};
console.log("==== PlayStation Store, fetched from GitHub ====");
for (const cc of CCS) for (const t of TITLES.slice(0, 4)) {
  const html = await get(`ps-search-${cc}-${t.replace(/\W+/g, "_")}.html`, `https://store.playstation.com/en-${cc}/search/${encodeURIComponent(t)}`);
  const list = F.pxPsParse(html);
  console.log(`  parsed ${list.length}:`, JSON.stringify(list.slice(0, 5)));
  const pid = (list[0] || {}).id || (html.match(/[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/) || [])[0];
  if (pid && cc === CCS[0]) {
    const ph = await get(`ps-product-${cc}-${pid}.html`, `https://store.playstation.com/en-${cc}/product/${pid}`);
    console.log("  product parsed:", JSON.stringify(F.pxPsParse(ph).slice(0, 6)));
    /* what a subscription price object looks like (PS Plus discounts / Extra catalogue) */
    const subs = [...ph.matchAll(/\{[^{}]*"isTiedToSubscription":true[^{}]*\}/g)].map(m => m[0]).slice(0, 4);
    console.log("  subscription price objects:", subs.join("\n    "));
    const plus = [...ph.matchAll(/.{0,160}(PS_PLUS|EXTRA|PREMIUM|GAME_CATALOG|Game Catalog|serviceBranding|upsell)[^"]{0,40}".{0,160}/g)].map(m => m[0]).slice(0, 8);
    console.log("  plus mentions:", plus.join("\n    "));
  }
  const concept = (html.match(/\/concept\/\d+/) || [])[0];
  if (concept && cc === CCS[0]) {
    const ch = await get(`ps-concept-${cc}-${concept.split("/").pop()}.html`, `https://store.playstation.com/en-${cc}${concept}`);
    console.log("  concept parsed:", JSON.stringify(F.pxPsParse(ch).slice(0, 6)));
  }
}
console.log("==== Steam, fetched from GitHub ====");
for (const cc of CCS.slice(0, 2)) for (const t of TITLES) {
  const f = await F.pxSteamFind(cc, t).catch(e => ({ err: e.message }));
  console.log(`steam ${cc} "${t}" →`, JSON.stringify(f));
}
const s1 = await get("steam-appdetails.json", "https://store.steampowered.com/api/appdetails?appids=1245620,2622380,1030300,3489700&cc=us&filters=price_overview");
console.log("  ", s1.slice(0, 900));
console.log("==== The whole check, from GitHub ====");
const items = TITLES.map((t, i) => ({ k: "g" + i, t, st: 0, ps: "" }));
for (const cc of CCS.slice(0, 2)) console.log(cc, JSON.stringify(await F.pxCheck(cc, items.map(x => ({ ...x }))), null, 1));
console.log("==== The live helper (what Cloudflare sees) ====");
try {
  const r = await fetch("https://playnext-helper.hussamnabil48.workers.dev/prices", { method: "POST", headers: { "Content-Type": "application/json", Origin: "https://shamlawy.github.io" },
    body: JSON.stringify({ cc: CCS[0], items: items.slice(0, 6) }) });
  console.log(r.status, (await r.text()).slice(0, 4000));
} catch (e) { console.log("helper FAILED", e.message); }
