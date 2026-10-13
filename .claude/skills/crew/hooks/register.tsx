import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Helper } from '../types'
import { bar, billed, clock, cost, crabSvg, elapsed, k, modelName, progress, toolLabel, totals, usd, window } from './numbers.ts'

type $ = EngineInterface

// Savvy-Progress-style view of Claude's helper agents: what each is doing, how far along, what it costs.
const PANE = 'crew'
const MAX = 60

const helpers = atom({ plugin: 'crew', key: 'helpers' } as const, [])
const now = atom({ plugin: 'crew', key: 'now' } as const, 0)
// When the person last sent a message: the band shows this round's helpers (and any still running).
const since = atom({ plugin: 'crew', key: 'since' } as const, 0)
const showDone = atom({ plugin: 'crew', key: 'showDone' } as const, true)

const patch = ($: $, id: string, fn: (h: Helper) => Helper) => update($, helpers, list => list.map(h => (h.id === id ? fn(h) : h)))
const ctxPct = (h: Helper) => Math.min(100, Math.round((h.context / (/haiku-4/.test(h.model) ? 200_000 : 1_000_000)) * 100))
const openPane = ($: $) => $.ui.open({ id: PANE, title: 'Agents' })

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({ name: 'agents-info', description: "Open the Agents panel: every helper Claude starts, live (model, progress, tokens, rough cost, time)." })
    // A one-second tick while something runs: times and crabs move; nothing redraws at rest.
    $.clock.every(1000, async () => {
      const list = await read($, helpers)
      if (list.some(h => h.status === 'running')) await update($, now, () => Date.now())
    })
    await update($, now, () => Date.now())
    return next(e)
  })

  on('command.run', { command: 'agents-info' }, async $ => {
    const r = await openPane($)
    const list = await read($, helpers)
    const t = totals(list, Date.now())
    const summary = list.length
      ? `${list.length} helper(s) this chat, ${t.running} running · ${k(t.tokens)} tokens · ${usd(t.cost)} · ${clock(t.time)}`
      : 'No helpers yet this chat.'
    return { text: r.isPlaced ? `Agents panel open. ${summary}` : `${summary} (This screen can't show the panel; the band above the prompt shows the same while helpers run.)` }
  })

  on('prompt.submit', async ($, e, next) => {
    await update($, since, () => Date.now())
    return next(e)
  })

  on('agent.spawn', async ($, e, next) => {
    const r = await next(e)
    if (r.agentId) {
      const id = r.agentId
      const h: Helper = {
        id, title: e.description || e.subagentType, type: e.subagentType, model: r.model || e.model || e.parentModel,
        startedAt: Date.now(), status: 'running', steps: 0, tokens: 0, costUsd: 0, context: 0,
      }
      void update($, helpers, list => [...list.filter(x => x.id !== id), h].slice(-MAX))
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

  // Each tool a helper calls moves its bar along and says what it is doing.
  on('tool.call', ($, e, next) => {
    const id = e.agentId
    if (id) void patch($, id, x => ({ ...x, steps: x.steps + 1, lastTool: toolLabel(String(e.tool), e as unknown as Record<string, unknown>) }))
    return next(e)
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
    const round = list.filter(h => h.status === 'running' || h.startedAt >= from)
    if (!round.length || e.props.hasSurvey) return next(e)
    const { Box, Text, Button } = $.ui.resolve(e)
    const t = totals(round, Math.max(t0, ...round.map(h => h.startedAt)))
    const done = round.length - t.running
    const avg = round.reduce((a, h) => a + progress(h), 0) / round.length
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

  // The panel: totals, then each helper with its crab, model, bar and numbers.
  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const list = await read($, helpers)
    const t0 = await read($, now)
    const open = await read($, showDone)
    const els = $.ui.resolve(e)
    const { Box, Text, Button } = els
    const width = Math.max(20, e.props.bodyColumns)
    const at = Math.max(t0, ...list.map(h => h.startedAt), 0)
    const t = totals(list, at)
    const running = list.filter(h => h.status === 'running')
    const finished = list.filter(h => h.status !== 'running').reverse()
    const job = running[running.length - 1]?.title ?? finished[0]?.title ?? 'This chat'

    const crab = (x: Helper) => {
      const walking = x.status === 'running'
      if ('Svg' in els && els.Svg) {
        const Svg = els.Svg
        return <Svg source={crabSvg(walking, x.status === 'failed' ? '#888888' : undefined)} alt={walking ? 'a crab at work' : 'a crab resting'} width={walking ? 36 : 24} height={18} isInteractive={walking ? true : undefined} />
      }
      const pad = walking ? [0, 1, 2, 1][Math.floor(at / 1000) % 4] ?? 0 : 0
      return <Text>{' '.repeat(pad)}🦀{' '.repeat(2 - pad)}</Text>
    }

    const stat = (label: string, value: string) => (
      <Box flexDirection="column" borderStyle="round" borderDimColor paddingX={1} flexGrow={1}>
        <Text dimColor>{label}</Text>
        <Text bold>{value}</Text>
      </Box>
    )

    const row = (x: Helper) => {
      const [full, rest] = bar(progress(x), Math.max(8, width - 4))
      const mark = x.status === 'running' ? <Text color="success">●</Text> : x.status === 'done' ? <Text color="success">✓</Text> : <Text color="error">✗</Text>
      return (
        <Box key={`h-${x.id}`} flexDirection="column" marginTop={1}>
          <Box gap={1}>
            {crab(x)}
            <Box flexDirection="column" flexGrow={1}>
              <Box justifyContent="space-between">
                <Text bold wrap="truncate-end">{x.title}</Text>
                {mark}
              </Box>
              <Text dimColor><Text color="success">{x.type}</Text> {modelName(x.model)}{x.effort ? ` · ${x.effort}` : ''}</Text>
            </Box>
          </Box>
          <Box justifyContent="flex-end">
            <Text dimColor>ctx {ctxPct(x)}% · {k(x.context)} · {usd(x.costUsd)} · {clock(elapsed(x, at))}</Text>
          </Box>
          <Text><Text color={x.status === 'failed' ? 'error' : 'success'}>{full}</Text><Text dimColor>{rest}</Text></Text>
          {x.status === 'running' && x.lastTool ? <Text dimColor wrap="truncate-end">{x.lastTool}</Text> : null}
        </Box>
      )
    }

    if (!list.length) {
      return (
        <Box flexDirection="column">
          <Text dimColor>No helpers yet. When Claude splits a job across helper agents, each one shows up here live.</Text>
        </Box>
      )
    }
    return (
      <Box flexDirection="column">
        <Text bold>{job}</Text>
        <Box gap={1} marginTop={1}>
          {stat('Cost', usd(t.cost))}
          {stat('Tokens', k(t.tokens))}
          {stat('Time', clock(t.time))}
        </Box>
        <Box marginTop={1}><Text dimColor>Running · {running.length}</Text></Box>
        {running.map(row)}
        {finished.length ? (
          <Box marginTop={1}>
            <Button key="crew-done" plain label={`${open ? '▾' : '▸'} Finished · ${finished.length}`} onPress={() => void update($, showDone, v => !v)} />
          </Box>
        ) : null}
        {open ? finished.map(row) : null}
        <Box marginTop={1}><Text dimColor>Cost is a rough guide from list prices; a Claude plan isn't billed per token.</Text></Box>
      </Box>
    )
  })
}
