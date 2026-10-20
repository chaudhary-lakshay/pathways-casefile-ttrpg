import { error, require } from './kt'
import type { PoolThreshold } from './content/schema.gen'
import type { Trait } from './model'
import * as Pools from './Pools'
import type { PoolStatus } from './Pools'
import * as TraitAttributeRolls from './TraitAttributeRolls'

export type VitalCheck = 'CONSCIOUSNESS' | 'SURVIVAL'

const CONSCIOUSNESS_ROW = '0 HP'

export function due(hp: PoolStatus): VitalCheck[] {
  const out: VitalCheck[] = []
  if (hp.rollsToStayConscious) out.push('CONSCIOUSNESS')
  if (hp.rollsToSurvive) out.push('SURVIVAL')
  return out
}

export const bonusFor = (check: VitalCheck, traits: Trait[]) =>
  TraitAttributeRolls.htRollBonus(traits) +
  (check === 'SURVIVAL' ? TraitAttributeRolls.deathRollBonus(traits) : TraitAttributeRolls.consciousnessRollBonus(traits))

export const targetFor = (check: VitalCheck, ht: number, traits: Trait[], poolPenalty: number, shock = 0, states = 0) =>
  ht + bonusFor(check, traits) + poolPenalty + shock + states

export const printed = (check: VitalCheck, hp: PoolStatus) => row(check, hp).effect
export const rowFor = (check: VitalCheck, hp: PoolStatus) => row(check, hp).level

function row(check: VitalCheck, hp: PoolStatus): PoolThreshold {
  require(hp.pool === 'HP', () => `a vital roll reads the HP table, not ${hp.pool}`)
  require(check === 'CONSCIOUSNESS' || hp.rollsToSurvive, () => `no survival roll is due at ${hp.level}`)
  const level = check === 'CONSCIOUSNESS' ? CONSCIOUSNESS_ROW : hp.level
  return Pools.thresholds('HP').find((it) => it.level === level) ?? error(`the HP table prints no row for ${check} at ${level}`)
}
