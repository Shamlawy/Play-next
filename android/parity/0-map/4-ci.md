# Map 4: CI, automation and tooling for the Android app (Phase 0)

Read-only mapping, Oct 26 2026. Repo `/home/user/Play-next` at `b63e073`. `android/` doesn't exist yet. The repo has no GitHub Releases yet (`gh api repos/Shamlawy/Play-next/releases` → 0). This report prints no secret values.

Sources: `docs/ANDROID-PLAN.md` (L37-45 "How it reaches the phone", L67-115 gates, L127-132 "Problem reports from the app"), CLAUDE.md (Automation, mods), `.github/workflows/*.yml`, `.github/scripts/bugsync.mjs`, `tools/bugsync-check.mjs`, `worker/helper.js`, `.claude/skills/release-guard/hooks/{rules,register}.ts`, `mods/second-look/hooks/register.ts`, `.claude/second-look.md`, `.claude/skills/play-next-design/SKILL.md`, `.claude/skills/bug-inbox/hooks/*`, `.gitignore`.

---

## 1. Workflows

### 1.1 Table

| File | Trigger | `permissions:` | Secrets | Actions (pin) | Notes |
|---|---|---|---|---|---|
| `announce.yml` | push main, paths `index.html` (L4-8); dispatch | **none set** (repo default) | none | `actions/checkout@v4` | waits for Pages, POSTs helper `/announce` |
| `app-check.yml` | `pull_request` paths index.html, sw.js, worker/**, tools/**, swcheck.mjs, itself (L13-22); dispatch | `contents: read` (L23-24) | none | checkout@v4, setup-node@v4, upload-artifact@v4 | 9-job matrix (L32-42); playwright@1.56.1, eslint@9.36.0 pinned (L51). **`android/**` isn't in its paths, so an android-only PR doesn't start it (good).** A Phase 0 PR that edits `tools/bugsync-check.mjs` will start it (tools/**). |
| `book-keys.yml` | dispatch | `contents: read` | `CLOUDFLARE_API_TOKEN` (L18) | setup-node@v4 | BUG_KEY = sha256(token+":play-next-bugs") (L22) |
| `bugs.yml` | cron `17 * * * *` (L6); dispatch | `issues: write`, `contents: read` (L8-10) | `CLOUDFLARE_API_TOKEN` (L22), `github.token` (L23) | checkout@v4, setup-node@v4 (node 20) | runs `.github/scripts/bugsync.mjs`; `concurrency: bugs` |
| `deploy-helper.yml` | push main, paths `worker/**` + itself (L9-11); dispatch | **none set** | `CLOUDFLARE_API_TOKEN` (L25), `CLOUDFLARE_ACCOUNT_ID` (L26, optional), `YOUTUBE_KEY` (L27, optional) | checkout@v4, setup-node@v4; **`npx --yes wrangler@4` (floating major, L40)** | KV find/create, see 1.3 |
| `hltb-probe.yml`, `news-probe.yml`, `ost-probe.yml`, `price-probe.yml`, `steam-probe.yml` | dispatch with inputs + push on their script/helper paths | `contents: read` | none | checkout@v4, setup-node@v4 (upload-artifact@v4 in price) | inputs passed through `env:` (safe against injection) |
| `usage.yml` | cron `41 6 * * 1`; dispatch | `issues: write`, `contents: read` | `CLOUDFLARE_API_TOKEN`, `github.token` | checkout, setup-node | `.github/scripts/usagesync.mjs` |
| `webkit-check.yml` | push main index.html/sw.js/...; dispatch with `scenario`, `widths` | `contents: read` | none | checkout, setup-node, upload-artifact@v4 | validates inputs with `case` (L44-45) |

**Secrets by name** (from `secrets.*` uses; the proxy blocks listing them, `gh secret list` → 403): `CLOUDFLARE_API_TOKEN` (required, an account token with Workers Scripts + **Workers KV Storage: Edit**, deploy-helper L2-4), `CLOUDFLARE_ACCOUNT_ID` (optional override), `YOUTUBE_KEY` (optional). The worker's own secrets (`SGDB_KEY`, `YT_KEY`, `BUG_KEY`) live on Cloudflare. BUG_KEY is derived from the CF token, so nothing new needs setting up.

**Pinning style:** first-party `actions/*` only, by **major tag** (`@v4`), never by SHA. No third-party actions today. npm tools are pinned to exact versions, except wrangler (`@4`). Android CI will need third-party actions (`gradle/actions/setup-gradle`, `android-actions/setup-android`, `reactivecircus/android-emulator-runner`). Pin those **by full SHA** with a `# vX.Y.Z` comment (github-actions-hardening skill), and pin `wrangler@4.x.y` exactly in the new workflows. Never use `pull_request_target`. Set `permissions:` explicitly on every new workflow: announce and deploy-helper rely on the repo default, which can't be read from here.

### 1.2 Things worth knowing
- `tools/bugsync-check.mjs` **is not run by any CI job** (no hit in kit.mjs or .github). It's run by hand only (tools/README.md L33). App-check's paths also don't include `.github/scripts/bugsync.mjs`. Phase 0 should add a step that runs it (in android-check, or app-check's `code` job, with `.github/scripts/bugsync.mjs` in its paths).
- release-guard's live watch looks for the Pages run `'pages build and deployment'` and `deploy-helper.yml` (register.ts L16, L136). An Android release run would need a third watch: "android-vN is released".

### 1.3 How deploy-helper finds or makes the KV namespace (deploy-helper.yml L28-61)
1. It strips whitespace from TOKEN/ACCOUNT and refuses a 32-hex "token" (it's an ID) (L33-38).
2. It runs `wrangler whoami`, taking the account id from it unless `CLOUDFLARE_ACCOUNT_ID` is set, then exports `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` (L39-46).
3. `find_kv()` = `wrangler kv namespace list` piped to node: JSON-parse from the first `[` and pick the **first title matching `/nudge/i`** (L48).
4. If nothing matches, it runs `wrangler kv namespace create playnext-nudge` and finds it again. If it still finds nothing, it raises `::error::` (L49-51).
5. It writes `wrangler.deploy.json` with the id (wrangler.jsonc has `kv_namespaces: [{binding:"NUDGE"}]` with no id), then deploys and pipes `BUG_KEY` / `YT_KEY` into `wrangler secret put` through stdin (L54-65). Values go through stdin, never through argv.

**Hazards for a signing namespace:** (a) its title must **not** contain "nudge", or deploy-helper's substring finder could pick it. (b) Use an **exact-title** match for the signing namespaces, because a substring like "signing" would match both the main one and the copy. (c) In Wrangler 4, `kv key get/put` act on **local** storage unless you pass `--remote`. (d) Keep values out of argv: use `--path <file>` for put, and redirect get to a file.

### 1.4 Sketch: `android-signing-key.yml` (one-time, dispatch only)

```yaml
# One-time: makes the Android release signing key on GitHub's machine and keeps it in two Cloudflare KV namespaces
# (not the helper's NUDGE). Refuses to run if a key already exists in either. Never prints, uploads or commits the key.
name: Android signing key (one-time)
on:
  workflow_dispatch:
    inputs:
      confirm: { description: "Type MAKE-THE-KEY", required: true }
permissions: {}                      # needs no GitHub token at all
concurrency: android-signing-key
jobs:
  make:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@<sha> # v4, temurin 17 (keytool)
        with: { distribution: temurin, java-version: "17" }
      - name: Make and store the key
        env:
          TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          ACCOUNT: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          CONFIRM: ${{ inputs.confirm }}
        run: |
          set -euo pipefail
          [ "$CONFIRM" = "MAKE-THE-KEY" ] || { echo "::error::Type MAKE-THE-KEY to run"; exit 1; }
          umask 077; W="npx --yes wrangler@4.x.y"; T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
          export CLOUDFLARE_API_TOKEN=$(printf '%s' "$TOKEN" | tr -d '[:space:]')
          A=$(printf '%s' "$ACCOUNT" | tr -d '[:space:]'); [ -n "$A" ] || A=$($W whoami 2>&1 | grep -Eo '[0-9a-f]{32}' | head -n1)
          export CLOUDFLARE_ACCOUNT_ID="$A"
          ns() { $W kv namespace list 2>/dev/null | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const a=JSON.parse(s.slice(s.indexOf("[")));const n=a.find(x=>x.title===process.argv[1]);process.stdout.write(n?n.id:"")})' "$1"; }
          A1=$(ns playnext-android-signing); [ -n "$A1" ] || { $W kv namespace create playnext-android-signing >/dev/null; A1=$(ns playnext-android-signing); }
          A2=$(ns playnext-android-signing-copy); [ -n "$A2" ] || { $W kv namespace create playnext-android-signing-copy >/dev/null; A2=$(ns playnext-android-signing-copy); }
          [ -n "$A1" ] && [ -n "$A2" ] || { echo "::error::Couldn't find or make the signing namespaces"; exit 1; }
          # refuse if a key already exists in EITHER copy (list keys: names only, never values)
          for id in "$A1" "$A2"; do
            if $W kv key list --namespace-id "$id" --remote | grep -q '"keystore"'; then echo "::error::A signing key already exists. Never make a second one: updates would stop installing."; exit 1; fi
          done
          PASS=$(openssl rand -base64 33 | tr -d '\n/+='); echo "::add-mask::$PASS"
          keytool -genkeypair -keystore "$T/k.p12" -storetype PKCS12 -alias playnext -keyalg RSA -keysize 4096 -validity 36500 \
            -dname "CN=Play next" -storepass:env PASS -keypass:env PASS >/dev/null 2>&1   # export PASS first
          base64 -w0 "$T/k.p12" > "$T/k.b64"; printf '%s' "$PASS" > "$T/p.txt"
          for id in "$A1" "$A2"; do
            $W kv key put keystore --path "$T/k.b64" --namespace-id "$id" --remote >/dev/null
            $W kv key put password --path "$T/p.txt" --namespace-id "$id" --remote >/dev/null
          done
          # read both back and compare hashes, printing only "same"/"differ" and the cert SHA-256 (public: fine to log)
          for id in "$A1" "$A2"; do $W kv key get keystore --namespace-id "$id" --remote > "$T/back.b64"; cmp -s "$T/k.b64" "$T/back.b64" || { echo "::error::copy in $id differs"; exit 1; }; done
          keytool -list -v -keystore "$T/k.p12" -storepass:env PASS | grep -E 'SHA256:' | head -n1
```
Notes: `-storepass:env` needs `export PASS`. No `--expiration-ttl` (no expiry). No `actions/checkout` (nothing from the repo runs). No artifact upload. The release workflow fetches the key the same way (exact-title namespace, `kv key get … --remote > file`, `::add-mask::` the password, decode into `$RUNNER_TEMP`, delete in an `always()` step), **only when `github.ref == 'refs/heads/main'` and the event is push or dispatch**. PR builds sign with a throwaway debug-style key made on the fly. Record the cert SHA-256 in the plan, so a later build can check it signed with the right key. Before the first release, decide whether a third offline copy is wanted (only an owner step could hold one, and the plan says no owner step).

---

## 2. Problem reports: bugs.yml → bugsync.mjs

### 2.1 How a report becomes an issue today
- **App → helper:** `POST /bug {reports:[…]}` (helper.js L178-181). If an `Origin` header is present, it must be in `ALLOW` = `["https://shamlawy.github.io","http://localhost:8765"]` (L41). `bugTake` (L1284-1306) keeps **only** `sig` (8 hex), `kind`(16), `msg`(600), `stack`(1500), `at`, `screen`(60), `w,h,dpr`, `dev`(40), `v`(**8 chars**), `style`, `note`, `crumbs`, `lib`. **Any other field, such as `app`, is dropped.** One KV record per sig (`bug:<sig>`, TTL 45 days, `BUG_MAX = 300` records, shared with the web app) merges `n`, `vs`, `devs`, `screens` and the latest `sample`.
- **bugs.yml** runs every hour, with `GET /bugs` (Bearer BUG_KEY) → bugsync.mjs → `POST /bugs/ack {sigs:{sig:n}}` (L330).
- **bugsync.mjs:**
  - Labels: `LABEL = "auto-bug"`, `PENDING = "pending-proof"` (L18, created L45-46). It lists issues by `labels=auto-bug` across up to 5 pages (L47-51) and maps sigs from `[bug:xxxxxxxx]` in titles (L52-53).
  - Title: `🐞 <KIND>(<devFam>): <msg≤80> [bug:<sig>]` (L262). `KIND` map L55. `devFam` = `/^(iPhone|iPad|Android|Mac|Windows)/` on `sample.dev` (L126). **The owner's web Chrome on the Fold already reports as "Android".**
  - Body: `body()` L58-71, with the table (Times/Versions/Phones/…) and a Safari hint for iPhone/iPad.
  - Gate for timing reports: `gate()` L88-102. `REPEAT=3`, `REOPEN=2`, `BIG_MS=400`, and the measurement thresholds. `HITCH` regex (L85) and `MEASURE` (L86) are matched against the **web app's exact message wording**.
  - Families (L104-153): `famOf(msg)` from `PLACE` regexes (L127-135) gives `kind|place`. `costOf(msg, stack)` parses the web app's `lagSplit` line (`N long frames, Tms: script Sms (forced style/layout Fms), style+layout Lms, render Rms`, or `no long frame …`) into script / layout / drawing / unknown. `sameFam` (L153). A fold step closes newer open issues of one family into the oldest (L205-215). Reports with no open issue join an open family issue (L246-249). One-offs of a family count together (L253).
  - Note in the body: `<!-- bugsync {json} -->` (`fam, cause, also[], proof{fix,dev,w,clean{},dirty[],done}, dirty[], folded`) (L154-155, save L193-197).
  - Version logic: `vNum(v)` = leading digits (L158). `APP_V` comes from env or **index.html's `const APP_V`** (L125). `fixIn(comments)` = the highest "Fixed in vN" a person wrote (L180-181). A closed issue whose latest sighting `s.v` is below its fix stays closed ("Seen on an older version", L280-293).
  - Pending proof (L113-121, L217-230, L268-278, L301-326): a timing issue closed as completed after `PROOF_SINCE` (2026-10-20) reopens with `pending-proof`, `proof.fix = fixIn || APP_V`, and `proof.dev = devFam`, `w` from its latest sighting. Each later sighting at or above the fix: once REOPEN sightings (or one 400ms+) arrive, "didn't hold" and the label comes off. Proof data (`ran`) comes from **every bug sample's (dev family, width, v)** plus **the helper's `/usage` records** (L304-308). It closes once `PROOF_N=3` clean versions include ≥1 at the same width (±`NEAR_W=24`).
  - Limits: `MAX_NEW = 15` new issues per run.

### 2.2 What must change for `app:"android"` → its own `android-bug` label and version/proof logic

**A. Getting the marker through the helper (choose one; the plan says "the helper stays as it is"):**
1. *(Cleanest; one line plus a deploy)* In `bugTake`, add `app: /^(android)$/.test(r.app) ? r.app : ""` to `sample`, and `app` to the new record. Bump HELPER_V only if the app checks it. This contradicts ANDROID-PLAN L49 ("the helper stays as it is… Notifications need one helper change"), so the plan's line would need amending in the same PR (second-look blocks on a rule left contradicting itself, `.claude/second-look.md` L9).
2. *(No helper change)* Encode it in a kept field: `dev` starts with a fixed marker, e.g. `"App Android 16 SM-F…"` (40 chars max). Don't prefix `v` with a letter: `vNum("a12")` → 0 (bugsync L158) and v is cut at 8 chars. bugsync then uses `appOf(b) = b.sample?.app === "android" || /^App\b/.test(b.sample?.dev || "") ? "android" : "web"`.
- Either way, **sigs must not collide with the web app's**: the app should hash with a prefix (e.g. sha256("android|"+kind+"|"+msg)[0..8]). The 300-record cap is shared: watch it.

**B. bugsync.mjs changes:**
1. `const ALABEL = "android-bug"`. Create the label beside L45. List issues for **both** labels (a second loop like L47-51 with `labels=android-bug`, de-duplicated by number). Mark each tracked issue `app = labelsOf(i).includes(ALABEL) ? "android" : "web"`.
2. **Route by (app, sig):** the `bySig` and `route` maps (L52-53, L232-234) key on `app + ":" + sig`, so a web sig can never land on an android issue or the other way round.
3. New issue (L261-265): `labels: [app === "android" ? ALABEL : LABEL]` (decide whether android issues also carry `auto-bug`; see 2.4). Title device `(App)` instead of `devFam` (`devFam` of "App …" is ""). Body: skip the Safari hint, and add "Android app vN (versionCode)" to the table.
4. **Families stay per app:** add `app` to the family object and to `sameFam` (L153). `openOf` (L200) filters by app. The fold loop (L205-215) uses that. `famB` (L240) carries `appOf(b)`.
5. **Version logic per app:**
   - `APP_V` for android = the newest `android-vN` release (GitHub API `GET /repos/{REPO}/releases?per_page=20`, filter `tag_name` `^android-v(\d+)$`, ignore drafts and prereleases). Allow an env override `ANDROID_V` for tests, like `APP_V` (L125). The versionCode is the CI run number and doesn't live in the repo, so it can't be read from a file.
   - `fixIn` on an android issue: accept "Fixed in android-vN" and plain "Fixed in vN" (meaning versionCode). Write a convention into CLAUDE.md and the plan: android issues close with "Fixed in android-vN".
6. **Proof per app (L302-326):** `ran` entries carry `app`. For a pending android issue, count only android samples. Today `p.dev = "Android"` would be satisfied by the owner's **web Chrome** reports (`devFam` = "Android"), which is exactly the mix-up to prevent. `/usage` records are web-only (the app writes no usage until switch day), so **android proof has no "this version ran" source unless the app sends one**. Proposal: a tiny allowed report through `/bug`, kind `ran` (fits in 16 chars), sig = hash("android-ran|"+versionCode), sent once per version. bugsync treats `kind === "ran"` as no issue: it adds to `ran`, acks it, and creates no issue (add a branch before L251). Without it, an android stutter fix can never be proven and stays `pending-proof` forever.
7. **Timing wording:** the app's JankStats messages must reuse the web wording exactly ("A 230ms stutter on Queue", "Scrolling stutters on Queue (2 hitches, worst 180ms)", "A tap took 260ms to respond on X", "Opening the app froze for 300ms"), or `HITCH`, `PLACE` and `gate` won't treat them as timing reports and every hitch would be filed at once. For "where the time went", have the app write the same `lagSplit` line format, mapping FrameMetrics: input + animation + composition → "script", measure/layout → "style+layout", draw + sync + GPU → "render". Otherwise every android stutter is cause "unknown".
8. `KIND` (L55): add any new kinds the app sends (`anr` → "Freeze", `exit` → "Killed"?), or map them app-side to existing kinds (`error`, `stuck`, `slow`, `user`).
9. Pass `ANDROID_V` and the release lookup through `bugs.yml`. It already has `GH_TOKEN`, so the releases API call is authenticated, and `contents: read` covers it.

### 2.3 tools/bugsync-check.mjs: how cases are written
- **Run 1** (L17-63): `CASES` rows are `[sig, kind, msg, n, seen, version, device, expected]` (expected ∈ file | held | log | comment | reopen | stays). The pre-existing `ISSUES` are `[number, sig, state]` (closed ones carry the comment "Fixed in v283."). `bugs` are built with `smp(v, dev)` (default dev "Android Chrome 155 (installed)", w 859). It runs bugsync with `DRY=1 REPO=x/y CF_TOKEN="" BUGS_FILE ISSUES_FILE` and classifies by regex on stdout (`issues "…[bug:sig]"` = file, `held [bug:` = held, …). Held reports must not be acked.
- **Run 2** (L65-129): `ISSUES2` built by `iss(number, sig, msg, state, extra)` (labels default `auto-bug`; `extra` = closed_at, state_reason, comments, body with `note()` / `pend()`). `BUGS2` built by `bug(sig, kind, msg, n, seen, vs[], stack, dev, w)`. It runs with `APP_V=315`, `USAGE_FILE`, `DRY_LOG` (one JSON line per write request). `RULES` = `[description, () => boolean]` using helpers `to(n, method, tail)`, `said(n, re)`, `noteNow(n)`, `filed(sig)`, `acked2`.
- **Adding android cases:** easiest as a **run 3** (its own fixtures, env `ANDROID_V=…`, and a stub for the releases lookup, e.g. a `RELEASES_FILE` fixture env like `ISSUES_FILE`, since DRY's `gh()` returns `[]` for anything that isn't page=1). Suggested RULES:
  1. A report with `app:"android"` (or the `App ` dev marker) is filed with label `android-bug`, title `(App)`, not `auto-bug`.
  2. An android report whose sig equals an open **web** issue's sig gets its own issue, not a comment on the web one.
  3. An android stutter doesn't fold into a web issue of the same `kind|place|cause`.
  4. A closed android issue "Fixed in android-v12" with a sighting from versionCode 11 stays closed. Web APP_V 330 doesn't affect it.
  5. A pending-proof android issue: web reports from "Android Chrome" at the same width don't count as clean releases. Android `ran` reports for 3 versions (one at its width) close it.
  6. kind `ran` creates no issue and is acked.
  - Update the final count line. Also add `.github/scripts/bugsync.mjs` and `tools/bugsync-check.mjs` to a CI path so the check actually runs.

### 2.4 Other consumers of the label
- **bug-inbox** mod reads only `labels=auto-bug` (`.claude/skills/bug-inbox/hooks/register.ts` L20; title parser `inbox.ts` L10-15 already accepts any `(device)`). For android issues to show in a chat's start note, add a second fetch for `android-bug`, or give android issues both labels. Plan L132 wants the build sessions to work them, not light mode. If they also carried `auto-bug`, the daily fixer's light-mode rules would see them. **Recommendation: `android-bug` only, plus bug-inbox listing it separately.**
- **learn** mod: `register.ts` L58-60 searches for `label:auto-bug … "Happened again after it was closed"`. Extend to android-bug if wanted.
- CLAUDE.md "Daily bug fixer" / "Light mode" text mentions only `auto-bug`. A line saying android-bug belongs to the build sessions avoids a contradiction (second-look L9).

---

## 3. Merge guards

### 3.1 release-guard (`.claude/skills/release-guard/`, v0.1.3, not a symlink: lives in `.claude/skills`, not `mods/`)
- **Hooks** (register.ts): Bash `git commit` → `fileProblems` (L255-266: any staged `*.json` not matching `MANIFEST` rules.ts L128, or a deleted repo-root file, is denied). Bash `git push` to main → `check('HEAD')` blocks (L268-276). Branch push → heads-up only (L296-301). `mcp__github__merge_pull_request` → fetches the PR (unauthenticated), `git fetch` the head, `check(head.sha)`, deny on block (L306-325). Stop hook: an index.html edit needs a later test command (`TESTED` regex L34; L327-336). **`gh pr merge` (used on the owner's PC) isn't hooked**, and neither is any merge path other than the MCP tool and `git push … main`.
- **`gather`** (L72-94) builds the Range (changes, LOG added lines, base/head/main index.html, helper + `node --check`/import).
- **`releaseProblems`** (rules.ts L153-187): block on files. `shipped` = index.html | sw.js | manifest | worker/** → needs a new docs/LOG.md line (L159-160). APP_V must go up and stay above main's; WHATS_NEW for vN with go/at/say/tryIt; `vN` in the LOG (L162-178). Helper check (L180-185).
- **An android-only merge passes with no checks today** (`shipped` ignores `android/**`).

**Rules an Android merge needs** (add to rules.ts as pure functions with tests in `tests/guard.test.ts`, and bump `plugin.json` version, lesson L34):
1. `androidShipped = changes.some(c => c.path.startsWith('android/'))` → the LOG needs a new line naming the Android release (e.g. `android-vN` or "Android:"), with N decided as in point 3.
2. **android-check.yml passed on the head commit.** register.ts fetches `GET https://api.github.com/repos/{repo}/actions/workflows/android-check.yml/runs?head_sha=<sha>` (or `/commits/<sha>/check-runs`) and passes `{status, conclusion}` into the Range. The rule blocks when it's missing, still running or not `success`. "Still running" should say so plainly ("wait for android-check, then merge"). Unauthenticated calls are fine (public repo, 60/hr).
3. **"Higher versionCode":** the versionCode is the release workflow's run number, so it's **not in the repo at merge time** and a merge rule can't compare it. Options: (a) keep a human release counter in the repo, e.g. `android/version.properties` `release=N` (like APP_V), which goes up on every user-facing merge and keys the what's-new and the LOG line, while versionCode stays CI-only and always increasing. Then the rule copies the APP_V rules: higher than base, higher than origin/main, LOG has `android vN`. Or (b) drop that rule and let the release workflow refuse a versionCode ≤ the latest tag. **Recommended: (a) plus (b).**
4. **App what's-new entry:** the plan (gate 7) generates what's-new from index.html's WHATS_NEW for ported features. Android-only changes (self-update, widgets) need their own entries in the repo, in a non-JSON file (e.g. `android/whatsnew.toml` or a Kotlin list) with v/go/at/say/tryIt-style fields. The rule: an entry for release N exists, each with the required fields (a copy of `whatsNew()` for that format).
5. Files: `fileProblems` will **block every `.json` under android/**. Extend `MANIFEST` (rules.ts L128) for exactly what `.gitignore` allows (see 5). Keystores (`*.jks`, `*.keystore`, `*.p12`), `local.properties`, `google-services.json` and `key.properties` should be **always blocked**, as a new rule.
6. Stop hook: a turn that changed `android/**` should need `./gradlew` test/lint (or a CI run) after the last change, the same way as index.html (`TESTED` regex + `HTML_WRITE` equivalent). Mind lesson L3: update the guard pattern in the same change that adds the new way to test.
7. Live watch: after a merge touching `android/**`, watch for the release workflow and say "android-vN is released (the phone updates itself)" or that it failed.
8. Rollback: a `rollback-android` dispatch isn't a merge, so release-guard doesn't see it. Fine.

### 3.2 second-look (`mods/second-look`, shared and generic: don't put Android specifics in the mod)
- It reviews `merge_pull_request` and pushes to the default branch with opus. The diff is capped at `MAX_DIFF = 160_000` chars (register.ts L14). Phase 0's scaffold PR will be large and **cut**. Built-in `SKIP` (L16) covers images, fonts and lock files, but not Gradle's.
- Per-repo config is **`.claude/second-look.md`** (11 lines: prose L1-9 about the web app only, `first:` L10, `skip:` L11). Add:
  - Prose: the Android app (owner's Fold only, Kotlin + Compose, `android/`, a practice copy until switch day). Block on: a writing helper route getting past the network client's write switch; the restore code, tokens, keystore or password in logs, reports, artifacts or the repo; a missing `Origin: https://shamlawy.github.io` header; `applicationId` changes; `allowBackup`/data-extraction not off; cleartext traffic on; versionCode not from CI or going down; release builds debuggable or without R8; Compose default `indication`/ripple or any press scale (#309); Material You dynamic colour; colours outside the token list; emoji icons; animating anything but transform/alpha (graphicsLayer); unknown JSON backup fields dropped on save; game titles in reports (scrubber); a PR build fetching the real key; workflow injection (`${{ }}` in `run:`), unpinned third-party actions, `pull_request_target`.
  - `first:` add `android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml .github/workflows/android-*.yml` (and the network client file once it exists).
  - `skip:` add `android/gradle/wrapper/* android/**/build/* *.jar android/**/baseline-prof.txt android/**/schemas/*` and generated/golden files.

### 3.3 Design skill
- `.claude/skills/play-next-design/SKILL.md` frontmatter says "UI change in index.html". It won't load by itself for Compose work. Amend the description to include `android/` Compose screens, and add an Android section: tokens generated from index.html (gate 7), no ripple/indication, no press scale, transform/alpha only, one still background, drawn icons (GICON) with TalkBack labels, 48dp targets, font-scale caps. `tools/design-check.mjs` and `kit.mjs eye` are index.html-only. Android equivalents are the named lint checks in plan gate 6. CLAUDE.md says a look rule goes into CLAUDE.md and the skill in the same change.

---

## 4. Self-update: finding releases

- **List:** `GET https://api.github.com/repos/Shamlawy/Play-next/releases?per_page=10`. Each release has `tag_name`, `draft`, `prerelease`, `published_at`, `body`, and `assets[]` with `name`, `size`, `content_type`, `browser_download_url`, `digest` ("sha256:…", GitHub has exposed asset digests since 2025; keep a fallback). Filter `^android-v(\d+)$`, not draft (drafts are invisible unauthenticated anyway), not prerelease.
- **By tag:** `GET /repos/Shamlawy/Play-next/releases/tags/android-v{N}`.
- `GET /releases/latest` returns the newest non-draft, non-prerelease release **repo-wide**. Today the repo has none, so it would work, but it breaks if anything else is ever released. Prefer the filtered list.
- **No-API path (no rate limit):** `https://github.com/Shamlawy/Play-next/releases/latest/download/<asset>` (302 to `release-assets.githubusercontent.com` / `objects.githubusercontent.com`). With a fixed-name small text asset, e.g. `android-latest.txt` = `versionCode sha256 size tag`, the app can check without touching the API. Mark the emulator-verified release as "latest" only when it's promoted (see below).
- **Rate limits (unauthenticated):** 60 requests/hour **per IP** (`x-ratelimit-limit/remaining/reset` headers; 403/429 when over). Conditional requests with `If-None-Match: <ETag>` that return **304 don't count**. Mobile carriers often share IPs (CGNAT), so use ETags, check at start, on return after 10+ min and every few hours, back off on 403/429, and fall back to the `latest/download` text asset. Downloading assets from `browser_download_url` doesn't count against the API limit. The CDN supports `Range` for resuming (verify in Phase 0 on the phone).
- **Asset naming:** `play-next-android-v{N}.apk`, `play-next-android-v{N}.apk.sha256`, `android-latest.txt` (fixed name; `.txt`, not `.json`, since nothing is committed but it matches the house aversion), plus the R8 `mapping-v{N}.txt` **as a CI artifact, not a release asset** if it should stay private (mapping files leak class names only, so they're fine either way in a public repo, but keep them out of the phone's download). The release `body` = the first what's-new line (the "updated" notification text, plan L104).
- **Tag / versionCode:** tag `android-v{versionCode}`, versionCode = `github.run_number` of the release workflow. Caveats:
  - `run_number` is per workflow file. **Renaming or recreating the workflow resets it to 1**, and Android refuses a lower versionCode. A re-run keeps the same `run_number` (only `run_attempt` rises), so the tag already exists.
  - A separate `rollback-android.yml` has its **own** counter, starting at 1, lower than the live release.
  - **Fix:** versionCode = `max(github.run_number + OFFSET, latestTag + 1)`, computed in the workflow from the releases API, and the release step refuses if the tag exists or the code isn't higher. Run rollback as a `workflow_dispatch` input (`ref`) of the same release workflow, or with the same max() rule.
- **"A release reaches the updater only after the emulator opened every screen":** create the release as **draft** (invisible to the unauthenticated app), run the emulator smoke on the exact signed APK, then publish it (`gh release edit --draft=false --latest`). Needs `permissions: contents: write` in that workflow only.
- **At most one a day unless it fixes a crash:** needs a machine-readable marker (PR label `crash-fix`, or a `[crash]` token in the merge commit). The workflow checks the last `android-v*` `published_at` and either holds the release (next day's scheduled run releases main's head) or goes now.
- **Emulator on GitHub runners:** ubuntu-latest has KVM (enable with the udev rule step). dl.google.com is reachable from GitHub runners. This cloud container can't reach it until the owner allows the domain (plan L210).

---

## 5. .gitignore and android/

Current `.gitignore` (21 lines): `*.json` (L2) with exceptions only for mod manifests, the marketplace and `.claude/settings.json` (L6-12), plus `webkit/`, `tools/.cache/`, `.claude/worktrees/`, `node_modules/`. Nothing Android-specific.

**JSON files an Android project may want, and what to do:**
| File | Advice |
|---|---|
| `google-services.json` (Firebase, Phase 9) | **Keep ignored.** Generate it in CI from a secret (e.g. `GOOGLE_SERVICES_JSON` base64) or skip the Gradle plugin and set Firebase options in code from BuildConfig fields fed by secrets. Its API key isn't truly secret, but the house rule is no `.json` committed. |
| Room schema exports `android/app/schemas/**/*.json` | Only if Room is used. Allow `!android/app/schemas/**/*.json` (no user data, but review). Otherwise turn off `exportSchema`. |
| Test fixtures (seed backup, golden numbers) | The plan says the golden file is made in CI, never committed. Make the seed backup in CI from `tools/seed.mjs` too, so nothing needs allowing. If a fixture must be committed, allow one exact path (`!android/app/src/test/resources/fixtures/*.json`, made-up data only) and extend release-guard's `MANIFEST`. |
| `res/raw/*.json` (Lottie and the like) | Avoid. Generated content from index.html goes to `build/generated` (plan gate 7), never committed. |
| Baseline profile / startup profile | `baseline-prof.txt`: not JSON, fine. |
| Renovate, `.idea/*.json` | Don't commit. |

**Add to `.gitignore`** (Phase 0): `android/**/build/`, `android/.gradle/`, `android/.kotlin/`, `android/local.properties`, `android/.idea/`, `*.iml`, `android/captures/`, `*.jks`, `*.keystore`, `*.p12`, `key.properties`, `android/app/release/`, `*.apk`, `*.aab`. **Keep committed:** `android/gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties` (not ignored by anything today; verify the jar's checksum in CI with `gradle/actions/wrapper-validation` pinned by SHA). Any `!` exception added to `.gitignore` must be mirrored in release-guard `MANIFEST` (rules.ts L128), or every commit with it is denied. Blast-radius protects only repo-root files (`*`), so `android/` is a directory and unaffected.

---

## Phase 0 CI/tooling checklist

- [ ] `.gitignore`: Android build, IDE and key ignores (5). Keep `*.json` blocked. Allow-list only what's truly needed, mirrored in release-guard `MANIFEST`.
- [ ] `android-check.yml`: `pull_request` on `android/**` + itself (+ `.github/scripts/bugsync.mjs`, `tools/bugsync-check.mjs`), `permissions: contents: read`, third-party actions SHA-pinned, wrapper validation, unit tests (golden numbers with a fixed clock), lint + the house-rule lint checks, screenshot tests at the folded/unfolded widths and font scales 1.3/2.0, Compose stability report, app-size budget, 16 KB page check, Baseline Profile present, an emulator journey + Macrobenchmark against main (only a clear slowdown fails), and a live-helper call with `Origin` that fails on a refusal. Throwaway signing key. **No secrets on PRs.**
- [ ] `android-signing-key.yml` (one-time, dispatch, confirm input, exact-title namespaces `playnext-android-signing` + `-copy`, refuses if a key exists, read-back compare, `::add-mask::`, `--remote`, values via `--path`/stdin, no artifact, no checkout). Run once and record the cert SHA-256 in the plan.
- [ ] `android-release.yml`: push to main on `android/**` + dispatch (`ref` input for rollback), `concurrency: android-release`, `contents: write`, versionCode = max(run_number+offset, latest tag+1), key fetched only on main, R8 release + Baseline Profile, signed APK, `.sha256`, `android-latest.txt`, a **draft** release, the emulator smoke on that APK, then publish as latest. One a day unless crash-marked. Mapping kept as an artifact.
- [ ] Rollback: the same workflow's dispatch with an older `ref` and a higher versionCode (not a separate workflow with its own counter).
- [ ] bugsync.mjs: `android-bug` label, (app, sig) routing, families and fold per app, android version from `android-v*` releases (+ `ANDROID_V` env, `RELEASES_FILE` fixture), "Fixed in android-vN", proof counting only android samples, kind `ran` heartbeat → no issue, `(App)` titles, no Safari hint.
- [ ] Helper marker decision: a one-line `app` field kept in `bugTake` (amend plan L49), or the `dev` "App " prefix. Prefixed sigs in the app either way.
- [ ] `tools/bugsync-check.mjs`: run 3 with the android cases (2.3). Make CI run bugsync-check.
- [ ] bug-inbox: also read `android-bug` (separate count). The learn mod's "came back" search too (optional). Bump both mods' `plugin.json` versions and run `claude plugin test`.
- [ ] release-guard: `androidShipped` → LOG line; android-check success on the head sha (API lookup in register.ts); an `android/version.properties` release counter going up and above main's; an android what's-new entry for it; keystores / `google-services.json` / `local.properties` always blocked; a Stop hook for android edits without a gradle test; a release watch; tests in `guard.test.ts`; version bump; note that `gh pr merge` isn't hooked.
- [ ] `.claude/second-look.md`: Android prose, `first:` and `skip:` lines (3.2).
- [ ] play-next-design SKILL.md description + an Android section; CLAUDE.md and the plan in the same change (no contradicting older rule).
- [ ] CLAUDE.md Automation: android-bug is worked by build sessions; "Fixed in android-vN"; the new workflows listed in "Workflows".
- [ ] Owner step already in the plan: allow `dl.google.com` for in-chat Gradle (otherwise CI-only).

## Risks / unknowns

1. **`app` field is dropped by the helper** (`bugTake`, helper.js L1287-1300). Without a helper change or a `dev` marker, bugsync can't tell app from web. The owner's web Chrome also reports as "Android".
2. **Android proof has no "ran this version" source.** `/usage` is a writing route banned until switch day. Without a `ran` heartbeat through `/bug`, android stutters stay pending-proof forever.
3. **The versionCode counter can go backwards or collide** (workflow rename, a separate rollback workflow, re-runs). It needs the max(run_number, latest+1) rule and a refusal check. A wrong versionCode means the phone can't update (or needs a reinstall, risking data if the backup isn't current).
4. **The signing key is unrecoverable if both KV copies are lost** (namespace deleted, token rotated to another account, Cloudflare account lost). Two namespaces in the same account are one failure domain. Consider a third copy, e.g. encrypted with a key derived from a second secret, kept as a long-retention release asset? (That's a public repo, so only with strong encryption.) The owner must decide.
5. **Wrangler 4 local-by-default KV key commands**: forgetting `--remote` silently writes to the runner's local Miniflare store, the workflow "succeeds", and the key is lost when the runner ends. The read-back compare must run with `--remote` too.
6. **Default `GITHUB_TOKEN` permissions** for workflows without a `permissions:` block (announce, deploy-helper) are unknown (a repo setting not readable through the proxy). New workflows must set them explicitly.
7. **Unauthenticated API limit** (60/hr/IP, CGNAT on mobile). Mitigated by ETag/304 and the `releases/latest/download` text asset. `releases/latest` breaks if any non-android release is ever made.
8. **Silent self-install:** whether Android 12+ `USER_ACTION_NOT_REQUIRED` applies depends on Chrome being the installer of record for the first install (it won't be, so the first self-update asks). Phase 0 proves it on the phone (plan L42, L103).
9. **Asset `digest` field** availability and CDN `Range` support should be verified on the first release. Keep the `.sha256` asset as the source of truth.
10. **second-look diff cap** (160k chars): Phase 0's scaffold PR will be cut, so the reviewer sees part of it. Split the PR or rely on `first:`.
11. **release-guard doesn't see `gh pr merge`** (the PC's merge path, CLAUDE.md "The owner's Windows PC"), so Android rules won't hold there either unless that's added.
12. **The "one release a day unless a crash fix" signal** isn't defined yet (label vs commit token).
13. **Where the Android what's-new lives**, and which "N" it keys on (the release counter vs versionCode), is undecided in the plan. The release-guard rule depends on it.
14. **Daily fixer / light mode** text names only `auto-bug`. With `android-bug` as a separate label it's untouched, but bug-inbox won't show android reports until it's updated.
