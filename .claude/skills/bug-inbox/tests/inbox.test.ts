import { describe, expect, mock, test } from 'claude-code/testing'

const NOW = Date.parse('2026-10-07T12:00:00Z')
const ago = (h: number) => new Date(NOW - h * 3_600_000).toISOString()

const ISSUES = [
  { number: 330, title: '🐞 Reported (iPhone): The search box hides behind the keyboard [bug:aa11bb22]', created_at: ago(3) },
  { number: 314, title: '🐞 Slow (Android): A 200ms stutter on Queue [bug:32c30627]', created_at: ago(30) },
  { number: 215, title: "🐞 Crash (iPad): null is not an object (evaluating 'AC.state') [bug:43a]", created_at: ago(100) },
  { number: 120, title: '🐞 Cut off: Part of the screen stayed blank while scrolling [bug:5291ce01]', created_at: ago(200) },
  { number: 99, title: 'A pull request', created_at: ago(1), pull_request: {} },
]

function world(on: any, issues: unknown[], store?: Record<string, unknown>) {
  mock.store(on, store)
  const clock = mock.clock(on, { now: NOW })
  let list = issues
  const contexts: string[] = []
  on('process.run', () => ({ value: { exitCode: 0, stdout: 'https://github.com/shamlawy/Play-next.git\n', stderr: '', isStdoutTruncated: false, isStderrTruncated: false } }))
  on('http.fetch', () => ({ value: { status: 200, ok: true, headers: {}, text: JSON.stringify(list) } }))
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  on('ui.status', () => ({ value: undefined }))
  on('prompt.submit', ($: unknown, e: any) => {
    contexts.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  return { clock, contexts, set: (l: unknown[]) => { list = l } }
}

const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })

describe('bug-inbox', () => {
  test('a fresh container: the first message lists what is open and names the recent hand-filed one', async ($, on) => {
    const w = world(on, ISSUES)
    await start($)
    await $.prompt.submit({ text: 'hi' } as any)
    await $.prompt.submit({ text: 'again' } as any)
    expect(w.contexts[0]).toContain('Open problem reports (auto-bug issues): 4 (')
    expect(w.contexts[0]).toContain('#215 Crash (iPad)')
    expect(w.contexts[0]).toContain('New since the last chat:\n- #330 Reported (iPhone): The search box hides behind the keyboard')
    expect(w.contexts[0]).not.toContain('pull request')
    expect(w.contexts[1]).toBe('')
  })

  test('nothing new since the last chat: listed, but quietly', async ($, on) => {
    const w = world(on, ISSUES, { seen: [330, 314, 215, 120] })
    await start($)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('mention the inbox only if they ask')
    expect(w.contexts[0]).not.toContain('New since the last chat')
  })

  test('a hand-filed report arriving mid-chat is passed on once', async ($, on) => {
    const w = world(on, ISSUES, { seen: [330, 314, 215, 120] })
    await start($)
    await $.prompt.submit({ text: 'hi' } as any)
    w.set([{ number: 331, title: '🐞 Reported (Android): Price ping came twice [bug:cc33]', created_at: ago(0) }, ...ISSUES])
    await w.clock.advance(31 * 60_000)
    await $.prompt.submit({ text: 'next' } as any)
    await $.prompt.submit({ text: 'and' } as any)
    expect(w.contexts[1]).toContain('#331 Reported (Android): Price ping came twice')
    expect(w.contexts[2]).toBe('')
  })

  test('/inbox lists everything, hand-filed first', async ($, on) => {
    world(on, ISSUES)
    await start($)
    const r: any = await $.command.run({ command: 'inbox', args: '' } as any)
    expect(r.text).toMatch(/^4 open problem reports/)
    expect(r.text.indexOf('Crash (1)')).toBeLessThan(r.text.indexOf('Slow (1)'))
  })
})
