// RitualParts.kt: the footer the Ritual, Boon and Divine dialogs share, and
// the ritual's result card.
import * as RitualResolution from '../../rules/RitualResolution'
import type { RitualOutcome } from '../../rules/RitualResolution'
import * as SuccessRoll from '../../rules/SuccessRoll'
import type { IconName } from '../icons'
import { FooterBand, FooterRow, RollButton } from '../kit'

export function RitualFooter({ terms, target, cost, refusal, targetText, label = 'Cast', icon = 'autoFixHigh', onCast }: {
  terms: string[]
  target: number | null
  cost: string
  refusal: string | null
  targetText?: string
  label?: string
  icon?: IconName
  onCast: () => void
}) {
  return (
    <FooterBand>
      {target !== null && (
        <>
          <p className="labelMedium muted">{terms.join(' · ')}</p>
          <p className="labelMedium muted">{cost}</p>
        </>
      )}
      {refusal !== null && <p className="bodySmall err" style={{ paddingTop: 4 }}>{refusal}</p>}
      <FooterRow target={target} button={<RollButton label={label} onClick={onCast} disabled={target === null || refusal !== null} icon={icon} />}>
        <span className="labelLarge mono">{target !== null ? targetText ?? SuccessRoll.printed(target) : ''}</span>
      </FooterRow>
    </FooterBand>
  )
}

export function RitualResultCard({ result }: { result: RitualOutcome }) {
  const held = RitualResolution.succeeded(result)
  const accent = held ? 'var(--primary)' : 'var(--error)'
  return (
    <div className="fright-card" style={{ borderColor: accent }}>
      <p className="headlineMedium" style={{ color: accent }}>{held ? 'The ritual holds' : 'The ritual fails'}</p>
      <p className="titleMedium mono">3d6: {result.roll.total} vs {result.target}</p>
      {result.success !== null && <p style={{ paddingTop: 8 }}>{result.success.printed} — {result.success.outcome}</p>}
      <RitualFailureLines result={result} />
    </div>
  )
}

export function RitualFailureLines({ result }: { result: RitualOutcome }) {
  return (
    <>
      {result.consequence !== null && <p className="err" style={{ paddingTop: 8 }}>{result.consequence.printed} — {result.consequence.consequence}</p>}
      {result.critical !== null && (
        <>
          <p className="err" style={{ paddingTop: 8 }}>Critical Failure Table {result.criticalRoll} ({result.critical.printed}): {result.critical.outcome}</p>
          {result.critical.corGained !== null && <p className="bodySmall">+{result.critical.corGained} CoR on closing.</p>}
        </>
      )}
    </>
  )
}
