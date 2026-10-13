/* v273: behind every screen there's only the still gradient (html.paper::before): no live background (#hlv/#hlvg,
   removed), no queue art backdrop (removed in v274), nothing animating in the background layers.
   v276: mostly black by default (base #000, a 30% blue glow), the old wash's settings gone, and Settings' background
   colour / how much gradient really change it (0% = no glow layer at all).
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
        art: vis("bgart") || vis("bgart2"), moving, bgOff: !("bgOff" in S) };
    });
    if (r.hlv || r.hlvon || !r.grad || r.art || r.moving || !r.bgOff) bad.push(`${name}: ${JSON.stringify(r)}`);
    if (shots) await p.page.screenshot({ path: `${shots}/${w}-${name}.png` });
    if (name === "game") await p.step("close", `closeFull()`, 700);
  }
  const d = await p.page.evaluate(() => { const H = document.documentElement, c = () => getComputedStyle(H), b = () => getComputedStyle(H, "::before");
    const o = { base: c().backgroundColor, op: +b().opacity, old: ["paper", "paperCol", "paperStr"].filter(k => k in S), stripes: c().backgroundImage !== "none" };
    S.bgBase = "#060B1A"; S.bgGrad = 0; applyLook(); o.base2 = c().backgroundColor; o.glow0 = b().content !== "none";
    S.bgGrad = 80; applyLook(); o.op2 = +b().opacity; delete S.bgBase; delete S.bgGrad; applyLook(); return o; });
  if (d.base !== "rgb(0, 0, 0)" || Math.abs(d.op - .3) > .01 || d.old.length || d.stripes || d.base2 !== "rgb(6, 11, 26)" || d.glow0 || Math.abs(d.op2 - .8) > .01)
    bad.push(`settings: ${JSON.stringify(d)}`);
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "only the still gradient behind Home, Queue, Plan, Taste and a game page; black + 30% blue by default, Settings change it" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
