import { describe, expect, mock, test } from 'claude-code/testing'

const HOUR = 3_600_000
const at = (ms: number) => new Date(ms).toISOString()

function world(on: any, tokens: number, five: number, week: number, limits = true) {
  mock.store(on)
  const clock = mock.clock(on, { now: Date.parse('2026-10-05T12:00:00Z') })
  const now = clock.now()
  on('session.usage', () => ({ value: {
    startedAt: 0,
    context: { tokens, window: 1_000_000, percent: Math.round(tokens / 10_000) },
    rateLimits: !limits ? [] : [
      { kind: 'five_hour', percentUsed: five, resetsAt: at(now + 2 * HOUR + 10 * 60_000) },
      { kind: 'seven_day', percentUsed: week, resetsAt: at(now + 3 * 24 * HOUR) },
    ],
  } }))
  const seen: string[] = []
  on('prompt.submit', ($: unknown, e: any) => {
    seen.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  return seen
}

describe('usage-guard', () => {
  test('a small chat only carries the meter', async ($, on) => {
    const seen = world(on, 80_000, 12, 5)
    await $.prompt.submit({ text: 'hi' })
    expect(seen[0]).toContain('5h 12% (resets in 2h 10m) · week 5% (resets in 3d 0h) · chat 80k')
    expect(seen[0]).not.toContain('fresh chat')
  })

  test('past 200k a gentle nudge, once per 50k', async ($, on) => {
    const seen = world(on, 210_000, 12, 5)
    await $.prompt.submit({ text: 'one' })
    await $.prompt.submit({ text: 'two' })
    expect(seen[0]).toContain('past 200k')
    expect(seen[1]).not.toContain('past 200k')
  })

  test('past 300k every message recommends a fresh chat', async ($, on) => {
    const seen = world(on, 320_000, 12, 5)
    await $.prompt.submit({ text: 'one' })
    await $.prompt.submit({ text: 'two' })
    expect(seen[0]).toContain('past the 300k line')
    expect(seen[1]).toContain('past the 300k line')
  })

  test('a limit past 75% is announced once', async ($, on) => {
    const seen = world(on, 80_000, 78, 5)
    await $.prompt.submit({ text: 'one' })
    await $.prompt.submit({ text: 'two' })
    expect(seen[0]).toContain('5h usage limit just passed 75%')
    expect(seen[1]).not.toContain('just passed')
  })

  test('typing handoff asks for the handoff', async ($, on) => {
    const seen = world(on, 80_000, 12, 5)
    await $.prompt.submit({ text: 'handoff' })
    await $.prompt.submit({ text: 'please handoff the docs later' })
    expect(seen[0]).toContain('create_session')
    expect(seen[1]).not.toContain('create_session')
  })

  test('every reply ends with the usage line until "hide meter"', async ($, on) => {
    const seen = world(on, 80_000, 12, 5)
    await $.prompt.submit({ text: 'hi' })
    await $.prompt.submit({ text: 'hide meter' })
    await $.prompt.submit({ text: 'hi again' })
    await $.prompt.submit({ text: 'show the usage meter' })
    expect(seen[0]).toContain('very last line, exactly as written, after a blank line: ⛽ 5h 12%')
    expect(seen[1]).toContain('now off')
    expect(seen[1]).not.toContain('very last line')
    expect(seen[2]).not.toContain('very last line')
    expect(seen[3]).toContain('now on')
    expect(seen[3]).toContain('very last line')
  })

  test('no limit readings says so', async ($, on) => {
    const seen = world(on, 225_000, 0, 0, false)
    await $.prompt.submit({ text: 'hi' })
    expect(seen[0]).toContain('⛽ chat 225k · 5h/week limits not reported yet')
  })

  test('the usage window draws a bar per limit on every app, the phone too', async ($, on) => {
    world(on, 210_000, 78, 17)
    for (const surface of ['terminal', 'desktop', 'vscode', 'mobile'] as const) {
      const ui = await $.ui.mount({
        plugin: 'usage-guard', surface, component: 'Pane', requestId: 'usage',
        props: { title: '⛽ Usage', isFocused: false, bodyColumns: 30, placement: 'inline' } as any,
      })
      expect(await ui.find({ type: 'Text', text: /^78% · resets 2h 10m$/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /^17% · resets 3d 0h$/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /^210k of 300k$/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /^█{17}░{7}$/ })).toBeDefined()
      await ui.unmount()
    }
  })
})
