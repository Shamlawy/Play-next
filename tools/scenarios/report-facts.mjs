/* Oct 20: a hand-filed report's "Show me where" says what the game at the spot is like (title length, words, kinds of
   picture, counts) and the pictures in its card; every report says how big the library is. Never a title.
     node tools/kit.mjs run tools/scenarios/report-facts.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  await p.step("queue", `goTab("queue")`, 900);
  const r = await p.page.evaluate(() => {
    const row = document.querySelector("#queue [data-g], .row[data-g], [data-opg]") || document.querySelector("[data-g]");
    if (!row) return { err: "no game row on the Queue" };
    const b = row.getBoundingClientRect(), el = document.elementFromPoint(b.left + Math.min(40, b.width / 2), b.top + b.height / 2) || row;
    const s = bugSnap(el, b.left + 40, b.top + b.height / 2);
    BUG.q.length = 0; bugReport("user", "test " + Date.now(), { note: "x", stack: s.snap });
    const q = BUG.q.pop();
    return { snap: s.snap, lib: q.lib, stack: q.stack, titles: S.games.map(g => g.title), n: S.games.length };
  });
  if (r.err) bad.push(r.err);
  else {
    if (!/^game: title \d+ chars, \d+ words/m.test(r.snap)) bad.push("no game line in the snap:\n" + r.snap);
    if (!/cover [^,]+, key art [^,]+, logo \S/.test(r.snap)) bad.push("no picture kinds");
    if (!/^pictures in its card/m.test(r.snap)) bad.push("no card pictures line");
    if (!/game:/.test(r.stack)) bad.push("game line cut from the sent report (8-line limit?)");
    if (!new RegExp("^" + r.n + " games \\(").test(r.lib || "")) bad.push("library line wrong: " + r.lib);
    const all = r.snap + r.lib + r.stack;
    if (r.titles.some(t => t.length > 2 && all.includes(t))) bad.push("a game title is in the report");
  }
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? "game facts + library size in the report, no titles" : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  if (ok) console.log("    " + r.snap.split("\n").slice(-2).join("\n    ") + "\n    library: " + r.lib);
  return ok;
};
