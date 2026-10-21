#!/usr/bin/env node
/* The skills book's words (v318, ideas M41/M42): what each finished game taught you, written per game. The repo is public and
   must never name the owner's games, so each entry is sealed to one phone's public key: every phone makes an ECDH P-256 key
   pair (bookKey in index.html) and sends the public half to the helper (POST /book/key); book-keys.yml prints the keys.
   An entry = base64 of [a one-off public key (65 bytes) | iv (12) | AES-GCM of {n: the title as bookNorm makes it, skills}],
   the AES key = HKDF-SHA-256 over the ECDH secret (salt = the one-off key, info "play-next-book-v2"). Its id is random, so
   the repo holds neither titles nor which games have pages; only the phone with the private key can open it.
     node tools/book-seal.mjs <data.json> <key.json> [game key ...]   prints the new entries as JSON (merge them into BOOK_SEALED)
     node tools/book-seal.mjs --open <entries.json> <private.json>    opens entries with a private key (a check, tests only)
   data.json (kept out of the repo, *.json is git-ignored): { "games": [{ "key", "title", "skills": [{ ic, n, lv, gain, txt, mind, life, tr, story }] }] }
   key.json: one phone's public key as book-keys.yml prints it ({ kty, crv, x, y }, or the whole { uid, pub } row).
   Must stay the same as bookNorm / bookOpen in index.html. */
import fs from "fs"; import { webcrypto as C } from "crypto";
const enc = s => new TextEncoder().encode(s), b64 = b => Buffer.from(b).toString("base64");
const norm = t => String(t || "").toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "");
const EC = { name: "ECDH", namedCurve: "P-256" }, INFO = enc("play-next-book-v2");
const aesKey = async (priv, pub, salt, use) => {
  const bits = await C.subtle.deriveBits({ name: "ECDH", public: pub }, priv, 256);
  const hk = await C.subtle.importKey("raw", bits, "HKDF", false, ["deriveKey"]);
  return C.subtle.deriveKey({ name: "HKDF", hash: "SHA-256", salt, info: INFO }, hk, { name: "AES-GCM", length: 256 }, false, [use]);
};
export async function seal(body, pubJwk) {
  const pub = await C.subtle.importKey("jwk", { kty: "EC", crv: "P-256", x: pubJwk.x, y: pubJwk.y }, EC, false, []);
  const eph = await C.subtle.generateKey(EC, true, ["deriveBits"]), ephRaw = new Uint8Array(await C.subtle.exportKey("raw", eph.publicKey));
  const iv = C.getRandomValues(new Uint8Array(12)), ct = await C.subtle.encrypt({ name: "AES-GCM", iv }, await aesKey(eph.privateKey, pub, ephRaw, "encrypt"), enc(JSON.stringify(body)));
  return b64(Buffer.concat([Buffer.from(ephRaw), Buffer.from(iv), Buffer.from(ct)]));
}
export async function open(sealed, privJwk) {
  const raw = Buffer.from(sealed, "base64"), priv = await C.subtle.importKey("jwk", privJwk, EC, false, ["deriveBits"]);
  const eph = await C.subtle.importKey("raw", raw.subarray(0, 65), EC, false, []);
  return JSON.parse(new TextDecoder().decode(await C.subtle.decrypt({ name: "AES-GCM", iv: raw.subarray(65, 77) }, await aesKey(priv, eph, raw.subarray(0, 65), "decrypt"), raw.subarray(77))));
}
export const bodyOf = g => ({ n: norm(g.title), skills: g.skills.map(({ ic, n, lv, gain, txt, mind, life, tr, story }) => ({ ic, n, lv, gain, txt, mind, life, tr, story })) });
export const newId = () => Buffer.from(C.getRandomValues(new Uint8Array(8))).toString("hex");

if (/book-seal\.mjs$/.test(process.argv[1] || "")) {   /* run, not imported (a scenario imports seal/open) */
  const args = process.argv.slice(2);
  if (args[0] === "--open") {
    const entries = JSON.parse(fs.readFileSync(args[1], "utf8")), priv = JSON.parse(fs.readFileSync(args[2], "utf8")); let ok = 0;
    for (const [id, s] of Object.entries(entries)) try { const e = await open(s, priv); console.log(`${id}: opens, ${e.skills.length} skills`); ok++; } catch (e) { console.log(`${id}: not for this key`); }
    console.log(`${ok} of ${Object.keys(entries).length} entries opened`); process.exit(ok ? 0 : 1);
  }
  if (args.length < 2) { console.error("usage: node tools/book-seal.mjs <data.json> <key.json> [game key ...] | --open <entries.json> <private.json>"); process.exit(1); }
  const data = JSON.parse(fs.readFileSync(args[0], "utf8")), k = JSON.parse(fs.readFileSync(args[1], "utf8")), want = args.slice(2);
  const pub = k.pub || k; if (pub.crv !== "P-256" || !pub.x || !pub.y) { console.error("key.json isn't a P-256 public key"); process.exit(1); }
  const out = {};
  for (const g of data.games) { if (want.length && !want.includes(g.key)) continue; out[newId()] = await seal(bodyOf(g), pub); }
  console.log(JSON.stringify(out));
}
