import { error, floorDiv, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { PoolThreshold } from './content/schema.gen'
import * as ScoreFloors from './ScoreFloors'

export type Pool = 'HP' | 'FP' | 'SPI'
export type PenaltyScope = 'ALL_ROLLS' | 'SPI_ROLLS'

export interface PoolStatus {
  pool: Pool
  current: number
  max: number
  level: string
  effect: string
  penalty: number
  scope: PenaltyScope
  corGained: number
  isDead: boolean
  isUnconscious: boolean
  abilitiesOffline: boolean
  rollsToStayConscious: boolean
  rollsToSurvive: boolean
}

function status(p: Pick<PoolStatus, 'pool' | 'current' | 'max' | 'level' | 'effect'> & Partial<PoolStatus>): PoolStatus {
  return {
    penalty: 0, scope: 'ALL_ROLLS', corGained: 0, isDead: false, isUnconscious: false,
    abilitiesOffline: false, rollsToStayConscious: false, rollsToSurvive: false, ...p,
  }
}

const THIRD = 3
const DEAD_MULTIPLE = 5
const DESTROYED_MULTIPLE = 10
const SECOND_MULTIPLE = 2
export const HEALTHY_EFFECT = 'Normal function; no penalties.'
export const REELING_PENALTY = -1
export const ATTRITION_PENALTY = -3
export const TIRED_PENALTY = -1

export const UNMODELLED = new Map([
  ['Spirituality Recovery',
    'p. 34: "recovers 1 SPI per hour. This is the standard and only ' +
    'recovery rate. Combat, ritual casting, or heavy physical ' +
    'exertion interrupts rest and halts recovery." -- both the rate ' +
    'and the interruption are clock-bound, and this app holds no ' +
    "clock to run either against. p. 88's Sequence 9 Bard prints a " +
    'narrower ability that restores SPI outside ordinary rest ' +
    '("allies recover 1 SPI at the song\'s end", two at p. 100). ' +
    'Reading that as specific-beating-general rather than as a ' +
    "contradiction is THIS REPO's, on the model of the Potion " +
    "Consumption Roll against SuccessRoll's crits; the book states " +
    'no priority. Nothing turns on it while both are unmodelled, and ' +
    'pathway prose is not modelled here either'],
])

export const thresholds = (pool: Pool): PoolThreshold[] => ContentLoader.poolThresholds().filter((it) => it.pool === pool)

export function hp(current: number, max: number): PoolStatus {
  requireMax(max, 'HP')
  const dead = current <= -DEAD_MULTIPLE * max
  let level: string | null
  if (current <= -DESTROYED_MULTIPLE * max) level = '-10×HP'
  else if (dead) level = '-5×HP'
  else if (current <= -SECOND_MULTIPLE * max) level = '-2× to -4×HP'
  else if (current <= -max) level = '-1×HP'
  else if (current <= 0) level = '0 HP'
  else if (current <= floorDiv(max, THIRD)) level = '1/3 HP remaining'
  else level = null
  return status({
    pool: 'HP',
    current,
    max,
    level: level ?? 'Above 1/3 HP',
    effect: level !== null ? row('HP', level).effect : HEALTHY_EFFECT,
    penalty: current <= floorDiv(max, THIRD) ? REELING_PENALTY : 0,
    isDead: dead,
    rollsToStayConscious: !dead && current <= 0,
    rollsToSurvive: !dead && current <= -max,
  })
}

export function fp(current: number, max: number): PoolStatus {
  requireMax(max, 'FP')
  const tired = fatigued(current, max) ? TIRED_PENALTY : 0
  const spent = current < 0 ? current : 0
  return status({
    pool: 'FP',
    current,
    max,
    level: fpLevel(current, max),
    effect: fpEffect(current, max),
    penalty: Math.min(tired, spent),
    isUnconscious: current < -max,
  })
}

export function isFatigued(fpStatus: PoolStatus): boolean {
  require(fpStatus.pool === 'FP', () => `fatigue is an FP state; ${fpStatus.pool} has its own 1/3 row and halves nothing (p. 42)`)
  return fatigued(fpStatus.current, fpStatus.max)
}

export const halvedForFatigue = (value: number, fpStatus: PoolStatus) =>
  isFatigued(fpStatus) ? -floorDiv(-value, 2) : value

export function spi(current: number, max: number): PoolStatus {
  require(max >= 1, () => 'SPI max must be at least 1; a mortal has no SPI pool to report on (p. 41)')
  let level: string | null
  if (current < 0) level = 'Below 0 SPI'
  else if (current === 0) level = '0 SPI'
  else if (current <= floorDiv(max, THIRD)) level = '<= 1/3 Max SPI'
  else level = null
  return status({
    pool: 'SPI',
    current,
    max,
    level: level ?? 'Above 1/3 Max SPI',
    effect: level !== null ? row('SPI', level).effect : HEALTHY_EFFECT,
    penalty: current <= floorDiv(max, THIRD) ? ATTRITION_PENALTY : 0,
    scope: 'SPI_ROLLS',
    corGained: current < 0 ? -current : 0,
    abilitiesOffline: current <= 0,
  })
}

function fpLevel(current: number, max: number): string {
  if (current < -max) return 'below -1×Max FP'
  if (current < 0) return 'below 0 FP'
  if (fatigued(current, max)) return '1/3 FP or below'
  return 'FP to 1'
}

function fpEffect(current: number, max: number): string {
  if (current < -max) return 'Unconscious (p. 41).'
  if (current < 0) return '-1 to all rolls per 1 FP below 0 (p. 41). Every point spent below 0 also costs 1 HP (p. 42).'
  if (fatigued(current, max)) return '-1 to all rolls; Move and Dodge halved (p. 42).'
  return HEALTHY_EFFECT
}

function row(pool: Pool, level: string): PoolThreshold {
  return thresholds(pool).find((it) => it.level === level) ?? error(`pool_thresholds.json prints no ${pool} row "${level}"`)
}

const fatigued = (current: number, max: number) => current <= floorDiv(max, THIRD)

function requireMax(max: number, pool: Pool) {
  require(max >= ScoreFloors.MIN_POOL_MAX, () => `${pool} max must be at least ${ScoreFloors.MIN_POOL_MAX}; was ${max}`)
}
