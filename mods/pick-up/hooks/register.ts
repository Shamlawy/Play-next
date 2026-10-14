import type { EngineInterface, Register } from 'claude-code'

type $ = EngineInterface
type Run = { exitCode: number; stdout: string; stderr: string }
type Git = (args: string[], o?: { env?: Record<string, string>; timeoutMs?: number }) => Promise<Run>

// Pick up a chat's work anywhere. A copy on a computer and the cloud chats each hold work the others can't see until it is
// committed and pushed. At the end of every turn, uncommitted or unpushed work is saved to GitHub as one commit on
// pickup/<place>/<branch> (the files as they are now, on top of the branch's HEAD; nothing in the working copy changes).
// The next chat elsewhere is told at its start and brings it in with the `pickup` tool or /pickup: it switches to that
// branch and restores the files as uncommitted changes, then removes the saved copy. The place it came from sees that
// its copy was taken over and, if its files still match, sets them aside (git stash) so it can catch up with GitHub.
// Work that is committed and pushed leaves nothing behind (the saved copy is deleted). Git-ignored files are never saved.
// A repo turns it off with an empty .claude/pick-up-off file.

const KEEP_DAYS = 14   // saved copies older than this aren't offered
const DROP_DAYS = 30   // and are deleted from GitHub after this
const BOT = { GIT_AUTHOR_NAME: 'pick-up', GIT_AUTHOR_EMAIL: 'pick-up@users.noreply.github.com', GIT_COMMITTER_NAME: 'pick-up', GIT_COMMITTER_EMAIL: 'pick-up@users.noreply.github.com' }

type Place = { slug: string; label: string }
type Pick = { name: string; sha: string; at: number; tree: string; label: string; branch: string; files: string[]; commits: string[] }

let note = ''
let pending: Promise<void> | undefined

const first = (s: string) => s.trim().split('\n')[0] ?? ''
const ago = (ms: number) => { const m = Math.max(0, Math.round(ms / 60_000)); return m < 60 ? `${m} min ago` : m < 48 * 60 ? `${Math.round(m / 60)} h ago` : `${Math.round(m / 1440)} days ago` }
const slugOf = (branch: string) => branch.replace(/\//g, '--')

function gitIn($: $, cwd: string): Git {
  return (args, o = {}) => $.process.run(['git', ...args], { cwd, timeoutMs: o.timeoutMs ?? 20_000, ...(o.env ? { env: o.env } : {}) })
    .then(r => ({ exitCode: r.exitCode, stdout: r.stdout, stderr: r.stderr }), err => ({ exitCode: 1, stdout: '', stderr: String(err) }))
}

// The repo's top folder when this is a git repo with an origin and the mod isn't turned off there; else ''.
async function topOf($: $, cwd: string): Promise<string> {
  const git = gitIn($, cwd)
  const top = await git(['rev-parse', '--show-toplevel'])
  if (top.exitCode !== 0) return ''
  if ((await git(['remote', 'get-url', 'origin'])).exitCode !== 0) return ''
  const dir = top.stdout.trim()
  if (await $.fs.exists(`${dir}/.claude/pick-up-off`).catch(() => false)) return ''
  return dir
}

async function placeOf($: $): Promise<Place> {
  if ((await $.env.get('CLAUDE_CODE_REMOTE')) === 'true') return { slug: 'cloud', label: 'a cloud chat' }
  const host = (await $.env.get('COMPUTERNAME')) || (await $.env.get('HOSTNAME')) || 'computer'
  return { slug: 'computer-' + (host.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '') || 'x'), label: `the computer ${host}` }
}

const branchOf = async (git: Git) => (await git(['symbolic-ref', '--short', 'HEAD'])).stdout.trim() || 'detached'

// The working copy as a tree (tracked + untracked, ignored files left out), through a private index: nothing changes.
async function treeOf(git: Git, top: string): Promise<string> {
  const p = (await git(['rev-parse', '--git-path', 'pick-up-index'])).stdout.trim()
  if (!p) return ''
  const env = { GIT_INDEX_FILE: /^([a-zA-Z]:)?[\\/]/.test(p) ? p : `${top}/${p}` }
  if ((await git(['read-tree', 'HEAD'], { env })).exitCode !== 0) return ''
  if ((await git(['add', '-A'], { env, timeoutMs: 60_000 })).exitCode !== 0) return ''
  const t = await git(['write-tree'], { env })
  return t.exitCode === 0 ? t.stdout.trim() : ''
}

// Every saved copy fetched to refs/pickup/*, newest first.
async function picks(git: Git): Promise<Pick[]> {
  const r = await git(['for-each-ref', '--sort=-committerdate', '--format=%(refname)%1f%(objectname)%1f%(committerdate:unix)%1f%(tree)%1f%(contents)%1e', 'refs/pickup/'])
  if (r.exitCode !== 0) return []
  return r.stdout.split('\x1e').map(s => s.replace(/^\s+/, '')).filter(Boolean).map(rec => {
    const [ref = '', sha = '', at = '0', tree = '', body = ''] = rec.split('\x1f')
    const p: Pick = { name: ref.replace(/^refs\/pickup\//, ''), sha, at: Number(at) * 1000, tree, label: '?', branch: 'detached', files: [], commits: [] }
    let sec: string[] | undefined
    for (const line of body.split('\n')) {
      if (line.startsWith('place: ')) p.label = line.slice(7).trim()
      else if (line.startsWith('branch: ')) p.branch = line.slice(8).trim()
      else if (line.startsWith('files')) sec = p.files
      else if (line.startsWith('commits')) sec = p.commits
      else if (line.startsWith('  ') && sec) sec.push(line.trim())
    }
    return p
  })
}

// End of a turn: save this copy's unfinished work, or remove the saved copy once everything is committed and pushed.
async function save($: $, cwd: string): Promise<string> {
  const top = await topOf($, cwd)
  if (!top) return ''
  const git = gitIn($, top)
  const pl = await placeOf($)
  const branch = await branchOf(git)
  const ref = `pickup/${pl.slug}/${slugOf(branch)}`, key = `pushed:${ref}`
  const head = (await git(['rev-parse', 'HEAD'])).stdout.trim()
  const tree = head ? await treeOf(git, top) : ''
  if (!tree) return ''
  const headTree = (await git(['rev-parse', 'HEAD^{tree}'])).stdout.trim()
  const ahead = (await git(['log', '--format=%s', '-n', '20', 'HEAD', '--not', '--remotes=origin'])).stdout.trim().split('\n').filter(Boolean)
  const was = await $.store.get(key)
  if (tree === headTree && !ahead.length) {
    if (was) { await git(['push', '-q', 'origin', '--delete', ref], { timeoutMs: 30_000 }); await $.store.delete(key) }
    return ''
  }
  const stamp = `${head} ${tree}`
  if (was === stamp) return ''
  const files = (await git(['diff-tree', '-r', '--name-status', 'HEAD', tree])).stdout.trim().split('\n').filter(Boolean).map(l => l.replace(/\t/g, ' '))
  const msg = [`pick-up: ${pl.label} · ${branch}`, '', `place: ${pl.label}`, `branch: ${branch}`,
    ...(files.length ? [`files (${files.length}):`, ...files.slice(0, 60).map(f => '  ' + f)] : []),
    ...(ahead.length ? [`commits not on GitHub (${ahead.length}):`, ...ahead.map(s => '  ' + s)] : [])].join('\n')
  const c = await git(['commit-tree', tree, '-p', head, '-m', msg], { env: BOT })
  if (c.exitCode !== 0) return ''
  const p = await git(['push', '-q', '-f', 'origin', `${c.stdout.trim()}:refs/heads/${ref}`], { timeoutMs: 60_000 })
  if (p.exitCode !== 0) return `pick-up: couldn't save this chat's unfinished work to GitHub (${first(p.stderr) || 'push failed'}).`
  await $.store.set(key, stamp)
  return ''
}

// Chat start: what other places left unfinished, and whether this copy's own saved work was taken over elsewhere.
async function scan($: $, cwd: string): Promise<{ note: string; toast: string }> {
  const none = { note: '', toast: '' }
  const top = await topOf($, cwd)
  if (!top) return none
  const git = gitIn($, top)
  const pl = await placeOf($)
  const f = await git(['fetch', '-q', '--prune', '--no-write-fetch-head', 'origin', '+refs/heads/pickup/*:refs/pickup/*'], { timeoutMs: 60_000 })
  if (f.exitCode !== 0) return none
  const all = await picks(git)
  const now = await $.clock.now()
  const notes: string[] = []
  let here: string | undefined
  const current = async () => (here ??= `${(await git(['rev-parse', 'HEAD'])).stdout.trim()} ${await treeOf(git, top)}`)

  // Ours, gone from GitHub: another chat picked it up. Still the same files here: set them aside and catch up.
  for (const key of (await $.store.keys()).filter(k => k.startsWith(`pushed:pickup/${pl.slug}/`))) {
    const name = key.slice('pushed:pickup/'.length)
    if (all.some(p => p.name === name)) continue
    const stamp = await $.store.get(key)
    await $.store.delete(key)
    if (slugOf(await branchOf(git)) !== name.slice(pl.slug.length + 1) || stamp !== await current()) continue
    const st = await git(['stash', 'push', '-u', '-q', '-m', 'pick-up: carried on in another chat'])
    if (st.exitCode !== 0) continue
    here = undefined
    const ff = await git(['merge', '--ff-only', '-q', '@{upstream}'], { timeoutMs: 60_000 })
    notes.push(`pick-up: this copy's unfinished work on ${await branchOf(git)} was picked up and carried on in another chat, so it was set aside here (git stash, "pick-up: carried on in another chat")${ff.exitCode === 0 ? ' and the branch brought up to date with GitHub' : ''}.`)
  }

  const offer: Pick[] = []
  for (const p of all) {
    if (p.name.startsWith(`${pl.slug}/`)) continue
    const age = now - p.at
    if (age > DROP_DAYS * 86_400_000) { await git(['push', '-q', 'origin', '--delete', `pickup/${p.name}`], { timeoutMs: 30_000 }); continue }
    if (age > KEEP_DAYS * 86_400_000 || offer.length >= 3) continue
    if ((await current()).endsWith(` ${p.tree}`)) continue   // already here
    offer.push(p)
  }
  if (offer.length) {
    const line = (p: Pick) => `- ${p.label}, ${ago(now - p.at)}, branch ${p.branch}: ${p.files.length} changed file(s)` +
      (p.files.length ? ` (${p.files.slice(0, 4).map(f => f.replace(/^\w+ /, '')).join(', ')}${p.files.length > 4 ? ', …' : ''})` : '') +
      (p.commits.length ? `; ${p.commits.length} commit(s) not on GitHub ("${p.commits[0]}")` : '') + ` [id: ${p.name}]`
    notes.push(`[pick-up] Unfinished work other chats left on GitHub (not in this copy yet), newest first:\n${offer.map(line).join('\n')}\n` +
      'If the person\'s message continues that work or asks to pick up where they left off, bring it in first with the mcp__pick-up__pickup tool (the id), then carry on. Otherwise mention it in one line and don\'t bring it in unasked.')
  }
  const o = offer[0]
  return { note: notes.join('\n\n'), toast: o ? `Unfinished work from ${o.label} (${ago(now - o.at)}): say "pick up" to carry on here.` : '' }
}

// Bring a saved copy in: its branch, its files as uncommitted changes; then it is removed from GitHub.
async function apply($: $, cwd: string, id: string): Promise<string> {
  const top = await topOf($, cwd)
  if (!top) return 'pick-up: not in a git repository with an origin (or pick-up is off here).'
  const git = gitIn($, top)
  const pl = await placeOf($)
  let name = id.trim().replace(/^(refs\/)?(heads\/)?pickup\//, '')
  if (!name) {
    await git(['fetch', '-q', '--prune', '--no-write-fetch-head', 'origin', '+refs/heads/pickup/*:refs/pickup/*'], { timeoutMs: 60_000 })
    name = (await picks(git)).find(p => !p.name.startsWith(`${pl.slug}/`))?.name ?? ''
    if (!name) return 'pick-up: no unfinished work from other chats is saved on GitHub.'
  }
  if (!/^[A-Za-z0-9._/-]+$/.test(name) || name.includes('..')) return `pick-up: "${id}" isn't a saved copy's id.`
  const f = await git(['fetch', '-q', '--no-write-fetch-head', 'origin', `+refs/heads/pickup/${name}:refs/pickup/${name}`], { timeoutMs: 60_000 })
  if (f.exitCode !== 0) return `pick-up: couldn't fetch pickup/${name} from GitHub (${first(f.stderr)}).`
  if ((await git(['status', '--porcelain'])).stdout.trim()) return 'pick-up: this copy has uncommitted changes of its own. Commit or stash them first, then pick up again.'
  const p = (await picks(git)).find(x => x.name === name)
  if (!p) return `pick-up: pickup/${name} isn't on GitHub.`
  const base = (await git(['rev-parse', `${p.sha}^`])).stdout.trim()
  let branch = p.branch
  let sw: Run
  if (branch === 'detached') sw = await git(['switch', '-q', '--detach', base])
  else {
    const local = await git(['rev-parse', '--verify', '-q', `refs/heads/${branch}`])
    const l = local.stdout.trim()
    if (local.exitCode !== 0) {
      sw = await git(['switch', '-q', '-c', branch, base])
      if (sw.exitCode === 0) await git(['branch', '-q', `--set-upstream-to=origin/${branch}`])
    } else if (l === base) sw = await git(['switch', '-q', branch])
    else if ((await git(['merge-base', '--is-ancestor', l, base])).exitCode === 0) {
      sw = await git(['switch', '-q', branch])
      if (sw.exitCode === 0) sw = await git(['merge', '--ff-only', '-q', base])
    } else {
      branch = `${branch}-picked-up`
      sw = await git(['switch', '-q', '-C', branch, base])
    }
  }
  if (sw.exitCode !== 0) return `pick-up: couldn't switch to ${branch} (${first(sw.stderr)}).`
  const co = await git(['checkout', p.sha, '--', '.'])
  if (co.exitCode !== 0) return `pick-up: switched to ${branch} but couldn't restore the files (${first(co.stderr)}).`
  const gone = (await git(['diff-tree', '-r', '--name-only', '--diff-filter=D', base, p.sha])).stdout.trim().split('\n').filter(Boolean)
  if (gone.length) await git(['rm', '-q', '--', ...gone])
  await git(['reset', '-q'])
  await git(['push', '-q', 'origin', '--delete', `pickup/${name}`], { timeoutMs: 30_000 })
  await git(['update-ref', '-d', `refs/pickup/${name}`])
  return `pick-up: brought in the unfinished work from ${p.label} (saved ${ago((await $.clock.now()) - p.at)}): now on branch ${branch} with ${p.files.length} changed file(s) restored as uncommitted changes` +
    (p.commits.length ? `, plus ${p.commits.length} commit(s) that weren't on GitHub yet (they're on ${branch} here now; push them with the rest)` : '') +
    `. The saved copy on GitHub was removed; ${p.label} sets its own copy aside the next time it opens.`
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    const r = await next(e)
    await $.command.register({ name: 'pickup', description: 'Bring in the newest unfinished work another chat left on GitHub.', argumentHint: '[id]' }).catch(() => undefined)
    await $.tool.register({
      name: 'pickup',
      description: 'Bring in unfinished work another chat (another computer or a cloud chat) saved to GitHub: switches to its branch and restores its files as uncommitted changes. Use when the person wants to carry on that work. The id comes from the [pick-up] note; leave it out for the newest.',
      inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The saved copy\'s id, e.g. computer-x/main' } } },
    }).catch(() => undefined)
    pending = scan($, e.cwd).then(s => {
      note = s.note
      if (s.toast) $.ui.toast(s.toast, { timeoutMs: 12_000 })
    }).catch(() => undefined)
    return r
  })

  // The first message after the scan carries what was found.
  on('prompt.submit', async ($, e, next) => {
    if (pending) {
      const wait = pending
      pending = undefined
      await Promise.race([wait, $.clock.sleep(30_000)]).catch(() => undefined)
    }
    if (!note) return next(e)
    const text = note
    note = ''
    return next({ ...e, context: [...(e.context ?? []), text] })
  })

  on('tool.call', { tool: 'mcp__pick-up__pickup' }, async ($, e) => {
    const id = (e as { id?: unknown }).id
    return { result: await apply($, await $.session.cwd(), typeof id === 'string' ? id : '') }
  })

  on('command.run', { command: 'pickup' }, async ($, e) => ({ text: await apply($, await $.session.cwd(), e.args) }))

  // Every main-loop turn that ends leaves its unfinished work on GitHub.
  on('turn.complete', async ($, e, next) => {
    const r = await next(e)
    if (e.agentId) return r
    const msg = await save($, await $.session.cwd()).catch(() => '')
    if (msg) $.ui.toast(msg, { timeoutMs: 10_000 })
    return r
  })
}
