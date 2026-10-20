// BoonDialog.kt (FORM 13-B): petition a patron for the next boon. The boon
// and a critical failure's CoR are settled once, when the dialog closes.
import { useEffect, useRef, useState } from 'react'
import { boonRefusal, fuelLine, hpChoices, standingRefusal } from '../../app/DialogLines'
import { formatDelta } from '../../app/PathwayModel'
import { endsBetween } from '../../app/session/SheetLines'
import { boonPill } from '../../app/SheetModel'
import * as Boons from '../../rules/Boons'
import { boonModifierKey } from '../../rules/content/members'
import type { BoonModifier, LanguageProficiency, SequenceBlock } from '../../rules/content/schema.gen'
import * as Corruption from '../../rules/Corruption'
import { isBlank } from '../../rules/kt'
import type { BoonState } from '../../rules/model'
import * as NonStandardPathways from '../../rules/NonStandardPathways'
import * as PowerSources from '../../rules/PowerSources'
import type { RandomSource } from '../../rules/RandomSource'
import * as RitualResolution from '../../rules/RitualResolution'
import type { RitualOutcome } from '../../rules/RitualResolution'
import type { ClauseBonus } from '../../rules/TraitEffects'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, OptionStepper, RuleNote, SectionLabel, signed, SteadyBody, useLatch, VerdictCard } from '../kit'
import { Abilities } from '../pathway/PathwayParts'
import { MysticalLanguagePicker } from './RitualControls'
import { RitualFailureLines, RitualFooter } from './RitualParts'

export const FORM_BOON = 'FORM 13-B · PETITION'

function GrantedBlock({ block }: { block: SequenceBlock }) {
  const gains = block.stats.map((it) => `${it.attr} ${formatDelta(it.delta)}`).join(' · ')
  return (
    <>
      {gains !== '' && <p className="bodyMedium">{gains}</p>}
      {block.skills.length > 0 && <p className="bodySmall muted">{block.skills.map((it) => `${it.name} ${signed(it.bonus)}`).join(' · ')}</p>}
      {([['Advantages', block.advantages], ['Drawbacks', block.drawbacks]] as const).map(([label, text]) =>
        isBlank(text) ? null : <RuleNote key={label} title={label}><p className="bodySmall">{text}</p></RuleNote>,
      )}
      {isBlank(block.ability)
        ? <p className="bodySmall err">The book prints an Ability heading for this block and nothing under it.</p>
        : <Abilities title="Abilities" text={block.ability} />}
      {!isBlank(block.reinforcedAbilities) && <Abilities title="Reinforced abilities" text={block.reinforcedAbilities} />}
    </>
  )
}

interface Prayed {
  outcome: RitualOutcome
  asked: BoonState | null
}

export function BoonDialog({ skillLevel, conditionPenalty, shock = 0, states = { total: 0, line: null }, cor, held, nextBoon, boughtLanguages = [], grantedLanguages = [], lost = false, dice, onCorGained, onSpendSpi, onSpendHp, onGranted, onDismiss }: {
  skillLevel: number | null
  conditionPenalty: number
  shock?: number
  states?: ClauseBonus
  cor: number
  held: BoonState | null
  nextBoon: (patron: string) => BoonState | null
  boughtLanguages?: string[]
  grantedLanguages?: string[]
  lost?: boolean
  dice: RandomSource
  onCorGained: (n: number) => void
  onSpendSpi: (n: number) => void
  onSpendHp: (n: number) => void
  onGranted: (b: BoonState) => void
  onDismiss: () => void
}) {
  const granting = Boons.granting()
  const groups = Boons.groups()
  const byGroup = new Map(groups.map((g) => [g, Boons.modifiers().filter((it) => it.group === g)]))
  const [chosen, setChosen] = useState<Map<string, BoonModifier>>(() => new Map(groups.map((g) => [g, byGroup.get(g)!.find((it) => it.modifier === 0)!])))
  const [picked, setPicked] = useState<Map<string, number>>(new Map())
  const [spiChoice, setSpiChoiceRaw] = useState(granting.spi)
  const [hpSacrificed, setHpSacrificed] = useState(0)
  // remember(spiChoice): a new fuel forgets the HP paid towards it.
  const setSpiChoice = (n: number) => { setSpiChoiceRaw(n); setHpSacrificed(0) }
  const [burned, setBurned] = useState(0)
  const [patron, setPatron] = useState<string | null>(held?.pathway ?? null)
  const [prayed, setPrayed, livePrayed] = useLatch<Prayed | null>(null)
  const [language, setLanguage] = useState<LanguageProficiency | null>(null)
  const next = patron !== null ? nextBoon(patron) : null
  const picks = groups.flatMap((g) => (chosen.has(g) ? [chosen.get(g)!] : []))
  const overrides = new Map(picks.flatMap((row) => (picked.has(boonModifierKey(row)) ? [[boonModifierKey(row), picked.get(boonModifierKey(row))!] as [string, number]] : [])))
  const canSpeak = language === null || language.canPerformRituals === true
  const target = skillLevel === null || next === null || !canSpeak
    ? null
    : Boons.targetFor(skillLevel + conditionPenalty + shock + states.total, cor, next.sequence, picks, overrides, language, burned)
  const cost = Boons.spiFor(spiChoice)
  const spiOwed = PowerSources.spiOwed(cost, hpSacrificed)

  // settle(): the critical's CoR and the boon granted, once, as the dialog goes.
  const settled = useRef(false)
  const callbacks = useRef({ onCorGained, onGranted })
  callbacks.current = { onCorGained, onGranted }
  const settle = () => {
    const p = livePrayed()
    if (settled.current || p === null) return
    settled.current = true
    if (p.outcome.critical?.corGained != null) callbacks.current.onCorGained(p.outcome.critical.corGained)
    if (RitualResolution.succeeded(p.outcome) && p.asked !== null) callbacks.current.onGranted(p.asked)
  }
  useEffect(() => () => settle(), []) // eslint-disable-line react-hooks/exhaustive-deps
  const finish = () => { settle(); onDismiss() }

  const standing = standingRefusal(skillLevel, lost)
  const refusal = boonRefusal(skillLevel, patron, held, next, lost, language)
  return (
    <Dialog onDismiss={finish} confirm={<CloseButton onClick={finish} />} title={<FormTitle form={FORM_BOON}>Petition for a boon</FormTitle>}>
      <SteadyBody>
        {prayed !== null ? (
          <div className="scroll">
            <VerdictCard verdict={RitualResolution.succeeded(prayed.outcome) ? 'The boon is granted' : 'The prayer fails'} passed={RitualResolution.succeeded(prayed.outcome)} roll={prayed.outcome.roll}>
              <RitualFailureLines result={prayed.outcome} />
            </VerdictCard>
            {RitualResolution.succeeded(prayed.outcome) && prayed.asked !== null && (
              <>
                <p style={{ paddingTop: 8, color: 'var(--on-surface)' }}>
                  {boonPill(prayed.asked).replace(/^Boon · /, '')}, of the {prayed.asked.pathway} Pathway. Its gains are on the sheet when this closes.
                </p>
                <RuleNote title="The patron is aware of you now">
                  {granting.consequences.map((it) => <p key={it} className="bodySmall">• {it}</p>)}
                </RuleNote>
              </>
            )}
          </div>
        ) : (
          <>
            <div className="scroll">
              {standing !== null && <p className="bodyMedium err" style={{ paddingBottom: 8 }}>{standing}</p>}
              {held === null ? (
                <>
                  <SectionLabel text="Patron's pathway" />
                  <Chips>
                    {NonStandardPathways.patrons().map((option) => <CaseChip key={option} selected={option === patron} onClick={() => setPatron(option)}>{option}</CaseChip>)}
                  </Chips>
                </>
              ) : (
                <p style={{ color: 'var(--on-surface)' }}>Held: {boonPill(held).replace(/^Boon · /, '')}, {held.pathway} Pathway</p>
              )}
              {groups.map((group) => {
                const rows = byGroup.get(group)!
                const selected = chosen.get(group) ?? null
                const ends = selected !== null && selected.best !== null ? endsBetween(selected.modifier, selected.best) : null
                return (
                  <div key={group}>
                    <SectionLabel text={group} />
                    <Chips>
                      {rows.map((option) => (
                        <CaseChip key={option.label} selected={selected?.label === option.label} onClick={() => setChosen(new Map(chosen).set(group, option))}>
                          {option.label} ({option.printed})
                        </CaseChip>
                      ))}
                    </Chips>
                    {rows.some((it) => it.best !== null) && (
                      <Chips>
                        {(ends ?? [selected?.modifier ?? 0]).map((value) => (
                          <CaseChip
                            key={value}
                            selected={selected !== null && (picked.get(boonModifierKey(selected)) ?? selected.modifier) === value}
                            disabled={ends === null}
                            onClick={() => { if (ends !== null && selected !== null) setPicked(new Map(picked).set(boonModifierKey(selected), value)) }}
                          >
                            {signed(value)}
                          </CaseChip>
                        ))}
                      </Chips>
                    )}
                  </div>
                )
              })}
              <SectionLabel text="Fuel" />
              <Chips>
                {Array.from({ length: granting.spiMax - granting.spi + 1 }, (_, i) => granting.spi + i).map((option) => (
                  <CaseChip key={option} selected={option === spiChoice} onClick={() => setSpiChoice(option)}>{option} SPI</CaseChip>
                ))}
              </Chips>
              <OptionStepper label="Burn SPI" value={burned} shown={`+${burned}`} note="before rolling: 1 SPI buys +1, no cap, separate from the fuel" onStep={(d) => setBurned(Math.max(burned + d, 0))} />
              <SectionLabel text={`Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)`} />
              <Chips>
                {hpChoices(cost).map((option) => <CaseChip key={option} selected={option === hpSacrificed} onClick={() => setHpSacrificed(option)}>{option === 0 ? 'None' : `${option} HP`}</CaseChip>)}
              </Chips>
              <MysticalLanguagePicker language={language} boughtLanguages={boughtLanguages} grantedLanguages={grantedLanguages} onLanguage={setLanguage} />
              {next !== null && (() => {
                const chain = NonStandardPathways.chainFor(next)
                const block = chain[chain.length - 1]
                return (
                  <>
                    <p className="titleMedium" style={{ paddingTop: 8, color: 'var(--primary)' }}>
                      {block.name} — Sequence {next.sequence} · {signed(Boons.penaltyForSequence(next.sequence))}
                    </p>
                    <GrantedBlock block={block} />
                  </>
                )
              })()}
              <RuleNote title="What a petition needs">
                {granting.requirements.map((it) => <p key={it} className="bodySmall">• {it}</p>)}
              </RuleNote>
              <RuleNote title="How the boon's level is priced">
                <p className="bodySmall">{granting.boonLevelRule}</p>
              </RuleNote>
            </div>
            <RitualFooter
              terms={[
                skillLevel !== null ? `${granting.skill} ${skillLevel}` : null,
                `${granting.weight} ${granting.weightModifier}`,
                next !== null ? `Sequence ${next.sequence} ${signed(Boons.penaltyForSequence(next.sequence))}` : null,
                `circumstances ${signed(Boons.modifierTotal(picks, overrides))}`,
                language?.ritualModifier ? `${language.ritualModifier} for an accented tongue` : null,
                conditionPenalty !== 0 ? `${conditionPenalty} hurt or tired` : null,
                shock !== 0 ? `${shock} shock` : null,
                states.line,
                Corruption.ritualPenalty(cor) !== 0 ? `${Corruption.ritualPenalty(cor)} for ${cor} CoR` : null,
                burned > 0 ? `+${burned} SPI burned` : null,
              ].filter((it): it is string => it !== null)}
              target={target}
              cost={`costs ${fuelLine(spiOwed + burned, hpSacrificed)}, takes ${granting.complexityTime}`}
              refusal={refusal}
              label="Pray"
              icon="church"
              onCast={() => {
                if (livePrayed() !== null || refusal !== null || target === null) return
                setPrayed({ outcome: RitualResolution.rollAgainst(target, dice), asked: next })
                onSpendSpi(spiOwed + burned)
                if (hpSacrificed > 0) onSpendHp(hpSacrificed)
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
