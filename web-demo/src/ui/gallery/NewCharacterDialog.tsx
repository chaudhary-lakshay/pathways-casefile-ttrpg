// NewCharacterDialog.kt: FORM 23-B, a name and a starting sheet.
import { useId, useState } from 'react'
import { blankHint, createRefusal, fileDigits, fileNumber, templateChoices } from '../../app/Gallery'
import { isTemplate, newCharacterRefusal } from '../../app/session/Characters'
import { trim } from '../../rules/kt'
import type { PremadeSheet } from '../../rules/Premades'
import { Icon } from '../icons'
import { Caps, CloseButton, Dialog, FileLabel, FormTitle, InkButton } from '../kit'

export const FORM_INTAKE = 'FORM 23-B · INTAKE'

export function NewCharacterDialog({ bases, taken, file, onDismiss, onCreate }: {
  bases: PremadeSheet[]
  taken: Set<string>
  file: number
  onDismiss: () => void
  onCreate: (name: string, base: PremadeSheet) => void
}) {
  const blank = bases.find(isTemplate)!
  const [name, setName] = useState('')
  const [fromTemplate, setFromTemplate] = useState(false)
  const [picked, setPicked] = useState<PremadeSheet | null>(null)
  const [query, setQuery] = useState('')
  const base = fromTemplate ? picked : blank
  const refusal = createRefusal(name, taken, base)
  const nameRefusal = name !== '' ? newCharacterRefusal(name, taken) : null
  const choices = templateChoices(bases, query)
  const nameId = useId()
  return (
    <Dialog
      onDismiss={onDismiss}
      title={
        <div style={{ position: 'relative' }}>
          <FileLabel.Provider value={`File Nº ${fileDigits(file)}`}>
            <FormTitle form={FORM_INTAKE}>New character</FormTitle>
          </FileLabel.Provider>
          <span className="paperclip"><Icon name="attachFile" /></span>
        </div>
      }
      dismiss={<CloseButton onClick={onDismiss} label="Cancel" />}
      confirm={<InkButton label="Create" disabled={refusal !== null} onClick={() => base && onCreate(trim(name), base)} />}
    >
      <label htmlFor={nameId}><Caps text="Subject name" /></label>
      <input
        id={nameId}
        className={`text-field bodyLarge${nameRefusal ? ' error' : ''}`}
        value={name}
        autoComplete="off"
        onChange={(e) => setName(e.target.value)}
      />
      <p className="bodySmall err" style={{ minHeight: 20, padding: '4px 16px 0' }}>{nameRefusal ?? ''}</p>
      <div style={{ padding: '4px 0 8px' }}><Caps text="Starts as" /></div>
      <div className="segments" role="radiogroup">
        <Segment label="Blank sheet" icon="description" open={!fromTemplate} onClick={() => setFromTemplate(false)} />
        <Segment label="Template" icon="contentCopy" open={fromTemplate} onClick={() => setFromTemplate(true)} />
      </div>
      <div className="template-list">
        <div className="row template-search">
          <Icon name="search" size={18} />
          <input className="bodyMedium grow" placeholder="Filter templates…" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Filter templates" />
        </div>
        <hr className="rule" />
        <div className="template-scroll" role="radiogroup" style={{ opacity: fromTemplate ? 1 : 0.55 }}>
          {choices.length === 0 && <p className="bodySmall" style={{ padding: 12 }}>No template matches.</p>}
          {choices.map((option) => {
            const selected = fromTemplate && option === picked
            const n = fileNumber(option, bases)
            return (
              <button key={option.name} className="template-row" role="radio" aria-checked={selected} onClick={() => { setPicked(option); setFromTemplate(true) }}>
                <span className="labelSmall muted" style={{ width: 28 }} aria-hidden="true">{n !== null ? fileDigits(n) : ''}</span>
                <span className={`radio${selected ? ' on' : ''}`} />
                <span className="bodyMedium" style={{ paddingLeft: 8 }}>{option.name}</span>
              </button>
            )
          })}
        </div>
      </div>
      <div className="row" style={{ alignItems: 'flex-start', paddingTop: 8 }}>
        <Icon name="info" size={14} />
        <span className="labelMedium muted" style={{ paddingLeft: 6, minHeight: 34 }}>
          {!fromTemplate ? blankHint(blank) : picked === null ? 'Pick a template.' : `Starts as ${picked.name}'s sheet as printed.`}
        </span>
      </div>
    </Dialog>
  )
}

export function Segment({ label, icon, open, onClick }: { label: string; icon?: 'description' | 'contentCopy'; open: boolean; onClick: () => void }) {
  return (
    <button className={`segment${open ? ' open' : ''}`} role="radio" aria-checked={open} onClick={onClick}>
      {icon && <Icon name={icon} size={16} />}
      <span className="labelLarge mono caps">{label}</span>
    </button>
  )
}
