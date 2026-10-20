import { Trait } from './model'
import { associateBy, eqIc, error, maxByOrNull, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { AppearanceLevel } from './content/schema.gen'

export const all = (): AppearanceLevel[] => ContentLoader.appearance()

export function named(level: string): AppearanceLevel {
  const found = all().find((it) => eqIc(it.level, level))
  require(found !== undefined, () => `no appearance level called '${level}'; the book prints ${all().map((it) => it.level).join(', ')}`)
  return found
}

export function traitFor(level: AppearanceLevel): Trait {
  if (level.points > 0) return Trait(level.level, level.points, 'ADVANTAGE')
  if (level.points < 0) return Trait(level.level, -level.points, 'DISADVANTAGE')
  return error(`'${level.level}' costs nothing; this table prints no free level, so a 0-point row means the content changed`)
}

export const levelOf = (traits: Trait[]) => played(traits)

export function played(traits: Trait[]): AppearanceLevel | null {
  const byName = associateBy(all(), (it) => it.level.toLowerCase())
  return maxByOrNull(traits.flatMap((it) => byName.get(it.name.toLowerCase()) ?? []), (it) => it.points)
}

export const cellFor = (level: AppearanceLevel) => level.effect

export const isSuperseded = (trait: Trait, traits: Trait[]) =>
  played([trait]) !== null && !eqIc(played(traits)?.level, trait.name)

export const applied = (traits: Trait[], level: AppearanceLevel) => [...without(traits), traitFor(level)]

export function without(traits: Trait[]): Trait[] {
  const names = new Set(all().map((it) => it.level.toLowerCase()))
  return traits.filter((it) => !names.has(it.name.toLowerCase()))
}
