import { error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { ComputedDuration, FrightEffect, FrightRecovery, FrightTrigger } from './content/schema.gen'
import { isSuccess, type RollResult, type Trait } from './model'
import { threeDSix, type RandomSource } from './RandomSource'
import * as StunRecovery from './StunRecovery'
import * as SuccessRoll from './SuccessRoll'
import * as TraitAttributeRolls from './TraitAttributeRolls'

export type FrightKind = 'FRIGHT' | 'AWE' | 'CONFUSION'
export const FRIGHT_KINDS: FrightKind[] = ['FRIGHT', 'AWE', 'CONFUSION']

export interface FrightOutcome {
  roll: RollResult
  target: number
  margin: number
  effectRoll: number | null
  effect: FrightEffect | null
}

export const passed = (o: FrightOutcome) => o.effect === null

export const LOWEST_EFFECT_ROLL = 4

export const triggers = (): FrightTrigger[] => ContentLoader.frightTriggers()

export const targetFor = (will: number, modifier: number) => will + modifier

export const against = (will: number, modifier: number, source: RandomSource, effects = ContentLoader.frightEffects()) =>
  rollAgainst(targetFor(will, modifier), source, effects)

export function rollAgainst(
  target: number,
  source: RandomSource,
  effects: FrightEffect[] = ContentLoader.frightEffects(),
  effectSource: RandomSource = source,
): FrightOutcome {
  const roll = SuccessRoll.against(target, source)
  if (isSuccess(roll.outcome)) return { roll, target, margin: 0, effectRoll: null, effect: null }
  const margin = Math.max(roll.total - target, 0)
  const effectRoll = Math.max(threeDSix(effectSource) + margin, LOWEST_EFFECT_ROLL)
  return { roll, target, margin, effectRoll, effect: effectFor(effectRoll, effects) }
}

export function effectFor(roll: number, effects: FrightEffect[] = ContentLoader.frightEffects()): FrightEffect {
  require(roll >= LOWEST_EFFECT_ROLL, () =>
    `the fright table starts at ${LOWEST_EFFECT_ROLL} (3d6 plus a margin of at least 1); was ${roll}`)
  return effects.findLast((it) => roll >= it.low && (it.high === null || roll <= it.high)) ?? error(`no fright band covers ${roll}`)
}

export function text(effect: FrightEffect, kind: FrightKind): string {
  switch (kind) {
    case 'FRIGHT': return effect.fright
    case 'AWE': return effect.awe
    case 'CONFUSION': return effect.confusion
  }
}

const QUIRK_CELL = 'Acquire a quirk:'
const DISADVANTAGE_CELL = 'New −'
export const grantsQuirk = (effect: FrightEffect, kind: FrightKind) => text(effect, kind).startsWith(QUIRK_CELL)
export const grantsDisadvantage = (effect: FrightEffect, kind: FrightKind) => text(effect, kind).startsWith(DISADVANTAGE_CELL)

export function recoveryFor(effect: FrightEffect, kind: FrightKind): FrightRecovery | null {
  switch (kind) {
    case 'FRIGHT': return effect.frightRecovery
    case 'AWE': return effect.aweRecovery
    case 'CONFUSION': return effect.confusionRecovery
  }
}

export function leavesStunned(effect: FrightEffect | null, kind: FrightKind, recovery: RollResult | null): boolean {
  const r = effect === null ? null : recoveryFor(effect, kind)
  return r !== null && StunRecovery.isStun(r) && !(recovery !== null && isSuccess(recovery.outcome))
}

export function durationFor(effect: FrightEffect, kind: FrightKind): ComputedDuration | null {
  switch (kind) {
    case 'FRIGHT': return effect.frightComputedDuration
    case 'AWE': return effect.aweComputedDuration
    case 'CONFUSION': return effect.confusionComputedDuration
  }
}

const DURATION_PRINTED = { HT: 'HT', WILL: 'Will', IQ: 'IQ' } as const

export const secondsFor = (d: ComputedDuration, ht: number, will: number, iq: number) =>
  Math.max(d.base - levelOf(d, ht, will, iq), 0)

export const durationLine = (d: ComputedDuration, ht: number, will: number, iq: number) =>
  `${secondsFor(d, ht, will, iq)} sec (${d.base} − ${DURATION_PRINTED[d.attribute]} ${levelOf(d, ht, will, iq)})`

const levelOf = (d: ComputedDuration, ht: number, will: number, iq: number) =>
  d.attribute === 'HT' ? ht : d.attribute === 'WILL' ? will : iq

export const recoveryBonus = (recovery: FrightRecovery, traits: Trait[]) =>
  StunRecovery.isStun(recovery) ? TraitAttributeRolls.stunRecoveryBonus(traits) : 0

export const levelFor = (recovery: FrightRecovery, will: number, iq: number) => (recovery.attribute === 'WILL' ? will : iq)

export const takesWillClauses = (recovery: FrightRecovery) => recovery.attribute === 'WILL'
