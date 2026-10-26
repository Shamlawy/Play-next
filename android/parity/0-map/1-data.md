# Phase 0 map, area 1: reading the owner's games from the cloud backup

Mapped at web APP_V 327 (index.html, 28,868 lines), helper HELPER_V 24 (worker/helper.js, 1,682 lines).
Read-only: no repo file was changed. A working test vector made with the web app's own functions is at
`/tmp/claude-0/-home-user-Play-next/66feff30-fd9e-5cc8-8f0a-4e7630c0e8df/scratchpad/map/1-data-vector.txt` (the script that made it is `vec.mjs` in the same folder).

---

## 1. Cloud backup

### 1.1 Where it lives in index.html
The "v211: cloud backup" script runs from **index.html:18997 to about 19255**. Its key pieces:

| What | Line | Notes |
|---|---|---|
| `CB_KEY = "gq:cloud"`, `CB_GAP = 10*60e3`, `CB_ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"` | 19003 | 32 symbols, with no 0/1/I/O |
| `cbNorm(c)` | 19008 | normalises a code |
| `cbFmt(c)` | 19010 | `"PN-" + 4-4-4-4-4` |
| `cbEnsure()` (makes a code) | 19011 | |
| `cbHex`, `cbSha` | 19015–19016 | |
| `cbKeys(code)` (id, token, key) | 19018 | |
| `cbPipe` (gzip stream helper) | 19024 | |
| `cbSeal(text,key)` | 19026 | |
| `cbOpen(buf,key)` | 19034 | |
| `cbText()` (what is sent up) | 19042 | |
| `cbSealOff` (does the sealing in a Worker) | 19049 | the same functions, stringified |
| `cbFetch(op,k,q,opt)` (helper client) | 19067 | |
| `cbRun()` (upload) | 19087 | sends `&n=<games>&v=<APP_V>[&keep=1]` |
| `cbRestoreSheet()` (restore UI) | 19202 | list, then get, then open, then `readBackup` + `applyBackup` |
| `readBackup(raw)` / `applyBackup(d)` | 10225 / 10247 | shared with the file backup; `BK_SECRET` at 10200 |
| `HELPER = "https://playnext-helper.hussamnabil48.workers.dev"` | 13334 | (`SGDB_PROXY` at 4652 is the same URL) |

### 1.2 The restore code
- **How it's made** (`cbEnsure`, 19011): take 20 random bytes, map each to `CB_ALPHA[b & 31]` (256 is a multiple of 32, so there's no bias). That gives 20 symbols × 5 bits = 100 random bits, shown as `PN-XXXX-XXXX-XXXX-XXXX-XXXX`.
- **How it's normalised** (`cbNorm`, 19008). Kotlin must match this exactly:
  ```
  t = upper(input).removeAll(not [A-Z0-9])
  if len(t) == 22 and t.startsWith("PN"): t = t.drop(2)
  valid iff len(t) == 20 and every char in CB_ALPHA   (otherwise "")
  ```
  A typed code with or without `PN-`, with spaces, dashes or lower case, all works. The **normalised 20-character code (no PN, no dashes)** is what every hash uses.
- The web app keeps it in `localStorage["gq:cloud"].code` in its formatted form. The Android app keeps it in the Keystore (per the plan).

### 1.3 Key derivation (`cbKeys`, 19018). There is no KDF and no salt: the code is 100 random bits
```
c   = cbNorm(code)                                  // 20 chars
id  = lowerHex(SHA256(utf8("pn-vault-id:"  + c)))[0 until 24]     // 24 hex chars
tok = lowerHex(SHA256(utf8("pn-vault-tok:" + c)))                 // 64 hex chars
key = SHA256(utf8("pn-vault-key:" + c))                           // 32 raw bytes = AES-256 key
```
The helper never sees `key`. It stores `sha256hex(tok)` as `h` in the KV metadata and compares it (`sameKey`) on every call.

### 1.4 Sealed format (`cbSeal` 19026 / `cbOpen` 19034)
```
byte 0      = 1            (format version; anything else → "a newer kind of backup — update the app first")
byte 1      = z            (1 = plaintext was gzipped, 0 = not; the web app always gzips where CompressionStream exists)
bytes 2..13 = IV (12 random bytes)
bytes 14..  = AES-GCM ciphertext with the 16-byte tag appended (WebCrypto layout)
AAD         = [1, z]       (two bytes: version and z)
```
**Kotlin open:**
```kotlin
require(b[0] == 1.toByte())
val z = b[1].toInt()
val c = Cipher.getInstance("AES/GCM/NoPadding")
c.init(DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b, 2, 12))
c.updateAAD(byteArrayOf(1, b[1]))
var pt = c.doFinal(b, 14, b.size - 14)
if (z != 0) pt = GZIPInputStream(pt.inputStream()).readBytes()   // standard RFC 1952 gzip, one member
val text = pt.toString(Charsets.UTF_8)
```
**Kotlin seal** (for the CI round-trip test and after switch day): gzip, then a random 12-byte IV, then AES-GCM with AAD `[1,1]`, then the output `[1,1,iv,ct+tag]`.

### 1.5 The plaintext (`cbText`, 19042)
```
JSON.stringify({ rk: rawgKey || "" }) + "\n" + localStorage["gq:data"]   // falls back to JSON.stringify(S)
```
- **Line 1** is the header `{"rk":"<RAWG API key or empty>"}`. This is the only place the RAWG key travels. JSON escapes newlines, so the first `\n` is always the separator (the restore uses `text.indexOf("\n")`).
- **Line 2** is the saved state `S`, **byte for byte as the phone stores it** (the raw `gq:data` string, `JSON.stringify(S)`, no newlines). It still holds keys that the file backup strips: `syncTok/syncPub/syncSig/syncErr/syncOn` (friend sync), `bookKey` (private ECDH key), `nudgeId`, `calId`, `steamMe`. Treat the whole blob as secret.
- Size: a few MB is normal (uploaded pictures are `data:` URIs inside games). The helper caps a sealed copy at **20 MB** (`VAULT_MAX`, helper.js:40).

### 1.6 Helper vault routes (helper.js `vault()` 495–543, dispatched at 148)
Every call is checked like this:
- **Origin**: `ALLOW = ["https://shamlawy.github.io", "http://localhost:8765"]` (helper.js:41). If `!ALLOW.includes(req.headers.get("Origin") || "")` the answer is `403 {"error":"origin"}`. The native client **must send `Origin: https://shamlawy.github.io`** (exact string, no trailing slash). OkHttp lets you set it.
- `id` query param must match `^[a-f0-9]{24}$`; `Authorization: Bearer <tok>` with tok `^[a-f0-9]{64}$` (lower-case hex). Otherwise the answer is `400 {"error":"bad code"}`.
- If a latest copy exists and `sha256hex(tok) != meta.h` the answer is `403 {"error":"wrong code"}`.
- No KV binding gives `500 {"error":"No KV binding named NUDGE"}`. Any other thrown error gives `500 {"error": msg}`.
- The web app sends `cache: "no-store"`. Copy that: no HTTP caching.

| Route | Method | Request | Response | R/W |
|---|---|---|---|---|
| `/vault/list?id=<id>` | any (web uses GET) | Bearer tok | `200 {"list":[{k,t,n,v,sz}, …]}` sorted newest first; `{"list":[]}` when no latest copy exists (and then the token is **not** checked) | read |
| `/vault/get?id=<id>&k=<k>` | any (web GET) | Bearer tok; `k` = `latest` (default) \| `d0`..`d6` \| `k` | `200 application/octet-stream` raw sealed bytes, `Cache-Control: no-store`; `400 {"error":"bad copy"}`; `404 {"error":"no backup with that code"}`; `403 wrong code` | read |
| `/vault/put?id=<id>&n=<games>&v=<ver>[&keep=1]` | POST | Bearer tok, body = sealed bytes (`application/octet-stream`) | `200 {ok:true,t}`; `400 empty`; `413 too big`; `429 {"error":"too soon",t}` (under 20 s since the last put) | **WRITE** |
| `/vault/del?id=<id>` | POST | Bearer tok | `200 {ok:true}` deletes latest, `:k`, `:d0..d6` | **WRITE** |
| other `/vault/*` | | | `404 {"error":"not found"}`. The web app's `cbFetch` treats a 404 with "not found" as `helper-old` | |

List entry fields come from `pub(m)` (helper.js:504):
- `k`: `"latest"`, `"d0".."d6"` (a snapshot of the last copy of a UTC weekday, made when the first put of a new UTC day replaces yesterday's) or `"k"` (the copy before a put flagged `keep=1`).
- `t`: server ms epoch of that copy.
- `n`: game count the phone sent (clamped 0..1e5).
- `v`: version string the phone sent, **cut to 8 chars**.
- `sz`: sealed byte size.

KV layout: `vault:<id>` (latest), `vault:<id>:d<0-6>`, `vault:<id>:k`. All are kept 400 days (`VAULT_TTL`) after their write.

**Kotlin `cbFetch` equivalent:**
- `get` with 2xx gives the bytes.
- Anything else: parse JSON (or `{}`).
  - `404` and `/not found/i` means the helper is too old.
  - `!ok || error` throws with `status`.
- UI strings the web app uses on restore:
  - 403 on list: "That code doesn't match a backup."
  - Empty list: "No backup found for that code."
  - GCM failure: "That copy didn't open with this code."

### 1.7 The practice copy's read flow (pseudocode)
```
code   = keystore.read() ; c = cbNorm(code) ; if c == "" → ask again
(id,tok,key) = cbKeys(c)
list   = GET  HELPER/vault/list?id=id     headers {Origin, Authorization: Bearer tok}
latest = list.first { it.k == "latest" } ?: → "No backup found"
bytes  = GET  HELPER/vault/get?id=id&k=latest
text   = cbOpen(bytes, key)
cut    = text.indexOf('\n'); head = JSON(text[0,cut]); body = text[cut+1..]
rb     = readBackup(body)        // see 1.8; on failure show rb.msg
rawg   = head.rk (if non-empty)  // keep in memory/keystore, never log
state  = applyBackupReadOnly(rb.data)   // see 1.8; keep the RAW JSON too
show "Your games as of <latest.t, local 12-hour time>" ; warn if now - latest.t > 24 h
```
Don't trust `latest.n` as the game count: the restore counts `games.filter(id && title)`.

### 1.8 `readBackup` (10225) and `applyBackup` (10247): what the restore does to the data
`readBackup(raw)`:
- trim the input
- fail on empty
- fail if it starts with `GAME QUEUE`
- fail if it doesn't start with `{` or doesn't end with `}`
- fail on a `JSON.parse` error
- fail unless `games` is an array
- fail unless at least one game has `id && title`

If all of that passes, it returns `{ok, data, msg:"Restored N games (M skipped)[ and a RAWG key]"}`.

The restore then sets `rb.data.rawgKey = head.rk` if `rk` is non-empty, and calls `applyBackup(d)`:
```
key = d.rawgKey
d = copy(d) minus BK_SECRET = ["rawgKey","syncTok","syncPub","syncSig","syncErr","syncOn"]   // index.html:10200
S = Object.assign(S, d)                    // merge onto current S (top-level keys replaced, unknown ones kept)
S.games = S.games.filter(g => g && g.id && g.title)
S.duels ||= []; S.played ||= []
dedupePlayed()                             // 3510: one played record per id (max real, max runs, again OR, earliest t; drops records without id)
delete S.v; delete S.rawgKey
if key && no local RAWG key: save key to localStorage "gq:rawgkey"
```
`extend("applyBackup")` at 13331 only resets memo caches (`sigIds`, `rankMemo`, `deckMemo`). That's fine to ignore.

---

## 2. Saved state `S`

### 2.1 Storage keys
- `KEY = "gq:data"`: all of `S` as JSON (index.html:3493). `KKEY = "gq:rawgkey"`, `GKEY = "gq:sgdbkey"` and `BKEY = "gq:boost"` are separate keys. **Only `gq:data` plus the RAWG key go into the cloud backup.** The SGDB user key and the boost URL do not.
- `save()` (4324) is deferred by about 1.2 s plus idle time; `saveNow()` (4335) writes `JSON.stringify(S)`.
- `load()` (4252) reads it; the initial value is `S = { games: [], duels: [], played: [] }` (3790).

### 2.2 Bookkeeping kept out of S (and so NOT in the backup)
`gq:cloud` (backup status: `code,on,saved,at,hash,n,kb,err,errAt`), `gq:rawgkey`, `gq:sgdbkey`, `gq:boost`, `gq:uid` (random phone id used by /usage and /book/key), `gq:taps` + `gq:useSent` (usage counts), `gq:book` (opened skills-book entries + `_k`, `_tried`), `gq:bookpub` (the public key already sent), `gq:bt` (last buy advice per game), `gq:bugdone`, `gq:calsig` (`CAL_KEY`, plus `CAL_KEY+"n"`), `gq:newsAt` (`NW_KEY`), `gq:spSync` (`SP_SYNC`, last Steam sync), `gq:colfail`, `gq:dwDay`, `gq:lite`, `gq:logofit`, `gq:ost` (music lists), `gq:probe` (`PRB_KEY`), `gq:reel`, `gq:seen`, `gq:seenG`, `gq:shotw`, `gq:stest`, `gq:stestlast`, `gq:tea`.

The rule (v211/v212): anything that changes on every open goes here, or else the backup would re-upload. The Android equivalent is a separate DataStore that is never part of the backup JSON.

### 2.3 Collections inside S (the data that matters)
| Key | Shape | Notes |
|---|---|---|
| `games` | `Game[]` | see 2.4; order in the array is not the queue order (`ord` is) |
| `played` | `[{id, real, t, z?, dk?, again?, runs?}]` | `real` = score 0–10 (decimals allowed, e.g. 7.5); `z:1` = a real 0; `dk:1` = "don't remember" (unscored). `scored(p)` = `!p.dk && typeof real=="number" && (real>0 \|\| (real===0 && z===1))` (3504). `t` = finish date `YYYY-MM-DD` (a UTC date from `toISOString`, or a month picked with day 15). `runs` 1–12. Being in this list ⇔ `g.state==="played"` (load enforces it) |
| `duels` | `[{a, b, w, t, e?}]` | `a`/`b` game ids; `w` = winner id or `"draw"`; `t` `YYYY-MM-DD`; `e:1` = edge duel |
| `sessions` | `[{g, s, e}]` | game id, start and end ms; at most 4000 kept |
| `timer` | `{id, from}` \| `null` | running timer (ms start) |
| `lists` | `[{id:"l…", n, e}]` | your own lists (name ≤32, emoji); games point to them via `g.lists` |
| `sig` | `[{t,k,id,n,tg}]` | taste signals (add/remove/drop), at most 400; `n` = a title copy |
| `aiWhy` | `{gameId: …}` | cached AI verdicts |
| `friend` | `{n, …}` | the friend's data from sync |
| `bookKey` | `{kty,crv,x,y,d}` | **private** ECDH P-256 JWK for the skills book (28310). The practice copy uses it to open `BOOK_SEALED` entries and must never make or send one |
| `nxMem`, `nxXP`, `nxStreak`, `nxHat*`, `bub*`, … | Nexi state | |
| `usage` | `{since, v, o}` | screen visits and opens for the weekly usage post |

### 2.4 The game object (fields seen at v327)
Created by Add (`#f-save`, 5141) and rewritten by Edit (`collectEdit` about 6050–6125). Background jobs add more. **Unknown fields must survive untouched.**

Core (from Add/Edit):
| Field | Type | Meaning |
|---|---|---|
| `id` | string | `uid()` = `Math.random().toString(36).slice(2,9)` (7 chars); the seed uses `g0…` |
| `title` | string | required (a game without `id`+`title` is dropped on restore) |
| `desc` | string | your one-line note ("name" in Edit) |
| `about` | string | store description (may hold HTML from RAWG) |
| `added` | `YYYY-MM-DD` | UTC date added |
| `date` | `YYYY-MM-DD` or `""` | release date (only those two forms are written) |
| `status` | `"confirmed"\|"rumoured"\|"tba"` | of the date (`""` date ⇒ `tba`) |
| `state` | `"upcoming"\|"backlog"\|"playing"\|"played"\|"dropped"` | missing ⇒ treat as `"upcoming"` (`STATES` 3496) |
| `hype` | int 1–10 | |
| `ord` | number | queue order |
| `hours` | number | main hours to beat (0 when `comp`) |
| `hoursX`, `hours100` | number | main + extras / completionist |
| `hoursWas` | number | hours saved while `comp` is on |
| `comp` | bool | competitive or endless game (no hours) |
| `platforms` | string | comma list, e.g. `"PS5, PC"` (`platList` splits on `,`) |
| `genres` | string **or array** | normally a comma string; the test seed writes an array; `platList(String(x))` copes with both |
| `colour` | `"#RRGGBB"` | the game's colour; `colourHand` bool (picked by hand), `colourOf` (key of the art it came from) |
| `img` | URL / `data:` URI / repo file name | box cover; `imgX`, `imgY` 0–100 focal point |
| `art` | URL/data/file | logo or character art; `artSide` `"left"\|"right"`, `artSize` 12–100, `artH` 10–95, `artX` −25..60, `artY` −20..60 |
| `hero` | URL | backdrop; `bg` (alternative backdrop), `bgOff` bool, `bgX`, `bgY` 0–100, `bgPush` 0–70, `bgStr` 0–100 or null |
| `key` | URL/data/file | game page figure (transparent PNG); `keySize` 25–130, `keyX`, `keyY` −40..40 |
| `learn`, `tips` | string | notes |
| `critic` | number 0–100 | critic score; `criticFrom` source string |
| `rawgId` | number\|null | `rawgOk` bool (match confirmed) |
| `steamId` | string | a Steam link or id you typed (for prices) |
| `mine` | `{story,music,gameplay,replay,visuals,world,chars,pace: 0–10}` | your 8 area scores (`RATINGS` 3758) |
| `subs` | `{"<area>/<part>": n}` | advanced sub-scores (`SUBRATE`) |
| `why` | string[] | "what got you" tags |

Filled by background jobs (all optional):
- **Prices**: `px` `{st,ps,ns:{id,name,cur,base,now,pct,baseF,nowF,low,lowF,at}}`; `pxSt` (Steam app id, −1 = never look), `pxPs`/`pxPsC` (PS Store id, `"-"` = never), `pxNs`/`pxNsU` (eShop), `pxRw`, and the `pxStNo/pxPsNo/pxNsNo` flags.
- **Steam**: `stId` (Steam app id), `stTags` (Steam tags), `stAt`, `stNo`, `stG` (genres added from Steam).
- **Reviews**: `rv` `{mc, mcP, us, un, st:{d,p,n}, at, ann}`.
- **HowLongToBeat**: `hl` `{id,n,m,x,c,cnt,at}`, `hlKeep`, `hrsSrc` `"hltb"\|"me"`, `hbV`, `hbNo`.
- **News**: `nw` (≤3 `{id,t,u,d,k,x}`), `nwSeen`.
- **Screenshots**: `shots` (≤5 URLs), `shotsSrc`, `shotsAt`, `shotsNo`, `shotsNoV`.
- **Play**: `logged` (hours played), `logAt`, `lgH` `[[ms, deltaHours]]` (≤40), `plAt` (ms when Playing started), `dropAt`, `dropP`, `eta0` `{d, at}`.
- **Notes and lists**: `wasI` `[{t,x}]` (Where was I, ≤20), `wiStory` `{t,x,w,sure,at}`, `diary` `[{m,w,l,d,…}]`, `lists` (list ids), `own` (1 = owned).
- **Small flags**: `cap` (calling-card snapshot), `sgOk`, `qcK`, `hpFix`, `dwBack`, `rawgLook`.

### 2.5 Top-level settings and flags in S (273 distinct `S.xxx` names in index.html)
Port rule: **keep every top-level key, typed or not.** The groups are:
- **Version and tour**: `lastV` (string APP_V), `tourFrom`, `tourAt`, `tourRedo`. Flag-style keys: `scoreMig`, `gdMig`, `rowGlowMig`, `pxKindMig`, `pxMig229`, `genDedup`, `dupesFixed`.
- **Views**: `gamesView`, `calView`, `tasteView` (`SUBKEY` 3960), `startTab`, `qFilter`, `sort`, `qLook`, `libLayout`, `fullStyle`, `showPlayed`, `rateMode`, `stackCfg`, `shxX`, `dzX`.
- **Look**: `theme`, `bgBase`, `bgTint`, `bgGrad`, `uiStyle`, `radius`, `corners`, `motion`, `row*`, `art*`, `fig*`, `card*`, `lib*`, `rail*`, `nav`, `tabFade`.
- **Feature switches**: `*Off` keys (`pxOff`, `rvOff`, `newsOff`, `hbOff`, `bugOff`, `useOff`, `teaOff`, …), plus `grum`, `sound`, `buzz`, `bookMusic`.
- **Integration ids, all writing-side**: `nudgeOn`, `nudgeId`, `nudgeKinds`, `nudgeMode`, `calId`, `steamMe`, `pxWatching`, `syncOn`, `syncPub`, `syncTok`.
- **Bookkeeping timestamps**: `pxAt`, `stAt`, `hbAt`, `lastBackup`, `edits`, `fx` `{r,t,at}`.
- **Nexi**: `nx*`, `bub*`, `userName`.

`S.rawgKey` and `S.v` are deleted on restore. The file backup adds `v: BK_V = 11` (10197); the cloud copy has no `v`.

### 2.6 The RAWG key
It travels only in the header line (`rk`) of the cloud plaintext, never inside S. The web app keeps it in `gq:rawgkey`. Android: keep it in encrypted prefs, never in logs or reports.

### 2.7 Picture links
- A stored picture value (`img`, `art`, `hero`, `bg`, `key`, `shots[]`) is one of:
  - an absolute `https://` URL
  - a `data:image/...;base64` upload (can be MB)
  - a **bare repo-root file name** such as `fefw-key-art.jpg`, `FEFT_cast.webp` or `logo-512.png`
- The browser resolves a bare name against the page, so **resolve relative values with `URI("https://shamlawy.github.io/Play-next/").resolve(value)`** (the page is `/Play-next/index.html`).
- The web app's own test for a "same-site file" is `fitSame` (index.html:25416): `u.length < 300 && /^[\w\-./%]+\.(jpe?g|png|webp)$/i`, or a value starting with the page origin.
- `blob:` values are dead outside the session. Treat them as empty.
- `wsrv.nl` resize copies are made at draw time, never stored.
- Never assume a repo file is unused: saved games link to them (CLAUDE.md).

---

## 3. Every helper route: method, purpose, read/write
"Origin" means the route returns 403 unless the Origin is in `ALLOW`. The block list for the practice copy covers anything that **changes the owner's records or server-side state tied to them**. Pure caches (fx, steam tags, ost lists) count as read.

| Route | Method | Origin check | Purpose | Practice copy |
|---|---|---|---|---|
| `/sgdb/*` | any | no | SteamGridDB proxy (art search) | allow (read) |
| `/why` | GET | no | AI health check | allow (read) |
| `/why` | POST | yes | AI "why it fits you" | allow (read; sends ratings, stores nothing) |
| `/chat` | POST | yes | Nexi chat | allow (read; reply only) |
| `/chat/memory` | POST | yes | rewrites Nexi's memory text (returned to the phone, not stored) | allow (read) |
| `/similar` | POST | yes | AI similar games | allow (read) |
| `/recap` | POST | yes | AI story so far | allow (read) |
| `/review` | POST | yes | AI design review of a screen map | allow (read) |
| `/version` | any | no | helper version and features | allow (read) |
| `/fx` | any | no | exchange rates (KV cache only) | allow (read) |
| `/prices` | POST | yes | prices; **with `id`+`watch` it writes `px:<id>`; with `off` it deletes** | allow **only without `id`/`watch`/`off`** (strip them or block) |
| `/steam` | POST | yes | Steam tags + more like this (KV tag-name cache) | allow (read) |
| `/steam/me` | POST | yes | link a Steam profile (writes/deletes `sp:<id>`) | **BLOCK** |
| `/steam/sync` | POST | yes | Steam sessions; **writes `sp:<id>` when changed** | **BLOCK** |
| `/reviews` | POST | yes | Steam review summaries | allow (read) |
| `/hours` | POST | yes | HowLongToBeat | allow (read) |
| `/art` | POST | yes | store pictures | allow (read) |
| `/news` | POST | yes | Steam news | allow (read) |
| `/ost` | POST | yes | soundtrack ranking (KV cache `ost2:`) | allow (read) |
| `/ost/clip?u=` | GET | yes | Apple clip proxy | allow (read) |
| `/yt` | POST | yes | YouTube soundtrack (KV cache) | allow (read) |
| `/vault/list`, `/vault/get` | GET | yes | backup read | allow (read) |
| `/vault/put`, `/vault/del` | POST | yes | backup write/delete | **BLOCK** |
| `/cal/put` | POST | yes | release-day calendar list (writes/deletes `cal:<id>`) | **BLOCK** |
| `/cal/<id>.ics` | GET | no | calendar feed | allow (read; not needed) |
| `/nudge/key` | GET | no | VAPID public key (**creates it in KV if missing**) | **BLOCK** (push is Phase 9) |
| `/nudge` | POST | yes | save/delete a push subscription + plan | **BLOCK** |
| `/nudge/test` | POST | yes | sends a test push | **BLOCK** |
| `/announce` | GET/POST | no | announces a new app version to **every** phone (writes `appv`, sends pushes) | **BLOCK** (never call) |
| `/bug` | POST | only rejects a *wrong* Origin | problem reports (writes `bug:<sig>`) | **allow**: the plan permits the app's own reports (`app:"android"`) |
| `/usage` | POST | yes | weekly usage counts (writes `use:<id>`) | **BLOCK** |
| `/usage` | GET | Bearer BUG_KEY | for usage.yml | never called |
| `/book/key` | POST | yes | book public key (writes `bookkey:<uid>`) | **BLOCK** |
| `/book/keys` | GET | Bearer BUG_KEY | for book-keys.yml | never called |
| `/bugs`, `/bugs/ack` | GET/POST | Bearer BUG_KEY | for bugs.yml | never called |
| `OPTIONS *` | | | CORS preflight | n/a |
| anything else | | | `404 {"error":"not found"}` | |

There is a **second writing host outside the helper**: friend sync at `SYNC_URL = "https://playnext-sync.hussamnabil48.workers.dev"` (index.html:8083).
- `PUT /u/<syncPub>` with `X-Token` is **BLOCK**.
- `GET /u/<friendId>` is a read.

Every `save()` calls `frAuto()` (8088), which publishes when `S.syncOn` is set. The restored S carries `syncOn/syncTok` from the web phone, so the Android port must not auto-publish.

Suggested client rule: an allow-list of (method, path) pairs. Everything else throws `WriteBlocked`. Requests to `/prices` are rewritten to drop `id`, `watch`, `all` and `off`. A unit test sends every route in the BLOCK rows and expects a refusal.

---

## 4. Backup metadata and the `v=a<version>` tag
- **Where it's written:** `cbRun` (index.html:19110): `cbFetch("put", k, "&n=" + n + "&v=" + APP_V + keep, …)`. The helper stores `v: String(v).slice(0, 8)` in the KV metadata (helper.js:521), and `/vault/list` returns it as `v` for every copy (latest, d0–d6, k).
- Today the web app writes `"327"` (digits only) and **never reads `v` back**.
- **Tagging:** at switch day the app sends `&v=a<versionCode>` (for example `a12`, at most 8 chars in total, so versionCode ≤ 9,999,999). The phase-10 web check is `list.find(k=="latest").v.startsWith("a")`.
- Nothing needs to change in the helper.
- Related client-side metadata in `gq:cloud`: `at` (last good put), `hash` (sha256 of plaintext, to skip same uploads), `n`, `kb`, `saved`, `err/errAt`.
- `keep=1` is sent when `CB.n >= 6 && n < CB.n/2`.

---

## 5. Edge cases

### 5.1 Dates
- **Stored forms:**
  - `g.date`, `g.added`, `played.t`, `duels.t` are `YYYY-MM-DD` strings.
  - Timestamps (`*At`, `sessions.s/e`, `timer.from`, `lgH[i][0]`, list `t`) are ms epoch numbers. They are bigger than Int32, so use `Long`.
- **`new Date("YYYY-MM-DD")` = UTC midnight.** In the owner's UTC+4 that is 4 AM local on the same calendar day.
  - It is used plainly in 24 places: `days()` 7314, row countdowns 8825/8888/9115, `isFresh` 3727, `isUpcoming` 3732, `shortDate` 7916, load's flip 4264, 7345, 8589, 9394, 10614, 18600, 23667, …
  - Another 24 places use `new Date(d + "T00:00:00")` = **local** midnight (Nexi's week count 12488, …).
  - Countdowns like `Math.ceil((new Date(g.date) - now)/864e5)` therefore read "1 day away" between local midnight and 4 AM on release day.
  - Kotlin needs two helpers, `jsUtcMidnight(s)` and `jsLocalMidnight(s)`, chosen to match each call site when it is ported.
- **Today strings:**
  - "Today" comparisons use `new Date().toLocaleDateString("en-CA")` = **local** `YYYY-MM-DD`, compared as plain strings (`g.date <= td`).
  - New records use `new Date().toISOString().slice(0,10)` = **UTC** date (`added`, `played.t`, `duels.t`).
  - Port both exactly.
- Invalid or empty dates give `NaN` in JS (guarded by `isNaN` in `shortDate`). Kotlin must treat unparsable values as "no date", not crash.
- Edit writes `status = date ? (status==="tba" ? "confirmed" : status||"confirmed") : "tba"`.

### 5.2 Missing or odd fields (be lenient, like JS)
- `state` missing means `"upcoming"`.
- `games/played/duels` missing mean `[]`.
- Numbers may be strings in old data. The app uses `num(v, def, lo, hi)` (8393) and `+x || 0` everywhere, so parse leniently.
- `genres` may be a string or an array.
- `real` may be missing, which means unscored.
- `hype` may be missing.
- `rawgId` may be null.
- `bgStr` may be null.
- `colour` may be missing: fall back to `PALETTE[0]` `#4DA3FF`.
- JSON may contain escaped lone surrogates (`"\ud83d"`) from cut titles. The parser must accept them, and the raw string must be kept for writing back.
- Big ints (ms) and decimals (7.5) must round-trip unchanged. Keep `JsonElement` raw and type only what is read.

### 5.3 Data changes the web app makes at load (each changes S, then `save()`)
1. `load()` 4252:
   - Fill in `games/duels/played` if missing.
   - `dedupePlayed()` (3510); `S.dupesFixed` gets the count.
   - Every game in `played` gets `state="played"`.
   - An **upcoming game whose `date <= today(local)` and is less than 30 days old (UTC-midnight math) becomes `backlog`**.
   - `libLayout` `arc|wheel` becomes `reel` (v174).
   - Empty `games` means `seed()` (6 made-up games). The practice copy must **not** do this.
   - `tourRedo/tourFrom` (v173).
   - `lastV` set to APP_V (starts the what's-new tour).
2. `scoreMigrate()` 13179 (flag `S.scoreMig`, run via `extend("xFeatures")` 13330): every played record with `!(real>0) && !z && !dk` gets `real=0, dk=1`.
3. 17189 (flag `S.gdMig`): `fullStyle "hub"` becomes `"dash"`.
4. 17866 (flag `S.rowGlowMig`): `rowArtGlow === 7` is deleted.
5. 19768 (flag `S.pxKindMig`): `"deal"` is added to `nudgeKinds`.
6. 19772 (flag `S.pxMig229`): store matches whose name has a number the title lacks are dropped from `g.px` / `pxSt` / `pxPs(+C)` / `pxNs(+U)`; `px.*.none` and the `pxStNo/PsNo/NsNo` flags are cleared; `S.pxAt = 0`.
7. 22657 (flag `S.genDedup`): each game's genres are de-duplicated with `gdKey` (case/space/hyphen-insensitive), **written back as a `", "` string**.
8. 20369 (**no flag, every start**): `plAt = Date.now()` is set for any `playing` game without it.
9. 22033: a `qFilter` of `G:<genre>` registers a genre tab (UI only).

For a v327 backup, flags 2–7 are already set. In the practice copy, apply 1 (the flip and played state) and 8 **in memory only** (edits reset), and the rest only when the flag is missing.

The daily copies are all from v211+ (the vault arrived in v211), so pre-v211 shapes only arrive through a manual file backup.

---

## Test vector plan
1. **Known-answer vectors** (done; values are in `1-data-vector.txt`), made with the web app's own `cbNorm/cbKeys/cbSeal/cbOpen` extracted from index.html and run in Node 22 WebCrypto, with a made-up code:
   - code `PN-AB2C-DEF3-GH4J-KLM5-NPQ6` normalises to `AB2CDEF3GH4JKLM5NPQ6`
   - id `a2ef69c79f794293faa4577b`
   - tok `85dd99e5170ce73ed61716fe31a4db70bf63da27c1796b35504c1232d1d6b850`
   - key `7c13739db5b2760dc20f150c1b314ba661e20afbe1a4a973ff69555e79113411`
   - IV `0102…0c`
   - a sealed z=1 copy (157 bytes) and a z=0 copy of a small plaintext

   The Kotlin unit test must derive the same id/tok/key and open both to the exact plaintext.
2. **Normaliser cases:** `pn-ab2c def3…` gives the same code; 21/23 characters, a `0`, `1`, `I` or `O`, or `PN` + 20 characters each behave as `cbNorm` does. Generate the expected outputs from index.html's own function (copy `vec.mjs`).
3. **Negative cases:**
   - first byte 2 means "newer kind of backup"
   - a flipped AAD byte or a wrong key gives a GCM failure ("didn't open with this code")
   - truncated bytes fail
   - z=1 with a non-gzip body fails
4. **Round-trip CI test (from the plan):** Kotlin opens a seed backup sealed by the web code, changes one score, re-seals (z=1, new IV), then a Node script with the web `cbOpen` + `readBackup` opens it and diffs: only that score differs, and key order, numbers, unknown fields and lone-surrogate escapes are kept.
5. **Live gate test:**
   - `GET /version` with `Origin: https://shamlawy.github.io` returns 200.
   - `/vault/list` with a random valid-format id returns `{"list":[]}`.
   - The same call without Origin returns 403 `origin`. This proves both the header and the gate.
6. **Write-block test:** every BLOCK route and the sync PUT are refused by the client before any network call.

## Kotlin port checklist
- [ ] `CbCode.normalize/format`, exactly as `cbNorm`/`cbFmt`, with the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.
- [ ] `CbKeys`: SHA-256 of `"pn-vault-id:"/"pn-vault-tok:"/"pn-vault-key:" + code`; lower-case hex; id is the first 24 characters.
- [ ] `CbSeal.open` (and `seal` for tests): `[1][z][iv12][ct+tag16]`, AES-256-GCM, AAD `[1,z]`, gzip when z=1.
- [ ] Split the plaintext at the first `\n`: header `{rk}` (the RAWG key) and the raw S JSON.
- [ ] `readBackup` checks and messages, then `applyBackup` (strip `BK_SECRET`, filter games without id/title, fill `duels`/`played`, `dedupePlayed`, delete `v` and `rawgKey`).
- [ ] One network client: base `https://playnext-helper.hussamnabil48.workers.dev`; header `Origin: https://shamlawy.github.io` on every call; no cache on vault calls; `Authorization: Bearer <tok>` on vault calls.
- [ ] Allow-list of read routes; refuse the write routes (section 3); rewrite `/prices` to drop `id`, `watch`, `all` and `off`; block `PUT playnext-sync…/u/*`; never auto-publish friend sync even if `S.syncOn`.
- [ ] Error mapping like `cbFetch`: 404 with "not found" means the helper is too old; 403 means wrong code or origin; 429 means too soon (not used while read-only).
- [ ] The restore code goes in the Keystore; Android backup is off; never log the code, `tok`, the RAWG key, `bookKey`, `syncTok`, `nudgeId` or `calId`.
- [ ] Keep the raw `S` as a JSON tree (`kotlinx.serialization.json.JsonObject`), typed views over it, and preserve unknown keys and number literals.
- [ ] Lenient game parsing per 2.4 / 5.2 (genres as a string or array, numbers as strings, missing state).
- [ ] The load-time steps from 5.3, in memory, with **no seed** on an empty backup.
- [ ] Picture URL resolver: relative values resolve against `https://shamlawy.github.io/Play-next/`; `data:` is decoded locally; `blob:` is ignored.
- [ ] Two date helpers (UTC midnight vs local midnight) and two "today" helpers (local en-CA vs UTC ISO). Note at each call site which one the web code uses.
- [ ] "Your games as of <t>" comes from the `latest` list entry; warn when it is more than 24 h old; re-read at start and on resume.
- [ ] A separate local store for bookkeeping (the `gq:*` equivalents), never put into the backup JSON.
- [ ] Prepare for later: `put` with `&n=<valid games>&v=a<versionCode>` (≤8 chars) and `keep=1` per the web rule; 20 s minimum gap; 20 MB maximum.
- [ ] Skills book: open the `BOOK_SEALED` entries with `S.bookKey` (ECDH P-256, then HKDF-SHA256 with salt = the 65-byte ephemeral public key and info `"play-next-book-v2"`, then AES-GCM with IV bytes 65..76 and ciphertext from byte 77; entries are keyed by `bookNorm(title)`; index.html:28325). Never generate or send a key.

## Risks / unknowns
- **Origin spoofing as the only gate:** this works today, but a helper change that adds stricter checks (for example `Sec-Fetch-*`) would break the app. The Phase 0 live test catches it.
- **The backup is the whole raw S,** including the friend-sync token, the book private key and the push/calendar ids. If the app ever re-seals it after switch day it carries them too (fine), but any logging or crash dump of the state leaks them. Report scrubbing must cover the state.
- **Copy freshness:** the web app uploads at most every 10 minutes after a change and when the app is hidden (more than 60 s since the last copy), so the practice copy can lag by up to about 10 minutes. The `latest.t` it shows is the upload time, not the time of the last edit.
- **Memory:** a backup with many `data:` uploads may be 10–20 MB sealed and much bigger as a decoded string. Stream the decryption and gzip, and parse on a background thread.
- **Gzip determinism:** don't compare sealed bytes across implementations. Compare the plaintext only.
- **JS number and string semantics:** `JSON.stringify` of a double like `0.1+0.2` gives the shortest round-trip form. kotlinx must write back the original literal (keep `JsonPrimitive` content) or the round-trip test will show diffs. The order of keys must be kept (`LinkedHashMap`).
- **Lone surrogates:** some JSON parsers turn lone-surrogate escapes into U+FFFD. Check the chosen parser keeps them.
- **Date semantics:** the UTC-vs-local split is per call site and easy to get wrong. Each phase's Map must note which one each screen uses.
- **`/prices` and `/nudge/key` are mixed or hidden writers;** `/announce` and `/bug` accept calls without an Origin. The allow-list approach (not a block-list) is safer.
- **Things I could not check from here:** the live helper's current deploy matching helper.js (this container can't reach Cloudflare), and whether Cloudflare in front of the worker rejects non-browser user agents. Both are covered by the Phase 0 live test.
- `S.lastV` in the restored state is the web phone's version, so the practice copy needs its own "last seen version" for its own what's-new tour (bookkeeping, not S).
- The `v` tag is cut to 8 characters, so a versionCode above 9,999,999 would lose digits. CI run numbers are far below that.
