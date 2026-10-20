// The sheet's printed text, from PoolStrip.kt, EncumbranceCard.kt,
// VitalsTable.kt, SheetTabs.kt and SheetRows.kt.
import * as Encumbrance from '../rules/Encumbrance'
import { canonical, substringBefore } from '../rules/kt'
import { attrCode, DIFFICULTY, type SkillKey, type Trait } from '../rules/model'

/** AttackDialog.kt `SkillKey.label()`. */
export const skillKeyLabel = (k: SkillKey) => (k.specialization === null ? k.name : `${k.name} (${k.specialization})`)
import type { PoolStatus } from '../rules/Pools'

export function poolTags(status: PoolStatus): string[] {
  if (status.isDead) return ['Dead']
  const hurt = status.penalty !== 0 || status.isUnconscious || status.abilitiesOffline || status.rollsToStayConscious || status.corGained > 0
  if (!hurt) return []
  const out: string[] = []
  const head = substringBefore(status.effect, ':')
  if (status.isUnconscious) out.push('Unconscious')
  else if (status.effect.includes(':') && ![...head].some((c) => c === '.' || c === ';')) out.push(head)
  else if (status.pool !== 'FP') out.push(status.level)
  if (status.penalty !== 0) out.push(`−${-status.penalty} ${status.scope === 'SPI_ROLLS' ? 'SPI rolls' : 'all rolls'}`)
  if (status.corGained > 0) out.push(`+${status.corGained} CoR`)
  return out
}

export function poolLine(status: PoolStatus): string {
  const out = [status.level]
  if (status.penalty !== 0) out.push(`${status.penalty} to ${status.scope === 'SPI_ROLLS' ? 'SPI rolls and ability checks' : 'all rolls'}`)
  if (status.corGained > 0) out.push(`${status.corGained} CoR charged`)
  out.push(status.effect)
  return out.join(' — ')
}

export const spelledPool = (pool: PoolStatus['pool']) => (pool === 'HP' ? 'Hit points' : pool === 'FP' ? 'Fatigue points' : 'Spirituality')
export const twoDigits = (n: number) => (n < 0 ? `-${String(-n).padStart(2, '0')}` : String(n).padStart(2, '0'))

/** Kotlin `"%.1f".format`, HALF_UP. */
const oneDecimal = (x: number) => (Math.round(x * 10) / 10).toFixed(1)
const lbs = (v: number) => (v === Math.floor(v) ? `${v} lbs` : `${oneDecimal(v)} lbs`)

export function encumbranceLine(carried: number | null, basicLift: number): string {
  const head = `BL ${lbs(basicLift)} · `
  if (carried === null) return `${head}none`
  const level = Encumbrance.levelFor(carried, basicLift)
  if (level === null) return `${head}over 15 × BL: the table prints no level, so it cannot be carried`
  if (level.movePenalty === 0) return `${head}${level.name}, no penalty`
  return `${head}${level.name}: Move ${level.movePenalty}, Dodge ${level.dodgePenalty}${level.cannotRun ? ', cannot run' : ''}`
}

export const encumbranceDodge = (carried: number | null, basicLift: number) =>
  carried === null ? 0 : Encumbrance.levelFor(carried, basicLift)?.dodgePenalty ?? 0

/** What the carried field shows: a whole number without its ".0". */
export const carriedText = (carried: number | null) => (carried === null ? '' : String(carried))

export const spelledOut = (label: string) => (label === 'HP' ? 'Hit Points' : label === 'FP' ? 'Fatigue Points' : label === 'Per' ? 'Perception' : label)

export function drawbackPrice(trait: Trait, grantedTraits: Trait[], grantedBy: string): string {
  if (grantedTraits.some((it) => canonical(it) === canonical(trait))) return `from ${grantedBy}`
  return trait.cost === 0 ? 'gained in play' : `−${trait.cost} pts`
}

export const attrCodeOf = (key: SkillKey) => `${attrCode(key.attr)}/${DIFFICULTY[key.difficulty].code}`

export function disambiguated(key: SkillKey, visible: SkillKey[]): string {
  const label = skillKeyLabel(key)
  const collides = visible.some((it) => canonical(it) !== canonical(key) && skillKeyLabel(it) === label)
  return collides ? `${label} [${DIFFICULTY[key.difficulty].code}]` : label
}
