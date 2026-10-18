/* GPU layers in the first moments of a tab switch (v304, #333/#347 "Switching to Queue stuttered"): the phone's switch frame
   was all drawing (script 0, render 157ms). The census at rest never saw it: while a screen slides and its first pieces
   cascade in, Chromium assumes anything drawn after a moving piece may overlap it and copies the rest of the screen into
   extra layers (the whole 59-row Queue as one 796×7537 layer). Prints layers / MP / screens at ~1 frame, 300ms, 1.5s.
     node tools/kit.mjs run tools/scenarios/switch-layers.mjs --widths 412,859 [--file old.html]   (TABS=games:queue,games:played,home) */
import { countLayers, printLayers } from "../layercount.mjs";
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  const out = {};
  for (const t of (process.env.TABS || "games:queue,games:played,home,taste:insights").split(",")) {
    const [tab, sub] = t.split(":");
    await p.step("from", `closeFull(); goTab("${tab === "plan" ? "home" : "plan"}")`, 1800);
    await p.page.evaluate(([tab, sub]) => { if (sub) setSub(tab, sub); }, [tab, sub]);
    const r = await p.page.evaluate(t => { const b = document.querySelector(`nav button[data-tab="${t}"]`); if (!b || !b.getClientRects().length) return null; const q = b.getBoundingClientRect(); return [q.x + q.width / 2, q.y + q.height / 2]; }, tab);
    if (r) await p.tap(r[0], r[1]); else await p.page.evaluate(t => goTab(t), tab);
    for (const at of [0, 300, 1500]) {
      if (at) await p.page.waitForTimeout(at === 300 ? 250 : 1100);
      const c = await countLayers(p);
      out[`${t}@${at}`] = c.screens;
      printLayers(`${t} +${at}ms`, w, c, +(process.env.TOP || 4));
    }
  }
  console.log("\n  screens: " + Object.entries(out).map(([k, x]) => `${k} ${x}`).join(" · "));
  return true;
};
