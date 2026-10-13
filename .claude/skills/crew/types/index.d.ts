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
  tokens: number
  costUsd: number
  context: number
  // The helper's own task list, when it keeps one (TaskCreate/TodoWrite inside its loop).
  todo?: Step[]
}

declare module 'claude-code' {
  interface PluginState {
    crew: { helpers: Helper[]; now: number; since: number; showDone: boolean; compact: boolean; plan: Step[]; job: string }
  }
}
