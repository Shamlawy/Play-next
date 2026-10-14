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
{"gist": "...", "attention": [{"line": "...", "why": "..."}], "actions": [{"line": "...", "why": "..."}], "ideas": [{"line": "...", "why": "..."}], "choice": null}

- gist: one plain line (at most 15 words) saying what was done or found. Past tense. No filler.
- attention: what the user must know or decide: something failed, broke, was skipped or could not be done; a risk; a question the assistant asked them; a choice waiting on them; a result that differs from what they asked. Not routine success.
- actions: things ONLY the user can do (approve, answer, test on their device, add a secret, click a link, merge if the assistant did not). Never things the assistant already did or will do itself. Start each with a verb.
- ideas: suggestions or follow-ups the assistant offered but did not do, when they are not part of the choice below.
- choice: when the reply asks the user to pick, or lays out options or next steps the assistant could do on the user's say-so ("want me to A or B?", "options: ...", "I can also ..."), turn them into something the user can tap: {"question": "...", "multi": true|false, "options": [{"label": "...", "why": "..."}], "suggested": <index or null>}. question: the decision, at most 12 words, ending with "?". options: 2 to 5, each label at most 10 words, the way the user would say it ("Fix the Safari scroll first"); each why one plain sentence (what it means or costs). multi: true when several can be done together (independent follow-ups), false when they exclude each other (one way or the other). suggested: the option the assistant recommended, else null. Leave choice null when nothing is offered; never invent options. Options in the choice are not repeated in ideas or actions.

Each item has "line" (the short point, at most 15 words) and "why" (one or two plain sentences from the reply explaining it: the reason, the detail or what happens next; shown when the user hovers the line).

Rules: never invent anything that is not in the reply. Plain words, keep exact names, numbers, links and PR/issue references. Merge duplicates. At most ${MAX_LINES} items in the three lists together (the choice does not count), the most important first; one or two is often right. Empty lists are fine and common: a reply that only reports finished work has attention, actions and ideas all empty.`

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

Use 2 to 4 lines in all (each with its ↳), most important first, and only the kinds that apply. Each line at most 15 words, each ↳ at most 20; plain words; keep exact names, numbers and links; never invent. When nothing needs them, the box is one line: "> **Heads-up** ✓ <what was done, at most 15 words>". When the reply offers options or asks them to choose, the box ends with "> ❓ <the question>" and the options numbered on their own lines ("> 1. ...", the one you recommend marked "(suggested)"), then "> Reply with the numbers, or none." Then write the reply as usual, without repeating the box. Short replies get no box, and neither does a report a helper agent writes for another Claude.`

export function prompt(ask: string, reply: string): string {
  return `${ask ? `The user's message:\n${ask.slice(0, 2000)}\n\n` : ''}The assistant's reply:\n${reply.slice(0, 30_000)}`
}

const clean = (x: string, max: number) => {
  const t = x.replace(/\s+/g, ' ').trim()
  return t.length > max ? `${t.slice(0, max - 1)}…` : t
}

type Item = { line: string; why: string }
type Choice = NonNullable<Card['choice']>

// The choice, read leniently: 2-5 labelled options, a question, one or many, and the suggested one if it exists.
const choiceOf = (v: unknown): Choice | undefined => {
  if (!v || typeof v !== 'object') return undefined
  const c = v as Record<string, unknown>
  const options = (Array.isArray(c.options) ? c.options : [])
    .map(x => {
      if (typeof x === 'string') return { label: x, why: '' }
      if (!x || typeof x !== 'object') return undefined
      const o = x as Record<string, unknown>
      return { label: String(o.label ?? o.line ?? ''), why: typeof o.why === 'string' ? o.why : '' }
    })
    .filter((x): x is { label: string; why: string } => !!x)
    .map(x => ({ label: clean(x.label, 120), why: clean(x.why, MAX_WHY) }))
    .filter(x => x.label)
    .slice(0, 5)
  if (options.length < 2) return undefined
  const n = c.suggested
  const suggested = typeof n === 'number' && Number.isInteger(n) && n >= 0 && n < options.length ? n : undefined
  const question = clean(typeof c.question === 'string' && c.question ? c.question : 'Which one?', 160)
  return { question, multi: c.multi === true, options, ...(suggested === undefined ? {} : { suggested }), picked: [] }
}

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
export function parse(text: string): Pick<Card, 'gist' | 'attention' | 'actions' | 'ideas' | 'why' | 'choice'> | undefined {
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
  const choice = choiceOf(r.choice)
  return { gist, attention, actions, ideas, why, ...(choice ? { choice } : {}) }
}

export const heading = (text: string) => {
  const t = text.replace(/\s+/g, ' ').trim()
  const cut = t.length > 60 ? `${t.slice(0, 59).replace(/\s+\S*$/, '')}…` : t
  return cut ? cut.charAt(0).toUpperCase() + cut.slice(1) : ''
}

export const hasAny = (c: Pick<Card, 'attention' | 'actions' | 'ideas' | 'choice'>) => c.attention.length + c.actions.length + c.ideas.length > 0 || !!c.choice

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

// What still needs the person: un-ticked attention and action items, across the kept cards.
export const openCount = (cards: readonly Card[]) =>
  cards.reduce(
    (n, c) => n + waiting(c) + c.attention.filter((_, i) => !c.done.includes(`n${i}`)).length + c.actions.filter((_, i) => !c.done.includes(`a${i}`)).length,
    0,
  )

export const toggle = (c: Card, key: string): Card => ({ ...c, done: c.done.includes(key) ? c.done.filter(k => k !== key) : [...c.done, key] })

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

// The header's count chips: what is still open in one card, by kind.
export function chips(c: Card): { kind: Kind | 'choice'; n: number }[] {
  const open = (list: string[], k: string) => list.filter((_, i) => !c.done.includes(`${k}${i}`)).length
  return [
    { kind: 'attention' as const, n: open(c.attention, 'n') },
    { kind: 'choice' as const, n: waiting(c) },
    { kind: 'action' as const, n: open(c.actions, 'a') },
  ].filter(x => x.n > 0)
}

// Newest first, as plain text: the /heads-up reply, for a screen that seats no panes.
export function asText(c: Card): string {
  const box = (key: string) => (c.done.includes(key) ? '☑' : '☐')
  const why = (key: string) => (c.why?.[key] ? [`   ↳ ${c.why[key]}`] : [])
  return [
    c.ask ? `“${c.ask}”` : '',
    c.gist ? `✓ ${c.gist}` : '',
    ...(c.attention.length ? ['', '⚠ Needs your attention', ...c.attention.flatMap((x, i) => [`${box(`n${i}`)} ${x}`, ...why(`n${i}`)])] : []),
    ...(c.actions.length ? ['', '👉 For you to do', ...c.actions.flatMap((x, i) => [`${box(`a${i}`)} ${x}`, ...why(`a${i}`)])] : []),
    ...(c.choice ? ['', `❓ ${c.choice.question}${c.choice.multi ? ' (pick any)' : ' (pick one)'}`, ...c.choice.options.map((o, i) => `${i + 1}. ${o.label}${c.choice?.suggested === i ? ' (suggested)' : ''}`), c.choice.sent ? `→ ${c.choice.sent}` : 'Reply with the numbers, or none.'] : []),
    ...(c.ideas.length ? ['', '💡 Ideas', ...c.ideas.flatMap((x, i) => [`• ${x}`, ...why(`i${i}`)])] : []),
    ...(hasAny(c) ? [] : ['', 'Nothing needs you.']),
  ]
    .filter((x, i, a) => x !== '' || (i > 0 && a[i - 1] !== ''))
    .join('\n')
    .trim()
}
