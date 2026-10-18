/* Still screenshots of every screen that two copies of the app can be compared by (look-same.mjs since v287, `kit.mjs look`
   since Oct 18). Everything that changes between two runs of the same copy is held still: one fixed clock, every
   animation stopped on a fixed frame (loops at 0, entrances at their end; scroll-linked ones follow the scroll), Nexi's
   bubble, toasts and power-up sparks hidden, pictures loaded and decoded before each picture (M15).
     prepare(p, data)            seed the phone with `data`, fix the clock, reload, hide what moves on its own
     shootAll(p, w, out, only)   one PNG per screen: <out>/<w>-<name>.png; `only` = names to take (default all)
     compare(page, a, b, out)    { n, all, box } pixels that differ; with `out`, writes before | after | where (red) as one PNG
     sheet(page, files, out)     a contact sheet: every file small, with its name, in one PNG */
import fs from "node:fs";
import path from "node:path";

export const SHOTS = [
  ["home", `closeFull(); goTab("home")`, 1], ["queue", `closeFull(); goTab("games"); goSub("games", "queue")`, 1],
  ["lib", `closeFull(); goTab("games"); goSub("games", "lib")`, 1], ["played", `closeFull(); goTab("games"); goSub("games", "played")`, 1],
  ["plan", `closeFull(); goTab("plan")`, 1], ["taste", `closeFull(); goTab("taste")`, 1],
  ["game", `closeFull(); goTab("games"); goSub("games", "queue"); openFull(S.games[0].id)`, 1],
  /* since Oct 18 (kit.mjs look): an open Queue card, a playing game's page (news, hours), Settings and Edit */
  ["card", `closeFull(); goTab("games"); goSub("games", "queue"); const r = document.querySelector("#p-queue [data-row]:not(.hero)"); r && r.click();
    setTimeout(() => { const h = document.querySelector("#p-queue .hero"); h && h.scrollIntoView({ block: "center", behavior: "instant" }); }, 1200)`, -1],
  ["game2", `closeFull(); goTab("games"); goSub("games", "queue"); openFull((S.games.find(g => g.state === "playing" && g.nw) || S.games[1]).id)`, 1],
  ["settings", `closeFull(); openSettings()`, 1], ["edit", `closeFull(); openEdit(S.games[2].id)`, 0]];
const CLOSE = `typeof closeEdit === "function" && closeEdit(); typeof dyClose === "function" && dyClose(); closeFull()`;

export async function prepare(p, data) {
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify({ ...data, nxEvo: 99 }));
  /* one fixed time for both copies: countdowns and "N days to go" read the clock (timers still run) */
  await p.page.clock.setFixedTime(new Date(Date.UTC(2026, 9, 16, 12, 0, 0)));
  await p.page.reload(); await p.page.waitForTimeout(3000);
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  /* what changes between two runs of the same copy: Nexi's bubble and its message, his problem card, toasts, his power-up
     sparks (also off in the seed: nxEvo), the tour */
  await p.page.addStyleTag({ content: `#pnbub, #pnbubmsg, #nxbug, #toast, .toast, #pnwake, #nxtour, .nxtour, .ssjdot, .ssjbolt, .ssjember, .ssjwave, #ssjflash { visibility: hidden !important; }
    html { scroll-behavior: auto !important; } * { caret-color: transparent !important; }` });
}
const freeze = p => p.page.evaluate(() => { for (const a of document.getAnimations()) { try {
  if (a.timeline && a.timeline.constructor && /Scroll|View/.test(a.timeline.constructor.name)) continue;
  const t = a.effect.getComputedTiming(); if (t.endTime === Infinity) { a.pause(); a.currentTime = 0; } else a.finish(); } catch (e) {} } });
/* lazy pictures load as the scroll reaches them: load them all, then decode */
const pics = p => p.page.evaluate(async () => { const t0 = performance.now();
  for (const i of document.images) i.loading = "eager";
  while (performance.now() - t0 < 4000) { const left = [...document.images].filter(i => i.src && !i.complete); if (!left.length) break; await new Promise(r => setTimeout(r, 100)); }
  await Promise.all([...document.images].filter(i => i.complete && i.naturalWidth).map(i => i.decode().catch(() => {}))); });
export async function shot(p, file, scale = "device") {
  await p.page.waitForTimeout(400); await pics(p); await freeze(p); await p.page.waitForTimeout(150); await freeze(p);
  await p.page.screenshot({ path: file, animations: "disabled", caret: "hide", scale });
}
export async function shootAll(p, w, out, { only = null, scale = "device" } = {}) {
  fs.mkdirSync(out, { recursive: true });
  /* the open page's own scroller (a game page, a sheet: whichever box really scrolls), else the window */
  const box = `[...document.querySelectorAll("#full.on .hub.gpage, #sheet.on, #sheet.on *")].find(e => e.scrollHeight > e.clientHeight + 40 && /auto|scroll/.test(getComputedStyle(e).overflowY))`;
  const down = () => p.page.evaluate(`{ const g = ${box}; if (g) g.scrollTop = innerHeight * .8; else scrollTo(0, Math.min(innerHeight * 1.3, document.scrollingElement.scrollHeight)); }`);
  const up = () => p.page.evaluate(`{ const g = ${box}; if (g) g.scrollTop = 0; scrollTo(0, 0); }`);
  const files = [];
  for (const [name, js, alsoDown] of SHOTS) {
    if (only && !only.includes(name)) continue;
    await p.step("close", CLOSE, 300);
    /* alsoDown: 1 = also one screen down, 0 = the top only, -1 = where the step left it (the open Queue card) */
    await p.step(name, js, 2400); if (alsoDown >= 0) await up(); files.push(path.join(out, `${w}-${name}.png`)); await shot(p, files.at(-1), scale);
    if (alsoDown > 0) { await down(); files.push(path.join(out, `${w}-${name}-down.png`)); await shot(p, files.at(-1), scale); await up(); }
  }
  await p.step("close", CLOSE, 300);
  return files;
}
/* both PNGs into canvases in the page: count the pixels that differ by more than a little; `out` draws the pair and the
   changed pixels (red on a dimmed copy) side by side, so a person, or Claude reading the file, sees what moved */
export async function compare(page, a, b, out) {
  const url = f => "data:image/png;base64," + fs.readFileSync(f).toString("base64");
  const r = await page.evaluate(async ([a, b, draw]) => {
    const load = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
    const [ia, ib] = await Promise.all([load(a), load(b)]);
    const W = Math.max(ia.width, ib.width), H = Math.max(ia.height, ib.height);
    const px = img => { const c = document.createElement("canvas"); c.width = W; c.height = H; const x = c.getContext("2d"); x.drawImage(img, 0, 0); return x.getImageData(0, 0, W, H).data; };
    const da = px(ia), db = px(ib); let n = 0, minY = 1e9, maxY = -1, minX = 1e9, maxX = -1;
    const mask = draw ? new Uint8Array(W * H) : null;
    for (let i = 0; i < da.length; i += 4) if (Math.abs(da[i] - db[i]) + Math.abs(da[i + 1] - db[i + 1]) + Math.abs(da[i + 2] - db[i + 2]) > 24) {
      n++; const q = i / 4, y = Math.floor(q / W), x = q % W; if (mask) mask[q] = 1; minY = Math.min(minY, y); maxY = Math.max(maxY, y); minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
    const res = { n, all: W * H, size: ia.width !== ib.width || ia.height !== ib.height, box: n ? `${minX},${minY}–${maxX},${maxY}` : "" };
    if (draw && n) {
      const gap = 12, c = document.createElement("canvas"); c.width = W * 3 + gap * 2; c.height = H; const x = c.getContext("2d");
      x.fillStyle = "#333"; x.fillRect(0, 0, c.width, c.height); x.drawImage(ia, 0, 0); x.drawImage(ib, W + gap, 0);
      x.globalAlpha = .35; x.drawImage(ib, 2 * (W + gap), 0); x.globalAlpha = 1;
      const d = x.getImageData(2 * (W + gap), 0, W, H); for (let q = 0; q < W * H; q++) if (mask[q]) { d.data[q * 4] = 255; d.data[q * 4 + 1] = 40; d.data[q * 4 + 2] = 60; d.data[q * 4 + 3] = 255; }
      x.putImageData(d, 2 * (W + gap), 0);
      x.strokeStyle = "#ff2850"; x.lineWidth = 3; x.strokeRect(2 * (W + gap) + minX - 4, minY - 4, maxX - minX + 8, maxY - minY + 8);
      res.png = c.toDataURL("image/png");
    }
    return res;
  }, [url(a), url(b), !!out]);
  if (out && r.png) fs.writeFileSync(out, Buffer.from(r.png.split(",")[1], "base64"));
  delete r.png;
  return r;
}
export async function sheet(page, files, out, { cols = 5, w = 206 } = {}) {
  const items = files.map(f => ["data:image/png;base64," + fs.readFileSync(f).toString("base64"), path.basename(f, ".png")]);
  const png = await page.evaluate(async ([items, cols, w]) => {
    const load = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
    const imgs = await Promise.all(items.map(([u]) => load(u)));
    const h = Math.max(...imgs.map(i => Math.round(i.height * w / i.width))), lab = 22, gap = 10, rows = Math.ceil(imgs.length / cols);
    const c = document.createElement("canvas"); c.width = cols * (w + gap) + gap; c.height = rows * (h + lab + gap) + gap; const x = c.getContext("2d");
    x.fillStyle = "#222"; x.fillRect(0, 0, c.width, c.height); x.font = "13px sans-serif"; x.fillStyle = "#ddd";
    imgs.forEach((im, k) => { const cx = gap + (k % cols) * (w + gap), cy = gap + Math.floor(k / cols) * (h + lab + gap);
      x.fillText(items[k][1], cx, cy + 15); x.drawImage(im, cx, cy + lab, w, Math.round(im.height * w / im.width)); });
    return c.toDataURL("image/png");
  }, [items, cols, w]);
  fs.writeFileSync(out, Buffer.from(png.split(",")[1], "base64"));
}
