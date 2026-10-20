// app/.../SheetLines.kt and ModifierRange.kt: what a sheet row prints, and
// the values a modifier range offers to pick.
import type { PremadeSheet } from '../../rules/Premades'
import * as SelfControl from '../../rules/SelfControl'
import type { SelfControlCheck } from '../../rules/SelfControl'
import * as SheetComparison from '../../rules/SheetComparison'
import type { SheetRow } from '../../rules/SheetComparison'
import type { Secondaries } from '../../rules/SecondaryCharacteristics'
import { canonical, first } from '../../rules/kt'

export interface PlayedLine {
  label: string
  number: string
  basis: string | null
  disagreement: string | null
}

export function playedLine(row: SheetRow, played: string): PlayedLine {
  const disagreement = SheetComparison.matches(row) ? null : ` (sheet: ${row.published})`
  const basis = disagreement !== null && played !== row.computed ? ` · ${row.computed} from the sheet's points` : null
  return { label: row.label, number: played, basis, disagreement }
}

export function vitalLines(sheet: PremadeSheet, rows: SheetRow[], played: Secondaries, compared = true): PlayedLine[] {
  return SheetComparison.secondaryRows(played, sheet.published).map((now) => {
    const line = playedLine(first(rows, (it) => it.label === now.label), now.computed)
    return compared ? line : { ...line, basis: null, disagreement: null }
  })
}

export const standingTargets = (checks: SelfControlCheck[], will: number, iq: number, state: number): Map<string, number> =>
  new Map(checks.map((it) => [it.trait, SelfControl.targetFor(it.spec, will, iq, state)]))

export function endsBetween(from: number, to: number | null): number[] {
  if (to === null) return [from]
  const out: number[] = []
  if (to > from) for (let n = from; n <= to; n++) out.push(n)
  else for (let n = from; n >= to; n--) out.push(n)
  return out
}

export const OPEN_END_STEPS = 3

export const endsBeyond = (from: number): number[] =>
  Array.from({ length: OPEN_END_STEPS + 1 }, (_, it) => (from < 0 ? from - it : from + it))

export const valuesOf = (from: number, to: number | null, openEnded: boolean): number[] =>
  to === null && openEnded ? endsBeyond(from) : endsBetween(from, to)

export const pickInside = <R>(row: R | null, held: [R, number] | null): number | null =>
  held !== null && canonical(held[0]) === canonical(row) ? held[1] : null

export const repeatChoices = () => [1, 2, 3, 4]
