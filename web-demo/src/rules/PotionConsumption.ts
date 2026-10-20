import { error, maxByOrNull, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { PcrRow } from './content/schema.gen'
import { Beyonder, Dice, type BeyonderState, type DiceExpr, type RollResult } from './model'
import type { RandomSource } from './RandomSource'
import * as SuccessRoll from './SuccessRoll'

export interface PcrOutcome {
  roll: RollResult
  target: number
  digestion: number
  corGained: number
}

export const integrated = (o: PcrOutcome) => o.roll.total <= o.target
export const lost = (o: PcrOutcome) => !integrated(o)

export const ADVANCING_EARLY = 'Advancing sequence'

export function rowFor(digestion: number, rows: PcrRow[] = ContentLoader.potionConsumption()): PcrRow {
  require(digestion >= 0 && digestion <= 100, () => `digestion is a percentage; was ${digestion}`)
  return maxByOrNull(rows.filter((it) => it.digestion <= digestion), (it) => it.digestion) ??
    error(`no Potion Consumption Roll row at or below ${digestion}%`)
}

export function targetFor(digestion: number, cor: number, rows: PcrRow[] = ContentLoader.potionConsumption(), modifier = 0): number {
  require(cor >= 0, () => `CoR cannot be negative; was ${cor}`)
  return rowFor(digestion, rows).effectiveTarget - cor + modifier
}

export function attempt(
  state: BeyonderState, cor: number, source: RandomSource, rows: PcrRow[] = ContentLoader.potionConsumption(), modifier = 0,
): PcrOutcome {
  const target = targetFor(state.digestion, cor, rows, modifier)
  const roll = SuccessRoll.against(target, source)
  const dice = corDiceFor(state)
  let gained = 0
  if (roll.total <= target && dice !== null) {
    const faces = Array.from({ length: dice.count }, () => source.roll(dice.sides))
    source.thrown?.(faces, null)
    gained = faces.reduce((s, f) => s + f, 0) + dice.modifier
  }
  return { roll, target, digestion: state.digestion, corGained: gained }
}

export const corDiceFor = (state: BeyonderState): DiceExpr | null => (Beyonder.isFullyDigested(state) ? null : earlyAdvancementDice())

export function earlyAdvancementDice(): DiceExpr {
  const dice = ContentLoader.corGained().find((it) => it.action.startsWith(ADVANCING_EARLY))?.dice ?? null
  return dice === null ? error(`cor_gained.json has no rolled row starting '${ADVANCING_EARLY}'`) : Dice.parse(dice)
}
