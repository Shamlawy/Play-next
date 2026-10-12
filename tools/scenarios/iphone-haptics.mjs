/* v270 iPhone haptics: on a phone with no navigator.vibrate and an Apple user agent, navigator.vibrate flips a hidden
   <input switch> once per buzz in the pattern, and no page-wide click/change listener ever sees the flip.
     node tools/kit.mjs run tools/scenarios/iphone-haptics.mjs */
export default async ({ p, w }) => {
  await p.page.context().addInitScript(() => {
    delete Navigator.prototype.vibrate;
    Object.defineProperty(Navigator.prototype, "userAgent", { get: () => "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1" });
  });
  await p.page.reload(); await p.page.waitForTimeout(2500);
  const r = await p.page.evaluate(async () => {
    const seen = { cap: 0, bub: 0, change: 0 };
    const c = () => seen.cap++, b = () => seen.bub++, ch = () => seen.change++;
    document.addEventListener("click", c, true); document.addEventListener("click", b); document.addEventListener("change", ch, true);
    const shim = typeof navigator.vibrate === "function" && !/native code/.test(String(navigator.vibrate));
    navigator.vibrate(18);
    const box = document.querySelector("label[aria-hidden] > input[switch]");
    const one = box ? box.checked : null;
    await new Promise(r => setTimeout(r, 60));
    navigator.vibrate([30, 60, 90, 40, 20]);           /* three buzzes: now, +90ms, +220ms */
    await new Promise(r => setTimeout(r, 400));
    const flips3 = box ? box.checked : null;           /* 1 + 3 flips = even: back to unchecked */
    navigator.vibrate(0); navigator.vibrate([]);       /* nothing */
    document.removeEventListener("click", c, true); document.removeEventListener("click", b); document.removeEventListener("change", ch, true);
    return { shim, box: !!box, one, flips3, after: box ? box.checked : null, seen, hidden: box ? getComputedStyle(box.parentNode).display : "" };
  });
  const ok = r.shim && r.box && r.one === true && r.flips3 === false && r.after === false && r.hidden === "none"
    && r.seen.cap === 0 && r.seen.bub === 0 && r.seen.change === 0;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${JSON.stringify(r)}`);
  return ok;
};
