/* Art probe: what the stores really send for game pictures (Steam, Xbox/Microsoft Store, PlayStation Store, Nintendo eShop).
   RAW=1 prints the raw store answers (to build the reader); otherwise runs the helper's own /art code. */
import fs from "fs";
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en" };
const cut = (s, n) => String(s).slice(0, n || 3000);
const get = async (u, o) => { try { const r = await fetch(u, { ...(o || {}), headers: { ...UA, ...((o || {}).headers || {}) } }); const t = await r.text(); return [r.status, t]; } catch (e) { return [0, String(e)]; } };
const T = (process.env.ARTTITLES || "Elden Ring|Persona 5 Royal|Metaphor: ReFantazio|Clair Obscur: Expedition 33").split("|");
if (process.env.RAW !== "0") {
  console.log("===== Steam GetItems assets + screenshots (1245620 Elden Ring, 2679460 Metaphor) =====");
  for (const id of [1245620, 2679460, 1903340]) {
    const input = { ids: [{ appid: id }], context: { language: "english", country_code: "US", steam_realm: 1 },
      data_request: { include_assets: true, include_screenshots: true, include_basic_info: false } };
    const [s, t] = await get("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=" + encodeURIComponent(JSON.stringify(input)));
    console.log(id, s, cut(t, 2500));
  }
  console.log("\n===== Steam appdetails screenshots/background =====");
  { const [s, t] = await get("https://store.steampowered.com/api/appdetails?appids=1245620&cc=us&l=english");
    try { const d = JSON.parse(t)["1245620"].data; console.log(s, JSON.stringify({ header: d.header_image, capsule: d.capsule_image, capv5: d.capsule_imagev5, bg: d.background, bgraw: d.background_raw, shots: (d.screenshots || []).slice(0, 3) })); } catch (e) { console.log(s, cut(t, 500)); } }
  console.log("\n===== Xbox autosuggest =====");
  for (const q of ["Elden Ring", "Metaphor ReFantazio", "Clair Obscur Expedition 33"]) {
    for (const u of [`https://displaycatalog.mp.microsoft.com/v7.0/productFamilies/autosuggest?market=US&languages=en-US&query=${encodeURIComponent(q)}&mediaType=games`,
                     `https://displaycatalog.mp.microsoft.com/v7.0/productFamilies/autosuggest?market=US&languages=en-US&query=${encodeURIComponent(q)}&productFamilyNames=Games`]) {
      const [s, t] = await get(u); console.log(q, s, cut(t, 1500));
    }
  }
  console.log("\n===== Xbox product (9P3J32CTXLRZ = Elden Ring?) =====");
  { const [s, t] = await get("https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds=9P3J32CTXLRZ&market=US&languages=en-US");
    try { const p = JSON.parse(t).Products[0], L = p.LocalizedProperties[0]; console.log(s, L.ProductTitle, JSON.stringify(L.Images.map(i => [i.ImagePurpose, i.Width, i.Height, i.Uri]))); } catch (e) { console.log(s, cut(t, 800)); } }
  console.log("\n===== PS web search (raw first 2 results) =====");
  const PSQ = { metGetProductById: "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a", getSearchResults: "4df6284f982e57bec70f23c77e2c219dc792eb19af7fb3d3a81767aa3f1958aa",
    metGetConceptById: "cc90404ac049d935afbd9968aef523da2b6723abfb9d586e5f77ebf7c5289006" };
  const gql = (op, vars) => get(`https://web.np.playstation.com/api/graphql/v1/op?operationName=${op}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: PSQ[op] } }))}`,
    { headers: { "x-psn-store-locale-override": "en-US", "content-type": "application/json" } });
  { const [s, t] = await gql("getSearchResults", { countryCode: "US", languageCode: "en", nextCursor: "", pageOffset: 0, pageSize: 24, searchTerm: "Elden Ring" });
    try { const r = JSON.parse(t).data.universalSearch.results; console.log(s, JSON.stringify(r.slice(0, 2)).slice(0, 4000)); } catch (e) { console.log(s, cut(t, 800)); } }
  { const [s, t] = await gql("metGetProductById", { productId: "UP0700-PPSA04609_00-ELDENRING0000000" }); console.log("product", s, cut(t, 4000)); }
  console.log("\n===== Nintendo (Algolia US hit fields, EU doc image fields) =====");
  { const [s, t] = await get("https://U3B6GR4UA3-dsn.algolia.net/1/indexes/store_game_en_us/query", { method: "POST",
      headers: { "X-Algolia-Application-Id": "U3B6GR4UA3", "X-Algolia-API-Key": "a29c6927638bfd8cee23993e51e721c9", "Content-Type": "application/json" },
      body: JSON.stringify({ query: "Persona 5 Royal", hitsPerPage: 2 }) });
    try { const h = JSON.parse(t).hits[0]; console.log(s, JSON.stringify(Object.fromEntries(Object.entries(h).filter(([k, v]) => /image|art|hero|box|media|asset|url/i.test(k))))); } catch (e) { console.log(s, cut(t, 600)); } }
  { const [s, t] = await get("https://searching.nintendo-europe.com/en/select?q=Persona%205%20Royal&fq=type:GAME&rows=2&wt=json");
    try { const d = JSON.parse(t).response.docs[0]; console.log(s, JSON.stringify(Object.fromEntries(Object.entries(d).filter(([k]) => /image|art|hero|box|media|screenshot/i.test(k))))); } catch (e) { console.log(s, cut(t, 600)); } }
}
