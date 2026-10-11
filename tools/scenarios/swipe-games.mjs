/* A saved scenario (and a real check): open a game, swipe to the next one with a real finger (CDP touch), and back.
     node tools/kit.mjs run tools/scenarios/swipe-games.mjs --widths 412,859
   Copy it for a one-off check and keep the copy here when it could be useful again: the scratchpad is lost with the chat. */
export default async ({ p, w }) => {
  const at = () => p.page.evaluate(() => typeof hubAt !== "undefined" ? hubAt : null);
  await p.step("open", `openFull("g1")`, 1600);
  const a = await at();
  await p.swipe(p.w * 0.85, p.h * 0.45, p.w * 0.1, p.h * 0.46, 180); await p.page.waitForTimeout(1400);
  const b = await at();
  await p.swipe(p.w * 0.1, p.h * 0.45, p.w * 0.85, p.h * 0.46, 180); await p.page.waitForTimeout(1400);
  const c = await at();
  const ok = !!a && !!b && a !== b && c === a && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${a} → ${b} → ${c}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
