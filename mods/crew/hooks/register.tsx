import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Helper, Step } from '../types'
import { applyTask, bar, clock, crabSvg, elapsed, gist, modelName, progressOf, push, stepLine, tier, toolLabel, totals } from './numbers.ts'

type $ = EngineInterface

// Savvy-Progress-style view of Claude's helper agents: what each was asked, what it is doing right now, how
// (model, effort, its last steps), how far along, and what it came back with.
// 0.3 (the owner: "crew only shows price which i dont care about much i care about what the agents are doing and
// how"): prices and token counts are gone; the band names each running helper's current step, the panel shows its
// task, its words, its recent steps and its result.
const PANE = 'crew'
const MAX = 60
const TASK_TOOLS = new Set(['TaskCreate', 'TaskUpdate', 'TodoWrite'])
const PERSON = new Set(['composer', 'bridge', 'sdk', 'unclassified'])

const helpers = atom({ plugin: 'crew', key: 'helpers' } as const, [])
const now = atom({ plugin: 'crew', key: 'now' } as const, 0)
// When the person last sent a message: the band shows this round's helpers (and any still running).
const since = atom({ plugin: 'crew', key: 'since' } as const, 0)
const showDone = atom({ plugin: 'crew', key: 'showDone' } as const, true)
const compact = atom({ plugin: 'crew', key: 'compact' } as const, false)
// The main loop's own task list: its pending tasks no helper has taken yet are "Planned".
const plan = atom({ plugin: 'crew', key: 'plan' } as const, [])
// The panel's heading: the person's last message, cut short.
const job = atom({ plugin: 'crew', key: 'job' } as const, '')

// 0.4 (the owner: "i noticed you dont use agents and sub agents alone i need to ask and i dont have enough knowledge
// about this so make it auto"): unless `/agents-info auto off`, Claude's system prompt carries this standing request,
// so it splits work across helpers by itself when that helps.
export const DELEGATE = `Helper agents (the person's standing request: they want you to use helper agents on your own, without being asked, because they don't know when to ask; this counts as the person asking). Use the Agent tool by yourself when it makes the job faster or better:
- Searching or reading widely (many files, several places, "where is X used", an unfamiliar area): an Explore helper.
- Two or more independent questions or checks: one helper each, started in the same message so they run in parallel.
- A second opinion on a finished change (bugs, missed cases, the project's rules) before you call it done: a review helper.
- Long checks or test runs whose output you only need the verdict of, and research that needs many web or file reads.
Keep the main work yourself: decisions, design, the edits that need the whole conversation, and anything a few tool calls settle (a helper starts cold, so it only pays off for real work). Give each helper a complete brief (the goal, the files or area, what to report back) and a short description. Pick the model for the job: haiku or sonnet for mechanical or read-only work, the default model for anything that needs judgment. Never let two helpers edit the same files. When you start helpers, say in one short line who is doing what; then use what they report, checking anything surprising yourself.`

const patch = ($: $, id: string, fn: (x: Helper) => Helper) => update($, helpers, list => list.map(x => (x.id === id ? fn(x) : x)))
const openPane = ($: $) => $.ui.open({ id: PANE, title: 'Agents' })
const same = (a: string, b: string) => {
  const n = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
  return n(a) === n(b) || (n(a).length > 8 && n(b).includes(n(a))) || (n(b).length > 8 && n(a).includes(n(b)))
}
const heading = (text: string) => {
  const t = text.replace(/\s+/g, ' ').trim()
  const cut = t.length > 34 ? `${t.slice(0, 33).replace(/\s+\S*$/, '')}…` : t
  return cut ? cut.charAt(0).toUpperCase() + cut.slice(1) : ''
}
const planned = (steps: readonly Step[], list: readonly Helper[]) =>
  steps.map((s, i) => ({ ...s, n: i + 1 })).filter(s => s.status === 'pending' && !list.some(x => same(x.title, s.subject)))
// What a running helper is doing this moment: its current step, else what it last said.
const doing = (x: Helper) => x.lastTool || x.said || 'Starting…'
// How it works: its tier, model, effort and agent type.
const how = (x: Helper) => `${modelName(x.model)}${x.effort ? ` · ${x.effort}` : ''}${x.type && x.type !== 'general-purpose' ? ` · ${x.type}` : ''}`
// The progress line without the step it's on (that has its own "Now" line).
const progressLine = (x: Helper) => stepLine({ ...x, lastTool: undefined })

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'agents-info',
      description: 'Open the Agents panel: every helper Claude starts, live: its task, what it is doing now and how, its last steps and what it found. "/agents-info auto off" stops Claude starting helpers by itself, "/agents-info auto on" starts it again.',
      argumentHint: '[auto on|auto off]',
    })
    // A one-second tick while something runs: times and crabs move; nothing redraws at rest.
    $.clock.every(1000, async () => {
      const list = await read($, helpers)
      if (list.some(x => x.status === 'running')) await update($, now, () => Date.now())
    })
    await update($, now, () => Date.now())
    return next(e)
  })

  // The reply is the whole crew as text too, so a screen that seats no panes (the phone app) still shows it.
  on('command.run', { command: 'agents-info' }, async ($, e) => {
    const arg = (e.args ?? '').trim().toLowerCase().replace(/\s+/g, ' ')
    if (arg === 'auto off') {
      await $.store.set('manual', true)
      return { text: 'Claude will start helper agents only when you ask. "/agents-info auto on" lets it decide again.' }
    }
    if (arg === 'auto on' || arg === 'auto') {
      await $.store.delete('manual')
      return { text: 'Claude starts helper agents by itself when a job splits well (wide searches, parallel checks, a second-opinion review); the Agents panel opens when they start.' }
    }
    const r = await openPane($).catch(() => ({ isPlaced: false as const }))
    const list = await read($, helpers)
    const later = planned(await read($, plan), list)
    const at = Date.now()
    if (!list.length) return { text: 'No helpers yet this chat. When Claude splits a job across helper agents, type /agents-info again to see them.' }
    const t = totals(list, at)
    const line = (x: Helper) => {
      const [full, rest] = bar(progressOf(x), 16)
      const mark = x.status === 'running' ? '●' : x.status === 'done' ? '✓' : '✗'
      return [
        `${mark} ${x.title} (${tier(x.effort).name}, ${how(x)})`,
        x.task ? `   Task: ${x.task}` : '',
        x.status === 'running' ? `   Now: ${doing(x)}` : x.result ? `   Result: ${x.result}` : '',
        x.status === 'running' && x.said && x.lastTool ? `   Says: ${x.said}` : '',
        `   ${full}${rest} ${progressLine(x)} · ${clock(elapsed(x, at))}`,
      ].filter(Boolean).join('\n')
    }
    const running = list.filter(x => x.status === 'running')
    const finished = list.filter(x => x.status !== 'running').reverse()
    return {
      text: [
        `🦀 Agents · ${t.running} running · ${finished.length} finished · ${clock(t.time)}${r.isPlaced ? ' (panel open)' : ''}`,
        ...running.map(line),
        ...finished.map(line),
        ...later.map(s => `○ ${s.n}. ${s.subject} (planned)`),
      ].join('\n'),
    }
  })

  on('prompt.submit', async ($, e, next) => {
    // Only the person's own messages start a round or name the panel, not a helper's report or a task notice.
    if (!PERSON.has(e.origin?.kind ?? 'unclassified')) return next(e)
    await update($, since, () => Date.now())
    const head = heading(e.text)
    if (head && !head.startsWith('/') && !head.startsWith('<')) await update($, job, () => head)
    return next(e)
  })

  on('agent.spawn', async ($, e, next) => {
    const r = await next(e)
    if (r.agentId) {
      const id = r.agentId
      const x: Helper = {
        id, title: e.description || e.subagentType, type: e.subagentType, model: r.model || e.model || e.parentModel,
        startedAt: Date.now(), status: 'running', steps: 0, task: gist(e.prompt || '', 140) || undefined,
      }
      // The panel opens by itself when a batch of helpers starts (none was running), not on every spawn.
      const first = !(await read($, helpers)).some(y => y.status === 'running')
      await update($, helpers, list => [...list.filter(y => y.id !== id), x].slice(-MAX))
      void update($, now, () => Date.now())
      if (first) void openPane($).catch(() => undefined)
    }
    return r
  }).catch(($, e, next) => next(e))

  // The standing request to use helpers, at the end of the system prompt, wherever the Agent tool is offered.
  on('prompt.compose', async ($, e, next) => {
    const r = await next(e)
    if (!e.tools.includes('Agent') || (await $.store.get('manual')) === true) return r
    return { ...r, sections: [...r.sections.filter(x => x.id !== 'crew:delegate'), { id: 'crew:delegate', text: DELEGATE, scope: 'session' as const }] }
  }).catch(($, e, next) => next(e))

  // Each model request a helper makes: the model and effort it really runs on, and what it says between steps.
  on('turn.step', async function* ($, e, next) {
    const res = yield* next(e)
    const id = e.agentId
    if (id) {
      try {
        const said = gist(res.answer || '')
        await patch($, id, x => ({
          ...x,
          model: res.usage?.model || x.model,
          effort: e.effort === undefined ? x.effort : String(e.effort),
          said: said || x.said,
          status: 'running',
          endedAt: undefined,
        }))
      } catch {
        // A missed reading only makes the line a step behind.
      }
    }
    return res
  })

  // Each tool a helper calls moves its bar along and says, in plain words, what it is doing; task lists give real steps.
  on('tool.call', async ($, e, next) => {
    const id = e.agentId
    const tool = String(e.tool)
    const input = e as unknown as Record<string, unknown>
    if (id) {
      const label = toolLabel(tool, input)
      void patch($, id, x => ({ ...x, steps: x.steps + 1, lastTool: label, recent: push(x.recent, label) }))
    }
    if (!TASK_TOOLS.has(tool)) return next(e)
    const ran = await next(e)
    if (!ran.isError && ran.deny === undefined) {
      const text = ran.text ?? ''
      if (id) void patch($, id, x => ({ ...x, todo: applyTask(x.todo ?? [], tool, input, text) }))
      else void update($, plan, steps => applyTask(steps, tool, input, text))
    }
    return ran
  }).catch(($, e, next) => next(e))

  on('turn.complete', async ($, e, next) => {
    const id = e.agentId
    if (id) {
      const end = Date.now()
      const result = gist(e.answer || '', 160)
      await patch($, id, x => ({ ...x, status: e.reason === 'answer' ? 'done' : 'failed', endedAt: end, lastTool: undefined, result: result || x.result }))
      await update($, now, () => end)
    }
    return next(e)
  })

  // The band above the prompt: the round's progress and, for each running helper (as many as fit), what it is doing
  // now; a quiet line at rest (the owner: "I want the crew always", so it never disappears).
  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    if (e.props.hasSurvey) return next(e)
    const list = await read($, helpers)
    const from = await read($, since)
    const t0 = await read($, now)
    const round = list.filter(x => x.status === 'running' || x.startedAt >= from)
    const { Box, Text, Button } = $.ui.resolve(e)
    if (!round.length) {
      return (
        <Box gap={1}>
          <Text>🦀</Text>
          <Text bold>Agents</Text>
          <Text dimColor>{list.length ? `idle · ${list.length} earlier this chat` : 'idle · none yet this chat'}</Text>
          <Button key="crew-open" label="Details" onPress={() => void openPane($)} />
        </Box>
      )
    }
    const t = totals(round, Math.max(t0, ...round.map(x => x.startedAt)))
    const done = round.length - t.running
    const avg = round.reduce((a, x) => a + progressOf(x), 0) / round.length
    const [full, rest] = bar(avg, Math.max(6, Math.min(16, e.props.bodyColumns - 70)))
    const running = round.filter(x => x.status === 'running').slice(-Math.max(0, Math.min(3, (e.props.maxRows ?? 3) - 1)))
    return (
      <Box flexDirection="column">
        <Box gap={1}>
          <Text>🦀</Text>
          <Text bold>Agents</Text>
          <Text dimColor>{t.running ? `${t.running} running` : 'all done'}{done && t.running ? ` · ${done} done` : ''}</Text>
          <Text><Text color="success">{full}</Text><Text dimColor>{rest}</Text></Text>
          <Text dimColor>{clock(t.time)}</Text>
          <Button key="crew-open" label="Details" onPress={() => void openPane($)} />
        </Box>
        {running.map(x => (
          <Box key={`crew-now-${x.id}`} gap={1}>
            <Text color={tier(x.effort).colour}>●</Text>
            <Text wrap="truncate-end"><Text bold>{x.title}</Text><Text dimColor> · </Text>{doing(x)}</Text>
          </Box>
        ))}
      </Box>
    )
  })

  // The panel, laid out like Savvy Progress: heading, three stat cards, then Running / Finished / Planned. Each
  // helper: a crab in its tier's hat, its title, how it runs (tier, model, effort, type), its task, what it is doing
  // now (or its result), its last few steps, and its bar.
  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const list = await read($, helpers)
    const t0 = await read($, now)
    const open = await read($, showDone)
    const small = await read($, compact)
    const later = planned(await read($, plan), list)
    const title = (await read($, job)) || 'This chat'
    const els = $.ui.resolve(e)
    const { Box, Text, Button } = els
    const width = Math.max(20, e.props.bodyColumns)
    const at = Math.max(t0, ...list.map(x => x.startedAt), 0)
    const t = totals(list, at)
    const running = list.filter(x => x.status === 'running')
    const finished = list.filter(x => x.status !== 'running').reverse()

    const crab = (x: Helper | undefined, mode: 'working' | 'resting' | 'planned' | 'failed') => {
      const tr = tier(x?.effort)
      if ('Svg' in els && els.Svg) {
        const Svg = els.Svg
        return <Svg source={crabSvg(tr, mode)} alt={mode === 'working' ? `a crab in a ${tr.name} hat at work` : 'a crab'} width={36} height={36} isInteractive={mode === 'working' ? true : undefined} />
      }
      const pad = mode === 'working' ? [0, 1, 2, 1][Math.floor(at / 1000) % 4] ?? 0 : 0
      return <Text dimColor={mode === 'planned'}>{' '.repeat(pad)}🦀{' '.repeat(2 - pad)}</Text>
    }

    const stat = (label: string, value: string) => (
      <Box flexDirection="column" backgroundColor="#202124" paddingX={2} paddingY={1} flexGrow={1}>
        <Text dimColor>{label}</Text>
        <Text bold>{value}</Text>
      </Box>
    )

    // One bar per helper. Where the surface draws pictures, a filled track that always fits its column;
    // in a terminal, a line of cells sized to the pane.
    const meter = (p: number, colour: string, full: string, rest: string) =>
      'Svg' in els && els.Svg ? (
        <Box width="100%" height={1} backgroundColor="#2a2b2e">
          <Box width={`${Math.round(Math.max(0, Math.min(1, p)) * 100)}%`} height={1} backgroundColor={colour} />
        </Box>
      ) : (
        <Text wrap="truncate-end"><Text color={colour}>{full}</Text><Text dimColor>{rest}</Text></Text>
      )

    const labelled = (key: string, label: string, text: string, colour?: string) => (
      <Text key={key} wrap="wrap"><Text color={colour} dimColor={!colour}>{label} </Text>{text}</Text>
    )

    const row = (x: Helper) => {
      const tr = tier(x.effort)
      const [full, rest] = bar(progressOf(x), Math.max(8, width - 8))
      const live = x.status === 'running'
      const mark = live ? <Text color="success">●</Text> : x.status === 'done' ? <Text color="success">✓</Text> : <Text color="error">✗</Text>
      // The steps before the current one, oldest first.
      const earlier = (x.recent ?? []).slice(live ? 0 : undefined, live ? -1 : undefined).slice(-3)
      return (
        <Box key={`h-${x.id}`} flexDirection="column" marginTop={1}>
          <Box gap={1}>
            {crab(x, live ? 'working' : x.status === 'failed' ? 'failed' : 'resting')}
            <Box flexDirection="column" flexGrow={1} flexShrink={1}>
              <Box justifyContent="space-between">
                <Text bold wrap="truncate-end">{x.title}</Text>
                {mark}
              </Box>
              <Text><Text color={tr.colour}>{tr.name}</Text><Text dimColor> {how(x)}</Text></Text>
              {live ? labelled(`now-${x.id}`, 'Now', doing(x), tr.colour) : x.result ? labelled(`res-${x.id}`, x.status === 'failed' ? 'Stopped' : 'Found', x.result, x.status === 'failed' ? 'error' : 'success') : null}
              {small ? null : x.task ? labelled(`task-${x.id}`, 'Task', x.task) : null}
              {small || !live || !x.said || !x.lastTool ? null : labelled(`says-${x.id}`, 'Says', x.said)}
              {small ? null : earlier.map((s, i) => <Text key={`st-${x.id}-${i}`} dimColor wrap="truncate-end">  · {s}</Text>)}
              <Box justifyContent="space-between" gap={1}>
                <Text dimColor wrap="truncate-end">{progressLine(x)}</Text>
                <Text dimColor>{clock(elapsed(x, at))}</Text>
              </Box>
              {meter(progressOf(x), x.status === 'failed' ? 'error' : tr.colour, full, rest)}
            </Box>
          </Box>
        </Box>
      )
    }

    const plannedRow = (s: Step & { n: number }) => (
      <Box key={`p-${s.id}`} gap={1} marginTop={1}>
        {crab(undefined, 'planned')}
        <Box flexDirection="column" flexGrow={1}>
          <Text bold dimColor wrap="truncate-end">{s.n}. {s.subject}</Text>
          <Text dimColor>waiting</Text>
        </Box>
      </Box>
    )

    if (!list.length && !later.length) {
      return (
        <Box flexDirection="column">
          <Text dimColor>No helpers yet. When Claude splits a job across helper agents, each one shows up here live: its task, what it is doing and what it found.</Text>
        </Box>
      )
    }
    return (
      <Box flexDirection="column">
        <Text bold>{title}</Text>
        <Box gap={1} marginTop={1}>
          {stat('Running', String(running.length))}
          {stat('Finished', String(finished.length))}
          {stat('Time', clock(t.time))}
        </Box>
        <Box marginTop={1}>
          <Button key="crew-compact" plain label={small ? 'Expand' : 'Collapse'} onPress={() => void update($, compact, v => !v)} />
        </Box>
        <Text dimColor>Running · {running.length}</Text>
        {running.map(row)}
        {finished.length ? (
          <Box marginTop={1}>
            <Button key="crew-done" plain label={`${open ? '▾' : '▸'} Finished · ${finished.length}`} onPress={() => void update($, showDone, v => !v)} />
          </Box>
        ) : null}
        {open ? finished.map(row) : null}
        {later.length ? <Box marginTop={1}><Text dimColor>Planned · {later.length}</Text></Box> : null}
        {later.map(plannedRow)}
      </Box>
    )
  })
}
