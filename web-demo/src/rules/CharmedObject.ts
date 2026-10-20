import type { SkillKey, Trait } from './model'
import { eqIc, isBlank, startsWithIc } from './kt'
import type { TraitClause } from './TraitEffects'
import type { TraitNote } from './TraitReport'

export const ROW = 'Charmed Object'
export const CLAUSE = '+1 to one specific skill when used'
export const DESTROYED = 'Lost permanently if the item is destroyed'
export const REACH =
  'on a parry, an Acrobatic Dodge, a ritual, a divination or a boon ' +
  'built on the chosen skill. A roll of the skill from the Skills ' +
  'tab, and an attack with it, offer the +1 as a chip'
export const UNCHOSEN =
  'no skill is chosen for it, so no roll offers its +1: choose one in the builder'
const NO_ITEMS = 'this app tracks no item to destroy; give the row back in the builder'

const HEAD = `${ROW} (`

const label = (skill: string, specialization: string | null) =>
  specialization === null ? skill : `${skill} (${specialization})`

export const heldAs = (skill: string, specialization: string | null) => `${HEAD}${label(skill, specialization)})`

export function skillOf(printed: string): string | null {
  if (!startsWithIc(printed, HEAD) || !printed.endsWith(')')) return null
  const s = printed.substring(HEAD.length, printed.length - 1)
  return !isBlank(s) ? s : null
}

export const rowOf = (printed: string): string | null => (skillOf(printed) !== null ? ROW : null)

export const TEMPLATE: TraitClause = { trait: ROW, shown: ROW, printed: CLAUSE, value: 1, conditional: true }

export function clauses(traits: Trait[], skill: SkillKey): TraitClause[] {
  const rolled = label(skill.name, skill.specialization)
  return traits.filter((it) => eqIc(skillOf(it.name), rolled)).map((it) => ({ ...TEMPLATE, shown: it.name }))
}

export function notes(shown: string, printed: string, cell: string): TraitNote[] {
  if (skillOf(printed) === null) return [{ trait: shown, printed: cell, reason: UNCHOSEN }]
  return [
    { trait: shown, printed: CLAUSE, reason: REACH },
    { trait: shown, printed: DESTROYED, reason: NO_ITEMS },
  ]
}
