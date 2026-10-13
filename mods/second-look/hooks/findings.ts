// Pure helpers (no `$`): read the reviewer's answer and say it back.
import type { Finding } from '../types'

export function parseFindings(text: string): Finding[] | undefined {
  const at = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (at < 0 || end < at) return undefined
  try {
    const raw = (JSON.parse(text.slice(at, end + 1)) as { findings?: unknown }).findings
    if (!Array.isArray(raw)) return undefined
    return raw.flatMap(f => {
      const o = f as Record<string, unknown>
      if (typeof o.what !== 'string' || !o.what.trim()) return []
      return [{
        severity: o.severity === 'block' ? 'block' as const : 'note' as const,
        file: typeof o.file === 'string' ? o.file : '?',
        what: o.what.trim(),
        fix: typeof o.fix === 'string' ? o.fix.trim() : '',
      }]
    })
  } catch {
    return undefined
  }
}

export const describe = (fs: readonly Finding[]) => fs.map(f => `- ${f.file}: ${f.what}${f.fix ? ` Fix: ${f.fix}` : ''}`).join('\n')

// .claude/second-look.md: prose for the reviewer, plus "first:" (review these paths first) and "skip:" (leave out) lines.
export function readNotes(md: string) {
  const first: string[] = []
  const skip: string[] = []
  const text = md.split('\n').filter(l => {
    const m = l.match(/^\s*(first|skip):\s*(.+)$/i)
    if (!m) return true
    ;(m[1]?.toLowerCase() === 'first' ? first : skip).push(...(m[2] ?? '').split(/[\s,]+/).filter(Boolean))
    return false
  }).join('\n').trim()
  return { text, first, skip }
}
