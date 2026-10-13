// Blast radius (pure): which parts of a shell command can destroy work, and how to read what they would take.
// register.ts measures each risk with git/find and holds the command once with the list.

export type Risk =
  | { kind: 'rm'; cwd: string; targets: string[] }
  | { kind: 'find'; cwd: string; args: string[] }
  | { kind: 'reset'; cwd: string; rev: string }
  | { kind: 'clean'; cwd: string; flags: string }
  | { kind: 'discard'; cwd: string; paths: string[] }
  | { kind: 'force'; cwd: string; src: string; dst: string }
  | { kind: 'branch'; cwd: string; name: string }

// Shell words, quotes removed; enough for the commands Claude writes (no $(…) or backticks inside the risky parts).
export function words(s: string): string[] {
  const out: string[] = []
  let cur = ''
  let q = ''
  let any = false
  for (const ch of s) {
    if (q) {
      if (ch === q) q = ''
      else cur += ch
    } else if (ch === '"' || ch === "'") {
      q = ch
      any = true
    } else if (/\s/.test(ch)) {
      if (cur || any) out.push(cur)
      cur = ''
      any = false
    } else cur += ch
  }
  if (cur || any) out.push(cur)
  return out
}

// Split on && || ; | and newlines, outside quotes.
export function segments(cmd: string): string[] {
  const out: string[] = []
  let cur = ''
  let q = ''
  for (let i = 0; i < cmd.length; i++) {
    const ch = cmd[i] ?? ''
    if (q) {
      if (ch === q) q = ''
      cur += ch
      continue
    }
    if (ch === '"' || ch === "'") {
      q = ch
      cur += ch
      continue
    }
    if (ch === ';' || ch === '\n' || ch === '|' || (ch === '&' && cmd[i + 1] === '&')) {
      out.push(cur)
      cur = ''
      if (cmd[i + 1] === ch) i++
      continue
    }
    cur += ch
  }
  out.push(cur)
  return out.map(s => s.trim()).filter(Boolean)
}

export function join(base: string, p: string, home = '/root'): string {
  const raw = p === '~' || p.startsWith('~/') ? home + p.slice(1) : p
  const abs = raw.startsWith('/') ? raw : `${base.replace(/\/+$/, '')}/${raw}`
  const parts: string[] = []
  for (const part of abs.split('/')) {
    if (!part || part === '.') continue
    if (part === '..') parts.pop()
    else parts.push(part)
  }
  return '/' + parts.join('/')
}

const ENV = /^[A-Za-z_][A-Za-z0-9_]*=/

// Every destructive part of `cmd`, run from `cwd`.
export function risks(cmd: string, cwd: string, home = '/root'): Risk[] {
  const out: Risk[] = []
  let dir = cwd
  for (const seg of segments(cmd)) {
    let w = words(seg)
    while (w.length && (ENV.test(w[0] ?? '') || w[0] === 'sudo' || w[0] === 'command' || w[0] === 'time')) w = w.slice(1)
    const [head, ...rest] = w
    if (!head) continue
    if (head === 'cd') {
      const to = rest[0]
      dir = to && to !== '-' ? join(dir, to, home) : home
      continue
    }
    if (head === 'rm' || head.endsWith('/rm')) {
      let flagsDone = false
      const targets: string[] = []
      for (const a of rest) {
        if (!flagsDone && a === '--') flagsDone = true
        else if (!flagsDone && a.startsWith('-')) continue
        else targets.push(a)
      }
      if (targets.length) out.push({ kind: 'rm', cwd: dir, targets })
      continue
    }
    if (head === 'find' && rest.includes('-delete')) {
      out.push({ kind: 'find', cwd: dir, args: rest.filter(a => a !== '-delete') })
      continue
    }
    if (head !== 'git') continue
    let g = rest
    let gdir = dir
    while (g[0] === '-C' || g[0] === '-c') {
      if (g[0] === '-C' && g[1]) gdir = join(dir, g[1], home)
      g = g.slice(2)
    }
    const [sub, ...args] = g
    const plain = args.filter(a => !a.startsWith('-'))
    if (sub === 'reset' && args.includes('--hard')) out.push({ kind: 'reset', cwd: gdir, rev: plain[0] ?? 'HEAD' })
    else if (sub === 'clean' && args.some(a => /^-[a-zA-Z]*f/.test(a) || a === '--force')) {
      const flags = args.filter(a => a.startsWith('-') && !a.startsWith('--')).join('').replace(/[-fqi]/g, '')
      out.push({ kind: 'clean', cwd: gdir, flags })
    } else if (sub === 'checkout' && (args.includes('--') || plain.some(a => a === '.' || a.startsWith('./')))) {
      const at = args.indexOf('--')
      const paths = at >= 0 ? args.slice(at + 1) : plain.filter(a => a === '.' || a.startsWith('./'))
      if (paths.length) out.push({ kind: 'discard', cwd: gdir, paths })
    } else if (sub === 'restore' && !(args.includes('--staged') && !args.includes('--worktree') && !args.includes('-W'))) {
      const at = args.indexOf('--')
      const paths = at >= 0 ? args.slice(at + 1) : plain
      if (paths.length) out.push({ kind: 'discard', cwd: gdir, paths })
    } else if (sub === 'push' && !args.includes('--dry-run') && !args.includes('-n')) {
      const forced = args.some(a => a === '-f' || a === '--force' || a.startsWith('--force-with-lease') || a.startsWith('--force-if-includes'))
      const refs = plain.slice(1)
      const plus = refs.some(r => r.startsWith('+'))
      if (!forced && !plus) continue
      const ref = (refs[refs.length - 1] ?? '').replace(/^\+/, '')
      const [src = ref, dst = src] = ref.includes(':') ? ref.split(':') : [ref, ref]
      out.push({ kind: 'force', cwd: gdir, src: src || 'HEAD', dst: dst.replace(/^refs\/heads\//, '') })
    } else if (sub === 'branch' && (args.includes('-D') || (args.includes('-d') && args.includes('--force')))) {
      for (const name of plain) out.push({ kind: 'branch', cwd: gdir, name })
    }
  }
  return out
}

// Which deleted files matter. `repo` = repo root; `kept` = tracked + untracked-not-ignored paths (repo-relative); `tracked` ⊂ kept.
export type Sorted = { tracked: string[]; fresh: string[]; ignored: string[]; outside: string[]; rootTracked: string[]; whole: boolean }

const SAFE_OUTSIDE = [/^\/tmp(\/|$)/, /^\/var\/tmp(\/|$)/, /\/scratchpad(\/|$)/, /\/node_modules(\/|$)/]
// Ignored files in the repo that are only caches/build output; everything else ignored (a *.json backup!) counts.
const SAFE_IGNORED = [/(^|\/)node_modules\//, /^tools\/\.cache\//, /^webkit\//, /^webkit-report\.txt$/, /(^|\/)test-results\//, /(^|\/)playwright-report\//]

export function sortFiles(abs: readonly string[], repo: string, tracked: ReadonlySet<string>, kept: ReadonlySet<string>): Sorted {
  const s: Sorted = { tracked: [], fresh: [], ignored: [], outside: [], rootTracked: [], whole: false }
  const pre = repo.replace(/\/+$/, '') + '/'
  for (const a of abs) {
    if (a === repo || a === '/' || a === '/root' || a === '/home' || pre.startsWith(a + '/')) {
      s.whole = true
      continue
    }
    if (!a.startsWith(pre)) {
      if (!SAFE_OUTSIDE.some(r => r.test(a))) s.outside.push(a)
      continue
    }
    const rel = a.slice(pre.length)
    if (rel === '.git' || rel.startsWith('.git/')) {
      s.whole = true
      continue
    }
    if (tracked.has(rel)) {
      s.tracked.push(rel)
      if (!rel.includes('/')) s.rootTracked.push(rel)
    } else if (kept.has(rel)) s.fresh.push(rel)
    else if (!SAFE_IGNORED.some(r => r.test(rel))) s.ignored.push(rel)
  }
  return s
}

export function list(xs: readonly string[], max = 12): string {
  const shown = xs.slice(0, max).map(x => `  - ${x}`)
  if (xs.length > max) shown.push(`  - …and ${xs.length - max} more`)
  return shown.join('\n')
}
