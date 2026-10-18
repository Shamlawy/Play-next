/* Plant for the v287 layer-pile check: main's leftover #pnorbs (v68's two drifting glows), exactly as it was: a fixed
   full-screen box under every screen whose two circles loop a transform, so everything on screen is drawn over a GPU
   layer. node tools/kit.mjs sweep --plant tools/plants/layer-pile.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens queue
   expect: ^\[layout\] .*GPU layers pile up on this screen */
(() => {
  const s = document.createElement("style");
  s.textContent = `#pnorbsx { position: fixed; inset: 0; z-index: -1; pointer-events: none; overflow: hidden; }
  #pnorbsx i { position: absolute; width: 60vmax; height: 60vmax; border-radius: 50%; opacity: .16;
    background: radial-gradient(circle, var(--volt2), transparent 65%); will-change: transform; }
  #pnorbsx i:nth-child(1) { left: -20vmax; top: -15vmax; animation: pnOrbAx 26s ease-in-out infinite alternate; }
  #pnorbsx i:nth-child(2) { right: -25vmax; bottom: -20vmax; opacity: .12; animation: pnOrbBx 32s ease-in-out infinite alternate; }
  @keyframes pnOrbAx { to { transform: translate(18vmax, 22vmax) scale(1.15); } }
  @keyframes pnOrbBx { to { transform: translate(-20vmax, -16vmax) scale(.9); } }`;
  document.head.appendChild(s);
  document.body.insertAdjacentHTML("afterbegin", '<div id="pnorbsx" aria-hidden="true"><i></i><i></i></div>');
})();
