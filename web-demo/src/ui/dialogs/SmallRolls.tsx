// UntrainedDialog.kt (FORM 2-U), AttributeRollDialog.kt (FORM 3-A) and
// SelfControlDialog.kt (FORM 3-C): the three short rolls off the sheet.
import { useId, useState } from 'react'
import { controlKeyLine, skillKeyLabel, untrainedShown } from '../../app/SheetText'
import type { CharacterDice } from '../../app/RollLog'
import * as AttributeRoll from '../../rules/AttributeRoll'
import type { RollResult, Trait } from '../../rules/model'
import type { PoolStatus } from '../../rules/Pools'
import * as SelfControl from '../../rules/SelfControl'
import type { SelfControlCheck, SelfControlOutcome } from '../../rules/SelfControl'
import * as SkillDefaults from '../../rules/SkillDefaults'
import type { Untrained } from '../../rules/SkillDefaults'
import * as SuccessRoll from '../../rules/SuccessRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import type { ClauseBonus, TraitClause } from '../../rules/TraitEffects'
import { Caps, CloseButton, Dialog, FormTitle, OptionStepper, outcomeLabel, RollFooter, signed, SteadyBody, TargetCard, useLatch, VerdictCard } from '../kit'
import { ClauseChips } from '../sheet/SheetTabs'

export const FORM_UNTRAINED = 'FORM 2-U · UNTRAINED'
export const FORM_ATTRIBUTE = 'FORM 3-A · ATTRIBUTE ROLL'
export const FORM_CONTROL = 'FORM 3-C · SELF-CONTROL'

const isSuccess = (r: RollResult) => r.outcome === 'SUCCESS' || r.outcome === 'CRITICAL_SUCCESS'

export function UntrainedDialog({ rows, weaponNote, onPick, onDismiss }: {
  rows: Untrained[]
  weaponNote: string | null
  onPick: (row: Untrained) => void
  onDismiss: () => void
}) {
  const [query, setQuery] = useState('')
  const id = useId()
  const shown = untrainedShown(rows, query)
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={FORM_UNTRAINED}>Roll a skill untrained</FormTitle>}>
      <label htmlFor={id}><Caps text="Find a skill" /></label>
      <input id={id} className="text-field bodyLarge" value={query} autoComplete="off" onChange={(e) => setQuery(e.target.value)} />
      <div className="scroll">
        {shown.map((row) => (
          <button key={skillKeyLabel(row.key)} className="untrained-row" onClick={() => onPick(row)}>
            <span className="grow">
              <span className="bodyLarge" style={{ display: 'block' }}>{skillKeyLabel(row.key)}</span>
              <span className="bodySmall muted">{row.source}</span>
            </span>
            <span className="titleMedium mono">{row.level}</span>
          </button>
        ))}
        {[SkillDefaults.NOT_LISTED, SkillDefaults.PLAIN_ROLL, weaponNote].map(
          (it) => it !== null && <p key={it} className="bodySmall muted" style={{ paddingTop: 8 }}>{it}</p>,
        )}
      </div>
    </Dialog>
  )
}

export function AttributeRollDialog({ kind, level, traits, shock, hurt, states, spi = null, dice, onDismiss }: {
  kind: AttributeRoll.Kind
  level: number
  traits: Trait[]
  shock: number
  hurt: number
  states: ClauseBonus
  spi?: PoolStatus | null
  dice: CharacterDice
  onDismiss: () => void
}) {
  const label = AttributeRoll.KINDS[kind].label
  const clauses = AttributeRoll.clauses(kind, traits)
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const [gm, setGm] = useState(0)
  const [out, setOut, liveOut] = useLatch<RollResult | null>(null)
  const fromTraits = TraitClauses.clauseBonus(clauses, declared)
  const target = AttributeRoll.targetFor(kind, level, traits, shock, hurt, fromTraits.total + states.total + gm, spi)
  const lines = [
    ...AttributeRoll.partsFor(kind, level, traits, shock, hurt, spi),
    ...[fromTraits.line, states.line, gm !== 0 ? `GM ${signed(gm)}` : null].filter((it): it is string => it !== null),
  ]
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      dismiss={out !== null ? <CloseButton onClick={() => setOut(null)} label="Roll again" color="var(--on-surface)" /> : undefined}
      title={<FormTitle form={FORM_ATTRIBUTE}>{label} roll</FormTitle>}
    >
      <SteadyBody>
        {out !== null ? (
          <VerdictCard verdict={outcomeLabel(out.outcome)} passed={isSuccess(out)} roll={out} />
        ) : (
          <>
            <div className="scroll">
              <TargetCard target={target} baseTarget={level} />
              <ClauseChips clauses={clauses} declared={declared} onDeclared={setDeclared} />
              <OptionStepper label="GM" value={gm} shown={signed(gm)} note="the GM's, for the circumstance" onStep={(d) => setGm(gm + d)} />
            </div>
            <RollFooter
              lines={lines}
              caption={SuccessRoll.printed(target)}
              target={target}
              onRoll={() => {
                if (liveOut() !== null) return
                setOut(SuccessRoll.against(target, dice(`${label} roll`)))
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}

export function SelfControlDialog({ check, standing, clauses = [], states = { total: 0, line: null }, dice, onDismiss }: {
  check: SelfControlCheck
  standing: number
  clauses?: TraitClause[]
  states?: ClauseBonus
  dice: CharacterDice
  onDismiss: () => void
}) {
  const [modifier, setModifier] = useState(0)
  const [out, setOut, liveOut] = useLatch<SelfControlOutcome | null>(null)
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const fromTraits = TraitClauses.clauseBonus(clauses, declared)
  const target = standing + modifier + fromTraits.total
  const held = out !== null && SelfControl.outcomeHeld(out)
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      title={
        <FormTitle form={FORM_CONTROL}>
          <div>{check.trait}</div>
          <div className="labelLarge" style={{ color: 'var(--primary)' }}>{controlKeyLine(check.spec)}</div>
        </FormTitle>
      }
    >
      <SteadyBody>
        {out !== null ? (
          <VerdictCard verdict={held ? 'Held' : 'Gave in'} passed={held} roll={out.roll}>
            <p className="bodyMedium" style={{ paddingTop: 8, color: held ? 'var(--on-surface)' : 'var(--error)' }}>{check.printed}</p>
          </VerdictCard>
        ) : (
          <>
            <div className="scroll">
              <TargetCard target={target} baseTarget={standing} />
              <p className="bodyMedium" style={{ paddingTop: 8 }}>{check.printed}</p>
              <OptionStepper label="Modifier" value={modifier} shown={signed(modifier)} note="the GM's, for the circumstance" onStep={(d) => setModifier(modifier + d)} />
              <ClauseChips clauses={clauses} declared={declared} onDeclared={setDeclared} />
            </div>
            <RollFooter
              lines={[fromTraits.line, states.line].filter((it): it is string => it !== null).map((it) => `includes ${it}`)}
              caption={SuccessRoll.printed(target)}
              target={target}
              onRoll={() => {
                if (liveOut() !== null) return
                setOut(SelfControl.rollAgainst(check, target, dice(check.trait)))
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
