// The roll dialogs' printed ledgers, each named as in its Kotlin file.
import type { LanguageProficiency, RitualWeight } from '../rules/content/schema.gen'
import * as Languages from '../rules/Languages'
import type { BoonState } from '../rules/model'
import * as Corruption from '../rules/Corruption'
import * as PowerSources from '../rules/PowerSources'
import { OPEN_END_STEPS } from './session/SheetLines'
import * as SpiritContract from '../rules/SpiritContract'
import type { ClauseBonus } from '../rules/TraitEffects'

/** RitualDialog.kt `ritualRefusal`: a forbidden tongue is the one refusal. */
export const ritualRefusal = (canSpeak: boolean): string | null => (canSpeak ? null : 'No roll — the book forbids this attempt.')

/** RitualControls.kt `spiChoices`: the weight's SPI cell, an open one three past its top. */
export function spiChoices(weight: RitualWeight): number[] {
  const top = weight.spiMax ?? weight.spi
  const last = weight.spiOpen ? top + OPEN_END_STEPS : top
  return Array.from({ length: last - weight.spi + 1 }, (_, i) => weight.spi + i)
}

/** RitualControls.kt `invocationApplied`. */
export const invocationApplied = (invoked: boolean, orthodox: boolean, gmValue: number): number | null =>
  !invoked ? null : orthodox ? PowerSources.ORTHODOX_BONUS : gmValue

/** RitualControls.kt `hpChoices`: HP for up to 4 SPI of the fuel. */
export const hpChoices = (cost: number): number[] => Array.from({ length: Math.min(cost, 4) + 1 }, (_, i) => PowerSources.hpFor(i))

/** BoonDialog.kt `standingRefusal`: the two refusals no pick in the dialog can lift. */
export function standingRefusal(skillLevel: number | null, lost: boolean): string | null {
  if (lost) return 'No prayer — this character is lost, and the GM plays them.'
  if (skillLevel === null) return 'No prayer — a boon is rolled on Ritualistic Magic, which has no default. Buy it under Spend points.'
  return null
}

/** BoonDialog.kt `boonRefusal`. */
export function boonRefusal(
  skillLevel: number | null, patron: string | null, held: BoonState | null, next: BoonState | null, lost = false, language: LanguageProficiency | null = null,
): string | null {
  const standing = standingRefusal(skillLevel, lost)
  if (standing !== null) return standing
  if (patron === null) return 'Choose a patron.'
  if (next === null && held !== null) return `The book records no boon past Sequence ${held.sequence} for this patron.`
  if (next === null) return 'No boon can be asked of this patron.'
  if (language !== null && !Languages.canPerformRituals(language)) return `No prayer in a ${language.proficiency} tongue: the book forbids a ritual in it.`
  return null
}

/** BoonDialog.kt `fuelLine`. */
export const fuelLine = (spi: number, hp: number) => (hp > 0 ? `${spi} SPI and ${hp} HP` : `${spi} SPI`)

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
