/* v277 (#171): Settings › Look of the app › Live preview (off by default). On: a screen's Customise docks (bottom part on a
   phone, one side unfolded) with the real screen in sight, and a change shows on that screen straight away. Settings only
   docks while a look group is open; editing a game never does; off = the full sheet as before.
     node tools/kit.mjs run tools/scenarios/live-preview.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  const st = () => p.page.evaluate(() => {
    const sh = document.getElementById("sheet"), r = sh.getBoundingClientRect(), on = sh.classList.contains("on");
    const pt = innerWidth >= 620 ? (document.documentElement.classList.contains("railleft") ? [innerWidth * .3, innerHeight / 2] : [innerWidth * .75, innerHeight / 2]) : [innerWidth / 2, innerHeight * .2];
    const hit = document.elementFromPoint(...pt);
    return { on, lvp: document.documentElement.classList.contains("lvp"), top: r.top | 0, left: r.left | 0, w: r.width | 0, screenSeen: !!hit && !hit.closest("#sheet") };
  });
  /* off by default */
  await p.page.evaluate(() => { delete S.livePrev; S.games.slice(0, 4).forEach(g => g.art = "logo-512.png"); renderQueue(); });
  await p.step("games", `goTab("games")`, 800);
  await p.step("customise", `openSettings("games")`, 900);
  let s = await st(); if (s.lvp || s.screenSeen) bad.push("docked while off: " + JSON.stringify(s));
  await p.step("close", `closeEdit()`, 500);
  /* on: Customise docks, the screen shows and changes */
  await p.page.evaluate(() => { S.livePrev = true; });
  await p.step("customise", `openSettings("games")`, 900);
  s = await st();
  if (!s.lvp || !s.screenSeen) bad.push("Customise not docked: " + JSON.stringify(s));
  if (w < 620 && s.top < p.h * .3) bad.push("phone: sheet starts too high, top " + s.top);
  if (w >= 620 && s.w > w * .5) bad.push("unfolded: sheet too wide, " + s.w);
  const changed = await p.page.evaluate(async () => {
    const q = document.getElementById("p-queue"), before = q.innerHTML, b = document.querySelector('#s-rowart .chip[aria-pressed="false"]') || document.querySelector('#sheet-in .chips .chip[aria-pressed="false"]');
    if (!b) return "no chip to tap";
    b.click(); await new Promise(r => setTimeout(r, 600)); return q.innerHTML !== before ? "" : "the queue didn't change after " + (b.closest(".chips") || {}).id;
  });
  if (changed) bad.push(changed);
  await p.step("close", `closeEdit()`, 500);
  s = await st(); if (s.lvp) bad.push("still docked after closing");
  /* Settings: full sheet, docks in Look of the app */
  await p.step("settings", `openSettings("app")`, 900);
  s = await st(); if (s.lvp) bad.push("Settings docked with no look group open");
  await p.step("look", `setJump({ scope: "app", group: "Look of the app", text: "Live preview" })`, 1400);
  s = await st(); if (!s.lvp) bad.push("Look of the app didn't dock");
  const sw = await p.page.evaluate(() => !!document.querySelector('#s-livep .chip[data-v="on"][aria-pressed="true"]'));
  if (!sw) bad.push("Live preview switch missing or not showing On");
  await p.step("close", `closeEdit()`, 500);
  /* editing a game never docks */
  await p.step("edit", `openEdit("g2")`, 1000);
  s = await st(); if (s.lvp) bad.push("Edit docked");
  await p.step("close", `closeEdit()`, 500);
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "off by default; Customise docks and the screen changes live; Settings docks only in look groups; Edit never" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
