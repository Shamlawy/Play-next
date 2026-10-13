/* #432: a sideways swipe inside Settings (opened with the gear on a game page) must not move the game page behind it.
     node tools/kit.mjs run tools/scenarios/swipe-in-settings.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const at = () => p.page.evaluate(() => typeof hubAt !== "undefined" ? hubAt : null);
  await p.step("open", `openFull("g1")`, 1600);
  const a = await at();
  await p.step("settings", `openSettings()`, 1400);
  const open = await p.page.evaluate(() => $("#sheet").classList.contains("on"));
  await p.swipe(p.w * 0.85, p.h * 0.5, p.w * 0.1, p.h * 0.51, 180); await p.page.waitForTimeout(1400);
  const b = await at();
  const real = p.reports.filter(r => /swiped behind/.test(r.msg || r.message || ""));
  const ok = open && !!a && a === b && !real.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: settings open=${open}, game ${a} → ${b}${real.length ? ", reported: swiped behind" : ""}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
