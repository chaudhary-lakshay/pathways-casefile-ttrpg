import {
  Attributes, Character, controllingFromCode, difficultyFromCode, PurchasedSkill, SecondaryDelta, SkillKey, Trait,
  type Attr,
} from './model'
import { error, first, getValue } from './kt'
import * as Derive from './Derive'
import * as SecondaryCharacteristics from './SecondaryCharacteristics'
import * as SkillLadder from './SkillLadder'

export interface PremadeSkill {
  name: string
  specialization: string | null
  attr: string
  difficulty: string
  points: number
  publishedLevel: number
}

export const skillLabel = (s: PremadeSkill) => (s.specialization !== null ? `${s.name} (${s.specialization})` : s.name)

export const skillKey = (s: PremadeSkill): SkillKey =>
  SkillKey(s.name, s.specialization, controllingFromCode(s.attr), difficultyFromCode(s.difficulty))

export interface PremadeTrait {
  name: string
  cost: number
}

export interface PublishedValues {
  hp: number | null; fp: number | null; will: number | null; per: number | null
  spi: number | null; cor: number | null; basicSpeed: number | null
  basicMove: number | null; dodge: number | null
  bpTotal: number | null; totalSpent: number | null; unspent: number | null
  attributesSubtotal: number | null
  advantagesSubtotal: number | null
  disadvantagesSubtotal: number | null
  skillsSubtotal: number | null
}

export interface PremadeSheet {
  name: string
  attributes: Map<Attr, number>
  published: PublishedValues
  advantages: PremadeTrait[]
  disadvantages: PremadeTrait[]
  skills: PremadeSkill[]
}

const MORTAL_SPI = 0

function attributesOnly(sheet: PremadeSheet): Attributes {
  return Attributes({
    st: getValue(sheet.attributes, 'ST'), dx: getValue(sheet.attributes, 'DX'),
    iq: getValue(sheet.attributes, 'IQ'), ht: getValue(sheet.attributes, 'HT'),
  })
}

export function attributeLevelFor(sheet: PremadeSheet, skill: PremadeSkill): number {
  const attrs = attributesOnly(sheet)
  const secondaries = SecondaryCharacteristics.of(attrs, SecondaryDelta())
  return Derive.controllingLevel(controllingFromCode(skill.attr), attrs, secondaries, MORTAL_SPI)
}

export function levelFor(sheet: PremadeSheet, skill: PremadeSkill): number {
  return SkillLadder.levelFor(skill.points, difficultyFromCode(skill.difficulty), attributeLevelFor(sheet, skill))
}

export function toCharacter(sheet: PremadeSheet): Character {
  return Character({
    name: sheet.name,
    spi: MORTAL_SPI,
    attributes: attributesOnly(sheet),
    traits: [
      ...sheet.advantages.map((it) => Trait(it.name, it.cost, 'ADVANTAGE')),
      ...sheet.disadvantages.map((it) => Trait(it.name, it.cost, 'DISADVANTAGE')),
    ],
    skills: sheet.skills.map((it) => PurchasedSkill(skillKey(it), it.points)),
  })
}

export const NAMES = [
  'academic_researcher', 'antiquarian_dealer', 'apothecary', 'barmaid_brawler',
  'beat_cop', 'church_healer', 'dock_foreman', 'docks_bouncer', 'factory_foreman',
  'fortune_teller', 'hunter_tracker', 'journalist', 'lotm_blank_character_sheet',
  'military_police', 'monastery_scholar', 'night_watchman', 'nighthawk_recruit',
  'occult_scholar', 'pickpocket', 'private_investigator', 'street_acrobat',
  'underground_courier', 'university_librarian',
]

const files = import.meta.glob<any>('@fixtures/*.json', { eager: true, import: 'default' })

const PUBLISHED_KEYS: (keyof PublishedValues)[] = [
  'hp', 'fp', 'will', 'per', 'spi', 'cor', 'basicSpeed', 'basicMove', 'dodge', 'bpTotal', 'totalSpent',
  'unspent', 'attributesSubtotal', 'advantagesSubtotal', 'disadvantagesSubtotal', 'skillsSubtotal',
]

function load(slug: string): PremadeSheet {
  const key = Object.keys(files).find((k) => k.endsWith(`/${slug}.json`))
  if (!key) error(`premade sheet not on the classpath: ${slug}.json`)
  const j = files[key]
  const published = Object.fromEntries(PUBLISHED_KEYS.map((k) => [k, j.published[k] ?? null])) as unknown as PublishedValues
  return {
    name: j.name,
    attributes: new Map(Object.entries(j.attributes) as [Attr, number][]),
    published,
    advantages: (j.advantages ?? []).map((t: any) => ({ name: t.name, cost: t.cost })),
    disadvantages: (j.disadvantages ?? []).map((t: any) => ({ name: t.name, cost: t.cost })),
    skills: (j.skills ?? []).map((s: any) => ({
      name: s.name, specialization: s.specialization ?? null, attr: s.attr, difficulty: s.difficulty,
      points: s.points, publishedLevel: s.publishedLevel,
    })),
  }
}

let allLazy: PremadeSheet[] | null = null
export const all = (): PremadeSheet[] => (allLazy ??= NAMES.map(load))

export const byName = (display: string): PremadeSheet => first(all(), (it) => it.name === display)
