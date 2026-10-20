import type { RandomSource } from '../rules/RandomSource'

/** `parity/Main.kt`'s ScriptedDice: faces from a list, each throw logged. */
export class ScriptedDice {
  private next = 0
  readonly thrown: [number[], number | null][] = []

  constructor(private readonly faces: number[]) {}

  roll(sides: number): number {
    if (this.next >= this.faces.length) throw new Error(`script ran out after ${this.faces.length} faces`)
    const face = this.faces[this.next++]
    if (face < 1 || face > sides) throw new Error(`face ${face} is not on a d${sides}`)
    return face
  }

  thrown_(faces: number[], target: number | null) {
    this.thrown.push([faces, target])
  }
}

/** `CombatVectors.kt`'s `scripted`: the value, and every throw reported. */
export function scripted(faces: number[], call: (d: RandomSource) => unknown) {
  const dice = new ScriptedDice(faces)
  const source: RandomSource = { roll: (s) => dice.roll(s), thrown: (f, t) => dice.thrown_(f, t) }
  return { out: call(source), thrown: dice.thrown }
}
