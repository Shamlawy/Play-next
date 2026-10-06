import type { Register, SessionContextUsage, SessionRateLimit } from 'claude-code'

// Chat size (tokens the last reply was answered over) where we start nudging.
const SOFT = 200_000
const HARD = 300_000
// Past SOFT, remind again every STEP tokens; past HARD, every message.
const STEP = 50_000
// Usage-limit levels (percent) worth a heads-up, once each per window.
const LEVELS = [50, 75, 90]
const NAMES: Record<string, string> = { five_hour: '5h', seven_day: 'week', spend_limit: 'spend' }

// The usage window: a small pane with a bar per limit and the chat size.
const WINDOW = 'usage'
const WINDOW_TITLE = '⛽ Usage'

function bar(percent: number, width: number): string {
  const w = Math.max(4, width)
  const full = Math.round((Math.min(100, Math.max(0, percent)) / 100) * w)
  return '█'.repeat(full) + '░'.repeat(w - full)
}

const tone = (percent: number) => (percent >= 90 ? 'error' : percent >= 75 ? 'warning' : 'success')

type Row = { name: string; percent: number; note: string }

function windowRows(limits: readonly SessionRateLimit[], ctx: SessionContextUsage, now: number): Row[] {
  const rows: Row[] = limits.map(r => {
    const left = resetIn(r.resetsAt, now)
    return { name: NAMES[r.kind] ?? r.kind, percent: r.percentUsed, note: `${r.percentUsed}%${left ? ` · resets ${left}` : ''}` }
  })
  if (ctx.tokens) {
    // The chat bar fills toward the 300k "start a fresh chat" line, not the model's whole window.
    rows.push({ name: 'chat', percent: Math.round((ctx.tokens / HARD) * 100), note: `${k(ctx.tokens)} of ${k(HARD)}` })
  }
  return rows
}

const METER_SET = /^\s*\/?(hide|show|turn\s+off|turn\s+on)\s+(the\s+)?(usage\s+)?meter\s*[.!]?\s*$/i

const HANDOFF_ASK = /^\s*\/?(hand\s*off|new\s+chat|fresh\s+chat|move\s+to\s+a\s+new\s+chat)\s*[.!]?\s*$/i

const HANDOFF = [
  '[usage-guard] The person wants to move to a fresh chat to save usage. Do this:',
  '1. Make sure every change is committed and pushed (say so plainly if something can\'t be).',
  '2. Write a handoff note (under 300 words) for the new chat: the goal, what is done (commits, APP_V), what is left next, open questions, the branch, and gotchas learned in this chat. Don\'t repeat what CLAUDE.md already says.',
  '3. Start the new cloud session with the claude-code-remote create_session tool: source_url = this repo\'s GitHub URL, title starting "Play next · ", prompt = the handoff note.',
  '4. Reply with the new chat\'s title and a 2-line summary, and say this chat can be left now.',
  'If create_session is not available, give the note as one block they can paste into a new chat.',
].join('\n')

const k = (n: number) => (n >= 1000 ? `${Math.round(n / 1000)}k` : String(n))

function resetIn(iso: string | undefined, now: number): string {
  const at = iso ? Date.parse(iso) : NaN
  if (!Number.isFinite(at) || at <= now) return ''
  const mins = Math.round((at - now) / 60_000)
  const h = Math.floor(mins / 60)
  if (h >= 24) return `${Math.floor(h / 24)}d ${h % 24}h`
  return h ? `${h}h ${mins % 60}m` : `${mins}m`
}

function meter(limits: readonly SessionRateLimit[], ctx: SessionContextUsage, now: number): string {
  const parts = limits.map(r => {
    const left = resetIn(r.resetsAt, now)
    return `${NAMES[r.kind] ?? r.kind} ${r.percentUsed}%${left ? ` (resets in ${left})` : ''}`
  })
  if (ctx.tokens) parts.push(`chat ${k(ctx.tokens)}`)
  if (!limits.length) parts.push('5h/week limits not reported yet')
  return parts.join(' · ')
}

// Highest level already announced per window (keyed by its reset time, so a new window starts over).
function crossed(seen: Map<string, number>, r: SessionRateLimit): number {
  const level = LEVELS.filter(l => r.percentUsed >= l).pop() ?? 0
  const key = `${r.kind}@${r.resetsAt ?? ''}`
  if (level <= (seen.get(key) ?? 0)) return 0
  seen.set(key, level)
  return level
}

async function drawnOn($: { session: { surfaces: () => Promise<readonly string[]> } }): Promise<string> {
  try {
    return (await $.session.surfaces()).join(', ') || 'none'
  } catch {
    return 'unknown'
  }
}

export const register: Register = on => {
  const told = new Map<string, number>()
  const toasted = new Map<string, number>()
  let lastSoft = -Infinity
  // The meter line at the end of every reply (the one place every app shows); "hide meter" turns it off.
  let footer = true

  on('session.start', async ($, e, next) => {
    footer = (await $.store.get('footer')) !== false
    await $.command.register({ name: 'handoff', description: 'Write a handoff note and start a fresh chat with it.' })
    await $.command.register({ name: 'meter', description: 'Open the usage window (limits + chat size). /meter off|on: the line at the end of replies.' })
    // Opens by itself where there is room for it (a sidebar), unless the person closed it last time.
    if ((await $.store.get('window')) !== false) void $.ui.open({ id: WINDOW, title: WINDOW_TITLE })
    return next(e)
  })

  on('command.run', { command: 'handoff' }, async $ => {
    void $.prompt.submit({ text: 'handoff', asUser: true })
    return { text: 'Handing off to a fresh chat…' }
  })

  on('command.run', { command: 'meter' }, async ($, e) => {
    const arg = e.args.trim().toLowerCase()
    if (arg === 'off' || arg === 'on') {
      footer = arg === 'on'
      await $.store.set('footer', footer)
      return { text: `Usage line at the end of replies: ${arg}.` }
    }
    await $.store.set('window', true)
    const opened = await $.ui.open({ id: WINDOW, title: WINDOW_TITLE })
    const u = await $.session.usage()
    const line = meter(u.rateLimits, u.context, await $.clock.now())
    return { text: opened.isPlaced ? `Usage window open. ${line}` : line }
  })

  // Closed by the person: stay closed in later chats until /meter opens it again.
  on('ui.close', { id: WINDOW }, async ($, e, next) => {
    if (e.origin.kind === 'person') await $.store.set('window', false)
    return next(e)
  })

  on('ui.render', { component: 'Pane', requestId: WINDOW }, async ($, e) => {
    const { Box, Text } = $.ui.resolve(e)
    const u = await $.session.usage()
    const rows = windowRows(u.rateLimits, u.context, await $.clock.now())
    const width = Math.min(24, Math.max(4, (e.props.bodyColumns ?? 30) - 6))
    return (
      <Box flexDirection="column">
        {rows.map(r => (
          <Box flexDirection="column" key={r.name}>
            <Text bold>{r.name}</Text>
            <Text>
              <Text color={tone(r.percent)}>{bar(r.percent, width)}</Text>
            </Text>
            <Text dimColor key={`${r.name}-note`}>{r.note}</Text>
          </Box>
        ))}
        {!u.rateLimits.length && <Text dimColor key="no-limits">5h/week limits not reported yet</Text>}
      </Box>
    )
  })

  // Terminal / desktop: a status line plus a toast when a limit passes 50/75/90%.
  on('session.measure', async ($, e, next) => {
    const now = await $.clock.now()
    $.ui.status(`⛽ ${meter(e.rateLimits, e.context, now)}`)
    $.ui.invalidate('ui.render')
    for (const r of e.rateLimits) {
      const level = crossed(toasted, r)
      if (level) $.ui.toast(`${NAMES[r.kind] ?? r.kind} limit past ${level}% (resets in ${resetIn(r.resetsAt, now) || 'soon'})`)
    }
    return next(e)
  })

  // Terminal / desktop (and any app that draws it): the meter as a band above the message box.
  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    const u = await $.session.usage()
    if (!u.rateLimits.length && !u.context.tokens) return next(e)
    const { Box, Text } = $.ui.resolve(e)
    return (
      <Box>
        <Text dimColor>⛽ {meter(u.rateLimits, u.context, await $.clock.now())}</Text>
      </Box>
    )
  })

  // Every surface (the phone app too): hidden notes for Claude, who passes warnings on in the reply.
  on('prompt.submit', async ($, e, next) => {
    const u = await $.session.usage()
    const now = await $.clock.now()
    const notes = [
      `[usage-guard] Usage now: ${meter(u.rateLimits, u.context, now)}. Mention it only when the person asks about usage or a note below says to. Apps drawing this chat: ${await drawnOn($)}.`,
    ]

    for (const r of u.rateLimits) {
      const level = crossed(told, r)
      if (level) {
        notes.push(`[usage-guard] The ${NAMES[r.kind] ?? r.kind} usage limit just passed ${level}% (now ${r.percentUsed}%, resets in ${resetIn(r.resetsAt, now) || 'soon'}). End your reply with one short line telling the person.`)
      }
    }

    const t = u.context.tokens ?? 0
    if (t >= HARD) {
      notes.push(`[usage-guard] This chat is ${k(t)} tokens, past the 300k line: every message now re-reads all of it. End your reply with one short line recommending a fresh chat: they can type "handoff" and you'll write a summary and start the new chat for them.`)
    } else if (t >= SOFT && t - lastSoft >= STEP) {
      lastSoft = t
      notes.push(`[usage-guard] This chat is ${k(t)} tokens (past 200k). End your reply with one short, gentle line: a fresh chat would save usage when a good stopping point comes; typing "handoff" moves everything over.`)
    }

    if (HANDOFF_ASK.test(e.text)) notes.push(HANDOFF)

    const set = METER_SET.exec(e.text)
    if (set) {
      footer = !/hide|off/i.test(set[1] ?? '')
      await $.store.set('footer', footer)
      notes.push(`[usage-guard] The usage line at the end of replies is now ${footer ? 'on' : 'off'}. Confirm it in one short line.`)
    }
    if (footer) {
      notes.push(`[usage-guard] End your reply with this as its very last line, exactly as written, after a blank line: ⛽ ${meter(u.rateLimits, u.context, now)}`)
    }

    return next({ ...e, context: [...(e.context ?? []), notes.join('\n')] })
  })
}
