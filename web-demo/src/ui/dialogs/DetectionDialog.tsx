// DetectionDialog.kt (FORM 7-X): "Am I being divined?", a free action.
import { useState } from 'react'
import type { DivinationAwareness } from '../../rules/content/schema.gen'
import * as Detection from '../../rules/Detection'
import type { DetectionResult } from '../../rules/Detection'
import { eqIc, substringBefore } from '../../rules/kt'
import type { RollResult } from '../../rules/model'
import { AntiDivinationTechniques } from '../../rules/Mystical'
import type { PoolStatus } from '../../rules/Pools'
import type { RandomSource } from '../../rules/RandomSource'
import * as SuccessRoll from '../../rules/SuccessRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import type { ClauseBonus, TraitClause } from '../../rules/TraitEffects'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, RollFooter, SectionLabel, SteadyBody, TargetCard, useLatch, VerdictCard } from '../kit'
import { ClauseChips } from '../sheet/SheetTabs'

export const FORM_DETECT = 'FORM 7-X · COUNTER'

const signedInt = (n: number) => (n >= 0 ? `+${n}` : `${n}`)
const isSuccess = (r: RollResult) => r.outcome === 'SUCCESS' || r.outcome === 'CRITICAL_SUCCESS'
const toggled = (set: Set<string>, key: string) => {
  const next = new Set(set)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  return next
}

export function DetectionDialog({ spi, per, alertness = 0, shock = 0, hurt = 0, spiTrait = 0, states = { total: 0, line: null }, perClauses = [], spiPool = null, dice, onDismiss }: {
  spi: number
  per: number
  alertness?: number
  shock?: number
  hurt?: number
  spiTrait?: number
  states?: ClauseBonus
  perClauses?: TraitClause[]
  spiPool?: PoolStatus | null
  dice: RandomSource
  onDismiss: () => void
}) {
  const situations = Detection.modifiers()
  const items = Detection.items().filter((it) => it.detectionBonus !== null)
  const wards = AntiDivinationTechniques.onDetectionRoll()
  const [awareness, setAwarenessRaw] = useState<DivinationAwareness | null>(null)
  const [takeBonus, setTakeBonus] = useState(false)
  const [divinerIsWeaker, setDivinerIsWeaker] = useState(false)
  // remember(awareness): a new pick starts both answers over.
  const setAwareness = (row: DivinationAwareness | null) => { setAwarenessRaw(row); setTakeBonus(false); setDivinerIsWeaker(false) }
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [wardIds, setWardIds] = useState<Set<string>>(new Set())
  const [carried, setCarried] = useState<Set<string>>(new Set())
  const [result, setResult, liveResult] = useLatch<DetectionResult | null>(null)
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const perClause = TraitClauses.clauseBonus(perClauses, declared)
  const pickedWards = wards.filter((it) => wardIds.has(it.id))
  const pickedItems = items.filter((it) => carried.has(it.name))
  const charmTaken = pickedItems.length > 0
  const pickedSituations = situations.filter((it) => chosen.has(it.situation) && !(charmTaken && eqIc(it.situation, Detection.GENERIC_CHARM)))
  const bonus = takeBonus ? awareness?.bonus ?? 0 : 0
  const modifier = Detection.modifierTotal(pickedSituations, pickedWards, pickedItems, bonus)
  const freeRoll = awareness?.freeRoll ?? null
  const attrition = Detection.attritionFor(spiPool, awareness)
  const inputs = { modifier, freeRoll, alertness, shock, hurt, spiModifier: spiTrait, states, perClause, attrition }
  const target = Detection.targetFor(spi, per, inputs)
  const parts = Detection.partsFor(spi, per, {
    ...inputs, situations: pickedSituations, techniques: pickedWards, items: pickedItems, awarenessBonus: bonus, awarenessLabel: awareness?.pathway ?? null,
  })
  const grantsAutomatic = awareness?.isAutomatic === true
  const automatic = awareness !== null && Detection.isAutomaticAgainst(awareness, divinerIsWeaker)
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      dismiss={result !== null ? <CloseButton onClick={() => setResult(null)} label="Sense again" color="var(--on-surface)" /> : undefined}
      title={<FormTitle form={FORM_DETECT}>Am I being divined?</FormTitle>}
    >
      <SteadyBody>
        {result !== null ? (
          <div className="scroll">
            <VerdictCard verdict={result.row.result} passed={isSuccess(result.roll)} roll={result.roll}>
              <p className="bodyMedium" style={{ paddingTop: 8 }}>{result.row.effect}</p>
            </VerdictCard>
            <p className="bodySmall" style={{ paddingTop: 8 }}>{result.parts.join(', ')}</p>
          </div>
        ) : (
          <>
            <div className="scroll">
              <TargetCard target={target} baseTarget={Detection.targetFor(spi, per, { freeRoll })} />
              <SectionLabel text="Pathway awareness (Sec. 1)" />
              <p className="bodySmall">Some pathways feel it without rolling. Leave this off if yours is not listed.</p>
              <Chips>
                <CaseChip selected={awareness === null} onClick={() => setAwareness(null)}>None</CaseChip>
                {Detection.awareness().map((row) => (
                  <CaseChip key={row.pathway} selected={awareness === row} onClick={() => setAwareness(row)}>{substringBefore(row.pathway, ' (')}</CaseChip>
                ))}
              </Chips>
              {awareness !== null && (
                <>
                  <p className="bodySmall">{awareness.sequence} — {awareness.detection}</p>
                  {awareness.divinerPenalty !== null && (
                    <p className="bodySmall">This row grants no detection roll. Its {signedInt(awareness.divinerPenalty)} is on the diviner's roll, at the GM's discretion.</p>
                  )}
                  {awareness.bonus !== null && (
                    <>
                      <div style={{ paddingTop: 4 }}><CaseChip selected={takeBonus} onClick={() => setTakeBonus(!takeBonus)}>Take {signedInt(awareness.bonus)}</CaseChip></div>
                      {awareness.bonusText !== null && <p className="bodySmall">{awareness.bonusText}</p>}
                    </>
                  )}
                </>
              )}
              <SectionLabel text="Situation (Sec. 2)" />
              <Chips>
                {situations.map((row) => {
                  const superseded = charmTaken && eqIc(row.situation, Detection.GENERIC_CHARM)
                  return (
                    <CaseChip key={row.situation} selected={chosen.has(row.situation) && !superseded} disabled={superseded} onClick={() => setChosen(toggled(chosen, row.situation))}>
                      {row.situation} {signedInt(row.modifier)}
                    </CaseChip>
                  )
                })}
              </Chips>
              {charmTaken && <p className="bodySmall">The charm you named prices itself, so Sec. 2's general charm row does not also apply.</p>}
              <SectionLabel text="Your own protection (Sec. 3, 4)" />
              <p className="bodySmall">A ward's price is on its chip; spend it on the tracker. Nothing here holds a clock for the duration it buys.</p>
              <Chips>
                {wards.map((row) => {
                  const cost = row.bullets.find((it) => it.label === 'Cost')?.text
                  return (
                    <CaseChip key={row.id} selected={wardIds.has(row.id)} onClick={() => setWardIds(toggled(wardIds, row.id))}>
                      {row.name} {signedInt(row.detectionBonus ?? 0)}{cost !== undefined ? ` · ${cost}` : ''}
                    </CaseChip>
                  )
                })}
                {items.map((row) => (
                  <CaseChip key={row.name} selected={carried.has(row.name)} onClick={() => setCarried(toggled(carried, row.name))}>
                    {substringBefore(row.name, ' (')} {signedInt(row.detectionBonus ?? 0)}
                  </CaseChip>
                ))}
              </Chips>
              {grantsAutomatic && (
                <>
                  <div style={{ paddingTop: 8 }}><CaseChip selected={divinerIsWeaker} onClick={() => setDivinerIsWeaker(!divinerIsWeaker)}>The diviner is of weaker Sequence</CaseChip></div>
                  {!divinerIsWeaker && (
                    <p className="bodySmall" style={{ paddingTop: 4 }}>
                      Against an equal or stronger Sequence the book calls for a Quick Contest of SPI vs the diviner's Divination Arts or Ritualistic Magic,
                      which needs both sheets and is not rolled here. Sense below.
                    </p>
                  )}
                </>
              )}
              <ClauseChips clauses={perClauses} declared={declared} onDeclared={setDeclared} />
              {perClauses.length > 0 && !Detection.isPerRoll(spi, per, freeRoll) && <p className="bodySmall">This roll takes SPI, so a Perception clause does not reach it.</p>}
            </div>
            <RollFooter
              lines={[`${parts.join(', ')}. Free action, costs no SPI.`]}
              caption={automatic ? 'Automatic awareness — no roll.' : SuccessRoll.printed(target)}
              target={automatic ? null : target}
              disabled={automatic}
              icon="sensors"
              label="Sense"
              onRoll={() => {
                if (liveResult() !== null || automatic) return
                setResult(Detection.rollAgainst(target, dice, parts))
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
