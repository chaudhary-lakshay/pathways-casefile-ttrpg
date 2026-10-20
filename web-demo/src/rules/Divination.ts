import { Buyable, isSuccess, SkillKey, SPI, type Outcome, type RollResult } from './model'
import { eqIc, error, first, maxByOrNull, minByOrNull, multiplyExact, nn, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { DivinationClarity, DivinationDifficulty, DivinationMethod, DivinationModifier } from './content/schema.gen'
import * as SuccessRoll from './SuccessRoll'
import type { RandomSource } from './RandomSource'

export const SKILL = 'Divination Arts'
export const SPECIALISATION_BONUS = 1
export const UNAPPLIED = new Map([
  ['Specialisation',
    'the book prices it as a technique (GURPS p. B229), which this book ' +
    'does not reprint, and nothing here records which method a diviner ' +
    'trained in'],
])
export const BURN_PER_SPI = 1
export const DEFAULT_PENALTY = -4

/** `TraitEffects.VERSATILE`, a literal: read in a string at call time only. */
const VERSATILE = 'Versatile'

export interface SkillOrDefault {
  level: number
  isDefault: boolean
  own: number | null
  defaultBonus: number
}

export function sourceLine(chosen: SkillOrDefault): string | null {
  if (!chosen.isDefault) return null
  const spi = chosen.level - DEFAULT_PENALTY - chosen.defaultBonus
  const lift = chosen.defaultBonus === 0 ? '' : `, +${chosen.defaultBonus} for ${VERSATILE}`
  const base = `Defaulted from SPI ${spi}${DEFAULT_PENALTY}${lift}. "Divination Arts defaults to SPI-4 (unskilled)."`
  return chosen.own === null
    ? `${base} This character has no ${SKILL} of their own.`
    : `${base} Better than their own ${SKILL} ${chosen.own}.`
}

export function divinationLevelFor(own: number | null, spi: number, defaultBonus: number): SkillOrDefault {
  const defaulted = spi + DEFAULT_PENALTY + defaultBonus
  return own !== null && defaulted <= own
    ? { level: own, isDefault: false, own, defaultBonus: 0 }
    : { level: defaulted, isDefault: true, own, defaultBonus }
}

export const keyFor = (chosen: SkillOrDefault): SkillKey =>
  SkillKey(SKILL, null, chosen.isDefault ? SPI : Buyable('IQ'), 'HARD')

export const difficulties = (): DivinationDifficulty[] => ContentLoader.divinationDifficulty()
export const methods = (): DivinationMethod[] => ContentLoader.divinationMethods()
export const modifiers = (): DivinationModifier[] => ContentLoader.divinationModifiers()
export const clarities = (): DivinationClarity[] => ContentLoader.divinationClarity()

export function methodNamed(name: string): DivinationMethod {
  const found = methods().find((it) => eqIc(it.method, name))
  require(found !== undefined, () => `no divination method named '${name}'; the book prints ${methods().map((it) => it.method).join(', ')}`)
  return found
}

export function modifierTotal(rows: DivinationModifier[], gmValue: Map<string, number> = new Map(), repeats = 1): number {
  require(repeats >= 1, () => `a repeat count starts at 1; was ${repeats}`)
  let sum = 0
  for (const row of rows) {
    require(!row.crossReference, () => `'${row.source}' is Step 1's own table, not a modifier to add beside it`)
    const value = gmValue.get(row.source) ?? row.modifier ?? error(`'${row.source}' prints no modifier and none was given`)
    if (row.best !== null) {
      const ends = [nn(row.modifier), row.best].sort((a, b) => a - b)
      require(value >= ends[0] && value <= ends[1], () => `'${row.source}' prints ${row.printed}; ${value} is outside it`)
    }
    sum += row.perRepeat ? value * repeats : value
  }
  return sum
}

export function targetFor(skill: number, difficulty: DivinationDifficulty, extras = 0, burned = 0): number {
  require(burned >= 0, () => `cannot burn a negative number of SPI; was ${burned}`)
  return skill + difficulty.modifier + extras + multiplyExact(burned, BURN_PER_SPI)
}

export function clarityFor(roll: RollResult, demoted = false): DivinationClarity {
  const rows = clarities()
  if (roll.outcome === 'CRITICAL_FAILURE') return first(rows, (it) => it.isCritical)
  const succeeded = !demoted && isSuccess(roll.outcome)
  const side = rows.filter((it) => !it.isCritical && it.isFailure !== succeeded)
  const by = succeeded ? roll.margin : -roll.margin
  return maxByOrNull(side.filter((it) => (it.margin ?? 0) <= by), (it) => it.margin ?? 0) ??
    minByOrNull(side, (it) => it.margin ?? 0) ??
    error(`the clarity table prints no ${succeeded ? 'success' : 'failure'} row`)
}

export interface DivinationOutcome {
  roll: RollResult
  clarity: DivinationClarity
  requiresCritical: boolean
}

export const outcomeIsCritical = (o: DivinationOutcome) => o.clarity.isCritical

export const effectiveOutcome = (o: DivinationOutcome): Outcome =>
  o.requiresCritical && o.roll.outcome === 'SUCCESS' ? 'FAILURE' : o.roll.outcome

export const attempt = (skill: number, difficulty: DivinationDifficulty, source: RandomSource, extras = 0, requiresCritical = false, burned = 0) =>
  rollAgainst(targetFor(skill, difficulty, extras, burned), source, requiresCritical)

export const rollAgainst = (target: number, source: RandomSource, requiresCritical = false): DivinationOutcome =>
  read(SuccessRoll.against(target, source), requiresCritical)

function read(roll: RollResult, requiresCritical: boolean): DivinationOutcome {
  const demoted = requiresCritical && roll.outcome === 'SUCCESS'
  return { roll, clarity: clarityFor(roll, demoted), requiresCritical }
}

export function spiFor(method: DivinationMethod, chosen: number = method.spi, burned = 0): number {
  const most = method.spiMax ?? method.spi
  require(chosen >= method.spi && chosen <= most, () => `${method.method} costs ${method.spi}..${most} SPI; ${chosen} is outside that`)
  require(burned >= 0, () => `SPI burned cannot be negative; was ${burned}`)
  return chosen + burned
}
