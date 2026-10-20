// app/.../EditedCharacter.kt: a premade with the player's saved edits applied.
import { canonical, coerceIn, eqIc } from '../../rules/kt'
import * as ContentLoader from '../../rules/content/ContentLoader'
import type { AppearanceLevel, CataloguedWeapon, SkillDefinition, WealthLevel } from '../../rules/content/schema.gen'
import {
  BASE_ATTRIBUTE, MAX_SECONDARY_DELTA_MAGNITUDE, PurchasedSkill, SecondaryDelta, SkillKey, Trait,
  type Attr, type Attributes, type BeyonderState, type Character,
} from '../../rules/model'
import * as Appearance from '../../rules/Appearance'
import * as CharmedObject from '../../rules/CharmedObject'
import * as Corruption from '../../rules/Corruption'
import * as Derive from '../../rules/Derive'
import type { ValueMap } from '../../rules/kt'
import * as Languages from '../../rules/Languages'
import type { OwnedLanguage } from '../../rules/Languages'
import * as PotionDigestion from '../../rules/PotionDigestion'
import type { DigestedCharacter } from '../../rules/PotionDigestion'
import * as Premades from '../../rules/Premades'
import type { PremadeSheet } from '../../rules/Premades'
import * as PurchaseCeilings from '../../rules/PurchaseCeilings'
import * as PurchasableTraits from '../../rules/PurchasableTraits'
import { InstancedPurchase } from '../../rules/PurchasableTraits'
import * as ScoreFloors from '../../rules/ScoreFloors'
import * as SecondaryCharacteristics from '../../rules/SecondaryCharacteristics'
import type { Secondaries } from '../../rules/SecondaryCharacteristics'
import * as TraitGrants from '../../rules/TraitGrants'
import * as Wealth from '../../rules/Wealth'
import * as Weapons from '../../rules/Weapons'
import { isEmpty, type SavedEdits, type SavedInstancedTrait, type SavedLanguage, type SavedSession } from './SessionStore'

/** Kotlin's `list - element`: the first equal element goes. */
export function minusFirst<T>(xs: readonly T[], x: T): T[] {
  const key = canonical(x)
  const at = xs.findIndex((it) => canonical(it) === key)
  return at < 0 ? [...xs] : [...xs.slice(0, at), ...xs.slice(at + 1)]
}

/** Kotlin's `list - collection`: every element in it goes. */
export function minusAll<T>(xs: readonly T[], gone: Iterable<T>): T[] {
  const keys = new Set([...gone].map(canonical))
  return xs.filter((it) => !keys.has(canonical(it)))
}

const sameSecondaries = (a: SecondaryDelta, b: SecondaryDelta) => canonical(a) === canonical(b)
const NO_DELTA = SecondaryDelta()

const purchaseOf = (t: SavedInstancedTrait) => InstancedPurchase(t)

export function applyTo(e: SavedEdits, base: Character): Character {
  if (isEmpty(e)) return base
  const attributes: Attributes = {
    ...base.attributes,
    st: e.st ?? base.attributes.st,
    dx: e.dx ?? base.attributes.dx,
    iq: e.iq ?? base.attributes.iq,
    ht: e.ht ?? base.attributes.ht,
  }
  return {
    ...base,
    attributes,
    secondaryDelta: SecondaryCharacteristics.plusSteps(base.secondaryDelta, new Map(Object.entries(e.secondaries))),
    traits: [...gainedFree(e, chosenTraits(e, base.traits), base.traits), ...e.quirks.map((it) => Trait(it, 0, 'QUIRK'))],
    skills: withStudy(e, [...base.skills, ...purchases(e, base)]),
    boon: e.boon,
  }
}

function gainedFree(e: SavedEdits, traits: Trait[], printed: Trait[]): Trait[] {
  const sheetLeft = new Map<string, number>()
  for (const t of printed) sheetLeft.set(t.name, (sheetLeft.get(t.name) ?? 0) + 1)
  return traits.map((it) => {
    const fromSheet = (sheetLeft.get(it.name) ?? 0) > 0
    if (fromSheet) sheetLeft.set(it.name, sheetLeft.get(it.name)! - 1)
    return !fromSheet && it.kind === 'DISADVANTAGE' && e.gained.includes(it.name) ? { ...it, cost: 0 } : it
  })
}

export const digestedWith = (c: Character, state: BeyonderState | null): DigestedCharacter | null =>
  PotionDigestion.of({ ...c, beyonder: state }, ContentLoader.pathways())
export const attributesWith = (c: Character, state: BeyonderState | null): Attributes =>
  Derive.attributes(c, digestedWith(c, state))
export const secondariesWith = (c: Character, state: BeyonderState | null): Secondaries =>
  digestedWith(c, state)?.secondaries ?? Derive.of(c).secondaries

export const boughtNote = (bought: string, played: string): string | null => (bought === played ? null : `${bought} bought`)
export const BEFORE_POTIONS = 'before potions'

export function shortfallOn(e: SavedEdits, sheet: PremadeSheet, state: BeyonderState | null): SecondaryDelta {
  const character = applyTo(e, Premades.toCharacter(sheet))
  const bought = ScoreFloors.shortfall(Derive.of(character).secondaries, character.traits)
  if (!sameSecondaries(bought, NO_DELTA)) return bought
  const digested = digestedWith(character, state)
  if (digested === null) return bought
  return ScoreFloors.shortfall(digested.secondaries, character.traits)
}

export function corCeilingOn(e: SavedEdits, sheet: PremadeSheet, state: BeyonderState | null): number {
  const character = applyTo(e, Premades.toCharacter(sheet))
  const walked = digestedWith(character, state)
  return Corruption.maxFor(walked?.secondaries?.will ?? Derive.of(character).secondaries.will, character.traits)
}

export function withinCeilings(e: SavedEdits): SavedEdits {
  const held = (v: number | null, attr: Attr) =>
    v === null ? null : coerceIn(v, BASE_ATTRIBUTE, PurchaseCeilings.attribute(attr, e.traits))
  return {
    ...e,
    st: held(e.st, 'ST'),
    dx: held(e.dx, 'DX'),
    iq: held(e.iq, 'IQ'),
    ht: held(e.ht, 'HT'),
    secondaries: Object.fromEntries(Object.entries(e.secondaries).map(([key, steps]) =>
      [key, coerceIn(steps, -MAX_SECONDARY_DELTA_MAGNITUDE, PurchaseCeilings.secondarySteps(key))])),
  }
}

export function boughtBack(e: SavedEdits, gap: SecondaryDelta): SavedEdits {
  if (sameSecondaries(gap, NO_DELTA)) return e
  const repaired = ContentLoader.secondaryCosts().map((it) => it.key).reduce((acc, key) => {
    const owed = SecondaryCharacteristics.stepsOf(gap, key)
    if (owed === 0) return acc
    const bought = coerceIn(acc[key] ?? 0, -MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE)
    if (bought + owed === 0) {
      const { [key]: _, ...rest } = acc
      return rest
    }
    return { ...acc, [key]: bought + owed }
  }, e.secondaries)
  return { ...e, secondaries: repaired }
}

export function repairedEdits(s: SavedSession, sheet: PremadeSheet): SavedEdits {
  let floored = withinCeilings(s.edits)
  for (;;) {
    const gap = shortfallOn(floored, sheet, s.beyonder)
    if (sameSecondaries(gap, NO_DELTA)) break
    floored = boughtBack(floored, gap)
  }
  const owed = s.lost || s.corrupted ? 0 : s.cor + 1 - corCeilingOn(floored, sheet, s.beyonder)
  if (owed <= 0) return floored
  return withinCeilings(boughtBack(floored, SecondaryDelta({ will: Math.min(owed, MAX_SECONDARY_DELTA_MAGNITUDE) })))
}

function chosenTraits(e: SavedEdits, printed: Trait[]): Trait[] {
  return [...traitsBeforeLanguages(e, printed), ...ownedLanguages(e, printed).map(Languages.toTrait)]
}

function traitsBeforeLanguages(e: SavedEdits, printed: Trait[]): Trait[] {
  const withBought = traitsBeforeInstanced(e, printed)
  return [...withBought, ...instancedRows(e, withBought).flatMap(([, t]) => (t ? [t] : []))]
}

export const ownedLanguages = (e: SavedEdits, printed: Trait[]): OwnedLanguage[] =>
  Languages.owned(
    e.languages.map((it) => ({ language: it.language, proficiency: it.proficiency, form: it.form })),
    traitsBeforeLanguages(e, printed),
  ).flatMap(([, o]) => (o ? [o] : []))

export const pricedWith = (e: SavedEdits, bought: SavedLanguage, printed: Trait[]): OwnedLanguage | null =>
  ownedLanguages(withLanguage(e, bought), printed).find((it) => eqIc(it.purchase.language, bought.language)) ?? null

export function withLanguage(e: SavedEdits, bought: SavedLanguage): SavedEdits {
  if (!e.languages.some((it) => eqIc(it.language, bought.language))) return { ...e, languages: [...e.languages, bought] }
  return { ...e, languages: e.languages.map((it) => (eqIc(it.language, bought.language) ? bought : it)) }
}

export function instancedRows(e: SavedEdits, held: Trait[]): [SavedInstancedTrait, Trait | null][] {
  const resolved = PurchasableTraits.resolved(e.instanced.map(purchaseOf), held)
  return e.instanced.map((it, i) => [it, resolved[i][1]])
}

export function traitsBeforeInstanced(e: SavedEdits, printed: Trait[]): Trait[] {
  const wealth = wealthLevel(e)
  const withWealth = wealth ? Wealth.applied(printed, wealth) : printed
  const looks = appearanceLevel(e)
  const withLooks = looks ? Appearance.applied(withWealth, looks) : withWealth
  return [...withLooks, ...boughtTraits(e, withLooks)]
}

function boughtTraits(e: SavedEdits, held: Trait[]): Trait[] {
  const chosen: Trait[] = []
  for (const name of e.traits) {
    const row = PurchasableTraits.named(name)
    if (row === null) continue
    if (!PurchasableTraits.canBuy(row, [...held, ...chosen])) continue
    chosen.push(charmed(e, PurchasableTraits.toTrait(row)))
  }
  return chosen
}

function charmed(e: SavedEdits, trait: Trait): Trait {
  const skill = trait.name === CharmedObject.ROW ? e.charmedSkill : null
  if (skill === null) return trait
  if (definitionOf(skill.name, skill.specialization) === null) return trait
  return { ...trait, name: CharmedObject.heldAs(skill.name, skill.specialization) }
}

export const liveSkillLevels = (c: Character): ValueMap<SkillKey, number> =>
  PotionDigestion.of(c, ContentLoader.pathways())?.skillLevels ?? Derive.of(c).skillLevels

export function carriedWeapons(e: SavedEdits): CataloguedWeapon[] {
  const printed = Weapons.all()
  const found = e.weapons.flatMap((saved) => {
    const w = printed.find((it) => eqIc(it.name, saved))
    return w ? [w] : []
  })
  return found.filter((w, i) => found.findIndex((it) => it.name === w.name) === i)
}

export const appearanceLevel = (e: SavedEdits): AppearanceLevel | null =>
  e.appearance === null ? null : Appearance.all().find((it) => eqIc(it.level, e.appearance)) ?? null
export const wealthLevel = (e: SavedEdits): WealthLevel | null =>
  e.wealth === null ? null : Wealth.all().find((it) => eqIc(it.status, e.wealth)) ?? null
export const wealthShownFor = (e: SavedEdits, c: Character): WealthLevel | null => wealthLevel(e) ?? Wealth.levelOf(c.traits)

export const skillLevelOf = (name: string, levels: ValueMap<SkillKey, number>): number | null =>
  [...levels.entries()].find(([k]) => eqIc(k.name, name))?.[1] ?? null
export const liveLevelOf = (key: SkillKey, levels: ValueMap<SkillKey, number>, fallback: number): number =>
  liveLevelOrNull(key, levels) ?? fallback
export const liveLevelOrNull = (key: SkillKey, levels: ValueMap<SkillKey, number>): number | null =>
  [...levels.entries()].find(([k]) => sameSkillAs(k, key))?.[1] ?? null

export const purchasedKeys = (e: SavedEdits): SkillKey[] =>
  e.skills.flatMap((saved) => {
    const d = definitionOf(saved.name, saved.specialization)
    return d ? [SkillKey(d.name, saved.specialization, d.attr, d.difficulty)] : []
  })

const sameSkillAs = (a: SkillKey, b: SkillKey) =>
  eqIc(a.name, b.name) && a.specialization === b.specialization && a.difficulty === b.difficulty

export function grantedSkills(sheet: PremadeSheet, e: SavedEdits, levels: ValueMap<SkillKey, number>): [SkillKey, number][] {
  const shown = [...sheet.skills.map(Premades.skillKey), ...purchasedKeys(e)]
  return [...levels.entries()].filter(([k]) => !shown.some((it) => sameSkillAs(k, it)))
}

export function definitionOf(name: string, specialization: string | null = null): SkillDefinition | null {
  const byName = ContentLoader.skills().filter((it) => eqIc(it.name, name))
  if (specialization === null) return byName[0] ?? null
  return byName.find((it) => it.specialization === specialization) ?? byName.find((it) => it.specializable) ?? null
}

function purchases(e: SavedEdits, base: Character): PurchasedSkill[] {
  const owned = new Set(base.skills.map((it) => canonical([it.key.name.toLowerCase(), it.key.specialization])))
  return e.skills.flatMap((saved) => {
    const d = definitionOf(saved.name, saved.specialization)
    if (d === null) return []
    const key = SkillKey(d.name, saved.specialization ?? d.specialization, d.attr, d.difficulty)
    return owned.has(canonical([key.name.toLowerCase(), key.specialization])) ? [] : [PurchasedSkill(key, saved.points)]
  })
}

function withStudy(e: SavedEdits, skills: PurchasedSkill[]): PurchasedSkill[] {
  return skills.map((skill) => {
    const n = e.studied.find((it) => eqIc(it.name, skill.key.name) && it.specialization === skill.key.specialization)?.points ?? 0
    return n === 0 ? skill : { ...skill, points: skill.points + n, studied: skill.studied + n }
  })
}

const FIELD: Record<Attr, 'st' | 'dx' | 'iq' | 'ht'> = { ST: 'st', DX: 'dx', IQ: 'iq', HT: 'ht' }
export const levelOf = (e: SavedEdits, attr: Attr): number | null => e[FIELD[attr]]
export const withAttribute = (e: SavedEdits, attr: Attr, level: number): SavedEdits => ({ ...e, [FIELD[attr]]: level })

export const traitsInPlay = (e: SavedEdits, sheet: PremadeSheet | null, state: BeyonderState | null): Trait[] =>
  TraitGrants.withGrantedTraits(
    sheet !== null
      ? applyTo(e, Premades.toCharacter(sheet)).traits
      : e.traits.flatMap((it) => {
        const row = PurchasableTraits.named(it)
        return row ? [PurchasableTraits.toTrait(row)] : []
      }),
    state,
    e.boon,
  )

export function keepsPrerequisites(e: SavedEdits, sheet: PremadeSheet | null, state: BeyonderState | null): boolean {
  const held = traitsInPlay(e, sheet, state)
  return !e.traits.some((it) => {
    const row = PurchasableTraits.named(it)
    return row !== null && PurchasableTraits.missingPrerequisite(row, held) !== null
  })
}

