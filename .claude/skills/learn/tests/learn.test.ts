import { describe, expect, mock, test } from 'claude-code/testing'
import { deferrals, estimateIn, parseLessons, promptSignals, relevant } from '../hooks/signals.ts'

const NOW = Date.parse('2026-10-11T12:00:00Z')

const LESSONS = `# Lessons

## Mistakes

- **L1** · 2026-10-11 · v267 · \`bugLayoutSoon\`, \`tools/kit.mjs\` — Wait 2.6s per screen in sweeps: the layout check runs 1.6s after a change
- **L2** ×3 · 2026-10-11 · v267 · \`openFull\` — Wrappers run around openFull: read docs/MAP.md first

## Missed opportunities

- [ ] **M1** · 2026-10-11 · v267 · \`.github/scripts/webkitcheck.mjs\` — The WebKit check could import tools/kit.mjs
- [x] **M2** · 2026-10-11 · v267 · \`tools/kit.mjs\` — Keep harnesses in tools/ (done: v268)

## Promoted to checks

- **L3** → \`tools/where.mjs --check\` · 2026-10-11 · v267 · \`APP_V\` — Never assign to a top-level const
`

const BRIEF = { lessons: 2, promoted: 1, open: [{ id: 'M1', text: 'The WebKit check could import tools/kit.mjs' }], openCount: 1, promote: [{ id: 'L2', n: 3, text: 'Wrappers run around openFull' }], stale: [], big: false }
const AT = [{ line: 9540, script: 2, fns: [{ name: 'openFull', from: 9537, to: 9766, wrappedAfter: [11564, 12709] }] }]

type W = { etaOpen?: unknown[]; etaTip?: string; lessonsDirty?: boolean; commitDiff?: string; mapCheck?: { code: number; out: string }; reopened?: unknown[] }
function world(on: any, w: W = {}) {
  mock.store(on)
  // The repo carries the mod (.claude/skills/<name>), so it is on.
  on('fs.exists', () => ({ value: true }))
  mock.clock(on, { now: NOW })
  let head = 1
  const runs: string[] = []
  const contexts: string[] = []
  const toasts: string[] = []
  const ok = (stdout = '', exitCode = 0) => ({ value: { exitCode, stdout, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } })
  on('process.run', ($: unknown, e: any) => {
    const a = (e.argv as string[]).join(' ')
    runs.push(a)
    if (a === 'git rev-parse --show-toplevel') return ok('/repo\n')
    if (a === 'git remote get-url origin') return ok('https://github.com/shamlawy/Play-next.git\n')
    if (a === 'git rev-parse HEAD') return ok(`h${head}\n`)
    if (a.startsWith('git status --porcelain -- docs/MAP.md')) return ok(' M docs/MAP.md\n')
    if (a.startsWith('git status --porcelain')) return ok(w.lessonsDirty ? ' M docs/LESSONS.md\n' : '')
    if (a.startsWith('git show -U0')) return ok(w.commitDiff ?? '')
    if (a.startsWith('git diff --cached --name-only')) return ok('index.html\n')
    if (a === 'node tools/learn.mjs brief') return ok(JSON.stringify(BRIEF))
    if (a.startsWith('node tools/eta.mjs start')) return ok('E1 started: about 10 min, kit\n')
    if (a === 'node tools/eta.mjs open --json') return ok(JSON.stringify(w.etaOpen ?? []))
    if (a === 'node tools/eta.mjs tip') return ok(w.etaTip ?? '')
    if (a.startsWith('node tools/where.mjs --at=')) return ok(JSON.stringify(AT))
    if (a === 'node tools/map.mjs') return ok('docs/MAP.md: 154 blocks\n')
    if (a.startsWith('node tools/where.mjs --check')) return ok(w.mapCheck?.out ?? 'No new hazards', w.mapCheck?.code ?? 0)
    return ok('')
  })
  on('fs.read', ($: unknown, e: any) => ({ value: String(e.path).endsWith('LESSONS.md') ? LESSONS : 'line1\nline2\n' + 'function openFull(id) {\n' }))
  on('http.fetch', () => ({ value: { status: 200, ok: true, headers: {}, text: JSON.stringify({ items: w.reopened ?? [] }) } }))
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  on('ui.status', () => ({ value: undefined }))
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text); return { value: undefined } })
  on('prompt.submit', ($: unknown, e: any) => { contexts.push((e.context ?? []).join('\n')); return { text: e.text, context: e.context } })
  on('classic.Stop', () => ({}))
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  on('tool.call', ($: unknown, e: any) => {
    if (e.tool === 'Bash' && /git commit/.test(e.command)) head++
    if (e.tool === 'Bash' && /FAILING/.test(e.command)) return { result: 'x', text: 'FAIL', isError: true }
    if (e.tool === 'Bash' && /tools\/learn\.mjs add/.test(e.command)) return { result: 'x', text: 'Learned L4: something' }
    return { result: 'done', text: 'done' }
  })
  const steps: string[] = []
  on('turn.step', async function* ($: unknown, e: any) {
    return { turnId: e.turnId, index: e.index, answer: steps.shift() ?? '', toolUses: [], stopReason: 'end_turn', usage: null }
  })
  return { runs, contexts, toasts, steps }
}
const step = async ($: any, agentId?: string) => { for await (const _ of $.turn.step({ turnId: 't', index: 0, model: 'm', messageCount: 1, agentId } as any)) { /* stream */ } }
const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
const stop = ($: any, last = 'Done.') => $.classic.Stop({ stop_hook_active: false, last_assistant_message: last } as any)
const endTurn = ($: any) => $.turn.complete({ answer: '', durationMs: 1, isAborted: false, turnId: 't' } as any)

describe('learn', () => {
  test('time estimates: read from the reply, background work told apart, re-estimates seen', () => {
    expect(estimateIn("⏱ About 10 min: I'll film both videos and send them.")).toEqual({ kind: 'start', min: 10, bg: false, what: "I'll film both videos and send them." })
    expect(estimateIn('> ⏱ About 25 min, so around 4:12 PM UAE time. Four helpers search in parallel.')).toMatchObject({ kind: 'start', min: 25, bg: true })
    expect(estimateIn('⏱ About 1–3 h: a new screen')).toMatchObject({ min: 180 })
    expect(estimateIn('⏱ Taking longer: about 5 more min, the render is at 76%')).toEqual({ kind: 'more', min: 5 })
    expect(estimateIn('It took about 10 min last time.')).toBeUndefined()
  })

  test('a ⏱ estimate is timed by itself: the turn that gave it ends it, background work gets one reminder per turn', async ($, on) => {
    const w0: W = { etaTip: '[learn] Time estimates (docs/ETA.md): 2 recorded' }
    const w = world(on, w0)
    await start($)
    await $.prompt.submit({ text: 'run the sweep' } as any)
    expect(w.contexts[0]).toContain('Time estimates (docs/ETA.md)')
    w.steps.push("⏱ About 10 min: I'll run the sweep and send the sheet.")
    await step($)
    expect(w.runs).toContain("node tools/eta.mjs start --min=10 I'll run the sweep and send the sheet.")
    expect(w.toasts.some(t => t.startsWith('⏱ E1 started'))).toBe(true)
    expect((await stop($)).block).toBeUndefined()
    await endTurn($)
    expect(w.runs).toContain('node tools/eta.mjs end E1')
    // Background work: not ended by the turn; the next turn's end asks once.
    await $.prompt.submit({ text: 'film the demo' } as any)
    w.steps.push('⏱ About 8 min, so around 4:12 PM UAE time: the render runs in the background.')
    await step($)
    expect(w.runs.filter(r => r.startsWith('node tools/eta.mjs start')).pop()).toContain('--bg')
    w0.etaOpen = [{ id: 'E1', said: '8 min', at: '', bg: true, what: 'the render' }]
    expect((await stop($)).block).toBeUndefined()
    const ends = w.runs.filter(r => r.startsWith('node tools/eta.mjs end')).length
    await endTurn($)
    expect(w.runs.filter(r => r.startsWith('node tools/eta.mjs end')).length).toBe(ends)
    await $.prompt.submit({ text: 'is it done?' } as any)
    const r1: any = await stop($)
    expect(r1.block).toContain('Time estimate still open')
    expect(r1.block).toContain('node tools/eta.mjs end <id>')
    expect((await stop($)).block).toBeUndefined()
    // A helper's own words never start one.
    await endTurn($)
    w.steps.push('⏱ About 3 min: checking')
    const starts = w.runs.filter(r => r.startsWith('node tools/eta.mjs start')).length
    await step($, 'a1')
    expect(w.runs.filter(r => r.startsWith('node tools/eta.mjs start')).length).toBe(starts)
  })

  test('the first message knows the lessons, what keeps happening and the fixes that did not hold', async ($, on) => {
    const w = world(on, { reopened: [{ number: 412, title: '🐞 Cut off (Android): A button is cut off [bug:ab12]' }] })
    await start($)
    await $.prompt.submit({ text: 'hi' } as any)
    await $.prompt.submit({ text: 'and this' } as any)
    expect(w.contexts[0]).toContain('docs/LESSONS.md: 2 lessons, 1 turned into checks, 1 open idea')
    expect(w.contexts[0]).toContain('L2 ×3')
    expect(w.contexts[0]).toContain("Fixes that didn't hold")
    expect(w.contexts[0]).toContain('#412 🐞 Cut off (Android): A button is cut off')
    expect(w.contexts[0]).toContain("don't start them unasked")
    expect(w.contexts[1]).toBe('')
  })

  test('a correction earns one short retrospective at the end of the turn', async ($, on) => {
    world(on)
    await start($)
    await $.prompt.submit({ text: "no, that's not what I asked, the queue still lags" } as any)
    const r1: any = await stop($)
    expect(r1.block).toContain('the person corrected you')
    expect(r1.block).toContain('node tools/learn.mjs add --kind=mistake')
    const r2: any = await stop($)
    expect(r2.block).toBeUndefined()
  })

  test('a quiet turn ends quietly', async ($, on) => {
    world(on)
    await start($)
    await $.prompt.submit({ text: 'add a dark border to the cards' } as any)
    await $.tool.call({ tool: 'Bash', command: 'ls' } as any)
    const r: any = await stop($)
    expect(r.block).toBeUndefined()
  })

  test('a check that failed and then passed is a caught mistake; recording it ends the ask, an uncommitted lesson is flagged once', async ($, on) => {
    const w = world(on, { lessonsDirty: true })
    await start($)
    await $.prompt.submit({ text: 'fix the sweep' } as any)
    await $.tool.call({ tool: 'Bash', command: 'node tools/kit.mjs sweep --widths 412 # FAILING' } as any)
    await $.tool.call({ tool: 'Bash', command: 'node tools/kit.mjs sweep --widths 412' } as any)
    const r1: any = await stop($)
    expect(r1.block).toContain('a check failed, then passed after a change: “kit sweep”')
    await $.tool.call({ tool: 'Bash', command: 'node tools/learn.mjs add --kind=mistake --anchors=x "a real lesson here"' } as any)
    expect(w.toasts.some(t => t.includes('Learned L4'))).toBe(true)
    const r2: any = await stop($)
    expect(r2.block).toContain('docs/LESSONS.md has lessons that are not committed')
    const r3: any = await stop($)
    expect(r3.block).toBeUndefined()
  })

  test('a release asks about missed opportunities; signals reset with the turn', async ($, on) => {
    world(on, { commitDiff: '+const APP_V = "268"\n' })
    await start($)
    await $.prompt.submit({ text: 'ship it' } as any)
    await $.tool.call({ tool: 'Bash', command: 'git add index.html && git commit -m "v268"' } as any)
    const r: any = await stop($, 'Shipped v268.')
    expect(r.block).toContain('a release went out')
    expect(r.block).toContain('what would have made it better or arrive sooner')
    await endTurn($)
    await $.prompt.submit({ text: 'thanks' } as any)
    const r2: any = await stop($)
    expect(r2.block).toBeUndefined()
  })

  test('a commit with index.html refreshes the code map first and reports new hazards', async ($, on) => {
    const w = world(on, { commitDiff: '+foo()\n', mapCheck: { code: 1, out: '[v216] line 9: APP_V is a const (line 4) and this assigns to it' } })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git commit -am "x"' } as any)
    expect(w.runs.indexOf('node tools/map.mjs')).toBeLessThan(w.runs.indexOf('git add docs/MAP.md'))
    expect(w.runs).toContain('git add docs/MAP.md')
    expect((r.context ?? []).join('\n')).toContain('[v216] line 9')
  })

  test('editing index.html brings back the lessons and the wrappers for that code, once each', async ($, on) => {
    world(on)
    await start($)
    const r1: any = await $.tool.call({ tool: 'Edit', file_path: '/repo/index.html', old_string: 'function openFull(id) {', new_string: 'function openFull(id) { bugLayoutSoon();' } as any)
    const c1 = (r1.context ?? []).join('\n')
    expect(c1).toContain('L2 ×3: Wrappers run around openFull')
    expect(c1).toContain('L1: Wait 2.6s per screen')
    expect(c1).toContain('openFull (lines 9537-9766) is wrapped by 2 later scripts')
    const r2: any = await $.tool.call({ tool: 'Edit', file_path: '/repo/index.html', old_string: 'function openFull(id) {', new_string: 'function openFull(id) { ' } as any)
    expect((r2.context ?? []).join('\n')).not.toContain('L2')
  })

  test('a fix comment that skips the detector question is caught; one that answers it is not', async ($, on) => {
    world(on)
    await start($)
    await $.prompt.submit({ text: 'fix #412' } as any)
    await $.tool.call({ tool: 'mcp__github__add_issue_comment', owner: 'shamlawy', repo: 'Play-next', issue_number: 412, body: 'Fixed in v268.' } as any)
    const r: any = await stop($)
    expect(r.block).toContain('#412')
    await endTurn($)
    await $.prompt.submit({ text: 'next' } as any)
    await $.tool.call({ tool: 'mcp__github__add_issue_comment', owner: 'shamlawy', repo: 'Play-next', issue_number: 413, body: "Fixed in v268. I've learned to spot this myself now." } as any)
    const r2: any = await stop($)
    expect(r2.block).toBeUndefined()
  })

  test('a scratchpad browser harness gets one nudge toward tools/scenarios', async ($, on) => {
    world(on)
    await start($)
    const r: any = await $.tool.call({ tool: 'Write', file_path: '/tmp/x/scratchpad/h.cjs', content: "const { chromium } = require('playwright')" } as any)
    expect((r.context ?? []).join('\n')).toContain('tools/scenarios/')
    const r2: any = await $.tool.call({ tool: 'Write', file_path: '/tmp/x/scratchpad/h2.cjs', content: "require('playwright')" } as any)
    expect((r2.context ?? []).join('\n')).not.toContain('tools/scenarios/')
  })

  test('only the person asking for something automatic earns a retrospective, not text the app or a session put in', async ($, on) => {
    world(on)
    await start($)
    // A system-reminder appended to the person's message: "runs on its own" is the app's, not theirs.
    await $.prompt.submit({ text: 'ok thanks\n<system-reminder>\nLinked sessions now: fix-x (working). It runs on its own; a note reaches you when it ends a turn.\n</system-reminder>' } as any)
    expect(((await stop($)) as any).block).toBeUndefined()
    await endTurn($)
    // A side-session note quoting a child's report.
    await $.prompt.submit({ text: '<side-sessions-event>\nfix-x ended a turn. Its report: "Merged PR 512. Left an idea to make that automatic: a check on every merge."\n</side-sessions-event>' } as any)
    expect(((await stop($)) as any).block).toBeUndefined()
    await endTurn($)
    // A brief from the session that started this one.
    await $.prompt.submit({ text: 'Background (from the session that started this one):\nThe owner wants it to run by itself.\n\nTask:\nMake the sweep automatic.' } as any)
    expect(((await stop($)) as any).block).toBeUndefined()
    await endTurn($)
    // The person, in their own words.
    await $.prompt.submit({ text: 'can the price check run automatically every morning?' } as any)
    const r: any = await stop($)
    expect(r.block).toContain('the person asked for something to happen by itself')
    expect(r.block).toContain('run automatically every morning')
  })

  test('/retro asks for a retrospective of the whole chat', async ($, on) => {
    const w = world(on)
    await start($)
    await $.prompt.submit({ text: 'hi' } as any)
    await $.prompt.submit({ text: 'retro' } as any)
    expect(w.contexts[1]).toContain('The person asked for a retrospective of this chat')
  })
})

describe('signals', () => {
  test('corrections and wishes for automatic things', () => {
    expect(promptSignals("no that's wrong").map(s => s.kind)).toEqual(['correction'])
    expect(promptSignals('it still lags when I swipe').map(s => s.kind)).toEqual(['correction'])
    expect(promptSignals('can it do that by itself?').map(s => s.kind)).toEqual(['auto'])
    expect(promptSignals('add a button for the timer')).toEqual([])
    expect(promptSignals('the price card looks broken on the iPad')).toEqual([])
    expect(promptSignals('you broke the queue').map(s => s.kind)).toEqual(['correction'])
    expect(promptSignals('/release-check')).toEqual([])
  })
  test("only the person's own words are scanned", () => {
    const reminder = '<system-reminder>Linked sessions now: a (working). It runs on its own; a note reaches you when it ends a turn.</system-reminder>'
    expect(promptSignals(`looks good ${reminder}`)).toEqual([])
    expect(promptSignals(`${reminder} can it do that by itself?`).map(s => s.kind)).toEqual(['auto'])
    expect(promptSignals('<side-sessions-event>b ended a turn: "an idea to make that automatic, and the rest of the report"</side-sessions-event>')).toEqual([])
    expect(promptSignals('b ended a turn. Its report: "Merged it. Left an idea to make that automatic on every merge."')).toEqual([])
    expect(promptSignals('> it runs on its own now\n\nnice')).toEqual([])
    expect(promptSignals('<task-notification>the job runs on its own and is cut off')).toEqual([])
    expect(promptSignals('<pasted_content id="x">you broke it, do it automatically</pasted_content> what is this log?')).toEqual([])
    // Messages from another session or an agent, whatever they say.
    expect(promptSignals('Background (from the session that started this one):\nmake it automatic')).toEqual([])
    expect(promptSignals('Handoff note from an earlier chat\nThe price pings now run by themselves.')).toEqual([])
    expect(promptSignals('do it automatically', 'peer')).toEqual([])
    expect(promptSignals('do it automatically', 'composer').map(s => s.kind)).toEqual(['auto'])
  })
  test('things put off in a reply', () => {
    const d = deferrals('Fixed the queue. The calendar could also use the same fix, worth doing later. All tests pass.')
    expect(d.length).toBe(1)
    expect(d[0]).toContain('could also')
    expect(deferrals('All good.')).toEqual([])
  })
  test('the lessons file reads back the way tools/learn.mjs writes it', () => {
    const ls = parseLessons(LESSONS)
    expect(ls.map(l => `${l.id}:${l.kind}:${l.n}:${l.open}`)).toEqual(['L1:mistake:1:false', 'L2:mistake:3:false', 'M1:missed:1:true', 'M2:missed:1:false', 'L3:promoted:1:false'])
    expect(relevant(ls, { file: '/repo/.github/scripts/webkitcheck.mjs' }).map(l => l.id)).toEqual(['M1'])
    expect(relevant(ls, { names: ['openFull'] }).map(l => l.id)).toEqual(['L2'])
    expect(relevant(ls, { cmd: 'node tools/kit.mjs sweep' }).map(l => l.id)).toEqual(['L1'])
  })
})
