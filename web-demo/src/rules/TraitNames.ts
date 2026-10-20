// The ONE resolution of a sheet's spelling of a trait to its catalogue row.
import type { Trait } from './model'
import {
  associateBy, eqIc, lazy, substringAfter, substringAfterLast, substringBefore, substringBeforeLast, toIntOrNull,
  trimEndChars, WS,
} from './kt'
import * as ContentLoader from './content/ContentLoader'
import { blessingAsTrait } from './content/members'
import type { TraitDefinition } from './content/schema.gen'
import * as CharmedObject from './CharmedObject'

export function levelOf(traits: Trait[], name: string): number | null {
  const owned = traits.find((it) => eqIc(catalogueName(it.name), name))
  if (owned === undefined) return null
  return toIntOrNull(substringAfterLast(owned.name, ' ')) ?? 1
}

export function catalogueName(printed: string): string {
  const row = rowNames().get(printed.toLowerCase())
  if (row !== undefined) return row
  const ruled = RULED_SPELLINGS.get(printed)
  if (ruled !== undefined) return ruled
  if (toIntOrNull(substringAfterLast(printed, ' ')) !== null) return substringBeforeLast(printed, ' ')
  return placeholderRow(printed) ?? variantRow(printed) ?? gluedHead(printed) ??
    quotedInstanceRow(printed) ?? CharmedObject.rowOf(printed) ?? printed
}

const RULED_SPELLINGS = new Map([
  ['Pacifism (Cannot Kill)', 'Cannot Kill'],
  ['Pacifism (Cannot Harm Innocents)', 'Cannot Harm Innocents'],
  ['Wierdness Magnet', 'Weirdness Magnet'],
  ['Blacklisted: Specific Industry org or church', 'Blacklisted'],
])

function quotedInstanceRow(printed: string): string | null {
  const named = substringBefore(printed, SHEET_SEPARATOR)
  if (!named.endsWith(')')) return null
  const open = named.lastIndexOf(' (')
  if (open <= 0 || named.substring(open + 2, named.length - 1).trim() === '') return null
  const head = named.substring(0, open)
  const row = QUOTED_INSTANCE_ROWS.find((it) => eqIc(it, head))
  if (row === undefined) return null
  if (printed === named) return row
  const tail = substringAfter(printed, SHEET_SEPARATOR)
  const notes = catalogue().get(row.toLowerCase())?.notes
  if (notes === undefined) return null
  return quoted(tail) === quoted(notes) ? row : null
}

const QUOTED_INSTANCE_ROWS = ['Sense of Duty', 'Secret']

export function pricedVariants(notes: string): [string, number][] {
  const found: [string, number][] = [...notes.matchAll(PRICED_VARIANT)].map((m) => [m[1].trim(), Number(m[2])])
  return found.length >= 2 ? found : []
}

const PRICED_VARIANT = new RegExp(`([A-Z][A-Za-z]*(?: [A-Za-z]+)*?)${WS}*\\(?[-−](\\d+)\\)?`, 'g')

function variantRow(printed: string): string | null {
  const open = printed.lastIndexOf(' (')
  if (open <= 0 || !printed.endsWith(')')) return null
  const qualifier = printed.substring(open + 2, printed.length - 1)
  if (qualifier.trim() === '') return null
  const row = catalogue().get(printed.substring(0, open).toLowerCase())
  if (row === undefined) return null
  const named = pricedVariants(row.notes).filter((it) => it[0].toLowerCase().startsWith(qualifier.toLowerCase()))
  return named.length === 1 ? row.name : null
}

export const PLACEHOLDER = ' (specify)'

function placeholderRow(printed: string): string | null {
  if (!printed.endsWith(')')) return null
  const opens = [...new Set([printed.lastIndexOf(' ('), printed.indexOf(' (')])]
  for (const open of opens) {
    if (open <= 0) continue
    const inside = printed.substring(open + 2, printed.length - 1)
    if (inside.trim() === '' || ` (${inside})` === PLACEHOLDER) continue
    const head = printed.substring(0, open)
    const row = catalogue().get(`${head.toLowerCase()}${PLACEHOLDER}`)
    if (row !== undefined) return row.name
  }
  return null
}

function gluedHead(printed: string): string | null {
  const rows = catalogue()
  if (rows.has(printed.toLowerCase())) return null
  const candidates = [...rows.values()]
    .filter((it) => printed.length > it.name.length && printed.startsWith(it.name))
    .sort((a, b) => b.name.length - a.name.length)
  for (const row of candidates) {
    const rest = printed.substring(row.name.length)
    let tail: string
    if (rest.startsWith(SHEET_SEPARATOR)) tail = rest.slice(SHEET_SEPARATOR.length)
    else if (rest.startsWith(' ')) tail = rest.slice(1)
    else continue
    if (quoted(tail) === quoted(row.notes)) return row.name
  }
  return null
}

function quoted(text: string): string {
  return trimEndChars(squashed(text).replaceAll('−', '-'), '.')
}

export function displayName(printed: string): string {
  return gluedHead(printed) ??
    (quotedInstanceRow(printed) !== null ? substringBefore(printed, SHEET_SEPARATOR) : null) ??
    printed
}

const SHEET_SEPARATOR = ' - '

const rowNames = lazy(() => new Map([...catalogue()].map(([k, v]) => [k, v.name])))

/** Advantages, disadvantages and the seven blessings, keyed by lower-cased name. */
export const catalogue = lazy((): Map<string, TraitDefinition> => {
  const cost = ContentLoader.blessingCost()
  return associateBy(
    [...ContentLoader.advantages(), ...ContentLoader.disadvantages(), ...ContentLoader.blessings().map((it) => blessingAsTrait(it, cost))],
    (it) => it.name.toLowerCase(),
  )
})

function squashed(text: string): string {
  return text.split(new RegExp(`${WS}+`)).filter((it) => it !== '').join(' ').trim()
}
