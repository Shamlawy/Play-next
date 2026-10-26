// Pure checks (no `$`): the house rules from CLAUDE.md, the release rules, and the LOG lookup.
// register.ts feeds them text it got from git or the edit; tests call them directly.

export type Finding = { rule: string; text: string }

// Transitions may move only these (CLAUDE.md "Speed rule").
const CHEAP = new Set(['transform', 'opacity', 'translate', 'scale', 'rotate', 'none'])

// A hex colour that is clearly a colour (not grey / near-black): new accents use var(--volt) (v181).
function isAccentHex(hex: string): boolean {
  const h = hex.length <= 4 ? hex.slice(1, 4).split('').map(c => c + c).join('') : hex.slice(1, 7)
  if (h.length !== 6) return false
  const rgb = [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16))
  return Math.max(...rgb) - Math.min(...rgb) > 64
}

function hexes(line: string): string[] {
  // Fallbacks inside var(--x, #hex) are fine; so are id selectors (#s-theme) and JS palettes.
  const css = line.replace(/var\([^()]*\)/g, '')
  const out: string[] = []
  const re = /(?:color|background(?:-color)?|border(?:-[a-z]+)*|fill|stroke|outline(?:-color)?|box-shadow|text-shadow|accent-color|caret-color)\s*:[^;{}]*?(#[0-9a-fA-F]{3,8})\b/g
  for (let m = re.exec(css); m; m = re.exec(css)) if (m[1] && isAccentHex(m[1])) out.push(m[1])
  return out
}

function badTransition(line: string): string[] {
  const out: string[] = []
  const re = /transition(?:-property)?\s*:\s*([^;}"'`]+)/g
  for (let m = re.exec(line); m; m = re.exec(line)) {
    for (const part of (m[1] ?? '').split(',')) {
      const prop = part.trim().split(/\s+/)[0]?.toLowerCase() ?? ''
      if (prop && !/^[0-9.]/.test(prop) && !CHEAP.has(prop) && !prop.startsWith('var(')) out.push(prop)
    }
  }
  return out
}

// Rules on single added lines of index.html.
export function scanLines(lines: readonly string[]): Finding[] {
  const found: Finding[] = []
  const seen = new Set<string>()
  const add = (rule: string, text: string) => {
    if (seen.has(rule + text)) return
    seen.add(rule + text)
    found.push({ rule, text })
  }
  const colours: string[] = []
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    colours.push(...hexes(line))
    for (const p of badTransition(line)) add('speed', `transition on "${p}": animate only transform and opacity (Speed rule)`)
    if (/:active\b[^{]*\{[^}]*\b(transform|scale|translate|rotate|filter|opacity)\s*:/.test(line))
      add('press', 'a press effect on :active: no press animation, nothing shrinks, lifts or fades when touched (#309, v246)')
    if (/<!doctype/i.test(line)) add('doctype', 'a doctype: the page runs in quirks mode on purpose; adding one needs a full layout sweep (v219)')
    if (/type\s*=\s*\\?["']?(date|datetime-local|month|week|time)\b|\.type\s*=\s*["'](date|datetime-local|month|week|time)["']/.test(line))
      add('date', 'a native date/time input: the owner could not use it on the Fold, use a picker like finPick() (v180)')
    if (/font-style\s*:\s*italic|text-transform\s*:\s*uppercase/.test(line))
      add('heading', 'italic / uppercase text: new UI uses upright sentence case (v188)')
    if (/\bzIndex\b|z-index/.test(line) && /\.animate\(|@keyframes|keyframes/.test(line))
      add('zindex', 'z-index inside an animation: no zIndex in keyframes (v168)')
    if (/fill\s*:\s*["']forwards["']/.test(line))
      add('forwards', 'fill: "forwards": cancel this animation on every path that ends it, or it can leave an invisible layer eating taps (v215)')
    if (/animation[^;]*\binfinite\b/.test(line) && /blur|backdrop-filter|filter\s*:/.test(line))
      add('blur', 'a looping blur/filter: no looping blur (Speed rule)')
    if (/(?:^|[^\w$.])(?:MutationObserver)\b/.test(line) && /debounce|setTimeout/.test(line))
      add('idle', 'idle work waiting on DOM changes: filter the mutations or cap the wait (v202)')
  }
  // Scene and status colours (a sky, a green tick) are fine as hex; the theme accent never is.
  if (colours.length) found.unshift({ rule: 'colour', text: `coloured hex (${[...new Set(colours)].slice(0, 4).join(', ')}): fine for scene or status colours, but anything in the theme accent must be var(--volt) / rgba(var(--vrgb), a) so themes recolour it (v181)` })
  return found
}

// Rules that need the whole new block, not one line.
export function scanBlock(text: string): Finding[] {
  const found: Finding[] = []
  if (/\.innerHTML\s*\+?=/.test(text) && /getBoundingClientRect|offset(?:Width|Height|Top|Left)\b|scroll(?:Top|Height|Width)\b|client(?:Height|Width)\b|getComputedStyle/.test(text) && !/requestAnimationFrame|ResizeObserver|IntersectionObserver/.test(text))
    found.push({ rule: 'layout', text: 'reads layout in the same block as an innerHTML redraw: do the read in requestAnimationFrame or an observer (v206)' })
  return found
}

export const appV = (html: string | undefined) => html?.match(/^\s*const APP_V = "(\d+)"/m)?.[1]
export const helperV = (js: string | undefined) => js?.match(/const HELPER_V = (\d+)/)?.[1]

// Every WHATS_NEW entry for version `v` and what each is missing (go, at, say, tryIt).
export function whatsNew(html: string, v: string): { count: number; missing: string[] } {
  const re = new RegExp(`\\{\\s*v:\\s*["']?${v}["']?\\s*,`, 'g')
  const missing: string[] = []
  let count = 0
  for (let m = re.exec(html); m; m = re.exec(html)) {
    count++
    const rest = html.slice(m.index + 1)
    const end = rest.search(/\{\s*v:\s*["']?\d+["']?\s*,/)
    const entry = rest.slice(0, end < 0 ? 4000 : Math.min(end, 4000))
    const lack = ['go', 'at', 'say', 'tryIt'].filter(k => !new RegExp(`\\b${k}\\s*:`).test(entry))
    if (lack.length) missing.push(`entry ${count} lacks ${lack.join(', ')}`)
  }
  return { count, missing }
}

export type Change = { status: string; path: string }

// `git diff --name-status` output; a rename is the old path gone plus the new one added.
export function parseNameStatus(out: string): Change[] {
  return out.split('\n').filter(Boolean).flatMap(l => {
    const [st = '', a = '', b] = l.split('\t')
    const s = st.charAt(0)
    if ((s === 'R' || s === 'C') && b) return s === 'R' ? [{ status: 'D', path: a }, { status: 'A', path: b }] : [{ status: 'A', path: b }]
    return [{ status: s, path: a }]
  })
}

// `git status --porcelain` output, for a commit that stages as it goes (`git add -A && git commit`).
export function parsePorcelain(out: string): Change[] {
  return out.split('\n').filter(Boolean).flatMap(l => {
    const xy = l.slice(0, 2)
    const path = l.slice(3)
    if (xy.includes('R')) {
      const [a = '', b = ''] = path.split(' -> ')
      return [{ status: 'D', path: a }, { status: 'A', path: b }]
    }
    return [{ status: xy.includes('D') ? 'D' : xy === '??' ? 'A' : 'M', path }]
  })
}

export const rootDeleted = (changes: readonly Change[]) => changes.filter(c => c.status === 'D' && !c.path.includes('/')).map(c => c.path)

const MANIFEST = /^(?:(?:\.claude\/skills|mods)\/[^/]+\/(?:\.claude-plugin\/plugin|hooks\/hooks)|\.claude-plugin\/marketplace|\.claude\/settings)\.json$/
// The Android app's signing key and machine-local files, anywhere in the repo: never committed.
export const SECRET_FILE = /(?:^|\/)(?:[^/]+\.(?:jks|keystore|p12)|key\.properties|local\.properties|google-services\.json)$/i

// Files that must never be committed or deleted, whatever the release.
export function fileProblems(changes: readonly Change[], deleted: readonly string[] = []): string[] {
  const out: string[] = []
  for (const c of changes) {
    if (c.status === 'D') continue
    if (SECRET_FILE.test(c.path))
      out.push(`${c.path}: keystores, key.properties, local.properties and google-services.json are never committed (the signing key lives only in Cloudflare KV; the rest is machine-local or made in CI)`)
    else if (/\.json$/i.test(c.path) && !MANIFEST.test(c.path))
      out.push(`${c.path}: .json files are never committed (backups hold private data)`)
  }
  for (const p of deleted) out.push(`${p}: files in the repo root are never deleted (saved games can link to them)`)
  return out
}

// ---- The Android app (android/, Kotlin + Compose) ----
// android/parity/ is documentation; everything else under android/ is the app and ships through android-release.yml.
export const ANDROID_VERSION = 'android/version.properties'
export const ANDROID_WHATSNEW = 'android/app/src/main/assets/whatsnew.txt'
export const isAndroidShip = (path: string) => path.startsWith('android/') && !path.startsWith('android/parity/')
export const isWebShip = (path: string) => path === 'index.html' || path === 'sw.js' || path === 'manifest.webmanifest' || path.startsWith('worker/')

// `release=N` in android/version.properties: the release counter (a merge that raises it is a release).
export const androidRelease = (props: string | undefined) => props?.match(/^[ \t]*release[ \t]*=[ \t]*(\d+)[ \t]*\r?$/m)?.[1]

// whatsnew.txt: `#` comment lines, blocks split by blank lines, `key=value` lines (v, go, at, say, tryIt).
export function androidWhatsNewBlocks(text: string): Record<string, string>[] {
  const lines = text.split(/\r?\n/).filter(l => !/^\s*#/.test(l))
  const blocks: Record<string, string>[] = []
  let cur: Record<string, string> | undefined
  for (const l of lines) {
    if (!l.trim()) {
      cur = undefined
      continue
    }
    const at = l.indexOf('=')
    if (at < 0) continue
    if (!cur) blocks.push((cur = {}))
    cur[l.slice(0, at).trim()] = l.slice(at + 1).trim()
  }
  return blocks
}

// Every what's-new block for release `n` and what each is missing (go, say and tryIt must say something; at may be empty).
export function androidWhatsNew(text: string, n: string): { count: number; missing: string[] } {
  const of = androidWhatsNewBlocks(text).filter(b => b.v === n)
  const missing: string[] = []
  of.forEach((b, i) => {
    const lack = ['go', 'say', 'tryIt'].filter(k => !b[k])
    if (lack.length) missing.push(`block ${i + 1} lacks ${lack.join(', ')}`)
  })
  return { count: of.length, missing }
}

// android-check.yml on one commit, as GitHub's runs API reports it.
export type CheckRun = { state: 'success' | 'running' | 'failed' | 'missing' | 'unknown'; conclusion?: string; url?: string }
type ApiRun = { status?: string; conclusion?: string | null; head_sha?: string; created_at?: string; html_url?: string }

// The newest run decides (a re-run replaces an older failure).
export function checkState(runs: readonly ApiRun[], sha?: string): CheckRun {
  const of = runs.filter(r => !sha || !r.head_sha || r.head_sha === sha).sort((a, b) => Date.parse(b.created_at ?? '') - Date.parse(a.created_at ?? ''))
  const r = of[0]
  if (!r) return { state: 'missing' }
  if (r.status !== 'completed') return { state: 'running', url: r.html_url }
  return r.conclusion === 'success' ? { state: 'success', url: r.html_url } : { state: 'failed', conclusion: r.conclusion ?? 'failed', url: r.html_url }
}

// What a range does to the Android app: shipped at all, the release it makes (when release=N went up), else the unchanged N.
export function androidStep(r: Pick<Range, 'changes' | 'baseAndroid' | 'headAndroid'>): { shipped: boolean; release?: string; same?: string } {
  const shipped = r.changes.some(c => isAndroidShip(c.path))
  if (!shipped) return { shipped }
  const head = androidRelease(r.headAndroid)
  const base = androidRelease(r.baseAndroid)
  if (head && base && Number(head) <= Number(base)) return { shipped, same: head === base ? head : undefined }
  return head ? { shipped, release: head } : { shipped }
}

// `gh pr merge …` in a shell command (Bash or PowerShell): which PR it merges. undefined = not a gh merge.
export type GhMerge = { arg?: string; number?: number; owner?: string; name?: string; repo?: string }
const VALUE_FLAGS = new Set(['-t', '--subject', '-b', '--body', '-F', '--body-file', '-R', '--repo', '--match-head-commit', '-A', '--author-email'])
export function ghMerge(cmd: string): GhMerge | undefined {
  // Quoted text (a subject, a commit message that mentions gh) is never the PR.
  const bare = cmd.replace(/"(?:[^"\\]|\\.)*"|'[^']*'/g, '""')
  const m = bare.match(/(?:^|[\s;&|(])gh(?:\.exe)?\s+pr\s+merge\b([^&;|\n]*)/)
  if (!m) return undefined
  const toks = (m[1] ?? '').trim().split(/\s+/).filter(t => t && !/^\d*[<>]/.test(t))
  const out: GhMerge = {}
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i] ?? ''
    if (VALUE_FLAGS.has(t)) {
      if (t === '-R' || t === '--repo') out.repo = toks[i + 1]
      i++
      continue
    }
    if (t.startsWith('--repo=')) out.repo = t.slice(7)
    if (t.startsWith('-')) continue
    if (out.arg === undefined) out.arg = t
  }
  const a = out.arg
  const url = a?.match(/github\.com\/([\w.-]+)\/([\w.-]+)\/pull\/(\d+)/)
  if (url) Object.assign(out, { owner: url[1], name: url[2], number: Number(url[3]) })
  else if (a && /^#?\d+$/.test(a)) out.number = Number(a.replace('#', ''))
  if (out.repo) {
    const parts = out.repo.replace(/^https?:\/\//, '').split('/').filter(Boolean)
    if (parts.length >= 2) {
      out.owner = parts[parts.length - 2]
      out.name = parts[parts.length - 1]
    }
  }
  return out
}

// A file path from the Edit/Write tools, relative to the repo root with forward slashes ('' outside the repo).
// Windows gives C:\Users\…, Git Bash /c/Users/…, git itself C:/Users/….
export function repoPath(file: string, root: string): string {
  const norm = (p: string) => String(p ?? '').replace(/\\/g, '/').replace(/^\/([a-zA-Z])\//, '$1:/').replace(/\/+$/, '')
  const f = norm(file)
  const r = norm(root)
  if (r && f.toLowerCase().startsWith(`${r.toLowerCase()}/`)) return f.slice(r.length + 1)
  if (/^(?:[a-zA-Z]:)?\//.test(f)) return ''
  return f.replace(/^\.\//, '')
}

export type Range = {
  changes: Change[]
  baseHtml?: string
  headHtml?: string
  mainHtml?: string // origin/main's index.html now: main can ship a release while the branch is being built
  logAdded: string
  baseHelper?: string
  headHelper?: string
  helperCheck?: string // why the helper failed node --check / import, if it did
  // The Android app, read only when the range touches android/ (outside android/parity/).
  baseAndroid?: string // android/version.properties at the base
  headAndroid?: string
  mainAndroid?: string // origin/main's now: main can make a release while the branch is being built
  androidWhatsNew?: string // whatsnew.txt at the head
  androidCheck?: CheckRun // android-check.yml on the head commit
}

// The android-check items that only mean "CI hasn't finished yet" (a branch push hears nothing about them).
export const isCheckWait = (text: string) => /^android-check (?:hasn't run|is still running|couldn't be read)/.test(text)

// The Android half of a release: release=N up, above main's, a what's-new block, an `android-vN` LOG line, android-check green.
function androidProblems(r: Range, block: string[], notes: string[]) {
  const step = androidStep(r)
  if (!step.shipped) return
  const head = androidRelease(r.headAndroid)
  const base = androidRelease(r.baseAndroid)
  const main = androidRelease(r.mainAndroid)
  const ck = r.androidCheck ?? { state: 'unknown' as const }
  if (!head) {
    block.push(`${ANDROID_VERSION} has no "release=N" line: the Android app's release counter must exist (it keys the what's-new and the LOG line)`)
    return
  }
  if (!step.release) {
    if (base && Number(head) < Number(base)) block.push(`${ANDROID_VERSION}: release went from ${base} to ${head}: it never goes down`)
    else notes.push(`no Android release (release=${head} unchanged): a build-only change, nothing new reaches the phone. Raise release= in ${ANDROID_VERSION} (with a what's-new block and an android-v${Number(head) + 1} LOG line) if the owner should see it.${ck.state === 'success' ? '' : ` android-check on this commit: ${ck.state === 'failed' ? ck.conclusion : ck.state}.`}`)
    return
  }
  if (main && Number(head) <= Number(main)) {
    block.push(`main already made android-v${main} while this branch was built (it has release=${head}): merge origin/main, raise release= above ${main} and move the what's-new block and the LOG line to the new number`)
    return
  }
  if (r.androidWhatsNew === undefined) block.push(`${ANDROID_WHATSNEW} is missing: the Android what's-new lives there`)
  else {
    const wn = androidWhatsNew(r.androidWhatsNew, head)
    if (!wn.count) block.push(`whatsnew.txt has no block with v=${head}: every Android release gets one (go, say, tryIt; at may be empty) or the tour skips it`)
    for (const m of wn.missing) block.push(`whatsnew.txt v=${head} ${m}`)
  }
  if (!new RegExp(`\\bandroid-v${head}\\b`).test(r.logAdded)) block.push(`docs/LOG.md has no "android-v${head}" line`)
  if (ck.state === 'missing') block.push("android-check hasn't run on this commit yet: wait for android-check, then merge (it runs on the PR, so open one if there isn't)")
  else if (ck.state === 'running') block.push(`android-check is still running on this commit: wait for android-check, then merge${ck.url ? ` (${ck.url})` : ''}`)
  else if (ck.state === 'unknown') block.push("android-check couldn't be read from GitHub just now (network or rate limit): wait for android-check, then merge")
  else if (ck.state === 'failed') block.push(`android-check ${ck.conclusion ?? 'failed'} on this commit: fix it on the branch, push, wait for android-check, then merge${ck.url ? ` (${ck.url})` : ''}`)
}

// What a branch brings to main: what blocks a merge, and what is worth a look.
export function releaseProblems(r: Range): { block: string[]; notes: string[] } {
  const block: string[] = []
  const notes: string[] = []
  const has = (p: string) => r.changes.some(c => c.path === p)
  block.push(...fileProblems(r.changes, rootDeleted(r.changes)))
  androidProblems(r, block, notes)

  const shipped = r.changes.some(c => isWebShip(c.path))
  if (shipped && !r.logAdded.trim()) block.push('docs/LOG.md has no new line: every release and no-version fix gets one')

  if (has('index.html') && r.headHtml) {
    const base = appV(r.baseHtml)
    const head = appV(r.headHtml)
    if (!head) block.push('index.html: APP_V not found')
    else if (base && head === base) {
      notes.push(`APP_V is still ${head}: right only for a detector-only fix. Anything the owner can see needs a bump (it triggers the update popup and the auto-reload) and a WHATS_NEW entry.`)
    } else if (base && Number(head) <= Number(base)) {
      block.push(`APP_V went from ${base} to ${head}: it must go up`)
    } else if (appV(r.mainHtml) && Number(head) <= Number(appV(r.mainHtml))) {
      block.push(`main already shipped v${appV(r.mainHtml)} while this branch was built (it has v${head}): merge origin/main, bump APP_V above it and move the WHATS_NEW entry and LOG line to the new number`)
    } else {
      const wn = whatsNew(r.headHtml, head)
      if (!wn.count) block.push(`no WHATS_NEW entry with v: ${head}: every user-facing change gets one (go, at, say, tryIt) or the tour skips it`)
      for (const m of wn.missing) block.push(`WHATS_NEW v${head} ${m}`)
      if (!new RegExp(`\\bv${head}\\b`).test(r.logAdded)) block.push(`docs/LOG.md has no "v${head}" line`)
    }
  }

  if (has('worker/helper.js')) {
    if (r.helperCheck) block.push(`worker/helper.js: ${r.helperCheck}`)
    const bv = helperV(r.baseHelper)
    const hv = helperV(r.headHelper)
    if (bv && hv && bv === hv) notes.push(`HELPER_V is still ${hv}: bump it if the app now relies on the new helper code (helperCheck() reads it).`)
  }
  return { block, notes }
}

// Added / removed lines of a unified diff (-U0 is enough).
export function diffLines(diff: string): { added: string[]; removed: string[] } {
  const added: string[] = []
  const removed: string[] = []
  for (const l of diff.split('\n')) {
    if (l.startsWith('+++') || l.startsWith('---')) continue
    if (l.startsWith('+')) added.push(l.slice(1))
    else if (l.startsWith('-')) removed.push(l.slice(1))
  }
  return { added, removed }
}

// Lines of docs/LOG.md that mention code names found in `code`, newest first, once per name.
export function logLookup(code: string, log: string, told: Set<string>, max = 2): string[] {
  const names = new Set<string>()
  for (const m of code.matchAll(/\b([a-z][a-z0-9]*[A-Z][A-Za-z0-9]*|[a-z]{2,}[0-9]+[a-z0-9]*)\b/g)) if (m[1]) names.add(m[1])
  const lines = log.split('\n').filter(l => l.startsWith('- ')).reverse()
  const out: string[] = []
  for (const n of names) {
    if (told.has(n) || out.length >= max) continue
    const tick = new RegExp('`[^`]*\\b' + n.replace(/\$/g, '\\$') + '\\b[^`]*`')
    const hit = lines.find(l => tick.test(l))
    if (!hit) continue
    told.add(n)
    const at = hit.search(new RegExp('\\b' + n.replace(/\$/g, '\\$') + '\\b'))
    const head = hit.match(/^- ([^:(]{1,60})/)?.[1]?.trim() ?? 'log'
    const from = Math.max(0, at - 160)
    const snip = hit.slice(from, at + 220).replace(/\s+/g, ' ')
    out.push(`${n} (${head}): …${snip}…`)
  }
  return out
}
