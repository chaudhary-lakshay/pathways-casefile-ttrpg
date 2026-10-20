import { Modifier, type Trait } from './model'
import { first } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { MajorWound } from './content/schema.gen'
import * as TraitNames from './TraitNames'

export type RollKind = 'SKILL' | 'ATTRIBUTE' | 'ACTIVE_DEFENSE' | 'MAJOR_WOUND_HT' | 'DAILY_RECOVERY'

export const PENALTY = -4
const ROW = 'Success'
export const IGNORED_BY = 'High Pain Threshold'

export const causedBy = (row: MajorWound) => row.result === ROW

export const printed = (): string => first(ContentLoader.majorWound(), (it) => it.result === ROW).effect

export const appliesTo = (kind: RollKind) => kind !== 'MAJOR_WOUND_HT' && kind !== 'DAILY_RECOVERY'

export const ignoredBy = (traits: Trait[]) => TraitNames.levelOf(traits, IGNORED_BY) !== null

export const penaltyFor = (kind: RollKind, shocked: boolean, traits: Trait[]) =>
  shocked && appliesTo(kind) && !ignoredBy(traits) ? PENALTY : 0

export function modifiers(shocked: boolean, traits: Trait[]): Modifier[] {
  const value = penaltyFor('SKILL', shocked, traits)
  if (value === 0) return []
  return [Modifier({
    id: 'state:shock',
    source: { type: 'StateThreshold', tracker: 'Shock' },
    target: { type: 'AllRolls' },
    effect: { type: 'Flat', n: value },
    layer: 'STATE_THRESHOLD',
  })]
}
