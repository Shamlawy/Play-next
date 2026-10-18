/* The taste checks' plant (tools/scenarios/taste.mjs): every look they hold, broken on purpose. Run in the page (it's not a
   module; .mjs only keeps tools/plants/run.mjs, which sweeps every *.js plant, from picking it up).
     PLANT=tools/plants/taste-broken.mjs node tools/kit.mjs run tools/scenarios/taste.mjs --widths 412
   must fail every check (app-check runs it). */
(() => {
  const s = document.createElement("style");
  s.textContent = `.panel .tabbar.shx > :not(h3) { display: none !important; }                      /* T4: a header card with only its title */
    .sgc-p img, .sg-lib > button img { width: 40px !important; height: 24px !important; }       /* T12: postage-stamp pictures */
    .qkcov, .qkcov img { border-radius: 0 !important; }                                          /* T33: a sharp-cornered box art */
    .qkstrap { background-image: linear-gradient(90deg, rgb(40, 90, 200), rgb(40, 90, 200)) !important; }   /* T35: one fixed blue strap */`;
  document.head.appendChild(s);
  /* T9 + T10: a playing game's page gets its own extra block and a second main action */
  onNav("after", kind => { if (kind !== "game") return; setTimeout(() => { const g = S.games.find(x => x.id === hubAt), b = document.querySelector("#full.on .gd-body");
    if (!g || !b || g.state !== "playing") return; b.insertAdjacentHTML("afterbegin", `<div class="gd-planted">planted</div>`);
    b.insertAdjacentHTML("beforeend", `<button class="hot" type="button">Second main action</button>`); }, 200); });
  /* T32: an emoji back on a Settings group title */
  onSettings("after", () => setTimeout(() => { const gi = document.querySelector("#sheet.on #sheet-in details > summary .gi"); if (gi) gi.outerHTML = "<span>🎮</span>"; }, 300));
})();
