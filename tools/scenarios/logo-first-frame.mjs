/* v308 (#334): the Queue's logo tile is measured by a ResizeObserver (lfRO), not read in a frame, which forced a full layout
   of the hidden Queue every time it was drawn (twice while the app opened, ~470ms each at 4x). This opens the app on Home,
   waits for the start to settle, switches to the Queue and checks, from an observer that runs after lfRO in the same frame
   (before it is painted), that the tile size and the logo rules are already in place: the first Queue frame shows the
   fitted logos, nothing jumps. Then unfolds/folds and checks the size follows.
     node tools/kit.mjs run tools/scenarios/logo-first-frame.mjs --widths 412,859 */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload(); await p.page.waitForTimeout(6500);
  const r = await p.page.evaluate(() => new Promise(res => {
    const im = document.querySelector("#p-queue .rowart"); if (!im) return res({ err: "no logo tile" });
    const out = { home: curTab() === "home", before: !!lfBox };
    const ro = new ResizeObserver(() => { if (out.box) return; const q = im.getBoundingClientRect();
      out.box = lfBox && Math.abs(lfBox.w - q.width) < 1 && Math.abs(lfBox.h - q.height) < 1;
      out.rules = ((document.getElementById("lfcss") || {}).textContent || "").length; });
    ro.observe(im);
    goTab("games"); goSub("games", "queue");
    requestAnimationFrame(() => requestAnimationFrame(() => { ro.disconnect(); res(out); }));
  }));
  const vw = w > 600 ? 412 : 859;
  await p.page.setViewportSize({ width: vw, height: 900 }); await p.page.waitForTimeout(900);
  const z = await p.page.evaluate(() => { const q = document.querySelector("#p-queue .rowart").getBoundingClientRect();
    return lfBox && Math.abs(lfBox.w - q.width) < 1 && Math.abs(lfBox.h - q.height) < 1; });
  const ok = !r.err && r.home && !r.before && r.box && r.rules > 0 && z && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "the tile is measured when the Queue first shows, logo rules in place before that frame is painted; resizing to " + vw + " follows" : JSON.stringify({ ...r, resized: z })}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
