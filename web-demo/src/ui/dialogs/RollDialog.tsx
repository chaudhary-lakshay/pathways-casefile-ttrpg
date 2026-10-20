// RollDialog.kt: FORM 3-S, a skill roll. The target is composed by the
// caller (SheetModel.skillRollTarget, SheetDialogs.kt 88-112) from what is
// picked here; this draws it and rolls it once.
import { useState } from 'react'
import { endsBetween, endsBeyond } from '../../app/session/SheetLines'
import * as CommonModifiers from '../../rules/CommonModifiers'
import type { CommonModifier } from '../../rules/content/schema.gen'
import * as EffortBurn from '../../rules/EffortBurn'
import type { BurnKind, BurnWhen } from '../../rules/EffortBurn'
import type { Modifier, Trait } from '../../rules/model'
import * as SkillRoll from '../../rules/SkillRoll'
import type { SkillRollOutcome, SkillTarget } from '../../rules/SkillRoll'
import { clauseKey, type TraitClause } from '../../rules/TraitEffects'
import {
  CaseChip, Chips, CloseButton, Dialog, FormTitle, LedgerLines, OptionStepper, ResultCard, RollButton, RuleNote, SectionLabel,
  signed, StandingCard, SteadyBody, useLatch,
} from '../kit'

export const FORM_SKILL = 'FORM 3-S · SKILL ROLL'

/** SheetRows.kt `BurnOffer`. */
export interface BurnOffer {
  kind: BurnKind
  whenAllowed: BurnWhen
}
export function burnOffer(kind: BurnKind | null, traits: Trait[]): BurnOffer | null {
  if (kind === null) return null
  const whenAllowed = EffortBurn.whenAllowed(kind, traits)
  return whenAllowed === null ? null : { kind, whenAllowed }
}

export function BurnStepper({ offer, points, onPoints }: { offer: BurnOffer; points: number; onPoints: (n: number) => void }) {
  const pool = EffortBurn.pool(offer.kind).pool
  return (
    <OptionStepper
      label={offer.whenAllowed === 'IN_COMBAT' ? `Burn ${pool} (in combat)` : `Burn ${pool}`}
      value={points}
      shown={`+${points}`}
      note={`before the roll: 1 ${pool} buys +1, no cap`}
      onStep={(d) => onPoints(Math.max(points + d, 0))}
    />
  )
}

export function RollDialog({ label, base, burn = null, equipment = [], declarableClauses = [], onBurn = () => {}, targetFor, roll, onDismiss }: {
  label: string
  base: number
  burn?: BurnOffer | null
  equipment?: string[]
  declarableClauses?: TraitClause[]
  onBurn?: (kind: BurnKind, points: number) => void
  targetFor: (circumstances: Modifier[], declared: Set<string>) => SkillTarget
  roll: (t: SkillTarget) => SkillRollOutcome
  onDismiss: () => void
}) {
  const [out, setOut, liveOut] = useLatch<SkillRollOutcome | null>(null)
  const [burnPoints, setBurnPoints] = useState(0)
  const drawnBurn = burn === null ? 0 : burnPoints
  const [chosen, setChosen] = useState<CommonModifier[]>([])
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const [gmValue, setGmValue] = useState<Map<string, number>>(new Map())
  const [gmRuling, setGmRuling] = useState(0)
  const burnMod = burn ? EffortBurn.modifier(burn.kind, drawnBurn) : null
  const ruling = CommonModifiers.gmRuling(gmRuling)
  const standing = targetFor(
    [
      ...CommonModifiers.modifiers(chosen, new Map([...gmValue].filter(([k]) => chosen.some((it) => it.situation === k)))),
      ...(ruling ? [ruling] : []),
      ...(burnMod ? [burnMod] : []),
    ],
    declared,
  )
  return (
    <Dialog
      onDismiss={onDismiss}
      dismiss={<CloseButton onClick={onDismiss} />}
      confirm={
        <RollButton
          label="Roll"
          disabled={out !== null}
          onClick={() => {
            if (liveOut() !== null) return
            setOut(roll(standing))
            if (burn !== null && drawnBurn > 0) onBurn(burn.kind, drawnBurn)
          }}
        />
      }
      title={
        <FormTitle form={FORM_SKILL}>
          <div>{label}</div>
          <div className="bodyMedium muted">Skill {base}</div>
        </FormTitle>
      }
    >
      <SteadyBody>
        <div className="scroll">
          {out === null ? (
            <>
              <StandingCard standing={standing} />
              {declarableClauses.length > 0 && (
                <>
                  <p className="titleSmall" style={{ paddingTop: 12, color: 'var(--on-surface)' }}>Does any of this hold?</p>
                  <Chips>
                    {declarableClauses.map((clause) => {
                      const key = clauseKey(clause)
                      return (
                        <CaseChip
                          key={key}
                          selected={declared.has(key)}
                          onClick={() => {
                            const next = new Set(declared)
                            if (next.has(key)) next.delete(key)
                            else next.add(key)
                            setDeclared(next)
                          }}
                        >
                          {clause.shown}: {clause.printed}
                        </CaseChip>
                      )
                    })}
                  </Chips>
                </>
              )}
              <CircumstanceRow chosen={chosen} onChosen={setChosen} gmValue={gmValue} onGmValue={setGmValue} />
              <OptionStepper label="Modifier" value={gmRuling} shown={signed(gmRuling)} note="the GM's, for anything the chips do not name" onStep={(d) => setGmRuling(gmRuling + d)} />
              {burn !== null && <BurnStepper offer={burn} points={burnPoints} onPoints={setBurnPoints} />}
              <LedgerLines lines={SkillRoll.lines(standing).filter((it) => it.kind !== 'SITUATIONAL')} requiresCritical={standing.requiresCritical} />
              {equipment.length > 0 && (
                <RuleNote title="Gear that names this skill (not applied)">
                  {equipment.map((it) => <p key={it} className="bodySmall">{it}</p>)}
                </RuleNote>
              )}
            </>
          ) : (
            <ResultCard out={out} />
          )}
        </div>
      </SteadyBody>
    </Dialog>
  )
}

export function CircumstanceRow({ chosen, onChosen, gmValue, onGmValue }: {
  chosen: CommonModifier[]
  onChosen: (c: CommonModifier[]) => void
  gmValue: Map<string, number>
  onGmValue: (m: Map<string, number>) => void
}) {
  return (
    <>
      <SectionLabel text="Circumstances (Appendix A)" />
      <Chips>
        {CommonModifiers.offered().map((row) => {
          const isOn = chosen.some((it) => it.situation === row.situation)
          return (
            <CaseChip
              key={row.situation}
              selected={isOn}
              onClick={() => {
                if (isOn) {
                  onChosen(chosen.filter((it) => it.situation !== row.situation))
                  const next = new Map(gmValue)
                  next.delete(row.situation)
                  onGmValue(next)
                } else onChosen([...chosen, row])
              }}
            >
              {row.situation.split(' (')[0]} {row.printed}
            </CaseChip>
          )
        })}
      </Chips>
      {chosen
        .filter((it) => it.otherEnd !== null || it.isOpenEnded)
        .map((row) => {
          const printed = row.modifier
          const ends = printed === null ? [] : row.otherEnd !== null ? endsBetween(printed, row.otherEnd) : endsBeyond(printed)
          return (
            <div key={row.situation}>
              <p className="bodySmall" style={{ paddingTop: 6 }}>{row.situation}: {row.printed}</p>
              <Chips>
                {ends.map((value) => (
                  <CaseChip key={value} selected={(gmValue.get(row.situation) ?? row.modifier) === value} onClick={() => onGmValue(new Map(gmValue).set(row.situation, value))}>
                    {value > 0 ? `+${value}` : `${value}`}
                  </CaseChip>
                ))}
              </Chips>
            </div>
          )
        })}
    </>
  )
}
