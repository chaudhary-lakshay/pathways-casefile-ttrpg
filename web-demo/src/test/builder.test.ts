import { describe, expect, test } from 'vitest'
import {
  BUDGET_PIPS, budgetPips, carriedLine, charmableSkills, charmLine, ledgerStacks, matchesSearch, pointsLabel, secondaryShortName, skillMatches, studyAdvice,
} from '../app/BuilderModel'
import { ValueMap } from '../rules/kt'
import * as Weapons from '../rules/Weapons'
import * as Blessings from '../rules/Blessings'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as Derive from '../rules/Derive'
import { Attributes, BeyonderState, Buyable, Character, SkillKey, Trait } from '../rules/model'
import * as Premades from '../rules/Premades'
import * as Study from '../rules/Study'
import * as TraitEffects from '../rules/TraitEffects'

// The Kotlin tests' own inputs and expected values: BuilderLedgerTest,
// StudyAdviceTest and CharmedSkillTest (app/src/test/kotlin/com/pathways/app).
describe('BuilderLedgerTest', () => {
  test('budget pips', () => {
    expect(budgetPips(76, 90)).toBe(25)
    expect(budgetPips(0, 90)).toBe(0)
    expect(budgetPips(89, 90)).toBe(29)
    expect(budgetPips(90, 90)).toBe(BUDGET_PIPS)
    expect(budgetPips(120, 90)).toBe(BUDGET_PIPS)
    expect(budgetPips(-15, 70)).toBe(0)
    expect(budgetPips(0, 0)).toBe(0)
    expect(budgetPips(5, 0)).toBe(BUDGET_PIPS)
  })

  test("the six priced rows take the frame's short names", () => {
    const shown = ContentLoader.secondaryCosts().filter((it) => it.pointsPerStep !== null).map((it) => secondaryShortName(it.key, it.characteristic))
    expect(shown).toEqual(['HP', 'Will', 'Per', 'FP', 'Speed', 'Move'])
    expect(secondaryShortName('spi', 'Spirituality')).toBe('Spirituality')
  })

  test('the ledger stacks only when the capped column is narrow in text units', () => {
    expect(ledgerStacks(390, 1.0)).toBe(false)
    expect(ledgerStacks(390, 1.3)).toBe(true)
    expect(ledgerStacks(960, 2.0)).toBe(true)
    expect(ledgerStacks(960, 1.3)).toBe(false)
    expect(ledgerStacks(600, 1.3)).toBe(false)
  })

  test('points labels sign with a true minus and agree in number', () => {
    expect(pointsLabel(5)).toBe('+5 pts')
    expect(pointsLabel(-1)).toBe('−1 pt')
    expect(pointsLabel(0)).toBe('0 pts')
  })
})

describe('StudyAdviceTest', () => {
  test('a refusal is tagged and keeps its sentence, and a plan is priced', () => {
    let refused = 0
    let planned = 0
    for (const sheet of Premades.all()) {
      const character = Premades.toCharacter(sheet)
      const derived = Derive.of(character)
      for (const skill of character.skills) for (const method of Study.STUDY_METHODS) {
        const advice = studyAdvice(skill, character, derived, method)
        const attributeLevel = Derive.controllingLevel(skill.key.attr, character.attributes, derived.secondaries, character.spi)
        const plan = Study.plan(skill.points, skill.key.difficulty, attributeLevel, method)
        if (plan.ok) {
          planned++
          expect(advice.reason).toBeNull()
          expect(advice.tag).toContain(`${plan.plan.pointsNeeded} CP`)
          expect(advice.tag).toContain(`→ ${plan.plan.toLevel}`)
        } else {
          refused++
          const expected = plan.error.refusal === 'AT_CAP' ? `at cap ${Study.capFor(attributeLevel)}` : plan.error.refusal === 'NOT_POSSIBLE' ? 'not by self-study' : 'potion skill'
          expect(advice.tag).toBe(expected)
          expect(advice.reason).toBe(plan.error.reason)
        }
      }
    }
    expect(refused > 0 && planned > 0).toBe(true)
  })

  test("a blessed holder's study tag climbs from the level the row prints", () => {
    let checked = 0
    let capped = 0
    for (const sheet of Premades.all()) {
      const mortal = Premades.toCharacter(sheet)
      const character = { ...mortal, traits: [...mortal.traits, Trait(TraitEffects.GOD_OF_COMBAT, 75, 'ADVANTAGE')] }
      const derived = Derive.of(character)
      for (const skill of character.skills) {
        if (skill.key.attr.type !== 'Buyable') continue
        const attr = skill.key.attr.attr
        if (Blessings.gainTo(attr, [TraitEffects.GOD_OF_COMBAT]) === 0) continue
        const tag = studyAdvice(skill, character, derived, 'TUTOR').tag
        if (tag.startsWith('at cap ')) {
          capped++
          expect(tag).toBe(`at cap ${mortal.attributes[attr.toLowerCase() as 'st'] + 3 + 1}`)
        }
        const to = tag.includes('→ ') ? Number(tag.split('→ ')[1]) : NaN
        if (Number.isNaN(to)) continue
        checked++
        expect(to).toBe(derived.skillLevels.get(skill.key)! + 1)
      }
    }
    expect(checked > 0 && capped > 0).toBe(true)
  })
})

describe('DefenseDialogTest, SkillSearchTest, TraitSearchTest', () => {
  test("a carried weapon's line names its level and parry, or why there is none", () => {
    const broadsword = Weapons.named('Broadsword')
    const key = SkillKey('Broadsword', null, Buyable('DX'), 'AVERAGE')
    expect(carriedLine(broadsword, new ValueMap(), 'WARRIOR', 12)).toBe('Broadsword 12 — parry 9')
    expect(carriedLine(broadsword, new ValueMap([[key, 10]]), 'WARRIOR', 12)).toBe('Broadsword 12 — parry 9')
    expect(carriedLine(broadsword, new ValueMap(), null, 12)).toBe('Broadsword — not a skill this character has, so no parry')
  })

  test('the skill search finds any part of the name, ignoring case; blank matches all', () => {
    const skills = ContentLoader.skills()
    const found = (q: string) => skills.filter((it) => skillMatches(it, q)).map((it) => (it.specialization ? `${it.name} (${it.specialization})` : it.name))
    expect(found('guns (')).toEqual(['Guns (Pistol)', 'Guns (Revolver)', 'Guns (Rifle)', 'Guns (Shotgun)'])
    expect(found('revolver')).toContain('Guns (Revolver)')
    expect(found('')).toHaveLength(skills.length)
    expect(found('  ')).toHaveLength(skills.length)
  })

  test('the trait search', () => {
    expect(matchesSearch('Phobia (specify)', '')).toBe(true)
    expect(matchesSearch('Phobia (specify)', '  ')).toBe(true)
    expect(matchesSearch('Phobia (specify)', 'phob')).toBe(true)
    expect(matchesSearch('Combat Reflexes', 'REFLEX')).toBe(true)
    expect(matchesSearch('Combat Reflexes', 'phob')).toBe(false)
  })
})

describe('CharmedSkillTest', () => {
  const pistol = SkillKey('Guns', 'Pistol', Buyable('DX'), 'EASY')
  const chosen = { name: 'Guns', specialization: 'Pistol' }

  test('the builder says which skill is charmed, that none is, or that it is not held', () => {
    expect(charmLine(null, [pistol])).toBe('No skill chosen: no roll offers its +1.')
    expect(charmLine(chosen, [pistol])).toBe('Charms Guns (Pistol): +1 when used, lit on the roll.')
    expect(charmLine({ name: 'Guns', specialization: 'Rifle' }, [pistol])).toBe('Charms Guns (Rifle), which this character has no level in.')
  })

  test("a potion's skill can be charmed, as the Skills tab lists it", () => {
    const mortal = Character({ name: 'test', attributes: Attributes({ iq: 10 }) })
    const names = (state: BeyonderState | null) => charmableSkills(mortal, state).map((it) => it.name)
    expect(names(null)).not.toContain('Divination Arts')
    expect(names(BeyonderState({ pathway: 'Fool', sequence: 9 }))).toContain('Divination Arts')
    expect(charmLine(chosen, [{ ...pistol, specialization: 'pistol' }])).toBe('Charms Guns (Pistol): +1 when used, lit on the roll.')
  })
})
