import type { EngineInterface, Register } from 'claude-code'

type $ = EngineInterface

// A copy of the repo on a computer falls behind while cloud chats and the daily fixer change GitHub.
// At chat start: fetch, and when the current branch is only behind (nothing of its own on top), fast-forward it.
// Never merges, rebases or touches uncommitted work: anything else is only reported. A fresh cloud copy is already current.

let note = ''
// The pull started at chat start: the first message waits for it (capped), so files don't change under Claude's first turn.
let pending: Promise<void> | undefined

async function pull($: $, cwd: string): Promise<string> {
  const git = (args: string[], timeoutMs = 20_000) => $.process.run(['git', ...args], { cwd, timeoutMs })
  const top = await git(['rev-parse', '--show-toplevel']).catch(() => undefined)
  if (!top || top.exitCode !== 0) return ''
  const branch = (await git(['symbolic-ref', '--short', 'HEAD'])).stdout.trim()
  if (!branch) return ''
  const up = await git(['rev-parse', '--abbrev-ref', `${branch}@{upstream}`])
  const remote = up.exitCode === 0 ? up.stdout.trim() : `origin/${branch}`
  const fetched = await git(['fetch', '-q', 'origin'], 60_000)
  if (fetched.exitCode !== 0) return `auto-pull: couldn't reach GitHub (${fetched.stderr.trim().split('\n')[0] || 'fetch failed'}); this copy may be behind.`
  const count = async (range: string) => {
    const r = await git(['rev-list', '--count', range])
    return r.exitCode === 0 ? Number(r.stdout.trim()) || 0 : -1
  }
  const behind = await count(`HEAD..${remote}`)
  if (behind <= 0) return ''
  const ahead = await count(`${remote}..HEAD`)
  if (ahead > 0) return `auto-pull: ${branch} is ${behind} commit(s) behind ${remote} and has ${ahead} of its own; not pulled. Merge ${remote} before working on it.`
  const ff = await git(['merge', '--ff-only', '-q', remote], 60_000)
  if (ff.exitCode !== 0) return `auto-pull: ${branch} is ${behind} commit(s) behind ${remote} but couldn't fast-forward (uncommitted edits in the way?): ${ff.stderr.trim().split('\n')[0] ?? ''}`
  const log = (await git(['log', '--format=%s', '-n', '5', `HEAD~${Math.min(behind, 5)}..HEAD`])).stdout.trim().split('\n').filter(Boolean)
  return `auto-pull: brought ${branch} up to date with GitHub (${behind} new commit(s)). Newest: ${log.map(l => `"${l.slice(0, 80)}"`).join('; ')}`
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const r = await next(e)
    pending = pull($, e.cwd).then(text => {
      if (!text) return
      note = text
      $.ui.toast(text.replace(/^auto-pull: /, ''), { timeoutMs: 10_000 })
    }, () => undefined)
    return r
  })

  // The first message after the pull carries what came in, so Claude works from the new code knowingly.
  on('prompt.submit', async ($, e, next) => {
    if (pending) {
      const wait = pending
      pending = undefined
      await Promise.race([wait, $.clock.sleep(30_000)])
    }
    if (!note) return next(e)
    const text = note
    note = ''
    return next({ ...e, context: [...(e.context ?? []), text] })
  })
}
