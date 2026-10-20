/* Plant for the v213 "old Arcade heading" check (#130): every big heading on screen turned slanted and in capitals,
   the look Clean (html.ui2) left behind. Since Oct 20 (#136) the check skips headings in hidden screens before it
   measures anything; this proves it still sees the ones on the screen that's showing.
     node tools/kit.mjs sweep --plant tools/plants/arcade-heading.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens queue
   expect: ^\[look\] .*A heading is (slanted|in capitals): the old Arcade look */
(() => {
  const s = document.createElement("style");
  s.textContent = `html.ui2 h1, html.ui2 h2, html.ui2 h3 { font-style: italic !important; text-transform: uppercase !important; font-size: 24px !important; }`;
  document.head.appendChild(s);
})();
