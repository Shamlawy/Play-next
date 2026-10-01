/* PS Store probe 2: the search page is drawn in the browser, so find the store's GraphQL operations (persisted-query
   hashes live in its JS bundles), call them, and print what comes back: search results, prices, PS Plus info. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const T = s => String(s).slice(0, 1800);
const page = await (await fetch("https://store.playstation.com/en-us/search/elden%20ring", { headers: UA })).text();
console.log("NEXT_DATA:", /__NEXT_DATA__/.test(page), "apollo:", /apolloState|__APOLLO/.test(page));
const nd = (page.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
console.log("NEXT_DATA head:", T(nd));
const srcs = [...page.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => new URL(m[1], "https://store.playstation.com/").href);
console.log("scripts:", srcs.length, srcs.slice(0, 40).join("\n  "));
const ops = {};
for (const s of srcs.slice(0, 60)) {
  try {
    const js = await (await fetch(s, { headers: UA })).text();
    for (const m of js.matchAll(/([A-Za-z]{4,60})["']?\s*[:,]\s*["']?([a-f0-9]{64})["']?/g)) ops[m[1]] = m[2];
    for (const m of js.matchAll(/["']([a-f0-9]{64})["']\s*[:,]\s*["']?([A-Za-z]{4,60})/g)) ops[m[2]] = m[1];
    for (const m of js.matchAll(/operationName:\s*["']([A-Za-z]+)["'][^;]{0,300}?sha256Hash:\s*["']([a-f0-9]{64})/g)) ops[m[1]] = m[2];
  } catch (e) {}
}
console.log("ops found:", JSON.stringify(ops, null, 1).slice(0, 6000));
const KNOWN = { metGetProductById: "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a", metGetConceptById: "cc90404ac049d935afbd9968aef523da2b6723abfb9d586e5f77ebf7c5289006",
  metGetPricingDataByConceptId: "abcb311ea830e679fe2b697a27f755764535d825b24510ab1239a4ca3092bd09", categoryGridRetrieve: "4ce7d410a4db2c8b635a48c1dcec375906ff63b19dadd87e073f8fd0c0481d35" };
const H = Object.assign({}, KNOWN, ops);
const gql = async (op, vars, loc = "en-US") => {
  const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${op}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: H[op] } }))}`;
  const r = await fetch(u, { headers: { ...UA, "x-psn-store-locale-override": loc, "content-type": "application/json", "apollographql-client-name": "@sie-ppr-web-store/app" } });
  const t = await r.text(); console.log(`\n### ${op} ${loc} → ${r.status} ${t.length}B\n${T(t)}`); return t;
};
const searchOps = Object.keys(H).filter(k => /search/i.test(k));
console.log("search ops:", searchOps);
for (const op of searchOps.slice(0, 6)) {
  await gql(op, { searchTerm: "elden ring", searchContext: "MobileUniversalSearchGame", displayTitleLocale: "en-US", pageArgs: { size: 5, offset: 0 }, countryCode: "US", languageCode: "en", nextCursor: "" });
}
/* find a concept/product id for known games through the search results if one worked, else known ids */
let txt = "";
for (const op of searchOps) { try { txt += await gql(op, { searchTerm: "ghost of tsushima", searchContext: "MobileUniversalSearchGame", displayTitleLocale: "en-US", pageArgs: { size: 5, offset: 0 }, countryCode: "US", languageCode: "en", nextCursor: "" }); } catch (e) {} }
const pid = (txt.match(/[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/) || [])[0] || "UP9000-PPSA01712_00-GHOSTOFTSUSHIMA0";
const cid = (txt.match(/"concept":\{[^}]*?"id":"(\d+)"/) || txt.match(/"conceptId":"?(\d+)/) || [])[1] || "10002694";
console.log("\nusing product", pid, "concept", cid);
await gql("metGetProductById", { productId: pid });
await gql("metGetConceptById", { conceptId: cid, productId: pid });
await gql("metGetPricingDataByConceptId", { conceptId: cid });
await gql("metGetPricingDataByConceptId", { conceptId: cid }, "en-AE");
/* product page HTML (server-rendered?) */
const ph = await (await fetch(`https://store.playstation.com/en-us/product/${pid}`, { headers: UA })).text();
console.log("\nproduct page", ph.length, "basePriceValue×", (ph.match(/basePriceValue/g) || []).length, "NEXT_DATA", /__NEXT_DATA__/.test(ph));
for (const m of [...ph.matchAll(/\{[^{}]*"basePriceValue"[^{}]*\}/g)].slice(0, 6)) console.log("  price obj:", m[0]);
const ch = await (await fetch(`https://store.playstation.com/en-us/concept/${cid}`, { headers: UA })).text();
console.log("\nconcept page", ch.length, "basePriceValue×", (ch.match(/basePriceValue/g) || []).length);
for (const m of [...ch.matchAll(/\{[^{}]*"basePriceValue"[^{}]*\}/g)].slice(0, 6)) console.log("  price obj:", m[0]);
