import { describe, expect, mock, test } from 'claude-code/testing'
import { applyTask, bar, clock, crabSvg, gist, modelName, progress, push, stepLine, tier, toolLabel } from '../hooks/numbers.ts'

const U = (i: number, o: number) => ({ input_tokens: i, output_tokens: o, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 })

function world(on: any) {
  mock.store(on)
  mock.clock(on, { now: 0 })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  const opens: string[] = []
  on('ui.open', ($: unknown, e: any) => { opens.push(e.id); return { value: { isPlaced: true } } })
  on('prompt.compose', () => ({ sections: [{ id: 'intro', text: 'engine', scope: 'shared' }] }))
  on('agent.spawn', ($: unknown, e: any) => ({ model: 'claude-opus-5-5', agentId: `a-${e.description.length}` }))
  on('turn.step', async function* ($: unknown, e: any) {
    return { turnId: e.turnId, index: 0, answer: 'Looking at the **src** functions first.', toolUses: [], stopReason: 'end_turn', usage: { ...U(30_000, 2_000), model: 'claude-opus-5-5' } }
  })
  on('tool.call', ($: unknown, e: any) => ({ result: 'ok', text: e.tool === 'TaskCreate' ? `Task #${e.subject.length} created` : 'ok' }))
  on('turn.complete', ($: unknown, e: any) => ({ text: e.answer }))
  on('prompt.submit', ($: unknown, e: any) => ({ text: e.text }))
  on('ui.render', ($: any, e: any) => { const { Text } = $.ui.resolve(e); return <Text>engine</Text> })
  return { opens }
}

const compose = ($: any, tools = ['Agent', 'Read']) => $.prompt.compose({ model: 'm', promptModel: 'm', surfaces: [], tools, outputStyle: null, traits: [] })

async function crew($: any) {
  await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
  await $.prompt.submit({ text: 'build the launch kit for the cold brew and review it all' })
  for (const subject of ['Add JSDoc to src functions', 'Write greet test', 'Review the whole kit'])
    await $.tool.call({ tool: 'TaskCreate', subject, description: 'x' } as any)
  const a: any = await $.agent.spawn({ prompt: 'Add JSDoc comments to every exported function in src/.\nKeep them short.', description: 'Add JSDoc to src functions', subagentType: 'general-purpose' })
  const b: any = await $.agent.spawn({ prompt: 'p', description: 'Write greet test', subagentType: 'general-purpose' })
  for await (const _ of $.turn.step({ turnId: 't', index: 0, model: 'claude-opus-5-5', effort: 'low', messageCount: 1, agentId: a.agentId })) { /* stream */ }
  await $.tool.call({ tool: 'Grep', pattern: 'export function', path: '/repo/src', agentId: a.agentId } as any)
  await $.tool.call({ tool: 'Read', file_path: '/repo/src/greet.js', agentId: a.agentId } as any)
  await $.tool.call({ tool: 'TodoWrite', todos: [{ content: 'Find functions', status: 'completed' }, { content: 'Hero copy', status: 'in_progress' }, { content: 'Check', status: 'pending' }], agentId: a.agentId } as any)
  await $.turn.complete({ answer: 'Added a greet test; it passes.\nDetails below.', durationMs: 1, isAborted: false, turnId: 't', agentId: b.agentId, reason: 'answer' } as any)
  return { a: a.agentId, b: b.agentId }
}

describe('crew', () => {
  test('numbers: names, plain-words steps, bars, times', () => {
    expect(modelName('claude-opus-5-5')).toBe('Opus 5.5')
    expect(modelName('claude-haiku-4-5-20251001')).toBe('Haiku 4.5')
    expect(toolLabel('Read', { file_path: 'C:\\repo\\index.html' })).toBe('Reading index.html')
    expect(toolLabel('Grep', { pattern: 'onScrolled', path: '/repo/index.html' })).toBe("Searching for 'onScrolled' in index.html")
    expect(toolLabel('Bash', { command: 'node tools/kit.mjs sweep', description: 'Run the layout sweep' })).toBe('Run the layout sweep')
    expect(toolLabel('Bash', { command: 'node tools/kit.mjs sweep' })).toBe('Running node tools/kit.mjs sweep')
    expect(toolLabel('mcp__x__do_thing', {})).toBe('do_thing')
    expect(gist('**Found** it in [sw.js](sw.js:12).\nMore.')).toBe('Found it in sw.js.')
    expect(push(['a', 'b', 'c', 'd'], 'e')).toEqual(['b', 'c', 'd', 'e'])
    expect(bar(0.5, 10)).toEqual(['━━━━━', '─────'])
    expect(clock(79_000)).toBe('1:19')
    expect(progress({ status: 'running', steps: 0 })).toBeLessThan(progress({ status: 'running', steps: 5 }))
    expect(progress({ status: 'done', steps: 0 })).toBe(1)
    expect(tier('low').name).toBe('light')
    expect(tier('medium').name).toBe('medium')
    expect(tier('xhigh').name).toBe('careful')
    expect(crabSvg(tier('high'), 'working')).toContain('animate')
    let steps = applyTask([], 'TaskCreate', { subject: 'Hero copy' }, 'Task #7 created successfully')
    steps = applyTask(steps, 'TaskUpdate', { taskId: '7', status: 'in_progress' })
    expect(steps).toEqual([{ id: '7', subject: 'Hero copy', status: 'in_progress' }])
    expect(stepLine({ todo: steps, steps: 2, status: 'running' })).toBe('0/1 · Hero copy')
    expect(stepLine({ steps: 4, lastTool: 'Read a.js', status: 'running' })).toBe('4 steps · Read a.js')
  })

  test('a helper\'s report does not rename the panel or end the round', async ($, on) => {
    world(on)
    await crew($)
    await $.prompt.submit({ text: '<agent-message from="a-1">done</agent-message>', origin: { kind: 'peer' } } as any)
    await $.prompt.submit({ text: '<task-notification>x</task-notification>', origin: { kind: 'task-notification' } } as any)
    const pane = await $.ui.mount({ plugin: 'crew', surface: 'desktop', component: 'Pane', requestId: 'crew', props: { title: 'Agents', isFocused: false, bodyColumns: 60, placement: 'dock' } } as any)
    expect(await pane.find({ type: 'Text', text: /Build the launch kit/ })).toBeDefined()
    expect(await pane.find({ type: 'Text', text: /agent-message/ })).toBeUndefined()
    await pane.unmount()
    const band = await $.ui.mount({ plugin: 'crew', surface: 'terminal', component: 'AbovePrompt', props: { hasSurvey: false, isWorking: true, maxRows: 3, bodyColumns: 100 } } as any)
    expect(await band.find({ type: 'Text', text: /Agents/ })).toBeDefined()
    await band.unmount()
  })

  for (const surface of ['terminal', 'desktop', 'mobile'] as const) {
    test(`the panel shows running and finished helpers on ${surface}`, async ($, on) => {
      world(on)
      await crew($)
      const ui = await $.ui.mount({ plugin: 'crew', surface, component: 'Pane', requestId: 'crew', props: { title: 'Agents', isFocused: false, bodyColumns: 60, placement: 'dock' } } as any)
      expect(await ui.find({ type: 'Text', text: /Running · 1/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Add JSDoc to src functions/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /1\/3 · Hero copy/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /^light$/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Build the launch kit/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Planned · 1/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /3\. Review the whole kit/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Add JSDoc comments to every exported function/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Reading greet.js/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Looking at the src functions first/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Searching for 'export function' in src/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Added a greet test; it passes\./ })).toBeDefined()
      const drawn = JSON.stringify(await ui.drawn())
      expect(drawn).not.toContain('≈$')
      expect(drawn).not.toContain('Cost')
      expect(drawn).not.toContain('Tokens')
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
      // Each running helper's current step, in plain words; no price.
      expect(await ui.find({ type: 'Text', text: /Add JSDoc to src functions/ })).toBeDefined()
      expect(await ui.find({ type: 'Text', text: /Updating its task list/ })).toBeDefined()
      expect(JSON.stringify(await ui.drawn())).not.toContain('≈$')
      await ui.unmount()
    })
  }

  test('/agents-info answers with the whole crew as text (for screens without panels)', async ($, on) => {
    world(on)
    await crew($)
    const r: any = await $.command.run({ command: 'agents-info', args: '' } as any)
    expect(r.text).toContain('🦀 Agents · 1 running · 1 finished')
    expect(r.text).toContain('● Add JSDoc to src functions (light, Opus 5.5 · low)')
    expect(r.text).toContain('○ 3. Review the whole kit (planned)')
    expect(r.text).toContain('1/3 · Hero copy')
    expect(r.text).toContain('✓ Write greet test')
    expect(r.text).toContain('Task: Add JSDoc comments to every exported function in src/.')
    expect(r.text).toContain('Now: Updating its task list')
    expect(r.text).toContain('Result: Added a greet test; it passes.')
    expect(r.text).not.toContain('≈$')
  })

  test('no helpers: the band stays, saying the crew is idle', async ($, on) => {
    world(on)
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
    const ui = await $.ui.mount({ plugin: 'crew', surface: 'terminal', component: 'AbovePrompt', props: { hasSurvey: false, isWorking: false, maxRows: 3, bodyColumns: 100 } } as any)
    expect(await ui.find({ key: 'crew-open' })).toBeDefined()
    expect(await ui.find({ text: 'idle · none yet this chat' })).toBeDefined()
    await ui.unmount()
  })

  test('the panel opens by itself when a batch of helpers starts, once per batch', async ($, on) => {
    const { opens } = world(on)
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
    const a: any = await $.agent.spawn({ prompt: 'p', description: 'Find the scroll code', subagentType: 'Explore' })
    await $.agent.spawn({ prompt: 'p', description: 'Check the tests', subagentType: 'general-purpose' })
    expect(opens).toEqual(['crew'])
    await $.turn.complete({ answer: 'x', durationMs: 1, isAborted: false, turnId: 't', agentId: a.agentId, reason: 'answer' } as any)
    await $.turn.complete({ answer: 'x', durationMs: 1, isAborted: false, turnId: 't', agentId: 'a-15', reason: 'answer' } as any)
    await $.agent.spawn({ prompt: 'p', description: 'Review the diff', subagentType: 'general-purpose' })
    expect(opens).toEqual(['crew', 'crew'])
  })

  test('Claude is told to use helpers by itself, unless "/agents-info auto off"', async ($, on) => {
    world(on)
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
    const section = (r: any) => r.sections.find((x: any) => x.id === 'crew:delegate')
    const on1: any = await compose($)
    expect(section(on1)?.text).toContain('without being asked')
    expect(section(on1)?.text).toContain('in parallel')
    expect(section(on1)?.scope).toBe('session')
    // No Agent tool (a helper's own loop): nothing to say.
    expect(section(await compose($, ['Read']))).toBeUndefined()
    const off: any = await $.command.run({ command: 'agents-info', args: 'auto off' } as any)
    expect(off.text).toContain('only when you ask')
    expect(section(await compose($))).toBeUndefined()
    await $.command.run({ command: 'agents-info', args: 'auto on' } as any)
    expect(section(await compose($))).toBeDefined()
  })

  test('a survey above the prompt still wins over the band', async ($, on) => {
    world(on)
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
    const ui = await $.ui.mount({ plugin: 'crew', surface: 'terminal', component: 'AbovePrompt', props: { hasSurvey: true, isWorking: false, maxRows: 3, bodyColumns: 100 } } as any)
    expect(await ui.find({ key: 'crew-open' })).toBeUndefined()
    await ui.unmount()
  })
})
