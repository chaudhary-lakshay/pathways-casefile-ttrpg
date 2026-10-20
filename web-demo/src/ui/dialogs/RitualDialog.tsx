// RitualDialog.kt (FORM 7-R): cast a ritual. A critical failure's CoR is
// charged when the dialog closes, once.
import { useEffect, useRef, useState } from 'react'
import { fuelLine, invocationApplied, ritualRefusal } from '../../app/DialogLines'
import { pickInside, valuesOf } from '../../app/session/SheetLines'
import type { LanguageProficiency, RitualPreparation, RitualRevelation, TargetLink } from '../../rules/content/schema.gen'
import * as Corruption from '../../rules/Corruption'
import * as Languages from '../../rules/Languages'
import { Incantations } from '../../rules/Mystical'
import * as PowerSources from '../../rules/PowerSources'
import type { RitualPacing } from '../../rules/PowerSources'
import type { RandomSource } from '../../rules/RandomSource'
import * as RitualMode from '../../rules/RitualMode'
import * as RitualResolution from '../../rules/RitualResolution'
import type { RitualOutcome } from '../../rules/RitualResolution'
import type { ClauseBonus } from '../../rules/TraitEffects'
import { printedCost } from '../../rules/content/members'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, OptionStepper, RuleNote, SectionLabel, signed, SteadyBody, useLatch } from '../kit'
import { CellValueChips, MysticalLanguagePicker, RevelationPicker, RitualPowerSourceChips } from './RitualControls'
import { RitualFooter, RitualResultCard } from './RitualParts'

export const FORM_RITUAL = 'FORM 7-R · RITUAL'
export const RITUAL_SKILL = 'Ritualistic Magic'

const neutral = (mode: RitualMode.RitualMode) => RitualMode.preparations(mode).find((it) => it.modifier === 0)!

export function RitualDialog({ skillLevel, conditionPenalty, shock = 0, states = { total: 0, line: null }, cor, boughtLanguages, grantedLanguages, dice, onCorGained, onSpendSpi, onSpendHp, onDismiss }: {
  skillLevel: number
  conditionPenalty: number
  shock?: number
  states?: ClauseBonus
  cor: number
  boughtLanguages: string[]
  grantedLanguages: string[]
  dice: RandomSource
  onCorGained: (n: number) => void
  onSpendSpi: (n: number) => void
  onSpendHp: (n: number) => void
  onDismiss: () => void
}) {
  const weights = RitualResolution.weights()
  const [weight, setWeightRaw] = useState(weights[0])
  const [mode, setModeRaw] = useState<RitualMode.RitualMode>('FULL')
  const [preparation, setPreparation] = useState<RitualPreparation>(neutral('FULL'))
  const [link, setLink] = useState<TargetLink | null>(null)
  const [flourish, setFlourish] = useState(false)
  // remember(mode): a new mode starts its preparation, link and flourish over.
  const setMode = (m: RitualMode.RitualMode) => { setModeRaw(m); setPreparation(neutral(m)); setLink(null); setFlourish(false) }
  const [heldPreparationPick, setHeldPreparationPick] = useState<[RitualPreparation, number] | null>(null)
  const [heldLinkPick, setHeldLinkPick] = useState<[TargetLink, number] | null>(null)
  const preparationPick = pickInside(preparation, heldPreparationPick)
  const linkPick = pickInside(link, heldLinkPick)
  const complexity = PowerSources.complexityFor(weight)
  const [spiChoice, setSpiChoiceRaw] = useState(weights[0].spi)
  const [hpSacrificed, setHpSacrificed] = useState(0)
  // remember(weight, spiChoice): a new fuel forgets the HP paid towards it.
  const setSpiChoice = (n: number) => { setSpiChoiceRaw(n); setHpSacrificed(0) }
  const setWeight = (w: typeof weight) => { setWeightRaw(w); setSpiChoiceRaw(w.spi); setHpSacrificed(0) }
  const [burned, setBurned] = useState(0)
  const [pacing, setPacing] = useState<RitualPacing>('NORMAL')
  const [assistants, setAssistants] = useState(0)
  const [sacrifice, setSacrifice] = useState(0)
  const [suspended, setSuspended] = useState(false)
  const [outcome, setOutcome, liveOutcome] = useLatch<RitualOutcome | null>(null)
  const [language, setLanguage] = useState<LanguageProficiency | null>(null)
  const canSpeak = language === null || Languages.canPerformRituals(language)
  const [invoked, setInvoked] = useState(false)
  const [orthodox, setOrthodox] = useState(false)
  const [invocation, setInvocation] = useState(PowerSources.INVOCATION_LOWEST)
  const [revelation, setRevelation] = useState<RitualRevelation | null>(null)
  const [sacrifices, setSacrifices] = useState(0)
  const [blessed, setBlessed] = useState(false)
  const effectiveSkill = skillLevel + conditionPenalty + shock + states.total
  const declaration = PowerSources.PowerSourceDeclaration({
    pacing,
    assistants,
    sacrifice,
    suspended,
    invocation: invocationApplied(invoked, orthodox, invocation),
    revelation: revelation !== null ? { entity: revelation, sacrifices: revelation.perSacrifice !== null ? sacrifices : 0, blessedByDeity: revelation.reducibleTo !== null && blessed } : null,
  })
  const target = canSpeak
    ? RitualResolution.targetFor(effectiveSkill, weight, cor, {
        preparation, language, mode, link, flourish, complexity, powerSources: declaration, preparationValue: preparationPick, linkValue: linkPick, burned,
      })
    : null
  const cost = RitualResolution.spiFor(weight, spiChoice)
  const declaredSpi = PowerSources.spiOwed(cost, hpSacrificed)

  // settle(): a critical failure's CoR, charged once, when the dialog goes.
  const settled = useRef(false)
  const onCor = useRef(onCorGained)
  onCor.current = onCorGained
  const settle = () => {
    const o = liveOutcome()
    if (settled.current || o === null) return
    settled.current = true
    if (o.critical?.corGained != null) onCor.current(o.critical.corGained)
  }
  useEffect(() => () => settle(), []) // eslint-disable-line react-hooks/exhaustive-deps
  const finish = () => { settle(); onDismiss() }

  const preparationValue = RitualMode.preparationValueFor(preparation, preparationPick)
  const terms = target === null ? [] : [
    `${RITUAL_SKILL} ${skillLevel}`,
    `${weight.weight} ${weight.modifier}`,
    `preparation ${signed(preparationValue)}`,
    link !== null ? `${signed(RitualMode.linkValueFor(link, linkPick))} ${link.link.toLowerCase()} link` : null,
    flourish ? `${signed(RitualMode.FLOURISH_BONUS)} flourish` : null,
    invocationApplied(invoked, orthodox, invocation) !== null ? `${signed(invocationApplied(invoked, orthodox, invocation)!)} invoked` : null,
    language?.ritualModifier ? `${language.ritualModifier} for an accented tongue` : null,
    conditionPenalty !== 0 ? `${conditionPenalty} hurt or tired` : null,
    shock !== 0 ? `${shock} shock` : null,
    states.line,
    Corruption.ritualPenalty(cor) !== 0 ? `${Corruption.ritualPenalty(cor)} for ${cor} CoR` : null,
    PowerSources.pacingModifier(complexity, pacing) !== 0 ? `${signed(PowerSources.pacingModifier(complexity, pacing))} ${pacing.toLowerCase()}` : null,
    PowerSources.participantBonus(assistants) !== 0 ? `${signed(PowerSources.participantBonus(assistants))} from ${assistants} assisting` : null,
    sacrifice !== 0 ? `${signed(sacrifice)} sacrifice` : null,
    suspended ? `${signed(PowerSources.SUSPENSION_PENALTY)} suspended` : null,
    burned > 0 ? `+${burned} SPI burned` : null,
  ].filter((it): it is string => it !== null)
  const refusal = ritualRefusal(canSpeak)
  const incantation = Incantations.standard()
  return (
    <Dialog
      onDismiss={finish}
      confirm={<CloseButton onClick={finish} />}
      title={
        <FormTitle form={FORM_RITUAL}>
          <div>Cast a ritual</div>
          <div className="labelLarge" style={{ color: 'var(--primary)' }}>{RITUAL_SKILL} {skillLevel}</div>
        </FormTitle>
      }
    >
      <SteadyBody>
        {outcome !== null ? (
          <div className="scroll"><RitualResultCard result={outcome} /></div>
        ) : (
          <>
            <div className="scroll">
              <SectionLabel text="Mode" />
              <Chips>
                {RitualMode.RITUAL_MODES.map((option) => <CaseChip key={option} selected={option === mode} onClick={() => setMode(option)}>{option === 'QUICK' ? 'Quick' : 'Full'}</CaseChip>)}
              </Chips>
              <SectionLabel text="Weight" />
              <Chips>
                {weights.map((option) => <CaseChip key={option.weight} selected={option.weight === weight.weight} onClick={() => setWeight(option)}>{option.weight}</CaseChip>)}
              </Chips>
              <RitualPowerSourceChips
                weight={weight}
                complexity={complexity}
                spiChoice={spiChoice}
                cost={cost}
                hpSacrificed={hpSacrificed}
                pacing={pacing}
                assistants={assistants}
                sacrifice={sacrifice}
                suspended={suspended}
                invoked={invoked}
                orthodox={orthodox}
                invocation={invocation}
                onSpiChoice={setSpiChoice}
                onHpSacrificed={setHpSacrificed}
                onPacing={setPacing}
                onAssistants={setAssistants}
                onSacrificeStep={(d) => setSacrifice(Math.min(Math.max(sacrifice + d, PowerSources.SACRIFICE_LOWEST), PowerSources.SACRIFICE_HIGHEST))}
                onToggleSuspended={() => setSuspended(!suspended)}
                onInvoked={setInvoked}
                onToggleOrthodox={() => setOrthodox(!orthodox)}
                onInvocationStep={(d) => setInvocation(Math.min(Math.max(invocation + d, PowerSources.INVOCATION_LOWEST), PowerSources.INVOCATION_HIGHEST))}
              />
              <OptionStepper label="Burn SPI" value={burned} shown={`+${burned}`} note="before rolling: 1 SPI buys +1, no cap, separate from the fuel" onStep={(d) => setBurned(Math.max(burned + d, 0))} />
              <RevelationPicker
                revelation={revelation}
                sacrifices={sacrifices}
                blessed={blessed}
                onRevelation={setRevelation}
                onToggleBlessed={() => setBlessed(!blessed)}
                onSacrificeStep={(d) => setSacrifices(Math.max(sacrifices + d, 0))}
              />
              <SectionLabel text={mode === 'QUICK' ? 'Preparation' : 'Preparation (Phase I)'} />
              <Chips>
                {RitualMode.preparations(mode).map((option) => (
                  <CaseChip key={option.quality} selected={option.quality === preparation.quality} onClick={() => setPreparation(option)}>{option.quality} {option.printed}</CaseChip>
                ))}
              </Chips>
              <CellValueChips printed={preparation.printed} values={valuesOf(preparation.modifier, preparation.best, preparation.openEnded)} applied={preparationValue} onPick={(v) => setHeldPreparationPick([preparation, v])} />
              {RitualMode.usesTargetLink(mode) && (
                <>
                  <SectionLabel text="Link to target" />
                  <Chips>
                    {RitualMode.links().map((option) => (
                      <CaseChip key={option.link} selected={link?.link === option.link} onClick={() => setLink(link?.link === option.link ? null : option)}>{option.link} {option.printed}</CaseChip>
                    ))}
                  </Chips>
                  {link !== null && (
                    <CellValueChips printed={link.printed} values={valuesOf(link.modifier, link.best, false)} applied={RitualMode.linkValueFor(link, linkPick)} onPick={(v) => setHeldLinkPick([link, v])} />
                  )}
                </>
              )}
              {RitualMode.usesFlourish(mode) && (
                <>
                  <SectionLabel text="Delivery (Phase II)" />
                  <Chips><CaseChip selected={flourish} onClick={() => setFlourish(!flourish)}>Flourish {signed(RitualMode.FLOURISH_BONUS)}</CaseChip></Chips>
                  <p className="bodySmall">
                    "The GM may award a Flourish Bonus ({signed(RitualMode.FLOURISH_BONUS)}) for good in-character delivery." Awarded, not earned — the app does not judge the performance.
                  </p>
                  <RuleNote title="Incantation (Phase II)">
                    <p className="bodySmall">{incantation.text}</p>
                    {Incantations.all().filter((it) => it.referenceOnly).map((ref) => (
                      <div key={ref.scope}>
                        <p className="bodySmall">{ref.scope}: {ref.text}</p>
                        {ref.parts.map((it) => <p key={it} className="bodySmall">{it}</p>)}
                        {ref.note !== null && <p className="bodySmall">{ref.note}</p>}
                      </div>
                    ))}
                  </RuleNote>
                </>
              )}
              <MysticalLanguagePicker language={language} boughtLanguages={boughtLanguages} grantedLanguages={grantedLanguages} onLanguage={setLanguage} />
              <RuleNote title="What these picks mean">
                {[
                  'Mode: ' + (mode === 'QUICK'
                    ? 'Routine work — four steps. Preparation reaches +2 and the target link is counted separately.'
                    : 'Consequential magic — three phases. Preparation reaches +3 and already includes the link.'),
                  weight.categories !== null ? `${weight.weight}: ${weight.categories}` : null,
                  `${weight.weight}: ${printedCost(weight)}, ${weight.baseTime} base time`,
                  link?.looksLike != null ? `${link.link} link: ${link.looksLike}` : null,
                  language !== null ? `${language.proficiency}: ${language.effect}` : null,
                ].filter((it) => it !== null).map((it) => <p key={it} className="bodySmall" style={{ paddingTop: 4 }}>{it}</p>)}
              </RuleNote>
            </div>
            <RitualFooter
              terms={terms}
              target={target}
              cost={`costs ${fuelLine(declaredSpi + burned, hpSacrificed)}`}
              refusal={refusal}
              onCast={() => {
                if (liveOutcome() !== null || target === null || refusal !== null) return
                setOutcome(RitualResolution.rollAgainst(target, dice))
                onSpendSpi(declaredSpi + burned)
                if (hpSacrificed > 0) onSpendHp(hpSacrificed)
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
