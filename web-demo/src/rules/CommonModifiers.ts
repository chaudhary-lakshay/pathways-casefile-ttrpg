import { error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { CommonModifier } from './content/schema.gen'
import { Flat, Modifier } from './model'

export const all = (): CommonModifier[] => ContentLoader.commonModifiers()
export const offered = () => all().filter((it) => !it.isDefault)

export const named = (situation: string): CommonModifier =>
  all().find((it) => it.situation === situation) ?? error(`the book prints no common modifier called '${situation}'`)

export function valueFor(row: CommonModifier, chosen: number | null = null): number {
  require(!row.isDefault, () =>
    `'${row.situation}' is not a modifier -- it prints '${row.printed}', which ` +
    'replaces the roll with an attribute default. Ask SkillDefaults.')
  const printed = row.modifier ?? error(`'${row.situation}' prints no modifier`)
  if (chosen === null) return printed
  const limit = row.otherEnd
  if (limit !== null) {
    require(chosen >= Math.min(printed, limit) && chosen <= Math.max(printed, limit), () =>
      `'${row.situation}' prints ${row.printed}; ${chosen} is outside it`)
  } else if (row.isOpenEnded) {
    require(printed < 0 ? chosen <= printed : chosen >= printed, () =>
      `'${row.situation}' prints ${row.printed}; ${chosen} is on the wrong side of it`)
  } else {
    require(chosen === printed, () => `'${row.situation}' prints exactly ${row.printed}, not ${chosen}`)
  }
  return chosen
}

export function modifiers(chosen: CommonModifier[] = [], gmValue: Map<string, number> = new Map()): Modifier[] {
  const names = new Set(chosen.map((it) => it.situation))
  const unmatched = [...gmValue.keys()].filter((k) => !names.has(k))
  require(unmatched.length === 0, () => `a value was given for ${unmatched.join(', ')}, which was not chosen`)
  return chosen
    .map((row) => situational(row, valueFor(row, gmValue.get(row.situation) ?? null)))
    .filter((m): m is Modifier => m !== null)
}

const GM_RULING = "GM's ruling"

export const gmRuling = (value: number): Modifier | null =>
  value === 0
    ? null
    : Modifier({
        id: 'gm:ruling',
        source: { type: 'Situational', description: GM_RULING },
        target: { type: 'AllRolls' },
        effect: Flat(value),
        layer: 'SITUATIONAL',
      })

function situational(row: CommonModifier, value: number): Modifier | null {
  if (value === 0) return null
  return Modifier({
    id: `common:${row.situation.toLowerCase()}`,
    source: { type: 'Situational', description: row.situation },
    target: { type: 'AllRolls' },
    effect: Flat(value),
    layer: 'SITUATIONAL',
  })
}
