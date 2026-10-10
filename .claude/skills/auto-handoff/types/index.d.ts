export type Done = boolean

declare module 'claude-code' {
  interface PluginState {
    'auto-handoff': { done: Done }
  }
}
