import { describe, expect, mock, test } from 'claude-code/testing'
import { protectPatterns, risks, sortFiles } from '../hooks/blast.ts'

// A fake repo at /repo: what git and the file walk answer.
function world(on: any, w: { files?: string; tracked?: string; fresh?: string; diff?: string; ahead?: string; branch?: string; protect?: string }) {
  mock.store(on)
  mock.clock(on, { now: 0 })
  const ran: string[] = []
  on('process.run', ($: unknown, e: any) => {
    const a = (e.argv as string[]).join(' ')
    const out = (stdout = '') => ({ value: { exitCode: 0, stdout, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } })
    if (a === 'git rev-parse --show-toplevel') return out('/repo\n')
    if (a === 'git remote get-url origin') return out('https://github.com/shamlawy/Play-next.git\n')
    if (a.startsWith('bash -c')) return out(w.files ?? '')
    if (a === 'git ls-files') return out(w.tracked ?? '')
    if (a === 'git ls-files -o --exclude-standard') return out(w.fresh ?? '')
    if (a === 'git diff --name-only HEAD' || a.startsWith('git diff --name-only --')) return out(w.diff ?? '')
    if (a.startsWith('git log --oneline origin/')) return out(w.ahead ?? '')
    if (a === 'git rev-parse --abbrev-ref HEAD') return out(w.branch ?? 'claude/x\n')
    if (a === 'git symbolic-ref --short refs/remotes/origin/HEAD') return out('origin/main\n')
    if (a.startsWith('sh -c')) return out('/root')
    return out('')
  })
  on('fs.read', ($: unknown, e: any) => (e.path.endsWith('blast-radius-protect') && w.protect ? { value: w.protect } : { value: '' }))
  on('tool.call', ($: unknown, e: any) => {
    ran.push(e.command)
    return { result: 'done', text: 'done' }
  })
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  return ran
}
const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })
const bash = ($: any, command: string) => $.tool.call({ tool: 'Bash', command } as any) as Promise<any>

describe('blast-radius', () => {
  test('finds the destructive parts of a command', () => {
    const r = risks('cd tools && rm -rf scenarios/old "my dir" ; git reset --hard origin/main && git push -f origin HEAD:claude/x', '/repo')
    expect(r.map(x => x.kind)).toEqual(['rm', 'reset', 'force'])
    expect(r[0]).toEqual({ kind: 'rm', cwd: '/repo/tools', targets: ['scenarios/old', 'my dir'] })
    expect(r[2]).toEqual({ kind: 'force', cwd: '/repo/tools', src: 'HEAD', dst: 'claude/x' })
    expect(risks('git push -u origin claude/x && git checkout -b y && git restore --staged a.txt', '/repo')).toEqual([])
  })

  test('sorts deleted files by how much they matter', () => {
    const s = sortFiles(['/repo/a.jpg', '/repo/tools/x.mjs', '/repo/new.txt', '/repo/backup.json', '/repo/node_modules/k/i.js', '/tmp/z', '/root/.claude/s'], '/repo',
      new Set(['a.jpg', 'tools/x.mjs']), new Set(['a.jpg', 'tools/x.mjs', 'new.txt']), protectPatterns('*.jpg'))
    expect(s.protected).toEqual(['a.jpg'])
    expect(s.tracked).toEqual(['tools/x.mjs'])
    expect(s.fresh).toEqual(['new.txt'])
    expect(s.ignored).toEqual(['backup.json'])
    expect(s.outside).toEqual(['/root/.claude/s'])
    expect(sortFiles(['/repo'], '/repo', new Set(), new Set()).whole).toBe(true)
  })

  test('rm of a folder with new files is held once with the list, then goes through', async ($, on) => {
    const ran = world(on, { files: 'tools/scenarios/a.mjs\ntools/scenarios/b.mjs\n', tracked: 'tools/scenarios/a.mjs\n', fresh: 'tools/scenarios/b.mjs\n' })
    await start($)
    const r1 = await bash($, 'rm -rf tools/scenarios')
    expect(r1.deny).toContain('not in git yet')
    expect(r1.deny).toContain('tools/scenarios/b.mjs')
    expect(ran.length).toBe(0)
    const r2 = await bash($, 'rm -rf tools/scenarios')
    expect(r2.deny).toBeUndefined()
    expect(ran.length).toBe(1)
  })

  test('a file the repo protects is always stopped; elsewhere a root file is only held', async ($, on) => {
    const ran = world(on, { files: 'fefw-key-art.jpg\n', tracked: 'fefw-key-art.jpg\n', protect: '# saved games link to these\n*\n' })
    await start($)
    expect((await bash($, 'rm fefw-key-art.jpg')).deny).toContain('protects')
    expect((await bash($, 'rm fefw-key-art.jpg')).deny).toContain('protects')
    expect(ran.length).toBe(0)
  })

  test('throwaway files in /tmp go straight through', async ($, on) => {
    const ran = world(on, { files: '/tmp/x/a.png\n' })
    await start($)
    expect((await bash($, 'rm -rf /tmp/x')).deny).toBeUndefined()
    expect(ran.length).toBe(1)
  })

  test('reset --hard over uncommitted work is held; force-push to main is stopped', async ($, on) => {
    world(on, { diff: 'index.html\n' })
    await start($)
    expect((await bash($, 'git reset --hard')).deny).toContain('index.html')
    expect((await bash($, 'git push --force origin HEAD:main')).deny).toContain('never rewrite it')
    expect((await bash($, 'BLAST_OK=1 git reset --hard')).deny).toBeUndefined()
  })

  test('a force-push that would drop commits from GitHub is held', async ($, on) => {
    world(on, { ahead: 'abc123 someone else\'s fix\n' })
    await start($)
    expect((await bash($, 'git push --force-with-lease origin claude/x')).deny).toContain('removes 1 commit')
  })
})
