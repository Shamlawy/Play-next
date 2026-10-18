/* Made-up games for the test kit (no real data: the repo is public). Pictures are files already in the repo, so nothing
   has to come from the network. seed(APP_V) = 14 games; seed(APP_V, 60) for the perf walk.
   Since M3 the games also carry what the helper fills in on a real phone (Steam id and tags, Steam reviews and a critic score,
   hours to beat, Steam news), the same made-up answers tools/fake-helper.mjs gives, with every sync marked as just done;
   seed(APP_V, n, { bare: true }) leaves them out (the empty states). */
export const TITLES = ["Starfall Odyssey", "Crimson Tactics", "Neon Drift", "Echoes of the Deep", "Moonlit Farm", "Iron Saga II", "Pixel Quest", "The Long Night",
  "Skyward Blades", "Ocean Tales", "Shadow Protocol", "Ember Knight", "Garden Story", "Rift Runner"];
import { LOGOS } from "./standin-logos.mjs";
import { steamId, tagsFor, reviewFor, hoursFor, criticFor, newsFor } from "./fake-helper.mjs";
const ART = ["fefw-key-art.jpg", "logo-512.png", "FEFT_cast.webp"];
const day = 864e5, iso = d => new Date(Date.now() + d * day).toISOString().slice(0, 10);

export function seed(APP_V, n = TITLES.length, { bare = false } = {}) {
  const out = Math.max(9, Math.round(n * 0.65));
  const games = Array.from({ length: n }, (_, i) => ({
    id: "g" + i, title: TITLES[i % TITLES.length] + (i >= TITLES.length ? " " + (1 + Math.floor(i / TITLES.length)) : ""),
    desc: "A long description of the game. ".repeat(6), img: ART[i % 3], key: i % 4 === 0 ? ART[0] : "",
    /* v290: queue row logos (made-up, tools/standin-logos.mjs), so every sweep checks them too */
    art: i >= 1 && i <= LOGOS.length && i < 9 ? LOGOS[i - 1].url : "",
    colour: ["#4DA3FF", "#7C5CFF", "#3FD9A4", "#F0A63C", "#FF5470", "#5AE0FF"][i % 6],
    date: iso(i < out ? -400 + i * (360 / out) : (i - out + 1) * 9), hours: 12 + (i % 14) * 4,
    status: i < out ? "out" : "upcoming", state: i >= out ? "upcoming" : i === 1 || i === 4 ? "playing" : "backlog", hype: 4 + (i % 6), ord: i + 1,
    platforms: ["PS5, PC", "Switch", "PS5", "PC", "Switch, PS5, PC", "PS4 & PS5"][i % 6], genres: ["RPG", "Action", "Strategy", "Adventure"].slice(0, 1 + (i % 3)),
    added: iso(-200 + i * 3) }));
  if (!bare) games.forEach((g, i) => {
    const now = Date.now(), pc = /PC/.test(g.platforms);
    /* a phone that has been online: Steam knows the PC games, critics and players scored most, HowLongToBeat timed them */
    if (pc) Object.assign(g, { stId: steamId(g.id), stTags: tagsFor(g.id), stAt: now, rv: { st: (({ d, p, n }) => ({ d, p, n }))(reviewFor(g.id)), at: now } });
    if (g.status === "out" && i % 5 !== 3) { g.rv = { ...(g.rv || { at: now }), mc: criticFor(g.id), mcP: "PlayStation 5", us: 3.4 + (i % 5) * .3, un: 180 + i * 41 }; g.critic = criticFor(g.id); g.criticFrom = "RAWG"; }
    const h = hoursFor(g.id); g.hoursX = h.x; g.hours100 = h.c; g.hbAt = now;
    /* news for the two games being played: the newest post is the one already seen, the older two came in this week */
    if (pc || g.state === "playing") { const nw = newsFor(g.id); g.nwSeen = nw[0].d;
      if (g.state === "playing") g.nw = nw.map(({ id, t, u, d, k, x }) => ({ id, t, u, d, k, x })); }
  });
  const pl = [0, 2, 3, 5, 6].concat(n > 20 ? [10, 11, 12, 15, 16, 17] : []);
  const played = pl.map((g, k) => ({ id: "g" + g, real: [9, 7.5, 6, 8.5, 5, 7, 8, 6.5, 9.5, 4, 7][k], t: iso(-(k + 1) * 30) }));
  return { games, played, duels: [], ...(bare ? {} : { stAt: Date.now(), hbAt: Date.now() }), lastV: String(APP_V), tourFrom: +APP_V, tourRedo: 1, nxAsked: 1, userName: "Sam", fxSeen: 1, fxCal: 1, scoreMig: 1, gdMig: 1, pxOff: true, cbOn: false };
}
