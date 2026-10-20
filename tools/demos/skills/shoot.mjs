/* Demo (Oct 20, idea M41 "what a game builds in you"): draws three looks of the section into the real app's game page
   (a played and a not-played made-up game) and a "Your build" card on Insights, then saves screenshots.
     SHOTS=tools/.cache/skills node tools/kit.mjs run tools/demos/skills/shoot.mjs --widths 412,859
   Made-up games and words only (public repo). Not part of the app. */
import fs from "node:fs";
import path from "node:path";
const CSS = `
.skd { margin: 12px 0 0; padding: 14px 14px 15px; border-radius: 20px; background: var(--u-card); border: 1px solid var(--u-line); }
.skd-h { display: flex; align-items: center; gap: 8px; font: 600 13px/1.2 "Poppins", system-ui, sans-serif; color: var(--ice); }
.skd-h svg { width: 18px; height: 18px; color: var(--volt); }
.skd-h small { margin-left: auto; font: 500 11.5px/1.2 "Poppins", system-ui, sans-serif; color: var(--mute); white-space: nowrap; }
.skd-ic { width: 22px; height: 22px; flex: none; }
.skd p { margin: 0; }
.skd-try { font: 500 12px/1.4 "Poppins", system-ui, sans-serif; color: var(--mute); margin-top: 6px !important; }
.skd-try b { color: var(--ice); font-weight: 600; }
.skd-from { font: 500 12.5px/1.45 "Poppins", system-ui, sans-serif; color: var(--ice); opacity: .86; margin-top: 4px !important; }
/* A: perk tiles */
.skA { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; margin-top: 12px; }
.skA > div { padding: 12px; border-radius: 14px; background: var(--u-card2); border: 1px solid var(--u-line); }
.skA-t { display: flex; align-items: center; gap: 10px; }
.skA-d { width: 38px; height: 38px; display: grid; place-items: center; flex: none; position: relative; color: #fff; }
.skA-d::before { content: ""; position: absolute; inset: 4px; transform: rotate(45deg); border-radius: 7px; background: var(--accent); }
.skA-d svg { position: relative; width: 20px; height: 20px; }
.skA-n { font: 700 14px/1.2 "Poppins", system-ui, sans-serif; color: var(--ice); letter-spacing: -.015em; }
.skA-l { display: flex; gap: 3px; margin-top: 5px; }
.skA-l i { width: 18px; height: 5px; border-radius: 3px; background: var(--u-line2); }
.skA-l i.on { background: var(--volt); }
.skA-l span { font: 600 11px/5px "Poppins", system-ui, sans-serif; color: var(--mute); margin-left: 5px; white-space: nowrap; }
.skA-x { margin-top: 10px; padding-top: 9px; border-top: 1px solid var(--u-line); }
/* B: level-up sheet */
.skB { margin-top: 10px; }
.skB > div { display: grid; grid-template-columns: 26px 1fr auto; gap: 4px 10px; align-items: center; padding: 11px 0; border-top: 1px solid var(--u-line); }
.skB > div:first-child { border-top: 0; }
.skB svg { width: 22px; height: 22px; color: var(--volt); }
.skB-n { font: 700 14px/1.2 "Poppins", system-ui, sans-serif; color: var(--ice); letter-spacing: -.015em; }
.skB-v { font: 700 20px/1 "Poppins", system-ui, sans-serif; color: var(--volt); white-space: nowrap; }
.skB-v small { font-size: 11px; color: var(--mute); font-weight: 600; margin-left: 2px; }
.skB-bar { grid-column: 2 / 4; height: 6px; border-radius: 3px; background: var(--u-line2); overflow: hidden; }
.skB-bar i { display: block; height: 100%; border-radius: 3px; background: linear-gradient(90deg, var(--accent), var(--volt)); }
.skB-x { grid-column: 2 / 4; }
.skB-top { display: flex; align-items: baseline; gap: 8px; margin-top: 10px; font: 700 22px/1.1 "Poppins", system-ui, sans-serif; color: var(--ice); letter-spacing: -.015em; }
.skB-top small { font: 500 12px/1.2 "Poppins", system-ui, sans-serif; color: var(--mute); }
/* C: codex entries */
.skC { margin-top: 10px; display: grid; gap: 8px; }
.skC > div { display: grid; grid-template-columns: 40px 1fr; gap: 10px; padding: 12px; border-radius: 14px; background: var(--u-card2); border: 1px solid var(--u-line); position: relative; overflow: hidden; }
.skC > div::before { content: ""; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--accent); }
.skC-i { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; background: rgba(var(--vrgb), .14); color: var(--volt); }
.skC-i svg { width: 22px; height: 22px; }
.skC-k { font: 600 11px/1.2 "Poppins", system-ui, sans-serif; color: var(--volt); white-space: nowrap; }
.skC-n { font: 700 14px/1.25 "Poppins", system-ui, sans-serif; color: var(--ice); margin-top: 2px !important; letter-spacing: -.015em; }
.skC-lock { display: flex; align-items: center; gap: 6px; font: 500 12px/1.35 "Poppins", system-ui, sans-serif; color: var(--mute); margin-top: 6px !important; }
.skC-lock svg { width: 14px; height: 14px; flex: none; }
.skd-you { margin-top: 12px; padding: 11px 12px; border-radius: 14px; border: 1px dashed var(--u-line2); font: 500 12.5px/1.4 "Poppins", system-ui, sans-serif; color: var(--mute); }
.skd-you b { color: var(--ice); font-weight: 600; }
.skd-vote { display: flex; gap: 6px; margin-top: 10px; font: 500 11.5px/1.2 "Poppins", system-ui, sans-serif; color: var(--mute); align-items: center; }
/* Insights: your build */
.skI-row { display: grid; grid-template-columns: 130px 1fr 34px; gap: 10px; align-items: center; margin-top: 9px; font: 600 12.5px/1.2 "Poppins", system-ui, sans-serif; color: var(--ice); }
.skI-row span:first-child { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.skI-row b { height: 8px; border-radius: 4px; background: var(--u-line2); overflow: hidden; display: block; }
.skI-row b i { display: block; height: 100%; background: var(--volt); border-radius: 4px; }
.skI-row b.lo i { background: var(--u-line2); }
.skI-row small { color: var(--mute); font-weight: 600; text-align: right; }
.skI-tip { margin-top: 12px; padding: 11px 12px; border-radius: 14px; background: rgba(var(--vrgb), .1); font: 500 12.5px/1.45 "Poppins", system-ui, sans-serif; color: var(--ice); }
`;
/* drawn icons, 24x24, fill-only currentColor, one part at .35 (v247/v297 style) */
const IC = {
  time: `<path opacity=".35" d="M12 2a10 10 0 1 0 0 20a10 10 0 0 0 0-20z"/><path d="M11 6h2v5.6l3.7 2.2-1 1.7L11 12.7z"/>`,
  bond: `<path opacity=".35" d="M16 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 0 0 0 7zm0 2c-1.3 0-2.6.3-3.6.8C14 15 15 16.6 15 18.5V20h8v-2c0-2.8-3.6-5-7-5z"/><path d="M8.5 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 0 0 0 7zM1 20v-1.5C1 15.5 4.6 13 8.5 13S16 15.5 16 18.5V20z"/>`,
  grit: `<path opacity=".35" d="M2 21L9.5 7l3.5 6.5 2.5-4L22 21z"/><path d="M9.5 7L13 13.5l-2 1.3-1.5-2.3L7 14.5 9.5 7zM15.5 9.5L17.6 13l-1.6 1-1.4-2z"/>`,
  eye: `<path opacity=".35" d="M12 5C6.5 5 2.7 9 1.5 12c1.2 3 5 7 10.5 7s9.3-4 10.5-7C21.3 9 17.5 5 12 5z"/><path d="M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8z"/>`,
  heart: `<path opacity=".35" d="M12 21s-9-5.6-9-12a5 5 0 0 1 9-3a5 5 0 0 1 9 3c0 6.4-9 12-9 12z"/><path d="M12 9.5l1.4 2.9 3.1.4-2.3 2.1.6 3.1-2.8-1.6-2.8 1.6.6-3.1-2.3-2.1 3.1-.4z"/>`,
  spark: `<path opacity=".35" d="M12 2l2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"/><path d="M12 7l1.3 3.7L17 12l-3.7 1.3L12 17l-1.3-3.7L7 12l3.7-1.3z"/>`,
  tactic: `<path opacity=".35" d="M3 3h18v18H3z"/><path d="M6 6h5v5H6zm7 7h5v5h-5z"/>`,
  search: `<path opacity=".35" d="M10 3a7 7 0 1 0 0 14a7 7 0 0 0 0-14z"/><path d="M15.5 14.1l6 6-1.4 1.4-6-6zM10 6a4 4 0 0 1 4 4h-2a2 2 0 0 0-2-2z"/>`,
  clock: `<path opacity=".35" d="M6 2h12v2l-5 8 5 8v2H6v-2l5-8-5-8z"/><path d="M8 20h8l-4-6z"/>`,
  moon: `<path opacity=".35" d="M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z"/><path d="M17 3l.9 2.1L20 6l-2.1.9L17 9l-.9-2.1L14 6l2.1-.9z"/>`,
  lock: `<path opacity=".35" d="M5 10h14v11H5z"/><path d="M8 10V7a4 4 0 0 1 8 0v3h-2V7a2 2 0 0 0-4 0v3z"/>`,
  you: `<path opacity=".35" d="M4 4h16v12H8l-4 4z"/><path d="M8 8h8v2H8zm0 3h5v2H8z"/>`
};
const svg = k => `<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">${IC[k]}</svg>`;
/* made-up games by default; DATA=<json file> (kept out of the repo) swaps in other games and words */
const DEF = { games: [
  { id: "g0", key: "played", played: 1, accent: "#7C5CFF", hours: 46, skills: [
    { ic: "time", n: "Time management", lv: 3, txt: "The 60-day voyage: you can't train the crew, repair the ship and chase the side missions all before the eclipse", tr: "plan your week with 3 slots a day, like the voyage board" },
    { ic: "bond", n: "Keeping bonds alive", lv: 3, txt: "Mira's arc: skip her for two chapters and she leaves the crew before the finale", tr: "pick one person this week and check in twice" },
    { ic: "grit", n: "Not giving up", lv: 2, txt: "The Storm Captain: three phases, and the third only opens once you stop rushing", tr: "when something fails, change one thing and try again" },
    { ic: "heart", n: "Letting go", lv: 1, story: 1, txt: "The ending at the lighthouse, where Kael chooses to stay behind", tr: "" }] },
  { id: "g7", key: "notplayed", played: 0, accent: "#7C5CFF", hours: 46, skills: [
    { ic: "time", n: "Time management", lv: 3, txt: "A calendar where every day forces a choice", tr: "plan your week with 3 slots a day" },
    { ic: "bond", n: "Keeping bonds alive", lv: 3, txt: "Friendships that only grow if you keep showing up", tr: "pick one person this week and check in twice" },
    { ic: "grit", n: "Not giving up", lv: 2, txt: "A tough fight halfway through that rewards learning it", tr: "when something fails, change one thing and try again" },
    { ic: "heart", n: "Letting go", lv: 1, story: 1, txt: "The story is about accepting what you can't change", tr: "" }] }],
  build: { meta: "11 played games", rows: [["Planning ahead", 86], ["Time management", 72], ["Keeping bonds alive", 64], ["Not giving up", 58], ["Spotting patterns", 40], ["Reaction speed", 12, 1], ["Teamwork", 6, 1]],
    tip: "you rarely train reaction speed. Rift Runner in your queue is all about it." } };
const DATA = process.env.DATA ? JSON.parse(fs.readFileSync(process.env.DATA, "utf8")) : DEF;
const AMT = ["", "some", "a lot", "a core part"];
const lv = n => `<div class="skA-l">${[1, 2, 3].map(i => `<i class="${i <= n ? "on" : ""}"></i>`).join("")}<span>${AMT[n]}</span></div>`;
const head = (meta) => `<div class="skd-h">${svg("spark")}<span>What it builds in you</span><small>${meta}</small></div>`;
const you = played => played ? `<div class="skd-you">${svg("you").replace("<svg", '<svg style="width:14px;height:14px;vertical-align:-2px;margin-right:6px"')}<b>Nexi will ask:</b> did anything stick with you? Your answer goes here.</div>` : "";
const tryL = s => s.tr ? `<p class="skd-try"><b>Try:</b> ${s.tr}</p>` : "";
const LOOKS = {
  A: g => `<section class="skd" style="--accent:${g.accent}">${head(g.played ? "from the moments that taught it" : "no spoilers until you finish")}
    <div class="skA">${g.skills.map(s => `<div><div class="skA-t"><span class="skA-d">${svg(s.ic)}</span><div><p class="skA-n">${s.n}</p>${lv(s.lv)}</div></div>
      <div class="skA-x"><p class="skd-from">${s.txt}</p>${tryL(s)}</div></div>`).join("")}</div>${you(g.played)}</section>`,
  B: g => `<section class="skd" style="--accent:${g.accent}">${head(g.played ? "finished" : "if you play it")}
    <p class="skB-top">${g.played ? `You levelled up ${g.skills.length} skills` : `Levels up ${g.skills.length} skills`}<small>~${g.hours} hours</small></p>
    <div class="skB">${g.skills.map(s => `<div>${svg(s.ic)}<span class="skB-n">${s.n}</span><span class="skB-v">+${s.lv}<small>lv</small></span>
      <span class="skB-bar"><i style="width:${s.lv * 33}%"></i></span><div class="skB-x"><p class="skd-from">${s.txt}</p>${tryL(s)}</div></div>`).join("")}</div>${you(g.played)}</section>`,
  C: g => `<section class="skd" style="--accent:${g.accent}">${head(g.played ? `${g.skills.length} of ${g.skills.length} unlocked` : "unlocks as you finish")}
    <div class="skC">${g.skills.map(s => `<div><span class="skC-i">${svg(s.ic)}</span><div><p class="skC-k">${s.story ? "Story lesson" : "Skill"} · ${AMT[s.lv]}</p><p class="skC-n">${s.n}</p>
      <p class="skd-from">${s.txt}</p>${g.played ? "" : `<p class="skC-lock">${svg("lock")}What taught it unlocks when you finish</p>`}${tryL(s)}</div></div>`).join("")}</div>${you(g.played)}</section>`
};
const BUILD = `<section class="skd" style="margin:14px 0"><div class="skd-h">${svg("spark")}<span>What your games built</span><small>${DATA.build.meta}</small></div>
  ${DATA.build.rows.map(([n, v, lo]) => `<div class="skI-row"><span>${n}</span><b class="${lo ? "lo" : ""}"><i style="width:${v}%"></i></b><small>${v}</small></div>`).join("")}
  <div class="skI-tip"><b>Nexi:</b> ${DATA.build.tip}</div></section>`;
export default async ({ p, w, seed }) => {
  const dir = process.env.SHOTS || "tools/.cache/skills"; fs.mkdirSync(dir, { recursive: true });
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(css => { const s = document.createElement("style"); s.textContent = css; document.head.appendChild(s);
    try { if (typeof pnPuEnd === "function") pnPuEnd(); } catch (e) {} }, CSS);
  /* Nexi, his words and toasts out of the picture (the tall rail stays, so the unfolded shots show where it sits) */
  const hide = () => p.page.evaluate(() => { for (const e of document.querySelectorAll("body *")) { const c = getComputedStyle(e);
    if ((c.position === "fixed" || c.position === "sticky") && !e.closest("#full") && e.getBoundingClientRect().height < innerHeight * .6) e.style.visibility = "hidden"; }
    const b = document.querySelector("#pnbub"); if (b) b.style.visibility = "hidden"; });
  for (const g of DATA.games) for (const k of Object.keys(LOOKS)) {
    await p.page.evaluate(([id, t]) => { if (t) S.games.find(x => x.id === id).title = t; openFull(id); }, [g.id, g.title || ""]); await p.page.waitForTimeout(1500);
    await p.page.evaluate(html => { document.querySelectorAll(".skd").forEach(x => x.remove());
      document.querySelector("#full .gd-s-prog").insertAdjacentHTML("afterend", html); }, LOOKS[k](g));
    /* the section with the end of the one above it, so it's clear where it sits */
    await p.page.evaluate(() => document.querySelector("#full .gd-s-prog").scrollIntoView({ block: "start", behavior: "instant" }));
    await p.page.waitForTimeout(400); await hide();
    await p.page.screenshot({ path: path.join(dir, `${w}-${k}-${g.key}.png`) });
    await p.page.evaluate(() => typeof closeFull === "function" && closeFull()); await p.page.waitForTimeout(600);
  }
  await p.step("insights", `goTab("taste"); goSub("taste", "insights")`, 2500);
  await p.page.evaluate(html => { document.querySelectorAll("#p-taste .ix-rv").forEach(x => x.classList.add("in", "ix-now"));
    const a = document.querySelector("#p-taste .shs-skyc") || document.querySelector("#p-taste section"); a.insertAdjacentHTML("beforebegin", html);
    document.querySelector("#p-taste .skd").scrollIntoView({ block: "center", behavior: "instant" }); }, BUILD);
  await hide(); await p.page.waitForTimeout(500);
  await p.page.screenshot({ path: path.join(dir, `${w}-insights.png`) });
  console.log("  " + w + ": shots in " + dir);
};
