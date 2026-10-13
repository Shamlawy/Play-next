/* v277: each Background colour swatch (BG_BASES) really paints the page, black is the default, and Settings shows them all.
     node tools/kit.mjs run tools/scenarios/bg-colours.mjs --widths 412
     SHOTS=dir node tools/kit.mjs run tools/scenarios/bg-colours.mjs   (a Home screenshot per colour) */
import fs from "node:fs";
export default async ({ p, w }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const bad = [];
  await p.step("home", `goTab("home")`, 1500);
  const list = await p.page.evaluate(() => BG_BASES);
  if (list[0] !== "#000000" || list.length < 10) bad.push("swatches: " + list.join(" "));
  if (await p.page.evaluate(() => getComputedStyle(document.documentElement).backgroundColor) !== "rgb(0, 0, 0)") bad.push("default is not black");
  for (const c of list) {
    const got = await p.page.evaluate(c => { S.bgBase = c; applyLook(); return getComputedStyle(document.documentElement).backgroundColor; }, c);
    const want = `rgb(${[1, 3, 5].map(i => parseInt(c.slice(i, i + 2), 16)).join(", ")})`;
    if (got !== want) bad.push(`${c} → ${got}`);
    if (shots) { await p.page.waitForTimeout(150); await p.page.screenshot({ path: `${shots}/${w}-${c.slice(1)}.png` }); }
  }
  await p.page.evaluate(() => { delete S.bgBase; applyLook(); });
  await p.step("settings", `setJump({ scope: "app", group: "Look of the app", text: "Background colour" })`, 1500);
  const n = await p.page.evaluate(() => document.querySelectorAll("#s-bgbase .sw").length);
  if (n !== list.length) bad.push(`Settings shows ${n} swatches`);
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? `${list.length} background colours, black by default, each one paints the page` : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
