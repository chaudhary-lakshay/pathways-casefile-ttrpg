import type { Character, Trait } from './model'
import { coerceIn, eqIc, error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { CorGain, RitualCorThreshold } from './content/schema.gen'
import * as Derive from './Derive'
import * as ScoreFloors from './ScoreFloors'

export const FORTITUDE = 'Spiritual Fortitude'
export const FORTITUDE_BONUS = 3
export const RITUAL_INSTABILITY_PENALTY = -1

export const gained = (): CorGain[] => ContentLoader.corGained()

export function drainedUse(currentSpi: number): number {
  if (currentSpi > 0) return 0
  return gained().find((it) => it.action.startsWith(DRAINED_USE))?.minimum ??
    error(`cor_gained.json has no priced row starting '${DRAINED_USE}'`)
}

export const soulDebt = (currentSpi: number, price: number) =>
  price <= 0 ? 0 : Math.max(Math.max(0, price - currentSpi) - Math.max(0, -currentSpi), 0)

export const DRAINED_USE = 'Using powers while drained'

/** `maxFor(character)`. */
export const maxForCharacter = (character: Character) => maxFor(Derive.of(character).secondaries.will, character.traits)

export function maxFor(will: number, traits: Trait[]): number {
  const warded = traits.some((it) => eqIc(it.name, FORTITUDE))
  return will + (warded ? FORTITUDE_BONUS : 0)
}

export function clamp(cor: number, max: number): number {
  requireWillScore(max)
  return coerceIn(cor, 0, max)
}

export function isLostAt(cor: number, max: number): boolean {
  requireWillScore(max)
  return cor >= max
}

export const isLostAfter = (cor: number, delta: number, max: number) => isLostAt(clamp(cor + delta, max), max)

export const ritualThresholdsReached = (cor: number, thresholds: RitualCorThreshold[] = ContentLoader.ritualCorThresholds()) =>
  thresholds.filter((it) => it.cor <= cor)

export function ritualPenalty(cor: number, thresholds: RitualCorThreshold[] = ContentLoader.ritualCorThresholds()): number {
  if (thresholds.length === 0) return 0
  const instability = Math.min(...thresholds.map((it) => it.cor))
  return cor >= instability ? RITUAL_INSTABILITY_PENALTY : 0
}

function requireWillScore(max: number) {
  require(ScoreFloors.holdsCeiling(max), () => `maximum CoR is a Will score; was ${max}`)
}
