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

const MANIFEST = /^\.claude\/skills\/[^/]+\/(\.claude-plugin\/plugin|hooks\/hooks)\.json$/

// Files that must never be committed or deleted, whatever the release.
export function fileProblems(changes: readonly Change[], deleted: readonly string[] = []): string[] {
  const out: string[] = []
  for (const c of changes) {
    if (c.status !== 'D' && /\.json$/i.test(c.path) && !MANIFEST.test(c.path))
      out.push(`${c.path}: .json files are never committed (backups hold private data)`)
  }
  for (const p of deleted) out.push(`${p}: files in the repo root are never deleted (saved games can link to them)`)
  return out
}

export type Range = {
  changes: Change[]
  baseHtml?: string
  headHtml?: string
  logAdded: string
  baseHelper?: string
  headHelper?: string
  helperCheck?: string // why the helper failed node --check / import, if it did
}

// What a branch brings to main: what blocks a merge, and what is worth a look.
export function releaseProblems(r: Range): { block: string[]; notes: string[] } {
  const block: string[] = []
  const notes: string[] = []
  const has = (p: string) => r.changes.some(c => c.path === p)
  block.push(...fileProblems(r.changes, rootDeleted(r.changes)))

  const shipped = r.changes.some(c => c.path === 'index.html' || c.path === 'sw.js' || c.path === 'manifest.webmanifest' || c.path.startsWith('worker/'))
  if (shipped && !r.logAdded.trim()) block.push('docs/LOG.md has no new line: every release and no-version fix gets one')

  if (has('index.html') && r.headHtml) {
    const base = appV(r.baseHtml)
    const head = appV(r.headHtml)
    if (!head) block.push('index.html: APP_V not found')
    else if (base && head === base) {
      notes.push(`APP_V is still ${head}: right only for a detector-only fix. Anything the owner can see needs a bump (it triggers the update popup and the auto-reload) and a WHATS_NEW entry.`)
    } else if (base && Number(head) <= Number(base)) {
      block.push(`APP_V went from ${base} to ${head}: it must go up`)
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
