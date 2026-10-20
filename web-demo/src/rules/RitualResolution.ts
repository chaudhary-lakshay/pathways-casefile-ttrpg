import { addExact, ArithmeticException, coerceIn, eqIc, error, IllegalArgumentException, minByOrNull, multiplyExact, require, single } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type {
  LanguageProficiency, RitualComplexity, RitualCritical, RitualFailure, RitualPreparation,
  RitualSuccess, RitualWeight, TargetLink,
} from './content/schema.gen'
import { printedCost } from './content/members'
import type { RollResult } from './model'
import * as Corruption from './Corruption'
import * as Languages from './Languages'
import * as PowerSources from './PowerSources'
import { PowerSourceDeclaration } from './PowerSources'
import { threeDSix, type RandomSource } from './RandomSource'
import * as RitualMode from './RitualMode'
import * as SuccessRoll from './SuccessRoll'

export interface RitualOutcome {
  roll: RollResult
  target: number
  marginOfFailure: number
  consequence: RitualFailure | null
  criticalRoll: number | null
  critical: RitualCritical | null
  success: RitualSuccess | null
}

export const succeeded = (o: RitualOutcome) => o.consequence === null

export const SEVERE_MISFIRE_SHIFT = -2
export const LOWEST_CRITICAL_ROLL = 3
export const HIGHEST_CRITICAL_ROLL = 18
const CRITICAL_ROLL_FLOOR = 17
export const BURN_PER_SPI = 1

export const weights = (): RitualWeight[] => ContentLoader.ritualWeights()
export const preparations = (): RitualPreparation[] => ContentLoader.ritualPreparation()

export function preparationNamed(quality: string): RitualPreparation {
  const row = preparations().find((it) => eqIc(it.quality, quality))
  if (row === undefined) {
    throw new IllegalArgumentException(
      `no preparation quality named '${quality}'; the book prints ` + preparations().map((it) => it.quality).join(', '),
    )
  }
  return row
}

export function spiFor(weight: RitualWeight, chosen: number = weight.spi, burned = 0): number {
  require(burned >= 0, () => `SPI burned cannot be negative; was ${burned}`)
  const most = weight.spiOpen ? null : weight.spiMax ?? weight.spi
  require(chosen >= weight.spi && (most === null || chosen <= most), () =>
    `a ${weight.weight} ritual costs ${printedCost(weight)}; ${chosen} is outside that`)
  try {
    return addExact(chosen, burned)
  } catch (e) {
    if (e instanceof ArithmeticException) throw new IllegalArgumentException(`${chosen} SPI plus ${burned} burned does not fit`)
    throw e
  }
}

export interface RitualInputs {
  preparation?: RitualPreparation | null
  language?: LanguageProficiency | null
  mode?: RitualMode.RitualMode
  link?: TargetLink | null
  flourish?: boolean
  complexity?: RitualComplexity | null
  powerSources?: PowerSourceDeclaration
  preparationValue?: number | null
  linkValue?: number | null
  burned?: number
}

export function targetFor(
  skillLevel: number,
  weight: RitualWeight,
  cor: number,
  {
    preparation = null, language = null, mode = 'FULL', link = null, flourish = false, complexity = null,
    powerSources = PowerSourceDeclaration(), preparationValue = null, linkValue = null, burned = 0,
  }: RitualInputs = {},
): number {
  return skillLevel + weight.modifier + burnBonus(burned) + Corruption.ritualPenalty(cor) +
    RitualMode.modifierTotal(mode, preparation, link, flourish, preparationValue, linkValue) +
    Languages.ritualModifier(language) +
    PowerSources.modifierTotal(complexity, powerSources)
}

export function rollAgainst(
  target: number,
  source: RandomSource,
  failures: RitualFailure[] = ContentLoader.ritualFailures(),
  criticals: RitualCritical[] = ContentLoader.ritualCriticals(),
  successBands: RitualSuccess[] = ContentLoader.ritualSuccess(),
): RitualOutcome {
  const roll = SuccessRoll.against(target, source)
  return classify(roll, target, failures, criticals, successBands, (shift) =>
    coerceIn(threeDSix(source) + shift, LOWEST_CRITICAL_ROLL, HIGHEST_CRITICAL_ROLL))
}

function classify(
  roll: RollResult,
  target: number,
  failures: RitualFailure[],
  criticals: RitualCritical[],
  successBands: RitualSuccess[],
  tableRoll: (shift: number) => number,
): RitualOutcome {
  const none = { consequence: null, criticalRoll: null, critical: null, success: null }
  if (roll.outcome === 'SUCCESS' || roll.outcome === 'CRITICAL_SUCCESS') {
    return { ...none, roll, target, marginOfFailure: 0, success: successFor(target - roll.total, successBands) }
  }
  const margin = Math.max(roll.total - target, 0)
  const rolledCritical = roll.total >= CRITICAL_ROLL_FLOOR
  const consequence = rolledCritical ? single(failures.filter((it) => it.critical)) : failureFor(margin, failures)
  if (!rolledCritical && !reachesTheTable(consequence)) {
    return { ...none, roll, target, marginOfFailure: margin, consequence }
  }
  const criticalRoll = tableRoll(rolledCritical ? 0 : SEVERE_MISFIRE_SHIFT)
  return {
    ...none, roll, target, marginOfFailure: margin, consequence, criticalRoll,
    critical: criticalFor(criticalRoll, criticals),
  }
}

export function successFor(margin: number, bands: RitualSuccess[] = ContentLoader.ritualSuccess()): RitualSuccess {
  return bands.find((it) => margin >= it.from && (it.to === null || margin <= it.to)) ??
    minByOrNull(bands, (it) => it.from) ??
    error('the book prints no ritual success band')
}

export function failureFor(margin: number, failures: RitualFailure[] = ContentLoader.ritualFailures()): RitualFailure {
  require(margin >= 1, () => `a failure is by at least 1; was ${margin}`)
  return failures.findLast((it) => !it.critical && margin >= (it.from ?? 0) && (it.to === null || margin <= it.to)) ??
    error(`no ritual failure row covers a margin of ${margin}`)
}

export function criticalFor(roll: number, criticals: RitualCritical[] = ContentLoader.ritualCriticals()): RitualCritical {
  require(roll >= LOWEST_CRITICAL_ROLL && roll <= HIGHEST_CRITICAL_ROLL, () => `the Critical Failure Table is 3d6; was ${roll}`)
  return single(criticals.filter((it) => roll >= it.low && roll <= it.high))
}

const reachesTheTable = (f: RitualFailure) => f.consequence.includes('Critical Failure Table')

export function burnBonus(burned: number): number {
  require(burned >= 0, () => `cannot burn a negative number of SPI; was ${burned}`)
  return multiplyExact(burned, BURN_PER_SPI)
}
