// Pure helpers (no `$`): prices, labels and bars for the crew panel.
import type { Helper, Step } from '../types'

type Usage = { input_tokens: number; output_tokens: number; cache_read_input_tokens: number; cache_creation_input_tokens: number }

// $ per million tokens: input, output, cache read (first-party API list prices, Oct 2026). Cache writes cost 1.25x input.
// A rough guide only: on a Claude plan nothing is billed per token.
const PRICES: [RegExp, number, number, number][] = [
  [/fable|mythos/, 10, 50, 0.25],
  [/opus-5-5/, 4, 20, 0.2],
  [/opus/, 5, 25, 0.5],
  [/sonnet-5/, 2, 10, 0.2],
  [/sonnet/, 3, 15, 0.3],
  [/haiku-5/, 0.1, 0.5, 0.01],
  [/haiku/, 1, 5, 0.1],
]

export function cost(model: string, u: Usage): number {
  const p = PRICES.find(([re]) => re.test(model.toLowerCase()))
  if (!p) return 0
  const [, inp, out, read] = p
  return (u.input_tokens * inp + u.cache_creation_input_tokens * inp * 1.25 + u.cache_read_input_tokens * read + u.output_tokens * out) / 1e6
}

export const billed = (u: Usage) => u.input_tokens + u.output_tokens + u.cache_read_input_tokens + u.cache_creation_input_tokens
export const window = (u: Usage) => u.input_tokens + u.cache_read_input_tokens + u.cache_creation_input_tokens

// claude-opus-5-5 → Opus 5.5; an alias or unknown id is shown as given.
export function modelName(id: string): string {
  const m = id.toLowerCase().match(/(fable|mythos|opus|sonnet|haiku)-(\d+)(?:-(\d+))?/)
  if (!m) return id
  const fam = (m[1] ?? '').charAt(0).toUpperCase() + (m[1] ?? '').slice(1)
  return `${fam} ${m[2]}${m[3] && m[3].length < 3 ? `.${m[3]}` : ''}`
}

export const k = (n: number) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1000 ? `${Math.round(n / 1000)}k` : `${n}`)
export const usd = (n: number) => (n >= 10 ? `≈$${n.toFixed(0)}` : `≈$${n.toFixed(2)}`)

export function clock(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = String(s % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`
}

// How far along a helper looks: finished is full; running creeps toward 90% with every step it takes.
export const progress = (h: Pick<Helper, 'status' | 'steps'>) => (h.status === 'running' ? Math.min(0.9, 0.08 + 0.82 * (1 - 1 / (1 + h.steps / 6))) : 1)

export function bar(frac: number, width: number): [string, string] {
  const full = Math.round(Math.max(0, Math.min(1, frac)) * width)
  return ['━'.repeat(full), '─'.repeat(Math.max(0, width - full))]
}

export const elapsed = (h: Pick<Helper, 'startedAt' | 'endedAt'>, now: number) => (h.endedAt ?? Math.max(now, h.startedAt)) - h.startedAt

// The whole crew's totals for the header.
export function totals(hs: readonly Helper[], now: number) {
  const start = Math.min(...hs.map(h => h.startedAt))
  const end = hs.some(h => h.status === 'running') ? now : Math.max(...hs.map(h => h.endedAt ?? now))
  return {
    cost: hs.reduce((a, h) => a + h.costUsd, 0),
    tokens: hs.reduce((a, h) => a + h.tokens, 0),
    time: hs.length ? Math.max(0, end - start) : 0,
    running: hs.filter(h => h.status === 'running').length,
  }
}

// A short "what it is doing" from a tool call.
export function toolLabel(tool: string, input: Record<string, unknown>): string {
  const file = typeof input.file_path === 'string' ? input.file_path.split('/').pop() : undefined
  if (file) return `${tool} ${file}`
  if (typeof input.command === 'string') return `${tool}: ${input.command.slice(0, 40)}`
  if (typeof input.pattern === 'string') return `${tool} ${input.pattern.slice(0, 30)}`
  return tool.replace(/^mcp__[^_]+__/, '')
}

// How hard a helper thinks, as a crew tier: its word, colour and hat.
export type Tier = { name: 'light' | 'medium' | 'careful'; colour: string; hat: string; band: string }
const TIERS: Record<Tier['name'], Tier> = {
  light: { name: 'light', colour: '#2BB58A', hat: '#2BB58A', band: '#1E8C69' },
  medium: { name: 'medium', colour: '#4C8DF6', hat: '#F2F2F2', band: '#4C8DF6' },
  careful: { name: 'careful', colour: '#F0962E', hat: '#F2C230', band: '#C9971A' },
}
export function tier(effort: string | undefined): Tier {
  if (effort === 'low') return TIERS.light
  if (effort === 'high' || effort === 'xhigh' || effort === 'max' || (effort && Number(effort) >= 16000)) return TIERS.careful
  return TIERS.medium
}

// A 12x12 pixel crab in its tier's hat; two leg frames step while it works (SMIL, no script). A planned one is a dim shadow.
export function crabSvg(t: Tier, mode: 'working' | 'resting' | 'planned' | 'failed'): string {
  const hat = t.name === 'light'
    ? ['....hhhh....', '...hhhhhhh..', '...bbbbbbbbb']
    : t.name === 'medium'
      ? ['....hhhh....', '...hhhhhh...', '..bbbbbbbb..']
      : ['....hhhh....', '...hhhhhh...', '.hhhhhhhhhh.']
  const body = ['..oooooooo..', '.oooooooooo.', '.owwoooowwo.', '.oweooooweo.', 'oooooooooooo', '.oooooooooo.']
  const legsA = ['..o..oo..o..', '.o...oo...o.', '............']
  const legsB = ['...o.oo.o...', '..o..oo..o..', '............']
  const ghost = mode === 'planned'
  const fill: Record<string, string> = {
    h: ghost ? '#4A4038' : t.hat, b: ghost ? '#4A4038' : t.band,
    o: ghost ? '#5A4A40' : mode === 'failed' ? '#8A8A8A' : '#E8743B', e: '#151515', w: ghost ? '#6A5A50' : '#F4F4F4',
  }
  const px = (rows: string[], y0: number) => rows.flatMap((r, y) => [...r].map((c, x) => (fill[c] ? `<rect x="${x}" y="${y + y0}" width="1" height="1" fill="${fill[c]}"/>` : ''))).join('')
  const top = px(hat, 0) + px(body, 3)
  const feet = 9
  const head = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 12 12" width="36" height="36" shape-rendering="crispEdges">`
  if (mode !== 'working') return `${head}${top}${px(legsA, feet)}</svg>`
  return `${head}<g>${top}<animateTransform attributeName="transform" type="translate" values="0 0;0 -0.5;0 0" dur="0.5s" repeatCount="indefinite"/></g>` +
    `<g>${px(legsA, feet)}<animate attributeName="opacity" values="1;0;1" dur="0.5s" calcMode="discrete" repeatCount="indefinite"/></g>` +
    `<g opacity="0">${px(legsB, feet)}<animate attributeName="opacity" values="0;1;0" dur="0.5s" calcMode="discrete" repeatCount="indefinite"/></g></svg>`
}

// A task list tool call (TaskCreate / TaskUpdate / TodoWrite) applied to a list of steps.
export function applyTask(list: readonly Step[], tool: string, input: Record<string, unknown>, resultText = ''): Step[] {
  if (tool === 'TodoWrite' && Array.isArray(input.todos)) {
    return input.todos.flatMap((t, i) => {
      const o = t as Record<string, unknown>
      const subject = String(o.content ?? o.subject ?? '').trim()
      const st = o.status === 'completed' || o.status === 'in_progress' ? o.status : 'pending'
      return subject ? [{ id: String(i + 1), subject, status: st as Step['status'] }] : []
    })
  }
  if (tool === 'TaskCreate' && typeof input.subject === 'string') {
    const id = resultText.match(/#\s*(\d+)/)?.[1] ?? String(list.length + 1)
    return [...list.filter(x => x.id !== id), { id, subject: input.subject.trim(), status: 'pending' }]
  }
  if (tool === 'TaskUpdate' && typeof input.taskId === 'string') {
    const id = input.taskId
    if (input.status === 'deleted') return list.filter(x => x.id !== id)
    return list.map(x => x.id !== id ? x : {
      ...x,
      subject: typeof input.subject === 'string' ? input.subject : x.subject,
      status: input.status === 'completed' || input.status === 'in_progress' || input.status === 'pending' ? input.status : x.status,
    })
  }
  return [...list]
}

// "3/5 · Hero copy" from a helper's own task list, else "4 steps · Read index.html".
export function stepLine(x: Pick<Helper, 'todo' | 'steps' | 'lastTool' | 'status'>): string {
  const todo = x.todo ?? []
  if (todo.length) {
    const done = todo.filter(t => t.status === 'completed').length
    const now = todo.find(t => t.status === 'in_progress') ?? (x.status === 'running' ? todo.find(t => t.status === 'pending') : undefined)
    return `${done}/${todo.length}${now ? ` · ${now.subject}` : ''}`
  }
  return `${x.steps} step${x.steps === 1 ? '' : 's'}${x.status === 'running' && x.lastTool ? ` · ${x.lastTool}` : ''}`
}

// Real progress when the helper keeps a task list; otherwise it creeps toward 90% with every step.
export function progressOf(x: Pick<Helper, 'todo' | 'steps' | 'status'>): number {
  if (x.status !== 'running') return 1
  const todo = x.todo ?? []
  if (todo.length) return Math.max(0.04, Math.min(0.96, (todo.filter(t => t.status === 'completed').length + 0.5 * todo.filter(t => t.status === 'in_progress').length) / todo.length))
  return progress(x)
}
