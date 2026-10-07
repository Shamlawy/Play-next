/* TEMPORARY probe (branch probe/restore-webkit, not for main): does "Restore from a code" open its sheet on iPad/iPhone WebKit? */
import http from "http"; import fs from "fs"; import path from "path";
const { webkit, devices } = await import("playwright");
const ROOT = process.cwd(); fs.mkdirSync("webkit", { recursive: true });
const srv = http.createServer((q, res) => { let f = decodeURIComponent(q.url.split("?")[0]); if (f === "/") f = "/index.html";
  const p = path.join(ROOT, f); if (!fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "content-type": f.endsWith(".html") ? "text/html" : "application/octet-stream" }); fs.createReadStream(p).pipe(res); }).listen(0);
const BASE = `http://localhost:${srv.address().port}/`;
const games = []; for (let i = 0; i < 8; i++) games.push({ id: "g" + i, title: "Game " + i, state: "backlog", hype: 6, genres: ["RPG"] });
const SEED = JSON.stringify({ games, played: [], lastV: "999", tourFrom: 999, tourRedo: 1, nxAsked: 1, fxSeen: 1, fxCal: 1, scoreMig: 1, gdMig: 1, pxOff: true, cbOn: false, bugOff: true, reviewOff: true });
const browser = await webkit.launch();
for (const [name, dev] of [["iPad", devices["iPad (gen 7)"]], ["iPad landscape", devices["iPad (gen 7) landscape"]], ["iPhone 11", devices["iPhone 11"]]]) {
  const ctx = await browser.newContext({ ...dev, serviceWorkers: "block" });
  await ctx.route(u => !u.href.startsWith(BASE), r => r.abort());
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("s")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("s", "1"); } }, SEED);
  const page = await ctx.newPage(); const errs = []; page.on("pageerror", e => errs.push(e.message));
  await page.addInitScript(() => { window.__EV = []; try { new PerformanceObserver(l => l.getEntries().forEach(e => __EV.push(e.name + ":" + Math.round(e.duration)))).observe({ type: "event", durationThreshold: 16 }); } catch (e) { window.__EV.push("no event timing"); } });
  await page.goto(BASE); await page.waitForTimeout(3000);
  await page.evaluate(() => setJump({ scope: "app", group: "Cloud backup", text: "Restore" })); await page.waitForTimeout(1500);
  const btn = await page.$("#s-cbrestore"); const out = { btn: !!btn };
  for (let k = 1; k <= 2; k++) {
    if (btn && await btn.isVisible()) { await btn.scrollIntoViewIfNeeded(); const bb = await btn.boundingBox(); const t0 = Date.now(); await page.touchscreen.tap(bb.x + bb.width / 2, bb.y + bb.height / 2); out["tap" + k + "ms"] = Date.now() - t0; }
    await page.waitForTimeout(1200);
    out["after" + k] = await page.evaluate(() => { const d = document.querySelector("#diary"), inp = d && d.querySelector(".cb-in"), cs = d && getComputedStyle(d);
      const r = inp && inp.getBoundingClientRect(); const t = r && document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      const rb = document.querySelector("#s-cbrestore").getBoundingClientRect(); const t2 = document.elementFromPoint(rb.left + rb.width / 2, rb.top + rb.height / 2);
      return { diaryOn: !!(d && d.classList.contains("on")), diaryOpacity: cs && cs.opacity, diaryZ: cs && cs.zIndex, input: !!inp, inputRect: r && [r.left, r.top, r.width, r.height].map(Math.round), atInput: t ? (t.closest("#diary") ? "diary" : t.closest("#sheet") ? "SHEET" : t.tagName) : "none", atRestoreBtn: t2 ? (t2.closest("#diary") ? "diary" : t2.closest("#s-cbrestore") ? "RESTORE BTN" : t2.tagName + "." + String(t2.className).slice(0, 30)) : "none", active: document.activeElement && (document.activeElement.className || document.activeElement.tagName), ev: (window.__EV || []).slice(-6) }; });
    await page.screenshot({ path: `webkit/${name.replace(/\s+/g, "-")}-tap${k}.png` });
  }
  console.log(`\n===== ${name} =====\n` + JSON.stringify(out, null, 1) + (errs.length ? "\nERRORS: " + errs.join(" | ") : ""));
  await ctx.close();
}
await browser.close(); srv.close();
