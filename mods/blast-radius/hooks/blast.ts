// Blast radius (pure): which parts of a shell command can destroy work, and how to read what they would take.
// register.ts measures each risk with git/find and holds the command once with the list.
// Works in any git repo; a repo can list files that must never be deleted in .claude/blast-radius-protect.

export type Risk =
  | { kind: 'rm'; cwd: string; targets: string[] }
  | { kind: 'find'; cwd: string; args: string[] }
  | { kind: 'reset'; cwd: string; rev: string }
  | { kind: 'clean'; cwd: string; flags: string }
  | { kind: 'discard'; cwd: string; paths: string[] }
  | { kind: 'force'; cwd: string; src: string; dst: string }
  | { kind: 'branch'; cwd: string; name: string }
  // A PowerShell delete whose paths come from a pipe or a variable: nothing to measure, so it is held with what feeds it.
  | { kind: 'piped'; cwd: string; from: string }

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
  const fix = (x: string) => x.replace(/\\/g, '/').replace(/^([a-zA-Z]):/, (_, d: string) => `${d.toUpperCase()}:`)
  const h = fix(home)
  let raw = fix(p)
  if (raw === '~' || raw.startsWith('~/')) raw = h + raw.slice(1)
  else if (/^\$(env:USERPROFILE|HOME)(\/|$)/i.test(raw)) raw = h + raw.replace(/^\$(env:USERPROFILE|HOME)/i, '')
  const drive = (x: string) => /^[A-Z]:(\/|$)/.test(x)
  // Git Bash on Windows (base is a drive path): /c/Users/… and /cygdrive/c/… are C:/Users/…
  if (drive(fix(base))) raw = raw.replace(/^(?:\/cygdrive)?\/([a-zA-Z])(?=\/|$)/, (_, d: string) => `${d.toUpperCase()}:`)
  const abs = raw.startsWith('/') || drive(raw) ? raw : `${fix(base).replace(/\/+$/, '')}/${raw}`
  const pre = drive(abs) ? abs.slice(0, 2) : ''
  const parts: string[] = []
  for (const part of abs.slice(pre.length).split('/')) {
    if (!part || part === '.') continue
    if (part === '..') parts.pop()
    else parts.push(part)
  }
  return pre + '/' + parts.join('/')
}

// PowerShell's ways to delete and to change folder (names and aliases, any case).
const PS_DELETE = new Set(['remove-item', 'ri', 'rm', 'del', 'erase', 'rd', 'rmdir'])
const PS_CD = new Set(['cd', 'set-location', 'sl', 'chdir', 'pushd'])
// Remove-Item parameters that name what goes, and ones whose value only narrows it.
const PS_PATH = /^-(path|literalpath|lp|pspath)$/i
const PS_VALUE = /^-(include|exclude|filter|credential|stream)$/i

const ENV = /^[A-Za-z_][A-Za-z0-9_]*=/

// Every destructive part of `cmd`, run from `cwd`.
// `ps`: the command is PowerShell (Windows), where deleting is Remove-Item and its aliases.
export function risks(cmd: string, cwd: string, home = '/root', ps = false): Risk[] {
  const out: Risk[] = []
  let dir = cwd
  let prev = ''
  for (const seg of segments(cmd)) {
    let w = words(seg)
    while (w.length && (ENV.test(w[0] ?? '') || w[0] === 'sudo' || w[0] === 'command' || w[0] === 'time')) w = w.slice(1)
    const [first, ...rest] = w
    if (!first) continue
    const head = ps ? first.toLowerCase().replace(/\.exe$/, '') : first
    const before = prev
    prev = seg
    if (ps && PS_CD.has(head)) {
      const to = rest.find((a, i) => !a.startsWith('-') || PS_PATH.test(rest[i - 1] ?? '')) ?? ''
      dir = to && !to.startsWith('-') ? join(dir, to, home) : home
      continue
    }
    // A delete inside a script block (ForEach-Object { Remove-Item $_ }): its paths come from the pipe.
    if (ps && !PS_DELETE.has(head) && /\{[^}]*(^|[\s{;])(remove-item|ri|rm|del|erase|rd|rmdir)(\s|\}|$)/i.test(seg)) {
      out.push({ kind: 'piped', cwd: dir, from: before || seg })
      continue
    }
    if (ps && PS_DELETE.has(head)) {
      const targets: string[] = []
      for (let i = 0; i < rest.length; i++) {
        const a = rest[i] ?? ''
        // -Path:foo is the same as -Path foo
        const [name = a, glued] = a.startsWith('-') && a.includes(':') ? [a.slice(0, a.indexOf(':')), a.slice(a.indexOf(':') + 1)] : [a, undefined]
        if (PS_PATH.test(name)) targets.push(...(glued ?? rest[++i] ?? '').split(','))
        else if (PS_VALUE.test(name)) glued === undefined && i++
        else if (!a.startsWith('-')) targets.push(...a.split(','))
      }
      const real = targets.map(t => t.trim()).filter(Boolean)
      if (!real.length || real.some(t => t.startsWith('$') && !/^\$(env:USERPROFILE|HOME)(\/|\\|$)/i.test(t))) out.push({ kind: 'piped', cwd: dir, from: before || seg })
      else out.push({ kind: 'rm', cwd: dir, targets: real })
      continue
    }
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
export type Sorted = { tracked: string[]; fresh: string[]; ignored: string[]; outside: string[]; protected: string[]; whole: boolean }

const SAFE_OUTSIDE = [/^\/tmp(\/|$)/, /\/AppData\/Local\/Temp(\/|$)/i, /^\/var\/tmp(\/|$)/, /\/scratchpad(\/|$)/, /\/node_modules(\/|$)/]
// Ignored files in a repo that are only caches/build output; everything else ignored (a .env, a backup) counts.
const SAFE_IGNORED = [/(^|\/)node_modules\//, /(^|\/)\.cache\//, /(^|\/)(dist|build|out|coverage|\.next|\.turbo|__pycache__|\.pytest_cache|\.venv|venv|target)\//, /\.pyc$/, /(^|\/)test-results\//, /(^|\/)playwright-report\//, /(^|\/)\.DS_Store$/]

// Lines of .claude/blast-radius-protect as path patterns: * stays inside a folder, ** crosses folders, # starts a comment.
export function protectPatterns(text: string): RegExp[] {
  return text.split('\n').map(l => l.replace(/#.*/, '').trim()).filter(Boolean).map(g => {
    const body = g.replace(/^\/+/, '').replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*\*/g, '\u0000').replace(/\*/g, '[^/]*').replace(/\?/g, '[^/]').replace(/\u0000/g, '.*')
    return new RegExp(`^${body}$`)
  })
}

// Windows paths (a drive letter) ignore case: compare them lower-cased.
const fold = (repo: string) => (/^[A-Za-z]:/.test(repo) ? (x: string) => x.toLowerCase() : (x: string) => x)

export function sortFiles(abs: readonly string[], repo0: string, tracked0: ReadonlySet<string>, kept0: ReadonlySet<string>, protect0: readonly RegExp[] = [], home0 = '/root'): Sorted {
  const s: Sorted = { tracked: [], fresh: [], ignored: [], outside: [], protected: [], whole: false }
  const f = fold(repo0)
  const repo = f(repo0)
  const home = f(home0)
  const tracked = new Set([...tracked0].map(f))
  const kept = new Set([...kept0].map(f))
  const protect = /^[A-Za-z]:/.test(repo0) ? protect0.map(r => new RegExp(r.source, r.flags.includes('i') ? r.flags : `${r.flags}i`)) : protect0
  const pre = repo.replace(/\/+$/, '') + '/'
  for (const a0 of abs) {
    const a = f(a0)
    if (a === repo || a === '/' || a === '/root' || a === '/home' || a === home || /^[a-z]:\/?$/i.test(a) || /^[a-z]:\/users\/?$/i.test(a) || pre.startsWith(a + '/')) {
      s.whole = true
      continue
    }
    if (!a.startsWith(pre)) {
      if (!SAFE_OUTSIDE.some(r => r.test(a))) s.outside.push(a0)
      continue
    }
    const rel0 = a0.slice(pre.length)
    const rel = a.slice(pre.length)
    if (rel === '.git' || rel.startsWith('.git/')) {
      s.whole = true
      continue
    }
    if (protect.some(r => r.test(rel0)) && kept.has(rel)) s.protected.push(rel0)
    else if (tracked.has(rel)) s.tracked.push(rel0)
    else if (kept.has(rel)) s.fresh.push(rel0)
    else if (!SAFE_IGNORED.some(r => r.test(rel))) s.ignored.push(rel0)
  }
  return s
}

export function list(xs: readonly string[], max = 12): string {
  const shown = xs.slice(0, max).map(x => `  - ${x}`)
  if (xs.length > max) shown.push(`  - …and ${xs.length - max} more`)
  return shown.join('\n')
}

// What deleting `targets` from `cwd` takes, read from git's own lists instead of walking the disk (no bash on Windows):
// every tracked, untracked or ignored file at or under a target (globs matched), plus each target outside the repo as is.
export function fromGit(targets: readonly string[], cwd: string, repo: string, files: readonly string[], home = '/root'): string[] {
  const out: string[] = []
  const f = fold(repo)
  const pre = repo.replace(/\/+$/, '') + '/'
  for (const t of targets) {
    const abs = join(cwd, t, home)
    if (!f(abs).startsWith(f(pre))) {
      out.push(abs)
      continue
    }
    const rel = f(abs.slice(pre.length))
    const glob = /[*?[]/.test(rel)
    const re = glob ? new RegExp(`^${rel.replace(/[.+^${}()|\\]/g, '\\$&').replace(/\*/g, '[^/]*').replace(/\?/g, '[^/]')}(/|$)`) : undefined
    for (const file of files) {
      const k = f(file)
      if (re ? re.test(k) : k === rel || k.startsWith(rel + '/')) out.push(pre + file)
    }
    if (rel === '.git' || rel.startsWith('.git/')) out.push(abs)
  }
  return out
}
