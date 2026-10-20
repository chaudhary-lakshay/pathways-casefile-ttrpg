import type { Outcome, RollResult } from './model'
import { throwThree, type RandomSource } from './RandomSource'

/** Ch. 2, p. 7. Roll 3d6, succeed on equal or under. */
export const ALWAYS_SUCCEEDS_AT_OR_UNDER = 4
export const ALWAYS_FAILS_AT_OR_OVER = 17

export function printed(target: number): string {
  if (target < ALWAYS_SUCCEEDS_AT_OR_UNDER) return '3d6: a 3 or 4 only'
  if (target >= ALWAYS_FAILS_AT_OR_OVER) return `3d6 ≤ ${ALWAYS_FAILS_AT_OR_OVER - 1}, 17–18 fail`
  return `3d6 ≤ ${target}`
}

export const against = (target: number, source: RandomSource): RollResult => evaluate(throwThree(source, target), target)

export function evaluate(total: number, target: number): RollResult {
  const margin = target - total
  let outcome: Outcome
  if (total <= ALWAYS_SUCCEEDS_AT_OR_UNDER) outcome = 'CRITICAL_SUCCESS'
  else if (total >= ALWAYS_FAILS_AT_OR_OVER) outcome = 'CRITICAL_FAILURE'
  else if (margin <= -10) outcome = 'CRITICAL_FAILURE'
  else if (margin >= 0) outcome = 'SUCCESS'
  else outcome = 'FAILURE'
  return { total, target, margin, outcome }
}
