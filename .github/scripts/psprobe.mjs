/* PS Store probe 3: find a working search (title → concept id), and dump the pricing CTAs compactly (PS Plus types). */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const T = (s, n = 1500) => String(s).slice(0, n);
const H = { metGetProductById: "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a", metGetConceptById: "cc90404ac049d935afbd9968aef523da2b6723abfb9d586e5f77ebf7c5289006",
  metGetPricingDataByConceptId: "abcb311ea830e679fe2b697a27f755764535d825b24510ab1239a4ca3092bd09" };
const gql = async (op, hash, vars, loc = "en-US") => {
  const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${op}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: hash } }))}`;
  const r = await fetch(u, { headers: { ...UA, "x-psn-store-locale-override": loc, "content-type": "application/json" } });
  return [r.status, await r.text()];
};
/* 1. look through every JS chunk the search page and the build manifest name for search queries */
const page = await (await fetch("https://store.playstation.com/en-us/search/elden%20ring", { headers: UA })).text();
let srcs = [...page.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => new URL(m[1], "https://store.playstation.com/").href);
const man = srcs.find(s => /_buildManifest/.test(s));
if (man) { const mt = await (await fetch(man)).text(); for (const m of mt.matchAll(/"(static\/chunks\/[^"]+\.js)"/g)) srcs.push("https://store.playstation.com/_next/" + m[1]); }
srcs = [...new Set(srcs)].filter(s => /\.js/.test(s));
console.log("chunks:", srcs.length);
const hits = [];
for (const s of srcs.slice(0, 160)) {
  let js = ""; try { js = await (await fetch(s, { headers: UA })).text(); } catch (e) { continue; }
  for (const m of js.matchAll(/(getSearchResults|universalSearch|searchTerm|SearchResults|sha256Hash|persistedQuery|[a-f0-9]{64})/g)) {
    hits.push(s.split("/").pop() + " :: " + js.slice(Math.max(0, m.index - 140), m.index + 180).replace(/\s+/g, " "));
    if (hits.length > 60) break;
  }
}
console.log("hits:\n" + hits.slice(0, 60).join("\n---\n"));
/* 2. known candidates for search */
const CAND = [["getSearchResults", "a2fbc15433b37ca7bfcd7112f741735e13268f5e9ebd5ffce51b85acc126f41a"], ["getSearchResults", "6ef5e809c35ab5bc1b8a5bd8e1aa5a5e9fc7d3b31da6f61b8a7ab2ffc8ff13f1"],
  ["universalSearch", "4cd67a3e17f9e3f1c4a6e6c7c95b44a4e5bfaf4f2b4fa17bba4b6a0ad48e7b1e"]];
for (const [op, h] of CAND) { const [st, t] = await gql(op, h, { searchTerm: "elden ring", searchContext: "MobileUniversalSearchGame", displayTitleLocale: "en-US", pageArgs: { size: 5, offset: 0 } }); console.log(`\n${op} ${h.slice(0, 8)} → ${st} ${T(t, 400)}`); }
/* 3. other search endpoints */
for (const u of ["https://store.playstation.com/store/api/chihiro/00_09_000/tumbler/US/en/999/elden%20ring?suggested_size=5&mode=game",
  "https://store.playstation.com/valkyrie-api/en/US/999/search/elden%20ring?suggested_size=5&mode=game",
  "https://m.np.playstation.com/api/search/v1/universalSearch?searchTerm=elden%20ring&countryCode=US&languageCode=en&domainRequests=%5B%7B%22domain%22%3A%22ConceptGameMobileApp%22%2C%22pagination%22%3A%7B%22cursor%22%3A%22%22%2C%22pageSize%22%3A5%7D%7D%5D"]) {
  try { const r = await fetch(u, { headers: UA }); console.log(`\n${u.slice(0, 90)} → ${r.status} ${T(await r.text(), 700)}`); } catch (e) { console.log(u, "FAILED", e.message); }
}
/* 4. the pricing CTAs for a few concepts, compact */
for (const [cid, loc] of [["10002694", "en-US"], ["10001130", "en-US"], ["10001130", "en-AE"], ["10010645", "en-US"], ["10000176", "en-US"]]) {
  const [st, t] = await gql("metGetPricingDataByConceptId", H.metGetPricingDataByConceptId, { conceptId: cid }, loc);
  let j = {}; try { j = JSON.parse(t); } catch (e) {}
  const c = j.data && j.data.conceptRetrieve, dp = c && c.defaultProduct;
  console.log(`\n== concept ${cid} ${loc} → ${st} name=${c && c.name} product=${dp && dp.id}`);
  if (!c) { console.log(T(t, 300)); continue; }
  const walk = (o, f) => { if (o && typeof o === "object") { f(o); for (const k in o) walk(o[k], f); } };
  walk(c, o => { if (o.__typename === "GameCTA") console.log("  CTA", o.type, JSON.stringify(o.price && { b: o.price.basePrice, d: o.price.discountedPrice, bv: o.price.basePriceValue, dv: o.price.discountedValue, t: o.price.discountText, sub: o.price.isTiedToSubscription, br: o.price.serviceBranding, up: T(o.price.upsellText || "", 90), end: o.price.endTime, cur: o.price.currencyCode }), "tier", JSON.stringify(((o.action || {}).param || []).filter(p => /tier|membership/.test(p.name)).map(p => p.name + "=" + p.values))); });
  console.log("  keys:", Object.keys(c).join(","), "| dp keys:", dp ? Object.keys(dp).join(",") : "");
}
