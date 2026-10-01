/* Probe (v216, round 2): Steam names+tags for many apps in one call; a discounted eShop price shape; Switch 2 vs Switch titles. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en" };
const cut = (s, n = 600) => String(s).replace(/\s+/g, " ").slice(0, n);
async function get(u, o = {}) { try { const r = await fetch(u, { ...o, headers: { ...UA, ...(o.headers || {}) } }); const t = await r.text(); return { s: r.status, t }; } catch (e) { return { s: "ERR " + e.message, t: "" }; } }
console.log("===== IStoreBrowseService/GetItems =====");
{ const input = { ids: [374320, 570940, 335300, 2584270, 1627720].map(appid => ({ appid })), context: { language: "english", country_code: "US", steam_realm: 1 }, data_request: { include_basic_info: true, include_tag_count: 12, include_assets: false } };
  const r = await get("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=" + encodeURIComponent(JSON.stringify(input)));
  console.log(r.s, cut(r.t, 1500)); }
console.log("\n===== GetTagList =====");
{ const r = await get("https://api.steampowered.com/IStoreService/GetTagList/v1/?language=english"); console.log(r.s, cut(r.t, 600)); }
console.log("\n===== Algolia: discounted games + editions =====");
const alg = async (body) => { const r = await get("https://U3B6GR4UA3-dsn.algolia.net/1/indexes/store_game_en_us/query", { method: "POST", headers: { "X-Algolia-Application-Id": "U3B6GR4UA3", "X-Algolia-API-Key": "a29c6927638bfd8cee23993e51e721c9", "Content-Type": "application/json" }, body: JSON.stringify(body) }); try { return JSON.parse(r.t); } catch (e) { return { err: cut(r.t, 300) }; } };
{ const j = await alg({ query: "", hitsPerPage: 5, filters: "price.discounted:true" }); console.log(j.err || j.hits.map(h => `${h.title} | ${h.nsuid} | ${JSON.stringify(h.price)} | dlcType ${h.dlcType} | topLevelCategory ${h.topLevelCategory} | urlKey ${h.urlKey} | url ${h.url} | genres ${JSON.stringify(h.gameGenreLabels)}`).join("\n"));
  const ns = j.hits ? j.hits.map(h => h.nsuid).filter(Boolean).slice(0, 3) : [];
  if (ns.length) { const r = await get(`https://api.ec.nintendo.com/v1/price?country=US&lang=en&ids=${ns.join(",")}`); console.log("price:", cut(r.t, 1200)); } }
for (const q of ["Pokemon Legends Z-A", "Mario Kart World", "Donkey Kong Bananza", "Persona 5 Royal", "Hades II", "Metroid Prime 4"]) {
  const j = await alg({ query: q, hitsPerPage: 5 }); console.log(q, "→", j.err || j.hits.map(h => `${h.title} [${h.platform}] ${h.nsuid} ${h.price && h.price.finalPrice} dlc:${h.dlcType} cat:${h.topLevelCategory}`).join(" || "));
}
console.log("\n===== EU search: discounted + url =====");
{ const r = await get(`https://searching.nintendo-europe.com/en/select?q=hades&fq=type:GAME%20AND%20system_type:nintendoswitch*&rows=3&wt=json`); let j = null; try { j = JSON.parse(r.t); } catch (e) {} console.log(j ? j.response.docs.map(d => `${d.title} | ${JSON.stringify(d.nsuid_txt)} | url ${d.url} | type ${d.type} | ${d.system_names_txt} | price ${d.price_regular_f}`).join("\n") : cut(r.t, 300)); }
