/* Heap snapshot after N Settings open/close cycles, written to SNAP (a file). Then: node tools/heap-retain.mjs <file>
     SNAP=/path/x.heapsnapshot node tools/kit.mjs run tools/scenarios/soak-snapshot.mjs --widths 412 */
import fs from "node:fs";
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  if (process.env.LOOP === "full") {   /* the soak.mjs round: tabs + a scroll, six game pages, Settings */
    const ids = await p.page.evaluate(() => S.games.slice(0, 6).map(g => g.id));
    for (let r = 0; r < 4; r++) {
      for (const t of ["home", "queue", "lib", "insights", "home"]) { await p.step("tab", `goTab(${JSON.stringify(t)})`, 450); await p.page.evaluate(() => { for (const s of document.querySelectorAll(".panel.on, .panel.on *")) if (s.scrollHeight > s.clientHeight + 50 && /auto|scroll/.test(getComputedStyle(s).overflowY)) { s.scrollTop = s.scrollHeight / 2; break; } }); await p.page.waitForTimeout(250); }
      for (const id of ids) { await p.step("open", `openFull(${JSON.stringify(id)})`, 600); await p.step("close", "closeFull()", 400); }
      await p.step("o", "openSettings()", 700); await p.step("c", "closeEdit()", 400); await p.step("home", 'goTab("home")', 500);
    }
  } else for (let i = 0; i < 3; i++) { await p.step("o", "openSettings()", 600); await p.step("c", "closeEdit()", 400); }
  await p.cdp.send("HeapProfiler.collectGarbage");
  const chunks = []; p.cdp.on("HeapProfiler.addHeapSnapshotChunk", e => chunks.push(e.chunk));
  await p.cdp.send("HeapProfiler.takeHeapSnapshot", { reportProgress: false });
  fs.writeFileSync(process.env.SNAP || "heap.heapsnapshot", chunks.join(""));
  console.log("  snapshot written", chunks.join("").length, "bytes");
};
