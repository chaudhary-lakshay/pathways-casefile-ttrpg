// DefensePickers.kt and AcrobaticDodge.kt's composables: the Defend dialog's
// controls. Each renders; the numbers come from DefenseModel.ts and the rules.
import { acrobaticLabel, isSpent, type AcrobaticSkill, type RoundState } from '../../app/RollLog'
import * as ActiveDefenses from '../../rules/ActiveDefenses'
import type { DefenseBoost, DefenseRefusal } from '../../rules/ActiveDefenses'
import type { Shield } from '../../rules/content/schema.gen'
import { substringBefore } from '../../rules/kt'
import { isSuccess, type RollResult } from '../../rules/model'
import * as Shields from '../../rules/Shields'
import type { Facing, ShieldCoverage } from '../../rules/Shields'
import * as SkillRoll from '../../rules/SkillRoll'
import type { SkillRollOutcome, SkillTarget } from '../../rules/SkillRoll'
import * as SuccessRoll from '../../rules/SuccessRoll'
import { BracketAction, CaseChip, Chips, InkAction, OptionStepper, outcomeLabel, SectionLabel, useLatch } from '../kit'
import { Icon } from '../icons'

export function DefenseNote({ text }: { text: string }) {
  return <p className="bodySmall muted" style={{ paddingBottom: 4 }}>{text}</p>
}

export function ShieldPicker({ shields, carried, coverage, onPick }: { shields: Shield[]; carried: Shield | null; coverage: ShieldCoverage; onPick: (s: Shield) => void }) {
  return (
    <>
      <SectionLabel text="Shield Readied" />
      <Chips>
        {shields.map((option) => (
          <CaseChip key={option.type} selected={option === carried} onClick={() => onPick(option)}>
            {substringBefore(option.type, ' ')} +{option.db}
          </CaseChip>
        ))}
      </Chips>
      {carried !== null && (
        <DefenseNote
          text={
            coverage === 'COUNTS' ? 'Arrow shows the DB applied: the book grants it from the front.'
              : coverage === 'GM_DECIDES' ? 'Arrow OFFERS the DB — it is not applied. From the side it counts only if that is your shield side, and the book names no hand, so the call is yours.'
                : `No DB from behind: "${Shields.PRINTED_ARC}". The rows below are the unshielded numbers.`
          }
        />
      )}
    </>
  )
}

export function NewRoundControl({ round, onNewRound }: { round: RoundState; onNewRound: () => void }) {
  return (
    <div className="row">
      <span className="bodySmall grow" style={{ paddingRight: 8 }}>What is marked here holds until the next round.</span>
      <BracketAction label="New round" onClick={onNewRound} disabled={!isSpent(round)} />
    </div>
  )
}

export function DiveControl({ taken, bonus, onToggle }: { taken: boolean; bonus: number; onToggle: () => void }) {
  return (
    <>
      <DefenseNote text={ActiveDefenses.printedDive()} />
      <Chips><CaseChip selected={taken} onClick={onToggle}>Dived</CaseChip></Chips>
      {taken && bonus === 0 && (
        <DefenseNote text={`Not against this attack: the bullet is "vs. ranged only", so the +${ActiveDefenses.DIVE_BONUS} lands on nothing. Pick "A shot" above.`} />
      )}
      {taken && <DefenseNote text="Getting back up costs a turn (Change Posture) — the GM's to spend; this app holds no clock to run it against." />}
    </>
  )
}

const RETREAT_ONCE = 'One melee attack this round — which one is the GM\'s to count. New round clears this.'

export function RetreatControl({ taken, bonus, holdingPin, onToggle }: { taken: boolean; bonus: number; holdingPin: boolean; onToggle: () => void }) {
  return (
    <>
      <DefenseNote text={ActiveDefenses.printedRetreat()} />
      <Chips><CaseChip selected={taken} disabled={holdingPin} onClick={() => { if (!holdingPin) onToggle() }}>Stepped back</CaseChip></Chips>
      <DefenseNote
        text={taken && bonus === 0 ? 'Not against this attack: the bullet is "vs. 1 melee attack", so nothing lands. Pick a bare hand or a weapon above.' : RETREAT_ONCE}
      />
    </>
  )
}

export function BoostPicker({ rows, chosen, onPick }: { rows: string[]; chosen: [DefenseBoost, string] | null; onPick: (b: [DefenseBoost, string] | null) => void }) {
  if (rows.length === 0 && chosen === null) return null
  return (
    <>
      <SectionLabel text={`Buy +${ActiveDefenses.BOOST_BONUS} on one defence`} />
      {ActiveDefenses.DEFENSE_BOOSTS.map((boost) => (
        <div key={boost}>
          <p className="bodySmall" style={{ paddingTop: 4 }}>
            {ActiveDefenses.BOOSTS[boost].printed} — {ActiveDefenses.fpCostOf(boost)} FP: {ActiveDefenses.printedBoost(boost)}
          </p>
          <Chips>
            {rows.map((row) => {
              const isChosen = chosen !== null && chosen[0] === boost && chosen[1] === row
              return (
                <CaseChip key={row} selected={isChosen} disabled={chosen !== null && !isChosen} onClick={() => onPick(isChosen ? null : [boost, row])}>
                  {row}
                </CaseChip>
              )
            })}
          </Chips>
        </div>
      ))}
      {chosen !== null && (
        <>
          <DefenseNote text={`Paid ${ActiveDefenses.fpCostOf(chosen[0])} FP: +${ActiveDefenses.BOOST_BONUS} on ${chosen[1]} this turn.`} />
          <InkAction label={`Clear the +${ActiveDefenses.BOOST_BONUS} (the FP stays spent)`} onClick={() => onPick(null)} />
        </>
      )}
    </>
  )
}

export type Incoming = 'BARE_HAND' | 'WEAPON' | 'SHOT'
const INCOMING: [Incoming, string][] = [['BARE_HAND', 'A bare hand'], ['WEAPON', 'A weapon'], ['SHOT', 'A shot']]
const FACINGS: [Facing, string][] = [['FRONT', 'Front'], ['SIDE', 'Side'], ['REAR', 'Rear']]

export function FacingPicker({ chosen, onPick }: { chosen: Facing; onPick: (f: Facing) => void }) {
  return (
    <>
      <SectionLabel text="Where the attack comes from" />
      <Chips>
        {FACINGS.map(([option, label]) => <CaseChip key={option} selected={option === chosen} onClick={() => onPick(option)}>{label}</CaseChip>)}
      </Chips>
      {chosen !== 'FRONT' && (
        <DefenseNote text={'The book also prints a defence penalty for this position. It is listed under Modifiers below and is NOT in the numbers above: it prints "to defense" in the singular, and this app only takes a penalty to every row when a second printing says so.'} />
      )}
    </>
  )
}

export function IncomingPicker({ chosen, onPick }: { chosen: Incoming; onPick: (i: Incoming) => void }) {
  return (
    <>
      <SectionLabel text="The attack coming in" />
      <Chips>
        {INCOMING.map(([option, label]) => <CaseChip key={option} selected={option === chosen} onClick={() => onPick(option)}>{label}</CaseChip>)}
      </Chips>
    </>
  )
}

export function StunPicker({ stunned, onToggle }: { stunned: boolean; onToggle: () => void }) {
  return (
    <>
      <SectionLabel text="Stunned" />
      <Chips><CaseChip selected={stunned} onClick={onToggle}>Stunned</CaseChip></Chips>
      {stunned && (
        <>
          <DefenseNote text={`Do Nothing (p. 37): "${ActiveDefenses.printedStun()}"`} />
          <DefenseNote text="Every defence below already includes it. The recovery roll is on the Vitals tab; the duration is the GM's, as this app holds no clock." />
        </>
      )}
    </>
  )
}

const REFUSAL_LABELS: [DefenseRefusal, string][] = [
  ['SURPRISE', 'Surprised'], ['ALL_OUT_ATTACK', 'Forfeited last turn'], ['UNAWARE_RANGED', 'Never saw the shot'], ['PINNED', 'Pinned'],
]

export function RefusalPicker({ declared, forfeiting, onToggle }: { declared: Set<DefenseRefusal>; forfeiting: string[]; onToggle: (c: DefenseRefusal) => void }) {
  return (
    <>
      <SectionLabel text="Can this character defend?" />
      <Chips>
        {REFUSAL_LABELS.map(([cause, label]) => <CaseChip key={cause} selected={declared.has(cause)} onClick={() => onToggle(cause)}>{label}</CaseChip>)}
      </Chips>
      {declared.has('ALL_OUT_ATTACK') && <DefenseNote text={`Forfeits the next turn's defence: ${forfeiting.join(', ')}.`} />}
    </>
  )
}

export function ParriesThisRound({ prior, penalty, onChange }: { prior: number; penalty: number; onChange: (n: number) => void }) {
  return (
    <OptionStepper
      label="Parries already this round"
      value={prior}
      note={penalty === 0 ? 'The first parry of a round is free.' : `Parry rows above include ${`${penalty}`.replace('-', '−')}. Chapter 8 prints no fencing weapon or rapier, whose tiers are −2 and −1.`}
      onStep={(d) => onChange(Math.max(prior + d, 0))}
    />
  )
}

export function BoxedRoll({ label, onClick, disabled }: { label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button className="boxed-roll labelLarge tabular semibold" onClick={onClick} disabled={disabled}>
      <Icon name="casino" size={18} />
      <span>{label}</span>
    </button>
  )
}

/** One defence: its targets, a roll per target, the last throw. Keyed by name, as `remember(name)`. */
export function DefenseRow({ name, targets, roll }: { name: string; targets: number[]; roll: (target: number) => RollResult }) {
  const [rolled, setRolled] = useLatch<RollResult | null>(null)
  const [rolls, setRolls, liveRolls] = useLatch(0)
  const drawn = rolls
  return (
    <div className="defense-row">
      <div className="row">
        <span className="titleMedium grow">{name}</span>
        <span className="labelLarge tabular muted">{targets.join(' → ')}</span>
      </div>
      <div className="row" style={{ paddingTop: 6, gap: 6, flexWrap: 'wrap' }}>
        {targets.map((target) => (
          <BoxedRoll
            key={target}
            label={`Roll ≤ ${target}`}
            onClick={() => {
              if (liveRolls() !== drawn) return
              setRolls(drawn + 1)
              setRolled(roll(target))
            }}
          />
        ))}
      </div>
      <p className="defense-result labelLarge tabular" style={{ paddingTop: 4, minHeight: '1.5em', color: rolled !== null && !isSuccess(rolled.outcome) ? 'var(--error)' : 'var(--on-surface)' }}>
        {rolled !== null ? `3d6: ${rolled.total} vs ${rolled.target} — ${outcomeLabel(rolled.outcome)}` : ''}
      </p>
    </div>
  )
}

const FORBIDS = 'Something forbids this roll, so the Dodge row above is unchanged.'

export function AcrobaticDodge({ skill, standing, rolled, onRoll, onClear }: {
  skill: AcrobaticSkill | null
  standing: SkillTarget | null
  rolled: SkillRollOutcome | null
  onRoll: () => void
  onClear: () => void
}) {
  const printed = <DefenseNote text={ActiveDefenses.printedAcrobaticDodge()} />
  if (skill === null || standing === null) return <>{printed}<DefenseNote text={`No ${ActiveDefenses.ACROBATIC_SKILL} on this sheet, and no default for it.`} /></>
  if (standing.forbidden) return <>{printed}<DefenseNote text={FORBIDS} /></>
  const against = standing.target === standing.baseTarget
    ? SuccessRoll.printed(standing.target)
    : `${SuccessRoll.printed(standing.target)} (${standing.baseTarget} → ${standing.target})`
  return (
    <>
      {printed}
      {skill.untrainedSource !== null && (
        <DefenseNote text={`${acrobaticLabel(skill)} ${skill.level}. The GM may rule that a skill needs specialist knowledge before it can be attempted at all.`} />
      )}
      <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
        <InkAction label={`Roll vs. ${ActiveDefenses.ACROBATIC_SKILL} — ${against}`} onClick={onRoll} icon="casino" />
        <InkAction label="Clear" onClick={onClear} disabled={rolled === null} />
      </div>
      {rolled !== null && <AcrobaticResult skill={skill} outcome={rolled} />}
    </>
  )
}

function AcrobaticResult({ skill, outcome }: { skill: AcrobaticSkill; outcome: SkillRollOutcome }) {
  if (outcome.roll === null) return <DefenseNote text={FORBIDS} />
  const effective = SkillRoll.effectiveOutcome(outcome)
  if (effective === null) return null
  const target = outcome.target === outcome.baseTarget ? `${outcome.baseTarget}` : `${outcome.baseTarget} → ${outcome.target}`
  const swing = ActiveDefenses.acrobaticDodge(effective)
  return (
    <DefenseNote
      text={`${acrobaticLabel(skill)} ${target}: rolled ${outcome.roll.total} — ${outcomeLabel(effective, SkillRoll.degree(outcome))}, ${swing > 0 ? '+' : ''}${swing} on Dodge above.`}
    />
  )
}
