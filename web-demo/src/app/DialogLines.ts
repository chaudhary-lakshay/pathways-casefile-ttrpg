// The roll dialogs' printed ledgers, each named as in its Kotlin file.
import * as Corruption from '../rules/Corruption'
import * as SpiritContract from '../rules/SpiritContract'
import type { ClauseBonus } from '../rules/TraitEffects'

/** ContractDialog.kt `contractLines`: every term of the contract roll, none worth nothing. */
export function contractLines(
  skillLevel: number, conditionPenalty: number, shock: number, states: ClauseBonus, cor: number, sequence: number, held: number, burned: number,
): string[] {
  const corPenalty = Corruption.ritualPenalty(cor)
  return [
    `Ritualistic Magic ${skillLevel}`,
    conditionPenalty !== 0 ? `${conditionPenalty} hurt or tired` : null,
    shock !== 0 ? `${shock} shock` : null,
    states.line,
    corPenalty !== 0 ? `${corPenalty} for ${cor} CoR` : null,
    `Sequence -${sequence}`,
    `-${held}d6, rolled at Cast`,
    `Burn +${burned}`,
    `${SpiritContract.spi() + burned} SPI`,
  ].filter((it): it is string => it !== null)
}
