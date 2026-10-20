import { canonical, eqIc, IllegalArgumentException, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { RitualPreparation, TargetLink } from './content/schema.gen'

export type RitualMode = 'QUICK' | 'FULL'
export const RITUAL_MODES: RitualMode[] = ['QUICK', 'FULL']

export const usesTargetLink = (m: RitualMode) => m === 'QUICK'
export const usesFlourish = (m: RitualMode) => m === 'FULL'

export const FLOURISH_BONUS = 1

export const preparations = (m: RitualMode): RitualPreparation[] =>
  m === 'QUICK' ? ContentLoader.ritualQuickPreparation() : ContentLoader.ritualPreparation()

export function preparationNamed(m: RitualMode, quality: string): RitualPreparation {
  const row = preparations(m).find((it) => eqIc(it.quality, quality))
  if (row === undefined) {
    throw new IllegalArgumentException(
      `no '${quality}' row in the ${m} ritual's preparation table; it prints ` + preparations(m).map((it) => it.quality).join(', '),
    )
  }
  return row
}

export function modifierTotal(
  m: RitualMode,
  preparation: RitualPreparation | null,
  link: TargetLink | null = null,
  flourish = false,
  preparationValue: number | null = null,
  linkValue: number | null = null,
): number {
  require(link === null || usesTargetLink(m), () =>
    `a ${m} ritual's preparation modifier already includes the target ` +
    `link (its rows name one); adding '${link?.link}' would pay it twice`)
  require(!flourish || usesFlourish(m), () =>
    `a ${m} ritual prints no Flourish Bonus; its Step 3 sums exactly ` +
    'the two modifiers it names, and only Phase II of a full ' +
    `ritual awards +${FLOURISH_BONUS} for delivery`)
  require(preparation === null || preparations(m).some((it) => canonical(it) === canonical(preparation)), () =>
    `'${preparation?.quality}' at ${preparation?.printed} is not a row in ` +
    `the ${m} ritual's preparation table, which prints ` +
    preparations(m).map((it) => `${it.quality} ${it.printed}`).join(', '))
  return preparationValueFor(preparation, preparationValue) + linkValueFor(link, linkValue) + (flourish ? FLOURISH_BONUS : 0)
}

export function preparationValueFor(preparation: RitualPreparation | null, chosen: number | null = null): number {
  if (preparation === null) {
    require(chosen === null, () => `${chosen} was picked with no preparation row to pick it inside`)
    return 0
  }
  return pickedWithin(`'${preparation.quality}' preparation`, preparation.printed, preparation.modifier, preparation.best, preparation.openEnded, chosen)
}

export function linkValueFor(link: TargetLink | null, chosen: number | null = null): number {
  if (link === null) {
    require(chosen === null, () => `${chosen} was picked with no target link to pick it inside`)
    return 0
  }
  return pickedWithin(`a '${link.link}' link`, link.printed, link.modifier, link.best, false, chosen)
}

function pickedWithin(what: string, printed: string, near: number, far: number | null, openEnded: boolean, chosen: number | null): number {
  if (chosen === null) return near
  if (far !== null) {
    require(chosen >= Math.min(near, far) && chosen <= Math.max(near, far), () => `${what} prints ${printed}; ${chosen} is outside it`)
  } else if (openEnded) {
    require(near < 0 ? chosen <= near : chosen >= near, () => `${what} prints ${printed}; ${chosen} is on the wrong side of it`)
  } else {
    require(chosen === near, () => `${what} prints exactly ${printed}, not ${chosen}`)
  }
  return chosen
}

export const links = (): TargetLink[] => ContentLoader.ritualTargetLink()

export function linkNamed(link: string): TargetLink {
  const row = links().find((it) => eqIc(it.link, link))
  if (row === undefined) {
    throw new IllegalArgumentException(`no target link called '${link}'; the book prints ` + links().map((it) => it.link).join(', '))
  }
  return row
}
