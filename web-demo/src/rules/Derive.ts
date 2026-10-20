import { attr, type Attributes, type Character, type ControllingAttribute, type SkillKey } from './model'
import { ValueMap } from './kt'
import * as Blessings from './Blessings'
import * as PointBudget from './PointBudget'
import * as SecondaryCharacteristics from './SecondaryCharacteristics'
import * as SkillLadder from './SkillLadder'
import type { BudgetReport } from './PointBudget'
import type { Secondaries } from './SecondaryCharacteristics'

export interface DerivedCharacter {
  secondaries: Secondaries
  budget: BudgetReport
  skillLevels: ValueMap<SkillKey, number>
}

/** The mortal's derivation: secondaries and skill levels from the PLAYED attributes, the budget from the bought ones. */
export function of(c: Character): DerivedCharacter {
  const played = Blessings.played(c.attributes, c.traits)
  const secondaries = SecondaryCharacteristics.of(played, c.secondaryDelta)
  return {
    secondaries,
    budget: PointBudget.report(c.attributes, c.secondaryDelta, c.traits, c.skills),
    skillLevels: new ValueMap(c.skills.map((s) => [
      s.key,
      SkillLadder.levelFor(s.points, s.key.difficulty, controllingLevel(s.key.attr, played, secondaries, c.spi)),
    ] as const)),
  }
}

/** ST, DX, IQ and HT as played: the walk's, or the blessed purchase for a mortal. */
export function attributes(c: Character, walked: { effectiveAttributes: Attributes } | null): Attributes {
  return walked?.effectiveAttributes ?? Blessings.played(c.attributes, c.traits)
}

export function controllingLevel(a: ControllingAttribute, attributes: Attributes, secondaries: Secondaries, spi: number): number {
  switch (a.type) {
    case 'Buyable': return attr(attributes, a.attr)
    case 'Will': return secondaries.will
    case 'Per': return secondaries.per
    case 'Spi': return spi
  }
}
