/* #435: a slow tap on a text field is the keyboard coming up, not lag; a slow tap on a button still counts.
   Both taps get a 450ms click handler; the slow-tap check (event timing, 250ms+) runs 8s after start.
     node tools/kit.mjs run tools/scenarios/slow-tap-text-field.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.waitForTimeout(7000);
  const busy = `addEventListener("click", e => { if (e.target.closest && e.target.closest("#dxq, #vpill button")) { const t = performance.now(); while (performance.now() - t < 450); } }, true)`;
  await p.step("busy", busy, 300);
  const box = sel => p.page.evaluate(s => { const e = [...document.querySelectorAll(s)].find(x => x.getClientRects().length && x.getBoundingClientRect().width); if (!e) return null; e.scrollIntoView({ block: "center" }); const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel);
  await p.step("home", `goTab("home")`, 1500);
  let b = await box("#dxq"); if (b) { await p.tap(...b); await p.page.waitForTimeout(1500); }
  await p.step("blur", `document.activeElement && document.activeElement.blur(); goTab("games")`, 1500);
  const c = await box('#vpill button[aria-pressed="false"]'); if (c) { await p.tap(...c); await p.page.waitForTimeout(1500); }
  await p.flush();
  const taps = p.reports.filter(r => /tap took/.test(r.msg || r.message || ""));
  const field = taps.some(r => /home/.test(r.screen || "")), button = taps.some(r => /games/.test(r.screen || ""));
  const ok = !!b && !!c && !field && button;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: search box tap reported=${field}, button tap reported=${button}${b && c ? "" : " (target missing)"}`);
  return ok;
};
