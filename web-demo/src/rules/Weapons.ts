import { Dice, DiceExpr, type SkillKey } from './model'
import { canonical, eqIc, floorDiv, require, type ValueMap } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { CataloguedWeapon, WeaponMode } from './content/schema.gen'
import * as Damage from './Damage'
import * as Wounds from './Wounds'
import type { DamageRoll } from './Damage'

const PARRY_BASE = 3

export const all = (): CataloguedWeapon[] => ContentLoader.weapons()

export function named(name: string): CataloguedWeapon {
  const found = all().find((it) => eqIc(it.name, name))
  require(found !== undefined, () => `no weapon named '${name}'; the book prints ${all().map((it) => it.name).join(', ')}`)
  return found
}

const ALSO_ARMS = new Map([['Guns (Pistol)', ['Guns (Revolver)']]])

export function forSkill(key: SkillKey): CataloguedWeapon[] {
  const printed = key.specialization !== null ? `${key.name} (${key.specialization})` : key.name
  const also = [...ALSO_ARMS].find(([k]) => eqIc(k, printed))?.[1] ?? []
  const arms = [...new Set([printed, ...also])]
  return all().filter((w) => arms.some((it) => eqIc(w.skill, it)))
}

export function levelFor(weapon: CataloguedWeapon, skillLevels: ValueMap<SkillKey, number>): number | null {
  const found = [...skillLevels.entries()].filter(([key]) => forSkill(key).some((it) => it.name === weapon.name)).map((it) => it[1])
  return found.length === 0 ? null : Math.max(...found)
}

export const isRanged = (weapon: CataloguedWeapon) => weapon.halfRange !== null
export const canParry = (weapon: CataloguedWeapon) => !isRanged(weapon)

export const parryFor = (weapon: CataloguedWeapon, skillLevel: number): number | null =>
  canParry(weapon) ? floorDiv(skillLevel, 2) + PARRY_BASE : null

export const hasWoundingMultiplier = (mode: WeaponMode) => Wounds.knowsType(mode.type)

export function damageFor(weapon: CataloguedWeapon, mode: WeaponMode, st: number, bonus = 0): DamageRoll {
  require(weapon.damage.some((it) => canonical(it) === canonical(mode)), () =>
    `'${mode.printed}' is not one of ${weapon.name}'s attacks: ${weapon.damage.map((it) => it.printed).join(', ')}`)
  let base: DiceExpr
  if (mode.base === 'sw') base = Damage.swing(st)
  else if (mode.base === 'thr') base = Damage.thrust(st)
  else base = Dice.parse(mode.base)
  return { dice: DiceExpr(base.count, base.sides, base.modifier + mode.modifier + bonus), type: mode.type }
}
