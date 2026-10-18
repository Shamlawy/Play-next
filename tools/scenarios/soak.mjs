/* Does the app get slower the longer it is used? (the owner, Oct 18: "the more I use the app the more it lags … keeps
   getting worse"). Drives a fixed loop of use (every tab, open + close a game, scroll, open Settings) for ROUNDS rounds and,
   after each, reports what must stay flat if nothing leaks: DOM nodes, event listeners, JS heap (after a GC), running and
   total animations, and the time the same screen switch takes. Anything that only climbs is the leak.
     node tools/kit.mjs run tools/scenarios/soak.mjs --widths 412      (ROUNDS=12, CPU via --cpu 4) */
const ROUNDS = +process.env.ROUNDS || 12;
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } document.documentElement.className = document.documentElement.className.replace(/\bprb-\w+/g, ""); });
  await p.cdp.send("Performance.enable");
  const metrics = async () => {
    await p.cdp.send("HeapProfiler.collectGarbage").catch(() => {});
    const m = Object.fromEntries((await p.cdp.send("Performance.getMetrics")).metrics.map(x => [x.name, x.value]));
    const a = await p.page.evaluate(() => ({ anims: document.getAnimations().length, running: document.getAnimations().filter(x => x.playState === "running").length }));
    return { nodes: m.Nodes, listeners: m.JSEventListeners, heapMB: +(m.JSHeapUsedSize / 1048576).toFixed(1), docs: m.Documents, ...a };
  };
  const ids = await p.page.evaluate(() => (S.games || []).slice(0, 6).map(g => g.id));
  const tabs = ["home", "queue", "lib", "insights", "home"];
  const timeSwitch = () => p.page.evaluate(async () => { const t = performance.now(); goTab("queue"); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))); const a = performance.now() - t; goTab("home"); await new Promise(r => setTimeout(r, 300)); return Math.round(a); });
  const rows = [];
  rows.push({ round: 0, ...(await metrics()), switchMs: await timeSwitch() });
  for (let r = 1; r <= ROUNDS; r++) {
    for (const t of tabs) { await p.step("tab", `goTab(${JSON.stringify(t)})`, 450); await p.page.evaluate(() => { for (const s of document.querySelectorAll(".panel.on, .panel.on *")) if (s.scrollHeight > s.clientHeight + 50 && /auto|scroll/.test(getComputedStyle(s).overflowY)) { s.scrollTop = s.scrollHeight / 2; break; } }); await p.page.waitForTimeout(250); }
    for (const id of ids) { await p.step("open", `openFull(${JSON.stringify(id)})`, 600); await p.step("close", `closeFull()`, 400); }
    await p.step("settings", `typeof openSettings==="function"&&openSettings()`, 700);
    await p.step("close settings", `closeEdit()`, 400);
    await p.step("home", `goTab("home")`, 500);
    rows.push({ round: r, ...(await metrics()), switchMs: await timeSwitch() });
  }
  console.log(`  ${w}px  round  nodes  listeners  heapMB  docs  anims(running)  queue-switch ms`);
  for (const x of rows) console.log(`        ${String(x.round).padStart(3)}  ${String(x.nodes).padStart(6)}  ${String(x.listeners).padStart(8)}  ${String(x.heapMB).padStart(6)}  ${String(x.docs).padStart(4)}  ${String(x.anims).padStart(5)}(${x.running})  ${String(x.switchMs).padStart(6)}`);
};
