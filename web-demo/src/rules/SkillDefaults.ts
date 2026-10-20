// What an untrained character may roll, and at what: the book's default table
// and the few lines that override it.
import { attrCode, DIFFICULTY, SkillKey, SPI, type Character, type ControllingAttribute, type Trait } from './model'
import { canonical, eqIc, getValue, lazy, sortedBy, ValueMap } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { CataloguedWeapon, SkillDefinition } from './content/schema.gen'
import * as Damage from './Damage'
import * as Weapons from './Weapons'
import * as TraitEffects from './TraitEffects'
import * as TraitNames from './TraitNames'
import * as PotionDigestion from './PotionDigestion'
import * as Derive from './Derive'
import * as SkillLadder from './SkillLadder'
import * as Divination from './Divination'

export const BRAWLING = 'Brawling'
export const PHYSICIAN = 'Physician'
export const MEDICAL_TRAINING = 'Medical Training (Informal)'
export const MEDICAL_TRAINING_PENALTY = -2
export const WRESTLING = 'Wrestling'
export const BRAWLING_PENALTY = -2

const WEAPON_SCOPES = new Map<string, string>([
  ['Twilight Giant\u0000Warrior', 'any weapon at effective DX, DX+2 after repeated use'],
  ['Twilight Giant\u0000Pugilist', 'a melee weapon at DX+2'],
  ['Twilight Giant\u0000Weapon Master', 'any weapon at DX+4'],
  ['Mother\u0000Harvest Priest', 'a farming or gardening tool used as a weapon at no default penalty'],
  ['Abyss\u0000Unwinged Angel (Coldblooded)', 'any firearm at effective DX+1'],
])
const scopeKey = (pathway: string, name: string) => `${pathway}\u0000${name}`

export const WEAPON_LINES = (): [string, string][] => [...WEAPON_SCOPES.keys()].map((k) => k.split('\u0000') as [string, string])

/** The Divination Arts skill name, as `Divination.SKILL` (a literal here: read at import). */
const DIVINATION_SKILL = 'Divination Arts'

export const NOT_LISTED =
  `Not listed: Very Hard skills and ${WRESTLING}, which the book gives no ` +
  `default, and ${DIVINATION_SKILL}, rolled from the Divine tile. The GM may rule that a skill needs specialist ` +
  'knowledge before it can be attempted at all; this app records no ' +
  'such ruling.'

export const PLAIN_ROLL =
  'A roll from this list is the skill alone. For an untrained punch, or ' +
  'a weapon you have no skill with, use Attack, which carries the ' +
  'damage. For an Acrobatic Dodge use Defend, which carries its ±2 ' +
  'to the Dodge row.'

export function weaponNote(pathway: string, sequence: number): string | null {
  const names = weaponLinesFor(pathway, sequence)
  if (names.length === 0) return null
  const lines = names.map((name) => `${name}: ${getValue(WEAPON_SCOPES, scopeKey(pathway, name))}`).join('; ')
  return `Not applied here: ${lines}. This list does not know what is in hand.`
}

export interface Untrained {
  key: SkillKey
  level: number
  source: string
}

const inList = (w: CataloguedWeapon, xs: CataloguedWeapon[]) => xs.some((it) => canonical(it) === canonical(w))

export const attacks = (untrained: Untrained[], carried: CataloguedWeapon[], owned: ValueMap<SkillKey, number>): Untrained[] =>
  untrained.filter((it) =>
    Damage.attacksFor(it.key.name, true).length > 0 || attackWeapons(it.key, it.level, carried, owned).length > 0)

export function attackWeapons(key: SkillKey, untrainedAt: number | null, carried: CataloguedWeapon[], owned: ValueMap<SkillKey, number>): CataloguedWeapon[] {
  const found = Weapons.forSkill(key).filter((weapon) =>
    untrainedAt === null || (inList(weapon, carried) && (Weapons.levelFor(weapon, owned) ?? Number.MIN_SAFE_INTEGER) < untrainedAt))
  return sortedBy(found, (it) => (inList(it, carried) ? 0 : 1))
}

export const COMBAT_SKILLS = 'COMBAT SKILLS'

export const ownedAttacks = (skillLevels: ValueMap<SkillKey, number>) =>
  new ValueMap([...skillLevels.entries()].filter(([k]) => TraitEffects.categoryOf(k) === COMBAT_SKILLS))

const FULL_PROFICIENCY = 2

export type WeaponMastery = 'WARRIOR' | 'PUGILIST' | 'WEAPON_MASTER'
export const WEAPON_MASTERIES: WeaponMastery[] = ['WARRIOR', 'PUGILIST', 'WEAPON_MASTER']

const MASTERY: Record<WeaponMastery, { block: string; immediateMelee: number; immediateRanged: number }> = {
  WARRIOR: { block: 'Warrior', immediateMelee: 0, immediateRanged: 0 },
  PUGILIST: { block: 'Pugilist', immediateMelee: FULL_PROFICIENCY, immediateRanged: 0 },
  WEAPON_MASTER: { block: 'Weapon Master', immediateMelee: 4, immediateRanged: 4 },
}

export const masteryBlock = (m: WeaponMastery) => MASTERY[m].block
const immediate = (m: WeaponMastery, isRanged: boolean) => (isRanged ? MASTERY[m].immediateRanged : MASTERY[m].immediateMelee)
export const warmsUp = (m: WeaponMastery, isRanged: boolean) => immediate(m, isRanged) < FULL_PROFICIENCY
export const bonusOverDx = (m: WeaponMastery, isRanged: boolean, warmedUp: boolean) =>
  warmedUp && warmsUp(m, isRanged) ? FULL_PROFICIENCY : immediate(m, isRanged)
export function masterySourceFor(m: WeaponMastery, isRanged: boolean, warmedUp: boolean): string {
  const bonus = bonusOverDx(m, isRanged, warmedUp)
  const dx = bonus === 0 ? 'DX' : `DX+${bonus}`
  return `${dx}, ${MASTERY[m].block}` + (warmedUp && warmsUp(m, isRanged) ? ' after repeated use' : '')
}

export function masteryFor(pathway: string, sequence: number): WeaponMastery | null {
  const walked = ContentLoader.pathways().filter((it) => it.pathway === pathway && it.sequence >= sequence).map((it) => it.name)
  let found: WeaponMastery | null = null
  for (const m of WEAPON_MASTERIES) if (walked.includes(MASTERY[m].block)) found = m
  return found
}

export function wieldedAt(weapon: CataloguedWeapon, skillLevels: ValueMap<SkillKey, number>, mastery: WeaponMastery | null, dx: number, warmedUp = false): number | null {
  const own = Weapons.levelFor(weapon, skillLevels)
  if (mastery === null) return own
  const floor = dx + bonusOverDx(mastery, false, warmedUp)
  return Math.max(own ?? floor, floor)
}

export function mastered(level: number, weaponInHand: boolean, mastery: WeaponMastery | null, dx: number, isRanged = false, warmedUp = false): number | null {
  if (!weaponInHand || mastery === null) return null
  const floor = dx + bonusOverDx(mastery, isRanged, warmedUp)
  return level < floor ? floor : null
}

const HARVEST_PRIEST = 'Harvest Priest'

export function toolNote(pathway: string, sequence: number): string | null {
  const it = weaponLinesFor(pathway, sequence).find((x) => x === HARVEST_PRIEST)
  if (it === undefined) return null
  return `Not applied here: ${it}'s "no default penalty" with a farming or ` +
    'gardening tool. This app does not know which weapon is one.'
}

export function offered(
  owned: ValueMap<SkillKey, number>,
  attributeLevel: (a: ControllingAttribute) => number,
  defaultBonus: number,
  traits: Trait[],
): Untrained[] {
  const printed = ContentLoader.skills()
  const suffix = defaultBonus !== 0 ? `, +${defaultBonus} ${TraitEffects.VERSATILE}` : ''
  const keys = owned.keys()
  return printed
    .filter((row) => {
      const onlyRow = printed.filter((it) => it.name === row.name).length === 1
      return !keys.some((it) => covers(it, row, onlyRow))
    })
    .flatMap((skill): Untrained[] => {
      const key = SkillKey(skill.name, skill.specialization, skill.attr, skill.difficulty)
      const granted = grantedBy(skill, traits, attributeLevel)
      if (granted !== null) return [{ key, level: granted[0], source: granted[1] }]
      const d = defaultOf(skill, key, attributeLevel, defaultBonus)
      return d !== null ? [{ ...d, source: d.source + suffix }] : []
    })
}

function grantedBy(skill: SkillDefinition, traits: Trait[], attributeLevel: (a: ControllingAttribute) => number): [number, string] | null {
  const level = traitLevel(skill.name, skill.attr, traits, attributeLevel)
  return level !== null ? [level, `${attrCode(skill.attr)}${MEDICAL_TRAINING_PENALTY}, ${MEDICAL_TRAINING}`] : null
}

function traitLevel(name: string, a: ControllingAttribute, traits: Trait[], attributeLevel: (a: ControllingAttribute) => number): number | null {
  return name === PHYSICIAN && TraitNames.levelOf(traits, MEDICAL_TRAINING) !== null
    ? attributeLevel(a) + MEDICAL_TRAINING_PENALTY
    : null
}

export function unboughtLevel(key: SkillKey, attributeLevel: (a: ControllingAttribute) => number, defaultBonus: number, traits: Trait[]): number | null {
  const found = [untrainedLevel(key, attributeLevel, defaultBonus), traitLevel(key.name, key.attr, traits, attributeLevel)]
    .filter((it): it is number => it !== null)
  return found.length === 0 ? null : Math.max(...found)
}

function covers(owned: SkillKey, row: SkillDefinition, onlyRow: boolean): boolean {
  if (!eqIc(owned.name, row.name)) return false
  if (owned.specialization === null ? row.specialization === null : eqIc(owned.specialization, row.specialization)) return true
  return onlyRow && !row.specializable && (owned.specialization === null || row.specialization === null)
}

export function offeredTo(character: Character): Untrained[] {
  const walked = PotionDigestion.of(character, ContentLoader.pathways())
  const attributes = Derive.attributes(character, walked)
  const secondaries = walked?.secondaries ?? Derive.of(character).secondaries
  const spi = walked?.effectiveSpi ?? character.spi
  return offered(
    walked?.skillLevels ?? Derive.of(character).skillLevels,
    (it) => Derive.controllingLevel(it, attributes, secondaries, spi),
    TraitEffects.defaultBonus(character.traits),
    character.traits,
  )
}

function defaultOf(skill: SkillDefinition, key: SkillKey, attributeLevel: (a: ControllingAttribute) => number, defaultBonus: number): Untrained | null {
  if (skill.name === Divination.SKILL) return null
  const level = untrainedLevel(key, attributeLevel, defaultBonus)
  if (level === null) return null
  const source = skill.name === BRAWLING
    ? `${attrCode(skill.attr)}${BRAWLING_PENALTY}, the book's own line for ${BRAWLING}`
    : `${attrCode(skill.attr)}${DIFFICULTY[skill.difficulty].defaultPenalty}`
  return { key, level, source }
}

export function untrainedLevel(key: SkillKey, attributeLevel: (a: ControllingAttribute) => number, defaultBonus: number): number | null {
  let base: number | null
  if (key.name === BRAWLING) base = attributeLevel(key.attr) + BRAWLING_PENALTY
  else if (key.name === WRESTLING) base = null
  else if (key.name === Divination.SKILL) base = attributeLevel(SPI) + Divination.DEFAULT_PENALTY
  else base = SkillLadder.defaultFor(key.difficulty, attributeLevel(key.attr))
  return base === null ? null : base + defaultBonus
}

const weaponLines = lazy(() => new Set(WEAPON_SCOPES.keys()))

export const weaponLinesFor = (pathway: string, sequence: number): string[] =>
  sortedBy(ContentLoader.pathways().filter((it) => it.pathway === pathway && it.sequence >= sequence), (it) => it.sequence, true)
    .filter((it) => weaponLines().has(scopeKey(it.pathway, it.name)))
    .map((it) => it.name)
