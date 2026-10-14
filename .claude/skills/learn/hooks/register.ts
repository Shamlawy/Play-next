import type { EngineInterface, Register } from 'claude-code'
import {
  checkFamily, deferrals, failed, firstNote, identifiers, parseLessons, promptSignals, recallNote, reflection, relevant, STRONG, wrapNote,
  type At, type Brief, type Lesson, type Reopened, type Signal,
} from './signals.ts'
import { herePath, isHere, setHere } from './here.ts'

type $ = EngineInterface

// Module state: a reload starts it over. Lessons live in docs/LESSONS.md (git), so nothing important is only here.
let root = ''
let repo = 'shamlawy/Play-next'
let brief: Brief | undefined
let reopened: Reopened[] = []
let told = false
let lessons: Lesson[] = []
let lessonsAt = 0
const toldLessons = new Set<string>()
const toldWraps = new Set<string>()
let toldScratch = false
const sessionSignals: Signal[] = []
// Per turn
let signals: Signal[] = []
let asked = false
let askedCommit = false
let recorded = false
let committed = false
const failing = new Map<string, number>()
const editMiss = new Map<string, number>()

const RETRO_ASK = /^\s*\/?(retro|retrospective|what did you learn)\s*[.!?]?\s*$/i
const LEARN_CMD = /tools\/learn\.mjs\s+(add|done|promote)\b/
const COMMIT = /\bgit\s+(?:-C\s+\S+\s+)?commit\b/
const PUSH = /\bgit\s+(?:-C\s+\S+\s+)?push\b/
const SAID_DETECTOR = /spot (this|it|these) (myself|by myself|himself)|learned to (spot|catch)|can'?t be (detected|caught)|cannot be (detected|caught)|not (something|a thing) (the app|nexi) can|matter of taste|detector/i

const signal = (s: Signal) => { signals.push(s); sessionSignals.push(s); if (sessionSignals.length > 60) sessionSignals.shift() }
const run = ($: $, argv: string[], timeoutMs = 20_000) => $.process.run(argv, { cwd: root || undefined, timeoutMs })

async function loadLessons($: $) {
  const now = await $.clock.now()
  if (now - lessonsAt < 30_000) return lessons
  const md = await $.fs.read(`${root}/docs/LESSONS.md`).catch(() => '')
  lessons = typeof md === 'string' ? parseLessons(md) : []
  lessonsAt = now
  return lessons
}
async function loadBrief($: $) {
  const r = await run($, ['node', 'tools/learn.mjs', 'brief']).catch(() => undefined)
  if (r?.exitCode === 0) { try { brief = JSON.parse(r.stdout) as Brief } catch { /* old checkout without tools/learn.mjs */ } }
  if (brief) $.ui.status(`🧠 ${brief.lessons + brief.promoted} lessons · ${brief.openCount} ideas`)
}
// Problem reports that came back after a fix (bugsync comments "Happened again after it was closed").
async function loadReopened($: $) {
  const q = encodeURIComponent(`repo:${repo} label:auto-bug is:issue is:open "Happened again after it was closed" in:comments`)
  const res = await $.http.fetch(`https://api.github.com/search/issues?q=${q}&per_page=10`, {
    headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-learn' },
  }).catch(() => undefined)
  if (!res?.ok) return
  const items = (JSON.parse(res.text) as { items?: { number: number; title: string }[] }).items ?? []
  reopened = items.map(i => ({ number: i.number, title: i.title }))
}
const withContext = <T extends { context?: readonly string[] }>(ran: T, notes: string[]) =>
  notes.filter(Boolean).length ? { ...ran, context: [...(ran.context ?? []), ...notes.filter(Boolean)] } : ran

// Lessons for this spot, each once per chat.
async function recall($: $, at: { file?: string; names?: readonly string[]; cmd?: string }) {
  const hits = relevant(await loadLessons($), at).filter(l => !toldLessons.has(l.id)).slice(0, 4)
  hits.forEach(l => toldLessons.add(l.id))
  return hits.length ? recallNote(hits) : ''
}

// On only in a repo that carries this mod (see here.ts); kept in this file, as $ never crosses an import.
async function checkHere($: $, cwd: string) {
  const path = herePath(cwd, 'learn')
  return setHere(path ? await $.fs.exists(path).catch(() => false) : false)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    if (!(await checkHere($, e.cwd))) return next(e)
    try {
      const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd: e.cwd })
      root = top.exitCode === 0 ? top.stdout.trim() : e.cwd
      const url = (await run($, ['git', 'remote', 'get-url', 'origin'])).stdout.trim()
      const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
      if (m) repo = `${m[1]}/${m[2]}`
      await loadBrief($)
      await loadReopened($)
    } catch {
      // No brief this chat; the hooks below still work.
    }
    await $.command.register({ name: 'lessons', description: "List what Claude has learned on this repo (docs/LESSONS.md): mistakes, ideas, lessons turned into checks." })
    await $.command.register({ name: 'retro', description: 'A retrospective of this chat now: mistakes and missed opportunities, written to docs/LESSONS.md.' })
    return next(e)
  })

  on('command.run', { command: 'lessons' }, async $ => {
    const r = await run($, ['node', 'tools/learn.mjs', 'list']).catch(() => undefined)
    return { text: r?.exitCode === 0 ? r.stdout.trim() || 'No lessons yet.' : 'tools/learn.mjs is missing in this checkout.' }
  })
  on('command.run', { command: 'retro' }, async $ => {
    void $.prompt.submit({ text: 'retro', asUser: true })
    return { text: 'Looking back over this chat…' }
  })

  on('prompt.submit', ($, e, next) => {
    if (!isHere()) return next(e)
    const notes: string[] = []
    if (!told) {
      told = true
      const n = firstNote(brief, reopened)
      if (n) notes.push(n)
    }
    if (RETRO_ASK.test(e.text)) {
      notes.push(reflection(sessionSignals.length ? sessionSignals : [{ kind: 'release', text: 'asked for a retrospective of the whole chat' }])
        .replace('Before you finish: a short retrospective, since this turn had', 'The person asked for a retrospective of this chat. What it had so far'))
      asked = true
    } else promptSignals(e.text).forEach(signal)
    return notes.length ? next({ ...e, context: [...(e.context ?? []), notes.join('\n')] }) : next(e)
  }).catch(($, e, next) => next(e))

  // Edits: lessons about the code touched, what wraps it (index.html), and edits that didn't apply.
  on('tool.call', { tool: 'Edit' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const html = /(^|\/)index\.html$/.test(e.file_path)
    let lines: number[] = []
    if (html && !e.replace_all) {
      const before = await $.fs.read(e.file_path).catch(() => '')
      const i = typeof before === 'string' ? before.indexOf(e.old_string) : -1
      if (i >= 0) lines = [before.slice(0, i).split('\n').length]
    }
    const ran = await next(e)
    if (ran.deny !== undefined) return ran
    if (ran.isError) {
      const n = (editMiss.get(e.file_path) ?? 0) + 1
      editMiss.set(e.file_path, n)
      if (n === 2) signal({ kind: 'edit-miss', text: e.file_path.split('/').pop() ?? '' })
      return ran
    }
    const notes = [await recall($, { file: e.file_path, names: identifiers(e.old_string + '\n' + e.new_string) })]
    if (lines.length) {
      const r = await run($, ['node', 'tools/where.mjs', `--at=${lines.join(',')}`, '--json'], 30_000).catch(() => undefined)
      if (r?.exitCode === 0) { try { notes.push(wrapNote(JSON.parse(r.stdout) as At[], toldWraps)) } catch { /* map unavailable */ } }
    }
    return withContext(ran, notes)
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Write' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    const notes = [await recall($, { file: e.file_path, names: identifiers(e.content.slice(0, 20_000)) })]
    // A browser harness written to the scratchpad dies with the chat: the test kit is the place for it.
    if (!toldScratch && /\/scratchpad\/.*\.(c|m)?js$/.test(e.file_path) && /playwright|chromium|newContext|dispatchTouchEvent/i.test(e.content)) {
      toldScratch = true
      notes.push('[learn] A browser harness in the scratchpad is lost when this chat ends (docs/LOG.md lists dozens rebuilt that way). tools/kit.mjs already gives a seeded phone with every /bug report caught, step / scroll / flush and real touch (p.tap, p.swipe): write it as tools/scenarios/<name>.mjs (export default async ({ p, w, fail }) => …) and run `node tools/kit.mjs run tools/scenarios/<name>.mjs`, so the next chat has it.')
    }
    return withContext(ran, notes)
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const cmd = e.command
    const fam = checkFamily(cmd)
    // A commit that carries index.html also carries a fresh docs/MAP.md (the code map can't go stale).
    if (COMMIT.test(cmd) && !/RG_SKIP=1/.test(cmd)) {
      const staged = (await run($, ['git', 'diff', '--cached', '--name-only']).catch(() => undefined))?.stdout ?? ''
      if (/(^|\n)index\.html$/m.test(staged) || /\bindex\.html\b|\s-a\b|\s-am\b|--all\b|\badd\s+(-A|\.)/.test(cmd)) {
        const md = await run($, ['node', 'tools/map.mjs'], 60_000).catch(() => undefined)
        const changed = md?.exitCode === 0 ? (await run($, ['git', 'status', '--porcelain', '--', 'docs/MAP.md']).catch(() => undefined))?.stdout.trim() : ''
        if (changed) await run($, ['git', 'add', 'docs/MAP.md']).catch(() => undefined)
      }
    }
    const headBefore = COMMIT.test(cmd) ? (await run($, ['git', 'rev-parse', 'HEAD']).catch(() => undefined))?.stdout.trim() : ''
    const ran = await next(e)
    if (ran.deny !== undefined) {
      if (/release-guard/.test(ran.deny)) signal({ kind: 'guard', text: ran.deny.split('\n').slice(0, 2).join(' ').slice(0, 160) })
      return ran
    }
    const notes: string[] = []
    if (fam) {
      if (failed(ran)) {
        const n = (failing.get(fam) ?? 0) + 1
        failing.set(fam, n)
        if (n === 3) signal({ kind: 'thrash', text: fam })
      } else if (failing.has(fam)) {
        failing.delete(fam)
        signal({ kind: 'caught', text: fam })
      }
    }
    if (LEARN_CMD.test(cmd) && !ran.isError) {
      recorded = true
      lessonsAt = 0
      const said = String(ran.text ?? '').trim().split('\n')[0]
      if (said) $.ui.toast(`🧠 ${said.slice(0, 160)}`, { timeoutMs: 8000 })
      void loadBrief($)
    }
    if (COMMIT.test(cmd) && !ran.isError) {
      const head = (await run($, ['git', 'rev-parse', 'HEAD']).catch(() => undefined))?.stdout.trim()
      if (head && head !== headBefore) {
        committed = true
        const d = await run($, ['git', 'show', '-U0', '--format=', 'HEAD', '--', 'index.html']).catch(() => undefined)
        if (/^\+const APP_V = "\d+"/m.test(d?.stdout ?? '')) signal({ kind: 'release', text: `APP_V ${(d?.stdout.match(/^\+const APP_V = "(\d+)"/m) ?? [])[1] ?? ''}` })
        // Hazards from the hard-won rules (v198/v216/v237) that this commit adds.
        if (d?.stdout) {
          const hz = await run($, ['node', 'tools/where.mjs', '--check', '--base=HEAD~1'], 90_000).catch(() => undefined)
          if (hz && hz.exitCode !== 0 && /^\[v\d+\]/m.test(hz.stdout)) notes.push(`[learn] tools/where.mjs --check on this commit:\n${hz.stdout.trim().split('\n').slice(0, 8).join('\n')}\nFix it in the next commit unless it is deliberate.`)
        }
      }
    }
    if (!fam && !LEARN_CMD.test(cmd) && !PUSH.test(cmd) && !COMMIT.test(cmd)) notes.push(await recall($, { cmd }))
    return withContext(ran, notes)
  }).catch(($, e, next) => next(e))

  // Closing a problem report: did it say whether Nexi can now catch it himself (CLAUDE.md)?
  on('tool.call', { tool: 'mcp__github__add_issue_comment' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const a = e as unknown as { body?: string; issue_number?: number }
    const ran = await next(e)
    if (ran.deny === undefined && !ran.isError && /fixed in v\d+/i.test(a.body ?? '') && !SAID_DETECTOR.test(a.body ?? ''))
      signal({ kind: 'fixed-issue', text: `#${a.issue_number ?? '?'}` })
    return ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'mcp__github__merge_pull_request' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const ran = await next(e)
    if (ran.deny === undefined && !ran.isError && !/not mergeable|failed|error/i.test(ran.text ?? '')) signal({ kind: 'release', text: 'merged to main' })
    return ran
  }).catch(($, e, next) => next(e))

  // The end of a turn: a short retrospective when the turn earned one; lessons committed so the next chat has them.
  on('classic.Stop', async ($, e, next) => {
    if (!isHere()) return next(e)
    const r = await next(e)
    if (r.block || e.stop_hook_active) return r
    if (!asked) {
      const deferred = deferrals(e.last_assistant_message ?? '')
      const all: Signal[] = [...signals, ...deferred.map(text => ({ kind: 'deferred' as const, text }))]
      if (!recorded && (all.some(s => STRONG.has(s.kind)) || (deferred.length && committed))) {
        asked = true
        return { ...r, block: reflection(all) }
      }
    }
    if (recorded && !askedCommit) {
      const st = await run($, ['git', 'status', '--porcelain', '--', 'docs/LESSONS.md']).catch(() => undefined)
      if (st?.stdout.trim()) {
        askedCommit = true
        return { ...r, block: '[learn] docs/LESSONS.md has lessons that are not committed. Commit it (and push if this branch is pushed): lessons only reach the next chat through git. Then finish as usual.' }
      }
    }
    return r
  }).catch(($, e, next) => next(e))

  on('turn.complete', ($, e, next) => {
    if (!isHere()) return next(e)
    if (!e.agentId) {
      signals = []
      asked = askedCommit = recorded = committed = false
      failing.clear()
      editMiss.clear()
    }
    return next(e)
  })
}
