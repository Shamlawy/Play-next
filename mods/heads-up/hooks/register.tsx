import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register, RenderChildren } from 'claude-code'

import type { Card } from '../types'
import { MAX_CARDS, MIN_REPLY, SYSTEM, asText, hasAny, heading, openCount, parse, prompt, toggle } from './sift.ts'

type $ = EngineInterface

// After each of Claude's replies, a small model boils it down to what needs the person
// (things to look at, things to do, ideas) and shows only that in a side panel.
const PANE = 'heads-up'
const TITLE = 'Heads-up'
const MODEL = 'haiku'
const PERSON = new Set(['composer', 'bridge', 'sdk', 'unclassified'])

const cards = atom({ plugin: 'heads-up', key: 'cards' } as const, [])
const ask = atom({ plugin: 'heads-up', key: 'ask' } as const, '')
const busy = atom({ plugin: 'heads-up', key: 'busy' } as const, false)
const showOld = atom({ plugin: 'heads-up', key: 'showOld' } as const, false)
// What the panel says when the newest reply had nothing for the person.
const last = atom({ plugin: 'heads-up', key: 'last' } as const, '')

const openPane = ($: $) => $.ui.open({ id: PANE, title: TITLE }).catch(() => ({ isPlaced: false as const }))
const isOff = async ($: $) => (await $.store.get('off')) === true

async function status($: $) {
  const n = openCount(await read($, cards))
  $.ui.status(n ? `👀 ${n} need${n === 1 ? 's' : ''} you · /heads-up` : undefined)
}

async function sift($: $, reply: string, question: string) {
  await update($, busy, () => true)
  try {
    const r = await $.model.complete({ model: MODEL, effort: 'low', maxTokens: 1200, timeoutMs: 60_000, system: SYSTEM, prompt: prompt(question, reply) })
    const got = r.isAnswered ? parse(r.text) : undefined
    if (!got) return
    const at = await $.clock.now()
    const card: Card = { id: `c${at}`, at, ask: heading(question), ...got, done: [] }
    if (!hasAny(card)) {
      await update($, last, () => (card.gist ? `✓ ${card.gist}` : '✓ Nothing needs you in the last reply.'))
      return
    }
    await update($, last, () => '')
    await update($, cards, list => [...list.filter(c => c.id !== card.id), card].sort((a, b) => a.at - b.at).slice(-MAX_CARDS))
    await status($)
    const opened = await openPane($)
    if (!opened.isPlaced) {
      const n = card.attention.length + card.actions.length
      $.ui.toast(n ? `👀 ${n} thing${n === 1 ? '' : 's'} need${n === 1 ? 's' : ''} you · /heads-up` : `💡 ${card.ideas.length} idea${card.ideas.length === 1 ? '' : 's'} · /heads-up`)
    }
  } finally {
    await update($, busy, () => false)
  }
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'heads-up',
      description: 'Open the Heads-up panel: only what needs you from Claude\'s replies (to look at, to do, ideas). "/heads-up off" stops it, "/heads-up on" starts it again.',
      argumentHint: '[on|off|clear]',
    })
    return next(e)
  })

  on('command.run', { command: 'heads-up' }, async ($, e) => {
    const arg = (e.args ?? '').trim().toLowerCase()
    if (arg === 'off') {
      await $.store.set('off', true)
      $.ui.status(undefined)
      return { text: 'Heads-up is off: replies are no longer summarised. "/heads-up on" turns it back on.' }
    }
    if (arg === 'on') {
      await $.store.delete('off')
      await status($)
      return { text: 'Heads-up is on: after each long reply, what needs you shows in the side panel.' }
    }
    if (arg === 'clear') {
      await update($, cards, () => [])
      await status($)
      return { text: 'Heads-up cleared.' }
    }
    const opened = await openPane($)
    const list = await read($, cards)
    const off = (await isOff($)) ? '\n(Heads-up is off: "/heads-up on" turns it back on.)' : ''
    if (!list.length) return { text: `Nothing needs you yet.${off}` }
    // The newest reply's card as text too, for a screen that seats no panes (the phone app).
    const newest = list[list.length - 1]!
    const more = list.length > 1 ? `\n\n${list.length - 1} earlier · ${openCount(list.slice(0, -1))} still open${opened.isPlaced ? ' (in the panel)' : ''}` : ''
    return { text: `${asText(newest)}${more}${off}` }
  })

  on('prompt.submit', async ($, e, next) => {
    // Only the person's own messages, not a helper's report or a task notice.
    if (PERSON.has(e.origin?.kind ?? 'unclassified') && !e.text.trim().startsWith('/')) await update($, ask, () => e.text)
    return next(e)
  }).catch(($, e, next) => next(e))

  // The main loop's finished answer: sift it in a dispatch of its own, so the reply is never held up.
  on('turn.complete', async ($, e, next) => {
    const r = await next(e)
    if (e.agentId || e.reason !== 'answer') return r
    const reply = (r.text || e.answer || '').trim()
    if (reply.length < MIN_REPLY || (await isOff($))) return r
    const question = await read($, ask)
    $.clock.after(10, () => void sift($, reply, question).catch(() => update($, busy, () => false)))
    return r
  }).catch(($, e, next) => next(e))

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const { Box, Text, Button } = $.ui.resolve(e)
    const list = await read($, cards)
    const working = await read($, busy)
    const quiet = await read($, last)
    const old = await read($, showOld)
    const newest = list[list.length - 1]
    const earlier = list.slice(0, -1).reverse()

    const tick = (c: Card, key: string, text: string, colour: string) => {
      const ticked = c.done.includes(key)
      return (
        <Button
          key={`hu-${c.id}-${key}`}
          plain
          onPress={() => void update($, cards, l => l.map(x => (x.id === c.id ? toggle(x, key) : x))).then(() => status($))}
        >
          <Text color={ticked ? undefined : colour} dimColor={ticked}>{ticked ? '☑' : '☐'}</Text> <Text dimColor={ticked} strikethrough={ticked}>{text}</Text>
        </Button>
      )
    }

    const section = (label: string, colour: string, rows: RenderChildren[]) =>
      rows.length ? (
        <Box key={`s-${label}`} flexDirection="column" marginTop={1}>
          <Text bold color={colour}>{label}</Text>
          {rows}
        </Box>
      ) : null

    const card = (c: Card, isNewest: boolean) => (
      <Box key={`card-${c.id}`} flexDirection="column" marginTop={isNewest ? 0 : 1}>
        {c.ask ? <Text dimColor wrap="truncate-end">“{c.ask}”</Text> : null}
        {c.gist ? <Text>✓ {c.gist}</Text> : null}
        {section('⚠ Needs your attention', 'warning', c.attention.map((x, i) => tick(c, `n${i}`, x, 'warning')))}
        {section('👉 For you to do', 'suggestion', c.actions.map((x, i) => tick(c, `a${i}`, x, 'suggestion')))}
        {section('💡 Ideas', 'success', c.ideas.map((x, i) => <Text key={`i-${c.id}-${i}`}>• {x}</Text>))}
      </Box>
    )

    return (
      <Box flexDirection="column">
        {working ? <Text dimColor>Reading the last reply…</Text> : null}
        {quiet && !working ? <Text dimColor>{quiet}</Text> : null}
        {quiet && newest && !working ? <Box marginTop={1}><Text dimColor>Earlier:</Text></Box> : null}
        {newest ? card(newest, true) : !working && !quiet ? <Text dimColor>Nothing needs you yet. After each long reply, what needs your attention, what you have to do and any ideas show up here.</Text> : null}
        {earlier.length ? (
          <Box marginTop={1}>
            <Button key="hu-old" plain label={`${old ? '▾' : '▸'} Earlier replies · ${earlier.length} · ${openCount(earlier)} open`} onPress={() => void update($, showOld, v => !v)} />
          </Box>
        ) : null}
        {old ? earlier.map(c => card(c, false)) : null}
        {list.length ? (
          <Box marginTop={1}>
            <Button key="hu-clear" plain label="Clear all" onPress={() => void update($, cards, () => []).then(() => status($))} />
          </Box>
        ) : null}
      </Box>
    )
  })
}
