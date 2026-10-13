import { describe, expect, mock, test } from 'claude-code/testing'
import { asText, openCount, parse } from '../hooks/sift.ts'

const LONG = `I fixed the layout bug and pushed it. ${'Details of everything I checked along the way. '.repeat(12)} One thing failed: the iPhone check timed out. Could you open the app on your phone and tell me if the list scrolls? We could also add a dark-mode toggle later.`
const SIFTED = '```json\n{"gist":"Fixed the layout bug and pushed it","attention":["iPhone check timed out"],"actions":["Open the app on your phone and check the list scrolls"],"ideas":["Add a dark-mode toggle"]}\n```'

function world(on: any, answer = SIFTED, placed = true) {
  const calls: any[] = []
  const toasts: string[] = []
  mock.store(on)
  const clock = mock.clock(on, { now: 1000 })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  on('ui.open', () => ({ value: placed ? { isPlaced: true } : { isPlaced: false, reason: 'narrow' } }))
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text); return { value: undefined } })
  on('ui.status', () => ({ value: undefined }))
  on('model.complete', ($: unknown, e: any) => { calls.push(e); return { value: { isAnswered: true, text: answer, usage: { input_tokens: 1, output_tokens: 1, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } } } })
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  on('prompt.submit', ($: unknown, e: any) => ({ text: e.text }))
  on('ui.render', ($: any, e: any) => { const { Text } = $.ui.resolve(e); return <Text>engine</Text> })
  return { calls, toasts, clock }
}

async function reply($: any, clock: any, answer = LONG, extra: any = {}) {
  await $.turn.complete({ answer, durationMs: 1, isAborted: false, turnId: 't', reason: 'answer', ...extra })
  await clock.advance(50)
}

const pane = (surface: 'terminal' | 'desktop' | 'mobile') => ({
  plugin: 'heads-up', surface, component: 'Pane', requestId: 'heads-up',
  props: { title: 'Heads-up', isFocused: false, bodyColumns: 60, placement: 'dock' },
}) as any

describe('heads-up', () => {
  test('parse reads fenced JSON, trims and caps lists, refuses junk', () => {
    expect(parse(SIFTED)?.actions).toEqual(['Open the app on your phone and check the list scrolls'])
    expect(parse('no json here')).toBeUndefined()
    expect(parse('{"gist": 3, "attention": "x", "actions": [1, " a  b "], "ideas": []}')).toEqual({ gist: '', attention: [], actions: ['a b'], ideas: [] })
    expect(parse(`{"ideas": ${JSON.stringify(Array(10).fill('i'))}}`)?.ideas.length).toBe(6)
    const card = { id: 'c', at: 0, ask: 'Fix it', gist: 'Done', attention: ['x'], actions: ['y', 'z'], ideas: [], done: ['a1'] }
    expect(openCount([card])).toBe(2)
    expect(asText(card)).toContain('☑ z')
    expect(asText({ ...card, attention: [], actions: [] })).toContain('Nothing needs you.')
  })

  for (const surface of ['terminal', 'desktop', 'mobile'] as const) {
    test(`a long reply becomes a panel of only what needs you on ${surface}`, async ($, on) => {
      const { calls, clock } = world(on)
      await $.session.start({ cwd: '/repo', surface: null, isInteractive: true } as any)
      await $.prompt.submit({ text: 'fix the layout bug on the queue screen' } as any)
      await reply($, clock)
      expect(calls.length).toBe(1)
      expect(calls[0].model).toBe('haiku')
      expect(calls[0].prompt).toContain('fix the layout bug')
      const ui = await $.ui.mount(pane(surface))
      expect(await ui.find({ type: 'Text', text: /Fix the layout bug on the queue screen/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Needs your attention/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /iPhone check timed out/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Add a dark-mode toggle/ })).toBeDefined()
      // Ticking a to-do marks it done.
      await ui.press({ key: (await ui.find({ type: 'Button', text: /Open the app/ }))!.key })
      expect(await ui.find({ type: 'Text', text: '☑' })).toBeDefined()
      await ui.unmount()
    })
  }

  test('short replies, helpers\' replies and interrupted turns cost no model call', async ($, on) => {
    const { calls, clock } = world(on)
    await reply($, clock, 'Done, pushed.')
    await reply($, clock, LONG, { agentId: 'a1' })
    await reply($, clock, LONG, { reason: 'aborted', isAborted: true })
    expect(calls.length).toBe(0)
  })

  test('a reply with nothing for the person opens no panel and says so', async ($, on) => {
    const { clock } = world(on, '{"gist":"Renamed the files","attention":[],"actions":[],"ideas":[]}')
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /Renamed the files/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Needs your attention/ })).toBeUndefined()
    await ui.unmount()
  })

  test('/heads-up answers as text (phones), toasts when no panel fits, and /heads-up off stops it', async ($, on) => {
    const { calls, toasts, clock } = world(on, SIFTED, false)
    await reply($, clock)
    expect(toasts.some(t => /2 things need you/.test(t))).toBe(true)
    const r: any = await $.command.run({ command: 'heads-up', args: '' } as any)
    expect(r.text).toContain('👉 For you to do')
    expect(r.text).toContain('☐ Open the app on your phone')
    await $.command.run({ command: 'heads-up', args: 'off' } as any)
    await reply($, clock)
    expect(calls.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'on' } as any)
    await reply($, clock)
    expect(calls.length).toBe(2)
  })
})
