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
  const games = (d.order ? d.order.map(k => d.games.find(g => g.key === k)) : d.games).filter(g => g && g.skills && g.skills.length);
  const book = { games, cover: d.cover || d.build.rows.slice(0, 3).map(r => r[0]) };
  const at = html.search(/<script>\r?\nconst DEF/);   /* \r?: a Windows checkout has CRLF line endings */
  if (at < 0) { console.error("book.html: can't find where to put DATA"); process.exit(1); }
  html = html.slice(0, at) + `<script>window.BOOK = ${JSON.stringify(book)};</script>\n` + html.slice(at); }
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
/* the sounds, made here sample by sample (no sound files): a soft shimmer and a rising chime as the cover opens and a low
   thump when it lands, airy paper whooshes for each page of the riffle, a bell and a few sparkles when the book lands on the
   game, a long swish and a paper flap for the finger's turn, a small glassy tick for the tap; then a light room reverb */
const turns = await p.evaluate(() => TURNS()), E = await p.evaluate(() => EVENTS()), SR = 44100, n = Math.ceil(SR * LEN);
const Lc = new Float32Array(n), Rc = new Float32Array(n); let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647 * 2 - 1;
const put = (i, v, pan) => { if (i >= 0 && i < n) { Lc[i] += v * (1 - pan) * .9; Rc[i] += v * (1 + pan) * .9; } };
/* a struck bell or chime: a few partials, each fading at its own speed */
const bell = (t0, f, amp, pan = 0, len = 2.2, parts = [[1, 1, 1], [2.01, .35, .6], [2.76, .28, .42], [4.07, .12, .28], [5.43, .06, .18]]) => {
  const s0 = Math.floor(t0 * SR);
  for (let i = 0; i < len * SR; i++) { const x = i / SR, at = Math.min(1, x / .003); let v = 0;
    for (const [m, a, d] of parts) v += a * Math.sin(2 * Math.PI * f * m * x + .3 * Math.sin(2 * Math.PI * 5.2 * x)) * Math.exp(-x / (d * len * .45));
    put(s0 + i, v * amp * at, pan); } };
/* a held glassy shimmer: soft sines with a slow swell and a gentle flutter */
const pad = (t0, len, notes, amp, pan = 0) => { const s0 = Math.floor(t0 * SR);
  for (let i = 0; i < len * SR; i++) { const x = i / SR, env = Math.sin(Math.PI * Math.min(1, x / len)) ** 2; let v = 0;
    notes.forEach((f, k) => { v += Math.sin(2 * Math.PI * f * x + k) * (1 + .25 * Math.sin(2 * Math.PI * (4.5 + k) * x)) + .5 * Math.sin(2 * Math.PI * f * 1.003 * x); });
    put(s0 + i, v * env * amp / notes.length, pan * Math.sin(x * 1.3)); } };
/* filtered noise whose band sweeps from f0 to f1: paper moving through air */
const whoosh = (t0, len, amp, f0, f1, pan = 0, q = 1.1, peak = .4) => { const s0 = Math.floor(t0 * SR); let x1 = 0, x2 = 0, y1 = 0, y2 = 0, c = null;
  for (let i = 0; i < len * SR; i++) { const u = i / (len * SR);
    if (i % 32 === 0) { const f = f0 * Math.pow(f1 / f0, u), w = 2 * Math.PI * f / SR, al = Math.sin(w) / (2 * q), a0 = 1 + al; c = [al / a0, 0, -al / a0, -2 * Math.cos(w) / a0, (1 - al) / a0]; }
    const xn = rnd(), y = c[0] * xn + c[1] * x1 + c[2] * x2 - c[3] * y1 - c[4] * y2; x2 = x1; x1 = xn; y2 = y1; y1 = y;
    const env = u < peak ? Math.sin(Math.PI / 2 * u / peak) ** 2 : Math.cos(Math.PI / 2 * (u - peak) / (1 - peak)) ** 2;
    put(s0 + i, y * env * amp, pan * (u * 2 - 1)); } };
const thump = (t0, amp) => { const s0 = Math.floor(t0 * SR); let lp = 0;
  for (let i = 0; i < .35 * SR; i++) { const x = i / SR, f = 62 + 40 * Math.exp(-x / .03); lp += .08 * (rnd() - lp);
    put(s0 + i, (Math.sin(2 * Math.PI * f * x) * .8 + lp * 1.4) * Math.exp(-x / .09) * amp, 0); } };
const flap = (t0, amp, pan) => whoosh(t0, .12, amp, 2600, 900, pan, .8, .15);
/* the tap: a small glassy tick */
bell(E.tap, 2637, .05, 0, .6); bell(E.tap + .03, 3951, .025, .2, .5);
/* the book rises: a low airy swell; the sheen: a thin shimmer that crosses left to right */
whoosh(E.rise - .1, 1.4, .05, 180, 700, 0, .7, .6);
pad(E.sheen - .1, 2.6, [1046.5, 1568, 2349.3], .05, .6);
/* the cover opens: a warm shimmer under it, a chime that climbs as it lifts, a low thump when it lands and one bell on top */
pad(E.open - .2, 3.4, [523.25, 783.99, 1174.66, 1318.5], .07);
[1046.5, 1318.5, 1568, 1975.5, 2349.3, 2637, 3136].forEach((f, k) => bell(E.open + .12 + k * .11, f, .055 * (1 - k * .06), (k / 6) * 1.2 - .6, 1.8));
whoosh(E.open + .3, 1.5, .09, 300, 1400, -.5, .9, .55);
thump(E.openLand, .32); bell(E.openLand + .02, 784, .05, 0, 2.6);
/* the riffle: each page an airy whoosh, a little higher and softer as they go, with a light flap as it lands */
turns.forEach(([t, size, d], k) => { const pan = k % 2 ? .35 : -.35;
  whoosh(t + d * .1, d * .9, .16 * size, 500 + 120 * k, 2200 + 180 * k, pan, 1.0, .45); flap(t + d * .92, .06 * size, -pan); });
/* the landing: a bell, its fifth a beat later, sparkles, then a tiny tick per pip as they fill */
bell(E.land + .05, 1318.5, .11, -.15, 3); bell(E.land + .2, 1975.5, .07, .15, 2.6); pad(E.land, 2.4, [659.25, 987.77, 1318.5, 1975.5], .05);
for (let k = 0; k < 14; k++) bell(E.land + .1 + Math.abs(rnd()) * .9, 3000 + Math.abs(rnd()) * 3600, .018 + Math.abs(rnd()) * .02, rnd() * .8, .35);
for (let k = 0; k < E.pips; k++) bell(E.land + .35 + k * .09, 2093 * Math.pow(2, k / 12 * 2), .022, .3, .4);
/* the finger: a soft peel, a long swish as the page goes over, a flap and a last small chime when it lies down */
whoosh(E.peel, .35, .05, 3000, 5000, .4, 1.4, .3);
whoosh(E.peel + .3, E.release - E.peel + .2, .14, 600, 2600, .6, .9, .7);
whoosh(E.release, 1.0, .12, 2400, 700, -.5, 1, .25); flap(E.drop, .12, -.4); bell(E.drop + .05, 1568, .04, -.3, 2);
/* a light room: four combs and two allpasses per side (Schroeder), mixed in softly */
const verb = (x, off) => { const out = new Float32Array(n), combs = [1557, 1617, 1491, 1422].map(d => ({ d: d + off, b: new Float32Array(d + off), i: 0, lp: 0 }));
  for (let i = 0; i < n; i++) { let s = 0; for (const c of combs) { const y = c.b[c.i]; c.lp = y * .72 + c.lp * .28; c.b[c.i] = x[i] + c.lp * .82; c.i = (c.i + 1) % c.d; s += y; } out[i] = s / 4; }
  for (const d of [225 + off, 556 + off]) { const b = new Float32Array(d); let j = 0; for (let i = 0; i < n; i++) { const y = b[j], v = out[i] + y * .5; b[j] = v; out[i] = y - v * .5; j = (j + 1) % d; } }
  return out; };
const wl = verb(Lc, 0), wr = verb(Rc, 23);
let pk = 0; for (let i = 0; i < n; i++) { Lc[i] += wl[i] * .32; Rc[i] += wr[i] * .32; pk = Math.max(pk, Math.abs(Lc[i]), Math.abs(Rc[i])); }
const g = pk > .89 ? .89 / pk : 1; for (let i = 0; i < n; i++) { Lc[i] *= g; Rc[i] *= g; }
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
