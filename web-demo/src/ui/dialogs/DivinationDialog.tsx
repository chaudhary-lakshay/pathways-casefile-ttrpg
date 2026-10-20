// DivinationDialog.kt (FORM 7-D): a question, a method, the circumstances,
// the target's protection, and one roll that charges its SPI.
import { useState } from 'react'
import { endsBetween, repeatChoices } from '../../app/session/SheetLines'
import * as AntiDivination from '../../rules/AntiDivination'
import type { AntiDivinationResult } from '../../rules/AntiDivination'
import type { DivinationMethod } from '../../rules/content/schema.gen'
import * as Divination from '../../rules/Divination'
import type { DivinationOutcome } from '../../rules/Divination'
import { substringBefore } from '../../rules/kt'
import { AntiDivinationTechniques } from '../../rules/Mystical'
import { threeDSix, type RandomSource } from '../../rules/RandomSource'
import * as RitualResolution from '../../rules/RitualResolution'
import * as SuccessRoll from '../../rules/SuccessRoll'
import * as TraitEffects from '../../rules/TraitEffects'
import type { ClauseBonus } from '../../rules/TraitEffects'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, OptionStepper, RuleNote, SectionLabel, signed, SteadyBody, useLatch, VerdictCard } from '../kit'
import { RitualFooter } from './RitualParts'

export const FORM_DIVINE = 'FORM 7-D · DIVINATION'

const spiLabel = (m: DivinationMethod) => (m.spiMax !== null ? `${m.spi}-${m.spiMax}` : `${m.spi}`)
const techniquesOnly = (penalty: number): AntiDivinationResult => ({
  penalty, requiresCritical: false, parts: penalty === 0 ? [] : [`active techniques ${signed(penalty)}`], hpSpent: 0,
})
const toggled = (set: Set<string>, key: string) => {
  const next = new Set(set)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  return next
}

interface Asked {
  outcome: DivinationOutcome
  backlash: string | null
  hpBurned: number
}

export function DivinationDialog({ skillLevel, sourceLine = null, conditionPenaltyAfterSpending, shock = 0, states = { total: 0, line: null }, spiTrait = 0, divinerSequence = null, dice, onSpendSpi, onSpendHp, onDismiss }: {
  skillLevel: number
  sourceLine?: string | null
  conditionPenaltyAfterSpending: (cost: number) => number
  shock?: number
  states?: ClauseBonus
  spiTrait?: number
  divinerSequence?: number | null
  dice: RandomSource
  onSpendSpi: (n: number) => void
  onSpendHp: (n: number) => void
  onDismiss: () => void
}) {
  const difficulties = Divination.difficulties()
  const methods = Divination.methods()
  const rows = Divination.modifiers().filter((it) => !it.crossReference)
  const allTechniques = AntiDivinationTechniques.all()
  const [difficulty, setDifficulty] = useState(difficulties[0])
  const [method, setMethodRaw] = useState(methods[0])
  const [spiChoice, setSpiChoice] = useState(methods[0].spi)
  const setMethod = (m: DivinationMethod) => { setMethodRaw(m); setSpiChoice(m.spi) }
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [asked, setAsked, liveAsked] = useLatch<Asked | null>(null)
  const [picked, setPicked] = useState<Map<string, number>>(new Map())
  const [repeats, setRepeats] = useState(1)
  const [burned, setBurned] = useState(0)
  const [targetSequence, setTargetSequenceRaw] = useState<number | null>(null)
  const [directLink, setDirectLink] = useState(false)
  const [sacrificeHp, setSacrificeHp] = useState(false)
  // remember(targetSequence): a new target forgets the link and the sacrifice.
  const setTargetSequence = (s: number | null) => { setTargetSequenceRaw(s); setDirectLink(false); setSacrificeHp(false) }
  const hpForSacrifice = AntiDivination.counterNamed('HP Sacrifice').hpRequired ?? 0
  const [techniqueIds, setTechniqueIds] = useState<Set<string>>(new Set())
  const techniques = allTechniques.filter((it) => techniqueIds.has(it.id))
  const techniquePenalty = AntiDivinationTechniques.penaltyTotal(techniques)
  const protection = targetSequence !== null
    ? AntiDivination.against(targetSequence, divinerSequence, techniquePenalty, directLink, sacrificeHp ? hpForSacrifice : 0)
    : techniquesOnly(techniquePenalty)
  const cost = Divination.spiFor(method, spiChoice)
  const picks = rows.filter((it) => chosen.has(it.source))
  const overrides = new Map(picks.flatMap((row) => (picked.has(row.source) ? [[row.source, picked.get(row.source)!] as [string, number]] : [])))
  const circumstances = Divination.modifierTotal(picks, overrides, repeats) + conditionPenaltyAfterSpending(cost + burned)
  const extras = circumstances + protection.penalty + shock + states.total + spiTrait
  const target = Divination.targetFor(skillLevel, difficulty, extras, burned)
  const spent = cost + burned

  const ask = () => {
    if (liveAsked() !== null) return
    const outcome = Divination.rollAgainst(target, dice, protection.requiresCritical)
    if (protection.hpSpent > 0) onSpendHp(protection.hpSpent)
    const backlash = Divination.outcomeIsCritical(outcome) ? RitualResolution.criticalFor(threeDSix(dice)).outcome : null
    setAsked({ outcome, backlash, hpBurned: protection.hpSpent })
    onSpendSpi(spent)
  }

  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      dismiss={asked !== null ? <CloseButton onClick={() => { setAsked(null); setBurned(0) }} label="Ask again" color="var(--on-surface)" /> : undefined}
      title={<FormTitle form={FORM_DIVINE}>Divination Arts {skillLevel}</FormTitle>}
    >
      <SteadyBody>
        {asked !== null ? (
          <div className="scroll">
            <VerdictCard verdict={asked.outcome.clarity.result} passed={['SUCCESS', 'CRITICAL_SUCCESS'].includes(Divination.effectiveOutcome(asked.outcome))} roll={asked.outcome.roll}>
              <p className="titleMedium" style={{ paddingTop: 8 }}>{asked.outcome.clarity.clarity}</p>
              <p className="bodyMedium">{asked.outcome.clarity.effect}</p>
              {asked.hpBurned > 0 && <p className="bodyMedium">Burned {asked.hpBurned} HP for the sacrifice, whatever the reading.</p>}
              {asked.backlash !== null && <p className="bodyMedium err" style={{ paddingTop: 8 }}>Critical Failure Table: {asked.backlash}</p>}
            </VerdictCard>
          </div>
        ) : (
          <>
            <div className="scroll">
              {sourceLine !== null && <p className="bodySmall">{sourceLine}</p>}
              <SectionLabel text="Question" />
              <Chips>
                {difficulties.map((option) => (
                  <CaseChip key={option.band} selected={option === difficulty} onClick={() => setDifficulty(option)}>{substringBefore(option.band, ' ')} {option.printedModifier}</CaseChip>
                ))}
              </Chips>
              <SectionLabel text="Method" />
              <Chips>
                {methods.map((option) => <CaseChip key={option.method} selected={option === method} onClick={() => setMethod(option)}>{option.method} {spiLabel(option)}</CaseChip>)}
              </Chips>
              {method.spiMax !== null && (
                <Chips>
                  {Array.from({ length: method.spiMax - method.spi + 1 }, (_, i) => method.spi + i).map((option) => (
                    <CaseChip key={option} selected={option === spiChoice} onClick={() => setSpiChoice(option)}>{option} SPI</CaseChip>
                  ))}
                </Chips>
              )}
              <SectionLabel text="Circumstances" />
              <Chips>
                {rows.map((option) => (
                  <CaseChip key={option.source} selected={chosen.has(option.source)} onClick={() => setChosen(toggled(chosen, option.source))}>
                    {substringBefore(option.source, ' (')} {option.printed}
                  </CaseChip>
                ))}
              </Chips>
              {picks.filter((it) => it.best !== null).map((row) => (
                <div key={row.source}>
                  <p className="bodySmall" style={{ paddingTop: 8 }}>{substringBefore(row.source, ' (')} — pick a value</p>
                  <Chips>
                    {endsBetween(row.modifier!, row.best!).map((value) => (
                      <CaseChip key={value} selected={(picked.get(row.source) ?? row.modifier) === value} onClick={() => setPicked(new Map(picked).set(row.source, value))}>{signed(value)}</CaseChip>
                    ))}
                  </Chips>
                </div>
              ))}
              {picks.some((it) => it.perRepeat) && (
                <>
                  <p className="bodySmall" style={{ paddingTop: 8 }}>How many times this subject has been divined before</p>
                  <Chips>
                    {repeatChoices().map((count) => <CaseChip key={count} selected={repeats === count} onClick={() => setRepeats(count)}>x{count}</CaseChip>)}
                  </Chips>
                </>
              )}
              {[...Divination.UNAPPLIED].map(([rule, reason]) => (
                <p key={rule} className="bodySmall muted">{rule} {signed(Divination.SPECIALISATION_BONUS)} is not applied here — {reason}.</p>
              ))}
              <hr className="rule" style={{ margin: '8px 0' }} />
              <SectionLabel text="Target's protection" />
              <p className="bodySmall">A Beyonder resists being divined. Leave this off for an ordinary target.</p>
              <Chips>
                <CaseChip selected={targetSequence === null} onClick={() => setTargetSequence(null)}>Not a Beyonder</CaseChip>
                {AntiDivination.rows().map((row) => (
                  <CaseChip key={row.label} selected={targetSequence !== null && targetSequence >= row.sequenceLow && targetSequence <= row.sequenceHigh} onClick={() => setTargetSequence(row.sequenceHigh)}>
                    {substringBefore(row.label, ' (')}
                  </CaseChip>
                ))}
              </Chips>
              <SectionLabel text="Active techniques (Sec. 3)" />
              <p className="bodySmall">What the target has raised, and paid SPI for. All active techniques stack.</p>
              <Chips>
                {allTechniques.map((option) => (
                  <CaseChip key={option.id} selected={techniqueIds.has(option.id)} onClick={() => setTechniqueIds(toggled(techniqueIds, option.id))}>
                    {option.name}{option.divinationPenalty !== null ? ` ${signed(option.divinationPenalty)}` : ''}
                  </CaseChip>
                ))}
              </Chips>
              {techniques.map((it) => (
                <div key={it.id}>
                  <p className="bodyMedium">{it.name} — {it.availability}</p>
                  {it.bullets.map((line) => <p key={line.label} className="bodySmall">{line.label}: {line.text}</p>)}
                </div>
              ))}
              {targetSequence !== null && (
                <>
                  <p className="bodySmall" style={{ paddingTop: 8 }}>{AntiDivination.rowFor(targetSequence).text}</p>
                  <Chips>
                    <CaseChip selected={directLink} onClick={() => setDirectLink(!directLink)}>True name + personal item</CaseChip>
                    <CaseChip selected={sacrificeHp} onClick={() => setSacrificeHp(!sacrificeHp)}>Burn {hpForSacrifice} HP</CaseChip>
                  </Chips>
                </>
              )}
              <OptionStepper label="Burn SPI" value={burned} shown={`+${burned}`} note="before rolling: 1 SPI buys +1, no cap, beside the method's cost" onStep={(d) => setBurned(Math.max(burned + d, 0))} />
              {protection.requiresCritical && (
                <p className="bodySmall err" style={{ paddingTop: 8 }}>Only a critical success (3–4) can succeed — the book says this penalty cannot be offset by skill alone.</p>
              )}
              <RuleNote title="What these picks mean">
                <p className="bodySmall" style={{ paddingTop: 4 }}>{difficulty.band}: {difficulty.example}</p>
                <p className="bodySmall" style={{ paddingTop: 4 }}>{method.method}: {method.time}, {spiLabel(method)} SPI — {method.bestFor}</p>
              </RuleNote>
            </div>
            <RitualFooter
              terms={[
                `Skill ${skillLevel}`,
                `question ${signed(difficulty.modifier)}`,
                shock !== 0 ? `shock ${signed(shock)}` : null,
                states.line,
                spiTrait !== 0 ? `${signed(spiTrait)} ${TraitEffects.ETHER_BODY_DAMAGE}` : null,
                circumstances !== 0 ? `circumstances ${signed(circumstances)}` : null,
                burned > 0 ? `+${burned} SPI burned` : null,
                ...protection.parts,
              ].filter((it): it is string => it !== null)}
              target={target}
              cost={`costs ${spent} SPI, takes ${method.time.toLowerCase()}`}
              refusal={null}
              targetText={protection.requiresCritical ? '3d6: a 3 or 4 only' : SuccessRoll.printed(target)}
              label="Ask"
              icon="visibility"
              onCast={ask}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}
