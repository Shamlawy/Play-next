/* v290 plant: v289's queue logo (hanging past the row's right edge and over the next row, tilted) put back, so Nexi's
   logo checks must report it: node tools/kit.mjs sweep --plant tools/plants/logo-spill.js --expect
   On deck goes off (as on the owner's phone), or the logo rows sit below the first screen while the sweep looks */
(() => { const s = document.createElement("style");
  s.textContent = `#p-queue .row.artrow .rowart { right: -40px; top: -24px; bottom: -18px; overflow: visible; }
    #p-queue .row.artrow .rowart img { transform: rotate(-14deg); }`;
  document.head.appendChild(s); S.deckOff = true; renderQueue(); })();
