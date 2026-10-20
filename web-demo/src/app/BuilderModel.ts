// The builder's pure helpers: BuilderLedger.kt, BuilderRows.kt,
// BuilderScreen.kt and TraitPicker.kt, each named as in Kotlin.
import * as Derive from '../rules/Derive'
import type { CataloguedWeapon, SkillDefinition } from '../rules/content/schema.gen'
import { containsIc, eqIc, isBlank, trim, type ValueMap } from '../rules/kt'
import * as SkillDefaults from '../rules/SkillDefaults'
import * as Weapons from '../rules/Weapons'
import type { BeyonderState, Character, PurchasedSkill, SkillKey } from '../rules/model'
import * as PotionDigestion from '../rules/PotionDigestion'
import type { DigestedCharacter } from '../rules/PotionDigestion'
import * as Study from '../rules/Study'
import type { SavedCharmedSkill } from './session/SessionStore'
import { liveSkillLevels } from './session/EditedCharacter'

export const FORM_LEDGER = 'FORM 2-P · POINT LEDGER'
export const BUDGET_PIPS = 30

/** A pip fills only when its share of the budget is all spent. */
export function budgetPips(spent: number, budget: number): number {
  if (spent <= 0) return 0
  if (spent >= budget) return BUDGET_PIPS
  return Math.floor((spent * BUDGET_PIPS) / budget)
}

const SHORT_NAMES: Record<string, string> = { hp: 'HP', per: 'Per', fp: 'FP', basicSpeed: 'Speed', basicMove: 'Move' }
export const secondaryShortName = (key: string, characteristic: string) => SHORT_NAMES[key] ?? characteristic

const MAX_COLUMN_DP = 600
const STUDY_BESIDE_MIN = 360

/** The ledger is drawn in the 600 dp column: below 360 text-dp the stepper takes its own line. */
export const ledgerStacks = (screenWidthDp: number, fontScale: number) => Math.min(screenWidthDp, MAX_COLUMN_DP) / fontScale < STUDY_BESIDE_MIN

/** BuilderScreen.kt `Int.signed()`: a true minus sign. */
export const signedPoints = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '0')
export const pointsLabel = (n: number) => `${signedPoints(n)} ${n === 1 || n === -1 ? 'pt' : 'pts'}`

export interface StudyAdvice {
  tag: string
  reason: string | null
}

export const isSupersededByPotion = (key: SkillKey, played: DigestedCharacter | null) =>
  played !== null && [...played.grants.skillBonuses.keys()].some((it) => PotionDigestion.isSameSkill(it, key))

/** BuilderRows.kt `studyAdvice`: a refusal tagged by its cause, a plan by its price and rung. */
export function studyAdvice(
  purchased: PurchasedSkill,
  character: Character,
  derived: ReturnType<typeof Derive.of>,
  method: Study.StudyMethod,
  isPotionGranted = false,
): StudyAdvice {
  const attributeLevel = Derive.controllingLevel(purchased.key.attr, Derive.attributes(character, null), derived.secondaries, character.spi)
  const result = Study.plan(purchased.points, purchased.key.difficulty, attributeLevel, method, isPotionGranted)
  if (result.ok) return { tag: `${Study.printedPlan(result.plan)} · ${result.plan.pointsNeeded} CP → ${result.plan.toLevel}`, reason: null }
  const refusal = result.error.refusal
  const tag = refusal === 'AT_CAP' ? `at cap ${Study.capFor(attributeLevel)}`
    : refusal === 'NOT_POSSIBLE' ? `not by ${Study.METHOD_LABEL[method].toLowerCase()}`
    : 'potion skill'
  return { tag, reason: result.error.reason }
}

export const isCharmed = (chosen: SavedCharmedSkill | null, key: SkillKey) =>
  chosen !== null && eqIc(key.name, chosen.name) && eqIc(key.specialization, chosen.specialization)

/** TraitPicker.kt `charmLine`. */
export function charmLine(chosen: SavedCharmedSkill | null, held: SkillKey[]): string {
  if (chosen === null) return 'No skill chosen: no roll offers its +1.'
  const label = chosen.specialization === null ? chosen.name : `${chosen.name} (${chosen.specialization})`
  return held.some((it) => isCharmed(chosen, it)) ? `Charms ${label}: +1 when used, lit on the roll.` : `Charms ${label}, which this character has no level in.`
}

/** WeaponPicker.kt `carriedLine`: the level it is wielded at, and its parry. */
export function carriedLine(weapon: CataloguedWeapon, skillLevels: ValueMap<SkillKey, number>, mastery: SkillDefaults.WeaponMastery | null, dx: number): string {
  const level = SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx)
  if (level === null) return `${weapon.skill} — not a skill this character has, so no parry`
  const parry = Weapons.parryFor(weapon, level)
  return parry === null ? `${weapon.skill} ${level} — cannot parry` : `${weapon.skill} ${level} — parry ${parry}`
}

/** TraitPicker.kt `matchesSearch` and BuilderPickers.kt `skillMatches`. */
export const matchesSearch = (name: string, query: string) => isBlank(query) || containsIc(name, trim(query))
export const definitionLabel = (d: SkillDefinition) => (d.specialization === null ? d.name : `${d.name} (${d.specialization})`)
export const skillMatches = (d: SkillDefinition, query: string) => matchesSearch(definitionLabel(d), query)

export const charmableSkills =(character: Character, state: BeyonderState | null): SkillKey[] =>
  [...liveSkillLevels({ ...character, beyonder: state }).keys()]
