import { require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { Outcome, Trait } from './model'
import * as Shock from './Shock'
import * as TraitEffects from './TraitEffects'
import * as TraitNames from './TraitNames'

export type DefenseRefusal = 'SURPRISE' | 'ALL_OUT_ATTACK' | 'UNAWARE_RANGED' | 'PINNED'
export const DEFENSE_REFUSALS: DefenseRefusal[] = ['SURPRISE', 'ALL_OUT_ATTACK', 'UNAWARE_RANGED', 'PINNED']

export type DefenseBoost = 'FEVERISH' | 'ALL_OUT_INCREASED'
export const DEFENSE_BOOSTS: DefenseBoost[] = ['FEVERISH', 'ALL_OUT_INCREASED']
export const BOOSTS: Record<DefenseBoost, { printed: string; extraEffort: boolean }> = {
  FEVERISH: { printed: 'Feverish Defense — Variant Rule', extraEffort: true },
  ALL_OUT_INCREASED: { printed: 'All-Out Defense (Increased)', extraEffort: false },
}

export interface DefenseState {
  refused: DefenseRefusal | null
  printed: string | null
  narrower: string | null
  negated: DefenseRefusal | null
  negatedBy: string | null
}

export const defends = (s: DefenseState) => s.refused === null

export const PIN_HOLD_PARRY = -4
export const NEVER_FREEZES = 'Combat Reflexes' // TraitEffects.COMBAT_REFLEXES
export const FORFEIT_PHRASE = 'Cannot defend next turn.'

export function state(declared: Set<DefenseRefusal>, traits: Trait[]): DefenseState {
  const cancelled = DEFENSE_REFUSALS.filter((it) => declared.has(it) && cancels(it, traits))
  const surviving = DEFENSE_REFUSALS.find((it) => declared.has(it) && !cancelled.includes(it)) ?? null
  return {
    refused: surviving,
    printed: surviving === null ? null : operativeFor(surviving),
    narrower: surviving === null ? null : narrowerFor(surviving),
    negated: cancelled[0] ?? null,
    negatedBy: cancelled.length > 0 ? NEVER_FREEZES : null,
  }
}

export const forfeitingActions = (): string[] => [
  ...ContentLoader.maneuvers().filter((it) => it.description.includes(FORFEIT_PHRASE)).map((it) => it.maneuver),
  ...ContentLoader.extraEffort().filter((it) => it.effect.includes(FORFEIT_PHRASE)).map((it) => it.option),
]

const operativeFor = (cause: DefenseRefusal) => (cause === 'ALL_OUT_ATTACK' ? FORFEIT_PHRASE : printedFor(cause))
const narrowerFor = (cause: DefenseRefusal) => (cause === 'ALL_OUT_ATTACK' ? printedFor(cause) : null)

export const BOOST_BONUS = 2

export function fpCostOf(boost: DefenseBoost): number {
  const b = BOOSTS[boost]
  if (b.extraEffort) {
    const row = ContentLoader.extraEffort().find((it) => it.option === b.printed)
    require(row !== undefined, () => `extra_effort.json has no option named '${b.printed}'`)
    return row!.fp
  }
  const row = ContentLoader.maneuvers().find((it) => it.maneuver === b.printed)
  require(row !== undefined, () => `maneuvers.json has no row named '${b.printed}'`)
  const fp = row!.fp
  require(fp !== null, () => `'${b.printed}' no longer prints an FP cost`)
  return fp!
}

export function printedBoost(boost: DefenseBoost): string {
  const b = BOOSTS[boost]
  return b.extraEffort
    ? ContentLoader.extraEffort().find((it) => it.option === b.printed)!.effect
    : ContentLoader.maneuvers().find((it) => it.maneuver === b.printed)!.description
}

export const UNPAYABLE = new Map([
  ['All-Out Defense (Double)',
    'this dialog allocates no defence — it reports what every row WOULD ' +
    'be and never asks the GM to pick one, so the one-defence limit the ' +
    'row buys out of is not imposed here; a cap invented in the same ' +
    'commit that charges for lifting it would be a mechanic authored to ' +
    'justify a price, not a rule applied'],
])

export const ACROBATIC_SKILL = 'Acrobatics'
export const ACROBATIC_SWING = 2
const ACROBATIC_ROW = '[Acrobatic Dodge]'

function printedModifier(tag: string): string {
  const row = ContentLoader.defenseModifiers().find((it) => it.modifier.includes(tag))
  require(row !== undefined, () => `defense_modifiers.json no longer prints ${tag}`)
  return row!.modifier
}

export const printedAcrobaticDodge = () => printedModifier(ACROBATIC_ROW)

export function acrobaticDodge(outcome: Outcome | null): number {
  if (outcome === null) return 0
  return outcome === 'SUCCESS' || outcome === 'CRITICAL_SUCCESS' ? ACROBATIC_SWING : -ACROBATIC_SWING
}

const DIVE_ROW = '[Dive!]'
export const DIVE_BONUS = 3
export const printedDive = () => printedModifier(DIVE_ROW)
export const dive = (taken: boolean, isRanged: boolean) => (taken && isRanged ? DIVE_BONUS : 0)

const RETREAT_ROW = '[Retreat]'
export type Retreat = 'DODGE' | 'PARRY' | 'BLOCK'
export const RETREAT_BONUS: Record<Retreat, number> = { DODGE: 3, PARRY: 1, BLOCK: 1 }
export const printedRetreat = () => printedModifier(RETREAT_ROW)
export const retreat = (taken: boolean, isMelee: boolean, row: Retreat) => (taken && isMelee ? RETREAT_BONUS[row] : 0)

const APPLIED_ROWS = [ACROBATIC_ROW, DIVE_ROW, RETREAT_ROW]

export const unappliedModifiers = (): string[] =>
  ContentLoader.defenseModifiers().map((it) => it.modifier).filter((bullet) => !APPLIED_ROWS.some((it) => bullet.includes(it)))

export const STUNNED_PENALTY = -4
const STUN_ROW = 'Do Nothing'
export const printedStun = () => ContentLoader.maneuvers().find((it) => it.maneuver === STUN_ROW)!.description

export const everyDefence = (traits: Trait[], stunned: boolean, shocked: boolean, poolPenalty: number, states = 0) =>
  TraitEffects.defenseBonus(traits) +
  (stunned ? STUNNED_PENALTY : 0) +
  Shock.penaltyFor('ACTIVE_DEFENSE', shocked, traits) +
  poolPenalty + states

export function printedFor(cause: string): string {
  const row = ContentLoader.defenseRefusals().find((it) => it.cause === cause)
  require(row !== undefined, () => `the book prints no defence refusal called '${cause}'`)
  return row!.printed
}

const cancels = (cause: DefenseRefusal, traits: Trait[]) =>
  cause === 'SURPRISE' && TraitNames.levelOf(traits, NEVER_FREEZES) !== null
