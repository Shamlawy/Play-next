// Pure helpers (no `$`): prices, labels and bars for the crew panel.
import type { Helper } from '../types'

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

// An 8x6 pixel crab; two leg frames walk while it works (SMIL, no script).
export function crabSvg(walking: boolean, colour = '#E8743B'): string {
  const body = ['.X....X.', 'X.XXXX.X', 'XXXXXXXX', '.XXXXXX.', '.X.XX.X.', 'X......X']
  const legs2 = ['.X....X.', 'X.XXXX.X', 'XXXXXXXX', '.XXXXXX.', 'X.X..X.X', '.X....X.']
  const px = (rows: string[]) => rows.flatMap((r, y) => [...r].map((c, x) => (c === 'X' ? `<rect x="${x}" y="${y}" width="1" height="1"/>` : ''))).join('')
  const eyes = '<rect x="2" y="1" width="1" height="1" fill="#111"/><rect x="5" y="1" width="1" height="1" fill="#111"/>'
  if (!walking) return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 6" width="24" height="18" shape-rendering="crispEdges"><g fill="${colour}">${px(body)}</g>${eyes}</svg>`
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 0 12 6" width="36" height="18" shape-rendering="crispEdges">` +
    `<g><animateTransform attributeName="transform" type="translate" values="-2 0;2 0;-2 0" dur="2.4s" repeatCount="indefinite"/>` +
    `<g fill="${colour}">${px(body)}<animate attributeName="opacity" values="1;0;1" dur="0.5s" calcMode="discrete" repeatCount="indefinite"/></g>` +
    `<g fill="${colour}" opacity="0">${px(legs2)}<animate attributeName="opacity" values="0;1;0" dur="0.5s" calcMode="discrete" repeatCount="indefinite"/></g>${eyes}</g></svg>`
}
