import { error, floorDiv, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { UnarmedAttack } from './content/schema.gen'
import { unarmedWounds } from './content/members'
import { Flat, Modifier } from './model'

export type AttackManeuver = 'REGULAR' | 'DETERMINED' | 'STRONG' | 'DOUBLE' | 'CHARGE'
export const ATTACK_MANEUVERS: AttackManeuver[] = ['REGULAR', 'DETERMINED', 'STRONG', 'DOUBLE', 'CHARGE']
export const MANEUVERS: Record<AttackManeuver, { printed: string; melee: number; ranged: number; damageBonus: number }> = {
  REGULAR: { printed: 'Regular Attack', melee: 0, ranged: 0, damageBonus: 0 },
  DETERMINED: { printed: 'All-Out Attack (Determined)', melee: 4, ranged: 1, damageBonus: 0 },
  STRONG: { printed: 'All-Out Attack (Strong)', melee: 0, ranged: 0, damageBonus: 2 },
  DOUBLE: { printed: 'All-Out Attack (Double)', melee: 0, ranged: 0, damageBonus: 0 },
  CHARGE: { printed: 'Charge Attack', melee: -4, ranged: -4, damageBonus: 0 },
}
export const toHit = (m: AttackManeuver, isRanged: boolean) => (isRanged ? MANEUVERS[m].ranged : MANEUVERS[m].melee)

export interface AttackPlan {
  maneuver: AttackManeuver
  isRanged: boolean
  rapidStrikes: number
  deceptive: number
  heroicCharge: boolean
  strongBlow: boolean
  giantStep: boolean
  pinOnGrappledProne: boolean
}

export const AttackPlan = (p: Partial<AttackPlan> = {}): AttackPlan => ({
  maneuver: 'REGULAR', isRanged: false, rapidStrikes: 0, deceptive: 0,
  heroicCharge: false, strongBlow: false, giantStep: false, pinOnGrappledProne: false, ...p,
})

export const RAPID_STRIKE_PENALTY = -6
export const DECEPTIVE_STEP_PENALTY = -2
export const HEROIC_CHARGE = 'Heroic Charge'
export const STRONG_BLOW = 'Strong Blow'
export const GIANT_STEP = 'Giant Step'
export const STRONG_BLOW_DAMAGE = 2
export const PIN = 'Pin'
export const PIN_PRONE_BONUS = 4

export const atRange = (plan: AttackPlan, isRanged: boolean): AttackPlan =>
  !isRanged ? { ...plan, isRanged: false } : { ...plan, isRanged: true, rapidStrikes: 0, deceptive: 0, strongBlow: false }

export const atLevel = (plan: AttackPlan, level: number): AttackPlan =>
  plan.deceptive > maxDeceptive(level) ? { ...plan, deceptive: 0 } : plan

export function withAttack(plan: AttackPlan, unarmed: UnarmedAttack | null): AttackPlan {
  const p = unarmed === null || unarmedWounds(unarmed) ? plan : { ...plan, strongBlow: false }
  return unarmed?.attack === PIN ? p : { ...p, pinOnGrappledProne: false }
}

export const withManeuver = (plan: AttackPlan, maneuver: AttackManeuver): AttackPlan =>
  AttackPlan({ maneuver, isRanged: plan.isRanged, giantStep: plan.giantStep })

export const offersStrongBlow = (plan: AttackPlan, dealsDamage: boolean, isFired: boolean) =>
  plan.maneuver === 'REGULAR' && !plan.isRanged && dealsDamage && !isFired

export const maxDeceptive = (skillLevel: number) => floorDiv(floorDiv(Math.max(skillLevel, 0), 2), -DECEPTIVE_STEP_PENALTY)

export function damageBonus(plan: AttackPlan, fromSkill = 0): number {
  require(fromSkill >= 0, () => `a skill damage bonus cannot be negative: ${fromSkill}`)
  const effort = plan.strongBlow ? STRONG_BLOW_DAMAGE : 0
  return MANEUVERS[plan.maneuver].damageBonus + effort + fromSkill
}

export function attackCount(plan: AttackPlan): number {
  require(plan.rapidStrikes >= 0, () => `rapidStrikes must not be negative: ${plan.rapidStrikes}`)
  const fromManeuver = plan.maneuver === 'DOUBLE' ? 2 : 1
  return fromManeuver * (plan.rapidStrikes + 1)
}

export function fpCost(plan: AttackPlan): number {
  const printed = MANEUVERS[plan.maneuver].printed
  const maneuver = ContentLoader.maneuvers().find((it) => it.maneuver === printed) ?? error(`maneuvers.json has no row named '${printed}'`)
  const charge = plan.heroicCharge ? effortFp(HEROIC_CHARGE) : 0
  const blow = plan.strongBlow ? effortFp(STRONG_BLOW) : 0
  const step = plan.giantStep ? effortFp(GIANT_STEP) : 0
  return (maneuver.fp ?? 0) + charge + blow + step
}

export const effortFp = (option: string): number =>
  ContentLoader.extraEffort().find((it) => it.option === option)?.fp ?? error(`extra_effort.json has no option named '${option}'`)

export function modifiers(plan: AttackPlan, skillLevel: number, unarmed: UnarmedAttack | null = null): Modifier[] {
  require(plan.rapidStrikes >= 0, () => `rapidStrikes must not be negative: ${plan.rapidStrikes}`)
  require(plan.deceptive >= 0, () => `deceptive steps must not be negative: ${plan.deceptive}`)
  require(plan.deceptive <= maxDeceptive(skillLevel), () =>
    `deceptive ${plan.deceptive} exceeds half of skill ${skillLevel} (max ${maxDeceptive(skillLevel)} steps)`)
  require(!plan.isRanged || plan.rapidStrikes === 0, () => 'Rapid Strike is a melee option')
  require(!plan.isRanged || plan.deceptive === 0, () => 'Deceptive Attack is a melee option')
  require(!plan.heroicCharge || plan.maneuver === 'CHARGE', () => `${HEROIC_CHARGE} only applies to ${MANEUVERS.CHARGE.printed}`)
  require(!plan.strongBlow || plan.maneuver === 'REGULAR', () => `${STRONG_BLOW} only applies to ${MANEUVERS.REGULAR.printed}`)
  require(!plan.strongBlow || !plan.isRanged, () => `${STRONG_BLOW} is a melee option`)
  require(!plan.strongBlow || unarmed === null || unarmedWounds(unarmed), () =>
    `${STRONG_BLOW} needs an attack that rolls damage, and ${unarmed?.attack} does not`)
  const out: (Modifier | null)[] = []
  if (unarmed !== null) {
    out.push(situational('unarmed', unarmed.rollModifier, unarmed.attack))
    if (unarmed.attack === PIN && plan.pinOnGrappledProne) {
      out.push(situational('pin-prone', PIN_PRONE_BONUS, 'target grappled & prone'))
    }
  }
  const fromManeuver = plan.heroicCharge ? 0 : toHit(plan.maneuver, plan.isRanged)
  out.push(situational('maneuver', fromManeuver, MANEUVERS[plan.maneuver].printed))
  out.push(situational('rapid', RAPID_STRIKE_PENALTY * plan.rapidStrikes, `Rapid Strike x${plan.rapidStrikes}`))
  out.push(situational('deceptive', DECEPTIVE_STEP_PENALTY * plan.deceptive, `Deceptive Attack -${plan.deceptive} to their defence`))
  return out.filter((m): m is Modifier => m !== null)
}

function situational(id: string, value: number, description: string): Modifier | null {
  if (value === 0) return null
  return Modifier({
    id: `attack:${id}`,
    source: { type: 'Situational', description },
    target: { type: 'AllRolls' },
    effect: Flat(value),
    layer: 'SITUATIONAL',
  })
}
