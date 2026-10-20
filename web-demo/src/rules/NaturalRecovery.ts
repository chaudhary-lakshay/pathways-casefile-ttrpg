import { substringAfter, substringBefore } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { HpRecoveryRow } from './content/schema.gen'
import type { Trait } from './model'
import type { PoolStatus } from './Pools'
import * as TraitAttributeRolls from './TraitAttributeRolls'
import * as TraitEffects from './TraitEffects'

export const UNMODELLED = new Map([
  ['First Aid',
    '"1 HP + stops bleeding ... 1 minute; once per wound" -- a skill ' +
    'roll the sheet offers, from Skills or from the untrained list, and the ' +
    'HP stepper applies the point. The minute, the bleeding and the ' +
    "once are the GM's: this app tracks a pool and no wounds"],
  ['Physician',
    '"1 HP (2 HP on critical success) ... Per day of bed rest" -- ' +
    'a skill roll the sheet offers, from Skills or from the untrained list, ' +
    'and the HP stepper applies it. "skill 12+", the day and the ' +
    "equipment are the GM's"],
  ['Healing advantage',
    '"2 HP (costs 1 FP) ... Concentration + IQ roll" -- an ' +
    'advantage no catalogue prints, so no character can hold it'],
  ['Regeneration',
    'five rates, "1 HP per 12 hours" down to "10 HP per second" ' +
    '-- restored "without rolls", so all that is left is a clock, ' +
    'and this app holds none. No catalogue prints the advantage; ' +
    'Chained Sequences 7 and 6 grant it as prose'],
])

export const rows = (): HpRecoveryRow[] => ContentLoader.hpRecovery()
export const isOffered = (hp: PoolStatus) => hp.current < hp.max && !hp.isDead
export const bonusFor = (traits: Trait[]) => TraitAttributeRolls.htRollBonus(traits)

const FAST_HEALER_CLAUSE = 'Recover 1 additional HP per day of rest'

export function dayLine(success: boolean, traits: Trait[]): string {
  const healer = TraitEffects.healsFast(traits)
  if (success && healer) {
    return `${restore()}, and 1 more for Fast Healer ("${FAST_HEALER_CLAUSE}"). ${bands()} ` +
      "Which band applies, and whether it scales that point, is the GM's."
  }
  if (success) return `${restore()}. ${bands()} Which band applies is the GM's.`
  if (healer) {
    return `Nothing is restored by the roll. Whether Fast Healer's "${FAST_HEALER_CLAUSE}" ` +
      "reaches a day the roll failed is the GM's call."
  }
  return 'Nothing is restored for this day.'
}

const restore = () => `Restore ${rows()[0].recovered} on the HP row`
const bands = () =>
  rows().slice(1).map((it) => `${substringBefore(substringAfter(it.method, '('), ')')}: ${it.recovered}`).join('; ') + '.'

export const targetFor = (ht: number, traits: Trait[], poolPenalty: number, gmModifier: number, states = 0) =>
  ht + bonusFor(traits) + poolPenalty + gmModifier + states
