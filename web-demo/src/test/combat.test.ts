import { describe } from 'vitest'
import { replay } from './parity'
import { scripted } from './dice'
import * as Concentration from '../rules/Concentration'
import * as Corruption from '../rules/Corruption'
import * as Damage from '../rules/Damage'
import * as Derive from '../rules/Derive'
import * as Divination from '../rules/Divination'
import * as Encumbrance from '../rules/Encumbrance'
import * as ParryBlock from '../rules/ParryBlock'
import * as Pools from '../rules/Pools'
import * as Premades from '../rules/Premades'
import * as ScoreFloors from '../rules/ScoreFloors'
import * as SecondaryCharacteristics from '../rules/SecondaryCharacteristics'
import * as Shields from '../rules/Shields'
import * as Shock from '../rules/Shock'
import * as SkillDefaults from '../rules/SkillDefaults'
import * as StunRecovery from '../rules/StunRecovery'
import * as SuccessRoll from '../rules/SuccessRoll'
import * as Weapons from '../rules/Weapons'
import * as Wounds from '../rules/Wounds'

const character = (n: string) => Premades.toCharacter(Premades.byName(n))
const levels = (n: string) => Derive.of(character(n)).skillLevels
const loc = (t: string | null) => (t === null ? null : Wounds.locationNamed(t))
const band = (b: string) => Divination.difficulties().find((d) => d.band === b)!

describe('pools and corruption', () => {
  replay('combat', 'Pools.hp', Pools.hp)
  replay('combat', 'Pools.fp', Pools.fp)
  replay('combat', 'Pools.spi', Pools.spi)
  replay('combat', 'Pools.halvedForFatigue', (c, m) => Pools.halvedForFatigue(11, Pools.fp(c, m)))
  replay('combat', 'Pools.thresholds', Pools.thresholds)
  replay('combat', 'Corruption.maxFor', Corruption.maxFor)
  replay('combat', 'Corruption.clamp', Corruption.clamp)
  replay('combat', 'Corruption.isLostAt', Corruption.isLostAt)
  replay('combat', 'Corruption.isLostAfter', Corruption.isLostAfter)
  replay('combat', 'Corruption.ritualPenalty', (c) => Corruption.ritualPenalty(c))
  replay('combat', 'Corruption.ritualThresholdsReached', (c) => Corruption.ritualThresholdsReached(c))
  replay('combat', 'Corruption.drainedUse', Corruption.drainedUse)
  replay('combat', 'Corruption.soulDebt', Corruption.soulDebt)
  replay('combat', 'Corruption.gained', () => Corruption.gained())
  replay('combat', 'Corruption.maxForCharacter', (n) => Corruption.maxForCharacter(character(n)))
  replay('combat', 'ScoreFloors.shortfall', (n) => {
    const c = character(n)
    return ScoreFloors.shortfall(SecondaryCharacteristics.of(c.attributes, c.secondaryDelta), c.traits)
  })
  replay('combat', 'ScoreFloors.holdsCeiling', ScoreFloors.holdsCeiling)
  replay('combat', 'Encumbrance.basicLift', (n) => Encumbrance.basicLift(character(n).attributes.st, character(n).traits))
  replay('combat', 'Encumbrance.levelFor', Encumbrance.levelFor)
})

describe('wounds', () => {
  replay('combat', 'Wounds.knowsType', Wounds.knowsType)
  replay('combat', 'Wounds.typeMultiplier', Wounds.typeMultiplier)
  replay('combat', 'Wounds.modifier', (l) => Wounds.modifier(loc(l)!))
  replay('combat', 'Wounds.effectsAt', (l, d, h) => Wounds.effectsAt(loc(l)!, d, h))
  replay('combat', 'Wounds.thresholdFor', (l, o, h) => Wounds.thresholdFor(loc(l)!, o, h))
  replay('combat', 'Wounds.multiplierFor', (l, t) => Wounds.multiplierFor(loc(l), t))
  replay('combat', 'Wounds.injuryFor', (p, l, t) => Wounds.injuryFor(p, loc(l), t))
  replay('combat', 'Wounds.injuryTo', (pool, p, l, t) => Wounds.injuryTo(pool, p, loc(l), t))
  replay('combat', 'Wounds.locationNamed', Wounds.locationNamed)
  replay('combat', 'Wounds.spiritNextRoll', Wounds.spiritNextRoll)
  replay('combat', 'Wounds.majorWoundThreshold', Wounds.majorWoundThreshold)
  replay('combat', 'Wounds.isMajorWound', Wounds.isMajorWound)
  replay('combat', 'Wounds.majorWoundFor', Wounds.majorWoundFor)
  replay('combat', 'Wounds.render', Wounds.render)
  replay('combat', 'Wounds.cripplingRule', () => Wounds.cripplingRule())
  replay('combat', 'StunRecovery.stunsOn', StunRecovery.stunsOn)
  replay('combat', 'Shock.causedBy', Shock.causedBy)
  replay('combat', 'StunRecovery.leavesStunned', StunRecovery.leavesStunned)
  replay('combat', 'StunRecovery.stillStunned', StunRecovery.stillStunned)
  replay('combat', 'StunRecovery.printed', () => StunRecovery.printed())
  replay('combat', 'Shock.printed', () => Shock.printed())
  replay('combat', 'StunRecovery.bonusFor', StunRecovery.bonusFor)
  replay('combat', 'StunRecovery.targetFor', StunRecovery.targetFor)
  replay('combat', 'Shock.ignoredBy', Shock.ignoredBy)
  replay('combat', 'Shock.modifiers', Shock.modifiers)
  replay('combat', 'Shock.penaltyFor', Shock.penaltyFor)
  replay('combat', 'Shock.appliesTo', Shock.appliesTo)
})

describe('weapons and defences', () => {
  replay('combat', 'Damage.thrust', Damage.thrust)
  replay('combat', 'Damage.swing', Damage.swing)
  replay('combat', 'Damage.attacksFor', Damage.attacksFor)
  replay('combat', 'Shields.bashesFor', Shields.bashesFor)
  replay('combat', 'Damage.forAttack', Damage.forAttack)
  replay('combat', 'Damage.boxingBonus', Damage.boxingBonus)
  const w = Weapons.named
  replay('combat', 'Weapons.isRanged', (n) => Weapons.isRanged(w(n)))
  replay('combat', 'Weapons.canParry', (n) => Weapons.canParry(w(n)))
  replay('combat', 'Weapons.parryFor', (n, lv) => Weapons.parryFor(w(n), lv))
  replay('combat', 'Weapons.damageFor', (n, i, st, b) => Weapons.damageFor(w(n), w(n).damage[i], st, b))
  replay('combat', 'Weapons.hasWoundingMultiplier', Weapons.hasWoundingMultiplier)
  replay('combat', 'Weapons.named', Weapons.named)
  replay('combat', 'Weapons.forSkill', (k) => Weapons.forSkill(k).map((x) => x.name))
  replay('combat', 'SkillDefaults.offeredTo', (n) => SkillDefaults.offeredTo(character(n)))
  replay('combat', 'SkillDefaults.ownedAttacks', (n) => SkillDefaults.ownedAttacks(levels(n)))
  replay('combat', 'Weapons.levelFor', (n, wn) => Weapons.levelFor(w(wn), levels(n)))
  replay('combat', 'SkillDefaults.wieldedAt', (n, wn, m, warm) =>
    SkillDefaults.wieldedAt(w(wn), levels(n), m, character(n).attributes.dx, warm),
  )
  replay('combat', 'SkillDefaults.attacks', (n) =>
    SkillDefaults.attacks(SkillDefaults.offeredTo(character(n)), Weapons.all(), levels(n)),
  )
  replay('combat', 'SkillDefaults.mastered', SkillDefaults.mastered)
  replay('combat', 'SkillDefaults.masteryFor', SkillDefaults.masteryFor)
  replay('combat', 'SkillDefaults.weaponNote', SkillDefaults.weaponNote)
  replay('combat', 'SkillDefaults.toolNote', SkillDefaults.toolNote)
  replay('combat', 'SkillDefaults.weaponLinesFor', SkillDefaults.weaponLinesFor)
  const s = Shields.named
  replay('combat', 'Shields.blockPenalty', (t, r) => Shields.blockPenalty(s(t), r))
  replay('combat', 'Shields.blockFor', (t, lv, r) => Shields.blockFor(s(t), lv, r))
  replay('combat', 'Shields.withBonus', (d, t) => Shields.withBonus(d, t === null ? null : s(t)))
  replay('combat', 'Shields.coverageAt', Shields.coverageAt)
  replay('combat', 'Shields.rules', () => Shields.rules())
  replay('combat', 'ParryBlock.versusWeaponPenalty', ParryBlock.versusWeaponPenalty)
  replay('combat', 'ParryBlock.canParryRanged', ParryBlock.canParryRanged)
  replay('combat', 'ParryBlock.unarmedParry', ParryBlock.unarmedParry)
  replay('combat', 'ParryBlock.bareHandParry', ParryBlock.bareHandParry)
  replay('combat', 'ParryBlock.blockFor', ParryBlock.blockFor)
  replay('combat', 'ParryBlock.parryPenalty', ParryBlock.parryPenalty)
})

describe('rolls', () => {
  replay('combat', 'SuccessRoll.printed', SuccessRoll.printed)
  replay('combat', 'SuccessRoll.against', (t, f) => scripted(f, (d) => SuccessRoll.against(t, d)))
  replay('combat', 'Concentration.targetFor', Concentration.targetFor)
  replay('combat', 'Concentration.rollAgainst', (t, dist, f) => scripted(f, (d) => Concentration.rollAgainst(t, dist, d)))
  replay('combat', 'Concentration.printed', () => Concentration.printed())
  const chosen = (o: number | null, spi: number, b: number) => Divination.divinationLevelFor(o, spi, b)
  replay('combat', 'Divination.divinationLevelFor', chosen)
  replay('combat', 'Divination.sourceLine', (o, spi, b) => Divination.sourceLine(chosen(o, spi, b)))
  replay('combat', 'Divination.keyFor', (o, spi, b) => Divination.keyFor(chosen(o, spi, b)))
  replay('combat', 'Divination.targetFor', (sk, b, e, burned) => Divination.targetFor(sk, band(b), e, burned))
  replay('combat', 'Divination.clarityFor', Divination.clarityFor)
  replay('combat', 'Divination.rollAgainst', (t, crit, f) => scripted(f, (d) => Divination.rollAgainst(t, d, crit)))
  replay('combat', 'Divination.modifierTotal', (idx: number[], gm, repeats) =>
    Divination.modifierTotal(idx.map((i) => Divination.modifiers()[i]), new Map(Object.entries(gm)), repeats),
  )
  replay('combat', 'Divination.spiFor', (m, chosen, burned) => {
    const method = Divination.methodNamed(m)
    return Divination.spiFor(method, chosen ?? method.spi, burned)
  })
})
