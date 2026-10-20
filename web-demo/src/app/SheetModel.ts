// What MainActivity.kt's SheetScreen derives before it draws (lines 111-192),
// and SheetDialogs.kt's shared helpers (spiNow, conditionsFor, the skill-roll
// target). One pure function: the sheet's components render its fields.
import * as Conditions from '../rules/Conditions'
import * as Derive from '../rules/Derive'
import * as Divination from '../rules/Divination'
import * as EffortBurn from '../rules/EffortBurn'
import { SpiritualSenses } from '../rules/Mystical'
import type { Attributes, BeyonderState, BoonState, Modifier, SkillKey, Trait } from '../rules/model'
import * as NonStandardPathways from '../rules/NonStandardPathways'
import * as Pools from '../rules/Pools'
import type { PoolStatus } from '../rules/Pools'
import type { PremadeSheet } from '../rules/Premades'
import * as SelfControl from '../rules/SelfControl'
import type { SelfControlCheck } from '../rules/SelfControl'
import * as SheetComparison from '../rules/SheetComparison'
import type { SheetRow } from '../rules/SheetComparison'
import * as Shock from '../rules/Shock'
import * as SkillRoll from '../rules/SkillRoll'
import type { SkillTarget } from '../rules/SkillRoll'
import * as SpiritVision from '../rules/SpiritVision'
import * as StunRecovery from '../rules/StunRecovery'
import * as TraitClauses from '../rules/TraitClauses'
import type { DeclaredStates } from '../rules/TraitClauses'
import * as TraitEffects from '../rules/TraitEffects'
import type { ClauseBonus, TraitClause } from '../rules/TraitEffects'
import * as TraitGrants from '../rules/TraitGrants'
import * as TraitReport from '../rules/TraitReport'
import type { HeldTrait } from '../rules/TraitReport'
import * as Wounds from '../rules/Wounds'
import type { ValueMap } from '../rules/kt'
import type { BeyonderSession } from './session/BeyonderSession'
import { attributesWith, corCeilingOn, digestedWith, liveSkillLevels, secondariesWith, skillLevelOf } from './session/EditedCharacter'
import { standingTargets, vitalLines, type PlayedLine } from './session/SheetLines'

export const SECONDARY_ROWS = 7
export const RITUAL_SKILL = 'Ritualistic Magic'

/** SheetRows.kt `Vitals`. */
export interface Vitals {
  maxHp: number
  maxFp: number
  maxSpi: number
  will: number
  per: number
  dodge: number
}

/** The SheetScene fields the derivation reads. */
export interface SceneValues {
  shocked: boolean
  declaredStates: Set<string>
  spiritPending: number
}

export interface SheetModel {
  drunk: BeyonderState | null
  boon: BoonState | null
  rows: SheetRow[]
  isOwn: boolean
  vitals: Vitals
  secondaryLines: PlayedLine[]
  grantedTraits: Trait[]
  bought: Trait[]
  traits: Trait[]
  maxCor: number
  selfControl: Map<string, SelfControlCheck>
  drawbacks: HeldTrait[]
  advantages: HeldTrait[]
  attributes: Attributes
  skillLevels: ValueMap<SkillKey, number>
  divination: Divination.SkillOrDefault
  ritualLevel: number | null
  hasSpiritVision: boolean
  sensesOffered: boolean
  hpNow: PoolStatus
  fpNow: PoolStatus
  pools: PoolStatus[]
  shockOnAttribute: number
  hurtOnAttribute: number
  allRolls: TraitClause[]
  spiritPendingClauses: TraitClause[]
  states: ClauseBonus
  physicalStates: ClauseBonus
  declaredNow: DeclaredStates
  stunRecoveryTarget: number
  standing: Map<string, number>
  shocked: boolean
  declaredStates: Set<string>
}

/** `isOwn`: the App passes `onDelete != null`, true for a character the player created. */
export function sheetModel(sheet: PremadeSheet, session: BeyonderSession, scene: SceneValues, isOwn = false): SheetModel {
  const drunk = session.state // 111
  const rows = SheetComparison.of(sheet) // 112
  const base = session.characterFor(sheet)
  const walked = digestedWith(base, drunk) // 122-123
  const secondaries = walked?.secondaries ?? Derive.of(base).secondaries // 124
  const vitals: Vitals = {
    maxHp: secondaries.maxHp,
    maxFp: secondaries.maxFp,
    maxSpi: walked?.effectiveSpi ?? base.spi,
    will: secondaries.will,
    per: secondaries.per,
    dodge: secondaries.dodge,
  } // 125-132
  const secondaryLines = vitalLines(sheet, rows, secondariesWith(base, drunk), !isOwn) // 134-135
  const grantedTraits = TraitGrants.grantedTraits(drunk, session.boon) // 137
  const bought = base.traits // 138
  const traits = session.traitsInPlay(sheet) // 139
  const maxCor = corCeilingOn(session.edits, sheet, drunk) // 140
  const selfControl = new Map(SelfControl.checksFor(traits).map((it) => [it.trait, it])) // 141-143
  const drawbacks = [...TraitReport.held(traits, 'DISADVANTAGE'), ...TraitReport.held(traits, 'QUIRK')] // 144-147
  const advantages = TraitReport.held(traits, 'ADVANTAGE') // 148
  const attributes = attributesWith(base, drunk) // 150-152
  const skillLevels = liveSkillLevels({ ...base, beyonder: drunk }) // 153-155
  const ritualLevel = skillLevelOf(RITUAL_SKILL, skillLevels) // 158, 165
  const divination = Divination.divinationLevelFor(skillLevelOf(Divination.SKILL, skillLevels), vitals.maxSpi, TraitEffects.defaultBonus(traits)) // 159-164
  const hasSpiritVision = SpiritVision.isHeldBy(traits) // 166
  const sensesOffered = SpiritualSenses.isOffered(drunk !== null, vitals.maxSpi - session.spiSpent) // 167
  const hpNow = Pools.hp(vitals.maxHp - session.hpLost, vitals.maxHp) // 168
  const fpNow = Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp) // 169
  const shockOnAttribute = Shock.penaltyFor('ATTRIBUTE', scene.shocked, traits) // 170
  const hurtOnAttribute = Conditions.penaltyFor('ATTRIBUTE', hpNow, fpNow) // 171
  const allRolls = TraitClauses.declaredStateClauses(traits) // 172
  const pending = Wounds.spiritNextRoll(scene.spiritPending)
  const spiritPendingClauses = pending ? [pending] : [] // 173
  const declaredNow = TraitClauses.declaredStates(traits, scene.declaredStates, spiritPendingClauses) // 174
  const stunRecoveryTarget = StunRecovery.targetFor(attributes.ht, traits, hurtOnAttribute, shockOnAttribute, declaredNow.physical.total) // 177-183
  const standing = standingTargets([...selfControl.values()], vitals.will, attributes.iq, shockOnAttribute + hurtOnAttribute + declaredNow.all.total) // 187-192
  const pools = [hpNow, fpNow, ...(vitals.maxSpi > 0 ? [Pools.spi(vitals.maxSpi - session.spiSpent, vitals.maxSpi)] : [])] // 196-200
  return {
    drunk, boon: session.boon, rows, isOwn, vitals, secondaryLines, grantedTraits, bought, traits, maxCor, selfControl,
    drawbacks, advantages, attributes, skillLevels, divination, ritualLevel, hasSpiritVision, sensesOffered, hpNow, fpNow,
    pools, shockOnAttribute, hurtOnAttribute, allRolls, spiritPendingClauses, states: declaredNow.all,
    physicalStates: declaredNow.physical, declaredNow, stunRecoveryTarget, standing,
    shocked: scene.shocked, declaredStates: scene.declaredStates,
  }
}

/** SheetScreen's `stateModifiers` (184-186). */
export const stateModifiers = (m: SheetModel, physical: boolean): Modifier[] =>
  TraitClauses.declaredModifiers(m.traits, m.declaredStates, physical, m.spiritPendingClauses)

/** SheetDialogs.kt `spiNow`. */
export const spiNow = (m: SheetModel, session: BeyonderSession, aboutToSpend = 0): PoolStatus | null =>
  m.vitals.maxSpi > 0 ? Pools.spi(m.vitals.maxSpi - session.spiSpent - aboutToSpend, m.vitals.maxSpi) : null

/** SheetDialogs.kt `conditionsFor`. */
export function conditionsFor(
  m: SheetModel,
  session: BeyonderSession,
  key: SkillKey,
  opts: { cor?: number; spiAboutToSpend?: number; withStates?: boolean; withDeclared?: boolean } = {},
): Modifier[] {
  const withStates = opts.withStates ?? true
  const withDeclared = opts.withDeclared ?? withStates
  return [
    ...Conditions.forSkill(key, m.hpNow, m.fpNow, spiNow(m, session, opts.spiAboutToSpend ?? 0), opts.cor ?? session.cor),
    ...(withStates ? Shock.modifiers(m.shocked, m.traits) : []),
    ...(withDeclared ? stateModifiers(m, EffortBurn.isPhysical(key)) : []),
  ]
}

/** SheetRows.kt `RollableSkill`. */
export interface RollableSkill {
  label: string
  base: number
  key: SkillKey
}

/** SheetDialogs.kt lines 88-112: the skill roll's target. */
export const skillRollTarget = (m: SheetModel, session: BeyonderSession, skill: RollableSkill, circumstances: Modifier[], declaredClauses: Set<string>): SkillTarget =>
  SkillRoll.targetFor(skill.label, skill.base, skill.key, [
    ...conditionsFor(m, session, skill.key),
    ...TraitEffects.modifiers(m.traits, skill.key, declaredClauses),
    ...circumstances,
  ])

/** BoonDialog.kt `grantedBy` and `boonPill`. */
export const grantedBy = (potion: boolean, boon: boolean) =>
  potion && boon ? 'the potion or the boon' : boon ? 'the boon' : 'the potion'

export function boonPill(boon: BoonState): string {
  const chain = NonStandardPathways.chainFor(boon).filter((it) => it.sequence === boon.sequence)
  const name = chain.length ? chain[chain.length - 1].name : null
  return `Boon · ${name ?? boon.pathway} · Seq ${boon.sequence}`
}
