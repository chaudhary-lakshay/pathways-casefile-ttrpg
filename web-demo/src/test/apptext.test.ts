import { describe } from 'vitest'
import { replay } from './parity'
import * as ContentLoader from '../rules/content/ContentLoader'
import * as Derive from '../rules/Derive'
import { SecondaryDelta } from '../rules/model'
import * as NonStandardPathways from '../rules/NonStandardPathways'
import * as Premades from '../rules/Premades'
import * as SecondaryCharacteristics from '../rules/SecondaryCharacteristics'
import * as SelfControl from '../rules/SelfControl'
import * as SheetComparison from '../rules/SheetComparison'
import * as PT from '../app/session/PathwayText'
import * as SL from '../app/session/SheetLines'

const blocks = () => [...ContentLoader.pathways(), ...NonStandardPathways.all()]

describe('pathway text', () => {
  replay('apptext', 'PathwayText.cut', (i, field) => {
    const text = (blocks()[i] as any)[field] as string
    const paragraphs = PT.abilityParagraphs(text)
    return [
      paragraphs.map((p) => [p, PT.paragraphText(p), PT.paragraphBody(p), PT.bullets(p.lead), p.items.map(PT.named)]),
      PT.abilities(text).map((a) => [
        a.head.heading === null ? null : PT.titleOf(a.head.heading),
        a.head.heading === null ? null : PT.spiPriceOf(PT.titleOf(a.head.heading)[1]),
        a.parts.length,
      ]),
      // Dump.kt writes an IntRange as its elements.
      PT.keyTerms(text).map((r) => Array.from({ length: r.last - r.first + 1 }, (_, k) => r.first + k)),
      PT.rejoined(paragraphs) === text,
    ]
  })
  replay('apptext', 'PathwayText.noteOf', (i, j) => PT.noteOf(blocks()[i].skills[j].note))
  replay('apptext', 'PathwayText.spiPriceOf', PT.spiPriceOf)
})

describe('sheet lines', () => {
  const played = (n: string) =>
    SecondaryCharacteristics.of(Premades.toCharacter(Premades.byName(n)).attributes, SecondaryDelta({ hp: 2, will: -1 }))
  replay('apptext', 'SheetLines.vitalLines', (n, compared) =>
    SL.vitalLines(Premades.byName(n), SheetComparison.of(Premades.byName(n)), played(n), compared))
  replay('apptext', 'SheetLines.playedLine', (n, label) => {
    const row = SheetComparison.of(Premades.byName(n)).find((it) => it.label === label)!
    return [SL.playedLine(row, row.computed), SL.playedLine(row, '99')]
  })
  replay('apptext', 'SheetLines.standingTargets', (n, state) => {
    const c = Premades.toCharacter(Premades.byName(n))
    const d = Derive.of(c).secondaries
    return [...SL.standingTargets(SelfControl.checksFor(c.traits), d.will, c.attributes.iq, state)]
  })
  replay('apptext', 'ModifierRange.valuesOf', SL.valuesOf)
  replay('apptext', 'ModifierRange.pickInside', (row, held, n) => SL.pickInside(row, held === null ? null : [held, n]))
})
