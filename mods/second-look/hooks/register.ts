import { atom, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Review } from '../types'
import { describe, parseFindings, readNotes } from './findings.ts'

type $ = EngineInterface

// When nobody reads the code before it ships, the merge is where a second pair of eyes pays. Works in any GitHub repo;
// a project adds its own notes for the reviewer in .claude/second-look.md.
// One review per head commit: a merge of a reviewed commit with blocking findings is held once; the same commit again goes through.

const MODEL = 'opus'
const MAX_DIFF = 160_000
// Generated, binary or lock files: no review needed. A project adds its own with "skip:" lines in .claude/second-look.md.
const SKIP = ['*.png', '*.jpg', '*.jpeg', '*.gif', '*.webp', '*.ico', '*.pdf', '*.woff', '*.woff2', '*.min.js', '*.map', 'package-lock.json', 'yarn.lock', 'pnpm-lock.yaml', 'bun.lockb', 'Cargo.lock', 'poetry.lock']

const last = atom({ plugin: 'second-look', key: 'last' } as const, null)

const pushTo = (branch: string) => new RegExp(`\\bgit\\s+(?:-C\\s+\\S+\\s+)?push\\b[^&;|]*(?:\\s(?:\\S+:)?(?:refs\\/heads\\/)?${branch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\w./-]))`)

const SYSTEM = `You are the second reviewer on a code change that is about to be merged into the default branch.
Often nobody else reads this code before it ships, so you are the last look.

You get the repo's CLAUDE.md (its house rules, if it has one), the project's own review notes (if any) and the diff. Find what would actually go wrong:
- a bug, crash or error the change introduces; something that no longer works or can no longer be reached
- loses, corrupts or leaks data (a migration that drops fields, secrets or personal data committed, private data in a public repo)
- breaks a rule written in CLAUDE.md or the project's review notes
- an incomplete change (a caller left pointing at something removed, a config or version bump the rules require but is missing)
- a real security hole (secrets, injection, an open endpoint)
- something switched off or hidden that still runs timers, listeners or work; two features doing the same job

Only report what you can point to in the diff. No style nits, no "consider", no praise.
severity "block" = it would visibly break, lose or leak data, or break a hard rule. Everything else is "note".

Answer with JSON only, no prose, exactly: {"findings":[{"severity":"block"|"note","file":"path:line","what":"one sentence: what goes wrong and for whom","fix":"one sentence"}]}
An empty list is a fine answer.`

let root = ''
let repo = ''
let mainBranch = 'main'
// The project's own review notes (.claude/second-look.md): prose for the reviewer, plus "first:" and "skip:" path lines.
let notes = { text: '', first: [] as string[], skip: [] as string[] }


function git($: $, args: string[], timeoutMs = 30_000) {
  return $.process.run(['git', ...args], { cwd: root || undefined, timeoutMs })
}

// The diff a revision brings to the default branch, the project's "first:" paths first, cut to what one request holds.
async function diffFor($: $, rev: string): Promise<{ diff: string; cut: boolean } | string> {
  await git($, ['fetch', '-q', 'origin', mainBranch], 40_000).catch(() => undefined)
  const mb = await git($, ['merge-base', `origin/${mainBranch}`, rev])
  if (mb.exitCode !== 0) return `no common base with origin/${mainBranch} for ${rev}`
  const base = mb.stdout.trim()
  const skip = [...SKIP, ...notes.skip].map(p => `:(exclude,glob)**/${p.replace(/^\/+/, '')}`)
  const first = notes.first.length ? (await git($, ['diff', '-U5', base, rev, '--', ...notes.first])).stdout : ''
  const rest = await git($, ['diff', '-U3', base, rev, '--', '.', ...notes.first.map(p => `:(exclude)${p}`), ...skip])
  const all = `${first}${rest.stdout}`
  if (!all.trim()) return { diff: '', cut: false }
  return all.length > MAX_DIFF ? { diff: all.slice(0, MAX_DIFF), cut: true } : { diff: all, cut: false }
}

async function review($: $, rev: string, sha: string): Promise<Review> {
  const now = await $.clock.now()
  const got = await diffFor($, rev)
  if (typeof got === 'string') return { sha, at: now, findings: [], failed: got }
  if (!got.diff) return { sha, at: now, findings: [] }
  const rules = await $.fs.read(`${root}/CLAUDE.md`).catch(() => '')
  const r = await $.model.complete({
    model: MODEL,
    effort: 'high',
    maxTokens: 8000,
    timeoutMs: 240_000,
    system: SYSTEM,
    prompt: [
      { text: `CLAUDE.md:\n${typeof rules === 'string' && rules ? rules : '(none)'}${notes.text ? `\n\nThe project's review notes (.claude/second-look.md):\n${notes.text}` : ''}`, cache: true },
      { text: `\n\nThe release diff${got.cut ? ` (cut at ${MAX_DIFF} characters; the app files come first)` : ''}:\n${got.diff}` },
    ],
  })
  if (!r.isAnswered) return { sha, at: now, findings: [], failed: `the reviewer did not answer (${r.reason})` }
  const findings = parseFindings(r.text)
  if (!findings) return { sha, at: now, findings: [], failed: 'the reviewer answered in a shape I could not read' }
  return { sha, at: now, findings }
}

// Shared by the merge and the push to main: review once, hold once on blocking findings.
async function gate($: $, rev: string, sha: string, where: string): Promise<{ deny?: string; note?: string }> {
  const prev = (await $.store.get(`r:${sha}`)) as Review | null | undefined
  const r = prev ?? (await review($, rev, sha))
  if (!prev && !r.failed) await $.store.set(`r:${sha}`, r)
  await update($, last, () => r)
  const blocks = r.findings.filter(f => f.severity === 'block')
  const notes = r.findings.filter(f => f.severity === 'note')
  if (r.failed) {
    $.ui.toast(`Second look skipped: ${r.failed}`)
    return { note: `[second-look] The review could not run (${r.failed}); the ${where} went ahead on release-guard's checks alone. Say so in one line.` }
  }
  if (blocks.length && !(await $.store.get(`h:${sha}`))) {
    await $.store.set(`h:${sha}`, true)
    $.ui.toast(`Second look held the ${where}: ${blocks.length} problem(s)`)
    return {
      deny: `second-look held this ${where}: a second reviewer read the change and found what would break:\n${describe(blocks)}${notes.length ? `\nAlso noted:\n${describe(notes)}` : ''}\n` +
        'Fix each one on the branch and push (a new commit gets a fresh review). If a finding is wrong, say why in your reply, then run the same merge again: it goes through once it has been shown.',
    }
  }
  $.ui.toast(blocks.length ? `Second look: went ahead past ${blocks.length} finding(s) you answered` : `Second look: nothing blocking${notes.length ? `, ${notes.length} note(s)` : ''}`)
  const said = blocks.length
    ? `[second-look] Merged past these findings after they were shown (make sure the reply says why each was wrong):\n${describe(blocks)}`
    : notes.length ? `[second-look] Nothing blocking. Notes worth a follow-up (mention them in one line; fix the plainly right ones in the next change):\n${describe(notes)}` : ''
  return { note: said || undefined }
}

// A shell command that pushes to the default branch gets the same review as a merge; anything else, undefined.
async function pushGate($: $, cmd: string) {
  if (!root || !pushTo(mainBranch).test(cmd) || /RG_SKIP=1|LOOK_OK=1/.test(cmd)) return undefined
  const sha = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
  if (!sha) return undefined
  return gate($, 'HEAD', sha, `push to ${mainBranch}`)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd: e.cwd }).catch(() => undefined)
    root = top?.exitCode === 0 ? top.stdout.trim() : e.cwd
    const url = (await git($, ['remote', 'get-url', 'origin']).catch(() => undefined))?.stdout.trim() ?? ''
    const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
    if (m) repo = `${m[1]}/${m[2]}`
    const head = await git($, ['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']).catch(() => undefined)
    const b = head?.exitCode === 0 ? head.stdout.trim().replace(/^origin\//, '') : ''
    if (b) mainBranch = b
    const md = await $.fs.read(`${root}/.claude/second-look.md`).catch(() => '')
    notes = readNotes(typeof md === 'string' ? md : '')
    await $.command.register({ name: 'second-look', description: 'Have a second Claude review this branch against CLAUDE.md now, the way it does before every merge.' })
    return next(e)
  })

  on('command.run', { command: 'second-look' }, async $ => {
    const sha = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
    const r = await review($, 'HEAD', sha)
    if (!r.failed) await $.store.set(`r:${sha}`, r)
    await update($, last, () => r)
    if (r.failed) return { text: `Second look could not run: ${r.failed}` }
    if (!r.findings.length) return { text: 'Second look: nothing that would break.' }
    const blocks = r.findings.filter(f => f.severity === 'block')
    const notes = r.findings.filter(f => f.severity === 'note')
    return { text: [blocks.length ? `Would hold the merge:\n${describe(blocks)}` : 'Nothing that would hold the merge.', notes.length ? `Notes:\n${describe(notes)}` : ''].filter(Boolean).join('\n\n') }
  })

  on('tool.call', { tool: 'mcp__github__merge_pull_request' }, async ($, e, next) => {
    const a = e as unknown as { owner?: string; repo?: string; pullNumber?: number }
    if (!repo || `${a.owner ?? ''}/${a.repo ?? ''}`.toLowerCase() !== repo.toLowerCase() || !a.pullNumber) return next(e)
    const pr = await $.http.fetch(`https://api.github.com/repos/${repo}/pulls/${a.pullNumber}`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'second-look-mod' },
    }).catch(() => undefined)
    const info = pr?.ok ? (JSON.parse(pr.text) as { head?: { sha?: string }; base?: { ref?: string } }) : {}
    if (info.base?.ref && info.base.ref !== mainBranch) return next(e)
    // A private repo answers the API with 404: GitHub keeps every PR's head at pull/N/head, which git can fetch.
    const got = await git($, ['fetch', '-q', 'origin', `pull/${a.pullNumber}/head`], 40_000)
    const sha = info.head?.sha ?? (got.exitCode === 0 ? (await git($, ['rev-parse', 'FETCH_HEAD'])).stdout.trim() : '')
    if (!sha) return next(e)
    const g = await gate($, sha, sha, 'merge')
    if (g.deny) return { deny: g.deny }
    const ran = await next(e)
    return g.note && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), g.note] } : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    const g = await pushGate($, e.command)
    if (g?.deny) return { deny: g.deny }
    const ran = await next(e)
    return g?.note && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), g.note] } : ran
  }).catch(($, e, next) => next(e))

  // PowerShell is the shell tool Claude Code uses on Windows: the same push, the same review.
  on('tool.call', { tool: 'PowerShell' }, async ($, e, next) => {
    const cmd = (e as unknown as { command?: unknown }).command
    const g = typeof cmd === 'string' ? await pushGate($, cmd) : undefined
    if (g?.deny) return { deny: g.deny }
    const ran = await next(e)
    return g?.note && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), g.note] } : ran
  }).catch(($, e, next) => next(e))
}
