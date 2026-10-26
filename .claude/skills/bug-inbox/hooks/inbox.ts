// Pure parts of the inbox: reading problem-report issue titles and summing them up.
// Two kinds of issue: the web app's (label auto-bug) and the Android app's (label android-bug, worked by the Android build sessions).

export type Issue = { number: number; title: string; created_at?: string; updated_at?: string; comments?: number; pull_request?: unknown }
export type App = 'web' | 'android'
export type Report = { n: number; kind: string; device: string; text: string; created: string; updated: string; app: App }

// Kinds a person should hear about the moment they arrive: hand-filed reports and crashes.
export const LOUD = new Set(['Reported', 'Crash', 'Stuck'])

// "🐞 Slow (Android): A 200ms stutter on Queue [bug:32c30627]" (the device is left out on older ones).
export function parse(i: Issue, app: App = 'web'): Report | undefined {
  if (i.pull_request) return undefined
  const m = i.title.match(/^\s*🐞\s*([^:(]+?)\s*(?:\(([^)]*)\))?\s*:\s*(.*?)\s*(?:\[bug:[\w-]+\])?\s*$/u)
  if (!m) return undefined
  return { n: i.number, kind: (m[1] ?? '').trim(), device: (m[2] ?? '').trim(), text: (m[3] ?? '').trim(), created: i.created_at ?? '', updated: i.updated_at ?? '', app }
}

const cut = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1)}…` : s)
// `tag`: in a list that mixes both apps, the Android app's reports say so.
export const line = (r: Report, tag = false) => `#${r.n} ${tag && r.app === 'android' ? 'Android app · ' : ''}${r.kind}${r.device ? ` (${r.device})` : ''}: ${cut(r.text, 90)}`

const web = (rs: readonly Report[]) => rs.filter(r => r.app !== 'android')
const droid = (rs: readonly Report[]) => rs.filter(r => r.app === 'android')

export function counts(rs: readonly Report[]): string {
  const by = new Map<string, number>()
  for (const r of rs) by.set(r.kind, (by.get(r.kind) ?? 0) + 1)
  return [...by].sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k} ${n}`).join(', ')
}

export function status(rs: readonly Report[]): string {
  const w = web(rs)
  const a = droid(rs)
  const loud = rs.filter(r => LOUD.has(r.kind)).length
  return `🐞 ${w.length} open${a.length ? ` · Android ${a.length}` : ''}${loud ? ` · ${loud} by hand/crash` : ''}`
}

// The note the first message of a chat carries.
export function firstNote(rs: readonly Report[], fresh: readonly Report[]): string {
  const w = web(rs)
  const a = droid(rs)
  if (!rs.length) return '[bug-inbox] No open problem reports (auto-bug or android-bug issues).'
  const parts = [w.length ? `[bug-inbox] Open problem reports (auto-bug issues): ${w.length} (${counts(w)}).` : '[bug-inbox] No open problem reports from the web app (auto-bug issues).']
  const wLoud = w.filter(r => LOUD.has(r.kind)).sort((x, y) => y.n - x.n)
  if (wLoud.length) parts.push(`Hand-filed / crash ones, newest first:\n${wLoud.slice(0, 6).map(r => `- ${line(r)}`).join('\n')}`)
  if (a.length) {
    parts.push(`Android app: ${a.length} open (android-bug issues: ${counts(a)}). They belong to the Android build sessions, not the daily fixer.`)
    const aLoud = a.filter(r => LOUD.has(r.kind)).sort((x, y) => y.n - x.n)
    if (aLoud.length) parts.push(`Android hand-filed / crash ones, newest first:\n${aLoud.slice(0, 6).map(r => `- ${line(r)}`).join('\n')}`)
  }
  const freshLoud = fresh.filter(r => LOUD.has(r.kind))
  if (freshLoud.length) {
    parts.push(`New since the last chat:\n${freshLoud.slice(0, 5).map(r => `- ${line(r, true)}`).join('\n')}\nEnd your reply with one short line telling the person (number + what it is). Don't start fixing unless they ask; the daily fixer (web) and the Android build sessions handle the rest.`)
  } else {
    parts.push('Nothing hand-filed is new since the last chat: mention the inbox only if they ask what to work on next or about bugs.')
  }
  return parts.join('\n')
}

// One app's reports grouped by kind, the loud kinds first.
function groups(rs: readonly Report[], indent: string): string[] {
  const out: string[] = []
  const kinds = [...new Set(rs.map(r => r.kind))].sort((a, b) => Number(LOUD.has(b)) - Number(LOUD.has(a)) || a.localeCompare(b))
  for (const k of kinds) {
    const of = rs.filter(r => r.kind === k).sort((a, b) => b.n - a.n)
    out.push(`\n${indent}${k} (${of.length})`)
    for (const r of of.slice(0, 8)) out.push(`${indent}  ${line(r)}`)
    if (of.length > 8) out.push(`${indent}  …and ${of.length - 8} more`)
  }
  return out
}

// /inbox: everything, the web app's grouped by kind (loud kinds first), then the Android app's in their own group.
export function listing(rs: readonly Report[]): string {
  if (!rs.length) return 'No open problem reports.'
  const w = web(rs)
  const a = droid(rs)
  const out = [w.length ? `${w.length} open problem reports (${counts(w)}):` : 'No open problem reports from the web app.']
  out.push(...groups(w, ''))
  if (a.length) {
    out.push(`\nAndroid app (android-bug): ${a.length} open (${counts(a)}):`)
    out.push(...groups(a, '  '))
  }
  return out.join('\n')
}
