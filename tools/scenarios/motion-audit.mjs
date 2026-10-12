/* Motion audit: does each open / close / switch move, or does it jump? Lists the finite animations
   (CSS animations, transitions, element.animate) that start within 120ms of each action.
   "— jumps" = nothing moved. Read-only, never fails (it's a map for animation work, not a check).
     node tools/kit.mjs run tools/scenarios/motion-audit.mjs --widths 412 */
const ACTIONS = [
  ["toast", `toast("Saved")`],
  ["settings open", `openSettings()`],
  ["settings close", `closeEdit()`],
  ["edit open", `openEdit("g2")`],
  ["edit close", `closeEdit()`],
  ["game open", `openFull("g1")`],
  ["game close", `closeFull()`],
  ["peek open", `openPeek(S.games.slice(0, 3).map(g => g.id))`],
  ["peek close", `closePeek()`],
  ["chat open", `nxChat()`],
  ["chat close", `dyClose()`],
  ["tab games", `goTab("games")`],
  ["sub library", `goSub("games", "lib")`],
  ["sub queue", `goSub("games", "queue")`],
  ["tab plan", `goTab("plan")`],
  ["tab taste", `goTab("taste")`],
  ["tab home", `goTab("home")`],
  /* smaller moments: a redraw in place */
  ["month next", `goTab("plan"); goSub("plan", "month"); $("#cal-next").click()`],
  ["month prev", `$("#cal-prev").click()`],
  ["timeline", `goSub("plan", "flow")`],
  ["duel pick", `goTab("taste"); goSub("taste", "duels"); document.querySelector("[data-duel]").click()`],
  ["lib filter", `goTab("lib"); (document.querySelectorAll("#p-lib .chips button, #p-lib [data-lf], #p-lib .chip")[1] || {}).click()`],
  ["queue open card", `goTab("queue"); (document.querySelector("#p-queue .row") || {}).click()`],
  ["home", `goTab("home")`]
];
export default async ({ p, w }) => {
  console.log(`  ${w}px`);
  for (const [label, fn] of ACTIONS) {
    const r = await p.page.evaluate(async fn => {
      const name = el => !el || !el.tagName ? "?" : el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (typeof el.className === "string" && el.className.trim() ? "." + el.className.trim().split(/\s+/).slice(0, 2).join(".") : "");
      const before = new Set(document.getAnimations());
      try { (0, eval)(fn); } catch (e) { return { err: String(e.message || e) }; }
      await new Promise(r => setTimeout(r, 120));
      const seen = new Map();
      for (const a of document.getAnimations()) {
        if (before.has(a)) continue;
        const t = a.effect && a.effect.getTiming ? a.effect.getTiming() : {};
        if (t.iterations === Infinity) continue;
        const el = a.effect && a.effect.target;
        if (!el || !el.isConnected || !el.getClientRects().length) continue;
        const k = name(el) + " " + (a.animationName || a.transitionProperty || "js") + " " + Math.round(+t.duration || 0) + "ms";
        seen.set(k, (seen.get(k) || 0) + 1);
      }
      return { list: [...seen].sort((a, b) => b[1] - a[1]).slice(0, 4).map(([k, n]) => n > 1 ? k + " ×" + n : k), total: [...seen.values()].reduce((a, b) => a + b, 0) };
    }, fn);
    console.log(`   ${label.padEnd(15)} ${r.err ? "error: " + r.err : r.total ? r.total + " moving: " + r.list.join(" | ") : "— jumps"}`);
    await p.page.waitForTimeout(900);
  }
  return true;
};
