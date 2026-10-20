import { isSuccess, type RollResult, type Trait } from './model'
import { error } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { FrightRecovery, MajorWound } from './content/schema.gen'
import * as TraitAttributeRolls from './TraitAttributeRolls'

const ROW = 'Failure'
export const COMBAT_REFLEXES_BONUS = 6

export const stunsOn = (row: MajorWound) => row.result === ROW

export const leavesStunned = (row: MajorWound, recovery: RollResult | null) =>
  stunsOn(row) && (recovery === null || stillStunned(recovery))

export const stillStunned = (recovery: RollResult) => !isSuccess(recovery.outcome)

export const printed = (): string => row().effect

export const isStun = (recovery: FrightRecovery) => recovery.state === 'STUNNED'

export const bonusFor = (traits: Trait[]) =>
  TraitAttributeRolls.htRollBonus(traits) + TraitAttributeRolls.stunRecoveryBonus(traits)

export const targetFor = (ht: number, traits: Trait[], poolPenalty: number, shock: number, states: number) =>
  ht + bonusFor(traits) + poolPenalty + shock + states

function row(): MajorWound {
  return ContentLoader.majorWound().find(stunsOn) ?? error(`the Major Wound table prints no "${ROW}" row to recover from`)
}
