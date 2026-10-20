// SheetTabs.kt: the Vitals, Traits and Skills tabs, with SheetRows.kt's rows,
// VitalsTable.kt, TraitRows.kt, EncumbranceCard.kt and ClauseChips.
import { useState } from 'react'
import type { CharacterDice } from '../../app/RollLog'
import { attrCodeOf, carriedText, disambiguated, drawbackPrice, encumbranceLine, spelledOut } from '../../app/SheetText'
import { grantedBy, SECONDARY_ROWS, type RollableSkill, type SheetModel } from '../../app/SheetModel'
import { grantedSkills, liveLevelOf, liveLevelOrNull, purchasedKeys } from '../../app/session/EditedCharacter'
import { playedLine, type PlayedLine } from '../../app/session/SheetLines'
import type { SavedEdits } from '../../app/session/SessionStore'
import * as Appearance from '../../rules/Appearance'
import * as AttributeRoll from '../../rules/AttributeRoll'
import { canonical, type ValueMap } from '../../rules/kt'
import { isSuccess, type RollResult, type SkillKey, type Trait } from '../../rules/model'
import * as NaturalRecovery from '../../rules/NaturalRecovery'
import type { PoolStatus } from '../../rules/Pools'
import * as Premades from '../../rules/Premades'
import type { PremadeSheet } from '../../rules/Premades'
import type { SelfControlCheck } from '../../rules/SelfControl'
import * as SheetComparison from '../../rules/SheetComparison'
import type { SheetRow } from '../../rules/SheetComparison'
import * as Shock from '../../rules/Shock'
import * as StunRecovery from '../../rules/StunRecovery'
import * as SuccessRoll from '../../rules/SuccessRoll'
import { clauseKey, type ClauseBonus, type TraitClause } from '../../rules/TraitEffects'
import * as TraitGrants from '../../rules/TraitGrants'
import * as TraitReport from '../../rules/TraitReport'
import type { HeldTrait } from '../../rules/TraitReport'
import * as VitalRolls from '../../rules/VitalRolls'
import type { VitalCheck } from '../../rules/VitalRolls'
import { Icon } from '../icons'
import { BracketAction, CaseChip, Caps, Chips, InkAction, outcomeLabel, RuleNote, SectionLabel, Stamp, useLatch, VerdictCard } from '../kit'
import { LevelBox, RollGlyph, SheetSection, TableHead } from './SheetParts'

const SKILL_ATTR_WIDTH = 52
const SKILL_LEVEL_WIDTH = 80

// ---- Skills ----

export function SkillsTab({ sheet, rows, skillLevels, edits, onRoll, onPickUntrained, amendedOnly, onAmendedOnly }: {
  sheet: PremadeSheet
  rows: SheetRow[]
  skillLevels: ValueMap<SkillKey, number>
  edits: SavedEdits
  onRoll: (s: RollableSkill) => void
  onPickUntrained: () => void
  amendedOnly: boolean
  onAmendedOnly: (v: boolean) => void
}) {
  const published = sheet.skills.flatMap((skill, i) => {
    const row = rows[SECONDARY_ROWS + i]
    return row ? [[skill, row] as const] : []
  })
  const bought = purchasedKeys(edits).flatMap((key) => {
    const level = liveLevelOrNull(key, skillLevels)
    return level === null ? [] : [[key, level] as const]
  })
  const granted = grantedSkills(sheet, edits, skillLevels)
  const shown = amendedOnly ? published.filter(([, row]) => !SheetComparison.matches(row)) : published
  const addedKeys = [...sheet.skills.map(Premades.skillKey), ...purchasedKeys(edits), ...granted.map(([k]) => k)]
  return (
    <>
      <SheetSection title="Skills" count={shown.length + (amendedOnly ? 0 : bought.length + granted.length)} />
      <button className="dashed-button" onClick={onPickUntrained}>
        <Icon name="casino" size={20} />
        <Caps text="Roll a skill this sheet does not have" className="labelLarge mono" color="var(--on-surface)" />
      </button>
      {amendedOnly && <div style={{ padding: '0 12px' }}><BracketAction label="Show all values" onClick={() => onAmendedOnly(false)} /></div>}
      <TableHead heads={['Skill', 'Attr', 'Lvl']} widths={[SKILL_ATTR_WIDTH, SKILL_LEVEL_WIDTH]} />
      {shown.map(([skill, row]) => {
        const key = Premades.skillKey(skill)
        const level = liveLevelOf(key, skillLevels, Premades.levelFor(sheet, skill))
        return (
          <ComparisonRow
            key={`${skill.name}/${skill.specialization}`}
            line={playedLine(row, String(level))}
            attr={attrCodeOf(key)}
            onClick={() => onRoll({ label: Premades.skillLabel(skill), base: level, key })}
          />
        )
      })}
      {!amendedOnly &&
        bought.map(([key, level]) => {
          const label = disambiguated(key, addedKeys)
          return <AddedSkillRow key={`b/${canonical(key)}`} label={label} attr={attrCodeOf(key)} level={level} provenance="bought" onClick={() => onRoll({ label, base: level, key })} />
        })}
      {!amendedOnly &&
        granted.map(([key, level]) => {
          const label = disambiguated(key, addedKeys)
          return <AddedSkillRow key={`g/${canonical(key)}`} label={label} attr={attrCodeOf(key)} level={level} provenance="potion" onClick={() => onRoll({ label, base: level, key })} />
        })}
    </>
  )
}

function ComparisonRow({ line, attr, onClick }: { line: PlayedLine; attr: string; onClick: () => void }) {
  return (
    <SkillRowFrame onClick={onClick}>
      <span className="grow col">
        <span className="bodyLarge">{line.label}</span>
        {line.basis && <span className="labelSmall muted">{line.basis.replace(/^ · /, '')}</span>}
        {line.disagreement && <span className="labelSmall err">{line.disagreement.trim()}</span>}
      </span>
      <span className="labelSmall muted" style={{ width: SKILL_ATTR_WIDTH }}>{attr}</span>
      <SkillRollCells level={line.number} />
    </SkillRowFrame>
  )
}

function AddedSkillRow({ label, attr, level, provenance, onClick }: { label: string; attr: string; level: number; provenance: string; onClick: () => void }) {
  const potion = provenance === 'potion'
  return (
    <SkillRowFrame onClick={onClick}>
      <span className="grow col">
        <span className="bodyLarge">{label}</span>
        <Caps text={provenance} color={potion ? 'var(--secondary)' : 'var(--on-surface-variant)'} />
      </span>
      <span className="labelSmall muted" style={{ width: SKILL_ATTR_WIDTH }}>{attr}</span>
      <SkillRollCells level={String(level)} />
    </SkillRowFrame>
  )
}

function SkillRowFrame({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <div>
      <button className="skill-row" onClick={onClick} aria-label={undefined}>
        {children}
      </button>
      <hr className="rule inset" />
    </div>
  )
}

function SkillRollCells({ level }: { level: string }) {
  return (
    <span className="row" style={{ width: SKILL_LEVEL_WIDTH, gap: 8, justifyContent: 'flex-end' }}>
      <RollGlyph />
      <LevelBox number={level} />
    </span>
  )
}

// ---- Vitals ----

export function VitalsTab({ m, isOwn, dice, amendedOnly, onAmendedOnly, onClearShock, stunned, onClearStun, spiritPending, onClearSpirit, onDeclareStates, onRollAttribute, carried, basicLift, onCarried }: {
  m: SheetModel
  isOwn: boolean
  dice: CharacterDice
  amendedOnly: boolean
  onAmendedOnly: (v: boolean) => void
  onClearShock: () => void
  stunned: boolean
  onClearStun: () => void
  spiritPending: number
  onClearSpirit: () => void
  onDeclareStates: (s: Set<string>) => void
  onRollAttribute: (k: AttributeRoll.Kind) => void
  carried: number | null
  basicLift: number
  onCarried: (v: number | null) => void
}) {
  const differing = m.rows.filter((it) => !SheetComparison.matches(it)).length
  const diffSummary = isOwn
    ? null
    : differing === 0
      ? `All ${m.rows.length} values match the published sheet`
      : `${differing} of ${m.rows.length} values differ from the published sheet`
  const sheetFigures = isOwn ? null : new Map(m.rows.slice(0, SECONDARY_ROWS).map((it) => [it.label, it.published]))
  return (
    <>
      <PoolTrackers hp={m.hpNow} ht={m.attributes.ht} traits={m.traits} poolPenalty={m.hurtOnAttribute} shock={m.shockOnAttribute} states={m.physicalStates} dice={dice} />
      {m.allRolls.length > 0 && (
        <div className="pad16">
          <ClauseChips clauses={m.allRolls} declared={m.declaredStates} onDeclared={onDeclareStates} />
          <p className="bodySmall muted">Lit: on every roll it names until cleared here</p>
        </div>
      )}
      {stunned && <StunRow key={m.stunRecoveryTarget - m.physicalStates.total} target={m.stunRecoveryTarget} onClear={onClearStun} dice={dice} />}
      {spiritPending > 0 && (
        <div className="row pad16">
          <p className="bodySmall err grow">Spirit body damage — -{spiritPending} on the next roll, then it clears</p>
          <InkAction label="Clear" onClick={onClearSpirit} />
        </div>
      )}
      {m.shocked && (
        <div className="row pad16">
          <p className="bodySmall err grow">
            Shock — "{Shock.printed()}"{Shock.ignoredBy(m.traits) ? ` — ignored by ${Shock.IGNORED_BY}` : ''}
          </p>
          <InkAction label="Clear" onClick={onClearShock} />
        </div>
      )}
      <VitalsCard diffSummary={diffSummary} amended={!isOwn && differing > 0} amendedOnly={amendedOnly} onAmendedOnly={onAmendedOnly} lines={m.secondaryLines} sheetFigures={sheetFigures} onRoll={onRollAttribute} />
      <div className="pad16">
        <SectionLabel text="Carrying" />
        <label className="outlined-field">
          <span className="bodySmall muted">Total weight carried (lbs)</span>
          <input
            className="bodyLarge"
            inputMode="decimal"
            defaultValue={carriedText(carried)}
            onChange={(e) => {
              const typed = e.target.value
              if (typed.trim() === '') onCarried(null)
              else {
                const n = Number(typed)
                if (Number.isFinite(n) && n >= 0 && /^\s*[0-9.]+\s*$/.test(typed)) onCarried(n)
              }
            }}
          />
        </label>
        <p className="bodySmall">{encumbranceLine(carried, basicLift)}</p>
      </div>
    </>
  )
}

const ATTRIBUTE_KINDS: AttributeRoll.Kind[] = ['WILL', 'PER', 'INTUITION', 'PERCEPTION']

function VitalsCard({ diffSummary, amended, amendedOnly, onAmendedOnly, lines, sheetFigures, onRoll }: {
  diffSummary: string | null
  amended: boolean
  amendedOnly: boolean
  onAmendedOnly: (v: boolean) => void
  lines: PlayedLine[]
  sheetFigures: Map<string, string | null> | null
  onRoll: (k: AttributeRoll.Kind) => void
}) {
  const count = diffSummary?.split(' values')[0] ?? ''
  return (
    <div style={{ padding: '8px 0' }}>
      {diffSummary !== null && (
        <div style={{ padding: '8px 16px', position: 'relative' }}>
          <div className={`diff-card${amended ? ' amended' : ''}`}>
            <p className="bodyLarge" style={{ paddingRight: 72 }}>
              <span className="mono semibold">{count}</span>
              {diffSummary.slice(count.length)}
            </p>
            {amended && (
              <button className="labelMedium err underline amend-toggle" onClick={() => onAmendedOnly(!amendedOnly)}>
                {amendedOnly ? 'Show all values' : 'Show amendments'}
              </button>
            )}
          </div>
          {amended && <span className="amended-stamp"><Stamp text="Amended" color="var(--error)" small /></span>}
        </div>
      )}
      <SheetSection title="Secondary characteristics" />
      <TableHead heads={['Stat', ...(sheetFigures ? ['Sheet'] : []), 'Now']} />
      {lines
        .filter((it) => !amendedOnly || it.disagreement !== null)
        .map((line) => {
          const differs = line.disagreement !== null
          const rolls = ATTRIBUTE_KINDS.find((k) => AttributeRoll.KINDS[k].label === line.label) ?? null
          const figure = sheetFigures?.get(line.label) ?? '—'
          const body = (
            <>
              <span className="grow col">
                <span className="row" style={{ gap: 10 }}>
                  <span className="bodyLarge" style={{ color: differs ? 'var(--error)' : undefined }}>{spelledOut(line.label)}</span>
                  {rolls && <RollGlyph />}
                </span>
                {line.basis && <span className="labelSmall muted">{line.basis.replace(/^ · /, '')}</span>}
              </span>
              {sheetFigures && (
                <span className="titleMedium mono" style={{ width: 72, textAlign: 'right', color: differs ? 'var(--error)' : 'var(--on-surface-variant)', textDecoration: differs ? 'line-through' : undefined }} aria-label={differs ? `sheet ${figure}, disputed` : `sheet ${figure}`}>
                  {figure}
                </span>
              )}
              <span className="titleLarge mono semibold" style={{ width: 72, textAlign: 'right', color: differs ? 'var(--error)' : undefined }}>{line.number}</span>
            </>
          )
          return (
            <div key={line.label} className={differs ? 'differs' : ''}>
              {rolls ? (
                <button className="vital-row" onClick={() => onRoll(rolls)} aria-label={`Roll ${AttributeRoll.KINDS[rolls].label}`}>{body}</button>
              ) : (
                <div className="vital-row">{body}</div>
              )}
              <hr className="rule inset" />
            </div>
          )
        })}
    </div>
  )
}

export function ClauseChips({ clauses, declared, onDeclared }: { clauses: TraitClause[]; declared: Set<string>; onDeclared: (s: Set<string>) => void }) {
  const conditional = clauses.filter((it) => it.conditional)
  if (conditional.length === 0) return null
  return (
    <>
      <p className="labelMedium" style={{ paddingTop: 8 }}>Does any of this hold?</p>
      <Chips>
        {conditional.map((clause) => (
          <CaseChip
            key={clauseKey(clause)}
            selected={declared.has(clauseKey(clause))}
            onClick={() => {
              const next = new Set(declared)
              if (next.has(clauseKey(clause))) next.delete(clauseKey(clause))
              else next.add(clauseKey(clause))
              onDeclared(next)
            }}
          >
            {clause.shown}: {clause.printed}
          </CaseChip>
        ))}
      </Chips>
    </>
  )
}

function PoolTrackers({ hp, ht, traits, poolPenalty, shock, states, dice }: { hp: PoolStatus; ht: number; traits: Trait[]; poolPenalty: number; shock: number; states: ClauseBonus; dice: CharacterDice }) {
  return (
    <div style={{ padding: '4px 0' }}>
      {VitalRolls.due(hp).map((check) => {
        const target = VitalRolls.targetFor(check, ht, traits, poolPenalty, shock, states.total)
        return (
          <VitalRollRow
            key={`${check}/${target - states.total}`}
            check={check}
            hp={hp}
            target={target}
            fromTraits={VitalRolls.bonusFor(check, traits)}
            ht={ht}
            poolPenalty={poolPenalty}
            shock={shock}
            states={states}
            dice={dice}
          />
        )
      })}
      {NaturalRecovery.isOffered(hp) && <RecoveryRollRow ht={ht} traits={traits} poolPenalty={poolPenalty} states={states} dice={dice} />}
    </div>
  )
}

function partsLine(ht: number, fromTraits: number, poolPenalty: number, shock: number | null, states: ClauseBonus, entered: number | null = null) {
  const out = [`HT ${ht}`]
  if (fromTraits > 0) out.push(`+${fromTraits} from traits`)
  if (fromTraits < 0) out.push(`${fromTraits} from traits`)
  if (poolPenalty !== 0) out.push(`${poolPenalty} hurt or tired`)
  if (shock !== null && shock !== 0) out.push(`${shock} shock`)
  if (states.line !== null) out.push(states.line)
  if (entered !== null && entered > 0) out.push(`+${entered} entered`)
  if (entered !== null && entered < 0) out.push(`${entered} entered`)
  return out.join(', ')
}

function VitalRollRow({ check, hp, target, fromTraits, ht, poolPenalty, shock, states, dice }: {
  check: VitalCheck; hp: PoolStatus; target: number; fromTraits: number; ht: number; poolPenalty: number; shock: number; states: ClauseBonus; dice: CharacterDice
}) {
  const [outcome, setOutcome, live] = useLatch<RollResult | null>(null)
  return (
    <div className="pad16" style={{ padding: '4px 16px' }}>
      <p className="bodySmall">{VitalRolls.printed(check, hp)}</p>
      <p className="bodySmall">{partsLine(ht, fromTraits, poolPenalty, shock, states)}</p>
      {outcome === null ? (
        <InkAction
          label={`Roll HT ${target} — ${VitalRolls.rowFor(check, hp)}`}
          icon="casino"
          onClick={() => {
            if (live() !== null) return
            setOutcome(SuccessRoll.against(target, dice(check === 'SURVIVAL' ? 'HT to survive' : 'HT to stay conscious')))
          }}
        />
      ) : (
        <>
          <p className="titleSmall">3d6: {outcome.total} vs {outcome.target} - {outcomeLabel(outcome.outcome).toLowerCase()}</p>
          <p className="bodySmall">The book asks for this roll each turn while the threshold holds - that repetition is the GM's to run.</p>
          <InkAction label="Roll again" onClick={() => setOutcome(null)} />
        </>
      )}
    </div>
  )
}

function RecoveryRollRow({ ht, traits, poolPenalty, states, dice }: { ht: number; traits: Trait[]; poolPenalty: number; states: ClauseBonus; dice: CharacterDice }) {
  const [open, setOpen] = useState(false)
  const [modifier, setModifier] = useState(0)
  const [outcome, setOutcome, live] = useLatch<RollResult | null>(null)
  const fromTraits = NaturalRecovery.bonusFor(traits)
  const target = NaturalRecovery.targetFor(ht, traits, poolPenalty, modifier, states.total)
  return (
    <div style={{ padding: '8px 16px' }}>
      <hr className="rule thick" />
      <button className="recovery-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className="headlineSmall grow">Natural recovery</span>
        <Icon name={open ? 'remove' : 'add'} />
      </button>
      {open && (
        <>
          <hr className="rule ink" />
          {outcome === null ? (
            <>
              <p className="bodyMedium muted" style={{ paddingTop: 12 }}>{partsLine(ht, fromTraits, poolPenalty, null, states, modifier)}</p>
              <div className="row" style={{ padding: '12px 0' }}>
                <span className="entered-stepper">
                  <button className="titleMedium mono red" onClick={() => setModifier(modifier - 1)}>−1</button>
                  <span className="titleMedium mono">{modifier > 0 ? '+' : ''}{modifier}</span>
                  <button className="titleMedium mono" onClick={() => setModifier(modifier + 1)}>+1</button>
                </span>
                <span className="grow" />
                <span className="bodyLarge muted">vs&nbsp;</span>
                <span className="headlineSmall mono semibold">HT {target}</span>
              </div>
              <button
                className="wide-roll"
                onClick={() => {
                  if (live() !== null) return
                  setOutcome(SuccessRoll.against(target, dice('Natural recovery')))
                }}
              >
                <Icon name="casino" size={20} />
                <Caps text={`Roll HT ${target} — a day of rest and food`} className="labelLarge mono" color="currentColor" />
              </button>
            </>
          ) : (
            <>
              <VerdictCard verdict={outcomeLabel(outcome.outcome)} passed={isSuccess(outcome.outcome)} roll={outcome}>
                <p className="bodyMedium" style={{ paddingTop: 8 }}>{NaturalRecovery.dayLine(isSuccess(outcome.outcome), traits)}</p>
              </VerdictCard>
              <BracketAction label="Another day" onClick={() => setOutcome(null)} />
            </>
          )}
          <RuleNote title="The book's rows">
            {NaturalRecovery.rows().map((it) => (
              <p key={it.method} className="bodySmall">{it.method}: {it.recovered}. {it.notes}</p>
            ))}
            <p className="bodySmall" style={{ paddingTop: 4 }}>
              Anything else that moves the daily HT roll - a potion's grant, say - is entered with −1 / +1. Only what the line above them shows is applied for you.
            </p>
          </RuleNote>
        </>
      )}
    </div>
  )
}

function StunRow({ target, onClear, dice }: { target: number; onClear: () => void; dice: CharacterDice }) {
  const [outcome, setOutcome, live] = useLatch<RollResult | null>(null)
  const drawn = outcome
  return (
    <div style={{ padding: '4px 16px' }}>
      <div className="row">
        <p className="bodySmall err grow">Stunned — "{StunRecovery.printed()}"</p>
        <InkAction label="Clear" onClick={onClear} />
      </div>
      <InkAction
        label={`Roll HT ${target} to recover`}
        icon="casino"
        onClick={() => {
          if (live() !== drawn) return
          const roll = SuccessRoll.against(target, dice('Stun recovery'))
          setOutcome(roll)
          if (!StunRecovery.stillStunned(roll)) onClear()
        }}
      />
      {outcome && (
        <p className="titleSmall">3d6: {outcome.total} vs {outcome.target} - {outcomeLabel(outcome.outcome).toLowerCase()}, still stunned</p>
      )}
    </div>
  )
}

// ---- Traits ----

const LEGEND: [TraitReport.Applied, string][] = [
  ['APPLIED', 'every rule on the row reaches the rolls it names.'],
  ['PARTLY', 'open the row for the part that is not applied, and why.'],
  ['ROLL_ONLY', `${TraitReport.ROLLED_ONLY}.`],
  ['GM', `${TraitReport.UNREAD}, unless the row says why.`],
  ['NOT_IN_BOOK', "the sheet's spelling matches no row in the book's list, so nothing is read from it."],
]
const capitalised = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

const superseded = (trait: Trait, traits: Trait[]) => (Appearance.isSuperseded(trait, traits) ? ", not played: the potion's face is better" : '')
const held = (trait: Trait, list: Trait[]) => list.some((it) => canonical(it) === canonical(trait))

export function TraitsTab({ m, expanded, onToggle, onControl }: {
  m: SheetModel
  expanded: Set<string>
  onToggle: (key: string) => void
  onControl: (check: SelfControlCheck) => void
}) {
  const by = grantedBy(m.drunk !== null, m.boon !== null)
  return (
    <>
      <TraitLegend />
      <SheetSection title="Advantages" count={m.advantages.length} />
      {m.advantages.map((row, i) => {
        const key = `a:${row.shown}:${row.trait.cost}`
        const levels = TraitGrants.grantedLevelsIn(row.trait, m.bought)
        const price = held(row.trait, m.grantedTraits) ? `from ${by}` : `${row.trait.cost} pts${levels !== null ? `, +${levels} from ${by}` : ''}`
        return <TraitRow key={`${key}/${i}`} row={row} priceLine={price + superseded(row.trait, m.traits)} roll={null} expanded={expanded.has(key)} onToggle={() => onToggle(key)} onRoll={() => {}} />
      })}
      <SheetSection title="Disadvantages" count={m.drawbacks.length} />
      {m.drawbacks.map((row, i) => {
        const key = `d:${row.shown}:${row.trait.cost}`
        const check = m.selfControl.get(row.shown) ?? null
        return (
          <TraitRow
            key={`${key}/${i}`}
            row={row}
            priceLine={drawbackPrice(row.trait, m.grantedTraits, by) + superseded(row.trait, m.traits)}
            roll={check ? `Roll ${SuccessRoll.printed(m.standing.get(check.trait)!)}` : null}
            expanded={expanded.has(key)}
            onToggle={() => onToggle(key)}
            onRoll={() => check && onControl(check)}
          />
        )
      })}
    </>
  )
}

function TraitLegend() {
  const [open, setOpen] = useState(false)
  return (
    <div style={{ padding: '0 8px' }}>
      <button className="labelMedium underline legend-toggle" onClick={() => setOpen(!open)}>
        {open ? 'Hide what the tags mean' : 'What the tags mean'}
      </button>
      {open &&
        LEGEND.map(([status, meaning]) => (
          <div key={status} className="row" style={{ alignItems: 'flex-start', padding: '3px 8px', gap: 8 }}>
            <StatusPill status={status} />
            <span className="bodySmall">{capitalised(meaning)}</span>
          </div>
        ))}
    </div>
  )
}

function StatusPill({ status }: { status: TraitReport.Applied }) {
  const [label, color, filled] =
    status === 'APPLIED' ? ['Applied', 'var(--primary)', true]
    : status === 'PARTLY' ? ['Partly applied', 'var(--primary)', false]
    : status === 'ROLL_ONLY' ? ['Roll only', 'var(--primary)', false]
    : status === 'GM' ? ['GM call', 'var(--primary)', false]
    : ['Unmatched name', 'var(--error)', false]
  return (
    <span className="status-pill labelSmall semibold" style={filled ? { background: color, color: 'var(--on-primary)' } : { border: `1px solid ${color}`, color }}>
      {label}
    </span>
  )
}

function TraitRow({ row, priceLine, roll, expanded, onToggle, onRoll }: { row: HeldTrait; priceLine: string; roll: string | null; expanded: boolean; onToggle: () => void; onRoll: () => void }) {
  const status = TraitReport.applied(row)
  return (
    <div>
      <div className="trait-row">
        <button className="row trait-head" onClick={onToggle} aria-expanded={expanded}>
          <span className="grow col">
            <span className="titleSmall">{row.shown}</span>
            <span className="labelSmall muted" style={{ paddingTop: 2 }}>{priceLine}</span>
          </span>
          <StatusPill status={status} />
          <Icon name={expanded ? 'expandLess' : 'expandMore'} label={expanded ? 'Hide details' : 'Show details'} />
        </button>
        {roll !== null && (
          <button className="assist-chip labelMedium" onClick={onRoll}>
            <Icon name="casino" size={18} />
            {roll}
          </button>
        )}
        {expanded && (
          <div className="col" style={{ paddingTop: 6, gap: 6 }}>
            <p className="bodyMedium trait-cell">{row.cell ?? "This sheet's name matches no row in the book's list, so nothing is read from it."}</p>
            {row.notes.map((note, i) => (
              <div key={i}>
                {note.printed !== row.cell && <p className="bodyMedium semibold">Not applied: {note.printed}</p>}
                <p className="bodySmall muted">{capitalised(note.reason)}.</p>
              </div>
            ))}
          </div>
        )}
      </div>
      <hr className="rule inset" />
    </div>
  )
}
