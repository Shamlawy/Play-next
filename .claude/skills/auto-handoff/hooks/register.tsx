import { atom, read, update } from 'claude-code'
import type { Register } from 'claude-code'

// Chat size (tokens the last reply was answered over) where the chat moves on by itself.
const LIMIT = 300_000

const HANDOFF_ASK = /^\s*\/?(hand\s*off|new\s+chat|fresh\s+chat|move\s+to\s+a\s+new\s+chat)\s*[.!]?\s*$/i

// Once per chat (session state survives a mod reload), so a long chat never spawns two new ones.
const done = atom({ plugin: 'auto-handoff', key: 'done' } as const, false)

const STEPS = [
  '1. Make sure every change is committed and pushed (say so plainly if something can\'t be).',
  '2. Write a handoff note (under 300 words) for the new chat: the goal, what is done (commits, APP_V), what is left next, open questions, the branch, and gotchas learned in this chat. Don\'t repeat what CLAUDE.md already says.',
  '3. Start the new cloud session with the claude-code-remote create_session tool: source_url = this repo\'s GitHub URL, title starting "Play next · ", prompt = the handoff note.',
  '4. End with the new chat\'s title and a 2-line summary, and say this chat can be left now.',
  'If create_session is not available, give the note as one block they can paste into a new chat.',
].join('\n')

const k = (n: number) => `${Math.round(n / 1000)}k`

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({ name: 'handoff', description: 'Write a handoff note and start a fresh chat with it.' })
    return next(e)
  })

  on('command.run', { command: 'handoff' }, async $ => {
    void $.prompt.submit({ text: 'handoff', asUser: true })
    return { text: 'Handing off to a fresh chat…' }
  })

  on('prompt.submit', async ($, e, next) => {
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
