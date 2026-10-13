import { describe, expect, mock, test } from 'claude-code/testing'
import { bar, clock, cost, modelName, progress } from '../hooks/numbers.ts'

const U = (i: number, o: number) => ({ input_tokens: i, output_tokens: o, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 })

function world(on: any) {
  mock.store(on)
  mock.clock(on, { now: 0 })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  on('ui.open', () => ({ value: { isPlaced: true } }))
  on('agent.spawn', ($: unknown, e: any) => ({ model: 'claude-opus-5-5', agentId: `a-${e.description.length}` }))
  on('turn.step', async function* ($: unknown, e: any) {
    return { turnId: e.turnId, index: 0, answer: '', toolUses: [], stopReason: 'end_turn', usage: { ...U(30_000, 2_000), model: 'claude-opus-5-5' } }
  })
  on('tool.call', () => ({ result: 'ok', text: 'ok' }))
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  on('prompt.submit', ($: unknown, e: any) => ({ text: e.text }))
  on('ui.render', ($: any, e: any) => { const { Text } = $.ui.resolve(e); return <Text>engine</Text> })
}

async function crew($: any) {
  await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
  await $.prompt.submit({ text: 'add tests' })
  const a: any = await $.agent.spawn({ prompt: 'p', description: 'Add JSDoc to src functions', subagentType: 'general-purpose' })
  const b: any = await $.agent.spawn({ prompt: 'p', description: 'Write greet test', subagentType: 'general-purpose' })
  for await (const _ of $.turn.step({ turnId: 't', index: 0, model: 'claude-opus-5-5', effort: 'low', messageCount: 1, agentId: a.agentId })) { /* stream */ }
  await $.tool.call({ tool: 'Read', file_path: '/repo/src/greet.js', agentId: a.agentId } as any)
  await $.turn.complete({ answer: 'done', durationMs: 1, isAborted: false, turnId: 't', agentId: b.agentId, reason: 'answer' } as any)
  return { a: a.agentId, b: b.agentId }
}

describe('crew', () => {
  test('numbers: names, prices, bars, times', () => {
    expect(modelName('claude-opus-5-5')).toBe('Opus 5.5')
    expect(modelName('claude-haiku-4-5-20251001')).toBe('Haiku 4.5')
    expect(Math.round(cost('claude-opus-5-5', U(1_000_000, 0)) * 100)).toBe(400)
    expect(Math.round(cost('claude-sonnet-5-5', U(0, 1_000_000)) * 100)).toBe(1000)
    expect(bar(0.5, 10)).toEqual(['━━━━━', '─────'])
    expect(clock(79_000)).toBe('1:19')
    expect(progress({ status: 'running', steps: 0 })).toBeLessThan(progress({ status: 'running', steps: 5 }))
    expect(progress({ status: 'done', steps: 0 })).toBe(1)
  })

  for (const surface of ['terminal', 'desktop', 'mobile'] as const) {
    test(`the panel shows running and finished helpers on ${surface}`, async ($, on) => {
      world(on)
      await crew($)
      const ui = await $.ui.mount({ plugin: 'crew', surface, component: 'Pane', requestId: 'crew', props: { title: 'Agents', isFocused: false, bodyColumns: 60, placement: 'dock' } } as any)
      expect(await ui.find({ type: 'Text', text: /Running · 1/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Add JSDoc to src functions/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Read greet\.js/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /32k/ })).toBeDefined()
      expect(await ui.find({ key: 'crew-done' })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Write greet test/ })).toBeDefined()
      await ui.press({ key: 'crew-done' })
      expect(await ui.find({ type: 'Text', text: /Write greet test/ })).toBeUndefined()
      await ui.unmount()
    })

    test(`the band sums up the round on ${surface}`, async ($, on) => {
      world(on)
      await crew($)
      const ui = await $.ui.mount({ plugin: 'crew', surface, component: 'AbovePrompt', props: { hasSurvey: false, isWorking: true, maxRows: 3, bodyColumns: 100 } } as any)
      expect(await ui.find({ type: 'Text', text: /1 running · 1 done/ })).toBeDefined()
      expect(await ui.find({ key: 'crew-open' })).toBeDefined()
      await ui.unmount()
    })
  }

  test('no helpers, no band', async ($, on) => {
    world(on)
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
    const ui = await $.ui.mount({ plugin: 'crew', surface: 'terminal', component: 'AbovePrompt', props: { hasSurvey: false, isWorking: false, maxRows: 3, bodyColumns: 100 } } as any)
    expect(await ui.find({ key: 'crew-open' })).toBeUndefined()
    await ui.unmount()
  })
})
