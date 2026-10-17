/* v293: a game's colour comes from its art by itself (hue votes, not one speck), snapped to the 32-swatch roster.
     node tools/kit.mjs run tools/scenarios/art-colours.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const bad = [];
  await p.step("queue", `goTab("queue")`, 1200);
  const r = await p.page.evaluate(async () => {
    const pic = paint => { const c = document.createElement("canvas"); c.width = 300; c.height = 400; const x = c.getContext("2d"); paint(x); return c.toDataURL("image/jpeg", .9); };
    const arts = {
      /* mostly orange with a small pure-blue speck: the old picker chose the blue */
      orange: pic(x => { x.fillStyle = "#1a1008"; x.fillRect(0, 0, 300, 400); x.fillStyle = "#e8761e"; x.fillRect(40, 60, 220, 280); x.fillStyle = "#0040ff"; x.fillRect(0, 0, 14, 14); }),
      green: pic(x => { x.fillStyle = "#2f7d32"; x.fillRect(0, 0, 300, 400); x.fillStyle = "#d8e6f0"; x.fillRect(0, 0, 300, 90); }),
      grey: pic(x => { x.fillStyle = "#555"; x.fillRect(0, 0, 300, 400); x.fillStyle = "#bbb"; x.fillRect(80, 100, 140, 200); }),
      red: pic(x => { x.fillStyle = "#0b0b0b"; x.fillRect(0, 0, 300, 400); x.fillStyle = "#c81e1e"; x.fillRect(70, 90, 160, 220); x.fillStyle = "#f0c020"; x.fillRect(120, 140, 40, 40); }),
      pink: pic(x => { x.fillStyle = "#f2e4ea"; x.fillRect(0, 0, 300, 400); x.fillStyle = "#e0609a"; x.fillRect(60, 120, 180, 160); }),
    };
    const base = { date: "", hours: 0, status: "tba", hype: 7, state: "backlog" };
    const ids = {};
    for (const [k, img] of Object.entries(arts)) { const id = "art-" + k; ids[k] = id; S.games.push(Object.assign({ id, title: "Art " + k, img, colour: PALETTE[0], ord: nextOrd() }, base)); }
    S.games.push(Object.assign({ id: "art-hand", title: "Art hand", img: arts.orange, colour: PALETTE[0], colourHand: true, ord: nextOrd() }, base));
    S.games.push(Object.assign({ id: "art-old", title: "Art old", img: arts.green, colour: "#FF7AD9", ord: nextOrd() }, base));
    renderQueue();
    await colJob();
    const col = id => S.games.find(g => g.id === id).colour;
    const out = {}; for (const k in ids) out[k] = col(ids[k]);
    out.hand = col("art-hand"); out.old = col("art-old"); out.todo = colTodo().length; out.pal = PALETTE.length;
    const row = document.querySelector('[data-row="art-orange"]'); out.row = row ? row.style.getPropertyValue("--accent") : "none";
    return out;
  });
  const hue = h => { const [r, g, b] = [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16)) ; return { r, g, b }; };
  const o = hue(r.orange); if (!(o.r > o.b + 60 && o.r >= o.g)) bad.push("orange art → " + r.orange);
  const g = hue(r.green); if (!(g.g > g.r && g.g > g.b)) bad.push("green art → " + r.green);
  if (!["#9AA7BF", "#6B7A99", "#E8E2D0"].includes(r.grey)) bad.push("grey art → " + r.grey);
  const rd = hue(r.red); if (!(rd.r > rd.g + 80 && rd.r > rd.b + 60)) bad.push("red art → " + r.red);
  const pk = hue(r.pink); if (!(pk.r > pk.g + 60 && pk.b > pk.g)) bad.push("pink art → " + r.pink);
  if (r.hand !== "#4DA3FF") bad.push("a hand-picked colour changed → " + r.hand);
  if (r.old !== "#FF7AD9") bad.push("an older chosen colour changed → " + r.old);
  if (r.todo) bad.push(r.todo + " games still to colour");
  if (r.row !== r.orange) bad.push("Queue row not recoloured in place: " + r.row);
  if (r.pal !== 32) bad.push("PALETTE has " + r.pal);
  /* Edit shows ✨ + every swatch, ✨ pressed for an art-coloured game; a swatch tap makes it hand-picked */
  await p.step("edit", `openEdit("art-orange")`, 1200);
  const e = await p.page.evaluate(() => { const row = document.querySelector("#e-colour"); if (!row) return null;
    const a = row.querySelector("[data-swauto]"), n = row.querySelectorAll(".sw[data-c]").length, on = a && a.getAttribute("aria-pressed");
    row.querySelector('.sw[data-c="#5BD75B"]').click(); return { n, on, aOff: a.getAttribute("aria-pressed") }; });
  if (!e) bad.push("no colour row in Edit");
  else { if (e.n !== 32) bad.push("Edit shows " + e.n + " swatches"); if (e.on !== "true") bad.push("✨ not pressed for an art colour"); if (e.aOff !== "false") bad.push("✨ still pressed after a pick"); }
  const ok = !bad.length && !p.errors.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${ok ? `orange ${r.orange} · green ${r.green} · grey ${r.grey} · red ${r.red} · pink ${r.pink}; hand-picked and older colours kept; Edit has ✨ + 32` : bad.join(" | ")}${p.errors.length ? "  errors: " + p.errors.join("; ") : ""}`);
  return ok;
};
