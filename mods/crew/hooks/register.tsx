import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Helper, Step } from '../types'
import {
  applyTask, bar, billed, clock, cost, crabSvg, elapsed, k, modelName, progressOf, stepLine, tier, toolLabel, totals, usd, window,
} from './numbers.ts'

type $ = EngineInterface

// Savvy-Progress-style view of Claude's helper agents: what each is doing, how far along, what it costs.
const PANE = 'crew'
const MAX = 60
const TASK_TOOLS = new Set(['TaskCreate', 'TaskUpdate', 'TodoWrite'])

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

const patch = ($: $, id: string, fn: (x: Helper) => Helper) => update($, helpers, list => list.map(x => (x.id === id ? fn(x) : x)))
const ctxPct = (x: Helper) => Math.min(100, Math.round((x.context / (/haiku-4/.test(x.model) ? 200_000 : 1_000_000)) * 100))
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

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({ name: 'agents-info', description: 'Open the Agents panel: every helper Claude starts, live (model, progress, tokens, rough cost, time).' })
    // A one-second tick while something runs: times and crabs move; nothing redraws at rest.
    $.clock.every(1000, async () => {
      const list = await read($, helpers)
      if (list.some(x => x.status === 'running')) await update($, now, () => Date.now())
    })
    await update($, now, () => Date.now())
    return next(e)
  })

  // The reply is the whole crew as text too, so a screen that seats no panes (the phone app) still shows it.
  on('command.run', { command: 'agents-info' }, async $ => {
    const r = await openPane($).catch(() => ({ isPlaced: false as const }))
    const list = await read($, helpers)
    const later = planned(await read($, plan), list)
    const at = Date.now()
    if (!list.length) return { text: 'No helpers yet this chat. When Claude splits a job across helper agents, type /agents-info again to see them.' }
    const t = totals(list, at)
    const line = (x: Helper) => {
      const [full, rest] = bar(progressOf(x), 16)
      const mark = x.status === 'running' ? '●' : x.status === 'done' ? '✓' : '✗'
      return `${mark} ${x.title} (${tier(x.effort).name}, ${modelName(x.model)}${x.effort ? ` · ${x.effort}` : ''})\n   ${full}${rest} ${stepLine(x)} · ctx ${ctxPct(x)}% · ${k(x.context)} · ${usd(x.costUsd)} · ${clock(elapsed(x, at))}`
    }
    const running = list.filter(x => x.status === 'running')
    const finished = list.filter(x => x.status !== 'running').reverse()
    return {
      text: [
        `🦀 Agents · ${t.running} running · ${finished.length} finished · ${k(t.tokens)} tokens · ${usd(t.cost)} · ${clock(t.time)}${r.isPlaced ? ' (panel open)' : ''}`,
        ...running.map(line),
        ...finished.map(line),
        ...later.map(s => `○ ${s.n}. ${s.subject} (planned)`),
        'Cost is a rough guide from list prices; a Claude plan is not billed per token.',
      ].join('\n'),
    }
  })

  on('prompt.submit', async ($, e, next) => {
    await update($, since, () => Date.now())
    const head = heading(e.text)
    if (head && !head.startsWith('/')) await update($, job, () => head)
    return next(e)
  })

  on('agent.spawn', async ($, e, next) => {
    const r = await next(e)
    if (r.agentId) {
      const id = r.agentId
      const x: Helper = {
        id, title: e.description || e.subagentType, type: e.subagentType, model: r.model || e.model || e.parentModel,
        startedAt: Date.now(), status: 'running', steps: 0, tokens: 0, costUsd: 0, context: 0,
      }
      void update($, helpers, list => [...list.filter(y => y.id !== id), x].slice(-MAX))
      void update($, now, () => Date.now())
    }
    return r
  }).catch(($, e, next) => next(e))

  // Each model request a helper makes: its tokens, cost and context size, as the API reported them.
  on('turn.step', async function* ($, e, next) {
    const res = yield* next(e)
    const id = e.agentId
    if (id && res.usage) {
      const u = res.usage
      try {
        await patch($, id, x => ({
          ...x,
          model: u.model || x.model,
          effort: e.effort === undefined ? x.effort : String(e.effort),
          tokens: x.tokens + billed(u),
          costUsd: x.costUsd + cost(u.model || x.model, u),
          context: window(u),
          status: 'running',
          endedAt: undefined,
        }))
      } catch {
        // A missed reading only makes the numbers a little low.
      }
    }
    return res
  })

  // Each tool a helper calls moves its bar along and says what it is doing; task lists give real steps.
  on('tool.call', async ($, e, next) => {
    const id = e.agentId
    const tool = String(e.tool)
    const input = e as unknown as Record<string, unknown>
    if (id) void patch($, id, x => ({ ...x, steps: x.steps + 1, lastTool: toolLabel(tool, input) }))
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
      await patch($, id, x => ({ ...x, status: e.reason === 'answer' ? 'done' : 'failed', endedAt: end, lastTool: undefined }))
      await update($, now, () => end)
    }
    return next(e)
  })

  // The band above the prompt: one line with the crew's progress while this round has helpers.
  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    const list = await read($, helpers)
    const from = await read($, since)
    const t0 = await read($, now)
    const round = list.filter(x => x.status === 'running' || x.startedAt >= from)
    if (!round.length || e.props.hasSurvey) return next(e)
    const { Box, Text, Button } = $.ui.resolve(e)
    const t = totals(round, Math.max(t0, ...round.map(x => x.startedAt)))
    const done = round.length - t.running
    const avg = round.reduce((a, x) => a + progressOf(x), 0) / round.length
    const [full, rest] = bar(avg, Math.max(6, Math.min(24, e.props.bodyColumns - 62)))
    return (
      <Box gap={1}>
        <Text>🦀</Text>
        <Text bold>Agents</Text>
        <Text dimColor>{t.running ? `${t.running} running` : 'all done'}{done && t.running ? ` · ${done} done` : ''}</Text>
        <Text><Text color="success">{full}</Text><Text dimColor>{rest}</Text></Text>
        <Text dimColor>{k(t.tokens)} · {usd(t.cost)} · {clock(t.time)}</Text>
        <Button key="crew-open" label="Details" onPress={() => void openPane($)} />
      </Box>
    )
  })

  // The panel, laid out like Savvy Progress: heading, three stat cards, then Running / Finished / Planned,
  // each helper a crab in its tier's hat, its tier word and bar in the tier's colour.
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

    const row = (x: Helper) => {
      const tr = tier(x.effort)
      const [full, rest] = bar(progressOf(x), Math.max(8, width - 8))
      const mark = x.status === 'running' ? <Text color="success">●</Text> : x.status === 'done' ? <Text color="success">✓</Text> : <Text color="error">✗</Text>
      return (
        <Box key={`h-${x.id}`} flexDirection="column" marginTop={1}>
          <Box gap={1}>
            {crab(x, x.status === 'running' ? 'working' : x.status === 'failed' ? 'failed' : 'resting')}
            <Box flexDirection="column" flexGrow={1}>
              <Box justifyContent="space-between">
                <Text bold wrap="truncate-end">{x.title}</Text>
                {mark}
              </Box>
              {small ? null : <Text><Text color={tr.colour}>{tr.name}</Text><Text dimColor> {modelName(x.model)}{x.effort ? ` · ${x.effort}` : ''}</Text></Text>}
              {small ? null : (
                <Box justifyContent="space-between" gap={1}>
                  <Text wrap="truncate-end">{stepLine(x)}</Text>
                  <Text dimColor>ctx {ctxPct(x)}% · {k(x.context)} {usd(x.costUsd)} {clock(elapsed(x, at))}</Text>
                </Box>
              )}
              <Text><Text color={x.status === 'failed' ? 'error' : tr.colour}>{full}</Text><Text dimColor>{rest}</Text></Text>
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
          <Text dimColor>No helpers yet. When Claude splits a job across helper agents, each one shows up here live.</Text>
        </Box>
      )
    }
    return (
      <Box flexDirection="column">
        <Text bold>{title}</Text>
        <Box gap={1} marginTop={1}>
          {stat('Cost', usd(t.cost))}
          {stat('Tokens', k(t.tokens))}
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
        <Box marginTop={1}><Text dimColor>Cost is a rough guide from list prices; a Claude plan isn't billed per token.</Text></Box>
      </Box>
    )
  })
}
