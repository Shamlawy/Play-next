import { describe, expect, mock, test } from 'claude-code/testing'
import { asText, hasAny, openCount, parse, pick, pickText } from '../hooks/sift.ts'

const LONG = `I fixed the layout bug and pushed it. ${'Details of everything I checked along the way. '.repeat(12)} One thing failed: the iPhone check timed out. Could you open the app on your phone and tell me if the list scrolls? We could also add a dark-mode toggle later.`
const SIFTED = '```json\n{"gist":"Fixed the layout bug and pushed it","attention":[{"line":"iPhone check timed out","why":"The WebKit run stopped after 10 minutes, so the fix is untested on Safari."}],"actions":["Open the app on your phone and check the list scrolls"],"ideas":[{"line":"Add a dark-mode toggle","why":"Offered as a follow-up, not started."}]}\n```'

function world(on: any, answer = SIFTED, placed = true, mode: 'panel' | 'box' | 'auto' = 'panel', cloud = false) {
  const calls: any[] = []
  const toasts: string[] = []
  const statuses: (string | undefined)[] = []
  const opens: number[] = []
  mock.store(on, mode === 'auto' ? {} : { mode })
  on('env.get', ($: unknown, e: any) => ({ value: e.name === 'CLAUDE_CODE_REMOTE' && cloud ? 'true' : undefined }))
  const clock = mock.clock(on, { now: 1000 })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  on('ui.open', () => { opens.push(1); return { value: placed ? { isPlaced: true } : { isPlaced: false, reason: 'narrow' } } })
  on('ui.close', () => undefined)
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text); return { value: undefined } })
  on('ui.status', ($: unknown, e: any) => { statuses.push(e.text); return { value: undefined } })
  on('model.complete', ($: unknown, e: any) => { calls.push(e); return { value: { isAnswered: true, text: answer, usage: { input_tokens: 1, output_tokens: 1, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } } } })
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  const sent: string[] = []
  on('prompt.submit', ($: unknown, e: any) => { if (e.origin?.kind === 'plugin' || e.asUser) sent.push(e.text); return { text: e.text } })
  on('prompt.compose', () => ({ sections: [{ id: 'intro', text: 'engine', scope: 'shared' }] }))
  on('ui.render', ($: any, e: any) => { const { Text } = $.ui.resolve(e); return <Text>engine</Text> })
  return { calls, toasts, statuses, clock, opens, sent }
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
    expect(parse('{"gist": 3, "attention": "x", "actions": [1, " a  b ", {"why": "no line"}], "ideas": []}')).toEqual({ gist: '', attention: [], actions: ['a b'], ideas: [], why: {} })
    // Four lines in all, attention first, then actions, then ideas; each explanation keyed to its line.
    const many = parse(JSON.stringify({ attention: [{ line: 'n1', why: 'w1' }, 'n2'], actions: ['a1', { line: 'a2', why: 'w2' }, 'a3'], ideas: ['i1'] }))!
    expect([many.attention, many.actions, many.ideas]).toEqual([['n1', 'n2'], ['a1', 'a2'], []])
    expect(many.why).toEqual({ n0: 'w1', a1: 'w2' })
    const card = { id: 'c', at: 0, ask: 'Fix it', gist: 'Done', attention: ['x'], actions: ['y', 'z'], ideas: [], done: ['a1'] }
    expect(openCount([card])).toBe(2)
    expect(asText(card)).toContain('☑ z')
    expect(asText({ ...card, attention: [], actions: [] })).toContain('Nothing needs you.')
    expect(asText({ ...card, why: { a1: 'because' } })).toContain('☑ z\n   ↳ because')
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
      const why = /stopped after 10 minutes/
      if (surface === 'mobile') {
        // No hover on a phone: the explanation waits behind its ⓘ.
        expect(await ui.find({ type: 'Text', text: why })).toBeUndefined()
        expect(await ui.find({ type: 'Text', text: /Tap ⓘ/ })).toBeDefined()
        await ui.press({ key: (await ui.findAll({ type: 'Button', text: 'ⓘ ' }))[0]!.key })
        expect(await ui.find({ type: 'Text', text: why })).toBeDefined()
      } else {
        // Drawn hidden, shown by the surface while the pointer is over its line.
        const tree = JSON.stringify(await ui.drawn())
        expect(tree).toContain('stopped after 10 minutes')
        expect(tree).toContain('"display":"none"},"hover":{"display":"flex"}')
        expect(await ui.find({ type: 'Text', text: /Point at a line/ })).toBeDefined()
      }
      // Ticking a to-do marks it done.
      await ui.press({ key: (await ui.find({ type: 'Button', text: /Open the app/ }))!.key })
      expect(await ui.find({ type: 'Text', text: '☑' })).toBeDefined()
      await ui.unmount()
    })
  }

  test('each message opens the panel (placed at any width, unlike an open of its own); slash commands do not', async ($, on) => {
    const { opens, clock } = world(on)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(opens.length).toBe(1)
    await $.prompt.submit({ text: '/heads-up off' } as any)
    expect(opens.length).toBe(1)
    await $.prompt.submit({ text: 'fix the queue' } as any)
    expect(opens.length).toBe(2)
    // A new card opens it too (shown where the screen is wide enough).
    await reply($, clock)
    expect(opens.length).toBe(3)
  })

  test('a slash command clears the heading, so the next card is not headed with an older message', async ($, on) => {
    const { calls, clock } = world(on)
    await $.prompt.submit({ text: 'fix the layout bug on the queue screen' } as any)
    await $.prompt.submit({ text: '/review' } as any)
    await reply($, clock)
    expect(calls[0].prompt).not.toContain('fix the layout bug')
  })

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
    const { calls, toasts, statuses, clock } = world(on, SIFTED, false)
    await reply($, clock)
    expect(toasts.some(t => /2 things need you/.test(t))).toBe(true)
    const r: any = await $.command.run({ command: 'heads-up', args: '' } as any)
    expect(r.text).toContain('👉 For you to do')
    expect(r.text).toContain('☐ Open the app on your phone')
    await $.command.run({ command: 'heads-up', args: 'off' } as any)
    statuses.length = 0
    await $.command.run({ command: 'heads-up', args: 'clear' } as any)
    expect(statuses.every(x => x === undefined)).toBe(true)
    await reply($, clock)
    expect(calls.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'on' } as any)
    await reply($, clock)
    expect(calls.length).toBe(2)
  })

  test('a chat on a computer gets the panel by itself: no box, the reply is sifted and the panel opens', async ($, on) => {
    const { calls, opens, clock } = world(on, SIFTED, true, 'auto')
    const composed: any = await ($ as any).prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools: [], outputStyle: null, traits: [] })
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(false)
    await $.prompt.submit({ text: 'fix the queue' } as any)
    await reply($, clock)
    expect(calls.length).toBe(1)
    expect(opens.length).toBeGreaterThan(0)
  })

  test('a cloud chat gets box mode by itself: long replies start with a Heads-up box, no model call, no panel', async ($, on) => {
    const { calls, opens, clock } = world(on, SIFTED, true, 'auto', true)
    const composed: any = await ($ as any).prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools: [], outputStyle: null, traits: [] })
    const box = composed.sections.find((x: any) => x.id === 'heads-up:box')
    expect(box?.text).toContain('**Heads-up**')
    expect(box?.scope).toBe('session')
    await $.prompt.submit({ text: 'fix the queue' } as any)
    await reply($, clock)
    expect(calls.length).toBe(0)
    expect(opens.length).toBe(0)
    const r: any = await $.command.run({ command: 'heads-up', args: '' } as any)
    expect(r.text).toContain('box mode')
  })

  test('/heads-up panel switches to the side panel (and drops the box); /heads-up box goes back', async ($, on) => {
    const { calls, clock } = world(on, SIFTED, true, 'box')
    await $.command.run({ command: 'heads-up', args: 'panel' } as any)
    let composed: any = await ($ as any).prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools: [], outputStyle: null, traits: [] })
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(false)
    await reply($, clock)
    expect(calls.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'box' } as any)
    composed = await ($ as any).prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools: [], outputStyle: null, traits: [] })
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(true)
  })
  test('parse turns offered options into a choice: one or many, the suggested one, junk refused', () => {
    const one = parse('{"gist":"g","attention":[],"actions":[],"ideas":[],"choice":{"question":"Which fix first?","multi":false,"options":[{"label":"Fix Safari scroll","why":"Hits the friend."},{"label":"Fix Month stutter","why":"Owner sees it daily."}],"suggested":1}}')
    expect(one?.choice).toEqual({ question: 'Which fix first?', multi: false, options: [{ label: 'Fix Safari scroll', why: 'Hits the friend.' }, { label: 'Fix Month stutter', why: 'Owner sees it daily.' }], suggested: 1, picked: [] })
    expect(parse('{"gist":"g","choice":{"question":"?","options":["only one"]}}')?.choice).toBeUndefined()
    expect(parse('{"gist":"g","choice":{"options":["a","b"],"suggested":7,"multi":true}}')?.choice).toEqual({ question: 'Which one?', multi: true, options: [{ label: 'a', why: '' }, { label: 'b', why: '' }], picked: [] })
    expect(hasAny({ attention: [], actions: [], ideas: [], choice: one!.choice })).toBe(true)
  })

  test('pick and pickText: one swaps, many toggle, none says so', () => {
    const c = { question: 'Next?', multi: false, options: [{ label: 'A', why: '' }, { label: 'B', why: '' }, { label: 'C', why: '' }], picked: [] as number[] }
    expect(pick(pick(c, 0), 1).picked).toEqual([1])
    const m = { ...c, multi: true }
    expect(pick(pick(pick(m, 2), 0), 2).picked).toEqual([0])
    expect(pickText(c, [1])).toBe('Next? Go with "B".')
    expect(pickText(m, [0, 2])).toBe('Next? Do these: "A" and "C".')
    expect(pickText(c, [])).toBe('None of those for now (Next).')
  })

  test('the panel lets the person pick several options and sends them as their message, once', async ($, on) => {
    const CHOICE = '{"gist":"Found three slow screens","attention":[],"actions":[],"ideas":[],"choice":{"question":"Which should I fix?","multi":true,"options":[{"label":"Month scroll","why":"Worst hitch 1.4s."},{"label":"Library switch","why":"110ms."},{"label":"Game page scroll","why":"180ms."}],"suggested":0}}'
    const { clock, sent, statuses } = world(on, CHOICE)
    await $.prompt.submit({ text: 'what is slow?' } as any)
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /Which should I fix\?/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /★ suggested/ })).toBeDefined()
    expect(statuses[statuses.length - 1]).toContain('1 needs you')
    await ui.press({ key: (await ui.find({ type: 'Button', text: /Month scroll/ }))!.key })
    await ui.press({ key: (await ui.find({ type: 'Button', text: /Game page scroll/ }))!.key })
    await ui.press({ key: (await ui.find({ type: 'Button', text: 'Send 2 picks' }))!.key })
    expect(sent).toEqual(['Which should I fix? Do these: "Month scroll" and "Game page scroll".'])
    expect(await ui.find({ type: 'Text', text: /Sent:/ })).toBeDefined()
    expect(await ui.find({ type: 'Button', text: /None of these/ })).toBeUndefined()
    await ui.unmount()
  })

  test('"None of these" is always there and sends a no', async ($, on) => {
    const CHOICE = '{"gist":"g","attention":[],"actions":[],"ideas":[],"choice":{"question":"Want me to add tests?","multi":false,"options":[{"label":"Yes, unit tests"},{"label":"Yes, a scenario"}],"suggested":null}}'
    const { clock, sent } = world(on, CHOICE)
    await reply($, clock)
    const ui = await $.ui.mount(pane('mobile'))
    expect(await ui.find({ type: 'Button', text: /^Send/ })).toBeUndefined()
    await ui.press({ key: (await ui.find({ type: 'Button', text: 'None of these' }))!.key })
    expect(sent).toEqual(['None of those for now (Want me to add tests).'])
    await ui.unmount()
  })
})
