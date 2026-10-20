import { eqIc, floorDiv, IllegalArgumentException, multiplyExact, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { RitualComplexity, RitualRevelation, RitualWeight } from './content/schema.gen'

export type RitualPacing = 'RUSHED' | 'NORMAL' | 'EXTENDED'

export interface Revelation {
  entity: RitualRevelation
  sacrifices: number
  blessedByDeity: boolean
}

export interface PowerSourceDeclaration {
  pacing: RitualPacing
  assistants: number
  sacrifice: number
  suspended: boolean
  invocation: number | null
  revelation: Revelation | null
}

export const PowerSourceDeclaration = (p: Partial<PowerSourceDeclaration> = {}): PowerSourceDeclaration => ({
  pacing: 'NORMAL', assistants: 0, sacrifice: 0, suspended: false, invocation: null, revelation: null, ...p,
})

export const HP_PER_SPI = 2
export const PARTICIPANT_BONUS = 1
export const PARTICIPANT_CAP = 3
export const SACRIFICE_LOWEST = -40
export const SACRIFICE_HIGHEST = 40
export const INVOCATION_LOWEST = 1
export const INVOCATION_HIGHEST = 5
export const ORTHODOX_BONUS = 2
export const SUSPENSION_PENALTY = -2

export const complexities = (): RitualComplexity[] => ContentLoader.ritualComplexity()
export const revelations = (): RitualRevelation[] => ContentLoader.ritualRevelation()

export function revelationNamed(name: string): RitualRevelation {
  const r = revelations().find((it) => it.entity === name)
  if (r === undefined) throw new IllegalArgumentException(`no revelation entity named '${name}'`)
  return r
}

export function revelationPenalty(entity: RitualRevelation, sacrifices: number, blessedByDeity = false): number {
  require(sacrifices >= 0, () => `sacrifices cannot be negative (was ${sacrifices})`)
  if (blessedByDeity) {
    require(entity.reducibleTo !== null, () => `${entity.entity} prints no Blessed by Deity reduction`)
    return entity.reducibleTo!
  }
  const per = entity.perSacrifice ?? 0
  require(sacrifices === 0 || per > 0, () => `${entity.entity} prints no reduction per sacrifice`)
  return Math.min(0, entity.penalty + per * sacrifices)
}

export function complexityNamed(name: string): RitualComplexity {
  const c = complexities().find((it) => eqIc(it.complexity, name))
  if (c === undefined) {
    throw new IllegalArgumentException(
      `no ritual complexity named '${name}'; the book prints ` + complexities().map((it) => it.complexity).join(', '),
    )
  }
  return c
}

export const complexityFor = (weight: RitualWeight) => complexityNamed(weight.weight)

export function spiOwed(cost: number, hpSacrificed = 0): number {
  require(cost >= 0, () => `a ritual cannot cost negative SPI; was ${cost}`)
  require(hpSacrificed >= 0, () => `HP sacrificed cannot be negative; was ${hpSacrificed}`)
  const covered = floorDiv(hpSacrificed, HP_PER_SPI)
  require(covered <= cost, () => `${hpSacrificed} HP covers ${covered} SPI, more than the ${cost} this ritual costs`)
  return cost - covered
}

export function hpFor(spi: number): number {
  require(spi >= 0, () => `cannot convert negative SPI to HP; was ${spi}`)
  return multiplyExact(spi, HP_PER_SPI)
}

export function participantBonus(assistants: number): number {
  require(assistants >= 0, () => `a ritual cannot have negative assistants; was ${assistants}`)
  return Math.min(assistants * PARTICIPANT_BONUS, PARTICIPANT_CAP)
}

export function sacrificeBonus(chosen: number): number {
  require(chosen >= SACRIFICE_LOWEST && chosen <= SACRIFICE_HIGHEST, () =>
    `sacrifice quality ranges from ${SACRIFICE_LOWEST} to +${SACRIFICE_HIGHEST}; ${chosen} is outside that`)
  return chosen
}

export function invocationBonus(chosen: number | null): number {
  if (chosen === null) return 0
  require(chosen >= INVOCATION_LOWEST && chosen <= INVOCATION_HIGHEST, () =>
    `an invocation adds +${INVOCATION_LOWEST} to +${INVOCATION_HIGHEST}; ${chosen} is outside that`)
  return chosen
}

export function pacingModifier(complexity: RitualComplexity, pacing: RitualPacing): number {
  switch (pacing) {
    case 'RUSHED': return complexity.rushed
    case 'NORMAL': return 0
    case 'EXTENDED': return complexity.extended
  }
}

export function modifierTotal(complexity: RitualComplexity | null, d: PowerSourceDeclaration): number {
  require(complexity !== null || d.pacing === 'NORMAL', () =>
    `a ${d.pacing.toLowerCase()} ritual has no modifier until its ` +
    'complexity is declared; the column runs from -3 to -5 by rung')
  return (complexity === null ? 0 : pacingModifier(complexity, d.pacing)) +
    participantBonus(d.assistants) +
    sacrificeBonus(d.sacrifice) +
    invocationBonus(d.invocation) +
    (d.suspended ? SUSPENSION_PENALTY : 0) +
    (d.revelation === null ? 0 : revelationPenalty(d.revelation.entity, d.revelation.sacrifices, d.revelation.blessedByDeity))
}
