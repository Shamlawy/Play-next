import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Card } from '../types'
import { ACCENT, BOX, CHOICE_BG, LOOK, MAX_CARDS, MIN_REPLY, PICKED_BG, ROW_BG, SYSTEM, asText, chips, hasAny, heading, iconSvg, openCount, parse, pick, pickText, prompt, toggle } from './sift.ts'
import type { Kind } from './sift.ts'

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
// Whether the ticked tiles under "Done · N" are shown.
const showDone = atom({ plugin: 'heads-up', key: 'showDone' } as const, false)
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

  // The panel (0.7, the owner picked "B · Tiles": "B but make it better"): the gist with count chips, then one
  // tinted tile per line (pixel icon, the short line, a tick box); the why shows inside the tile on hover (a tap
  // on ⓘ on a phone). Ticked tiles drop to a collapsed "Done · N" line; earlier replies to one quiet line.
  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const els = $.ui.resolve(e)
    const { Box, Text, Button } = els
    const list = await read($, cards)
    const working = await read($, busy)
    const quiet = await read($, last)
    const old = await read($, showOld)
    const open = await read($, opened)
    const doneOpen = await read($, showDone)
    const touch = e.surface === 'mobile'
    // When the newest reply had nothing for the person, every kept card is an earlier one.
    const newest = quiet ? undefined : list[list.length - 1]
    const earlier = (quiet ? list : list.slice(0, -1)).slice().reverse()

    const icon = (kind: Kind) =>
      'Svg' in els && els.Svg ? (
        <els.Svg source={iconSvg(kind)} alt={LOOK[kind].alt} width={16} height={16} />
      ) : (
        <Text color={LOOK[kind].fg}>{LOOK[kind].emoji}</Text>
      )

    const flip = (id: string) => void update($, opened, l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id].slice(-40)))

    // The why, inside the tile: drawn hidden and shown while the pointer is over the tile (its keyed Box is the
    // hover scope, so nothing runs), or on a phone once its ⓘ is tapped.
    const whyLine = (id: string, why: string | undefined) =>
      !why ? null : touch ? (
        open.includes(id) ? <Text dimColor>{why}</Text> : null
      ) : (
        <Box display="none" hover={{ display: 'flex' }}><Text dimColor>{why}</Text></Box>
      )
    const whyMark = (id: string, why: string | undefined) =>
      !why ? null : touch ? (
        <Button key={`hw-${id}`} plain label={open.includes(id) ? '▾' : 'ⓘ'} onPress={() => flip(id)} />
      ) : (
        <Text dimColor>ⓘ</Text>
      )

    // Only the box: a desktop draws a Button's label in full (a long "Mark done: …" squeezed the line to a sliver).
    const tick = (c: Card, key: string) => {
      const ticked = c.done.includes(key)
      return (
        <Button
          key={`hu-${c.id}-${key}`}
          plain
          onPress={() => void update($, cards, l => l.map(x => (x.id === c.id ? toggle(x, key) : x))).then(() => status($))}
        >
          <Text color={ticked ? undefined : LOOK[key.startsWith('n') ? 'attention' : 'action'].fg} dimColor={ticked}>{ticked ? '☑' : '☐'}</Text>
        </Button>
      )
    }

    const tile = (c: Card, kind: Kind, key: string, text: string, withTick: boolean) => {
      const id = `${c.id}-${key}`
      const why = c.why?.[key]
      const ticked = c.done.includes(key)
      return (
        <Box key={`ht-${id}`} flexDirection="row" gap={1} marginTop={1} paddingX={1} backgroundColor={ticked ? CHOICE_BG : LOOK[kind].bg}>
          {icon(kind)}
          <Box flexDirection="column" flexGrow={1} flexShrink={1}>
            <Text dimColor={ticked} strikethrough={ticked}>{text}</Text>
            {whyLine(id, why)}
          </Box>
          {whyMark(id, why)}
          {withTick ? tick(c, key) : null}
        </Box>
      )
    }

    const setChoice = (c: Card, fn: (x: NonNullable<Card['choice']>) => NonNullable<Card['choice']>) =>
      update($, cards, l => l.map(x => (x.id === c.id && x.choice ? { ...x, choice: fn(x.choice) } : x)))

    // The pick goes back to Claude as the person's own next message; the block then says only what was sent.
    const send = async (c: Card, picked: number[]) => {
      if (!c.choice || c.choice.sent) return
      const text = pickText(c.choice, picked)
      await setChoice(c, x => ({ ...x, picked, sent: text }))
      await status($)
      await $.prompt.submit({ text, asUser: true })
    }

    // Options laid out in the reply, each a full-width row; a picked one gets a blue edge. Send shows once
    // something is picked; "None of these" is always there.
    const choiceBlock = (c: Card) => {
      const ch = c.choice
      if (!ch) return null
      if (ch.sent) {
        return (
          <Box key={`hc-${c.id}`} marginTop={1} paddingX={1} backgroundColor={CHOICE_BG}>
            <Text wrap="truncate-end"><Text color="success">✓ Sent: </Text><Text dimColor>{ch.sent}</Text></Text>
          </Box>
        )
      }
      const option = (o: { label: string; why: string }, i: number) => {
        const chosen = ch.picked.includes(i)
        const mark = ch.multi ? (chosen ? '☑' : '☐') : chosen ? '◉' : '○'
        const id = `${c.id}-o${i}`
        return (
          <Box key={`hco-${id}`} flexDirection="row" marginTop={1} backgroundColor={chosen ? PICKED_BG : ROW_BG}>
            <Box width={1} backgroundColor={chosen ? ACCENT : ROW_BG} />
            <Box flexDirection="column" flexGrow={1} flexShrink={1} paddingX={1}>
              <Button key={`hc-${c.id}-${i}`} plain onPress={() => void setChoice(c, x => pick(x, i))}>
                <Text color={chosen ? ACCENT : undefined}>{mark}</Text> <Text bold={chosen}>{o.label}</Text>
                {ch.suggested === i ? <Text color={LOOK.ok.fg}> ★</Text> : null}
              </Button>
              {whyLine(id, o.why || undefined)}
            </Box>
            {whyMark(id, o.why || undefined)}
          </Box>
        )
      }
      return (
        <Box key={`hc-${c.id}`} flexDirection="column" marginTop={1} paddingX={1} paddingY={0} backgroundColor={CHOICE_BG}>
          <Box flexDirection="row" justifyContent="space-between" gap={1}>
            <Text bold>{ch.question}</Text>
            <Text dimColor>{ch.multi ? 'pick any' : 'pick one'}</Text>
          </Box>
          {ch.options.map(option)}
          <Box marginTop={1} gap={2}>
            {ch.picked.length ? <Button key={`hcs-${c.id}`} label={ch.picked.length > 1 ? `Send ${ch.picked.length}` : 'Send'} onPress={() => void send(c, ch.picked)} /> : null}
            <Button key={`hcn-${c.id}`} plain dimColor label="None of these" onPress={() => void send(c, [])} />
          </Box>
        </Box>
      )
    }

    const chipRow = (c: Card) => {
      const got = chips(c)
      if (!got.length) return null
      return (
        <Text>
          {got.map((x, i) => (
            <Text key={`hk-${x.kind}`}>
              {i ? <Text dimColor> · </Text> : null}
              <Text color={x.kind === 'choice' ? ACCENT : LOOK[x.kind].fg}>{x.kind === 'choice' ? '❓' : LOOK[x.kind].emoji} {x.n}</Text>
            </Text>
          ))}
        </Text>
      )
    }

    const nothing = (text: string, sub?: string) => (
      <Box key="hu-ok" flexDirection="row" gap={1} paddingX={1} backgroundColor={LOOK.ok.bg}>
        {icon('ok')}
        <Box flexDirection="column" flexGrow={1} flexShrink={1}>
          <Text color={LOOK.ok.fg}>{text}</Text>
          {sub ? <Text dimColor>{sub}</Text> : null}
        </Box>
      </Box>
    )

    const card = (c: Card, isNewest: boolean) => {
      const openA = c.attention.map((x, i) => [x, i] as const).filter(([, i]) => !c.done.includes(`n${i}`))
      const openT = c.actions.map((x, i) => [x, i] as const).filter(([, i]) => !c.done.includes(`a${i}`))
      const done = [
        ...c.attention.map((x, i) => ['attention', `n${i}`, x] as const),
        ...c.actions.map((x, i) => ['action', `a${i}`, x] as const),
      ].filter(([, k]) => c.done.includes(k))
      const clear = !openA.length && !openT.length && !c.ideas.length && !(c.choice && !c.choice.sent)
      return (
        <Box key={`card-${c.id}`} flexDirection="column" marginTop={isNewest ? 0 : 1}>
          <Box flexDirection="row" justifyContent="space-between" gap={1}>
            <Box flexShrink={1}><Text bold>{c.gist || (isNewest ? 'The last reply' : c.ask)}</Text></Box>
            {chipRow(c)}
          </Box>
          {!isNewest && c.ask ? <Text dimColor wrap="truncate-end">for “{c.ask}”</Text> : null}
          {clear && isNewest ? <Box marginTop={1}>{nothing('Nothing needs you')}</Box> : null}
          {openA.map(([x, i]) => tile(c, 'attention', `n${i}`, x, true))}
          {choiceBlock(c)}
          {openT.map(([x, i]) => tile(c, 'action', `a${i}`, x, true))}
          {c.ideas.map((x, i) => tile(c, 'idea', `i${i}`, x, false))}
          {done.length ? (
            <Box marginTop={1}>
              <Button key={`hd-${c.id}`} plain dimColor label={`${doneOpen ? '▾' : '▸'} Done · ${done.length}`} onPress={() => void update($, showDone, v => !v)} />
            </Box>
          ) : null}
          {done.length && doneOpen ? done.map(([kind, k, x]) => tile(c, kind, k, x, true)) : null}
        </Box>
      )
    }

    return (
      <Box flexDirection="column">
        {working ? <Box marginBottom={1}><Text dimColor>Reading the last reply…</Text></Box> : null}
        {newest ? card(newest, true) : null}
        {!newest && quiet && !working ? nothing('Nothing needs you', quiet.replace(/^✓\s*/, '')) : null}
        {!list.length && !quiet && !working ? <Text dimColor>Nothing needs you yet. After each long reply, what needs you shows up here.</Text> : null}
        {earlier.length ? (
          <Box marginTop={1}>
            <Button key="hu-old" plain dimColor label={`${old ? '▾' : '▸'} ${earlier.length} earlier · ${openCount(earlier)} open`} onPress={() => void update($, showOld, v => !v)} />
          </Box>
        ) : null}
        {old ? earlier.map(c => card(c, false)) : null}
        {old && earlier.length ? (
          <Box marginTop={1}>
            <Button key="hu-clear" plain dimColor label="Clear all" onPress={() => void update($, cards, () => []).then(() => update($, last, () => '')).then(() => status($))} />
          </Box>
        ) : null}
      </Box>
    )
  })
}
