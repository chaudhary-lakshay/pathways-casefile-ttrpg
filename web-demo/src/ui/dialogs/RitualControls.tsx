// RitualControls.kt: the pickers the Ritual and Boon dialogs share.
import { hpChoices, invocationApplied, spiChoices } from '../../app/DialogLines'
import type { LanguageProficiency, RitualComplexity, RitualRevelation, RitualWeight } from '../../rules/content/schema.gen'
import * as Languages from '../../rules/Languages'
import * as PowerSources from '../../rules/PowerSources'
import type { RitualPacing } from '../../rules/PowerSources'
import { CaseChip, Chips, OptionStepper, SectionLabel, signed } from '../kit'

export function CellValueChips({ printed, values, applied, onPick }: { printed: string; values: number[]; applied: number; onPick: (v: number) => void }) {
  const isSpan = values.length >= 2
  return (
    <>
      <p className="bodySmall" style={{ paddingTop: 4 }}>{isSpan ? `The book prints ${printed} — the GM picks:` : `The book prints ${printed}.`}</p>
      <Chips>
        {(isSpan ? values : [applied]).map((value) => (
          <CaseChip key={value} selected={applied === value} disabled={!isSpan} onClick={() => { if (isSpan) onPick(value) }}>{signed(value)}</CaseChip>
        ))}
      </Chips>
    </>
  )
}

const PACINGS: RitualPacing[] = ['RUSHED', 'NORMAL', 'EXTENDED']

export function RitualPowerSourceChips({ weight, complexity, spiChoice, cost, hpSacrificed, pacing, assistants, sacrifice, suspended, invoked, orthodox, invocation, onSpiChoice, onHpSacrificed, onPacing, onAssistants, onSacrificeStep, onToggleSuspended, onInvoked, onToggleOrthodox, onInvocationStep }: {
  weight: RitualWeight
  complexity: RitualComplexity
  spiChoice: number
  cost: number
  hpSacrificed: number
  pacing: RitualPacing
  assistants: number
  sacrifice: number
  suspended: boolean
  invoked: boolean
  orthodox: boolean
  invocation: number
  onSpiChoice: (n: number) => void
  onHpSacrificed: (n: number) => void
  onPacing: (p: RitualPacing) => void
  onAssistants: (n: number) => void
  onSacrificeStep: (d: number) => void
  onToggleSuspended: () => void
  onInvoked: (v: boolean) => void
  onToggleOrthodox: () => void
  onInvocationStep: (d: number) => void
}) {
  return (
    <>
      <SectionLabel text="Fuel" />
      <Chips>{spiChoices(weight).map((option) => <CaseChip key={option} selected={option === spiChoice} onClick={() => onSpiChoice(option)}>{option} SPI</CaseChip>)}</Chips>
      <SectionLabel text={`Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)`} />
      <Chips>{hpChoices(cost).map((option) => <CaseChip key={option} selected={option === hpSacrificed} onClick={() => onHpSacrificed(option)}>{option === 0 ? 'None' : `${option} HP`}</CaseChip>)}</Chips>
      <SectionLabel text="Pacing" />
      <Chips>
        {PACINGS.map((option) => {
          const shift = PowerSources.pacingModifier(complexity, option)
          return (
            <CaseChip key={option} selected={option === pacing} onClick={() => onPacing(option)}>
              {option === 'RUSHED' ? `Rushed ${signed(shift)}` : option === 'NORMAL' ? complexity.baseTime : `Extended ${signed(shift)}`}
            </CaseChip>
          )
        })}
      </Chips>
      <SectionLabel text="Assistants" />
      <Chips>
        {[0, 1, 2, 3, 4].map((option) => (
          <CaseChip key={option} selected={option === assistants} onClick={() => onAssistants(option)}>{option === 0 ? 'None' : `${option} ${signed(PowerSources.participantBonus(option))}`}</CaseChip>
        ))}
      </Chips>
      <p className="bodySmall">
        {assistants > PowerSources.PARTICIPANT_CAP ? `Capped at ${signed(PowerSources.PARTICIPANT_CAP)}; the rest still pool their SPI.` : `+1 each, capped at ${signed(PowerSources.PARTICIPANT_CAP)}.`}
      </p>
      <OptionStepper label="Sacrifice or catalyst" value={sacrifice} shown={signed(sacrifice)} note={`the GM's call, ${PowerSources.SACRIFICE_LOWEST} to ${signed(PowerSources.SACRIFICE_HIGHEST)}`} onStep={onSacrificeStep} />
      <div style={{ paddingTop: 8 }}><CaseChip selected={suspended} onClick={onToggleSuspended}>Suspended part-way {signed(PowerSources.SUSPENSION_PENALTY)}</CaseChip></div>
      <SectionLabel text="Power source" />
      <Chips>
        <CaseChip selected={!invoked} onClick={() => onInvoked(false)}>Personal SPI</CaseChip>
        <CaseChip selected={invoked} onClick={() => onInvoked(true)}>Invocation</CaseChip>
      </Chips>
      <CaseChip selected={invoked && orthodox} disabled={!invoked} onClick={() => { if (invoked) onToggleOrthodox() }}>Orthodox deity, in good standing {signed(PowerSources.ORTHODOX_BONUS)}</CaseChip>
      <OptionStepper
        label="Invocation"
        value={invocation}
        shown={signed(invocationApplied(invoked, orthodox, invocation) ?? 0)}
        note={!invoked ? 'praying to oneself adds nothing' : orthodox ? `an orthodox deity's +${PowerSources.ORTHODOX_BONUS}` : `the GM's call, ${signed(PowerSources.INVOCATION_LOWEST)} to ${signed(PowerSources.INVOCATION_HIGHEST)}`}
        onStep={(d) => { if (invoked && !orthodox) onInvocationStep(d) }}
      />
    </>
  )
}

export function RevelationPicker({ revelation, sacrifices, blessed, onRevelation, onSacrificeStep, onToggleBlessed }: {
  revelation: RitualRevelation | null
  sacrifices: number
  blessed: boolean
  onRevelation: (r: RitualRevelation | null) => void
  onSacrificeStep: (d: number) => void
  onToggleBlessed: () => void
}) {
  const reduces = revelation !== null && revelation.perSacrifice !== null
  const blessable = revelation !== null && revelation.reducibleTo !== null
  return (
    <>
      <SectionLabel text="Petitioning for revelations" />
      <Chips>
        <CaseChip selected={revelation === null} onClick={() => onRevelation(null)}>No entity</CaseChip>
        {PowerSources.revelations().map((row) => <CaseChip key={row.entity} selected={revelation === row} onClick={() => onRevelation(row)}>{row.entity} {row.penalty}</CaseChip>)}
      </Chips>
      <OptionStepper
        label="Sacrifices to appease"
        value={sacrifices}
        shown={signed(revelation !== null ? PowerSources.revelationPenalty(revelation, reduces ? sacrifices : 0, blessable && blessed) : 0)}
        note={revelation === null ? 'no entity petitioned' : reduces ? '-5 each; the GM says what counts' : "no sacrifice reduces a deity's"}
        onStep={(d) => { if (reduces) onSacrificeStep(d) }}
      />
      <CaseChip selected={blessable && blessed} disabled={!blessable} onClick={() => { if (blessable) onToggleBlessed() }}>Blessed by Deity, the GM declares: -10</CaseChip>
    </>
  )
}

export function MysticalLanguagePicker({ language, boughtLanguages, grantedLanguages, onLanguage }: {
  language: LanguageProficiency | null
  boughtLanguages: string[]
  grantedLanguages: string[]
  onLanguage: (l: LanguageProficiency | null) => void
}) {
  const said = [
    boughtLanguages.length > 0 ? `Bought by this character: ${boughtLanguages.join('; ')}.` : null,
    grantedLanguages.length > 0 ? `From the potion: ${grantedLanguages.join('; ')}.` : null,
  ].filter((it) => it !== null).join(' ')
  return (
    <>
      <SectionLabel text="Mystical language" />
      <Chips>
        <CaseChip selected={language === null} onClick={() => onLanguage(null)}>Not stated</CaseChip>
        {Languages.proficiencies(Languages.MYSTICAL).map((option) => (
          <CaseChip key={option.proficiency} selected={language?.proficiency === option.proficiency} onClick={() => onLanguage(option)}>{option.proficiency}</CaseChip>
        ))}
      </Chips>
      {said !== '' && <p className="bodySmall">{said} A trait may grant others, and which language this ritual uses is yours to say.</p>}
    </>
  )
}
