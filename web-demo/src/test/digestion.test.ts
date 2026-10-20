import { describe } from 'vitest'
import { replay } from './parity'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as NonStandardPathways from '../rules/NonStandardPathways'
import * as PotionDigestion from '../rules/PotionDigestion'
import * as Premades from '../rules/Premades'

const blocks = () => ContentLoader.pathways()
const mortal = (n: string) => ({ ...Premades.toCharacter(Premades.byName(n)), beyonder: null, boon: null })

describe('potion digestion', () => {
  replay('digestion', 'PotionDigestion.digestThrough', (n, p, s) => PotionDigestion.digestThrough(mortal(n), p, s, blocks()))
  replay('digestion', 'PotionDigestion.of', (n, state, boon) =>
    PotionDigestion.of({ ...mortal(n), beyonder: state, boon }, blocks()),
  )
  replay('digestion', 'PotionDigestion.largestGrants', (p) =>
    PotionDigestion.largestGrants(p === null ? blocks() : blocks().filter((b) => b.pathway === p)),
  )
  replay('digestion', 'PotionDigestion.largestGrantsWithBoons', () =>
    PotionDigestion.largestGrantsWithBoons(blocks(), NonStandardPathways.all()),
  )
  replay('digestion', 'PotionDigestion.isSameSkill', PotionDigestion.isSameSkill)
})

describe('non-standard pathways', () => {
  replay('digestion', 'NonStandardPathways.knownSequences', NonStandardPathways.knownSequences)
  replay('digestion', 'NonStandardPathways.alsoPrintedAs', NonStandardPathways.alsoPrintedAs)
  replay('digestion', 'NonStandardPathways.patronOf', NonStandardPathways.patronOf)
  replay('digestion', 'NonStandardPathways.blockFor', (p, s) => NonStandardPathways.blockFor(p, s)?.name ?? null)
  replay('digestion', 'NonStandardPathways.nextRung', NonStandardPathways.nextRung)
  replay('digestion', 'NonStandardPathways.chainFor', (b) => NonStandardPathways.chainFor(b).map((x) => x.name))
})
