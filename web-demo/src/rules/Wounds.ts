import { isSuccess, Modifier, type RollResult } from './model'
import { eqIc, error, floorDiv, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { HitLocation, HitLocationThreshold, MajorWound, WoundingMultiplier } from './content/schema.gen'
import * as ScoreFloors from './ScoreFloors'
import type { Pool } from './Pools'
import type { TraitClause } from './TraitEffects'

export const locations = (): HitLocation[] => ContentLoader.hitLocations()
export const damageTypes = (): WoundingMultiplier[] => ContentLoader.woundingMultipliers()

export function locationNamed(target: string): HitLocation {
  const found = locations().find((it) => eqIc(it.target, target))
  require(found !== undefined, () => `no hit location named '${target}'; the book prints ${locations().map((it) => it.target).join(', ')}`)
  return found
}

export function typeMultiplier(type: string): number {
  const row = damageTypes().find((it) => eqIc(it.abbreviation, type))
  if (row !== undefined) return row.multiplier
  const fromAppendix = appendixMultiplier(type)
  require(fromAppendix !== null, () => `no damage type is abbreviated '${type}'; this book prints ${knownAbbreviations().join(', ')}`)
  return fromAppendix
}

const appendixMultiplier = (type: string): number | null =>
  ContentLoader.damageAbbreviations().find((it) => eqIc(it.abbreviation, type))?.multiplier ?? null

export function knownAbbreviations(): string[] {
  const chapterFive = damageTypes().flatMap((it) => (it.abbreviation !== null ? [it.abbreviation] : []))
  const appendixOnly = ContentLoader.damageAbbreviations()
    .flatMap((it) => (it.abbreviation !== null ? [it.abbreviation] : []))
    .filter((it) => !chapterFive.includes(it))
  return [...chapterFive, ...appendixOnly]
}

export const knowsType = (type: string) =>
  damageTypes().some((it) => eqIc(it.abbreviation, type)) || appendixMultiplier(type) !== null

export function multiplierFor(location: HitLocation | null, type: string): number {
  const ofType = typeMultiplier(type)
  if (location === null) return ofType
  return ofType * (location.byType[type.toLowerCase()] ?? location.multiplier)
}

export function injuryFor(penetrating: number, location: HitLocation | null, type: string): number {
  require(penetrating >= 0, () => `penetrating damage cannot be negative; was ${penetrating}`)
  return Math.floor(penetrating * multiplierFor(location, type))
}

export const injuryTo = (pool: Pool, penetrating: number, location: HitLocation | null, type: string | null) =>
  pool === 'HP' && type !== null ? injuryFor(penetrating, location, type) : penetrating

export const SPIRIT_DAMAGE = 'Spirit body damage'

export const spiritNextRoll = (points: number): TraitClause | null =>
  points > 0 ? { trait: SPIRIT_DAMAGE, shown: SPIRIT_DAMAGE, printed: `-${points} to the next roll`, value: -points, conditional: false } : null

export function modifier(location: HitLocation): Modifier | null {
  if (location.penalty === 0) return null
  return Modifier({
    id: `wounds:${location.target.toLowerCase()}`,
    source: { type: 'Situational', description: `${location.target} (called shot)` },
    target: { type: 'AllRolls' },
    effect: { type: 'Flat', n: location.penalty },
    layer: 'SITUATIONAL',
  })
}

export const majorWounds = (): MajorWound[] => ContentLoader.majorWound()

export function majorWoundThreshold(maxHp: number): number {
  require(maxHp >= ScoreFloors.MIN_POOL_MAX, () => `HP must be at least ${ScoreFloors.MIN_POOL_MAX}; was ${maxHp}`)
  return floorDiv(maxHp, 2) + 1
}

export const isMajorWound = (damage: number, maxHp: number) => damage >= majorWoundThreshold(maxHp)

export function majorWoundFor(roll: RollResult): MajorWound {
  const rows = majorWounds()
  if (isSuccess(roll.outcome)) return rows[0]
  const by = rows[2].failBy ?? error("the 'Fail by' row prints no margin")
  return -roll.margin >= by ? rows[2] : rows[1]
}

export const render = (multiplier: number) =>
  multiplier === Math.floor(multiplier) ? `×${multiplier}` : `×${multiplier}`

export const cripplingRule = (): string => ContentLoader.cripplingRule()

export function thresholdFor(location: HitLocation, outcome: string, maxHp: number): number | null {
  require(maxHp >= ScoreFloors.MIN_POOL_MAX, () => `HP must be at least ${ScoreFloors.MIN_POOL_MAX}; was ${maxHp}`)
  const row = location.thresholds.find((it) => it.outcome === outcome)
  if (row === undefined) return null
  if (row.minDamage !== null) return row.minDamage
  if (row.hpDivisor !== null) return floorDiv(maxHp, row.hpDivisor) + 1
  return 1
}

export const effectsAt = (location: HitLocation, damage: number, maxHp: number): HitLocationThreshold[] =>
  location.thresholds.filter((row) => {
    const at = thresholdFor(location, row.outcome, maxHp) ?? error(`${location.target} lost the threshold for ${row.outcome}`)
    return damage >= at
  })
