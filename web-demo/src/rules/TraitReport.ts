import type { Trait, TraitKind } from './model'
import { eqIc, lazy } from './kt'
import { catalogue, catalogueName, displayName } from './TraitNames'
import { CLEANSED_SPIRIT, ACUTE_SMELL, GREATER_ACUTE_VISION } from './TraitEffects'
import * as TraitEffects from './TraitEffects'
import { GRANTED_ONLY_SENSES } from './TraitClauses'
import { PARTLY_APPLIED, UNAPPLIED } from './TraitRefusals'
import * as SelfControl from './SelfControl'
import * as CharmedObject from './CharmedObject'
import * as Appearance from './Appearance'
import * as Wealth from './Wealth'

export interface TraitNote {
  trait: string
  printed: string
  reason: string
}

export const corruptionNotes = (traits: Trait[]) =>
  reported(traits.filter((it) => eqIc(catalogueName(it.name), CLEANSED_SPIRIT)))

export function reported(traits: Trait[]): TraitNote[] {
  const rows = catalogue()
  const known = TraitEffects.known()
  return traits.flatMap((owned): TraitNote[] => {
    const bare = catalogueName(owned.name)
    const row = rows.get(bare.toLowerCase())
    if (row === undefined) return []
    const printed = row.notes
    const shown = displayName(owned.name)
    if (!known.has(bare)) {
      const rolled = SelfControl.checksFor([owned]).length > 0
      return [{ trait: shown, printed, reason: rolled ? ROLLED_ONLY : UNREAD }]
    }
    if (bare === CharmedObject.ROW) return CharmedObject.notes(shown, owned.name, printed)
    const unapplied = UNAPPLIED().get(bare)
    const whole = unapplied !== undefined ? [{ trait: shown, printed, reason: unapplied }] : []
    const clauses = (PARTLY_APPLIED().get(bare) ?? []).map(([clause, reason]) => ({ trait: shown, printed: clause, reason }))
    return [...whole, ...clauses]
  })
}

export const UNREAD =
  'its price aside, this app applies nothing this row prints, so its ' +
  "text is the table's to apply"
export const ROLLED_ONLY =
  'its price and its self-control roll aside, this app applies nothing ' +
  "this row prints, so the rest of its text is the table's to apply"

export type Applied = 'APPLIED' | 'PARTLY' | 'ROLL_ONLY' | 'GM' | 'NOT_IN_BOOK'

export function applied(row: HeldTrait): Applied {
  if (row.cell === null) return 'NOT_IN_BOOK'
  if (row.notes.length === 0) return 'APPLIED'
  if (row.notes.some((it) => it.reason === ROLLED_ONLY)) return 'ROLL_ONLY'
  if (row.notes.some((it) => it.printed === row.cell)) return 'GM'
  return 'PARTLY'
}

export interface HeldTrait {
  trait: Trait
  shown: string
  cell: string | null
  notes: TraitNote[]
}

export function held(traits: Trait[], kind: TraitKind): HeldTrait[] {
  const rows = catalogue()
  return traits.filter((it) => it.kind === kind).map((owned) => {
    const shown = displayName(owned.name)
    const row = rows.get(catalogueName(owned.name).toLowerCase())
    const [cell, notes] = row !== undefined ? [row.notes, reported([owned])] as const : unrowed(owned, shown)
    return { trait: owned, shown, cell, notes }
  })
}

function unrowed(owned: Trait, shown: string): readonly [string | null, TraitNote[]] {
  if (owned.kind === 'QUIRK') return [QUIRK_CELL, [{ trait: shown, printed: QUIRK_CELL, reason: UNREAD }]]
  const face = Appearance.played([owned])
  if (face !== null) {
    const cell = Appearance.cellFor(face)
    return [cell, [{ trait: shown, printed: cell, reason: UNREAD }]]
  }
  const level = Wealth.levelOf([owned])
  if (level !== null) {
    const book = Wealth.traitFor(level)
    const row = book !== null ? catalogue().get(book.name.toLowerCase()) : undefined
    if (row !== undefined && book !== null) return [row.notes, reported([book]).map((it) => ({ ...it, trait: shown }))]
    const cell = Wealth.cellFor(level)
    return [cell, [{ trait: shown, printed: cell, reason: UNREAD }]]
  }
  const name = catalogueName(owned.name)
  const sense = GRANTED_ONLY_SENSES().find((it) => eqIc(it.trait, name))
  if (sense === undefined) return [null, []]
  const rest = SENSE_ROW_REST().get(sense.trait)
  if (rest === undefined) return [sense.printed, []]
  return [`${sense.printed}; ${rest}`, [{ trait: shown, printed: rest, reason: SENSE_REST }]]
}

const QUIRK_CELL = 'minor personality traits'
const SENSE_ROW_REST = lazy(() => new Map([
  [ACUTE_SMELL, 'detect subtle scents and emotional cues'],
  [GREATER_ACUTE_VISION, "allows the Pleasure to see their own Spider's Threads"],
]))
const SENSE_REST =
  'its bonus aside, declared on Perception rolls, this app applies nothing this row prints, ' +
  "so the rest is the table's to apply"
