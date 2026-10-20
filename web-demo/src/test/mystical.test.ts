import { describe } from 'vitest'
import { replay } from './parity'
import { scripted } from './dice'
import { canonical } from '../rules/kt'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as AntiDivination from '../rules/AntiDivination'
import * as Boons from '../rules/Boons'
import * as Detection from '../rules/Detection'
import * as Languages from '../rules/Languages'
import { AntiDivinationTechniques, Incantations, SealedArtifacts, SpiritualSenses } from '../rules/Mystical'
import * as Pools from '../rules/Pools'
import * as PotionConsumption from '../rules/PotionConsumption'
import * as PowerSources from '../rules/PowerSources'
import { PowerSourceDeclaration } from '../rules/PowerSources'
import * as Premades from '../rules/Premades'
import * as PurchasableTraits from '../rules/PurchasableTraits'
import * as PurchaseCeilings from '../rules/PurchaseCeilings'
import * as RitualMode from '../rules/RitualMode'
import * as RitualResolution from '../rules/RitualResolution'
import * as SpiritContract from '../rules/SpiritContract'
import * as Study from '../rules/Study'
import * as TraitGrants from '../rules/TraitGrants'
import { BeyonderState, Trait } from '../rules/model'

const techs = () => AntiDivinationTechniques.all()
const byKey = <V>(entries: [unknown, V][]) => Object.fromEntries(entries.map(([k, v]) => [canonical(k), v]))
const clause = (total: number, line: string | null) => ({ total, line })

function decls(): PowerSourceDeclaration[] {
  return [
    PowerSourceDeclaration(),
    PowerSourceDeclaration({ pacing: 'RUSHED', assistants: 5, sacrifice: -10, invocation: 3 }),
    PowerSourceDeclaration({ pacing: 'EXTENDED', suspended: true, sacrifice: 40 }),
    PowerSourceDeclaration({ sacrifice: 41 }),
    PowerSourceDeclaration({ invocation: 6 }),
    PowerSourceDeclaration({ assistants: -1 }),
    ...PowerSources.revelations().flatMap((entity) =>
      [
        { entity, sacrifices: 0, blessedByDeity: false },
        { entity, sacrifices: 2, blessedByDeity: false },
        { entity, sacrifices: 0, blessedByDeity: true },
      ].map((revelation) => PowerSourceDeclaration({ revelation })),
    ),
  ]
}

describe('anti-divination', () => {
  replay('mystical', 'SealedArtifacts.grades', () => SealedArtifacts.grades())
  replay('mystical', 'SpiritualSenses.all', () => SpiritualSenses.all())
  replay('mystical', 'SpiritualSenses.isOffered', SpiritualSenses.isOffered)
  replay('mystical', 'Incantations.standard', () => Incantations.standard())
  replay('mystical', 'AntiDivinationTechniques.named', AntiDivinationTechniques.named)
  replay('mystical', 'AntiDivinationTechniques.totals', (p: number[]) => {
    const chosen = p.map((i) => techs()[i])
    return [
      AntiDivinationTechniques.penaltyTotal(chosen), AntiDivinationTechniques.detectionBonus(chosen), AntiDivinationTechniques.spiFor(chosen),
    ]
  })
  replay('mystical', 'AntiDivinationTechniques.onDetectionRoll', () => AntiDivinationTechniques.onDetectionRoll().map((it) => it.id))
  replay('mystical', 'AntiDivination.halvedMagnitude', AntiDivination.halvedMagnitude)
  replay('mystical', 'AntiDivination.rowFor', AntiDivination.rowFor)
  replay('mystical', 'AntiDivination.against', (t, d, tech, link, hp) => {
    const r = AntiDivination.against(t, d, tech, link, hp)
    return [r, AntiDivination.modifiers(r)]
  })
  replay('mystical', 'AntiDivination.counterNamed', AntiDivination.counterNamed)
})

describe('rituals, boons, contracts', () => {
  replay('mystical', 'RitualMode.preparationNamed', (m, q: string) => RitualMode.preparationNamed(m, q.toUpperCase()))
  replay('mystical', 'RitualMode.modifierTotal', (m, pi, li, flourish, pv, lv) =>
    RitualMode.modifierTotal(
      m,
      pi === null ? null : RitualMode.preparations(m)[pi],
      li === null ? null : RitualMode.links()[li],
      flourish, pv, lv,
    ))
  replay('mystical', 'RitualMode.modifierTotal.foreign', () => RitualMode.modifierTotal('QUICK', RitualMode.preparations('FULL').at(-1)!))
  replay('mystical', 'RitualMode.linkNamed', RitualMode.linkNamed)
  const weight = (w: string) => RitualResolution.weights().find((it) => it.weight === w)!
  replay('mystical', 'RitualResolution.spiFor', (w, c, b) => RitualResolution.spiFor(weight(w), c, b))
  replay('mystical', 'PowerSources.complexityFor', (w) => PowerSources.complexityFor(weight(w)))
  replay('mystical', 'PowerSources.modifierTotal', (c, di) =>
    PowerSources.modifierTotal(c === null ? null : PowerSources.complexityNamed(c), decls()[di]))
  replay('mystical', 'PowerSources.spiOwed', PowerSources.spiOwed)
  replay('mystical', 'PowerSources.hpFor', PowerSources.hpFor)
  const profs = () => [null, ...ContentLoader.languageProficiencies().filter((it) => it.type === Languages.MYSTICAL)]
  replay('mystical', 'RitualResolution.targetFor', (skill, wi, cor, pi, burned) =>
    RitualResolution.targetFor(skill, RitualResolution.weights()[wi], cor, { language: profs()[pi], burned }))
  replay('mystical', 'RitualResolution.rollAgainst', (t, f) => scripted(f, (src) => RitualResolution.rollAgainst(t, src)))
  replay('mystical', 'RitualResolution.successFor', (m) => RitualResolution.successFor(m))
  replay('mystical', 'RitualResolution.failureFor', (m) => RitualResolution.failureFor(m))
  replay('mystical', 'RitualResolution.criticalFor', (r) => RitualResolution.criticalFor(r))
  replay('mystical', 'RitualResolution.burnBonus', RitualResolution.burnBonus)
  replay('mystical', 'Boons.penaltyForSequence', Boons.penaltyForSequence)
  replay('mystical', 'Boons.weight', () => Boons.weight())
  replay('mystical', 'Boons.groups', () => Boons.groups())
  replay('mystical', 'Boons.spiFor', () => Boons.spiFor())
  replay('mystical', 'Boons.modifierTotal', (idx: number[], gm) =>
    Boons.modifierTotal(idx.map((i) => Boons.modifiers()[i]), new Map(Object.entries(gm))))
  replay('mystical', 'Boons.targetFor', (s, cor, seq) => Boons.targetFor(s, cor, seq))
  replay('mystical', 'SpiritContract.maxContracts', SpiritContract.maxContracts)
  replay('mystical', 'SpiritContract.spi', () => SpiritContract.spi())
  replay('mystical', 'SpiritContract.penaltyFor', (s, h, m, f) => scripted(f, (src) => SpiritContract.penaltyFor(s, h, m, src)))
  replay('mystical', 'SpiritContract.targetFor', (skill, cor, b) => {
    const p = { sequence: 7, dice: [3, 4] }
    return [SpiritContract.targetFor(skill, cor, p, b), SpiritContract.penaltyTotal(p)]
  })
  replay('mystical', 'SpiritContract.outcomeFor', SpiritContract.outcomeFor)
})

describe('detection', () => {
  replay('mystical', 'Detection.attributeFor', Detection.attributeFor)
  replay('mystical', 'Detection.attributeLine', (spi, per, free, a, sm, total, line) =>
    Detection.attributeLine(spi, per, free, a, sm, clause(total, line)))
  replay('mystical', 'Detection.isPerRoll', Detection.isPerRoll)
  replay('mystical', 'Detection.awarenessFor', Detection.awarenessFor)
  replay('mystical', 'Detection.outcomeFor', Detection.outcomeFor)
  const pick = (si: number[], ti: number[], ii: number[]) => ({
    situations: si.map((i) => Detection.modifiers()[i]),
    techniques: ti.map((i) => techs()[i]),
    items: ii.map((i) => Detection.items()[i]),
  })
  replay('mystical', 'Detection.modifierTotal', (si, ti, ii, bonus) => {
    const p = pick(si, ti, ii)
    return Detection.modifierTotal(p.situations, p.techniques, p.items, bonus)
  })
  replay('mystical', 'Detection.partsFor', (si, ti, ii, bonus) =>
    Detection.partsFor(10, 12, {
      ...pick(si, ti, ii), awarenessBonus: bonus, awarenessLabel: 'Seer', freeRoll: null, alertness: 1, shock: -1, hurt: -2,
      spiModifier: 0, states: clause(-1, 'Reckless -1'), perClause: clause(0, null), attrition: -3,
    }))
  replay('mystical', 'Detection.attritionFor', (s, sm, aw) =>
    Detection.attritionFor(Pools.spi(s, sm), aw === null ? null : Detection.awareness()[aw]))
  replay('mystical', 'Detection.rollAgainst', (t, f) => scripted(f, (src) => Detection.rollAgainst(t, src, ['x'])))
  replay('mystical', 'Detection.targetFor', (spi, per) =>
    Detection.targetFor(spi, per, {
      modifier: 2, freeRoll: null, alertness: 1, shock: -1, hurt: -2, spiModifier: 0,
      states: clause(-1, 'x'), perClause: clause(1, 'y'), attrition: -3,
    }))
})

describe('character rules', () => {
  replay('mystical', 'PotionConsumption.targetFor', (d, cor, m) => PotionConsumption.targetFor(d, cor, undefined, m))
  replay('mystical', 'PotionConsumption.attempt', (d, cor, f) =>
    scripted(f, (src) => PotionConsumption.attempt(BeyonderState({ pathway: 'Fool', sequence: 9, digestion: d }), cor, src)))
  replay('mystical', 'PotionConsumption.earlyAdvancementDice', () => PotionConsumption.earlyAdvancementDice())
  replay('mystical', 'TraitGrants.tables', () => [
    byKey(TraitGrants.grantedTraitTable()), byKey(TraitGrants.refusedGrants()), byKey(TraitGrants.drawbackChoiceTable()),
    byKey(TraitGrants.boonTraitTable()), byKey(TraitGrants.refusedBoonGrants()),
  ])
  replay('mystical', 'TraitGrants.grantedTraits', TraitGrants.grantedTraits)
  replay('mystical', 'TraitGrants.drawbackChoices', TraitGrants.drawbackChoices)
  replay('mystical', 'TraitGrants.withGrantedTraits', (n, state, boon) =>
    TraitGrants.withGrantedTraits(Premades.toCharacter(Premades.byName(n)).traits, state, boon))
  const bought = [Trait('Charisma 1', 5, 'ADVANTAGE'), Trait('Acute Vision 2', 4, 'ADVANTAGE')]
  replay('mystical', 'TraitGrants.withGrantedTraits.bought', (state) => TraitGrants.withGrantedTraits(bought, state))
  replay('mystical', 'TraitGrants.grantedLevelsIn', TraitGrants.grantedLevelsIn)
  replay('mystical', 'PurchasableTraits.offered', () => PurchasableTraits.offered())
  replay('mystical', 'PurchasableTraits.unoffered', () => PurchasableTraits.unoffered())
  replay('mystical', 'PurchasableTraits.instantiable', () => PurchasableTraits.instantiable())
  replay('mystical', 'PurchasableTraits.blessingNames', () => [...PurchasableTraits.blessingNames()])
  replay('mystical', 'PurchasableTraits.canBuy', (name, held) => {
    const t = PurchasableTraits.offered().find((it) => it.name === name)!
    return [PurchasableTraits.canBuy(t, held), PurchasableTraits.missingPrerequisite(t, held)]
  })
  replay('mystical', 'PurchasableTraits.instanced', PurchasableTraits.instanced)
  replay('mystical', 'PurchasableTraits.resolved', PurchasableTraits.resolved)
  replay('mystical', 'PurchaseCeilings.attribute', PurchaseCeilings.attribute)
  replay('mystical', 'PurchaseCeilings.secondarySteps', PurchaseCeilings.secondarySteps)
  replay('mystical', 'Study.cellFor', (d, m) => [Study.cellFor(d, m), Study.isPriced(d, m)])
  replay('mystical', 'Study.costPerPoint', Study.costPerPoint)
  replay('mystical', 'Study.plan', (p, d, a, m, g) => {
    const r = Study.plan(p, d, a, m, g)
    return r.ok ? [r.plan, null] : [null, r.error.refusal]
  })
})
