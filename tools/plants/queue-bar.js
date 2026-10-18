/* Plant for v303's three label checks: the Queue bar exactly as the owner's report had it at 859 (a genre picked on a game page):
   the genre's tab said "undefined", and the Order button's box shrank below its button so Order ran over Shuffle ("My ord…").
   window.PLANT_KIND = "undefined" | "bar" | "cut" before it picks one (default: all three).  node tools/kit.mjs sweep --plant tools/plants/queue-bar.js --expect */
(() => {
  const k = window.PLANT_KIND || "all";
  const go = () => {
    if (typeof extend !== "function" || typeof qdDeck !== "function") return setTimeout(go, 200);
    if (k === "all" || k === "undefined") extend("qdDeck", { map: o => o.replace(/(<span class="qdtl">)All</, "$1undefined<") });
    if (k === "all" || k === "bar" || k === "cut") { const s = document.createElement("style");
      s.textContent = `#p-queue .qdsortw { flex: 0 1 auto !important; min-width: 0 !important; max-width: 90px; }` + (k === "cut" ? `#p-queue .qdsort { min-width: 0 !important; }` : "");
      document.head.appendChild(s); }
    try { renderQueue(); } catch (e) {}
  };
  go();
})();
