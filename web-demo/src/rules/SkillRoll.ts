import { canonical } from './kt'
import { sourceKind, type ModifierSource, type Modifier, type ModTarget, type Outcome, type RollResult, type SkillKey, type SourceKind } from './model'
import * as ModifierResolver from './ModifierResolver'
import { Context, type Contribution } from './ModifierResolver'
import * as DegreeOfSuccess from './DegreeOfSuccess'
import type { SuccessDegree } from './DegreeOfSuccess'
import * as SuccessRoll from './SuccessRoll'
import type { RandomSource } from './RandomSource'

export interface SkillTarget {
  label: string
  baseTarget: number
  target: number
  applied: Contribution[]
  forbidden: boolean
  requiresCritical: boolean
  /** Kotlin's `Map<ModifierId, ModifierSource>`, in insertion order. */
  sources: Map<string, ModifierSource>
}

export interface SkillRollOutcome extends SkillTarget {
  roll: RollResult | null
}

export interface LedgerLine {
  label: string
  delta: number
  kind: SourceKind | null
}

export function sourceLabel(s: ModifierSource): string {
  switch (s.type) {
    case 'Situational': return s.description
    case 'StateThreshold': return s.tracker
    case 'FromTrait': return s.name
    case 'Equipment': return s.name
    case 'Potion': return `${s.pathway}, Sequence ${s.sequence}`
  }
}

export function ledgerLines(applied: Contribution[], sources: Map<string, ModifierSource>): LedgerLine[] {
  return applied.map((c) => {
    const known = c.ids.map((id) => sources.get(id)).filter((s): s is ModifierSource => s !== undefined)
    const kinds = [...new Set(known.map(sourceKind))]
    return {
      label: c.ids.map((id) => { const s = sources.get(id); return s ? sourceLabel(s) : id }).join(', '),
      delta: c.delta,
      kind: kinds.length === 1 ? kinds[0] : null,
    }
  })
}

export const lines = (t: SkillTarget) => ledgerLines(t.applied, t.sources)

export function effectiveOutcome(o: SkillRollOutcome): Outcome | null {
  if (o.roll === null) return null
  if (o.requiresCritical && o.roll.outcome === 'SUCCESS') return 'FAILURE'
  return o.roll.outcome
}

export const degree = (o: SkillRollOutcome): SuccessDegree | null =>
  o.roll === null ? null : DegreeOfSuccess.of(effectiveOutcome(o), o.roll.margin)

export function targetFor(label: string, base: number, key: SkillKey, mods: Modifier[] = [], ctx: Context = Context()): SkillTarget {
  const target: ModTarget = { type: 'Skill', key }
  const all = canonical({ type: 'AllRolls' })
  const aimed = mods.map((it) => (canonical(it.target) === all ? { ...it, target } : it))
  const resolved = ModifierResolver.resolve(base, aimed, target, ctx)
  return {
    label,
    baseTarget: base,
    target: resolved.value,
    applied: resolved.applied,
    forbidden: resolved.forbidden,
    requiresCritical: resolved.requiresCritical,
    sources: new Map(aimed.map((it) => [it.id, it.source])),
  }
}

export const rollAgainst = (target: SkillTarget, source: RandomSource): SkillRollOutcome => ({
  ...target,
  roll: target.forbidden ? null : SuccessRoll.against(target.target, source),
})

export const at = (label: string, base: number, key: SkillKey, source: RandomSource, mods: Modifier[] = [], ctx: Context = Context()) =>
  rollAgainst(targetFor(label, base, key, mods, ctx), source)
