/* v279 usage counts: taps are counted under the nearest name (data-* attribute, else an id: a Settings chip counts as its
   group), kept in localStorage gq:taps (not S, so no backup upload per tap); once a week they go to the helper's /usage
   with names and numbers only (no game titles); Settings › Your data › Help tidy the app › Don't send stops it.
     node tools/kit.mjs run tools/scenarios/usage-counts.mjs   (waits ~30s for the send) */
export default async ({ p, w }) => {
  const bad = [], sent = [];
  await p.ctx.route(/\/usage$/, r => { sent.push(r.request().postData() || ""); return r.fulfill({ status: 200, contentType: "application/json", body: '{"ok":true}', headers: { "access-control-allow-origin": "*" } }); });
  /* taps */
  await p.step("games", `goTab("games")`, 600);
  await p.page.evaluate(() => { document.querySelector("nav button[data-tab='plan']")?.click(); });
  await p.step("settings", `openSettings("app")`, 900);
  await p.page.evaluate(() => { const b = document.querySelector('#s-useoff .chip[data-v="on"]'); b && b.click(); });
  await p.page.waitForTimeout(4500);
  const t = await p.page.evaluate(() => JSON.parse(localStorage.getItem("gq:taps") || "{}"));
  if (!t.t || !t.t.tab) bad.push("nav tap not counted as 'tab': " + JSON.stringify(t.t));
  if (!t.t || !t.t["#s-useoff"]) bad.push("Settings chip not counted as its group '#s-useoff'");
  /* the weekly send (25s after opening) */
  await p.page.evaluate(() => localStorage.removeItem("gq:useSent"));
  await p.page.reload(); await p.page.waitForTimeout(28000);
  if (sent.length !== 1) bad.push(`sent ${sent.length} times (want 1)`);
  else {
    const b = JSON.parse(sent[0]), titles = await p.page.evaluate(() => S.games.map(g => g.title));
    if (!/^[a-z0-9]{8,32}$/.test(b.id || "")) bad.push("no phone id");
    if (!b.taps || !b.taps.tab) bad.push("taps missing");
    if (titles.some(x => sent[0].includes(x))) bad.push("a game title was sent");
    const at = await p.page.evaluate(() => +localStorage.getItem("gq:useSent"));
    if (!(Date.now() - at < 60000)) bad.push("send time not kept");
  }
  /* switched off: no send even when a week has passed */
  await p.page.evaluate(() => { S.useOff = true; saveNow(); localStorage.removeItem("gq:useSent"); });
  await p.page.reload(); await p.page.waitForTimeout(28000);
  if (sent.length > 1) bad.push("sent while switched off");
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "taps counted by name, one weekly send without game titles, none when switched off" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
