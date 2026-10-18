/* Library cover tags (span.libtag: date, hype…) show whole on every engine. On iPhone/iPad the date tag showed 10 of its
   65px: .libshot was an inline span, so in WebKit the tags' containing block was 0px wide (its max-width went to 0).
     node tools/kit.mjs run tools/scenarios/lib-tags.mjs --widths 412,859   (webkit-check.yml scenario input: WebKit) */
export default async ({ p, w }) => {
  await p.step("games", `goTab("games")`, 1500);
  const r = await p.page.evaluate(() => {
    const tags = [...document.querySelectorAll("#p-lib .libtag")].slice(0, 12), out = [];
    for (const t of tags) {
      const s = t.closest(".libshot").getBoundingClientRect(), b = t.getBoundingClientRect();
      if (t.scrollWidth > t.clientWidth + 1 || b.left < s.left - 1 || b.right > s.right + 1)
        out.push(`${t.className} "${t.textContent}" ${t.clientWidth}/${t.scrollWidth}px`);
    }
    const sh = document.querySelector("#p-lib .libshot");
    return { n: tags.length, bad: out, shot: sh && getComputedStyle(sh).display };
  });
  const ok = r.n > 0 && !r.bad.length;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: ${r.n} tags, cover box ${r.shot}${r.bad.length ? ", cut: " + r.bad.join("; ") : ""}`);
  return ok;
};
