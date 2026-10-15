export type Step = { id: string; subject: string; status: 'pending' | 'in_progress' | 'completed' }

export type Helper = {
  id: string
  title: string
  type: string
  model: string
  effort?: string
  startedAt: number
  endedAt?: number
  status: 'running' | 'done' | 'failed'
  steps: number
  lastTool?: string
  // 0.3: what it was asked (the first line of its prompt), what it last said while working, its last few steps in
  // plain words ("Reading index.html") and the first line of its report.
  task?: string
  said?: string
  recent?: string[]
  result?: string
  // When it last did or said anything: a helper silent for 10 minutes no longer holds the panel's auto-open.
  seenAt?: number
  // The helper's own task list, when it keeps one (TaskCreate/TodoWrite inside its loop).
  todo?: Step[]
}

declare module 'claude-code' {
  interface PluginState {
    crew: { helpers: Helper[]; now: number; since: number; showDone: boolean; compact: boolean; plan: Step[]; job: string }
  }
}
