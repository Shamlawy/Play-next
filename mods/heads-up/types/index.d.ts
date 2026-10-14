// One reply, boiled down to what needs the person. Only the newest reply's card is kept (0.8).
export type Card = {
  id: string
  at: number
  // At most 8 words: what Claude did.
  gist: string
  // Problems, risks, questions Claude asked, decisions waiting on the person.
  attention: string[]
  // Things only the person can do.
  actions: string[]
  // Suggestions Claude made but didn't carry out.
  ideas: string[]
  // Ticked items, by key (`a1` = actions[1], `n0` = attention[0]).
  done: string[]
  // Options the reply laid out for the person to pick from: one (multi false) or several; none is always allowed.
  // picked = the tapped options' indexes; sent = what went back to Claude once they sent it.
  choice?: { question: string; multi: boolean; options: { label: string }[]; suggested?: number; picked: number[]; sent?: string }
}

declare module 'claude-code' {
  interface PluginState {
    'heads-up': { cards: Card[]; ask: string; busy: boolean; last: string; showDone: boolean }
  }
}
