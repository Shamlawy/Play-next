import { atom, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Review } from '../types'
import { describe, parseFindings } from './findings.ts'

type $ = EngineInterface

// The owner merges without reading code ("Always merge auto"), so the merge is where a second pair of eyes pays.
// One review per head commit: a merge of a reviewed commit with blocking findings is held once; the same commit again goes through.

const MODEL = 'opus'
const MAX_DIFF = 160_000
// Generated or bookkeeping files: no review needed.
const SKIP = [':(exclude)docs/MAP.md', ':(exclude)docs/LESSONS.md', ':(exclude)*.png', ':(exclude)*.jpg', ':(exclude)*.webp']

const last = atom({ plugin: 'second-look', key: 'last' } as const, null)

const PUSH_MAIN = /\bgit\s+(?:-C\s+\S+\s+)?push\b[^&;|]*(?:\s(?:\S+:)?(?:refs\/heads\/)?main\b)/

const SYSTEM = `You are the second reviewer for "Play next", a single-file web app (index.html, sw.js, worker/helper.js) used by two people:
the owner on a Samsung Galaxy Z Fold (Chrome, installed to the home screen, folded and unfolded, high refresh rate)
and a friend on an iPhone 11 (Safari, installed to the home screen). The owner never reads code and merges automatically,
so you are the last look before a change reaches both phones.

You get the repo's CLAUDE.md (the house rules) and the diff of one release. Find what would actually go wrong:
- breaks or throws on either phone (iOS Safari quirks, the folded 360-412px width, the unfolded ~860px width), or a screen that can no longer be reached
- loses, corrupts or leaks saved data (localStorage S, cloud backup /vault, a migration that drops fields, private data or game titles in a public file)
- breaks a hard rule in CLAUDE.md (press animation, animating anything but transform/opacity, looping blur, hex colours, native date inputs, a doctype, reassigning a wrapped const, layout read right after innerHTML, a fill-forwards animation never cancelled, *.json committed, a repo-root file deleted)
- a release that is incomplete (APP_V bumped without a WHATS_NEW entry that has go/at/say/tryIt, a helper route the app calls but worker/helper.js lacks)
- a real security hole (secrets, injection of untrusted text as HTML, an open proxy)
- something switched off or hidden that still runs timers, listeners or redraws; a feature that duplicates another on the same screen

Only report what you can point to in the diff. No style nits, no "consider", no praise.
severity "block" = it would visibly break, lose data, leak, or break a hard rule. Everything else is "note".

Answer with JSON only, no prose, exactly: {"findings":[{"severity":"block"|"note","file":"path:line","what":"one sentence: what goes wrong and for whom","fix":"one sentence"}]}
An empty list is a fine answer.`

let root = ''
let repo = 'shamlawy/Play-next'

function git($: $, args: string[], timeoutMs = 30_000) {
  return $.process.run(['git', ...args], { cwd: root || undefined, timeoutMs })
}

// The diff a revision brings to main, biggest-risk files first, cut to what one request holds.
async function diffFor($: $, rev: string): Promise<{ diff: string; cut: boolean } | string> {
  await git($, ['fetch', '-q', 'origin', 'main'], 40_000).catch(() => undefined)
  const mb = await git($, ['merge-base', 'origin/main', rev])
  if (mb.exitCode !== 0) return `no common base with origin/main for ${rev}`
  const base = mb.stdout.trim()
  const first = await git($, ['diff', '-U5', base, rev, '--', 'index.html', 'sw.js', 'worker', 'manifest.webmanifest'])
  const rest = await git($, ['diff', '-U3', base, rev, '--', '.', ':(exclude)index.html', ':(exclude)sw.js', ':(exclude)worker', ':(exclude)manifest.webmanifest', ...SKIP])
  const all = `${first.stdout}${rest.stdout}`
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
      { text: `CLAUDE.md:\n${typeof rules === 'string' ? rules : ''}`, cache: true },
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

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd: e.cwd }).catch(() => undefined)
    root = top?.exitCode === 0 ? top.stdout.trim() : e.cwd
    const url = (await git($, ['remote', 'get-url', 'origin']).catch(() => undefined))?.stdout.trim() ?? ''
    const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
    if (m) repo = `${m[1]}/${m[2]}`
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
    if (`${a.owner ?? ''}/${a.repo ?? ''}`.toLowerCase() !== repo.toLowerCase()) return next(e)
    const pr = await $.http.fetch(`https://api.github.com/repos/${repo}/pulls/${a.pullNumber}`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-second-look' },
    })
    const info = pr.ok ? (JSON.parse(pr.text) as { head?: { sha?: string; ref?: string }; base?: { ref?: string } }) : {}
    if (info.base?.ref !== 'main' || !info.head?.sha || !info.head.ref) return next(e)
    await git($, ['fetch', '-q', 'origin', info.head.ref], 40_000)
    const g = await gate($, info.head.sha, info.head.sha, 'merge')
    if (g.deny) return { deny: g.deny }
    const ran = await next(e)
    return g.note && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), g.note] } : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    if (!PUSH_MAIN.test(e.command) || /RG_SKIP=1/.test(e.command)) return next(e)
    const sha = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
    if (!sha) return next(e)
    const g = await gate($, 'HEAD', sha, 'push to main')
    if (g.deny) return { deny: g.deny }
    const ran = await next(e)
    return g.note && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), g.note] } : ran
  }).catch(($, e, next) => next(e))
}
