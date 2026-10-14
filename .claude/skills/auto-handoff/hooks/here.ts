// Cloud chats skip mods kept in a repo's .claude/skills/ (the folder is never "trusted" there), so the
// environment's setup script installs this mod for every repo. It is on only in a repo that carries it
// (.claude/skills/<name>): session.start checks, and every other hook passes straight through elsewhere.
// Its commands are registered only where it is on.
// The check itself (the $.fs / $.store / $.ui calls) lives in each register.ts: the engine follows $ only into
// functions of the same file, never across an import, so everything here is plain values.
export const PLAY_NEXT_MODS = ['release-guard', 'learn', 'bug-inbox', 'auto-handoff']

let on = true // until session.start says this repo doesn't carry it
let note = ''

export const isHere = () => on

// The repo root (no trailing slash) and the folder whose presence turns the mod on; '' without a cwd.
export const hereRoot = (cwd: string) => String(cwd ?? '').replace(/[\\/]+$/, '')
export const herePath = (cwd: string, name: string) => {
  const root = hereRoot(cwd)
  return root ? `${root}/.claude/skills/${name}` : ''
}

// What session.start found; `told` = the announcing note for the first message, if any.
export function setHere(found: boolean, told = ''): boolean {
  on = found
  note = told
  return on
}

// `announce`: in a repo without it, say so once per project (a toast now, a note on the first message), so
// the person can ask for these mods there too. One mod of the family announces for all of them.
export const toldKey = (root: string) => `told:${root}`
export const announceNote = (name: string) =>
  `[${name}] Heads-up for the person: this project doesn't carry the Play next mods (${PLAY_NEXT_MODS.join(', ')}), so they are off here. Tell them in one short line, and offer to set this project up with them (copy each mod's folder from Shamlawy/Play-next's .claude/skills/ into this repo's .claude/skills/) if they want it.`
export const ANNOUNCE_TOAST = `The ${PLAY_NEXT_MODS.length} Play next mods are off in this project. Ask Claude to add them here if you want them.`

// The first message after an announcing check carries its note.
export function withNote<E extends { context?: readonly string[] }>(e: E): E {
  if (!note) return e
  const text = note
  note = ''
  return { ...e, context: [...(e.context ?? []), text] }
}
