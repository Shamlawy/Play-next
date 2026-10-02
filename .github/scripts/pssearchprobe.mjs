/* PS Store search probe (v227): the old "tumbler" search misses newer games (GTA VI, FF VII Rebirth). Find how the store's
   own website searches: collect every script it can load (page scripts + the Next.js build manifest's chunks), list every
   GraphQL operation name that sits next to a persisted-query hash, then try the search ones. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const Q = process.env.Q || "grand theft auto vi";
const PS_ID = /[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/g;
const base = "https://store.playstation.com";
const html = await (await fetch(`${base}/en-us/search/${encodeURIComponent(Q)}`, { headers: UA })).text();
const nd = /<script id="__NEXT_DATA__"[^>]*>([^<]+)</.exec(html);
if (nd) { const j = JSON.parse(nd[1]); console.log("NEXT_DATA buildId", j.buildId, "page", j.page, "query", JSON.stringify(j.query), "props keys", Object.keys(j.props || {}).join(","), "pageProps", Object.keys((j.props || {}).pageProps || {}).join(",")); globalThis.BID = j.buildId; globalThis.ND = j; }
let srcs = [...new Set([...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => m[1]))].map(s => s.startsWith("http") ? s : base + s);
console.log("page scripts:", srcs.map(s => s.split("/").slice(-2).join("/")).join(" "));
const man = srcs.find(s => /_buildManifest\.js/.test(s)) || (globalThis.BID ? `${srcs[0].split("/_next/")[0]}/_next/static/${globalThis.BID}/_buildManifest.js` : "");
if (man) {
  const mt = await (await fetch(man, { headers: UA })).text().catch(() => "");
  console.log("build manifest", man, mt.length, "bytes; pages:", [...new Set([...mt.matchAll(/"(\/[^"]*)":\[/g)].map(m => m[1]))].join(" ").slice(0, 800));
  const root = man.split("/_next/")[0] + "/_next/";
  for (const m of mt.matchAll(/"(static\/chunks\/[^"]+\.js)"/g)) srcs.push(root + m[1]);
}
srcs = [...new Set(srcs)];
console.log("scripts to read:", srcs.length);
const pairs = new Map(); let total = 0;
for (const u of srcs) {
  const t = await (await fetch(u, { headers: UA })).text().catch(() => ""); total += t.length;
  for (const m of t.matchAll(/([0-9a-f]{64})/g)) {
    const around = t.slice(Math.max(0, m.index - 260), m.index + 330);
    const names = [...around.matchAll(/["'`]((?:met|get|query|search|wca|store)?[A-Za-z]*(?:Search|search|Query|Retrieve|Product|Concept|Category)[A-Za-z]*)["'`]/g)].map(x => x[1]);
    const k = m[1]; if (!pairs.has(k)) pairs.set(k, { names: new Set(), file: u.split("/").pop() });
    names.forEach(n => pairs.get(k).names.add(n));
  }
}
console.log("read", total, "bytes; hashes found:", pairs.size);
for (const [h, v] of pairs) if (v.names.size) console.log("  ", h.slice(0, 16), [...v.names].slice(0, 6).join(","), "in", v.file);
/* try search-looking ones */
const tries = [...pairs].filter(([, v]) => [...v.names].some(n => /search/i.test(n)));
for (const [hash, v] of tries) for (const name of [...v.names].filter(n => /search/i.test(n)).slice(0, 3)) {
  for (const vars of [{ searchTerm: Q, searchContext: "MobileUniversalSearchGame", displayTitleLocale: "en-US" }, { searchTerm: Q, searchContext: "MobileUniversalSearchGame" }, { searchTerm: Q }, { query: Q }, { searchTerm: Q, pageArgs: { size: 10, offset: 0 } }]) {
    const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${name}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: hash } }))}`;
    const r = await fetch(u, { headers: { ...UA, "x-psn-store-locale-override": "en-US", "content-type": "application/json" } });
    const t = await r.text(); const ids = [...new Set(t.match(PS_ID) || [])];
    console.log(`  try ${name} ${hash.slice(0, 8)} vars=${JSON.stringify(vars).slice(0, 90)} → ${r.status} ${t.length}b ids=${ids.slice(0, 4).join(" ")} :: ${t.slice(0, 260).replace(/\s+/g, " ")}`);
    if (ids.length) { console.log("    names:", [...new Set([...t.matchAll(/"name":"([^"]{2,80})"/g)].map(m => m[1]))].slice(0, 12).join(" | ")); console.log("    concepts:", [...new Set([...t.matchAll(/"id":"(\d{5,9})"/g)].map(m => m[1]))].slice(0, 8).join(" ")); break; }
  }
}
