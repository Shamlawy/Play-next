import type { Card } from '../types'

// Replies shorter than this are already short enough to read; no model call for them.
export const MIN_REPLY = 300
export const MAX_CARDS = 12
const MAX_ITEMS = 6
const MAX_ITEM = 220

export const SYSTEM = `You read a coding assistant's reply to its user and pull out ONLY what the user needs to see, so they can skip the wall of text.

Answer with one JSON object and nothing else:
{"gist": "...", "attention": ["..."], "actions": ["..."], "ideas": ["..."]}

- gist: one plain line (at most 15 words) saying what was done or found. Past tense. No filler.
- attention: what the user must know or decide: something failed, broke, was skipped or could not be done; a risk; a question the assistant asked them; a choice waiting on them; a result that differs from what they asked. Not routine success.
- actions: things ONLY the user can do (approve, answer, test on their device, add a secret, click a link, merge if the assistant did not). Never things the assistant already did or will do itself. Start each with a verb.
- ideas: suggestions, options or follow-ups the assistant offered but did not do.

Rules: never invent anything that is not in the reply. Each item at most 20 words, plain words, keep exact names, numbers, links and PR/issue references. Merge duplicates. At most ${MAX_ITEMS} items per list. Empty lists are fine and common: a reply that only reports finished work has attention, actions and ideas all empty.`

export function prompt(ask: string, reply: string): string {
  return `${ask ? `The user's message:\n${ask.slice(0, 2000)}\n\n` : ''}The assistant's reply:\n${reply.slice(0, 30_000)}`
}

const list = (v: unknown): string[] =>
  Array.isArray(v)
    ? v
        .filter((x): x is string => typeof x === 'string')
        .map(x => x.replace(/\s+/g, ' ').trim())
        .filter(Boolean)
        .map(x => (x.length > MAX_ITEM ? `${x.slice(0, MAX_ITEM - 1)}…` : x))
        .slice(0, MAX_ITEMS)
    : []

// The model's answer, read leniently: the first {...} in it, fenced or not.
export function parse(text: string): Pick<Card, 'gist' | 'attention' | 'actions' | 'ideas'> | undefined {
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
  return { gist, attention: list(r.attention), actions: list(r.actions), ideas: list(r.ideas) }
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
  return [
    c.ask ? `“${c.ask}”` : '',
    c.gist ? `✓ ${c.gist}` : '',
    ...(c.attention.length ? ['', '⚠ Needs your attention', ...c.attention.map((x, i) => `${box(`n${i}`)} ${x}`)] : []),
    ...(c.actions.length ? ['', '👉 For you to do', ...c.actions.map((x, i) => `${box(`a${i}`)} ${x}`)] : []),
    ...(c.ideas.length ? ['', '💡 Ideas', ...c.ideas.map(x => `• ${x}`)] : []),
    ...(hasAny(c) ? [] : ['', 'Nothing needs you.']),
  ]
    .filter((x, i, a) => x !== '' || (i > 0 && a[i - 1] !== ''))
    .join('\n')
    .trim()
}
