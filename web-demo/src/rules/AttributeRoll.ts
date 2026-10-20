import { substringBefore } from './kt'
import type { Trait } from './model'
import type { PoolStatus } from './Pools'
import * as TraitAttributeRolls from './TraitAttributeRolls'
import * as TraitClauses from './TraitClauses'
import type { TraitClause } from './TraitEffects'

export type Kind = 'WILL' | 'PER' | 'INTUITION' | 'PERCEPTION'

export const KINDS: Record<Kind, { label: string; code: string }> = {
  WILL: { label: 'Will', code: 'Will' },
  PER: { label: 'Per', code: 'Per' },
  INTUITION: { label: 'Spiritual Intuition', code: 'SPI' },
  PERCEPTION: { label: 'Spiritual Perception', code: 'SPI' },
}

export const isSense = (k: Kind) => KINDS[k].code === 'SPI'

export function clauses(kind: Kind, traits: Trait[]): TraitClause[] {
  switch (kind) {
    case 'WILL': return TraitClauses.willClauses(traits)
    case 'PER': return [...TraitClauses.senseClauses(traits), ...TraitClauses.perRollClauses(traits)]
    default: return []
  }
}

export const targetFor = (
  kind: Kind, level: number, traits: Trait[], shock = 0, hurt = 0, others = 0, spi: PoolStatus | null = null,
) => level + alertness(kind, traits) + shock + hurt + others + attrition(kind, spi)

export function partsFor(kind: Kind, level: number, traits: Trait[], shock = 0, hurt = 0, spi: PoolStatus | null = null): string[] {
  const out = [`${KINDS[kind].code} ${level}`]
  const a = alertness(kind, traits)
  if (a !== 0) out.push(`Alertness +${a}`)
  if (shock !== 0) out.push(`shock ${shock}`)
  if (hurt !== 0) out.push(`hurt or tired ${hurt}`)
  const t = attrition(kind, spi)
  if (t !== 0) out.push(`${substringBefore(spi!.effect, ':')} ${t}`)
  return out
}

const attrition = (kind: Kind, spi: PoolStatus | null) =>
  isSense(kind) && spi !== null && spi.scope === 'SPI_ROLLS' ? spi.penalty : 0

const alertness = (kind: Kind, traits: Trait[]) => (kind === 'PER' ? TraitAttributeRolls.alertnessBonus(traits) : 0)
