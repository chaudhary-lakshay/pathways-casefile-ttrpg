import { eqIc, require } from './kt'
import { Flat, Modifier } from './model'
import type { SkillKey } from './model'
import * as Corruption from './Corruption'
import type { PoolStatus } from './Pools'
import type { RollKind } from './Shock'

export const RITUAL_SKILL = 'Ritualistic Magic'

export function forSkill(skill: SkillKey, hp: PoolStatus, fp: PoolStatus, spi: PoolStatus | null = null, cor = 0): Modifier[] {
  const out: (Modifier | null)[] = [penalty('hp', hp.penalty, hp.level), penalty('fp', fp.penalty, fp.level)]
  if (spi !== null && skill.attr.type === 'Spi') out.push(penalty('spi', spi.penalty, spi.level))
  if (eqIc(skill.name, RITUAL_SKILL)) out.push(penalty('cor', Corruption.ritualPenalty(cor), `${cor} CoR`))
  return out.filter((m): m is Modifier => m !== null)
}

export function penaltyFor(kind: RollKind, hp: PoolStatus, fp: PoolStatus): number {
  require(kind !== 'SKILL', () => "a skill roll takes reeling and fatigue through forSkill's ledger, not this number")
  return hp.penalty + fp.penalty
}

function penalty(id: string, value: number, tracker: string): Modifier | null {
  if (value === 0) return null
  return Modifier({
    id: `state:${id}`,
    source: { type: 'StateThreshold', tracker },
    target: { type: 'AllRolls' },
    effect: Flat(value),
    layer: 'STATE_THRESHOLD',
  })
}
