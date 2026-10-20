import { describe, expect, test } from 'vitest'
import {
  acrobaticsFor, acrobaticTarget, armedParries, BARE_HANDS, bareBlock, blockTargets, boostOn, defenceLabels, shieldedTargets, shieldForDb, shieldPair,
  shotBlockNote, unarmedParries, unskilledParriers, unskilledParryNote,
} from '../app/DefenseModel'
import { acrobaticLabel } from '../app/RollLog'
import * as ContentLoader from '../rules/content/ContentLoader'
import { ValueMap } from '../rules/kt'
import { Buyable, SkillKey, Trait, type Difficulty } from '../rules/model'
import * as Shields from '../rules/Shields'
import * as TraitEffects from '../rules/TraitEffects'
import * as Weapons from '../rules/Weapons'

// DefenseDialogTest.kt's inputs and expected values (the RoundState cases are in scene.test.ts).
const dxSkill = (name: string, difficulty: Difficulty) => SkillKey(name, null, Buyable('DX'), difficulty)
const broadswordKey = dxSkill('Broadsword', 'AVERAGE')
const knifeKey = dxSkill('Knife', 'EASY')
const revolverKey = SkillKey('Guns', 'Revolver', Buyable('DX'), 'EASY')
const brawlingKey = dxSkill('Brawling', 'EASY')
const acrobaticsKey = dxSkill('Acrobatics', 'HARD')
const levels = (...entries: [SkillKey, number][]) => new ValueMap(entries)

describe('DefenseDialogTest', () => {
  test('a carried weapon parries at half its skill plus three; one that cannot parry, or lacks its skill, is not listed', () => {
    expect(armedParries([Weapons.named('Broadsword')], levels([broadswordKey, 13]), null, 10)).toEqual([['Broadsword', 9]])
    expect(armedParries([Weapons.named('Revolver (.38 cal)')], levels([revolverKey, 12]), null, 10)).toEqual([])
    expect(armedParries([Weapons.named('Broadsword')], levels(), null, 10)).toEqual([])
  })

  test("a master's carried weapon parries at DX without the skill", () => {
    const carried = [Weapons.named('Broadsword'), Weapons.named('Revolver (.38 cal)')]
    expect(armedParries(carried, levels(), 'WARRIOR', 12)).toEqual([['Broadsword', 9]])
    expect(armedParries(carried, levels([broadswordKey, 10]), 'WARRIOR', 12)).toEqual([['Broadsword', 9]])
    expect(unskilledParriers(carried, levels(), 'WARRIOR', 12)).toEqual([])
    expect(armedParries(carried, levels(), 'WARRIOR', 12, new Set(['Broadsword']))).toEqual([['Broadsword', 10]])
    expect(armedParries(carried, levels(), 'WARRIOR', 12, new Set(['Revolver (.38 cal)']))).toEqual([['Broadsword', 9]])
  })

  test('two carried weapons give two parries', () => {
    expect(armedParries([Weapons.named('Broadsword'), Weapons.named('Combat Knife')], levels([broadswordKey, 12], [knifeKey, 11]), null, 10))
      .toEqual([['Broadsword', 9], ['Combat Knife', 8]])
  })

  test('a bought boost lands on the named row alone', () => {
    const bought: ['FEVERISH', string] = ['FEVERISH', 'Parry (Brawling)']
    expect(boostOn('Parry (Brawling)', bought)).toBe(2)
    expect(boostOn('Dodge', bought)).toBe(0)
    expect(boostOn('Parry (Wrestling)', bought)).toBe(0)
    expect(boostOn('Block', bought)).toBe(0)
    for (const row of ['Dodge', 'Parry (Brawling)', 'Block']) expect(boostOn(row, null)).toBe(0)
  })

  test("the shield's DB is withheld from behind and offered from the front and side", () => {
    const shield = Shields.named('Medium')
    expect(shieldForDb(shield, 'FRONT')).toBe(shield)
    expect(shieldForDb(shield, 'SIDE')).toBe(shield)
    expect(shieldForDb(shield, 'REAR')).toBeNull()
    for (const facing of ['FRONT', 'SIDE', 'REAR'] as const) expect(shieldForDb(null, facing)).toBeNull()
  })

  test('only the defences on screen can be paid for', () => {
    const armed: [string, number][] = [['Shortsword', 9]]
    const unarmed: [string, 'BRAWLING', number][] = [['Brawling', 'BRAWLING', 9]]
    expect(defenceLabels(true, armed, unarmed, true)).toEqual(['Dodge', 'Parry (Shortsword)', 'Parry (Brawling)', 'Block'])
    expect(defenceLabels(true, armed, [], false)).toEqual(['Dodge', 'Parry (Shortsword)'])
    expect(defenceLabels(true, [], unarmedParries(levels(), 10), false)).toEqual(['Dodge', `Parry (${BARE_HANDS})`])
  })

  test('the unarmed parries: DX bare-handed, a brawler at or above DX alone, Boxing and Wrestling beside DX', () => {
    expect(unarmedParries(levels(), 10)).toEqual([[BARE_HANDS, 'BARE_HANDS', 8]])
    expect(unarmedParries(levels([brawlingKey, 12]), 10)).toEqual([['Brawling', 'BRAWLING', 9]])
    expect(unarmedParries(levels([brawlingKey, 11]), 12)).toEqual([['Brawling', 'BRAWLING', 8], [BARE_HANDS, 'BARE_HANDS', 9]])
    expect(unarmedParries(levels([dxSkill('Boxing', 'AVERAGE'), 14], [dxSkill('Wrestling', 'AVERAGE'), 14]), 10))
      .toEqual([['Boxing', 'BOXING', 10], ['Wrestling', 'WRESTLING', 8], [BARE_HANDS, 'BARE_HANDS', 8]])
  })

  test('an Acrobatic Dodge rolls the trained skill, else the default already composed, else nothing', () => {
    const untrained = [
      { key: dxSkill('Stealth', 'AVERAGE'), level: 5, source: 'DX-5' },
      { key: acrobaticsKey, level: 5, source: 'DX-6, +1 Versatile' },
    ]
    const skill = acrobaticsFor(levels(), untrained)
    expect(skill).toEqual({ key: acrobaticsKey, level: 5, untrainedSource: 'DX-6, +1 Versatile' })
    expect(acrobaticLabel(skill!)).toBe('Acrobatics (untrained, DX-6, +1 Versatile)')
    const trained = acrobaticsFor(levels([acrobaticsKey, 12]), [{ key: acrobaticsKey, level: 4, source: 'DX-6' }])
    expect(trained).toEqual({ key: acrobaticsKey, level: 12, untrainedSource: null })
    expect(acrobaticsFor(levels(), [])).toBeNull()
  })

  test('an Acrobatic Dodge takes the trait bonus the Skills tab gives, and what the ledger passes in', () => {
    const balance = Trait(TraitEffects.PERFECT_BALANCE, 15, 'ADVANTAGE')
    const untrained = { key: acrobaticsKey, level: 4, untrainedSource: 'DX-6' }
    const standing = acrobaticTarget(untrained, () => [], [balance])
    expect(standing.baseTarget).toBe(4)
    expect(standing.target).toBe(6)
    expect(acrobaticTarget(untrained, () => [], []).target).toBe(4)
    expect(acrobaticTarget(untrained, (k) => TraitEffects.modifiers([balance], k), []).target).toBe(6)
  })

  test('a defence row offers a roll at each figure it prints; at the front the shield figure is the only one', () => {
    expect(shieldedTargets(7, null, 'GM_DECIDES', 1)).toEqual([8])
    expect(shieldedTargets(7, Shields.named('Medium'), 'GM_DECIDES', 1)).toEqual([8, 10])
    expect(shieldedTargets(7, Shields.named('Medium'), 'COUNTS', 1)).toEqual([10])
    expect(shieldPair(7, 9, 'COUNTS')).toEqual([9])
    expect(shieldPair(7, 9, 'GM_DECIDES')).toEqual([7, 9])
    expect(shieldPair(7, null, 'COUNTS')).toEqual([7])
  })

  test("a block against a shot rolls at the ranged figure, and a shield's own penalty stands without its DB", () => {
    const light = Shields.named('Light')
    const improvised = Shields.named('Improvised')
    expect(blockTargets(light, 10, 'COUNTS', true)).toEqual([8])
    expect(blockTargets(light, 10, 'COUNTS', false)).toEqual([9])
    expect(blockTargets(light, 10, 'GM_DECIDES', false)).toEqual([8, 9])
    expect(blockTargets(light, 10, 'COUNTS', false, 1)).toEqual([10])
    expect(blockTargets(light, 10, 'GM_DECIDES', true)).toEqual([7, 8])
    expect(blockTargets(improvised, 10, 'GM_DECIDES', false)).toEqual([6, 7])
    expect(bareBlock(10, light, true)).toBe(7)
    expect(bareBlock(10, improvised, false)).toBe(6)
    expect(bareBlock(10, null, true)).toBe(8)
  })

  test("the ranged block note prints the side's two figures", () => {
    const light = Shields.named('Light')
    expect(shotBlockNote(light, 10, 'GM_DECIDES', 0)).toBe('Against ranged attacks: 7 → 8')
    expect(shotBlockNote(light, 10, 'COUNTS', 0)).toBe('Against ranged attacks: 8')
    expect(shotBlockNote(Shields.named('Improvised'), 10, 'COUNTS', 0)).toBeNull()
  })

  test('the note on an unskilled weapon gives the two cells and leaves it to the GM', () => {
    const cells = ContentLoader.defenses()
    const [line, why] = unskilledParryNote(['Broadsword'], cells)!
    expect(line).toContain('Broadsword')
    expect(line).toContain('GM')
    expect(line).not.toContain('÷')
    expect(why).toContain('"Weapon Skill ÷ 2 + 3"')
    expect(why).toContain('"Brawling or DX ÷ 2 + 3"')
    expect(line + why).not.toContain('gap')
    expect(unskilledParryNote([], cells)).toBeNull()
  })

  test('a parrying weapon carried without its skill is named, a firearm is not', () => {
    const carried = [Weapons.named('Broadsword'), Weapons.named('Combat Knife'), Weapons.named('Revolver (.38 cal)')]
    expect(unskilledParriers(carried, levels([knifeKey, 11]), null, 10)).toEqual(['Broadsword'])
    expect(unskilledParriers(carried, levels([knifeKey, 11], [broadswordKey, 9]), null, 10)).toEqual([])
  })
})
