/* v308: the background glow (html.paper::before, fixed) must never repaint while you scroll. Tried for #469: moving the glow
   onto html itself with background-attachment: fixed (one layer fewer, same look) repainted half the screen or more on
   every scroll frame (86 paints in three swipes at 412 in Chromium); letting it scroll with the page saves ~2 screens of
   layers but one of them is plain black and the glow is drawn once, so the look (it stays put behind the screens) won.
   This swipes the Queue (60 games) with real touch, counts the main thread's big Paint events mid-swipe and checks the
   glow is there.
     node tools/kit.mjs run tools/scenarios/bg-scroll-paint.mjs --widths 412,859
     CSS='<rules>' tries a change without editing the app; --file tools/.cache/old.html for the before. */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  await p.step("queue", `closeFull(); goTab("games"); goSub("games", "queue")`, 2600);
  const ev = [];
  const h = e => { for (const x of e.value) ev.push(x); };
  p.cdp.on("Tracing.dataCollected", h);
  await p.cdp.send("Tracing.start", { categories: "devtools.timeline,disabled-by-default-devtools.timeline", transferMode: "ReportEvents" });
  await p.page.waitForTimeout(300);
  const y0 = await p.page.evaluate(() => scrollY);
  for (let i = 0; i < 3; i++) { await p.swipe(p.w * .5, p.h * .8, p.w * .52, p.h * .3, 220, 12); await p.page.waitForTimeout(250); }
  const y1 = await p.page.evaluate(() => scrollY);
  const done = new Promise(r => p.cdp.once("Tracing.tracingComplete", r));
  await p.cdp.send("Tracing.end"); await done; p.cdp.off("Tracing.dataCollected", h);
  const paints = ev.filter(e => e.name === "Paint" && e.ph !== "E");
  const big = paints.filter(e => { const c = e.args && e.args.data && e.args.data.clip; if (!c) return false;
    const xs = [c[0], c[2], c[4], c[6]], ys = [c[1], c[3], c[5], c[7]];
    return (Math.max(...xs) - Math.min(...xs)) * (Math.max(...ys) - Math.min(...ys)) >= p.w * p.h * .5; });
  const look = await p.page.evaluate(() => { const H = document.documentElement, c = getComputedStyle(H), b = getComputedStyle(H, "::before");
    return { root: /gradient/.test(c.backgroundImage), fixed: /fixed/.test(c.backgroundAttachment),
      layer: b.content !== "none" && b.display !== "none" && /gradient/.test(b.backgroundImage) }; });
  const moved = Math.abs(y1 - y0) > p.h * .5, glow = look.root || look.layer;
  const ok = moved && glow && big.length <= 2 && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: scrolled ${Math.round(y1 - y0)}px, ${paints.length} paints mid-swipe (${big.length} of half the screen or more), glow ${look.root ? "on html" + (look.fixed ? " (fixed)" : " (scrolls!)") : look.layer ? "on ::before" : "missing"}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
