// Pure parts of the learn mod: what counts as a signal worth learning from, how lessons are read back, and the words
// Claude is shown. docs/LESSONS.md is written only by tools/learn.mjs; this reads the same line format.

export type Signal = { kind: SignalKind; text: string }
export type SignalKind =
  | 'correction' // the person said something went wrong / wasn't what they asked
  | 'auto' // the person asked for something to happen by itself (could it have from the start?)
  | 'caught' // a check failed, then passed after a change: it caught a mistake
  | 'thrash' // the same failing command three times
  | 'edit-miss' // edits that didn't apply (stale view of the file)
  | 'guard' // release-guard stopped a commit / push / merge
  | 'release' // a release went out (APP_V bumped / merged): the moment for "what could have been better"
  | 'fixed-issue' // a problem report closed without saying whether Nexi can catch it himself
  | 'deferred' // the reply put something off ("worth doing later", "out of scope")

// Strong signals stop the turn once for a short retrospective; weak ones only ride along.
export const STRONG: ReadonlySet<SignalKind> = new Set(['correction', 'auto', 'caught', 'thrash', 'edit-miss', 'guard', 'release', 'fixed-issue'])

const CORRECTION = [
  /^\s*(no+|nope|nah|wrong|stop|wait)\b[\s,.!]/i,
  /\byou (forgot|missed|broke|didn'?t|did not|ignored|skipped)\b/i,
  /\bi (said|told you|asked( you)? (to|for))\b/i,
  /\b(that'?s|this is|it'?s) (not|wrong|broken)\b/i,
  /\bnot what i\b/i,
  /\bstill (broken|lags?|lagging|not|doesn'?t|isn'?t|there|happening|the same|stuck|slow)\b/i,
  /\b(doesn'?t|didn'?t|does not|did not|isn'?t|is not|not) work(ing|s)?\b/i,
  /\b(regress(ed|ion)?|revert( it| that)?|undo (it|that))\b/i,
  /\bwhy (did|didn'?t|would) you\b/i,
  /\bagain\?/i,
]
const AUTO = /\b(automatic(ally)?|auto[- ]?(run|update|fix|detect|matic)|by (it|him|her)self|on (its|his) own|without (me|asking|a tap|tapping|me asking))\b/i

// Only the person's own words count. The app and other sessions put text into a message too: system-reminders
// ("it runs on its own"), side-session notes quoting a child's report ("an idea to make that automatic"),
// task notifications, pasted blocks. L22/M8: the 'auto' trigger fired on those three times.
const INJECTED = ['system-reminder', 'side-sessions-event', 'task-notification', 'agent-message', 'ci-monitor-event', 'pasted_content', 'command-name', 'command-message', 'command-args', 'local-command-stdout']
// Origins of a message the person sent (the same set crew and heads-up use); others are agents, peers, notifications.
const PERSON = new Set(['composer', 'bridge', 'sdk', 'unclassified'])
// A brief from another session: the app's "Background (from the session that started this one)", or an auto-handoff note.
const FROM_SESSION = /^[\s\S]{0,300}?\(from the session that started this one\)|^\s*(?:#+\s*|\*\*)?handoff note\b/i
export function fromSession(text: string, origin?: string): boolean {
  return !PERSON.has(origin ?? 'unclassified') || FROM_SESSION.test(String(text ?? ''))
}
export function personWords(text: string): string {
  let t = String(text ?? '')
  for (const tag of INJECTED) {
    t = t.replace(new RegExp(`<${tag}\\b[^>]*>[\\s\\S]*?</${tag}\\s*>`, 'gi'), ' ')
      .replace(new RegExp(`<${tag}\\b[^>]*>[\\s\\S]*$`, 'i'), ' ') // cut off before its end tag
  }
  return t
    .replace(/^[ \t]*>.*$/gm, ' ') // quoted lines
    .replace(/“[^”]{40,}”|"[^"\n]{40,}"/g, ' ') // a quoted report; a short quote is often the person's own
}

export function promptSignals(text: string, origin?: string): Signal[] {
  const out: Signal[] = []
  if (fromSession(text, origin)) return out
  const t = personWords(text).slice(0, 4000)
  // A slash command or a pasted block isn't a correction.
  if (/^\s*\//.test(t)) return out
  const hit = CORRECTION.find(r => r.test(t))
  if (hit) out.push({ kind: 'correction', text: excerpt(t, hit) })
  if (AUTO.test(t)) out.push({ kind: 'auto', text: excerpt(t, AUTO) })
  return out
}

const DEFER = /\b(could also|worth (doing|adding|a look|trying)|(do|fix|handle|add) (it|that|this) later|later on|follow[- ]?up|out of scope|not in this (change|pr|release|chat)|left (it|that|this) (as is|alone)|didn'?t get to|next time|a separate (task|pr|change|chat)|for another (day|time|chat)|parked|TODO)\b/i
export function deferrals(answer: string): string[] {
  return String(answer ?? '')
    .replace(/```[\s\S]*?```/g, ' ')
    .split(/(?<=[.!?])\s+|\n+/)
    .filter(s => DEFER.test(s) && s.length > 25)
    .map(s => s.replace(/\s+/g, ' ').trim().slice(0, 220))
    .slice(0, 3)
}

function excerpt(t: string, r: RegExp): string {
  const m = r.exec(t)
  const i = m ? m.index : 0
  return t.slice(Math.max(0, i - 40), i + 120).replace(/\s+/g, ' ').trim()
}

// Checks Claude runs, by family, so "failed, then passed" can be told apart from two different checks.
export function checkFamily(cmd: string): string | undefined {
  const c = String(cmd ?? '')
  const kit = c.match(/tools\/kit\.mjs\s+(\w+)/)
  if (kit) return `kit ${kit[1]}`
  if (/tools\/where\.mjs[^\n]*--check/.test(c)) return 'where --check'
  const pl = c.match(/claude plugin (test|validate)\s+(\S+)/)
  if (pl) return `plugin ${pl[1]} ${(pl[2] ?? '').split('/').filter(Boolean).pop() ?? ''}`
  if (/node\s+--check/.test(c)) return 'node --check'
  if (/\btsc\b/.test(c)) return 'tsc'
  if (/playwright|chromium|pw-browsers|webkit/i.test(c)) return 'browser test'
  return undefined
}
export function failed(r: { isError?: boolean; text?: string; deny?: string }): boolean {
  if (r.deny !== undefined || r.isError) return true
  const tail = String(r.text ?? '').slice(-3000)
  return /(^|\n)\s*(FAIL\b|✗ |not ok\b)|Exit code [1-9]|\b[1-9]\d* (failed|failing)\b|AssertionError|SyntaxError:/i.test(tail)
}

// ---- lessons: the line format tools/learn.mjs writes ----
export type Lesson = { id: string; kind: 'mistake' | 'missed' | 'promoted'; open: boolean; n: number; to: string; anchors: string[]; text: string }
const LINE = /^- (?:\[( |x)\] )?\*\*([LM]\d+)\*\*(?: ×(\d+))?(?: → `([^`]+)`)? · (\d{4}-\d\d-\d\d) · (v?\d*) · ((?:`[^`]*`(?:, )?)*) — (.*)$/
export function parseLessons(md: string): Lesson[] {
  const out: Lesson[] = []
  let sec = ''
  for (const l of String(md ?? '').split('\n')) {
    if (l.startsWith('## ')) { sec = l.slice(3); continue }
    const m = l.match(LINE)
    if (!m) continue
    const [, box, id = '', n, to, , , anc = '', rest = ''] = m
    out.push({
      id, open: box === ' ', n: +(n || 1), to: to || '',
      kind: id[0] === 'M' ? 'missed' : to || /Promoted/.test(sec) ? 'promoted' : 'mistake',
      anchors: [...anc.matchAll(/`([^`]*)`/g)].map(x => x[1] ?? '').filter(Boolean),
      text: rest.replace(/ \(done: [^)]*\)$/, ''),
    })
  }
  return out
}
const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
// Same rules as tools/learn.mjs relevant(): a path anchor matches the file, a name an identifier, a phrase the command.
export function relevant(ls: readonly Lesson[], at: { file?: string; names?: readonly string[]; cmd?: string }): Lesson[] {
  const names = new Set(at.names ?? [])
  const file = (at.file ?? '').replace(/\\/g, '/')
  const cmd = at.cmd ?? ''
  return ls.filter(x => x.kind !== 'missed' || x.open).filter(x => x.anchors.some(a => {
    if (a.includes('/') || /\.\w{1,5}$/.test(a)) return (!!file && (file === a || file.endsWith('/' + a))) || (!!cmd && cmd.includes(a))
    if (/\s/.test(a)) return !!cmd && cmd.includes(a)
    return names.has(a) || (!!cmd && new RegExp(`\\b${esc(a)}\\b`).test(cmd))
  }))
}
export const identifiers = (code: string): string[] => [...new Set(String(code ?? '').match(/[A-Za-z_$][\w$]{2,}/g) ?? [])].slice(0, 400)

// ---- words ----
const LABEL: Record<SignalKind, string> = {
  correction: 'the person corrected you',
  auto: 'the person asked for something to happen by itself',
  caught: 'a check failed, then passed after a change',
  thrash: 'the same command failed three times',
  'edit-miss': "edits didn't apply (the file had changed)",
  guard: 'release-guard stopped something',
  release: 'a release went out',
  'fixed-issue': 'a problem report was closed without saying whether Nexi can now catch it himself',
  deferred: 'you put something off',
}
export function reflection(sig: readonly Signal[]): string {
  const seen = new Set<string>()
  const lines = sig.filter(s => { const k = s.kind + s.text; if (seen.has(k)) return false; seen.add(k); return true })
    .slice(0, 8).map(s => `- ${LABEL[s.kind]}${s.text ? `: “${s.text}”` : ''}`)
  const release = sig.some(s => s.kind === 'release')
  const deferred = sig.filter(s => s.kind === 'deferred')
  const fixed = sig.filter(s => s.kind === 'fixed-issue')
  return [
    '[learn] Before you finish: a short retrospective, since this turn had',
    ...lines,
    '',
    'Mistakes: for each real one, what went wrong and what would have caught it sooner:',
    '  node tools/learn.mjs add --kind=mistake --anchors=<functions, files, commands it is about> "<what happened> → <what to do instead>"',
    'Missed opportunities: what could have been better, or could run by itself (the owner loves automatic things)' +
      (release ? '; for this release: what would have made it better or arrive sooner?' : '') + ':',
    '  node tools/learn.mjs add --kind=idea --anchors=<…> "<the idea>"',
    ...(deferred.length ? ['  Anything you put off goes in as an idea so it is not lost.'] : []),
    ...(fixed.length ? ["  CLAUDE.md: say in the fix's comment whether Nexi learned to spot it himself, or why it can't be detected."] : []),
    "If tools/learn.mjs answers ×3 or more, the reminder isn't working: turn it into a check (release-guard rule, where.mjs --check, a kit.mjs sweep --plant) and `node tools/learn.mjs promote <id> --to=\"<check>\"`.",
    'Skip anything trivial or one-off; if nothing is worth keeping, just finish. No game titles, scores or personal data (public repo).',
    'Lessons reach the next chat only through git: commit docs/LESSONS.md (with your next commit, or on its own) and push. Then end with your normal answer; mention it in one line at most ("Noted 1 lesson").',
  ].join('\n')
}

export type Brief = { lessons: number; promoted: number; open: { id: string; text: string }[]; openCount: number; promote: { id: string; n: number; text: string }[]; stale: string[]; big: boolean }
export type Reopened = { number: number; title: string }
export function firstNote(b: Brief | undefined, reopened: readonly Reopened[]): string {
  const parts: string[] = []
  if (b) {
    parts.push(`[learn] docs/LESSONS.md: ${b.lessons} lesson${b.lessons === 1 ? '' : 's'}, ${b.promoted} turned into checks, ${b.openCount} open idea${b.openCount === 1 ? '' : 's'}. Lessons show up by themselves when you touch the code they're about; \`node tools/learn.mjs list\` has them all.`)
    if (b.promote.length) parts.push(`Keeps happening (×3+), make it an automatic check when you're next in that code: ${b.promote.map(p => `${p.id} ×${p.n} ${p.text.slice(0, 90)}`).join(' · ')}`)
    if (b.stale.length) parts.push(`Lessons whose code is gone (check, then delete or re-anchor them): ${b.stale.join(', ')}`)
    if (b.big) parts.push('Over 60 lessons: merge the near-duplicates and drop the ones that no longer apply.')
  }
  if (reopened.length) parts.push(`Fixes that didn't hold (closed, then "Happened again after it was closed"): ${reopened.slice(0, 5).map(r => `#${r.number} ${r.title.replace(/\s*\[bug:\w+\]$/, '').slice(0, 70)}`).join(' · ')}. When you fix one, also record why the first fix missed (kind=mistake).`)
  if (b && b.open.length) parts.push(`Open ideas, for when a task finishes and the person asks what's next (don't start them unasked): ${b.open.map(o => `${o.id} ${o.text.slice(0, 100)}`).join(' · ')}`)
  return parts.join('\n')
}

export function recallNote(ls: readonly Lesson[]): string {
  return `[learn] Lessons about this code (docs/LESSONS.md):\n${ls.map(l => `- ${l.id}${l.n > 1 ? ` ×${l.n}` : ''}${l.kind === 'missed' ? ' (open idea)' : ''}: ${l.text}`).join('\n')}`
}
export type At = { line: number; script: number; fns: { name: string; from: number; to: number; wrappedAfter: number[] }[] }
export function wrapNote(at: readonly At[], told: Set<string>): string {
  const out: string[] = []
  for (const a of at) for (const f of a.fns) {
    if (!f.wrappedAfter.length || told.has(f.name)) continue
    told.add(f.name)
    out.push(`- ${f.name} (lines ${f.from}-${f.to}) is wrapped by ${f.wrappedAfter.length} later script${f.wrappedAfter.length > 1 ? 's' : ''} (lines ${f.wrappedAfter.slice(0, 8).join(', ')}${f.wrappedAfter.length > 8 ? ', …' : ''}): they run around this code; \`node tools/where.mjs ${f.name}\` shows each.`)
  }
  return out.length ? `[learn] Code map (docs/MAP.md):\n${out.join('\n')}` : ''
}

// "⏱ About 25 min, so around 4:12 PM …" starts a timed estimate (tools/eta.mjs); "⏱ Taking longer: about 5 more min"
// adds to the open one. A finish time or "background" means the work outlives the turn.
export type Estimate = { kind: 'start'; min: number; bg: boolean; what: string } | { kind: 'more'; min: number }
export function estimateIn(text: string): Estimate | undefined {
  const toMin = (n: string, u: string) => Number(n) * (/^h/i.test(u) ? 60 : 1)
  const more = text.match(/⏱\s*\**\s*Taking longer:?\**\s*about\s+(\d+(?:\.\d+)?)\s*(?:more\s+)?(min|mins|minutes|h|hours?)\b/i)
  if (more) return { kind: 'more', min: toMin(more[1], more[2]) }
  const m = text.match(/⏱\s*\**\s*About\s+(\d+(?:\.\d+)?)(?:\s*[–-]\s*(\d+(?:\.\d+)?))?\s*(min|mins|minutes|h|hours?)\b([^\n]*)/i)
  if (!m) return undefined
  const line = m[4] ?? ''
  const what = line.replace(/^[\s*,.:;)–-]+/, '').replace(/^so (around|done by) [^,:.]+[,:.]?\s*/i, '').trim().slice(0, 140) || 'the task'
  return { kind: 'start', min: toMin(m[2] ?? m[1], m[3]), bg: /around \d{1,2}:\d\d|should be done|background/i.test(line), what }
}
