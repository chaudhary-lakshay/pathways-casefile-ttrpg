import { eqIc, isBlank, lazy, removeSuffix, sortedBy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { TraitDefinition } from './content/schema.gen'
import { blessingAsTrait } from './content/members'
import { Trait } from './model'
import * as TE from './TraitEffects'
import * as TraitNames from './TraitNames'

export interface PurchasableTrait {
  name: string
  cost: number
  advantage: boolean
  notes: string
}

export const toTrait = (t: PurchasableTrait) => Trait(t.name, t.cost, t.advantage ? 'ADVANTAGE' : 'DISADVANTAGE')

export interface UnofferedTraits {
  reason: string
  names: string[]
}

export interface InstantiableTrait {
  name: string
  advantage: boolean
  notes: string
  costMin: number
  costMax: number
  variants: [string, number][]
}

export const needsInstance = (t: InstantiableTrait) => t.name.endsWith(TraitNames.PLACEHOLDER)

export interface InstancedPurchase {
  row: string
  variant: string | null
  instance: string | null
  cost: number
}

export const InstancedPurchase = (p: Pick<InstancedPurchase, 'row' | 'cost'> & Partial<InstancedPurchase>): InstancedPurchase => ({
  variant: null, instance: null, ...p,
})

export const purchaseKey = (p: InstancedPurchase) => `${p.row.trim().toLowerCase()}/${p.instance?.trim().toLowerCase() ?? ''}`

const WEALTH_PREFIX = 'Wealth: ' // Wealth.TRAIT_PREFIX
const LANGUAGE_ROW = 'Language (specify)' // Languages.ROW
const isWealth = (row: TraitDefinition) => row.name.startsWith(WEALTH_PREFIX)

export const offered = (): PurchasableTrait[] => [
  ...rows(ContentLoader.advantages(), true),
  ...blessingRows(),
  ...rows(ContentLoader.disadvantages(), false),
]

const blessingRows = () => {
  const cost = ContentLoader.blessingCost()
  return rows(ContentLoader.blessings().map((it) => blessingAsTrait(it, cost)), true)
}

export const blessingRequirement = () => ContentLoader.blessingRequirement()
export const blessingNames = () => new Set(ContentLoader.blessings().map((it) => it.deity))

export function unoffered(): UnofferedTraits[] {
  const all = [...ContentLoader.advantages(), ...ContentLoader.disadvantages()]
  const open = all.filter((it) => it.cost === null && !isWealth(it) && (it.costMin === null || it.costMax === null))
  const language = open.filter((it) => it.name === LANGUAGE_ROW)
  const unbounded = open.filter((it) => it.name !== LANGUAGE_ROW)
  const out: UnofferedTraits[] = []
  if (language.length > 0) {
    out.push({
      reason: "bought in the Languages row above, which prices it from the Languages section's own tables",
      names: language.map((it) => it.name),
    })
  }
  if (unbounded.length > 0) {
    out.push({
      reason: 'the book prices this row "varies" and prints neither bounds nor a way to reach the number, so there is no interval to choose inside',
      names: unbounded.map((it) => it.name).sort(),
    })
  }
  out.push({ reason: 'economic status is one choice, made in the Wealth row above', names: all.filter(isWealth).map((it) => it.name).sort() })
  return out
}

export const canBuy = (trait: PurchasableTrait, held: Trait[]) =>
  !held.some((it) => eqIc(TraitNames.catalogueName(it.name), trait.name))

export const PREREQUISITES = lazy(() => new Map([
  [TE.VERY_FIT, TE.FIT],
  [TE.INCISIVE_VISION, TE.ACUTE_VISION],
]))

export function missingPrerequisite(trait: PurchasableTrait, held: Trait[]): string | null {
  const needed = PREREQUISITES().get(trait.name)
  return needed !== undefined && TraitNames.levelOf(held, needed) === null ? needed : null
}

export const named = (name: string) => offered().find((it) => eqIc(it.name, name)) ?? null

export const instantiable = (): InstantiableTrait[] => [
  ...instantiableIn(ContentLoader.advantages(), true),
  ...instantiableIn(ContentLoader.disadvantages(), false),
]

export const instantiableNamed = (name: string) => instantiable().find((it) => eqIc(it.name, name)) ?? null

export function canInstantiate(row: InstantiableTrait, held: Trait[]): boolean {
  if (row.variants.length === 0) return true
  return !held.some((it) => eqIc(TraitNames.catalogueName(it.name), row.name))
}

const blankOrNull = (s: string | null) => s === null || isBlank(s)

export function instanced(purchase: InstancedPurchase): Trait | null {
  const row = instantiableNamed(purchase.row)
  if (row === null) return null
  let variant: [string, number] | null = null
  if (purchase.variant !== null) {
    const matches = row.variants.filter((it) => eqIc(it[0], purchase.variant))
    if (matches.length !== 1) return null
    variant = matches[0]
  }
  if (variant === null && blankOrNull(purchase.instance)) return null
  if (variant !== null && !blankOrNull(purchase.instance)) return null
  if (variant !== null && purchase.cost !== variant[1]) return null
  if (variant === null && row.variants.length > 0) return null
  if (purchase.cost < row.costMin || purchase.cost > row.costMax) return null
  const head = removeSuffix(row.name, TraitNames.PLACEHOLDER)
  const name = needsInstance(row)
    ? `${head} (${purchase.instance!.trim()})`
    : variant !== null ? `${row.name} (${variant[0]})` : row.name
  return Trait(name, purchase.cost, row.advantage ? 'ADVANTAGE' : 'DISADVANTAGE')
}

export const instancedTraits = (purchases: InstancedPurchase[], held: Trait[] = []): Trait[] =>
  resolved(purchases, held).map((it) => it[1]).filter((t): t is Trait => t !== null)

export function resolved(purchases: InstancedPurchase[], held: Trait[] = []): [InstancedPurchase, Trait | null][] {
  const seen = new Set<string>()
  return purchases.map((purchase) => {
    const row = instantiableNamed(purchase.row)
    const trait = row === null || !canInstantiate(row, held) ? null : instanced(purchase)
    if (trait === null) return [purchase, null]
    const k = purchaseKey(purchase)
    if (seen.has(k)) return [purchase, null]
    seen.add(k)
    return [purchase, trait]
  })
}

const instantiableIn = (entries: TraitDefinition[], advantage: boolean): InstantiableTrait[] =>
  sortedBy(
    entries
      .filter((it) => it.cost === null && it.costMin !== null && it.costMax !== null && !isWealth(it))
      .map((it) => ({
        name: it.name,
        advantage,
        notes: it.notes,
        costMin: it.costMin!,
        costMax: it.costMax!,
        variants: TraitNames.pricedVariants(it.notes),
      })),
    (it) => it.name,
  )

const rows = (entries: TraitDefinition[], advantage: boolean): PurchasableTrait[] =>
  sortedBy(
    entries.filter((it) => it.cost !== null && !isWealth(it)).map((it) => ({ name: it.name, cost: it.cost!, advantage, notes: it.notes })),
    (it) => it.name,
  )
