// The shared Compose kit, one component each: CaseText.kt (Caps, InkAction,
// InkButton, CloseButton, BracketAction, FormTitle), RuleNote.kt, CaseChip.kt,
// RollCards.kt (TargetCard, ResultCard, VerdictCard, Stamp, Tag),
// RollFooter.kt, StepperParts.kt, AttackParts.kt's OptionStepper and
// SheetHeader.kt's BoxedIconButton. Each renders; none computes a rule.
import { createContext, useContext, useEffect, useId, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Outcome, RollResult } from '../rules/model'
import type { SuccessDegree } from '../rules/DegreeOfSuccess'
import * as SuccessRoll from '../rules/SuccessRoll'
import * as SkillRoll from '../rules/SkillRoll'
import type { LedgerLine, SkillRollOutcome, SkillTarget } from '../rules/SkillRoll'
import { Icon, type IconName } from './icons'
import './kit.css'

export function Caps({ text, className = 'labelSmall', color, spoken, style }: { text: string; className?: string; color?: string; spoken?: string; style?: CSSProperties }) {
  return (
    <span className={`${className} caps`} style={{ color: color ?? 'var(--on-surface-variant)', ...style }} aria-label={spoken}>
      {text}
    </span>
  )
}

export function InkAction({ label, onClick, disabled, icon, className }: { label: string; onClick: () => void; disabled?: boolean; icon?: IconName; className?: string }) {
  return (
    <button className={`ink-action${className ? ` ${className}` : ''}`} onClick={onClick} disabled={disabled}>
      {icon && <Icon name={icon} size={18} />}
      <span className="labelLarge mono caps">{label}</span>
    </button>
  )
}

export function InkButton({ label, onClick, disabled, icon }: { label: string; onClick: () => void; disabled?: boolean; icon?: IconName }) {
  return (
    <button className="ink-button" onClick={onClick} disabled={disabled}>
      {icon && <Icon name={icon} size={18} />}
      <span className="labelLarge mono caps semibold">{label}</span>
    </button>
  )
}

export function CloseButton({ onClick, label = 'Close', color = 'var(--secondary)' }: { onClick: () => void; label?: string; color?: string }) {
  return (
    <button className="close-button" onClick={onClick} style={{ color }}>
      <span className="labelLarge mono semibold caps">{label}</span>
    </button>
  )
}

export function BracketAction({ label, onClick, color = 'var(--on-surface)', underlined, disabled }: { label: string; onClick: () => void; color?: string; underlined?: boolean; disabled?: boolean }) {
  return (
    <button className="bracket-action bodyLarge mono" onClick={onClick} disabled={disabled} aria-label={label} style={{ color, textDecoration: underlined ? 'underline' : undefined }}>
      [ {label} ]
    </button>
  )
}

/** `LocalFileLabel`: the open file's "File Nº 07", printed on every form. */
export const FileLabel = createContext<string | null>(null)

export function FormTitle({ form, children }: { form: string | null; children: ReactNode }) {
  const file = useContext(FileLabel)
  return (
    <div className="form-title">
      {form !== null && (
        <div className="form-codes labelSmall" aria-hidden="true">
          <span>{form}</span>
          {file !== null && <span>{file.toUpperCase()}</span>}
        </div>
      )}
      <div className="headlineSmall">{children}</div>
      <hr className="rule thick" style={{ marginTop: 8 }} />
    </div>
  )
}

/** Where every dialog draws: inside the phone's screen, never over the frame. */
export const DialogHost = createContext<HTMLElement | null>(null)

/**
 * Material 3's AlertDialog: scrim, an 8 dp card at most 560 wide, title, a
 * body that scrolls, buttons at the end. Escape and the scrim dismiss it.
 */
export function Dialog({ title, children, confirm, dismiss, onDismiss, wide }: {
  title: ReactNode
  children?: ReactNode
  confirm?: ReactNode
  dismiss?: ReactNode
  onDismiss: () => void
  wide?: boolean
}) {
  const host = useContext(DialogHost)
  const id = useId()
  useEffect(() => {
    const key = (e: KeyboardEvent) => e.key === 'Escape' && onDismiss()
    window.addEventListener('keydown', key)
    return () => window.removeEventListener('keydown', key)
  }, [onDismiss])
  const card = (
    <div className="scrim" onClick={onDismiss}>
      <div className={`dialog${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby={id} onClick={(e) => e.stopPropagation()}>
        <div className="dialog-title" id={id}>{title}</div>
        {children !== undefined && <div className="dialog-body bodyMedium">{children}</div>}
        <div className="dialog-buttons">
          {dismiss}
          {confirm}
        </div>
      </div>
    </div>
  )
  return host ? createPortal(card, host) : card
}

export function RuleNote({ title, children }: { title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="rule-note">
      <button className="rule-note-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className="labelLarge mono grow">{title}</span>
        <Icon name={open ? 'expandLess' : 'expandMore'} />
      </button>
      {open && <div className="rule-note-body">{children}</div>}
    </div>
  )
}

export function CaseChip({ selected, onClick, disabled, children }: { selected: boolean; onClick: () => void; disabled?: boolean; children: ReactNode }) {
  return (
    <button className={`case-chip labelLarge mono${selected ? ' on' : ''}`} role="checkbox" aria-checked={selected} onClick={onClick} disabled={disabled}>
      {selected && <Icon name="check" size={16} />}
      <span>{children}</span>
    </button>
  )
}

export function Chips({ children }: { children: ReactNode }) {
  return <div className="chips">{children}</div>
}

export function Stamp({ text, color, small, caps = true }: { text: string; color: string; small?: boolean; caps?: boolean }) {
  return (
    <span className={`stamp ${small ? 'labelSmall semibold' : 'titleSmall mono'}${caps ? ' caps' : ''}`} style={{ color, borderColor: color }}>
      {text}
    </span>
  )
}

export function Tag({ text, color = 'var(--on-surface-variant)' }: { text: string; color?: string }) {
  return <span className="tag labelLarge mono" style={{ color }}>{text}</span>
}

export function ChipTag({ text, ink }: { text: string; ink?: boolean }) {
  return <span className={`chip-tag labelMedium mono${ink ? ' ink' : ''}`}>{text}</span>
}

export const FORBIDDEN_ROLL = 'Forbidden — this roll cannot be attempted.'

/** RollDialog.kt `label`. */
export function outcomeLabel(outcome: Outcome, degree: SuccessDegree | null = null): string {
  switch (outcome) {
    case 'CRITICAL_SUCCESS':
      return 'Critical success'
    case 'SUCCESS':
      return degree === 'EXCELLENT' ? 'Excellent success' : degree === 'GOOD' ? 'Good success' : 'Success'
    case 'FAILURE':
      return 'Failure'
    case 'CRITICAL_FAILURE':
      return 'Critical failure'
  }
}
export const outcomeColor = (o: Outcome) => (o === 'FAILURE' || o === 'CRITICAL_FAILURE' ? 'var(--error)' : 'var(--primary)')

export const signed = (n: number) => (n >= 0 ? `+${n}` : `${n}`)

export function TargetCard({ target, baseTarget, forbidden }: { target: number; baseTarget: number; forbidden?: boolean }) {
  return (
    <div className="target-card">
      <div className="row target-head">
        <Caps text="Target" />
        {!forbidden && <span className="fit-pill labelLarge mono">{SuccessRoll.printed(target)}</span>}
      </div>
      <div className="target-figures">
        {forbidden ? (
          <span className="err">{FORBIDDEN_ROLL}</span>
        ) : (
          <>
            <span className="boxed-target headlineMedium mono semibold">{target}</span>
            <span className="struck titleLarge mono" style={{ visibility: target !== baseTarget ? 'visible' : 'hidden' }}>
              {baseTarget}
            </span>
          </>
        )}
      </div>
    </div>
  )
}
export const StandingCard = ({ standing }: { standing: SkillTarget }) => (
  <TargetCard target={standing.target} baseTarget={standing.baseTarget} forbidden={standing.forbidden} />
)

function DiceLine({ roll }: { roll: RollResult }) {
  return (
    <div className="dice-line">
      <span className="titleMedium mono">3d6: {roll.total} vs {roll.target}</span>
      <Tag text={`margin ${roll.margin}`} />
    </div>
  )
}

export function StampSheet({ children }: { children: ReactNode }) {
  return <div className="stamp-sheet">{children}</div>
}

export function ResultCard({ out, degree }: { out: SkillRollOutcome; degree?: SuccessDegree | null }) {
  const roll = out.roll
  const shown = roll ? SkillRoll.effectiveOutcome(out) ?? roll.outcome : null
  const deg = degree === undefined ? SkillRoll.degree(out) : degree
  return (
    <>
      <StampSheet>
        {roll === null || shown === null ? (
          <span className="err">{FORBIDDEN_ROLL}</span>
        ) : (
          <>
            <Stamp text={outcomeLabel(shown, deg)} color={outcomeColor(shown)} />
            <DiceLine roll={roll} />
          </>
        )}
      </StampSheet>
      <div style={{ paddingTop: 8 }}>
        {out.target !== out.baseTarget && (
          <p className="labelMedium muted">base {out.baseTarget}, modified to {out.target}</p>
        )}
        <LedgerLines lines={SkillRoll.lines(out)} requiresCritical={out.requiresCritical} />
      </div>
    </>
  )
}

export function VerdictCard({ verdict, passed, roll, children }: { verdict: string; passed: boolean; roll: RollResult; children?: ReactNode }) {
  return (
    <StampSheet>
      <Stamp text={verdict} color={passed ? 'var(--primary)' : 'var(--error)'} />
      <DiceLine roll={roll} />
      {children}
    </StampSheet>
  )
}

export function LedgerLines({ lines, requiresCritical }: { lines: LedgerLine[]; requiresCritical: boolean }) {
  return (
    <>
      {lines.map((line, i) => (
        <div className="ledger-line labelMedium" key={i}>
          <span>{line.label}</span>
          <span className="leader" />
          <span>{signed(line.delta)}</span>
        </div>
      ))}
      {requiresCritical && <p className="bodySmall err">Only a critical success counts.</p>}
    </>
  )
}

export function SectionLabel({ text }: { text: string }) {
  return <div style={{ padding: '16px 0 6px' }}><Caps text={text} /></div>
}

export function JoinedStepper({ value, onMinus, onPlus, minusDisabled, plusDisabled, valueColor, minusLabel, plusLabel }: {
  value: string | null
  onMinus: () => void
  onPlus: () => void
  minusDisabled?: boolean
  plusDisabled?: boolean
  valueColor?: string
  minusLabel?: string
  plusLabel?: string
}) {
  return (
    <span className="joined-stepper">
      <button className="titleLarge" onClick={onMinus} disabled={minusDisabled} aria-label={minusLabel}>−</button>
      {value !== null && <span className="titleLarge mono value" style={{ color: valueColor }}>{value}</span>}
      <button className="titleLarge" onClick={onPlus} disabled={plusDisabled} aria-label={plusLabel}>+</button>
    </span>
  )
}

export function OptionStepper({ label, value, note, shown = `${value}`, onStep }: { label: string; value: number; note: string; shown?: string; onStep: (d: number) => void }) {
  return (
    <div className="option-stepper">
      <div className="grow">
        <div className="titleSmall">{label} {shown}</div>
        <div className="bodySmall muted">{note}</div>
      </div>
      <JoinedStepper value={shown} onMinus={() => onStep(-1)} onPlus={() => onStep(1)} minusLabel={`One less ${label}`} plusLabel={`One more ${label}`} />
    </div>
  )
}

export function FooterBand({ children }: { children: ReactNode }) {
  return (
    <div className="footer-band">
      <hr className="rule thick" />
      <div className="footer-inner">{children}</div>
    </div>
  )
}

export function BoxedTarget({ target }: { target: number | null }) {
  return (
    <span className="boxed-target headlineMedium mono semibold" style={{ visibility: target === null ? 'hidden' : 'visible' }} aria-hidden={target === null}>
      {target ?? 0}
    </span>
  )
}

export function RollButton({ label, onClick, disabled, icon = 'casino' }: { label: string; onClick: () => void; disabled?: boolean; icon?: IconName }) {
  return <InkButton label={label} onClick={onClick} disabled={disabled} icon={icon} />
}

/** RollFooter.kt `FooterRow`: target and caption, then the button; stacks in a narrow dialog. */
export function FooterRow({ target, button, boxed = true, children }: { target: number | null; button: ReactNode; boxed?: boolean; children: ReactNode }) {
  return (
    <div className="footer-row">
      <div className="row footer-target">
        {boxed && <BoxedTarget target={target} />}
        <div className="grow footer-caption" style={{ paddingLeft: boxed ? 10 : 0 }}>{children}</div>
      </div>
      {button}
    </div>
  )
}

export function RollFooter({ lines, caption, target, disabled, label = 'Roll', icon = 'casino', onRoll }: {
  lines: string[]
  caption: string
  target: number | null
  disabled?: boolean
  label?: string
  icon?: IconName
  onRoll: () => void
}) {
  return (
    <FooterBand>
      {(lines.length ? lines : ['']).map((it, i) => (
        <p className="labelMedium muted" key={i}>{it || ' '}</p>
      ))}
      <FooterRow target={target} button={<RollButton label={label} onClick={onRoll} disabled={disabled} icon={icon} />}>
        <span className="labelLarge mono">{caption}</span>
      </FooterRow>
    </FooterBand>
  )
}

export function BoxedIconButton({ icon, label, onClick }: { icon: IconName; label: string; onClick: () => void }) {
  return (
    <button className="boxed-icon" onClick={onClick} aria-label={label} title={label}>
      <Icon name={icon} size={20} />
    </button>
  )
}

export function LedgerCard({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <div className="ledger-card">
      <div className="row" style={{ minHeight: 32 }}>
        <span className="grow"><Caps text={title} className="labelLarge mono" color="var(--on-surface)" style={{ fontWeight: 700 }} /></span>
        {action}
      </div>
      <hr className="rule" style={{ margin: '8px 0 16px' }} />
      {children}
    </div>
  )
}

export function StepperCard({ title, minus, plus, value, onMinus, onPlus, disabled, valueColor, children }: {
  title: string
  minus: string
  plus: string
  value: string
  onMinus: () => void
  onPlus: () => void
  disabled?: boolean
  valueColor?: string
  children?: ReactNode
}) {
  return (
    <LedgerCard title={title}>
      <div className="stepper-card">
        <button className="titleMedium mono" onClick={onMinus} disabled={disabled}>{minus}</button>
        <span className="headlineMedium mono value" style={{ color: valueColor }}>{value}</span>
        <button className="titleMedium mono" onClick={onPlus} disabled={disabled}>{plus}</button>
      </div>
      {children}
    </LedgerCard>
  )
}

export function LedgerHeadRow({ heading, action }: { heading: string; action?: ReactNode }) {
  return (
    <div className="ledger-head">
      <div className="row" style={{ minHeight: 48 }}>
        <h2 className="grow"><Caps text={`§ ${heading}`} spoken={heading} className="labelLarge mono" color="var(--on-surface)" style={{ fontWeight: 700 }} /></h2>
        {action}
      </div>
      <hr className="rule" style={{ borderTopWidth: 1.5, borderTopColor: 'var(--outline)' }} />
    </div>
  )
}

/** A dialog body that keeps one height, so a verdict moves no control (6ia). */
export function SteadyBody({ children }: { children: ReactNode }) {
  return <div className="steady-body">{children}</div>
}
