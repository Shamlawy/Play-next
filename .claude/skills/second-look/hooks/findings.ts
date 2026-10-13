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
