import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register, RenderChildren } from 'claude-code'

import type { Card } from '../types'
import { BOX, MAX_CARDS, MIN_REPLY, SYSTEM, asText, hasAny, heading, openCount, parse, prompt, toggle } from './sift.ts'

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
// Lines whose explanation a tap opened (phones have no hover), as `<card id>-<key>`.
const opened = atom({ plugin: 'heads-up', key: 'opened' } as const, [])
// The newest card's id when the person closed the panel by hand ('' = not closed): it stays closed until a newer card.
const closedAt = atom({ plugin: 'heads-up', key: 'closedAt' } as const, '')

const newestKey = (list: readonly Card[]) => list[list.length - 1]?.id ?? 'none'
const openPane = ($: $) => $.ui.open({ id: PANE, title: TITLE }).catch(() => ({ isPlaced: false as const }))

// A panel a mod opens on its own waits unseen below 144 columns (the desktop app's chat is narrower), while one
// opened in answer to the person's message is placed at any width. So each message opens it, unless they closed
// it and nothing new has come since.
async function openOnMessage($: $) {
  if ((await isOff($)) || !(await isPanel($))) return
  const list = await read($, cards)
  const closed = await read($, closedAt)
  if (closed && closed === newestKey(list)) return
  await update($, closedAt, () => '')
  await openPane($)
}
const isOff = async ($: $) => (await $.store.get('off')) === true
// 'panel': the side panel with hover, where mods draw (a chat on a computer). 'box': the lines go in a box at the top
// of each long reply, the only thing a cloud chat shows. Unchosen (the owner: "I want the panel always, not me typing
// something"), it follows where the chat runs: the box in a cloud chat, the panel everywhere else.
// `/heads-up panel` and `/heads-up box` pin a choice on this machine.
const isPanel = async ($: $) => {
  const mode = await $.store.get('mode')
  if (mode === 'panel' || mode === 'box') return mode === 'panel'
  return (await $.env.get('CLAUDE_CODE_REMOTE')) !== 'true'
}

async function status($: $) {
  if ((await isOff($)) || !(await isPanel($))) return $.ui.status(undefined)
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
      description: 'The 2-4 lines that matter in Claude\'s long replies: the side panel with hover on a computer, a Heads-up box at the top of each in a cloud chat; "/heads-up panel" or "/heads-up box" picks one here. "/heads-up off" stops it, "/heads-up on" starts it again.',
      argumentHint: '[box|panel|on|off|clear]',
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
      return { text: (await isPanel($)) ? 'Heads-up is on: after each long reply, what needs you shows in the side panel.' : 'Heads-up is on: each long reply starts with a short Heads-up box of the lines that matter.' }
    }
    if (arg === 'panel') {
      await $.store.set('mode', 'panel')
      await status($)
      await openPane($)
      return { text: 'Heads-up is in panel mode: after each long reply, the side panel shows the lines that need you; point at one to see why. Mod panels only draw in a Local chat on your PC; "/heads-up box" goes back to the box at the top of replies.' }
    }
    if (arg === 'box') {
      await $.store.set('mode', 'box')
      await status($)
      return { text: 'Heads-up is in box mode: each long reply starts with a short Heads-up box of the 2-4 lines that matter.' }
    }
    if (arg === 'clear') {
      await update($, cards, () => [])
      await status($)
      return { text: 'Heads-up cleared.' }
    }
    const off = (await isOff($)) ? '\n(Heads-up is off: "/heads-up on" turns it back on.)' : ''
    if (!(await isPanel($))) return { text: `Heads-up is in box mode: each long reply starts with a short Heads-up box of the 2-4 lines that matter. "/heads-up panel" switches to the side panel with hover (it draws in a chat on a computer).${off}` }
    const opened = await openPane($)
    const list = await read($, cards)
    if (!list.length) return { text: `Nothing needs you yet.${off}` }
    // The newest reply's card as text too, for a screen that seats no panes (the phone app).
    const newest = list[list.length - 1]!
    const more = list.length > 1 ? `\n\n${list.length - 1} earlier · ${openCount(list.slice(0, -1))} still open${opened.isPlaced ? ' (in the panel)' : ''}` : ''
    return { text: `${asText(newest)}${more}${off}` }
  })

  on('prompt.submit', async ($, e, next) => {
    // Only the person's own messages, not a helper's report or a task notice.
    // A slash command clears it, so the reply after one isn't headed with an older, unrelated message.
    if (PERSON.has(e.origin?.kind ?? 'unclassified')) {
      const slash = e.text.trim().startsWith('/')
      await update($, ask, () => (slash ? '' : e.text))
      if (!slash) await openOnMessage($).catch(() => undefined)
    }
    return next(e)
  }).catch(($, e, next) => next(e))

  // Box mode: the instruction rides at the end of the system prompt.
  on('prompt.compose', async ($, e, next) => {
    const r = await next(e)
    if ((await isOff($)) || (await isPanel($))) return r
    return { ...r, sections: [...r.sections.filter(x => x.id !== 'heads-up:box'), { id: 'heads-up:box', text: BOX, scope: 'session' as const }] }
  }).catch(($, e, next) => next(e))

  // Closed by hand: stay closed until a newer card arrives.
  on('ui.close', { id: PANE }, async ($, e, next) => {
    if (e.origin?.kind === 'person') {
      const list = await read($, cards)
      await update($, closedAt, () => newestKey(list))
    }
    return next(e)
  }).catch(($, e, next) => next(e))

  // The main loop's finished answer: sift it in a dispatch of its own, so the reply is never held up.
  on('turn.complete', async ($, e, next) => {
    const r = await next(e)
    if (e.agentId || e.reason !== 'answer') return r
    const reply = (r.text || e.answer || '').trim()
    if (reply.length < MIN_REPLY || (await isOff($)) || !(await isPanel($))) return r
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
    const open = await read($, opened)
    const touch = e.surface === 'mobile'
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

    // A line and its explanation: hidden until the pointer is over the line (the keyed Box is the
    // hover scope, so nothing runs for it), or on a phone until its ⓘ is tapped.
    const row = (c: Card, key: string, body: RenderChildren) => {
      const why = c.why?.[key]
      if (!why) return body
      const id = `${c.id}-${key}`
      const isOpen = touch && open.includes(id)
      return (
        <Box key={`hr-${id}`} flexDirection="column">
          {touch ? (
            <Box flexDirection="row">
              <Button key={`hw-${id}`} plain label={isOpen ? '▾ ' : 'ⓘ '} onPress={() => void update($, opened, l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id].slice(-40)))} />
              <Box flexShrink={1}>{body}</Box>
            </Box>
          ) : (
            body
          )}
          {touch ? (
            isOpen ? <Box marginLeft={2}><Text dimColor>↳ {why}</Text></Box> : null
          ) : (
            <Box marginLeft={2} display="none" hover={{ display: 'flex' }}><Text dimColor>↳ {why}</Text></Box>
          )}
        </Box>
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
        {section('⚠ Needs your attention', 'warning', c.attention.map((x, i) => row(c, `n${i}`, tick(c, `n${i}`, x, 'warning'))))}
        {section('👉 For you to do', 'suggestion', c.actions.map((x, i) => row(c, `a${i}`, tick(c, `a${i}`, x, 'suggestion'))))}
        {section('💡 Ideas', 'success', c.ideas.map((x, i) => row(c, `i${i}`, <Text key={`i-${c.id}-${i}`}>• {x}</Text>)))}
        {isNewest && c.why && Object.keys(c.why).length ? (
          <Box marginTop={1}><Text dimColor>{touch ? 'Tap ⓘ to see why.' : 'Point at a line to see why.'}</Text></Box>
        ) : null}
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
