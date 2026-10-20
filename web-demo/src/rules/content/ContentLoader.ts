// ContentLoader: the Android app's own JSON, decoded as kotlinx.serialization
// decodes it (defaults, code serializers, enum spellings, sorting), from the
// schema scripts/gen-content.mjs reads off the Kotlin content classes.

import { controllingFromCode, difficultyFromCode, Dice } from '../model'
import { error, single } from '../kt'
import { ACCESSORS, ENUMS, LOADERS, SCHEMA } from './schema.gen'
import type * as C from './schema.gen'

const files = import.meta.glob<unknown>('@content/*.json', { eager: true, import: 'default' })

function raw(file: string): any {
  const key = Object.keys(files).find((k) => k.endsWith(`/${file}`))
  if (!key) error(`content file not found: ${file}`)
  return files[key]
}

function splitArgs(s: string): string[] {
  const out: string[] = []
  let depth = 0
  let start = 0
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '<') depth++
    else if (s[i] === '>') depth--
    else if (s[i] === ',' && depth === 0) {
      out.push(s.slice(start, i).trim())
      start = i + 1
    }
  }
  out.push(s.slice(start).trim())
  return out
}

function decodeAs(type: string, v: unknown, code: string | undefined, where: string): unknown {
  if (type.endsWith('?')) {
    if (v === null) return null
    type = type.slice(0, -1)
  }
  if (v === null || v === undefined) error(`${where}: null for non-null ${type}`)
  if (code === 'attr') return controllingFromCode(v as string)
  if (code === 'difficulty') return difficultyFromCode(v as string)
  const g = /^(\w+)<(.+)>$/.exec(type)
  if (g) {
    const args = splitArgs(g[2])
    if (g[1] === 'List' || g[1] === 'Set') return (v as unknown[]).map((x, i) => decodeAs(args[0], x, undefined, `${where}[${i}]`))
    if (g[1] === 'Map') {
      return Object.fromEntries(
        Object.entries(v as object).map(([k, x]) => [k, decodeAs(args[1], x, undefined, `${where}.${k}`)]),
      )
    }
  }
  switch (type) {
    case 'String': case 'Int': case 'Long': case 'Double': case 'Boolean':
      return v
  }
  if (ENUMS[type]) {
    const name = ENUMS[type][v as string]
    if (name === undefined) error(`${where}: '${String(v)}' is not a ${type}`)
    return name
  }
  if (SCHEMA[type]) return decodeClass(type, v, where)
  return error(`${where}: no decoder for ${type}`)
}

function decodeClass(cls: string, v: any, where = cls): any {
  const out: Record<string, unknown> = {}
  for (const f of SCHEMA[cls]) {
    const key = f.json ?? f.name
    if (v[key] === undefined) {
      if (!('default' in f)) error(`${where}: missing field ${key}`)
      out[f.name] = structuredClone(f.default)
    } else {
      out[f.name] = decodeAs(f.type, v[key], f.code, `${where}.${f.name}`)
    }
  }
  return out
}

const cache = new Map<string, unknown>()

function lazy(name: string): any {
  if (cache.has(name)) return cache.get(name)
  const l = LOADERS[name]
  const json = raw(l.file)
  let value: unknown
  switch (l.mode) {
    case 'whole':
      value = decodeClass(l.cls, json)
      break
    case 'single':
      value = single((json.entries as unknown[]).map((e) => decodeClass(l.cls, e)))
      break
    case 'thrustSwing': {
      const rows: C.DamageRowJson[] = json.entries.map((e: unknown) => decodeClass('DamageRowJson', e))
      value = new Map(rows.map((r) => [r.st, { st: r.st, thrust: Dice.parse(r.thrust), swing: Dice.parse(r.swing) }]))
      break
    }
    default: {
      const list = (json.entries as unknown[]).map((e, i) => decodeClass(l.cls, e, `${l.file}[${i}]`))
      value = l.sort ? [...list].sort((a: any, b: any) => a[l.sort!] - b[l.sort!]) : list
    }
  }
  cache.set(name, value)
  return value
}

function get(accessor: string): any {
  const a = ACCESSORS[accessor]
  const v = lazy(a.lazy)
  return a.field ? v[a.field] : v
}

/** Every accessor by name, for the content parity test. */
export const byName = (accessor: string): unknown => get(accessor)
export const accessorNames = (): string[] => Object.keys(ACCESSORS)

export const skills = (): C.SkillDefinition[] => get('skills')
export const advantages = (): C.TraitDefinition[] => get('advantages')
export const disadvantages = (): C.TraitDefinition[] => get('disadvantages')
export const blessings = (): C.Blessing[] => get('blessings')
export const blessingCost = (): number => get('blessingCost')
export const blessingRequirement = (): string => get('blessingRequirement')
export const thrustSwing = (): Map<number, C.DamageRow> => get('thrustSwing')
export const pathways = (): C.SequenceBlock[] => get('pathways')
export const actingMethod = (): C.ActingQuality[] => get('actingMethod')
export const potionConsumption = (): C.PcrRow[] => get('potionConsumption')
export const corGained = (): C.CorGain[] => get('corGained')
export const poolThresholds = (): C.PoolThreshold[] => get('poolThresholds')
export const hpRecovery = (): C.HpRecoveryRow[] => get('hpRecovery')
export const rangePenalties = (): C.RangePenalty[] => get('rangePenalties')
export const rangedModifiers = (): C.RangedModifier[] => get('rangedModifiers')
export const divinationDifficulty = (): C.DivinationDifficulty[] => get('divinationDifficulty')
export const incantations = (): C.Incantation[] => get('incantations')
export const divinationMethods = (): C.DivinationMethod[] => get('divinationMethods')
export const divinationModifiers = (): C.DivinationModifier[] => get('divinationModifiers')
export const divinationClarity = (): C.DivinationClarity[] => get('divinationClarity')
export const weapons = (): C.CataloguedWeapon[] => get('weapons')
export const wealth = (): C.WealthLevel[] => get('wealth')
export const equipment = (): C.EquipmentItem[] => get('equipment')
export const secondaryCosts = (): C.SecondaryCost[] => get('secondaryCosts')
export const studyCosts = (): C.StudyCost[] => get('studyCosts')
export const studyRules = (): C.StudyRule[] => get('studyRules')
export const appearance = (): C.AppearanceLevel[] => get('appearance')
export const glossary = (): C.GlossaryEntry[] => get('glossary')
export const spiritVision = (): C.SpiritVisionFile => get('spiritVision')
export const ritualQuickPreparation = (): C.RitualPreparation[] => get('ritualQuickPreparation')
export const ritualTargetLink = (): C.TargetLink[] => get('ritualTargetLink')
export const languages = (): C.LanguageEntry[] => get('languages')
export const languageProficiencies = (): C.LanguageProficiency[] => get('languageProficiencies')
export const languageNotes = (): C.LanguageNote[] => get('languageNotes')
export const damageAbbreviations = (): C.DamageAbbreviation[] => get('damageAbbreviations')
export const shields = (): C.Shield[] => get('shields')
export const shieldRules = (): C.ShieldRule[] => get('shieldRules')
export const defenseRefusals = (): C.DefenseRefusalRule[] => get('defenseRefusals')
export const majorWound = (): C.MajorWound[] => get('majorWound')
export const spiritDamage = (): C.SpiritDamageRule[] => get('spiritDamage')
export const hitLocations = (): C.HitLocation[] => get('hitLocations')
export const cripplingRule = (): string => get('cripplingRule')
export const woundingMultipliers = (): C.WoundingMultiplier[] => get('woundingMultipliers')
export const maneuvers = (): C.Maneuver[] => get('maneuvers')
export const extraEffort = (): C.ExtraEffort[] => get('extraEffort')
export const attackOptions = (): C.AttackOption[] => get('attackOptions')
export const defenses = (): C.Defense[] => get('defenses')
export const defenseModifiers = (): C.DefenseModifier[] => get('defenseModifiers')
export const unarmedAttacks = (): C.UnarmedAttack[] => get('unarmedAttacks')
export const ritualWeights = (): C.RitualWeight[] => get('ritualWeights')
export const ritualComplexity = (): C.RitualComplexity[] => get('ritualComplexity')
export const ritualRevelation = (): C.RitualRevelation[] => get('ritualRevelation')
export const ritualContract = (): C.RitualContract[] => get('ritualContract')
export const skillLadder = (): C.SkillLadderTable => get('skillLadder')
export const ritualPreparation = (): C.RitualPreparation[] => get('ritualPreparation')
export const ritualFailures = (): C.RitualFailure[] => get('ritualFailures')
export const ritualSuccess = (): C.RitualSuccess[] => get('ritualSuccess')
export const ritualCriticals = (): C.RitualCritical[] => get('ritualCriticals')
export const nonStandardPathways = (): C.SequenceBlock[] => get('nonStandardPathways')
export const boonModifiers = (): C.BoonModifier[] => get('boonModifiers')
export const boonGranting = (): C.BoonGranting => get('boonGranting')
export const frightTriggers = (): C.FrightTrigger[] => get('frightTriggers')
export const frightEffects = (): C.FrightEffect[] => get('frightEffects')
export const ritualCorThresholds = (): C.RitualCorThreshold[] => get('ritualCorThresholds')
export const successLadder = (): C.SuccessBand[] => get('successLadder')
export const commonModifiers = (): C.CommonModifier[] => get('commonModifiers')
export const sequenceImmunity = (): C.SequenceImmunity[] => get('sequenceImmunity')
export const antiDivinationCounters = (): C.AntiDivinationCounter[] => get('antiDivinationCounters')
export const divinationAwareness = (): C.DivinationAwareness[] => get('divinationAwareness')
export const detectionOutcomes = (): C.DetectionOutcome[] => get('detectionOutcomes')
export const detectionModifiers = (): C.DetectionModifier[] => get('detectionModifiers')
export const antiDivinationTechniques = (): C.AntiDivinationTechnique[] => get('antiDivinationTechniques')
export const antiDivinationItems = (): C.AntiDivinationItem[] => get('antiDivinationItems')
export const sealedArtifactGrades = (): C.SealedArtifactGrade[] => get('sealedArtifactGrades')
export const coreMechanic = (): C.CoreBand[] => get('coreMechanic')
export const effortBurn = (): C.BurnablePool[] => get('effortBurn')
export const attributeCosts = (): C.AttributeCost[] => get('attributeCosts')
export const encumbrance = (): C.EncumbranceLevel[] => get('encumbrance')
export const restFile = (): C.RestFile => get('restFile')
export const quickContests = (): C.QuickContest[] => get('quickContests')
export const skillDefaults = (): C.SkillDefault[] => get('skillDefaults')
export const spiritualSenses = (): C.SpiritualSense[] => get('spiritualSenses')
export const spiAffinity = (): C.SpiAffinity[] => get('spiAffinity')
