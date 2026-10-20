import { describe, expect, test } from 'vitest'
import {
  aimAcc, AIM_ROWS, Armed, attackCaption, attackStanding, attackTargets, charmFor, isFired, meleeModifiers, rangedModifiers, termLine, toggled, traitTerms, woundingNote,
} from '../app/AttackModel'
import { applyTo } from '../app/session/EditedCharacter'
import { SavedEdits } from '../app/session/SessionStore'
import * as Attacks from '../rules/Attacks'
import * as CharmedObject from '../rules/CharmedObject'
import * as Conditions from '../rules/Conditions'
import { Attributes, Buyable, Character, SkillKey, Trait, type Modifier } from '../rules/model'
import * as Pools from '../rules/Pools'
import * as Ranged from '../rules/Ranged'
import * as Shock from '../rules/Shock'
import * as TraitClauses from '../rules/TraitClauses'
import * as TraitEffects from '../rules/TraitEffects'
import * as Weapons from '../rules/Weapons'
import * as Wounds from '../rules/Wounds'

// AttackDialogTest.kt, SpiritPendingTest.kt and CharmedSkillTest.kt: their inputs and expected values.
const flats = (mods: Modifier[]) => mods.map((it) => (it.effect.type === 'Flat' ? it.effect.n : NaN))
const brawling = SkillKey('Brawling', null, Buyable('DX'), 'EASY')
const guns = SkillKey('Guns', 'Pistol', Buyable('DX'), 'EASY')
const hurt = [...Conditions.forSkill(brawling, Pools.hp(4, 14), Pools.fp(11, 11)), ...Shock.modifiers(true, [])]

describe('AttackDialogTest', () => {
  test('a firearm is fired at range and a melee weapon is not', () => {
    expect(isFired(Armed(Weapons.named('Revolver (.38 cal)')))).toBe(true)
    expect(isFired(Armed(Weapons.named('Bolt-Action Rifle')))).toBe(true)
    expect(isFired(Armed(Weapons.named('Broadsword')))).toBe(false)
    expect(isFired(Armed(Weapons.named('Combat Knife')))).toBe(false)
    expect(isFired(null)).toBe(false)
  })

  test('only a fired weapon lends its Acc to an Aim', () => {
    expect(aimAcc(Armed(Weapons.named('Bolt-Action Rifle')))).toBe(4)
    expect(aimAcc(Armed(Weapons.named('Revolver (.38 cal)')))).toBe(2)
    expect(aimAcc(Armed(Weapons.named('Hand Axe')))).toBeNull()
    expect(aimAcc(Armed(Weapons.named('Combat Knife')))).toBeNull()
    expect(aimAcc(null)).toBeNull()
  })

  test('a prone target is priced once, on whichever side of the toggle the plan is', () => {
    const prone = new Set(['Target is prone'])
    expect(flats(meleeModifiers(Attacks.AttackPlan({ isRanged: false }), prone))).toEqual([-4])
    expect(meleeModifiers(Attacks.AttackPlan({ isRanged: true }), prone)).toEqual([])
    expect(flats(meleeModifiers(Attacks.AttackPlan(), new Set(Ranged.conditions().map((it) => it.condition))))).toEqual([-4])
  })

  test('a weapon opens on the first attack the book prints', () => {
    const knife = Armed(Weapons.named('Combat Knife'))
    expect(knife.mode.printed).toBe('sw-1 cut')
  })

  test("the severity the GM picked reaches the darkness row's modifier, and only while the row is lit", () => {
    const darkness = Ranged.conditions().find((it) => it.worstCase !== null)!
    const ranged = Attacks.AttackPlan({ isRanged: true })
    const lit = new Set([darkness.condition])
    expect(flats(rangedModifiers(ranged, null, lit, null, false, 'DARKNESS', 0, null))).toEqual([darkness.modifier])
    expect(flats(rangedModifiers(ranged, null, lit, null, false, 'DARKNESS', 0, darkness.worstCase))).toEqual([darkness.worstCase])
    expect(rangedModifiers(ranged, null, new Set(), null, false, 'DARKNESS', 0, darkness.worstCase)).toEqual([])
  })

  test('the attack roll takes the trait terms and the declared to-hit clause', () => {
    const killer = [Trait(TraitEffects.RELUCTANT_KILLER, 5, 'DISADVANTAGE')]
    expect(traitTerms(killer, guns, null, new Set())).toEqual([])
    const clauses = TraitClauses.attackClauses(killer)
    expect(flats(traitTerms(killer, guns, TraitEffects.clauseKey(clauses[0]), new Set()))).toEqual([-4])
    expect(flats(traitTerms(killer, guns, TraitEffects.clauseKey(clauses[1]), new Set()))).toEqual([-2])
    const balance = [Trait(TraitEffects.PERFECT_BALANCE, 15, 'ADVANTAGE')]
    expect(flats(traitTerms(balance, SkillKey('Acrobatics', null, Buyable('DX'), 'HARD'), null, new Set()))).toEqual([2])
  })

  test('the tracker line is what the ledger applied for the trackers alone', () => {
    const deceptive = Attacks.modifiers(Attacks.AttackPlan({ deceptive: 2 }), 16)
    const standing = attackStanding('Brawling', 16, brawling, hurt, deceptive)
    expect(standing.fromTrackers).toBe(-5)
    expect(standing.target.target).toBe(16 - 4 - 5)
    expect(attackStanding('Brawling', 16, brawling, [], deceptive).fromTrackers).toBe(0)
  })

  test('a declared state is a picked term and never reaches the tracker line', () => {
    const held = [Trait('Addiction: Laudanum', 10, 'DISADVANTAGE'), Trait(TraitEffects.RELUCTANT_KILLER, 5, 'DISADVANTAGE')]
    const clauses = TraitClauses.allRollsClauses(held)
    const states = TraitClauses.allRollsModifiers(clauses, new Set(clauses.map(TraitEffects.clauseKey)))
    const killer = traitTerms(held, brawling, TraitEffects.clauseKey(TraitClauses.attackClauses(held)[0]), new Set())
    const standing = attackStanding('Brawling', 16, brawling, hurt, [...killer, ...states])
    expect(standing.fromTrackers).toBe(-5)
    expect(standing.target.target).toBe(16 + flats([...killer, ...states]).reduce((a, b) => a + b, 0) - 5)
    expect(attackStanding('Brawling', 16, brawling, [...hurt, ...states], killer).fromTrackers).toBeLessThan(-7)
  })

  test("the level and the printed terms add up to the attack's target", () => {
    let plans = 0
    for (const maneuver of Attacks.ATTACK_MANEUVERS) {
      for (let deceptive = 0; deceptive <= Attacks.maxDeceptive(16); deceptive++) {
        for (const location of [null, ...Wounds.locations()]) {
          for (const [trackers, cost] of [[[], 0], [hurt, -5]] as [Modifier[], number][]) {
            const loc = location !== null ? Wounds.modifier(location) : null
            const picked = [...Attacks.modifiers(Attacks.AttackPlan({ maneuver, deceptive }), 16), ...(loc !== null ? [loc] : [])]
            const standing = attackStanding('Brawling', 16, brawling, trackers, picked)
            expect(standing.fromTrackers).toBe(cost)
            expect(standing.target.target).toBe(16 + flats(picked).reduce((a, b) => a + b, 0) + cost)
            plans++
          }
        }
      }
    }
    expect(plans).toBeGreaterThan(100)
  })

  test('a second speed or a second aim replaces the first', () => {
    const walking = 'Target walking (up to 3 meters/sec)'
    const running = 'Target running (4+ meters/sec)'
    const moved = 'Shooter moved this turn'
    const [brief, long] = [...AIM_ROWS()]
    expect(toggled(new Set([walking]), running)).toEqual(new Set([running]))
    expect(toggled(new Set([moved, running]), 'Target is stationary')).toEqual(new Set([moved, 'Target is stationary']))
    expect(toggled(new Set([brief, running]), long)).toEqual(new Set([long, running]))
    expect(toggled(new Set([running]), moved)).toEqual(new Set([running, moved]))
    expect(toggled(new Set([running]), running)).toEqual(new Set())
    const names = Ranged.conditions().map((it) => it.condition)
    let picked = new Set<string>()
    for (const name of [...names, ...[...names].reverse().slice(1)]) {
      picked = toggled(picked, name)
      rangedModifiers(Attacks.AttackPlan({ isRanged: true }), null, picked, 2, false, 'DARKNESS', 0, null)
    }
  })

  test('the damage line says there only where a shot was called', () => {
    expect(woundingNote(null, 'pi+')).toBe(', ×1.5')
    expect(woundingNote(null, 'cr')).toBe('')
    expect(woundingNote(Wounds.locationNamed('Skull'), 'cr')).toBe(', ×4 there')
    expect(woundingNote(Wounds.locationNamed('Torso'), 'pi+')).toBe(', ×1.5 there')
  })

  test('terms that cancel print +0, not no modifier', () => {
    expect(termLine([])).toBe('no modifier')
    expect(termLine([0])).toBe('no modifier')
    expect(termLine([-3, 3])).toBe('+0')
    expect(termLine([-3])).toBe('-3')
  })
})

describe('SpiritPendingTest', () => {
  test('only the first of several attacks takes a pending spirit penalty', () => {
    expect(attackTargets(7, 3, 3)).toEqual([7, 10, 10])
    expect(attackTargets(7, 2, 0)).toEqual([7, 7])
  })

  test('a multi-attack caption names the first target and the rest', () => {
    expect(attackCaption(7, 3, 3)).toBe('first: 3d6 ≤ 7; after: 3d6 ≤ 10')
    expect(attackCaption(18, 2, 1)).toBe('3d6 ≤ 16, 17–18 fail, each')
    expect(attackCaption(7, 2, 0)).toBe('3d6 ≤ 7, each')
    expect(attackCaption(7, 1, 3)).toBe('3d6 ≤ 7')
  })
})

describe('CharmedSkillTest', () => {
  test('the charm is a declared +1 on its skill alone, and the Attack dialog offers its chip', () => {
    const mortal = Character({ name: 'test', attributes: Attributes({ iq: 10 }) })
    const held = applyTo(SavedEdits({ traits: [CharmedObject.ROW], charmedSkill: { name: 'Guns', specialization: 'Pistol' } }), mortal).traits
    const key = TraitEffects.clauseKey(TraitClauses.skillRollClauses(held, guns)[0])
    expect(traitTerms(held, guns, null, new Set())).toEqual([])
    expect(flats(traitTerms(held, guns, null, new Set([key])))).toEqual([1])
    expect(charmFor(held, guns) !== null && TraitEffects.clauseKey(charmFor(held, guns)!)).toBe(key)
    expect(charmFor(held, { ...guns, specialization: 'Rifle' })).toBeNull()
  })
})
