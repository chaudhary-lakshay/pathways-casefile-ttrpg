import { eqIc, require, startsWithIc, substringBefore } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { Shield, ShieldRule, UnarmedAttack } from './content/schema.gen'
import * as SkillDefaults from './SkillDefaults'
import * as ParryBlock from './ParryBlock'

export type Facing = 'FRONT' | 'SIDE' | 'REAR'
export type ShieldCoverage = 'COUNTS' | 'GM_DECIDES' | 'WITHHELD'

export const PRINTED_ARC = 'against attacks from the front or shield side'
const BLOCK_PENALTY = new Map([['Light', -1], ['Improvised', -2]])
const RANGED_ONLY = new Set(['Light'])
export const PRINTED_BASH = 'damage = thr-2 cr for light, thr-1 cr for medium, thr cr for heavy'
export const PRINTED_BASH_DEFAULT = 'Skill defaults to Shield/DX or Brawling-2'
const BASH = new Map([['Light', -2], ['Medium', -1], ['Heavy', 0]])
const BASH_BRAWLING_PENALTY = -2
export const SKILL = 'Shield'

export function bashesFor(skill: string, untrained: boolean, defaultBonus: number): UnarmedAttack[] {
  let roll: number
  if (eqIc(skill, SKILL)) roll = 0
  else if (eqIc(skill, SkillDefaults.BRAWLING) && !untrained) roll = BASH_BRAWLING_PENALTY + defaultBonus
  else return []
  return all().flatMap((shield): UnarmedAttack[] => {
    const key = substringBefore(shield.type, ' ')
    const modifier = BASH.get(key)
    if (modifier === undefined) return []
    let notes: string
    if (roll === 0) notes = shield.type
    else if (defaultBonus === 0) notes = `${shield.type}; Brawling-2, a default`
    else notes = `${shield.type}; Brawling-2, a default, +${defaultBonus} Versatile`
    return [{
      skill,
      attack: `Shield Bash (${key})`,
      rollModifier: roll,
      damage: modifier === 0 ? 'thr cr' : `thr${modifier} cr`,
      notes,
      base: 'thr',
      modifier,
      type: 'cr',
      opposedRoll: null,
    }]
  })
}

export const isWeaponInHand = (attack: UnarmedAttack | null) => attack !== null && eqIc(attack.skill, SKILL)

export const all = (): Shield[] => ContentLoader.shields()

export function coverageAt(facing: Facing): ShieldCoverage {
  switch (facing) {
    case 'FRONT': return 'COUNTS'
    case 'SIDE': return 'GM_DECIDES'
    case 'REAR': return 'WITHHELD'
  }
}

export const rules = (): ShieldRule[] => ContentLoader.shieldRules()

export function named(type: string): Shield {
  const found = all().find((it) => startsWithIc(it.type, type))
  require(found !== undefined, () => `no shield type starts with '${type}'; the book prints ${all().map((it) => it.type).join(', ')}`)
  return found
}

export const withBonus = (defense: number, shield: Shield | null) => defense + (shield?.db ?? 0)

export function blockPenalty(shield: Shield, isRanged: boolean): number {
  const key = substringBefore(shield.type, ' ')
  const penalty = BLOCK_PENALTY.get(key)
  if (penalty === undefined) return 0
  return RANGED_ONLY.has(key) && !isRanged ? 0 : penalty
}

export const blockFor = (shield: Shield, shieldSkillLevel: number, isRanged = false) =>
  ParryBlock.blockFor(shieldSkillLevel, shield.db) + blockPenalty(shield, isRanged)
