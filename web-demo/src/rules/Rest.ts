import { floorDiv, IllegalArgumentException } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { RestPeriod } from './content/schema.gen'

export interface RestRecovery {
  fp: number
  spi: number
  allFp: boolean
}

const MINUTES_PER_FP = 10

export const rests = (): RestPeriod[] => ContentLoader.restFile().rests

export function named(name: string): RestPeriod {
  const r = rests().find((it) => it.name === name)
  if (r === undefined) throw new IllegalArgumentException(`no rest named '${name}'`)
  return r
}

export function recovery(rest: RestPeriod, cogitated: boolean, maxFp: number): RestRecovery {
  const spi = ContentLoader.restFile().spi
  const fp = rest.allFp ? maxFp : floorDiv(rest.hours * 60, MINUTES_PER_FP)
  return { fp, spi: rest.hours * (cogitated ? spi.cogitationPerHour : spi.perHour), allFp: rest.allFp }
}
