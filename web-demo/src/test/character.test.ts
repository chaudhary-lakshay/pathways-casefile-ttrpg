import { describe } from 'vitest'
import { replay } from './parity'
import * as AttributeCosts from '../rules/AttributeCosts'
import * as Derive from '../rules/Derive'
import * as PointBudget from '../rules/PointBudget'
import * as Premades from '../rules/Premades'
import * as SecondaryCharacteristics from '../rules/SecondaryCharacteristics'
import * as SheetComparison from '../rules/SheetComparison'
import * as SkillLadder from '../rules/SkillLadder'
import { basicSpeed } from '../rules/SecondaryCharacteristics'

const sheet = (name: string) => Premades.byName(name)

describe('the 23 premades', () => {
  replay('character', 'Premade.toCharacter', (n) => Premades.toCharacter(sheet(n)))
  replay('character', 'Derive.of', (n) => Derive.of(Premades.toCharacter(sheet(n))))
  replay('character', 'SheetComparison.of', (n) => SheetComparison.of(sheet(n)))
  replay('character', 'PointBudget.report', PointBudget.report)
})

describe('the tables under them', () => {
  replay('character', 'AttributeCosts.costOf', AttributeCosts.costOf)
  replay('character', 'SkillLadder.levelFor', SkillLadder.levelFor)
  replay('character', 'SkillLadder.defaultFor', SkillLadder.defaultFor)
  replay('character', 'SkillLadder.nextRungPoints', SkillLadder.nextRungPoints)
  replay('character', 'SkillLadder.stepped', SkillLadder.stepped)
  replay('character', 'SecondaryCharacteristics.of', SecondaryCharacteristics.of)
  replay('character', 'Secondaries.basicSpeed', (a, d) => basicSpeed(SecondaryCharacteristics.of(a, d)))
  replay('character', 'SecondaryCharacteristics.costOf', SecondaryCharacteristics.costOf)
  replay('character', 'SecondaryCharacteristics.stepsOf', SecondaryCharacteristics.stepsOf)
  replay('character', 'SecondaryCharacteristics.withSteps', SecondaryCharacteristics.withSteps)
  replay('character', 'SecondaryCharacteristics.valueLabelOf', (a, d, k) =>
    SecondaryCharacteristics.valueLabelOf(SecondaryCharacteristics.of(a, d), k),
  )
  replay('character', 'SecondaryCharacteristics.plusSteps', (d, steps) =>
    SecondaryCharacteristics.plusSteps(d, new Map(Object.entries(steps))),
  )
})
