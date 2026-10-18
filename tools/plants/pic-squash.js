/* Plant for v199's picture check ("A picture is squashed out of shape", lookDesign): the Library's covers drawn into a
   short wide box with object-fit: fill, the look eye-broken.css plants for the design eye, here for Nexi's own check.
     node tools/kit.mjs sweep --plant tools/plants/pic-squash.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens lib
   expect: ^\[look\] .*A picture is squashed out of shape */
(() => {
  const s = document.createElement("style");
  s.textContent = `#libgridbox > .libcard img { object-fit: fill !important; height: 40px !important; }`;
  document.head.appendChild(s);
})();
