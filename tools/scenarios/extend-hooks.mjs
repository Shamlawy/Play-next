/* v279: applyLook, paintTab, renderHome, syncPlayed, openEdit, pnBubSay and pnBubLines had 4–6 wraps each; now
   extend(name, { before, after, map }) hooks (EXT[name] lists them). Everything that rode on them still happens:
     applyLook   battery saver class, Clean header cards, row glow, Grumble's class, extra features (diary/timer tabs)
     paintTab    onhome class, Home's fit, the usage counter
     renderHome  Nexi's line on Home, the stacks, the playing cards
     syncPlayed  plAt when you start playing, the Then & now card data on finishing, celebrate, drop point
     openEdit    the usage counter, Settings drill classes cleared
     pnBubSay    silent during the tour and the intro cinema, picks a line when given none, names you
     pnBubLines  a list of lines with the extra sources added
     node tools/kit.mjs run tools/scenarios/extend-hooks.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, H = document.documentElement;
    out.registered = ["applyLook", "paintTab", "renderHome", "syncPlayed", "openEdit", "pnBubSay", "pnBubLines"].filter(n => !(EXT[n] && EXT[n].list.length >= 4)).join(",") || "all";
    /* applyLook */
    S.rowArtGlow = 7; S.timerOn = true; applyLook();
    out.applyLook = H.classList.contains("rowglow") && SUBS.plan.some(x => x[0] === "timer");
    S.rowArtGlow = 0; S.timerOn = false; applyLook();
    out.applyLookOff = !H.classList.contains("rowglow") && !SUBS.plan.some(x => x[0] === "timer");
    /* paintTab */
    const u0 = (S.usage.v || {})["games/" + subOf("games")] || 0;
    goTab("games"); await wait(400); const onG = H.classList.contains("onhome");
    goTab("home"); await wait(400); goTab("games"); await wait(400);
    out.paintTab = !onG && ((S.usage.v || {})["games/" + subOf("games")] || 0) > u0;
    goTab("home"); await wait(600);
    out.paintTabHome = H.classList.contains("onhome");
    /* renderHome */
    renderHome(); await wait(200);
    out.renderHome = !!document.querySelector("#p-home .nxhome") || !!S.nxHomeOff;
    /* syncPlayed */
    const g = S.games.find(x => (x.state || "") === "backlog");
    g.state = "playing"; syncPlayed(g, "playing"); out.plAt = !!g.plAt;
    g.state = "played"; (S.played ||= []).push({ id: g.id, real: 7, t: new Date().toISOString().slice(0, 10) });
    const g2 = S.games.find(x => (x.state || "") === "backlog" && x !== g); delete g2.cap;
    syncPlayed(g2, "played"); out.cap = !!g2.cap;
    /* openEdit */
    const e0 = (S.usage.o || {}).edit || 0; document.getElementById("sheet-in").classList.add("drill");
    openEdit(S.games[0].id); await wait(300);
    out.openEdit = ((S.usage.o || {}).edit || 0) === e0 + 1 && !document.getElementById("sheet-in").classList.contains("drill");
    closeEdit();
    /* pnBubSay */
    const seen = []; const base = EXT.pnBubSay.base; EXT.pnBubSay.base = function (l) { seen.push(l); };
    try {
      pnBubSay(["hello {name}", ""]); out.say = seen.length === 1 && Array.isArray(seen[0]);
      H.classList.add("nxtouring"); const n1 = seen.length; window.__tour = true;
      const tour = nxTouring(); pnBubSay(["during tour", ""]); out.sayTour = !tour || seen.length === n1;
      H.classList.remove("nxtouring");
      window.pnCine = true; const n2 = seen.length; pnBubSay(["cinema", ""]); out.sayCine = seen.length === n2; window.pnCine = false;
      pnBubSay(); out.sayPicks = seen.length === n2 + 1 && Array.isArray(seen.at(-1)) && !!seen.at(-1)[0];
    } finally { EXT.pnBubSay.base = base; }
    /* pnBubLines */
    const L = pnBubLines(); out.lines = Array.isArray(L) && L.length > 0;
    return out;
  });
  const bad = Object.entries(r).filter(([k, v]) => v === false || (k === "registered" && v !== "all")).map(([k, v]) => k + (typeof v === "string" ? ":" + v : ""));
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "every hook still does its job" : "failed: " + bad.join(", ")}  ${JSON.stringify(r)}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
