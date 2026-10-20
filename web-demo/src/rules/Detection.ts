import { eqIc, error, IllegalArgumentException, require, sumOf } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type {
  AntiDivinationItem, AntiDivinationTechnique, DetectionModifier, DetectionOutcome, DivinationAwareness,
} from './content/schema.gen'
import type { RollResult } from './model'
import type { PoolStatus } from './Pools'
import type { RandomSource } from './RandomSource'
import * as ScoreFloors from './ScoreFloors'
import * as SuccessRoll from './SuccessRoll'
import { ETHER_BODY_DAMAGE, type ClauseBonus } from './TraitEffects'

export interface DetectionResult {
  roll: RollResult
  row: DetectionOutcome
  parts: string[]
}

export const GENERIC_CHARM = 'Target carries a protective charm or item'
export const PER = 'PER'
export const SPI = 'SPI'
const NO_CLAUSE: ClauseBonus = { total: 0, line: null }

export function attributeFor(spi: number, per: number, freeRoll: string | null = null, alertness = 0, spiModifier = 0, perClause = 0): number {
  require(spi >= 0, () => `SPI is an attribute, not a pool balance; was ${spi}`)
  require(per >= ScoreFloors.MIN_PER, () => `Per is an attribute, not a pool balance; was ${per}`)
  require(alertness >= 0, () => `Alertness is a bonus, not a penalty; was ${alertness}`)
  switch (freeRoll?.toUpperCase() ?? null) {
    case null: return takesPer(spi, per) ? per + alertness + perClause : spi + spiModifier
    case SPI: return spi + spiModifier
    case PER: return per + alertness + perClause
    default: throw new IllegalArgumentException(`the awareness table grants only ${SPI} and ${PER} rolls; was '${freeRoll}'`)
  }
}

export const isAutomaticAgainst = (row: DivinationAwareness, divinerIsWeaker: boolean) => row.isAutomatic && divinerIsWeaker

export function attributeLine(
  spi: number, per: number, freeRoll: string | null = null, alertness = 0, spiModifier = 0, perClause: ClauseBonus = NO_CLAUSE,
): string {
  const alert = (alertness > 0 ? `, +${alertness} Alertness` : '') + (perClause.line !== null ? `, ${perClause.line}` : '')
  const perTotal = per + alertness + perClause.total
  const ether = spiModifier !== 0 ? `, ${spiModifier} ${ETHER_BODY_DAMAGE}` : ''
  switch (freeRoll?.toUpperCase() ?? null) {
    case SPI: return `SPI ${spi + spiModifier} (the row grants a SPI roll${ether})`
    case PER: return `Per ${perTotal} (the row grants a Per roll${alert})`
    default:
      return takesPer(spi, per)
        ? `Per ${perTotal} (the higher of SPI ${spi} and Per ${per}${alert})`
        : `SPI ${spi + spiModifier} (the higher of SPI ${spi} and Per ${per}${ether})`
  }
}

const takesPer = (spi: number, per: number) => per > spi

export function isPerRoll(spi: number, per: number, freeRoll: string | null = null): boolean {
  const f = freeRoll?.toUpperCase() ?? null
  return f === null ? takesPer(spi, per) : f === PER
}

export const awareness = (): DivinationAwareness[] => ContentLoader.divinationAwareness()
export const outcomes = (): DetectionOutcome[] => ContentLoader.detectionOutcomes()
export const modifiers = (): DetectionModifier[] => ContentLoader.detectionModifiers()
export const items = (): AntiDivinationItem[] => ContentLoader.antiDivinationItems()

function named<T>(rows: T[], name: (t: T) => string, wanted: string, what: string): T {
  const row = rows.find((it) => eqIc(name(it), wanted))
  if (row === undefined) throw new IllegalArgumentException(`${what} '${wanted}'; the book prints ` + rows.map(name).join(', '))
  return row
}

export const modifierNamed = (s: string) => named(modifiers(), (it) => it.situation, s, 'no detection modifier called')
export const itemNamed = (s: string) => named(items(), (it) => it.name, s, 'no anti-divination item called')
export function awarenessFor(pathway: string): DivinationAwareness {
  const row = awareness().find((it) => eqIc(it.pathway, pathway))
  if (row === undefined) {
    throw new IllegalArgumentException(
      `the awareness table prints no row for '${pathway}'; it prints ` + awareness().map((it) => it.pathway).join(', '),
    )
  }
  return row
}

export function modifierTotal(
  situations: DetectionModifier[] = [],
  techniques: AntiDivinationTechnique[] = [],
  itemsHeld: AntiDivinationItem[] = [],
  awarenessBonus = 0,
): number {
  const withBonus = itemsHeld.filter((it) => it.detectionBonus !== null)
  if (withBonus.length > 0 && situations.some((it) => eqIc(it.situation, GENERIC_CHARM))) {
    throw new IllegalArgumentException(
      `'${GENERIC_CHARM}' is the general price of ` + withBonus.map((it) => it.name).join(', ') + '; take one or the other, never both',
    )
  }
  return sumOf(situations, (it) => it.modifier) +
    sumOf(techniques, (it) => it.detectionBonus ?? 0) +
    sumOf(withBonus, (it) => it.detectionBonus ?? 0) +
    awarenessBonus
}

export interface DetectionInputs {
  modifier?: number
  freeRoll?: string | null
  alertness?: number
  shock?: number
  hurt?: number
  spiModifier?: number
  states?: ClauseBonus
  perClause?: ClauseBonus
  attrition?: number
}

export const targetFor = (
  spi: number,
  per: number,
  { modifier = 0, freeRoll = null, alertness = 0, shock = 0, hurt = 0, spiModifier = 0, states = NO_CLAUSE, perClause = NO_CLAUSE, attrition = 0 }:
    DetectionInputs = {},
) => attributeFor(spi, per, freeRoll, alertness, spiModifier, perClause.total) + modifier + shock + hurt + states.total + attrition

export const attritionFor = (pool: PoolStatus | null, aw: DivinationAwareness | null) =>
  aw === null ? 0 : pool !== null && pool.scope === 'SPI_ROLLS' ? pool.penalty : 0

export function outcomeFor(roll: RollResult): DetectionOutcome {
  const namedRow = outcomes().findLast((it) => it.outcome === roll.outcome && it.margin === null) ?? null
  if (roll.outcome !== 'SUCCESS') return namedRow ?? error(`the detection table prints no ${roll.outcome} row`)
  const banded = outcomes().findLast((it) => it.outcome === 'SUCCESS' && (it.margin ?? 0) <= roll.margin) ?? null
  return banded ?? namedRow ?? error('the detection table prints no success row')
}

export function rollAgainst(target: number, source: RandomSource, parts: string[]): DetectionResult {
  const roll = SuccessRoll.against(target, source)
  return { roll, row: outcomeFor(roll), parts }
}

export interface PartsInputs extends DetectionInputs {
  situations?: DetectionModifier[]
  techniques?: AntiDivinationTechnique[]
  items?: AntiDivinationItem[]
  awarenessBonus?: number
  awarenessLabel?: string | null
}

export function partsFor(
  spi: number,
  per: number,
  {
    situations = [], techniques = [], items: held = [], awarenessBonus = 0, awarenessLabel = null, freeRoll = null,
    alertness = 0, shock = 0, hurt = 0, spiModifier = 0, states = NO_CLAUSE, perClause = NO_CLAUSE, attrition = 0,
  }: PartsInputs = {},
): string[] {
  const out = [attributeLine(spi, per, freeRoll, alertness, spiModifier, perClause)]
  if (shock !== 0) out.push(`shock ${signed(shock)}`)
  if (hurt !== 0) out.push(`hurt or tired ${signed(hurt)}`)
  if (states.line !== null) out.push(states.line)
  if (attrition !== 0) out.push(`Spiritual Attrition ${signed(attrition)}`)
  for (const it of situations.filter((s) => s.modifier !== 0)) out.push(`${it.situation} ${signed(it.modifier)}`)
  for (const it of techniques.filter((t) => (t.detectionBonus ?? 0) !== 0)) out.push(`${it.name} ${signed(it.detectionBonus ?? 0)}`)
  for (const it of held.filter((t) => (t.detectionBonus ?? 0) !== 0)) out.push(`${it.name} ${signed(it.detectionBonus ?? 0)}`)
  if (awarenessBonus !== 0) out.push(`${awarenessLabel ?? 'pathway ability'} ${signed(awarenessBonus)}`)
  return out
}

const signed = (n: number) => (n >= 0 ? `+${n}` : `${n}`)
