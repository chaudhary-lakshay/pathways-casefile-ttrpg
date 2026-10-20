import { MAX_SECONDARY_DELTA_MAGNITUDE, SecondaryDelta, type Attributes } from './model'
import { coerceIn, floorDiv, format2 } from './kt'

export interface Secondaries {
  maxHp: number
  maxFp: number
  will: number
  per: number
  basicSpeedQuarters: number
  basicMove: number
  dodge: number
}

/** `Secondaries.basicSpeed`: "%.2f" of the quarters. */
export const basicSpeed = (s: Secondaries): string => format2(s.basicSpeedQuarters / 4.0)

const COST_HP = 2
const COST_FP = 3
const COST_WILL = 10
const COST_PER = 10
const COST_SPEED_QUARTER = 5
const COST_MOVE = 5

export function of(a: Attributes, d: SecondaryDelta): Secondaries {
  const speedQuarters = a.ht + a.dx + d.basicSpeedQuarters
  const wholeSpeed = floorDiv(speedQuarters, 4)
  return {
    maxHp: a.st + d.hp,
    maxFp: a.ht + d.fp,
    will: a.iq + d.will,
    per: a.iq + d.per,
    basicSpeedQuarters: speedQuarters,
    basicMove: wholeSpeed + d.basicMove,
    dodge: wholeSpeed + 3,
  }
}

export function costOf(d: SecondaryDelta): number {
  return d.hp * COST_HP + d.fp * COST_FP + d.will * COST_WILL + d.per * COST_PER +
    d.basicSpeedQuarters * COST_SPEED_QUARTER + d.basicMove * COST_MOVE
}

export function stepsOf(d: SecondaryDelta, key: string): number {
  switch (key) {
    case 'hp': return d.hp
    case 'fp': return d.fp
    case 'will': return d.will
    case 'per': return d.per
    case 'basicSpeed': return d.basicSpeedQuarters
    case 'basicMove': return d.basicMove
    default: return 0
  }
}

export function withSteps(d: SecondaryDelta, key: string, steps: number): SecondaryDelta {
  const n = coerceIn(steps, -MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE)
  switch (key) {
    case 'hp': return SecondaryDelta({ ...d, hp: n })
    case 'fp': return SecondaryDelta({ ...d, fp: n })
    case 'will': return SecondaryDelta({ ...d, will: n })
    case 'per': return SecondaryDelta({ ...d, per: n })
    case 'basicSpeed': return SecondaryDelta({ ...d, basicSpeedQuarters: n })
    case 'basicMove': return SecondaryDelta({ ...d, basicMove: n })
    default: return d
  }
}

export function plusSteps(base: SecondaryDelta, steps: Map<string, number>): SecondaryDelta {
  let acc = base
  for (const [key, n] of steps) {
    acc = withSteps(acc, key, stepsOf(acc, key) + coerceIn(n, -MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE))
  }
  return acc
}

export function valueLabelOf(s: Secondaries, key: string): string | null {
  switch (key) {
    case 'hp': return String(s.maxHp)
    case 'fp': return String(s.maxFp)
    case 'will': return String(s.will)
    case 'per': return String(s.per)
    case 'basicSpeed': return basicSpeed(s)
    case 'basicMove': return String(s.basicMove)
    default: return null
  }
}
