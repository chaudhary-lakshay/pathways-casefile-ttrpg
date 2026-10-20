import { eqIc, lazy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import { Trait, type BeyonderState, type BoonState } from './model'
import * as Appearance from './Appearance'
import * as NonStandardPathways from './NonStandardPathways'
import * as TE from './TraitEffects'
import { catalogueName, levelOf } from './TraitNames'

/** Kotlin's `Pair<String, Int>` key, as `pathway|sequence`. */
const key = (pathway: string, sequence: number) => `${pathway}|${sequence}`
const unkey = (k: string): [string, number] => {
  const i = k.lastIndexOf('|')
  return [k.slice(0, i), Number(k.slice(i + 1))]
}

const EIDETIC_MEMORY = 'Eidetic Memory'
const advantages = (...names: string[]) => names.map((it) => Trait(it, 0, 'ADVANTAGE'))

const GRANTED_TRAITS = lazy(() => new Map<string, Trait[]>([
  [key('Fool', 8), advantages(TE.PERFECT_BALANCE)],
  [key('Visionary', 9), advantages(TE.ACUTE_VISION, EIDETIC_MEMORY)],
  [key('Visionary', 8), advantages(TE.INCISIVE_VISION)],
  [key('Visionary', 7), advantages(TE.ACUTE_SMELL)],
  [key('Sun', 9), advantages(TE.FIT)],
  [key('Sun', 7), advantages(TE.VERY_FIT)],
  [key('Tyrant', 9), advantages(TE.PERFECT_BALANCE)],
  [key('Tyrant', 7), advantages('Absolute Direction', EIDETIC_MEMORY)],
  [key('White Tower', 9), advantages(EIDETIC_MEMORY)],
  [key('Death', 9), advantages('Cold Resistance')],
  [key('Demoness', 9), advantages(TE.ACUTE_VISION, TE.ACUTE_HEARING)],
  [key('Demoness', 6), advantages(TE.GREATER_ACUTE_VISION)],
  [key('Red Priest', 9), advantages(TE.ACUTE_VISION, TE.ACUTE_HEARING)],
  [key('Red Priest', 6), advantages(TE.ACUTE_VISION, TE.ACUTE_HEARING, TE.ACUTE_TASTE_SMELL, TE.ACUTE_TOUCH)],
  [key('Justiciar', 9), advantages('Voice')],
  [key('Justiciar', 8), advantages(EIDETIC_MEMORY)],
  [key('Hanged Man', 8), advantages(TE.ACUTE_HEARING)],
  [key('Twilight Giant', 8), advantages(TE.HIGH_PAIN_THRESHOLD)],
  [key('Hermit', 8), advantages(TE.COMBAT_REFLEXES)],
  [key('Wheel of Fortune', 8), advantages('Danger Sense')],
  [key('Error', 7), advantages(EIDETIC_MEMORY)],
  [key('Error', 8), advantages(`${TE.CHARISMA} 1`)],
  [key('Demoness', 8), advantages(`${TE.CHARISMA} 2`)],
  [key('Demoness', 7), advantages(`${TE.CHARISMA} 1`, 'Handsome / Beautiful')],
]))

// Kotlin's mapOf prints Demoness 6 twice: the key keeps its FIRST position, the LAST value.
const GRANTED = lazy(() => {
  const m = GRANTED_TRAITS()
  m.set(key('Demoness', 6), advantages(TE.GREATER_ACUTE_VISION, `${TE.CHARISMA} 1`, 'Very Handsome / Very Beautiful'))
  m.set(key('Abyss', 8), [
    'Compulsive Behavior (Indulge Evil Desires) SC 6', 'Unsettling Appearance', 'Bloodlust', 'Callous',
  ].map((it) => Trait(it, 0, 'DISADVANTAGE')))
  return m
})

const REFUSED_GRANTS = lazy(() => new Map<string, string>([
  [`${key('Fool', 9)}|${EIDETIC_MEMORY}`, '"continuously grows as the potion digests" prints no number, not the catalogue\'s +5'],
  [`${key('Red Priest', 9)}|Danger Sense`, '"Danger Sense (partial)" is a variant the catalogue does not print'],
  [`${key('Red Priest', 8)}|Fast Healer`, '"Fast Healer (Limited)": 1 HP per 2 days, not the catalogue\'s per day'],
  [`${key('Tyrant', 7)}|${TE.PERFECT_BALANCE}`, '"Perfect Balance (Enhanced)" is a variant; Tyrant 9\'s own Perfect Balance is granted'],
]))

const DRAWBACK_CHOICES = new Map<string, string[]>([
  [key('Hanged Man', 9), ['Paranoia', 'Hallucinations', 'Compulsion (pathway)']],
])

const BOON_TRAITS = lazy(() => new Map<string, Trait[]>([
  [key('Chaos Mist', 9), advantages(`${TE.CHARISMA} 2`)],
  [key('Second Law', 8), advantages(TE.ACUTE_HEARING)],
  [key('Sublunary Eye', 8), advantages(TE.ACUTE_VISION)],
  [key('Patriarch', 8), advantages(TE.FIT)],
]))

const REFUSED_BOON_GRANTS = new Map<string, string>([
  [`${key('Eternal Aeon (Inevitability)', 9)}|Double-Jointed`,
    '"+2 to Escape and any roll involving contortion", not the catalogue\'s +5 to Climbing and Escape'],
  [`${key('Monarch of Decay', 9)}|Hard Stomach`,
    '"+4 to HT rolls to avoid food-borne illness", not the catalogue\'s disgusting environments'],
  [`${key('Sublunary Eye', 8)}|Experienced Investigator`,
    '"+1 to all Investigation, Observation, and Search rolls", not the catalogue\'s yes/no question'],
  [`${key('Patriarch', 9)}|Greed`, '"Sin of Greed" is the block\'s own row, with its own Will penalties'],
  [`${key('Patriarch', 8)}|Greed`, '"Replaces Sin of Greed" names the Sequence 9 row, not the catalogue\'s'],
])

/** The tables as Kotlin's `Map<Pair<String, Int>, …>`: each key unpacked. */
const unpacked = <V>(m: Map<string, V>): [[string, number], V][] => [...m].map(([k, v]) => [unkey(k), v])
const unpackedRefusals = (m: Map<string, string>): [[[string, number], string], string][] =>
  [...m].map(([k, v]) => {
    const parts = k.split('|')
    return [[[parts[0], Number(parts[1])], parts.slice(2).join('|')], v]
  })

export const boonTraitTable = () => unpacked(BOON_TRAITS())
export const refusedBoonGrants = () => unpackedRefusals(REFUSED_BOON_GRANTS)
export const grantedTraitTable = () => unpacked(GRANTED())
export const drawbackChoiceTable = () => unpacked(DRAWBACK_CHOICES)
export const refusedGrants = () => unpackedRefusals(REFUSED_GRANTS())

const reaching = <V>(m: Map<string, V>, pathway: string, sequence: number): V[] =>
  [...m].filter(([k]) => { const [p, s] = unkey(k); return p === pathway && s >= sequence }).map(([, v]) => v)

export const drawbackChoices = (state: BeyonderState): string[] => reaching(DRAWBACK_CHOICES, state.pathway, state.sequence).flat()

export function grantedTraits(state: BeyonderState | null, boon: BoonState | null = null): Trait[] {
  const drawback = state?.drawback ?? null
  const chosen = state !== null && drawback !== null && drawbackChoices(state).includes(drawback)
    ? [Trait(drawback, 0, 'DISADVANTAGE')]
    : []
  const potions = state === null ? [] : reaching(GRANTED(), state.pathway, state.sequence).flat()
  const boons = boon === null ? [] : reaching(BOON_TRAITS(), NonStandardPathways.patronOf(boon.pathway), boon.sequence).flat()
  const groups = new Map<string, Trait[]>()
  for (const t of [...potions, ...boons]) {
    const k = catalogueName(t.name).toLowerCase()
    groups.set(k, [...(groups.get(k) ?? []), t])
  }
  const rows = [...groups.values()].map((rs) => {
    const first = rs[0]
    return !isLevelled(first) ? first : levelled(first, rs.reduce((s, it) => s + levelOf([it], catalogueName(it.name))!, 0))
  })
  return [...chosen, ...rows.filter((it) => !isFace(it) || it.name === Appearance.played(rows)?.level)]
}

export function withGrantedTraits(traits: Trait[], state: BeyonderState | null, boon: BoonState | null = null): Trait[] {
  const granted = new Map<string, Trait>()
  for (const t of grantedTraits(state, boon)) granted.set(catalogueName(t.name).toLowerCase(), t)
  const held = new Set(traits.map((it) => catalogueName(it.name).toLowerCase()))
  const merged = traits.map((owned) => {
    const name = catalogueName(owned.name)
    const grant = granted.get(name.toLowerCase())
    if (grant === undefined || !isLevelled(grant)) return owned
    return levelled(owned, levelOf([owned], name)! + levelOf([grant], name)!)
  })
  const added = [...granted]
    .filter(([k]) => !held.has(k))
    .map(([, v]) => v)
    .filter((it) => !isFace(it) || Appearance.played([...traits, it])?.level === it.name)
  return [...merged, ...added]
}

const isFace = (trait: Trait) => Appearance.played([trait]) !== null

export function grantedLevelsIn(merged: Trait, bought: Trait[]): number | null {
  const name = catalogueName(merged.name)
  const had = levelOf(bought, name)
  if (had === null) return null
  const more = levelOf([merged], name)! - had
  return more > 0 ? more : null
}

const isLevelled = (trait: Trait) =>
  ContentLoader.advantages().some((it) => it.perLevel && eqIc(it.name, catalogueName(trait.name)))

function levelled(trait: Trait, level: number): Trait {
  const name = catalogueName(trait.name)
  return { ...trait, name: `${name} ${Math.min(level, TE.maxLevelsFor(name) ?? level)}` }
}
