// Pure parts of the inbox: reading auto-bug issue titles and summing them up.

export type Issue = { number: number; title: string; created_at?: string; updated_at?: string; comments?: number; pull_request?: unknown }
export type Report = { n: number; kind: string; device: string; text: string; created: string; updated: string }

// Kinds a person should hear about the moment they arrive: hand-filed reports and crashes.
export const LOUD = new Set(['Reported', 'Crash', 'Stuck'])

// "🐞 Slow (Android): A 200ms stutter on Queue [bug:32c30627]" (the device is left out on older ones).
export function parse(i: Issue): Report | undefined {
  if (i.pull_request) return undefined
  const m = i.title.match(/^\s*🐞\s*([^:(]+?)\s*(?:\(([^)]*)\))?\s*:\s*(.*?)\s*(?:\[bug:[\w-]+\])?\s*$/u)
  if (!m) return undefined
  return { n: i.number, kind: (m[1] ?? '').trim(), device: (m[2] ?? '').trim(), text: (m[3] ?? '').trim(), created: i.created_at ?? '', updated: i.updated_at ?? '' }
}

const cut = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1)}…` : s)
export const line = (r: Report) => `#${r.n} ${r.kind}${r.device ? ` (${r.device})` : ''}: ${cut(r.text, 90)}`

export function counts(rs: readonly Report[]): string {
  const by = new Map<string, number>()
  for (const r of rs) by.set(r.kind, (by.get(r.kind) ?? 0) + 1)
  return [...by].sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k} ${n}`).join(', ')
}

export function status(rs: readonly Report[]): string {
  const loud = rs.filter(r => LOUD.has(r.kind)).length
  return `🐞 ${rs.length} open${loud ? ` · ${loud} by hand/crash` : ''}`
}

// The note the first message of a chat carries.
export function firstNote(rs: readonly Report[], fresh: readonly Report[]): string {
  if (!rs.length) return '[bug-inbox] No open problem reports (auto-bug issues).'
  const loud = rs.filter(r => LOUD.has(r.kind)).sort((a, b) => b.n - a.n)
  const parts = [`[bug-inbox] Open problem reports (auto-bug issues): ${rs.length} (${counts(rs)}).`]
  if (loud.length) parts.push(`Hand-filed / crash ones, newest first:\n${loud.slice(0, 6).map(r => `- ${line(r)}`).join('\n')}`)
  const freshLoud = fresh.filter(r => LOUD.has(r.kind))
  if (freshLoud.length) {
    parts.push(`New since the last chat:\n${freshLoud.slice(0, 5).map(r => `- ${line(r)}`).join('\n')}\nEnd your reply with one short line telling the person (number + what it is). Don't start fixing unless they ask; the daily fixer handles the rest.`)
  } else {
    parts.push('Nothing hand-filed is new since the last chat: mention the inbox only if they ask what to work on next or about bugs.')
  }
  return parts.join('\n')
}

// /inbox: everything, grouped, the loud kinds first.
export function listing(rs: readonly Report[]): string {
  if (!rs.length) return 'No open problem reports.'
  const kinds = [...new Set(rs.map(r => r.kind))].sort((a, b) => Number(LOUD.has(b)) - Number(LOUD.has(a)) || a.localeCompare(b))
  const out = [`${rs.length} open problem reports (${counts(rs)}):`]
  for (const k of kinds) {
    const of = rs.filter(r => r.kind === k).sort((a, b) => b.n - a.n)
    out.push(`\n${k} (${of.length})`)
    for (const r of of.slice(0, 8)) out.push(`  ${line(r)}`)
    if (of.length > 8) out.push(`  …and ${of.length - 8} more`)
  }
  return out.join('\n')
}
