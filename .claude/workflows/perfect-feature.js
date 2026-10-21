export const meta = {
  name: 'perfect-feature',
  description: 'Perfect one Play next feature: map it, review it from 6 angles, prove each finding, list what was missed',
  whenToUse: 'The owner types /perfect <feature> (or "ultracode perfect <feature>"). Read-only: it finds and proves problems; the main chat fixes them.',
  phases: [
    { title: 'Map', detail: 'find the feature: code, CSS, scenarios, history, the owner\'s look calls' },
    { title: 'Review', detail: '6 lenses in parallel: bugs, speed, iPhone, look, touch, leftovers' },
    { title: 'Prove', detail: 'one skeptic per finding tries to reproduce or refute it' },
    { title: 'Missed', detail: 'what nobody checked' },
  ],
}

// args: "Queue card" or { feature: "Queue card", widths: "412,859,360" }
const FEATURE = typeof args === 'string' ? args : (args && args.feature) || ''
const WIDTHS = (args && args.widths) || '412,859,360'
if (!FEATURE) throw new Error('Pass the feature to perfect, e.g. args: "Queue card"')

const READONLY = `Read only: never edit index.html, sw.js, worker/ or any tracked file, never commit or push. ` +
  `You may run the kit (node tools/kit.mjs run|sweep|eye|perf|layers|smooth, tools/where.mjs, tools/map.mjs, tools/taste.mjs) ` +
  `and write throwaway probes as tools/scenarios/zz-<your-lens>-*.mjs (remove them with BLAST_OK=1 rm when done). ` +
  `Other reviewers run the kit at the same time on this machine, so timings are hints only (4x CPU numbers can double); ` +
  `prove speed problems with what runs (functions, forced layouts, GPU layers), not with milliseconds alone. ` +
  `Playwright: if node_modules lacks it, npm i --no-save playwright@1.56.1 (one at a time; if another reviewer is installing, wait). ` +
  `Never put game titles, scores or personal data in what you return.`

const MAP = {
  type: 'object',
  properties: {
    summary: { type: 'string', description: 'what the feature is and does, in plain words' },
    functions: { type: 'array', items: { type: 'string' }, description: 'function names with index.html line numbers, incl. extend() hooks and wrappers' },
    css: { type: 'array', items: { type: 'string' }, description: 'selectors / class names and their line ranges' },
    screens: { type: 'array', items: { type: 'string' }, description: 'where it shows and how to open it in a scenario (goTab, openFull, nxOpenSettings…)' },
    scenarios: { type: 'array', items: { type: 'string' }, description: 'tools/scenarios/*.mjs that cover it' },
    rules: { type: 'array', items: { type: 'string' }, description: 'CLAUDE.md rules and taste.md calls (T-ids) that apply to it' },
    history: { type: 'array', items: { type: 'string' }, description: 'docs/LOG.md versions and LESSONS ids about it; past fixes that did not hold' },
    issues: { type: 'array', items: { type: 'string' }, description: 'open GitHub issues about it (number + one line), if any' },
  },
  required: ['summary', 'functions', 'screens', 'rules'],
}

const FINDINGS = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string', description: 'one line, plain words' },
          kind: { type: 'string', enum: ['bug', 'speed', 'iphone', 'look', 'touch', 'leftover'] },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          where: { type: 'string', description: 'index.html:line (function) and the screen + width' },
          evidence: { type: 'string', description: 'how you saw it: the command and its output, the screenshot, the exact code path' },
          fix: { type: 'string', description: 'the smallest fix that keeps every house rule' },
          ownerCall: { type: 'boolean', description: 'true when it is a matter of taste or a new feature/look (the owner decides), false for a plain defect' },
        },
        required: ['title', 'kind', 'severity', 'where', 'evidence', 'fix', 'ownerCall'],
      },
    },
    checked: { type: 'array', items: { type: 'string' }, description: 'what you checked and found fine' },
  },
  required: ['findings', 'checked'],
}

const VERDICT = {
  type: 'object',
  properties: {
    real: { type: 'boolean' },
    reproduced: { type: 'boolean', description: 'you saw it happen (a run, a screenshot, a traced code path from a real tap)' },
    why: { type: 'string' },
    betterFix: { type: 'string', description: 'a smaller or safer fix, if the proposed one breaks a rule or misses the cause' },
  },
  required: ['real', 'reproduced', 'why'],
}

const LENSES = [
  { key: 'bugs', prompt: `Correctness. Walk every state the feature can be in: no games, one game, 60 games, a coming game, a released one, played, playing, no picture / a broken picture, no helper answer (offline), first open after an update, Settings switched off and on, folded 412 / unfolded 859 / 360. Look for wrong text ("undefined", NaN, a stale number), crashes, state that is not saved or saved into S when it should be its own localStorage key (v211), a layout read right after an innerHTML write (v206: node tools/where.mjs --list-v206), functions reassigned after extend(), state kept on a function (fn.x). Run the feature's scenarios and a sweep (node tools/kit.mjs sweep --widths ${WIDTHS}) and report every real problem.` },
  { key: 'speed', prompt: `Smoothness at 120Hz on the owner's Fold (the speed rule: only transform/opacity animate, no looping blur, no stacked effects, no scroll-linked motion on list rows, never read a hidden screen's layout). Time opening, closing, scrolling and the main taps of the feature (node tools/kit.mjs perf --widths 859; tools/scenarios/trace-stutter.mjs; tools/scenarios/layers.mjs for GPU layers; node tools/kit.mjs smooth). Name the function, forced layout or layer that costs the time, and a fix that keeps the look identical.` },
  { key: 'iphone', prompt: `The friend's iPhone 11 in Safari, installed to the home screen (notch, home bar, 414 wide). Use the ios-safari-quirks skill. Check the feature's code for things Safari does differently: touch and gesture events, :hover sticking, 100vh, safe-area insets, position: sticky inside overflow, backdrop-filter, WAAPI options, date parsing, Web Crypto / IndexedDB / storage limits, audio unlocking, scroll-snap, text-size-adjust and input zoom. This container has no WebKit: say which findings need webkit-check.yml (with its scenario input) to be proven, and name the scenario to run.` },
  { key: 'look', prompt: `The look. Load the play-next-design skill and read the owner's calls about this screen (node tools/taste.mjs list, and its --screen filter). Run the feature's scenarios with screenshots at ${WIDTHS} (SHOTS=<dir> where a scenario supports it, else write a zz- probe that opens the feature and calls p.screenshot) and Read every picture with your own eyes: text cut or overlapping, things off the screen edge, squashed pictures, uneven sizes, short words broken over two lines, faint text on the background, emoji used as icons, italic or uppercase headings, hex colours instead of the tokens. Also run node tools/kit.mjs eye and node tools/kit.mjs taste. A plain defect (cut, overlap, wrong state) is ownerCall false; a "this would look better" idea is ownerCall true.` },
  { key: 'touch', prompt: `Touch and feel on both phones, with real touch (p.tap / p.swipe in a scenario, CDP touch, not mouse). Every tap target at least 44px and not landing on a neighbour, double tap / hold / swipe doing what the feature says, nothing moving under the finger, no press animation or touch effect (#309, v280: node tools/kit.mjs run tools/scenarios/touch-empty.mjs --widths 412,859), back and close always work, nothing stuck open, Nexi and the rail not covering the feature unfolded, the What's new tour step for it (WHATS_NEW) pointing at a real element.` },
  { key: 'leftovers', prompt: `Code health of this feature only. Old versions of it still in the code (dead functions, CSS that never applies, Settings switches nothing reads), two features doing the same job on its screen, something hidden or switched off that still runs timers, listeners, observers or redraws, endless animations where nothing should move, the same function wrapped many times (node tools/audit.mjs, node tools/map.mjs --dead, node tools/where.mjs). Check every candidate by hand: some class names are built from strings. Name exactly what can go and what must stay.` },
]

phase('Map')
const map = await agent(
  `You are mapping one feature of Play next (index.html, a single-file web app) so six reviewers can check it.\n` +
  `Feature: "${FEATURE}".\n` +
  `Use docs/MAP.md, node tools/where.mjs <name>, grep of index.html, docs/LOG.md (grep the feature's names), docs/LESSONS.md, ` +
  `node tools/taste.mjs list, tools/scenarios/ and tools/README.md. Open issues: the GitHub MCP tools (mcp__github__list_issues on shamlawy/play-next, load them with ToolSearch) or skip if unavailable. ` +
  `If the name matches more than one feature, map the one the owner most likely means and say so in summary.\n${READONLY}`,
  { label: 'map', phase: 'Map', schema: MAP, effort: 'high' })

if (!map) throw new Error('Could not map the feature')
log(`Mapped: ${map.summary.slice(0, 120)}`)
const brief = `Feature: "${FEATURE}".\nWhat it is: ${map.summary}\nCode: ${map.functions.join('; ')}\nCSS: ${(map.css || []).join('; ')}\n` +
  `Where it shows: ${map.screens.join('; ')}\nScenarios: ${(map.scenarios || []).join('; ') || 'none yet'}\n` +
  `Rules and the owner's calls: ${map.rules.join('; ')}\nHistory: ${(map.history || []).join('; ')}\nOpen issues: ${(map.issues || []).join('; ') || 'none'}`

const results = await pipeline(
  LENSES,
  l => agent(
    `You review one Play next feature through one lens. Be thorough, but report only what you can show.\n\n${brief}\n\n` +
    `Your lens (${l.key}): ${l.prompt}\n\nWidths: ${WIDTHS}. ${READONLY}\n` +
    `Return every finding with evidence. Do not report a rule the code already keeps, and never propose a press animation, a touch glow, a moving background or a hover look outside @media (hover: hover) and (pointer: fine).`,
    { label: `review:${l.key}`, phase: 'Review', schema: FINDINGS, effort: 'high' }),
  (review, l) => parallel(((review && review.findings) || []).map((f, i) => () =>
    agent(
      `A reviewer of the Play next feature "${FEATURE}" claims a problem. Try hard to REFUTE it: check the code at the spot, ` +
      `run what is needed to see it happen (a scenario, a sweep, a screenshot you Read, a traced call from a real tap), ` +
      `and check it against CLAUDE.md and the owner's taste log (a "problem" that is a look the owner picked is not real). ` +
      `Default to real=false when you cannot show it.\n\nClaim (${f.kind}, ${f.severity}): ${f.title}\nWhere: ${f.where}\n` +
      `Evidence given: ${f.evidence}\nProposed fix: ${f.fix}\n\nFeature context:\n${brief}\n\n${READONLY}`,
      { label: `prove:${l.key}#${i + 1}`, phase: 'Prove', schema: VERDICT, effort: 'xhigh' })
      .then(v => ({ ...f, lens: l.key, verdict: v }))
  )).then(list => ({ lens: l.key, checked: (review && review.checked) || [], list }))
)

const done = results.filter(Boolean)
const all = done.flatMap(r => r.list.filter(Boolean))
const proven = all.filter(f => f.verdict && f.verdict.real)
const dropped = all.filter(f => !f.verdict || !f.verdict.real)
const lensesRan = done.map(r => r.lens)
const lensesLost = LENSES.map(l => l.key).filter(k => !lensesRan.includes(k))
if (lensesLost.length) log(`These lenses did not finish: ${lensesLost.join(', ')}`)
log(`${all.length} claimed, ${proven.length} proven, ${dropped.length} refuted`)

phase('Missed')
const missed = await agent(
  `Six reviewers checked the Play next feature "${FEATURE}". Find what nobody checked.\n\n${brief}\n\n` +
  `Checked and fine:\n${done.map(r => `${r.lens}: ${r.checked.join('; ')}`).join('\n')}\n\n` +
  `Proven problems:\n${proven.map(f => `- [${f.kind}] ${f.title} (${f.where})`).join('\n') || 'none'}\n\n` +
  `Lenses that did not finish: ${lensesLost.join(', ') || 'none'}\n\n` +
  `List states, widths, phones, taps or code paths that were never exercised, and check the two most likely to hide a defect yourself. ${READONLY}`,
  { label: 'missed', phase: 'Missed', schema: {
    type: 'object',
    properties: {
      notChecked: { type: 'array', items: { type: 'string' } },
      foundNow: FINDINGS.properties.findings,
    },
    required: ['notChecked', 'foundNow'],
  }, effort: 'high' })

const order = { high: 0, medium: 1, low: 2 }
const sortBySeverity = list => list.slice().sort((a, b) => order[a.severity] - order[b.severity])
const toFix = sortBySeverity(proven.filter(f => !f.ownerCall))
const ownerDecides = sortBySeverity(proven.filter(f => f.ownerCall))

return {
  feature: FEATURE,
  summary: map.summary,
  toFix: toFix.map(f => ({ title: f.title, kind: f.kind, severity: f.severity, where: f.where, evidence: f.evidence, fix: (f.verdict && f.verdict.betterFix) || f.fix, reproduced: !!(f.verdict && f.verdict.reproduced) })),
  ownerDecides: ownerDecides.map(f => ({ title: f.title, kind: f.kind, where: f.where, idea: f.fix })),
  needsWebkit: toFix.filter(f => f.kind === 'iphone' && !(f.verdict && f.verdict.reproduced)).map(f => f.title),
  refuted: dropped.map(f => `${f.title}: ${(f.verdict && f.verdict.why) || 'no verdict'}`),
  notChecked: (missed && missed.notChecked) || ['the completeness check did not finish'],
  foundByMissed: (missed && missed.foundNow) || [],
  lensesLost,
}
