// The Defend dialog's pure helpers: Parries.kt and AcrobaticDodge.kt, each
// named as in Kotlin. The round itself is RoundState in RollLog.ts.
import type { AcrobaticSkill } from './RollLog'
import { acrobaticLabel } from './RollLog'
import * as ActiveDefenses from '../rules/ActiveDefenses'
import type { DefenseBoost } from '../rules/ActiveDefenses'
import type { CataloguedWeapon, Defense, Shield } from '../rules/content/schema.gen'
import type { ValueMap } from '../rules/kt'
import type { Modifier, SkillKey, Trait } from '../rules/model'
import * as ParryBlock from '../rules/ParryBlock'
import type { UnarmedStyle } from '../rules/ParryBlock'
import * as Shields from '../rules/Shields'
import type { Facing, ShieldCoverage } from '../rules/Shields'
import * as SkillDefaults from '../rules/SkillDefaults'
import type { Untrained, WeaponMastery } from '../rules/SkillDefaults'
import * as SkillRoll from '../rules/SkillRoll'
import * as TraitEffects from '../rules/TraitEffects'
import * as Weapons from '../rules/Weapons'

const UNARMED = new Map<string, UnarmedStyle>([['Brawling', 'BRAWLING'], ['Boxing', 'BOXING'], ['Wrestling', 'WRESTLING']])
export const BARE_HANDS = 'DX, bare hands'
export const SHIELD_SKILL = 'Shield'

export type UnarmedParry = [string, UnarmedStyle, number]

/** Parries.kt `unarmedParries`: each held style, and DX bare-handed where Brawling does not parry better. */
export function unarmedParries(skillLevels: ValueMap<SkillKey, number>, dx: number): UnarmedParry[] {
  const held = [...skillLevels.entries()]
    .flatMap(([key, level]): UnarmedParry[] => (UNARMED.has(key.name) ? [[key.name, UNARMED.get(key.name)!, level]] : []))
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
  const brawling = held.filter((it) => it[1] === 'BRAWLING').map((it) => it[2])
  const bare = ParryBlock.bareHandParry(dx, brawling.length > 0 ? Math.max(...brawling) : null)
  return [
    ...held.map(([name, style, level]): UnarmedParry => [name, style, ParryBlock.unarmedParry(style, level)]),
    ...(bare !== null ? [[BARE_HANDS, 'BARE_HANDS', bare] as UnarmedParry] : []),
  ]
}

export const unskilledParriers = (weapons: CataloguedWeapon[], skillLevels: ValueMap<SkillKey, number>, mastery: WeaponMastery | null, dx: number): string[] =>
  weapons.filter((it) => Weapons.canParry(it) && SkillDefaults.wieldedAt(it, skillLevels, mastery, dx) === null).map((it) => it.name)

export function unskilledParryNote(names: string[], cells: Defense[]): [string, string] | null {
  if (names.length === 0) return null
  const armed = cells.find((it) => it.defense.startsWith('Parry (Armed)'))?.formula
  const unarmed = cells.find((it) => it.defense.startsWith('Parry (Unarmed)'))?.formula
  if (armed == null || unarmed == null) return null
  const line = `No parry offered with ${names.join(', ')}: no skill on this sheet wields it, and an untrained armed parry is the GM's call.`
  const why = `The Parry (Armed) cell prints "${armed}" and names no untrained operand, where the unarmed cell prints "${unarmed}": DX itself, not ` +
    "Brawling's default. So the book gives an untrained armed parry no number."
  return [line, why]
}

export function shieldPair(own: number, shielded: number | null, coverage: ShieldCoverage): number[] {
  if (shielded === null) return [own]
  return coverage === 'COUNTS' ? [shielded] : [own, shielded]
}

export function shieldedTargets(base: number, shield: Shield | null, coverage: ShieldCoverage, everyDefence = 0): number[] {
  const own = base + everyDefence
  return shieldPair(own, shield !== null ? Shields.withBonus(own, shield) : null, coverage)
}

export const bareBlock = (shieldSkill: number, carried: Shield | null, isRanged: boolean) =>
  ParryBlock.blockFor(shieldSkill, 0) + (carried !== null ? Shields.blockPenalty(carried, isRanged) : 0)

export const blockTargets = (shield: Shield, shieldSkill: number, coverage: ShieldCoverage, isRanged: boolean, extra = 0): number[] =>
  shieldPair(bareBlock(shieldSkill, shield, isRanged) + extra, Shields.blockFor(shield, shieldSkill, isRanged) + extra, coverage)

export function shotBlockNote(shield: Shield, shieldSkill: number, coverage: ShieldCoverage, extra: number): string | null {
  if (Shields.blockPenalty(shield, true) === Shields.blockPenalty(shield, false)) return null
  return `Against ranged attacks: ${blockTargets(shield, shieldSkill, coverage, true, extra).join(' → ')}`
}

export const shieldForDb = (carried: Shield | null, facing: Facing): Shield | null =>
  carried !== null && Shields.coverageAt(facing) !== 'WITHHELD' ? carried : null

/** "+2 to ONE defence": the named row alone. */
export const boostOn = (label: string, boost: [DefenseBoost, string] | null) => (boost !== null && boost[1] === label ? ActiveDefenses.BOOST_BONUS : 0)

export function defenceLabels(dodge: boolean, armed: [string, number][], unarmed: UnarmedParry[], block: boolean): string[] {
  return [
    ...(dodge ? ['Dodge'] : []),
    ...armed.map(([name]) => `Parry (${name})`),
    ...unarmed.map(([name]) => `Parry (${name})`),
    ...(block ? ['Block'] : []),
  ]
}

export const parryScore = (base: number, penalty: number, boost: number, retreat: number, vsWeapon = 0) => base + penalty + vsWeapon + boost + retreat

export function armedParries(weapons: CataloguedWeapon[], skillLevels: ValueMap<SkillKey, number>, mastery: WeaponMastery | null, dx: number, warmed: Set<string> = new Set()): [string, number][] {
  return weapons.flatMap((weapon): [string, number][] => {
    const level = SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx, warmed.has(weapon.name))
    if (level === null) return []
    const parry = Weapons.parryFor(weapon, level)
    return parry === null ? [] : [[weapon.name, parry]]
  })
}

/** AcrobaticDodge.kt `acrobaticsFor`: the trained skill, else the default already composed. */
export function acrobaticsFor(skillLevels: ValueMap<SkillKey, number>, untrained: Untrained[]): AcrobaticSkill | null {
  const trained = [...skillLevels.entries()].find(([key]) => key.name === ActiveDefenses.ACROBATIC_SKILL)
  if (trained !== undefined) return { key: trained[0], level: trained[1], untrainedSource: null }
  const row = untrained.find((it) => it.key.name === ActiveDefenses.ACROBATIC_SKILL)
  return row !== undefined ? { key: row.key, level: row.level, untrainedSource: row.source } : null
}

export const acrobaticTarget = (skill: AcrobaticSkill, conditions: (k: SkillKey) => Modifier[], traits: Trait[]) =>
  SkillRoll.targetFor(acrobaticLabel(skill), skill.level, skill.key, [...conditions(skill.key), ...TraitEffects.modifiers(traits, skill.key)])
