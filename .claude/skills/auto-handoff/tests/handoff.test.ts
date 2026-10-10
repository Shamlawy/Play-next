import { describe, expect, mock, test } from 'claude-code/testing'

function world(on: any, tokens: number) {
  mock.store(on)
  on('session.usage', () => ({ value: {
    startedAt: 0,
    context: { tokens, window: 1_000_000, percent: Math.round(tokens / 10_000) },
    rateLimits: [],
  } }))
  const seen: string[] = []
  on('prompt.submit', ($: unknown, e: any) => {
    seen.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  return seen
}

describe('auto-handoff', () => {
  test('a chat under 300k carries no note', async ($, on) => {
    const seen = world(on, 250_000)
    await $.prompt.submit({ text: 'hi' })
    expect(seen[0]).toBe('')
  })

  test('past 300k it starts a new chat by itself, once', async ($, on) => {
    const seen = world(on, 320_000)
    await $.prompt.submit({ text: 'fix the tour' })
    await $.prompt.submit({ text: 'thanks' })
    expect(seen[0]).toContain('past the 300k line')
    expect(seen[0]).toContain('create_session')
    expect(seen[1]).toBe('')
  })

  test('typing handoff asks for the handoff at any size', async ($, on) => {
    const seen = world(on, 80_000)
    await $.prompt.submit({ text: 'handoff' })
    await $.prompt.submit({ text: 'please handoff the docs later' })
    expect(seen[0]).toContain('create_session')
    expect(seen[1]).toBe('')
  })
})
