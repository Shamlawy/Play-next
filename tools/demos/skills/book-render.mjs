/* Films the skills book demo (tools/demos/skills/book.html) to an MP4, frame by frame like tools/reel/render.mjs.
     DATA=<games.json> node tools/demos/skills/book-render.mjs out.mp4 [--fold] [--beats dir]
   DATA is the same private file shoot.mjs reads (real titles never go in the repo); without it the made-up default plays.
   --fold films the folded width (412), else unfolded (860). --beats saves a picture every half second instead. */
import { chromium } from "playwright";
import { spawn } from "child_process"; import fs from "fs"; import os from "os"; import path from "path";
import { fileURLToPath } from "url";
const here = path.dirname(fileURLToPath(import.meta.url)), args = process.argv.slice(2);
const fold = args.includes("--fold"), bi = args.indexOf("--beats"), beats = bi >= 0 ? args[bi + 1] : null, OUT = beats ? null : args.find(a => !a.startsWith("--")) || "book.mp4";
let html = fs.readFileSync(path.join(here, "book.html"), "utf8");
if (process.env.DATA) { const d = JSON.parse(fs.readFileSync(process.env.DATA, "utf8"));
  /* the book's order = the order you finished them ("order": [keys] in the DATA file), the latest last */
  const games = d.order ? d.order.map(k => d.games.find(g => g.key === k)) : d.games;
  const book = { games, cover: d.cover || d.build.rows.slice(0, 3).map(r => r[0]) };
  html = html.replace("<script>\nconst DEF", `<script>window.BOOK = ${JSON.stringify(book)};</script>\n<script>\nconst DEF`); }
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "book-")), file = path.join(dir, "book.html"); fs.writeFileSync(file, html);
const vp = fold ? { width: 412, height: 892 } : { width: 860, height: 1040 }, FPS = 60, SUBS = 2;
const br = await chromium.launch(), p = await br.newPage({ viewport: vp, deviceScaleFactor: 2 });
p.on("pageerror", e => console.log("page error:", e.message));
await p.goto("file://" + file); await p.evaluate(() => window.ready); await p.waitForTimeout(300);
const LEN = await p.evaluate(() => window.LEN);
if (beats) {
  fs.mkdirSync(beats, { recursive: true });
  for (let t = 0, i = 0; t <= LEN; t += .5, i++) { await p.evaluate(t => seek(t), t); await p.screenshot({ path: path.join(beats, `b${String(i).padStart(2, "0")}-${t.toFixed(1)}.png`) }); }
  await br.close(); console.log("beats in", beats); process.exit(0);
}
/* page sounds: a short filtered paper noise at each turn (softer and shorter in the riffle), a soft tick for the tap */
const turns = await p.evaluate(() => TURNS()), tapAt = await p.evaluate(() => T.tap), SR = 44100, n = Math.ceil(SR * LEN);
const Lc = new Float32Array(n), Rc = new Float32Array(n); let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647 * 2 - 1;
const burst = (t0, amp, dur, pan) => { let lp = 0, hp = 0, prev = 0; const s0 = Math.floor(t0 * SR);
  for (let i = 0; i < dur * SR && s0 + i < n; i++) { const x = i / SR, env = Math.min(1, x / .012) * Math.exp(-x / (dur * .32)) * (1 + .6 * Math.sin(x * 70));
    const w = rnd(); lp += .35 * (w - lp); hp = .92 * (hp + lp - prev); prev = lp; const v = hp * env * amp * .5;
    Lc[s0 + i] += v * (1 - pan * .4); Rc[s0 + i] += v * (1 + pan * .4); } };
turns.forEach(([t, a], i) => burst(t + .06, a, a > .8 ? .32 : .2, i % 2 ? .3 : -.3));
{ const s0 = Math.floor(tapAt * SR); for (let i = 0; i < .05 * SR; i++) { const v = Math.sin(i / SR * 2 * Math.PI * 1500) * Math.exp(-i / SR / .008) * .18; Lc[s0 + i] += v; Rc[s0 + i] += v; } }
const wav = Buffer.alloc(44 + n * 4), wavPath = path.join(dir, "book.wav");
wav.write("RIFF", 0); wav.writeUInt32LE(36 + n * 4, 4); wav.write("WAVEfmt ", 8); wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(2, 22);
wav.writeUInt32LE(SR, 24); wav.writeUInt32LE(SR * 4, 28); wav.writeUInt16LE(4, 32); wav.writeUInt16LE(16, 34); wav.write("data", 36); wav.writeUInt32LE(n * 4, 40);
const s16 = v => Math.max(-32767, Math.min(32767, Math.round(v * 32767)));
for (let i = 0; i < n; i++) { wav.writeInt16LE(s16(Lc[i]), 44 + i * 4); wav.writeInt16LE(s16(Rc[i]), 46 + i * 4); }
fs.writeFileSync(wavPath, wav);
const ff = spawn("ffmpeg", ["-loglevel", "error", "-y", "-f", "image2pipe", "-framerate", String(FPS * SUBS), "-c:v", "mjpeg", "-i", "-", "-i", wavPath,
  "-filter_complex", `[0:v]tmix=frames=${SUBS},select='eq(mod(n\\,${SUBS})\\,${SUBS - 1})',setpts=N/(${FPS}*TB)[v]`, "-map", "[v]", "-map", "1:a", "-r", String(FPS),
  "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", OUT], { stdio: ["pipe", "inherit", "inherit"] });
const total = Math.round(FPS * LEN) * SUBS;
for (let i = 0; i < total; i++) {
  const f = Math.floor(i / SUBS), k = i % SUBS;
  await p.evaluate(t => seek(t), f / FPS - (SUBS - 1 - k) / (FPS * SUBS));
  if (!ff.stdin.write(await p.screenshot({ type: "jpeg", quality: 92 }))) await new Promise(r => ff.stdin.once("drain", r));
  if (i % 240 === 0) console.log(Math.round(i / total * 100) + "%");
}
ff.stdin.end(); await new Promise(r => ff.on("close", r)); await br.close(); fs.rmSync(dir, { recursive: true, force: true });
console.log("done:", OUT);
