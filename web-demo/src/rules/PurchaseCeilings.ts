import { lazy } from './kt'
import * as ContentLoader from './content/ContentLoader'
import { MAX_ATTRIBUTE, MAX_SECONDARY_DELTA_MAGNITUDE, type Attr } from './model'
import * as Blessings from './Blessings'
import * as NonStandardPathways from './NonStandardPathways'
import * as PotionDigestion from './PotionDigestion'
import * as SecondaryCharacteristics from './SecondaryCharacteristics'

const reach = lazy(() =>
  PotionDigestion.largestGrantsWithBoons(
    ContentLoader.pathways(),
    NonStandardPathways.all().map((it) => ({ ...it, pathway: NonStandardPathways.patronOf(it.pathway) })),
  ),
)

export const attribute = (attr: Attr, held: Iterable<string>) =>
  MAX_ATTRIBUTE - (reach().attributes.get(attr) ?? 0) - Blessings.gainTo(attr, held)

export const secondarySteps = (key: string) =>
  MAX_SECONDARY_DELTA_MAGNITUDE - SecondaryCharacteristics.stepsOf(reach().secondary, key)
