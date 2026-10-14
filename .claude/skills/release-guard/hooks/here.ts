import type { EngineInterface } from 'claude-code'

// Cloud chats skip mods kept in a repo's .claude/skills/ (the folder is never "trusted" there), so the
// environment's setup script installs this mod for every repo. It is on only in a repo that carries it
// (.claude/skills/<name>): session.start checks, and every other hook passes straight through elsewhere.
// Its commands are registered only where it is on.
export const PLAY_NEXT_MODS = ['release-guard', 'learn', 'bug-inbox', 'auto-handoff']

let on = true // until session.start says this repo doesn't carry it
let note = ''

export const isHere = () => on

// `announce`: in a repo without it, say so once per project (a toast now, a note on the first message), so
// the person can ask for these mods there too. One mod of the family announces for all of them.
export async function checkHere($: EngineInterface, cwd: string, name: string, announce = false): Promise<boolean> {
  const root = String(cwd ?? '').replace(/[\\/]+$/, '')
  on = root ? await $.fs.exists(`${root}/.claude/skills/${name}`).catch(() => false) : false
  note = ''
  if (!on && announce && root) {
    const key = `told:${root}`
    if (!(await $.store.get(key).catch(() => true))) {
      await $.store.set(key, true).catch(() => undefined)
      note = `[${name}] Heads-up for the person: this project doesn't carry the Play next mods (${PLAY_NEXT_MODS.join(', ')}), so they are off here. Tell them in one short line, and offer to set this project up with them (copy each mod's folder from Shamlawy/Play-next's .claude/skills/ into this repo's .claude/skills/) if they want it.`
      $.ui.toast(`The ${PLAY_NEXT_MODS.length} Play next mods are off in this project. Ask Claude to add them here if you want them.`)
    }
  }
  return on
}

// The first message after an announcing check carries its note.
export function withNote<E extends { context?: readonly string[] }>(e: E): E {
  if (!note) return e
  const text = note
  note = ''
  return { ...e, context: [...(e.context ?? []), text] }
}
