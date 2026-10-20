import { Attributes, type Attr, type Trait } from './model'
import { sumOf } from './kt'

export const GOD_OF_COMBAT = 'God of Combat'

const ATTRIBUTE_GAINS: Map<string, Map<Attr, number>> = new Map([
  [GOD_OF_COMBAT, new Map<Attr, number>([['ST', 3], ['DX', 3], ['HT', 3]])],
])

export const attributeGainTable = () => ATTRIBUTE_GAINS

export function gainTo(a: Attr, held: Iterable<string>): number {
  return sumOf([...new Set(held)], (it) => ATTRIBUTE_GAINS.get(it)?.get(a) ?? 0)
}

export function played(attributes: Attributes, traits: Trait[]): Attributes {
  const held = traits.map((it) => it.name)
  return Attributes({
    st: attributes.st + gainTo('ST', held),
    dx: attributes.dx + gainTo('DX', held),
    iq: attributes.iq + gainTo('IQ', held),
    ht: attributes.ht + gainTo('HT', held),
  })
}
