#!/usr/bin/env node
/* The skills book's words (v317, idea M41/M42): what each finished game taught you, written per game. The repo is public and
   must never name the owner's games, so each game's entry is sealed with its own title: the id is a hash of the title and the
   words are AES-GCM encrypted with a key made from the title (PBKDF2). The phone, which knows its games' titles, finds and
   opens the ones it has; the repo only holds ids and noise.
     node tools/book-seal.mjs <data.json> [key ...]     prints the BOOK_SEALED line for index.html (only the keys named, else all)
     node tools/book-seal.mjs --open <data.json>        opens index.html's BOOK_SEALED with the titles in data.json (a check)
   data.json (kept out of the repo, *.json is git-ignored): { "games": [{ "key", "title", "skills": [{ ic, n, lv, gain, txt, mind, life, tr, story }] }] }
   Must stay the same as bookNorm / bookOpen in index.html. */
import fs from "fs"; import { webcrypto as C } from "crypto";
const enc = s => new TextEncoder().encode(s), b64 = b => Buffer.from(b).toString("base64");
const norm = t => String(t || "").toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "");
const hex = b => [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, "0")).join("");
const idOf = async n => hex(await C.subtle.digest("SHA-256", enc("pn-book:" + n))).slice(0, 16);
async function keyOf(n) {
  const base = await C.subtle.importKey("raw", enc(n), "PBKDF2", false, ["deriveKey"]);
  return C.subtle.deriveKey({ name: "PBKDF2", salt: enc("play-next-book-v1"), iterations: 60000, hash: "SHA-256" }, base, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}
const args = process.argv.slice(2), open = args[0] === "--open", file = open ? args[1] : args[0];
if (!file) { console.error("usage: node tools/book-seal.mjs <data.json> [key ...] | --open <data.json>"); process.exit(1); }
const data = JSON.parse(fs.readFileSync(file, "utf8")), want = open ? [] : args.slice(1);
if (open) {
  const m = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8").match(/const BOOK_SEALED = (\{[^\n]*\});/);
  if (!m) { console.error("no BOOK_SEALED in index.html"); process.exit(1); }
  const sealed = JSON.parse(m[1]); let ok = 0;
  for (const g of data.games) { const n = norm(g.title), id = await idOf(n); if (!sealed[id]) continue;
    const raw = Buffer.from(sealed[id], "base64"), out = await C.subtle.decrypt({ name: "AES-GCM", iv: raw.subarray(0, 12) }, await keyOf(n), raw.subarray(12));
    const e = JSON.parse(new TextDecoder().decode(out)); console.log(`${g.key}: opens, ${e.skills.length} skills`); ok++; }
  console.log(`${ok} of ${Object.keys(sealed).length} entries opened`); process.exit(ok ? 0 : 1);
}
const out = {};
for (const g of data.games) {
  if (want.length && !want.includes(g.key)) continue;
  const n = norm(g.title), iv = C.getRandomValues(new Uint8Array(12));
  const body = { skills: g.skills.map(({ ic, n, lv, gain, txt, mind, life, tr, story }) => ({ ic, n, lv, gain, txt, mind, life, tr, story })) };
  const ct = await C.subtle.encrypt({ name: "AES-GCM", iv }, await keyOf(n), enc(JSON.stringify(body)));
  out[await idOf(n)] = b64(Buffer.concat([Buffer.from(iv), Buffer.from(ct)]));
}
console.log(`const BOOK_SEALED = ${JSON.stringify(out)};`);
