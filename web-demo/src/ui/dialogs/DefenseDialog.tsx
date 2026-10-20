// DefenseDialog.kt (FORM 5-D): every active defence this character has,
// against the attack described. A row rolls once per tap drawn; the burn is
// charged on each roll, the boost's FP once, when nothing is bought yet.
import { useEffect, useState } from 'react'
import {
  acrobaticsFor, acrobaticTarget, armedParries, BARE_HANDS, bareBlock, blockTargets, boostOn, defenceLabels, parryScore, SHIELD_SKILL, shieldForDb, shieldedTargets,
  shotBlockNote, unarmedParries, unskilledParriers, unskilledParryNote,
} from '../../app/DefenseModel'
import { keptFor, parried, RoundState, type Cell, type CharacterDice } from '../../app/RollLog'
import * as ActiveDefenses from '../../rules/ActiveDefenses'
import type { CataloguedWeapon, Shield } from '../../rules/content/schema.gen'
import * as ContentLoader from '../../rules/content/ContentLoader'
import * as EffortBurn from '../../rules/EffortBurn'
import type { ValueMap } from '../../rules/kt'
import type { Modifier, RollResult, SkillKey, Trait } from '../../rules/model'
import * as ParryBlock from '../../rules/ParryBlock'
import * as Shields from '../../rules/Shields'
import type { Facing } from '../../rules/Shields'
import { masterySourceFor, type Untrained, type WeaponMastery } from '../../rules/SkillDefaults'
import * as SkillRoll from '../../rules/SkillRoll'
import * as SuccessRoll from '../../rules/SuccessRoll'
import type { ClauseBonus } from '../../rules/TraitEffects'
import { useCell } from '../hooks'
import { CaseChip, Chips, CloseButton, Dialog, FormTitle, RuleNote, SectionLabel, SteadyBody, useLatch } from '../kit'
import {
  AcrobaticDodge, BoostPicker, DefenseNote, DefenseRow, DiveControl, FacingPicker, IncomingPicker, NewRoundControl, ParriesThisRound, RefusalPicker, RetreatControl, ShieldPicker,
  StunPicker, type Incoming,
} from './DefensePickers'
import { burnOffer, BurnStepper } from './RollDialog'
import './defense.css'

export const FORM_DEFEND = 'FORM 5-D · DEFENCE'

export function DefenseDialog({ dodge, skillLevels, dx, untrained, weapons = [], mastery, dice, conditions, traits = [], onSpendFp, fatigued = false, shocked = false, poolPenalty, states = { total: 0, line: null }, round: roundCell, stunned: stunnedCell, warmed, onDismiss }: {
  dodge: number
  skillLevels: ValueMap<SkillKey, number>
  dx: number
  untrained: Untrained[]
  weapons?: CataloguedWeapon[]
  mastery: WeaponMastery | null
  dice: CharacterDice
  conditions: (k: SkillKey) => Modifier[]
  traits?: Trait[]
  onSpendFp: (n: number) => void
  fatigued?: boolean
  shocked?: boolean
  poolPenalty: number
  states?: ClauseBonus
  round: Cell<RoundState>
  stunned: Cell<boolean>
  warmed: Set<string>
  onDismiss: () => void
}) {
  const [stored] = useCell(roundCell)
  const [stunned, setStunned] = useCell(stunnedCell)
  // Handlers read the live round, as Kotlin's `var round by roundState` does.
  const update = (f: (r: RoundState) => RoundState) => roundCell.set(f(roundCell.get()))
  const burn = burnOffer('PHYSICAL', traits)
  const [burnPoints, setBurnPoints] = useState(0)
  const [acrobaticRolls, setAcrobaticRolls, liveAcrobaticRolls] = useLatch(0)
  const drawnBurn = burn === null ? 0 : burnPoints
  const everyDefence = ActiveDefenses.everyDefence(traits, stunned, shocked, poolPenalty, states.total) + EffortBurn.bonusFor('PHYSICAL', drawnBurn)
  const chargeBurn = () => { if (drawnBurn > 0) onSpendFp(drawnBurn) }
  const acrobatics = acrobaticsFor(skillLevels, untrained)
  const round = keptFor(stored, acrobatics)
  useEffect(() => {
    if (round !== stored) roundCell.set(round)
  }, [round, stored, roundCell])
  const refusal = ActiveDefenses.state(round.declared, traits)
  const rollDefence = (label: string) => (target: number): RollResult => {
    const result = SuccessRoll.against(target, dice(label))
    chargeBurn()
    return result
  }
  const rollParry = (label: string) => (target: number) => {
    const result = rollDefence(label)(target)
    update(parried)
    return result
  }
  const forfeiting = ActiveDefenses.forfeitingActions()
  const printed = ContentLoader.defenses()
  const modifiers = ActiveDefenses.unappliedModifiers()
  const parries = unarmedParries(skillLevels, dx)
  const armed = armedParries(weapons, skillLevels, mastery, dx, warmed)
  const unskilled = unskilledParriers(weapons, skillLevels, mastery, dx)
  const unmastered = new Set(armedParries(weapons, skillLevels, null, dx).map(([n, v]) => `${n}:${v}`))
  const lifted = armed.filter(([n, v]) => !unmastered.has(`${n}:${v}`)).map(([n]) => n)
  const shieldSkill = [...skillLevels.entries()].find(([key]) => key.name === SHIELD_SKILL)?.[1] ?? null
  const acrobaticSwing = ActiveDefenses.acrobaticDodge(round.acrobatic !== null ? SkillRoll.effectiveOutcome(round.acrobatic) : null)
  const shields = Shields.all()
  const [carried, setCarried] = useState<Shield | null>(null)
  const [facing, setFacing] = useState<Facing>('FRONT')
  const coverage = Shields.coverageAt(facing)
  const held = shieldForDb(carried, facing)
  const [incoming, setIncoming] = useState<Incoming>('BARE_HAND')
  const isRanged = incoming === 'SHOT'
  const diveBonus = ActiveDefenses.dive(round.dived, isRanged)
  const stepped = round.retreated && !round.holdingPin
  const retreatDodge = ActiveDefenses.retreat(stepped, !isRanged, 'DODGE')
  const retreatParry = ActiveDefenses.retreat(stepped, !isRanged, 'PARRY')
  const retreatBlock = ActiveDefenses.retreat(stepped, !isRanged, 'BLOCK')
  const repeatPenalty = ParryBlock.parryPenalty(round.priorParries)
  const parryPenalty = repeatPenalty + (round.holdingPin ? ActiveDefenses.PIN_HOLD_PARRY : 0)
  const standing = parries.filter((it) => incoming !== 'SHOT' || ParryBlock.canParryRanged(it[1]))
  const unarmedCell = printed.find((it) => it.defense.startsWith('Parry (Unarmed)'))
  const note = (defense: string) => printed.find((it) => it.defense === defense)?.notes
  const acrobaticStanding = acrobatics !== null
    ? acrobaticTarget(acrobatics, (k) => [...conditions(k), ...[EffortBurn.modifier('PHYSICAL', drawnBurn)].filter((m) => m !== null)], traits)
    : null
  const drawnAcrobaticRolls = acrobaticRolls
  const payable = defenceLabels(!round.holdingPin, armed, standing, shieldSkill !== null && carried !== null)
  const unskilledNote = unskilledParryNote(unskilled, printed)
  const boosted = boostOn('Block', round.boost)

  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={FORM_DEFEND}>Active defenses</FormTitle>}>
      <SteadyBody>
        <div className="scroll">
          <NewRoundControl round={round} onNewRound={() => roundCell.set(RoundState())} />
          <RefusalPicker
            declared={round.declared}
            forfeiting={forfeiting}
            onToggle={(cause) => update((r) => {
              const declared = new Set(r.declared)
              if (declared.has(cause)) declared.delete(cause)
              else declared.add(cause)
              return { ...r, declared }
            })}
          />
          {refusal.printed !== null && <p className="bodySmall err" style={{ padding: '4px 0' }}>{refusal.printed}</p>}
          {refusal.narrower !== null && <DefenseNote text={`Page 37 prints it narrowly — "${refusal.narrower}" — and names only the All-Out Attacks.`} />}
          {refusal.negated !== null && <DefenseNote text={`${refusal.negatedBy} — "never freeze in surprise", so the defences stand.`} />}
          {ActiveDefenses.defends(refusal) && (
            <>
              <StunPicker stunned={stunned} onToggle={() => setStunned(!stunnedCell.get())} />
              {burn !== null && <BurnStepper offer={burn} points={burnPoints} onPoints={setBurnPoints} />}
              {poolPenalty !== 0 && <DefenseNote text={`Hurt or tired: ${poolPenalty} to every defence below, from the HP and FP thresholds on the sheet (pp. 40 and 42).`} />}
              {states.line !== null && <DefenseNote text={`Declared on the sheet: ${states.line} to every defence below.`} />}
              <IncomingPicker chosen={incoming} onPick={setIncoming} />
              <FacingPicker chosen={facing} onPick={setFacing} />
              <Chips>
                <CaseChip selected={round.holdingPin} onClick={() => update((r) => ({ ...r, holdingPin: !r.holdingPin }))}>
                  Holding a pin: no Dodge or Retreat, Parry {ActiveDefenses.PIN_HOLD_PARRY}
                </CaseChip>
              </Chips>
              <RetreatControl taken={stepped} bonus={retreatDodge} holdingPin={round.holdingPin} onToggle={() => update((r) => ({ ...r, retreated: !r.retreated }))} />
              <ShieldPicker shields={shields} carried={carried} coverage={coverage} onPick={(s) => setCarried(s === carried ? null : s)} />
              {round.holdingPin ? (
                <DefenseNote text={'Holding a pin: "the pinner cannot Dodge" (v6.9i 2684).'} />
              ) : (
                <DefenseRow
                  key="Dodge"
                  name="Dodge"
                  targets={shieldedTargets(dodge + boostOn('Dodge', round.boost) + acrobaticSwing + diveBonus + retreatDodge, held, coverage, everyDefence)}
                  roll={rollDefence('Dodge')}
                />
              )}
              {note('Dodge') !== undefined && <DefenseNote text={note('Dodge')!} />}
              {fatigued && <DefenseNote text={'At 1/3 FP or below: "Move and Dodge halved" (p. 42). Halved above, rounded up — the book prints no direction.'} />}
              <AcrobaticDodge
                skill={acrobatics}
                standing={acrobaticStanding}
                rolled={round.acrobatic}
                onRoll={() => {
                  if (acrobaticStanding === null || liveAcrobaticRolls() !== drawnAcrobaticRolls) return
                  setAcrobaticRolls(drawnAcrobaticRolls + 1)
                  const acrobatic = SkillRoll.rollAgainst(acrobaticStanding, dice('Acrobatic Dodge'))
                  update((r) => ({ ...r, acrobatic }))
                  chargeBurn()
                }}
                onClear={() => update((r) => ({ ...r, acrobatic: null }))}
              />
              <DiveControl taken={round.dived} bonus={diveBonus} onToggle={() => update((r) => ({ ...r, dived: !r.dived }))} />
              {armed.map(([name, value]) => {
                const label = `Parry (${name})`
                return (
                  <DefenseRow
                    key={label}
                    name={label}
                    targets={shieldedTargets(parryScore(value, parryPenalty, boostOn(label, round.boost), retreatParry), held, coverage, everyDefence)}
                    roll={rollParry(label)}
                  />
                )
              })}
              {armed.length > 0 && note('Parry (Armed)') !== undefined && <DefenseNote text={note('Parry (Armed)')!} />}
              {standing.map(([name, style, value]) => {
                const vsWeapon = incoming === 'WEAPON' ? ParryBlock.versusWeaponPenalty(style) : 0
                const label = `Parry (${name})`
                return (
                  <DefenseRow
                    key={label}
                    name={label}
                    targets={shieldedTargets(parryScore(value, parryPenalty, boostOn(label, round.boost), retreatParry, vsWeapon), held, coverage, everyDefence)}
                    roll={rollParry(label)}
                  />
                )
              })}
              {standing.length > 0 && unarmedCell !== undefined && <DefenseNote text={unarmedCell.notes} />}
              {standing.some((it) => it[1] === 'BARE_HANDS') && (
                <RuleNote title="Why the bare-hand parry is DX itself">
                  <DefenseNote
                    text={`Parry (${BARE_HANDS}) is the formula cell's other operand${unarmedCell !== undefined ? `, "${unarmedCell.formula}"` : ''}: DX itself, not a default, so nothing is taken off and Versatile adds nothing. Against a weapon it takes Brawling's −3. That is a reading: the cell names Brawling and exempts Boxing alone.`}
                  />
                </RuleNote>
              )}
              {unskilledNote !== null && (
                <>
                  <DefenseNote text={unskilledNote[0]} />
                  <RuleNote title="Why no untrained armed parry"><DefenseNote text={unskilledNote[1]} /></RuleNote>
                </>
              )}
              {mastery !== null && lifted.length > 0 && (
                <RuleNote title="Why a master's weapon parries higher">
                  <DefenseNote
                    text={`${lifted.join(', ')}: Weapon Skill no less than ${masterySourceFor(mastery, false, false)} before the halving, or DX+2 for a weapon declared used repeatedly in Attack, read as the Parry (Armed) row's operand. A reading: the book's examples are attacks.`}
                  />
                </RuleNote>
              )}
              {standing.length === 0 && parries.length > 0 && <DefenseNote text={'No unarmed parry against a shot: "Cannot parry ranged attacks."'} />}
              {(armed.length > 0 || standing.length > 0) && (
                <ParriesThisRound prior={round.priorParries} penalty={repeatPenalty} onChange={(n) => update((r) => ({ ...r, priorParries: n }))} />
              )}
              {shieldSkill !== null && (
                <>
                  {held === null ? (
                    <>
                      <DefenseRow key="Block" name="Block" targets={shieldedTargets(bareBlock(shieldSkill, carried, isRanged) + boosted + retreatBlock, null, coverage, everyDefence)} roll={rollDefence('Block')} />
                      <DefenseNote text={carried === null ? 'Pick a shield above; Block needs one Readied.' : `No DB from behind: "${Shields.PRINTED_ARC}".`} />
                    </>
                  ) : (
                    <>
                      <DefenseRow key="Block" name="Block" targets={blockTargets(held, shieldSkill, coverage, isRanged, everyDefence + boosted + retreatBlock)} roll={rollDefence('Block')} />
                      {!isRanged && (() => {
                        const shot = shotBlockNote(held, shieldSkill, coverage, everyDefence + boosted)
                        return shot !== null ? <DefenseNote text={shot} /> : null
                      })()}
                    </>
                  )}
                  {note('Block') !== undefined && <DefenseNote text={note('Block')!} />}
                </>
              )}
              {carried !== null && <DefenseNote text={`${carried.type}: DB +${carried.db}, Cover DR ${carried.coverDr}, HP ${carried.hp} — ${carried.notes}`} />}
              <RuleNote title="Shield rules">{Shields.rules().map((it) => <DefenseNote key={it.rule} text={it.rule} />)}</RuleNote>
              {round.boost !== null && !payable.includes(round.boost[1]) && (
                <DefenseNote text={`The defence you paid for is not available against this attack, so the +${ActiveDefenses.BOOST_BONUS} lands on nothing. The FP stays spent.`} />
              )}
              <BoostPicker
                rows={payable}
                chosen={round.boost}
                onPick={(next) => {
                  if (roundCell.get().boost === null && next !== null) onSpendFp(ActiveDefenses.fpCostOf(next[0]))
                  update((r) => ({ ...r, boost: next }))
                }}
              />
              <SectionLabel text="Modifiers" />
              {modifiers.map((it) => <DefenseNote key={it} text={it} />)}
            </>
          )}
        </div>
      </SteadyBody>
    </Dialog>
  )
}
