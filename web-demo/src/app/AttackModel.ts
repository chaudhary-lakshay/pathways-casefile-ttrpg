// The Attack dialog's pure helpers: AttackDialog.kt, AttackOptions.kt,
// AttackParts.kt and RangedOptions.kt, each named as in Kotlin.
import type { CataloguedWeapon, HitLocation, RangePenalty, WeaponMode } from '../rules/content/schema.gen'
import type { AttackPlan } from '../rules/Attacks'
import * as CharmedObject from '../rules/CharmedObject'
import type { Modifier, SkillKey, Trait } from '../rules/model'
import * as Ranged from '../rules/Ranged'
import type { DarknessCause } from '../rules/Ranged'
import * as SkillRoll from '../rules/SkillRoll'
import type { SkillTarget } from '../rules/SkillRoll'
import * as SuccessRoll from '../rules/SuccessRoll'
import * as TraitClauses from '../rules/TraitClauses'
import * as TraitEffects from '../rules/TraitEffects'
import type { TraitClause } from '../rules/TraitEffects'
import * as Weapons from '../rules/Weapons'
import * as Wounds from '../rules/Wounds'

const signed = (n: number) => (n >= 0 ? `+${n}` : `${n}`)

/** A weapon in hand, and the damage mode it strikes with. */
export interface Armed {
  weapon: CataloguedWeapon
  mode: WeaponMode
}
export const Armed = (weapon: CataloguedWeapon, mode: WeaponMode = weapon.damage[0]): Armed => ({ weapon, mode })
export const isFired = (a: Armed | null) => a !== null && Weapons.isRanged(a.weapon)
/** Only a fired weapon lends its Acc to an Aim. */
export const aimAcc = (a: Armed | null): number | null => (a !== null && isFired(a) ? a.weapon.acc : null)

export const PINNED_REFUSAL = 'Pinned: cannot attack. An Escape is a contest the GM runs.'

export const AIM_ROWS = () => new Set(Ranged.conditions().filter((it) => it.weaponAcc).map((it) => it.condition))
const darknessRow = () => Ranged.conditions().find((it) => it.worstCase !== null)!.condition

/** RangedOptions.kt `toggled`: a second speed or aim replaces the first; a second tap takes it off. */
export function toggled(circumstances: Set<string>, name: string): Set<string> {
  const next = new Set(circumstances)
  if (next.has(name)) {
    next.delete(name)
    return next
  }
  for (const other of Ranged.alternativesTo(name)) next.delete(other)
  next.add(name)
  return next
}

export function rangedModifiers(
  plan: AttackPlan, band: RangePenalty | null, circumstances: Set<string>, weaponAcc: number | null, aimCancelled: boolean,
  darknessCause: DarknessCause, darknessReduction: number, darknessSeverity: number | null,
): Modifier[] {
  if (!plan.isRanged) return []
  return Ranged.modifiers({
    band,
    conditions: Ranged.conditions().filter((it) => circumstances.has(it.condition)),
    gmValue: darknessSeverity !== null ? new Map([[darknessRow(), darknessSeverity]]) : new Map(),
    weaponAcc,
    aimCancelled,
    darknessCause,
    darknessReduction,
  })
}

/** AttackOptions.kt `meleeModifiers`: the one row priced in reach, never beside its ranged twin. */
export const meleeModifiers = (plan: AttackPlan, circumstances: Set<string>): Modifier[] =>
  plan.isRanged ? [] : Ranged.meleeModifiers(Ranged.meleeConditions().filter((it) => circumstances.has(it.condition)))

export const traitTerms = (traits: Trait[], key: SkillKey, killerTarget: string | null, declared: Set<string>): Modifier[] =>
  [...TraitEffects.modifiers(traits, key, declared), ...TraitClauses.attackModifiers(traits, killerTarget)]

export const charmFor = (traits: Trait[], key: SkillKey): TraitClause | null => CharmedObject.clauses(traits, key)[0] ?? null

export interface AttackStanding {
  target: SkillTarget
  fromTrackers: number
}

/** AttackDialog.kt `attackStanding`: the tracker line is read off the ledger by id. */
export function attackStanding(label: string, level: number, key: SkillKey, trackers: Modifier[], picked: Modifier[]): AttackStanding {
  const target = SkillRoll.targetFor(label, level, key, [...trackers, ...picked])
  const ids = new Set(trackers.map((it) => it.id))
  return { target, fromTrackers: target.applied.filter((a) => a.ids.some((id) => ids.has(id))).reduce((s, a) => s + a.delta, 0) }
}

/** "The penalty applies to a single roll": a Double or Rapid Strike's first. */
export const attackTargets = (first: number, attacks: number, spiritPending: number): number[] =>
  Array.from({ length: attacks }, (_, i) => (i === 0 ? first : first + spiritPending))

export function attackCaption(target: number, attacks: number, spiritPending: number): string {
  const first = SuccessRoll.printed(target)
  const later = SuccessRoll.printed(target + spiritPending)
  if (attacks <= 1) return first
  return first === later ? `${first}, each` : `first: ${first}; after: ${later}`
}

export function woundingNote(location: HitLocation | null, type: string): string {
  const times = Wounds.multiplierFor(location, type)
  if (times === 1) return ''
  return `, ${Wounds.render(times)}` + (location === null ? '' : ' there')
}

export const termLine = (terms: number[]) => (terms.every((it) => it === 0) ? 'no modifier' : signed(terms.reduce((a, b) => a + b, 0)))
