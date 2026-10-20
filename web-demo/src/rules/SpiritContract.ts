import { require, single } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { ContractOutcome, RitualContract } from './content/schema.gen'
import type { BeyonderState, RollResult } from './model'
import * as Corruption from './Corruption'
import type { RandomSource } from './RandomSource'

export interface ContractPenalty {
  sequence: number
  dice: number[]
}

export const penaltyTotal = (p: ContractPenalty) => -p.sequence - p.dice.reduce((s, d) => s + d, 0)

const SCROLLS_PATHWAY = 'Hermit'
const SCROLLS_SEQUENCE = 6

export const contract = (): RitualContract => single(ContentLoader.ritualContract())
export const spi = () => contract().spi
export const outcomes = (): ContractOutcome[] => contract().outcomes

export const maxContracts = (state: BeyonderState | null) =>
  state?.pathway === SCROLLS_PATHWAY && state.sequence <= SCROLLS_SEQUENCE
    ? contract().maxContractsScrollsProfessor
    : contract().maxContracts

export function targetFor(skill: number, cor: number, penalty: ContractPenalty, burned: number): number {
  require(burned >= 0, () => `a burn cannot be negative (was ${burned})`)
  return skill + Corruption.ritualPenalty(cor) + penaltyTotal(penalty) + burned
}

export function penaltyFor(sequence: number, held: number, max: number, source: RandomSource): ContractPenalty {
  require(held >= 0 && held < max, () => `${held} contracts held; the most is ${max}, so no other can be made`)
  const dice = Array.from({ length: held }, () => source.roll(6))
  if (dice.length > 0) source.thrown?.(dice, null)
  return { sequence, dice }
}

export function outcomeFor(roll: RollResult): ContractOutcome {
  const rows = outcomes()
  if (roll.outcome === 'CRITICAL_FAILURE') return single(rows.filter((it) => it.critical))
  if (roll.outcome === 'FAILURE') return single(rows.filter((it) => it.failure && !it.critical))
  const margin = Math.max(roll.margin, 0)
  return single(rows.filter((it) => !it.failure).filter((row) => margin >= row.from! && (row.to === null || margin <= row.to)))
}
