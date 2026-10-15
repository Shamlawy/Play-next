/* Wrap clean-up, batch 1 (no version): renderQueue, pnBubShow, lookDesign, dyShow had 3 wraps each, closeFull, setSub,
   gdMarkup, gdShots 2, render, renderLib, renderPlayed 1; now extend(name, { before, after, map }) hooks. Every layer's job
   is checked by what it does, so the same checks pass on the old wrapped copy (--file old.html) and on the new one:
     renderQueue  rows glide to their new places, dust + logo fit applied, the open card's size watcher
     pnBubShow    quiet during the tour (face only), dressed, leaves the seat
     lookDesign   Nexi spots an empty Read more, a price stuck on checking, a cut-off number in a tile
     dyShow       the flicker check's open mark, the lag watch
     closeFull    flying covers removed, the lag watch
     setSub       old Taste subs land on Insights, logo fit after a switch
     gdMarkup     the reviews / time-left / caption cards go in above the description
     gdShots      a page without screenshots asks for them, and schedules the "never got them" check
     render       the Now playing theme follows the game
     renderLib    the Room chip, renderPlayed: the played rows' entrance classes
     node tools/kit.mjs run tools/scenarios/extend-hooks-2.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, H = document.documentElement;
    /* swap a top-level function for a recorder while fn runs (hooks call these by their global name) */
    const spy = async (name, fn, keep) => { const old = window[name], calls = [];
      window[name] = function () { calls.push([...arguments]); return keep ? old.apply(this, arguments) : undefined; };
      try { await fn(calls); } finally { window[name] = old; } return calls; };
    out.left = ["renderQueue", "pnBubShow", "lookDesign", "dyShow", "closeFull", "setSub", "gdMarkup", "gdShots", "render", "renderLib", "renderPlayed"]
      .filter(n => /\b_[a-z0-9]+\.(apply|call)\(this|\brp\.apply/.test(String(window[n]))).join(",") || "none";

    /* renderQueue: glide, dust/logo fit, the open card's size watcher */
    goTab("queue"); await wait(700);
    const row = document.querySelector("[data-row]");   /* the glide only runs when the page's first row is on an open screen */
    if (row && row.closest("#p-queue") && !calm() && document.querySelectorAll("#p-queue [data-row]").length > 2) {
      row.scrollIntoView({ block: "center" }); await wait(200);
      const an = Element.prototype.animate, sg = window.sortGames, seen = [];
      Element.prototype.animate = function (k, o) { if (this.dataset && this.dataset.row && k && k[0] && k[0].translate) seen.push(k[0].translate); return an.call(this, k, o); };
      window.sortGames = l => sg(l).reverse();   /* the list turns upside down: every row moves */
      try { renderQueue(); } finally { Element.prototype.animate = an; window.sortGames = sg; }
      out.rqGlide = seen.length > 0; renderQueue(); await wait(700);
    } else out.rqGlide = "n/a";
    out.rqFit = (await spy("dustApply", async () => { await spy("lfApply", async c => { renderQueue(); out.rqLf = c.length > 0; }); })).length > 0;
    renderQueue(); await wait(100);
    const hero = document.querySelector("#p-queue .hero[data-row]"); out.rqSize = hero ? !!hero._szo : "n/a";

    /* pnBubShow: tour, dress, seat */
    let base = 0;
    await spy("pnBubBadge", async c => {
      H.classList.add("nxtouring"); pnBubShow(); H.classList.remove("nxtouring"); out.bubTour = c.length === 0;
      pnBubShow(); out.bubShows = c.length === 1; base = c.length; });
    out.bubDress = (await spy("nxPDress", async () => { pnBubShow(); })).length === 1;
    H.classList.add("nxseat"); pnBubShow(); out.bubSeat = !H.classList.contains("nxseat");

    /* lookDesign: three detectors */
    const box = document.createElement("div"); box.style.cssText = "position:fixed;left:10px;top:120px;width:300px;z-index:5";
    box.innerHTML = `<p style="height:60px;overflow:hidden;margin:0">short</p><button data-descmore aria-expanded="false">Read more</button>
      <div class="px"><span class="pxck">checking…</span></div>
      <div class="gd-glance"><b style="display:block;width:24px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis">123456789 Sept</b></div>`;
    document.body.appendChild(box);
    const said = await spy("bugReport", async () => { lookDesign(box, "test/x"); });
    box.remove();
    const msgs = said.map(a => a[1]);
    out.ldReadMore = msgs.includes("A Read more button has nothing more to show");
    out.ldChecking = pxBusy ? "n/a" : msgs.includes("A price stayed on “checking…”");
    out.ldNumber = msgs.includes("A number in a tile is cut off");

    /* dyShow: open mark + lag watch */
    const lw = await spy("lagWatch", async () => { const b = dyBox(); delete b._flkOpen; dyShow(b, "<p>hi</p>", () => {}); out.dyMark = b._flkOpen > 0; });
    out.dyLag = lw.length === 1 && typeof lw[0][0] === "function";
    dyClose(); await wait(350);

    /* closeFull: flying covers, lag watch */
    const g = S.games.find(x => x.state === "backlog") || S.games[0];
    openFull(g.id); await wait(700);
    const fly = document.createElement("div"); fly.className = "cvfly"; document.body.appendChild(fly);
    const cl = await spy("lagWatch", async () => { closeFull(); });
    out.cfFly = !fly.isConnected; out.cfLag = cl.some(a => a[0] === "Closing a game page");
    await wait(500);

    /* setSub: old Taste subs, logo fit */
    const tk = SUBKEY.taste, was = S[tk];
    if (tk && SUBS.taste.some(x => x[0] === "insights")) { setSub("taste", "psych"); out.ssInsights = S[tk] === "insights"; S[tk] = was; } else out.ssInsights = "n/a";
    out.ssFit = lfBox ? "n/a" : (await spy("lfApply", async () => { setSub("games", subOf("games")); await wait(150); })).length > 0;

    /* gdMarkup: its two old layers never ran (v2's page replaces the function after them) and are gone; v281's bar
       (an extend after the replacement) must still be on the page, and so must the page's own cards */
    const html = gdMarkup(g);
    out.gdBar = !GDSB || html.includes('class="gdsb"');
    out.gdPage = html.includes("hub gpage gd2");

    /* gdShots: asks for screenshots, schedules the check */
    const g2 = { ...g, id: "zz-shots-test", shots: [] };
    const st = window.setTimeout, delays = [];
    const fetched = await spy("shotsFetch", async () => {
      window.setTimeout = (f, ms, ...a) => { delays.push(ms); return st(f, ms, ...a); };
      try { gdShots(g2); } finally { window.setTimeout = st; }
      await wait(150); });
    out.shotsAsk = !shotsDue(g2) || SHOT_BUSY.has(g2.id) ? "n/a" : fetched.length === 1;
    out.shotsCheck = delays.includes(400);

    /* render: the Now playing theme */
    const th = S.theme; const n0 = (await spy("applyLook", async () => { render(); }, true)).length;
    S.theme = "chameleon"; const n1 = (await spy("applyLook", async () => { render(); }, true)).length;
    S.theme = th; render(); out.renderTheme = n1 > n0;

    /* renderLib + renderPlayed */
    goTab("games"); await wait(500); renderLib(); out.libRoom = !!document.querySelector("#lib-view [data-room]") || !document.getElementById("lib-view");
    renderPlayed(); const rows = [...document.querySelectorAll("#p-played .pnb-row")];
    out.playedRows = rows.length ? rows.every(x => x.classList.contains("in") || x.classList.contains("wait")) : "n/a";
    goTab("home"); await wait(300);
    return out;
  });
  const bad = Object.entries(r).filter(([k, v]) => v === false || (k === "left" && v !== "none")).map(([k, v]) => k + (typeof v === "string" ? ":" + v : ""));
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "every layer still does its job" : "failed: " + bad.join(", ")}  ${JSON.stringify(r)}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
