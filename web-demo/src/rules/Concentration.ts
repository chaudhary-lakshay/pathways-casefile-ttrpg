import type { RollResult } from './model'
import { first, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import * as SuccessRoll from './SuccessRoll'
import type { RandomSource } from './RandomSource'

export interface ConcentrationOutcome {
  target: number
  roll: RollResult
}

export const outcomeHeld = (o: ConcentrationOutcome) => o.roll.outcome === 'SUCCESS' || o.roll.outcome === 'CRITICAL_SUCCESS'

export const PENALTY = -3
const ROW = 'Concentrate'

export const printed = (): string => first(ContentLoader.maneuvers(), (it) => it.maneuver === ROW).description

export const targetFor = (will: number, shock = 0, hurt = 0, fromTraits = 0) => will + PENALTY + shock + hurt + fromTraits

export const attempt = (will: number, disturbed: boolean, source: RandomSource, shock = 0, hurt = 0, fromTraits = 0) =>
  rollAgainst(targetFor(will, shock, hurt, fromTraits), disturbed, source)

export function rollAgainst(target: number, disturbed: boolean, source: RandomSource): ConcentrationOutcome {
  require(disturbed, () => `the book calls for no roll here: ${printed()}`)
  return { target, roll: SuccessRoll.against(target, source) }
}
