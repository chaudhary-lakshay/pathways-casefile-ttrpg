import { Trait, type TraitKind } from './model'
import { eqIc, removePrefix, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { WealthLevel } from './content/schema.gen'

export const TRAIT_PREFIX = 'Wealth: '

export const all = (): WealthLevel[] => ContentLoader.wealth()

export function named(status: string): WealthLevel {
  const found = all().find((it) => eqIc(it.status, status))
  require(found !== undefined, () => `no wealth level called '${status}'; the book prints ${all().map((it) => it.status).join(', ')}`)
  return found
}

export function traitFor(level: WealthLevel): Trait | null {
  if (level.points > 0) return Trait(TRAIT_PREFIX + level.status, level.points, 'ADVANTAGE')
  if (level.points < 0) return Trait(TRAIT_PREFIX + level.status, -level.points, 'DISADVANTAGE')
  return null
}

export const cellFor = (level: WealthLevel) =>
  `Starting money ${level.startingMoney}. ` +
  `Social weight ${level.socialWeight > 0 ? '+' : ''}${level.socialWeight}. ` +
  level.lifestyle

export function levelOf(traits: Trait[]): WealthLevel | null {
  for (const t of traits) {
    const l = levelNamedBy(t)
    if (l !== null) return l
  }
  return null
}

export const namesEconomicStatus = (trait: Trait) => statusNamedBy(trait) !== null

export function applied(traits: Trait[], level: WealthLevel): Trait[] {
  const without = traits.filter((it) => !namesEconomicStatus(it))
  const t = traitFor(level)
  return t !== null ? [...without, t] : without
}

function levelNamedBy(trait: Trait): WealthLevel | null {
  const level = statusNamedBy(trait)
  if (level === null) return null
  if (trait.name.startsWith(TRAIT_PREFIX)) return level
  return trait.cost === magnitudeOf(level) && trait.kind === kindOf(level) ? level : null
}

function statusNamedBy(trait: Trait): WealthLevel | null {
  const head = HEADS.find((it) => trait.name.startsWith(it))
  if (head === undefined) return null
  const status = removePrefix(trait.name, head)
  return all().find((it) => eqIc(it.status, status)) ?? null
}

const HEADS = [TRAIT_PREFIX, 'Poverty: ']

const magnitudeOf = (level: WealthLevel) => (level.points < 0 ? -level.points : level.points)

function kindOf(level: WealthLevel): TraitKind | null {
  if (level.points > 0) return 'ADVANTAGE'
  if (level.points < 0) return 'DISADVANTAGE'
  return null
}
