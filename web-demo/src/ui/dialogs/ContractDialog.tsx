// ContractDialog.kt (FORM 9-K): a spirit contract, cast once.
import { useState } from 'react'
import { contractLines } from '../../app/DialogLines'
import type { RollResult } from '../../rules/model'
import type { RandomSource } from '../../rules/RandomSource'
import * as SpiritContract from '../../rules/SpiritContract'
import type { ContractPenalty } from '../../rules/SpiritContract'
import * as SuccessRoll from '../../rules/SuccessRoll'
import type { ClauseBonus } from '../../rules/TraitEffects'
import { CloseButton, Dialog, FormTitle, OptionStepper, RollFooter, RuleNote, SteadyBody, useLatch, VerdictCard } from '../kit'

export const FORM_CONTRACT = 'FORM 9-K · CONTRACT'

const isSuccess = (r: RollResult) => r.outcome === 'SUCCESS' || r.outcome === 'CRITICAL_SUCCESS'

export function ContractDialog({ skillLevel, conditionPenalty, shock, states, cor, sequence, maxContracts, dice, onSpendSpi, onDismiss }: {
  skillLevel: number
  conditionPenalty: number
  shock: number
  states: ClauseBonus
  cor: number
  sequence: number
  maxContracts: number
  dice: RandomSource
  onSpendSpi: (n: number) => void
  onDismiss: () => void
}) {
  const [held, setHeld] = useState(0)
  const [burned, setBurned] = useState(0)
  const [result, setResult, liveResult] = useLatch<[ContractPenalty, RollResult] | null>(null)
  const skill = skillLevel + conditionPenalty + shock + states.total
  const before = SpiritContract.targetFor(skill, cor, { sequence, dice: [] }, burned)
  const full = held >= maxContracts
  const contract = SpiritContract.contract()
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={FORM_CONTRACT}>Spirit contract</FormTitle>}>
      <SteadyBody>
        {result !== null ? (
          (() => {
            const [penalty, roll] = result
            const row = SpiritContract.outcomeFor(roll)
            return (
              <>
                {penalty.dice.length > 0 && (
                  <p className="bodySmall">Contracts held: {penalty.dice.join(' + ')} = -{penalty.dice.reduce((a, b) => a + b, 0)}</p>
                )}
                <VerdictCard verdict={row.printed} passed={isSuccess(roll)} roll={roll} />
                <p className="bodyMedium" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>{row.outcome}</p>
              </>
            )
          })()
        ) : (
          <>
            <div className="scroll">
              <OptionStepper
                label="Creatures already under contract"
                value={held}
                note={`each adds 1d6 to the penalty; the most is ${maxContracts}`}
                onStep={(d) => setHeld(Math.min(Math.max(held + d, 0), maxContracts))}
              />
              <OptionStepper
                label="Burn SPI"
                value={burned}
                shown={`+${burned}`}
                note={`before rolling: 1 SPI buys +1, beside the ${SpiritContract.spi()} SPI cost`}
                onStep={(d) => setBurned(Math.max(burned + d, 0))}
              />
              <RuleNote title="What the book says of the contract">
                <p className="bodySmall">{contract.difficulty}</p>
                <p className="bodySmall">{contract.time}. {contract.setup}</p>
                {contract.methods.map((it) => <p key={it.name} className="bodySmall">{it.name}: {it.text}</p>)}
                <p className="bodySmall">{contract.demands}</p>
              </RuleNote>
            </div>
            <RollFooter
              lines={contractLines(skillLevel, conditionPenalty, shock, states, cor, sequence, held, burned)}
              caption={full ? `${maxContracts} contracts held: no other can be made` : SuccessRoll.printed(before)}
              target={held === 0 ? before : null}
              disabled={full}
              label="Cast"
              onRoll={() => {
                if (liveResult() !== null || held >= maxContracts) return
                const penalty = SpiritContract.penaltyFor(sequence, held, maxContracts, dice)
                onSpendSpi(SpiritContract.spi() + burned)
                setResult([penalty, SuccessRoll.against(SpiritContract.targetFor(skill, cor, penalty, burned), dice)])
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
