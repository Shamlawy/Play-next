/* #201 (the friend, iPhone/iPad Safari: "Restore a code is not working"): Safari's keyboard slides over the page without
   shrinking it, so the short Restore sheet at the bottom went under it. This fakes the keyboard (visualViewport shorter by
   half the screen, as on an iPad in landscape) after a real tap on the code field, then checks:
     1. the sheet, the code field and "Find my backup" are all above the keyboard, the field is 16px (no Safari zoom);
     2. a backdrop tap while typing only puts the keyboard away, the next one closes the sheet;
     3. no problem report for any of it;
     4. the detector: with the lift turned off (the old behaviour) "The keyboard covers the text field…" is reported, and a
        field that zooms the screen in is reported too.
   node tools/kit.mjs run tools/scenarios/restore-keyboard.mjs --widths 412,859
   (--file old.html for the build before: step 1 fails there) */
export default async ({ p, w }) => {
  const pg = p.page;
  await pg.waitForTimeout(6000);
  const fake = (h, s) => pg.evaluate(([h, s]) => {
    const vv = visualViewport;
    if (h == null) { delete vv.height; delete vv.scale; delete vv.offsetTop; }
    else { Object.defineProperty(vv, "height", { configurable: true, get: () => h }); Object.defineProperty(vv, "offsetTop", { configurable: true, get: () => 0 });
      if (s) Object.defineProperty(vv, "scale", { configurable: true, get: () => s }); }
    vv.dispatchEvent(new Event("resize"));
  }, [h, s]);
  const open = async () => {
    await p.step("open restore", `dyClose(); setTimeout(cbRestoreSheet, 350)`, 1200);
    const c = await pg.evaluate(() => { const i = document.querySelector("#diary.on .cb-in"); if (!i) return null; const r = i.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; });
    if (c) { await p.tap(...c); await pg.waitForFunction(() => document.activeElement && document.activeElement.matches("#diary.on .cb-in"), null, { timeout: 5000 }).catch(() => {}); await pg.waitForTimeout(500); }
    return c;
  };
  const kb = Math.round(p.h * 0.5);
  const c = await open();
  await fake(p.h - kb); await pg.waitForTimeout(1500);
  const m = await pg.evaluate(vis => {
    const q = s => { const e = document.querySelector(s); return e ? e.getBoundingClientRect() : null; };
    const sh = q("#diary.on .dy-in"), inp = q("#diary.on .cb-in"), btn = q("#diary.on .cb-f .btn");
    return { vis, sheet: sh && Math.round(sh.bottom), inp: inp && Math.round(inp.bottom), btn: btn && Math.round(btn.bottom), top: sh && Math.round(sh.top),
      focused: !!(document.activeElement && document.activeElement.matches(".cb-in")), font: parseFloat(getComputedStyle(document.querySelector("#diary.on .cb-in")).fontSize) };
  }, p.h - kb);
  const above = m.sheet != null && m.sheet <= m.vis + 1 && m.btn <= m.vis && m.inp <= m.vis && m.top >= 0;
  console.log(`  ${above ? "✓" : "✗"} ${w}px: keyboard top at ${m.vis}, sheet ${m.top}–${m.sheet}, field bottom ${m.inp}, button bottom ${m.btn}, field focused ${m.focused}, field ${m.font}px`);
  /* backdrop tap while typing: keyboard away, sheet stays; the keyboard going away gives the page its height back */
  await p.tap(p.w / 2, 12); await pg.waitForTimeout(400);
  const s1 = await pg.evaluate(() => ({ on: !!document.querySelector("#diary.on .cb-f"), focused: !!(document.activeElement && document.activeElement.matches(".cb-in")) }));
  await fake(null); await pg.waitForTimeout(500);
  const low = await pg.evaluate(h => { const e = document.querySelector("#diary.on .dy-in"); return e ? Math.round(e.getBoundingClientRect().bottom) >= h - 1 : false; }, p.h);
  await p.tap(p.w / 2, 12); await pg.waitForTimeout(500);
  const s2 = await pg.evaluate(() => !!document.querySelector("#diary.on"));
  const guard = s1.on && !s1.focused && low && !s2;
  console.log(`  ${guard ? "✓" : "✗"} ${w}px: 1st backdrop tap keeps the sheet (${s1.on}) and drops the keyboard (${!s1.focused}), sheet back at the bottom (${low}), 2nd closes it (${!s2})`);
  await p.flush();
  const clean = !p.reports.some(r => r.kind !== "slow");
  console.log(`  ${clean ? "✓" : "✗"} ${w}px: no problem reports${clean ? "" : ": " + p.reports.map(r => r.msg).join(" | ")}`);
  /* the detector, on a copy of the old problem: lift off, then a zoom */
  await p.step("lift off", `const s = document.createElement("style"); s.id = "kbplant"; s.textContent = "#diary.dykb{padding-bottom:0!important}#diary.dykb .dy-in{max-height:85vh!important}"; document.head.appendChild(s)`, 200);
  await open(); await fake(p.h - kb); await pg.waitForTimeout(2500); await fake(null); await pg.waitForTimeout(300);
  await open(); await fake(Math.round(p.h / 1.6), 1.6); await pg.waitForTimeout(2500); await fake(null);
  await p.step("clean up", `dyClose(); document.getElementById("kbplant").remove()`, 500);
  await p.flush();
  const covers = p.reports.some(r => /keyboard covers the (sheet|text field)/.test(r.msg)), zoom = p.reports.some(r => /zoomed the screen in/.test(r.msg));
  console.log(`  ${covers && zoom ? "✓" : "✗"} ${w}px: planted problems reported: covered sheet ${covers}, zoom ${zoom}`);
  return !!c && above && guard && clean && covers && zoom;
};
