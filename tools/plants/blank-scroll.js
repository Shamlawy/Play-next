/* Plant for Nexi's "Part of the screen stayed blank while scrolling" check (#89; v311 looks every 250ms and needs two looks
   200ms+ apart in the same scroll, was every 100ms with three looks 90ms apart). Chrome tells the app a row skips its drawing
   with contentvisibilityautostatechange; this plant tells it that about the Queue row in the middle of the screen at every
   scroll, as if that row never got drawn (v313: and the browser's checkVisibility({ contentVisibilityAuto }) on its first child says
   so too: the check asks it before reporting; a really skipped row's child reads false there, tools/scenarios/queue-cold.mjs).  node tools/kit.mjs sweep --plant tools/plants/blank-scroll.js --expect
   Lines read by tools/plants/run.mjs:
   sweep: --widths 412 --screens queue
   expect: ^\[layout\] .*Part of the screen stayed blank while scrolling */
(() => {
  const mark = () => {
    const p = document.getElementById("p-queue"); if (!p || !p.classList.contains("on")) return;
    const el = document.elementFromPoint(innerWidth / 2, innerHeight / 2), row = el && el.closest("#p-queue [data-row]");
    if (!row || row.dataset.plantBlank) return; row.dataset.plantBlank = "1";
    const ev = new Event("contentvisibilityautostatechange", { bubbles: true }); Object.defineProperty(ev, "skipped", { value: true });
    const k = row.firstElementChild, cv = k && k.checkVisibility;
    if (cv) k.checkVisibility = o => o && o.contentVisibilityAuto ? false : cv.call(k, o);
    row.dispatchEvent(ev);
  };
  addEventListener("scroll", mark, { capture: true, passive: true });
  setInterval(mark, 300);
})();
