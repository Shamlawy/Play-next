/* The AI design review's "empty buttons" filter (#433): picture buttons with an aria-label (screenshot thumbnails) are
   not empty; a button with no text, no label and no picture still is.
     node tools/kit.mjs run tools/scenarios/review-empty-buttons.mjs --widths 412 */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(() => { const box = document.createElement("div"); document.body.appendChild(box);
    box.innerHTML = '<div id="rvA">' + [1, 2, 3, 4].map(i => `<button type="button" aria-label="Screenshot ${i} of 4" style="width:260px;height:146px"><img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" alt=""></button>`).join("")
      + '</div><div id="rvB"><button type="button" style="width:60px;height:40px"></button><button type="button" style="width:60px;height:40px"></button></div>';
    const x = { problem: "empty buttons", fix: "add text", confidence: .9 };
    const o = { pictures: revTrue(x, [...box.querySelectorAll("#rvA button")]), empty: revTrue(x, [...box.querySelectorAll("#rvB button")]) }; box.remove(); return o; });
  const ok = r.pictures === false && r.empty === true;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: picture buttons filed=${r.pictures}, truly empty filed=${r.empty}`);
  return ok;
};
