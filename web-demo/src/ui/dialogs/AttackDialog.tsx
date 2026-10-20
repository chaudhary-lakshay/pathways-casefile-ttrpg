// AttackDialog.kt, AttackOptions.kt, AttackParts.kt and RangedOptions.kt
// (FORM 5-A): pick a skill, then a strike or weapon, a maneuver, the range
// and conditions; the roll charges FP and burn once, and each hit's damage
// is rolled once.
import { useState } from 'react'
import {
  aimAcc, AIM_ROWS, Armed, attackCaption, attackStanding, attackTargets, charmFor, isFired, meleeModifiers, PINNED_REFUSAL, rangedModifiers, termLine,
  toggled, traitTerms, woundingNote,
} from '../../app/AttackModel'
import type { CharacterDice } from '../../app/RollLog'
import { endsBetween } from '../../app/session/SheetLines'
import { disambiguated, skillKeyLabel } from '../../app/SheetText'
import * as Attacks from '../../rules/Attacks'
import type { AttackPlan } from '../../rules/Attacks'
import type { CataloguedWeapon, HitLocation, RangePenalty, UnarmedAttack } from '../../rules/content/schema.gen'
import * as Damage from '../../rules/Damage'
import type { DamageResult, DamageRoll } from '../../rules/Damage'
import * as EffortBurn from '../../rules/EffortBurn'
import type { BurnKind } from '../../rules/EffortBurn'
import { substringBefore, type ValueMap } from '../../rules/kt'
import type { Modifier, SkillKey, Trait } from '../../rules/model'
import * as Ranged from '../../rules/Ranged'
import type { DarknessCause } from '../../rules/Ranged'
import * as Shields from '../../rules/Shields'
import * as SkillDefaults from '../../rules/SkillDefaults'
import type { Untrained, WeaponMastery } from '../../rules/SkillDefaults'
import * as SkillRoll from '../../rules/SkillRoll'
import type { SkillRollOutcome, SkillTarget } from '../../rules/SkillRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import * as TraitEffects from '../../rules/TraitEffects'
import type { ClauseBonus, TraitClause } from '../../rules/TraitEffects'
import * as Weapons from '../../rules/Weapons'
import * as Wounds from '../../rules/Wounds'
import { CaseChip, Chips, CloseButton, Dialog, FooterBand, FooterRow, FORBIDDEN_ROLL, FormTitle, InkAction, OptionStepper, ResultCard, RollButton, SectionLabel, signed, SteadyBody, useLatch } from '../kit'
import { burnOffer, BurnStepper, type BurnOffer } from './RollDialog'

export const FORM_ATTACK = 'FORM 5-A · ATTACK'
const BOXING = 'Boxing'
const CAUSES: [DarknessCause, string][] = [['HEAVY_FOG', 'Heavy fog'], ['DARKNESS', 'Darkness'], ['DIM_LIGHT', 'Dim gaslight or moonlight']]
const flat = (m: Modifier) => (m.effect.type === 'Flat' ? m.effect.n : 0)
const sum = (ms: Modifier[]) => ms.reduce((s, m) => s + flat(m), 0)

interface OfferedSkill {
  key: SkillKey
  level: number
  untrained: Untrained | null
}

interface Rolled {
  outcomes: SkillRollOutcome[]
  damage: DamageRoll | null
}

export function AttackDialog({ skillLevels, conditions, st, dx, dice, onSpendFp, onBurn, darknessReduction = 0, untrained = [], carried = [], mastery = null, toolNote = null, traits = [], states = { total: 0, line: null }, stateModifiers = [], warmed, onWarmed, spiritPending = 0, pinned = false, onDismiss }: {
  skillLevels: ValueMap<SkillKey, number>
  conditions: (k: SkillKey) => Modifier[]
  st: number
  dx: number
  dice: CharacterDice
  onSpendFp: (n: number) => void
  onBurn: (kind: BurnKind, points: number) => void
  darknessReduction?: number
  untrained?: Untrained[]
  carried?: CataloguedWeapon[]
  mastery?: WeaponMastery | null
  toolNote?: string | null
  traits?: Trait[]
  states?: ClauseBonus
  stateModifiers?: Modifier[]
  warmed: Set<string>
  onWarmed: (s: Set<string>) => void
  spiritPending?: number
  pinned?: boolean
  onDismiss: () => void
}) {
  const skills: OfferedSkill[] = [
    ...[...SkillDefaults.ownedAttacks(skillLevels).entries()].sort(([a], [b]) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0)).map(([key, level]) => ({ key, level, untrained: null })),
    ...untrained.map((it) => ({ key: it.key, level: it.level, untrained: it })),
  ]
  const defaultBonus = TraitEffects.defaultBonus(traits)
  const attacksOf = (s: OfferedSkill) => Damage.attacksFor(s.key.name, s.untrained !== null, defaultBonus)
  const [picked, setPicked] = useState<OfferedSkill | null>(null)
  const [plan, setPlan] = useState<AttackPlan>(Attacks.AttackPlan())
  const [unarmed, setUnarmed] = useState<UnarmedAttack | null>(null)
  const [armed, setArmed] = useState<Armed | null>(null)
  const [band, setBand] = useState<RangePenalty | null>(null)
  const [circumstances, setCircumstances] = useState<Set<string>>(new Set())
  const [aimCancelled, setAimCancelled] = useState(false)
  const [killerTarget, setKillerTarget] = useState<string | null>(null)
  const [charmLit, setCharmLit] = useState(false)
  const [darknessCause, setDarknessCause] = useState<DarknessCause>('DARKNESS')
  const [darknessSeverity, setDarknessSeverity] = useState<number | null>(null)
  const [location, setLocation] = useState<HitLocation | null>(null)
  const [rolled, setRolled, liveRolled] = useLatch<Rolled | null>(null)
  const [damageRolls, setDamageRolls, liveDamageRolls] = useLatch<Map<number, DamageResult>>(new Map())
  const [burnPoints, setBurnPoints] = useState(0)
  const killerClauses = TraitClauses.attackClauses(traits)
  const aimRefused = TraitClauses.refusesAim(traits)
  const warmKey = armed?.weapon.name ?? unarmed?.attack ?? null
  const warmedUp = warmKey !== null && warmed.has(warmKey)
  const chosen = picked

  const levelWith = (weaponInHand: boolean, isRanged: boolean, warm = warmedUp) => {
    const own = chosen?.level ?? 0
    return SkillDefaults.mastered(own, weaponInHand, mastery, dx, isRanged, warm) ?? own
  }
  const inHand = armed !== null || Shields.isWeaponInHand(unarmed)
  const level = levelWith(inHand, isFired(armed))
  const warmUpOffered = mastery !== null && (SkillDefaults.warmsUp(mastery, false) || SkillDefaults.warmsUp(mastery, true))
  const warmUpLive = inHand && mastery !== null && SkillDefaults.warmsUp(mastery, isFired(armed))
  const charm = chosen !== null ? charmFor(traits, chosen.key) : null
  const traitMods = chosen !== null ? traitTerms(traits, chosen.key, killerTarget, new Set(charm !== null && charmLit ? [TraitEffects.clauseKey(charm)] : [])) : []
  const burn = chosen !== null ? burnOffer(EffortBurn.kindFor(chosen.key), traits) : null
  const drawnBurn = burn === null ? 0 : burnPoints
  const isMastered = chosen !== null && level !== chosen.level
  const label = chosen === null ? '' : skillKeyLabel(chosen.key) + (
    isMastered && mastery !== null ? ` (${SkillDefaults.masterySourceFor(mastery, isFired(armed), warmedUp)})`
      : chosen.untrained !== null ? ` (untrained, ${chosen.untrained.source})` : ''
  )

  const anotherTurn = () => {
    setRolled(null); setBurnPoints(0); setDamageRolls(new Map()); setPicked(null); setPlan(Attacks.AttackPlan()); setUnarmed(null); setArmed(null)
    setBand(null); setCircumstances(new Set()); setAimCancelled(false); setKillerTarget(null); setCharmLit(false); setDarknessCause('DARKNESS'); setDarknessSeverity(null); setLocation(null)
  }

  const pick = (entry: OfferedSkill) => {
    const strike = attacksOf(entry)[0] ?? null
    const weapon = strike === null ? SkillDefaults.attackWeapons(entry.key, entry.untrained?.level ?? null, carried, skillLevels)[0] ?? null : null
    const held = weapon !== null ? Armed(weapon) : null
    setPicked(entry)
    setUnarmed(strike)
    setArmed(held)
    setPlan(Attacks.AttackPlan({ isRanged: isFired(held) }))
  }

  const body = () => {
    if (rolled !== null) {
      return (
        <div className="scroll">
          {rolled.outcomes.map((outcome, index) => {
            const hit = outcome.roll !== null && (outcome.roll.outcome === 'SUCCESS' || outcome.roll.outcome === 'CRITICAL_SUCCESS')
            const thrown = damageRolls.get(index) ?? null
            return (
              <div key={index} style={{ paddingBottom: 12 }}>
                <p className="titleSmall" style={{ color: 'var(--on-surface)' }}>{outcome.label}</p>
                <ResultCard out={outcome} degree={null} />
                {hit && rolled.damage !== null && (thrown === null ? (
                  <div style={{ paddingTop: 8 }}>
                    <InkAction
                      label={`Roll damage ${Damage.renderRoll(rolled.damage)}`}
                      icon="casino"
                      onClick={() => {
                        if (liveDamageRolls().has(index) || rolled.damage === null) return
                        const result = Damage.onAttack(Damage.roll(rolled.damage, dice('Attack damage')), outcome.roll?.outcome ?? 'SUCCESS')
                        setDamageRolls(new Map(liveDamageRolls()).set(index, result))
                      }}
                    />
                  </div>
                ) : (
                  <p className="titleMedium" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>
                    Damage {Damage.renderRoll(rolled.damage)}: {thrown.faces.join('+')}{rolled.damage.dice.modifier !== 0 ? signed(rolled.damage.dice.modifier) : ''}
                    {thrown.doubled ? ', ×2 critical hit' : ''} = {thrown.total} {thrown.type}
                  </p>
                ))}
              </div>
            )
          })}
        </div>
      )
    }
    if (chosen === null) {
      return (
        <div className="scroll">
          {skills.map((entry) => (
            <button key={`${skillKeyLabel(entry.key)}/${entry.untrained !== null}`} className="untrained-row" onClick={() => pick(entry)}>
              <span className="grow">
                <span className="bodyLarge" style={{ display: 'block' }}>{disambiguated(entry.key, skills.map((it) => it.key))}</span>
                {entry.untrained !== null && <span className="bodySmall muted">untrained, {entry.untrained.source}</span>}
              </span>
              <span className="level-badge titleMedium mono" style={{ borderColor: 'var(--primary)', color: 'var(--primary)' }}>{entry.level}</span>
            </button>
          ))}
        </div>
      )
    }
    const ranged = rangedModifiers(plan, band, circumstances, aimAcc(armed), aimCancelled || aimRefused, darknessCause, darknessReduction, darknessSeverity)
    const melee = meleeModifiers(plan, circumstances)
    const locationMod = location !== null ? Wounds.modifier(location) : null
    const burnMod = burn !== null ? EffortBurn.modifier(burn.kind, drawnBurn) : null
    const standing = attackStanding(label, level, chosen.key, conditions(chosen.key), [
      ...Attacks.modifiers(plan, level, unarmed), ...ranged, ...melee, ...traitMods, ...stateModifiers,
      ...(locationMod !== null ? [locationMod] : []), ...(burnMod !== null ? [burnMod] : []),
    ])
    const attacks = Attacks.attackCount(plan)
    const fp = Attacks.fpCost(plan)
    const damage = armed !== null
      ? Weapons.damageFor(armed.weapon, armed.mode, st, Attacks.damageBonus(plan, 0))
      : unarmed !== null
        ? Damage.forAttack(unarmed, st, Attacks.damageBonus(plan, unarmed.skill === BOXING ? Damage.boxingBonus(dx, level) : 0))
        : null
    const refusal = pinned ? PINNED_REFUSAL : null
    return (
      <AttackOptions
        label={label}
        skillLevel={level}
        notes={armed !== null && chosen.untrained !== null && toolNote !== null ? [toolNote] : []}
        wieldedAt={(w) => SkillDefaults.wieldedAt(w, skillLevels, mastery, dx, warmed.has(w.name))}
        plan={plan}
        unarmed={unarmed}
        damage={damage}
        attacks={attacksOf(chosen)}
        armed={armed}
        weapons={SkillDefaults.attackWeapons(chosen.key, chosen.untrained?.level ?? null, carried, skillLevels)}
        band={band}
        circumstances={circumstances}
        aimCancelled={aimCancelled}
        aimRefusedBy={aimRefused ? killerClauses[0]?.shown ?? null : null}
        killerClauses={killerClauses}
        killerTarget={killerTarget}
        onKillerTarget={(k) => setKillerTarget(k === killerTarget ? null : k)}
        charm={charm}
        charmLit={charmLit}
        onCharm={() => setCharmLit(!charmLit)}
        fromTraits={sum(traitMods)}
        states={states}
        darknessCause={darknessCause}
        darknessReduction={darknessReduction}
        darknessSeverity={darknessSeverity}
        onDarknessCause={setDarknessCause}
        onDarknessSeverity={setDarknessSeverity}
        location={location}
        onAttack={(it) => {
          setUnarmed(it)
          setArmed(null)
          setPlan(Attacks.atLevel(Attacks.withAttack(plan, it), levelWith(Shields.isWeaponInHand(it), false)))
          const next = new Set(circumstances)
          for (const row of AIM_ROWS()) next.delete(row)
          setCircumstances(next)
        }}
        onArmed={(held) => {
          const strike = held === null ? attacksOf(chosen)[0] ?? null : null
          setArmed(held)
          setUnarmed(strike)
          setPlan(Attacks.atLevel(Attacks.atRange(plan, isFired(held)), levelWith(held !== null || Shields.isWeaponInHand(strike), isFired(held))))
          if (!isFired(held)) { setBand(null); setCircumstances(new Set()) }
        }}
        onBand={setBand}
        onLocation={(it) => setLocation(it === location ? null : it)}
        onAimCancelled={() => {
          const next = !aimCancelled
          setAimCancelled(next)
          if (next) {
            const rows = new Set(circumstances)
            for (const row of AIM_ROWS()) rows.delete(row)
            setCircumstances(rows)
          }
        }}
        onCircumstance={(name) => setCircumstances(toggled(circumstances, name))}
        onPlan={(next) => {
          if (!next.isRanged) { setBand(null); setCircumstances(new Set()) }
          setPlan(next)
        }}
        standing={standing.target}
        fromTrackers={standing.fromTrackers}
        warmUp={warmUpOffered ? warmedUp : null}
        warmUpLive={warmUpLive}
        onWarmUp={() => {
          if (warmKey === null) return
          const next = new Set(warmed)
          if (next.has(warmKey)) next.delete(warmKey)
          else next.add(warmKey)
          onWarmed(next)
          setPlan(Attacks.atLevel(plan, levelWith(inHand, isFired(armed), next.has(warmKey))))
        }}
        burn={burn}
        burnPoints={drawnBurn}
        onBurnPoints={setBurnPoints}
        refusal={refusal}
        spiritPending={spiritPending}
        onRoll={() => {
          if (liveRolled() !== null || pinned) return
          const outcomes = attackTargets(standing.target.target, attacks, spiritPending).map((t) =>
            SkillRoll.rollAgainst({ ...standing.target, target: t }, dice('Attack')))
          setRolled({ outcomes, damage })
          setDamageRolls(new Map())
          if (fp > 0) onSpendFp(fp)
          if (burn !== null && drawnBurn > 0) onBurn(burn.kind, drawnBurn * attacks)
        }}
      />
    )
  }

  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<CloseButton onClick={onDismiss} />}
      dismiss={rolled !== null || chosen !== null ? <CloseButton onClick={anotherTurn} label="Another turn" color="var(--on-surface)" /> : undefined}
      title={<FormTitle form={FORM_ATTACK}>{chosen === null ? 'Attack with which skill?' : 'Attack'}</FormTitle>}
    >
      <SteadyBody>{body()}</SteadyBody>
    </Dialog>
  )
}

function AttackOptions(p: {
  label: string
  skillLevel: number
  notes: string[]
  wieldedAt: (w: CataloguedWeapon) => number | null
  plan: AttackPlan
  unarmed: UnarmedAttack | null
  damage: DamageRoll | null
  attacks: UnarmedAttack[]
  armed: Armed | null
  weapons: CataloguedWeapon[]
  band: RangePenalty | null
  circumstances: Set<string>
  aimCancelled: boolean
  aimRefusedBy: string | null
  killerClauses: TraitClause[]
  killerTarget: string | null
  onKillerTarget: (k: string) => void
  charm: TraitClause | null
  charmLit: boolean
  onCharm: () => void
  fromTraits: number
  states: ClauseBonus
  darknessCause: DarknessCause
  darknessReduction: number
  darknessSeverity: number | null
  onDarknessCause: (c: DarknessCause) => void
  onDarknessSeverity: (n: number) => void
  location: HitLocation | null
  onAttack: (a: UnarmedAttack) => void
  onArmed: (a: Armed | null) => void
  onBand: (b: RangePenalty) => void
  onCircumstance: (name: string) => void
  onAimCancelled: () => void
  onLocation: (l: HitLocation) => void
  onPlan: (p: AttackPlan) => void
  standing: SkillTarget
  fromTrackers: number
  warmUp: boolean | null
  warmUpLive: boolean
  onWarmUp: () => void
  burn: BurnOffer | null
  burnPoints: number
  onBurnPoints: (n: number) => void
  refusal: string | null
  spiritPending: number
  onRoll: () => void
}) {
  const { plan, unarmed, armed, damage, location, burn } = p
  const maxDeceptive = Attacks.maxDeceptive(p.skillLevel)
  const fp = Attacks.fpCost(plan)
  const total = sum(Attacks.modifiers(plan, p.skillLevel, unarmed))
  const count = Attacks.attackCount(plan)
  const rangeTerms = rangedModifiers(plan, p.band, p.circumstances, aimAcc(armed), p.aimCancelled || p.aimRefusedBy !== null, p.darknessCause, p.darknessReduction, p.darknessSeverity).map(flat)
  const fromMelee = sum(meleeModifiers(plan, p.circumstances))
  const lines = [
    `Attack options: ${total === 0 ? 'no modifier' : signed(total)}` + (count > 1 ? `, ${count} attacks` : '') + (fp > 0 ? `, costs ${fp} FP` : ''),
    plan.isRanged ? `Range and cover: ${termLine(rangeTerms)}` : fromMelee !== 0 ? `Target: ${signed(fromMelee)}` : null,
    p.fromTraits !== 0 ? `Traits: ${signed(p.fromTraits)}` : null,
    p.states.line !== null ? `Declared on the sheet: ${p.states.line}` : null,
    location !== null ? `Called shot: ${location.target} ${signed(location.penalty)}, ${location.printedMultiplier} — ${location.effect}` : null,
    damage !== null
      ? !Wounds.knowsType(damage.type)
        ? `Damage on a hit: ${Damage.renderRoll(damage)} — this book prints no wounding multiplier for "${damage.type}"`
        : `Damage on a hit: ${Damage.renderRoll(damage)}${woundingNote(location, damage.type)}`
      : unarmed !== null ? (unarmed.opposedRoll !== null ? `The target rolls ${unarmed.opposedRoll}.` : 'Rolls no damage; see the note above.') : null,
    p.fromTrackers !== 0 ? `Injury, fatigue and attrition: ${signed(p.fromTrackers)}` : null,
    burn !== null && p.burnPoints > 0
      ? `Burned ${EffortBurn.pool(burn.kind).pool}: +${p.burnPoints}` + (count > 1 ? ` on each attack, costs ${p.burnPoints * count} ${EffortBurn.pool(burn.kind).pool}` : '')
      : null,
  ].filter((it): it is string => it !== null && it !== '')
  return (
    <>
      <div className="scroll">
        <div className="target-card row" style={{ marginTop: 0, gap: 8 }}>
          <span className="titleMedium grow" style={{ color: 'var(--on-surface)' }}>{p.label}</span>
          <span className="level-badge titleMedium mono" style={{ borderColor: 'var(--primary)', color: 'var(--primary)' }}>{p.skillLevel}</span>
        </div>
        {p.notes.map((it) => <p key={it} className="bodySmall muted">{it}</p>)}
        {p.warmUp !== null && (
          <div style={{ padding: '4px 0' }}>
            <CaseChip selected={p.warmUp && p.warmUpLive} disabled={!p.warmUpLive} onClick={p.onWarmUp}>Used repeatedly this session (DX+2)</CaseChip>
          </div>
        )}
        {p.attacks.length > 0 && (
          <>
            <SectionLabel text="Strike" />
            <Chips>{p.attacks.map((option) => <CaseChip key={option.attack} selected={unarmed?.attack === option.attack} onClick={() => p.onAttack(option)}>{option.attack}</CaseChip>)}</Chips>
            <p className="bodySmall" style={{ padding: '4px 0', minHeight: 28 }}>{unarmed !== null ? `${unarmed.damage} — ${unarmed.notes}` : ''}</p>
            {p.attacks.some((it) => it.attack === Attacks.PIN) && (
              <CaseChip selected={unarmed?.attack === Attacks.PIN && plan.pinOnGrappledProne} disabled={unarmed?.attack !== Attacks.PIN} onClick={() => { if (unarmed?.attack === Attacks.PIN) p.onPlan({ ...plan, pinOnGrappledProne: !plan.pinOnGrappledProne }) }}>
                Target grappled &amp; prone +{Attacks.PIN_PRONE_BONUS}
              </CaseChip>
            )}
          </>
        )}
        <ArmedWith weapons={p.weapons} armed={armed} wieldedAt={p.wieldedAt} onArmed={p.onArmed} />
        <SectionLabel text="Maneuver" />
        <Chips>{Attacks.ATTACK_MANEUVERS.map((option) => <CaseChip key={option} selected={plan.maneuver === option} onClick={() => p.onPlan(Attacks.withManeuver(plan, option))}>{Attacks.MANEUVERS[option].printed}</CaseChip>)}</Chips>
        <Chips>
          {([[false, 'In reach'], [true, 'At range']] as const).map(([isRanged, text]) => (
            <CaseChip key={text} selected={plan.isRanged === isRanged} onClick={() => { if (plan.isRanged !== isRanged) p.onPlan(Attacks.atRange(plan, isRanged)) }}>{text}</CaseChip>
          ))}
        </Chips>
        {plan.isRanged ? (
          <RangedOptions {...p} />
        ) : (
          <>
            <SectionLabel text="Options" />
            <OptionStepper label="Rapid Strike" value={plan.rapidStrikes} note={`${Attacks.RAPID_STRIKE_PENALTY} each`} onStep={(d) => p.onPlan({ ...plan, rapidStrikes: Math.max(plan.rapidStrikes + d, 0) })} />
            <OptionStepper
              label="Deceptive"
              value={plan.deceptive}
              note={maxDeceptive === 0 ? 'skill too low' : `-2 each, max ${maxDeceptive}`}
              onStep={(d) => p.onPlan({ ...plan, deceptive: Math.min(Math.max(plan.deceptive + d, 0), maxDeceptive) })}
            />
            <Chips>
              {Ranged.meleeConditions().map((option) => (
                <CaseChip key={option.condition} selected={p.circumstances.has(option.condition)} onClick={() => p.onCircumstance(option.condition)}>{option.condition} {option.printed}</CaseChip>
              ))}
            </Chips>
          </>
        )}
        <SectionLabel text="Called shot" />
        <Chips>{Wounds.locations().map((option) => <CaseChip key={option.target} selected={option === location} onClick={() => p.onLocation(option)}>{option.target} {signed(option.penalty)}</CaseChip>)}</Chips>
        {p.killerClauses.length > 0 && (
          <>
            <SectionLabel text={`Target (${p.killerClauses[0].shown})`} />
            <Chips>
              {p.killerClauses.map((clause) => (
                <CaseChip key={TraitEffects.clauseKey(clause)} selected={TraitEffects.clauseKey(clause) === p.killerTarget} onClick={() => p.onKillerTarget(TraitEffects.clauseKey(clause))}>{clause.printed}</CaseChip>
              ))}
            </Chips>
          </>
        )}
        {p.charm !== null && (
          <>
            <SectionLabel text={p.charm.shown} />
            <CaseChip selected={p.charmLit} onClick={p.onCharm}>{p.charm.printed}</CaseChip>
          </>
        )}
        <SectionLabel text="Extra effort" />
        <div className="col" style={{ gap: 4, alignItems: 'flex-start' }}>
          {Attacks.offersStrongBlow(plan, damage !== null, isFired(armed)) && (
            <CaseChip selected={plan.strongBlow} onClick={() => p.onPlan({ ...plan, strongBlow: !plan.strongBlow })}>
              {Attacks.STRONG_BLOW} ({Attacks.effortFp(Attacks.STRONG_BLOW)} FP, +{Attacks.STRONG_BLOW_DAMAGE} damage)
            </CaseChip>
          )}
          {plan.maneuver === 'CHARGE' && (
            <CaseChip selected={plan.heroicCharge} onClick={() => p.onPlan({ ...plan, heroicCharge: !plan.heroicCharge })}>
              {Attacks.HEROIC_CHARGE} ({Attacks.effortFp(Attacks.HEROIC_CHARGE)} FP)
            </CaseChip>
          )}
          <CaseChip selected={plan.giantStep} onClick={() => p.onPlan({ ...plan, giantStep: !plan.giantStep })}>
            {Attacks.GIANT_STEP} ({Attacks.effortFp(Attacks.GIANT_STEP)} FP)
          </CaseChip>
        </div>
        {burn !== null && <BurnStepper offer={burn} points={p.burnPoints} onPoints={p.onBurnPoints} />}
      </div>
      <FooterBand>
        {lines.map((it) => <p key={it} className="labelMedium muted">{it}</p>)}
        <FooterRow
          target={p.standing.forbidden || p.refusal !== null ? null : p.standing.target}
          button={<RollButton label="Roll" onClick={p.onRoll} disabled={p.standing.forbidden || p.refusal !== null} />}
        >
          {p.refusal !== null ? <span className="err">{p.refusal}</span>
            : p.standing.forbidden ? <span className="err">{FORBIDDEN_ROLL}</span>
            : <span className="labelLarge mono">{attackCaption(p.standing.target, count, p.spiritPending)}</span>}
          {p.standing.requiresCritical && <span className="bodySmall err">Only a critical success counts.</span>}
        </FooterRow>
      </FooterBand>
    </>
  )
}

function ArmedWith({ weapons, armed, wieldedAt, onArmed }: { weapons: CataloguedWeapon[]; armed: Armed | null; wieldedAt: (w: CataloguedWeapon) => number | null; onArmed: (a: Armed | null) => void }) {
  if (weapons.length === 0) return null
  const level = armed !== null ? wieldedAt(armed.weapon) : null
  return (
    <>
      <SectionLabel text="Weapon" />
      <Chips>
        {weapons.map((option) => (
          <CaseChip key={option.name} selected={armed?.weapon.name === option.name} onClick={() => onArmed(armed?.weapon.name === option.name ? null : Armed(option))}>{option.name}</CaseChip>
        ))}
      </Chips>
      {armed !== null && (
        <>
          {armed.weapon.damage.length > 1 && (
            <Chips>
              {armed.weapon.damage.map((mode) => <CaseChip key={mode.printed} selected={armed.mode === mode} onClick={() => onArmed(Armed(armed.weapon, mode))}>{mode.printed}</CaseChip>)}
            </Chips>
          )}
          <p className="bodySmall" style={{ padding: '4px 0' }}>
            Acc {armed.weapon.acc} · reach/range {armed.weapon.range} · {!Weapons.canParry(armed.weapon) ? 'cannot parry' : level !== null ? `parry ${Weapons.parryFor(armed.weapon, level)}` : "untrained parry is the GM's call"} · {armed.weapon.cost}
          </p>
          {armed.weapon.notes.trim() !== '' && <p className="bodySmall">{armed.weapon.notes}</p>}
        </>
      )}
    </>
  )
}

function RangedOptions(p: {
  band: RangePenalty | null
  circumstances: Set<string>
  armed: Armed | null
  aimCancelled: boolean
  aimRefusedBy: string | null
  darknessCause: DarknessCause
  darknessReduction: number
  darknessSeverity: number | null
  onBand: (b: RangePenalty) => void
  onCircumstance: (name: string) => void
  onAimCancelled: () => void
  onDarknessCause: (c: DarknessCause) => void
  onDarknessSeverity: (n: number) => void
}) {
  const conditions = Ranged.offeredConditions(p.aimCancelled || p.aimRefusedBy !== null)
  const darkness = Ranged.conditions().find((it) => it.worstCase !== null)!
  const lit = p.circumstances.has(darkness.condition)
  const acc = aimAcc(p.armed)
  return (
    <>
      <p className="titleSmall" style={{ color: 'var(--on-surface)' }}>Distance</p>
      <Chips>
        {Ranged.bands().map((option) => (
          <CaseChip key={option.printed} selected={p.band?.printed === option.printed} onClick={() => p.onBand(option)}>{substringBefore(option.printed, ' meters')} {signed(option.penalty)}</CaseChip>
        ))}
      </Chips>
      <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>Conditions</p>
      <Chips>
        {conditions.filter((it) => !it.weaponAcc).map((option) => (
          <CaseChip key={option.condition} selected={p.circumstances.has(option.condition)} onClick={() => p.onCircumstance(option.condition)}>{option.condition} {option.printed}</CaseChip>
        ))}
      </Chips>
      {lit && (
        <>
          <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>How bad is it?</p>
          <Chips>
            {endsBetween(darkness.modifier!, darkness.worstCase).map((value) => (
              <CaseChip key={value} selected={(p.darknessSeverity ?? darkness.modifier) === value} onClick={() => p.onDarknessSeverity(value)}>{signed(value)}</CaseChip>
            ))}
          </Chips>
        </>
      )}
      {p.darknessReduction > 0 && lit && (
        <>
          <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>What is it?</p>
          <Chips>{CAUSES.map(([cause, text]) => <CaseChip key={cause} selected={cause === p.darknessCause} onClick={() => p.onDarknessCause(cause)}>{text}</CaseChip>)}</Chips>
          <p className="bodySmall">{TraitEffects.NIGHT_EYES}: "{TraitEffects.printedNightEyes()}"</p>
        </>
      )}
      {p.aimRefusedBy !== null ? (
        <p className="bodySmall">{p.aimRefusedBy}: "{TraitClauses.AIM_REFUSAL}"</p>
      ) : (
        <>
          <p className="titleSmall" style={{ color: 'var(--on-surface)' }}>Since taking aim</p>
          <Chips><CaseChip selected={p.aimCancelled} onClick={p.onAimCancelled}>Defended or was hurt</CaseChip></Chips>
          {p.aimCancelled && <p className="bodySmall">Aim (Ranged): "{Ranged.printedAim()}"</p>}
          {conditions.filter((it) => it.weaponAcc).map((option) =>
            acc === null ? (
              <p key={option.condition} className="bodySmall">{option.condition}: {option.printed} — choose a firearm above.</p>
            ) : (
              <CaseChip key={option.condition} selected={p.circumstances.has(option.condition)} onClick={() => p.onCircumstance(option.condition)}>{option.condition} {signed(acc + option.accBonus)}</CaseChip>
            ),
          )}
        </>
      )}
    </>
  )
}
