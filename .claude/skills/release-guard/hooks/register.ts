import type { EngineInterface, Register } from 'claude-code'
import {
  appV, diffLines, fileProblems, logLookup, parseNameStatus, parsePorcelain, releaseProblems, rootDeleted, scanBlock, scanLines,
  type Range,
} from './rules.ts'

type $ = EngineInterface

// A watch on GitHub after a merge to main: Pages (the app) and, when worker/** changed, the helper deploy.
type Watch = { since: number; appV?: string; helper: boolean; pagesDone?: boolean; helperDone?: boolean; slowTold?: boolean }

const POLL = 90_000
const SLOW = 25 * 60_000
const GIVE_UP = 60 * 60_000
const PAGES = 'pages build and deployment'

// Imports worker/helper.js (from stdin) in Node the way CLAUDE.md asks: node --check, then a real import.
const HELPER_CHECK = `
const fs=require('fs'),os=require('os'),path=require('path'),cp=require('child_process'),url=require('url');
const f=path.join(os.tmpdir(),'rg-helper-'+process.pid+'.mjs');fs.writeFileSync(f,fs.readFileSync(0,'utf8'));
const done=t=>{try{fs.unlinkSync(f)}catch{}console.log(t)};
let bad='';try{cp.execFileSync(process.execPath,['--check',f],{stdio:'pipe'})}catch(e){bad=String(e.stderr||e.message).trim().split('\\n').slice(0,4).join(' ')}
if(bad)done('node --check failed: '+bad);
else import(url.pathToFileURL(f).href).then(m=>done(m.default&&typeof m.default.fetch==='function'?'ok':'imports, but has no default export with fetch()'),e=>done('import in Node failed: '+String(e&&e.message||e).slice(0,200)));
`

const PUSH = /\bgit\s+(?:-C\s+\S+\s+)?push\b/
const COMMIT = /\bgit\s+(?:-C\s+\S+\s+)?commit\b/
const TO_MAIN = /\bgit\s+(?:-C\s+\S+\s+)?push\b[^&;|]*(?:\s(?:\S+:)?(?:refs\/heads\/)?main\b)/
const HTML_WRITE = /index\.html/
// Shell commands that change index.html (a script only when it writes files).
const WRITER = /\bsed\s+-[a-zA-Z]*i|\bperl\s+-[a-zA-Z]*i|>\s*\S*index\.html|\btee\b|\bpatch\b|\bgit\s+apply\b|\b(?:python3?|node)\b[\s\S]*(?:writeFileSync|\.write\(|open\([^)]*["'][wa]\+?["'])/
const TESTED = /playwright|chromium|pw-browsers|webkit|scratchpad\/\S+\.(?:m?js|py)\b/i

const bullet = (xs: readonly string[]) => xs.map(x => `- ${x}`).join('\n')

// Module state: a reload starts it over (the watch is kept in $.store).
let root = ''
let repo = 'shamlawy/Play-next'
let watch: Watch | undefined
let polling = false
const pending: string[] = []
const toldRules = new Set<string>()
const toldLog = new Set<string>()
// Per turn: was index.html changed after the last browser test? (one counter orders both)
let seq = 0
let lastEdit = 0
let lastTest = 0


function git($: $, args: string[], timeoutMs = 20_000) {
  return $.process.run(['git', ...args], { cwd: root || undefined, timeoutMs })
}
async function show($: $, rev: string, path: string) {
  const r = await git($, ['show', `${rev}:${path}`])
  return r.exitCode === 0 ? r.stdout : undefined
}

async function tell($: $, text: string) {
  pending.push(text)
  $.ui.toast(text, { timeoutMs: 12_000 })
  $.ui.status(text)
  try {
    await $.session.append({ message: { type: 'system', content: [{ type: 'text', text: `release-guard: ${text}` }] } })
  } catch {
    // A notice is a nicety; the toast and the next-reply note still carry it.
  }
}

// What a revision brings to main, gathered from git (and Node for the helper).
async function gather($: $, rev: string): Promise<Range | string> {
  await git($, ['fetch', '-q', 'origin', 'main'], 40_000).catch(() => undefined)
  const mb = await git($, ['merge-base', 'origin/main', rev])
  if (mb.exitCode !== 0) return `no common base with origin/main for ${rev}`
  const base = mb.stdout.trim()
  const changes = parseNameStatus((await git($, ['diff', '--name-status', '-M', base, rev])).stdout)
  const has = (p: string) => changes.some(c => c.path === p)
  const range: Range = { changes, logAdded: diffLines((await git($, ['diff', '-U0', base, rev, '--', 'docs/LOG.md'])).stdout).added.join('\n') }
  if (has('index.html')) {
    range.baseHtml = await show($, base, 'index.html')
    range.headHtml = await show($, rev, 'index.html')
  }
  if (has('worker/helper.js')) {
    range.baseHelper = await show($, base, 'worker/helper.js')
    range.headHelper = await show($, rev, 'worker/helper.js')
    if (range.headHelper !== undefined) {
      const r = await $.process.run(['node', '-e', HELPER_CHECK], { cwd: root || undefined, stdin: range.headHelper, timeoutMs: 30_000 })
      const said = r.stdout.trim().split('\n').pop() ?? ''
      if (said !== 'ok') range.helperCheck = said || r.stderr.trim().slice(0, 200) || `node exited ${r.exitCode}`
    }
  }
  return range
}

async function check($: $, rev: string) {
  const r = await gather($, rev)
  if (typeof r === 'string') return { block: [], notes: [r], range: undefined }
  return { ...releaseProblems(r), range: r }
}

function startWatch($: $, now: number, range: Range | undefined) {
  watch = {
    since: now,
    appV: appV(range?.headHtml),
    helper: !!range?.changes.some(c => c.path.startsWith('worker/')),
  }
  void $.store.set('watch', watch)
  $.ui.status(`watching GitHub Pages${watch.helper ? ' and the helper deploy' : ''}…`)
}

type Run = { name?: string; path?: string; status?: string; conclusion?: string | null; head_sha?: string; created_at?: string; html_url?: string }

async function poll($: $) {
  const w = watch
  if (!w || polling) return
  polling = true
  try {
    const now = await $.clock.now()
    const res = await $.http.fetch(`https://api.github.com/repos/${repo}/actions/runs?branch=main&per_page=20`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-release-guard' },
    })
    if (!res.ok) return
    const runs = ((JSON.parse(res.text) as { workflow_runs?: Run[] }).workflow_runs ?? [])
      .filter(r => Date.parse(r.created_at ?? '') >= w.since - 120_000)
    const pages = runs.find(r => r.name === PAGES)
    if (!w.pagesDone && pages?.status === 'completed') {
      w.pagesDone = true
      if (pages.conclusion === 'success') {
        const live = await $.http.fetch(`https://raw.githubusercontent.com/${repo}/${pages.head_sha}/index.html`)
        const v = live.ok ? appV(live.text) : undefined
        await tell($, v
          ? `v${v} is live on GitHub Pages: both phones get it on their next open${w.appV && v !== w.appV ? ` (expected v${w.appV})` : ''}.`
          : 'GitHub Pages finished deploying: the update is live.')
      } else {
        await tell($, `GitHub Pages deploy ${pages.conclusion ?? 'failed'}: the update is NOT live. ${pages.html_url ?? ''}`.trim())
      }
    }
    const helper = runs.find(r => (r.path ?? '').endsWith('deploy-helper.yml'))
    if (w.helper && !w.helperDone && helper?.status === 'completed') {
      w.helperDone = true
      await tell($, helper.conclusion === 'success'
        ? "Nexi's helper is deployed."
        : `Nexi's helper deploy ${helper.conclusion ?? 'failed'}: the live helper is still the old one. ${helper.html_url ?? ''}`.trim())
    }
    const settled = w.pagesDone && (!w.helper || w.helperDone)
    if (!settled && !w.slowTold && now - w.since > SLOW) {
      w.slowTold = true
      await tell($, `Still not live ${Math.round((now - w.since) / 60_000)} min after the merge: check the Actions tab.`)
    }
    if (settled || now - w.since > GIVE_UP) {
      watch = undefined
      if (settled) $.ui.status(undefined)
    }
    await $.store.set('watch', watch ?? null)
  } catch {
    // Network hiccup: the next tick tries again.
  } finally {
    polling = false
  }
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const cwd = e.cwd
    const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd }).catch(() => undefined)
    root = top?.exitCode === 0 ? top.stdout.trim() : cwd
    const url = (await git($, ['remote', 'get-url', 'origin']).catch(() => undefined))?.stdout.trim() ?? ''
    const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
    if (m) repo = `${m[1]}/${m[2]}`
    const saved = (await $.store.get('watch')) as Watch | null | undefined
    if (saved && (await $.clock.now()) - saved.since < GIVE_UP) watch = saved
    $.clock.every(POLL, () => void poll($))
    await $.command.register({ name: 'release-check', description: 'Check this branch against the release rules (APP_V, WHATS_NEW, LOG, helper, files).' })
    return next(e)
  })

  on('command.run', { command: 'release-check' }, async $ => {
    const { block, notes } = await check($, 'HEAD')
    const lines = [
      block.length ? `Not ready to merge:\n${bullet(block)}` : 'Ready to merge: APP_V, WHATS_NEW, LOG, helper and files all check out.',
      notes.length ? `Worth a look:\n${bullet(notes)}` : '',
    ]
    return { text: lines.filter(Boolean).join('\n\n') }
  })

  on('turn.start', ($, e, next) => {
    seq = lastEdit = lastTest = 0
    return next(e)
  })

  // Every reply: news from the watch (merged → live) that arrived since the last one.
  on('prompt.submit', ($, e, next) => {
    if (!pending.length) return next(e)
    const note = `[release-guard] Since your last reply: ${pending.splice(0).join(' ')} Tell the person in one short line.`
    return next({ ...e, context: [...(e.context ?? []), note] })
  })

  // Edits to index.html: house rules on the added lines, and the LOG lines about the code touched.
  on('tool.call', { tool: 'Edit' }, async ($, e, next) => {
    if (!/(^|\/)index\.html$/.test(e.file_path)) return next(e)
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    lastEdit = ++seq
    const oldLines = new Set(e.old_string.split('\n'))
    const added = e.new_string.split('\n').filter(l => !oldLines.has(l))
    const rules = [...scanLines(added), ...scanBlock(e.new_string)].map(f => f.text).filter(t => !toldRules.has(t))
    rules.forEach(t => toldRules.add(t))
    const log = await $.fs.read(`${root}/docs/LOG.md`).catch(() => '')
    const past = typeof log === 'string' && log ? logLookup(e.old_string, log, toldLog) : []
    const context = [...(ran.context ?? [])]
    if (rules.length) context.push(`[release-guard] House rules (CLAUDE.md) on the lines you just added to index.html:\n${bullet(rules)}\nFix them now unless it is deliberate.`)
    if (past.length) context.push(`[release-guard] docs/LOG.md on code you just changed (make sure an old fix isn't undone):\n${bullet(past)}`)
    return context.length ? { ...ran, context } : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Write' }, async ($, e, next) => {
    if (!/(^|\/)index\.html$/.test(e.file_path)) return next(e)
    const before = await $.fs.read(e.file_path).catch(() => '')
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    lastEdit = ++seq
    const old = new Set(typeof before === 'string' ? before.split('\n') : [])
    const added = e.content.split('\n').filter(l => !old.has(l))
    const rules = [...scanLines(added), ...scanBlock(added.join('\n'))].map(f => f.text).filter(t => !toldRules.has(t))
    rules.forEach(t => toldRules.add(t))
    if (!rules.length) return ran
    return { ...ran, context: [...(ran.context ?? []), `[release-guard] House rules (CLAUDE.md) on the lines you just added to index.html:\n${bullet(rules)}`] }
  }).catch(($, e, next) => next(e))

  // Shell: commits and pushes are gated; edits and browser tests are counted for the end-of-turn check.
  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    const cmd = e.command
    if (HTML_WRITE.test(cmd) && WRITER.test(cmd) && !COMMIT.test(cmd) && !PUSH.test(cmd)) lastEdit = ++seq
    if (TESTED.test(cmd)) lastTest = ++seq

    if (COMMIT.test(cmd) && !/RG_SKIP=1/.test(cmd)) {
      const stages = /\bgit\s+add\b/.test(cmd)
      const changes = stages
        ? parsePorcelain((await git($, ['status', '--porcelain=v1', '-uall'])).stdout)
        : [
            ...parseNameStatus((await git($, ['diff', '--cached', '--name-status', '-M'])).stdout),
            ...(/\s-[a-zA-Z]*a|\s--all\b/.test(cmd) ? parseNameStatus((await git($, ['diff', '--name-status'])).stdout) : []),
          ]
      const forced = /\bgit\s+add\b[^&;|]*\s-f\b[^&;|]*\.json\b/.test(cmd) ? ['a .json file force-added: backups hold private data'] : []
      const block = [...forced, ...fileProblems(changes, rootDeleted(changes))]
      if (block.length) return { deny: `release-guard stopped this commit:\n${bullet(block)}\nUnstage those (git restore --staged <file>, or git checkout -- <file> for a deleted one) and commit again.` }
    }

    if (PUSH.test(cmd) && TO_MAIN.test(cmd) && !/RG_SKIP=1/.test(cmd)) {
      const { block, notes, range } = await check($, 'HEAD')
      if (block.length) return { deny: `release-guard stopped this push to main:\n${bullet(block)}${notes.length ? `\nAlso:\n${bullet(notes)}` : ''}` }
      const headBefore = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
      const ran = await next(e)
      if (ran.deny === undefined && !ran.isError && headBefore) startWatch($, await $.clock.now(), range)
      return notes.length && ran.deny === undefined ? { ...ran, context: [...(ran.context ?? []), `[release-guard] Worth a look:\n${bullet(notes)}`] } : ran
    }

    const headBefore = COMMIT.test(cmd) ? (await git($, ['rev-parse', 'HEAD'])).stdout.trim() : ''
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    const context = [...(ran.context ?? [])]

    // After a commit: house rules on what it added to index.html (edits made through the shell skip the Edit hook).
    if (COMMIT.test(cmd)) {
      const head = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
      if (head && head !== headBefore) {
        const d = await git($, ['show', '-U0', '--format=', 'HEAD', '--', 'index.html'])
        const { added } = diffLines(d.stdout)
        const rules = [...scanLines(added), ...scanBlock(added.join('\n'))].map(f => f.text).filter(t => !toldRules.has(t))
        rules.forEach(t => toldRules.add(t))
        if (rules.length) context.push(`[release-guard] House rules (CLAUDE.md) on what this commit adds to index.html:\n${bullet(rules)}`)
      }
    }

    // After a branch push: what would stop the merge, said now while it is cheap to fix.
    if (PUSH.test(cmd)) {
      const { block, notes } = await check($, 'HEAD')
      if (block.length) context.push(`[release-guard] Pushed, but this branch would be stopped at merge:\n${bullet(block)}\nFix it before merging (or say it's a work-in-progress push).`)
      if (notes.length) context.push(`[release-guard] Worth a look before merging:\n${bullet(notes)}`)
    }
    return context.length > (ran.context?.length ?? 0) ? { ...ran, context } : ran
  }).catch(($, e, next) => next(e))

  // The merge is the release: GitHub Pages serves main. Check what the PR brings, then watch it go live.
  on('tool.call', { tool: 'mcp__github__merge_pull_request' }, async ($, e, next) => {
    const a = e as unknown as { owner?: string; repo?: string; pullNumber?: number }
    const full = `${a.owner ?? ''}/${a.repo ?? ''}`
    if (full.toLowerCase() !== repo.toLowerCase()) return next(e)
    const pr = await $.http.fetch(`https://api.github.com/repos/${full}/pulls/${a.pullNumber}`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-release-guard' },
    })
    const info = pr.ok ? (JSON.parse(pr.text) as { head?: { sha?: string; ref?: string }; base?: { ref?: string } }) : {}
    let range: Range | undefined
    if (info.base?.ref === 'main' && info.head?.sha && info.head.ref) {
      await git($, ['fetch', '-q', 'origin', info.head.ref], 40_000)
      const r = await check($, info.head.sha)
      range = r.range
      if (r.block.length) return { deny: `release-guard stopped this merge (it would ship to both phones):\n${bullet(r.block)}${r.notes.length ? `\nAlso:\n${bullet(r.notes)}` : ''}\nFix it on the branch, push, then merge.` }
    }
    const ran = await next(e)
    if (ran.deny === undefined && !ran.isError && !/not mergeable|failed|error/i.test(ran.text ?? '')) startWatch($, await $.clock.now(), range)
    return ran
  }).catch(($, e, next) => next(e))

  // Before Claude says it's done: index.html changed this turn, so a browser test must have run after it.
  on('classic.Stop', async ($, e, next) => {
    const r = await next(e)
    if (r.block || e.stop_hook_active || !lastEdit || lastTest > lastEdit) return r
    return {
      ...r,
      block: '[release-guard] index.html changed this turn but no browser test ran after the last change. CLAUDE.md: test in Playwright + Chromium (412 and 900 widths; the tour too if WHATS_NEW changed; the 412/859/360 detector sweep for problem-report fixes) before saying it is done. If this turn did not finish the change, say so plainly instead.',
    }
  }).catch(($, e, next) => next(e))
}
