/* Play next test kit: the pieces every harness used to rebuild from docs/LOG.md in each new chat (they lived in the
   scratchpad and were lost with the container). Serve the app, seed games with real-shaped pictures, catch the problem
   reports the app files about itself (its /bug calls), real touch through CDP, and frame timing.
   The commands in tools/pn.mjs are built from these; a one-off scenario is `node tools/pn.mjs run my.mjs`. */
import http from "http";
import fs from "fs";
import path from "path";
import os from "os";
import { execFileSync } from "child_process";
import { createRequire } from "module";
import { fileURLToPath } from "url";

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
export const CACHE = path.join(ROOT, "tools", ".cache");
export const OUT = path.join(ROOT, "tools", ".out");
const HELPER = "https://playnext-helper.hussamnabil48.workers.dev";

/* ---------- Playwright: the dev container has it globally (not in the repo); CI installs it locally ---------- */
export async function playwright() {
  const tries = [process.env.PW_PATH, path.join(ROOT, "node_modules")];
  try { tries.push(execFileSync("npm", ["root", "-g"], { encoding: "utf8" }).trim()); } catch (e) {}
  tries.push("/opt/node-tools/node_modules", "/opt/node22/lib/node_modules");
  for (const t of tries.filter(Boolean)) {
    try { const req = createRequire(path.join(t, "noop.js")); return req(t.endsWith("playwright") ? t : "playwright"); } catch (e) {}
  }
  throw new Error("Playwright not found (tried " + tries.filter(Boolean).join(", ") + ")");
}
/* Chromium lives at /opt/pw-browsers; never `playwright install` here */
export async function chromium(opts = {}) {
  const pw = await playwright();
  const args = opts.gpu ? ["--enable-gpu", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--enable-gpu-rasterization"] : [];
  return pw.chromium.launch({ args, ...(opts.launch || {}) });
}

export const appV = (file = "index.html") => +((fs.readFileSync(path.join(ROOT, file), "utf8").match(/const APP_V = "(\d+)"/) || [])[1] || 0);

/* ---------- devices: the owner's Fold (folded / unfolded), a narrow phone, the friend's iPhone 11 (Chromium stand-in;
   real Safari is webkit-check.yml), and the 900 width the tour is checked at ---------- */
export const DEVICES = {
  412: { width: 412, height: 915, dpr: 2.63 },
  859: { width: 859, height: 847, dpr: 2.63 },
  360: { width: 360, height: 780, dpr: 3 },
  414: { width: 414, height: 896, dpr: 2 },
  900: { width: 900, height: 860, dpr: 2 },
};
export const device = w => DEVICES[w] || { width: +w, height: Math.round(+w > 700 ? 860 : 900), dpr: 2.63 };

/* ---------- pictures: covers, heroes, key art, character art and logos drawn once on a canvas and cached
   (this container can't reach SteamGridDB / RAWG / Steam; the app needs real pixels to judge sharpness and sizes) ---------- */
const PICS = {
  cover: [600, 900, "jpeg"], hero: [1920, 620, "jpeg"], heroBig: [3840, 1240, "jpeg"], key: [1600, 900, "jpeg"],
  art: [600, 900, "png"], logoWide: [600, 100, "png"], logoTall: [240, 300, "png"], logoSq: [256, 256, "png"],
};
export async function pictures(browser, n = 14) {
  const dir = path.join(CACHE, "img");
  if (fs.existsSync(path.join(dir, `done-${n}`))) return dir;
  fs.mkdirSync(dir, { recursive: true });
  const page = await browser.newPage();
  for (let i = 0; i < n; i++) for (const [kind, [w, h, type]] of Object.entries(PICS)) {
    if (kind === "heroBig" && i > 3) continue;
    const data = await page.evaluate(([w, h, type, i, kind]) => {
      const c = document.createElement("canvas"); c.width = w; c.height = h; const x = c.getContext("2d");
      const hue = (i * 47) % 360;
      if (type === "jpeg") {
        const g = x.createLinearGradient(0, 0, w, h); g.addColorStop(0, `hsl(${hue},60%,35%)`); g.addColorStop(1, `hsl(${(hue + 80) % 360},55%,12%)`);
        x.fillStyle = g; x.fillRect(0, 0, w, h);
        for (let k = 0; k < 40; k++) { x.fillStyle = `hsla(${(hue + k * 9) % 360},70%,${30 + (k % 5) * 10}%,.35)`;
          x.beginPath(); x.arc((k * 97) % w, (k * 53) % h, 10 + (k * 13) % (h / 4), 0, 7); x.fill(); }
        x.fillStyle = "rgba(255,255,255,.85)"; x.font = `bold ${Math.round(h / 9)}px sans-serif`; x.fillText(kind + " " + i, w * 0.06, h * 0.9);
      } else if (kind === "art") {
        x.fillStyle = `hsl(${hue},65%,55%)`; x.beginPath(); x.ellipse(w / 2, h * 0.3, w * 0.18, h * 0.13, 0, 0, 7); x.fill();
        x.fillRect(w * 0.3, h * 0.42, w * 0.4, h * 0.55);
      } else {   /* logos keep see-through padding, like real SteamGridDB logos */
        x.fillStyle = `hsl(${hue},80%,70%)`; x.font = `900 ${Math.round(h * 0.45)}px sans-serif`; x.textAlign = "center"; x.textBaseline = "middle";
        x.fillText("LOGO" + i, w / 2, h / 2, w * 0.8);
      }
      return c.toDataURL("image/" + type, 0.86);
    }, [w, h, type, i, kind]);
    fs.writeFileSync(path.join(dir, `${kind}${i}.${type === "jpeg" ? "jpg" : "png"}`), Buffer.from(data.split(",")[1], "base64"));
  }
  await page.close();
  fs.writeFileSync(path.join(dir, `done-${n}`), "");
  return dir;
}

/* ---------- server: the repo root, /__img/ (the pictures above), /__old/ (another git ref's build, for A/B) ---------- */
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg",
  ".webp": "image/webp", ".svg": "image/svg+xml", ".webmanifest": "application/manifest+json", ".ico": "image/x-icon" };
export function serve({ oldRef } = {}) {
  let oldHtml = null;
  if (oldRef) oldHtml = execFileSync("git", ["show", `${oldRef}:index.html`], { cwd: ROOT, maxBuffer: 64 << 20 });
  const srv = http.createServer((q, res) => {
    let f = decodeURIComponent(q.url.split("?")[0]);
    if (oldHtml && (f === "/__old/" || f === "/__old/index.html")) { res.writeHead(200, { "content-type": "text/html" }); return res.end(oldHtml); }
    if (f.startsWith("/__old/")) f = f.slice(6);
    if (f === "/") f = "/index.html";
    const dir = f.startsWith("/__img/") ? path.join(CACHE, "img") : ROOT, p = path.join(dir, f.startsWith("/__img/") ? f.slice(7) : f);
    if (!p.startsWith(dir) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream", "access-control-allow-origin": "*" });
    fs.createReadStream(p).pipe(res);
  }).listen(0);
  const base = `http://localhost:${srv.address().port}/`;
  return { srv, base, url: old => base + (old ? "__old/" : ""), close: () => srv.close() };
}

/* ---------- seed: games in every state, scored Played games (≥3, so tour targets that need scores exist and a missing one
   is reported), a game with scores on its back (v234: the harness missed a bug because none had), pictures of every kind ---------- */
const TITLES = ["Starfall Odyssey", "Crimson Tactics", "Neon Drift", "Echoes of the Deep", "Moonlit Farm", "Iron Saga II", "Pixel Quest",
  "The Long Night", "Skyward Blades", "Ocean Tales", "Shadow Protocol", "Ember Knight", "Garden Story", "Rift Runner"];
export function seed({ base = "", v = appV(), big = false, games = 14, pics = true, extra = {} } = {}) {
  const day = 864e5, iso = d => new Date(Date.now() + d * day).toISOString().slice(0, 10), img = n => pics ? base + "__img/" + n : "";
  const list = TITLES.slice(0, games).map((title, i) => ({
    id: "g" + i, title, desc: "A long description of the game. ".repeat(6),
    img: img(`cover${i}.jpg`), key: i % 4 === 0 ? img(`key${i}.jpg`) : "", hero: i < 4 ? img(big ? `heroBig${i}.jpg` : `hero${i}.jpg`) : "",
    art: i % 3 === 0 ? [img(`art${i}.png`)] : [],
    colour: ["#4DA3FF", "#7C5CFF", "#3FD9A4", "#F0A63C", "#FF5470", "#5AE0FF"][i % 6],
    date: iso(i < 9 ? -400 + i * 30 : (i - 8) * 9), hours: 12 + i * 4, status: i < 9 ? "out" : "upcoming",
    state: i === 1 || i === 4 ? "playing" : i < 9 ? "backlog" : "upcoming", hype: 4 + (i % 6), ord: i + 1,
    platforms: ["PS5, PC", "Switch", "PS5", "PC", "Switch, PS5, PC", "PS4 & PS5"][i % 6], genres: ["RPG", "Action", "Strategy", "Adventure"].slice(0, 1 + (i % 3)),
    added: iso(-200 + i * 10),
    ...(i === 7 ? { mine: { story: 8, fun: 9, art: 7 }, learn: "Parry everything.", tips: ["Save often"] } : {}),
  }));
  const played = [0, 2, 3, 5, 6].filter(n => n < games).map((n, k) => ({ id: "g" + n, real: [9, 7.5, 6, 8.5, 5][k], t: iso(-(k + 1) * 30) }));
  return { games: list, played, duels: [], lastV: String(v), tourFrom: +v, tourRedo: 1, nxAsked: 1, userName: "Sam", fxSeen: 1, fxCal: 1,
    scoreMig: 1, gdMig: 1, pxOff: true, cbOn: false, ...extra };
}

/* ---------- open: a fresh context at a device size with touch, seeded storage (written once per tab, so the app's own
   pagehide save survives reloads), every outside request blocked except the helper's /bug, which is caught here ---------- */
export async function open(browser, { w = 412, base, old = false, state, keep = false, fake = {}, throttle = 0, wait = 2500, touch = true } = {}) {
  const d = device(w);
  const ctx = await browser.newContext({ viewport: { width: d.width, height: d.height }, deviceScaleFactor: d.dpr, hasTouch: touch, isMobile: touch, serviceWorkers: "block" });
  const reports = [], errors = [];
  await ctx.route(u => !u.href.startsWith(base), async r => {
    const u = r.request().url();
    if (u.startsWith(HELPER + "/bug") && r.request().method() === "POST") {
      try { reports.push(...(JSON.parse(r.request().postData() || "{}").reports || []).map(x => ({ ...x, t: Date.now() }))); } catch (e) {}
      return r.fulfill({ status: 200, contentType: "application/json", body: '{"ok":true}', headers: { "access-control-allow-origin": "*" } });
    }
    for (const [pat, fn] of Object.entries(fake)) if (u.includes(pat)) {
      const out = await fn(r.request());
      return r.fulfill({ status: out.status || 200, contentType: out.type || "application/json", body: typeof out.body === "string" ? out.body : JSON.stringify(out.body ?? out),
        headers: { "access-control-allow-origin": "*" } });
    }
    return r.abort();
  });
  if (state !== null) {
    const s = JSON.stringify(state || seed({ base }));
    await ctx.addInitScript(([s, keep]) => { if (keep || !sessionStorage.getItem("pn-seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("pn-seeded", "1"); } }, [s, keep]);
  }
  const page = await ctx.newPage();
  page.on("pageerror", e => errors.push(String(e && e.message || e)));
  page.on("console", m => { if (m.type() === "error" && !/Failed to load resource|net::|blocked|ERR_/i.test(m.text())) errors.push(m.text()); });
  const cdp = await ctx.newCDPSession(page);
  if (throttle > 1) await cdp.send("Emulation.setCPUThrottlingRate", { rate: throttle });
  await page.goto(base + (old ? "__old/" : ""));
  await page.waitForTimeout(wait);
  return { ctx, page, cdp, reports, errors, w: d.width, h: d.height, close: () => ctx.close() };
}

/* ---------- real touch (CDP Input.dispatchTouchEvent, v207: mouse clicks skip the app's touch paths) ---------- */
const pt = (x, y) => [{ x: Math.round(x), y: Math.round(y), radiusX: 4, radiusY: 4, force: 1, id: 1 }];
export async function tap(t, x, y) {
  await t.cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pt(x, y) });
  await new Promise(r => setTimeout(r, 40));
  await t.cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
}
export async function center(t, sel) {
  return t.page.evaluate(sel => { const el = typeof sel === "string" ? document.querySelector(sel) : null; if (!el) return null;
    el.scrollIntoView({ block: "nearest" }); const r = el.getBoundingClientRect(); return r.width ? { x: r.left + r.width / 2, y: r.top + r.height / 2 } : null; }, sel);
}
export async function tapEl(t, sel) { const c = await center(t, sel); if (!c) return false; await tap(t, c.x, c.y); return true; }
export async function swipe(t, x1, y1, x2, y2, ms = 220, steps = 12) {
  await t.cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pt(x1, y1) });
  for (let i = 1; i <= steps; i++) {
    await new Promise(r => setTimeout(r, ms / steps));
    await t.cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: pt(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps) });
  }
  await t.cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
}
/* a real finger scroll on the page's scroller (wheel events don't exist on a phone) */
export async function scroll(t, dy, { x } = {}) {
  const cx = x ?? t.w / 2, y0 = t.h * (dy > 0 ? 0.75 : 0.3);
  await swipe(t, cx, y0, cx, y0 - Math.max(-t.h * 0.5, Math.min(t.h * 0.5, dy)), 260, 14);
}

/* ---------- walking the app: every screen, a game page top / middle / bottom, Settings, Edit, the chat, the cards ---------- */
export const SCREENS = ["home", "queue", "lib", "played", "replay", "month", "flow", "plan", "timer", "psych", "duels", "patterns", "analytics", "friend", "add"];
const pause = ms => new Promise(r => setTimeout(r, ms));
export async function run(t, label, code, wait = 1200) {
  try { await t.page.evaluate(code); } catch (e) { t.errors.push(`${label}: ${String(e.message || e).split("\n")[0]}`); }
  await pause(wait);
}
/* scroll whatever scrolls on screen (the open page's own scroller, a sheet, else the window); programmatic, for sweeps */
export const scrollAny = (t, dy) => t.page.evaluate(dy => {
  const els = [...document.querySelectorAll("#full.on .hub, #full.on, .sheet.on, #sheet-in, main, .wrap")].filter(e => e.scrollHeight > e.clientHeight + 10 && e.getClientRects().length);
  (els[0] || document.scrollingElement).scrollBy(0, dy);
}, dy);
export async function walk(t, { shots = false, tag = "", screens = SCREENS, slow = 1 } = {}) {
  const shot = async n => { if (shots) { fs.mkdirSync(OUT, { recursive: true }); await t.page.screenshot({ path: path.join(OUT, `${tag || t.w}-${n}.png`) }); } };
  const s = ms => Math.round(ms * slow);
  for (const name of screens) {
    /* the layout check runs 1.6s after a screen change, when idle: let it finish before scrolling */
    await run(t, "goTab " + name, `goTab(${JSON.stringify(name)})`, s(2600));
    await shot(name);
    await scrollAny(t, 900); await pause(s(900));
    await scrollAny(t, -2000); await pause(s(300));
  }
  for (const [label, code, wait, n] of [
    ["game page", `openFull("g1")`, 1600, "game"],
    ["game middle", null, 900, "game-mid"],
    ["game bottom", null, 900, "game-end"],
    ["close game", `closeFull()`, 900],
    ["scored game", `openFull("g7")`, 1600, "game-scored"],
    ["close game", `closeFull()`, 900],
    ["settings", `document.querySelector("#gear") && document.querySelector("#gear").click()`, 1500, "settings"],
    ["close settings", `typeof closeEdit === "function" && closeEdit()`, 900],
    ["edit", `openEdit("g2")`, 1500, "edit"],
    ["close edit", `typeof closeEdit === "function" && closeEdit()`, 900],
    ["chat", `typeof nxChat === "function" && nxChat()`, 1200, "chat"],
    ["close chat", `typeof dyClose === "function" && dyClose()`, 700],
  ]) {
    if (code) await run(t, label, code, s(wait)); else { await scrollAny(t, label === "game middle" ? 900 : 6000); await pause(s(wait)); }
    if (n) await shot(n);
  }
  await run(t, "home", `goTab("home")`, s(1200));
}
/* the app batches reports (3s) and sends them when it can: give it the time, then ask it to send what's left */
export async function flush(t) {
  await run(t, "flush", `typeof bugFlush === "function" && bugFlush()`, 3500);
  await run(t, "flush again", `typeof bugFlush === "function" && bugFlush()`, 1500);
}

/* ---------- reading reports: problems vs timing (a headless box has no GPU, so "slow" is a hint) vs harness noise ---------- */
export function sortReports(reports) {
  const noise = r => r.kind === "helper" || /helper|offline|couldn't reach/i.test(r.msg || "");
  return {
    real: reports.filter(r => r.kind !== "slow" && r.kind !== "lag" && !noise(r)),
    timing: reports.filter(r => r.kind === "slow" || r.kind === "lag"),
    noise: reports.filter(r => noise(r) && r.kind !== "slow" && r.kind !== "lag"),
  };
}
export const line = r => `[${r.kind}] ${r.screen}: ${r.msg}${r.at ? " @ " + String(r.at).slice(0, 160) : ""}`;

/* ---------- frame timing: rAF gaps (frames over 33ms and the time beyond 16.7ms) + main-thread time from CDP ---------- */
export async function frames(t) {
  await t.page.evaluate(() => {
    const F = window.__pnF = { gaps: [], on: false, last: 0 };
    const tick = ts => { if (F.on && F.last) F.gaps.push(ts - F.last); F.last = ts; requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
    try { F.loaf = []; new PerformanceObserver(l => { if (F.on) F.loaf.push(...l.getEntries().map(e => e.duration)); }).observe({ type: "long-animation-frame", buffered: false }); } catch (e) {}
  });
  await t.cdp.send("Performance.enable");
}
const metric = async t => ((await t.cdp.send("Performance.getMetrics")).metrics.find(m => m.name === "TaskDuration") || {}).value || 0;
export async function measure(t, label, fn, settle = 1200) {
  const m0 = await metric(t);
  await t.page.evaluate(() => { const F = window.__pnF; F.gaps = []; F.loaf = []; F.last = 0; F.on = true; });
  await fn();
  await pause(settle);
  const r = await t.page.evaluate(() => { const F = window.__pnF; F.on = false; return { gaps: F.gaps, loaf: F.loaf || [] }; });
  const main = Math.round((await metric(t) - m0) * 1000);
  const over = r.gaps.filter(g => g > 33);
  return { label, main, worst: Math.round(Math.max(0, ...r.gaps)), over33: over.length, jank: Math.round(over.reduce((a, g) => a + g - 16.7, 0)), loafMax: Math.round(Math.max(0, ...r.loaf)) };
}
export const median = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.floor((s.length - 1) / 2)] : 0; };
