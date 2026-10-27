import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'
import { herePath, isHere, setHere } from './here.ts'

// Chat size (tokens the last reply was answered over) where the chat moves on by itself.
const LIMIT = 300_000

const HANDOFF_ASK = /^\s*\/?(hand\s*off|new\s+chat|fresh\s+chat|move\s+to\s+a\s+new\s+chat)\s*[.!]?\s*$/i

// Once per chat (session state survives a mod reload), so a long chat never spawns two new ones.
const done = atom({ plugin: 'auto-handoff', key: 'done' } as const, false)

const STEPS = [
  '1. Record what this chat learned that the next one should know (node tools/learn.mjs add --kind=mistake|idea --anchors=… "…"), then make sure every change, docs/LESSONS.md included, is committed and pushed (say so plainly if something can\'t be).',
  '2. Write a handoff note (under 300 words) for the new chat, its first line "Handoff note from an earlier chat" (so the new chat\'s mods know these are not the person\'s words): the goal, what is done (commits, APP_V), what is left next, open questions, the branch, and gotchas learned in this chat. Don\'t repeat what CLAUDE.md already says.',
  // create_session has no effort field, so a new chat opened at the app's default (high) though this one ran at
  // xhigh (the owner, Oct 27); a first message of /effort <level> sets it there (tested: "Set effort level to xhigh")
  '3. Start the new cloud session at this chat\'s effort: read this chat\'s level with the claude-code-remote get_session tool (no session_id; session_context.effort_level, else external_metadata.effort_level; xhigh when neither is set, the person\'s usual), then create_session with source_url = this repo\'s GitHub URL, title starting "Play next · ", prompt = "/effort <that level>" and nothing else, then send_message to the new session_id with the handoff note.',
  '4. End with the new chat\'s title and a 2-line summary, and say this chat can be left now.',
  'If create_session is not available, give the note as one block they can paste into a new chat.',
].join('\n')

const k = (n: number) => `${Math.round(n / 1000)}k`

// On only in a repo that carries this mod (see here.ts); kept in this file, as $ never crosses an import.
async function checkHere($: EngineInterface, cwd: string) {
  const path = herePath(cwd, 'auto-handoff')
  return setHere(path ? await $.fs.exists(path).catch(() => false) : false)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    if (!(await checkHere($, e.cwd))) return next(e)
    await $.command.register({ name: 'handoff', description: 'Write a handoff note and start a fresh chat with it.' })
    return next(e)
  })

  on('command.run', { command: 'handoff' }, async $ => {
    void $.prompt.submit({ text: 'handoff', asUser: true })
    return { text: 'Handing off to a fresh chat…' }
  })

  on('prompt.submit', async ($, e, next) => {
    if (!isHere()) return next(e)
    // A failed reading must never hold up the message.
    const tokens = await $.session.usage().then(u => u.context.tokens ?? 0).catch(() => 0)
    let note = ''
    if (HANDOFF_ASK.test(e.text)) {
      note = `[auto-handoff] The person wants to move to a fresh chat. Do this:\n${STEPS}`
    } else if (tokens >= LIMIT && !(await read($, done))) {
      note = `[auto-handoff] This chat is ${k(tokens)} tokens, past the 300k line, so it moves to a fresh chat by itself (the person asked for this; don't ask first). First answer their message as usual, finishing any work it asks for. Then, in the same reply:\n${STEPS}`
    }
    if (!note) return next(e)
    await update($, done, () => true)
    return next({ ...e, context: [...(e.context ?? []), note] })
  })
}
