import { eqIc, error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { BoonGranting, BoonModifier, LanguageProficiency, RitualWeight } from './content/schema.gen'
import { boonModifierKey } from './content/members'
import type { RandomSource } from './RandomSource'
import * as RitualResolution from './RitualResolution'

export const LOWEST_SEQUENCE = 0
export const HIGHEST_SEQUENCE = 9
const FIRST_PENALTY = 2

export const granting = (): BoonGranting => ContentLoader.boonGranting()
export const modifiers = (): BoonModifier[] => ContentLoader.boonModifiers()
export const groups = (): string[] => [...new Set(modifiers().map((it) => it.group))]

export function weight(): RitualWeight {
  const named = granting().weight
  return RitualResolution.weights().find((it) => eqIc(it.weight, named)) ??
    error(`Chapter 13 fixes this ritual at '${named}', which Chapter 7 no longer prints`)
}

export const spiFor = (chosen: number = weight().spi, burned = 0) => RitualResolution.spiFor(weight(), chosen, burned)

export function penaltyForSequence(sequence: number): number {
  require(sequence >= LOWEST_SEQUENCE && sequence <= HIGHEST_SEQUENCE, () =>
    `a boon runs Sequence ${HIGHEST_SEQUENCE} down to ${LOWEST_SEQUENCE}; was ${sequence}`)
  if (sequence === HIGHEST_SEQUENCE) return 0
  return -(FIRST_PENALTY << (HIGHEST_SEQUENCE - 1 - sequence))
}

export function modifierTotal(chosen: BoonModifier[], gmValue: Map<string, number> = new Map()): number {
  const counts = new Map<string, number>()
  for (const c of chosen) counts.set(c.group, (counts.get(c.group) ?? 0) + 1)
  const duplicated = [...counts].filter(([, n]) => n > 1).map(([g]) => g)
  require(duplicated.length === 0, () => `pick one option per group; [${duplicated.join(', ')}] got more than one`)
  const keys = new Set(chosen.map(boonModifierKey))
  const unknown = [...gmValue.keys()].filter((k) => !keys.has(k))
  require(unknown.length === 0, () => `no chosen option is keyed [${unknown.join(', ')}]`)
  let sum = 0
  for (const row of chosen) {
    const value = gmValue.get(boonModifierKey(row))
    if (value === undefined) {
      sum += row.modifier
      continue
    }
    const best = row.best
    require(best !== null, () => `'${row.label}' prints ${row.printed}, not a range to pick ${value} inside`)
    const lo = Math.min(row.modifier, best!)
    const hi = Math.max(row.modifier, best!)
    require(value >= lo && value <= hi, () => `'${row.label}' prints ${row.printed}; ${value} is outside it`)
    sum += value
  }
  return sum
}

export const targetFor = (
  skillLevel: number,
  cor: number,
  sequence: number,
  chosen: BoonModifier[] = [],
  gmValue: Map<string, number> = new Map(),
  language: LanguageProficiency | null = null,
  burned = 0,
) =>
  RitualResolution.targetFor(skillLevel + penaltyForSequence(sequence) + modifierTotal(chosen, gmValue), weight(), cor, {
    language,
    burned,
  })

export const attempt = (
  skillLevel: number, cor: number, sequence: number, source: RandomSource,
  chosen: BoonModifier[] = [], gmValue: Map<string, number> = new Map(),
) => RitualResolution.rollAgainst(targetFor(skillLevel, cor, sequence, chosen, gmValue), source)
