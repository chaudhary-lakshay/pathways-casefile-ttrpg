import { describe } from 'vitest'
import { replay } from './parity'
import { Trait, type TraitKind } from '../rules/model'
import type { TraitDefinition } from '../rules/content/schema.gen'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as Appearance from '../rules/Appearance'
import * as CharmedObject from '../rules/CharmedObject'
import * as Languages from '../rules/Languages'
import * as Premades from '../rules/Premades'
import * as SelfControl from '../rules/SelfControl'
import * as TraitAttributeRolls from '../rules/TraitAttributeRolls'
import * as TraitClauses from '../rules/TraitClauses'
import * as TraitEffects from '../rules/TraitEffects'
import * as TraitNames from '../rules/TraitNames'
import * as TraitReport from '../rules/TraitReport'
import * as Wealth from '../rules/Wealth'

const held = (d: TraitDefinition, kind: TraitKind) => Trait(d.name, Math.abs(d.cost ?? d.costMin ?? d.costMax ?? 0), kind)

/** TraitVectors.kt's `traitLists`, rebuilt from the label it passes. */
function traits(label: string): Trait[] {
  if (label.startsWith('premade:')) return Premades.toCharacter(Premades.byName(label.slice(8))).traits
  if (label === 'catalogue') {
    return [
      ...ContentLoader.advantages().map((d) => held(d, 'ADVANTAGE')),
      ...ContentLoader.disadvantages().map((d) => held(d, 'DISADVANTAGE')),
    ]
  }
  if (label === 'variants') return VARIANTS
  throw new Error(`no trait list ${label}`)
}

const VARIANTS: Trait[] = [
  Trait('Alertness 2', 10, 'ADVANTAGE'),
  Trait('Charisma 3', 15, 'ADVANTAGE'),
  Trait('Hard to Kill 3', 6, 'ADVANTAGE'),
  Trait('Acute Vision 2', 4, 'ADVANTAGE'),
  Trait('Acute Hearing 1', 2, 'ADVANTAGE'),
  Trait('Fit', 5, 'ADVANTAGE'),
  Trait('Combat Reflexes', 15, 'ADVANTAGE'),
  Trait('Luck', 15, 'ADVANTAGE'),
  Trait('Versatile', 5, 'ADVANTAGE'),
  Trait('Charmed Object (Stealth)', 5, 'ADVANTAGE'),
  Trait('Language (Balam (Dutan), Accented)', 4, 'ADVANTAGE'),
  Trait('Shyness (Mild)', 5, 'DISADVANTAGE'),
  Trait('Light Sleeper', 5, 'DISADVANTAGE'),
  Trait('Reckless', 10, 'DISADVANTAGE'),
  Trait('Unfit', 5, 'DISADVANTAGE'),
  Trait('Wealth (Poor)', 15, 'DISADVANTAGE'),
  Trait('Secret (Beyonder)', 10, 'DISADVANTAGE'),
  Trait('Talks to Himself', 1, 'QUIRK'),
]

const set = (a: string[]) => new Set(a)
const sorted = (s: Set<string>) => [...s].sort()

describe('trait names', () => {
  replay('traits', 'TraitNames.catalogueName', TraitNames.catalogueName)
  replay('traits', 'TraitNames.displayName', TraitNames.displayName)
  replay('traits', 'CharmedObject.skillOf', CharmedObject.skillOf)
  replay('traits', 'TraitNames.levelOf', (l, n) => TraitNames.levelOf(traits(l), n))
  replay('traits', 'TraitEffects.known', () => sorted(TraitEffects.known()))
  replay('traits', 'TraitEffects.unappliedNames', () => sorted(TraitEffects.unappliedNames()))
  replay('traits', 'TraitEffects.partlyAppliedNames', () => sorted(TraitEffects.partlyAppliedNames()))
  replay('traits', 'SelfControl.borrowedRolls', () => SelfControl.borrowedRolls())
})

describe('the trait report', () => {
  replay('traits', 'TraitReport.held', (l, k) => TraitReport.held(traits(l), k))
  replay('traits', 'TraitReport.applied', (l) =>
    (['ADVANTAGE', 'DISADVANTAGE', 'QUIRK'] as const).flatMap((k) => TraitReport.held(traits(l), k)).map(TraitReport.applied),
  )
  replay('traits', 'TraitReport.reported', (l) => TraitReport.reported(traits(l)))
  replay('traits', 'TraitReport.corruptionNotes', (l) => TraitReport.corruptionNotes(traits(l)))
  replay('traits', 'SelfControl.checksFor', (l) => SelfControl.checksFor(traits(l)))
})

describe('trait clauses', () => {
  replay('traits', 'TraitClauses.frightClauses', (l) => TraitClauses.frightClauses(traits(l)))
  replay('traits', 'TraitClauses.willClauses', (l) => TraitClauses.willClauses(traits(l)))
  replay('traits', 'TraitClauses.concentrationClauses', (l) => TraitClauses.concentrationClauses(traits(l)))
  replay('traits', 'TraitClauses.attackClauses', (l) => TraitClauses.attackClauses(traits(l)))
  replay('traits', 'TraitClauses.refusesAim', (l) => TraitClauses.refusesAim(traits(l)))
  replay('traits', 'TraitClauses.socialClauses', (l) => TraitClauses.socialClauses(traits(l)))
  replay('traits', 'TraitClauses.perRollClauses', (l) => TraitClauses.perRollClauses(traits(l)))
  replay('traits', 'TraitClauses.visionClause', (l) => TraitClauses.visionClause(traits(l)))
  replay('traits', 'TraitClauses.allRollsClauses', (l) => TraitClauses.allRollsClauses(traits(l)))
  replay('traits', 'TraitClauses.declaredStateClauses', (l) => TraitClauses.declaredStateClauses(traits(l)))
  replay('traits', 'TraitClauses.declaredStates', (l, d) => TraitClauses.declaredStates(traits(l), set(d)))
  replay('traits', 'TraitClauses.declaredModifiers', (l, d, p) => TraitClauses.declaredModifiers(traits(l), set(d), p))
  replay('traits', 'TraitClauses.clauseBonus.fright', (l, d) => {
    const clauses = TraitClauses.frightClauses(traits(l))
    return TraitClauses.clauseBonus(clauses, set([...d, ...clauses.map(TraitEffects.clauseKey)]))
  })
  replay('traits', 'TraitClauses.attackModifiers', (l, c) => TraitClauses.attackModifiers(traits(l), c))
  replay('traits', 'TraitClauses.skillRollClauses', (l, s) => TraitClauses.skillRollClauses(traits(l), s))
  replay('traits', 'CharmedObject.clauses', (l, s) => CharmedObject.clauses(traits(l), s))
})

describe('trait effects', () => {
  replay('traits', 'TraitEffects.modifiers', (l, s, d) => TraitEffects.modifiers(traits(l), s, set(d)))
  replay('traits', 'TraitEffects.defaultBonus', (l) => TraitEffects.defaultBonus(traits(l)))
  replay('traits', 'TraitEffects.darknessReduction', (l) => TraitEffects.darknessReduction(traits(l)))
  replay('traits', 'TraitEffects.defenseBonus', (l) => TraitEffects.defenseBonus(traits(l)))
  replay('traits', 'TraitEffects.healsFast', (l) => TraitEffects.healsFast(traits(l)))
  replay('traits', 'TraitEffects.rollsActingMethod', (l) => TraitEffects.rollsActingMethod(traits(l)))
  replay('traits', 'TraitEffects.rerollSource', (l, s) => TraitEffects.rerollSource(traits(l), s))
  replay('traits', 'TraitAttributeRolls.all', (l) => {
    const t = traits(l)
    return [
      TraitAttributeRolls.alertnessBonus(t),
      TraitAttributeRolls.spiRollBonus(t),
      TraitAttributeRolls.knockdownBonus(t),
      TraitAttributeRolls.deathRollBonus(t),
      TraitAttributeRolls.consciousnessRollBonus(t),
      TraitAttributeRolls.htRollBonus(t),
      TraitAttributeRolls.stunRecoveryBonus(t),
    ]
  })
  replay('traits', 'TraitAttributeRolls.majorWoundHt', (l) => TraitAttributeRolls.majorWoundHt(traits(l)))
  replay('traits', 'TraitAttributeRolls.stunRecoveryHt', (l) => TraitAttributeRolls.stunRecoveryHt(traits(l)))
})

describe('wealth, appearance, languages', () => {
  replay('traits', 'Wealth.levelOf', (l) => Wealth.levelOf(traits(l)))
  replay('traits', 'Appearance.levelOf', (l) => Appearance.levelOf(traits(l)))
  replay('traits', 'Appearance.played', (l) => Appearance.played(traits(l)))
  replay('traits', 'Appearance.without', (l) => Appearance.without(traits(l)))
  replay('traits', 'Wealth.applied', (l, w) => Wealth.applied(traits(l), Wealth.named(w)))
  replay('traits', 'Appearance.applied', (l, a) => Appearance.applied(traits(l), Appearance.named(a)))
  replay('traits', 'Wealth.traitFor', (w) => Wealth.traitFor(Wealth.named(w)))
  replay('traits', 'Appearance.traitFor', (a) => Appearance.traitFor(Appearance.named(a)))
  replay('traits', 'Languages.owned', Languages.owned)
  replay('traits', 'Languages.traits', Languages.traits)
  replay('traits', 'LanguagePurchase.label', Languages.purchaseLabel)
  replay('traits', 'Languages.proficiencies', Languages.proficiencies)
  replay('traits', 'Languages.ritualModifier', (type, prof) =>
    Languages.ritualModifier(type === null ? null : Languages.proficiency(type, prof)),
  )
  replay('traits', 'Languages.forms', () => Languages.forms())
  replay('traits', 'Languages.notes', () => Languages.notes())
  replay('traits', 'Languages.grantedBy', Languages.grantedBy)
})
