/* Parity sheets (docs/ANDROID-PLAN.md gate 4): the web app and the Android app side by side, same made-up games, same
   clock, same width, with a difference picture. The app's side is its Roborazzi baseline
   (android/app/src/test/screenshots/<shot>-<width>.png, ScreensTest); the web side is index.html in Chromium with that
   test's library (read from ScreensTest.kt, so the two never drift), the shot's extra settings, UTC and 2026-10-27 08:00 UTC,
   at the same width and height and 3x pixels (xxhdpi).
     node tools/parity-sheet.mjs [queue-412 queue-859 …]   (default: every shot in SHOTS)
   Writes tools/.cache/parity/<shot>.png (web | app | difference, the difference red where pixels part by more than 48 of
   255 on any channel) and prints the share of pixels that differ. A number, not a pass/fail: Claude reads every sheet. */
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { seed } from "./seed.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const PAGE = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
const APP_V = (/const APP_V = "(\d+)"/.exec(PAGE) || [])[1];
const SCREENS = path.join(ROOT, "android/app/src/test/java/io/github/shamlawy/playnext/ui/ScreensTest.kt");
const BASE = path.join(ROOT, "android/app/src/test/screenshots");
const OUT = path.join(ROOT, "tools/.cache/parity");

/* Each shot: the screen to open on the web, and the extra settings its ScreensTest test adds to lib(…). */
const QUEUE = `closeFull(); goTab("games"); goSub("games", "queue");`;
export const SHOTS = {
  "queue-412": { open: QUEUE, extra: { deckOff: true, sort: "hype", qFilter: "all" } },
  "queue-859": { open: QUEUE, extra: { deckOff: true } },
};

/** ScreensTest's made-up library (the JSON inside `private fun lib(...)`), without the test's `$extra`. */
export function screensLibrary(src = fs.readFileSync(SCREENS, "utf8")) {
  const m = /private fun lib\([^)]*\)\s*=\s*Library\(\s*Json\.parseToJsonElement\(\s*"""([\s\S]*?)"""/.exec(src);
  if (!m) throw new Error("parity-sheet: ScreensTest.kt has no lib(...) JSON");
  return JSON.parse(m[1].replace("$extra", ""));
}

/** The Roborazzi shot's height in dp, as ScreensTest's shot() picks it (915 folded, 1000 unfolded). */
const heightOf = w => (w < 600 ? 915 : 1000);

function pw() {
  for (const p of [process.env.PW_PATH, "playwright", "/opt/node22/lib/node_modules/playwright"]) {
    if (!p) continue;
    try { return createRequire(import.meta.url)(p); } catch (e) {}
  }
  throw new Error("Playwright not found (npm i --no-save playwright)");
}

const TYPES = { ".html": "text/html", ".js": "text/javascript", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp",
  ".svg": "image/svg+xml", ".webmanifest": "application/manifest+json", ".woff2": "font/woff2", ".ttf": "font/ttf" };
function serve() {
  const srv = http.createServer((q, r) => {
    let f = decodeURIComponent(new URL(q.url, "http://x").pathname);
    if (f === "/") f = "/index.html";
    const p = path.join(ROOT, f);
    if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { r.writeHead(404); return r.end(); }
    r.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream" });
    fs.createReadStream(p).pipe(r);
  });
  return new Promise(res => srv.listen(0, "127.0.0.1", () => res(srv)));
}

async function webShot(browser, base, name, shot, lib) {
  const width = +name.split("-").pop(), height = heightOf(width);
  const ctx = await browser.newContext({ timezoneId: "UTC", locale: "en-US", viewport: { width, height }, deviceScaleFactor: 3, serviceWorkers: "block" });
  await ctx.route(u => !u.href.startsWith(base) && !/^(blob|data):/.test(u.href), r => r.abort());
  await ctx.clock.setFixedTime(new Date("2026-10-27T08:00:00Z"));
  const start = { ...seed(APP_V, 1, { bare: true }), bugOff: true, ...lib, ...shot.extra };
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("seeded", "1"); } }, JSON.stringify(start));
  const page = await ctx.newPage();
  page.setDefaultNavigationTimeout(120000);
  await page.goto(base, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => typeof goTab === "function" && typeof S === "object" && S.games.length > 0, null, { timeout: 30000 });
  await page.evaluate(js => { (0, eval)(js); }, shot.open);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(1800); // the screen's entrance and the header card settle
  const png = await page.screenshot({ type: "png" });
  await ctx.close();
  return png;
}

/** Draws web | app | difference into one picture in the browser; returns it with the share of pixels that differ. */
async function sheet(browser, name, web, app) {
  const page = await (await browser.newContext({ viewport: { width: 400, height: 300 } })).newPage();
  const res = await page.evaluate(async ([name, w, a]) => {
    const load = src => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = no; i.src = src; });
    const [wi, ai] = await Promise.all([load(w), load(a)]);
    const W = Math.max(wi.width, ai.width), H = Math.max(wi.height, ai.height), top = 90, gap = 30;
    const px = (img) => { const c = new OffscreenCanvas(W, H), x = c.getContext("2d"); x.fillStyle = "#000"; x.fillRect(0, 0, W, H); x.drawImage(img, 0, 0); return x.getImageData(0, 0, W, H); };
    const A = px(wi), B = px(ai), D = new ImageData(W, H);
    let off = 0;
    for (let i = 0; i < A.data.length; i += 4) {
      const far = Math.max(Math.abs(A.data[i] - B.data[i]), Math.abs(A.data[i + 1] - B.data[i + 1]), Math.abs(A.data[i + 2] - B.data[i + 2])) > 48;
      const g = (A.data[i] + A.data[i + 1] + A.data[i + 2]) / 9;
      D.data[i] = far ? 255 : g; D.data[i + 1] = far ? 40 : g; D.data[i + 2] = far ? 60 : g; D.data[i + 3] = 255;
      if (far) off++;
    }
    const c = document.createElement("canvas"); c.width = W * 3 + gap * 4; c.height = H + top + gap;
    const x = c.getContext("2d"); x.fillStyle = "#111"; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = "#fff"; x.font = "600 44px sans-serif";
    const share = off / (W * H);
    [["Web app", A], ["Android app", B], [`Different: ${(share * 100).toFixed(1)}%`, D]].forEach(([t, img], k) => {
      const left = gap + k * (W + gap);
      x.fillText(t, left, 60); x.putImageData(img, left, top);
    });
    return { share, url: c.toDataURL("image/png"), W, H };
  }, [name, "data:image/png;base64," + web.toString("base64"), "data:image/png;base64," + app.toString("base64")]);
  await page.context().close();
  return { share: res.share, png: Buffer.from(res.url.split(",")[1], "base64"), size: `${res.W}x${res.H}` };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const names = process.argv.slice(2).filter(a => !a.startsWith("--"));
  const todo = names.length ? names : Object.keys(SHOTS);
  for (const n of todo) if (!SHOTS[n]) { console.error(`parity-sheet: no shot "${n}" (have ${Object.keys(SHOTS).join(", ")})`); process.exit(2); }
  const lib = screensLibrary();
  const srv = await serve(), base = `http://127.0.0.1:${srv.address().port}/`;
  const { chromium } = pw();
  const exe = ["/opt/pw-browsers/chromium/chrome-linux/chrome"].find(f => !process.env.CI && fs.existsSync(f));
  const browser = await chromium.launch(exe ? { executablePath: exe } : {});
  fs.mkdirSync(OUT, { recursive: true });
  try {
    for (const n of todo) {
      const appPng = path.join(BASE, `${n}.png`);
      if (!fs.existsSync(appPng)) { console.error(`parity-sheet: ${path.relative(ROOT, appPng)} is missing (record the Roborazzi shot first)`); process.exitCode = 1; continue; }
      const web = await webShot(browser, base, n, SHOTS[n], lib);
      fs.writeFileSync(path.join(OUT, `${n}-web.png`), web);
      const s = await sheet(browser, n, web, fs.readFileSync(appPng));
      fs.writeFileSync(path.join(OUT, `${n}.png`), s.png);
      console.log(`parity-sheet: ${n} ${s.size} · ${(s.share * 100).toFixed(1)}% of pixels differ → ${path.relative(ROOT, path.join(OUT, n + ".png"))}`);
    }
  } finally { await browser.close(); srv.close(); }
}
