import type { DiceExpr } from './model'

/** Randomness as a dependency, so every roll in the test suite is reproducible. */
export interface RandomSource {
  /** A value in 1..sides. */
  roll(sides: number): number
  /** The faces of one throw, reported after taking them: the roll log listens. */
  thrown?(faces: number[], target: number | null): void
}

/** 3d6 for a table, reported with no target. */
export const threeDSix = (source: RandomSource) => throwThree(source, null)

/** Three d6, reported with the target: SuccessRoll's throw and the tables'. */
export function throwThree(source: RandomSource, target: number | null): number {
  const faces = [source.roll(6), source.roll(6), source.roll(6)]
  source.thrown?.(faces, target)
  return faces[0] + faces[1] + faces[2]
}

/** `DiceExpr.roll`. */
export function rollDice(d: DiceExpr, source: RandomSource): number {
  let sum = 0
  for (let i = 0; i < d.count; i++) sum += source.roll(d.sides)
  return sum + d.modifier
}

/** The browser's dice: crypto-strength, uniform over 1..sides. */
export const browserDice: RandomSource = {
  roll(sides: number): number {
    const limit = Math.floor(0x100000000 / sides) * sides
    const buf = new Uint32Array(1)
    for (;;) {
      crypto.getRandomValues(buf)
      if (buf[0] < limit) return (buf[0] % sides) + 1
    }
  },
}
