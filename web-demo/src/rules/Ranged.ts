import { error, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { RangePenalty, RangedModifier } from './content/schema.gen'
import { Flat, Modifier } from './model'

export type DarknessCause = 'HEAVY_FOG' | 'DARKNESS' | 'DIM_LIGHT'

const NIGHT_EYES = 'Night Eyes' // TraitEffects.NIGHT_EYES

export const bands = (): RangePenalty[] => ContentLoader.rangePenalties()
export const conditions = (): RangedModifier[] => ContentLoader.rangedModifiers()
export const meleeConditions = () => conditions().filter((it) => it.melee !== null)

export function meleeModifiers(rows: RangedModifier[]): Modifier[] {
  return rows
    .map((row) => {
      require(row.melee !== null, () => `'${row.condition}' prints ${row.printed}, which is a ranged number only`)
      return situational(row.condition.toLowerCase(), row.melee!, row.condition)
    })
    .filter((m): m is Modifier => m !== null)
}

const AIM_ROW = 'Aim (Ranged)'
export const printedAim = () => ContentLoader.maneuvers().find((it) => it.maneuver === AIM_ROW)!.description

export const offeredConditions = (cancelled: boolean) => (cancelled ? conditions().filter((it) => !it.weaponAcc) : conditions())

export function alternativesTo(condition: string): Set<string> {
  const row = conditions().find((it) => it.condition === condition)
  const one = row === undefined ? null : oneOf(row)
  if (one === null) return new Set()
  return new Set(conditions().filter((it) => it.condition !== condition && oneOf(it) === one).map((it) => it.condition))
}

function oneOf(row: RangedModifier): string | null {
  if (row.weaponAcc) return 'a shot is aimed for one length of time'
  if (row.targetSpeed) return 'a target moves at one speed'
  return null
}

export function bandFor(meters: number): RangePenalty {
  require(meters >= 0, () => `a distance cannot be negative: ${meters}`)
  return bands().find((it) => meters >= it.from && (it.to === null || meters <= it.to)) ?? error(`no range band covers ${meters} metres`)
}

export const penaltyFor = (meters: number) => bandFor(meters).penalty

export interface RangedInputs {
  band?: RangePenalty | null
  conditions?: RangedModifier[]
  gmValue?: Map<string, number>
  weaponAcc?: number | null
  aimCancelled?: boolean
  darknessCause?: DarknessCause | null
  darknessReduction?: number
}

export function modifiers({
  band = null, conditions: rows = [], gmValue = new Map(), weaponAcc = null,
  aimCancelled = false, darknessCause = null, darknessReduction = 0,
}: RangedInputs = {}): Modifier[] {
  require(!aimCancelled || !rows.some((it) => it.weaponAcc), () => 'the aim was cancelled: ' + printedAim())
  const groups = new Map<string | null, RangedModifier[]>()
  for (const r of rows) groups.set(oneOf(r), [...(groups.get(oneOf(r)) ?? []), r])
  for (const [one, rs] of groups) {
    require(one === null || rs.length <= 1, () => `${one}; ` + rs.map((it) => it.condition).join(', ') + ' cannot both apply')
  }
  const out: (Modifier | null)[] = []
  if (band !== null) out.push(situational('range', band.penalty, band.printed))
  for (const row of rows) {
    let value: number
    if (row.weaponAcc) {
      require(weaponAcc !== null, () => `'${row.condition}' is worth the weapon's Acc, and no weapon was chosen`)
      value = weaponAcc! + row.accBonus
    } else {
      value = gmValue.get(row.condition) ?? row.modifier ?? error(`'${row.condition}' prints no modifier and none was given`)
    }
    if (row.worstCase !== null) {
      const hi = row.modifier ?? value
      require(value >= row.worstCase && value <= hi, () => `'${row.condition}' prints ${row.printed}; ${value} is outside it`)
    }
    const relief = reliefFor(row, value, darknessCause, darknessReduction)
    out.push(situational(
      row.condition.toLowerCase(),
      value + relief,
      relief !== 0 ? `${row.condition} — ${NIGHT_EYES} takes off ${relief}` : row.condition,
    ))
  }
  return out.filter((m): m is Modifier => m !== null)
}

function reliefFor(row: RangedModifier, value: number, cause: DarknessCause | null, reduction: number): number {
  if (row.worstCase === null || reduction === 0) return 0
  switch (cause) {
    case null:
    case 'HEAVY_FOG':
      return 0
    case 'DARKNESS':
      return Math.min(reduction, -value)
    case 'DIM_LIGHT':
      return -value
  }
}

function situational(id: string, value: number, description: string): Modifier | null {
  if (value === 0) return null
  return Modifier({
    id: `ranged:${id}`,
    source: { type: 'Situational', description },
    target: { type: 'AllRolls' },
    effect: Flat(value),
    layer: 'SITUATIONAL',
  })
}
