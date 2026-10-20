// The computed members the Kotlin content classes declare in their bodies.
import type {
  AntiDivinationTechnique, Blessing, BoonModifier, RitualWeight, TraitDefinition, UnarmedAttack, WeaponMode,
} from './schema.gen'

export const blessingAsTrait = (b: Blessing, cost: number): TraitDefinition => ({
  name: b.deity, cost, costMin: null, costMax: null, variableCost: false, perLevel: false,
  notes: b.effect, selfControl: null,
})

export const unarmedWounds = (a: UnarmedAttack) => a.base !== null

export const isFromSt = (m: WeaponMode) => m.base === 'sw' || m.base === 'thr'

export function printedCost(w: RitualWeight): string {
  if (w.spiOpen) return `${w.spi}+ SPI`
  if (w.spiMax !== null) return `${w.spi}-${w.spiMax} SPI`
  return `${w.spi} SPI`
}

export const boonModifierKey = (m: BoonModifier) => `${m.group} / ${m.label}`

export const techniqueBullet = (t: AntiDivinationTechnique, label: string): string | null =>
  t.bullets.find((it) => it.label === label)?.text ?? null
