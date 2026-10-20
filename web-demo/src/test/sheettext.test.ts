import { describe, expect, test } from 'vitest'
import { attrCodeOf, carriedText, disambiguated, drawbackPrice, encumbranceDodge, encumbranceLine, poolLine, poolTags, spelledOut } from '../app/SheetText'
import * as Pools from '../rules/Pools'
import { SkillKey, Trait } from '../rules/model'

// PoolStrip.kt, EncumbranceCard.kt, VitalsTable.kt, SheetTabs.kt, SheetRows.kt.
describe('the sheet text', () => {
  test('a healthy pool has no tags; a dead one says Dead', () => {
    expect(poolTags(Pools.hp(10, 10))).toEqual([])
    expect(poolTags(Pools.hp(-60, 10))).toEqual(['Dead'])
  })

  test('a hurt pool tags its level and penalty, and its line spells them out', () => {
    const low = Pools.hp(2, 10)
    const tags = poolTags(low)
    expect(tags.length).toBeGreaterThan(0)
    expect(tags.some((it) => it.startsWith('−'))).toBe(low.penalty !== 0)
    expect(poolLine(low).startsWith(low.level)).toBe(true)
    expect(poolLine(low)).toContain(low.effect)
  })

  test('encumbrance prints basic lift, the level and its penalties', () => {
    expect(encumbranceLine(null, 20)).toBe('BL 20 lbs · none')
    expect(encumbranceLine(0, 20)).toMatch(/^BL 20 lbs · \w+, no penalty$/)
    expect(encumbranceLine(1000, 20)).toBe('BL 20 lbs · over 15 × BL: the table prints no level, so it cannot be carried')
    expect(encumbranceLine(10, 12.8)).toMatch(/^BL 12\.8 lbs · /)
    expect(encumbranceDodge(null, 20)).toBe(0)
    expect(encumbranceDodge(1000, 20)).toBe(0)
    expect(encumbranceDodge(60, 20)).toBeLessThan(0)
    expect(carriedText(12)).toBe('12')
    expect(carriedText(12.5)).toBe('12.5')
    expect(carriedText(null)).toBe('')
  })

  test('labels and prices', () => {
    expect(spelledOut('HP')).toBe('Hit Points')
    expect(spelledOut('Will')).toBe('Will')
    const dis = Trait('Bad Temper', 10, 'DISADVANTAGE')
    expect(drawbackPrice(dis, [], 'the potion')).toBe('−10 pts')
    expect(drawbackPrice(dis, [dis], 'the potion')).toBe('from the potion')
    expect(drawbackPrice(Trait('Phobia', 0, 'DISADVANTAGE'), [], 'the potion')).toBe('gained in play')
    const easy = SkillKey('Knife', null, { type: 'Buyable', attr: 'DX' }, 'EASY')
    const hard = SkillKey('Knife', null, { type: 'Buyable', attr: 'DX' }, 'HARD')
    expect(attrCodeOf(easy)).toBe('DX/E')
    expect(disambiguated(easy, [easy])).toBe('Knife')
    expect(disambiguated(easy, [easy, hard])).toBe('Knife [E]')
  })
})
