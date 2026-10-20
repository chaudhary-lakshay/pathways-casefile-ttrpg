import type { Trait } from './model'
import { lazy, sumOf } from './kt'
import { levelOf } from './TraitNames'
import {
  ALERTNESS, ALERTNESS_PER_LEVEL, bespokeOf, BespokeBonus, COMBAT_REFLEXES, ETHER_BODY_DAMAGE, FIT, FIT_BONUS,
  HIGH_PAIN_THRESHOLD, UNFIT, UNFIT_PENALTY, VERY_FIT, VERY_FIT_BONUS,
} from './TraitEffects'
import * as StunRecovery from './StunRecovery'

export interface MajorWoundHt {
  bonus: number
  line: string | null
}

export const alertnessBonus = (traits: Trait[]) => (levelOf(traits, ALERTNESS) ?? 0) * ALERTNESS_PER_LEVEL

export const HT_ROLL_TRAITS = lazy(() => new Map<string, number>([
  [FIT, FIT_BONUS],
  [VERY_FIT, VERY_FIT_BONUS],
  [UNFIT, -UNFIT_PENALTY],
]))

export const htRollNames = () => new Set(HT_ROLL_TRAITS().keys())

export const SPI_ROLL_TRAITS = lazy(() => new Map<string, number>([[ETHER_BODY_DAMAGE, -1]]))

export const spiRollNames = () => new Set(SPI_ROLL_TRAITS().keys())

export const spiRollBonus = (traits: Trait[]) =>
  sumOf([...SPI_ROLL_TRAITS()], ([trait, value]) => (levelOf(traits, trait) !== null ? value : 0))

export const knockdownBonus = (traits: Trait[]) => bespokeOf(BespokeBonus.KNOCKDOWN, traits)
export const deathRollBonus = (traits: Trait[]) => bespokeOf(BespokeBonus.DEATH, traits)
export const consciousnessRollBonus = (traits: Trait[]) => bespokeOf(BespokeBonus.CONSCIOUSNESS, traits)

const signed = (value: number) => (value < 0 ? `−${-value}` : `+${value}`)

export function majorWoundHt(traits: Trait[]): MajorWoundHt {
  const parts: string[] = []
  const knockdown = knockdownBonus(traits)
  if (knockdown !== 0) parts.push(`+${knockdown} from ${HIGH_PAIN_THRESHOLD}`)
  for (const [trait, value] of HT_ROLL_TRAITS()) {
    if (levelOf(traits, trait) === null) continue
    parts.push(`${signed(value)} from ${trait}`)
  }
  if (parts.length === 0) return { bonus: 0, line: null }
  const tail = knockdown !== 0 ? " — this roll's fail-by-5 row is a knockdown." : '.'
  return { bonus: knockdown + htRollBonus(traits), line: `includes ${parts.join(', ')}${tail}` }
}

export function stunRecoveryHt(traits: Trait[]): MajorWoundHt {
  const parts: string[] = []
  if (stunRecoveryBonus(traits) !== 0) parts.push(`+${StunRecovery.COMBAT_REFLEXES_BONUS} from ${COMBAT_REFLEXES}`)
  for (const [trait, value] of HT_ROLL_TRAITS()) {
    if (levelOf(traits, trait) === null) continue
    parts.push(`${signed(value)} from ${trait}`)
  }
  if (parts.length === 0) return { bonus: 0, line: null }
  return { bonus: StunRecovery.bonusFor(traits), line: `includes ${parts.join(', ')}.` }
}

export const htRollBonus = (traits: Trait[]) =>
  sumOf([...HT_ROLL_TRAITS()], ([trait, value]) => (levelOf(traits, trait) !== null ? value : 0))

export const stunRecoveryBonus = (traits: Trait[]) =>
  levelOf(traits, COMBAT_REFLEXES) !== null ? StunRecovery.COMBAT_REFLEXES_BONUS : 0
