/* Screenshots of the opening reel at chosen times, with the app's real covers (v293), to look at by eye.
     OUT=<dir> TIMES=6.3,6.8,7.4,8.3 node tools/kit.mjs run tools/scenarios/reel-frames.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const pg = p.page, out = process.env.OUT || ".", times = (process.env.TIMES || "6.3,6.8,7.4,8.3").split(",").map(Number);
  await pg.evaluate(() => { localStorage.setItem("gq:reel", "1"); saveNow(); });
  await pg.reload();
  await pg.waitForFunction(() => PNREEL.art && PNREEL.art.done, null, { timeout: 6000 }).catch(() => {});
  for (const t of times) {
    await pg.evaluate(t => { const A = PNREEL.art; PNREEL.use = A && { title: A.title, hours: A.hours, covers: A.covers.slice() }; pnreelPaint(t); }, t);
    await pg.screenshot({ path: `${out}/reel-${w}-${t}.png` });
  }
  await pg.evaluate(() => localStorage.removeItem("gq:reel"));
  console.log(`  ✓ ${w}px: ${times.length} frames in ${out}`);
  return true;
};
