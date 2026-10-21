/* v322 (the owner: "music doesn't work on any game, I hear nothing"): the book's music end to end, the way the phone
   gets it: the REAL helper (POST /ost, GET /ost/clip) and Apple's clip, decoded and playing in Chromium with Sounds (tap
   sounds) off. Only on a machine that can reach them: ost-probe.yml runs it on every helper change,
     FAKE_HELPER=live CHROME=/usr/bin/google-chrome node tools/kit.mjs run tools/scenarios/skills-book-music-live.mjs --widths 412
   in real Chrome: Playwright's own Chromium has no AAC (Apple's clips), so it can never decode one.
   A well-known title only (a public repo's logs are public); this container can't reach the helper. */
export default async ({ p, w, seed, fail }) => {
  if (process.env.FAKE_HELPER !== "live") return fail("run with FAKE_HELPER=live (it needs the real helper)");
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(Object.assign(seed(v, 60), { nxEvo: 99, sound: false })));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  const T = process.env.OST_TITLE || "Hollow Knight";
  await p.page.evaluate(T => { const last = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).pop();
    S.games.find(g => g.id === last.id).title = T; saveNow();
    localStorage.setItem("gq:book", JSON.stringify({ [bookNorm(T)]: { skills: [{ ic: "time", n: "Patience", lv: 2, txt: "A made-up moment." }] } }));
    localStorage.removeItem("gq:ost"); bookStore = null; document.documentElement.classList.add("still"); }, T);
  const box = await p.page.evaluate(() => ({ w: innerWidth }));
  await p.tap(box.w / 2, 40);
  await p.step("book", `goTab("taste"); goSub("taste", "book")`, 1500);
  let st = null;
  for (let i = 0; i < 30; i++) {
    st = await p.page.evaluate(() => ({ g: SKB_MUS.g, srcs: SKB_MUS.srcs.length, gain: SKB_MUS.out ? +SKB_MUS.out.gain.value.toFixed(2) : 0, tr: SKB_MUS.tr && SKB_MUS.tr.n,
      ac: typeof AC !== "undefined" && AC ? AC.state : "none", ost: localStorage.getItem("gq:ost") }));
    if (st.srcs && st.gain > .05) break;
    await p.page.waitForTimeout(500);
  }
  console.log(`  ${w}: "${T}" → track ${JSON.stringify(st.tr)}, ${st.srcs} source(s), gain ${st.gain}, audio ${st.ac}`);
  if (!st.srcs || st.gain <= .05) {
    /* why: the clip's own fetch and decode, step by step (a CORS refusal, a codec the browser lacks) */
    const why = await p.page.evaluate(async () => { const M = JSON.parse(localStorage.getItem("gq:ost") || "{}"), e = Object.values(M)[0]; if (!e || !e.l.length) return "no tracks";
      try { const r = await fetch(HELPER + "/ost/clip?u=" + encodeURIComponent(e.l[0].u)); if (!r.ok) return "clip " + r.status;
        const ab = await r.arrayBuffer(); const b = await new OfflineAudioContext(1, 1, 44100).decodeAudioData(ab); return `decoded ${b.duration.toFixed(1)}s from ${location.origin}`; }
      catch (x) { return `${x.name}: ${x.message} (page ${location.origin})`; } });
    console.log("  why: " + why);
  }
  if (!st.srcs || st.gain <= .05) fail(`no music from the real helper: ${JSON.stringify(st).slice(0, 600)} errors: ${p.errors.join(" | ").slice(0, 400)}`);
};
