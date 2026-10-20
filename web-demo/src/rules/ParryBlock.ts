import type { Weapon } from './model'
import { floorDiv, require } from './kt'

export type UnarmedStyle = 'BRAWLING' | 'BOXING' | 'WRESTLING' | 'BARE_HANDS'
export type ParryClass = 'ORDINARY' | 'FENCING' | 'RAPIER'
export const PER_EXTRA_PARRY: Record<ParryClass, number> = { ORDINARY: -4, FENCING: -2, RAPIER: -1 }

export const parryFor = (weapon: Weapon, skillLevel: number): number | null =>
  weapon.parryMod !== null ? floorDiv(skillLevel, 2) + 3 + weapon.parryMod : null

export function unarmedParry(style: UnarmedStyle, skillLevel: number): number {
  switch (style) {
    case 'BRAWLING': case 'BOXING': case 'BARE_HANDS': return floorDiv(skillLevel, 2) + 3
    case 'WRESTLING': return floorDiv(skillLevel - 3, 2) + 3
  }
}

export function bareHandParry(dx: number, brawling: number | null): number | null {
  const own = unarmedParry('BARE_HANDS', dx)
  return brawling === null || unarmedParry('BRAWLING', brawling) < own ? own : null
}

export const VS_WEAPON_PENALTY = -3

export const versusWeaponPenalty = (style: UnarmedStyle) => (style === 'BOXING' ? 0 : VS_WEAPON_PENALTY)

export const canParryRanged = (_style: UnarmedStyle) => false

export const blockFor = (shieldSkillLevel: number, shieldDb: number) => floorDiv(shieldSkillLevel, 2) + 3 + shieldDb

export const BLOCKS_PER_ROUND = 1

export function parryPenalty(priorParries: number, weaponClass: ParryClass = 'ORDINARY'): number {
  require(priorParries >= 0, () => `a parry cannot follow ${priorParries} earlier ones`)
  return priorParries * PER_EXTRA_PARRY[weaponClass]
}
