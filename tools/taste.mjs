#!/usr/bin/env node
/* The owner's taste log (.claude/skills/play-next-design/taste.md, Oct 2026, the owner: a design skill that keeps learning).
   Every time the owner picks a look, calls something ugly or sets a look rule, it goes here with their words, the rule it
   means, the check that holds it and, when there is one, a screenshot (made-up seed games only: the repo is public).
     node tools/taste.mjs list [--screen=Queue]           every taste call (or one screen's)
     node tools/taste.mjs add --v=291 --screen=Queue --kind=pick|hate|rule --said="their words" --rule="what to do"
                              [--check="command or detector"] [--shot=file.png]
     node tools/taste.mjs gaps                            rules no check holds yet: the next things to teach the design eye
     node tools/taste.mjs for <file or selector text>     the taste calls about the screens a change touches
     node tools/taste.mjs pair <name> --before=a.png --after=b.png [--ref=link] --brief="layout / style / our colours / left out"
                              a before/after (or reference → ours) pair in references/<name>/
   design-check.mjs prints `for` on what the branch changes, so a UI change sees the owner's past calls about that screen. */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const DIR = path.join(ROOT, ".claude/skills/play-next-design"), LOG = path.join(DIR, "taste.md");
const main = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
const [cmd, ...rest] = main ? process.argv.slice(2) : [];
const opt = k => { const a = rest.find(x => x.startsWith(`--${k}=`)); return a ? a.slice(k.length + 3) : null; };

/* which code belongs to which screen: a change touching these words is about that screen */
export const SCREEN_WORDS = {
  "whole app": [/./],
  Home: [/\bdx\w*/, /#p-home\b/, /renderHome/, /\bhome\b/i],
  Queue: [/#p-queue\b/, /\brow(art|in|meta)?\b/, /\blf[A-Z]\w*/, /renderQueue/, /\bqueue\b/i, /\bdeck\b/i],
  Library: [/#p-lib\b/, /\blib\w*/, /\blibrary\b/i],
  Played: [/#p-played\b/, /\bpnb-?\w*/, /\bplayed\b/i, /\bbadge/i],
  "game page": [/#full\b/, /\bgd-?\w*/, /openFull/, /\bhub\b/, /\bgame page\b/i],
  Settings: [/#s-\w+/, /\bsetgroup\b/, /\bgrp\b/, /openSettings/, /\bsettings\b/i],
  Nexi: [/\bnx\w*/, /\bpn(?!b)\w*/, /\bnexi\b/i, /\brail\b/i],
  "Add game page": [/#p-add\b/, /\badd\b/i],
  "header cards": [/\bshx-?\w*/, /\btabbar\b/],
};

/* taste.md: "### T<n> · v<N> · <screen> · <kind>" then "- said:", "- rule:", "- check:", "- shot:" lines */
export function read() {
  if (!fs.existsSync(LOG)) return [];
  const out = [];
  for (const b of fs.readFileSync(LOG, "utf8").replace(/\r/g, "").split(/^### /m).slice(1)) {
    const h = b.match(/^(T\d+) · (v[\w/–-]+) · ([^·\n]+) · (\w[\w+]*)/); if (!h) continue;
    const f = k => (b.match(new RegExp(`^- ${k}: (.*)$`, "m")) || [])[1] || "";
    out.push({ id: h[1], v: h[2], screen: h[3].trim(), kind: h[4], said: f("said"), rule: f("rule"), check: f("check"), shot: f("shot") });
  }
  return out;
}
const show = t => `${t.id} ${t.v} ${t.screen} (${t.kind}): ${t.rule}\n     said: ${t.said}\n     check: ${t.check || "none"}${t.shot ? `\n     shot: ${t.shot}` : ""}`;

if (cmd === "list") {
  const s = opt("screen"); const all = read().filter(t => !s || t.screen.toLowerCase().includes(s.toLowerCase()));
  console.log(all.map(show).join("\n") || "no taste calls yet");
} else if (cmd === "gaps") {
  const g = read().filter(t => !t.check || /^none/i.test(t.check));
  console.log(`${g.length} rule(s) no check holds yet (teach the design eye, tools/eye.mjs, or a scenario, then set check:):\n` + g.map(t => `  ${t.id} ${t.screen}: ${t.rule}`).join("\n"));
} else if (cmd === "for") {
  const text = rest.filter(x => !x.startsWith("--")).map(x => fs.existsSync(x) ? fs.readFileSync(x, "utf8") : x).join("\n");
  console.log(forText(text).map(show).join("\n") || "no taste calls about these screens");
} else if (cmd === "add") {
  const need = ["v", "screen", "kind", "said", "rule"].filter(k => !opt(k));
  if (need.length) { console.error(`missing --${need.join(", --")}`); process.exit(1); }
  const all = read(), id = "T" + (all.reduce((m, t) => Math.max(m, +t.id.slice(1)), 0) + 1);
  let shot = "";
  if (opt("shot")) { fs.mkdirSync(path.join(DIR, "taste"), { recursive: true }); shot = `taste/${id}${path.extname(opt("shot")).toLowerCase()}`; fs.copyFileSync(opt("shot"), path.join(DIR, shot)); }
  const v = opt("v").startsWith("v") ? opt("v") : "v" + opt("v");
  fs.appendFileSync(LOG, `\n### ${id} · ${v} · ${opt("screen")} · ${opt("kind")}\n- said: ${opt("said")}\n- rule: ${opt("rule")}\n- check: ${opt("check") || "none"}\n${shot ? `- shot: ${shot}\n` : ""}`);
  console.log(`added ${id}${shot ? " with " + shot : ""}`);
} else if (cmd === "pair") {
  const name = rest.find(x => !x.startsWith("--"));
  if (!name || !opt("after") || !opt("brief")) { console.error("usage: taste.mjs pair <name> --before=a.png --after=b.png [--ref=link] --brief=\"…\""); process.exit(1); }
  const d = path.join(DIR, "references", name); fs.mkdirSync(d, { recursive: true });
  /* keep pictures small (a phone-width JPEG is ~60 KB): `convert x.png -resize 412x -quality 78 x.jpg` first */
  const put = (src, as) => { const f = as + path.extname(src).toLowerCase(); fs.copyFileSync(src, path.join(d, f)); return f; };
  const b = opt("before") ? put(opt("before"), "before") : "", a = put(opt("after"), "after");
  fs.writeFileSync(path.join(d, "brief.md"), `# ${name}\n\n${opt("ref") ? `Reference: ${opt("ref")}\n\n` : ""}${b ? `![before](${b}) → ![after](${a})` : `![after](${a})`}\n\n${opt("brief")}\n`);
  console.log(`references/${name}/ written`);
} else if (main) {
  console.log(fs.readFileSync(fileURLToPath(import.meta.url), "utf8").split("*/")[0]);
}

/* the taste calls whose screen the text touches (whole-app rules always come along) */
export function forText(text) {
  const hit = new Set(Object.entries(SCREEN_WORDS).filter(([s, res]) => s !== "whole app" && res.some(r => r.test(text))).map(([s]) => s.toLowerCase()));
  return read().filter(t => hit.has(t.screen.toLowerCase()) || [...hit].some(s => t.screen.toLowerCase().includes(s)));
}
