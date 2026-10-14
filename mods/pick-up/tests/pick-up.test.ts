import { describe, expect, mock, test } from 'claude-code/testing'

const DAY = 86_400_000
const NOW = 100 * DAY

type Saved = { sha: string; at: number; tree: string; body: string }
type W = {
  host?: string; cloud?: boolean; noRepo?: boolean; off?: boolean
  branch?: string; head?: string; tree?: string; headTree?: string; ahead?: string[]; dirty?: boolean; localBranch?: string
  remote?: Record<string, Saved>
}

// A fake git: the working copy (head / tree / what isn't pushed) and the pickup/* branches on GitHub.
function world(on: any, w: W) {
  mock.store(on)
  mock.clock(on, { now: NOW })
  const remote: Record<string, Saved> = w.remote ?? {}
  const ran: string[] = [], pushed: string[] = [], deleted: string[] = [], messages: string[] = [], contexts: string[] = [], toasts: string[] = []
  const out = (stdout = '', exitCode = 0, stderr = '') => ({ value: { exitCode, stdout, stderr, isStdoutTruncated: false, isStderrTruncated: false } })
  on('env.get', ($: unknown, e: any) => ({ value: e.name === 'CLAUDE_CODE_REMOTE' ? (w.cloud ? 'true' : undefined) : e.name === 'COMPUTERNAME' ? (w.cloud ? undefined : (w.host ?? 'PC1')) : undefined }))
  on('fs.exists', ($: unknown, e: any) => ({ value: !!w.off && String(e.path ?? e).endsWith('pick-up-off') }))
  on('session.cwd', () => ({ value: '/repo' }))
  on('process.run', ($: unknown, e: any) => {
    const argv = e.argv as string[], a = argv.join(' ')
    ran.push(a)
    if (a === 'git rev-parse --show-toplevel') return w.noRepo ? out('', 128, 'not a git repository') : out('/repo\n')
    if (a === 'git remote get-url origin') return out('https://github.com/o/r.git\n')
    if (a === 'git symbolic-ref --short HEAD') return out(`${w.branch ?? 'main'}\n`)
    if (a === 'git rev-parse HEAD') return out(`${w.head ?? 'h1'}\n`)
    if (a === 'git rev-parse --git-path pick-up-index') return out('.git/pick-up-index\n')
    if (a === 'git write-tree') return out(`${w.tree ?? 't1'}\n`)
    if (a === 'git rev-parse HEAD^{tree}') return out(`${w.headTree ?? 't0'}\n`)
    if (a.startsWith('git log --format=%s -n 20 HEAD --not')) return out((w.ahead ?? []).join('\n'))
    if (a.startsWith('git diff-tree -r --name-status HEAD')) return out('M\tindex.html\nA\tnotes.txt\n')
    if (a.startsWith('git commit-tree')) { messages.push(argv[argv.indexOf('-m') + 1]); return out('snap1\n') }
    if (a.startsWith('git push -q -f origin ')) {
      const [sha, ref] = argv[argv.length - 1].split(':')
      pushed.push(ref)
      remote[ref.replace('refs/heads/pickup/', '')] = { sha, at: NOW, tree: w.tree ?? 't1', body: messages[messages.length - 1] }
      return out()
    }
    if (a.startsWith('git push -q origin --delete ')) { const ref = argv[argv.length - 1]; deleted.push(ref); delete remote[ref.replace('pickup/', '')]; return out() }
    if (a.startsWith('git for-each-ref')) return out(Object.entries(remote).sort((x, y) => y[1].at - x[1].at)
      .map(([n, s]) => `refs/pickup/${n}\x1f${s.sha}\x1f${s.at / 1000}\x1f${s.tree}\x1f${s.body}\x1e`).join('\n'))
    if (a === 'git status --porcelain') return out(w.dirty ? ' M index.html\n' : '')
    if (a.startsWith('git rev-parse --verify -q refs/heads/')) return w.localBranch ? out(`${w.localBranch}\n`) : out('', 1)
    if (/^git rev-parse \w+\^$/.test(a)) return out('base1\n')
    if (a.startsWith('git merge-base --is-ancestor')) return out('', 1)
    return out()
  })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text ?? String(e)); return { value: undefined } })
  on('prompt.submit', ($: unknown, e: any) => { contexts.push((e.context ?? []).join('\n')); return { text: e.text, context: e.context } })
  on('turn.complete', () => ({ text: '' }))
  on('command.register', () => ({ value: { command: 'pickup' } }))
  on('tool.register', () => ({ value: { tool: 'mcp__pick-up__pickup' } }))
  return { ran, pushed, deleted, messages, contexts, toasts, remote }
}

const saved = (label: string, branch: string, at = NOW - 2 * 3_600_000, tree = 'tX'): Saved =>
  ({ sha: 'snapX', at, tree, body: `pick-up: ${label} · ${branch}\n\nplace: ${label}\nbranch: ${branch}\nfiles (2):\n  M index.html\n  A notes.txt\ncommits not on GitHub (1):\n  v281: half done\n` })

const start = async ($: any) => { await $.session.start({ cwd: '/repo', surface: null, isInteractive: true }); await $.prompt.submit({ text: 'hi' }) }
const turnEnd = ($: any) => $.turn.complete({ answer: 'ok', durationMs: 1, isAborted: false, turnId: 't', reason: 'answer' })

describe('pick-up', () => {
  test('a turn that leaves unfinished work saves it to GitHub once; committed and pushed removes it', async ($, on) => {
    const w: W = { tree: 't1', headTree: 't0', ahead: ['v281: half done'] }
    const f = world(on, w)
    await start($)
    await turnEnd($)
    expect(f.pushed).toEqual(['refs/heads/pickup/computer-pc1/main'])
    expect(f.messages[0]).toContain('place: the computer PC1')
    expect(f.messages[0]).toContain('  M index.html')
    expect(f.messages[0]).toContain('v281: half done')
    await turnEnd($)
    expect(f.pushed.length).toBe(1)   // nothing changed: not pushed again
    w.tree = 't0'; w.ahead = []
    await turnEnd($)
    expect(f.deleted).toEqual(['pickup/computer-pc1/main'])
  })

  test('a new chat elsewhere is told about it, and its own place is not offered', async ($, on) => {
    const f = world(on, { cloud: true, remote: { 'computer-pc1/main': saved('the computer PC1', 'main'), 'cloud/claude--old': saved('a cloud chat', 'claude/old') } })
    await start($)
    expect(f.contexts[0]).toContain('[pick-up]')
    expect(f.contexts[0]).toContain('the computer PC1, 2 h ago, branch main: 2 changed file(s) (index.html, notes.txt); 1 commit(s) not on GitHub ("v281: half done") [id: computer-pc1/main]')
    expect(f.contexts[0]).not.toContain('claude/old')
    expect(f.toasts[0]).toContain('say "pick up"')
  })

  test('the tool brings it in: branch, files as uncommitted changes, saved copy removed', async ($, on) => {
    const f = world(on, { cloud: true, remote: { 'computer-pc1/main': saved('the computer PC1', 'main') } })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__pick-up__pickup', id: 'computer-pc1/main' } as any)
    expect(f.ran).toContain('git switch -q -c main base1')
    expect(f.ran).toContain('git checkout snapX -- .')
    expect(f.ran).toContain('git reset -q')
    expect(f.deleted).toEqual(['pickup/computer-pc1/main'])
    expect(JSON.stringify(r)).toContain('now on branch main with 2 changed file(s)')
  })

  test('it refuses to bring work in over uncommitted changes', async ($, on) => {
    const f = world(on, { cloud: true, dirty: true, remote: { 'computer-pc1/main': saved('the computer PC1', 'main') } })
    await start($)
    const r: any = await $.command.run({ command: 'pickup', args: '' } as any)
    expect(JSON.stringify(r)).toContain('uncommitted changes of its own')
    expect(f.ran.some(a => a.startsWith('git switch'))).toBe(false)
  })

  test('back at the computer: work carried on elsewhere is set aside and the branch catches up', async ($, on) => {
    const w: W = { tree: 't1', headTree: 't0' }
    const f = world(on, w)
    await start($)
    await turnEnd($)                              // saved from this computer
    delete f.remote['computer-pc1/main']          // a cloud chat picked it up
    await start($)                                // the next chat here
    expect(f.ran).toContain('git stash push -u -q -m pick-up: carried on in another chat')
    expect(f.ran).toContain('git merge --ff-only -q @{upstream}')
    expect(f.contexts[1]).toContain('set aside here')
  })

  test('old copies are not offered and very old ones are deleted', async ($, on) => {
    const f = world(on, { cloud: true, remote: { 'computer-pc1/a': saved('the computer PC1', 'a', NOW - 20 * DAY), 'computer-pc1/b': saved('the computer PC1', 'b', NOW - 40 * DAY) } })
    await start($)
    expect(f.contexts[0]).toBe('')
    expect(f.deleted).toEqual(['pickup/computer-pc1/b'])
  })

  test('outside a git repo, or switched off, it does nothing', async ($, on) => {
    const f = world(on, { noRepo: true })
    await start($)
    await turnEnd($)
    expect(f.pushed).toEqual([])
    expect(f.ran.every(a => a === 'git rev-parse --show-toplevel')).toBe(true)
  })

  test('a repo with .claude/pick-up-off is left alone', async ($, on) => {
    const f = world(on, { off: true, tree: 't1', headTree: 't0' })
    await start($)
    await turnEnd($)
    expect(f.pushed).toEqual([])
  })
})
