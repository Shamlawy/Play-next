/* Oct 20: Nexi's glitch card (nxBugCard, shown when he isn't floating on screen) still pops in, again on a second report,
   and its restart no longer forces a layout (it read offsetWidth: 60-100ms at 4x after a lag report).
     node tools/kit.mjs run tools/scenarios/nxbug-card.mjs --widths 412,859 */
export default async ({ p, w }) => {
  let ok = true;
  const say = (good, what) => { console.log(`  ${good ? "✓" : "✗"} ${w}px: ${what}`); if (!good) ok = false; };
  await p.page.waitForTimeout(3000);
  const r = await p.page.evaluate(async () => {
    const reads = []; const d = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "offsetWidth");
    Object.defineProperty(HTMLElement.prototype, "offsetWidth", { configurable: true, get() { if (this.id === "nxbug") reads.push(1); return d.get.call(this); } });
    const frame = () => new Promise(f => requestAnimationFrame(() => f()));
    nxBugCard("First test line."); const c = document.getElementById("nxbug"), at0 = c.classList.contains("on");
    for (let i = 0; i < 4; i++) await frame();
    const on1 = c.classList.contains("on"), op1 = await new Promise(f => setTimeout(() => f(+getComputedStyle(c).opacity), 500));
    nxBugCard("Second test line."); const off2 = !c.classList.contains("on");
    for (let i = 0; i < 4; i++) await frame();
    const on2 = c.classList.contains("on") && /Second test line/.test(c.textContent);
    Object.defineProperty(HTMLElement.prototype, "offsetWidth", d);
    return { at0, on1, op1, off2, on2, reads: reads.length };
  });
  say(!r.at0 && r.on1 && r.op1 > .9, `the card pops in a frame later and shows (opacity ${r.op1})`);
  say(r.off2 && r.on2, "a second report restarts it with the new words");
  say(r.reads === 0, `no offsetWidth read on the card (${r.reads})`);
  return ok;
};
