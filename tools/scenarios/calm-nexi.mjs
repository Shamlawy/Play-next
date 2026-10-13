/* v274 (#447, the owner: "remove all and fix 1 2 3"):
   1. unfolded, Home shows one Nexi (the rail's seated Nexi steps out on Home, back on other tabs, back while he speaks)
   2. no idle "life": 20s untouched with the rail hidden → no random moods (yawn/sneeze/whistle/stretch/happy), no wandering
   3. opening the app with no news → he says nothing; the hot take is on his wheel (🔥) and only there
   4. no queue art backdrop: #bgart/#bgart2/#bgtint and applyBg are gone
     node tools/kit.mjs run tools/scenarios/calm-nexi.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  const vis = sel => p.page.evaluate(s => { const e = document.querySelector(s); return !!e && getComputedStyle(e).display !== "none" && getComputedStyle(e).visibility !== "hidden"; }, sel);
  /* 4 */
  const gone = await p.page.evaluate(() => !document.getElementById("bgart") && !document.getElementById("bgtint") && typeof applyBg === "undefined" && typeof bgBake === "undefined");
  if (!gone) bad.push("backdrop still there");
  /* 3: the seed's bubDay isn't today, so the first open had the daily brief: open again the same day → silence */
  await p.page.evaluate(() => { S.bubDay = new Date().toLocaleDateString("en-CA"); S.nxPartyDay = S.bubDay; S.bubMet = 1; saveNow ? saveNow() : save(); });
  await p.page.reload(); await p.page.waitForTimeout(400);
  await p.page.evaluate(() => { window._said = []; const t0 = performance.now(), o = pnBubSay; pnBubSay = function (l) { window._said.push(l ? l[0] : "(random)"); return o.apply(this, arguments); }; });
  /* the greeting runs when he (re)appears (pnBubShow): ask for it directly too, the way floating Nexi starts up */
  await p.page.evaluate(() => { try { pnBubShow(); } catch (e) {} });
  await p.page.waitForTimeout(12000);
  const said = await p.page.evaluate(() => window._said);
  if (said.length) bad.push("spoke on a plain open: " + said.join(" / "));
  /* 2: rail hidden (floating Nexi), 20s untouched */
  if (w >= 620) {
    /* hide the rail (he glides out and lands with a smile: that's our doing), then watch once he has settled */
    await p.page.evaluate(() => { if (typeof railHideNow === "function") railHideNow(); }); await p.page.waitForTimeout(3000);
    await p.page.evaluate(() => { window._moods = []; const o = pnBubMood; pnBubMood = function (c) { window._moods.push(c); return o.apply(this, arguments); }; });
    const x0 = await p.page.evaluate(() => [pnBubX, pnBubY].join(","));
    await p.page.waitForTimeout(20000);
    const r = await p.page.evaluate(() => ({ moods: window._moods, at: [pnBubX, pnBubY].join(","), peek: document.getElementById("pnbub").classList.contains("peek") }));
    const idle = r.moods.filter(m => ["yawn", "sneeze", "whistle", "stretch", "happy"].includes(m));
    if (idle.length || r.at !== x0 || r.peek) bad.push(`idle life: moods ${idle.join(",") || "-"}, moved ${x0}→${r.at}, peek ${r.peek}`);
    await p.page.evaluate(() => { S.railHide = false; saveNow ? saveNow() : save(); });
    await p.page.reload(); await p.page.waitForTimeout(2500);
  }
  /* 1 */
  if (w >= 620) {
    await p.step("home", `goTab("home")`, 1200);
    const seat = await p.page.evaluate(() => document.documentElement.classList.contains("nxseat"));
    const home = await vis("#pnbub"), face = await vis("#p-home .nxh-face");
    await p.step("games", `goTab("games")`, 1200);
    const games = await vis("#pnbub");
    await p.step("home", `goTab("home")`, 1200);
    await p.page.evaluate(() => pnBubSay(["test line", ""])); await p.page.waitForTimeout(300);
    const speaking = await vis("#pnbub");
    if (seat && (home || !face || !games || !speaking)) bad.push(`Home: rail Nexi ${home ? "shown" : "hidden"}, Home face ${face}, Games: ${games}, speaking on Home: ${speaking}`);
    if (!seat) bad.push("not seated in the rail (test setup)");
  }
  /* 3b: 🔥 on the wheel */
  const take = await p.page.evaluate(async () => { pnBubWheel(); await new Promise(r => setTimeout(r, 400));
    const b = document.querySelector('#bubwheel [data-a="take"]'); if (!b) return "no 🔥 button";
    window._said = []; const o = pnBubSay; pnBubSay = function (l) { window._said.push(l ? l[0] : ""); return o.apply(this, arguments); };
    b.click(); return window._said[0] || "nothing said"; });
  if (!/hot take/.test(take)) bad.push("wheel 🔥: " + take);
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "one Nexi on Home, no idle life, quiet on a plain open, hot take on the wheel, no backdrop" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
