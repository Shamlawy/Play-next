/* Plant for v303's three label checks: the Queue bar exactly as the owner's report had it at 859 (a genre picked on a game page):
   the genre's tab said "undefined", and the Order button's box shrank below its button so Order ran over Shuffle ("My ord…").
   window.PLANT_KIND = "undefined" | "bar" | "cut" before it picks one (default: all three).  node tools/kit.mjs sweep --plant tools/plants/queue-bar.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 859 --screens queue
   expect: ^\[look\] .*A label says undefined
   expect: ^\[look\] .*A button runs into the one next to it
   expect: ^\[look\] .*A short word on a button is cut short */
(() => {
  const k = window.PLANT_KIND || "all";
  const go = () => {
    if (typeof extend !== "function" || typeof qdDeck !== "function") return setTimeout(go, 200);
    if (k === "all" || k === "undefined") extend("qdDeck", { map: o => o.replace(/(<span class="qdtl">)All</, "$1undefined<") });
    if (k === "all" || k === "bar" || k === "cut") { const s = document.createElement("style");
      s.textContent = `#p-queue .qdsortw { flex: 0 1 auto !important; min-width: 0 !important; max-width: 90px; }` + (k === "cut" ? `#p-queue .qdsort { min-width: 0 !important; }` : "");
      document.head.appendChild(s); }
    /* the cut word for sure: on some fonts "My order" still fits the 146px button in the "all" plant (passed in CI, missed on the
       owner's PC, Oct 19), so a short tab word (Coming) is squeezed into a 22px label as well */
    if (k === "all" || k === "cut") { const c = document.createElement("style");
      c.textContent = `#p-queue .qdtab[data-qfilter=soon] .qdtl { display: inline-block; max-width: 22px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
      document.head.appendChild(c); }
    try { renderQueue(); } catch (e) {}
  };
  go();
})();
