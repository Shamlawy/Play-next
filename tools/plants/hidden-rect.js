/* Plant for kit.mjs detectors (tools/scenarios/detector-cost.mjs, Oct 20): a problem check that measures a screen that isn't
   showing, the way the Oct 20 heading check did (39-65ms laying out hidden screens): after every layout pass it reads the
   size of the first row in a hidden screen. The detectors step must name it.
     node tools/kit.mjs detectors --plant tools/plants/hidden-rect.js --expect --widths 412 --screens home,queue
   Line read by tools/plants/run.mjs (every plant at once, app-check runs it; `kit:` replaces the usual sweep):
   kit: detectors --widths 412 --screens home,queue */
(() => {
  BUG_AFTER.push(function plantHiddenRect() {
    const el = document.querySelector(".panel:not(.on) .row, .panel:not(.on) button");
    if (el) el.getBoundingClientRect();
  });
})();
