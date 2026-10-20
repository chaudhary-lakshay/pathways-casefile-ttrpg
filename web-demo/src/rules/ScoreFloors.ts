import { SecondaryDelta, type Trait } from './model'
import { floorDiv } from './kt'
import * as Corruption from './Corruption'
import type { Secondaries } from './SecondaryCharacteristics'

export const MIN_POOL_MAX = 1
export const MIN_COR_CEILING = 1
export const MIN_PER = 0
export const MIN_MOVE = 0
export const MIN_SPEED_QUARTERS = 0

export const holdsCeiling = (ceiling: number) => ceiling >= MIN_COR_CEILING

export function shortfall(s: Secondaries, traits: Trait[]): SecondaryDelta {
  const speed = Math.max(MIN_SPEED_QUARTERS - s.basicSpeedQuarters, 0)
  const moveReturned = floorDiv(s.basicSpeedQuarters + speed, 4) - floorDiv(s.basicSpeedQuarters, 4)
  return SecondaryDelta({
    hp: Math.max(MIN_POOL_MAX - s.maxHp, 0),
    fp: Math.max(MIN_POOL_MAX - s.maxFp, 0),
    will: Math.max(MIN_COR_CEILING - Corruption.maxFor(s.will, traits), 0),
    per: Math.max(MIN_PER - s.per, 0),
    basicSpeedQuarters: speed,
    basicMove: Math.max(MIN_MOVE - (s.basicMove + moveReturned), 0),
  })
}
