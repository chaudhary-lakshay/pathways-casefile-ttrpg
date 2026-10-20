import { useSyncExternalStore } from 'react'
import type { Cell } from '../app/RollLog'

interface Versioned {
  subscribe: (l: () => void) => () => void
  getVersion: () => number
}

/** Re-renders on every change of a session, the saves or the roll log. */
export const useVersion = (source: Versioned) => useSyncExternalStore(source.subscribe, source.getVersion)

/** `by mutableStateOf`: a Cell's value, re-rendered on set. */
export function useCell<T>(cell: Cell<T>): [T, (v: T) => void] {
  const value = useSyncExternalStore(cell.subscribe, cell.get)
  return [value, cell.set]
}
