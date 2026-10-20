import { error, floorDiv, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { AntiDivinationCounter, SequenceImmunity } from './content/schema.gen'
import { Flat, Modifier, type ModEffect } from './model'

export interface AntiDivinationResult {
  penalty: number
  requiresCritical: boolean
  parts: string[]
  hpSpent: number
}

export const rows = (): SequenceImmunity[] => ContentLoader.sequenceImmunity()
export const counters = (): AntiDivinationCounter[] => ContentLoader.antiDivinationCounters()

export const counterNamed = (name: string): AntiDivinationCounter =>
  counters().find((it) => it.name === name) ?? error(`the book prints no anti-divination counter called '${name}'`)

export function rowFor(sequence: number): SequenceImmunity {
  return rows().find((it) => sequence >= it.sequenceLow && sequence <= it.sequenceHigh) ??
    error(
      'the Sequence Immunity table prints no row for Sequence ' +
      `${sequence}; it covers ${Math.min(...rows().map((it) => it.sequenceLow))}..` +
      `${Math.max(...rows().map((it) => it.sequenceHigh))}`,
    )
}

export function halvedMagnitude(penalty: number, roundUp: boolean): number {
  require(penalty <= 0, () => `penalties are negative or zero; was ${penalty}`)
  const magnitude = -penalty
  const half = roundUp ? -floorDiv(-magnitude, 2) : floorDiv(magnitude, 2)
  return -half
}

export function against(
  targetSequence: number,
  divinerSequence: number | null = null,
  techniquePenalty = 0,
  directLink = false,
  hpSacrificed = 0,
): AntiDivinationResult {
  require(techniquePenalty <= 0, () => `an active technique is a penalty; was ${techniquePenalty}`)
  require(hpSacrificed >= 0, () => 'HP sacrificed cannot be negative')
  const row = rowFor(targetSequence)
  const parts: string[] = []
  const hasAdvantage = divinerSequence !== null && divinerSequence <= targetSequence - 2
  const isEqual = divinerSequence !== null && divinerSequence === targetSequence
  let immunity = hasAdvantage ? 0 : isEqual && row.equalPenalty !== null ? row.equalPenalty : row.penalty ?? 0
  const critical = row.requiresCritical && !hasAdvantage
  let techniques = hasAdvantage ? halvedMagnitude(techniquePenalty, true) : techniquePenalty
  if (directLink) {
    immunity = halvedMagnitude(immunity, false)
    techniques = halvedMagnitude(techniques, false)
  }
  let total = immunity + techniques
  const sacrifice = counterNamed('HP Sacrifice')
  const required = sacrifice.hpRequired ?? 0
  let hpSpent = 0
  let relief = 0
  if (required > 0 && hpSacrificed >= required && total !== 0) {
    const forgiven = sacrifice.ignoresPenalty ?? 0
    const eased = Math.min(0, total - forgiven)
    relief = eased - total
    total = eased
    hpSpent = hpSacrificed
  }
  const halved = directLink ? ' (halved by Direct Link)' : ''
  if (hasAdvantage && (row.penalty ?? 0) !== 0) parts.push(`Sequence Advantage ignores ${row.label}`)
  if (immunity !== 0) parts.push(`${row.label} ${signed(immunity)}${halved}`)
  if (techniques !== 0) parts.push(`active techniques ${signed(techniques)}${halved}`)
  if (relief !== 0) parts.push(`${hpSpent} HP forgives ${signed(relief)}`)
  return { penalty: total, requiresCritical: critical, parts, hpSpent }
}

export function modifiers(result: AntiDivinationResult): Modifier[] {
  const out: Modifier[] = []
  if (result.penalty !== 0) out.push(modifier('anti-divination', "Target's protection", Flat(result.penalty)))
  if (result.requiresCritical) {
    out.push(modifier('anti-divination:critical', 'Only a critical success can succeed', { type: 'RequireCrit' }))
  }
  return out
}

const modifier = (id: string, label: string, effect: ModEffect) =>
  Modifier({ id, source: { type: 'Situational', description: label }, target: { type: 'AllRolls' }, effect, layer: 'SITUATIONAL' })

const signed = (n: number) => (n >= 0 ? `+${n}` : `${n}`)
