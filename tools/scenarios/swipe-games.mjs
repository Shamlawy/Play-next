/* Example scenario (and a real check): open a game, swipe to the next one with a real finger (CDP touch), back again.
   node tools/pn.mjs run tools/scenarios/swipe-games.mjs --w=412,859
   Copy this file for a one-off check and keep it here if it could be useful again: scenarios survive between chats. */
export default async ({ t, lib }) => {
  await lib.run(t, "open", `openFull("g1")`, 1600);
  const at = () => t.page.evaluate(() => typeof hubAt !== "undefined" ? hubAt : null);
  const a = await at();
  await lib.swipe(t, t.w * 0.85, t.h * 0.45, t.w * 0.1, t.h * 0.46, 180);
  await new Promise(r => setTimeout(r, 1400));
  const b = await at();
  await lib.swipe(t, t.w * 0.1, t.h * 0.45, t.w * 0.85, t.h * 0.46, 180);
  await new Promise(r => setTimeout(r, 1400));
  const c = await at();
  const ok = a && b && a !== b && c === a && !t.errors.length;
  console.log(`swipe ${t.w}px: ${a} → ${b} → ${c} ${ok ? "OK" : "FAIL"}${t.errors.length ? " errors: " + t.errors.join("; ") : ""}`);
  return ok;
};
