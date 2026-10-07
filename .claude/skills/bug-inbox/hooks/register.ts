import type { EngineInterface, Register } from 'claude-code'
import { firstNote, listing, LOUD, line, parse, status, type Issue, type Report } from './inbox.ts'

type $ = EngineInterface

const REFRESH = 30 * 60_000
// With nothing remembered (a fresh cloud container), "new" means filed in the last two days.
const RECENT = 48 * 3_600_000

// Module state: a reload starts it over.
let repo = 'shamlawy/Play-next'
let reports: Report[] | undefined
let told = false
const pending: string[] = []

async function load($: $): Promise<Report[] | undefined> {
  const out: Report[] = []
  for (let page = 1; page <= 3; page++) {
    const res = await $.http.fetch(`https://api.github.com/repos/${repo}/issues?labels=auto-bug&state=open&per_page=100&page=${page}`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-bug-inbox' },
    })
    if (!res.ok) return page === 1 ? undefined : out
    const got = JSON.parse(res.text) as Issue[]
    for (const i of got) {
      const r = parse(i)
      if (r) out.push(r)
    }
    if (got.length < 100) break
  }
  return out
}

// Reports nobody here has heard of yet; remembers the rest.
async function fresh($: $, rs: readonly Report[]): Promise<Report[]> {
  const seen = (await $.store.get('seen')) as number[] | undefined
  const now = await $.clock.now()
  const news = seen ? rs.filter(r => !seen.includes(r.n)) : rs.filter(r => now - Date.parse(r.created) < RECENT)
  await $.store.set('seen', rs.map(r => r.n))
  return news
}

async function refresh($: $) {
  try {
    const rs = await load($)
    if (!rs) return
    const news = (await fresh($, rs)).filter(r => LOUD.has(r.kind))
    reports = rs
    $.ui.status(status(rs))
    if (news.length) {
      const text = `New problem report${news.length > 1 ? 's' : ''}: ${news.slice(0, 3).map(line).join(' · ')}`
      $.ui.toast(text, { timeoutMs: 12_000 })
      pending.push(text)
    }
  } catch {
    // GitHub unreachable or rate-limited: try again next time.
  }
}

export const register: Register = on => {
  let news: Report[] = []

  on('session.start', async ($, e, next) => {
    try {
      const url = (await $.process.run(['git', 'remote', 'get-url', 'origin'], { cwd: e.cwd })).stdout.trim()
      const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
      if (m) repo = `${m[1]}/${m[2]}`
      const rs = await load($)
      if (rs) {
        reports = rs
        news = await fresh($, rs)
        $.ui.status(status(rs))
      }
    } catch {
      // No inbox this chat; nothing else depends on it.
    }
    $.clock.every(REFRESH, () => void refresh($))
    await $.command.register({ name: 'inbox', description: 'List the open problem reports (auto-bug issues), hand-filed ones first.' })
    return next(e)
  })

  on('command.run', { command: 'inbox' }, async $ => {
    const rs = (await load($).catch(() => undefined)) ?? reports
    if (!rs) return { text: "Couldn't reach GitHub for the problem reports just now." }
    reports = rs
    $.ui.status(status(rs))
    return { text: listing(rs) }
  })

  // The first message of a chat knows what's open; later ones hear only of new hand-filed reports.
  on('prompt.submit', ($, e, next) => {
    const notes: string[] = []
    if (!told && reports) {
      told = true
      notes.push(firstNote(reports, news))
    }
    if (pending.length) notes.push(`[bug-inbox] Arrived while you were working: ${pending.splice(0).join(' ')} Tell the person in one short line.`)
    return notes.length ? next({ ...e, context: [...(e.context ?? []), notes.join('\n')] }) : next(e)
  })
}
