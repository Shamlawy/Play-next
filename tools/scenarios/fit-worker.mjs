/* v289: your own pictures still get their small copies after the copy-making moved into a worker (FIT.wk): prints how many
   were made and their sizes (compare with --file old.html). Runs in WebKit too (webkit-check.yml, scenario input). */
export default async ({ p, w, seed }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => { localStorage.setItem("gq:data", d); }, JSON.stringify({ ...seed(v, 60), lastV: v }));
  await p.page.reload({ waitUntil: "load" }); await p.page.waitForTimeout(9000);
  const r = await p.page.evaluate(() => ({ made: FIT.made.size, err: FIT.err || "", wk: FIT.wk === false ? "off" : FIT.wk ? "on" : "unused",
    blobs: [...document.images].filter(i => /^blob:/.test(i.src)).map(i => i.naturalWidth + "x" + i.naturalHeight).slice(0, 8), bgs: [...document.querySelectorAll("[style*='blob:']")].length }));
  console.log(JSON.stringify(r)); return r.made > 0;
};
