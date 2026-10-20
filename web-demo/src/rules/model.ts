// core/model, field for field. A Kotlin data class is a plain object here and
// its constructor a function of the same name that runs the same `init`
// checks; a sealed subclass carries `type`, its Kotlin simple name.

import { require } from './kt'

export type Attr = 'ST' | 'DX' | 'IQ' | 'HT'
export const ATTRS: readonly Attr[] = ['ST', 'DX', 'IQ', 'HT']

export const BASE_ATTRIBUTE = 9
export const MAX_ATTRIBUTE = 30

export interface Attributes {
  st: number
  dx: number
  iq: number
  ht: number
}

export function Attributes(p: Partial<Attributes> = {}): Attributes {
  const a = { st: p.st ?? BASE_ATTRIBUTE, dx: p.dx ?? BASE_ATTRIBUTE, iq: p.iq ?? BASE_ATTRIBUTE, ht: p.ht ?? BASE_ATTRIBUTE }
  for (const [label, v] of [['ST', a.st], ['DX', a.dx], ['IQ', a.iq], ['HT', a.ht]] as const) {
    require(v >= BASE_ATTRIBUTE && v <= MAX_ATTRIBUTE, () =>
      `${label} must be between ${BASE_ATTRIBUTE} and ${MAX_ATTRIBUTE} (the book gives no refund ` +
      `for buying below ${BASE_ATTRIBUTE}, and nothing legal passes ${MAX_ATTRIBUTE}); was ${v}`,
    )
  }
  return a
}

export function attr(a: Attributes, which: Attr): number {
  switch (which) {
    case 'ST': return a.st
    case 'DX': return a.dx
    case 'IQ': return a.iq
    case 'HT': return a.ht
  }
}

export const LOWEST_SEQUENCE = 6
export const HIGHEST_SEQUENCE = 9

export interface BeyonderState {
  pathway: string
  sequence: number
  digestion: number
  drawback: string | null
}

export function BeyonderState(p: { pathway: string; sequence: number; digestion?: number; drawback?: string | null }): BeyonderState {
  const s = { pathway: p.pathway, sequence: p.sequence, digestion: p.digestion ?? 0, drawback: p.drawback ?? null }
  require(s.pathway.trim() !== '', () => 'a Beyonder walks a named pathway')
  require(s.sequence >= LOWEST_SEQUENCE && s.sequence <= HIGHEST_SEQUENCE, () =>
    `sequence must be between ${LOWEST_SEQUENCE} and ${HIGHEST_SEQUENCE} ` +
    `(the extracted content covers no others); was ${s.sequence}`,
  )
  require(s.digestion >= 0 && s.digestion <= 100, () => `digestion is a percentage; was ${s.digestion}`)
  return s
}

export const Beyonder = {
  advanced(s: BeyonderState): BeyonderState | null {
    return s.sequence <= LOWEST_SEQUENCE ? null : BeyonderState({ ...s, sequence: s.sequence - 1, digestion: 0 })
  },
  digested(s: BeyonderState, points: number): BeyonderState {
    return BeyonderState({ ...s, digestion: Math.min(Math.max(s.digestion + points, 0), 100) })
  },
  isFullyDigested(s: BeyonderState): boolean {
    return s.digestion === 100
  },
  /** `BeyonderState?.canDrink`: a mortal drinks Sequence 9; a Beyonder the next one. */
  canDrink(s: BeyonderState | null, next: BeyonderState): boolean {
    if (s === null) return next.sequence === HIGHEST_SEQUENCE
    const a = Beyonder.advanced(s)
    return a !== null && sameBeyonder(next, a)
  },
}

function sameBeyonder(a: BeyonderState, b: BeyonderState) {
  return a.pathway === b.pathway && a.sequence === b.sequence && a.digestion === b.digestion && a.drawback === b.drawback
}

export interface BoonState {
  pathway: string
  sequence: number
}

export type ControllingAttribute =
  | { type: 'Buyable'; attr: Attr }
  | { type: 'Will' }
  | { type: 'Per' }
  | { type: 'Spi' }

export const Buyable = (a: Attr): ControllingAttribute => ({ type: 'Buyable', attr: a })
export const WILL: ControllingAttribute = { type: 'Will' }
export const PER: ControllingAttribute = { type: 'Per' }
export const SPI: ControllingAttribute = { type: 'Spi' }

export function attrCode(a: ControllingAttribute): string {
  switch (a.type) {
    case 'Buyable': return a.attr
    case 'Will': return 'Will'
    case 'Per': return 'Per'
    case 'Spi': return 'SPI'
  }
}

export function controllingFromCode(code: string): ControllingAttribute {
  switch (code.trim().toUpperCase()) {
    case 'ST': return Buyable('ST')
    case 'DX': return Buyable('DX')
    case 'IQ': return Buyable('IQ')
    case 'HT': return Buyable('HT')
    case 'WILL': return WILL
    case 'PER': return PER
    case 'SPI': return SPI
  }
  require(false, () => `unknown controlling attribute code '${code}'`)
}

export type Difficulty = 'EASY' | 'AVERAGE' | 'HARD' | 'VERY_HARD'
export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'AVERAGE', 'HARD', 'VERY_HARD']

export const DIFFICULTY: Record<Difficulty, { code: string; firstRungOffset: number; defaultPenalty: number | null }> = {
  EASY: { code: 'E', firstRungOffset: 0, defaultPenalty: -4 },
  AVERAGE: { code: 'A', firstRungOffset: -1, defaultPenalty: -5 },
  HARD: { code: 'H', firstRungOffset: -2, defaultPenalty: -6 },
  VERY_HARD: { code: 'VH', firstRungOffset: -3, defaultPenalty: null },
}

export function difficultyFromCode(code: string): Difficulty {
  const found = DIFFICULTIES.find((d) => DIFFICULTY[d].code === code.trim().toUpperCase())
  require(found !== undefined, () => `unknown difficulty code '${code}'`)
  return found
}

export interface DiceExpr {
  count: number
  sides: number
  modifier: number
}

export function DiceExpr(count: number, sides = 6, modifier = 0): DiceExpr {
  require(count > 0, () => `die count must be positive, was ${count}`)
  require(sides > 0, () => `die size must be positive, was ${sides}`)
  return { count, sides, modifier }
}

export const Dice = {
  render(d: DiceExpr): string {
    let s = `${d.count}d`
    if (d.sides !== 6) s += d.sides
    if (d.modifier > 0) s += `+${d.modifier}`
    if (d.modifier < 0) s += d.modifier
    return s
  },
  parse(raw: string): DiceExpr {
    const m = /^(\d+)d(\d+)?([+-]\d+)?$/.exec(raw.trim())
    require(m !== null, () => `not a dice expression: '${raw}'`)
    return DiceExpr(Number(m[1]), Number(m[2] || '6'), Number(m[3] || '0'))
  },
}

export const MAX_SPI = 1000

export interface Character {
  name: string
  attributes: Attributes
  secondaryDelta: SecondaryDelta
  spi: number
  beyonder: BeyonderState | null
  boon: BoonState | null
  traits: Trait[]
  skills: PurchasedSkill[]
  weapons: Weapon[]
}

export function Character(p: Partial<Character> & { name: string }): Character {
  const c: Character = {
    name: p.name,
    attributes: p.attributes ?? Attributes(),
    secondaryDelta: p.secondaryDelta ?? SecondaryDelta(),
    spi: p.spi ?? 0,
    beyonder: p.beyonder ?? null,
    boon: p.boon ?? null,
    traits: p.traits ?? [],
    skills: p.skills ?? [],
    weapons: p.weapons ?? [],
  }
  const seen = new Map<string, number>()
  for (const s of c.skills) {
    const k = skillKeyId(s.key)
    seen.set(k, (seen.get(k) ?? 0) + 1)
  }
  const duplicates = c.skills.filter((s, i) => (seen.get(skillKeyId(s.key)) ?? 0) > 1 &&
    c.skills.findIndex((x) => skillKeyId(x.key) === skillKeyId(s.key)) === i)
  require(duplicates.length === 0, () =>
    `duplicate skill purchase(s) for ${c.name}: ${duplicates.map((s) => skillDisplay(s.key)).join(', ')} ` +
    '-- combine each into a single PurchasedSkill instead of buying it twice',
  )
  require(c.spi >= 0 && c.spi <= MAX_SPI, () =>
    `spi must be between 0 and ${MAX_SPI} (mortals start at 0; nothing legal reaches ${MAX_SPI}); was ${c.spi}`,
  )
  return c
}

export type SourceKind = 'POTION' | 'ADVANTAGE' | 'DISADVANTAGE' | 'EQUIPMENT' | 'SITUATIONAL' | 'STATE'

export interface StackGroup {
  name: string
  cap: number
}

export function StackGroup(name: string, cap: number): StackGroup {
  require(cap >= 0, () => `StackGroup '${name}' cap must not be negative; was ${cap}`)
  return { name, cap }
}

export type ModifierSource =
  | { type: 'Potion'; pathway: string; sequence: number }
  | { type: 'FromTrait'; name: string; advantage: boolean }
  | { type: 'Equipment'; name: string }
  | { type: 'Situational'; description: string }
  | { type: 'StateThreshold'; tracker: string }

export function sourceKind(s: ModifierSource): SourceKind {
  switch (s.type) {
    case 'Potion': return 'POTION'
    case 'FromTrait': return s.advantage ? 'ADVANTAGE' : 'DISADVANTAGE'
    case 'Equipment': return 'EQUIPMENT'
    case 'Situational': return 'SITUATIONAL'
    case 'StateThreshold': return 'STATE'
  }
}

export type Pool = 'SPI' | 'FP' | 'HP'

export type ModTarget =
  | { type: 'Attribute'; a: Attr }
  | { type: 'Skill'; key: SkillKey }
  | { type: 'RollClass'; tag: string }
  | { type: 'AllRolls' }
  | { type: 'Damage'; slot: string }
  | { type: 'Cost'; pool: Pool }
  | { type: 'RequiredMargin' }

export type ModEffect =
  | { type: 'Flat'; n: number }
  | { type: 'Scale'; num: number; den: number; roundUp: boolean }
  | { type: 'SetTo'; n: number }
  | { type: 'Cancel'; upTo: number; of: SourceKind }
  | { type: 'Decay'; from: number; perTurn: number }
  | { type: 'Forbid' }
  | { type: 'RequireCrit' }

export const Flat = (n: number): ModEffect => ({ type: 'Flat', n })
export function Scale(num: number, den: number, roundUp = false): ModEffect {
  require(den !== 0, () => `Scale.den must not be zero (num=${num})`)
  return { type: 'Scale', num, den, roundUp }
}
export const SetTo = (n: number): ModEffect => ({ type: 'SetTo', n })
export function Cancel(upTo: number, of: SourceKind): ModEffect {
  require(upTo >= 0, () => `Cancel.upTo must not be negative (was ${upTo}); a negative budget would act as an extra penalty`)
  return { type: 'Cancel', upTo, of }
}

export type Layer =
  | 'BASE' | 'POTION' | 'ADVANTAGE' | 'EQUIPMENT' | 'SITUATIONAL'
  | 'STATE_THRESHOLD' | 'COUNTERMEASURE' | 'COUNTER_COUNTER' | 'BURN'
export const LAYERS: readonly Layer[] = [
  'BASE', 'POTION', 'ADVANTAGE', 'EQUIPMENT', 'SITUATIONAL',
  'STATE_THRESHOLD', 'COUNTERMEASURE', 'COUNTER_COUNTER', 'BURN',
]

export type Scope =
  | { type: 'Self' }
  | { type: 'Ally'; radiusYards: number }
  | { type: 'Enemy' }
  | { type: 'Area'; radiusYards: number }

export type Duration =
  | { type: 'Permanent' }
  | { type: 'Turns'; n: number }
  | { type: 'Minutes'; n: number }
  | { type: 'Scene' }

export type Condition =
  | { type: 'Always' }
  | { type: 'AtNight' }
  | { type: 'InDarkness' }
  | { type: 'AgainstLowerSequence'; delta: number }
  | { type: 'All'; of: Condition[] }
  | { type: 'Any'; of: Condition[] }

export interface Modifier {
  id: string
  source: ModifierSource
  target: ModTarget
  effect: ModEffect
  layer: Layer
  group: StackGroup | null
  supersedes: string | null
  scope: Scope
  duration: Duration
  startTurn: number | null
  condition: Condition
}

export function Modifier(p: Pick<Modifier, 'id' | 'source' | 'target' | 'effect' | 'layer'> & Partial<Modifier>): Modifier {
  return {
    id: p.id,
    source: p.source,
    target: p.target,
    effect: p.effect,
    layer: p.layer,
    group: p.group ?? null,
    supersedes: p.supersedes ?? null,
    scope: p.scope ?? { type: 'Self' },
    duration: p.duration ?? { type: 'Permanent' },
    startTurn: p.startTurn ?? null,
    condition: p.condition ?? { type: 'Always' },
  }
}

export interface PurchasedSkill {
  key: SkillKey
  points: number
  studied: number
}

export function PurchasedSkill(key: SkillKey, points: number, studied = 0): PurchasedSkill {
  require(points > 0, () => 'a purchased skill needs at least 1 point')
  require(studied >= 0 && studied <= points, () => `studied points are a share of the skill's; ${studied} of ${points}`)
  return { key, points, studied }
}

export type Outcome = 'CRITICAL_SUCCESS' | 'SUCCESS' | 'FAILURE' | 'CRITICAL_FAILURE'
export const isSuccess = (o: Outcome) => o === 'CRITICAL_SUCCESS' || o === 'SUCCESS'

export interface RollResult {
  total: number
  target: number
  margin: number
  outcome: Outcome
}

export const MAX_SECONDARY_DELTA_MAGNITUDE = 100

export interface SecondaryDelta {
  hp: number
  fp: number
  will: number
  per: number
  basicSpeedQuarters: number
  basicMove: number
}

export function SecondaryDelta(p: Partial<SecondaryDelta> = {}): SecondaryDelta {
  const d = {
    hp: p.hp ?? 0, fp: p.fp ?? 0, will: p.will ?? 0, per: p.per ?? 0,
    basicSpeedQuarters: p.basicSpeedQuarters ?? 0, basicMove: p.basicMove ?? 0,
  }
  for (const [label, v] of Object.entries(d)) {
    require(v >= -MAX_SECONDARY_DELTA_MAGNITUDE && v <= MAX_SECONDARY_DELTA_MAGNITUDE, () =>
      `SecondaryDelta.${label} magnitude too large: ${v} (must be within +-${MAX_SECONDARY_DELTA_MAGNITUDE})`,
    )
  }
  return d
}

export interface SkillKey {
  name: string
  specialization: string | null
  attr: ControllingAttribute
  difficulty: Difficulty
}

export function SkillKey(name: string, specialization: string | null, a: ControllingAttribute, difficulty: Difficulty): SkillKey {
  return { name, specialization, attr: a, difficulty }
}

export function skillDisplay(k: SkillKey): string {
  return `${k.name}${k.specialization !== null ? ` (${k.specialization})` : ''}/${attrCode(k.attr)}`
}

/** A SkillKey's identity as a string: data-class equality, for Map keys. */
export function skillKeyId(k: SkillKey): string {
  const a = k.attr.type === 'Buyable' ? k.attr.attr : k.attr.type
  return `${k.name}\u0000${k.specialization ?? '\u0001'}\u0000${a}\u0000${k.difficulty}`
}

export function sameSkillKey(a: SkillKey, b: SkillKey): boolean {
  return skillKeyId(a) === skillKeyId(b)
}

export type TraitKind = 'ADVANTAGE' | 'DISADVANTAGE' | 'QUIRK'

export interface Trait {
  name: string
  cost: number
  kind: TraitKind
}

export function Trait(name: string, cost: number, kind: TraitKind): Trait {
  require(cost >= 0, () => `trait cost is a magnitude; '${name}' was ${cost}`)
  return { name, cost, kind }
}

export interface Weapon {
  name: string
  skill: SkillKey
  parryMod: number | null
}
