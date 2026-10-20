import type { RollResult, Trait } from './model'
import { associateBy, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { SelfControlRoll, TraitDefinition } from './content/schema.gen'
import * as TraitNames from './TraitNames'
import * as SuccessRoll from './SuccessRoll'
import type { RandomSource } from './RandomSource'

export interface SelfControlCheck {
  trait: string
  spec: SelfControlRoll
  printed: string
}

export interface SelfControlOutcome {
  check: SelfControlCheck
  target: number
  roll: RollResult
}

export const outcomeHeld = (o: SelfControlOutcome) => o.roll.outcome === 'SUCCESS' || o.roll.outcome === 'CRITICAL_SUCCESS'

export const WILL = 'Will'
export const IQ = 'IQ'

const BORROWED_ROLLS = new Map([['Cannot Kill', 'Reluctant Killer']])
export const borrowedRolls = () => BORROWED_ROLLS

export const KILL_REACTION =
  'If you kill a recognizable person, become morose for 3d days — ' +
  'Will rolls required to use violence again.'

export function checksFor(traits: Trait[], catalogue: TraitDefinition[] = ContentLoader.disadvantages()): SelfControlCheck[] {
  const byName = associateBy(catalogue, (it) => it.name.toLowerCase())
  return traits.flatMap((owned): SelfControlCheck[] => {
    if (owned.kind !== 'DISADVANTAGE') return []
    const row = byName.get(TraitNames.catalogueName(owned.name).toLowerCase())
    if (row === undefined) return []
    const shown = TraitNames.displayName(owned.name)
    if (row.selfControl !== null) return [{ trait: shown, spec: row.selfControl, printed: row.notes }]
    const lenderName = BORROWED_ROLLS.get(row.name)
    const lender = lenderName !== undefined ? byName.get(lenderName.toLowerCase()) : undefined
    const spec = lender?.selfControl
    if (lender === undefined || spec == null) return []
    return [{ trait: shown, spec, printed: `${row.notes} ${lender.name}: "${KILL_REACTION}"` }]
  })
}

export function targetFor(spec: SelfControlRoll, will: number, iq: number, modifier = 0): number {
  let base = spec.target
  if (base === null) {
    if (spec.attribute === WILL) base = will
    else if (spec.attribute === IQ) base = iq
    else require(false, () => `self-control roll keyed on '${spec.attribute}'; this book prints only ${WILL} and ${IQ}`)
  }
  return base + spec.offset + modifier
}

export const attempt = (check: SelfControlCheck, will: number, iq: number, source: RandomSource, modifier = 0) =>
  rollAgainst(check, targetFor(check.spec, will, iq, modifier), source)

export const rollAgainst = (check: SelfControlCheck, target: number, source: RandomSource): SelfControlOutcome =>
  ({ check, target, roll: SuccessRoll.against(target, source) })
