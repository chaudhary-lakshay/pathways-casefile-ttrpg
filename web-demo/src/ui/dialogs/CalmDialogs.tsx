// RestDialog.kt (FORM 7-Z), ConcentrateDialog.kt (FORM 5-C) and
// SpiritVisionDialog.kt (FORM 7-V).
import { useState } from 'react'
import type { CharacterDice } from '../../app/RollLog'
import * as Concentration from '../../rules/Concentration'
import type { ConcentrationOutcome } from '../../rules/Concentration'
import * as ContentLoader from '../../rules/content/ContentLoader'
import * as Rest from '../../rules/Rest'
import type { RestRecovery } from '../../rules/Rest'
import * as SpiritVision from '../../rules/SpiritVision'
import * as SuccessRoll from '../../rules/SuccessRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import type { ClauseBonus, TraitClause } from '../../rules/TraitEffects'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, RollFooter, RuleNote, SteadyBody, TargetCard, useLatch, VerdictCard } from '../kit'
import { ClauseChips } from '../sheet/SheetTabs'

export const FORM_REST = 'FORM 7-Z · REST'
export const FORM_CONCENTRATE = 'FORM 5-C · CONCENTRATE'
export const FORM_SIGHT = 'FORM 7-V · SIGHT'

export function RestDialog({ maxFp, drawn, onRest, onDismiss }: {
  maxFp: number
  drawn: [number, number]
  onRest: (r: RestRecovery, drawn: [number, number]) => boolean
  onDismiss: () => void
}) {
  const rests = Rest.rests()
  const [rest, setRest] = useState(rests[0])
  const [cogitated, setCogitated] = useState(false)
  const [done, setDone, liveDone] = useLatch<RestRecovery | null>(null)
  const recovery = Rest.recovery(rest, cogitated, maxFp)
  const file = ContentLoader.restFile()
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={FORM_REST}>Rest</FormTitle>}>
      <SteadyBody>
        {done !== null ? (
          <p className="titleSmall" style={{ color: 'var(--on-surface)' }}>{rest.name}: +{done.fp} FP and +{done.spi} SPI, up to full.</p>
        ) : (
          <>
            <div className="scroll">
              <Chips>
                {rests.map((option) => <CaseChip key={option.name} selected={option === rest} onClick={() => setRest(option)}>{option.name} · {option.hours} h</CaseChip>)}
              </Chips>
              <div style={{ paddingTop: 6 }}>
                <CaseChip selected={cogitated} onClick={() => setCogitated(!cogitated)}>{file.spi.cogitationSkill} roll succeeded: 2 SPI an hour</CaseChip>
              </div>
              <RuleNote title="What the book says of this rest">
                <p className="bodySmall">{rest.text}</p>
                {rest.bullets.map((it) => <p key={it.label} className="bodySmall">{it.label}: {it.text}</p>)}
                {file.restrictions.map((it) => <p key={it.label} className="bodySmall">{it.label}: {it.text}</p>)}
                <p className="bodySmall">HP heals by the daily roll on Vitals; one rest a day and the focused tasks are the GM's.</p>
              </RuleNote>
            </div>
            <RollFooter
              lines={[`+${recovery.fp} FP`, `+${recovery.spi} SPI`]}
              caption="each up to full"
              target={null}
              label="Rest"
              icon="check"
              onRoll={() => {
                if (liveDone() === null && onRest(recovery, drawn)) setDone(recovery)
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}

export function ConcentrateDialog({ will, clauses = [], shock = 0, hurt = 0, states = { total: 0, line: null }, dice, onDismiss }: {
  will: number
  clauses?: TraitClause[]
  shock?: number
  hurt?: number
  states?: ClauseBonus
  dice: CharacterDice
  onDismiss: () => void
}) {
  const [disturbed, setDisturbed] = useState(false)
  const [declared, setDeclared] = useState<Set<string>>(new Set())
  const [out, setOut, liveOut] = useLatch<ConcentrationOutcome | null>(null)
  const fromTraits = TraitClauses.clauseBonus(clauses, declared)
  const target = Concentration.targetFor(will, shock, hurt, fromTraits.total + states.total)
  const held = out !== null && Concentration.outcomeHeld(out)
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      title={
        <FormTitle form={FORM_CONCENTRATE}>
          <div>Concentrate</div>
          <div className="labelLarge" style={{ color: 'var(--primary)' }}>Will {will}</div>
        </FormTitle>
      }
    >
      <SteadyBody>
        {out !== null ? (
          <VerdictCard verdict={held ? 'Held the focus' : 'Lost the focus'} passed={held} roll={out.roll}>
            {!held && <p className="bodyMedium" style={{ paddingTop: 8 }}>The book prints no consequence for a lost focus — what it costs is the GM's.</p>}
          </VerdictCard>
        ) : (
          <>
            <div className="scroll">
              <TargetCard target={target} baseTarget={Concentration.targetFor(will, 0, 0, 0)} />
              <p className="bodyMedium" style={{ paddingTop: 8 }}>{Concentration.printed()}</p>
              <div style={{ padding: '8px 0' }}>
                <CaseChip selected={disturbed} onClick={() => { setDisturbed(!disturbed); setOut(null) }}>Injured or defending</CaseChip>
              </div>
              <ClauseChips clauses={clauses} declared={declared} onDeclared={setDeclared} />
            </div>
            <RollFooter
              lines={[
                `Will ${will}${Concentration.PENALTY}`,
                shock !== 0 ? `includes ${shock} from shock` : null,
                hurt !== 0 ? `includes ${hurt} from the HP and FP trackers` : null,
                fromTraits.line !== null ? `includes ${fromTraits.line}` : null,
                states.line !== null ? `includes ${states.line}` : null,
              ].filter((it): it is string => it !== null)}
              caption={disturbed ? SuccessRoll.printed(target) : 'No roll unless injured or defending'}
              target={disturbed ? target : null}
              disabled={!disturbed}
              onRoll={() => {
                if (liveOut() !== null || !disturbed) return
                setOut(Concentration.rollAgainst(target, disturbed, dice('Concentrate')))
              }}
            />
          </>
        )}
      </SteadyBody>
    </Dialog>
  )
}

function ColourTable({ heading, rows }: { heading: string; rows: [string, string][] }) {
  return (
    <div className="col" style={{ gap: 2 }}>
      <p className="labelLarge" style={{ color: 'var(--secondary)' }}>{heading}</p>
      {rows.map(([key, value]) => (
        <p key={key} className="bodySmall"><span style={{ fontWeight: 500, paddingRight: 8 }}>{key}</span>{value}</p>
      ))}
    </div>
  )
}

export function SpiritVisionDialog({ spiAvailable, onSpendSpi, onDismiss }: { spiAvailable: number; onSpendSpi: (n: number) => void; onDismiss: () => void }) {
  const activation = SpiritVision.activation()
  const [activations, setActivations, live] = useLatch(0)
  const drawn = activations
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={FORM_SIGHT}>{SpiritVision.TRAIT}</FormTitle>}>
      <SteadyBody>
        <div className="scroll col" style={{ gap: 10 }}>
          {drawn > 0 && <p className="bodyMedium">Active. It lasts as long as you keep it open; looking again later is a new activation.</p>}
          <ColourTable heading="Astral Projection — emotional state" rows={SpiritVision.astralColors().map((it) => [it.color, it.meaning])} />
          <ColourTable heading="Ether Body — physical health" rows={SpiritVision.etherBody().map((it) => [it.region, it.color])} />
          <p className="bodySmall muted">{SpiritVision.etherBodyNote()}</p>
          <RuleNote title="Pathway refinements">
            {SpiritVision.pathwayDifferences().map((it) => <p key={it.name} className="bodySmall">{it.name}: {it.ability}</p>)}
          </RuleNote>
        </div>
        <RollFooter
          lines={[`${activation} SPI per activation, regardless of duration.`, ...(spiAvailable > 0 ? [] : ['Drained: abilities deactivate at 0 SPI, and each use costs 1 CoR.'])]}
          caption="No roll"
          target={null}
          label={drawn === 0 ? `Activate (${activation} SPI)` : `Activate again (${activation} SPI)`}
          icon="visibility"
          onRoll={() => {
            if (live() !== drawn) return
            onSpendSpi(activation)
            setActivations(drawn + 1)
          }}
        />
      </SteadyBody>
    </Dialog>
  )
}
