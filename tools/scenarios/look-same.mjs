/* Proof that a speed change changes nothing you can see: screenshots of every main screen (top and scrolled), a game page
   and Settings, with every animation stopped on a fixed frame (loops at 0, entry animations at their end; scroll-linked
   ones follow the scroll, so they're the same), then compared pixel by pixel with the same run of another copy.
     node tools/kit.mjs run tools/scenarios/look-same.mjs --file old.html --widths 412,859   (OUT=shots/old, saves them)
     OUT=shots/new node tools/kit.mjs run tools/scenarios/look-same.mjs --widths 412,859
     A=shots/old B=shots/new node tools/kit.mjs run tools/scenarios/look-same.mjs   (compares; fails on a difference)
   CSS=<rules> adds a style to the copy being shot (e.g. to leave out a change that is meant to show). */
import fs from "node:fs";
import path from "node:path";
export default async ({ p, w, seed }) => {
  if (process.env.A && process.env.B) {
    /* compare in the browser: two PNGs → canvas → count pixels that differ by more than a little */
    let bad = 0;
    for (const f of fs.readdirSync(process.env.A).filter(f => f.startsWith(w + "-"))) {
      const b = path.join(process.env.B, f); if (!fs.existsSync(b)) { console.log(`  ✗ ${f}: missing in B`); bad++; continue; }
      const r = await p.page.evaluate(async ([a, b]) => {
        const load = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
        const [ia, ib] = await Promise.all([load(a), load(b)]); if (ia.width !== ib.width || ia.height !== ib.height) return { size: true };
        const px = img => { const c = document.createElement("canvas"); c.width = img.width; c.height = img.height; const x = c.getContext("2d"); x.drawImage(img, 0, 0); return x.getImageData(0, 0, c.width, c.height).data; };
        const da = px(ia), db = px(ib); let n = 0, minY = 1e9, maxY = -1, minX = 1e9, maxX = -1;
        for (let i = 0; i < da.length; i += 4) if (Math.abs(da[i] - db[i]) + Math.abs(da[i + 1] - db[i + 1]) + Math.abs(da[i + 2] - db[i + 2]) > 24) {
          n++; const q = i / 4, y = Math.floor(q / ia.width), x = q % ia.width; minY = Math.min(minY, y); maxY = Math.max(maxY, y); minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
        return { n, all: da.length / 4, box: n ? `${minX},${minY}–${maxX},${maxY}` : "" };
      }, ["data:image/png;base64," + fs.readFileSync(path.join(process.env.A, f)).toString("base64"), "data:image/png;base64," + fs.readFileSync(b).toString("base64")]);
      const ok = !r.size && r.n <= r.all * .0005;
      if (!ok) bad++;
      console.log(`  ${ok ? "✓" : "✗"} ${f}: ${r.size ? "different size" : r.n + " pixels differ" + (r.n ? " in " + r.box : "")}`);
    }
    return !bad;
  }
  const out = process.env.OUT || "shots"; fs.mkdirSync(out, { recursive: true });
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 60)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  const freeze = () => p.page.evaluate(() => { for (const a of document.getAnimations()) { try {
    if (a.timeline && a.timeline.constructor && /Scroll|View/.test(a.timeline.constructor.name)) continue;
    const t = a.effect.getComputedTiming(); if (t.endTime === Infinity) { a.pause(); a.currentTime = 0; } else a.finish(); } catch (e) {} } });
  const shot = async name => { await p.page.waitForTimeout(400); await freeze(); await p.page.waitForTimeout(150); await freeze();
    await p.page.screenshot({ path: `${out}/${w}-${name}.png`, animations: "disabled", caret: "hide" }); };
  const SCR = [["home", `closeFull(); goTab("home")`], ["queue", `closeFull(); goTab("games"); goSub("games", "queue")`], ["lib", `closeFull(); goTab("games"); goSub("games", "lib")`],
    ["played", `closeFull(); goTab("games"); goSub("games", "played")`], ["plan", `closeFull(); goTab("plan")`], ["taste", `closeFull(); goTab("taste")`]];
  for (const [name, js] of SCR) {
    await p.step(name, js, 2400); await shot(name);
    await p.page.evaluate(() => scrollTo(0, Math.min(innerHeight * 1.3, document.scrollingElement.scrollHeight))); await shot(name + "-down");
    await p.page.evaluate(() => scrollTo(0, 0));
  }
  await p.step("game", `closeFull(); goTab("games"); goSub("games", "queue"); openFull(S.games[0].id)`, 2400); await shot("game");
  await p.page.evaluate(() => { const g = document.querySelector("#full .hub.gpage"); if (g) g.scrollTop = innerHeight; }); await shot("game-down");
  await p.step("close", `closeFull()`, 900); await shot("after-close");
  console.log(`  ${w}px: ${fs.readdirSync(out).filter(f => f.startsWith(w + "-")).length} screenshots in ${out}`);
  return true;
};
