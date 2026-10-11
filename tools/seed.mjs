/* Made-up games for the test kit (no real data: the repo is public). Pictures are files already in the repo, so nothing
   has to come from the network. seed(APP_V) = 14 games; seed(APP_V, 60) for the perf walk. */
export const TITLES = ["Starfall Odyssey", "Crimson Tactics", "Neon Drift", "Echoes of the Deep", "Moonlit Farm", "Iron Saga II", "Pixel Quest", "The Long Night",
  "Skyward Blades", "Ocean Tales", "Shadow Protocol", "Ember Knight", "Garden Story", "Rift Runner"];
const ART = ["fefw-key-art.jpg", "logo-512.png", "FEFT_cast.webp"];
const day = 864e5, iso = d => new Date(Date.now() + d * day).toISOString().slice(0, 10);

export function seed(APP_V, n = TITLES.length) {
  const out = Math.max(9, Math.round(n * 0.65));
  const games = Array.from({ length: n }, (_, i) => ({
    id: "g" + i, title: TITLES[i % TITLES.length] + (i >= TITLES.length ? " " + (1 + Math.floor(i / TITLES.length)) : ""),
    desc: "A long description of the game. ".repeat(6), img: ART[i % 3], key: i % 4 === 0 ? ART[0] : "",
    colour: ["#4DA3FF", "#7C5CFF", "#3FD9A4", "#F0A63C", "#FF5470", "#5AE0FF"][i % 6],
    date: iso(i < out ? -400 + i * (360 / out) : (i - out + 1) * 9), hours: 12 + (i % 14) * 4,
    status: i < out ? "out" : "upcoming", state: i === 1 || i === 4 ? "playing" : "backlog", hype: 4 + (i % 6), ord: i + 1,
    platforms: ["PS5, PC", "Switch", "PS5", "PC", "Switch, PS5, PC", "PS4 & PS5"][i % 6], genres: ["RPG", "Action", "Strategy", "Adventure"].slice(0, 1 + (i % 3)),
    added: iso(-200 + i * 3) }));
  const pl = [0, 2, 3, 5, 6].concat(n > 20 ? [10, 11, 12, 15, 16, 17] : []);
  const played = pl.map((g, k) => ({ id: "g" + g, real: [9, 7.5, 6, 8.5, 5, 7, 8, 6.5, 9.5, 4, 7][k], t: iso(-(k + 1) * 30) }));
  return { games, played, duels: [], lastV: String(APP_V), tourFrom: +APP_V, tourRedo: 1, nxAsked: 1, userName: "Sam", fxSeen: 1, fxCal: 1, scoreMig: 1, gdMig: 1, pxOff: true, cbOn: false };
}
