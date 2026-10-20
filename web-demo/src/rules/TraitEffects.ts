import type { BeyonderState, Modifier, SkillKey, Trait } from './model'
import { Modifier as mod } from './model'
import { eqIc, lazy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import { levelOf } from './TraitNames'
import * as Blessings from './Blessings'
import * as TraitClauses from './TraitClauses'
import * as TraitAttributeRolls from './TraitAttributeRolls'
import * as TraitRefusals from './TraitRefusals'
import * as Languages from './Languages'
import * as Corruption from './Corruption'
import * as SkillDefaults from './SkillDefaults'
import * as SpiritVision from './SpiritVision'
import * as Encumbrance from './Encumbrance'

export interface TraitClause {
  trait: string
  shown: string
  printed: string
  value: number
  conditional: boolean
}

/** `TraitClause.key`. */
export const clauseKey = (c: TraitClause) => `${c.shown} / ${c.printed}`

export interface ClauseBonus {
  total: number
  line: string | null
}

export const ALERTNESS = 'Alertness'
export const FIT = 'Fit'
export const VERY_FIT = 'Very Fit'
export const COMBAT_REFLEXES = 'Combat Reflexes'
export const PERFECT_BALANCE = 'Perfect Balance'
export const GREAT_BALANCE = 'Great Balance'
export const IRON_JAW = 'Iron Jaw'
export const RAPID_RECOVERY = 'Rapid Recovery'
export const NIGHT_EYES = 'Night Eyes'
export const CHARISMA = 'Charisma'
export const OUTDOORSMAN = 'Outdoorsman'
export const EMPATHY = 'Empathy'
export const CLUELESS = 'Clueless'
export const GOD_OF_KNOWLEDGE = 'God of Knowledge and Wisdom'
export const GOD_OF_COMBAT = 'God of Combat'
export const KNOWLEDGE_SKILLS = 'KNOWLEDGE SKILLS'
const KNOWLEDGE_BLESSING_BONUS = 5
export const SOCIAL_SKILLS = 'SOCIAL SKILLS'
export const CAPPED_LEVELS = 4
export const NIGHT_EYES_REDUCTION = 2
/** `Shock.IGNORED_BY`. */
export const HIGH_PAIN_THRESHOLD = 'High Pain Threshold'
export const ALERTNESS_PER_LEVEL = 1
export const FIT_BONUS = 1
export const UNFIT = 'Unfit'
export const UNFIT_PENALTY = 1
export const VERY_FIT_BONUS = 2
export const HARD_TO_KILL = 'Hard to Kill'
export const HARD_TO_SUBDUE = 'Hard to Subdue'
export const COMBAT_REFLEXES_BONUS = 1
export const KNOCKDOWN_BONUS = 3
export const VERSATILE = 'Versatile'
export const DEFAULT_BONUS = 1
export const BALANCE_SKILLS = new Set(['Acrobatics', 'Climbing', 'Piloting'])
export const ETHER_BODY_DAMAGE = 'Ether Body Damage'

interface SkillBonus {
  value: number
  skills: Set<string>
  perLevel: boolean
  maxLevels: number | null
}

const flat = (value: number, skills: string[]): SkillBonus => ({ value, skills: new Set(skills), perLevel: false, maxLevels: null })
const perLevel = (value: number, skills: string[]): SkillBonus => ({ value, skills: new Set(skills), perLevel: true, maxLevels: CAPPED_LEVELS })

const SKILL_BONUSES = new Map<string, SkillBonus>([
  [PERFECT_BALANCE, flat(2, [...BALANCE_SKILLS])],
  [GREAT_BALANCE, flat(1, [...BALANCE_SKILLS])],
  ['Flexibility', flat(3, ['Climbing', 'Escape'])],
  ['Double-Jointed', flat(5, ['Climbing', 'Escape'])],
  ['Healer', flat(3, ['First Aid'])],
  ['Medical Training (Informal)', flat(1, ['First Aid'])],
  ['Sanctity', flat(2, ['Theology', 'Religious Ritual'])],
  ['Absolute Direction', flat(3, ['Navigation', 'Body Sense'])],
  ['Acute Social Awareness', flat(2, ['Body Language', 'Detect Lies'])],
  ['Former Military Officer', flat(1, ['Leadership', 'Tactics'])],
  ['Photographic Instinct', flat(2, ['Photography'])],
  ['Famous Face', flat(-2, ['Disguise'])],
  [CHARISMA, perLevel(1, ['Leadership', 'Panhandling', 'Public Speaking', 'Savoir-Faire', 'Sex Appeal', 'Streetwise'])],
  [OUTDOORSMAN, perLevel(1, ['Camouflage', 'Fishing', 'Naturalist', 'Navigation', 'Survival', 'Tracking', 'Weather Sense'])],
])

const CATEGORY_BONUSES = new Map<string, [number, string]>([
  [EMPATHY, [3, SOCIAL_SKILLS]],
  [CLUELESS, [-3, SOCIAL_SKILLS]],
  [GOD_OF_KNOWLEDGE, [KNOWLEDGE_BLESSING_BONUS, KNOWLEDGE_SKILLS]],
])

export const categoryBonusNames = () => new Set(CATEGORY_BONUSES.keys())
export const categoryFor = (trait: string): string | null => CATEGORY_BONUSES.get(trait)?.[1] ?? null

export const categoryOf = (skill: SkillKey): string | null =>
  ContentLoader.skills().find((it) => it.name === skill.name)?.category ?? null

/** `BespokeBonus`: four rows whose effect is one bespoke number. */
export const BespokeBonus = {
  DEATH: { trait: HARD_TO_KILL, fixed: null as number | null },
  CONSCIOUSNESS: { trait: HARD_TO_SUBDUE, fixed: null as number | null },
  KNOCKDOWN: { trait: HIGH_PAIN_THRESHOLD, fixed: KNOCKDOWN_BONUS as number | null },
  DEFAULT: { trait: VERSATILE, fixed: DEFAULT_BONUS as number | null },
}

export function bespokeOf(b: { trait: string; fixed: number | null }, traits: Trait[]): number {
  const level = levelOf(traits, b.trait)
  if (level === null) return 0
  return b.fixed ?? level
}

export const defaultBonus = (traits: Trait[]) => bespokeOf(BespokeBonus.DEFAULT, traits)

export const darknessReduction = (traits: Trait[]) => (levelOf(traits, NIGHT_EYES) !== null ? NIGHT_EYES_REDUCTION : 0)

export const ACTING_METHOD = 'Knows the Acting Method'
export const FAST_HEALER = 'Fast Healer'
export const CLEANSED_SPIRIT = 'Cleansed Spirit'
export const LUCK = 'Luck'
export const BECKONING_LUCK = 'Beckoning Luck'
export const FATE_DEBT = 'Fate Debt'
export const LUCKY_ONE = 'Lucky One'
const WHEEL_OF_FORTUNE = 'Wheel of Fortune'
const LUCKY_ONE_SEQUENCE = 7

export const LUCK_REACH =
  'offered on a failed Potion Consumption Roll, which would end the character; ' +
  "any other reroll is the table's, and a ritual's or boon's critical-failure " +
  'CoR is charged before it and not undone'

export function rerollSource(traits: Trait[], state: BeyonderState | null): string | null {
  if (levelOf(traits, LUCK) !== null) return LUCK
  if (levelOf(traits, BECKONING_LUCK) !== null && levelOf(traits, FATE_DEBT) === null) return BECKONING_LUCK
  if (state?.pathway === WHEEL_OF_FORTUNE && state.sequence <= LUCKY_ONE_SEQUENCE) return LUCKY_ONE
  return null
}

export const healsFast = (traits: Trait[]) => levelOf(traits, FAST_HEALER) !== null
export const rollsActingMethod = (traits: Trait[]) => levelOf(traits, ACTING_METHOD) !== null

export const printedNightEyes = (): string => {
  const row = ContentLoader.advantages().find((it) => it.name === NIGHT_EYES)
  if (row === undefined) throw new Error('Collection contains no element matching the predicate.')
  return row.notes
}

export function modifiers(traits: Trait[], skill: SkillKey, declared: Set<string> = new Set()): Modifier[] {
  const out: Modifier[] = []
  if (skill.attr.type === 'Per') {
    const l = levelOf(traits, ALERTNESS)
    if (l !== null) out.push(bonus(ALERTNESS, l * ALERTNESS_PER_LEVEL))
  }
  if (skill.attr.type === 'Buyable' && skill.attr.attr === 'HT') {
    for (const [trait, value] of TraitAttributeRolls.HT_ROLL_TRAITS()) {
      if (levelOf(traits, trait) !== null) out.push(bonus(trait, value))
    }
  }
  if (skill.attr.type === 'Spi') {
    for (const [trait, value] of TraitAttributeRolls.SPI_ROLL_TRAITS()) {
      if (levelOf(traits, trait) !== null) out.push(bonus(trait, value))
    }
  }
  for (const [trait, row] of SKILL_BONUSES) {
    if (!row.skills.has(skill.name)) continue
    const owned = levelOf(traits, trait)
    if (owned === null) continue
    const levels = row.perLevel ? Math.min(owned, row.maxLevels ?? owned) : 1
    out.push(bonus(trait, row.value * levels))
  }
  const category = categoryOf(skill)
  if (category !== null) {
    for (const [trait, [value, named]] of CATEGORY_BONUSES) {
      if (named !== category) continue
      if (levelOf(traits, trait) === null) continue
      out.push(bonus(trait, value))
    }
  }
  for (const clause of TraitClauses.skillRollClauses(traits, skill)) {
    if (!declared.has(clauseKey(clause))) continue
    out.push(bonus(clause.trait, clause.value, clause.printed))
  }
  return out
}

export const defenseBonus = (traits: Trait[]) => (levelOf(traits, COMBAT_REFLEXES) !== null ? COMBAT_REFLEXES_BONUS : 0)

export const THINNED_VEIL = 'Thinned Veil'
export const CHRONIC_INSOMNIA = 'Chronic Insomnia'
export const RELUCTANT_KILLER = 'Reluctant Killer'
export const ACUTE_VISION = 'Acute Vision'
export const ACUTE_HEARING = 'Acute Hearing'
export const GREATER_ACUTE_VISION = 'Greater Acute Vision'
export const ACUTE_TASTE_SMELL = 'Acute Taste & Smell'
export const ACUTE_TOUCH = 'Acute Touch'
export const ACUTE_SMELL = 'Acute Smell'
export const INCISIVE_VISION = 'Incisive Vision'

export const skillBonusNames = () => new Set(SKILL_BONUSES.keys())
export const skillsFor = (trait: string): Set<string> => SKILL_BONUSES.get(trait)?.skills ?? new Set()
export const maxLevelsFor = (trait: string): number | null => SKILL_BONUSES.get(trait)?.maxLevels ?? null

export const known = lazy((): Set<string> => new Set([
  ...TraitRefusals.UNAPPLIED().keys(), ...TraitRefusals.PARTLY_APPLIED().keys(), ...SKILL_BONUSES.keys(),
  ...CATEGORY_BONUSES.keys(), ...Blessings.attributeGainTable().keys(),
  ...TraitClauses.frightClauseNames(), ...TraitClauses.socialClauseNames(),
  ...TraitClauses.attackClauseNames(),
  ...TraitClauses.willClauseNames(), ...TraitClauses.concentrationClauseNames(), ...TraitClauses.skillClauseNames(),
  ...TraitClauses.allRollsClauseNames(), ...TraitClauses.physicalRollsClauseNames(),
  ...TraitAttributeRolls.HT_ROLL_TRAITS().keys(), ALERTNESS, COMBAT_REFLEXES, ...TraitAttributeRolls.SPI_ROLL_TRAITS().keys(),
  ...Object.values(BespokeBonus).map((it) => it.trait),
  ...Languages.PRICING_TRAITS,
  NIGHT_EYES, Corruption.FORTITUDE,
  ACTING_METHOD,
  SkillDefaults.MEDICAL_TRAINING,
  SpiritVision.TRAIT,
  Encumbrance.LIFTING_ST,
]))

export const unappliedNames = () => new Set(TraitRefusals.UNAPPLIED().keys())
export const partlyAppliedNames = () => new Set(TraitRefusals.PARTLY_APPLIED().keys())

export function bonus(trait: string, value: number, suffix: string | null = null, shown: string = trait): Modifier {
  return mod({
    id: `trait:${trait.toLowerCase()}` + (suffix !== null ? `:${suffix}` : ''),
    source: { type: 'FromTrait', name: shown, advantage: isAdvantage(trait) },
    target: { type: 'AllRolls' },
    effect: { type: 'Flat', n: value },
    layer: 'ADVANTAGE',
  })
}

function isAdvantage(trait: string): boolean {
  return ContentLoader.advantages().some((it) => eqIc(it.name, trait))
}
