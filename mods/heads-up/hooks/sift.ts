import type { Card } from '../types'

// Replies shorter than this are already short enough to read; no model call for them.
export const MIN_REPLY = 300
export const MAX_CARDS = 12
// Only the few lines that matter: across all three lists, most important first.
export const MAX_LINES = 4
const MAX_ITEM = 220
const MAX_WHY = 500

export const SYSTEM = `You read a coding assistant's reply to its user and pull out ONLY what the user needs to see, so they can skip the wall of text.

Answer with one JSON object and nothing else:
{"gist": "...", "attention": [{"line": "...", "why": "..."}], "actions": [{"line": "...", "why": "..."}], "ideas": [{"line": "...", "why": "..."}]}

- gist: one plain line (at most 15 words) saying what was done or found. Past tense. No filler.
- attention: what the user must know or decide: something failed, broke, was skipped or could not be done; a risk; a question the assistant asked them; a choice waiting on them; a result that differs from what they asked. Not routine success.
- actions: things ONLY the user can do (approve, answer, test on their device, add a secret, click a link, merge if the assistant did not). Never things the assistant already did or will do itself. Start each with a verb.
- ideas: suggestions, options or follow-ups the assistant offered but did not do.

Each item has "line" (the short point, at most 15 words) and "why" (one or two plain sentences from the reply explaining it: the reason, the detail or what happens next; shown when the user hovers the line).

Rules: never invent anything that is not in the reply. Plain words, keep exact names, numbers, links and PR/issue references. Merge duplicates. At most ${MAX_LINES} items in all three lists together, the most important first; one or two is often right. Empty lists are fine and common: a reply that only reports finished work has attention, actions and ideas all empty.`

// Box mode (the default): cloud chats in the desktop and phone apps show only text, never a mod's panel, so the
// important lines go at the top of the reply itself. Added to the system prompt each turn.
export const BOX = `Heads-up box (the person asked that the important part of a long reply never gets buried). When your reply to the person will be longer than about 300 characters, begin it with this box, before anything else:

> **Heads-up**
> ⚠ <something they must know or decide: a failure, a risk, a question you are asking them>
> ↳ <one plain sentence: why, or the detail>
> 👉 <something only they can do>
> ↳ <one plain sentence>
> 💡 <an idea or option you offered but did not do>
> ↳ <one plain sentence>

Use 2 to 4 lines in all (each with its ↳), most important first, and only the kinds that apply. Each line at most 15 words, each ↳ at most 20; plain words; keep exact names, numbers and links; never invent. When nothing needs them, the box is one line: "> **Heads-up** ✓ <what was done, at most 15 words>". Then write the reply as usual, without repeating the box. Short replies get no box, and neither does a report a helper agent writes for another Claude.`

export function prompt(ask: string, reply: string): string {
  return `${ask ? `The user's message:\n${ask.slice(0, 2000)}\n\n` : ''}The assistant's reply:\n${reply.slice(0, 30_000)}`
}

const clean = (x: string, max: number) => {
  const t = x.replace(/\s+/g, ' ').trim()
  return t.length > max ? `${t.slice(0, max - 1)}…` : t
}

type Item = { line: string; why: string }

// Each entry a {line, why} or a bare string (an older answer, or a model that skipped the why).
const items = (v: unknown): Item[] =>
  Array.isArray(v)
    ? v
        .map(x => {
          if (typeof x === 'string') return { line: x, why: '' }
          if (x && typeof x === 'object' && typeof (x as Item).line === 'string') return { line: (x as Item).line, why: typeof (x as Item).why === 'string' ? (x as Item).why : '' }
          return undefined
        })
        .filter((x): x is Item => !!x)
        .map(x => ({ line: clean(x.line, MAX_ITEM), why: clean(x.why, MAX_WHY) }))
        .filter(x => x.line)
    : []

// The model's answer, read leniently: the first {...} in it, fenced or not.
export function parse(text: string): Pick<Card, 'gist' | 'attention' | 'actions' | 'ideas' | 'why'> | undefined {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) return undefined
  let o: unknown
  try {
    o = JSON.parse(text.slice(start, end + 1))
  } catch {
    return undefined
  }
  if (!o || typeof o !== 'object') return undefined
  const r = o as Record<string, unknown>
  const gist = typeof r.gist === 'string' ? r.gist.replace(/\s+/g, ' ').trim().slice(0, 200) : ''
  // Keep MAX_LINES in all, attention before actions before ideas.
  let room = MAX_LINES
  const why: Record<string, string> = {}
  const take = (v: unknown, k: string) => {
    const got = items(v).slice(0, Math.max(0, room))
    room -= got.length
    got.forEach((x, i) => {
      if (x.why && x.why !== x.line) why[`${k}${i}`] = x.why
    })
    return got.map(x => x.line)
  }
  const attention = take(r.attention, 'n')
  const actions = take(r.actions, 'a')
  const ideas = take(r.ideas, 'i')
  return { gist, attention, actions, ideas, why }
}

export const heading = (text: string) => {
  const t = text.replace(/\s+/g, ' ').trim()
  const cut = t.length > 60 ? `${t.slice(0, 59).replace(/\s+\S*$/, '')}…` : t
  return cut ? cut.charAt(0).toUpperCase() + cut.slice(1) : ''
}

export const hasAny = (c: Pick<Card, 'attention' | 'actions' | 'ideas'>) => c.attention.length + c.actions.length + c.ideas.length > 0

// What still needs the person: un-ticked attention and action items, across the kept cards.
export const openCount = (cards: readonly Card[]) =>
  cards.reduce(
    (n, c) => n + c.attention.filter((_, i) => !c.done.includes(`n${i}`)).length + c.actions.filter((_, i) => !c.done.includes(`a${i}`)).length,
    0,
  )

export const toggle = (c: Card, key: string): Card => ({ ...c, done: c.done.includes(key) ? c.done.filter(k => k !== key) : [...c.done, key] })

// Newest first, as plain text: the /heads-up reply, for a screen that seats no panes.
export function asText(c: Card): string {
  const box = (key: string) => (c.done.includes(key) ? '☑' : '☐')
  const why = (key: string) => (c.why?.[key] ? [`   ↳ ${c.why[key]}`] : [])
  return [
    c.ask ? `“${c.ask}”` : '',
    c.gist ? `✓ ${c.gist}` : '',
    ...(c.attention.length ? ['', '⚠ Needs your attention', ...c.attention.flatMap((x, i) => [`${box(`n${i}`)} ${x}`, ...why(`n${i}`)])] : []),
    ...(c.actions.length ? ['', '👉 For you to do', ...c.actions.flatMap((x, i) => [`${box(`a${i}`)} ${x}`, ...why(`a${i}`)])] : []),
    ...(c.ideas.length ? ['', '💡 Ideas', ...c.ideas.flatMap((x, i) => [`• ${x}`, ...why(`i${i}`)])] : []),
    ...(hasAny(c) ? [] : ['', 'Nothing needs you.']),
  ]
    .filter((x, i, a) => x !== '' || (i > 0 && a[i - 1] !== ''))
    .join('\n')
    .trim()
}
