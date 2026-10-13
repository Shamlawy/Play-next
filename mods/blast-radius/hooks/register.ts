import type { EngineInterface, Register } from 'claude-code'

import { fromGit, join, list, protectPatterns, risks, sortFiles, type Risk } from './blast.ts'

type $ = EngineInterface

// Blast radius: before a shell command that can destroy work runs, measure what it would take and hold it once
// with the list. The exact same command again goes through. A few things are always stopped. Works in any git repo.

let root = ''
let home = '/root'
let mainBranch = 'main'
let protect: RegExp[] = []
// Destructive commands already shown with their blast radius: the same command again goes through.
const shown = new Set<string>()

const bullet = (xs: readonly string[]) => xs.map(x => `- ${x}`).join('\n')

function git($: $, args: string[], timeoutMs = 20_000) {
  return $.process.run(['git', ...args], { cwd: root || undefined, timeoutMs })
}

// Lists what each path argument would delete (directories walked), one path per line, from `cwd`.
const RM_LIST = `shopt -s nullglob dotglob; IFS=''
for t in "$@"; do for p in $t; do
  if [ -d "$p" ] && [ ! -L "$p" ]; then find "$p" \\( -type f -o -type l \\) -print 2>/dev/null; elif [ -e "$p" ] || [ -L "$p" ]; then printf '%s\\n' "$p"; fi
done; done | head -n 20000`

const lines = (s: string) => s.split('\n').map(l => l.trim()).filter(Boolean)

// Blast radius: what the destructive parts of a shell command would take. `hard` is never allowed; `held` is shown once.
async function blast($: $, cmd: string, ps = false): Promise<{ hard: string[]; held: string[] }> {
  const hard: string[] = []
  const held: string[] = []
  if (!root) return { hard, held }
  const found = risks(cmd, root, home, ps)
  if (!found.length) return { hard, held }
  const sh = (r: Risk, argv: string[], timeoutMs = 20_000) => $.process.run(argv, { cwd: r.cwd, timeoutMs }).then(x => x.stdout, () => '')
  let sets: { tracked: Set<string>; kept: Set<string> } | undefined
  const repoSets = async () => sets ??= {
    tracked: new Set(lines((await git($, ['ls-files'])).stdout)),
    kept: new Set([...lines((await git($, ['ls-files'])).stdout), ...lines((await git($, ['ls-files', '-o', '--exclude-standard'])).stdout)]),
  }
  for (const r of found) {
    if (r.kind === 'rm' || r.kind === 'find') {
      const { tracked, kept } = await repoSets()
      let abs: string[]
      if (ps && r.kind === 'rm') {
        // PowerShell (Windows): no bash to walk folders, so read what goes from git's lists, ignored files included.
        const ignored = lines((await git($, ['ls-files', '-o', '-i', '--exclude-standard'])).stdout)
        abs = fromGit(r.targets, r.cwd, root, [...kept, ...ignored], home)
      } else {
        const out = r.kind === 'rm'
          ? await sh(r, ['bash', '-c', RM_LIST, 'rm-list', ...r.targets], 30_000)
          : r.args.some(a => a === '-exec' || a === '-execdir' || a === '-ok') ? '' : await sh(r, ['find', ...r.args, '-print'], 30_000)
        abs = lines(out).map(p => join(r.cwd, p))
        if (r.kind === 'rm') abs.push(...r.targets.filter(t => !/[*?[]/.test(t)).map(t => join(r.cwd, t)))
      }
      const s = sortFiles([...new Set(abs)], root, tracked, kept, protect, home)
      const what = r.kind === 'rm' ? `rm ${r.targets.join(' ')}` : 'find … -delete'
      if (s.whole) hard.push(`${what}: would delete the repo, its .git, or a whole home/system folder`)
      if (s.protected.length) hard.push(`${what}: deletes files this repo protects (.claude/blast-radius-protect):\n${list(s.protected)}`)
      if (s.fresh.length) held.push(`${what}: ${s.fresh.length} file(s) not in git yet, gone for good:\n${list(s.fresh)}`)
      const sub = s.tracked.filter(t => t.includes('/'))
      if (sub.length) held.push(`${what}: ${sub.length} tracked file(s) (git can bring them back, but a commit -a would ship the delete):\n${list(sub)}`)
      if (s.ignored.length) held.push(`${what}: ${s.ignored.length} git-ignored file(s) in the repo (a .env or a backup can live here):\n${list(s.ignored)}`)
      if (s.outside.length) held.push(`${what}: ${s.outside.length} file(s) outside the repo and outside /tmp:\n${list(s.outside)}`)
    } else if (r.kind === 'reset') {
      const edits = lines(await sh(r, ['git', 'diff', '--name-only', 'HEAD']))
      if (edits.length) held.push(`git reset --hard: throws away uncommitted changes in ${edits.length} file(s):\n${list(edits)}`)
      if (r.rev !== 'HEAD') {
        const gone = lines(await sh(r, ['git', 'log', '--oneline', 'HEAD', '--not', r.rev, '--remotes']))
        if (gone.length) held.push(`git reset --hard ${r.rev}: drops ${gone.length} commit(s) that are on no remote:\n${list(gone)}`)
      }
    } else if (r.kind === 'clean') {
      const gone = lines(await sh(r, ['git', 'clean', '-n', ...(r.flags ? [`-${r.flags}`] : [])])).map(l => l.replace(/^Would remove /, ''))
      if (gone.length) held.push(`git clean: deletes ${gone.length} untracked file(s)/folder(s):\n${list(gone)}`)
    } else if (r.kind === 'discard') {
      const gone = lines(await sh(r, ['git', 'diff', '--name-only', '--', ...r.paths]))
      if (gone.length) held.push(`discards uncommitted edits in ${gone.length} file(s):\n${list(gone)}`)
    } else if (r.kind === 'force') {
      const dst = r.dst === 'HEAD' || !r.dst ? (await sh(r, ['git', 'rev-parse', '--abbrev-ref', 'HEAD'])).trim() : r.dst
      if (dst === mainBranch || dst === 'main' || dst === 'master') {
        hard.push(`force-push to ${dst}: the default branch is what everyone builds on; never rewrite it`)
        continue
      }
      await sh(r, ['git', 'fetch', '-q', 'origin', dst], 40_000)
      const gone = lines(await sh(r, ['git', 'log', '--oneline', `origin/${dst}`, '--not', r.src || 'HEAD']))
      if (gone.length) held.push(`force-push to ${dst}: removes ${gone.length} commit(s) from GitHub:\n${list(gone)}`)
    } else if (r.kind === 'branch') {
      const gone = lines(await sh(r, ['git', 'log', '--oneline', r.name, '--not', 'HEAD', '--remotes']))
      if (gone.length) held.push(`git branch -D ${r.name}: drops ${gone.length} commit(s) that are nowhere else:\n${list(gone)}`)
    }
  }
  return { hard, held }
}


// Holds or stops a shell command (Bash, or PowerShell when `ps`) by its blast radius; undefined lets it run.
async function guard($: $, cmd: string, ps: boolean): Promise<{ deny: string } | undefined> {
  if (/BLAST_OK=1|RG_SKIP=1/.test(cmd)) return undefined
  const { hard, held } = await blast($, cmd, ps)
  if (hard.length) return { deny: `blast-radius stopped this command:\n${bullet(hard)}\nFind another way; BLAST_OK=1 in front only if the person asked for exactly this.` }
  const key = cmd.trim()
  if (held.length && !shown.has(key)) {
    shown.add(key)
    return { deny: `blast-radius held this command once. It would:\n${bullet(held)}\nIf that's all meant to go, run the exact same command again; otherwise narrow it.` }
  }
  return undefined
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd: e.cwd }).catch(() => undefined)
    root = top?.exitCode === 0 ? join('/', top.stdout.trim()) : ''
    const env = await $.process.run(['sh', '-c', 'printf %s "$HOME"'], { cwd: e.cwd }).catch(() => undefined)
    // Windows has no sh: the home folder comes from cmd instead.
    const win = env?.stdout.trim() ? undefined : await $.process.run(['cmd', '/c', 'echo %USERPROFILE%'], { cwd: e.cwd }).catch(() => undefined)
    const h = env?.stdout.trim() || (win?.exitCode === 0 ? win.stdout.trim() : '')
    if (h && !h.includes('%')) home = join('/', h)
    if (root) {
      const head = await git($, ['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']).catch(() => undefined)
      const b = head?.exitCode === 0 ? head.stdout.trim().replace(/^origin\//, '') : ''
      if (b) mainBranch = b
      const p = await $.fs.read(`${root}/.claude/blast-radius-protect`).catch(() => '')
      protect = typeof p === 'string' ? protectPatterns(p) : []
    }
    return next(e)
  })

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => (await guard($, e.command, false)) ?? next(e)).catch(($, e, next) => next(e))

  // PowerShell is the shell tool Claude Code uses on Windows.
  on('tool.call', { tool: 'PowerShell' }, async ($, e, next) => {
    const cmd = (e as unknown as { command?: unknown }).command
    return (typeof cmd === 'string' && (await guard($, cmd, true))) || next(e)
  }).catch(($, e, next) => next(e))
}
