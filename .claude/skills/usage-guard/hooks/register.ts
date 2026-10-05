import type { Register, SessionContextUsage, SessionRateLimit } from 'claude-code'

// Chat size (tokens the last reply was answered over) where we start nudging.
const SOFT = 200_000
const HARD = 300_000
// Past SOFT, remind again every STEP tokens; past HARD, every message.
const STEP = 50_000
// Usage-limit levels (percent) worth a heads-up, once each per window.
const LEVELS = [50, 75, 90]
const NAMES: Record<string, string> = { five_hour: '5h', seven_day: 'week', spend_limit: 'spend' }

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
  return parts.length ? parts.join(' · ') : 'no readings yet'
}

// Highest level already announced per window (keyed by its reset time, so a new window starts over).
function crossed(seen: Map<string, number>, r: SessionRateLimit): number {
  const level = LEVELS.filter(l => r.percentUsed >= l).pop() ?? 0
  const key = `${r.kind}@${r.resetsAt ?? ''}`
  if (level <= (seen.get(key) ?? 0)) return 0
  seen.set(key, level)
  return level
}

export const register: Register = on => {
  const told = new Map<string, number>()
  const toasted = new Map<string, number>()
  let lastSoft = -Infinity

  on('session.start', async ($, e, next) => {
    await $.command.register({ name: 'handoff', description: 'Write a handoff note and start a fresh chat with it.' })
    await $.command.register({ name: 'meter', description: 'Show usage limits and chat size.' })
    return next(e)
  })

  on('command.run', { command: 'handoff' }, async $ => {
    void $.prompt.submit({ text: 'handoff', asUser: true })
    return { text: 'Handing off to a fresh chat…' }
  })

  on('command.run', { command: 'meter' }, async $ => {
    const u = await $.session.usage()
    return { text: meter(u.rateLimits, u.context, await $.clock.now()) }
  })

  // Terminal / desktop: a status line plus a toast when a limit passes 50/75/90%.
  on('session.measure', async ($, e, next) => {
    const now = await $.clock.now()
    $.ui.status(`⛽ ${meter(e.rateLimits, e.context, now)}`)
    for (const r of e.rateLimits) {
      const level = crossed(toasted, r)
      if (level) $.ui.toast(`${NAMES[r.kind] ?? r.kind} limit past ${level}% (resets in ${resetIn(r.resetsAt, now) || 'soon'})`)
    }
    return next(e)
  })

  // Every surface (the phone app too): hidden notes for Claude, who passes warnings on in the reply.
  on('prompt.submit', async ($, e, next) => {
    const u = await $.session.usage()
    const now = await $.clock.now()
    const notes = [
      `[usage-guard] Usage now: ${meter(u.rateLimits, u.context, now)}. Mention it only when the person asks about usage or a note below says to.`,
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

    return next({ ...e, context: [...(e.context ?? []), notes.join('\n')] })
  })
}
