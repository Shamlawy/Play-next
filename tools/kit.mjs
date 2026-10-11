/* Play next test kit. Lives in the repo so every chat (and GitHub) runs the same checks; before this, each chat
   rebuilt its harness in a scratchpad that was lost at the end.

     node tools/kit.mjs syntax          every <script> block in index.html compiles (and sw.js); no press animation in the CSS
     node tools/kit.mjs sweep           every screen, a game page, Settings, Edit, chat at 412 / 859 / 360 wide in Chromium:
                                        script errors + every problem the app reports about itself (its own detectors)
     node tools/kit.mjs tour            the what's-new tour from the previous version: every step opens and finds its target
     node tools/kit.mjs perf            main-thread time for a fixed walk (4x slower CPU), to compare before/after a change
     node tools/kit.mjs sw              sw.js shrinks pictures right (.github/scripts/swcheck.mjs, in Chromium here; WebKit on GitHub)
     node tools/kit.mjs all             syntax + sw + sweep + tour
     node tools/kit.mjs run x.mjs       a saved scenario (tools/scenarios/): export default async ({ p, w, fail }) => …,
                                        with p.tap / p.swipe (real touch through CDP) beside step / scroll / flush

   Options: --file other.html (test another copy, e.g. the old build), --widths 412,859, --cpu 4,
   --set '{"bugOff":true}' (extra saved settings), --shots dir (tour: a screenshot of every step),
   --plant x.js (sweep: run this in every phone once it has loaded) with --expect (sweep fails unless the plant is reported:
   the proof a new detector catches its problem).
   Exit code 1 when something failed (script errors, a broken block, a real report, a tour step without its target).
   Timing reports ("slow") are printed but never fail: a headless machine has no GPU. */
import http from "http";
import fs from "fs";
import path from "path";
import vm from "vm";
import { createRequire } from "module";
import { spawnSync } from "child_process";
import { seed, TITLES } from "./seed.mjs";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const args = process.argv.slice(2), mode = args.find(a => !a.startsWith("--")) || "all";
const opt = (k, d) => { const i = args.indexOf("--" + k); return i >= 0 ? args[i + 1] : d; };
const FILE = opt("file", "index.html");
const SET = JSON.parse(opt("set", "{}"));
const PLANT = opt("plant") ? fs.readFileSync(opt("plant"), "utf8") : "", EXPECT = args.includes("--expect");   /* --set '{"bugOff":true}': extra saved settings for every phone */
const WIDTHS = opt("widths", "412,859,360").split(",").map(Number);
const SIZES = { 412: [412, 915, 2.625], 859: [859, 847, 2.63], 360: [360, 780, 3], 375: [375, 812, 3], 900: [900, 1000, 2] };
const html = fs.readFileSync(path.join(ROOT, FILE), "utf8");
const APP_V = (html.match(/const APP_V = "(\d+)"/) || [])[1] || "1";
let failed = 0;
const fail = m => { failed++; console.log("  ✗ " + m); };

/* ---------- syntax ---------- */
function syntax() {
  console.log(`\n== syntax (${FILE}, v${APP_V}) ==`);
  const re = /<script(\s[^>]*)?>([\s\S]*?)<\/script>/g; let m, n = 0;
  while ((m = re.exec(html))) {
    if (m[1] && /\bsrc=/.test(m[1])) continue;
    n++;
    try { new vm.Script(m[2], { filename: `${FILE}:${html.slice(0, m.index).split("\n").length}` }); }
    catch (e) { fail(`script block at line ${html.slice(0, m.index).split("\n").length}: ${e.message}`); }
  }
  /* house rule (#309, v246): nothing moves when touched. No :active rule may change transform/scale/translate/rotate/filter/opacity */
  const MOVE = /^(transform|scale|translate|rotate|filter|opacity)$/;
  for (const st of html.matchAll(/<style(?:\s[^>]*)?>([\s\S]*?)<\/style>/g)) {
    const css = st[1].replace(/\/\*[\s\S]*?\*\//g, c => " ".repeat(c.length));
    for (const r of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      if (!r[1].includes(":active")) continue;
      const bad = r[2].split(";").map(d => d.split(":")[0].trim()).filter(k => MOVE.test(k));
      if (bad.length) fail(`press animation (${bad.join(", ")}) in "${r[1].trim().slice(0, 80)}" at line ${html.slice(0, st.index + st[0].indexOf(st[1]) + r.index).split("\n").length}`);
    }
  }
  for (const f of ["sw.js"]) try { new vm.Script(fs.readFileSync(path.join(ROOT, f), "utf8")); } catch (e) { fail(`${f}: ${e.message}`); }
  console.log(`  ${n} script blocks checked`);
}

/* ---------- browser ---------- */
async function pw() {
  for (const p of [process.env.PW_PATH, "playwright", "/opt/node22/lib/node_modules/playwright"]) {
    if (!p) continue;
    try { return createRequire(import.meta.url)(p); } catch (e) {}
  }
  throw new Error("Playwright not found (npm i --no-save playwright)");
}
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml", ".webmanifest": "application/json" };
function serve() {
  const srv = http.createServer((q, res) => {
    let f = decodeURIComponent(q.url.split("?")[0]); if (f === "/" || f === "/index.html") f = "/" + FILE;
    const p = path.join(ROOT, f);
    if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream" }); fs.createReadStream(p).pipe(res);
  }).listen(0);
  return { srv, base: `http://localhost:${srv.address().port}/` };
}
async function launch() {
  const { chromium } = await pw();
  const exe = ["/opt/pw-browsers/chromium/chrome-linux/chrome", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"].find(f => fs.existsSync(f));
  /* no browser swipe-back: a real finger swiping right on a game page otherwise "goes back" to about:blank (the installed
     app has no browser history gesture) */
  const args = ["--overscroll-history-navigation=0", "--disable-features=OverscrollHistoryNavigation,TouchpadOverscrollHistoryNavigation"];
  return chromium.launch(exe && !process.env.CI ? { executablePath: exe, args } : { args });
}
/* a fresh phone: seeded data, the helper's /bug caught here, every other site blocked (pictures, APIs) */
async function phone(browser, base, w, data) {
  const [vw, vh, dpr] = SIZES[w] || [w, Math.round(w * 2.1), 2.6];
  const ctx = await browser.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: dpr, isMobile: true, hasTouch: true, serviceWorkers: "block",
    userAgent: "Mozilla/5.0 (Linux; Android 16; SM-F9800) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Mobile Safari/537.36" });
  const reports = [], errors = [];
  await ctx.route(u => !u.href.startsWith(base), async r => {
    const u = r.request().url();
    if (/\/bug$/.test(u) && r.request().method() === "POST") {
      try { reports.push(...(JSON.parse(r.request().postData() || "{}").reports || [])); } catch (e) {}
      return r.fulfill({ status: 200, contentType: "application/json", body: '{"ok":true}', headers: { "access-control-allow-origin": "*" } });
    }
    return r.abort();
  });
  data = { ...data, ...SET };
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("seeded", "1"); } }, JSON.stringify(data));
  const page = await ctx.newPage();
  page.on("pageerror", e => errors.push(String(e && e.message || e).split("\n")[0]));
  page.on("console", m => { if (m.type() === "error" && !/Failed to load resource|net::|blocked|ERR_/i.test(m.text())) errors.push(m.text().split("\n")[0]); });
  await page.goto(base); await page.waitForTimeout(2500);
  if (PLANT) await page.evaluate(PLANT).catch(e => errors.push("plant: " + String(e.message || e).split("\n")[0]));
  const step = async (label, fn, wait = 1200) => {
    try { await page.evaluate(fn); } catch (e) { errors.push(`${label}: ${String(e.message || e).split("\n")[0]}`); }
    await page.waitForTimeout(wait);
  };
  /* scrolls whatever scrolls on screen (the open page's own scroller, else the window) */
  const scroll = dy => page.evaluate(dy => {
    const els = [...document.querySelectorAll("#full.on .hub, #full.on, .sheet.on, #sheet-in, main, .wrap")].filter(e => e.scrollHeight > e.clientHeight + 10 && e.getClientRects().length);
    (els[0] || document.scrollingElement).scrollBy(0, dy);
  }, dy).catch(() => {});
  const flush = () => step("flush", `typeof bugFlush === "function" && bugFlush()`, 1500);
  /* real touch (v207: mouse clicks skip the app's touch paths) */
  const cdp = await ctx.newCDPSession(page);
  const pt = (x, y) => [{ x: Math.round(x), y: Math.round(y), radiusX: 4, radiusY: 4, force: 1, id: 1 }];
  const tap = async (x, y) => { await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pt(x, y) }); await page.waitForTimeout(40);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] }); };
  const swipe = async (x1, y1, x2, y2, ms = 220, n = 12) => {
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pt(x1, y1) });
    for (let i = 1; i <= n; i++) { await page.waitForTimeout(ms / n); await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: pt(x1 + (x2 - x1) * i / n, y1 + (y2 - y1) * i / n) }); }
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  };
  return { ctx, page, cdp, reports, errors, step, scroll, flush, tap, swipe, w: vw, h: vh };
}
const SCREENS = ["home", "queue", "lib", "played", "replay", "month", "flow", "plan", "psych", "duels", "friend", "add"];
const line = r => `[${r.kind}] ${r.screen}: ${r.msg}${r.at ? " @ " + String(r.at).slice(0, 140) : ""}`;
function verdict(name, p) {
  const real = p.reports.filter(r => r.kind !== "slow"), slow = p.reports.filter(r => r.kind === "slow");
  const errs = [...new Set(p.errors)];
  if (errs.length) errs.forEach(e => fail(`${name}: script error: ${e}`));
  if (real.length) real.forEach(r => fail(`${name}: ${line(r)}`));
  if (slow.length) console.log(`  · ${name}: ${slow.length} timing note(s) (no GPU here, a hint only):\n      ` + slow.map(line).join("\n      "));
  if (!errs.length && !real.length) console.log(`  ✓ ${name}: no script errors, nothing reported`);
}

/* ---------- sweep ---------- */
async function sweep(browser, base) {
  console.log(`\n== sweep (${FILE}, v${APP_V}) at ${WIDTHS.join(", ")} ==`);
  /* one width at a time: parallel runs starve the CPU and the app's lag checks report it */
  for (const w of WIDTHS) {
    const p = await phone(browser, base, w, seed(APP_V));
    for (const s of SCREENS) {
      /* the layout check runs 1.6s after a screen change, when idle: leave sooner and most of it is missed
         (a planted cut-off button was reported on 3 screens with 1.4s here, on 11 with 2.6s) */
      await p.step("goTab " + s, `goTab(${JSON.stringify(s)})`, 2600);
      await p.scroll(900); await p.page.waitForTimeout(700);
      await p.scroll(-900); await p.page.waitForTimeout(400);
    }
    for (const id of ["g1", "g9", "g5"]) {
      await p.step("game page " + id, `openFull(${JSON.stringify(id)})`, 1600);
      await p.scroll(700); await p.page.waitForTimeout(700);
      await p.step("close game", `closeFull()`, 700);
    }
    await p.step("settings", `openSettings()`, 1500); await p.scroll(1200); await p.page.waitForTimeout(500);
    await p.step("close settings", `typeof closeEdit === "function" && closeEdit()`, 700);
    await p.step("edit", `openEdit("g2")`, 1400); await p.scroll(900); await p.page.waitForTimeout(500);
    await p.step("close edit", `typeof closeEdit === "function" && closeEdit()`, 700);
    await p.step("chat", `typeof nxChat === "function" && nxChat()`, 1200);
    await p.step("close chat", `typeof dyClose === "function" && dyClose()`, 600);
    await p.step("home", `goTab("home")`, 1500);
    await p.flush(); await p.flush();
    if (EXPECT) {
      const real = p.reports.filter(r => r.kind !== "slow");
      if (real.length) console.log(`  ✓ ${w}px: the plant was reported (${real.length}):\n      ` + real.map(line).join("\n      "));
      else fail(`${w}px: the plant was NOT reported`);
    } else verdict(`${w}px`, p);
    await p.ctx.close();
  }
}

/* ---------- tour ---------- */
async function tour(browser, base) {
  const vs = [...html.matchAll(/\bv:\s*(\d{3})\b/g)].map(m => +m[1]).filter(v => v <= +APP_V);
  const prev = Math.max(...vs.filter(v => v < +APP_V), +APP_V - 1);
  console.log(`\n== tour v${prev} → v${APP_V} at ${WIDTHS.slice(0, 2).join(", ")} ==`);
  for (const w of WIDTHS.slice(0, 2)) {
    const d = seed(APP_V); d.lastV = String(prev); d.tourFrom = prev; delete d.tourAt;
    const p = await phone(browser, base, w, d);
    const seen = [];
    for (let k = 0; k < 40; k++) {
      await p.page.waitForTimeout(1300);
      const st = await p.page.evaluate(() => { const c = document.querySelector("#nxtour.on"); if (!c) return null;
        const ring = document.querySelector("#nxring.on"); return { head: c.querySelector("small")?.textContent || "", n: c.querySelector("i")?.textContent || "", ring: !!ring }; });
      if (!st) { if (k === 0) { await p.page.waitForTimeout(2500); continue; } break; }
      seen.push(`${st.n} ${st.head.split("·")[0].trim()}${st.ring ? "" : " (no target)"}`);
      if (opt("shots")) { fs.mkdirSync(opt("shots"), { recursive: true }); await p.page.screenshot({ path: path.join(opt("shots"), `tour-${w}-${seen.length}.png`) }); }
      const more = await p.page.evaluate(() => { const b = document.querySelector('#nxtour [data-nx="next"]'); const last = !document.querySelector('#nxtour [data-nx="skip"]'); b && b.click(); return !last; });
      if (!more) break;
    }
    await p.flush();
    if (!seen.length) fail(`${w}px: the tour never showed`);
    else console.log(`  ${w}px: ${seen.length} step(s): ${seen.join(" | ")}`);
    verdict(`tour ${w}px`, p);
    await p.ctx.close();
  }
}

/* ---------- perf ---------- */
async function perf(browser, base) {
  const cpu = +opt("cpu", 4), w = WIDTHS[0];
  console.log(`\n== perf (${FILE}, v${APP_V}) at ${w}px, CPU ${cpu}x slower ==`);
  const p = await phone(browser, base, w, seed(APP_V, 60));
  const cdp = await p.ctx.newCDPSession(p.page);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  await cdp.send("Performance.enable");
  const metric = async () => Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map(m => [m.name, m.value]));
  const rows = [];
  const measure = async (label, fn, wait = 1500) => {
    const a = await metric(); await p.step(label, fn, wait); const b = await metric();
    rows.push([label, Math.round((b.ScriptDuration - a.ScriptDuration) * 1000), Math.round((b.LayoutDuration - a.LayoutDuration) * 1000), Math.round((b.RecalcStyleDuration - a.RecalcStyleDuration) * 1000), Math.round((b.TaskDuration - a.TaskDuration) * 1000)]);
  };
  for (const s of ["queue", "lib", "played", "plan", "psych", "home"]) await measure(s, `goTab(${JSON.stringify(s)})`);
  await measure("open game", `openFull("g1")`); await measure("close game", `closeFull()`, 800);
  await measure("settings", `openSettings()`); await measure("close", `closeEdit()`, 800);
  const m = await metric();
  console.log("  step          script  layout  style   all (ms)");
  for (const r of rows) console.log("  " + r[0].padEnd(12) + r.slice(1).map(x => String(x).padStart(7)).join(" "));
  const sum = i => rows.reduce((a, r) => a + r[i], 0);
  console.log("  " + "TOTAL".padEnd(12) + [1, 2, 3, 4].map(i => String(sum(i)).padStart(7)).join(" "));
  console.log(`  JS heap ${Math.round(m.JSHeapUsedSize / 1048576)} MB, DOM nodes ${m.Nodes}, listeners ${m.JSEventListeners}`);
  await p.ctx.close();
}

/* ---------- sw.js picture shrinking ---------- */
function sw() {
  console.log("\n== sw.js picture shrink (Chromium) ==");
  const pwDir = [process.env.PW_PATH, "/opt/node22/lib/node_modules/playwright/index.mjs"].find(p => p && fs.existsSync(p));
  const r = spawnSync(process.execPath, [path.join(ROOT, ".github/scripts/swcheck.mjs")], { cwd: ROOT, encoding: "utf8",
    env: { ...process.env, BROWSER: "chromium", ...(pwDir ? { PW_PATH: pwDir } : {}) } });
  const out = (r.stdout + r.stderr).trim().split("\n").map(l => "  " + l).join("\n");
  console.log(out);
  if (r.status !== 0) fail("swcheck failed");
}

if (mode === "syntax" || mode === "all") syntax();
if (mode === "sw" || mode === "all") sw();
/* ---------- a saved scenario ---------- */
async function scenario(browser, base) {
  const file = args.find((a, i) => i > 0 && !a.startsWith("--") && args[i - 1] !== "--file" && args[i - 1] !== "--widths" && args[i - 1] !== "--plant" && args[i - 1] !== "--set");
  if (!file) return fail("usage: node tools/kit.mjs run tools/scenarios/x.mjs [--widths 412]");
  const fn = (await import(path.resolve(file))).default;
  for (const w of opt("widths") ? WIDTHS : [412]) {
    const p = await phone(browser, base, w, seed(APP_V));
    console.log(`\n== ${path.basename(file)} at ${w}px ==`);
    if ((await fn({ p, w, fail, seed })) === false) fail(`${path.basename(file)} at ${w}px`);
    await p.ctx.close();
  }
}

if (["sweep", "tour", "perf", "all", "run"].includes(mode)) {
  const { srv, base } = serve(); const browser = await launch();
  try {
    if (mode === "sweep" || mode === "all") await sweep(browser, base);
    if (mode === "tour" || mode === "all") await tour(browser, base);
    if (mode === "perf") await perf(browser, base);
    if (mode === "run") await scenario(browser, base);
  } finally { await browser.close(); srv.close(); }
}
console.log(failed ? `\n${failed} problem(s).` : "\nAll clear.");
process.exit(failed ? 1 : 0);
