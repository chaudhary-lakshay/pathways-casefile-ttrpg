import { ATTRS, Attributes, SecondaryDelta, SkillKey, type Attr, type Character, type ControllingAttribute, type Difficulty } from './model'
import { require, sortedBy, substringAfter, substringBefore, removeSuffix, error, ValueMap } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { SequenceBlock, StatGain } from './content/schema.gen'
import * as Blessings from './Blessings'
import * as Derive from './Derive'
import * as NonStandardPathways from './NonStandardPathways'
import * as SecondaryCharacteristics from './SecondaryCharacteristics'
import * as SkillDefaults from './SkillDefaults'
import * as SkillLadder from './SkillLadder'
import * as TraitEffects from './TraitEffects'
import type { BudgetReport } from './PointBudget'
import type { Secondaries } from './SecondaryCharacteristics'

export interface PotionGrants {
  attributes: Map<Attr, number>
  spi: number
  secondary: SecondaryDelta
  skillLevels: ValueMap<SkillKey, number>
  skillBonuses: ValueMap<SkillKey, number>
}

const grantsOf = (p: Partial<PotionGrants> = {}): PotionGrants => ({
  attributes: p.attributes ?? new Map(),
  spi: p.spi ?? 0,
  secondary: p.secondary ?? SecondaryDelta(),
  skillLevels: p.skillLevels ?? new ValueMap(),
  skillBonuses: p.skillBonuses ?? new ValueMap(),
})

export interface DigestedCharacter {
  base: Character
  pathway: string
  sequence: number
  grants: PotionGrants
  effectiveAttributes: Attributes
  effectiveSpi: number
  secondaries: Secondaries
  skillLevels: ValueMap<SkillKey, number>
  budget: BudgetReport
}

export const UNPARSED = new Map([
  ['ability', "priced Beyonder powers, 237 SPI costs in 15 qualifier shapes; unbuilt work, not a refusal -- see this object's KDoc"],
  ['reinforcedAbilities', 'the same, for the blocks that reinforce an earlier Sequence'],
])

export const STARTING_SEQUENCE = 9

/** `digest(character, block)`. */
export const digestBlock = (character: Character, block: SequenceBlock) => digest(character, [block])

export function of(character: Character, blocks: SequenceBlock[]): DigestedCharacter | null {
  if (character.beyonder !== null) return digestThrough(character, character.beyonder.pathway, character.beyonder.sequence, blocks)
  const chain = boonChain(character)
  return chain.length > 0 ? digest(character, chain) : null
}

const boonChain = (character: Character): SequenceBlock[] =>
  character.boon !== null ? NonStandardPathways.chainFor(character.boon) : []

export function digestThrough(character: Character, pathway: string, sequence: number, blocks: SequenceBlock[]): DigestedCharacter {
  const chain = sortedBy(blocks.filter((it) => it.pathway === pathway && it.sequence >= sequence), (it) => it.sequence, true)
  require(chain.length > 0, () => `no Sequence ${sequence} or earlier on the ${pathway} Pathway`)
  const last = chain[chain.length - 1]
  require(last.sequence === sequence, () =>
    `the ${pathway} Pathway has no Sequence ${sequence} block; digesting through it would ` +
    `silently report Sequence ${last.sequence} instead`)
  require(chain[0].sequence === STARTING_SEQUENCE, () =>
    `the ${pathway} Pathway chain starts at Sequence ${chain[0].sequence}, ` +
    `not ${STARTING_SEQUENCE} -- a Beyonder cannot skip the potion they began with`)
  return digest(character, [...chain, ...boonChain(character)], last)
}

function digest(character: Character, chain: SequenceBlock[], named: SequenceBlock = chain[chain.length - 1]): DigestedCharacter {
  const derived = Derive.of(character)
  let grants = grantsOf()
  const base = Blessings.played(character.attributes, character.traits)
  for (const block of chain) {
    grants = mergeStats(grants, statGrants(block.stats))
    for (const grant of block.skills) {
      const key = skillKey(grant.name, grant.attr, grant.difficulty)
      const held = grants.skillBonuses.keys().find((it) => isSameSkill(it, key)) ?? key
      const bonuses = new ValueMap(grants.skillBonuses.entries())
      bonuses.set(held, (grants.skillBonuses.get(held) ?? 0) + grant.bonus)
      grants = { ...grants, skillBonuses: bonuses }
    }
  }
  const effectiveAttributes = Attributes({
    st: base.st + (grants.attributes.get('ST') ?? 0),
    dx: base.dx + (grants.attributes.get('DX') ?? 0),
    iq: base.iq + (grants.attributes.get('IQ') ?? 0),
    ht: base.ht + (grants.attributes.get('HT') ?? 0),
  })
  const effectiveSpi = character.spi + grants.spi
  const secondaries = SecondaryCharacteristics.of(effectiveAttributes, plus(character.secondaryDelta, grants.secondary))
  const attributeLevel = (ca: ControllingAttribute) => Derive.controllingLevel(ca, effectiveAttributes, secondaries, effectiveSpi)
  const purchased = purchasedLevels(character, effectiveAttributes, secondaries, effectiveSpi)
  const defaultBonus = TraitEffects.defaultBonus(character.traits)
  const superseded = new ValueMap<SkillKey, true>()
  const levels = new ValueMap<SkillKey, number>()
  for (const [key, bonus] of grants.skillBonuses.entries()) {
    const bought = [...purchased.entries()].find(([k]) => isSameSkill(k, key))
    if (bought !== undefined) superseded.set(bought[0], true)
    const from = bought?.[1] ?? SkillDefaults.unboughtLevel(key, attributeLevel, defaultBonus, character.traits)
    if (from === null) continue
    levels.set(bought?.[0] ?? key, from + bonus)
  }
  grants = { ...grants, skillLevels: levels }
  const skillLevels = new ValueMap([...purchased.entries()].filter(([k]) => !superseded.has(k)))
  for (const [k, v] of levels.entries()) skillLevels.set(k, v)
  return {
    base: character,
    pathway: named.pathway,
    sequence: named.sequence,
    grants,
    effectiveAttributes,
    effectiveSpi,
    secondaries,
    skillLevels,
    budget: derived.budget,
  }
}

function purchasedLevels(character: Character, attributes: Attributes, secondaries: Secondaries, spi: number): ValueMap<SkillKey, number> {
  return new ValueMap(character.skills.map((s) => [
    s.key,
    SkillLadder.levelFor(s.points, s.key.difficulty, Derive.controllingLevel(s.key.attr, attributes, secondaries, spi)),
  ] as const))
}

export function isSameSkill(a: SkillKey, b: SkillKey): boolean {
  return a.name === b.name && a.difficulty === b.difficulty &&
    (a.specialization === b.specialization || ((a.specialization === null) !== (b.specialization === null) && isOneBareRow(a.name)))
}

function isOneBareRow(name: string): boolean {
  const rows = ContentLoader.skills().filter((it) => it.name === name)
  return rows.length === 1 && !rows[0].specializable && rows[0].specialization === null
}

/** `largestGrants(blocks)`. */
export function largestGrants(blocks: SequenceBlock[]): PotionGrants {
  const byPathway = new Map<string, SequenceBlock[]>()
  for (const b of blocks) byPathway.set(b.pathway, [...(byPathway.get(b.pathway) ?? []), b])
  let highest = grantsOf()
  for (const pathway of byPathway.values()) {
    let walked = grantsOf()
    highest = atLeast(highest, walked)
    for (const block of sortedBy(pathway, (it) => it.sequence, true)) {
      walked = mergeStats(walked, statGrants(block.stats))
      highest = atLeast(highest, walked)
    }
  }
  return highest
}

/** `largestGrants(potions, boons)`. */
export const largestGrantsWithBoons = (potions: SequenceBlock[], boons: SequenceBlock[]) =>
  mergeStats(largestGrants(potions), largestGrants(boons))

function atLeast(g: PotionGrants, other: PotionGrants): PotionGrants {
  return {
    ...g,
    attributes: new Map(ATTRS.map((it) => [it, Math.max(g.attributes.get(it) ?? 0, other.attributes.get(it) ?? 0)])),
    secondary: SecondaryDelta({
      hp: Math.max(g.secondary.hp, other.secondary.hp),
      fp: Math.max(g.secondary.fp, other.secondary.fp),
      will: Math.max(g.secondary.will, other.secondary.will),
      per: Math.max(g.secondary.per, other.secondary.per),
      basicSpeedQuarters: Math.max(g.secondary.basicSpeedQuarters, other.secondary.basicSpeedQuarters),
      basicMove: Math.max(g.secondary.basicMove, other.secondary.basicMove),
    }),
  }
}

function mergeStats(g: PotionGrants, other: PotionGrants): PotionGrants {
  const keys = [...new Set([...g.attributes.keys(), ...other.attributes.keys()])]
  return {
    ...g,
    attributes: new Map(keys.map((it) => [it, (g.attributes.get(it) ?? 0) + (other.attributes.get(it) ?? 0)])),
    spi: g.spi + other.spi,
    secondary: plus(g.secondary, other.secondary),
  }
}

function statGrants(stats: StatGain[]): PotionGrants {
  const attributes = new Map<Attr, number>()
  let spi = 0
  let s = SecondaryDelta()
  const raise = (a: Attr, d: number) => attributes.set(a, (attributes.get(a) ?? 0) + d)
  for (const gain of stats) {
    switch (gain.attr) {
      case 'ST': raise('ST', wholeSteps(gain)); break
      case 'DX': raise('DX', wholeSteps(gain)); break
      case 'IQ': raise('IQ', wholeSteps(gain)); break
      case 'HT': raise('HT', wholeSteps(gain)); break
      case 'SPI': spi += wholeSteps(gain); break
      case 'HP': s = SecondaryDelta({ ...s, hp: s.hp + wholeSteps(gain) }); break
      case 'FP': s = SecondaryDelta({ ...s, fp: s.fp + wholeSteps(gain) }); break
      case 'Will': s = SecondaryDelta({ ...s, will: s.will + wholeSteps(gain) }); break
      case 'Per': s = SecondaryDelta({ ...s, per: s.per + wholeSteps(gain) }); break
      case 'Basic Move': s = SecondaryDelta({ ...s, basicMove: s.basicMove + wholeSteps(gain) }); break
      case 'Basic Speed': s = SecondaryDelta({ ...s, basicSpeedQuarters: s.basicSpeedQuarters + quarterSteps(gain) }); break
      default: error(`unhandled stat grant '${gain.attr}'`)
    }
  }
  return grantsOf({ attributes, spi, secondary: s })
}

function wholeSteps(gain: StatGain): number {
  const whole = Math.trunc(gain.delta)
  require(whole === gain.delta, () => `${gain.attr} moves in whole steps; cannot grant ${gain.delta}`)
  return whole
}

function quarterSteps(gain: StatGain): number {
  const quarters = gain.delta * 4
  require(Math.trunc(quarters) === quarters, () => `Basic Speed moves in quarter steps; cannot grant ${gain.delta}`)
  return Math.trunc(quarters)
}

const plus = (a: SecondaryDelta, b: SecondaryDelta) => SecondaryDelta({
  hp: a.hp + b.hp,
  fp: a.fp + b.fp,
  will: a.will + b.will,
  per: a.per + b.per,
  basicSpeedQuarters: a.basicSpeedQuarters + b.basicSpeedQuarters,
  basicMove: a.basicMove + b.basicMove,
})

const skillKey = (name: string, a: ControllingAttribute, difficulty: Difficulty) => SkillKey(
  substringBefore(name, ' ('),
  name.includes(' (') ? removeSuffix(substringAfter(name, ' ('), ')') : null,
  a,
  difficulty,
)
