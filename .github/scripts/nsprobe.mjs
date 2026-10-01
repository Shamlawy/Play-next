/* Probe (v216): what Nintendo's eShop and Steam's store pages really return, from GitHub's servers.
   Prints short summaries only. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en" };
const cut = (s, n = 600) => String(s).replace(/\s+/g, " ").slice(0, n);
async function get(u, o = {}) { try { const r = await fetch(u, { ...o, headers: { ...UA, ...(o.headers || {}) } }); const t = await r.text(); return { s: r.status, t }; } catch (e) { return { s: "ERR " + e.message, t: "" }; } }
const titles = ["Super Mario Odyssey", "The Legend of Zelda: Tears of the Kingdom", "Hollow Knight", "Fire Emblem: Three Houses", "Metroid Prime 4: Beyond", "Persona 5 Royal"];
console.log("===== Nintendo US (Algolia) =====");
for (const t of titles.slice(0, 4)) {
  for (const [app, key, idx] of [["U3B6GR4UA3", "a29c6927638bfd8cee23993e51e721c9", "store_game_en_us"], ["U3B6GR4UA3", "a29c6927638bfd8cee23993e51e721c9", "store_all_products_en_us"]]) {
    const r = await get(`https://${app}-dsn.algolia.net/1/indexes/${idx}/query`, { method: "POST", headers: { "X-Algolia-Application-Id": app, "X-Algolia-API-Key": key, "Content-Type": "application/json" }, body: JSON.stringify({ query: t, hitsPerPage: 5 }) });
    let j = null; try { j = JSON.parse(r.t); } catch (e) {}
    console.log(idx, t, r.s, j && j.hits ? j.hits.slice(0, 3).map(h => `${h.title} | nsuid ${h.nsuid} | ${h.platform || h.platformCode || ""} | price ${JSON.stringify(h.price || h.msrp || "")} | keys ${Object.keys(h).slice(0, 40).join(",")}`).join(" || ") : cut(r.t, 300));
  }
}
console.log("\n===== Nintendo EU search =====");
for (const t of titles.slice(0, 4)) {
  const r = await get(`https://searching.nintendo-europe.com/en/select?q=${encodeURIComponent(t)}&fq=type:GAME%20AND%20system_type:nintendoswitch*&rows=5&wt=json`);
  let j = null; try { j = JSON.parse(r.t); } catch (e) {}
  console.log(t, r.s, j && j.response ? j.response.docs.slice(0, 3).map(d => `${d.title} | nsuid ${JSON.stringify(d.nsuid_txt)} | price ${d.price_regular_f} / ${d.price_discounted_f} | ${d.system_names_txt}`).join(" || ") : cut(r.t, 300));
}
console.log("\n===== Nintendo JP search =====");
for (const t of ["スーパーマリオ オデッセイ", "Hollow Knight"]) {
  const r = await get(`https://search.nintendo.jp/nintendo_soft/search.json?q=${encodeURIComponent(t)}&opt_hard=05_Switch&limit=5`);
  let j = null; try { j = JSON.parse(r.t); } catch (e) {}
  console.log(t, r.s, j && j.result ? j.result.items.slice(0, 3).map(d => `${d.title} | nsuid ${d.nsuid} | ${d.hard}`).join(" || ") : cut(r.t, 300));
}
console.log("\n===== Nintendo price API =====");
const ids = { US: ["70010000000964", "70010000063714", "70010000003208"], GB: ["70010000000963", "70010000063715", "70010000001131"], AU: ["70010000000963", "70010000063715"], DE: ["70010000000963"], JP: ["70010000000965"], CA: ["70010000000964"], MX: ["70010000000964"], ZA: ["70010000000963"], AE: ["70010000000963"], SA: ["70010000000963"] };
for (const [cc, list] of Object.entries(ids)) {
  const r = await get(`https://api.ec.nintendo.com/v1/price?country=${cc}&lang=en&ids=${list.join(",")}`);
  console.log(cc, r.s, cut(r.t, 700));
}
console.log("\n===== Steam store page tags =====");
const ck = { Cookie: "birthtime=0; lastagecheckage=1-0-1990; mature_content=1; wants_mature_content=1" };
for (const id of [1245620, 367520, 1687950, 2050650]) {
  const r = await get(`https://store.steampowered.com/app/${id}/?l=english&cc=us`, { headers: ck });
  const m = r.t.match(/InitAppTagModal\(\s*\d+,\s*(\[[\s\S]*?\])\s*,/);
  let tags = null; try { tags = m && JSON.parse(m[1]).map(x => x.name + ":" + x.count); } catch (e) { tags = "parse fail"; }
  const alt = [...r.t.matchAll(/class="app_tag"[^>]*>\s*([^<]+?)\s*</g)].map(x => x[1]).slice(0, 15);
  console.log(id, r.s, "len", r.t.length, "modal:", JSON.stringify(tags).slice(0, 500), "| app_tag:", alt.join(", "), r.t.includes("agegate") ? "AGEGATE" : "");
}
console.log("\n===== SteamSpy tags =====");
for (const id of [1245620, 367520]) { const r = await get(`https://steamspy.com/api.php?request=appdetails&appid=${id}`); let j = null; try { j = JSON.parse(r.t); } catch (e) {} console.log(id, r.s, j ? JSON.stringify(j.tags).slice(0, 300) + " genre:" + j.genre : cut(r.t, 200)); }
console.log("\n===== Steam appdetails genres =====");
for (const id of [1245620, 367520]) { const r = await get(`https://store.steampowered.com/api/appdetails?appids=${id}&l=english&filters=genres,categories,basic`); console.log(id, r.s, cut(r.t, 500)); }
console.log("\n===== Steam more like this =====");
for (const id of [1245620, 367520]) {
  for (const u of [`https://store.steampowered.com/recommended/morelike/app/${id}/?l=english`, `https://store.steampowered.com/explore/morelike/${id}/`]) {
    const r = await get(u, { headers: ck });
    const ids2 = [...new Set([...r.t.matchAll(/data-ds-appid="(\d+)"/g)].map(x => x[1]))];
    const names = [...r.t.matchAll(/<img[^>]+alt="([^"]{2,80})"/g)].map(x => x[1]).slice(0, 12);
    console.log(u, r.s, "len", r.t.length, "ids", ids2.length, ids2.slice(0, 15).join(","), "| names", names.join(" ; "));
    const snip = r.t.indexOf("similar_grid_item"); if (snip > 0) console.log("   snippet:", cut(r.t.slice(snip, snip + 800), 800));
  }
}
console.log("\n===== Steam storesearch for tags? =====");
{ const r = await get(`https://store.steampowered.com/api/storesearch/?term=elden%20ring&cc=us&l=english`); console.log(cut(r.t, 400)); }
