export type Finding = { severity: 'block' | 'note'; file: string; what: string; fix: string }
export type Review = { sha: string; at: number; findings: Finding[]; failed?: string }

declare module 'claude-code' {
  interface PluginState {
    'second-look': { last: Review | null }
  }
}
