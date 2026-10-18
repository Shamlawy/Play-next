/* Plant for the v305 scroll-linked check: 60 pieces on the screen on top, each with its own view() timeline animation
   (what Played had: four per row plus every locked badge, each one a GPU layer the whole time).
   node tools/kit.mjs sweep --plant tools/plants/scroll-pile.js --expect */
(() => {
  const s = document.createElement("style");
  s.textContent = `.scrpx { height: 6px; margin: 2px 0; animation: scrpx linear both; animation-timeline: view(); }
  @keyframes scrpx { from { opacity: .2; translate: 0 10px; } }`;
  document.head.appendChild(s);
  const add = () => { const p = document.querySelector(".panel.on"); if (!p || p.querySelector(".scrpx")) return;
    p.insertAdjacentHTML("beforeend", Array.from({ length: 60 }, () => '<div class="scrpx"></div>').join("")); };
  add(); onNav("after", () => setTimeout(add, 50));
})();
