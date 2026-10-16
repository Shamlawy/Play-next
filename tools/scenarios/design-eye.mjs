/* The design eye on every screen (tools/eye.mjs; `node tools/kit.mjs eye` runs it at 412 / 859 / 360 in `all`).
     node tools/kit.mjs run tools/scenarios/design-eye.mjs --widths 412,859
     SCREENS=home,queue   only these     PLANT=tools/plants/x.css   put a broken look on first (must then be flagged)
     SHOTS=dir   a screenshot of each place it looked
   Each screen at the top and one screen down, three game pages, Settings. Fails on anything tools/eye-ok.txt doesn't excuse. */
import fs from "node:fs";
import { look, still } from "../eye.mjs";

export const PLACES = [
  ...["home", "queue", "lib", "played", "replay", "month", "flow", "plan", "psych", "duels", "friend", "add"].map(s => [s, `goTab(${JSON.stringify(s)})`, `goTab("home")`]),
  ...["g1", "g9", "g5"].map(g => ["game " + g, `openFull(${JSON.stringify(g)})`, `closeFull()`]),
  ["settings", `openSettings()`, `closeEdit()`],
];

export async function eyeWalk({ p, w }, only) {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  if (process.env.PLANT) { const css = fs.readFileSync(process.env.PLANT, "utf8");
    await p.page.evaluate(css => { const s = document.createElement("style"); s.id = "eyeplant"; s.textContent = css; document.head.appendChild(s); }, css); }
  const found = [];
  for (const [name, go, back] of PLACES) {
    if (only && !only.includes(name.split(" ")[0]) && !only.includes(name)) continue;
    await p.step(name, go, 1800);
    for (const dy of [0, Math.round(p.h * .8)]) {
      await still(p.page, dy);
      if (shots) await p.page.screenshot({ path: `${shots}/${w}-${name.replace(/\W+/g, "-")}-${dy ? "down" : "top"}.png` });
      const r = await look(p.page);
      for (const i of r.issues) found.push(`${name}${dy ? " (scrolled)" : ""}: [${i.kind}] ${i.msg} @ ${i.at}`);
    }
    await still(p.page, 0);
    await p.step("back", back, 700);
  }
  return [...new Set(found)];
}

export default async ({ p, w }) => {
  const only = process.env.SCREENS && process.env.SCREENS.split(",");
  const found = await eyeWalk({ p, w }, only);
  const planted = !!process.env.PLANT;
  const ok = planted ? found.length > 0 : found.length === 0;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${found.length ? found.length + " thing(s) look wrong" + (planted ? " (planted)" : "") + ":\n      " + found.join("\n      ") : "nothing looks wrong"}`);
  if (p.errors.length) { console.log("  errors: " + [...new Set(p.errors)].join("; ")); return false; }
  return ok;
};
