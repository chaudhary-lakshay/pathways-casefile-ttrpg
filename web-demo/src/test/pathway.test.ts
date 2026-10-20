import { describe, expect, test } from 'vitest'
import {
  actingMethodRoll, corLadder, digestionCells, drinkCaption, drinkLine, drinkTarget, pcrLine, spendLabel, startingDigestion,
} from '../app/PathwayModel'
import { BeyonderSession } from '../app/session/BeyonderSession'
import { SavedSession } from '../app/session/SessionStore'
import { BeyonderState } from '../rules/model'
import * as Premades from '../rules/Premades'
import type { RandomSource } from '../rules/RandomSource'

// The Kotlin tests' own inputs and expected values: PathwayDrinkTest,
// PathwayScreenTest, AbilitySpendTest and two of
// BeyonderSessionTest's (app/src/test/kotlin/com/pathways/app).
const B = (pathway: string, sequence: number, digestion = 0, drawback: string | null = null) => BeyonderState({ pathway, sequence, digestion, drawback })
const printed = (s: BeyonderState) => s.sequence >= 6 && s.sequence <= 9
const fool = (digestion: number) => B('Fool', 9, digestion)

describe('PathwayDrinkTest', () => {
  test("a walker is offered the next potion whichever rung is browsed", () => {
    const drunk = B('Fool', 9, 40)
    expect(drinkTarget('Fool', 9, drunk, printed)).toEqual(B('Fool', 8))
    expect(drinkTarget('Fool', 6, drunk, printed)).toEqual(B('Fool', 8))
  })
  test("the next potion keeps the walked chain's drawback", () => {
    expect(drinkTarget('Hanged Man', 7, B('Hanged Man', 9, 0, 'Paranoia'), printed)?.drawback).toBe('Paranoia')
  })
  test('another pathway offers a walker nothing', () => {
    expect(drinkTarget('Sun', 8, B('Fool', 9), printed)).toBeNull()
  })
  test('no potion is offered past the last printed rung', () => {
    expect(drinkTarget('Fool', 6, B('Fool', 6), printed)).toBeNull()
  })
  test("a mortal is offered the browsed pathway's first potion only", () => {
    expect(drinkTarget('Sun', 9, null, printed)).toEqual(B('Sun', 9))
    expect(drinkTarget('Sun', 8, null, printed)).toBeNull()
  })
  test('the caption names the potion before the roll', () => {
    expect(drinkCaption(B('Fool', 8), B('Fool', 9, 40), 0, 30)).toBe(
      'next: Sequence 8 of the Fool Pathway · at 40% digested, rolls 3d6 ≤ 8 — on success +1d6 CoR',
    )
  })
  test("a mortal's caption is the refusal alone, or nothing", () => {
    expect(drinkCaption(B('Sun', 9), null, 0, 10)).toBeNull()
    expect(drinkCaption(B('Abyss', 9), null, 0, 0)).toBe(drinkLine(null, 0, 0))
  })
  test('the digestion bar fills a cell per whole ten percent', () => {
    expect([0, 40, 45, 100].map(digestionCells)).toEqual([0, 4, 4, 10])
  })
  test('the corruption ladder prints all four rungs and marks the reached', () => {
    const ladder = corLadder(5)
    expect(ladder.map(([t]) => t.cor)).toEqual([3, 5, 8, 10])
    expect(ladder.map(([, reached]) => reached)).toEqual([true, true, false, false])
  })
})

describe('PathwayScreenTest', () => {
  test('the roll is printed with what advancing early costs', () => {
    expect(pcrLine(fool(40), 0)).toBe('3d6 ≤ 8 — on success +1d6 CoR')
    expect(pcrLine(fool(40), 2)).toBe('3d6 ≤ 6 — on success +1d6 CoR')
  })
  test('a fully digested potion prints no cost', () => {
    expect(pcrLine(fool(100), 0)).toBe('3d6 ≤ 19')
  })
  test('the Drink button prints the roll the tracker printed', () => {
    const line = drinkLine(fool(40), 0, 30)!
    expect(line).toContain(pcrLine(fool(40), 0))
    expect(line).toContain('40% digested')
    expect(line).not.toContain('lost to corruption')
  })
  test('an advancement whose cost reaches the ceiling says it ends the character', () => {
    const line = drinkLine(fool(40), 5, 6)!
    expect(line).toContain('Maximum CoR after this potion is 6')
    expect(line).toContain('; 6 CoR reaches it')
    expect(line).toContain('lost to corruption even if the potion integrates')
  })
  test('an advancement the die may push to the ceiling names the roll that does', () => {
    const line = drinkLine(fool(40), 3, 7)!
    expect(line).toContain('a 4 or more on the 1d6 reaches it')
    expect(line).not.toContain('even if the potion integrates')
    expect(drinkLine(fool(40), 3, 10)!).not.toContain('Maximum CoR')
  })
  test('a ceiling below one is printed as the reason the potion is refused', () => {
    const line = drinkLine(fool(40), 0, 0)!
    expect(line).toContain(pcrLine(fool(40), 0))
    expect(line).toContain('CoR ceiling after this potion would be 0')
    expect(line).toContain('cannot be drunk')
    expect(line).not.toContain('lost to corruption')
    expect(drinkLine(fool(100), 0, -3)!).toContain('would be -3,')
  })
  test('a first potion prints nothing unless it is refused', () => {
    expect(drinkLine(null, 0, 9)).toBeNull()
    const refused = drinkLine(null, 0, 0)!
    expect(refused).toContain('cannot be drunk')
    expect(refused).not.toContain('3d6')
  })
  test('a potion that lowers the ceiling onto the total says so at no cost', () => {
    const line = drinkLine(fool(100), 9, 9)!
    expect(line).toContain('3d6 ≤ 10')
    expect(line).toContain('lost to corruption')
    expect(drinkLine(fool(100), 8, 9)!).not.toContain('lost to corruption')
  })
})

describe('AbilitySpendTest', () => {
  test('the Spend button names every CoR the tap charges', () => {
    expect(spendLabel(1, -3)).toBe('Spend 1 SPI · +2 CoR (Soul Debt, drained)')
    expect(spendLabel(2, 1)).toBe('Spend 2 SPI · +1 CoR (Soul Debt)')
    expect(spendLabel(1, 5)).toBe('Spend 1 SPI')
  })
})

describe('BeyonderSessionTest', () => {
  const cop = Premades.byName('Beat Cop')
  const fixed = (face: number): RandomSource => ({ roll: () => face })

  test('the Acting Method roll adds its 3d6 as percent once per picture', () => {
    const s = new BeyonderSession(SavedSession({ beyonder: fool(0) }), cop)
    expect(actingMethodRoll(s, 0, fixed(4))).toBe(12)
    expect(s.state?.digestion).toBe(12)
    expect(actingMethodRoll(s, 0, fixed(4))).toBeNull()
    const full = new BeyonderSession(SavedSession({ beyonder: fool(100) }), cop)
    expect(actingMethodRoll(full, 100, { roll: () => { throw new Error('rolled at 100%') } })).toBeNull()
  })

  test('a starting Beyonder rolls 4d20 percent once, from 0', () => {
    const s = new BeyonderSession(SavedSession({ beyonder: fool(0) }), cop)
    const logged: number[][] = []
    const dice: RandomSource = { roll: (sides) => { expect(sides).toBe(20); return 7 }, thrown: (faces) => void logged.push(faces) }
    expect(startingDigestion(s, 0, dice)).toBe(28)
    expect(s.state?.digestion).toBe(28)
    expect(logged).toEqual([[7, 7, 7, 7]])
    expect(startingDigestion(s, 0, dice)).toBeNull()
    expect(startingDigestion(s, 28, dice)).toBeNull()
  })
})
