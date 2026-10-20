import type { Attributes, PurchasedSkill, SecondaryDelta, Trait, TraitKind } from './model'
import { sumOf } from './kt'
import * as AttributeCosts from './AttributeCosts'
import * as SecondaryCharacteristics from './SecondaryCharacteristics'

export interface BudgetReport {
  budget: number
  spent: number
  violations: string[]
}

export const surplus = (r: BudgetReport) => r.budget - r.spent

export const STARTING_TOTAL = 70
export const MAX_DISADVANTAGES = 40
export const MAX_QUIRKS = 5

export function report(
  attributes: Attributes,
  secondaryDelta: SecondaryDelta,
  traits: Trait[],
  skills: PurchasedSkill[],
): BudgetReport {
  const sum = (kind: TraitKind) => sumOf(traits.filter((it) => it.kind === kind), (it) => it.cost)
  const rawDisadvantages = sum('DISADVANTAGE')
  const rawQuirks = sum('QUIRK')
  const budget = STARTING_TOTAL + Math.min(rawDisadvantages, MAX_DISADVANTAGES) + Math.min(rawQuirks, MAX_QUIRKS)
  const spent = AttributeCosts.totalCost(attributes) +
    SecondaryCharacteristics.costOf(secondaryDelta) +
    sum('ADVANTAGE') +
    sumOf(skills, (it) => it.points - it.studied)
  const violations: string[] = []
  if (rawDisadvantages > MAX_DISADVANTAGES) violations.push(`Disadvantages total ${rawDisadvantages}, capped at ${MAX_DISADVANTAGES}`)
  if (rawQuirks > MAX_QUIRKS) violations.push(`Quirks total ${rawQuirks}, capped at ${MAX_QUIRKS}`)
  return { budget, spent, violations }
}
