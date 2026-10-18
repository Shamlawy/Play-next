/* Which addEventListener calls does one openSettings() make, on what, and from which line? (leak finder)
     node tools/kit.mjs run tools/scenarios/soak-listeners.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  await p.page.evaluate(() => { window.__L = null;
    const a = EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener = function (t, ...x) { if (window.__L) { const d = this === document ? "document" : this === window ? "window" : this.nodeType ? (this.isConnected ? "live " : "DETACHED ") + (this.id ? "#" + this.id : this.tagName.toLowerCase() + (this.classList && this.classList[0] ? "." + this.classList[0] : "")) : (this.constructor.name);
      const ln = (new Error().stack.split("\n").slice(2).find(l => /:\d+:\d+\)?$/.test(l)) || "").match(/:(\d+):\d+\)?$/);
      const k = `${t} on ${d} @line ${ln ? ln[1] : "?"}`; window.__L[k] = (window.__L[k] || 0) + 1; }
      return a.call(this, t, ...x); }; });
  await p.step("o", "openSettings()", 600); await p.step("c", "closeEdit()", 400);
  await p.page.evaluate(() => { window.__L = {}; });
  await p.step("o", "openSettings()", 800); await p.step("c", "closeEdit()", 400);
  const L = await p.page.evaluate(() => window.__L);
  Object.entries(L).sort((a, b) => b[1] - a[1]).forEach(([k, n]) => console.log(`   ${n}× ${k}`));
};
