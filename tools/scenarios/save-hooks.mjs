/* v277: save() had 10 wraps; now onSave({ before, after }) hooks. Everything that rode on a save still happens:
   logged hours → logAt + lgH (one hook, was two), cloud backup marked dirty, a badge check queued, the CLEAR! banner when a
   game is finished, the boss HUD on a real backlog change, and a deleted game is not a hit (pnSilentHP, no HUD, max drops).
     node tools/kit.mjs run tools/scenarios/save-hooks.mjs */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {};
    const g = S.games.find(x => (x.state || "") === "playing") || S.games[0];
    /* 1. logged hours */
    g.logged = (+g.logged || 0) + 2; save();
    out.logAt = !!g.logAt && Date.now() - g.logAt < 5000;
    out.lgH = Array.isArray(g.lgH) && g.lgH.length && g.lgH.at(-1)[1] === 2;
    /* 2. cloud backup dirty */
    out.cloud = typeof cbDirty === "undefined" ? "n/a" : cbDirty === true;
    /* 3. badge check queued */
    out.badge = typeof pnBadgeT === "undefined" ? "n/a" : pnBadgeT > 0;
    /* 4. a deleted game is no hit: no HUD, silent flag restored */
    document.getElementById("fxhud")?.remove();
    const b = S.games.find(x => x.state === "backlog" && x !== g); const max0 = S.fxBossMax;
    S.games = S.games.filter(x => x !== b); save(); await wait(1200);
    out.deleteQuiet = !document.getElementById("fxhud") && !window.pnSilentHP;
    out.maxDropped = !max0 || S.fxBossMax <= max0;
    /* 5. finishing a game you were playing: CLEAR! and the HUD */
    document.querySelectorAll(".fxclr, #fxhud").forEach(e => e.remove());
    g.state = "played"; (S.played ||= []).push({ id: g.id, real: 8, t: new Date().toISOString().slice(0, 10) }); save(); await wait(900);
    out.clear = !!document.querySelector(".fxclr") || document.documentElement.classList.contains("still");
    for (let i = 0; i < 40 && !document.getElementById("fxhud"); i++) await wait(250);   /* it waits for CLEAR! to finish */
    out.hud = !!document.getElementById("fxhud") || document.documentElement.classList.contains("still");
    /* 6. no wraps left */
    out.wraps = /_sv\.apply|pnSaveRaw\.apply/.test(save.toString()) ? "still wrapped" : "none";
    return out;
  });
  const bad = Object.entries(r).filter(([k, v]) => v === false || v === "still wrapped").map(([k]) => k);
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${JSON.stringify(r)}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
