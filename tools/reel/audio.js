/* the soundtrack: 120 BPM, a kick on every beat, hats between, a hit on every scene change, a riser into the last full bar */
async function pnReelAudio() {
  const SR = 44100, LEN = 15, ac = new OfflineAudioContext(2, SR * LEN, SR), out = ac.createGain(); out.gain.value = .8;
  const comp = ac.createDynamicsCompressor(); comp.threshold.value = -14; comp.ratio.value = 4; out.connect(comp); comp.connect(ac.destination);
  const noise = (() => { const b = ac.createBuffer(1, SR * 3, SR), d = b.getChannelData(0); let s = 7; for (let i = 0; i < d.length; i++) { s = (s * 16807) % 2147483647; d[i] = s / 1073741823.5 - 1; } return b; })();
  const env = (g, t, a, peak, dec) => { g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(peak, t + a); g.gain.exponentialRampToValueAtTime(.0001, t + a + dec); };
  const kick = t => { const o = ac.createOscillator(), g = ac.createGain(); o.frequency.setValueAtTime(140, t); o.frequency.exponentialRampToValueAtTime(42, t + .12); env(g, t, .002, 1, .38); o.connect(g); g.connect(out); o.start(t); o.stop(t + .5);
    const n = ac.createBufferSource(), f = ac.createBiquadFilter(), g2 = ac.createGain(); n.buffer = noise; f.type = "highpass"; f.frequency.value = 2500; env(g2, t, .001, .18, .02); n.connect(f); f.connect(g2); g2.connect(out); n.start(t, .3, .05); };
  const hat = (t, v) => { const n = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain(); n.buffer = noise; f.type = "highpass"; f.frequency.value = 8000; env(g, t, .001, v, .05); n.connect(f); f.connect(g); g.connect(out); n.start(t, Math.random() * 2, .1); };
  const bass = (t, hz, d) => { const o = ac.createOscillator(), f = ac.createBiquadFilter(), g = ac.createGain(); o.type = "sawtooth"; o.frequency.value = hz; f.type = "lowpass"; f.frequency.setValueAtTime(900, t); f.frequency.exponentialRampToValueAtTime(140, t + d); env(g, t + .02, .01, .22, d); o.connect(f); f.connect(g); g.connect(out); o.start(t); o.stop(t + d + .1); };
  const hit = (t, root) => {
    const n = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain(); n.buffer = noise; f.type = "bandpass"; f.frequency.value = 1800; f.Q.value = .7; env(g, t, .001, .55, .28); n.connect(f); f.connect(g); g.connect(out); n.start(t, 1, .4);
    [1, 1.5, 2, 2.52].forEach((m, i) => { const o = ac.createOscillator(), lf = ac.createBiquadFilter(), g2 = ac.createGain(); o.type = i % 2 ? "square" : "sawtooth"; o.frequency.value = root * m * 2; lf.type = "lowpass"; lf.frequency.setValueAtTime(4000, t); lf.frequency.exponentialRampToValueAtTime(300, t + .3); env(g2, t, .003, .07, .34); o.connect(lf); lf.connect(g2); g2.connect(out); o.start(t); o.stop(t + .5); });
  };
  const roots = [55, 43.65, 65.41, 49];   /* A F C G, a bar each */
  for (let b = 0; b < 30; b++) {
    const t = b * .5, bar = Math.floor(b / 4), root = roots[bar % 4];
    if (t < 14.5) kick(t);
    hat(t + .25, b % 4 === 3 ? .16 : .1);
    if (b >= 24 && b < 28) hat(t + .125, .07), hat(t + .375, .07);   /* double time in the last full bar */
    if (t < 14.5) bass(t, root, .42);
    if (b % 4 === 0) hit(t, root);
  }
  /* riser into the last full bar (10 → 12) */
  { const n = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain(); n.buffer = noise; n.loop = true; f.type = "bandpass"; f.Q.value = 2; f.frequency.setValueAtTime(300, 10); f.frequency.exponentialRampToValueAtTime(7000, 12); g.gain.setValueAtTime(0, 10); g.gain.linearRampToValueAtTime(.32, 11.96); g.gain.linearRampToValueAtTime(0, 12.01); n.connect(f); f.connect(g); g.connect(out); n.start(10); n.stop(12.05);
    const o = ac.createOscillator(), g2 = ac.createGain(); o.type = "sawtooth"; o.frequency.setValueAtTime(110, 10); o.frequency.exponentialRampToValueAtTime(880, 12); g2.gain.setValueAtTime(0, 10); g2.gain.linearRampToValueAtTime(.06, 11.96); g2.gain.linearRampToValueAtTime(0, 12.01); o.connect(g2); g2.connect(out); o.start(10); o.stop(12.05); }
  /* the end: a bigger hit on the title, a short drop as it folds back into the pixel */
  hit(14, 110); kick(14);
  { const o = ac.createOscillator(), g = ac.createGain(); o.frequency.setValueAtTime(600, 14.5); o.frequency.exponentialRampToValueAtTime(60, 14.85); env(g, 14.5, .01, .25, .35); o.connect(g); g.connect(out); o.start(14.5); o.stop(15); }
  const buf = await ac.startRendering();
  return [Array.from(buf.getChannelData(0)), Array.from(buf.getChannelData(1))];
}
