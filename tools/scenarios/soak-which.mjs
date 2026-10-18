/* Which action leaks? Repeats ONE kind of use N times and prints DOM nodes + listeners after each batch.
     ACT=tabs|game|settings node tools/kit.mjs run tools/scenarios/soak-which.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.cdp.send("Performance.enable");
  const m = async () => { const x = Object.fromEntries((await p.cdp.send("Performance.getMetrics")).metrics.map(y => [y.name, y.value])); return `${x.Nodes} nodes ${x.JSEventListeners} listeners`; };
  const id = await p.page.evaluate(() => S.games[0].id);
  const act = process.env.ACT || "tabs";
  const one = { tabs: ['goTab("queue")', 'goTab("lib")', 'goTab("insights")', 'goTab("home")'], game: [`openFull(${JSON.stringify(id)})`, "closeFull()"], settings: ["openSettings()", "closeEdit()", 'goTab("home")'] }[act];
  console.log(`  ${w}px ${act}: start ${await m()}`);
  for (let b = 1; b <= 5; b++) { for (let i = 0; i < 4; i++) for (const s of one) await p.step("s", s, 500); console.log(`   after ${b * 4}: ${await m()}`); }
};
