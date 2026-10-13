/* v273: behind every screen there's only the still gradient (html.paper::before): no live background (#hlv/#hlvg,
   removed), no queue art backdrop (switched off once), nothing animating in the background layers.
     node tools/kit.mjs run tools/scenarios/bg-gradient.mjs --widths 412,859
     SHOTS=dir node tools/kit.mjs run tools/scenarios/bg-gradient.mjs   (also saves a screenshot per screen) */
import fs from "node:fs";
export default async ({ p, w }) => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const bad = [];
  for (const [name, js] of [["home", `goTab("home")`], ["queue", `goTab("games")`], ["plan", `goTab("plan")`], ["taste", `goTab("taste")`], ["game", `openFull("g1")`]]) {
    await p.step(name, js, 2200);
    const r = await p.page.evaluate(() => {
      const H = document.documentElement, b = getComputedStyle(H, "::before");
      const vis = id => { const e = document.getElementById(id); if (!e) return 0; const c = getComputedStyle(e);
        return c.display !== "none" && c.visibility !== "hidden" && +c.opacity > .02 ? 1 : 0; };
      const moving = ["bgart", "bgart2", "pnorbs"].flatMap(id => { const e = document.getElementById(id); return e && vis(id) ? e.getAnimations({ subtree: true }) : []; }).length;
      return { hlv: !!document.querySelector("#hlv, #hlvg, .hlv-s"), hlvon: H.classList.contains("hlvon"), grad: b.content !== "none" && b.display !== "none" && /gradient/.test(b.backgroundImage),
        art: vis("bgart") || vis("bgart2"), moving, bgOff: S.bgOff === true };
    });
    if (r.hlv || r.hlvon || !r.grad || r.art || r.moving || !r.bgOff) bad.push(`${name}: ${JSON.stringify(r)}`);
    if (shots) await p.page.screenshot({ path: `${shots}/${w}-${name}.png` });
    if (name === "game") await p.step("close", `closeFull()`, 700);
  }
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "only the still gradient behind Home, Queue, Plan, Taste and a game page" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
