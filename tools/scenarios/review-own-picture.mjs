/* The AI design review's "hidden by pictures" / "overlayed elements" claims (#364, #288): a card's picture sits inside the
   card on purpose (Library cards, Home's hero), and pictures in three separate cards don't cover anything. A button that
   really sits on top of another one is still filed.
     node tools/kit.mjs run tools/scenarios/review-own-picture.mjs --widths 412 */
export default async ({ p, w }) => {
  const r = await p.page.evaluate(() => { const box = document.createElement("div"); box.style.cssText = "position:fixed;left:0;top:0;width:400px;height:400px"; document.body.appendChild(box);
    const pic = "data:image/gif;base64,R0lGODlhAQABAAAAACw=";
    box.innerHTML = '<div id="rvA" style="display:flex;gap:10px">' + [1, 2, 3].map(() => `<button type="button" style="width:100px;height:140px"><span><img src="${pic}" style="width:100px;height:140px" alt=""></span></button>`).join("") + "</div>"
      + `<div id="rvH" style="position:relative;width:300px;height:160px"><img class="bg" src="${pic}" style="position:absolute;inset:0;width:100%;height:100%" alt=""></div>`
      + '<div id="rvB" style="position:relative;height:60px"><button type="button" style="position:absolute;left:0;top:0;width:120px;height:44px">A</button><button type="button" style="position:absolute;left:40px;top:10px;width:120px;height:44px">B</button></div>';
    const hid = { problem: "buttons hidden by pictures", fix: "reorder layers", confidence: 1 }, ovl = { problem: "overlayed elements", fix: "reorder layers", confidence: .9 };
    const o = { cards: revTrue(hid, [...box.querySelectorAll("#rvA img")]), hero: revTrue(ovl, [box.querySelector("#rvH"), box.querySelector("#rvH img")]),
      real: revTrue(ovl, [...box.querySelectorAll("#rvB button")]) };
    box.remove(); return o; });
  const ok = r.cards === false && r.hero === false && r.real === true;
  console.log(`  ${ok ? "✓" : "✗"} ${w}px: card pictures filed=${r.cards}, hero's own picture filed=${r.hero}, buttons really on top of each other filed=${r.real}`);
  return ok;
};
