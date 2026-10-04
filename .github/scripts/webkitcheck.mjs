/* WebKit check: the friend uses an iPhone 11 and an iPad (Safari), and this repo's dev container only has Chromium.
   On GitHub this opens the app in Playwright's WebKit with the iPhone 11 and iPad sizes, walks every screen, a game page,
   Settings and Edit, and prints (1) script errors and (2) the problems the app reports about itself (its /bug calls are caught
   here instead of going to the helper). Screenshots go to the "webkit" artifact. Fails when a script error happens. */
import http from "http";
import fs from "fs";
import path from "path";

/* PW_PATH / CHROMIUM: to dry-run the script in the dev container (Chromium only, same steps) */
const { webkit, chromium, devices } = await import(process.env.PW_PATH || "playwright");
const ROOT = process.cwd(), OUT = "webkit";
fs.mkdirSync(OUT, { recursive: true });
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".webmanifest": "application/json" };
const srv = http.createServer((q, res) => {
  let f = decodeURIComponent(q.url.split("?")[0]); if (f === "/") f = "/index.html";
  const p = path.join(ROOT, f); if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream" }); fs.createReadStream(p).pipe(res);
}).listen(0);
const port = srv.address().port, BASE = `http://localhost:${port}/`;
const APP_V = (fs.readFileSync("index.html", "utf8").match(/const APP_V = "(\d+)"/) || [])[1] || "1";

const day = 864e5, iso = d => new Date(Date.now() + d * day).toISOString().slice(0, 10);
const ART = ["fefw-key-art.jpg", "logo-512.png", "FEFT_cast.webp"];
const TITLES = ["Starfall Odyssey", "Crimson Tactics", "Neon Drift", "Echoes of the Deep", "Moonlit Farm", "Iron Saga II", "Pixel Quest", "The Long Night",
  "Skyward Blades", "Ocean Tales", "Shadow Protocol", "Ember Knight", "Garden Story", "Rift Runner"];
function seed() {
  const games = TITLES.map((title, i) => ({ id: "g" + i, title, desc: "A long description of the game. ".repeat(6), img: ART[i % 3], key: i % 4 === 0 ? ART[0] : "",
    colour: ["#4DA3FF", "#7C5CFF", "#3FD9A4", "#F0A63C", "#FF5470", "#5AE0FF"][i % 6], date: iso(i < 9 ? -400 + i * 30 : (i - 8) * 9), hours: 12 + i * 4,
    status: i < 9 ? "out" : "upcoming", state: i === 1 || i === 4 ? "playing" : "backlog", hype: 4 + (i % 6), ord: i + 1,
    platforms: ["PS5, PC", "Switch", "PS5", "PC", "Switch, PS5, PC", "PS4 & PS5"][i % 6], genres: ["RPG", "Action", "Strategy", "Adventure"].slice(0, 1 + (i % 3)),
    added: iso(-200 + i * 10) }));
  const played = [0, 2, 3, 5, 6].map((n, k) => ({ id: "g" + n, real: [9, 7.5, 6, 8.5, 5][k], t: iso(-(k + 1) * 30) }));
  return { games, played, duels: [], lastV: APP_V, tourFrom: +APP_V, tourRedo: 1, nxAsked: 1, userName: "Sam", fxSeen: 1, fxCal: 1, scoreMig: 1, gdMig: 1, pxOff: true, cbOn: false };
}

/* mobile WebKit has no mouse wheel: scroll whatever is scrollable on screen (the open page's own scroller, else the window) */
const scrollBy = (page, dy) => page.evaluate(dy => {
  const els = [...document.querySelectorAll("#full.on .hub, #full.on, .sheet.on, #sheet-in, main, .wrap")].filter(e => e.scrollHeight > e.clientHeight + 10 && e.getClientRects().length);
  (els[0] || document.scrollingElement).scrollBy(0, dy);
}, dy);
const RUNS = [["iPhone 11", devices["iPhone 11"]], ["iPad", devices["iPad (gen 7)"]], ["iPad landscape", devices["iPad (gen 7) landscape"]]];
const SCREENS = ["home", "queue", "lib", "played", "replay", "month", "flow", "plan", "psych", "duels", "friend", "add"];
let failed = 0;
const launch = () => process.env.CHROMIUM ? chromium.launch({ executablePath: process.env.CHROMIUM }) : webkit.launch();
let browser = await launch();
async function walk(name, dev) {
  const ctx = await browser.newContext({ ...dev, serviceWorkers: "block" });
  const reports = [], errors = [];
  await ctx.route(u => !u.href.startsWith(BASE), async r => {
    const u = r.request().url();
    if (/\/bug$/.test(u) && r.request().method() === "POST") { try { reports.push(...(JSON.parse(r.request().postData() || "{}").reports || [])); } catch (e) {} return r.fulfill({ status: 200, contentType: "application/json", body: '{"ok":true}', headers: { "access-control-allow-origin": "*" } }); }
    return r.abort();
  });
  const S = JSON.stringify(seed());
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("seeded", "1"); } }, S);
  const page = await ctx.newPage();
  page.on("pageerror", e => errors.push(String(e && e.message || e)));
  page.on("console", m => { if (m.type() === "error" && !/Failed to load resource|net::|blocked/i.test(m.text())) errors.push(m.text()); });
  await page.goto(BASE); await page.waitForTimeout(2500);
  const tag = name.replace(/\s+/g, "-").toLowerCase();
  const step = async (label, fn, wait = 1200) => {
    try { await page.evaluate(fn); } catch (e) { errors.push(`${label}: ${String(e.message || e).split("\n")[0]}`); }
    await page.waitForTimeout(wait);
  };
  /* two walks: the first without screenshots (a screenshot forces a full repaint that the app's lag check reports as a
     stutter), collecting what the app reports; the second only takes the screenshots */
  let shots = false;
  const shot = async n => { if (shots) await page.screenshot({ path: `${OUT}/${tag}-${n}.png` }); };
  for (const pass of [0, 1]) {
    shots = !!pass;
    for (const s of SCREENS) {
      await step("goTab " + s, `goTab(${JSON.stringify(s)})`, pass ? 700 : 1600);
      await shot(s);
      await scrollBy(page, 900); await page.waitForTimeout(pass ? 300 : 900);
    }
    await step("game page", `openFull("g1")`, 1600); await shot("game");
    await scrollBy(page, 700); await page.waitForTimeout(800); await shot("game-scrolled");
    await step("close game", `closeFull()`, 800);
    await step("settings", `(document.querySelector("#gear") || {}).click && document.querySelector("#gear").click()`, 1500); await shot("settings");
    await page.keyboard.press("Escape"); await page.waitForTimeout(400);
    await step("close settings", `document.querySelectorAll(".sheet.on .x, #sheet.on .x, [data-close]").forEach(b => b.offsetParent && b.click())`, 800);
    await step("edit", `openEdit("g2")`, 1500); await shot("edit");
    await step("close edit", `typeof closeEdit === "function" && closeEdit()`, 800);
    await step("chat", `typeof nxChat === "function" && nxChat()`, 1200); await shot("chat");
    await step("close chat", `typeof dyClose === "function" && dyClose()`, 600);
    if (!pass) { await step("flush", `goTab("home"); typeof bugFlush === "function" && bugFlush()`, 4000); await step("flush again", `typeof bugFlush === "function" && bugFlush()`, 1500); reports.splice(0, 0, ...reports.splice(0).map(r => ({ ...r, kept: 1 }))); }
  }
  console.log(`\n===== ${name} (${dev.viewport.width}×${dev.viewport.height} @${dev.deviceScaleFactor}x) =====`);
  console.log(errors.length ? "SCRIPT ERRORS:\n  " + [...new Set(errors)].join("\n  ") : "No script errors.");
  const kept = reports.filter(r => r.kept), line = r => `[${r.kind}] ${r.screen}: ${r.msg}${r.at ? " @ " + r.at : ""}`;
  const real = kept.filter(r => r.kind !== "slow"), slow = kept.filter(r => r.kind === "slow");
  console.log(real.length ? "The app reported:\n  " + real.map(line).join("\n  ") : "The app reported nothing (besides timing).");
  if (slow.length) console.log("Timing reports (a CI machine has no GPU, so these are only a hint):\n  " + slow.map(line).join("\n  "));
  if (errors.length) failed++;
  await ctx.close();
}
/* Playwright's WebKit page sometimes dies mid-walk on GitHub's runners ("Target page, context or browser has been closed";
   983e68c, v231 and 2ba6dd7 each passed on a re-run). Say so, and walk that device once more; a second crash fails. */
for (const [name, dev] of RUNS) {
  for (let tries = 1; ; tries++) {
    try { await walk(name, dev); break; }
    catch (e) {
      const msg = String(e && e.message || e).split("\n")[0];
      if (!/has been closed|crash/i.test(msg)) throw e;
      console.log(`\n===== ${name}: the WebKit page crashed (${msg}) — try ${tries} of 2 =====`);
      if (!browser.isConnected()) browser = await launch();
      if (tries >= 2) { failed++; break; }
    }
  }
}
await browser.close(); srv.close();
if (failed) { console.log(`\n${failed} device(s) had script errors or crashed twice.`); process.exit(1); }
