import { DIFFICULTY, type Difficulty } from './model'
import { floorDiv, require } from './kt'

const OPENING = [1, 2, 4, 8, 16]
const FLAT = 16
const LAST = OPENING[OPENING.length - 1]
const LAST_INDEX = OPENING.length - 1

function rungOf(points: number): number {
  if (points < LAST) {
    for (let i = OPENING.length - 1; i >= 0; i--) if (OPENING[i] <= points) return i
    return -1
  }
  return LAST_INDEX + floorDiv(points - LAST, FLAT)
}

function pointsAt(rung: number): number {
  return rung <= LAST_INDEX ? OPENING[rung] : LAST + (rung - LAST_INDEX) * FLAT
}

export function levelFor(points: number, difficulty: Difficulty, attributeLevel: number): number {
  require(points > 0, () => 'spend at least 1 point, or ask SkillDefaults for an untrained level')
  return attributeLevel + DIFFICULTY[difficulty].firstRungOffset + rungOf(points)
}

export function defaultFor(difficulty: Difficulty, attributeLevel: number): number | null {
  const p = DIFFICULTY[difficulty].defaultPenalty
  return p === null ? null : attributeLevel + p
}

export function nextRungPoints(points: number): number {
  require(points >= 0, () => `points spent cannot be negative; was ${points}`)
  return pointsAt(rungOf(points) + 1)
}

export function stepped(bought: number, studied: number, up: boolean): number {
  require(bought > 0 && studied >= 0, () => `a bought skill holds at least 1 point; was ${bought} + ${studied}`)
  const total = bought + studied
  if (up) return nextRungPoints(total) - studied
  const rung = rungOf(total)
  const below = rung === 0 ? 0 : pointsAt(rung - 1)
  const sold = below - studied
  if (sold >= 1) return sold
  if (bought > 1) return 1
  return 0
}
