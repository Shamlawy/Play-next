/* v318: the skills book's lock. The phone makes its key pair and sends the public half to the helper (fake here); an entry
   sealed to that key by tools/book-seal.mjs opens on the phone and its game gets pages; one sealed to another phone's key
   doesn't; the private key sits only in S (the encrypted backup), the words never do; BOOK_SEALED in the repo is empty or
   holds only random ids (no title hashes).
     node tools/kit.mjs run tools/scenarios/skills-book-lock.mjs --widths 412 */
import { webcrypto as C } from "node:crypto";
import { seal, bodyOf, newId } from "../book-seal.mjs";
export default async ({ p, w, seed, fail }) => {
  const v = await p.page.evaluate(() => APP_V);
  await p.page.evaluate(d => localStorage.setItem("gq:data", d), JSON.stringify(seed(v, 30)));
  await p.page.reload(); await p.page.waitForTimeout(2500);
  const sent = [];
  p.page.on("request", r => { if (/\/book\/key$/.test(r.url())) sent.push(r.postData()); });
  /* the key is made and sent ~9s after opening */
  await p.page.waitForTimeout(10500);
  const k = await p.page.evaluate(() => S.bookKey ? { x: S.bookKey.x, y: S.bookKey.y, d: !!S.bookKey.d } : null);
  if (!k || !k.d) return fail("no key pair in S after opening");
  if (!sent.length) fail("the public key was never sent to the helper");
  else { const b = JSON.parse(sent[0]); if (b.pub.d || !b.uid || b.pub.x !== k.x) fail("the helper got the wrong thing: " + sent[0].slice(0, 120)); }
  if (await p.page.evaluate(() => localStorage.getItem("gq:bookpub")) !== k.x) fail("the sent key wasn't remembered (it would be sent on every open)");
  /* one entry for the most recently finished seed game sealed to this phone, one to someone else's key */
  const title = await p.page.evaluate(() => { const x = [...S.played].sort((a, b) => String(a.t).localeCompare(String(b.t))).pop(); return S.games.find(g => g.id === x.id).title; });
  const sk = { ic: "time", n: "Prioritising", lv: 3, gain: "Better at picking what matters", txt: "A made-up moment.", mind: "Made-up mind line.", life: "Made-up life line.", tr: "Made-up try." };
  const other = await C.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]), oj = await C.subtle.exportKey("jwk", other.publicKey);
  const mine = newId(), theirs = newId();
  const E = { [mine]: await seal(bodyOf({ title, skills: [sk] }), k), [theirs]: await seal(bodyOf({ title: "Made Up Other", skills: [sk] }), oj) };
  const got = await p.page.evaluate(async E => { Object.assign(BOOK_SEALED, E); localStorage.removeItem("gq:book"); bookStore = null;
    const B = await bookGames(); return { titles: B.map(b => b.g.title), skills: B.map(b => b.skills.length), keys: Object.keys(bookLocal()) }; }, E);
  if (got.titles.length !== 1 || got.titles[0] !== title) fail(`the sealed game didn't get its pages: ${JSON.stringify(got)}`);
  if (got.keys.includes("madeupother")) fail("an entry sealed to another phone opened");
  if (await p.page.evaluate(() => JSON.stringify(S).includes("Made-up mind line"))) fail("book words leaked into S");
  /* the repo's own entries: random 16-hex ids, never the old title-hash scheme's PBKDF2 blobs */
  const ids = await p.page.evaluate(() => Object.keys(BOOK_SEALED));
  ids.forEach(id => { if (!/^[0-9a-f]{16}$/.test(id)) fail("odd BOOK_SEALED id " + id); });
  console.log(`  ${w}: key made and sent, ${got.titles.length} game opened from 2 entries (one for another phone)`);
};
