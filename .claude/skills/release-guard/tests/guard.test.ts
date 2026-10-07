import { describe, expect, mock, test } from 'claude-code/testing'

const NOW = Date.parse('2026-10-07T12:00:00Z')

const html = (v: string, entry = `{ v: ${v}, go: () => nxHome(), at: null, say: "New thing", tryIt: "Tap it" }`) =>
  `<script>\nconst m = t.match(/const APP_V = "([^"]+)"/);\nconst APP_V = "${v}";\nconst WHATS_NEW = [\n${entry}\n];\n</script>`

type World = {
  staged?: string
  porcelain?: string
  names?: string
  log?: string
  baseHtml?: string
  headHtml?: string
  commitDiff?: string
  runs?: unknown[]
}

// A fake repo, GitHub and tool runner beneath the plugin.
function world(on: any, w: World) {
  mock.store(on)
  const clock = mock.clock(on, { now: NOW })
  let head = 1
  const calls: any[] = []
  const contexts: string[] = []
  on('process.run', ($: unknown, e: any) => {
    const a = (e.argv as string[]).join(' ')
    const out = (stdout = '', exitCode = 0) => ({ value: { exitCode, stdout, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } })
    if (a === 'git rev-parse --show-toplevel') return out('/repo\n')
    if (a === 'git remote get-url origin') return out('http://proxy@127.0.0.1:1/git/shamlawy/Play-next\n')
    if (a === 'git rev-parse HEAD') return out(`h${head}\n`)
    if (a.startsWith('git diff --cached --name-status')) return out(w.staged ?? '')
    if (a.startsWith('git status --porcelain')) return out(w.porcelain ?? '')
    if (a.startsWith('git merge-base')) return out('base\n')
    if (a.startsWith('git diff --name-status -M base')) return out(w.names ?? '')
    if (a.startsWith('git diff -U0 base') && a.endsWith('docs/LOG.md')) return out(w.log ?? '')
    if (a === 'git show base:index.html') return out(w.baseHtml ?? '')
    if (/^git show \S+:index\.html$/.test(a)) return out(w.headHtml ?? '')
    if (a.startsWith('git show -U0')) return out(w.commitDiff ?? '')
    if (a.startsWith('node ')) return out('ok\n')
    return out('')
  })
  on('fs.read', ($: unknown, e: any) => ({ value: e.path.endsWith('LOG.md') ? '- v215: the tour left `pnClosing` set and a fill-forwards animation on `#full`.\n' : 'old\n' }))
  on('http.fetch', ($: unknown, e: any) => {
    const ok = (text: string) => ({ value: { status: 200, ok: true, headers: {}, text } })
    if (e.url.includes('/pulls/')) return ok(JSON.stringify({ head: { sha: 'feed', ref: 'claude/x' }, base: { ref: 'main' } }))
    if (e.url.includes('/actions/runs')) return ok(JSON.stringify({ workflow_runs: w.runs ?? [] }))
    if (e.url.includes('raw.githubusercontent.com')) return ok(w.headHtml ?? '')
    return { value: { status: 404, ok: false, headers: {}, text: '' } }
  })
  on('tool.call', ($: unknown, e: any) => {
    calls.push(e)
    if (e.tool === 'Bash' && /git commit/.test(e.command)) head++
    return { result: 'done', text: 'done' }
  })
  on('session.append', () => ({ value: { uuid: 'u' } }))
  on('prompt.submit', ($: unknown, e: any) => {
    contexts.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  on('classic.Stop', () => ({}))
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('turn.start', ($: unknown, e: any) => ({ turnId: e.turnId }))
  on('command.register', () => ({ value: undefined }))
  return { clock, calls, contexts }
}

const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })

describe('release-guard', () => {
  test('a commit with a backup .json is stopped', async ($, on) => {
    const w = world(on, { staged: 'A\tbackup-2026.json\nM\tindex.html\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git commit -m "x"' } as any)
    expect(r.deny).toContain('backup-2026.json')
    expect(w.calls.length).toBe(0)
  })

  test('a commit that deletes a file in the repo root is stopped, even through git add -A', async ($, on) => {
    world(on, { porcelain: ' D fefw-key-art.jpg\n M index.html\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git add -A && git commit -m "tidy"' } as any)
    expect(r.deny).toContain('fefw-key-art.jpg')
  })

  test('an ordinary commit goes through, with house rules on what it added', async ($, on) => {
    const w = world(on, { staged: 'M\tindex.html\n', commitDiff: '+.x { transition: width .3s; }\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git commit -m "x"' } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
    expect((r.context ?? []).join('\n')).toContain('transition on "width"')
  })

  test('a merge without a WHATS_NEW entry for the new version is stopped', async ($, on) => {
    const w = world(on, { names: 'M\tindex.html\nM\tdocs/LOG.md\n', log: '+- v249: new thing\n', baseHtml: html('248'), headHtml: html('249', '{ v: 248, go: 1, at: 1, say: 1, tryIt: 1 }') })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 7 } as any)
    expect(r.deny).toContain('no WHATS_NEW entry with v: 249')
    expect(w.calls.length).toBe(0)
  })

  test('a merge without a LOG line is stopped', async ($, on) => {
    world(on, { names: 'M\tindex.html\n', log: '', baseHtml: html('248'), headHtml: html('249') })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 7 } as any)
    expect(r.deny).toContain('docs/LOG.md')
  })

  test('a good merge goes through and says when it is live', async ($, on) => {
    const w = world(on, {
      names: 'M\tindex.html\nM\tdocs/LOG.md\n', log: '+- v249: new thing\n', baseHtml: html('248'), headHtml: html('249'),
      runs: [{ name: 'pages build and deployment', status: 'completed', conclusion: 'success', head_sha: 'feed', created_at: new Date(NOW + 60_000).toISOString() }],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 7 } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
    await w.clock.advance(95_000)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('v249 is live on GitHub Pages')
    await $.prompt.submit({ text: 'again' } as any)
    expect(w.contexts[1]).not.toContain('is live')
  })

  test('an index.html edit hears the house rules and the LOG history', async ($, on) => {
    world(on, {})
    await start($)
    const r: any = await $.tool.call({
      tool: 'Edit', file_path: '/repo/index.html',
      old_string: 'if (pnClosing) a.cancel();', new_string: 'if (pnClosing) a.cancel();\n.card:active { transform: scale(.97); }',
    } as any)
    const said = (r.context ?? []).join('\n')
    expect(said).toContain('no press animation')
    expect(said).toContain('pnClosing (v215)')
  })

  test('the turn cannot end untested after an index.html edit', async ($, on) => {
    world(on, {})
    await start($)
    await $.turn.start({ text: 'fix it', turnId: 't1' })
    await $.tool.call({ tool: 'Edit', file_path: '/repo/index.html', old_string: 'a', new_string: 'b' } as any)
    const r1: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r1.block).toContain('no browser test ran')
    await $.tool.call({ tool: 'Bash', command: 'node /tmp/x/scratchpad/sweep.mjs # playwright' } as any)
    const r2: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r2.block).toBeUndefined()
  })

  test('a turn that only reads index.html can end', async ($, on) => {
    world(on, {})
    await start($)
    await $.turn.start({ text: 'look', turnId: 't2' })
    await $.tool.call({ tool: 'Bash', command: "python3 -c \"print(open('index.html').read()[:10])\"" } as any)
    const r: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r.block).toBeUndefined()
  })
})
