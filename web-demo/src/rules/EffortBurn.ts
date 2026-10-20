import { eqIc, error, multiplyExact, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { BurnablePool } from './content/schema.gen'
import { Flat, Modifier } from './model'
import type { SkillKey, Trait } from './model'
import * as TraitNames from './TraitNames'

export type BurnKind = 'PHYSICAL' | 'MYSTICAL'
export type BurnWhen = 'IN_COMBAT' | 'ALWAYS'

export const all = (): BurnablePool[] => ContentLoader.effortBurn()

export const pool = (kind: BurnKind): BurnablePool =>
  all().find((it) => eqIc(it.kind, kind)) ?? error(`the book prints no ${kind.toLowerCase()} burn`)

// Conditions.RITUAL_SKILL, and Divination's skill.
export const SUPERNATURAL_SKILLS = new Set(['Ritualistic Magic', 'Divination Arts'])
const PHYSICAL_ATTRS = new Set(['ST', 'DX', 'HT'])

export function kindFor(key: SkillKey): BurnKind | null {
  const ca = key.attr
  if (ca.type === 'Buyable') {
    if (PHYSICAL_ATTRS.has(ca.attr)) return 'PHYSICAL'
    return SUPERNATURAL_SKILLS.has(key.name) ? 'MYSTICAL' : null
  }
  return ca.type === 'Spi' ? 'MYSTICAL' : null
}

export function whenAllowed(kind: BurnKind, traits: Trait[]): BurnWhen | null {
  const row = pool(kind)
  const holdsAny = (names: string[]) => names.some((it) => TraitNames.levelOf(traits, it) !== null)
  if (row.outOfCombatTraits !== null && holdsAny(row.outOfCombatTraits)) return 'ALWAYS'
  if (row.combatTraits.length === 0 || holdsAny(row.combatTraits)) return 'IN_COMBAT'
  return null
}

export function bonusFor(kind: BurnKind, points: number): number {
  require(points >= 0, () => `cannot burn a negative number of points; was ${points}`)
  return multiplyExact(points, pool(kind).perPoint)
}

export function modifier(kind: BurnKind, points: number): Modifier | null {
  const bonus = bonusFor(kind, points)
  if (bonus === 0) return null
  return Modifier({
    id: idFor(kind),
    source: { type: 'Situational', description: `Burned ${pool(kind).pool}` },
    target: { type: 'AllRolls' },
    effect: Flat(bonus),
    layer: 'SITUATIONAL',
  })
}

export const idFor = (kind: BurnKind) => `burn:${pool(kind).pool.toLowerCase()}`

export function requireOnePool(fp: number, spi: number) {
  require(fp === 0 || spi === 0, () => `the book allows one pool per roll, not both; was ${fp} FP and ${spi} SPI`)
}

export const isPhysical = (key: SkillKey) => kindFor(key) === 'PHYSICAL'
