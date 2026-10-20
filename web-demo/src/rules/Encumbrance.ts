import type { Trait } from './model'
import { require, sortedBy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { EncumbranceLevel } from './content/schema.gen'
import * as TraitNames from './TraitNames'

export const LIFTING_ST = 'Lifting ST'

export function basicLift(st: number, traits: Trait[]): number {
  const lifting = st + (TraitNames.levelOf(traits, LIFTING_ST) ?? 0)
  return (lifting * lifting) / 5.0
}

export const levels = (): EncumbranceLevel[] => ContentLoader.encumbrance()

export function levelFor(carriedLbs: number, basicLift: number): EncumbranceLevel | null {
  require(carriedLbs >= 0, () => `a carried weight cannot be negative (was ${carriedLbs})`)
  return sortedBy(levels(), (it) => it.level).find((it) => carriedLbs <= it.maxLoadBl * basicLift) ?? null
}
