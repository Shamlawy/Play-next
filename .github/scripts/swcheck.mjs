/* Picture worker check (v268): sw.js now shrinks pictures on the phone (createImageBitmap + OffscreenCanvas inside the
   service worker). Safari draws and saves pictures differently (no webp writing, older versions without OffscreenCanvas),
   so this runs sw.js's own picSize and shrink in a WebKit worker on a made-up 3840×1240 JPEG and a see-through PNG and
   prints what comes out. "null" = this browser can't, and the app falls back to the resize service (fine, just slower).
   Fails only on a script error or a wrong size. Dry run in the dev container: PW_PATH=… BROWSER=chromium node … */
import fs from "fs";
const m = await import(process.env.PW_PATH || "playwright"), pw = m.webkit ? m : m.default;
const sw = fs.readFileSync("sw.js", "utf8");
const code = sw.slice(sw.indexOf("async function shrink("), sw.indexOf("async function fitFetch("));
if (!code.includes("function picSize(")) { console.log("FAIL: shrink/picSize not found in sw.js"); process.exit(1); }
const browser = await pw[process.env.BROWSER || "webkit"].launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const page = await browser.newPage();
await page.setContent("<p>sw check</p>");
const out = await page.evaluate(async code => {
  const src = code + `
  async function make(w, h, type) {
    const c = new OffscreenCanvas(w, h), x = c.getContext("2d");
    const g = x.createLinearGradient(0, 0, w, h); g.addColorStop(0, "rgba(255,0,0,1)"); g.addColorStop(1, type === "image/png" ? "rgba(0,0,255,0)" : "rgba(0,0,255,1)");
    x.fillStyle = g; x.fillRect(0, 0, w, h);
    return c.convertToBlob(type === "image/png" ? { type } : { type, quality: .9 });
  }
  const dims = async b => { if (!b) return null; const m = await createImageBitmap(b); const r = [m.width, m.height, b.type]; m.close && m.close(); return r; };
  onmessage = async () => {
    const r = { ua: navigator.userAgent, offscreen: typeof OffscreenCanvas, cib: typeof createImageBitmap };
    try {
      const jpg = await make(3840, 1240, "image/jpeg"), png = await make(1600, 1000, "image/png");
      r.jpgType = jpg.type; r.headJpg = picSize(new Uint8Array(await jpg.slice(0, 65536).arrayBuffer()));
      r.headPng = picSize(new Uint8Array(await png.slice(0, 65536).arrayBuffer()));
      const base = await shrink(jpg, 2560, 2560, false); r.base = await dims(base);
      if (base) { r.headBase = picSize(new Uint8Array(await base.slice(0, 65536).arrayBuffer())); r.strip = await dims(await shrink(base, 1536, 192, true)); r.thumb = await dims(await shrink(base, 128, 192, true)); }
      r.pngThumb = await dims(await shrink(png, 256, 256, true));
    } catch (e) { r.err = String(e && e.message || e); }
    postMessage(r);
  };`;
  const w = new Worker(URL.createObjectURL(new Blob([src], { type: "text/javascript" })));
  return new Promise(ok => { w.onmessage = e => ok(e.data); w.postMessage(1); setTimeout(() => ok({ err: "timeout" }), 20000); });
}, code);
await browser.close();
console.log(JSON.stringify(out, null, 1));
const bad = [];
if (out.err) bad.push("error: " + out.err);
if (out.headJpg && (out.headJpg[0] !== 3840 || out.headJpg[1] !== 1240)) bad.push("picSize read the JPEG as " + out.headJpg);
if (out.headPng && (out.headPng[0] !== 1600 || out.headPng[1] !== 1000)) bad.push("picSize read the PNG as " + out.headPng);
if (out.base && (out.base[0] !== 2560 || out.base[1] !== 827)) bad.push("4K shrunk to " + out.base);
if (out.base && out.headBase && (out.headBase[0] !== out.base[0] || out.headBase[1] !== out.base[1])) bad.push("picSize read the shrunk copy as " + out.headBase);
if (out.strip && (out.strip[0] < 1536 || out.strip[1] < 192)) bad.push("strip copy too small: " + out.strip);
if (out.thumb && (out.thumb[0] < 128 || out.thumb[1] < 192)) bad.push("thumb copy too small: " + out.thumb);
if (out.pngThumb && /jpeg/.test(out.pngThumb[2])) bad.push("a see-through PNG was saved as JPEG");
if (!out.base) console.log("NOTE: this browser can't shrink in the worker; the app uses the resize service instead");
if (bad.length) { console.log("FAIL\n" + bad.join("\n")); process.exit(1); }
console.log("OK");
