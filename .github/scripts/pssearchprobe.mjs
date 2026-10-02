/* PS Store search probe (v227), step 4: open the store's real search page in a headless browser and record the GraphQL
   requests it makes (operation name, variables, persisted-query hash) and what comes back, so the helper can make the same
   search request. Needs playwright (installed by the workflow step). */
import { chromium } from "playwright";
const Q = process.env.Q || "grand theft auto vi";
const b = await chromium.launch();
const pg = await b.newPage({ userAgent: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", locale: "en-US" });
pg.on("request", r => { const u = r.url(); if (/graphql/.test(u)) { const q = new URL(u); console.log("REQ", r.method(), q.searchParams.get("operationName"), "vars", q.searchParams.get("variables"), "ext", q.searchParams.get("extensions"), r.method() === "POST" ? "body " + String(r.postData()).slice(0, 600) : ""); console.log("   headers", JSON.stringify(Object.fromEntries(Object.entries(r.headers()).filter(([k]) => /^x-|apollo|content-type/i.test(k))))); } });
pg.on("response", async r => { const u = r.url(); if (/graphql/.test(u)) { const t = await r.text().catch(() => ""); console.log("RES", r.status(), new URL(u).searchParams.get("operationName"), t.length, "bytes:", t.slice(0, 1500).replace(/\s+/g, " ")); } });
for (const cc of ["en-us", "en-ae"]) {
  console.log(`\n===== ${cc} "${Q}" =====`);
  await pg.goto(`https://store.playstation.com/${cc}/search/${encodeURIComponent(Q)}`, { waitUntil: "networkidle", timeout: 60000 }).catch(e => console.log("goto", e.message));
  await pg.waitForTimeout(3000);
  const tiles = await pg.evaluate(() => [...document.querySelectorAll("a[href*='/concept/'], a[href*='/product/']")].slice(0, 8).map(a => a.getAttribute("href") + " | " + a.innerText.replace(/\s+/g, " ").slice(0, 80)));
  console.log("tiles:", tiles.join("\n       "));
}
await b.close();
