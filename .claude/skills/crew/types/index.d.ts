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
}

declare module 'claude-code' {
  interface PluginState {
    crew: { helpers: Helper[]; now: number; since: number; showDone: boolean }
  }
}
