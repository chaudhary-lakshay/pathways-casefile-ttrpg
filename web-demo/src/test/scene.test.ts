import { describe, expect, test } from 'vitest'
import { RollLog, logLine, SheetScene, Scenes, RoundState, isSpent, parried } from '../app/RollLog'
import type { RandomSource } from '../rules/RandomSource'
import { throwThree } from '../rules/RandomSource'

const fixed = (...faces: number[]): RandomSource => {
  let i = 0
  return { roll: () => faces[i++ % faces.length] }
}

// RollLog.kt and SheetScene.kt, behaviour for behaviour.
describe('the roll log', () => {
  test('a throw is logged newest first, per character, with its target', () => {
    const log = new RollLog(fixed(1, 2, 3))
    throwThree(log.dice('A', 'Stealth'), 12)
    throwThree(log.dice('B', 'Fright'), null)
    throwThree(log.dice('A', 'Will'), 10)
    expect(log.of('A').map((t) => t.label)).toEqual(['Will', 'Stealth'])
    expect(logLine(log.of('A')[0])).toBe('1 + 2 + 3 = 6 vs 10')
    expect(logLine(log.of('B')[0])).toBe('1 + 2 + 3 = 6')
  })

  test("each character keeps at most its cap, dropping its own oldest", () => {
    const log = new RollLog(fixed(1), 2)
    throwThree(log.dice('A', 'one'), null)
    throwThree(log.dice('B', 'other'), null)
    throwThree(log.dice('A', 'two'), null)
    throwThree(log.dice('A', 'three'), null)
    expect(log.of('A').map((t) => t.label)).toEqual(['three', 'two'])
    expect(log.of('B')).toHaveLength(1)
  })

  test('forget drops one character and notifies', () => {
    const log = new RollLog(fixed(1))
    let heard = 0
    log.subscribe(() => heard++)
    throwThree(log.dice('A', 'x'), null)
    log.forget('A')
    expect(log.of('A')).toEqual([])
    expect(heard).toBe(2)
  })
})

describe('the round', () => {
  test('any declaration spends it, a declared refusal included', () => {
    expect(isSpent(RoundState())).toBe(false)
    expect(isSpent(RoundState({ declared: new Set(['SURPRISE']) }))).toBe(true)
    expect(isSpent(parried(RoundState()))).toBe(true)
  })
})

describe('the sheet scene', () => {
  test('a throw with a target clears the pending spirit penalty; a table throw does not', () => {
    const scene = new SheetScene()
    scene.spiritPending.set(3)
    throwThree(scene.clearingSpiritOn(fixed(2)), null)
    expect(scene.spiritPending.value).toBe(3)
    throwThree(scene.clearingSpiritOn(fixed(2)), 9)
    expect(scene.spiritPending.value).toBe(0)
  })

  test('scenes are kept per character until forgotten', () => {
    const scenes = new Scenes()
    const a = scenes.of('A')
    a.shocked.set(true)
    expect(scenes.of('A')).toBe(a)
    scenes.forget('A')
    expect(scenes.of('A').shocked.value).toBe(false)
  })
})
