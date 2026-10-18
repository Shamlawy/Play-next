/* What Nexi's design eye (lookEye, Oct 18) costs on the phone, and what it would file: on each screen, with 60 games and
   a 4x slower CPU, the eye runs the way the phone runs it (no pixel pass, no words) three times; prints the slowest run and
   every finding. The phone runs it once per screen and size, when idle; it must stay a short task (under ~25ms here).
     node tools/kit.mjs run tools/scenarios/eye-cost.mjs --widths 412,859
   CPU=1 (no slowdown), GAMES=14 (the sweep's set). Fails when a screen takes over MAX ms (default 50: a real phone is ~4x faster, so ~12ms, once per screen, only after 1.2s untouched). */
const SCREENS = [["home", `closeFull(); goTab("home")`], ["queue", `closeFull(); goTab("games"); goSub("games", "queue")`], ["lib", `closeFull(); goTab("games"); goSub("games", "lib")`],
  ["played", `closeFull(); goTab("games"); goSub("games", "played")`], ["plan", `closeFull(); goTab("plan")`], ["taste", `closeFull(); goTab("taste")`],
  ["game", `closeFull(); goTab("games"); goSub("games", "queue"); openFull(S.games[1].id)`], ["settings", `closeFull(); openSettings()`]];
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => { localStorage.setItem("gq:data", d); }, JSON.stringify({ ...seed(v, +process.env.GAMES || 60), nxEvo: 99 }));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  const cpu = +process.env.CPU || 4, MAX = +process.env.MAX || 50;
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
  let ok = true;
  for (const [name, js] of SCREENS) {
    await p.step(name, js, 2600);
    const r = await p.page.evaluate(() => {
      const root = document.querySelector("#full.on .hub") || document.querySelector("#sheet.on #sheet-in") || document.querySelector("#diary.on .dy-in") || document.querySelector(".panel.on");
      if (!root || typeof lookEye !== "function") return null;
      const t = []; let out;
      for (let k = 0; k < 3; k++) { const t0 = performance.now(); out = lookEye(root, EYE_OK, { pics: false, words: false, skip: ["faint", "squashed"] }); t.push(performance.now() - t0); }
      return { ms: Math.max(...t), groups: out.groups, n: root.getElementsByTagName("*").length, found: out.issues.map(i => `${i.kind} ${i.at}: ${i.msg}`) };
    });
    if (!r) { console.log(`  · ${w}px ${name}: no screen root or no lookEye`); continue; }
    const bad = r.ms > MAX; if (bad) ok = false;
    console.log(`  ${bad ? "✗" : "✓"} ${w}px ${name}: ${r.ms.toFixed(1)}ms at ${cpu}x (${r.n} elements, ${r.groups} groups)${r.found.length ? "\n      " + r.found.join("\n      ") : ""}`);
  }
  await p.cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  return ok;
};
