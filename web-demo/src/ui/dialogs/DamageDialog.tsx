// DamageDialog.kt (FORM 5-W): a hit taken, priced by type and location,
// its Major Wound roll, and the stun recovery that row may ask for.
import { useState } from 'react'
import type { CharacterDice } from '../../app/RollLog'
import type { HitLocation } from '../../rules/content/schema.gen'
import * as ContentLoader from '../../rules/content/ContentLoader'
import type { RollResult } from '../../rules/model'
import type { Pool } from '../../rules/Pools'
import * as Shock from '../../rules/Shock'
import * as StunRecovery from '../../rules/StunRecovery'
import * as SuccessRoll from '../../rules/SuccessRoll'
import type { MajorWoundHt } from '../../rules/TraitAttributeRolls'
import type { ClauseBonus } from '../../rules/TraitEffects'
import * as Wounds from '../../rules/Wounds'
import { Caps, CaseChip, Chips, CloseButton, Dialog, FormTitle, InkAction, InkButton, OptionStepper, RuleNote, SectionLabel, Stamp, useLatch } from '../kit'

export const FORM_DAMAGE = 'FORM 5-W · INJURY'

const isSuccess = (r: RollResult) => r.outcome === 'SUCCESS' || r.outcome === 'CRITICAL_SUCCESS'
const POOLS: Pool[] = ['HP', 'SPI']

export function DamageDialog({ hasSpirituality, maxHp, ht, dice, onApply, onSpiritHit, onShocked, onStunned, majorWoundHt = { bonus: 0, line: null }, stunRecoveryHt = { bonus: 0, line: null }, hurt, recoveryTarget, states = { total: 0, line: null }, onDismiss }: {
  hasSpirituality: boolean
  maxHp: number
  ht: number
  dice: CharacterDice
  onApply: (pool: Pool, n: number) => void
  onSpiritHit: (points: number, cor: number) => void
  onShocked: () => void
  onStunned: () => void
  majorWoundHt?: MajorWoundHt
  stunRecoveryHt?: MajorWoundHt
  hurt: number
  recoveryTarget: number
  states?: ClauseBonus
  onDismiss: () => void
}) {
  const [cor, setCor] = useState(0)
  const [amount, setAmount] = useState(1)
  const [pool, setPool] = useState<Pool>('HP')
  const [shock, setShockRaw, liveShock] = useLatch<RollResult | null>(null)
  const [recovery, setRecovery, liveRecovery] = useLatch<RollResult | null>(null)
  // remember(shock): a new Major Wound roll forgets the recovery.
  const setShock = (r: RollResult | null) => { setShockRaw(r); setRecovery(null) }
  const [landed, setLanded] = useState<HitLocation | null>(null)
  const [type, setType] = useState<string | null>(null)
  const [, setApplied, liveApplied] = useLatch(false)
  const injuryNow = () => Wounds.injuryTo(pool, amount, landed, type)
  const injury = injuryNow()
  const isMajor = pool === 'HP' && Wounds.isMajorWound(injury, maxHp)
  const htTarget = ht + majorWoundHt.bonus + hurt + states.total
  const apply = () => {
    if (liveApplied()) return
    setApplied(true)
    const hit = injuryNow()
    const major = pool === 'HP' && Wounds.isMajorWound(hit, maxHp)
    const rolled = liveShock()
    if (major && rolled !== null && Shock.causedBy(Wounds.majorWoundFor(rolled))) onShocked()
    if (major && rolled !== null && StunRecovery.leavesStunned(Wounds.majorWoundFor(rolled), liveRecovery())) onStunned()
    if (pool === 'SPI') onSpiritHit(hit, cor)
    else onApply(pool, hit)
    onDismiss()
  }
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={
        <button className="danger-button" onClick={apply}>
          <Caps text={pool === 'SPI' ? `Apply ${injury} spirit damage` : `Apply ${injury} to HP`} className="labelLarge mono semibold" color="var(--on-error)" />
        </button>
      }
      dismiss={<CloseButton onClick={onDismiss} label="Cancel" />}
      title={<FormTitle form={FORM_DAMAGE}>Damage</FormTitle>}
    >
      <AmountCard
        amount={amount}
        threshold={pool !== 'HP' ? `No Major wound on ${pool}` : `Major wound at ${Wounds.majorWoundThreshold(maxHp)}+`}
        isMajor={isMajor}
        onStep={(delta) => {
          const next = amount + delta
          if (delta > 0 || next >= 1) { setAmount(next); setShock(null) }
        }}
      />
      <SectionLabel text="Pool" />
      <Chips>
        {POOLS.map((option) => (
          <CaseChip key={option} selected={pool === option} onClick={() => { setPool(option); setShock(null); setLanded(null) }}>
            {option === 'SPI' ? 'Spirit body' : 'HP'}
          </CaseChip>
        ))}
      </Chips>
      <OptionStepper
        label="CoR the ability states"
        value={cor}
        note={hasSpirituality ? 'spirit damage only; most forms add none' : 'a mortal loses no SPI; this and the penalty still apply'}
        onStep={(d) => { if (pool === 'SPI') setCor(Math.max(cor + d, 0)) }}
      />
      <DamageType type={type} amount={amount} landed={landed} pool={pool} onPick={(t) => { setType(t); setShock(null) }} />
      <WhereItLanded
        landed={landed}
        damage={injury}
        maxHp={maxHp}
        isHp={pool === 'HP'}
        onLocation={(it) => {
          if (pool !== 'HP') return
          setLanded(landed === it ? null : it)
          if (type !== null) setShock(null)
        }}
      />
      <MajorWoundPanel
        isMajor={isMajor}
        ht={htTarget}
        traitLine={majorWoundHt.line}
        statesLine={states.line}
        shock={shock}
        onRoll={() => {
          if (liveShock() === null && pool === 'HP' && Wounds.isMajorWound(injuryNow(), maxHp)) setShock(SuccessRoll.against(htTarget, dice('Major wound HT')))
        }}
        recoveryTarget={recoveryTarget}
        recoveryLine={stunRecoveryHt.line}
        recovery={recovery}
        onRecoveryRoll={() => {
          if (liveRecovery() === null) setRecovery(SuccessRoll.against(recoveryTarget, dice('Stun recovery')))
        }}
      />
      <RuleNote title="Spirit body damage rules">
        {ContentLoader.spiritDamage().map((it, i, all) => (
          <div key={i}>
            {(i === 0 || all[i - 1].section !== it.section) && <p className="titleSmall" style={{ paddingTop: 8 }}>{it.section}</p>}
            <p className="bodySmall">{it.rule}</p>
          </div>
        ))}
      </RuleNote>
    </Dialog>
  )
}

function DamageType({ type, amount, landed, pool, onPick }: { type: string | null; amount: number; landed: HitLocation | null; pool: Pool; onPick: (t: string | null) => void }) {
  const isHp = pool === 'HP'
  const line = type === null
    ? 'Injury as entered: the multipliers are already in it.'
    : isHp
      ? `${amount} ${type} past DR ${Wounds.render(Wounds.multiplierFor(landed, type))} = ${Wounds.injuryTo(pool, amount, landed, type)} injury, rounded down. Subtract DR first.`
      : `${amount} spirit damage as rolled: it pierces DR and takes no multiplier.`
  return (
    <>
      <SectionLabel text="The number above is" />
      <Chips>
        <CaseChip selected={type === null} onClick={() => onPick(null)}>Injury</CaseChip>
        {Wounds.knownAbbreviations().map((option) => <CaseChip key={option} selected={type === option} onClick={() => onPick(option)}>{option}</CaseChip>)}
      </Chips>
      <p className="bodySmall" style={{ paddingTop: 4 }}>{line}</p>
    </>
  )
}

function MajorWoundPanel({ isMajor, ht, traitLine, statesLine, shock, onRoll, recoveryTarget, recoveryLine, recovery, onRecoveryRoll }: {
  isMajor: boolean
  ht: number
  traitLine: string | null
  statesLine: string | null
  shock: RollResult | null
  onRoll: () => void
  recoveryTarget: number
  recoveryLine: string | null
  recovery: RollResult | null
  onRecoveryRoll: () => void
}) {
  if (shock === null) {
    return (
      <div style={{ paddingTop: 12 }}>
        <InkButton label={`Roll HT ${ht}`} icon="casino" disabled={!isMajor} onClick={onRoll} />
        {traitLine !== null && <p className="bodySmall">{traitLine}</p>}
        {statesLine !== null && <p className="bodySmall">includes {statesLine}</p>}
      </div>
    )
  }
  const row = Wounds.majorWoundFor(shock)
  return (
    <div style={{ paddingTop: 12 }}>
      <p className="titleMedium" style={{ color: 'var(--on-surface)' }}>3d6: {shock.total} vs {shock.target}</p>
      <p className="bodySmall" style={{ color: 'var(--primary)' }}>{row.result} — {row.effect}</p>
      {StunRecovery.stunsOn(row) && (recovery === null ? (
        <>
          <InkAction label={`Roll HT ${recoveryTarget} to recover now`} icon="casino" onClick={onRecoveryRoll} />
          {recoveryLine !== null && <p className="bodySmall">{recoveryLine}</p>}
          {statesLine !== null && <p className="bodySmall">includes {statesLine}</p>}
        </>
      ) : (
        <>
          <p className="titleMedium" style={{ color: 'var(--on-surface)' }}>3d6: {recovery.total} vs {recovery.target}</p>
          <p className="bodySmall" style={{ color: 'var(--primary)' }}>{isSuccess(recovery) ? 'Recovered early.' : 'Still stunned; the row asks again next turn.'}</p>
        </>
      ))}
    </div>
  )
}

function WhereItLanded({ landed, damage, maxHp, isHp, onLocation }: { landed: HitLocation | null; damage: number; maxHp: number; isHp: boolean; onLocation: (l: HitLocation) => void }) {
  const fired = landed !== null ? Wounds.effectsAt(landed, damage, maxHp) : []
  const line = landed === null
    ? 'Pick where it landed.'
    : fired.length === 0
      ? 'Nothing this hit triggers there.'
      : fired.map((row) => `${row.outcome} (at ${Wounds.thresholdFor(landed, row.outcome, maxHp)}${row.isApproximate ? ', approx.' : ''})`).join('; ')
  return (
    <>
      <SectionLabel text="Where it landed" />
      <Chips>
        {Wounds.locations().map((option) => (
          <CaseChip key={option.target} selected={option === landed} disabled={!isHp} onClick={() => onLocation(option)}>{option.target}</CaseChip>
        ))}
      </Chips>
      <p className="bodyMedium" style={{ paddingTop: 4, color: 'var(--on-surface)' }}>{line}</p>
      <p className="bodySmall muted" style={{ minHeight: 20 }}>{landed?.effect ?? ''}</p>
      {fired.some((it) => it.outcome === 'Crippled') && <p className="bodySmall muted" style={{ paddingTop: 4 }}>{Wounds.cripplingRule()}</p>}
    </>
  )
}

function AmountCard({ amount, threshold, isMajor, onStep }: { amount: number; threshold: string; isMajor: boolean; onStep: (d: number) => void }) {
  const cell = (text: string, spoken: string, d: number) => (
    <button className="titleMedium mono" aria-label={spoken} onClick={() => onStep(d)}>{text}</button>
  )
  return (
    <div className="target-card" style={{ marginTop: 0 }}>
      <Caps text="Damage" />
      <div className="amount-stepper">
        {cell('−5', 'Five less', -5)}
        {cell('−', 'One less', -1)}
        <span className="headlineMedium mono semibold">{amount}</span>
        {cell('+', 'One more', 1)}
        {cell('+5', 'Five more', 5)}
      </div>
      <span aria-label={`${threshold}, ${isMajor ? 'crossed by this hit' : 'not crossed'}`}>
        <Stamp text={threshold} color={isMajor ? 'var(--error)' : 'var(--on-surface-variant)'} caps={false} />
      </span>
    </div>
  )
}
