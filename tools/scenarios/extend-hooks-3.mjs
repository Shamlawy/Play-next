/* Wrap clean-up, batch 2 (no version): the functions with 2 wraps (renderTaste, xFeatures, qCounts, queueList, pnPowerUp,
   nxContext, wiAdd, pxRun, nxPlan, pnBubReact, tasteInsightsPage, sgdbFillOne) and pnBubMood, applyBackup, mergeBackup are
   extend() hooks now, and pnPowerUp / pnBubMood keep their timers in variables instead of on the function (v198). Checks:
     pnBubMood        no mood change during the intro cinema; the mood wears off
     renderTaste      Insights is drawn in the old frame and the swap is undone
     xFeatures        score migration + "games like" filter restored first
     qCounts/queueList "games like X" (S:) and genre (G:) filters count and list the right games
     pnPowerUp        not while resting; the cinema's wait-for-power-up; a second power-up ends the first
     nxContext        "they left off" on playing games, the facts
     wiAdd            the story so far is fetched; a note that sounds like the end asks if you finished
     nxPlan           the "Did you finish" and "Reviews for" notifications
     pnBubReact       the seasonal hat; a game marked already played isn't a hit
     tasteInsightsPage the ORC card and the Court card
     sgdbFillOne      screenshots and hours-to-beat are fetched after a picture fill
     pxRun            deal radar + the price watch after a check (new copy: base stubbed, no network)
     applyBackup/mergeBackup  the signature watch restarts (new copy: base stubbed)
     node tools/kit.mjs run tools/scenarios/extend-hooks-3.mjs --widths 412,859 (and --file old.html: same answers) */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, H = document.documentElement, NEW = typeof EXT === "object" && !!EXT.pnPowerUp;
    const spy = async (name, fn, keep, ret) => { const old = window[name], calls = [];
      window[name] = function () { calls.push([...arguments]); return keep ? old.apply(this, arguments) : ret; };
      try { await fn(calls); } finally { window[name] = old; } return calls; };
    const names = ["renderTaste", "xFeatures", "applyBackup", "mergeBackup", "qCounts", "queueList", "pnBubMood", "pnPowerUp", "nxContext", "wiAdd", "pxRun", "nxPlan", "pnBubReact", "tasteInsightsPage", "sgdbFillOne"];
    out.left = names.filter(n => /\b_[a-z0-9]+\.(apply|call)\(this/.test(String(window[n]))).join(",") || "none";
    out.fnState = /pnPowerUp\.(t2|end)|pnBubMood\.t\b/.test([...document.scripts].map(s => s.text).join("\n")) ? "still on the function" : "none";
    const g = S.games.find(x => x.state === "playing") || S.games[0];
    const b = document.getElementById("pnbub");

    /* pnBubMood */
    if (b) { window.pnCine = true; pnBubMood("sad", 400); const cine = b.classList.contains("sad"); window.pnCine = false;
      pnBubMood("sad", 400); const on = b.classList.contains("sad"); await wait(550);
      out.moodCine = !cine; out.moodOn = on; out.moodOff = !b.classList.contains("sad"); }

    /* renderTaste on Insights */
    const tk = SUBKEY.taste, tWas = S[tk];
    if (SUBS.taste.some(x => x[0] === "insights")) {
      goTab("taste"); await wait(300); S[tk] = "insights"; renderTaste(); await wait(100);
      out.tasteSwapUndone = tastePsych !== tasteInsightsPage;
      out.tasteInsights = !!document.querySelector("#p-taste .ix-hero, #p-taste [class^=ix-]");
      S[tk] = tWas; goTab("home"); await wait(300);
    } else out.tasteInsights = "n/a";

    /* xFeatures */
    const sm = await spy("scoreMigrate", async () => { const sr = await spy("simRestore", async () => { xFeatures(); }, true); out.xfSim = sr.length === 1; }, true);
    out.xfScore = sm.length === 1;

    /* qCounts / queueList: S: and G: filters */
    const genre = platList(g.genres)[0], fS = "S:" + g.id, fG = genre ? "G:" + genre : null, fq = S.qFilter;
    QFILTERS.push([fS, "like"]); if (fG) QFILTERS.push([fG, "genre"]);
    try {
      const c = qCounts(); out.qcS = c[fS] === simLocal(g, 99).length; if (fG) out.qcG = c[fG] === allGames().filter(x => gdGenreSet(x).has(gdKey(genre))).length;
      S.qFilter = fS; out.qlS = queueList().map(x => x.id).join() === simLocal(g, 99).map(x => x.g.id).join();
      if (fG) { S.qFilter = fG; out.qlG = queueList().map(x => x.id).join() === allGames().filter(x => gdGenreSet(x).has(gdKey(genre))).map(x => x.id).join(); }
    } finally { QFILTERS.splice(QFILTERS.findIndex(x => x[0] === fS), fG ? 2 : 1); S.qFilter = fq; }

    /* pnPowerUp */
    const calmNow = calm();
    window.pnRest = true; out.puRest = (await spy("pnFlame", async () => { pnPowerUp(false); })).length === 0; window.pnRest = false;
    let waited = 0; window.pnCineWaitPU = () => { waited++; };
    out.puRuns = (await spy("pnFlame", async () => { pnPowerUp(false); }, true)).length === 1;
    await wait(50); out.puCineWait = waited === 1 && !window.pnCineWaitPU;
    if (!calmNow) { await wait(1300); const c1 = !!document.getElementById("ssjcatch");
      pnPowerUp(false); await wait(100); out.puEndsFirst = c1 && !document.getElementById("ssjcatch");
      await wait(1300); document.getElementById("ssjcatch")?.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true })); await wait(100);
      out.puTapEnds = !document.getElementById("ssjcatch"); } else out.puEndsFirst = "n/a";

    /* nxContext */
    const wl = g.wasI; g.wasI = [{ x: "chapter 3", t: Date.now() }];
    let ctx; try { ctx = nxContext(); } finally { g.wasI = wl; }
    out.ctxLeftOff = ctx && Array.isArray(ctx.playing) && ctx.playing.length ? ctx.playing.some(t => t.includes("they left off: chapter 3")) : "n/a";
    out.ctxFacts = !!ctx && "facts" in ctx;

    /* wiAdd */
    const gp = S.games.find(x => x.state === "playing");
    if (gp) { const notes = (gp.wasI || []).slice(); let asked = [];
      const got = await spy("wiStoryGet", async () => { asked = await spy("fnAsk", async () => { wiAdd(gp, "beat the final boss, credits rolling"); await wait(400); }); });
      gp.wasI = notes; out.wiStory = got.length === 1; out.wiFinish = S.fnOff ? "n/a" : asked.length === 1; }

    /* nxPlan */
    /* something due for each: a game out today without reviews, and a playing game that sounds finished */
    const ug = S.games.find(x => !isPlayed(x) && !isDropped(x) && !x.comp && x.state !== "playing"), uWas = ug && { date: ug.date, rv: ug.rv, status: ug.status };
    if (ug) { ug.date = rvToday(); delete ug.rv; ug.status = ""; }
    const fw = window.fnWhy; window.fnWhy = () => ({ t: "you're near the end" });
    try { await spy("nxKinds", async () => { const plan = nxPlan(7) || [], x = !S.fnOff ? fnPick() : null;
      out.planFinish = x ? plan.some(n => n.tag === "finish-" + x[0].id) : "n/a";
      const rv = S.rvOff ? [] : S.games.filter(z => rvWant(z) && !rvHas(z) && rvAge(z) >= -RV_SOON && rvAge(z) <= 1).slice(0, 3);
      out.planReviews = rv.length ? rv.every(z => plan.some(n => n.tag === "rv-" + z.id) || new Date(z.date + "T00:00:00").getTime() + 864e5 * 2 < Date.now()) : "n/a";
      out.planSorted = plan.every((n, i) => !i || plan[i - 1].at <= n.at); }, false, new Set(["playing", "release"]));
    } finally { window.fnWhy = fw; if (ug) Object.assign(ug, uWas); }

    /* pnBubReact */
    out.reactHat = (await spy("ssnHat", async () => { pnBubReact(); })).length === 1;
    const pl = S.played || [], last = pl[pl.length - 1], lg = last && S.games.find(x => x.id === last.id);
    if (lg) { const snap = S.bubSnap; S.bubSnap = { ...(snap || {}), p: pl.length - 1 }; lg.hpFix = 1;
      const said = await spy("pnBubSay", async () => { pnBubReact(); });
      out.reactNoHit = !lg.hpFix && S.bubSnap.p === pl.length && said.some(a => /already played/.test(a[0] && a[0][0]));
      S.bubSnap = snap; } else out.reactNoHit = "n/a";

    /* tasteInsightsPage */
    const orc = await spy("orcCard", async () => { out.orc = String(tasteInsightsPage()).includes("data-t=orc"); }, false, "<i data-t=orc></i>");
    out.court = !dzOn("court") || !ixType()[0] ? "n/a" : String(tasteInsightsPage()).includes("dzc-table");

    /* sgdbFillOne: shots and hours after a fill (a made-up game, the network is off) */
    const fake = { id: "zz-fill-test", title: "Zz test", genres: [] };
    let hl = [];
    const sf = await spy("shotsFetch", async () => { hl = await spy("hlRun", async () => { sgdbFillOne(fake).catch(() => {}); await wait(3200); }); });
    out.fillShots = sf.length === 1 && sf[0][0] === fake && sf[0][1] === true; out.fillHours = hl.length === 1 && hl[0][1] === fake;

    /* pxRun + backups: hooks only, with the function itself stubbed (no network, no data change) */
    if (NEW) {
      const base = EXT.pxRun.base; EXT.pxRun.base = async () => ({ drops: [] });
      try { const dr = await spy("drRun", async () => { const sw = await spy("smWatch", async () => { await pxRun(true); await wait(1900); }); out.pxWatch = sw.length === 1; });
        out.pxRadar = dr.length === 1 && dr[0][0] === true;
        const dr2 = await spy("drRun", async () => { await pxRun(false, "only"); }); out.pxOnly = dr2.length === 0;
      } finally { EXT.pxRun.base = base; }
      for (const n of ["applyBackup", "mergeBackup"]) { const bb = EXT[n].base; EXT[n].base = () => "ok";
        try { let res; const sw = await spy("sigWatch", async () => { res = window[n]({}); }); out[n] = res === "ok" && sw.length === 1; }
        finally { EXT[n].base = bb; } }
    }
    return out;
  });
  const bad = Object.entries(r).filter(([k, v]) => v === false || ((k === "left" || k === "fnState") && v !== "none")).map(([k, v]) => k + (typeof v === "string" ? ":" + v : ""));
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "every layer still does its job" : "failed: " + bad.join(", ")}  ${JSON.stringify(r)}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
