/* Made-up logos for the test kit (no real art: the repo is public). Each copies a SHAPE that real logos from SteamGridDB
   come in and that has gone wrong on the queue rows before (the owner's screenshot, Oct 2026):
   wide two-line, tall starburst, near-black, chunky square, very long one-liner, lots of see-through padding,
   wide script, tall narrow. LOGOS[i].url is an SVG data: URL, so nothing comes from the network and the canvas can
   read its pixels. */
const svg = (w, h, body) => "data:image/svg+xml;charset=utf-8," + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`);
const T = (x, y, s, fill, txt, extra = "") =>
  `<text x="${x}" y="${y}" font-family="Georgia, serif" font-weight="900" font-size="${s}" fill="${fill}" stroke="#fff" stroke-width="3" paint-order="stroke" ${extra}>${txt}</text>`;
const star = (cx, cy, r1, r2, n) => Array.from({ length: n * 2 }, (_, i) => {
  const r = i % 2 ? r2 : r1, a = i / (n * 2) * Math.PI * 2 - Math.PI / 2;
  return `${(cx + Math.cos(a) * r).toFixed(1)},${(cy + Math.sin(a) * r).toFixed(1)}`; }).join(" ");

export const LOGOS = [
  { shape: "wide two-line", url: svg(600, 220, T(20, 100, 92, "#d8262b", "STARFALL", 'font-style="italic" transform="rotate(-8 300 110)"') + T(150, 190, 80, "#fff", "ODYSSEY", 'transform="rotate(-8 300 110)"')) },
  { shape: "tall starburst", url: svg(420, 420, `<polygon points="${star(210, 210, 208, 120, 13)}" fill="#e01e2e" stroke="#fff" stroke-width="8"/>` + T(70, 250, 120, "#111", "CT")) },
  { shape: "near-black", url: svg(420, 260, `<circle cx="210" cy="110" r="100" fill="none" stroke="#0d0d0d" stroke-width="22"/>` + `<text x="125" y="150" font-family="Arial" font-weight="900" font-size="120" fill="#0d0d0d">ND</text><text x="40" y="250" font-family="Arial" font-weight="700" font-size="50" textLength="340" lengthAdjust="spacing" fill="#141414">NEON DRIFT</text>`) },
  { shape: "chunky square", url: svg(500, 400, `<path d="M40 380 L120 20 L300 20 Q420 20 400 150 Q380 260 230 260 L200 380 Z" fill="#ffd400" stroke="#111" stroke-width="16"/>` + T(160, 220, 110, "#e8f", "EoD")) },
  { shape: "very long one-liner", url: svg(980, 120, T(10, 95, 96, "#7fd3ff", "MOONLIT FARM DELUXE", 'textLength="960" lengthAdjust="spacingAndGlyphs"')) },
  { shape: "big see-through padding", url: svg(800, 800, T(250, 430, 110, "#ff8a00", "IRON") + T(300, 520, 90, "#fff", "SAGA II")) },
  { shape: "wide script", url: svg(720, 260, T(20, 170, 150, "#1e6bff", "Pixel", 'font-style="italic" transform="skewX(-12)"') + T(400, 230, 110, "#ffd000", "Q", 'font-style="italic"')) },
  { shape: "tall narrow", url: svg(200, 520, T(30, 160, 140, "#c4f", "T") + T(30, 320, 140, "#fff", "L") + T(30, 480, 140, "#c4f", "N")) }
];
