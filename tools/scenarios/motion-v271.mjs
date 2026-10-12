/* v271 check: the month slides in from the side you went, and a game page's progress line draws to the new step
   (start playing a backlog game while its page is open), then cleans up after itself.
     node tools/kit.mjs run tools/scenarios/motion-v271.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const out = [];
  await p.step("month", `goTab("plan"); goSub("plan", "month")`, 1500);
  const month = await p.page.evaluate(async () => {
    const tx = () => { const a = $("#p-cal .cmonth").getAnimations().find(x => x.effect && x.effect.getKeyframes()[0].transform); return a ? a.effect.getKeyframes()[0].transform : null; };
    $("#cal-next").click(); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    const n = tx(); await new Promise(r => setTimeout(r, 400));
    $("#cal-prev").click(); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    return [n, tx()];
  });
  const mOk = /28px/.test(month[0] || "") && /-28px/.test(month[1] || "");
  out.push(`month ${mOk ? "✓" : "✗"} ${month.join(" / ")}`);
  const id = await p.page.evaluate(() => (S.games.find(g => gdState(g) === "backlog") || {}).id || null);
  let pOk = false;
  if (id) {
    await p.step("game", `openFull(${JSON.stringify(id)})`, 1400);
    const r = await p.page.evaluate(async () => {
      document.querySelector("#full [data-gdplay]").click();
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      const trk = $("#full .gd-trk"), f = trk && trk.querySelector(".gd-fill");
      const mid = { fill: !!f, w: f ? Math.round(parseFloat(f.style.width)) : 0, go: trk && trk.classList.contains("gd-go"), pops: trk ? [...trk.querySelectorAll("i")].filter(i => i.getAnimations().length).length : 0 };
      await new Promise(r => setTimeout(r, 900));
      const t2 = $("#full .gd-trk");
      return { mid, after: { fill: !!(t2 && t2.querySelector(".gd-fill")), go: t2 && t2.classList.contains("gd-go"), marks: t2 ? t2.querySelectorAll(".gd-prev, .gd-new").length : -1 } };
    });
    pOk = r.mid.fill && r.mid.w > 20 && r.mid.go && r.mid.pops >= 1 && !r.after.fill && !r.after.go && r.after.marks === 0;
    out.push(`progress ${pOk ? "✓" : "✗"} ${JSON.stringify(r)}`);
  } else out.push("progress ✗ no backlog game in the seed");
  const ok = mOk && pOk && !p.errors.length;
  console.log(`  ${w}px: ${out.join("  |  ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
