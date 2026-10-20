import { Trait, type BeyonderState } from './model'
import { eqIc, floorDiv, removeSuffix, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { LanguageEntry, LanguageProficiency } from './content/schema.gen'
import * as TraitNames from './TraitNames'

export interface LanguagePurchase {
  language: string
  proficiency: string
  form: string
}

export const purchaseLabel = (p: LanguagePurchase) =>
  [p.language, p.proficiency, p.form !== BOTH_FORMS ? p.form.toLowerCase() : null].filter((it) => it !== null).join(', ')

export interface OwnedLanguage {
  purchase: LanguagePurchase
  type: string
  cost: number
  freedBy: string | null
  listed: number
  isHalvedByTalent: boolean
}

export const isFree = (o: OwnedLanguage) => o.freedBy !== null

export function costLine(o: OwnedLanguage): string {
  if (o.freedBy !== null) return `free with ${o.freedBy} (${o.listed} listed)`
  const moved = [
    o.purchase.form !== BOTH_FORMS ? 'half for one form' : null,
    o.isHalvedByTalent ? `halved by ${TALENT}, rounded up` : null,
  ].filter((it) => it !== null)
  const points = o.cost === 1 ? '1 pt' : `${o.cost} pts`
  return moved.length === 0 ? points : `${points} (${o.listed} listed; ${moved.join('; ')})`
}

export const ownedLabel = (o: OwnedLanguage) => purchaseLabel(o.purchase)

export const toTrait = (o: OwnedLanguage): Trait =>
  Trait(`${removeSuffix(ROW, ' (specify)')} (${ownedLabel(o)})`, o.cost, 'ADVANTAGE')

export const ROW = 'Language (specify)'
export const BOTH_FORMS = 'Spoken + Written'
export const SPOKEN_ONLY = 'Spoken only'
export const WRITTEN_ONLY = 'Written only'
export const TALENT = 'Language Talent'
export const MULTILINGUAL = 'Multilingual'
export const MULTILINGUAL_LANGUAGES = 2
export const DEAD_LANGUAGE_FLUENCY = 'Dead Language Fluency'
export const DEAD_LANGUAGES = new Set(['Ancient Hermes', 'Ancient Feysac'])
export const PRICING_TRAITS = new Set([TALENT, MULTILINGUAL, DEAD_LANGUAGE_FLUENCY])
export const FLUENT = 'Native'

export const forms = () => [BOTH_FORMS, SPOKEN_ONLY, WRITTEN_ONLY]

export function owned(purchases: LanguagePurchase[], held: Trait[]): [LanguagePurchase, OwnedLanguage | null][] {
  const talentDivisor = holds(held, TALENT) ? 2 : 1
  let free = holds(held, MULTILINGUAL) ? MULTILINGUAL_LANGUAGES : 0
  let dead = holds(held, DEAD_LANGUAGE_FLUENCY) ? 1 : 0
  const seen = new Set<string>()
  return purchases.map((purchase): [LanguagePurchase, OwnedLanguage | null] => {
    const entry = all().find((it) => eqIc(it.name, purchase.language))
    const row = entry !== undefined
      ? proficiencies(entry.type).find((it) => eqIc(it.proficiency, purchase.proficiency))
      : undefined
    const formDivisor = purchase.form === BOTH_FORMS ? 1
      : purchase.form === SPOKEN_ONLY || purchase.form === WRITTEN_ONLY ? 2 : null
    if (entry === undefined || row === undefined || formDivisor === null || seen.has(entry.name)) {
      return [purchase, null]
    }
    seen.add(entry.name)
    const fluentInBoth = row.proficiency === FLUENT && purchase.form === BOTH_FORMS
    let freedBy: string | null = null
    if (fluentInBoth) {
      if (free > 0 && entry.type === COMMON) {
        freedBy = MULTILINGUAL
        free--
      } else if (dead > 0 && DEAD_LANGUAGES.has(entry.name)) {
        freedBy = DEAD_LANGUAGE_FLUENCY
        dead--
      }
    }
    const cost = freedBy !== null ? 0 : -floorDiv(-row.points, formDivisor * talentDivisor)
    return [purchase, {
      purchase: { ...purchase, language: entry.name, proficiency: row.proficiency },
      type: entry.type,
      cost,
      freedBy,
      listed: row.points,
      isHalvedByTalent: freedBy === null && talentDivisor > 1,
    }]
  })
}

export const traits = (purchases: LanguagePurchase[], held: Trait[]): Trait[] =>
  owned(purchases, held).flatMap(([, o]) => (o !== null ? [toTrait(o)] : []))

const holds = (held: Trait[], name: string) => TraitNames.levelOf(held, name) !== null

export const COMMON = 'Common'
export const MYSTICAL = 'Mystical'

export const all = (): LanguageEntry[] => ContentLoader.languages()
export const common = () => all().filter((it) => it.type === COMMON)
export const mystical = () => all().filter((it) => it.type === MYSTICAL)

const GRANTED: [[string, number], LanguagePurchase][] = [
  [['Justiciar', 6], { language: 'Ancient Hermes', proficiency: FLUENT, form: BOTH_FORMS }],
]

export function grantedBy(state: BeyonderState | null): LanguagePurchase[] {
  if (state === null) return []
  const found = GRANTED.filter(([[pathway, sequence]]) => pathway === state.pathway && sequence >= state.sequence).map((it) => it[1])
  return found.filter((p, i) => found.findIndex((q) => JSON.stringify(q) === JSON.stringify(p)) === i)
}

export const proficiencies = (type: string): LanguageProficiency[] =>
  ContentLoader.languageProficiencies().filter((it) => eqIc(it.type, type))

export function proficiency(type: string, prof: string): LanguageProficiency {
  const found = proficiencies(type).find((it) => eqIc(it.proficiency, prof))
  if (found !== undefined) return found
  const rows = proficiencies(type)
  return require(false, () =>
    `no '${prof}' row in the ${type} language table; the book prints ` +
    (rows.length > 0 ? rows.map((it) => it.proficiency).join(', ') : `no such table, only ${COMMON} and ${MYSTICAL}`),
  ) as never
}

export function ritualModifier(prof: LanguageProficiency | null): number {
  if (prof === null) return 0
  require(canPerformRituals(prof), () =>
    prof.canPerformRituals === false
      ? `${prof.proficiency} prints 'Cannot perform rituals in the language'; there is no roll to modify`
      : `${prof.type} languages are not ritual languages in this book; only the mystical table grants ritual power`,
  )
  return prof.ritualModifier ?? 0
}

export const canPerformRituals = (prof: LanguageProficiency) => prof.canPerformRituals === true

export const notes = (): string[] => ContentLoader.languageNotes().map((it) => it.text)
