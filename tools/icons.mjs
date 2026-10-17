/* Draws Play next's icons in the v247 menu style (v296, the owner: "I hate the icons … default ugly icons"): 24x24,
   fill-only (currentColor), one part at 35% (F(...)). These are the drawings behind index.html's GICON (Settings,
   Customise and Edit groups, Most used); add a new one here, look at the contact sheet, then paste its markup into GICON.
     node tools/icons.mjs <out dir>     → <out>/icons.json (name → inner svg) + <out>/sheet.html (open it, or screenshot it)
   Helpers: R rect, C circle, E ellipse, P path (eo = evenodd hole), poly, band (ring sector, 0° = up), ring, star, spark
   (the Taste tab's 4-point star), rot. */
import { writeFileSync, mkdirSync } from "node:fs";
import path from "node:path";
const OUT = process.argv[2] || "."; mkdirSync(OUT, { recursive: true });
const n = v => +v.toFixed(2);
const R = (x, y, w, h, r = 0) => `<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}"${r ? ` rx="${n(r)}"` : ""}/>`;
const C = (cx, cy, r) => `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(r)}"/>`;
const E = (cx, cy, rx, ry) => `<ellipse cx="${n(cx)}" cy="${n(cy)}" rx="${n(rx)}" ry="${n(ry)}"/>`;
const P = (d, eo) => `<path${eo ? ' fill-rule="evenodd"' : ""} d="${d}"/>`;
const F = (...s) => `<g opacity=".35">${s.join("")}</g>`;
const pt = (cx, cy, r, a) => { const t = (a - 90) * Math.PI / 180; return [n(cx + r * Math.cos(t)), n(cy + r * Math.sin(t))]; };
const poly = pts => P("M" + pts.map(p => p.join(" ")).join("L") + "z");
/* annular sector from angle a0 to a1 (degrees, 0 = up, clockwise), round ends */
const band = (cx, cy, r1, r2, a0, a1) => {
  const [x1, y1] = pt(cx, cy, r2, a0), [x2, y2] = pt(cx, cy, r2, a1), [x3, y3] = pt(cx, cy, r1, a1), [x4, y4] = pt(cx, cy, r1, a0);
  const big = a1 - a0 > 180 ? 1 : 0, cap = n((r2 - r1) / 2);
  return P(`M${x1} ${y1}A${r2} ${r2} 0 ${big} 1 ${x2} ${y2}A${cap} ${cap} 0 0 1 ${x3} ${y3}A${r1} ${r1} 0 ${big} 0 ${x4} ${y4}A${cap} ${cap} 0 0 1 ${x1} ${y1}z`);
};
const ringD = (cx, cy, r) => `M${n(cx - r)} ${cy}a${r} ${r} 0 1 0 ${n(2 * r)} 0a${r} ${r} 0 1 0 ${n(-2 * r)} 0z`;
const ring = (cx, cy, r1, r2) => P(ringD(cx, cy, r2) + ringD(cx, cy, r1), 1);
const star = (cx, cy, R1, r1, k = 5, rot = 0) => poly([...Array(k * 2)].map((_, i) => pt(cx, cy, i % 2 ? r1 : R1, rot + i * 180 / k)));
/* a 4-point sparkle like the Taste tab's */
const spark = (cx, cy, r) => { const q = r * .18; return P(`M${n(cx)} ${n(cy - r)}C${n(cx + q)} ${n(cy - q)} ${n(cx + q)} ${n(cy - q)} ${n(cx + r)} ${n(cy)}C${n(cx + q)} ${n(cy + q)} ${n(cx + q)} ${n(cy + q)} ${n(cx)} ${n(cy + r)}C${n(cx - q)} ${n(cy + q)} ${n(cx - q)} ${n(cy + q)} ${n(cx - r)} ${n(cy)}C${n(cx - q)} ${n(cy - q)} ${n(cx - q)} ${n(cy - q)} ${n(cx)} ${n(cy - r)}z`); };
const rot = (deg, cx, cy, ...s) => `<g transform="rotate(${deg} ${cx} ${cy})">${s.join("")}</g>`;

const I = {};
/* ---- app Settings ---- */
I["Sound and reactions"] = P("M3.5 9.2a1.2 1.2 0 0 1 1.2-1.2H7.6l4.6-3.9a.9.9 0 0 1 1.5.7v14.4a.9.9 0 0 1-1.5.7L7.6 16H4.7a1.2 1.2 0 0 1-1.2-1.2z")
  + band(13.4, 12, 3.4, 5.4, 45, 135) + F(band(13.4, 12, 6.6, 8.6, 40, 140));
I["Nexi"] = F(C(12, 12.6, 9.4)) + R(7.4, 8.4, 3.2, 6.8, 1.6) + R(13.4, 8.4, 3.2, 6.8, 1.6) + C(12, 2.6, 1.5) + R(11.25, 3, 1.5, 2.6, .5);
I["Extra features"] = F(P("M9.2 2.6h5.6a1 1 0 0 1 0 2h-.6v4.1l5.3 8.9a2.6 2.6 0 0 1-2.2 3.9H6.7a2.6 2.6 0 0 1-2.2-3.9l5.3-8.9V4.6h-.6a1 1 0 0 1 0-2z"))
  + P("M6.9 14.4h10.2l2.4 3.9a1.6 1.6 0 0 1-1.4 2.4H5.9a1.6 1.6 0 0 1-1.4-2.4z") + C(11, 11.6, 1.1) + C(13.6, 9.4, .8);
I["Motion"] = C(15.6, 12, 5.6) + F(R(2, 6.4, 7.6, 2.4, 1.2), R(1, 10.8, 7, 2.4, 1.2), R(2, 15.2, 7.6, 2.4, 1.2));
I["Header cards"] = F(R(2.5, 3.5, 19, 17, 3)) + P("M5.5 3.5h13a3 3 0 0 1 3 3v3.5h-19V6.5a3 3 0 0 1 3-3z") + R(5.5, 13, 8, 2, 1) + R(5.5, 16.4, 5, 2, 1);
I["Look of the app"] = F(P("M12 2.5c5.5 0 9.5 3.7 9.5 8.2 0 3-2.3 4.6-4.7 4.6h-1.6a1.7 1.7 0 0 0-1.2 2.9 1.9 1.9 0 0 1-1.4 3.3C6.9 21.5 2.5 17.3 2.5 12S6.7 2.5 12 2.5z"))
  + C(7.4, 11.6, 1.8) + C(10, 7, 1.8) + C(15.2, 7.2, 1.8);
I["Getting around"] = F(C(12, 12, 9.5)) + rot(45, 12, 12, P("M12 4.6l2.9 7.4H9.1z"), F(P("M9.1 12h5.8L12 19.4z")));
I["Games database"] = F(P("M4.5 6v12c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V6c0 1.7-3.4 3-7.5 3S4.5 7.7 4.5 6z")) + E(12, 6, 7.5, 3)
  + P("M4.5 11.2c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3v2c0 1.7-3.4 3-7.5 3s-7.5-1.3-7.5-3z");
I["This build"] = F(C(12, 12, 9.5)) + C(12, 7.4, 1.6) + R(10.6, 10.2, 2.8, 7.6, 1.4);
I["Price alerts"] = P("M3.6 6.4h5.6a1.8 1.8 0 0 1 1.27.53l7.3 7.3a1.8 1.8 0 0 1 0 2.54l-4.1 4.1a1.8 1.8 0 0 1-2.54 0l-7.3-7.3A1.8 1.8 0 0 1 2.3 13.3V7.7a1.3 1.3 0 0 1 1.3-1.3zm3.1 2.6a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2z", 1) + F(band(15.4, 8.6, 2.4, 4.2, 0, 90), band(15.4, 8.6, 5.6, 7.4, 0, 90));
I["Steam"] = F(P("M7.2 6.5h9.6a5 5 0 0 1 4.9 4.1l.9 5.1a2.9 2.9 0 0 1-5 2.4l-2-2.2H8.4l-2 2.2a2.9 2.9 0 0 1-5-2.4l.9-5.1a5 5 0 0 1 4.9-4.1z"))
  + R(6.2, 8.4, 1.8, 5.6, .6) + R(4.3, 10.3, 5.6, 1.8, .6) + C(16.6, 9.6, 1.25) + C(18.6, 12.2, 1.25);
I["Cloud backup"] = F(P("M7 19.5a4.6 4.6 0 0 1-.7-9.15A6.2 6.2 0 0 1 18.2 8.9a5.3 5.3 0 0 1-.5 10.6z"))
  + P("M12 8.6l3.9 3.9-1.5 1.5-1.35-1.35v4.6h-2.1v-4.6L9.6 14l-1.5-1.5z");
I["Your data"] = F(P("M4 9h16v9.5a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 18.5z")) + R(2.5, 3.5, 19, 5, 2) + R(9, 11.6, 6, 2.2, 1.1);
/* ---- Home ---- */
I["Tiles on Home"] = R(3, 3, 8, 8, 2.4) + F(R(13, 3, 8, 8, 2.4), R(3, 13, 8, 8, 2.4)) + R(13, 13, 8, 8, 2.4);
I["How tiles look"] = F(R(2.5, 2.5, 19, 19, 5)) + P("M12 6.2c2.7 3.1 4.2 5.4 4.2 7.4a4.2 4.2 0 0 1-8.4 0c0-2 1.5-4.3 4.2-7.4z");
I["Playing now cards"] = F(R(2.5, 4, 19, 16, 4)) + P("M10 8.3a.8.8 0 0 1 1.2-.7l5.6 3.7a.8.8 0 0 1 0 1.4l-5.6 3.7a.8.8 0 0 1-1.2-.7z");
I["The standings tile"] = R(8.8, 4, 6.4, 17, 1.6) + F(R(2, 9.5, 6, 11.5, 1.6), R(16, 12.5, 6, 8.5, 1.6)) + star(12, 8.4, 2.3, 1, 5);
/* ---- Games ---- */
I["On deck cards"] = F(R(9, 2.5, 12.5, 15.5, 2.6)) + R(2.5, 6, 12.5, 15.5, 2.6);
I["On deck"] = F(C(12, 12, 9.5)) + ring(12, 12, 4.6, 6.8) + C(12, 12, 2.2);
I["Queue cards"] = R(2.5, 3.5, 5, 5, 1.6) + R(2.5, 9.5, 5, 5, 1.6) + R(2.5, 15.5, 5, 5, 1.6) + F(R(9, 4.3, 12.5, 3.4, 1.7), R(9, 10.3, 12.5, 3.4, 1.7), R(9, 16.3, 12.5, 3.4, 1.7));
I["Character art"] = C(12, 6.6, 4) + F(P("M4 20.2c0-4.3 3.6-7.4 8-7.4s8 3.1 8 7.4a1.3 1.3 0 0 1-1.3 1.3H5.3A1.3 1.3 0 0 1 4 20.2z"));
I["Opening a card"] = F(R(6.5, 6.5, 11, 11, 2.4)) + P("M3 4.2A1.2 1.2 0 0 1 4.2 3H9v2.4H5.4V9H3zM21 4.2A1.2 1.2 0 0 0 19.8 3H15v2.4h3.6V9H21zM3 19.8A1.2 1.2 0 0 0 4.2 21H9v-2.4H5.4V15H3zM21 19.8a1.2 1.2 0 0 1-1.2 1.2H15v-2.4h3.6V15H21z");
I["Top board"] = P("M6.5 3h11v5.5a5.5 5.5 0 0 1-11 0z") + F(P("M6.5 5H3.6v1.8a4 4 0 0 0 3.6 4M17.5 5h2.9v1.8a4 4 0 0 1-3.6 4z"), R(10.6, 13.5, 2.8, 4, .6)) + R(7, 17.6, 10, 3.4, 1.2);
I["Library"] = R(3, 4, 4.6, 17, 1.4) + R(8.6, 2.5, 4.6, 18.5, 1.4) + F(rot(-16, 17.5, 20, R(14.6, 4.2, 4.6, 16.6, 1.4)));
I["A game's own page"] = F(P("M6 2.5h8.2l5.3 5.3v11.2a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5v-14A2.5 2.5 0 0 1 6 2.5z")) + P("M14.2 2.5l5.3 5.3h-3.6a1.7 1.7 0 0 1-1.7-1.7z")
  + R(6.6, 11.6, 9.8, 2, 1) + R(6.6, 15.4, 7, 2, 1);
/* ---- Plan ---- */
I["Timeline board"] = F(R(2.5, 3.5, 11, 4, 2), R(6.5, 10, 15, 4, 2)) + R(4.5, 16.5, 10, 4, 2) + R(16.4, 2, 2, 20, 1);
I["Labels on the board"] = F(R(8.5, 11, 13, 7.5, 3.75)) + P("M2.5 5.4A2.4 2.4 0 0 1 4.9 3h7.4a2.4 2.4 0 0 1 1.9.9l2.4 3.1-2.4 3.1a2.4 2.4 0 0 1-1.9.9H4.9a2.4 2.4 0 0 1-2.4-2.4z")
  + F(C(17.6, 14.75, 1.5));
I["Your evening"] = P("M14.6 2.8a8.8 8.8 0 1 0 6.6 12.6A7 7 0 0 1 14.6 2.8z") + F(spark(18.6, 5.6, 3.2), C(21, 10.4, 1));
/* ---- Taste ---- */
I["Player type card"] = F(R(2.5, 4, 19, 16, 3.5)) + C(8.6, 10.2, 2.5) + P("M4.8 16.6c0-2.1 1.7-3.4 3.8-3.4s3.8 1.3 3.8 3.4z") + R(14, 8.6, 5, 2, 1) + R(14, 12.6, 3.6, 2, 1);
I["The web"] = F(poly([0, 60, 120, 180, 240, 300].map(a => pt(12, 12, 10, a)))) + poly([[12, 5.4], [17.6, 9.6], [15.3, 16.4], [8.4, 15.6], [7.2, 9.4]]);
I["The big number"] = F(R(2.5, 2.5, 19, 19, 5)) + P("M11.2 6.6h2.6v10.8h-2.6V9.7l-2 1.1-1.1-2z") + R(8.4, 15.4, 7.2, 2, 1);
I["Conclusions"] = F(P("M12 2.2a7 7 0 0 1 4.2 12.6 2.4 2.4 0 0 0-1 1.9v.5H8.8v-.5a2.4 2.4 0 0 0-1-1.9A7 7 0 0 1 12 2.2z")) + R(8.6, 18.2, 6.8, 1.8, .9) + P("M9.6 21h4.8a2.4 2.4 0 0 1-4.8 0z") + spark(12, 9.4, 4.4);
/* ---- a game's Edit ---- */
I["Name"] = rot(45, 12, 12, R(9.6, 1.2, 4.8, 15, 1.2), P("M9.6 17.6h4.8L12 21.6z")) + F(R(13, 19.4, 8.5, 2.2, 1.1));
I["Release"] = F(R(2.5, 4.5, 19, 17, 3)) + P("M5.5 4.5h13a3 3 0 0 1 3 3v2h-19v-2a3 3 0 0 1 3-3z") + R(6.6, 2, 2.2, 5, 1.1) + R(15.2, 2, 2.2, 5, 1.1) + R(13.4, 13.4, 4.6, 4.6, 1.2);
I["Where it stands"] = F(E(12, 20.6, 6, 1.6)) + P("M12 2a7 7 0 0 1 7 7c0 4.6-5 9.4-6.4 10.6a.9.9 0 0 1-1.2 0C10 18.4 5 13.6 5 9a7 7 0 0 1 7-7zm0 4.4a2.6 2.6 0 1 0 0 5.2 2.6 2.6 0 0 0 0-5.2z", 1);
I["Platforms"] = F(R(6.5, 5, 11, 14, 1.4)) + P("M5.5 5v14H4.2A2.7 2.7 0 0 1 1.5 16.3V7.7A2.7 2.7 0 0 1 4.2 5z") + P("M18.5 5h1.3a2.7 2.7 0 0 1 2.7 2.7v8.6a2.7 2.7 0 0 1-2.7 2.7h-1.3z")
  + F(C(3.5, 9, 1)) + F(C(20.5, 14.6, 1));
I["Genres and tags"] = C(7.2, 7.2, 4.4) + F(R(13, 3, 8.4, 8.4, 2)) + F(C(7.2, 16.8, 4.4)) + poly([[17.2, 12.6], [21.6, 20.6], [12.8, 20.6]]);
I["Notes"] = F(P("M5 2.5h14A2.5 2.5 0 0 1 21.5 5v10.5l-6 6H5A2.5 2.5 0 0 1 2.5 19V5A2.5 2.5 0 0 1 5 2.5z")) + P("M21.5 15.5h-4a2 2 0 0 0-2 2v4z") + R(6.4, 7, 11.2, 2, 1) + R(6.4, 11, 8, 2, 1);
I["Hype"] = F(P("M12.4 1.8c.6 3.6 6.6 6.2 6.6 12.2a7 7 0 0 1-14 0c0-2.8 1.4-4.6 2.8-5.8.2 1.8 1 2.8 2 3.4-.3-4.2.8-7.4 2.6-9.8z"))
  + P("M12.3 10.6c.4 2.2 3.7 3.6 3.7 7.1a4 4 0 0 1-8 0c0-1.7.8-2.8 1.7-3.5.2 1 .6 1.6 1.2 2-.2-2.5.4-4.2 1.4-5.6z");
I["Critics"] = F(C(12, 12, 9.5)) + star(12, 12.4, 6.6, 2.8, 5);
I["My rating"] = F(ring(12, 12, 6.6, 9.6)) + band(12, 12, 6.6, 9.6, 0, 250) + C(12, 12, 2.6);
I["Played"] = F(C(12, 12, 9.5)) + P("M16.3 7.9l1.8 1.7-7.3 7.6-4.9-4.7 1.8-1.8 3 2.9z");
I["Art from SteamGridDB and the stores"] = F(R(6.5, 2.5, 15, 12, 2.6)) + P("M5 6.5h11.5A2.5 2.5 0 0 1 19 9v10a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 19V9A2.5 2.5 0 0 1 5 6.5zm1.6 12h9.6l-3.3-4.7-2.3 2.9-1.5-1.6zm1.9-7.6a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z", 1);
I["Cover and colour"] = F(R(4, 2.5, 16, 19, 3)) + P("M7 2.5h10a3 3 0 0 1 3 3v2.3L4 19.6V5.5a3 3 0 0 1 3-3z");
I["Backdrop"] = F(R(1.5, 5, 21, 14, 3)) + P("M1.5 15.2l5.6-5.5 4.4 4.3 3.2-2.9 6.8 6.1A3 3 0 0 1 19.5 19h-15a3 3 0 0 1-3-3z") + C(16.8, 8.8, 1.7);
I["Hub page figure"] = F(R(3, 2.5, 18, 19, 3)) + C(12, 7.6, 2.7) + P("M7.4 19.4v-2.8c0-2.6 2-4.6 4.6-4.6s4.6 2 4.6 4.6v2.8z");
I["Logo / character art"] = P("M10.3 3.5h3.4l5.6 16h-3.3l-1.2-3.6H9.2L8 19.5H4.7zm-.2 9.6h3.8L12 7.4z", 1) + F(R(2.5, 20.4, 19, 1.8, .9));
/* ---- Most used shortcuts ---- */
I["Grumble"] = F(C(12, 12, 9.5)) + rot(18, 8.4, 9, R(5.6, 8, 5.6, 2, 1)) + rot(-18, 15.6, 9, R(12.8, 8, 5.6, 2, 1)) + C(8.6, 12.6, 1.4) + C(15.4, 12.6, 1.4) + P("M8.4 17.6a3.9 3.9 0 0 1 7.2 0l-1.7.9a2 2 0 0 0-3.8 0z");
I["Speed boost"] = F(C(12, 12, 9.5)) + P("M13.6 2.8 6.4 13.1h4.8l-1.4 8.1 7.4-10.6h-4.9z");
I["What's new"] = spark(10, 13.2, 8.6) + F(spark(18.4, 5.4, 3.6), C(19, 18.8, 1.6));
I["Play style"] = F(P("M4.5 15h15a2 2 0 0 1 2 2v1.8a2.7 2.7 0 0 1-2.7 2.7H5.2a2.7 2.7 0 0 1-2.7-2.7V17a2 2 0 0 1 2-2z")) + R(10.8, 8, 2.4, 8, 1.2) + C(12, 6.8, 4.3) + F(C(17.6, 18.2, 1.3));
I["Diary & Timer"] = F(C(12, 13.4, 8.4)) + R(9.6, 1.6, 4.8, 2.4, 1.2) + R(11, 3, 2, 3, .6) + P("M11 8.4h2v4.8l3 2.2-1.2 1.6-3.8-2.8z");
/* the fallback, when a group has no icon of its own: the menu's gear */
I["_"] = F(P("M19.25 9.11 21.92 9.62v4.76l-2.67.51-.08.19 1.52 2.25-3.36 3.36-2.25-1.52-.19.08-.51 2.67H9.62l-.51-2.67-.19-.08-2.25 1.52-3.36-3.36 1.52-2.25-.08-.19-2.67-.51V9.62l2.67-.51.08-.19-1.52-2.25 3.36-3.36 2.25 1.52.19-.08.51-2.67h4.76l.51 2.67.19.08 2.25-1.52 3.36 3.36-1.52 2.25z")) + C(12, 12, 3.4);

writeFileSync(path.join(OUT, "icons.json"), JSON.stringify(I, null, 0));
const svg = (k, s) => `<svg viewBox="0 0 24 24" width="${s}" height="${s}" fill="currentColor">${I[k]}</svg>`;
writeFileSync(path.join(OUT, "sheet.html"), `<!doctype html><meta charset=utf-8><style>
body{background:#0a0e1b;color:#EAF2FF;font:500 11px system-ui;margin:16px;display:grid;grid-template-columns:repeat(8,120px);gap:10px}
div{display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center}
.t{width:46px;height:38px;border-radius:12px;display:grid;place-items:center;color:#7C92C4}
.t.on{background:#19213d;color:#EAF2FF;box-shadow:inset 0 0 0 1px rgba(234,242,255,.14)}
.g{width:34px;height:34px;border-radius:11px;background:rgba(77,163,255,.12);display:grid;place-items:center;color:#4DA3FF}
.row{flex-direction:row;gap:6px}
</style>${Object.keys(I).map(k => `<div><div class="row"><span class="t">${svg(k, 22)}</span><span class="t on">${svg(k, 22)}</span><span class="g">${svg(k, 18)}</span></div>${svg(k, 48)}<span>${k}</span></div>`).join("")}`);
console.log(Object.keys(I).length, "icons");
