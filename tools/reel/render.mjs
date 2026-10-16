/* v290: renders the opening reel ("One pixel plays everything") to a 1920x1080 60fps MP4 with its soundtrack.
   The reel's code is read from index.html (between REEL-START and REEL-END), so the video and the app's opening
   screen are the same drawing. Each frame blends 3 subframes (ffmpeg tmix), each with a short in-page shutter blur;
   the soundtrack (audio.js, Web Audio OfflineAudioContext) is rendered to WAV and muxed in.
     node tools/reel/render.mjs [out.mp4]        the video (about 3 minutes; needs ffmpeg + Playwright)
     node tools/reel/render.mjs --beats dir      one PNG per beat, to look at before a full render */
import { chromium } from "playwright";
import { spawn } from "child_process"; import fs from "fs"; import os from "os"; import path from "path";
const here = path.dirname(new URL(import.meta.url).pathname), root = path.join(here, "../..");
const beats = process.argv[2] === "--beats" ? process.argv[3] || "reel-beats" : null, OUT = beats ? null : process.argv[2] || "reel.mp4";
const src = fs.readFileSync(path.join(root, "index.html"), "utf8"), a = src.indexOf("/* REEL-START"), b = src.indexOf("/* REEL-END */");
if (a < 0 || b < 0) throw new Error("no REEL-START / REEL-END in index.html");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "reel-"));
fs.writeFileSync(path.join(dir, "reel.html"), `<!doctype html><html><head><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Poppins:wght@700&display=block" rel="stylesheet">
<style>html,body{margin:0;background:#000}canvas{display:block}</style></head><body><canvas id="c" width="1920" height="1080"></canvas>
<script>${src.slice(a, b)}</script><script>${fs.readFileSync(path.join(here, "audio.js"), "utf8")}</script>
<script>const cv = document.getElementById("c"), ctx = cv.getContext("2d"); window.SUB = 1;
window.seek = t => pnReel.draw(ctx, t, cv.width, cv.height, window.SUB);
window.ready = document.fonts.load('700 100px "Poppins"').then(() => (seek(0), true));</script></body></html>`);
const FPS = 60, SUBS = 3, LEN = 15;
const br = await chromium.launch(), p = await br.newPage({ viewport: { width: 1920, height: 1080 } });
p.on("pageerror", e => console.log("page error:", e.message));
await p.goto("file://" + path.join(dir, "reel.html")); await p.evaluate(() => window.ready);
if (beats) {
  fs.mkdirSync(beats, { recursive: true });
  for (let i = 0; i < 30; i++) { const t = i * .5 + .4; await p.evaluate(t => seek(t), t); await p.screenshot({ path: path.join(beats, `beat${String(i + 1).padStart(2, "0")}.png`) }); }
  await br.close(); console.log("30 beats in", beats); process.exit(0);
}
const [L, R] = await p.evaluate(() => pnReelAudio());
const n = L.length, wav = Buffer.alloc(44 + n * 4), wavPath = path.join(dir, "reel.wav");
wav.write("RIFF", 0); wav.writeUInt32LE(36 + n * 4, 4); wav.write("WAVEfmt ", 8); wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(2, 22);
wav.writeUInt32LE(44100, 24); wav.writeUInt32LE(44100 * 4, 28); wav.writeUInt16LE(4, 32); wav.writeUInt16LE(16, 34); wav.write("data", 36); wav.writeUInt32LE(n * 4, 40);
const s16 = v => Math.max(-32767, Math.min(32767, Math.round(v * 32767)));
for (let i = 0; i < n; i++) { wav.writeInt16LE(s16(L[i]), 44 + i * 4); wav.writeInt16LE(s16(R[i]), 46 + i * 4); }
fs.writeFileSync(wavPath, wav);
await p.evaluate(() => { window.SUB = 3; });
const ff = spawn("ffmpeg", ["-loglevel", "error", "-y", "-f", "image2pipe", "-framerate", String(FPS * SUBS), "-c:v", "mjpeg", "-i", "-", "-i", wavPath,
  "-filter_complex", `[0:v]tmix=frames=${SUBS},select='eq(mod(n\\,${SUBS})\\,${SUBS - 1})',setpts=N/(${FPS}*TB)[v]`, "-map", "[v]", "-map", "1:a",
  "-r", String(FPS), "-c:v", "libx264", "-preset", "slow", "-crf", "15", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", OUT], { stdio: ["pipe", "inherit", "inherit"] });
const total = FPS * LEN * SUBS;
for (let i = 0; i < total; i++) {
  /* subframe k of frame f sits at f/60 - (2-k)/180, so the blend ends on the frame's own time */
  const f = Math.floor(i / SUBS), k = i % SUBS;
  await p.evaluate(t => seek(t), f / FPS - (SUBS - 1 - k) / (FPS * SUBS));
  if (!ff.stdin.write(await p.screenshot({ type: "jpeg", quality: 95 }))) await new Promise(r => ff.stdin.once("drain", r));
  if (i % 450 === 0) console.log(Math.round(i / total * 100) + "%");
}
ff.stdin.end(); await new Promise(r => ff.on("close", r)); await br.close(); fs.rmSync(dir, { recursive: true, force: true });
console.log("done:", OUT);
