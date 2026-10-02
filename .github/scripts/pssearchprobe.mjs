/* PS Store search probe (v227): the old "tumbler" search misses newer games (GTA VI, FF VII Rebirth). Find how the store's
   own website searches: read the search page, its script bundles, and look for the GraphQL search operation + its hash. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const Q = process.env.Q || "grand theft auto vi";
const PS_ID = /[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/g;
const page = await fetch(`https://store.playstation.com/en-us/search/${encodeURIComponent(Q)}`, { headers: UA });
const html = await page.text();
console.log("search page", page.status, html.length, "bytes");
console.log("product ids in html:", [...new Set(html.match(PS_ID) || [])].slice(0, 10).join(" "));
console.log("concept ids in html:", [...new Set((html.match(/concept\/(\d+)/g) || []))].slice(0, 10).join(" "));
for (const m of html.matchAll(/"__typename":"(\w+)"/g)) {}
const types = {}; for (const m of html.matchAll(/"__typename":"(\w+)"/g)) types[m[1]] = (types[m[1]] || 0) + 1;
console.log("typenames in html:", JSON.stringify(types).slice(0, 600));
const ix = html.indexOf("Grand Theft Auto VI"); console.log("GTA VI text at", ix, ix >= 0 ? JSON.stringify(html.slice(Math.max(0, ix - 300), ix + 300)) : "");
const ops = new Set([...html.matchAll(/operationName[\\"]*[:=][\\"]*(\w+)/g)].map(m => m[1])); console.log("ops in html:", [...ops].join(" "));
const srcs = [...new Set([...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => m[1]))];
console.log("scripts:", srcs.length);
const found = {};
for (const s of srcs) {
  const u = s.startsWith("http") ? s : new URL(s, "https://store.playstation.com").href;
  const t = await (await fetch(u, { headers: UA })).text().catch(() => "");
  for (const m of t.matchAll(/([a-zA-Z]*[Ss]earch[a-zA-Z]*)[^]{0,400}?([0-9a-f]{64})/g)) { const k = m[1] + " " + m[2]; if (!found[k]) { found[k] = u.split("/").pop(); } }
  for (const m of t.matchAll(/([0-9a-f]{64})[^]{0,300}?(\w*[Ss]earch\w*)/g)) { const k = m[2] + " " + m[1] + " (hash first)"; if (!found[k]) found[k] = u.split("/").pop(); }
}
console.log("search-ish names near hashes:"); Object.entries(found).slice(0, 40).forEach(([k, v]) => console.log("  ", k, "in", v));
/* try each candidate as a GraphQL persisted query with a few variable shapes */
const tried = new Set();
for (const k of Object.keys(found)) {
  const [name, hash] = k.split(" "); if (tried.has(name + hash) || !/search/i.test(name)) continue; tried.add(name + hash);
  for (const vars of [{ searchTerm: Q, searchContext: "MobileUniversalSearchGame", displayTitleLocale: "en-US" }, { searchTerm: Q }, { query: Q }, { searchTerm: Q, pageArgs: { size: 10, offset: 0 } }]) {
    const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${name}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: hash } }))}`;
    const r = await fetch(u, { headers: { ...UA, "x-psn-store-locale-override": "en-US", "content-type": "application/json" } });
    const t = await r.text();
    const ids = [...new Set(t.match(PS_ID) || [])];
    console.log(`  try ${name} ${hash.slice(0, 8)} vars=${Object.keys(vars).join(",")} → ${r.status} ${t.length}b ids=${ids.slice(0, 4).join(" ")} ${t.slice(0, 220).replace(/\s+/g, " ")}`);
    if (ids.length) { const names = [...t.matchAll(/"name":"([^"]{2,80})"/g)].map(m => m[1]); console.log("    names:", [...new Set(names)].slice(0, 12).join(" | ")); break; }
  }
}
