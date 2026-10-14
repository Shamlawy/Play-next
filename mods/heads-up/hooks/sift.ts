import type { Card } from '../types'

// Replies shorter than this are already short enough to read; no model call for them.
export const MIN_REPLY = 300
// Only the few lines that matter (0.8, the owner: "too much ... it should be shorter"): three in all across
// attention, to-dos and ideas, a choice counting as one, most important first.
export const MAX_LINES = 3
const MAX_ITEM = 80
const MAX_GIST = 80
const MAX_LABEL = 60

export const SYSTEM = `You read a coding assistant's reply to its user and pull out ONLY what the user needs to see, so they can skip the wall of text. Be very short.

Answer with one JSON object and nothing else:
{"gist": "...", "attention": ["..."], "actions": ["..."], "ideas": ["..."], "choice": null}

- gist: at most 8 words saying what was done or found. Past tense. No filler.
- attention: what the user must know or decide: something failed, broke, was skipped or could not be done; a risk; a question the assistant asked them. Not routine success.
- actions: things ONLY the user can do (approve, answer, test on their device, add a secret, click a link). Never things the assistant already did or will do itself. Start each with a verb.
- ideas: suggestions the assistant offered but did not do, when they are not part of the choice below.
- choice: when the reply asks the user to pick, or lays out options or next steps the assistant could do on the user's say-so ("want me to A or B?", "options: ..."), turn them into something the user can tap: {"question": "...", "multi": true|false, "options": ["...", "..."], "suggested": <index or null>}. question: at most 6 words, ending with "?". options: 2 to 4 labels, each at most 6 words, the way the user would say it ("Fix the Safari scroll first"). multi: true when several can be done together, false when they exclude each other. suggested: the option the assistant recommended, else null. Leave choice null when nothing is offered; never invent options. Options in the choice are not repeated in ideas or actions.

Each line is a plain string of at most 8 words.

Rules: never invent anything that is not in the reply. Plain words, keep exact names, numbers and PR/issue references. Merge duplicates. At most ${MAX_LINES} lines in attention, actions and ideas together, and a choice counts as one of them; the most important first; one is often right. Empty lists are fine and common: a reply that only reports finished work has attention, actions and ideas all empty.`

// Box mode (cloud chats): the desktop and phone apps show a cloud chat's text only, never a mod's panel, so the
// important lines go at the top of the reply itself. Added to the system prompt each turn.
export const BOX = `Heads-up box (the person asked that the important part of a long reply never gets buried, and that it stays short). When your reply to the person will be longer than about 300 characters, begin it with this box, before anything else:

> **Heads-up**
> ⚠ <something they must know or decide: a failure, a risk, a question you are asking them>
> 👉 <something only they can do>
> 💡 <an idea you offered but did not do>

Use 1 to ${MAX_LINES} lines in all, most important first, and only the kinds that apply. Each line at most 8 words; plain words; keep exact names and numbers; never invent. When nothing needs them, the box is one line: "> **Heads-up** ✓ <what was done, at most 8 words>". When the reply offers options or asks them to choose, that counts as one line: "> ❓ <the question>" followed by the options numbered on their own lines ("> 1. ...", at most 6 words each, the one you recommend marked "(suggested)"). Then write the reply as usual, without repeating the box. Short replies get no box, and neither does a report a helper agent writes for another Claude.`

export function prompt(ask: string, reply: string): string {
  return `${ask ? `The user's message:\n${ask.slice(0, 2000)}\n\n` : ''}The assistant's reply:\n${reply.slice(0, 30_000)}`
}

const clean = (x: string, max: number) => {
  const t = x.replace(/\s+/g, ' ').trim()
  return t.length > max ? `${t.slice(0, max - 1)}…` : t
}

type Choice = NonNullable<Card['choice']>

// A line from the model: a plain string, or an older {line, ...} object.
const text = (x: unknown): string => {
  if (typeof x === 'string') return x
  if (x && typeof x === 'object') {
    const o = x as Record<string, unknown>
    const v = o.line ?? o.label
    return typeof v === 'string' ? v : ''
  }
  return ''
}

// The choice, read leniently: 2-4 labels, a question, one or many, and the suggested one if it exists.
const choiceOf = (v: unknown): Choice | undefined => {
  if (!v || typeof v !== 'object') return undefined
  const c = v as Record<string, unknown>
  const options = (Array.isArray(c.options) ? c.options : [])
    .map(x => clean(text(x), MAX_LABEL))
    .filter(Boolean)
    .slice(0, 4)
    .map(label => ({ label }))
  if (options.length < 2) return undefined
  const n = c.suggested
  const suggested = typeof n === 'number' && Number.isInteger(n) && n >= 0 && n < options.length ? n : undefined
  const question = clean(typeof c.question === 'string' && c.question ? c.question : 'Which one?', 80)
  return { question, multi: c.multi === true, options, ...(suggested === undefined ? {} : { suggested }), picked: [] }
}

const items = (v: unknown): string[] => (Array.isArray(v) ? v.map(x => clean(text(x), MAX_ITEM)).filter(Boolean) : [])

// The model's answer, read leniently: the first {...} in it, fenced or not.
export function parse(raw: string): Pick<Card, 'gist' | 'attention' | 'actions' | 'ideas' | 'choice'> | undefined {
  const start = raw.indexOf('{')
  const end = raw.lastIndexOf('}')
  if (start < 0 || end <= start) return undefined
  let o: unknown
  try {
    o = JSON.parse(raw.slice(start, end + 1))
  } catch {
    return undefined
  }
  if (!o || typeof o !== 'object') return undefined
  const r = o as Record<string, unknown>
  const gist = typeof r.gist === 'string' ? clean(r.gist, MAX_GIST) : ''
  const choice = choiceOf(r.choice)
  // MAX_LINES in all, the choice counting as one; attention before actions before ideas.
  let room = MAX_LINES - (choice ? 1 : 0)
  const take = (v: unknown) => {
    const got = items(v).slice(0, Math.max(0, room))
    room -= got.length
    return got
  }
  const attention = take(r.attention)
  const actions = take(r.actions)
  const ideas = take(r.ideas)
  return { gist, attention, actions, ideas, ...(choice ? { choice } : {}) }
}

export const hasAny = (c: Pick<Card, 'attention' | 'actions' | 'ideas' | 'choice'>) => c.attention.length + c.actions.length + c.ideas.length > 0 || !!c.choice

// How many lines the card shows besides the gist (the choice is one).
export const lines = (c: Pick<Card, 'attention' | 'actions' | 'ideas' | 'choice'>) => c.attention.length + c.actions.length + c.ideas.length + (c.choice ? 1 : 0)

// A choice still waiting on the person counts as one thing that needs them.
const waiting = (c: Card) => (c.choice && !c.choice.sent ? 1 : 0)

// What the person's pick says, sent as their next message.
export function pickText(c: Choice, picked: readonly number[]): string {
  if (!picked.length) return `None of those for now (${c.question.replace(/\?$/, '')}).`
  const names = picked.map(i => `"${c.options[i]?.label}"`)
  const last = names[names.length - 1]
  return `${c.question} ${names.length > 1 ? `Do these: ${names.slice(0, -1).join(', ')} and ${last}.` : `Go with ${last}.`}`
}

// One tap on an option: a single choice swaps to it (or clears it), a multi choice toggles it.
export const pick = (c: Choice, i: number): Choice => ({
  ...c,
  picked: c.multi ? (c.picked.includes(i) ? c.picked.filter(x => x !== i) : [...c.picked, i].sort((a, b) => a - b)) : c.picked[0] === i ? [] : [i],
})

// What still needs the person on a card: un-ticked attention and action items, and an unsent choice.
export const openCount = (c: Card | undefined) =>
  !c ? 0 : waiting(c) + c.attention.filter((_, i) => !c.done.includes(`n${i}`)).length + c.actions.filter((_, i) => !c.done.includes(`a${i}`)).length

export const toggle = (c: Card, key: string): Card => ({ ...c, done: c.done.includes(key) ? c.done.filter(k => k !== key) : [...c.done, key] })

// The card as plain text: the /heads-up reply, for a screen that seats no panes.
export function asText(c: Card): string {
  const box = (key: string) => (c.done.includes(key) ? '☑' : '☐')
  return [
    c.gist ? `✓ ${c.gist}` : '',
    ...c.attention.map((x, i) => `⚠ ${box(`n${i}`)} ${x}`),
    ...c.actions.map((x, i) => `👉 ${box(`a${i}`)} ${x}`),
    ...(c.choice ? [`❓ ${c.choice.question}${c.choice.multi ? ' (pick any)' : ' (pick one)'}`, ...c.choice.options.map((o, i) => `${i + 1}. ${o.label}${c.choice?.suggested === i ? ' (suggested)' : ''}`), c.choice.sent ? `→ ${c.choice.sent}` : 'Reply with the numbers, or none.'] : []),
    ...c.ideas.map(x => `💡 ${x}`),
    ...(hasAny(c) ? [] : ['Nothing needs you.']),
  ]
    .filter(Boolean)
    .join('\n')
}

// The tiles' look: each kind its tint, its colour and an 8x8 pixel icon (an emoji where no Svg is drawn).
export type Kind = 'attention' | 'action' | 'idea' | 'ok'
export const LOOK: Record<Kind, { bg: string; fg: string; emoji: string; alt: string }> = {
  attention: { bg: '#2a2112', fg: '#F0B44C', emoji: '⚠', alt: 'needs your attention' },
  action: { bg: '#13212f', fg: '#5AA9F0', emoji: '👉', alt: 'for you to do' },
  idea: { bg: '#132619', fg: '#5FD38A', emoji: '💡', alt: 'idea' },
  ok: { bg: '#132619', fg: '#5FD38A', emoji: '✓', alt: 'nothing needs you' },
}
export const CHOICE_BG = '#1c1d20'
export const ROW_BG = '#26272b'
export const PICKED_BG = '#172638'
export const ACCENT = '#4C8DF6'

const ICONS: Record<Kind, string[]> = {
  attention: ['...##...', '...##...', '...##...', '...##...', '...##...', '........', '...##...', '...##...'],
  action: ['........', '....#...', '....##..', '.######.', '.######.', '....##..', '....#...', '........'],
  idea: ['..####..', '.######.', '.######.', '.######.', '..####..', '...bb...', '..bbbb..', '...bb...'],
  ok: ['........', '.......#', '......##', '#....##.', '##..##..', '.####...', '..##....', '........'],
}

export function iconSvg(kind: Kind): string {
  const fill: Record<string, string> = { '#': LOOK[kind].fg, b: '#8A8F98' }
  const px = ICONS[kind].flatMap((r, y) => [...r].map((c, x) => (fill[c] ? `<rect x="${x}" y="${y}" width="1" height="1" fill="${fill[c]}"/>` : ''))).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" width="16" height="16" shape-rendering="crispEdges">${px}</svg>`
}

// The header's count chips: what is still open in the card, by kind.
export function chips(c: Card): { kind: Kind | 'choice'; n: number }[] {
  const open = (list: string[], k: string) => list.filter((_, i) => !c.done.includes(`${k}${i}`)).length
  return [
    { kind: 'attention' as const, n: open(c.attention, 'n') },
    { kind: 'choice' as const, n: waiting(c) },
    { kind: 'action' as const, n: open(c.actions, 'a') },
  ].filter(x => x.n > 0)
}
