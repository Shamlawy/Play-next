import { describe, expect, mock, test } from 'claude-code/testing'
import { MAX_LINES, asText, fallback, hasAny, opening, openCount, parse, pick, pickText } from '../hooks/sift.ts'

const LONG = `I fixed the layout bug and pushed it. ${'Details of everything I checked along the way. '.repeat(12)} One thing failed: the iPhone check timed out. Could you open the app on your phone and tell me if the list scrolls? We could also add a dark-mode toggle later.`
const SIFTED = '```json\n{"gist":"Fixed the layout bug","attention":["iPhone check timed out"],"actions":["Check the list scrolls on your phone"],"ideas":["Add a dark-mode toggle"]}\n```'

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
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text); return { value: undefined } })
  on('ui.status', ($: unknown, e: any) => { statuses.push(e.text); return { value: undefined } })
  on('model.complete', ($: unknown, e: any) => { calls.push(e); return { value: { isAnswered: true, text: answer, usage: { input_tokens: 1, output_tokens: 1, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } } } })
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  // The turn's steps: each one the test queues is what the model "said" in that step.
  const steps: { answer: string; toolUses?: any[] }[] = []
  on('turn.start', ($: unknown, e: any) => ({ turnId: e.turnId }))
  on('turn.step', async function* ($: unknown, e: any) {
    const s = steps.shift() ?? { answer: '' }
    return { turnId: e.turnId, index: e.index, answer: s.answer, toolUses: s.toolUses ?? [], stopReason: 'end_turn', usage: null }
  })
  const sent: string[] = []
  on('prompt.submit', ($: unknown, e: any) => { if (e.origin?.kind === 'plugin' || e.asUser) sent.push(e.text); return { text: e.text } })
  on('prompt.compose', () => ({ sections: [{ id: 'intro', text: 'engine', scope: 'shared' }] }))
  on('ui.render', ($: any, e: any) => { const { Text } = $.ui.resolve(e); return <Text>engine</Text> })
  return { calls, toasts, statuses, clock, opens, sent, steps }
}

async function reply($: any, clock: any, answer = LONG, extra: any = {}) {
  await $.turn.complete({ answer, durationMs: 1, isAborted: false, turnId: 't', reason: 'answer', ...extra })
  await clock.advance(50)
}

const pane = (surface: 'terminal' | 'desktop' | 'mobile') => ({
  plugin: 'heads-up', surface, component: 'Pane', requestId: 'heads-up',
  props: { title: 'Heads-up', isFocused: false, bodyColumns: 60, placement: 'dock' },
}) as any

const compose = ($: any) => $.prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools: [], outputStyle: null, traits: [] })

describe('heads-up', () => {
  test('parse reads fenced JSON, keeps 3 lines in all (a choice is one), and keeps no explanations', () => {
    expect(parse(SIFTED)?.actions).toEqual(['Check the list scrolls on your phone'])
    expect(parse('no json here')).toBeUndefined()
    expect(parse('{"gist": 3, "attention": "x", "actions": [1, " a  b ", {"why": "no line"}], "ideas": []}')).toEqual({ gist: '', attention: [], actions: ['a b'], points: [], ideas: [] })
    expect(parse('{"gist": "", "attention": []}')).toBeUndefined()
    // Three in all, attention first, then actions, then ideas; an older {line, why} answer keeps only the line.
    const many = parse(JSON.stringify({ attention: [{ line: 'n1', why: 'w1' }, 'n2'], actions: ['a1', 'a2'], ideas: ['i1'] }))!
    expect([many.attention, many.actions, many.ideas]).toEqual([['n1', 'n2'], ['a1'], []])
    expect(MAX_LINES).toBe(3)
    const withChoice = parse(JSON.stringify({ attention: ['n1', 'n2', 'n3'], choice: { question: 'Q?', options: ['a', 'b'] } }))!
    expect(withChoice.attention).toEqual(['n1', 'n2'])
    expect(JSON.stringify(parse(JSON.stringify({ attention: [{ line: 'x', why: 'because' }] })))).not.toContain('because')
    const card = { id: 'c', at: 0, gist: 'Done', attention: ['x'], actions: ['y', 'z'], ideas: [], done: ['a1'] }
    expect(openCount(card)).toBe(2)
    expect(asText(card)).toContain('👉 ☑ z')
    expect(asText({ ...card, attention: [], actions: [] })).toContain('Nothing needs you.')
  })

  for (const surface of ['terminal', 'desktop', 'mobile'] as const) {
    test(`a long reply becomes a few short tiles, no hover, on ${surface}`, async ($, on) => {
      const { calls, clock } = world(on)
      await $.session.start({ cwd: '/repo', surface: null, isInteractive: true } as any)
      await $.prompt.submit({ text: 'fix the layout bug on the queue screen' } as any)
      await reply($, clock)
      expect(calls.length).toBe(1)
      expect(calls[0].model).toBe('haiku')
      expect(calls[0].prompt).toContain('fix the layout bug')
      expect(calls[0].system).not.toContain('"why"')
      const ui = await $.ui.mount(pane(surface))
      expect(await ui.find({ type: 'Text', text: /Fix the layout bug on the queue screen/ })).toBeUndefined()
      expect(await ui.find({ type: 'Text', text: 'Fixed the layout bug' })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /iPhone check timed out/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Add a dark-mode toggle/ })).toBeDefined()
      // Three lines: the chips show.
      expect(await ui.find({ type: 'Text', text: '⚠ 1' })).toBeDefined()
      const drawn = JSON.stringify(await ui.drawn())
      expect(drawn).toContain('#2a2112')
      // No hover, no ⓘ, no "for <message>" line, no earlier list.
      expect(drawn).not.toContain('"hover"')
      expect(drawn).not.toContain('ⓘ')
      expect(drawn).not.toContain('earlier')
      expect(drawn).not.toContain('Mark done')
      if (surface === 'terminal') expect(drawn).not.toContain('"type":"Svg"')
      else expect(drawn).toContain('shape-rendering')
      // Ticking a to-do drops it to a collapsed "Done · 1" line.
      const box = (await ui.findAll({ type: 'Button' })).find(b => /^hu-.*-a0$/.test(b.key))!
      await ui.press({ key: box.key })
      expect(await ui.find({ type: 'Text', text: /Check the list scrolls/ })).toBeUndefined()
      await ui.press({ key: (await ui.find({ type: 'Button', text: /Done · 1/ }))!.key })
      expect(await ui.find({ type: 'Text', text: '☑' })).toBeDefined()
      await ui.unmount()
    })
  }

  test('auto clear: the next message wipes the card, so only the newest reply ever shows', async ($, on) => {
    const { clock, statuses } = world(on)
    await $.prompt.submit({ text: 'fix it' } as any)
    await reply($, clock)
    expect(statuses[statuses.length - 1]).toContain('2 need you')
    await $.prompt.submit({ text: 'thanks, next thing' } as any)
    expect(statuses[statuses.length - 1]).toBeUndefined()
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /iPhone check timed out/ })).toBeUndefined()
    await ui.unmount()
    // A slash command does not wipe it.
    await reply($, clock)
    await $.prompt.submit({ text: '/heads-up' } as any)
    const again = await $.ui.mount(pane('desktop'))
    expect(await again.find({ type: 'Text', text: /iPhone check timed out/ })).toBeDefined()
    await again.unmount()
  })

  test('two lines or fewer: no count chips', async ($, on) => {
    const { clock } = world(on, '{"gist":"Pushed the fix","attention":["Safari not tested"],"actions":[],"ideas":[]}')
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /Safari not tested/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: '⚠ 1' })).toBeUndefined()
    await ui.unmount()
  })

  test('each message opens the panel; slash commands and "off" do not', async ($, on) => {
    const { opens, clock } = world(on)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(opens.length).toBe(1)
    await $.prompt.submit({ text: '/heads-up off' } as any)
    expect(opens.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'off' } as any)
    await $.prompt.submit({ text: 'fix the queue' } as any)
    expect(opens.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'on' } as any)
    await $.prompt.submit({ text: 'fix the queue' } as any)
    expect(opens.length).toBe(2)
    await reply($, clock)
    expect(opens.length).toBe(3)
  })

  test('a slash command clears the remembered message, so the next sift is not given an older one', async ($, on) => {
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

  test('the whole turn is read, not just its last message, and the closing recap rides along', async ($, on) => {
    const { calls, clock, steps } = world(on, '{"gist":"Fixed the scroll stutter on Library","attention":[],"actions":[],"points":["Cause: a layout read after every redraw","Checked at 412 and 859 widths"],"ideas":[]}')
    await $.prompt.submit({ text: 'fix the library stutter' } as any)
    await $.turn.start({ text: 'fix the library stutter', turnId: 't1' } as any)
    steps.push({ answer: `Found it: the library reads layout right after each redraw. ${'More detail. '.repeat(30)}` })
    steps.push({ answer: '', toolUses: [{ name: 'mcp__ccd_turn__wrap_up', input: { recap: 'Library scroll fixed and checked at two widths; nothing merged yet.', ask: 'Merge it?' } }] })
    steps.push({ answer: 'Done.' })
    for (let i = 0; i < 3; i++) for await (const _ of $.turn.step({ turnId: 't1', index: i, model: 'm', messageCount: 1 } as any)) { /* stream */ }
    // The final message alone ("Done.") would be too short to sum up: the turn's earlier text and the recap make it.
    await reply($, clock, 'Done.', { turnId: 't1' })
    expect(calls.length).toBe(1)
    expect(calls[0].prompt).toContain('reads layout right after each redraw')
    expect(calls[0].prompt).toContain('Library scroll fixed')
    expect(calls[0].prompt).toContain('Question for the user: Merge it?')
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: 'Fixed the scroll stutter on Library' })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Cause: a layout read/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Nothing needs you/ })).toBeDefined()
    expect(JSON.stringify(await ui.drawn())).toContain('#1c1d20')
    await ui.unmount()
    // A helper's steps never count as the main loop's words.
    await $.turn.start({ text: 'next', turnId: 't2' } as any)
    steps.push({ answer: 'helper chatter' })
    for await (const _ of $.turn.step({ turnId: 't2', index: 0, model: 'm', messageCount: 1, agentId: 'a1' } as any)) { /* stream */ }
    await reply($, clock, LONG, { turnId: 't2' })
    expect(calls[1].prompt).not.toContain('helper chatter')
  })

  test('a short reply is its own summary, with no model call', async ($, on) => {
    const { calls, clock } = world(on)
    await $.prompt.submit({ text: 'did it push?' } as any)
    await reply($, clock, 'Yes, **pushed** to `main` as v285.')
    expect(calls.length).toBe(0)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: 'Yes, pushed to main as v285.' })).toBeDefined()
    await ui.unmount()
  })

  test('a model that fails or answers junk still leaves a summary: the opening and the last question', async ($, on) => {
    const { clock } = world(on, 'sorry, no json')
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /^I fixed the layout bug and pushed it\./ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Could you open the app on your phone/ })).toBeDefined()
    await ui.unmount()
    expect(opening('# Title\n\nFirst one. Second one. ' + 'x'.repeat(400))).toBe('Title')
    expect(fallback('Did a thing.\nWant me to merge?').attention).toEqual(['Want me to merge?'])
  })

  test('a reply with nothing for the person shows one green tile', async ($, on) => {
    const { clock } = world(on, '{"gist":"Renamed the files","attention":[],"actions":[],"ideas":[]}')
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /Nothing needs you/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Renamed the files/ })).toBeDefined()
    await ui.unmount()
  })

  test('/heads-up answers as text (phones), toasts when no panel fits, and /heads-up off stops it', async ($, on) => {
    const { calls, toasts, statuses, clock } = world(on, SIFTED, false)
    await reply($, clock)
    expect(toasts.some(t => /2 things need you/.test(t))).toBe(true)
    const r: any = await $.command.run({ command: 'heads-up', args: '' } as any)
    expect(r.text).toContain('👉 ☐ Check the list scrolls')
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
    const composed: any = await compose($)
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(false)
    await $.prompt.submit({ text: 'fix the queue' } as any)
    await reply($, clock)
    expect(calls.length).toBe(1)
    expect(opens.length).toBeGreaterThan(0)
  })

  test('a cloud chat gets a short box by itself: no ↳ explanations, no model call, no panel', async ($, on) => {
    const { calls, opens, clock } = world(on, SIFTED, true, 'auto', true)
    const composed: any = await compose($)
    const box = composed.sections.find((x: any) => x.id === 'heads-up:box')
    expect(box?.text).toContain('**Heads-up**')
    expect(box?.text).toContain('at most 12 words')
    expect(box?.text).toContain('at most 25 words: what was done or found')
    expect(box?.text).not.toContain('↳')
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
    let composed: any = await compose($)
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(false)
    await reply($, clock)
    expect(calls.length).toBe(1)
    await $.command.run({ command: 'heads-up', args: 'box' } as any)
    composed = await compose($)
    expect(composed.sections.some((x: any) => x.id === 'heads-up:box')).toBe(true)
  })

  test('parse turns offered options into a choice of labels: one or many, the suggested one, junk refused', () => {
    const one = parse('{"gist":"g","attention":[],"actions":[],"ideas":[],"choice":{"question":"Which fix first?","multi":false,"options":["Fix Safari scroll",{"label":"Fix Month stutter","why":"Owner sees it daily."}],"suggested":1}}')
    expect(one?.choice).toEqual({ question: 'Which fix first?', multi: false, options: [{ label: 'Fix Safari scroll' }, { label: 'Fix Month stutter' }], suggested: 1, picked: [] })
    expect(parse('{"gist":"g","choice":{"question":"?","options":["only one"]}}')?.choice).toBeUndefined()
    expect(parse('{"gist":"g","choice":{"options":["a","b"],"suggested":7,"multi":true}}')?.choice).toEqual({ question: 'Which one?', multi: true, options: [{ label: 'a' }, { label: 'b' }], picked: [] })
    expect(hasAny({ attention: [], actions: [], ideas: [], choice: one!.choice })).toBe(true)
  })

  test('pick and pickText: one swaps, many toggle, none says so', () => {
    const c = { question: 'Next?', multi: false, options: [{ label: 'A' }, { label: 'B' }, { label: 'C' }], picked: [] as number[] }
    expect(pick(pick(c, 0), 1).picked).toEqual([1])
    const m = { ...c, multi: true }
    expect(pick(pick(pick(m, 2), 0), 2).picked).toEqual([0])
    expect(pickText(c, [1])).toBe('Next? Go with "B".')
    expect(pickText(m, [0, 2])).toBe('Next? Do these: "A" and "C".')
    expect(pickText(c, [])).toBe('None of those for now (Next).')
  })

  test('the panel lets the person pick several options and sends them as their message, once', async ($, on) => {
    const CHOICE = '{"gist":"Found three slow screens","attention":[],"actions":[],"ideas":[],"choice":{"question":"Which should I fix?","multi":true,"options":["Month scroll","Library switch","Game page scroll"],"suggested":0}}'
    const { clock, sent, statuses } = world(on, CHOICE)
    await $.prompt.submit({ text: 'what is slow?' } as any)
    await reply($, clock)
    const ui = await $.ui.mount(pane('desktop'))
    expect(await ui.find({ type: 'Text', text: /Which should I fix\?/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /★/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: 'pick any' })).toBeDefined()
    expect(await ui.find({ type: 'Button', text: /^Send/ })).toBeUndefined()
    expect(statuses[statuses.length - 1]).toContain('1 needs you')
    await ui.press({ key: (await ui.find({ type: 'Button', text: /Month scroll/ }))!.key })
    await ui.press({ key: (await ui.find({ type: 'Button', text: /Game page scroll/ }))!.key })
    await ui.press({ key: (await ui.find({ type: 'Button', text: 'Send 2' }))!.key })
    expect(sent).toEqual(['Which should I fix? Do these: "Month scroll" and "Game page scroll".'])
    expect(await ui.find({ type: 'Text', text: /Sent:/ })).toBeDefined()
    expect(await ui.find({ type: 'Button', text: /None of these/ })).toBeUndefined()
    await ui.unmount()
  })

  test('"None of these" is always there and sends a no', async ($, on) => {
    const CHOICE = '{"gist":"g","attention":[],"actions":[],"ideas":[],"choice":{"question":"Want me to add tests?","multi":false,"options":["Yes, unit tests","Yes, a scenario"],"suggested":null}}'
    const { clock, sent } = world(on, CHOICE)
    await reply($, clock)
    const ui = await $.ui.mount(pane('mobile'))
    expect(await ui.find({ type: 'Button', text: /^Send/ })).toBeUndefined()
    await ui.press({ key: (await ui.find({ type: 'Button', text: 'None of these' }))!.key })
    expect(sent).toEqual(['None of those for now (Want me to add tests).'])
    await ui.unmount()
  })
})
