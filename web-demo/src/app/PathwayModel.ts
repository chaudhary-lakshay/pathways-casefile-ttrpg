// The pathway screen's pure helpers: PathwayScreen.kt, PathwayTrackers.kt,
// PathwayPotion.kt and PathwayHeader.kt, each named as in Kotlin.
import type { RitualCorThreshold } from '../rules/content/schema.gen'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as Corruption from '../rules/Corruption'
import { canonical } from '../rules/kt'
import { Beyonder, BeyonderState, type DiceExpr } from '../rules/model'
import * as PotionConsumption from '../rules/PotionConsumption'
import * as PotionDigestion from '../rules/PotionDigestion'
import { threeDSix, type RandomSource } from '../rules/RandomSource'
import * as ScoreFloors from '../rules/ScoreFloors'
import type { BeyonderSession } from './session/BeyonderSession'

/** PathwayScreen.kt `swapping`: two names trade places. */
export function swapping(list: string[], a: string, b: string): string[] {
  const i = list.indexOf(a)
  const j = list.indexOf(b)
  if (i < 0 || j < 0) return list
  const out = [...list]
  out[i] = b
  out[j] = a
  return out
}

/** PathwayTrackers.kt `pcrLine`. */
export function pcrLine(state: BeyonderState, cor: number, modifier = 0): string {
  const dice = PotionConsumption.corDiceFor(state)
  return `3d6 ≤ ${PotionConsumption.targetFor(state.digestion, cor, undefined, modifier)}` + (dice === null ? '' : ` — on success +${printedDice(dice)} CoR`)
}

const printedDice = (dice: DiceExpr) => `${dice.count}d${dice.sides}`

/** PathwayTrackers.kt `drinkLine`. */
export function drinkLine(state: BeyonderState | null, cor: number, ceilingAfter: number, modifier = 0): string | null {
  const roll = state !== null ? `at ${state.digestion}% digested, rolls ${pcrLine(state, cor, modifier)}` : null
  if (!ScoreFloors.holdsCeiling(ceilingAfter)) {
    const refused = `CoR ceiling after this potion would be ${ceilingAfter}, and this app keeps it at ` +
      `${ScoreFloors.MIN_COR_CEILING} or more — it cannot be drunk until the ceiling is raised`
    return roll === null ? refused : `${roll}. ${refused}`
  }
  if (state === null) return null
  const dice = PotionConsumption.corDiceFor(state)
  const least = dice ? dice.count + dice.modifier : 0
  const most = dice ? dice.count * dice.sides + dice.modifier : 0
  if (Corruption.isLostAfter(cor, least, ceilingAfter)) {
    return `${roll}. Maximum CoR after this potion is ${ceilingAfter}; ${cor + least} CoR reaches it — ` +
      'lost to corruption even if the potion integrates'
  }
  if (dice !== null && Corruption.isLostAfter(cor, most, ceilingAfter)) {
    return `${roll}. Maximum CoR after this potion is ${ceilingAfter}; a ${ceilingAfter - cor} or more on the ` +
      `${printedDice(dice)} reaches it — lost to corruption if it does`
  }
  return roll
}

/** PathwayTrackers.kt `drinkTarget`: the potion Drink offers on this rung, if any. */
export function drinkTarget(pathway: string, sequence: number, drunk: BeyonderState | null, printed: (s: BeyonderState) => boolean): BeyonderState | null {
  if (drunk === null) return sequence === PotionDigestion.STARTING_SEQUENCE ? BeyonderState({ pathway, sequence }) : null
  if (drunk.pathway !== pathway) return null
  const next = Beyonder.advanced(drunk)
  return next !== null && printed(next) ? next : null
}

/** PathwayTrackers.kt `drinkCaption`. */
export function drinkCaption(target: BeyonderState, drunk: BeyonderState | null, cor: number, ceilingAfter: number, modifier = 0): string | null {
  const line = drinkLine(drunk, cor, ceilingAfter, modifier)
  if (line === null) return null
  return drunk === null ? line : `next: Sequence ${target.sequence} of the ${target.pathway} Pathway · ${line}`
}

export const digestionCells = (digestion: number) => Math.floor(digestion / 10)

/** PathwayTrackers.kt `corLadder`: every rung, and whether it is reached. */
export function corLadder(cor: number): [RitualCorThreshold, boolean][] {
  const reached = new Set(Corruption.ritualThresholdsReached(cor).map(canonical))
  return ContentLoader.ritualCorThresholds().map((it) => [it, reached.has(canonical(it))])
}

/** PathwayTrackers.kt `actingMethodRoll`: 3d6 percent, once for the picture drawn. */
export function actingMethodRoll(session: BeyonderSession, drawnDigestion: number, dice: RandomSource): number | null {
  if (session.isLost || session.state?.digestion !== drawnDigestion || drawnDigestion >= 100) return null
  const rolled = threeDSix(dice)
  session.digest(rolled)
  return rolled
}

const STARTING_DIGESTION_DICE = 4

/** PathwayTrackers.kt `startingDigestion`: 4d20 percent, once, from 0. */
export function startingDigestion(session: BeyonderSession, drawnDigestion: number, dice: RandomSource): number | null {
  if (session.isLost || drawnDigestion !== 0 || session.state?.digestion !== 0) return null
  const faces = Array.from({ length: STARTING_DIGESTION_DICE }, () => dice.roll(20))
  dice.thrown?.(faces, null)
  const sum = faces.reduce((a, b) => a + b, 0)
  session.digest(sum)
  return sum
}

/** PathwayPotion.kt `spendLabel`: the Spend button names every CoR the tap charges. */
export function spendLabel(price: number, left: number): string {
  const debt = Corruption.soulDebt(left, price)
  const drained = Corruption.drainedUse(left)
  const why = [debt > 0 ? 'Soul Debt' : null, drained > 0 ? 'drained' : null].filter((it) => it !== null)
  return why.length === 0 ? `Spend ${price} SPI` : `Spend ${price} SPI · +${debt + drained} CoR (${why.join(', ')})`
}

/** PathwayPotion.kt `formatDelta`. */
export function formatDelta(delta: number): string {
  const sign = delta < 0 ? '' : '+'
  return `${sign}${delta}`
}

/** PathwayScreen.kt `potionOn`: the block's potion, keeping a walked chain's drawback. */
export const potionOn = (pathway: string, sequence: number, drunk: BeyonderState | null) =>
  BeyonderState({ pathway, sequence, drawback: drunk?.pathway === pathway ? drunk.drawback : null })
