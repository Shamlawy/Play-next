/* PS Store probe 4: search (chihiro tumbler) → product id → GraphQL product pricing; dump shapes compactly. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const T = (s, n = 1200) => String(s).slice(0, n);
const H = { metGetProductById: "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a", metGetConceptById: "cc90404ac049d935afbd9968aef523da2b6723abfb9d586e5f77ebf7c5289006",
  metGetPricingDataByConceptId: "abcb311ea830e679fe2b697a27f755764535d825b24510ab1239a4ca3092bd09" };
const gql = async (op, vars, loc = "en-US") => {
  const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${op}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: H[op] } }))}`;
  const r = await fetch(u, { headers: { ...UA, "x-psn-store-locale-override": loc, "content-type": "application/json" } });
  const t = await r.text(); let j = null; try { j = JSON.parse(t); } catch (e) {} return [r.status, t, j];
};
const walk = (o, f, p = "") => { if (o && typeof o === "object") { f(o, p); for (const k in o) walk(o[k], f, p + "." + k); } };
const ctas = (j) => { const out = []; walk(j, (o, p) => { if (o.__typename === "GameCTA") out.push(`${o.type} ${JSON.stringify(o.price && { b: o.price.basePrice, d: o.price.discountedPrice, bv: o.price.basePriceValue, dv: o.price.discountedValue, t: o.price.discountText, sub: o.price.isTiedToSubscription, br: o.price.serviceBranding, end: o.price.endTime, up: T(o.price.upsellText || "", 70) })} tier=${(((o.action || {}).param || []).find(x => x.name === "tierNumber") || {}).values} sku=${(((o.action || {}).param || []).find(x => x.name === "skuId") || {}).values} @${p.slice(-60)}`); }); return out; };
for (const [cc, q] of [["US", "returnal"], ["US", "elden ring"], ["AE", "elden ring"], ["US", "ghost of yotei"], ["US", "stellar blade"], ["AE", "persona 3 reload"]]) {
  const r = await fetch(`https://store.playstation.com/store/api/chihiro/00_09_000/tumbler/${cc}/en/999/${encodeURIComponent(q)}?suggested_size=8&mode=game`, { headers: UA });
  const j = await r.json().catch(() => ({}));
  console.log(`\n#### tumbler ${cc} "${q}" → ${r.status}`);
  for (const l of (j.links || []).slice(0, 8)) console.log(`  ${l.container_type} ${l.bucket} ${l.id} | ${l.name} | ${(l.default_sku || {}).display_price} | plat ${JSON.stringify(l.playable_platform)} | sku rewards ${JSON.stringify(((l.default_sku || {}).rewards || []).map(x => ({ d: x.discount, p: x.display_price, plus: x.isPlus, bonus: x.bonus_display_price, end: x.end_date })))} | top-level keys ${Object.keys(l).slice(0, 30).join(",")}`);
  const pid = ((j.links || []).find(l => l.container_type === "product") || {}).id;
  if (!pid) continue;
  const [st, t, pj] = await gql("metGetProductById", { productId: pid }, "en-" + cc);
  const pr = pj && pj.data && pj.data.productRetrieve;
  console.log(`  metGetProductById ${pid} → ${st} keys ${pr ? Object.keys(pr).join(",") : T(t, 300)}`);
  if (pr) {
    console.log("   name", pr.name, "| concept", JSON.stringify(pr.concept && { id: pr.concept.id }), "| price", JSON.stringify(pr.price));
    ctas(pj).slice(0, 10).forEach(x => console.log("   CTA", x));
    const cid = pr.concept && pr.concept.id;
    if (cid) { const [s2, t2, cj] = await gql("metGetPricingDataByConceptId", { conceptId: cid }, "en-" + cc); console.log(`  pricing by concept ${cid} → ${s2}`); ctas(cj).slice(0, 8).forEach(x => console.log("   CTA", x)); }
  }
  /* legacy product container */
  const lr = await fetch(`https://store.playstation.com/store/api/chihiro/00_09_000/container/${cc}/en/999/${pid}`, { headers: UA });
  const lj = await lr.json().catch(() => null);
  console.log(`  chihiro container → ${lr.status} name=${lj && lj.name} default_sku=${T(JSON.stringify(lj && lj.default_sku && { price: lj.default_sku.price, display_price: lj.default_sku.display_price, rewards: lj.default_sku.rewards, eligibilities: (lj.default_sku.eligibilities || []).length }), 700)}`);
}
