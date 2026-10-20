/* What Nexi's own problem checks cost (Oct 20, lesson L1 ×6 turned into a check). The checks (bugLayout, the BUG_AFTER list,
   bugLook, the design eye, picCheck/picWait, the lag observers…) caused the stutters they report at least 5 times: v283 ×2,
   v287, v305 and the Oct 20 heading check (39-65ms laying out hidden screens). This walks every screen with each check timed:
     - every BUG_AFTER entry, every function in CHECKS and every PerformanceObserver callback runs inside a timer; whatever
       one of them schedules (setTimeout, requestIdleCallback, requestAnimationFrame) is timed as "<check> › later";
     - every layout read (rects, offset/client/scroll sizes, getComputedStyle, innerText, elementFromPoint) is watched: inside
       a check, a read of something in a hidden screen (.panel:not(.on)) is a failure (it lays out that whole screen even
       under content-visibility), and a read that took over 1ms, or came in the same task as an innerHTML write, is a forced
       layout (printed with its cost);
     - the walk runs at 4x slower CPU (like `kit.mjs perf`) so a check's worst single run is close to a slower phone's.
   `node tools/kit.mjs detectors` (in `all`, every PR) walks 412 and 859 with the checks on and again with
   --set '{"bugOff":true}', prints what the checks add per screen, and fails when a check's worst run is over
   tools/detector-budget.txt or it reads a hidden screen. Plant: tools/plants/hidden-rect.js (tools/plants/run.mjs runs it).
   This file on its own (checks on only, no on/off comparison):
     node tools/kit.mjs run tools/scenarios/detector-cost.mjs --widths 412,859 */
import fs from "node:fs";
import path from "node:path";

/* top-level checks timed by name (BUG_AFTER entries are timed by their function names: name every new one) */
export const CHECKS = ["bugLayout", "bugLayoutPass", "bugLook", "bugBlurry", "lookText", "lookDesign", "lookMap", "lookReview",
  "lookEyeRun", "lookEye", "picCheck", "picWait", "picLook", "drawLoad", "lagSplit"];

/* runs in the page before the app (ctx.addInitScript): everything it needs is inside */
export function instrument() {
  if (window.__dc) return;
  const D = window.__dc = { cur: [], calls: {}, hidden: new Map(), skipped: new Map(), forced: new Map(), outside: new Map(), scr: "start", dirty: false, ov: 0 };
  const now = () => performance.now(), ST = setTimeout;
  const path = el => { const p = []; for (let a = el; a && a.nodeType === 1 && a !== document.body && p.length < 4; a = a.parentElement) {
    p.unshift(a.id ? "#" + a.id : a.tagName.toLowerCase() + [...a.classList].slice(0, 2).map(c => "." + c).join("")); if (a.id) break; } return p.join(" > "); };
  D.wrap = (name, f) => {
    if (typeof f !== "function" || f.__dc) return f;
    const g = function () {
      const nm = typeof name === "function" ? name() : name;
      D.cur.push(nm); const t = now(), ov = D.ov;
      try { return f.apply(this, arguments); }
      /* minus the time this file's own read watching took inside it */
      finally { const d = now() - t - (D.ov - ov); D.cur.pop(); const c = D.calls[nm] || (D.calls[nm] = { n: 0, ms: 0, max: 0, scr: "" }); c.n++; c.ms += d; if (d > c.max) { c.max = d; c.scr = D.scr; } }
    };
    g.__dc = 1; return g;
  };
  /* what a check schedules is timed as part of it */
  const later = () => { const top = D.cur[D.cur.length - 1]; return top && (/ › later$/.test(top) ? top : top + " › later"); };
  for (const k of ["setTimeout", "requestIdleCallback", "requestAnimationFrame"]) { const f = window[k]; if (typeof f !== "function") continue;
    window[k] = function (cb, ...rest) { const n = typeof cb === "function" && D.cur.length && later(); return f.call(window, n ? D.wrap(n, cb) : cb, ...rest); }; }
  /* the lag observers */
  if (window.PerformanceObserver) { const PO = window.PerformanceObserver;
    const W = function (cb) { const h = { name: "observer" }; const o = new PO(D.wrap(() => h.name, cb));
      const ob = o.observe; o.observe = function (x) { h.name = "observer:" + ((x && (x.type || (x.entryTypes || []).join("+"))) || "?"); return ob.call(o, x); }; return o; };
    W.prototype = PO.prototype; W.supportedEntryTypes = PO.supportedEntryTypes; window.PerformanceObserver = W; }
  /* layout reads */
  const read = (el, api, run) => {
    if (!D.cur.length) {
      /* outside every timed check: only hidden-screen reads, by where they came from (compared with the bugOff walk) */
      if (el && el.nodeType === 1 && el.closest(".panel:not(.on)") && D.outside.size < 300) {
        const sig = (new Error().stack || "").split("\n").slice(3, 7).map(l => (l.match(/at (?:async )?([^\s(]+)? ?\(?.*?:(\d+):\d+\)?$/) || []).slice(1).map(x => x || "anon").join(":")).join(" ← ");
        const o = D.outside.get(sig) || { n: 0, api, el: path(el) }; o.n++; D.outside.set(sig, o); }
      return run();
    }
    const t0 = now(), dirty = D.dirty, t = now(), r = run(), d = now() - t, who = D.cur.join(" > ");
    const hid = el && el.nodeType === 1 && el.closest(".panel:not(.on)");
    if (hid) { const k = who + "|" + api + "|" + hid.id; let o = D.hidden.get(k);
      if (!o) { const on = document.querySelector(".panel.on");
        o = { check: who, api, panel: hid.id, el: path(el), scr: D.scr, n: 0, showing: (document.querySelector("#full.on") ? "game page over " : document.querySelector("#sheet.on") ? "sheet over " : "") + (on ? "#" + on.id : "no screen"),
          stack: (new Error().stack || "").split("\n").slice(3, 8).map(l => (l.match(/at (?:async )?([^\s(]+)? ?\(?.*?:(\d+):\d+\)?$/) || []).slice(1).map(x => x || "anon").join(":")).join(" ← ") }; }
      o.n++; D.hidden.set(k, o); }
    /* a piece whose drawing is skipped (content-visibility: auto, off screen): its size lays out the skipped part */
    else if (el && el.nodeType === 1 && el.checkVisibility && api !== "getComputedStyle" && !el.checkVisibility({ contentVisibilityAuto: true })) {
      const k = who + "|" + api; const o = D.skipped.get(k) || { check: who, api, el: path(el), n: 0, scr: D.scr }; o.n++; D.skipped.set(k, o); }
    if (d > 1 || dirty) { const k = who + "|" + api; const o = D.forced.get(k) || { check: who, api, el: el && el.nodeType === 1 ? path(el) : api, n: 0, ms: 0, max: 0, dirty: 0, scr: D.scr };
      o.n++; o.ms += d; if (d > o.max) { o.max = d; o.scr = D.scr; if (el && el.nodeType === 1) o.el = path(el); } if (dirty) o.dirty++; D.forced.set(k, o); }
    D.dirty = false;
    D.ov += now() - t0 - d;
    return r;
  };
  const hook = (proto, key) => { const d = proto && Object.getOwnPropertyDescriptor(proto, key); if (!d) return;
    if (d.get) Object.defineProperty(proto, key, { configurable: true, enumerable: d.enumerable, set: d.set, get() { return read(this, key, () => d.get.call(this)); } });
    else if (typeof d.value === "function") { const f = d.value; Object.defineProperty(proto, key, { configurable: true, writable: true, enumerable: d.enumerable, value: function () { const a = arguments; return read(this, key, () => f.apply(this, a)); } }); } };
  ["getBoundingClientRect", "getClientRects", "clientWidth", "clientHeight", "clientTop", "clientLeft", "scrollWidth", "scrollHeight", "scrollTop", "scrollLeft"].forEach(k => hook(Element.prototype, k));
  ["offsetWidth", "offsetHeight", "offsetTop", "offsetLeft", "offsetParent", "innerText"].forEach(k => hook(HTMLElement.prototype, k));
  ["elementFromPoint", "elementsFromPoint"].forEach(k => hook(Document.prototype, k));
  { const g = window.getComputedStyle; window.getComputedStyle = function (el, ps) { return read(el, "getComputedStyle", () => g.call(window, el, ps)); }; }
  /* an innerHTML write: a read in the same task lays out again */
  const dirt = () => { if (!D.dirty) { D.dirty = true; ST(() => { D.dirty = false; }); } };
  { const d = Object.getOwnPropertyDescriptor(Element.prototype, "innerHTML");
    Object.defineProperty(Element.prototype, "innerHTML", { configurable: true, enumerable: d.enumerable, get: d.get, set(v) { d.set.call(this, v); dirt(); } });
    const ia = Element.prototype.insertAdjacentHTML; Element.prototype.insertAdjacentHTML = function () { const r = ia.apply(this, arguments); dirt(); return r; }; }
  /* after the app has loaded: time BUG_AFTER's entries and the named checks */
  D.arm = names => {
    if (typeof BUG_AFTER !== "undefined") BUG_AFTER.forEach((f, i) => { BUG_AFTER[i] = D.wrap(f.name || "BUG_AFTER[" + i + "] (give it a name)", f); });
    for (const n of names) if (typeof window[n] === "function") window[n] = D.wrap(n, window[n]);
    D.cur.length = 0;
  };
  D.result = () => ({ calls: Object.entries(D.calls).map(([name, c]) => ({ name, ...c })).sort((a, b) => b.max - a.max),
    hidden: [...D.hidden.values()], skipped: [...D.skipped.values()].sort((a, b) => b.n - a.n), forced: [...D.forced.values()].sort((a, b) => b.max - a.max), outside: [...D.outside].map(([sig, o]) => ({ sig, ...o })) });
}

/* the walk: every tab, three game pages, Settings, each scrolled like the sweep; ms of main thread per visit. only: a few of
   them (kit.mjs detectors --screens home,queue,game,settings) */
const TABS = ["home", "queue", "lib", "played", "replay", "month", "flow", "plan", "psych", "duels", "friend", "add"];
export async function detectorWalk(p, { cpu = 4, only = null } = {}) {
  await p.page.evaluate(n => window.__dc.arm(n), CHECKS);
  await p.cdp.send("Performance.enable");
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  const task = async () => (await p.cdp.send("Performance.getMetrics")).metrics.find(m => m.name === "TaskDuration").value * 1000;
  await p.page.waitForTimeout(6000);   /* the lag checks start 8s after opening */
  const visits = [];
  const visit = async (name, js, close) => {
    if (only && !only.some(s => name === s || name.startsWith(s + " "))) return;
    await p.page.evaluate(n => { window.__dc.scr = n; }, name);
    const a = await task();
    await p.step(name, js, 2600);
    await p.scroll(900); await p.page.waitForTimeout(1100); await p.scroll(-900); await p.page.waitForTimeout(400);
    if (close) await p.step("close " + name, close, 800);
    visits.push({ name, ms: Math.round(await task() - a) });
  };
  for (const s of TABS) await visit(s, `goTab(${JSON.stringify(s)})`);
  for (const id of ["g1", "g9", "g5"]) await visit("game " + id, `openFull(${JSON.stringify(id)})`, `closeFull()`);
  await visit("settings", `openSettings()`, `typeof closeEdit === "function" && closeEdit()`);
  await visit("home again", `goTab("home")`);
  await p.page.waitForTimeout(1500);
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  return { visits, ...(await p.page.evaluate(() => window.__dc.result())) };
}

/* the budget: "<check> <ms>" lines, "*" for every check without its own line */
export const BUDGET_FILE = "tools/detector-budget.txt";
export function readBudget(root) {
  const b = {}; const f = path.join(root, BUDGET_FILE);
  if (fs.existsSync(f)) for (const l of fs.readFileSync(f, "utf8").split("\n")) { const m = l.match(/^(\S.*?)\s+([\d.]+)\s*$/); if (m && !l.startsWith("#")) b[m[1]] = +m[2]; }
  return b;
}
const r1 = x => Math.round(x * 10) / 10;
/* what failed and what to print, for one width (off = the bugOff walk, or null) */
export function judge(w, on, off, budget) {
  const bad = [], notes = [];
  for (const c of on.calls) { const b = budget[c.name] ?? budget[c.name.replace(/ › later$/, "")] ?? budget["*"];
    const line = `${c.name}: worst run ${r1(c.max)}ms (on ${c.scr}), ${c.n} runs, ${Math.round(c.ms)}ms in all`;
    if (b != null && c.max > b) bad.push(`${w}px ${line}: over its budget of ${b}ms (${BUDGET_FILE})`); else notes.push(`✓ ${line}${b != null ? ` (budget ${b})` : ""}`); }
  for (const h of on.hidden) bad.push(`${w}px ${h.check} read ${h.api} of a hidden screen (#${h.panel}: ${h.el}) ${h.n}× (walk at ${h.scr}, showing ${h.showing}; ${h.stack}): that lays out the whole screen; skip screens that aren't showing before measuring`);
  for (const k of on.skipped.slice(0, 4)) notes.push(`· ${k.check} read ${k.api} of something off screen whose drawing is skipped ${k.n}× (${k.el}, on ${k.scr}): ask bugSkipped(el) first`);
  for (const f of on.forced.slice(0, 6)) notes.push(`· forced layout in ${f.check}: ${f.api} ${f.n}×, worst ${r1(f.max)}ms${f.dirty ? `, ${f.dirty}× right after an innerHTML write` : ""} (${f.el}, on ${f.scr})`);
  if (off) {
    const offSig = new Set(off.outside.map(o => o.sig));
    for (const o of on.outside.filter(o => !offSig.has(o.sig)).slice(0, 5)) notes.push(`· a hidden-screen read only with the checks on, outside a timed check (${o.api} of ${o.el}, ${o.n}×): ${o.sig}`);
    const offBy = Object.fromEntries(off.visits.map(v => [v.name, v.ms]));
    notes.push("· main thread per visit, checks on − off (ms, 4x CPU, a hint only): " + on.visits.map(v => `${v.name} ${v.ms - (offBy[v.name] ?? v.ms) >= 0 ? "+" : ""}${v.ms - (offBy[v.name] ?? v.ms)}`).join(", "));
    const offCalls = off.calls.filter(c => c.n && !/^observer/.test(c.name));
    if (offCalls.length) notes.push(`· with bugOff these still ran: ${offCalls.map(c => `${c.name} ${c.n}× ${Math.round(c.ms)}ms`).join(", ")}`);
  }
  return { bad, notes };
}

/* `kit.mjs run` on its own: the checks-on walk only (the page reloads once with the timers in) */
export default async ({ p, w, fail }) => {
  await p.ctx.addInitScript(instrument);
  await p.page.reload(); await p.page.waitForTimeout(2500);
  if (process.env.PLANT) await p.page.evaluate(fs.readFileSync(process.env.PLANT, "utf8"));
  const on = await detectorWalk(p);
  const { bad, notes } = judge(w, on, null, readBudget(process.cwd()));
  notes.forEach(n => console.log("  " + n)); bad.forEach(b => fail(b));
  return !bad.length;
};
