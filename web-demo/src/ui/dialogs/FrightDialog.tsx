// FrightDialog.kt (FORM 4-F): the Fright Check, its effect, and the
// recovery roll a stun or a freeze row asks for.
import { useId, useState } from 'react'
import type { CharacterDice } from '../../app/RollLog'
import type { FrightRecovery, FrightTrigger } from '../../rules/content/schema.gen'
import * as FrightCheck from '../../rules/FrightCheck'
import type { FrightKind, FrightOutcome } from '../../rules/FrightCheck'
import { isBlank, trim } from '../../rules/kt'
import type { RollResult } from '../../rules/model'
import * as Shock from '../../rules/Shock'
import * as SuccessRoll from '../../rules/SuccessRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import * as TraitEffects from '../../rules/TraitEffects'
import type { ClauseBonus, TraitClause } from '../../rules/TraitEffects'
import { Caps, CaseChip, Chips, CloseButton, Dialog, FooterBand, FooterRow, FormTitle, InkAction, OptionStepper, RollButton, SectionLabel, signed, SteadyBody, TargetCard, useLatch } from '../kit'
import { ClauseChips } from '../sheet/SheetTabs'

export const FORM_FRIGHT = 'FORM 4-F · FRIGHT'

const isSuccess = (r: RollResult) => r.outcome === 'SUCCESS' || r.outcome === 'CRITICAL_SUCCESS'
const capitalised = (k: FrightKind) => k.charAt(0) + k.slice(1).toLowerCase()

export function FrightDialog({ will, iq, ht, clauses = [], willOnlyClauses = [], recoveryBonus = () => 0, shock = 0, hurt = 0, states = { total: 0, line: null }, onStunned, stunned, onQuirk, dice, onDismiss }: {
  will: number
  iq: number
  ht: number
  clauses?: TraitClause[]
  willOnlyClauses?: TraitClause[]
  recoveryBonus?: (r: FrightRecovery) => number
  shock?: number
  hurt?: number
  states?: ClauseBonus
  onStunned: (v: boolean) => void
  stunned: boolean
  onQuirk: (name: string) => void
  dice: CharacterDice
  onDismiss: () => void
}) {
  const [kind, setKind] = useState<FrightKind>('FRIGHT')
  const [out, setOut, liveOut] = useLatch<FrightOutcome | null>(null)
  const [picked, setPicked] = useState<FrightTrigger | null>(null)
  const [modifier, setModifier] = useState(0)
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const [recovery, setRecovery, liveRecovery] = useLatch<RollResult | null>(null)
  const [stunnedHere, setStunnedHere] = useState(false)
  const fromTraits = TraitClauses.clauseBonus(clauses, declared)
  const effective = modifier + shock + hurt + fromTraits.total + states.total
  const target = FrightCheck.targetFor(will, effective)

  const result = () => {
    const o = out!
    const failed = !FrightCheck.passed(o)
    const effect = o.effect
    const rec = effect !== null ? FrightCheck.recoveryFor(effect, kind) : null
    return (
      <div className="scroll">
        <FrightResultCard result={o} kind={kind} ht={ht} will={will} iq={iq} />
        {effect !== null && failed && FrightCheck.grantsQuirk(effect, kind) && <QuirkEntry onQuirk={onQuirk} />}
        {effect !== null && failed && FrightCheck.grantsDisadvantage(effect, kind) && (
          <p className="bodySmall" style={{ paddingTop: 8 }}>Record it in Spend points: buy it and tick Gained in play.</p>
        )}
        {rec !== null && (
          <FrightRecoveryPanel
            recovery={rec}
            attributeLevel={FrightCheck.levelFor(rec, will, iq)}
            bonus={recoveryBonus(rec)}
            clauseBonus={FrightCheck.takesWillClauses(rec) ? TraitClauses.clauseBonus(willOnlyClauses, declared) : { total: 0, line: null }}
            shock={shock}
            hurt={hurt}
            states={states}
            rolled={recovery}
            onRoll={(t) => {
              if (liveRecovery() !== null) return
              const rolled = SuccessRoll.against(t, dice('Fright recovery'))
              setRecovery(rolled)
              if (stunnedHere && isSuccess(rolled)) onStunned(false)
            }}
          />
        )}
      </div>
    )
  }

  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      title={
        <FormTitle form={FORM_FRIGHT}>
          <div>Fright Check</div>
          <div className="labelLarge" style={{ color: 'var(--primary)' }}>Will {will}</div>
        </FormTitle>
      }
    >
      <SteadyBody>
        {out !== null ? result() : (
          <>
            <div className="scroll">
              <TargetCard target={target} baseTarget={will} />
              <SectionLabel text="Kind" />
              <Chips>
                {FrightCheck.FRIGHT_KINDS.map((option) => <CaseChip key={option} selected={option === kind} onClick={() => setKind(option)}>{capitalised(option)}</CaseChip>)}
              </Chips>
              <SectionLabel text="What was witnessed" />
              <div role="radiogroup">
                {FrightCheck.triggers().map((trigger) => {
                  const selected = trigger === picked
                  return (
                    <button key={trigger.trigger} role="radio" aria-checked={selected} className="trigger-row" onClick={() => { setPicked(trigger); setModifier(trigger.modifier ?? 0) }}>
                      <span className={`radio${selected ? ' on' : ''}`} aria-hidden="true" />
                      <span className="bodyLarge grow" style={{ fontWeight: selected ? 600 : 400 }}>{trigger.trigger}</span>
                      <span className="labelLarge mono" style={{ color: selected ? 'var(--on-surface)' : 'var(--on-surface-variant)' }}>{trigger.printed}</span>
                    </button>
                  )
                })}
              </div>
              <OptionStepper
                label="Modifier"
                value={modifier}
                shown={signed(modifier)}
                note={picked !== null ? `the book prints ${picked.printed}${picked.openEnded ? ' — no floor, so worse is legal' : ''}` : 'pick what was witnessed'}
                onStep={(d) => { if (picked !== null) setModifier(modifier + d) }}
              />
              <ClauseChips clauses={clauses} declared={declared} onDeclared={setDeclared} />
            </div>
            <FooterBand>
              {shock !== 0 && <p className="labelMedium">includes {shock} from shock — "{Shock.printed()}"</p>}
              {hurt !== 0 && <p className="labelMedium">includes {hurt} from the HP and FP trackers</p>}
              {fromTraits.line !== null && <p className="labelMedium">includes {fromTraits.line}</p>}
              {states.line !== null && <p className="labelMedium">includes {states.line}</p>}
              <FooterRow
                target={null}
                boxed={false}
                button={
                  <RollButton
                    label="Roll"
                    disabled={picked === null}
                    onClick={() => {
                      if (liveOut() !== null || picked === null) return
                      const rolled = FrightCheck.rollAgainst(target, dice('Fright Check'), undefined, dice('Fright effect'))
                      setOut(rolled)
                      if (FrightCheck.leavesStunned(rolled.effect, kind, null)) {
                        setStunnedHere(!stunned)
                        onStunned(true)
                      }
                    }}
                  />
                }
              >
                <span className="labelLarge mono">{picked === null ? 'Pick what was witnessed.' : SuccessRoll.printed(target)}</span>
              </FooterRow>
            </FooterBand>
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}

function FrightResultCard({ result, kind, ht, will, iq }: { result: FrightOutcome; kind: FrightKind; ht: number; will: number; iq: number }) {
  const passed = FrightCheck.passed(result)
  const accent = passed ? 'var(--primary)' : 'var(--error)'
  const held = result.effect !== null ? FrightCheck.durationFor(result.effect, kind) : null
  return (
    <div className="fright-card" style={{ borderColor: accent }}>
      <p className="headlineMedium" style={{ color: accent }}>{passed ? 'Held their nerve' : 'Fright Check failed'}</p>
      <p className="titleMedium mono">3d6: {result.roll.total} vs {result.target}</p>
      {!passed && result.effect !== null && (
        <>
          <p>failed by {result.margin}; effect roll {result.effectRoll}</p>
          <p className="err" style={{ paddingTop: 8 }}>{result.effect.printed}: {FrightCheck.text(result.effect, kind)}</p>
          {held !== null && <p className="bodyMedium err">{FrightCheck.durationLine(held, ht, will, iq)}</p>}
        </>
      )}
    </div>
  )
}

function QuirkEntry({ onQuirk }: { onQuirk: (name: string) => void }) {
  const [name, setName] = useState('')
  const [recorded, setRecorded, live] = useLatch<string | null>(null)
  const id = useId()
  if (recorded !== null) return <p className="bodyMedium" style={{ paddingTop: 8 }}>Recorded on the sheet: {recorded}</p>
  return (
    <div style={{ paddingTop: 8 }}>
      <label htmlFor={id}><Caps text="The quirk, as the GM names it" /></label>
      <input id={id} className="text-field bodyLarge" value={name} autoComplete="off" onChange={(e) => setName(e.target.value)} />
      <InkAction
        label="Record the quirk"
        disabled={isBlank(name)}
        onClick={() => {
          if (live() !== null) return
          onQuirk(name)
          setRecorded(trim(name))
        }}
      />
    </div>
  )
}

function FrightRecoveryPanel({ recovery, attributeLevel, bonus, clauseBonus, shock, hurt, states, rolled, onRoll }: {
  recovery: FrightRecovery
  attributeLevel: number
  bonus: number
  clauseBonus: ClauseBonus
  shock: number
  hurt: number
  states: ClauseBonus
  rolled: RollResult | null
  onRoll: (target: number) => void
}) {
  const target = attributeLevel + bonus + clauseBonus.total + shock + hurt + states.total
  if (rolled === null) {
    return (
      <div style={{ paddingTop: 8 }}>
        <InkAction label={`Roll ${recovery.attribute === 'WILL' ? 'Will' : 'IQ'} ${target} to recover now`} icon="casino" onClick={() => onRoll(target)} />
        {bonus !== 0 && <p className="bodySmall">includes +{bonus} from {TraitEffects.COMBAT_REFLEXES} — "+6 to recover from stun"</p>}
        {clauseBonus.line !== null && <p className="bodySmall">includes {clauseBonus.line}</p>}
        {states.line !== null && <p className="bodySmall">includes {states.line}</p>}
      </div>
    )
  }
  return (
    <div style={{ paddingTop: 8 }}>
      <p className="titleMedium">3d6: {rolled.total} vs {rolled.target}</p>
      <p className="bodySmall" style={{ color: 'var(--primary)' }}>{isSuccess(rolled) ? 'Recovered.' : 'Still there; the row asks again.'}</p>
    </div>
  )
}
