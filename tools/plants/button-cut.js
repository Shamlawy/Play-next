/* Plant for the oldest layout checks (v196 bugLayoutPass, v199 bugLook): the Queue's filter tabs grown wider than the screen
   on one line (as a long genre tab would make them), so the last ones run past the right edge with nothing clipping them,
   and every Queue row's name cut mid-letter (one line, overflow hidden, no "…"; On deck goes off so the rows are on the
   first screen while the check looks). queue-bar.js covers the bar's own label checks (a label saying undefined, Order
   running over Shuffle, a short word cut); this is the screen edge and plain cut-off text.
   window.PLANT_KIND = "edge" | "text" before it picks one (default: both).
     node tools/kit.mjs sweep --plant tools/plants/button-cut.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens queue
   expect: ^\[layout\] .*A button is cut off by the (left|right) edge of the screen
   expect: ^\[layout\] .*Text is cut off without … */
(() => {
  const k = window.PLANT_KIND || "all", s = document.createElement("style");
  if (k === "all" || k === "edge") s.textContent += `#p-queue .qdtabs { flex-wrap: nowrap !important; }
    #p-queue .qdtab { flex: none !important; min-width: 130px !important; }`;
  if (k === "all" || k === "text") s.textContent += `#p-queue .row .name { white-space: nowrap !important; text-overflow: clip !important;
    -webkit-line-clamp: none !important; display: block !important; max-width: 64px !important; }`;
  document.head.appendChild(s);
  if (k === "all" || k === "text") { S.deckOff = true; renderQueue(); }
})();
