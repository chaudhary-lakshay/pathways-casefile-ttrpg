import { Dice, DiceExpr, type Outcome } from './model'
import { coerceIn, eqIc, error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import { unarmedWounds } from './content/members'
import type { UnarmedAttack } from './content/schema.gen'
import * as Shields from './Shields'
import type { RandomSource } from './RandomSource'

export const MAX_BOXING_BONUS = 2

export interface DamageRoll {
  dice: DiceExpr
  type: string
}

export const renderRoll = (d: DamageRoll) => `${Dice.render(d.dice)} ${d.type}`

export interface DamageResult {
  faces: number[]
  total: number
  type: string
  doubled: boolean
}

export function roll(damage: DamageRoll, source: RandomSource): DamageResult {
  const faces: number[] = []
  for (let i = 0; i < damage.dice.count; i++) faces.push(source.roll(damage.dice.sides))
  source.thrown?.(faces, null)
  return { faces, total: Math.max(faces.reduce((a, b) => a + b, 0) + damage.dice.modifier, 0), type: damage.type, doubled: false }
}

export const onAttack = (thrown: DamageResult, attack: Outcome): DamageResult =>
  attack === 'CRITICAL_SUCCESS' ? { ...thrown, total: thrown.total * 2, doubled: true } : thrown

export const thrust = (st: number): DiceExpr => rowFor(st).thrust
export const swing = (st: number): DiceExpr => rowFor(st).swing

function rowFor(st: number) {
  const table = ContentLoader.thrustSwing()
  const row = table.get(st)
  if (row !== undefined) return row
  const keys = [...table.keys()]
  return error(`the thrust/swing table has no row for ST ${st}; it runs ${Math.min(...keys)}..${Math.max(...keys)}`)
}

export const attacksFor = (skill: string, untrained = false, defaultBonus = 0): UnarmedAttack[] => [
  ...ContentLoader.unarmedAttacks().filter((it) => eqIc(it.skill, skill)),
  ...Shields.bashesFor(skill, untrained, defaultBonus),
]

export const boxingBonus = (dx: number, boxingLevel: number) => coerceIn(boxingLevel - dx, 0, MAX_BOXING_BONUS)

export function forAttack(attack: UnarmedAttack, st: number, bonus = 0): DamageRoll | null {
  if (!unarmedWounds(attack)) return null
  require(bonus >= 0, () => `a damage bonus cannot be negative: ${bonus}`)
  let base: DiceExpr
  if (attack.base === 'thr') base = thrust(st)
  else if (attack.base === 'sw') base = swing(st)
  else base = error(`unknown damage base '${attack.base}' on ${attack.attack}`)
  return {
    dice: DiceExpr(base.count, base.sides, base.modifier + attack.modifier + bonus),
    type: attack.type ?? error(`${attack.attack} has a base but no damage type`),
  }
}
