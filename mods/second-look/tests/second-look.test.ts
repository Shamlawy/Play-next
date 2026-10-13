import { describe, expect, mock, test } from 'claude-code/testing'
import { parseFindings, readNotes } from '../hooks/findings.ts'

const BLOCK = JSON.stringify({ findings: [
  { severity: 'block', file: 'index.html:120', what: 'the new card uses a native date input, which the owner cannot use on the Fold', fix: 'use finPick()' },
  { severity: 'note', file: 'sw.js:4', what: 'cache name could be clearer', fix: '' },
] })

// A fake repo, GitHub and reviewer beneath the plugin.
function world(on: any, w: { diff?: string; answers?: string[]; head?: string; private?: boolean }) {
  mock.store(on)
  mock.clock(on, { now: 1_000 })
  const merged: unknown[] = []
  const asked: any[] = []
  const answers = [...(w.answers ?? [])]
  on('process.run', ($: unknown, e: any) => {
    const a = (e.argv as string[]).join(' ')
    const out = (stdout = '') => ({ value: { exitCode: 0, stdout, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } })
    if (a === 'git rev-parse --show-toplevel') return out('/repo\n')
    if (a === 'git remote get-url origin') return out('https://github.com/shamlawy/Play-next.git\n')
    if (a.startsWith('git merge-base')) return out('base\n')
    if (a.startsWith('git diff -U')) return out(a.includes(':(exclude)') ? '' : w.diff ?? '')
    if (a === 'git symbolic-ref --short refs/remotes/origin/HEAD') return out('origin/main\n')
    if (a === 'git rev-parse FETCH_HEAD') return out('beef\n')
    if (a === 'git rev-parse HEAD') return out(`${w.head ?? 'h1'}\n`)
    return out('')
  })
  on('fs.read', ($: unknown, e: any) => ({ value: e.path.endsWith('second-look.md') ? 'Two phones use this app.\nfirst: index.html\nskip: docs/MAP.md' : '# Play next rules\n- No press animation.' }))
  on('http.fetch', () => (w.private
    ? { value: { status: 404, ok: false, headers: {}, text: '' } }
    : { value: { status: 200, ok: true, headers: {}, text: JSON.stringify({ head: { sha: w.head ?? 'feed', ref: 'claude/x' }, base: { ref: 'main' } }) } }))
  on('model.complete', ($: unknown, e: any) => {
    asked.push(e)
    return { value: { isAnswered: true, text: answers.shift() ?? '{"findings":[]}', usage: { input_tokens: 1, output_tokens: 1, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } } }
  })
  on('tool.call', ($: unknown, e: any) => {
    merged.push(e)
    return { result: 'merged', text: 'merged' }
  })
  on('ui.toast', () => ({ value: undefined }))
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('command.register', () => ({ value: undefined }))
  return { merged, asked }
}
const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
const merge = ($: any) => $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'Shamlawy', repo: 'Play-next', pullNumber: 9 } as any) as Promise<any>

describe('second-look', () => {
  test('reads the reviewer answer, even with prose around it', () => {
    const f = parseFindings(`Here you go:\n${BLOCK}\nThanks`)
    expect(f?.length).toBe(2)
    expect(f?.[0]?.severity).toBe('block')
    expect(parseFindings('no json')).toBeUndefined()
  })

  test('a blocking finding holds the merge once, then the same commit goes through', async ($, on) => {
    const w = world(on, { diff: '+<input type="date">\n', answers: [BLOCK] })
    await start($)
    const r1 = await merge($)
    expect(r1.deny).toContain('native date input')
    expect(r1.deny).toContain('Also noted')
    expect(w.merged.length).toBe(0)
    expect(String(w.asked[0].prompt)).toContain('No press animation')
    expect(String(w.asked[0].prompt)).toContain('Two phones use this app')
    expect(String(w.asked[0].prompt)).not.toContain('first: index.html')
    expect(String(w.asked[0].prompt)).toContain('type="date"')
    const r2 = await merge($)
    expect(r2.deny).toBeUndefined()
    expect(w.merged.length).toBe(1)
    expect(w.asked.length).toBe(1) // reviewed once per commit
    expect((r2.context ?? []).join('\n')).toContain('Merged past these findings')
  })

  test('a clean review merges at once and passes the notes on', async ($, on) => {
    const w = world(on, { diff: '+let a = 1\n', answers: [JSON.stringify({ findings: [{ severity: 'note', file: 'a', what: 'small thing', fix: 'x' }] })] })
    await start($)
    const r = await merge($)
    expect(r.deny).toBeUndefined()
    expect(w.merged.length).toBe(1)
    expect((r.context ?? []).join('\n')).toContain('small thing')
  })

  test('an unreadable answer never blocks the release', async ($, on) => {
    const w = world(on, { diff: '+x\n', answers: ['sorry, I cannot'] })
    await start($)
    const r = await merge($)
    expect(r.deny).toBeUndefined()
    expect(w.merged.length).toBe(1)
    expect((r.context ?? []).join('\n')).toContain('could not run')
  })

  test('a private repo (the API says 404) is still reviewed, through pull/N/head', async ($, on) => {
    const w = world(on, { diff: '+x\n', answers: [BLOCK], private: true })
    await start($)
    const r = await merge($)
    expect(r.deny).toContain('second-look held this merge')
    expect(w.asked.length).toBe(1)
  })

  test('project notes: prose for the reviewer, first/skip path lines', () => {
    expect(readNotes('Be careful.\nfirst: a.js, src\nskip: docs/MAP.md')).toEqual({ text: 'Be careful.', first: ['a.js', 'src'], skip: ['docs/MAP.md'] })
  })

  test('a push to main is reviewed too; other pushes are not', async ($, on) => {
    const w = world(on, { diff: '+x\n', answers: [BLOCK] })
    await start($)
    const r0 = await $.tool.call({ tool: 'Bash', command: 'git push -u origin claude/x' } as any) as any
    expect(r0.deny).toBeUndefined()
    expect(w.asked.length).toBe(0)
    const r1 = await $.tool.call({ tool: 'Bash', command: 'git push origin HEAD:main' } as any) as any
    expect(r1.deny).toContain('second-look held this push to main')
  })

  test('a push to main from PowerShell (Windows) is reviewed the same way', async ($, on) => {
    const w = world(on, { diff: '+x\n', answers: [BLOCK] })
    await start($)
    const r0 = await $.tool.call({ tool: 'PowerShell', command: 'git push -u origin claude/x; Get-ChildItem' } as any) as any
    expect(r0.deny).toBeUndefined()
    expect(w.asked.length).toBe(0)
    const r1 = await $.tool.call({ tool: 'PowerShell', command: 'Set-Location C:\\code\\app; git push origin HEAD:main' } as any) as any
    expect(r1.deny).toContain('second-look held this push to main')
  })
})
