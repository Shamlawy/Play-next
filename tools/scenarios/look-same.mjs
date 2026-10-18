/* Proof that a speed change changes nothing you can see: screenshots of every main screen (top and scrolled), game pages,
   an open Queue card, Settings and Edit, with every animation stopped on a fixed frame (tools/shots.mjs), then compared
   pixel by pixel with the same run of another copy.
     node tools/kit.mjs run tools/scenarios/look-same.mjs --file old.html --widths 412,859   (OUT=shots/old, saves them)
     OUT=shots/new node tools/kit.mjs run tools/scenarios/look-same.mjs --widths 412,859
     A=shots/old B=shots/new node tools/kit.mjs run tools/scenarios/look-same.mjs   (compares; fails on a difference)
   CSS=<rules> adds a style to the copy being shot (e.g. to leave out a change that is meant to show).
   One command for the same job since Oct 18: node tools/kit.mjs look --same (this branch against main). */
import fs from "node:fs";
import path from "node:path";
import { prepare, shootAll, compare } from "../shots.mjs";
export default async ({ p, w, seed }) => {
  if (process.env.A && process.env.B) {
    let bad = 0;
    for (const f of fs.readdirSync(process.env.A).filter(f => f.startsWith(w + "-"))) {
      const b = path.join(process.env.B, f); if (!fs.existsSync(b)) { console.log(`  ✗ ${f}: missing in B`); bad++; continue; }
      const r = await compare(p.page, path.join(process.env.A, f), b);
      const ok = !r.size && r.n <= r.all * .0005;
      if (!ok) bad++;
      console.log(`  ${ok ? "✓" : "✗"} ${f}: ${r.size ? "different size" : r.n + " pixels differ" + (r.n ? " in " + r.box : "")}`);
    }
    return !bad;
  }
  const out = process.env.OUT || "shots";
  await prepare(p, seed(await p.page.evaluate(() => APP_V), 60));
  if (process.env.CSS) await p.page.evaluate(c => { const s = document.createElement("style"); s.textContent = c; document.head.appendChild(s); }, process.env.CSS);
  const files = await shootAll(p, w, out);
  console.log(`  ${w}px: ${files.length} screenshots in ${out}`);
  return true;
};
