/* HowLongToBeat probe: how the site's own search works today (it has no public API and changes its search address now
   and then). Part 1 (plain fetch): the home page, its scripts and any "/api/" strings in them. Part 2 (BROWSER=1): open the
   real site in a headless browser, search, and record every /api/ request it makes (method, headers, body) and the answer.
   Part 3: run the helper's own /hours code against the real site. */
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36";
const Q = (process.env.Q || "Elden Ring|Persona 5 Royal|Metaphor: ReFantazio|Hollow Knight|Danganronpa: Trigger Happy Havoc|AI: The Somnium Files|Final Fantasy VII Rebirth|STEINS;GATE ELITE|The Apothecary Diaries: The False Imperial Brothers|GTA 6|Hades|The Witcher 3|Persona 3 Reload|Fire Emblem: Three Houses|Stardew Valley").split("|").map(s => s.trim()).filter(Boolean);
const base = "https://howlongtobeat.com";
const TO = () => AbortSignal.timeout(15000);
setTimeout(() => { console.log("probe: 6 min, giving up"); process.exit(0); }, 360000);
if (process.env.PLAIN) try {
  const r = await fetch(base + "/", { headers: { "User-Agent": UA, Referer: base + "/" }, signal: TO() });
  const html = await r.text();
  console.log("home", r.status, html.length, "bytes");
  const srcs = [...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map(m => m[1]);
  console.log("scripts:", srcs.join("\n  "));
  for (const s of srcs.filter(s => /_app|pages\/|chunks/.test(s)).slice(0, 30)) {
    const u = s.startsWith("http") ? s : base + s;
    const t = await (await fetch(u, { headers: { "User-Agent": UA, Referer: base + "/" }, signal: TO() })).text().catch(() => "");
    const hits = [...t.matchAll(/.{0,160}\/api\/.{0,220}/g)].map(m => m[0]);
    if (hits.length) { console.log("\n== " + s + " (" + t.length + ")"); hits.slice(0, 12).forEach(h => console.log("   " + h.replace(/\s+/g, " "))); }
  }
} catch (e) { console.log("plain fetch failed", e.message); }

if (process.env.BROWSER) {
  const { chromium } = await import("playwright");
  const b = await chromium.launch();
  const pg = await b.newPage({ userAgent: UA, locale: "en-US" });
  pg.setDefaultTimeout(30000);
  pg.on("request", r => { const u = r.url(); if (/\/api\//.test(u)) { console.log("REQ", r.method(), u); console.log("   headers", JSON.stringify(r.headers())); if (r.postData()) console.log("   body", String(r.postData()).slice(0, 1500)); } });
  pg.on("response", async r => { const u = r.url(); if (/\/api\//.test(u)) { const t = await r.text().catch(() => ""); console.log("RES", r.status(), u, t.length, "bytes:", t.slice(0, 2500).replace(/\s+/g, " ")); } });
  for (const q of Q.slice(0, 2)) {
    console.log(`\n===== browser "${q}" =====`);
    await pg.goto(`${base}/?q=${encodeURIComponent(q)}`, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(e => console.log("goto", e.message));
    await pg.waitForTimeout(8000);
    const tiles = await pg.evaluate(() => [...document.querySelectorAll("a[href*='/game/']")].slice(0, 6).map(a => a.getAttribute("href") + " | " + a.innerText.replace(/\s+/g, " ").slice(0, 120)));
    console.log("tiles:", tiles.join("\n       "));
  }
  await b.close();
}

/* the helper's own code */
try {
  const mod = await import("../../worker/helper.js");
  if (mod.hltbFind) {
    for (const q of Q) { const t0 = Date.now(); const x = await mod.hltbFind({}, q).catch(e => ({ error: e.message })); console.log("\nhelper hltbFind", JSON.stringify(q), Date.now() - t0 + "ms", JSON.stringify(x)); }
  } else console.log("\n(helper has no hltbFind yet)");
} catch (e) { console.log("helper import failed", e.message); }
