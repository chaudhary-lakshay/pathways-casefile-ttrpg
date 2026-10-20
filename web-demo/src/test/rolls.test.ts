import { describe } from 'vitest'
import { replay } from './parity'
import { scripted } from './dice'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as ActiveDefenses from '../rules/ActiveDefenses'
import * as Attacks from '../rules/Attacks'
import * as AttributeRoll from '../rules/AttributeRoll'
import * as CommonModifiers from '../rules/CommonModifiers'
import * as Conditions from '../rules/Conditions'
import * as DegreeOfSuccess from '../rules/DegreeOfSuccess'
import * as EffortBurn from '../rules/EffortBurn'
import * as Equipment from '../rules/Equipment'
import * as FrightCheck from '../rules/FrightCheck'
import * as ModifierResolver from '../rules/ModifierResolver'
import * as NaturalRecovery from '../rules/NaturalRecovery'
import * as Pools from '../rules/Pools'
import * as Ranged from '../rules/Ranged'
import * as Rest from '../rules/Rest'
import * as SkillRoll from '../rules/SkillRoll'
import * as VitalRolls from '../rules/VitalRolls'
import { Modifier } from '../rules/model'

const KEY_A = { name: 'Stealth', specialization: null, attr: { type: 'Buyable', attr: 'DX' }, difficulty: 'AVERAGE' } as const
const crit = Modifier({ id: 'crit', source: { type: 'Situational', description: 'crit' }, target: { type: 'AllRolls' }, effect: { type: 'RequireCrit' }, layer: 'SITUATIONAL' })
const forbid = Modifier({ id: 'no', source: { type: 'Situational', description: 'no' }, target: { type: 'AllRolls' }, effect: { type: 'Forbid' }, layer: 'SITUATIONAL' })
const effect = (i: number) => ContentLoader.frightEffects()[i]
const unarmed = (i: number) => (i < 0 ? null : ContentLoader.unarmedAttacks()[i])
const condition = (c: string) => Ranged.conditions().find((it) => it.condition === c)!

describe('the modifier ledger', () => {
  replay('rolls', 'ModifierResolver.resolve', ModifierResolver.resolve)
  replay('rolls', 'SkillRoll.targetFor', SkillRoll.targetFor)
  replay('rolls', 'SkillRoll.rollAgainst', (target, f) => {
    const t = SkillRoll.targetFor('x', target, KEY_A, [crit, forbid].slice(0, ((target % 3) + 3) % 3))
    return scripted(f, (src) => {
      const o = SkillRoll.rollAgainst(t, src)
      return [o, SkillRoll.effectiveOutcome(o), SkillRoll.degree(o), SkillRoll.lines(o)]
    })
  })
  replay('rolls', 'DegreeOfSuccess.of', DegreeOfSuccess.of)
  replay('rolls', 'DegreeOfSuccess.marginFor', DegreeOfSuccess.marginFor)
})

describe('checks', () => {
  replay('rolls', 'Conditions.forSkill', (k, h, hm, f, fm, s, sm, cor) => Conditions.forSkill(k, Pools.hp(h, hm), Pools.fp(f, fm), Pools.spi(s, sm), cor))
  replay('rolls', 'Conditions.penaltyFor', (k, h, hm, f, fm) => Conditions.penaltyFor(k, Pools.hp(h, hm), Pools.fp(f, fm)))
  replay('rolls', 'VitalRolls.printed', (c, h, hm) => VitalRolls.printed(c, Pools.hp(h, hm)))
  replay('rolls', 'VitalRolls.rowFor', (c, h, hm) => VitalRolls.rowFor(c, Pools.hp(h, hm)))
  replay('rolls', 'VitalRolls.due', (h, hm) => VitalRolls.due(Pools.hp(h, hm)))
  replay('rolls', 'VitalRolls.bonusFor', VitalRolls.bonusFor)
  replay('rolls', 'VitalRolls.targetFor', VitalRolls.targetFor)
  replay('rolls', 'NaturalRecovery.isOffered', (h, hm) => NaturalRecovery.isOffered(Pools.hp(h, hm)))
  replay('rolls', 'NaturalRecovery.dayLine', NaturalRecovery.dayLine)
  replay('rolls', 'NaturalRecovery.targetFor', NaturalRecovery.targetFor)
  replay('rolls', 'NaturalRecovery.rows', () => NaturalRecovery.rows())
  replay('rolls', 'AttributeRoll.clauses', AttributeRoll.clauses)
  replay('rolls', 'AttributeRoll.targetFor', (k, l, t, sh, h, o, s, sm) => AttributeRoll.targetFor(k, l, t, sh, h, o, Pools.spi(s, sm)))
  replay('rolls', 'AttributeRoll.partsFor', (k, l, t, sh, h, s, sm) => AttributeRoll.partsFor(k, l, t, sh, h, Pools.spi(s, sm)))
  replay('rolls', 'Rest.recovery', (n, c, fp) => Rest.recovery(Rest.named(n), c, fp))
  replay('rolls', 'Rest.named', Rest.named)
  replay('rolls', 'CommonModifiers.valueFor', (s, c) => CommonModifiers.valueFor(CommonModifiers.named(s), c))
  replay('rolls', 'CommonModifiers.modifiers', (names: string[], gm) =>
    CommonModifiers.modifiers(names.map(CommonModifiers.named), new Map(Object.entries(gm))))
  replay('rolls', 'CommonModifiers.gmRuling', CommonModifiers.gmRuling)
  replay('rolls', 'Equipment.referenceFor', Equipment.referenceFor)
})

describe('defences and effort', () => {
  replay('rolls', 'ActiveDefenses.everyDefence', ActiveDefenses.everyDefence)
  replay('rolls', 'ActiveDefenses.state', (d, t) => ActiveDefenses.state(new Set(d), t))
  replay('rolls', 'ActiveDefenses.forfeitingActions', () => ActiveDefenses.forfeitingActions())
  replay('rolls', 'ActiveDefenses.fpCostOf', ActiveDefenses.fpCostOf)
  replay('rolls', 'ActiveDefenses.printedBoost', ActiveDefenses.printedBoost)
  replay('rolls', 'ActiveDefenses.printed', () => [
    ActiveDefenses.printedAcrobaticDodge(), ActiveDefenses.printedDive(), ActiveDefenses.printedRetreat(), ActiveDefenses.printedStun(),
  ])
  replay('rolls', 'ActiveDefenses.unappliedModifiers', () => ActiveDefenses.unappliedModifiers())
  replay('rolls', 'ActiveDefenses.acrobaticDodge', ActiveDefenses.acrobaticDodge)
  replay('rolls', 'ActiveDefenses.dive', ActiveDefenses.dive)
  replay('rolls', 'ActiveDefenses.retreat', ActiveDefenses.retreat)
  replay('rolls', 'ActiveDefenses.printedFor', ActiveDefenses.printedFor)
  replay('rolls', 'EffortBurn.whenAllowed', EffortBurn.whenAllowed)
  replay('rolls', 'EffortBurn.pool', EffortBurn.pool)
  replay('rolls', 'EffortBurn.bonusFor', EffortBurn.bonusFor)
  replay('rolls', 'EffortBurn.modifier', EffortBurn.modifier)
  replay('rolls', 'EffortBurn.kindFor', EffortBurn.kindFor)
  replay('rolls', 'EffortBurn.requireOnePool', (fp, spi) => { EffortBurn.requireOnePool(fp, spi); return true })
})

describe('fright', () => {
  replay('rolls', 'FrightCheck.rollAgainst', (t, f) => scripted(f, (src) => FrightCheck.rollAgainst(t, src)))
  replay('rolls', 'FrightCheck.effectFor', (r) => FrightCheck.effectFor(r))
  replay('rolls', 'FrightCheck.text', (i, k) => FrightCheck.text(effect(i), k))
  replay('rolls', 'FrightCheck.grants', (i, k) => [FrightCheck.grantsQuirk(effect(i), k), FrightCheck.grantsDisadvantage(effect(i), k)])
  replay('rolls', 'FrightCheck.recoveryFor', (i, k) => FrightCheck.recoveryFor(effect(i), k))
  replay('rolls', 'FrightCheck.recovery', (i, k) => {
    const rec = FrightCheck.recoveryFor(effect(i), k)!
    return [
      FrightCheck.recoveryBonus(rec, [{ name: 'Combat Reflexes', cost: 15, kind: 'ADVANTAGE' }]),
      FrightCheck.levelFor(rec, 11, 13),
      FrightCheck.takesWillClauses(rec),
    ]
  })
  replay('rolls', 'FrightCheck.leavesStunned', (i, k, r) => FrightCheck.leavesStunned(effect(i), k, r))
  replay('rolls', 'FrightCheck.durationLine', (i, k) => {
    const d = FrightCheck.durationFor(effect(i), k)!
    return [FrightCheck.secondsFor(d, 12, 11, 13), FrightCheck.durationLine(d, 12, 11, 13)]
  })
  replay('rolls', 'FrightCheck.triggers', () => FrightCheck.triggers())
})

describe('attack options', () => {
  replay('rolls', 'Attacks.attackCount', Attacks.attackCount)
  replay('rolls', 'Attacks.fpCost', Attacks.fpCost)
  replay('rolls', 'Attacks.damageBonus', Attacks.damageBonus)
  replay('rolls', 'Attacks.atLevel', Attacks.atLevel)
  replay('rolls', 'Attacks.modifiers', (p, lv, i) => Attacks.modifiers(p, lv, unarmed(i)))
  replay('rolls', 'Attacks.atRange', Attacks.atRange)
  replay('rolls', 'Attacks.withAttack', (p, i) => Attacks.withAttack(p, unarmed(i)))
  replay('rolls', 'Attacks.withManeuver', Attacks.withManeuver)
  replay('rolls', 'Attacks.offersStrongBlow', Attacks.offersStrongBlow)
  replay('rolls', 'Attacks.maxDeceptive', Attacks.maxDeceptive)
  replay('rolls', 'Ranged.bandFor', Ranged.bandFor)
  replay('rolls', 'Ranged.alternativesTo', (c) => [...Ranged.alternativesTo(c)])
  replay('rolls', 'Ranged.modifiers', (band, conds: string[], gm, acc, cancelled, cause, red) =>
    Ranged.modifiers({
      band: band === null ? null : Ranged.bands()[band],
      conditions: conds.map(condition),
      gmValue: new Map(Object.entries(gm)),
      weaponAcc: acc,
      aimCancelled: cancelled,
      darknessCause: cause,
      darknessReduction: red,
    }))
  replay('rolls', 'Ranged.offeredConditions', (c) => Ranged.offeredConditions(c).map((it) => it.condition))
  replay('rolls', 'Ranged.meleeModifiers', (conds: string[]) => Ranged.meleeModifiers(conds.map(condition)))
  replay('rolls', 'Ranged.printedAim', () => Ranged.printedAim())
})

