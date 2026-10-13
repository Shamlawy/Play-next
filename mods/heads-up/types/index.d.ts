// One reply, boiled down to what needs the person.
export type Card = {
  id: string
  at: number
  // The person's message it answered, cut short.
  ask: string
  // One line: what Claude did.
  gist: string
  // Problems, risks, questions Claude asked, decisions waiting on the person.
  attention: string[]
  // Things only the person can do.
  actions: string[]
  // Suggestions Claude made but didn't carry out.
  ideas: string[]
  // Ticked items, by key (`a3` = actions[3], `n0` = attention[0]).
  done: string[]
}

declare module 'claude-code' {
  interface PluginState {
    'heads-up': { cards: Card[]; ask: string; busy: boolean; showOld: boolean; last: string }
  }
}
