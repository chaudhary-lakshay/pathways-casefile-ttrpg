import { error } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { SuccessBand } from './content/schema.gen'
import type { Outcome } from './model'

export type SuccessDegree = 'PLAIN' | 'GOOD' | 'EXCELLENT'

const PLAIN_ROW = 'Success'
const GOOD_ROW = 'Good Success'
const EXCELLENT_ROW = 'Excellent Success'

export const bands = (): SuccessBand[] => ContentLoader.successLadder()

export function of(outcome: Outcome | null, margin: number): SuccessDegree | null {
  if (outcome !== 'SUCCESS') return null
  const reached = bands().filter((it) => it.comparison === '<=' && margin >= -(it.offset ?? 0))
  return degreeNamed(reached.at(-1)?.result ?? PLAIN_ROW)
}

function degreeNamed(result: string): SuccessDegree {
  switch (result) {
    case PLAIN_ROW: return 'PLAIN'
    case GOOD_ROW: return 'GOOD'
    case EXCELLENT_ROW: return 'EXCELLENT'
    default: return error(`the ladder printed a degree this app has no case for: ${result}`)
  }
}

export function marginFor(degree: SuccessDegree): number {
  const row = degree === 'PLAIN' ? PLAIN_ROW : degree === 'GOOD' ? GOOD_ROW : EXCELLENT_ROW
  const band = bands().find((it) => it.result === row) ?? error(`the ladder no longer prints a ${row} row`)
  return -(band.offset ?? 0)
}
