import { describe, expect, mock, test } from 'claude-code/testing'
import { androidWhatsNew, checkState, ghMerge, releaseProblems, repoPath } from '../hooks/rules.ts'

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
  mainHtml?: string
  commitDiff?: string
  runs?: unknown[]
  // The Android app: android/version.properties at base / head / origin/main, whatsnew.txt at head (undefined = no such file)
  baseAndroid?: string
  headAndroid?: string
  mainAndroid?: string
  whatsnew?: string
  // android-check.yml's runs on the head commit; 404 = GitHub doesn't know the workflow by name yet
  checkRuns?: unknown[]
  check404?: boolean
  // what `gh pr view --json …` answers (undefined = gh finds no PR)
  ghView?: unknown
  // false: a repo that doesn't carry the Play next mods (another project)
  carries?: boolean
}

// A fake repo, GitHub and tool runner beneath the plugin.
function world(on: any, w: World) {
  mock.store(on)
  // The repo carries the mod (.claude/skills/<name>), so it is on.
  on('fs.exists', () => ({ value: w.carries ?? true }))
  const clock = mock.clock(on, { now: NOW })
  let head = 1
  const calls: any[] = []
  const contexts: string[] = []
  const toasts: string[] = []
  const procs: string[] = []
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
    if (a === 'git show origin/main:index.html') return out(w.mainHtml ?? w.baseHtml ?? '')
    if (/^git show \S+:index\.html$/.test(a)) return out(w.headHtml ?? '')
    if (a.startsWith('git show -U0')) return out(w.commitDiff ?? '')
    const file = (v: string | undefined) => (v === undefined ? out('', 128) : out(v))
    if (a === 'git show base:android/version.properties') return file(w.baseAndroid)
    if (a === 'git show origin/main:android/version.properties') return file(w.mainAndroid ?? w.baseAndroid)
    if (/^git show \S+:android\/version\.properties$/.test(a)) return file(w.headAndroid)
    if (/^git show \S+:android\/app\/src\/main\/assets\/whatsnew\.txt$/.test(a)) return file(w.whatsnew)
    if (a.startsWith('gh pr view')) { procs.push(a); return w.ghView === undefined ? out('', 1) : out(JSON.stringify(w.ghView)) }
    if (a.startsWith('node ')) return out('ok\n')
    return out('')
  })
  on('fs.read', ($: unknown, e: any) => ({ value: e.path.endsWith('LOG.md') ? '- v215: the tour left `pnClosing` set and a fill-forwards animation on `#full`.\n' : 'old\n' }))
  on('http.fetch', ($: unknown, e: any) => {
    const ok = (text: string) => ({ value: { status: 200, ok: true, headers: {}, text } })
    if (e.url.includes('/actions/workflows/android-check.yml/runs')) return w.check404 ? { value: { status: 404, ok: false, headers: {}, text: '' } } : ok(JSON.stringify({ workflow_runs: w.checkRuns ?? [] }))
    if (e.url.includes('/actions/runs?head_sha=')) return ok(JSON.stringify({ workflow_runs: w.checkRuns ?? [] }))
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
  on('session.append', ($: unknown, e: any, next: any) => next(e))
  on('ui.status', () => ({ value: undefined }))
  on('ui.toast', ($: unknown, e: any) => { toasts.push(e.text); return { value: undefined } })
  on('prompt.submit', ($: unknown, e: any) => {
    contexts.push((e.context ?? []).join('\n'))
    return { text: e.text, context: e.context }
  })
  on('classic.Stop', () => ({}))
  on('session.start', ($: unknown, e: any) => ({ cwd: e.cwd }))
  on('turn.start', ($: unknown, e: any) => ({ turnId: e.turnId }))
  on('command.register', () => ({ value: undefined }))
  return { clock, calls, contexts, toasts, procs }
}

const start = ($: any) => $.session.start({ cwd: '/repo', surface: null, isInteractive: false })

describe('release-guard', () => {
  test('in a project without the Play next mods it stays out of the way and says so once', async ($, on) => {
    const w = world(on, { carries: false, staged: 'A\tbackup-2026.json\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git commit -m "x"' } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
    expect(w.toasts.some(t => /Play next mods are off in this project/.test(t))).toBe(true)
    await $.prompt.submit({ text: 'hi' } as any)
    await $.prompt.submit({ text: 'again' } as any)
    expect(w.contexts[0]).toContain('off here')
    expect(w.contexts[1]).not.toContain('off here')
    // A later chat in the same project hears nothing more.
    w.toasts.length = 0
    await start($)
    await $.prompt.submit({ text: 'new chat' } as any)
    expect(w.toasts.length).toBe(0)
    expect(w.contexts[2]).not.toContain('off here')
  })

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

  test('a merge whose version main already shipped meanwhile is stopped', async ($, on) => {
    world(on, { names: 'M\tindex.html\nM\tdocs/LOG.md\n', log: '+- v249: new thing\n', baseHtml: html('248'), headHtml: html('249'), mainHtml: html('249') })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 7 } as any)
    expect(r.deny).toContain('main already shipped v249')
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

  test('the committed test kit counts as a browser test', async ($, on) => {
    world(on, {})
    await start($)
    await $.turn.start({ text: 'fix it', turnId: 't3' })
    await $.tool.call({ tool: 'Edit', file_path: '/repo/index.html', old_string: 'a', new_string: 'b' } as any)
    const r1: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r1.block).toContain('node tools/kit.mjs sweep')
    await $.tool.call({ tool: 'Bash', command: 'node tools/kit.mjs sweep --widths 412,859,360' } as any)
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

  // ---- The Android app (android/) ----

  test('keystores, key.properties, local.properties and google-services.json are never committed', async ($, on) => {
    const w = world(on, { staged: 'A\tandroid/app/release.jks\nA\tandroid/key.properties\nA\tandroid/local.properties\nA\tandroid/app/google-services.json\nA\tsigning/x.p12\nA\tx.keystore\nM\tandroid/app/build.gradle.kts\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git commit -m "x"' } as any)
    for (const f of ['release.jks', 'key.properties', 'local.properties', 'google-services.json', 'x.p12', 'x.keystore']) expect(r.deny).toContain(f)
    expect(r.deny).not.toContain('build.gradle.kts')
    expect(r.deny).not.toContain('google-services.json: .json files') // one reason per file
    expect(w.calls.length).toBe(0)
    // A force-add of an ignored keystore is read from the command itself.
    const f: any = await $.tool.call({ tool: 'Bash', command: 'git add -f android/app/upload.jks && git commit -m "x"' } as any)
    expect(f.deny).toContain('keystore')
    // The same commit through PowerShell (the PC) is stopped too.
    const p: any = await $.tool.call({ tool: 'PowerShell', command: 'git commit -m "x"' } as any)
    expect(p.deny).toContain('release.jks')
  })

  test('an Android release merge with everything in place goes through and says when android-vN is released', async ($, on) => {
    const w = world(on, {
      names: 'M\tandroid/app/src/main/java/io/x/Probe.kt\nM\tandroid/version.properties\nM\tandroid/app/src/main/assets/whatsnew.txt\nM\tdocs/LOG.md\n',
      log: '+- android-v4: the probe screen\n', baseAndroid: 'release=3\n', headAndroid: '# counter\nrelease=4\n',
      whatsnew: '# Android what\'s new\nv=3\ngo=home\nat=\nsay=Old\ntryIt=Old\n\nv=4\ngo=probe\nat=\nsay=A 120 Hz check\ntryIt=Open the probe\n',
      checkRuns: [{ status: 'completed', conclusion: 'success', head_sha: 'feed', created_at: new Date(NOW - 600_000).toISOString() }],
      runs: [
        { name: 'pages build and deployment', status: 'completed', conclusion: 'success', head_sha: 'm1', created_at: new Date(NOW + 60_000).toISOString() },
        { name: 'Android release', path: '.github/workflows/android-release.yml', status: 'completed', conclusion: 'success', head_sha: 'm1', created_at: new Date(NOW + 60_000).toISOString() },
      ],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
    await w.clock.advance(95_000)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('android-v4 is released (the phone updates itself)')
    // An Android-only merge changes nothing the web app serves: no Pages news.
    expect(w.contexts[0]).not.toContain('GitHub Pages')
  })

  test('an Android release without its what\'s-new block, LOG line or a finished android-check is stopped', async ($, on) => {
    const w = world(on, {
      names: 'M\tandroid/app/src/main/java/io/x/Probe.kt\nM\tandroid/version.properties\n',
      log: '', baseAndroid: 'release=3\n', headAndroid: 'release=4\n', whatsnew: 'v=3\ngo=home\nsay=Old\ntryIt=Old\n',
      checkRuns: [{ status: 'in_progress', conclusion: null, head_sha: 'feed', created_at: new Date(NOW).toISOString() }],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    expect(r.deny).toContain("it would ship to the owner's phone")
    expect(r.deny).toContain('whatsnew.txt has no block with v=4')
    expect(r.deny).toContain('docs/LOG.md has no "android-v4" line')
    expect(r.deny).toContain('wait for android-check, then merge')
    expect(w.calls.length).toBe(0)
  })

  test('android-check missing, failed or unreadable stops an Android release; a passed re-run counts', async ($, on) => {
    const good = { names: 'M\tandroid/version.properties\nM\tandroid/app/src/main/assets/whatsnew.txt\nM\tdocs/LOG.md\n', log: '+- android-v4: x\n', baseAndroid: 'release=3\n', headAndroid: 'release=4\n', whatsnew: 'v=4\ngo=home\nat=\nsay=x\ntryIt=y\n' }
    world(on, { ...good, checkRuns: [] })
    await start($)
    const missing: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    expect(missing.deny).toContain("android-check hasn't run on this commit yet: wait for android-check, then merge")
    // The pure rule for the other states.
    const range = { changes: [{ status: 'M', path: 'android/version.properties' }], logAdded: '- android-v4: x', baseAndroid: 'release=3', headAndroid: 'release=4', androidWhatsNew: good.whatsnew }
    expect(releaseProblems({ ...range, androidCheck: { state: 'failed', conclusion: 'failure' } }).block.join('\n')).toContain('android-check failure on this commit')
    expect(releaseProblems({ ...range, androidCheck: { state: 'unknown' } }).block.join('\n')).toContain('wait for android-check, then merge')
    expect(releaseProblems({ ...range, androidCheck: { state: 'success' } }).block).toEqual([])
    // A re-run that passed replaces the older failure.
    expect(checkState([
      { status: 'completed', conclusion: 'failure', head_sha: 'a', created_at: '2026-10-07T10:00:00Z' },
      { status: 'completed', conclusion: 'success', head_sha: 'a', created_at: '2026-10-07T11:00:00Z' },
    ], 'a').state).toBe('success')
  })

  test('android-check found through the commit\'s runs when the workflow is new', async ($, on) => {
    const w = world(on, {
      names: 'M\tandroid/version.properties\nM\tandroid/app/src/main/assets/whatsnew.txt\nM\tdocs/LOG.md\n', log: '+- android-v1: first\n',
      headAndroid: 'release=1\n', whatsnew: 'v=1\ngo=probe\nat=\nsay=x\ntryIt=y\n', check404: true,
      checkRuns: [
        { path: '.github/workflows/app-check.yml', status: 'in_progress', head_sha: 'feed', created_at: new Date(NOW).toISOString() },
        { path: '.github/workflows/android-check.yml', status: 'completed', conclusion: 'success', head_sha: 'feed', created_at: new Date(NOW).toISOString() },
      ],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
  })

  test('an Android release must stay above main\'s and the counter never goes down', async ($, on) => {
    world(on, { names: 'M\tandroid/version.properties\n', log: '+- android-v4: x\n', baseAndroid: 'release=3\n', headAndroid: 'release=4\n', mainAndroid: 'release=4\n', whatsnew: 'v=4\ngo=home\nsay=x\ntryIt=y\n' })
    await start($)
    const r: any = await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    expect(r.deny).toContain('main already made android-v4')
    const down = releaseProblems({ changes: [{ status: 'M', path: 'android/version.properties' }], logAdded: '', baseAndroid: 'release=5', headAndroid: 'release=4' })
    expect(down.block.join('\n')).toContain('it never goes down')
    const none = releaseProblems({ changes: [{ status: 'A', path: 'android/app/build.gradle.kts' }], logAdded: '' })
    expect(none.block.join('\n')).toContain('has no "release=N" line')
  })

  test('a build-only Android change is a note, and android/parity/ docs are not the app', async ($, on) => {
    world(on, { names: 'M\tandroid/app/build.gradle.kts\n', baseAndroid: 'release=4\n', headAndroid: 'release=4\n', checkRuns: [] })
    await start($)
    const r: any = await $.command.run({ command: 'release-check', args: '' } as any)
    expect(r.text).toContain('Ready to merge')
    expect(r.text).toContain('no Android release (release=4 unchanged)')
    const docs = releaseProblems({ changes: [{ status: 'M', path: 'android/parity/0.md' }], logAdded: '' })
    expect(docs).toEqual({ block: [], notes: [] })
  })

  test('whatsnew.txt: comment lines, blank-line blocks, go/say/tryIt required, at may be empty', () => {
    const t = '# v=9 in a comment is not a block\r\nv=9\r\ngo=queue\r\nat=\r\nsay=Hi\r\ntryIt=\r\n\r\nv=9\r\ngo=home\r\nsay=Two\r\ntryIt=Tap\r\n'
    expect(androidWhatsNew(t, '9')).toEqual({ count: 2, missing: ['block 1 lacks tryIt'] })
    expect(androidWhatsNew(t, '8').count).toBe(0)
  })

  test('gh pr merge (Bash and PowerShell) gets the same check as the GitHub merge tool', async ($, on) => {
    const w = world(on, { names: 'M\tindex.html\nM\tdocs/LOG.md\n', log: '+- v249: new thing\n', baseHtml: html('248'), headHtml: html('249', '{ v: 248, go: 1, at: 1, say: 1, tryIt: 1 }') })
    await start($)
    const b: any = await $.tool.call({ tool: 'Bash', command: 'gh pr merge 7 --squash --delete-branch' } as any)
    expect(b.deny).toContain('no WHATS_NEW entry with v: 249')
    const p: any = await $.tool.call({ tool: 'PowerShell', command: 'gh pr merge https://github.com/Shamlawy/Play-next/pull/7 --squash --delete-branch; if ($?) { git pull }' } as any)
    expect(p.deny).toContain('no WHATS_NEW entry with v: 249')
    expect(w.calls.length).toBe(0)
    // RG_SKIP=1 in front (or $env:RG_SKIP=1 in PowerShell) lets it through on purpose.
    await $.tool.call({ tool: 'Bash', command: 'RG_SKIP=1 gh pr merge 7 --squash' } as any)
    await $.tool.call({ tool: 'PowerShell', command: '$env:RG_SKIP=1; gh pr merge 7 --squash' } as any)
    expect(w.calls.length).toBe(2)
  })

  test('gh pr merge with no number asks gh for the current branch\'s PR, and a good one is watched', async ($, on) => {
    const w = world(on, {
      names: 'M\tindex.html\nM\tdocs/LOG.md\n', log: '+- v249: new thing\n', baseHtml: html('248'), headHtml: html('249'),
      ghView: { number: 12, headRefOid: 'beef', headRefName: 'claude/y', baseRefName: 'main' },
      runs: [{ name: 'pages build and deployment', status: 'completed', conclusion: 'success', head_sha: 'beef', created_at: new Date(NOW + 60_000).toISOString() }],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'PowerShell', command: 'gh pr merge --squash --delete-branch -t "Fix 12 things"' } as any)
    expect(r.deny).toBeUndefined()
    expect(w.procs[0]).toBe('gh pr view --json number,headRefOid,headRefName,baseRefName')
    await w.clock.advance(95_000)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('v249 is live on GitHub Pages')
  })

  test('gh pr merge with no PR gh can find is stopped with plain words', async ($, on) => {
    const w = world(on, {})
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'gh pr merge --squash' } as any)
    expect(r.deny).toContain('gh pr merge N --squash --delete-branch')
    expect(w.calls.length).toBe(0)
  })

  test('reading gh pr merge commands', () => {
    expect(ghMerge('gh pr merge 650 --squash --delete-branch')).toMatchObject({ number: 650 })
    expect(ghMerge('cd /c/x && gh pr merge #651 --squash')).toMatchObject({ number: 651 })
    expect(ghMerge('gh pr merge https://github.com/Shamlawy/Play-next/pull/652 --squash')).toMatchObject({ number: 652, owner: 'Shamlawy', name: 'Play-next' })
    expect(ghMerge('gh pr merge -t "Fix 12 things" --squash')?.number).toBeUndefined()
    expect(ghMerge('gh pr merge claude/branch --squash')).toMatchObject({ arg: 'claude/branch' })
    expect(ghMerge('gh pr merge 5 -R someone/else --squash')).toMatchObject({ number: 5, owner: 'someone', name: 'else' })
    expect(ghMerge('gh pr merge --squash 2>&1')?.arg).toBeUndefined()
    expect(ghMerge('git commit -m "then gh pr merge 5"')).toBeUndefined()
    expect(ghMerge('gh pr view 5')).toBeUndefined()
  })

  test('a merge of someone else\'s repo through gh is left alone', async ($, on) => {
    const w = world(on, { names: 'M\tindex.html\n', baseHtml: html('248'), headHtml: html('249') })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'gh pr merge 5 -R someone/else --squash' } as any)
    expect(r.deny).toBeUndefined()
    expect(w.calls.length).toBe(1)
  })

  test('the turn cannot end after an Android edit until a Gradle run follows it (asked once)', async ($, on) => {
    world(on, {})
    await start($)
    await $.turn.start({ text: 'build it', turnId: 'a1' })
    await $.tool.call({ tool: 'Edit', file_path: '/repo/android/app/src/main/java/io/x/A.kt', old_string: 'a', new_string: 'b' } as any)
    const r1: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r1.block).toContain('no Gradle run followed the last change')
    expect(r1.block).not.toContain('index.html')
    // Asked once: the second stop goes through.
    const again: any = await $.classic.Stop({ stop_hook_active: true } as any)
    expect(again.block).toBeUndefined()
    // node tools/kit.mjs is the web's test, not the app's.
    await $.tool.call({ tool: 'Bash', command: 'node tools/kit.mjs sweep' } as any)
    expect(((await $.classic.Stop({ stop_hook_active: false } as any)) as any).block).toContain('no Gradle run')
    await $.tool.call({ tool: 'Bash', command: 'cd android && ./gradlew :app:testDebugUnitTest --no-daemon' } as any)
    const r2: any = await $.classic.Stop({ stop_hook_active: false } as any)
    expect(r2.block).toBeUndefined()
  })

  test('Windows paths and PowerShell Gradle runs count; android/parity/ edits need no Gradle', async ($, on) => {
    world(on, {})
    await start($)
    await $.turn.start({ text: 'docs', turnId: 'a2' })
    await $.tool.call({ tool: 'Write', file_path: '/repo/android/parity/0.md', content: 'x' } as any)
    expect(((await $.classic.Stop({ stop_hook_active: false } as any)) as any).block).toBeUndefined()
    await $.turn.start({ text: 'build it', turnId: 'a3' })
    await $.tool.call({ tool: 'Write', file_path: '\\repo\\android\\app\\build.gradle.kts', content: 'x' } as any)
    expect(((await $.classic.Stop({ stop_hook_active: false } as any)) as any).block).toContain('no Gradle run')
    await $.tool.call({ tool: 'PowerShell', command: 'cd android; .\\gradlew.bat lintDebug' } as any)
    expect(((await $.classic.Stop({ stop_hook_active: false } as any)) as any).block).toBeUndefined()
    // The house rules reach an index.html edit made with a Windows path too.
    const e: any = await $.tool.call({ tool: 'Edit', file_path: 'C:\\repo\\index.html', old_string: 'a', new_string: '.card:active { transform: scale(.97); }' } as any)
    expect((e.context ?? []).join('\n')).toContain('no press animation')
    expect(repoPath('C:\\Users\\PC\\Play-next\\android\\app\\A.kt', 'C:/Users/PC/Play-next')).toBe('android/app/A.kt')
    expect(repoPath('/c/Users/PC/Play-next/android/A.kt', 'C:/Users/PC/Play-next')).toBe('android/A.kt')
    expect(repoPath('/elsewhere/android/A.kt', '/repo')).toBe('')
  })

  test('a failed Android release is said plainly', async ($, on) => {
    const w = world(on, {
      names: 'M\tandroid/version.properties\nM\tandroid/app/src/main/assets/whatsnew.txt\nM\tdocs/LOG.md\n', log: '+- android-v5: x\n',
      baseAndroid: 'release=4\n', headAndroid: 'release=5\n', whatsnew: 'v=5\ngo=home\nat=\nsay=x\ntryIt=y\n',
      checkRuns: [{ status: 'completed', conclusion: 'success', head_sha: 'feed', created_at: new Date(NOW).toISOString() }],
      runs: [{ path: '.github/workflows/android-release.yml', status: 'completed', conclusion: 'failure', html_url: 'https://github.com/x/runs/1', created_at: new Date(NOW + 60_000).toISOString() }],
    })
    await start($)
    await $.tool.call({ tool: 'mcp__github__merge_pull_request', owner: 'shamlawy', repo: 'Play-next', pullNumber: 9 } as any)
    await w.clock.advance(95_000)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('android-v5 is NOT released')
  })

  test('a build-only Android merge is watched too, and says nothing new reaches the phone', async ($, on) => {
    const w = world(on, {
      names: 'M\tandroid/app/build.gradle.kts\n', baseAndroid: 'release=4\n', headAndroid: 'release=4\n', checkRuns: [],
      runs: [{ path: '.github/workflows/android-release.yml', status: 'completed', conclusion: 'success', created_at: new Date(NOW + 60_000).toISOString() }],
    })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'gh pr merge 9 --squash --delete-branch' } as any)
    expect(r.deny).toBeUndefined()
    await w.clock.advance(95_000)
    await $.prompt.submit({ text: 'hi' } as any)
    expect(w.contexts[0]).toContain('No new Android release (release=4 unchanged)')
    expect(w.contexts[0]).not.toContain('is released')
  })

  test('a branch push does not nag about android-check, which only just started', async ($, on) => {
    world(on, { names: 'M\tandroid/version.properties\n', log: '', baseAndroid: 'release=3\n', headAndroid: 'release=4\n', whatsnew: 'v=4\ngo=home\nsay=x\ntryIt=y\n', checkRuns: [] })
    await start($)
    const r: any = await $.tool.call({ tool: 'Bash', command: 'git push -u origin claude/x' } as any)
    const said = (r.context ?? []).join('\n')
    expect(said).toContain('android-v4')
    expect(said).not.toContain('android-check')
  })
})
