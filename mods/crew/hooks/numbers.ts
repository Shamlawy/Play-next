// Pure helpers (no `$`): labels, bars and "what it is doing" lines for the crew panel.
// 0.3 (the owner: "crew only shows price which i dont care about much i care about what the agents are doing and how"):
// no prices or token counts any more; each helper's task, what it says, its recent steps and its result instead.
import type { Helper, Step } from '../types'

// claude-opus-5-5 → Opus 5.5; an alias or unknown id is shown as given.
export function modelName(id: string): string {
  const m = id.toLowerCase().match(/(fable|mythos|opus|sonnet|haiku)-(\d+)(?:-(\d+))?/)
  if (!m) return id
  const fam = (m[1] ?? '').charAt(0).toUpperCase() + (m[1] ?? '').slice(1)
  return `${fam} ${m[2]}${m[3] && m[3].length < 3 ? `.${m[3]}` : ''}`
}


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
    time: hs.length ? Math.max(0, end - start) : 0,
    running: hs.filter(h => h.status === 'running').length,
  }
}

const base = (x: string) => x.replace(/\\/g, '/').split('/').filter(Boolean).pop() ?? x
const cut = (x: string, n: number) => {
  const t = x.replace(/\s+/g, ' ').trim()
  return t.length > n ? `${t.slice(0, n - 1)}…` : t
}

// What a tool call is doing, in plain words: "Reading index.html", "Searching for 'onScrolled'", "Running node tools/kit.mjs sweep".
export function toolLabel(tool: string, input: Record<string, unknown>): string {
  const str = (k: string) => (typeof input[k] === 'string' ? (input[k] as string) : '')
  const file = str('file_path') || str('notebook_path')
  const short = tool.replace(/^mcp__.*?__/, '')
  switch (tool) {
    case 'Read': return `Reading ${base(file)}`
    case 'Edit': case 'MultiEdit': case 'NotebookEdit': return `Editing ${base(file)}`
    case 'Write': return `Writing ${base(file)}`
    case 'Grep': return `Searching for '${cut(str('pattern'), 30)}'${str('path') ? ` in ${base(str('path'))}` : ''}`
    case 'Glob': return `Finding files ${cut(str('pattern'), 30)}`
    case 'Bash': case 'PowerShell': return str('description') ? cut(str('description'), 50) : `Running ${cut(str('command'), 40)}`
    case 'WebFetch': return `Fetching ${cut(str('url').replace(/^https?:\/\//, ''), 40)}`
    case 'WebSearch': return `Searching the web: ${cut(str('query'), 36)}`
    case 'TaskCreate': case 'TaskUpdate': case 'TodoWrite': return 'Updating its task list'
    case 'Agent': case 'Task': return `Starting a helper: ${cut(str('description'), 36)}`
  }
  if (file) return `${short} ${base(file)}`
  if (str('command')) return `${short}: ${cut(str('command'), 40)}`
  if (str('pattern')) return `${short} ${cut(str('pattern'), 30)}`
  return short
}

// A helper's words, cut to one short line: its task, what it last said, its result.
export function gist(text: string, n = 120): string {
  const line = text.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/[`*_#>]+/g, '').split('\n').map(x => x.trim()).find(Boolean) ?? ''
  return cut(line, n)
}

// The newest steps first-in-first-out, at most n.
export const push = (list: readonly string[] | undefined, x: string, n = 4) => [...(list ?? []).filter((y, i, a) => !(i === a.length - 1 && y === x)), x].slice(-n)

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

// A helper still at work: running, and heard from in the last 10 minutes (one killed midway never reports back,
// and must not keep the panel from opening by itself for the next batch).
export const active = (h: Pick<Helper, 'status' | 'startedAt' | 'seenAt'>, at: number) => h.status === 'running' && at - (h.seenAt ?? h.startedAt) < 10 * 60_000
