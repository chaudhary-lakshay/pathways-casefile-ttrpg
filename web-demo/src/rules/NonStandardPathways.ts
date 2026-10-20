import type { BoonState } from './model'
import { require, single, sortedBy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { SequenceBlock } from './content/schema.gen'

const NAME_DISAGREEMENT = new Set(['Eternal Aeon (Inevitability)', 'Eternal Aeon'])
const WALKED_AS = 'Eternal Aeon (Inevitability)'

export const all = (): SequenceBlock[] => ContentLoader.nonStandardPathways()

export const alsoPrintedAs = (pathway: string): string | null =>
  NAME_DISAGREEMENT.has(pathway) ? single([...NAME_DISAGREEMENT].filter((it) => it !== pathway)) : null

export const patronOf = (pathway: string) => (NAME_DISAGREEMENT.has(pathway) ? WALKED_AS : pathway)

export const patrons = () => [...new Set(pathways().map(patronOf))]

export const chainFor = (boon: BoonState): SequenceBlock[] =>
  sortedBy(all().filter((it) => patronOf(it.pathway) === patronOf(boon.pathway) && it.sequence >= boon.sequence), (it) => it.sequence, true)

export function nextRung(held: BoonState | null, patron: string): BoonState | null {
  if (held !== null && patronOf(held.pathway) !== patronOf(patron)) return null
  const below = held?.sequence ?? Number.MAX_SAFE_INTEGER
  const found = all().filter((it) => patronOf(it.pathway) === patronOf(patron) && it.sequence < below).map((it) => it.sequence)
  return found.length === 0 ? null : { pathway: patronOf(patron), sequence: Math.max(...found) }
}

export const pathways = () => [...new Set(all().map((it) => it.pathway))]

export function blocksFor(pathway: string): SequenceBlock[] {
  require(pathways().includes(pathway), () =>
    `no non-standard pathway named '${pathway}'; the book prints ${pathways().join(', ')}`)
  return sortedBy(all().filter((it) => it.pathway === pathway), (it) => it.sequence, true)
}

export const knownSequences = (pathway: string) => blocksFor(pathway).map((it) => it.sequence)

export const blockFor = (pathway: string, sequence: number): SequenceBlock | null =>
  blocksFor(pathway).find((it) => it.sequence === sequence) ?? null
