import type { EngineInterface, Register } from 'claude-code'
import {
  ANDROID_VERSION, ANDROID_WHATSNEW, androidStep, appV, checkState, diffLines, fileProblems, ghMerge, isAndroidShip, isCheckWait, isWebShip,
  logLookup, parseNameStatus, parsePorcelain, releaseProblems, repoPath, rootDeleted, scanBlock, scanLines,
  type CheckRun, type GhMerge, type Range,
} from './rules.ts'
import { ANNOUNCE_TOAST, announceNote, herePath, hereRoot, isHere, setHere, toldKey, withNote } from './here.ts'

type $ = EngineInterface

// A watch on GitHub after a merge to main: Pages (the app), the helper deploy when worker/** changed, and
// android-release.yml when android/ changed (androidV = the release it makes, androidSame = release=N unchanged).
type Watch = {
  since: number; appV?: string; helper: boolean; pages?: boolean; pagesDone?: boolean; helperDone?: boolean; slowTold?: boolean
  android?: boolean; androidV?: string; androidSame?: string; androidDone?: boolean
}

const POLL = 90_000
const SLOW = 25 * 60_000
const GIVE_UP = 60 * 60_000
// The Android release builds, signs and runs an emulator before it publishes: give it longer.
const SLOW_ANDROID = 45 * 60_000
const GIVE_UP_ANDROID = 120 * 60_000
const PAGES = 'pages build and deployment'
const GH_HEADERS = { accept: 'application/vnd.github+json', 'user-agent': 'play-next-release-guard' }

// Imports worker/helper.js (from stdin) in Node the way CLAUDE.md asks: node --check, then a real import.
const HELPER_CHECK = `
const fs=require('fs'),os=require('os'),path=require('path'),cp=require('child_process'),url=require('url');
const f=path.join(os.tmpdir(),'rg-helper-'+process.pid+'.mjs');fs.writeFileSync(f,fs.readFileSync(0,'utf8'));
const done=t=>{try{fs.unlinkSync(f)}catch{}console.log(t)};
let bad='';try{cp.execFileSync(process.execPath,['--check',f],{stdio:'pipe'})}catch(e){bad=String(e.stderr||e.message).trim().split('\\n').slice(0,4).join(' ')}
if(bad)done('node --check failed: '+bad);
else import(url.pathToFileURL(f).href).then(m=>done(m.default&&typeof m.default.fetch==='function'?'ok':'imports, but has no default export with fetch()'),e=>done('import in Node failed: '+String(e&&e.message||e).slice(0,200)));
`

const PUSH = /\bgit\s+(?:-C\s+\S+\s+)?push\b/
const COMMIT = /\bgit\s+(?:-C\s+\S+\s+)?commit\b/
const TO_MAIN = /\bgit\s+(?:-C\s+\S+\s+)?push\b[^&;|]*(?:\s(?:\S+:)?(?:refs\/heads\/)?main\b)/
const HTML_WRITE = /index\.html/
// Shell commands that change index.html (a script only when it writes files).
const WRITER = /\bsed\s+-[a-zA-Z]*i|\bperl\s+-[a-zA-Z]*i|>\s*\S*index\.html|\btee\b|\bpatch\b|\bgit\s+apply\b|\b(?:python3?|node)\b[\s\S]*(?:writeFileSync|\.write\(|open\([^)]*["'][wa]\+?["'])/
const TESTED = /playwright|chromium|pw-browsers|webkit|tools\/kit\.mjs\s+(?:sweep|tour|perf|all|run|eye|taste|look|smooth|layers)\b|scratchpad\/\S+\.(?:m?js|py)\b/i
// The Android app's test: a Gradle run (gradlew / gradlew.bat, from Bash or PowerShell) with a test, lint, assemble, check or build task.
const GRADLE = /\bgradlew(?:\.bat)?\b[^&;|\n]*?[\s:](?:[\w:-]*?)(?:test|lint|assemble|check|build)\w*/i
// RG_SKIP=1 in front (Bash) or $env:RG_SKIP=1 (PowerShell) skips the gates on purpose.
const SKIP = /RG_SKIP\s*=\s*["']?1/

const bullet = (xs: readonly string[]) => xs.map(x => `- ${x}`).join('\n')

// Module state: a reload starts it over (the watch is kept in $.store).
let root = ''
let repo = 'shamlawy/Play-next'
let watch: Watch | undefined
let polling = false
const pending: string[] = []
const toldRules = new Set<string>()
const toldLog = new Set<string>()
// Per turn: was index.html changed after the last browser test, android/ after the last Gradle run? (one counter orders all)
let seq = 0
let lastEdit = 0
let lastTest = 0
let lastAndroid = 0
let lastGradle = 0


function git($: $, args: string[], timeoutMs = 20_000) {
  return $.process.run(['git', ...args], { cwd: root || undefined, timeoutMs })
}
async function show($: $, rev: string, path: string) {
  const r = await git($, ['show', `${rev}:${path}`])
  return r.exitCode === 0 ? r.stdout : undefined
}

async function tell($: $, text: string) {
  pending.push(text)
  $.ui.toast(text, { timeoutMs: 12_000 })
  $.ui.status(text)
  try {
    await $.session.append({ message: { type: 'system', content: [{ type: 'text', text: `release-guard: ${text}` }] } })
  } catch {
    // A notice is a nicety; the toast and the next-reply note still carry it.
  }
}

// What a revision brings to main, gathered from git (and Node for the helper).
async function gather($: $, rev: string): Promise<Range | string> {
  await git($, ['fetch', '-q', 'origin', 'main'], 40_000).catch(() => undefined)
  const mb = await git($, ['merge-base', 'origin/main', rev])
  if (mb.exitCode !== 0) return `no common base with origin/main for ${rev}`
  const base = mb.stdout.trim()
  const changes = parseNameStatus((await git($, ['diff', '--name-status', '-M', base, rev])).stdout)
  const has = (p: string) => changes.some(c => c.path === p)
  const range: Range = { changes, logAdded: diffLines((await git($, ['diff', '-U0', base, rev, '--', 'docs/LOG.md'])).stdout).added.join('\n') }
  if (has('index.html')) {
    range.baseHtml = await show($, base, 'index.html')
    range.headHtml = await show($, rev, 'index.html')
    range.mainHtml = await show($, 'origin/main', 'index.html')
  }
  if (has('worker/helper.js')) {
    range.baseHelper = await show($, base, 'worker/helper.js')
    range.headHelper = await show($, rev, 'worker/helper.js')
    if (range.headHelper !== undefined) {
      const r = await $.process.run(['node', '-e', HELPER_CHECK], { cwd: root || undefined, stdin: range.headHelper, timeoutMs: 30_000 })
      const said = r.stdout.trim().split('\n').pop() ?? ''
      if (said !== 'ok') range.helperCheck = said || r.stderr.trim().slice(0, 200) || `node exited ${r.exitCode}`
    }
  }
  if (changes.some(c => isAndroidShip(c.path))) {
    range.baseAndroid = await show($, base, ANDROID_VERSION)
    range.headAndroid = await show($, rev, ANDROID_VERSION)
    range.mainAndroid = await show($, 'origin/main', ANDROID_VERSION)
    range.androidWhatsNew = await show($, rev, ANDROID_WHATSNEW)
    const sha = /^[0-9a-f]{4,40}$/i.test(rev) ? rev : (await git($, ['rev-parse', rev])).stdout.trim()
    range.androidCheck = sha ? await androidCheck($, sha) : { state: 'unknown' }
  }
  return range
}

// android-check.yml's newest run on a commit (GitHub's public API, no token).
async function androidCheck($: $, sha: string): Promise<CheckRun> {
  try {
    const res = await $.http.fetch(`https://api.github.com/repos/${repo}/actions/workflows/android-check.yml/runs?head_sha=${sha}&per_page=20`, { headers: GH_HEADERS })
    if (res.ok) return checkState((JSON.parse(res.text) as { workflow_runs?: Run[] }).workflow_runs ?? [], sha)
    if (res.status !== 404) return { state: 'unknown' }
    // GitHub doesn't know the workflow by file name until main has it (its first PR): read every run of the commit.
    const all = await $.http.fetch(`https://api.github.com/repos/${repo}/actions/runs?head_sha=${sha}&per_page=50`, { headers: GH_HEADERS })
    if (!all.ok) return { state: 'unknown' }
    const runs = ((JSON.parse(all.text) as { workflow_runs?: Run[] }).workflow_runs ?? []).filter(r => /(?:^|\/)android-check\.yml\b/.test(r.path ?? ''))
    return checkState(runs, sha)
  } catch {
    return { state: 'unknown' }
  }
}

async function check($: $, rev: string) {
  const r = await gather($, rev)
  if (typeof r === 'string') return { block: [], notes: [r], range: undefined }
  return { ...releaseProblems(r), range: r }
}

function startWatch($: $, now: number, range: Range | undefined) {
  const droid = range ? androidStep(range) : { shipped: false }
  watch = {
    since: now,
    appV: appV(range?.headHtml),
    helper: !!range?.changes.some(c => c.path.startsWith('worker/')),
    // An Android-only merge changes nothing the web app serves: no Pages news for it.
    pages: !droid.shipped || !!range?.changes.some(c => isWebShip(c.path)),
    android: droid.shipped,
    androidV: droid.release,
    androidSame: droid.same,
  }
  void $.store.set('watch', watch)
  const what = [watch.pages ? 'GitHub Pages' : '', watch.helper ? 'the helper deploy' : '', watch.android ? 'the Android release' : ''].filter(Boolean)
  $.ui.status(`watching ${what.join(' and ')}…`)
}

type Run = { name?: string; path?: string; status?: string; conclusion?: string | null; head_sha?: string; created_at?: string; html_url?: string }

async function poll($: $) {
  const w = watch
  if (!w || polling) return
  polling = true
  try {
    const now = await $.clock.now()
    const res = await $.http.fetch(`https://api.github.com/repos/${repo}/actions/runs?branch=main&per_page=20`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'play-next-release-guard' },
    })
    if (!res.ok) return
    const runs = ((JSON.parse(res.text) as { workflow_runs?: Run[] }).workflow_runs ?? [])
      .filter(r => Date.parse(r.created_at ?? '') >= w.since - 120_000)
    const pages = runs.find(r => r.name === PAGES)
    if (w.pages !== false && !w.pagesDone && pages?.status === 'completed') {
      w.pagesDone = true
      if (pages.conclusion === 'success') {
        const live = await $.http.fetch(`https://raw.githubusercontent.com/${repo}/${pages.head_sha}/index.html`)
        const v = live.ok ? appV(live.text) : undefined
        await tell($, v
          ? `v${v} is live on GitHub Pages: both phones get it on their next open${w.appV && v !== w.appV ? ` (expected v${w.appV})` : ''}.`
          : 'GitHub Pages finished deploying: the update is live.')
      } else {
        await tell($, `GitHub Pages deploy ${pages.conclusion ?? 'failed'}: the update is NOT live. ${pages.html_url ?? ''}`.trim())
      }
    }
    const helper = runs.find(r => (r.path ?? '').endsWith('deploy-helper.yml'))
    if (w.helper && !w.helperDone && helper?.status === 'completed') {
      w.helperDone = true
      await tell($, helper.conclusion === 'success'
        ? "Nexi's helper is deployed."
        : `Nexi's helper deploy ${helper.conclusion ?? 'failed'}: the live helper is still the old one. ${helper.html_url ?? ''}`.trim())
    }
    const release = runs.find(r => /(?:^|\/)android-release\.yml\b/.test(r.path ?? ''))
    if (w.android && !w.androidDone && release?.status === 'completed') {
      w.androidDone = true
      const c = release.conclusion ?? 'failed'
      const quiet = c === 'skipped' || c === 'neutral'
      if (w.androidV) {
        await tell($, c === 'success'
          ? `android-v${w.androidV} is released (the phone updates itself).`
          : `The Android release run ended "${c}": android-v${w.androidV} is NOT released. ${release.html_url ?? ''}`.trim())
      } else {
        await tell($, c === 'success' || quiet
          ? `No new Android release (release=${w.androidSame ?? '?'} unchanged): a build-only change, the phone gets nothing new.`
          : `The Android build on main ended "${c}": nothing was released, and main's Android build needs a look. ${release.html_url ?? ''}`.trim())
      }
    }
    const settled = (w.pages === false || w.pagesDone) && (!w.helper || w.helperDone) && (!w.android || w.androidDone)
    if (!settled && !w.slowTold && now - w.since > (w.android ? SLOW_ANDROID : SLOW)) {
      w.slowTold = true
      await tell($, `Still not live ${Math.round((now - w.since) / 60_000)} min after the merge: check the Actions tab.`)
    }
    if (settled || now - w.since > (w.android ? GIVE_UP_ANDROID : GIVE_UP)) {
      watch = undefined
      if (settled) $.ui.status(undefined)
    }
    await $.store.set('watch', watch ?? null)
  } catch {
    // Network hiccup: the next tick tries again.
  } finally {
    polling = false
  }
}

// A pull request about to be merged: its head commit and the branch it merges into.
type Pr = { number?: number; sha?: string; ref?: string; base?: string }

async function prByNumber($: $, full: string, n: number): Promise<Pr | undefined> {
  const res = await $.http.fetch(`https://api.github.com/repos/${full}/pulls/${n}`, { headers: GH_HEADERS }).catch(() => undefined)
  if (!res?.ok) return undefined
  const i = JSON.parse(res.text) as { head?: { sha?: string; ref?: string }; base?: { ref?: string } }
  return { number: n, sha: i.head?.sha, ref: i.head?.ref, base: i.base?.ref }
}

// The merge is the release (GitHub Pages serves main; android-release.yml builds from it): check what the PR brings.
async function mergeGate($: $, pr: Pr): Promise<{ deny?: string; range?: Range }> {
  if (pr.base !== 'main' || !pr.sha) return {}
  const got = pr.ref ? await git($, ['fetch', '-q', 'origin', pr.ref], 40_000) : undefined
  if (!got || got.exitCode !== 0) await git($, ['fetch', '-q', 'origin', `pull/${pr.number}/head`], 40_000)
  const r = await check($, pr.sha)
  if (!r.block.length) return { range: r.range }
  const web = !!r.range?.changes.some(c => isWebShip(c.path))
  const droid = !!r.range && androidStep(r.range).shipped
  const what = web ? 'it would ship to both phones' : droid ? "it would ship to the owner's phone" : 'main is the release'
  return { deny: `release-guard stopped this merge (${what}):\n${bullet(r.block)}${r.notes.length ? `\nAlso:\n${bullet(r.notes)}` : ''}\nFix it on the branch, push, then merge.`, range: r.range }
}

// `gh pr merge` (the PC's merge path): find the PR (number, URL, branch, or the current branch's), then the same check.
// undefined = another repo's PR: not ours to check or watch.
async function ghMergeGate($: $, gm: GhMerge): Promise<{ deny?: string; range?: Range } | undefined> {
  if (gm.owner && gm.name && `${gm.owner}/${gm.name}`.toLowerCase() !== repo.toLowerCase()) return undefined
  let pr = gm.number ? await prByNumber($, repo, gm.number) : undefined
  if (!pr?.sha) {
    // No number (a branch, or the current branch's PR), or the API didn't answer: ask gh itself.
    const args = ['gh', 'pr', 'view', ...(gm.arg ? [gm.arg] : []), ...(gm.repo ? ['-R', gm.repo] : []), '--json', 'number,headRefOid,headRefName,baseRefName']
    const r = await $.process.run(args, { cwd: root || undefined, timeoutMs: 30_000 }).catch(() => undefined)
    if (r?.exitCode === 0) {
      try {
        const j = JSON.parse(r.stdout) as { number?: number; headRefOid?: string; headRefName?: string; baseRefName?: string }
        pr = { number: j.number, sha: j.headRefOid, ref: j.headRefName, base: j.baseRefName }
      } catch {
        // Not JSON: treated as not found below.
      }
    }
  }
  if (!pr?.sha) return { deny: `release-guard couldn't tell which pull request this merges (${gm.arg ? `"${gm.arg}"` : 'no PR number, and the current branch has no open PR gh could find'}): run it again with the number (gh pr merge N --squash --delete-branch), or put RG_SKIP=1 in front if you checked it yourself.` }
  return mergeGate($, pr)
}

// What a shell command needs before it runs (Bash, or PowerShell on Windows), and what to do once it ran.
type ShellGate = { deny?: string; main?: { range?: Range; notes: string[] }; merge?: { range?: Range }; headBefore?: string }

async function shellBefore($: $, cmd: string): Promise<ShellGate> {
  if (HTML_WRITE.test(cmd) && WRITER.test(cmd) && !COMMIT.test(cmd) && !PUSH.test(cmd)) lastEdit = ++seq
  if (TESTED.test(cmd)) lastTest = ++seq
  if (GRADLE.test(cmd)) lastGradle = ++seq
  const skip = SKIP.test(cmd)
  const g: ShellGate = {}

  if (COMMIT.test(cmd) && !skip) {
    const stages = /\bgit\s+add\b/.test(cmd)
    const changes = stages
      ? parsePorcelain((await git($, ['status', '--porcelain=v1', '-uall'])).stdout)
      : [
          ...parseNameStatus((await git($, ['diff', '--cached', '--name-status', '-M'])).stdout),
          ...(/\s-[a-zA-Z]*a|\s--all\b/.test(cmd) ? parseNameStatus((await git($, ['diff', '--name-status'])).stdout) : []),
        ]
    // A force-add of an ignored file isn't staged yet when this runs, so the command itself is read.
    const forced = [
      ...(/\bgit\s+add\b[^&;|]*\s(?:-f|--force)\b[^&;|]*\.json\b/.test(cmd) ? ['a .json file force-added: backups hold private data'] : []),
      ...(/\bgit\s+add\b[^&;|]*\s(?:-f|--force)\b[^&;|]*(?:\.(?:jks|keystore|p12)\b|key\.properties|local\.properties)/i.test(cmd) ? ['a keystore / key.properties / local.properties force-added: the signing key lives only in Cloudflare KV, local files stay local'] : []),
    ]
    const block = [...forced, ...fileProblems(changes, rootDeleted(changes))]
    if (block.length) return { deny: `release-guard stopped this commit:\n${bullet(block)}\nUnstage those (git restore --staged <file>, or git checkout -- <file> for a deleted one) and commit again.` }
  }

  const gm = skip ? undefined : ghMerge(cmd)
  const m = gm ? await ghMergeGate($, gm) : undefined
  if (m?.deny) return { deny: m.deny }
  if (m) g.merge = { range: m.range }

  if (PUSH.test(cmd) && TO_MAIN.test(cmd) && !skip) {
    const { block, notes, range } = await check($, 'HEAD')
    if (block.length) return { deny: `release-guard stopped this push to main:\n${bullet(block)}${notes.length ? `\nAlso:\n${bullet(notes)}` : ''}` }
    g.main = { range, notes }
  }
  if (COMMIT.test(cmd)) g.headBefore = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
  return g
}

// After a shell command ran without an error: start a watch, and what to tell Claude.
async function shellAfter($: $, cmd: string, g: ShellGate): Promise<string[]> {
  const context: string[] = []
  if (g.main || g.merge) startWatch($, await $.clock.now(), (g.main ?? g.merge)?.range)
  if (g.main?.notes.length) context.push(`[release-guard] Worth a look:\n${bullet(g.main.notes)}`)

  // After a commit: house rules on what it added to index.html (edits made through the shell skip the Edit hook).
  if (COMMIT.test(cmd)) {
    const head = (await git($, ['rev-parse', 'HEAD'])).stdout.trim()
    if (head && head !== g.headBefore) {
      const d = await git($, ['show', '-U0', '--format=', 'HEAD', '--', 'index.html'])
      const { added } = diffLines(d.stdout)
      const rules = [...scanLines(added), ...scanBlock(added.join('\n'))].map(f => f.text).filter(t => !toldRules.has(t))
      rules.forEach(t => toldRules.add(t))
      if (rules.length) context.push(`[release-guard] House rules (CLAUDE.md) on what this commit adds to index.html:\n${bullet(rules)}`)
    }
  }

  // After a branch push: what would stop the merge, said now while it is cheap to fix (android-check has only just started).
  if (PUSH.test(cmd) && !g.main) {
    const { block, notes } = await check($, 'HEAD')
    const real = block.filter(b => !isCheckWait(b))
    if (real.length) context.push(`[release-guard] Pushed, but this branch would be stopped at merge:\n${bullet(real)}\nFix it before merging (or say it's a work-in-progress push).`)
    if (notes.length) context.push(`[release-guard] Worth a look before merging:\n${bullet(notes)}`)
  }
  return context
}

const WEB_STOP = '[release-guard] index.html changed this turn but no browser test ran after the last change. CLAUDE.md: test in Playwright + Chromium before saying it is done: `node tools/kit.mjs sweep` (412/859/360 detector sweep), `node tools/kit.mjs tour` if WHATS_NEW changed, `node tools/kit.mjs run tools/scenarios/<x>.mjs` for the change itself. If this turn did not finish the change, say so plainly instead.'
const ANDROID_STOP = '[release-guard] The Android app (android/) changed this turn but no Gradle run followed the last change. Run it in android/: `./gradlew test lint` (Bash) or `.\\gradlew.bat test lint` (PowerShell), plus `assembleDebug` when the build itself changed. If Gradle cannot run here (no dl.google.com), say so plainly: android-check runs it on the PR. If this turn did not finish the change, say so instead.'

// On only in a repo that carries this mod (see here.ts); kept in this file, as $ never crosses an import.
// Elsewhere it says so once per project (a toast now, a note on the first message) for the whole family.
async function checkHere($: $, cwd: string) {
  const root = hereRoot(cwd)
  const path = herePath(cwd, 'release-guard')
  const found = path ? await $.fs.exists(path).catch(() => false) : false
  if (found || !root) return setHere(found)
  if (await $.store.get(toldKey(root)).catch(() => true)) return setHere(false)
  await $.store.set(toldKey(root), true).catch(() => undefined)
  $.ui.toast(ANNOUNCE_TOAST)
  return setHere(false, announceNote('release-guard'))
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    if (!(await checkHere($, e.cwd))) return next(e)
    const cwd = e.cwd
    const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'], { cwd }).catch(() => undefined)
    root = top?.exitCode === 0 ? top.stdout.trim() : cwd
    const url = (await git($, ['remote', 'get-url', 'origin']).catch(() => undefined))?.stdout.trim() ?? ''
    const m = url.match(/([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
    if (m) repo = `${m[1]}/${m[2]}`
    const saved = (await $.store.get('watch')) as Watch | null | undefined
    if (saved && (await $.clock.now()) - saved.since < (saved.android ? GIVE_UP_ANDROID : GIVE_UP)) watch = saved
    $.clock.every(POLL, () => void poll($))
    await $.command.register({ name: 'release-check', description: 'Check this branch against the release rules (APP_V, WHATS_NEW, LOG, helper, files, the Android release).' })
    return next(e)
  })

  on('command.run', { command: 'release-check' }, async $ => {
    const { block, notes } = await check($, 'HEAD')
    const lines = [
      block.length ? `Not ready to merge:\n${bullet(block)}` : 'Ready to merge: APP_V, WHATS_NEW, LOG, helper, files and the Android release all check out.',
      notes.length ? `Worth a look:\n${bullet(notes)}` : '',
    ]
    return { text: lines.filter(Boolean).join('\n\n') }
  })

  on('turn.start', ($, e, next) => {
    if (!isHere()) return next(e)
    seq = lastEdit = lastTest = lastAndroid = lastGradle = 0
    return next(e)
  })

  // Every reply: news from the watch (merged → live) that arrived since the last one.
  on('prompt.submit', ($, e, next) => {
    if (!isHere()) return next(withNote(e))
    if (!pending.length) return next(e)
    const note = `[release-guard] Since your last reply: ${pending.splice(0).join(' ')} Tell the person in one short line.`
    return next({ ...e, context: [...(e.context ?? []), note] })
  })

  // Edits to index.html: house rules on the added lines, and the LOG lines about the code touched.
  // Edits to the Android app (android/, not android/parity/): counted for the end-of-turn Gradle check.
  on('tool.call', { tool: 'Edit' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const file = String(e.file_path ?? '').replace(/\\/g, '/')
    if (isAndroidShip(repoPath(file, root))) {
      const ran = await next(e)
      if (ran.deny === undefined && !ran.isError) lastAndroid = ++seq
      return ran
    }
    if (!/(^|\/)index\.html$/.test(file)) return next(e)
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    lastEdit = ++seq
    const oldLines = new Set(e.old_string.split('\n'))
    const added = e.new_string.split('\n').filter(l => !oldLines.has(l))
    const rules = [...scanLines(added), ...scanBlock(e.new_string)].map(f => f.text).filter(t => !toldRules.has(t))
    rules.forEach(t => toldRules.add(t))
    const log = await $.fs.read(`${root}/docs/LOG.md`).catch(() => '')
    const past = typeof log === 'string' && log ? logLookup(e.old_string, log, toldLog) : []
    const context = [...(ran.context ?? [])]
    if (rules.length) context.push(`[release-guard] House rules (CLAUDE.md) on the lines you just added to index.html:\n${bullet(rules)}\nFix them now unless it is deliberate.`)
    if (past.length) context.push(`[release-guard] docs/LOG.md on code you just changed (make sure an old fix isn't undone):\n${bullet(past)}`)
    return context.length ? { ...ran, context } : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Write' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const file = String(e.file_path ?? '').replace(/\\/g, '/')
    if (isAndroidShip(repoPath(file, root))) {
      const ran = await next(e)
      if (ran.deny === undefined && !ran.isError) lastAndroid = ++seq
      return ran
    }
    if (!/(^|\/)index\.html$/.test(file)) return next(e)
    const before = await $.fs.read(e.file_path).catch(() => '')
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    lastEdit = ++seq
    const old = new Set(typeof before === 'string' ? before.split('\n') : [])
    const added = e.content.split('\n').filter(l => !old.has(l))
    const rules = [...scanLines(added), ...scanBlock(added.join('\n'))].map(f => f.text).filter(t => !toldRules.has(t))
    rules.forEach(t => toldRules.add(t))
    if (!rules.length) return ran
    return { ...ran, context: [...(ran.context ?? []), `[release-guard] House rules (CLAUDE.md) on the lines you just added to index.html:\n${bullet(rules)}`] }
  }).catch(($, e, next) => next(e))

  // Shell: commits, pushes to main and `gh pr merge` are gated; edits, browser tests and Gradle runs are counted.
  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const g = await shellBefore($, e.command)
    if (g.deny !== undefined) return { deny: g.deny }
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    const context = await shellAfter($, e.command, g)
    return context.length ? { ...ran, context: [...(ran.context ?? []), ...context] } : ran
  }).catch(($, e, next) => next(e))

  // PowerShell is the shell tool Claude Code uses on Windows (the owner's PC merges with `gh pr merge` there): the same gates.
  on('tool.call', { tool: 'PowerShell' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const cmd = (e as unknown as { command?: unknown }).command
    if (typeof cmd !== 'string') return next(e)
    const g = await shellBefore($, cmd)
    if (g.deny !== undefined) return { deny: g.deny }
    const ran = await next(e)
    if (ran.deny !== undefined || ran.isError) return ran
    const context = await shellAfter($, cmd, g)
    return context.length ? { ...ran, context: [...(ran.context ?? []), ...context] } : ran
  }).catch(($, e, next) => next(e))

  // The merge is the release: check what the PR brings, then watch it go live.
  on('tool.call', { tool: 'mcp__github__merge_pull_request' }, async ($, e, next) => {
    if (!isHere()) return next(e)
    const a = e as unknown as { owner?: string; repo?: string; pullNumber?: number }
    const full = `${a.owner ?? ''}/${a.repo ?? ''}`
    if (full.toLowerCase() !== repo.toLowerCase()) return next(e)
    const pr = a.pullNumber ? await prByNumber($, full, a.pullNumber) : undefined
    const g = pr ? await mergeGate($, pr) : {}
    if (g.deny) return { deny: g.deny }
    const ran = await next(e)
    if (ran.deny === undefined && !ran.isError && !/not mergeable|failed|error/i.test(ran.text ?? '')) startWatch($, await $.clock.now(), g.range)
    return ran
  }).catch(($, e, next) => next(e))

  // Before Claude says it's done: index.html changed this turn → a browser test after it; android/ changed → a Gradle run after it.
  on('classic.Stop', async ($, e, next) => {
    if (!isHere()) return next(e)
    const r = await next(e)
    const web = lastEdit > 0 && lastTest < lastEdit
    const droid = lastAndroid > 0 && lastGradle < lastAndroid
    if (r.block || e.stop_hook_active || (!web && !droid)) return r
    return { ...r, block: [web ? WEB_STOP : '', droid ? ANDROID_STOP : ''].filter(Boolean).join('\n') }
  }).catch(($, e, next) => next(e))
}
