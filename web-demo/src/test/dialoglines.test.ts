import { describe, expect, test } from 'vitest'
import { boonRefusal, contractLines, fuelLine, invocationApplied, ritualRefusal, spiChoices, standingRefusal } from '../app/DialogLines'
import * as Languages from '../rules/Languages'
import * as PowerSources from '../rules/PowerSources'
import * as RitualResolution from '../rules/RitualResolution'

// BoonDialogTest.kt's inputs and expected values.
describe('BoonDialogTest', () => {
  const broker = { pathway: 'Chaos Mist', sequence: 9 }
  const refusal = (p: { skill?: number | null; patron?: string | null; held?: typeof broker | null; next?: typeof broker | null; lost?: boolean } = {}) =>
    boonRefusal(p.skill === undefined ? 12 : p.skill, p.patron === undefined ? 'Chaos Mist' : p.patron, p.held ?? null, p.next === undefined ? broker : p.next, p.lost ?? false)

  test('a prayer needs the skill, a patron and a rung to ask for', () => {
    expect(refusal()).toBeNull()
    expect(refusal({ skill: null })).toContain('Ritualistic Magic')
    expect(refusal({ patron: null, next: null })).toContain('patron')
    expect(refusal({ held: { pathway: 'Chaos Mist', sequence: 8 }, next: null })).toContain('Sequence 8')
    expect(refusal({ next: null, lost: true })).toContain('GM')
  })

  test('only a refusal no pick can lift leads the dialog', () => {
    expect(standingRefusal(null, false)).toContain('Ritualistic Magic')
    expect(standingRefusal(12, true)).toContain('GM')
    expect(standingRefusal(12, false)).toBeNull()
    expect(boonRefusal(null, null, null, null)).toBe(standingRefusal(null, false))
  })

  test('a prayer in a Broken tongue is refused', () => {
    const broken = Languages.proficiencies(Languages.MYSTICAL).find((it) => !Languages.canPerformRituals(it))!
    expect(boonRefusal(12, 'Chaos Mist', null, broker, false, broken)).toContain(broken.proficiency)
  })
})

// RitualDialogTest.kt's inputs and expected values.
describe('RitualDialogTest', () => {
  test('only a forbidden tongue refuses a ritual', () => {
    expect(ritualRefusal(true)).toBeNull()
    expect(ritualRefusal(false)).toContain('forbids')
  })

  test("the fuel offered is the weight's printed cell", () => {
    const byName = new Map(RitualResolution.weights().map((it) => [it.weight, it]))
    expect(spiChoices(byName.get('Light')!)).toEqual([2])
    expect(spiChoices(byName.get('Moderate')!)).toEqual([4, 5, 6])
    expect(spiChoices(byName.get('Major')!)).toEqual([12, 13, 14, 15])
  })

  test("the invocation applied is the toggle's or the GM's", () => {
    expect(invocationApplied(false, true, 4)).toBeNull()
    expect(invocationApplied(true, true, 4)).toBe(PowerSources.ORTHODOX_BONUS)
    expect(invocationApplied(true, false, 4)).toBe(4)
  })

  test('the fuel line names HP only when some is paid', () => {
    expect(fuelLine(4, 0)).toBe('4 SPI')
    expect(fuelLine(3, 2)).toBe('3 SPI and 2 HP')
  })
})

// ContractLinesTest.kt's inputs and expected lines.
describe('ContractLinesTest', () => {
  const none = { total: 0, line: null }

  test('the corruption penalty is printed', () => {
    expect(contractLines(16, 0, 0, none, 4, 6, 0, 0)).toContain('-1 for 4 CoR')
  })

  test('hurt, shock and declared states are printed', () => {
    const lines = contractLines(16, -1, -4, { total: -2, line: '-2 declared' }, 0, 6, 0, 0)
    expect(lines).toContain('-1 hurt or tired')
    expect(lines).toContain('-4 shock')
    expect(lines).toContain('-2 declared')
  })

  test('a term worth nothing is not printed', () => {
    const lines = contractLines(16, 0, 0, none, 2, 6, 0, 0)
    expect(lines.some((it) => it.includes('CoR') || it.includes('shock') || it.includes('hurt'))).toBe(false)
  })
})
