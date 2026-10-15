/* Wrap clean-up, batch 3 (no version): the last 39 one-layer wraps are extend() hooks; withTransition (nothing called it)
   and its pnVT flag are gone; pnRail's "shown once" and wiAsk's hide timer are variables, not properties of the function.
   Each layer's job, checked by what it does (the same answers on the batch-2 copy with --file; checks marked "new only"
   stub the function itself through EXT and show n/a there):
     node tools/kit.mjs run tools/scenarios/extend-hooks-4.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, NEW = typeof EXT === "object" && !!EXT.bugReport;
    const spy = async (name, fn, keep, ret) => { const old = window[name], calls = [];
      window[name] = function () { calls.push([...arguments]); return keep ? old.apply(this, arguments) : typeof ret === "function" ? ret.apply(this, arguments) : ret; };
      try { await fn(calls); } finally { window[name] = old; } return calls; };
    /* new only: swap the function itself for a stub while fn runs */
    const stub = async (name, base, fn) => { if (!NEW || !EXT[name]) return "n/a"; const b = EXT[name].base; EXT[name].base = base;
      try { return await fn(); } finally { EXT[name].base = b; } };
    const names = ["stopTimer", "dyClose", "nxHomeLine", "nxOpenSettings", "pnBadgeStats", "nxPost", "bugLayoutPass", "nxVoice", "lookText", "nudgeBar", "wiCard",
      "wiRefresh", "wiAsk", "openSimilar", "pxDeals", "pxSetHtml", "pxSetWire", "nxWindow", "etaOf", "scoreWrite", "capCard", "pxTake", "sgdbTop", "sgAdd", "hubStep",
      "hubDragStart", "gpBox", "dyBox", "fill", "rawgLookup", "gdGlance", "pxCard", "pxItem", "gdProgress", "pnRail", "onDeckStrip", "lagWatch", "startDrag", "hubPaint", "bugReport"];
    out.left = names.filter(n => /\b_[a-z0-9]+\.(apply|call)\(this|\b_wt\(/.test(String(window[n]))).join(",") || "none";
    const src = [...document.scripts].map(s => s.text).join("\n");
    out.fnState = /pnRail\.shown|wiAsk\.t\b/.test(src) ? "still on the function" : "none";
    out.noWithTransition = typeof window.withTransition === "undefined";
    const g = S.games.find(x => x.state === "playing") || S.games[0], gb = S.games.find(x => x.state === "backlog") || S.games[1];

    /* stopTimer: the session is logged before the redraw; a long one asks for a diary note unless quiet */
    { const was = S.timer, dOn = S.diaryOn; S.timer = { id: g.id, from: Date.now() - 30 * 6e4 }; S.diaryOn = true; let diary = [];
      const ss = await spy("sessAdd", async () => { diary = await spy("openDiary", async () => { stopTimer(); await wait(800); }); });
      out.timerSess = ss.length === 1 && ss[0][0] === g.id; out.timerDiary = diary.length === 1;
      S.timer = { id: g.id, from: Date.now() - 30 * 6e4 };
      const dq = await spy("sessAdd", async () => { diary = await spy("openDiary", async () => { stopTimer(true); await wait(800); }); });
      out.timerQuiet = dq.length === 1 && diary.length === 0; S.timer = was; S.diaryOn = dOn; dyClose(); await wait(350); }

    /* dyClose: Home redraws after a "today" sheet closes */
    qdToday = 1; out.dyCloseHome = (await spy("renderHome", async () => { dyClose(); await wait(400); }, true)).length >= 1 && qdToday === 0;

    /* nxHomeLine: names the game to play next */
    { const pick = onDeck(1)[0], h = nxHomeLine(); out.homeLine = !pick || !h ? "n/a" : h.includes(`data-home-open="${pick.id}"`); }

    /* nxOpenSettings: opens the section it points at */
    out.openSettings = (await spy("setMoreOpen", async () => { nxOpenSettings("#s-motion"); await wait(300); })).length === 1;
    goTab("home"); await wait(400);

    /* pnBadgeStats: the duel/climb numbers */
    { const s = pnBadgeStats(); out.badgeStats = ["duels", "climb", "dDays", "edge", "top3"].every(k => typeof s[k] === "number"); }

    /* nxPost: a failed helper call is reported and still fails */
    { const f = window.fetch; window.fetch = () => Promise.reject(new Error("boom-test")); let rejected = false;
      const rep = await spy("bugReport", async () => { try { await nxPost("/why", {}); } catch (e) { rejected = true; } });
      window.fetch = f; out.postReported = rep.some(a => a[0] === "helper" && /Helper \/why failed/.test(a[1])); out.postRejects = rejected; }

    /* bugLayoutPass: the look checks run on the open screen */
    out.layoutLook = !bugOn() ? "n/a" : (await spy("bugLook", async () => { bugLayoutPass(); })).length === 1;

    /* nxVoice: a persona's own line is left as written */
    out.voiceRaw = nxVoice(NX_RAW + "as written") === "as written"; out.voiceStr = typeof nxVoice(42) === "string";

    /* lookText: the same screen asked twice within 400ms is answered from memory */
    { const root = document.querySelector(".panel.on") || document.body; const a = lookText(root), b = lookText(root); await wait(450); const c = lookText(root);
      out.lookMemo = a === b && (typeof a !== "object" || a !== c); }

    /* nudgeBar: "save your restore code" once the cloud backup has run */
    { const was = { ...CB }; CB.on = true; CB.at = Date.now(); CB.saved = false; const sn = S.nudgeSnooze; delete S.nudgeSnooze;
      out.nudgeCode = nudgeBar().includes("nudge-cbsave"); CB = was; S.nudgeSnooze = sn; }

    /* wiCard / wiRefresh / wiAsk: the story so far */
    out.wiCardStory = await (async () => { let a, b; await spy("wiStoryHtml", async () => { a = wiCard(g); b = wiCard(g, true); }, false, "<i data-t=ws></i>");
      return !a ? "n/a" : a.includes("data-t=ws") && !String(b).includes("data-t=ws"); })();
    out.wiRefreshPaint = (await spy("wiStoryPaint", async () => { wiRefresh(g); })).some(a => a[0] === g);
    { let got = []; await spy("wiPick", async () => { got = await spy("wiStoryGet", async () => { wiAsk(); await wait(100); }); }, false, g);
      out.wiAskStory = got.length === 1 && got[0][0] === g; document.getElementById("wiask")?.remove(); }

    /* openSimilar: Steam's "More like this" under the local picks */
    { let asked = [];
      await spy("stMore", async c => { asked = c; openSimilar(gb.id); await wait(300); }, false, () => Promise.resolve({ l: [] }));
      const box = dyBox(); out.similarSteam = !stIdOf(gb) ? "n/a" : !box.querySelector(".sim-new") ? "n/a" : asked.length === 1 && !!box.querySelector(".st-more");
      dyClose(); await wait(350); }

    /* pxDeals / pxSetHtml / pxSetWire: deal radar */
    { const dl = S.drList; S.drList = [{ id: 10, name: "Test deal", pct: 60, by: "a game you love" }];
      if (drOn()) { pxDeals(); await wait(300); out.dealsRadar = !dyBox().querySelector("[data-pxnow]") ? "n/a" : !!dyBox().querySelector(".pxd-h"); dyClose(); await wait(350); } else out.dealsRadar = "n/a";
      S.drList = dl;
      const html = pxSetHtml(); out.setHtml = html.includes('id="s-dr"') && html.includes('id="s-rd"');
      const box = document.createElement("div"); box.innerHTML = html; document.body.appendChild(box);
      pxSetWire(); out.setWire = typeof document.getElementById("s-dr").onclick === "function" && typeof document.getElementById("s-rd").onclick === "function"; box.remove(); }

    /* nxWindow: later evenings in Ramadan */
    out.windowRamadan = await (async () => { let wv; await spy("ssnNow", async () => { wv = nxWindow(); }, false, { k: "ramadan" }); return wv[1] === 23 && wv[0] >= 20; })();

    /* etaOf: the first forecast is kept */
    { const eg = S.games.find(x => { const e = etaOf(x); return e && e.left > 0; });
      if (eg) { const e0 = eg.eta0; delete eg.eta0; etaOf(eg); out.etaFirst = !!eg.eta0; eg.eta0 = e0; } else out.etaFirst = "n/a"; }

    /* scoreWrite: Nexi's guess against your score */
    { const cap = gb.cap; gb.cap = { pr: 7.5 }; const rec = { id: gb.id };
      const said = await spy("pnBubSay", async () => { scoreWrite(rec, 8); await wait(1500); });
      out.scoreGuess = gb.cap.got === 8 && said.some(a => /guessed 7\.5/.test(a[0] && a[0][0])); gb.cap = cap; }

    /* capCard: the finish forecast against the real finish */
    { const pg = S.games.find(x => isPlayed(x) && playedRec(x) && playedRec(x).t);
      if (pg) { const cap = pg.cap; pg.cap = { ...(cap || {}), e0: playedRec(pg).t };
        const html = capCard(pg); out.capForecast = !html || !html.includes('class="cap-r"') ? "n/a" : html.includes("vs my finish forecast"); pg.cap = cap; }
      else out.capForecast = "n/a"; }

    /* pxTake (new only): games stuck on "not found" are fixed from RAWG */
    out.pxTakeFix = await stub("pxTake", () => "ok", async () => { const f = await spy("pxRawgFix", async () => { pxTake({ [gb.id]: {} }); await wait(1600); });
      return pxRwNeed(gb) ? f.length === 1 : f.length === 0; });

    /* sgdbTop: the stores are asked too */
    { const f = window.fetch; window.fetch = () => Promise.reject(new Error("offline-test"));
      const as = await spy("artStores", async () => { try { await sgdbTop("Zz test"); } catch (e) {} }, false, () => Promise.resolve({ pics: [] }));
      window.fetch = f; out.topStores = as.length === 1 && as[0][0] === "Zz test"; }

    /* sgAdd (new only): a picked game keeps the screenshots it came with */
    out.addShots = await stub("sgAdd", async () => ({ id: "zz-add", title: "Zz" }), async () => { const ng = await sgAdd({ _shots: [{ u: "a.jpg" }], _src: "Test" });
      return !!ng && ng.shots && ng.shots[0].u === "a.jpg" && ng.shotsSrc === "Test"; });

    /* hubStep / hubDragStart (new only): swiping behind an open sheet is reported */
    { const shv = document.createElement("div"); shv.id = "shv"; document.body.appendChild(shv);
      for (const n of ["hubStep", "hubDragStart"]) out[n] = await stub(n, () => {}, async () => (await spy("bugReport", async () => { window[n](); })).some(a => /swiped behind an open sheet/.test(a[1])));
      shv.remove(); }

    /* gpBox / dyBox: sheets don't swipe the game page */
    out.boxNoHub = !!dyBox()._noHub && (typeof gpBox !== "function" || !!gpBox()._noHub);

    /* fill: RAWG's average hours aren't put in as yours */
    { const fh = document.getElementById("f-hours"); out.fillHours = !fh ? "n/a" : await stub("fill", () => { fh.value = "12"; }, async () => { fill({ playtime: 12 }); return fh.value === ""; }); }

    /* rawgLookup (new only): RAWG players' hours are dropped */
    out.rawgHours = await stub("rawgLookup", async () => ({ hours: 30, hoursFrom: "RAWG players" }), async () => { const d = await rawgLookup("x"); return d.hours === 0 && d.hoursFrom === ""; });

    /* gdGlance: the hours tile opens How long to beat */
    { const gg = S.games.find(x => !x.comp); out.glanceHl = gdGlance(gg).includes(`data-hltb="${gg.id}"`); }

    /* pxCard / pxItem: a game you own isn't priced */
    if (pxOn()) { const own = gb.own; gb.own = true; out.ownCard = pxCard(gb).includes("You own it"); out.ownItem = pxItem(gb).st === -1; gb.own = own;
      let c; await spy("smLine", async () => { c = pxCard(gb); }, false, "<i data-t=sm></i>"); out.smLine = !c ? "n/a" : c.includes("data-t=sm"); } else out.ownCard = "n/a";

    /* gdProgress: the diary card */
    { let o; await spy("dwCard", async () => { o = gdProgress(g); }, false, "<i data-t=dw></i>"); out.progressDiary = String(o).includes("data-t=dw"); }

    /* pnRail: the rail's art repaints */
    out.railPaint = (await spy("rlvPaint", async () => { pnRail(); })).length === 1;

    /* onDeckStrip: the new On deck strip, or the old one when it's off */
    if (dzOn("deck") && !S.deckOff && onDeck(3).length) { out.deckNew = onDeckStrip().includes("deck dzd"); S.deckOff = true; out.deckOld = !String(onDeckStrip()).includes("deck dzd"); S.deckOff = false; }
    else out.deckNew = "n/a";

    /* lagWatch: the GPU check follows */
    out.lagGpu = lagTr ? "n/a" : (await spy("gpuCheck", async () => { lagWatch("Test"); await wait(400); })).some(a => a[0] === "Test");

    /* startDrag (new only): a lifted row holds the list still */
    out.dragBlock = await stub("startDrag", () => { drag = { test: 1 }; }, async () => { const ae = window.addEventListener, seen = [];
      window.addEventListener = function (t, f, o) { if (t === "touchmove") seen.push(f); return ae.apply(this, arguments); };
      try { startDrag(document.body, 0); } finally { window.addEventListener = ae; drag = null; removeEventListener("touchmove", dragBlock, true); }
      return seen.includes(dragBlock); });

    /* hubPaint: the frame probe starts */
    out.hubProbe = await stub("hubPaint", () => {}, async () => (await spy("prbStart", async () => { hubPaint(); })).some(a => a[0] === "h"));

    /* bugReport (new only): the self-test only hears its own slow reports */
    { let n = 0; out.selfTest = await stub("bugReport", () => { n++; }, async () => { window.pnSelfTest = true;
        try { bugReport("slow", "x", { at: "other" }); bugReport("slow", "x", { at: "selftest 1" }); bugReport("look", "y", {}); } finally { window.pnSelfTest = false; }
        return n === 2; }); }
    return out;
  });
  const bad = Object.entries(r).filter(([k, v]) => v === false || ((k === "left" || k === "fnState") && v !== "none")).map(([k, v]) => k + (typeof v === "string" ? ":" + v : ""));
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "every layer still does its job" : "failed: " + bad.join(", ")}  ${JSON.stringify(r)}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
