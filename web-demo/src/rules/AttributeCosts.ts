import { attr, ATTRS, BASE_ATTRIBUTE, MAX_ATTRIBUTE, type Attr, type Attributes } from './model'
import { getValue, require, sumOf } from './kt'
import * as ContentLoader from './content/ContentLoader'

let perLevelLazy: Map<Attr, number> | null = null
const perLevel = () =>
  (perLevelLazy ??= new Map(ContentLoader.attributeCosts().map((it) => [it.attr as Attr, it.costPerLevel])))

export function costOf(a: Attr, level: number): number {
  require(level >= BASE_ATTRIBUTE, () =>
    `attributes cannot be bought below ${BASE_ATTRIBUTE} (was ${level}); the book gives no refund`)
  require(level <= MAX_ATTRIBUTE, () => `attribute level ${level} exceeds the supported maximum of ${MAX_ATTRIBUTE}`)
  return getValue(perLevel(), a) * (level - BASE_ATTRIBUTE)
}

export function totalCost(a: Attributes): number {
  return sumOf(ATTRS, (it) => costOf(it, attr(a, it)))
}
