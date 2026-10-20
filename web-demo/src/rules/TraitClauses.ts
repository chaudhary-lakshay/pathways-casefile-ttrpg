// Every clause a roll asks the GM about, by the roll it reaches.
import type { Modifier, SkillKey, Trait } from './model'
import { distinctBy, eqIc, lazy, removeSuffix, require, startsWithIc, substringAfterLast, sumOf } from './kt'
import { catalogueName, displayName, levelOf } from './TraitNames'
import * as CharmedObject from './CharmedObject'
import {
  ACUTE_HEARING, ACUTE_SMELL, ACUTE_TASTE_SMELL, ACUTE_TOUCH, ACUTE_VISION, bonus, categoryOf, CHRONIC_INSOMNIA,
  clauseKey, GREATER_ACUTE_VISION, INCISIVE_VISION, RELUCTANT_KILLER, SOCIAL_SKILLS, THINNED_VEIL,
  type ClauseBonus, type TraitClause,
} from './TraitEffects'

const clause = (trait: string, printed: string, value: number, conditional = true): TraitClause =>
  ({ trait, shown: trait, printed, value, conditional })

const copy = (c: TraitClause, shown: string): TraitClause => ({ ...c, shown })

const FRIGHT_CLAUSES = lazy(() => [
  clause('Fearlessness', '+1 per level to Fright Checks', 1, false),
  clause('Faithful Grounding', 'Fright Checks in church buildings, shrines, or during prayer are at +3', 3),
  clause('Chronophobia (Temporal)', 'immediate Fright Check at -3', -3),
  clause('Phobia (specify)', 'Fright Check at -2 when exposed', -2),
  clause('Phobia: Sacred Symbols', 'Fright Check at -2 on direct exposure', -2),
  clause('Phobia: Sacred Symbols', '-1 in consecrated buildings', -1),
])

export const frightClauseRows = () => FRIGHT_CLAUSES()
export const frightClauseNames = () => new Set(FRIGHT_CLAUSES().map((it) => it.trait))

export function frightClauses(traits: Trait[]): TraitClause[] {
  return traits.flatMap((owned) => {
    const bare = catalogueName(owned.name)
    const levels = levelOf(traits, bare) ?? 1
    return FRIGHT_CLAUSES()
      .filter((it) => eqIc(it.trait, bare))
      .map((row) => ({ ...row, shown: displayName(owned.name), value: row.conditional ? row.value : row.value * levels }))
  })
}

export function clauseBonus(clauses: TraitClause[], declared: Set<string>): ClauseBonus {
  const applied = clauses.filter((it) => !it.conditional || declared.has(clauseKey(it)))
  if (applied.length === 0) return { total: 0, line: null }
  const parts = applied.map((it) => `${it.shown} ${it.value > 0 ? '+' : ''}${it.value}`).join(', ')
  return { total: sumOf(applied, (it) => it.value), line: parts }
}

const WILL_CLAUSES = lazy(() => [
  clause('Incorruptible Will', '+3 to all Will rolls to resist Beyonder abilities, evil god whispers, and ritual compulsion', 3),
  clause('Grief-Stricken', 'In situations that echo a specific loss, Will rolls to act clearly are at -2', -2),
  clause('Evil Eye Fear', 'If interrupted from a warding routine, -1 to Will rolls for the day', -1),
  clause('Addiction: Tobacco', 'Minor withdrawal irritability (-1 to Will) if unable to smoke for a full day', -1),
  clause('Guilt', '-1 to Will in situations that echo the original act', -1),
])

export const willClauseRows = () => WILL_CLAUSES()
export const willClauseNames = () => new Set(WILL_CLAUSES().map((it) => it.trait))

function matching(traits: Trait[], rows: TraitClause[]): TraitClause[] {
  return traits.flatMap((owned) => {
    const bare = catalogueName(owned.name)
    return rows.filter((it) => eqIc(it.trait, bare)).map((it) => copy(it, displayName(owned.name)))
  })
}

export const willClauses = (traits: Trait[]) => matching(traits, WILL_CLAUSES())

const CONCENTRATION_CLAUSES = lazy(() => [
  clause(THINNED_VEIL, 'Concentration in spiritually active environments at -2', -2),
  clause(CHRONIC_INSOMNIA, 'rolls requiring sustained concentration at -1', -1),
])

export const concentrationClauseNames = () => new Set(CONCENTRATION_CLAUSES().map((it) => it.trait))

export const concentrationClauses = (traits: Trait[]) => [...willClauses(traits), ...matching(traits, CONCENTRATION_CLAUSES())]

const SOCIAL_CLAUSES = lazy((): [string | null, TraitClause][] => [
  [null, clause('Callous', '-3 to social skill rolls when warmth or empathy is required', -3)],
  ['Mild', clause('Shyness', 'Mild (-5): -1 in groups of 6+', -1)],
  ['Severe', clause('Shyness', 'Severe (-10): -2 in groups of 3+', -2)],
  [null, clause('Social Anxiety', '-2 to social skill rolls in groups of 6+', -2)],
  [null, clause('Social Anxiety', '-3 when addressing strangers of higher status', -3)],
  [null, clause('Loner', '-1 to social rolls in crowds', -1)],
  [null, clause('Workaholic', '-2 to social rolls in leisure settings', -2)],
  [null, clause('Illegitimate Birth', '-1 to social rolls in formal/upper-class settings where this is known', -1)],
  [null, clause('Dead-Eyed', '-2 to first-impression social rolls with strangers', -2)],
  [null, clause('Local Legend', '+2 to social rolls there', 2)],
  [null, clause('Respectable Address', '+1 to social rolls with middle/upper class NPCs who would otherwise look down on you', 1)],
])

export const socialClauseRows = () => SOCIAL_CLAUSES().map((it) => it[1])

const ATTACK_CLAUSES = lazy(() => [
  clause(RELUCTANT_KILLER, '-4 to hit recognizable people with deadly force', -4),
  clause(RELUCTANT_KILLER, '-2 if face hidden', -2),
])

export function attackClauses(traits: Trait[]): TraitClause[] {
  return traits.flatMap((owned) => {
    if (owned.kind !== 'DISADVANTAGE') return []
    const bare = catalogueName(owned.name)
    return ATTACK_CLAUSES().filter((it) => eqIc(it.trait, bare)).map((it) => copy(it, displayName(owned.name)))
  })
}

export function attackModifiers(traits: Trait[], declared: string | null): Modifier[] {
  if (declared === null) return []
  const c = attackClauses(traits).find((it) => clauseKey(it) === declared)
  require(c !== undefined, () => `no to-hit clause offered for '${declared}'`)
  return [bonus(c.trait, c.value, c.printed)]
}

export const refusesAim = (traits: Trait[]) => attackClauses(traits).length > 0
export const attackClauseNames = () => new Set(ATTACK_CLAUSES().map((it) => it.trait))
export const AIM_REFUSAL = 'cannot Aim'
export const socialClauseNames = () => new Set(SOCIAL_CLAUSES().map((it) => it[1].trait))

export function socialClauses(traits: Trait[]): TraitClause[] {
  return traits.flatMap((owned) => {
    const bare = catalogueName(owned.name)
    const variant = removeSuffix(substringAfterLast(owned.name, '(', ''), ')')
    return SOCIAL_CLAUSES()
      .filter(([needs, row]) => eqIc(row.trait, bare) && (needs === null || (variant !== '' && startsWithIc(needs, variant))))
      .map(([, row]) => copy(row, displayName(owned.name)))
  })
}

interface NamedSkillClause {
  skill: string
  specializations: (string | null)[] | null
  clause: TraitClause
}

const skillClause = (trait: string, skill: string, printed: string, value: number, specializations: (string | null)[] | null = null): NamedSkillClause =>
  ({ skill, specializations, clause: clause(trait, printed, value) })

const SKILL_CLAUSES = lazy(() => [
  skillClause('Class Mobility', 'Savoir-Faire', '+2 to Savoir-Faire in either', 2),
  skillClause('Fearsome Reputation', 'Intimidation', 'Intimidation in these circles is at +2', 2),
  skillClause('Safecracker', 'Lockpicking', '+3 to Lockpicking for combination locks and mechanical safes specifically', 3),
  skillClause('Alcohol Tolerance', 'Carousing', 'Carousing rolls to appear sober at +3', 3),
  skillClause("Navigator's Eye", 'Navigation', 'rural navigation rolls at +2', 2, [null, 'Land']),
])

const SENSE_CLAUSES = lazy(() => [
  clause(ACUTE_VISION, '+2 to Vision rolls', 2),
  clause(ACUTE_HEARING, '+2 to Hearing rolls', 2),
  clause(INCISIVE_VISION, '+4 to all Vision rolls', 4),
])

export const GRANTED_ONLY_SENSES = lazy(() => [
  clause(GREATER_ACUTE_VISION, '+4 to all vision-based Perception rolls', 4),
  clause(ACUTE_TASTE_SMELL, '+2 to taste/smell-based Perception rolls', 2),
  clause(ACUTE_TOUCH, '+2 to touch-based Perception rolls', 2),
  clause(ACUTE_SMELL, '+2 sharper sense of smell', 2),
])

export const senseClauses = (traits: Trait[]) => matching(traits, [...SENSE_CLAUSES(), ...GRANTED_ONLY_SENSES()])

const PER_ROLL_CLAUSES = lazy(() => [clause('Light Sleeper', 'Perception rolls while sleeping at +4', 4)])

export const perRollClauses = (traits: Trait[]) => matching(traits, PER_ROLL_CLAUSES())

export const visionClause = (traits: Trait[]): TraitClause | null =>
  senseClauses(traits).find((it) => it.trait === ACUTE_VISION) ?? null

const ANY_SKILL_CLAUSES = lazy(() => [
  clause('Absent-Mindedness', '-3 to skill rolls requiring concentration or organisation in everyday life', -3),
])

export const skillClauseNames = () => new Set(skillClauseRows().map((it) => it.trait))

export const skillClauseRows = (): TraitClause[] => [
  ...SKILL_CLAUSES().map((it) => it.clause), ...SENSE_CLAUSES(), ...PER_ROLL_CLAUSES(), ...ANY_SKILL_CLAUSES(),
  CharmedObject.TEMPLATE,
]

export function skillRollClauses(traits: Trait[], skill: SkillKey): TraitClause[] {
  const sense = skill.attr.type === 'Per' ? [...senseClauses(traits), ...perRollClauses(traits)] : []
  const social = categoryOf(skill) === SOCIAL_SKILLS ? socialClauses(traits) : []
  const named = traits.flatMap((owned) => {
    const bare = catalogueName(owned.name)
    return SKILL_CLAUSES()
      .filter((row) => eqIc(row.clause.trait, bare) && row.skill === skill.name &&
        (row.specializations === null || row.specializations.includes(skill.specialization)))
      .map((it) => copy(it.clause, displayName(owned.name)))
  })
  const any = matching(traits, ANY_SKILL_CLAUSES())
  const charmed = CharmedObject.clauses(traits, skill)
  return distinctBy([...social, ...named, ...sense, ...any, ...charmed], (it) => [it.trait, it.printed])
}

const ALL_ROLLS_CLAUSES = lazy(() => [
  clause('Phobia (specify)', '-1 to all rolls while the phobic stimulus is present and unavoidable', -1),
  clause('Addiction: Laudanum', 'Must use daily or suffer -2 to all rolls from withdrawal', -2),
  clause('Alcoholism', 'Deprived of alcohol long enough, withdrawal applies -2 to all rolls until you can drink', -2),
  clause('Superstitious (mundane)', 'If warding routine is disrupted, -1 to all rolls for the day', -1),
  clause('Soul Scar', 'Under extreme spiritual stress (Fright Check failure by 5+, witnessing Seq. 5+ ' +
    'abilities), old pain resurfaces: -1 to all rolls for the scene', -1),
  clause('Entity Fixation', '-1 to all rolls when a specific entity type is present nearby', -1),
  clause('Reckless', '-1 to any roll where caution would be smarter', -1),
])

export const allRollsClauseRows = () => ALL_ROLLS_CLAUSES()
export const allRollsClauseNames = () => new Set(ALL_ROLLS_CLAUSES().map((it) => it.trait))

export const allRollsClauses = (traits: Trait[]) => distinctBy(matching(traits, ALL_ROLLS_CLAUSES()), clauseKey)

export function allRollsModifiers(clauses: TraitClause[], declared: Set<string>): Modifier[] {
  return clauses.filter((it) => !it.conditional || declared.has(clauseKey(it)))
    .map((it) => bonus(it.trait, it.value, clauseKey(it), it.shown))
}

const PHYSICAL_ROLLS_CLAUSES = lazy(() => [
  clause('Perceptual Splitting', 'All physical rolls at -2 during an episode', -2),
])

export const physicalRollsClauseRows = () => PHYSICAL_ROLLS_CLAUSES()
export const physicalRollsClauseNames = () => new Set(PHYSICAL_ROLLS_CLAUSES().map((it) => it.trait))

const physicalRollsClauses = (traits: Trait[]) => distinctBy(matching(traits, PHYSICAL_ROLLS_CLAUSES()), clauseKey)

export const declaredStateClauses = (traits: Trait[]) => [...allRollsClauses(traits), ...physicalRollsClauses(traits)]

export interface DeclaredStates {
  all: ClauseBonus
  physical: ClauseBonus
}

export function declaredStates(traits: Trait[], declared: Set<string>, pending: TraitClause[] = []): DeclaredStates {
  const all = [...allRollsClauses(traits), ...pending]
  return {
    all: clauseBonus(all, declared),
    physical: clauseBonus([...all, ...physicalRollsClauses(traits)], declared),
  }
}

export function declaredModifiers(traits: Trait[], declared: Set<string>, physical: boolean, pending: TraitClause[] = []): Modifier[] {
  return allRollsModifiers(
    [...allRollsClauses(traits), ...pending, ...(physical ? physicalRollsClauses(traits) : [])],
    declared,
  )
}
