// app/.../RollLog.kt, SheetScene.kt and RoundState.kt. Compose's
// mutableStateOf becomes a Cell: a value with subscribe, which React reads
// through useSyncExternalStore (ui/hooks.ts).
import type { DefenseBoost, DefenseRefusal } from '../rules/ActiveDefenses'
import type { RandomSource } from '../rules/RandomSource'
import type { SkillKey } from '../rules/model'
import type { SkillRollOutcome } from '../rules/SkillRoll'

type Listener = () => void

export class Cell<T> {
  private readonly listeners = new Set<Listener>()
  constructor(public value: T) {}
  set = (value: T) => {
    if (Object.is(value, this.value)) return
    this.value = value
    this.listeners.forEach((l) => l())
  }
  subscribe = (l: Listener) => {
    this.listeners.add(l)
    return () => this.listeners.delete(l)
  }
  get = () => this.value
}

export interface LoggedThrow {
  character: string
  label: string
  faces: number[]
  target: number | null
}

/** `LoggedThrow.line()`. */
export const logLine = (t: LoggedThrow) =>
  `${t.faces.join(' + ')} = ${t.faces.reduce((a, b) => a + b, 0)}${t.target !== null ? ` vs ${t.target}` : ''}`

export class RollLog {
  private entries: LoggedThrow[] = []
  private readonly listeners = new Set<Listener>()
  version = 0

  constructor(private readonly source: RandomSource, private readonly cap = 100) {}

  private changed() {
    this.version++
    this.listeners.forEach((l) => l())
  }

  subscribe = (l: Listener) => {
    this.listeners.add(l)
    return () => this.listeners.delete(l)
  }
  getVersion = () => this.version

  dice(character: string, label: string): RandomSource {
    return {
      roll: (sides) => this.source.roll(sides),
      thrown: (faces, target) => {
        const next = [{ character, label, faces, target }, ...this.entries]
        if (next.filter((it) => it.character === character).length > this.cap) {
          const last = next.map((it) => it.character).lastIndexOf(character)
          next.splice(last, 1)
        }
        this.entries = next
        this.changed()
      },
    }
  }

  of = (character: string) => this.entries.filter((it) => it.character === character)

  forget(character: string) {
    this.entries = this.entries.filter((it) => it.character !== character)
    this.changed()
  }
}

/** `CharacterDice`: the dice a sheet's rolls take, labelled for the log. */
export type CharacterDice = (label: string) => RandomSource

export interface AcrobaticSkill {
  key: SkillKey
  level: number
  untrainedSource: string | null
}
export const acrobaticLabel = (s: AcrobaticSkill) =>
  s.untrainedSource !== null ? `${s.key.name} (untrained, ${s.untrainedSource})` : s.key.name

export interface RoundState {
  declared: Set<DefenseRefusal>
  acrobatic: SkillRollOutcome | null
  dived: boolean
  retreated: boolean
  holdingPin: boolean
  priorParries: number
  boost: [DefenseBoost, string] | null
}
export const RoundState = (p: Partial<RoundState> = {}): RoundState => ({
  declared: new Set(),
  acrobatic: null,
  dived: false,
  retreated: false,
  holdingPin: false,
  priorParries: 0,
  boost: null,
  ...p,
})
export function keptFor(r: RoundState, skill: AcrobaticSkill | null): RoundState {
  const roll = r.acrobatic
  if (roll === null) return r
  const same = skill !== null && roll.label === acrobaticLabel(skill) && roll.baseTarget === skill.level
  return same ? r : { ...r, acrobatic: null }
}
export const parried = (r: RoundState): RoundState => ({ ...r, priorParries: r.priorParries + 1 })
/** `this != RoundState()`, field by field: canonical() cannot see inside a Set. */
export const isSpent = (r: RoundState) =>
  r.declared.size > 0 || r.acrobatic !== null || r.dived || r.retreated || r.holdingPin || r.priorParries > 0 || r.boost !== null

export type SheetTab = 'VITALS' | 'SKILLS' | 'TRAITS'

export class SheetScene {
  readonly tab = new Cell<SheetTab>('SKILLS')
  readonly shocked = new Cell(false)
  readonly declaredStates = new Cell<Set<string>>(new Set())
  readonly round = new Cell<RoundState>(RoundState())
  readonly stunned = new Cell(false)
  readonly warmed = new Cell<Set<string>>(new Set())
  readonly carried = new Cell<number | null>(null)
  readonly spiritPending = new Cell(0)

  clearingSpiritOn(source: RandomSource): RandomSource {
    return {
      roll: (sides) => source.roll(sides),
      thrown: (faces, target) => {
        source.thrown?.(faces, target)
        if (target !== null) this.spiritPending.set(0)
      },
    }
  }
}

export class Scenes {
  private readonly scenes = new Map<string, SheetScene>()
  of(character: string): SheetScene {
    let s = this.scenes.get(character)
    if (!s) this.scenes.set(character, (s = new SheetScene()))
    return s
  }
  forget(character: string) {
    this.scenes.delete(character)
  }
}
