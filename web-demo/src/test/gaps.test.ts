import { describe } from 'vitest'
import { replay } from './parity'
import { scripted } from './dice'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as Damage from '../rules/Damage'
import * as Derive from '../rules/Derive'
import * as Divination from '../rules/Divination'
import * as FrightCheck from '../rules/FrightCheck'
import * as Languages from '../rules/Languages'
import * as Pools from '../rules/Pools'
import * as PotionDigestion from '../rules/PotionDigestion'
import * as PowerSources from '../rules/PowerSources'
import * as Premades from '../rules/Premades'
import * as RitualMode from '../rules/RitualMode'
import * as SelfControl from '../rules/SelfControl'
import * as SheetComparison from '../rules/SheetComparison'
import * as SkillDefaults from '../rules/SkillDefaults'
import * as SpiritVision from '../rules/SpiritVision'
import * as TraitClauses from '../rules/TraitClauses'
import * as Wounds from '../rules/Wounds'
import { DiceExpr, Trait } from '../rules/model'
import type { ControllingAttribute } from '../rules/model'

const ROLLS = [
  { dice: DiceExpr(1, 6, -4), type: 'cr' },
  { dice: DiceExpr(2, 6, 1), type: 'cut' },
  { dice: DiceExpr(3, 6, 0), type: 'pi+' },
]
const SPECS = () => [...new Map(ContentLoader.disadvantages().flatMap((d) => (d.selfControl ? [[JSON.stringify(d.selfControl), d.selfControl]] : []))).values()]
const character = (n: string) => Premades.toCharacter(Premades.byName(n))
const levelOf = (n: string) => {
  const c = character(n)
  const d = Derive.of(c)
  return (a: ControllingAttribute) => Derive.controllingLevel(a, c.attributes, d.secondaries, c.spi)
}

describe('dice the UI throws', () => {
  replay('gaps', 'Damage.renderRoll', (i) => Damage.renderRoll(ROLLS[i]))
  replay('gaps', 'Damage.roll', (i, f, o) => scripted(f, (d) => {
    const r = Damage.roll(ROLLS[i], d)
    return [r, Damage.onAttack(r, o)]
  }))
  replay('gaps', 'SelfControl.targetFor', (i, w, iq, m) => SelfControl.targetFor(SPECS()[i], w, iq, m))
  replay('gaps', 'SelfControl.attempt', (i, m, f) => scripted(f, (d) => {
    const o = SelfControl.attempt({ trait: 'T', spec: SPECS()[i], printed: 'p' }, 11, 9, d, m)
    return [o, SelfControl.outcomeHeld(o)]
  }))
  replay('gaps', 'Divination.outcome', (t, crit, f) => scripted(f, (d) => {
    const o = Divination.rollAgainst(t, d, crit)
    return [Divination.outcomeIsCritical(o), Divination.effectiveOutcome(o)]
  }))
  replay('gaps', 'FrightCheck.against', (w, m, f) => scripted(f, (d) => {
    const o = FrightCheck.against(w, m, d)
    return [o, FrightCheck.passed(o)]
  }))
  replay('gaps', 'Pools.isFatigued', (fp, max) => Pools.isFatigued(Pools.fp(fp, max)))
  replay('gaps', 'TraitClauses.allRollsModifiers', (d) => TraitClauses.allRollsModifiers(TraitClauses.allRollsClauseRows(), new Set(d)))
})

describe('tables the UI prints', () => {
  replay('gaps', 'SpiritVision.astralColors', () => SpiritVision.astralColors())
  replay('gaps', 'SpiritVision.etherBody', () => SpiritVision.etherBody())
  replay('gaps', 'SpiritVision.etherBodyNote', () => SpiritVision.etherBodyNote())
  replay('gaps', 'SpiritVision.pathwayDifferences', () => SpiritVision.pathwayDifferences())
  replay('gaps', 'SpiritVision.activation', () => SpiritVision.activation())
  replay('gaps', 'Wounds.locations', () => Wounds.locations())
  replay('gaps', 'Wounds.damageTypes', () => Wounds.damageTypes())
  replay('gaps', 'Wounds.majorWounds', () => Wounds.majorWounds())
  replay('gaps', 'PowerSources.pacingModifier', (c, p) =>
    PowerSources.pacingModifier(PowerSources.complexities().find((it) => it.complexity === c)!, p))
  replay('gaps', 'PowerSources.participantBonus', PowerSources.participantBonus)
  replay('gaps', 'PowerSources.sacrificeBonus', PowerSources.sacrificeBonus)
  replay('gaps', 'PowerSources.invocationBonus', PowerSources.invocationBonus)
  replay('gaps', 'PowerSources.revelationPenalty', (e, s, b) => PowerSources.revelationPenalty(PowerSources.revelationNamed(e), s, b))
  replay('gaps', 'PowerSources.revelationNamed', PowerSources.revelationNamed)
  replay('gaps', 'Languages.labels', (p, h) =>
    Languages.owned(p, h).map(([purchase, o]) =>
      [Languages.purchaseLabel(purchase), o && Languages.isFree(o), o && Languages.costLine(o), o && Languages.ownedLabel(o)]))
  replay('gaps', 'RitualMode.preparationValueFor', (m, i, chosen) =>
    RitualMode.preparationValueFor(i < 0 ? null : RitualMode.preparations(m)[i], chosen))
  replay('gaps', 'RitualMode.linkValueFor', (i, chosen) => RitualMode.linkValueFor(i < 0 ? null : RitualMode.links()[i], chosen))
})

describe('per premade', () => {
  replay('gaps', 'Premades.levels', (n, skill) => {
    const sheet = Premades.byName(n)
    const s = sheet.skills.find((it) => it.name === skill)!
    return [Premades.attributeLevelFor(sheet, s), Premades.levelFor(sheet, s)]
  })
  replay('gaps', 'SheetComparison.secondaryRows', (n) =>
    SheetComparison.secondaryRows(Derive.of(character(n)).secondaries, Premades.byName(n).published))
  replay('gaps', 'Derive.attributes', (n) => Derive.attributes(character(n), null))
  replay('gaps', 'Derive.controllingLevel', (n, a) => levelOf(n)(a))
  replay('gaps', 'SkillDefaults.offered', (n, b) => {
    const c = character(n)
    return SkillDefaults.offered(Derive.of(c).skillLevels, levelOf(n), b, c.traits)
  })
  replay('gaps', 'SkillDefaults.unbought', (n, key, b) =>
    [SkillDefaults.untrainedLevel(key, levelOf(n), b), SkillDefaults.unboughtLevel(key, levelOf(n), b, character(n).traits)])
  replay('gaps', 'PotionDigestion.digestBlock', (n, p) =>
    PotionDigestion.digestBlock(character(n), ContentLoader.pathways().find((it) => it.sequence === 9 && it.pathway === p)!))
  replay('gaps', 'SpiritVision.isHeldBy', (n) =>
    SpiritVision.isHeldBy(n === '+Spirit Vision' ? [Trait(SpiritVision.TRAIT, 0, 'ADVANTAGE')] : character(n).traits))
})
