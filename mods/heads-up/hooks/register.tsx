import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Card } from '../types'
import { ACCENT, BOX, CHOICE_BG, LOOK, MIN_REPLY, PICKED_BG, ROW_BG, SYSTEM, asText, chips, hasAny, iconSvg, lines, openCount, parse, pick, pickText, prompt, toggle } from './sift.ts'
import type { Kind } from './sift.ts'

type $ = EngineInterface

// After each of Claude's replies, a small model boils it down to what needs the person
// (things to look at, things to do, ideas) and shows only that in a side panel.
// 0.8 (the owner: "whoaah too much, it should auto clear after each message and it should be shorter, also the
// hover feature remove it"): one card, the newest reply's, wiped by the person's next message; at most 3 short
// lines; no explanations, no hover.
const PANE = 'heads-up'
const TITLE = 'Heads-up'
const MODEL = 'haiku'
const PERSON = new Set(['composer', 'bridge', 'sdk', 'unclassified'])

// The newest reply's card (none or one).
const cards = atom({ plugin: 'heads-up', key: 'cards' } as const, [])
const ask = atom({ plugin: 'heads-up', key: 'ask' } as const, '')
const busy = atom({ plugin: 'heads-up', key: 'busy' } as const, false)
// Whether the ticked tiles under "Done · N" are shown.
const showDone = atom({ plugin: 'heads-up', key: 'showDone' } as const, false)
// What the panel says when the newest reply had nothing for the person.
const last = atom({ plugin: 'heads-up', key: 'last' } as const, '')

const openPane = ($: $) => $.ui.open({ id: PANE, title: TITLE }).catch(() => ({ isPlaced: false as const }))
const isOff = async ($: $) => (await $.store.get('off')) === true
// 'panel': the side panel, where mods draw (a chat on a computer). 'box': the lines go in a box at the top of each
// long reply, the only thing a cloud chat shows. Unchosen (the owner: "I want the panel always, not me typing
// something"), it follows where the chat runs: the box in a cloud chat, the panel everywhere else.
// `/heads-up panel` and `/heads-up box` pin a choice on this machine.
const isPanel = async ($: $) => {
  const mode = await $.store.get('mode')
  if (mode === 'panel' || mode === 'box') return mode === 'panel'
  return (await $.env.get('CLAUDE_CODE_REMOTE')) !== 'true'
}

async function status($: $) {
  if ((await isOff($)) || !(await isPanel($))) return $.ui.status(undefined)
  const n = openCount((await read($, cards))[0])
  $.ui.status(n ? `👀 ${n} need${n === 1 ? 's' : ''} you · /heads-up` : undefined)
}

async function sift($: $, reply: string, question: string) {
  await update($, busy, () => true)
  try {
    const r = await $.model.complete({ model: MODEL, effort: 'low', maxTokens: 600, timeoutMs: 60_000, system: SYSTEM, prompt: prompt(question, reply) })
    const got = r.isAnswered ? parse(r.text) : undefined
    if (!got) return
    const at = await $.clock.now()
    const card: Card = { id: `c${at}`, at, ...got, done: [] }
    if (!hasAny(card)) {
      await update($, cards, () => [])
      await update($, last, () => card.gist || 'Nothing needs you in the last reply.')
      await status($)
      return
    }
    await update($, last, () => '')
    await update($, cards, () => [card])
    await status($)
    const opened = await openPane($)
    if (!opened.isPlaced) {
      const n = card.attention.length + card.actions.length + (card.choice ? 1 : 0)
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
      description: 'The few lines that matter in Claude\'s long replies: the side panel on a computer, a Heads-up box at the top of each in a cloud chat; "/heads-up panel" or "/heads-up box" picks one here. "/heads-up off" stops it, "/heads-up on" starts it again.',
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
      return { text: 'Heads-up is in panel mode: after each long reply, the side panel shows the lines that need you. Mod panels only draw in a Local chat on your PC; "/heads-up box" goes back to the box at the top of replies.' }
    }
    if (arg === 'box') {
      await $.store.set('mode', 'box')
      await status($)
      return { text: 'Heads-up is in box mode: each long reply starts with a short Heads-up box of the lines that matter.' }
    }
    if (arg === 'clear') {
      await update($, cards, () => [])
      await update($, last, () => '')
      await status($)
      return { text: 'Heads-up cleared.' }
    }
    const off = (await isOff($)) ? '\n(Heads-up is off: "/heads-up on" turns it back on.)' : ''
    if (!(await isPanel($))) return { text: `Heads-up is in box mode: each long reply starts with a short Heads-up box of the lines that matter. "/heads-up panel" switches to the side panel (it draws in a chat on a computer).${off}` }
    await openPane($)
    const card = (await read($, cards))[0]
    // The card as text too, for a screen that seats no panes (the phone app).
    return { text: card ? `${asText(card)}${off}` : `Nothing needs you right now.${off}` }
  })

  on('prompt.submit', async ($, e, next) => {
    // Only the person's own messages, not a helper's report or a task notice. Each one wipes the last card
    // (auto clear) and opens the panel; a slash command does neither and clears the remembered message, so the
    // reply after one isn't sifted against an older, unrelated message.
    if (PERSON.has(e.origin?.kind ?? 'unclassified')) {
      const slash = e.text.trim().startsWith('/')
      await update($, ask, () => (slash ? '' : e.text))
      if (!slash) {
        await update($, cards, () => [])
        await update($, last, () => '')
        await update($, showDone, () => false)
        await status($).catch(() => undefined)
        if (!(await isOff($)) && (await isPanel($))) await openPane($)
      }
    }
    return next(e)
  }).catch(($, e, next) => next(e))

  // Box mode: the instruction rides at the end of the system prompt.
  on('prompt.compose', async ($, e, next) => {
    const r = await next(e)
    if ((await isOff($)) || (await isPanel($))) return r
    return { ...r, sections: [...r.sections.filter(x => x.id !== 'heads-up:box'), { id: 'heads-up:box', text: BOX, scope: 'session' as const }] }
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

  // The panel: the gist (with count chips once there are 3 lines), then one tinted tile per line (pixel icon,
  // the short line, a tick box), the choice in its own block; ticked tiles fold into "Done · N".
  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const els = $.ui.resolve(e)
    const { Box, Text, Button } = els
    const c = (await read($, cards))[0]
    const working = await read($, busy)
    const quiet = await read($, last)
    const doneOpen = await read($, showDone)

    const icon = (kind: Kind) =>
      'Svg' in els && els.Svg ? (
        <els.Svg source={iconSvg(kind)} alt={LOOK[kind].alt} width={16} height={16} />
      ) : (
        <Text color={LOOK[kind].fg}>{LOOK[kind].emoji}</Text>
      )

    const nothing = (sub?: string) => (
      <Box key="hu-ok" flexDirection="row" gap={1} paddingX={1} backgroundColor={LOOK.ok.bg}>
        {icon('ok')}
        <Box flexDirection="column" flexGrow={1} flexShrink={1}>
          <Text color={LOOK.ok.fg}>Nothing needs you</Text>
          {sub ? <Text dimColor>{sub}</Text> : null}
        </Box>
      </Box>
    )

    if (!c) {
      return (
        <Box flexDirection="column">
          {working ? <Text dimColor>Reading the last reply…</Text> : quiet ? nothing(quiet) : <Text dimColor>After each long reply, what needs you shows up here.</Text>}
        </Box>
      )
    }

    // Only the box: a desktop draws a Button's label in full (a long label squeezed the line to a sliver, 0.7.1).
    const tick = (key: string) => {
      const ticked = c.done.includes(key)
      return (
        <Button key={`hu-${c.id}-${key}`} plain onPress={() => void update($, cards, l => l.map(x => (x.id === c.id ? toggle(x, key) : x))).then(() => status($))}>
          <Text color={ticked ? undefined : LOOK[key.startsWith('n') ? 'attention' : 'action'].fg} dimColor={ticked}>{ticked ? '☑' : '☐'}</Text>
        </Button>
      )
    }

    const tile = (kind: Kind, key: string, line: string, withTick: boolean) => {
      const ticked = c.done.includes(key)
      return (
        <Box key={`ht-${c.id}-${key}`} flexDirection="row" gap={1} marginTop={1} paddingX={1} backgroundColor={ticked ? CHOICE_BG : LOOK[kind].bg}>
          {icon(kind)}
          <Box flexGrow={1} flexShrink={1}>
            <Text dimColor={ticked} strikethrough={ticked}>{line}</Text>
          </Box>
          {withTick ? tick(key) : null}
        </Box>
      )
    }

    const setChoice = (fn: (x: NonNullable<Card['choice']>) => NonNullable<Card['choice']>) =>
      update($, cards, l => l.map(x => (x.id === c.id && x.choice ? { ...x, choice: fn(x.choice) } : x)))

    // The pick goes back to Claude as the person's own next message (which then clears the card).
    const send = async (picked: number[]) => {
      if (!c.choice || c.choice.sent) return
      const text = pickText(c.choice, picked)
      await setChoice(x => ({ ...x, picked, sent: text }))
      await status($)
      await $.prompt.submit({ text, asUser: true })
    }

    // Options laid out in the reply, each a full-width row; a picked one gets a blue edge. Send shows once
    // something is picked; "None of these" is always there.
    const choiceBlock = () => {
      const ch = c.choice
      if (!ch) return null
      if (ch.sent) {
        return (
          <Box key={`hc-${c.id}`} marginTop={1} paddingX={1} backgroundColor={CHOICE_BG}>
            <Text wrap="truncate-end"><Text color="success">✓ Sent: </Text><Text dimColor>{ch.sent}</Text></Text>
          </Box>
        )
      }
      const option = (o: { label: string }, i: number) => {
        const chosen = ch.picked.includes(i)
        const mark = ch.multi ? (chosen ? '☑' : '☐') : chosen ? '◉' : '○'
        return (
          <Box key={`hco-${c.id}-${i}`} flexDirection="row" marginTop={1} backgroundColor={chosen ? PICKED_BG : ROW_BG}>
            <Box width={1} backgroundColor={chosen ? ACCENT : ROW_BG} />
            <Box flexGrow={1} flexShrink={1} paddingX={1}>
              <Button key={`hc-${c.id}-${i}`} plain onPress={() => void setChoice(x => pick(x, i))}>
                <Text color={chosen ? ACCENT : undefined}>{mark}</Text> <Text bold={chosen}>{o.label}</Text>
                {ch.suggested === i ? <Text color={LOOK.ok.fg}> ★</Text> : null}
              </Button>
            </Box>
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
            {ch.picked.length ? <Button key={`hcs-${c.id}`} label={ch.picked.length > 1 ? `Send ${ch.picked.length}` : 'Send'} onPress={() => void send(ch.picked)} /> : null}
            <Button key={`hcn-${c.id}`} plain dimColor label="None of these" onPress={() => void send([])} />
          </Box>
        </Box>
      )
    }

    // Count chips only when the card is long enough to need them (3 lines).
    const chipRow = () => {
      const got = lines(c) > 2 ? chips(c) : []
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

    const openA = c.attention.map((x, i) => [x, i] as const).filter(([, i]) => !c.done.includes(`n${i}`))
    const openT = c.actions.map((x, i) => [x, i] as const).filter(([, i]) => !c.done.includes(`a${i}`))
    const done = [
      ...c.attention.map((x, i) => ['attention', `n${i}`, x] as const),
      ...c.actions.map((x, i) => ['action', `a${i}`, x] as const),
    ].filter(([, k]) => c.done.includes(k))
    const clear = !openA.length && !openT.length && !c.ideas.length && !(c.choice && !c.choice.sent)
    return (
      <Box flexDirection="column">
        <Box flexDirection="row" justifyContent="space-between" gap={1}>
          <Box flexShrink={1}><Text bold>{c.gist || 'The last reply'}</Text></Box>
          {chipRow()}
        </Box>
        {clear ? <Box marginTop={1}>{nothing()}</Box> : null}
        {openA.map(([x, i]) => tile('attention', `n${i}`, x, true))}
        {choiceBlock()}
        {openT.map(([x, i]) => tile('action', `a${i}`, x, true))}
        {c.ideas.map((x, i) => tile('idea', `i${i}`, x, false))}
        {done.length ? (
          <Box marginTop={1}>
            <Button key={`hd-${c.id}`} plain dimColor label={`${doneOpen ? '▾' : '▸'} Done · ${done.length}`} onPress={() => void update($, showDone, v => !v)} />
          </Box>
        ) : null}
        {done.length && doneOpen ? done.map(([kind, k, x]) => tile(kind, k, x, true)) : null}
      </Box>
    )
  })
}
