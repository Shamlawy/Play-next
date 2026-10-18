/* Plant for Nexi's design eye on the phone (lookEye, Oct 18): eye-broken.css's looks, here for the app's own reports instead
   of the kit's eye: the first Library card dragged over the one beside it, and Played's top board chips broken in two (v293
   fixed this). (Cards pushed past the screen edge are planted too, but Nexi's older check files those as a button cut off by
   the edge, so this expects the two only the eye files.)
     node tools/kit.mjs sweep --plant tools/plants/eye-in-app.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens lib,played
   expect: ^\[look\] .*A date or short word breaks onto two lines
   expect: ^\[look\] .*Two cards sit on top of each other */
(() => {
  const s = document.createElement("style");
  s.textContent = `#libgridbox > .libcard:nth-child(3n) { transform: translateX(70px); }
    #libgridbox > .libcard:nth-child(1) { transform: translateX(55%); }
    .pnb-p span { white-space: normal !important; max-width: 34px; }`;
  document.head.appendChild(s);
})();
