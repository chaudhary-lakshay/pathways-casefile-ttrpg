import { describe, expect, test } from 'vitest'
import { sheetModel, skillRollTarget } from '../app/SheetModel'
import { BeyonderSession } from '../app/session/BeyonderSession'
import { SavedSession } from '../app/session/SessionStore'
import * as Premades from '../rules/Premades'
import * as Derive from '../rules/Derive'
import { BeyonderState } from '../rules/model'
import * as SkillRoll from '../rules/SkillRoll'

// MainActivity.kt SheetScreen lines 111-192, and SheetDialogs.kt's skill-roll target.
const cop = Premades.byName('Beat Cop')
const scene = { shocked: false, declaredStates: new Set<string>(), spiritPending: 0, carried: null }

describe('the sheet model', () => {
  test('a mortal at full health: the derived vitals, no penalties, no spirituality pool', () => {
    const session = new BeyonderSession(SavedSession(), cop)
    const m = sheetModel(cop, session, scene)
    const derived = Derive.of(Premades.toCharacter(cop)).secondaries
    expect(m.vitals.maxHp).toBe(derived.maxHp)
    expect(m.vitals.will).toBe(derived.will)
    expect(m.hpNow.current).toBe(derived.maxHp)
    expect(m.hurtOnAttribute).toBe(0)
    expect(m.shockOnAttribute).toBe(0)
    expect(m.pools.map((it) => it.pool)).toEqual(['HP', 'FP'])
    expect(m.sensesOffered).toBe(false)
  })

  test('wounds and shock reach the attribute penalties', () => {
    const session = new BeyonderSession(SavedSession(), cop)
    const max = sheetModel(cop, session, scene).vitals.maxHp
    session.spend('HP', max - 1, max, 10)
    const hurt = sheetModel(cop, session, { ...scene, shocked: true })
    expect(hurt.hpNow.current).toBe(1)
    expect(hurt.hurtOnAttribute).toBeLessThan(0)
    expect(hurt.shockOnAttribute).toBeLessThan(0)
  })

  test("a Beyonder's walk raises spirituality and offers the senses", () => {
    const session = new BeyonderSession(SavedSession({ beyonder: BeyonderState({ pathway: 'Fool', sequence: 9 }) }), cop)
    const m = sheetModel(cop, session, scene)
    expect(m.vitals.maxSpi).toBeGreaterThan(0)
    expect(m.pools.map((it) => it.pool)).toEqual(['HP', 'FP', 'SPI'])
    expect(m.sensesOffered).toBe(true)
  })

  test('a skill roll stands on its level, less a wound, plus the chosen circumstances', () => {
    const session = new BeyonderSession(SavedSession(), cop)
    const m0 = sheetModel(cop, session, scene)
    const skill = cop.skills[0]
    const key = Premades.skillKey(skill)
    const base = Premades.levelFor(cop, skill)
    const plain = skillRollTarget(m0, session, { label: skill.name, base, key }, [], new Set())
    expect(plain.baseTarget).toBe(base)
    session.spend('HP', m0.vitals.maxHp - 1, m0.vitals.maxHp, 10)
    const m1 = sheetModel(cop, session, scene)
    const hurt = skillRollTarget(m1, session, { label: skill.name, base, key }, [], new Set())
    expect(hurt.target).toBeLessThan(plain.target)
    expect(SkillRoll.lines(hurt).length).toBeGreaterThan(0)
  })
})
