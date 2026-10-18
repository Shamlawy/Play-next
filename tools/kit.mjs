/* Play next test kit. Lives in the repo so every chat (and GitHub) runs the same checks; before this, each chat
   rebuilt its harness in a scratchpad that was lost at the end.

     node tools/kit.mjs syntax          every <script> block in index.html compiles (and sw.js);
                                        main not already at this branch's new APP_V (L27); no press animation or sticky :hover in the CSS;
                                        no new wrap of goTab/goSub/openFull/openSettings (use onNav/onSettings)
     node tools/kit.mjs sweep           every screen, a game page, Settings, Edit, chat at 412 / 859 / 360 wide in Chromium:
                                        script errors + every problem the app reports about itself (its own detectors)
     node tools/kit.mjs tour            the what's-new tour from the previous version: every step opens and finds its target
     node tools/kit.mjs perf            main-thread time for a fixed walk (4x slower CPU), to compare before/after a change
     node tools/kit.mjs smooth          mid-swipe (Queue, Library, a game page) Nexi's and the rail's loops keep running, GPU only (v282)
     node tools/kit.mjs sw              sw.js shrinks pictures right (.github/scripts/swcheck.mjs, in Chromium here; WebKit on GitHub)
     node tools/kit.mjs audit           code health (tools/audit.mjs --check): nothing piled up past tools/audit-budget.txt
     node tools/kit.mjs layers          GPU layers per screen (Home, Queue, Library, Played, a game page) at 412 / 859, in screens'
                                        worth: fails over tools/layers-budget.txt (--lower writes the new numbers) (v287)
     node tools/kit.mjs eye             the design eye (tools/eye.mjs) on every screen, 3 game pages and Settings at 412 / 859 / 360:
                                        repeated things that spill, overlap, run off the screen, break short words, squash
                                        pictures or barely show; tools/eye-ok.txt excuses what's meant to look that way
     node tools/kit.mjs taste           the owner's look calls that boxes can hold (tools/scenarios/taste.mjs, each named by its
                                        taste.md id: header cards, one game page skeleton, one main action, pick list pictures,
                                        drawn group icons, the open Queue card's corners, colour and logo slot) at 412 / 859
     node tools/kit.mjs all             syntax + audit + sw + sweep + tour + smooth + layers + eye + taste
     node tools/kit.mjs look            what this branch changes on screen against main: before | after | where pictures of each
                                        changed screen and a contact sheet of every screen (tools/.cache/look), for Claude to read
                                        (--base <ref>, --out dir, --same: fail on any change)
     node tools/kit.mjs samecss --before old.html   the browser applies exactly the same CSS (after a CSS tidy)
     node tools/kit.mjs run x.mjs       a saved scenario (tools/scenarios/): export default async ({ p, w, fail }) => …,
                                        with p.tap / p.swipe (real touch through CDP) beside step / scroll / flush
                                        --browser webkit: the same in Safari's engine (no p.tap / p.swipe; this container has
                                        only Chromium, so on GitHub: webkit-check.yml with its "scenario" input)

   Options: --file other.html (test another copy, e.g. the old build), --widths 412,859, --cpu 4,
   --set '{"bugOff":true}' (extra saved settings), --shots dir (tour: a screenshot of every step),
   --plant x.js (sweep: run this in every phone once it has loaded) with --expect (sweep fails unless the plant is reported:
   the proof a new detector catches its problem); --match <regex> (repeatable): with --expect, a report line
   ("[kind] screen: msg @ at") must match each one, so an unrelated report can't pass for the plant;
   --screens queue,lib,game,settings,edit,chat (sweep: only these; default every one). Every plant at once: tools/plants/run.mjs.
   Exit code 1 when something failed (script errors, a broken block, a real report, a tour step without its target).
   Timing reports ("slow") are printed but never fail: a headless machine has no GPU. */
import http from "http";
import fs from "fs";
import path from "path";
import vm from "vm";
import { createRequire } from "module";
import { fileURLToPath, pathToFileURL } from "url";
import { spawnSync } from "child_process";
import { seed, TITLES } from "./seed.mjs";
import { countLayers, LAYER_SCREENS } from "./layercount.mjs";
import { fakeHelper, HELPER_HOST } from "./fake-helper.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2), mode = args.find(a => !a.startsWith("--")) || "all";
const opt = (k, d) => { const i = args.indexOf("--" + k); return i >= 0 ? args[i + 1] : d; };
const FILE = opt("file", "index.html");
const SET = JSON.parse(opt("set", "{}"));
const PLANT = opt("plant") ? fs.readFileSync(opt("plant"), "utf8") : "", EXPECT = args.includes("--expect");   /* --set '{"bugOff":true}': extra saved settings for every phone */
const MATCH = args.flatMap((a, i) => a === "--match" && args[i + 1] ? [new RegExp(args[i + 1], "i")] : []);
const WIDTHS = opt("widths", "412,859,360").split(",").map(Number);
/* --browser webkit: Safari's engine, for the friend's iPhone 11 / iPad (on GitHub: webkit-check.yml's "scenario" input; this
   container only has Chromium). No CDP there, so p.tap / p.swipe and perf need Chromium */
const BROWSER = opt("browser", "chromium"), WK = BROWSER === "webkit";
/* FAKE_HELPER=0: block the helper like every other site (the empty states), instead of tools/fake-helper.mjs's answers */
const FAKE = process.env.FAKE_HELPER !== "0", STANDIN_PIC = fs.readFileSync(path.join(ROOT, "FEFT_cast.webp"));
const SIZES = { 412: [412, 915, 2.625], 859: [859, 847, 2.63], 360: [360, 780, 3], 375: [375, 812, 3], 900: [900, 1000, 2],
  414: [414, 896, 2], 810: [810, 1080, 2], 1080: [1080, 810, 2] };   /* 414 = iPhone 11, 810/1080 = iPad 9th gen upright / sideways */
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
  /* L27 (×5): another chat shipped the same version number to main while this branch was built, found only at the merge.
     The branch raised APP_V but main is already at that number or past it → take the next one (fetches main first; skipped offline) */
  if (FILE === "index.html") {
    const g = (...a) => spawnSync("git", a, { cwd: ROOT, encoding: "utf8", timeout: 15000 });
    g("fetch", "-q", "origin", "main");
    const vOf = ref => ((g("show", ref + ":index.html").stdout || "").match(/const APP_V = "(\d+)"/) || [])[1];
    const base = (g("merge-base", "HEAD", "origin/main").stdout || "").trim(), mainV = vOf("origin/main"), baseV = base && vOf(base);
    if (mainV && baseV && +APP_V > +baseV && +mainV >= +APP_V) fail(`main already shipped v${mainV} while this branch raised APP_V to ${APP_V}: merge main and take v${+mainV + 1}`);
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
  /* house rule (v280, the owner: "remove the touch glow and any touch effects when I touch an empty spot"): phones keep
     :hover on whatever was touched last (a Queue row stayed grey after a scroll started on it, the gear stayed lit), so a
     :hover look only applies with a real mouse: inside @media (hover: hover) */
  for (const st of html.matchAll(/<style(?:\s[^>]*)?>([\s\S]*?)<\/style>/g)) {
    const css = st[1].replace(/\/\*[\s\S]*?\*\//g, c => " ".repeat(c.length)), open = [];
    let from = 0;
    for (let i = 0; i < css.length; i++) {
      if (css[i] === "{") { open.push(css.slice(from, i)); from = i + 1; }
      else if (css[i] === "}") { const pre = open.pop() || ""; from = i + 1;
        if (/:hover\b/.test(pre) && !/^\s*@/.test(pre) && !open.some(o => /@media[^{]*\(\s*hover\s*:\s*hover\s*\)/.test(o)))
          fail(`sticky touch look: "${pre.trim().slice(0, 80)}" at line ${html.slice(0, st.index + st[0].indexOf(st[1]) + i).split("\n").length} needs @media (hover: hover) and (pointer: fine) { … }`); }
      else if (css[i] === ";" && !open.length) from = i + 1;
    }
  }
  /* house rule (v273/v276): one still background behind every screen: <html>'s base colour (var(--bgb)) and the
     html.paper::before glow. Any other background on html/body or their ::before/::after is a second layer (v257's live
     background, v274's art backdrop, the old stripes) */
  for (const st of html.matchAll(/<style(?:\s[^>]*)?>([\s\S]*?)<\/style>/g)) {
    const css = st[1].replace(/\/\*[\s\S]*?\*\//g, c => " ".repeat(c.length));
    for (const r of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      const bg = r[2].split(";").map(d => d.split(":")).filter(([k, ...v]) => /^\s*background(-image)?\s*$/.test(k) && !/^\s*(none|transparent)\s*(!important)?\s*$/.test(v.join(":")));
      if (!bg.length) continue;
      for (const sel of r[1].split(",").map(x => x.trim()).filter(x => /^(html|body)([.:\[][^\s>+~]*)?$/.test(x)))
        if (!(sel === "html.paper::before" || (sel === "html" && bg.every(([, ...v]) => /^\s*var\(--bgb/.test(v.join(":"))))))
          fail(`second background layer "${sel}" at line ${html.slice(0, st.index + st[0].indexOf(st[1]) + r.index).split("\n").length}: change html.paper::before / --bgb (Settings › Background) instead`);
    }
  }
  /* house rule (v270): screen changes go through onNav / onSettings hooks, never a new wrap of these four */
  for (const m of html.matchAll(/const\s+[\w$]+\s*=\s*(goTab|goSub|openFull|openSettings)\s*;\s*\1\s*=/g))
    fail(`${m[1]} is wrapped at line ${html.slice(0, m.index).split("\n").length}: register onNav(...) / onSettings(...) instead`);
  /* v285 wrap clean-up: a function given to extend() and then assigned again (fn = function …) loses every hook, and every
     extend() after that adds to a list nothing runs (gdMarkup: v2's page replaced it after two layers, so those never ran,
     and turning them into hooks would have dropped v281's bar). Replace it before its first extend, or extend instead */
  { const js = html.replace(/<script(\s[^>]*)?>([\s\S]*?)<\/script>/g, (s, a, body) => s.replace(body, body.replace(/\/\*[\s\S]*?\*\//g, c => c.replace(/[^\n]/g, " "))));
    const first = new Map(); for (const m of js.matchAll(/\bextend\(\s*"([\w$]+)"/g)) if (!first.has(m[1])) first.set(m[1], m.index);
    for (const [n, at] of first) for (const m of js.matchAll(new RegExp(`(^|[^\\w$.])(?:window\\.)?${n.replace(/\$/g, "\\$")}\\s*=(?![=>]|\\s*["'\`])`, "g"))) {   /* not fill="none" in markup */
      const i = m.index + m[1].length;
      if (i > at && !/\b(const|let|var)\s+$/.test(js.slice(Math.max(0, i - 12), i)))
        fail(`${n} is extended at line ${js.slice(0, at).split("\n").length} and replaced at line ${js.slice(0, i).split("\n").length}: the hooks stop running; use extend(…) or move the replacement above the first extend`); } }
  /* Oct 18: the phone runs the design eye too (lookEye), with its own copy of tools/eye-ok.txt (EYE_OK): the two must match,
     or Nexi files a look the kit was told is on purpose (or the other way round) */
  { const m = html.match(/const EYE_OK = (\[[^\n]*\]);/);
    if (m) { let app = []; try { app = JSON.parse(m[1]); } catch (e) { fail(`EYE_OK in ${FILE} isn't a plain list of ["kind", "part"] pairs`); }
      const txt = fs.readFileSync(path.join(ROOT, "tools/eye-ok.txt"), "utf8").split(/\r?\n/).map(l => l.match(/^(\w+)\s+(\S+)\s+—/)).filter(Boolean).map(x => x[1] + " " + x[2]);
      const a = new Set(app.map(x => x.join(" "))), t = new Set(txt);
      for (const x of t) if (!a.has(x)) fail(`tools/eye-ok.txt has "${x}" but EYE_OK in ${FILE} doesn't: add ["${x.split(" ").join('", "')}"] (the phone's eye would file it)`);
      for (const x of a) if (!t.has(x)) fail(`EYE_OK in ${FILE} has "${x}" but tools/eye-ok.txt doesn't: add the line there with its reason, or drop it from EYE_OK`); } }
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
    /* "/?file=tools/.cache/x.html" serves another copy at the root, so its relative pictures still resolve (kit.mjs look) */
    const other = (/[?&]file=([^&]+)/.exec(q.url) || [])[1];
    let f = decodeURIComponent(q.url.split("?")[0]); if (f === "/" || f === "/index.html") f = "/" + (other ? decodeURIComponent(other) : FILE);
    const p = path.join(ROOT, f);
    if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream" }); fs.createReadStream(p).pipe(res);
  }).listen(0);
  return { srv, base: `http://localhost:${srv.address().port}/` };
}
async function launch() {
  if (WK) return (await pw()).webkit.launch();
  const { chromium } = await pw();
  const exe = [process.env.CHROME, "/opt/pw-browsers/chromium/chrome-linux/chrome", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"].find(f => fs.existsSync(f));
  /* no browser swipe-back: a real finger swiping right on a game page otherwise "goes back" to about:blank (the installed
     app has no browser history gesture) */
  const args = ["--overscroll-history-navigation=0", "--disable-features=OverscrollHistoryNavigation,TouchpadOverscrollHistoryNavigation"];
  return chromium.launch(exe && !process.env.CI ? { executablePath: exe, args } : { args });
}
/* a fresh phone: seeded data, the helper's /bug caught here, every other site blocked (pictures, APIs) */
async function phone(browser, base, w, data, { url = base } = {}) {
  const [vw, vh, dpr] = SIZES[w] || [w, Math.round(w * 2.1), 2.6];
  const ctx = await browser.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: dpr, isMobile: true, hasTouch: true, serviceWorkers: "block",
    userAgent: WK ? (vw >= 600 ? "Mozilla/5.0 (iPad; CPU OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1"
      : "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1")
      : "Mozilla/5.0 (Linux; Android 16; SM-F9800) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Mobile Safari/537.36" });
  const reports = [], errors = [];
  /* blob:/data: stay local: WebKit routes them too (Chromium doesn't), and blocking them stopped workers made from a blob */
  await ctx.route(u => !u.href.startsWith(base) && !/^(blob|data):/.test(u.href), async r => {
    const u = r.request().url();
    if (/\/bug$/.test(u) && r.request().method() === "POST") {
      try { reports.push(...(JSON.parse(r.request().postData() || "{}").reports || [])); } catch (e) {}
      return r.fulfill({ status: 200, contentType: "application/json", body: '{"ok":true}', headers: { "access-control-allow-origin": "*" } });
    }
    /* M3: the helper answers with made-up data in its real shapes (tools/fake-helper.mjs), so filled cards get tested too */
    if (FAKE && new URL(u).host === HELPER_HOST) {
      let b = null; try { b = JSON.parse(r.request().postData() || "null"); } catch (e) {}
      const a = fakeHelper(new URL(u).pathname, b, base);
      return r.fulfill({ status: a.status, contentType: "application/json", body: JSON.stringify(a.json), headers: { "access-control-allow-origin": "*" } });
    }
    /* the pictures those answers point at (Steam's "more like this" headers, store art) are a repo picture, so their cards
       are tested with a picture in them, like on a phone */
    if (FAKE && r.request().resourceType() === "image") return r.fulfill({ status: 200, contentType: "image/webp", body: STANDIN_PIC, headers: { "access-control-allow-origin": "*" } });
    return r.abort();
  });
  data = { ...data, ...SET };
  await ctx.addInitScript(s => { if (!sessionStorage.getItem("seeded")) { localStorage.setItem("gq:data", s); sessionStorage.setItem("seeded", "1"); } }, JSON.stringify(data));
  const page = await ctx.newPage();
  /* WebKit reports "Cannot load blob:… due to access control checks" for the old page's picture blob: URLs (made from data
     URLs) still loading when a scenario reloads: one per reload, none on Chromium, not the app's (open idea M14) */
  const wkNoise = t => WK && /\/\/localhost:\d+\/[0-9a-f-]{36} due to access control checks/i.test(t);
  page.on("pageerror", e => { const t = String(e && e.message || e); if (!wkNoise(t)) errors.push(t.split("\n")[0]); });
  page.on("console", m => { if (m.type() === "error" && !/Failed to load resource|net::|blocked|ERR_/i.test(m.text()) && !wkNoise(m.text())) errors.push(m.text().split("\n")[0]); });
  await page.goto(url); await page.waitForTimeout(2500);
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
  const cdp = WK ? { send: () => { throw new Error("real touch (p.tap / p.swipe) needs Chromium: drop --browser webkit"); } } : await ctx.newCDPSession(page);
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
  /* --screens queue,game: only those tabs and pages (a plant's proof needs one screen, not the whole walk) */
  const ONLY = opt("screens") ? opt("screens").split(",") : null, on = s => !ONLY || ONLY.includes(s);
  for (const s of ONLY || []) if (!SCREENS.includes(s) && !["game", "settings", "edit", "chat"].includes(s)) return fail(`--screens: no screen "${s}" (${SCREENS.join(", ")}, game, settings, edit, chat)`);
  console.log(`\n== sweep (${FILE}, v${APP_V}) at ${WIDTHS.join(", ")}${ONLY ? " on " + ONLY.join(", ") : ""} ==`);
  /* one width at a time: parallel runs starve the CPU and the app's lag checks report it */
  for (const w of WIDTHS) {
    const p = await phone(browser, base, w, seed(APP_V));
    for (const s of SCREENS) {
      if (!on(s)) continue;
      /* the layout check runs 1.6s after a screen change, when idle: leave sooner and most of it is missed
         (a planted cut-off button was reported on 3 screens with 1.4s here, on 11 with 2.6s) */
      await p.step("goTab " + s, `goTab(${JSON.stringify(s)})`, 2600);
      /* 1.1s: the app's smooth scroll takes ~0.5s and checks that run when a scroll comes to rest (v290's Queue logos) wait 350ms more */
      await p.scroll(900); await p.page.waitForTimeout(1100);
      await p.scroll(-900); await p.page.waitForTimeout(400);
    }
    if (on("game")) for (const id of ["g1", "g9", "g5"]) {
      await p.step("game page " + id, `openFull(${JSON.stringify(id)})`, 1600);
      await p.scroll(700); await p.page.waitForTimeout(700);
      await p.step("close game", `closeFull()`, 700);
    }
    if (on("settings")) { await p.step("settings", `openSettings()`, 1500); await p.scroll(1200); await p.page.waitForTimeout(500);
      await p.step("close settings", `typeof closeEdit === "function" && closeEdit()`, 700); }
    if (on("edit")) { await p.step("edit", `openEdit("g2")`, 1400); await p.scroll(900); await p.page.waitForTimeout(500);
      await p.step("close edit", `typeof closeEdit === "function" && closeEdit()`, 700); }
    if (on("chat")) { await p.step("chat", `typeof nxChat === "function" && nxChat()`, 1200);
      await p.step("close chat", `typeof dyClose === "function" && dyClose()`, 600); }
    await p.step("home", `goTab("home")`, 1500);
    await p.flush(); await p.flush();
    if (EXPECT) {
      const real = p.reports.filter(r => r.kind !== "slow").map(line), miss = MATCH.filter(re => !real.some(l => re.test(l)));
      /* a plant that threw shows here: otherwise a renamed function in the plant reads as a blind detector */
      [...new Set(p.errors)].filter(e => /^plant: /.test(e)).forEach(e => console.log(`  · ${w}px: ${e}`));
      if (real.length && !miss.length) console.log(`  ✓ ${w}px: the plant was reported (${real.length}):\n      ` + real.join("\n      "));
      else fail(`${w}px: the plant was NOT reported` + (miss.length && MATCH.length ? ` (nothing matched ${miss.map(String).join(", ")})` : "")
        + (real.length ? `; reported instead:\n      ` + real.join("\n      ") : ""));
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

/* ---------- smooth: loops keep running while a finger moves, and cost the scroll nothing (v282) ----------
   v280 paused every endless loop while a finger moved; v282 removed that (the owner: "nexi animation and rail stops ... I dont
   want animation to stop while scrolling"): pausing and restarting them cost 20–43ms when a scroll started and stopped, more
   than the ~0.3ms a frame the loops cost while running (tools/scenarios/trace-loops.mjs). This fails if Nexi's or the rail's
   loops stop mid-swipe (a hold is back), if a loop running mid-swipe animates anything but transform/opacity (it would be
   restyled and repainted on the main thread every scroll frame), or if what the finger landed on moves or animates (#309). */
async function smooth(browser, base) {
  console.log(`\n== smooth: loops keep running mid-swipe, GPU only (${FILE}) ==`);
  for (const w of opt("widths") ? WIDTHS : [412, 859]) {
    /* nxEvo before the load: Nexi's "I evolved" power-up is a timer set 15s ahead at the first load, so turning it off
       after the load came too late; on a slower machine the game page's swipe landed on the power-up's full-screen tap
       catcher (#ssjcatch) and never reached the page. The power-up gets its own fixed-time case below */
    const p = await phone(browser, base, w, { ...seed(APP_V), nxEvo: 99 });
    await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } S.nxEvo = 99; document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
    /* every running endless loop that is drawn: where it is (Nexi, the rail, elsewhere) and what it animates */
    const live = () => p.page.evaluate(() => {
      const out = [];
      for (const a of document.getAnimations()) {
        const t = a.effect && a.effect.target, tm = a.effect && a.effect.getTiming();
        if (!t || a.playState !== "running" || !tm || tm.iterations !== Infinity) continue;
        if (!t.checkVisibility || !t.checkVisibility({ opacityProperty: true, visibilityProperty: true, contentVisibilityAuto: true })) continue;
        const props = new Set(); for (const k of a.effect.getKeyframes()) for (const x of Object.keys(k)) if (!/^(offset|easing|composite|computedOffset)$/.test(x)) props.add(x);
        out.push({ id: `${a.animationName || "script"} on ${t.tagName.toLowerCase()}${t.classList[0] ? "." + t.classList[0] : ""}${a.effect.pseudoElement || ""}`,
          own: t.closest("#pnbub") ? "nexi" : t.closest("#hrail") ? "rail" : "", slow: [...props].filter(x => !/^(transform|opacity|translate|scale|rotate)$/.test(x)) });
      }
      return out;
    });
    /* the piece under the finger: no inline transform, nothing animating on it while the swipe runs */
    const touched = (x, y) => p.page.evaluate(([x, y]) => {
      const el = document.elementFromPoint(x, y), card = el && el.closest("button, .row, .chip, .libcard, .tile, a") || el;
      if (!card) return "";
      const moving = card.getAnimations().filter(a => a.playState === "running").map(a => a.animationName || a.transitionProperty || "script");
      return [card.style.transform && "transform " + card.style.transform, moving.length && "animating " + moving.join(",")].filter(Boolean).join(", ")
        + (card.style.transform || moving.length ? ` (${card.tagName.toLowerCase()}.${card.classList[0] || ""})` : "");
    }, [x, y]);
    for (const [name, js, side] of [["queue", `goTab("games")`, 0], ["library", `goTab("lib")`, 0], ["game page", `openFull("g1")`, 1]]) {
      await p.step(name, js, 2500);
      const before = await live(), x0 = side ? p.w * .85 : p.w * .5, y0 = side ? p.h * .5 : p.h * .8;
      const go = side ? p.swipe(x0, y0, p.w * .2, p.h * .52, 600, 30) : p.swipe(x0, y0, p.w * .52, p.h * .3, 600, 30);
      await p.page.waitForTimeout(60);
      const under = side ? "" : await touched(x0, y0 - 20);
      await p.page.waitForTimeout(190);
      const mid = await live();
      await go; await p.page.waitForTimeout(1500);
      const ids = l => new Set(l.map(a => a.id)), m = ids(mid);
      const stopped = before.filter(a => a.own && !m.has(a.id)).map(a => `${a.own}: ${a.id}`);
      const slow = mid.filter(a => a.slow.length).map(a => `${a.id} (${a.slow.join(", ")})`);
      const own = mid.filter(a => a.own).length;
      if (stopped.length) fail(`${w}px ${name}: stopped mid-swipe (nothing should hold them while a finger moves): ${[...new Set(stopped)].join(", ")}`);
      else if (!side && !own && before.some(a => a.own)) fail(`${w}px ${name}: Nexi and the rail had no loop running mid-swipe`);
      else if (slow.length) fail(`${w}px ${name}: a loop running mid-swipe animates more than transform/opacity, so every scroll frame restyles and repaints it: ${[...new Set(slow)].join(", ")}`);
      else if (under) fail(`${w}px ${name}: what the finger landed on moved under it: ${under}`);
      else console.log(`  ✓ ${w}px ${name}: ${before.length} endless running → ${mid.length} mid-swipe (Nexi + rail ${own}), all transform/opacity`);
      if (side) await p.step("close", `closeFull()`, 700);
    }
    /* a loop CSS switches on mid-swipe (Nexi's flame runs only while he's powered up) runs, and CSS stays in charge of it */
    if (await p.page.evaluate(() => typeof pnPowerUp === "function" && !!document.querySelector("#pnbub") && document.querySelector("#pnbub").checkVisibility() && !calm())) {
      await p.step("queue", `goTab("games")`, 1500);
      const flame = () => p.page.evaluate(() => document.getAnimations().filter(a => a.effect && a.effect.target && a.effect.target.closest && a.effect.target.closest(".flame")).map(a => a.playState));
      const go = p.swipe(p.w * .5, p.h * .85, p.w * .52, p.h * .2, 1600, 80);
      await p.page.waitForTimeout(100); await p.page.evaluate(() => pnPowerUp(false));
      await p.page.waitForTimeout(1250);
      const mid = await flame(); await go; await p.page.waitForTimeout(600);
      await p.page.evaluate(() => { const end = typeof pnPuEnd !== "undefined" ? pnPuEnd : pnPowerUp.end; if (end) end(); }); await p.page.waitForTimeout(400);
      const end = await flame();
      const n = (l, st) => l.filter(x => x === st).length;
      if (!n(mid, "running")) fail(`${w}px power-up mid-swipe: the flame isn't running mid-swipe (${mid.join(",") || "none"})`);
      else if (n(end, "running")) fail(`${w}px power-up mid-swipe: ${n(end, "running")} flame loops still running after the power-up ended (CSS lost control of them)`);
      else console.log(`  ✓ ${w}px power-up mid-swipe: flame ${n(mid, "running")} running mid-swipe → paused by CSS when it ends`);
    }
    await p.ctx.close();
  }
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

/* ---------- audit: nothing piled up (tools/audit.mjs, its budget only goes down) ---------- */
function audit() {
  const r = spawnSync(process.execPath, [path.join(ROOT, "tools/audit.mjs"), "--check", "--file", FILE], { cwd: ROOT, encoding: "utf8" });
  console.log((r.stdout + r.stderr).trimEnd());
  if (r.status !== 0) fail("code health: a measure went over its budget (tools/audit-budget.txt)");
}

if (mode === "syntax" || mode === "all") syntax();
if (mode === "audit" || mode === "all") audit();
if (mode === "sw" || mode === "all") sw();
/* ---------- a saved scenario ---------- */
async function scenario(browser, base) {
  const file = args.find((a, i) => i > 0 && !a.startsWith("--") && args[i - 1] !== "--file" && args[i - 1] !== "--widths" && args[i - 1] !== "--plant" && args[i - 1] !== "--set" && args[i - 1] !== "--browser" && args[i - 1] !== "--before" && args[i - 1] !== "--match" && args[i - 1] !== "--screens");
  if (!file) return fail("usage: node tools/kit.mjs run tools/scenarios/x.mjs [--widths 412]");
  const fn = (await import(pathToFileURL(path.resolve(file)).href)).default;
  for (const w of opt("widths") ? WIDTHS : [412]) {
    const p = await phone(browser, base, w, seed(APP_V));
    console.log(`\n== ${path.basename(file)} at ${w}px${WK ? " (WebKit)" : ""} ==`);
    if ((await fn({ p, w, fail, seed })) === false) fail(`${path.basename(file)} at ${w}px`);
    await p.ctx.close();
  }
}

/* ---------- eye: what looks broken on every screen (tools/eye.mjs, Oct 2026: the design skill that keeps learning) ---------- */
async function eyeCheck(browser, base) {
  console.log(`\n== eye: what looks broken (${FILE}) at ${WIDTHS.join(", ")} ==`);
  const before = failed;
  const { eyeWalk } = await import(pathToFileURL(path.join(ROOT, "tools/scenarios/design-eye.mjs")).href);
  for (const w of WIDTHS) {
    const p = await phone(browser, base, w, seed(APP_V));
    const found = await eyeWalk({ p, w });
    if (found.length) found.forEach(f => fail(`${w}px ${f}`));
    else console.log(`  ✓ ${w}px: nothing looks wrong`);
    [...new Set(p.errors)].forEach(e => fail(`${w}px: script error: ${e}`));
    await p.ctx.close();
  }
  if (failed > before) console.log("  (a real problem: fix it; meant to look that way: a line in tools/eye-ok.txt with the reason)");
}

/* ---------- taste: the owner's look calls that boxes can hold (tools/scenarios/taste.mjs, Oct 18) ---------- */
async function tasteCheck(browser, base) {
  console.log(`\n== taste: the owner's look calls (${FILE}) at 412, 859 ==`);
  const run = (await import(pathToFileURL(path.join(ROOT, "tools/scenarios/taste.mjs")).href)).default;
  for (const w of opt("widths") ? WIDTHS : [412, 859]) {
    const p = await phone(browser, base, w, seed(APP_V));
    await run({ p, w, fail, seed });
    [...new Set(p.errors)].forEach(e => fail(`${w}px: script error: ${e}`));
    await p.ctx.close();
  }
}

/* ---------- look: what this branch changes on screen (Oct 18, the owner: "upgrade how you are testing the app and seeing
   glitches and ui problems … I want that you get to see all the lags and bad ui and bad design") ----------
   Every check above measures boxes and timings; none of them shows Claude the screen. This takes still screenshots of every
   screen (tools/shots.mjs: home, Queue, Library, Played, Plan, Taste top and one screen down, two game pages, an open Queue
   card, Settings, Edit) of main and of this copy, the same seed and clock for both, and compares them pixel by pixel. Each
   screen that changed gets one picture, before | after | where (in red), in --out (default tools/.cache/look, git-ignored),
   plus a contact sheet of every screen as it is now (sheet-<w>.png): Claude reads them (the Read tool shows pictures) before
   merging, so a fix that moves something else gets seen, which the feature pause is about ("fixes broke other things", 16
   times). Never fails on a change (look changes are expected); --same fails on any (a speed fix that must look the same).
   --base <ref> (default origin/main), --widths (default 412,859). */
async function lookCheck(browser, base) {
  const { shootAll, compare, sheet, prepare } = await import(pathToFileURL(path.join(ROOT, "tools/shots.mjs")).href);
  const ref = opt("base", "origin/main"), out = path.resolve(ROOT, opt("out", "tools/.cache/look")), same = args.includes("--same");
  const ws = opt("widths") ? WIDTHS : [412, 859];
  if (ref.startsWith("origin/")) spawnSync("git", ["fetch", "-q", "origin", ref.slice(7)], { cwd: ROOT, timeout: 30000 });
  const old = spawnSync("git", ["show", ref + ":index.html"], { cwd: ROOT, encoding: "utf8", maxBuffer: 1 << 26 });
  if (old.status) return fail(`look: no index.html at ${ref} (${(old.stderr || "").trim()})`);
  console.log(`\n== look: what ${FILE} changes on screen against ${ref}, at ${ws.join(", ")} ==`);
  fs.rmSync(out, { recursive: true, force: true }); fs.mkdirSync(path.join(out, "before"), { recursive: true });
  const cache = path.join(ROOT, "tools/.cache"); fs.mkdirSync(cache, { recursive: true });
  const baseFile = path.join(cache, "look-base.html"); fs.writeFileSync(baseFile, old.stdout);
  const vOf = h => (h.match(/const APP_V = "(\d+)"/) || [])[1] || "1";
  let changed = 0;
  for (const w of ws) {
    const sets = {};
    for (const [k, file, v] of [["before", baseFile, vOf(old.stdout)], ["after", path.join(ROOT, FILE), APP_V]]) {
      const rel = path.relative(ROOT, file).split(path.sep).join("/");
      const p = await phone(browser, base, w, seed(v, 60), { url: base + "?file=" + encodeURIComponent(rel) });
      await prepare(p, seed(v, 60));
      sets[k] = await shootAll(p, w, k === "before" ? path.join(out, "before") : out, { scale: "css" });
      [...new Set(p.errors)].forEach(e => fail(`${w}px ${k}: script error: ${e}`));
      if (k === "after") await sheet(p.page, sets.after, path.join(out, `sheet-${w}.png`));
      if (k === "after") for (const f of sets.after) {
        const b = path.join(out, "before", path.basename(f)); if (!fs.existsSync(b)) { console.log(`  + ${path.basename(f, ".png")}: new screen`); continue; }
        const r = await compare(p.page, b, f, path.join(out, "changed-" + path.basename(f)));
        const pct = r.n / r.all * 100;
        if (r.n <= r.all * .0005) { console.log(`  ✓ ${path.basename(f, ".png")}: same`); continue; }
        changed++;
        const line = `${path.basename(f, ".png")}: ${pct < .1 ? "<0.1" : pct.toFixed(1)}% changed in ${r.box} → ${path.relative(ROOT, path.join(out, "changed-" + path.basename(f)))}`;
        if (same) fail(line); else console.log(`  △ ${line}`);
      }
      await p.ctx.close();
    }
  }
  fs.rmSync(baseFile, { force: true });
  console.log(`  ${changed ? changed + " screen(s) changed: read each changed-*.png (before | after | where) and check it's what was meant"
    : "nothing changed on screen"}; every screen now: ${ws.map(w => path.relative(ROOT, path.join(out, `sheet-${w}.png`))).join(", ")}`);
}

/* ---------- samecss: the browser applies exactly the same CSS in two copies (after audit.mjs --fix-css or any CSS tidy) ---------- */
/* ---------- layers: how much the GPU draws per screen (v287) ----------
   The stutters that kept coming back (#60 Home, #62 game page, #207 Played) were drawing, not code: the phone timed a 128ms
   frame at "script 0, style+layout 1, render 5". A piece with its own GPU layer under later content (a side scroller, a loop,
   a finished entry animation still holding its last frame, will-change) cuts everything above it into more layers; Played
   was 14.5 screens of layers. tools/layers-budget.txt holds each screen's number; this fails when one grows by more than
   LAYER_SLACK (headless jitter) and prints the biggest layers with their reasons. --lower writes today's numbers. */
const LAYER_BUDGET = path.join(ROOT, "tools/layers-budget.txt"), LAYER_SLACK = 0.6;
async function layers(browser, base) {
  console.log(`\n== layers: GPU layers per screen, in screens' worth (${FILE}) ==`);
  const budget = {};
  if (fs.existsSync(LAYER_BUDGET)) for (const l of fs.readFileSync(LAYER_BUDGET, "utf8").split("\n")) { const m = l.match(/^(\d+)\s+(\w+)\s+([\d.]+)/); if (m) budget[m[1] + " " + m[2]] = +m[3]; }
  const now = {};
  for (const w of opt("widths") ? WIDTHS : [412, 859]) {
    const p = await phone(browser, base, w, { ...seed(APP_V, 60), nxEvo: 99 });
    await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
    for (const [name, js] of Object.entries(LAYER_SCREENS)) {
      await p.step(name, js, 2600);
      const c = await countLayers(p), k = w + " " + name, b = budget[k]; now[k] = c.screens;
      const big = [...c.rows].sort((a, b) => b.scr - a.scr).slice(0, 3).map(r => `${r.d.slice(0, 40)} ${r.w}×${r.h} (${r.why.split("+")[0]})`).join(", ");
      if (b != null && c.screens > b + LAYER_SLACK) fail(`${k}: ${c.screens} screens of GPU layers, budget ${b} (biggest: ${big}; see SCREENS=${name} node tools/kit.mjs run tools/scenarios/layers.mjs --widths ${w})`);
      else console.log(`  ✓ ${k}: ${c.screens} screens, ${c.n} layers${b != null ? ` (budget ${b})` : " (no budget yet)"}`);
    }
    verdict(`layers ${w}`, p); await p.ctx.close();
  }
  if (args.includes("--lower")) {
    for (const k in now) if (budget[k] == null || now[k] < budget[k] || args.includes("--reset")) budget[k] = now[k];
    fs.writeFileSync(LAYER_BUDGET, "# GPU layers per screen, in screens' worth (node tools/kit.mjs layers). Only lower these (--lower);\n# raising one is a hand edit with the reason in the commit.\n" +
      Object.entries(budget).map(([k, v]) => `${k} ${v}`).join("\n") + "\n");
    console.log("  wrote " + path.relative(ROOT, LAYER_BUDGET));
  }
}

async function samecss(browser) {
  const old = opt("before"); if (!old) return fail("usage: node tools/kit.mjs samecss --before old.html [--file index.html]");
  const dump = async file => {
    const page = await browser.newPage();
    await page.setContent(fs.readFileSync(path.resolve(ROOT, file), "utf8").replace(/<script[\s\S]*?<\/script>/g, ""), { waitUntil: "domcontentloaded" });
    const r = await page.evaluate(() => { const w = {}; let n = 0;
      const walk = (rules, ctx) => { for (const r of rules) {
        if (r instanceof CSSKeyframesRule) w["@keyframes " + r.name + "#" + Object.keys(w).length] = { v: r.cssText };
        else if (r.selectorText != null && r.style) for (let i = 0; i < r.style.length; i++) { const k = r.style[i], p = r.style.getPropertyPriority(k), key = ctx + "|" + r.selectorText + "|" + k, c = w[key]; n++;
          if (!c || p || !c.p) w[key] = { v: r.style.getPropertyValue(k), p }; }
        else if (r.cssRules) walk(r.cssRules, ctx + " " + (r.conditionText || (r.media && r.media.mediaText) || ""));
      } };
      for (const s of document.styleSheets) walk(s.cssRules, ""); return { w, n }; });
    await page.close(); return r;
  };
  const A = await dump(old), B = await dump(FILE), d = [];
  const kf = o => Object.keys(o.w).filter(k => k.startsWith("@keyframes")).map(k => o.w[k].v).sort().join("\n");
  for (const k in A.w) if (!k.startsWith("@") && (!B.w[k] || B.w[k].v !== A.w[k].v || B.w[k].p !== A.w[k].p)) d.push(`${k}: ${A.w[k].v} → ${B.w[k] ? B.w[k].v : "(gone)"}`);
  for (const k in B.w) if (!k.startsWith("@") && !A.w[k]) d.push(`${k}: (new) ${B.w[k].v}`);
  if (kf(A) !== kf(B)) d.push("@keyframes differ");
  console.log(`\n== samecss: ${old} → ${FILE} ==\n  declarations ${A.n} → ${B.n}, winning ${Object.keys(A.w).length} → ${Object.keys(B.w).length}`);
  if (d.length) d.slice(0, 30).forEach(x => fail(x)); else console.log("  ✓ the browser applies exactly the same CSS");
}

if (mode === "samecss") { const browser = await launch(); try { await samecss(browser); } finally { await browser.close(); } }
if (["sweep", "tour", "perf", "smooth", "layers", "eye", "taste", "all", "run", "look"].includes(mode)) {
  const { srv, base } = serve(); const browser = await launch();
  try {
    if (mode === "sweep" || mode === "all") await sweep(browser, base);
    if (mode === "tour" || mode === "all") await tour(browser, base);
    if (mode === "smooth" || mode === "all") await smooth(browser, base);
    if (mode === "layers" || mode === "all") await layers(browser, base);
    if (mode === "eye" || mode === "all") await eyeCheck(browser, base);
    if (mode === "taste" || mode === "all") await tasteCheck(browser, base);
    if (mode === "perf") await perf(browser, base);
    if (mode === "run") await scenario(browser, base);
    if (mode === "look") await lookCheck(browser, base);
  } finally { await browser.close(); srv.close(); }
}
console.log(failed ? `\n${failed} problem(s).` : "\nAll clear.");
process.exit(failed ? 1 : 0);
