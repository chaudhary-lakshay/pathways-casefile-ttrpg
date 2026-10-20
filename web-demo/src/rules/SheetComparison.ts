import { format2 } from './kt'
import * as Derive from './Derive'
import * as Premades from './Premades'
import { basicSpeed, type Secondaries } from './SecondaryCharacteristics'
import type { PremadeSheet, PremadeSkill, PublishedValues } from './Premades'

export interface SheetRow {
  label: string
  computed: string
  published: string | null
}

export const matches = (r: SheetRow) => r.published === null || r.computed === r.published

export function of(sheet: PremadeSheet): SheetRow[] {
  return [
    ...secondaryRows(Derive.of(Premades.toCharacter(sheet)).secondaries, sheet.published),
    ...sheet.skills.map((it) => skillRow(sheet, it)),
  ]
}

export function secondaryRows(s: Secondaries, p: PublishedValues): SheetRow[] {
  return [
    row('HP', s.maxHp, p.hp),
    row('FP', s.maxFp, p.fp),
    row('Will', s.will, p.will),
    row('Per', s.per, p.per),
    { label: 'Basic Speed', computed: basicSpeed(s), published: p.basicSpeed === null ? null : format2(p.basicSpeed) },
    row('Basic Move', s.basicMove, p.basicMove),
    row('Dodge', s.dodge, p.dodge),
  ]
}

function skillRow(sheet: PremadeSheet, skill: PremadeSkill): SheetRow {
  return row(Premades.skillLabel(skill), Premades.levelFor(sheet, skill), skill.publishedLevel)
}

function row(label: string, computed: number, published: number | null): SheetRow {
  return { label, computed: String(computed), published: published === null ? null : String(published) }
}
