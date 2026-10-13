import { describe, expect, mock, test } from 'claude-code/testing'

// A fake git: `behind` / `ahead` commits against origin/main, and whether the fast-forward works.
function world(on: any, w: { behind?: number; ahead?: number; ffFails?: boolean; fetchFails?: boolean; noRepo?: boolean }) {
  mock.store(on)
  mock.clock(on, { now: 0 })
  const ran: string[] = []
  const contexts: string[] = []
  const toasts: string[] = []
  on('process.run', ($: unknown, e: any) => {
    const a = (e.argv as string[]).join(' ')
    ran.push(a)
    const out = (stdout = '', exitCode = 0, stderr = '') => ({ value: { exitCode, stdout, stderr, isStdoutTruncated: false, isStderrTruncated: false } })
    if (a === 'git rev-parse --show-toplevel') return w.noRepo ? out('', 128, 'not a git repository') : out('/repo\n')
    if (a === 'git symbolic-ref --short HEAD') return out('main\n')
    if (a === 'git rev-parse --abbrev-ref main@{upstream}') return out('origin/main\n')
    if (a === 'git fetch -q origin') return w.fetchFails ? out('', 128, 'Could not resolve host: github.com') : out()
    if (a === 'git rev-list --count HEAD..origin/main') return out(`${w.behind ?? 0}\n`)
    if (a === 'git rev-list --count origin/main..HEAD') return out(`${w.ahead ?? 0}\n`)
    if (a === 'git merge --ff-only -q origin/main') return w.ffFails ? out('', 1, 'error: Your local changes would be overwritten') : out()
    if (a.startsWith('git log')) return out('v276: new thing\nfix a bug\n')
    return out()
  })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text ?? String(e)); return { value: undefined } })
  on('prompt.submit', ($: unknown, e: any) => {
    contexts.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  return { ran, contexts, toasts }
}

const start = async ($: any) => {
  await $.session.start({ cwd: '/repo', surface: null, isInteractive: true })
  for (let i = 0; i < 20; i++) await Promise.resolve()
  await new Promise(r => setTimeout(r, 20))
}

describe('auto-pull', () => {
  test('a copy that is behind is fast-forwarded and Claude is told once', async ($, on) => {
    const w = world(on, { behind: 2 })
    await start($)
    expect(w.ran).toContain('git merge --ff-only -q origin/main')
    await $.prompt.submit({ text: 'hi' })
    await $.prompt.submit({ text: 'again' })
    expect(w.contexts[0]).toContain('2 new commit(s)')
    expect(w.contexts[0]).toContain('v276: new thing')
    expect(w.contexts[1]).toBe('')
  })

  test('up to date: nothing is run past the check, nothing is said', async ($, on) => {
    const w = world(on, { behind: 0 })
    await start($)
    expect(w.ran.some(a => a.startsWith('git merge'))).toBe(false)
    await $.prompt.submit({ text: 'hi' })
    expect(w.contexts[0]).toBe('')
  })

  test('own commits on top: never merged, only reported', async ($, on) => {
    const w = world(on, { behind: 3, ahead: 1 })
    await start($)
    expect(w.ran.some(a => a.startsWith('git merge'))).toBe(false)
    await $.prompt.submit({ text: 'hi' })
    expect(w.contexts[0]).toContain('has 1 of its own; not pulled')
  })

  test('a failed fast-forward or no network is reported, not forced', async ($, on) => {
    const w = world(on, { behind: 1, ffFails: true })
    await start($)
    await $.prompt.submit({ text: 'hi' })
    expect(w.contexts[0]).toContain("couldn't fast-forward")
  })

  test('offline: says the copy may be behind', async ($, on) => {
    const w = world(on, { fetchFails: true })
    await start($)
    await $.prompt.submit({ text: 'hi' })
    expect(w.contexts[0]).toContain("couldn't reach GitHub")
  })

  test('outside a git repo it does nothing', async ($, on) => {
    const w = world(on, { noRepo: true })
    await start($)
    expect(w.ran).toEqual(['git rev-parse --show-toplevel'])
  })

  test('the first message waits for a pull still running', async ($, on) => {
    const w = world(on, { behind: 1 })
    await $.session.start({ cwd: '/repo', surface: null, isInteractive: true })
    await $.prompt.submit({ text: 'right away' })
    expect(w.ran).toContain('git merge --ff-only -q origin/main')
    expect(w.contexts[0]).toContain('1 new commit(s)')
  })
})
