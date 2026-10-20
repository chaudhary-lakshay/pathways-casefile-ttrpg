// PathwayScreen.kt: choose a pathway, read its potions, drink the next one,
// and track digestion and corruption. The pieces are PathwayParts.tsx.
import { useState } from 'react'
import { actingMethodRoll, drinkCaption, drinkTarget, startingDigestion, swapping } from '../../app/PathwayModel'
import type { Cell } from '../../app/RollLog'
import type { BeyonderSession } from '../../app/session/BeyonderSession'
import { corCeilingOn, digestedWith } from '../../app/session/EditedCharacter'
import * as ContentLoader from '../../rules/content/ContentLoader'
import { canonical } from '../../rules/kt'
import { Beyonder, type BeyonderState } from '../../rules/model'
import * as PotionConsumption from '../../rules/PotionConsumption'
import type { PcrOutcome } from '../../rules/PotionConsumption'
import * as PotionDigestion from '../../rules/PotionDigestion'
import type { PremadeSheet } from '../../rules/Premades'
import type { RandomSource } from '../../rules/RandomSource'
import * as ScoreFloors from '../../rules/ScoreFloors'
import * as TraitEffects from '../../rules/TraitEffects'
import * as TraitGrants from '../../rules/TraitGrants'
import { useCell, useVersion } from '../hooks'
import { BracketAction, Dialog, FormTitle, InkButton, Stamp, useLatch } from '../kit'
import {
  CorruptionTracker, DigestionBar, DigestionTracker, DrawbackPicker, DrinkButton, Note, PathwayBar, PathwayTabs, PathwayTile, PotionContent,
  PotionHeading, SequenceTile,
} from './PathwayParts'
import './pathway.css'

const SEQUENCES = [9, 8, 7, 6]
export const FORM_PCR = 'FORM 6-P · CONSUMPTION'

export function PathwayScreen({ sheet, session, dice, spiritPending, onBack }: {
  sheet: PremadeSheet
  session: BeyonderSession
  dice: RandomSource
  file: number | null
  spiritPending: Cell<number>
  onBack: () => void
}) {
  useVersion(session)
  const [pending] = useCell(spiritPending)
  const pcrModifier = -pending
  const drunk = session.state
  const printedCor = session.cor
  const [pcr, setPcr, livePcr] = useLatch<PcrOutcome | null>(null)
  const [tab, setTab] = useState(0)
  // remember(drunk?.pathway, drunk?.sequence): reset while drawing, never a frame late.
  const drunkKey = `${drunk?.pathway}/${drunk?.sequence}`
  const [chosen, setChosen] = useState({ key: drunkKey, pathway: drunk?.pathway ?? null, sequence: drunk?.sequence ?? PotionDigestion.STARTING_SEQUENCE })
  const current = chosen.key === drunkKey ? chosen : { key: drunkKey, pathway: drunk?.pathway ?? null, sequence: drunk?.sequence ?? PotionDigestion.STARTING_SEQUENCE }
  if (current !== chosen) setChosen(current)
  const choose = (pathway: string | null, sequence = current.sequence) => setChosen({ key: drunkKey, pathway, sequence })

  const blocks = ContentLoader.pathways()
  const pathways = swapping([...new Set(blocks.map((it) => it.pathway))].sort(), 'Wheel of Fortune', 'White Tower')
  const blockOf = (pathway: string, sequence: number) => blocks.find((it) => it.pathway === pathway && it.sequence === sequence)!
  const block = current.pathway !== null ? blockOf(current.pathway, current.sequence) : null
  const traits = session.traitsInPlay(sheet)
  const rerollSource = TraitEffects.rerollSource(traits, drunk)
  const base = session.characterFor(sheet)
  const digested = block !== null ? PotionDigestion.digestThrough(base, block.pathway, block.sequence, blocks) : null
  const maxCorFor = (state: BeyonderState | null) => corCeilingOn(session.edits, sheet, state)
  const maxCor = maxCorFor(drunk)
  const target = block !== null ? drinkTarget(block.pathway, block.sequence, drunk, (s) => blocks.some((it) => it.pathway === s.pathway && it.sequence === s.sequence)) : null
  const maxSpi = digestedWith(base, drunk)?.effectiveSpi ?? 0

  const onDrink = (t: BeyonderState) => {
    if (livePcr() !== null || !session.stillShows(drunk, printedCor)) return
    setPcr(session.drink(t, dice, pcrModifier, maxCorFor))
  }
  const onReroll = (t: BeyonderState) => {
    if (livePcr() !== null) return
    if (session.pendingPcr !== null && canonical(session.pendingPcr) === canonical(t) && ScoreFloors.holdsCeiling(maxCorFor(t))) {
      setPcr(session.rerollPcr(dice, pcrModifier, maxCorFor))
    }
  }

  return (
    <div className="scaffold pathway">
      <PathwayBar name={sheet.name} pathway={current.pathway} onBack={onBack} />
      <div className="scroll pathway-list">
        {block === null || digested === null ? (
          <div className="pathway-grid">
            {pathways.map((name) => <PathwayTile key={name} name={name} onClick={() => { choose(name); setTab(0) }} />)}
          </div>
        ) : (
          <>
            <PotionHeading block={block} />
            <div className="sequence-row" role="tablist">
              {SEQUENCES.map((n) => (
                <SequenceTile
                  key={n}
                  sequence={n}
                  potion={blockOf(block.pathway, n).name}
                  selected={n === current.sequence}
                  drunk={drunk?.pathway === block.pathway && n >= drunk.sequence}
                  onClick={() => { choose(block.pathway, n); setTab(0) }}
                />
              ))}
            </div>
            {drunk !== null && drunk.pathway === block.pathway && <DigestionBar digestion={drunk.digestion} />}
            <DrinkRow
              pcrModifier={pcrModifier}
              target={target}
              drunk={drunk}
              session={session}
              printedCor={printedCor}
              rerollSource={rerollSource}
              maxCorFor={maxCorFor}
              onAnother={() => choose(null)}
              onDrink={onDrink}
              onReroll={onReroll}
            />
            {drunk !== null && <PathwayTabs tab={tab} onTab={setTab} />}
            {drunk !== null && tab === 1 ? (
              <>
                {!session.isLost && (
                  <DigestionTracker
                    state={drunk}
                    cor={session.cor}
                    traits={traits}
                    title={drunk.pathway === block.pathway && drunk.sequence === block.sequence ? 'Digesting this potion' : `Digesting ${blockOf(drunk.pathway, drunk.sequence).name}`}
                    onDigest={(d) => session.digest(d)}
                    onAct={(q, drawn) => session.act(q, drawn)}
                    onActingMethod={(drawn) => actingMethodRoll(session, drawn, dice)}
                    onStartingRoll={(drawn) => startingDigestion(session, drawn, dice)}
                    pcrModifier={pcrModifier}
                  />
                )}
                <CorruptionTracker session={session} drunk={drunk} max={maxCor} traits={traits} />
              </>
            ) : (
              <PotionContent
                block={block}
                digested={digested}
                drawbackPicker={(() => {
                  if (drunk === null || drunk.pathway !== block.pathway || session.isLost) return null
                  const choices = TraitGrants.drawbackChoiceTable().find(([[p, s]]) => p === block.pathway && s === block.sequence)?.[1]
                  if (!choices) return null
                  return (
                    <DrawbackPicker
                      choices={choices}
                      chosen={drunk.drawback}
                      pathway={block.pathway}
                      canChoose={(name) => session.canChooseDrawback(sheet, name)}
                      onChoose={(name) => { if (session.canChooseDrawback(sheet, name)) session.chooseDrawback(sheet, name) }}
                    />
                  )
                })()}
                spend={
                  drunk !== null && drunk.pathway === block.pathway && block.sequence >= drunk.sequence && !session.isLost
                    ? {
                        left: maxSpi - session.spiSpent,
                        onSpend: (price, drawnLeft) => {
                          if (maxSpi - session.spiSpent === drawnLeft) session.usePower(price, maxSpi, maxCor)
                        },
                      }
                    : null
                }
              />
            )}
          </>
        )}
      </div>
      {pcr !== null && (
        <PcrDialog
          outcome={pcr}
          corrupted={session.corrupted}
          rerollSource={session.pendingPcr !== null ? rerollSource : null}
          stamp={drunk !== null && PotionConsumption.integrated(pcr) ? `Seq ${drunk.sequence} · ${blockOf(drunk.pathway, drunk.sequence).name}` : null}
          onDismiss={() => setPcr(null)}
        />
      )}
    </div>
  )
}

function DrinkRow({ pcrModifier, target, drunk, session, printedCor, rerollSource, maxCorFor, onAnother, onDrink, onReroll }: {
  pcrModifier: number
  target: BeyonderState | null
  drunk: BeyonderState | null
  session: BeyonderSession
  printedCor: number
  rerollSource: string | null
  maxCorFor: (s: BeyonderState | null) => number
  onAnother: () => void
  onDrink: (t: BeyonderState) => void
  onReroll: (t: BeyonderState) => void
}) {
  const rerolling = session.lost && target !== null && session.pendingPcr !== null && canonical(session.pendingPcr) === canonical(target) && rerollSource !== null
  const drinking = !session.isLost && target !== null && Beyonder.canDrink(drunk, target)
  const ceilingAfter = (rerolling || drinking) && target !== null ? maxCorFor(target) : null
  const caption = target === null || ceilingAfter === null
    ? null
    : rerolling
      ? [drinkCaption(target, drunk, session.cor, ceilingAfter, pcrModifier), "lost to the Rampager, unless the reroll succeeds; the GM counts the session's rerolls"].filter((it) => it !== null).join('. ')
      : drinking
        ? drinkCaption(target, drunk, printedCor, ceilingAfter, pcrModifier)
        : null
  const next = drunk !== null ? Beyonder.advanced(drunk) : null
  return (
    <div>
      <div className="drink-row">
        <BracketAction label="Another pathway" onClick={onAnother} />
        <div className="drink-slot">
          {rerolling && target !== null && ceilingAfter !== null ? (
            <DrinkButton label={`Reroll the PCR (${rerollSource})`} enabled={ScoreFloors.holdsCeiling(ceilingAfter)} onClick={() => onReroll(target)} />
          ) : session.lost ? (
            <Note text="lost to the Rampager" />
          ) : session.corrupted ? (
            <Note text={`lost to corruption at ${session.cor} CoR`} />
          ) : drinking && target !== null && ceilingAfter !== null ? (
            <DrinkButton label={drunk === null ? 'Drink this potion' : 'Drink and roll'} enabled={ScoreFloors.holdsCeiling(ceilingAfter)} onClick={() => onDrink(target)} />
          ) : drunk === null ? (
            <Note text={`a pathway begins at Sequence ${PotionDigestion.STARTING_SEQUENCE}`} />
          ) : (
            <Note text={next !== null ? `next: Sequence ${next.sequence} of the ${next.pathway} Pathway` : `already at Sequence ${drunk.sequence}`} />
          )}
        </div>
      </div>
      {caption !== null && <p className="bodySmall mono muted" style={{ paddingTop: 8 }}>{caption}</p>}
    </div>
  )
}

function PcrDialog({ outcome, corrupted, rerollSource, stamp, onDismiss }: {
  outcome: PcrOutcome
  corrupted: boolean
  rerollSource: string | null
  stamp: string | null
  onDismiss: () => void
}) {
  const integrated = PotionConsumption.integrated(outcome)
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<InkButton label="OK" onClick={onDismiss} />}
      title={
        <FormTitle form={FORM_PCR}>
          <span className="headlineMedium">{!integrated ? 'Lost to the Rampager' : corrupted ? 'The potion integrates — and takes them' : 'The potion integrates'}</span>
        </FormTitle>
      }
    >
      {stamp !== null && <Stamp text={stamp} color="var(--secondary)" />}
      <p className="titleMedium mono" style={{ paddingTop: 16, color: 'var(--on-surface)' }}>
        3d6: {outcome.roll.total} vs {outcome.target} (digested {outcome.digestion}%)
      </p>
      {integrated && outcome.corGained > 0 && <p style={{ paddingTop: 8 }}>+{outcome.corGained} CoR (1d6) charged for advancing before full digestion</p>}
      {corrupted && <p style={{ paddingTop: 8 }}>That reached their maximum CoR. The character is permanently lost; the GM takes full control.</p>}
      {!integrated && (
        <>
          <p style={{ paddingTop: 8 }}>The character transforms into an NPC Rampager, permanently under the GM's control.</p>
          {rerollSource !== null && (
            <p style={{ paddingTop: 8 }}>
              {rerollSource} may reroll this failed roll: the potion's block offers it. How many rerolls are left this session is the GM's count.
            </p>
          )}
        </>
      )}
    </Dialog>
  )
}
