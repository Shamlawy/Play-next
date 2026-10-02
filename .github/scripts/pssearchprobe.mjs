/* PS Store search probe (v227), step 3: the website's scripts hold no persisted-query hashes, so find how its search
   request is built: every "search"/"persisted"/"sha256"/"graphql" spot in the search page's own scripts, with context. */
const UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US,en" };
const Q = process.env.Q || "grand theft auto vi";
const base = "https://store.playstation.com";
const html = await (await fetch(`${base}/en-us/search/${encodeURIComponent(Q)}`, { headers: UA })).text();
let srcs = [...new Set([...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => m[1]))].map(s => s.startsWith("http") ? s : base + s);
const seen = new Set();
for (const u of srcs) {
  const t = await (await fetch(u, { headers: UA })).text().catch(() => "");
  const name = u.split("/").slice(-1)[0].slice(0, 40);
  const hits = [];
  for (const re of [/persistedQuery/g, /sha256/gi, /operationName/g, /getSearchResults|universalSearch|SearchResults|searchTerm/g, /graphql\/v1/g, /createPersistedQuery|generateHash/g]) {
    for (const m of t.matchAll(re)) { const k = name + Math.floor(m.index / 400); if (seen.has(k)) continue; seen.add(k); hits.push([m[0], t.slice(Math.max(0, m.index - 220), m.index + 280).replace(/\s+/g, " ")]); if (hits.length > 14) break; }
  }
  if (hits.length) { console.log(`\n##### ${name} (${t.length} bytes)`); hits.slice(0, 14).forEach(([w, c]) => console.log(`  [${w}] …${c}…`)); }
}
