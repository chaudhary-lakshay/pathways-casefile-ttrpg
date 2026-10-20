import type { Trait } from './model'
import * as ContentLoader from './content/ContentLoader'
import * as TraitNames from './TraitNames'

export const TRAIT = 'Spirit Vision'

const file = () => ContentLoader.spiritVision()
export const astralColors = () => file().astralColors
export const etherBody = () => file().etherBody
export const etherBodyNote = () => file().etherBodyNote
export const pathwayDifferences = () => file().pathwayDifferences
export const activation = (): number => file().activation.spi
export const isHeldBy = (traits: Trait[]) => TraitNames.levelOf(traits, TRAIT) !== null
