/* Plant for v196's first two checks (script errors): opening the Queue crashes. A screen-change hook reads a field of a game
   that isn't there (a TypeError; onNav's hub re-throws a hook's error on its own, as extend() does, so the screen still
   opens), and a job it starts fails on bad saved data with nobody catching the promise. The app must file an [error] and a
   [promise] report. window.PLANT_KIND = "error" | "promise" before it picks one (default: both).
     node tools/kit.mjs sweep --plant tools/plants/thrown-error.js --expect
   Lines read by tools/plants/run.mjs (every plant at once, app-check runs it):
   sweep: --widths 412 --screens queue
   expect: ^\[error\] .*Cannot read properties of undefined
   expect: ^\[promise\] .*JSON */
(() => {
  const k = window.PLANT_KIND || "all";
  onNav("after", (kind, a) => {
    if (kind !== "tab" || a[0] !== "queue") return;
    if (k === "all" || k === "promise") (async () => { await null; return JSON.parse("{gone"); })();
    if (k === "all" || k === "error") return S.games.find(g => g.id === "no-such-game").title;
  });
})();
