// SheetMenu.kt: the top bar's "More" menu: the roll log, reset, delete.
import { useEffect, useRef, useState } from 'react'
import { logLine, type RollLog } from '../../app/RollLog'
import { useVersion } from '../hooks'
import { BoxedIconButton, Caps, CloseButton, Dialog, FormTitle } from '../kit'

export function SheetMenu({ name, rollLog, onReset, onDelete }: {
  name: string
  rollLog: RollLog
  onReset: () => void
  onDelete: (() => void) | null
}) {
  const [menu, setMenu] = useState(false)
  const [logging, setLogging] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const anchor = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!menu) return
    const close = (e: MouseEvent) => anchor.current?.contains(e.target as Node) || setMenu(false)
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [menu])
  const item = (label: string, then: () => void) => (
    <button role="menuitem" className="menu-item bodyLarge" onClick={() => { setMenu(false); then() }}>{label}</button>
  )
  return (
    <div className="menu-anchor" ref={anchor}>
      <BoxedIconButton icon="moreVert" label="More" onClick={() => setMenu(!menu)} />
      {menu && (
        <div className="menu" role="menu">
          {item('Roll log', () => setLogging(true))}
          {item('Reset to sheet', () => setConfirming(true))}
          {onDelete !== null && item('Delete character', () => setDeleting(true))}
        </div>
      )}
      {logging && <RollLogDialog name={name} rollLog={rollLog} onDismiss={() => setLogging(false)} />}
      {confirming && (
        <Confirm
          title={`Reset ${name}?`}
          body={
            onDelete !== null
              ? `Back to ${name} as built: potions, wounds, corruption, study, boons, traits gained in play, ` +
                'declared states and the roll log are cleared. Purchases are kept. This cannot be undone.'
              : 'Back to the printed sheet: potions, purchases, wounds, corruption, study, boons, ' +
                'traits gained in play, declared states and the roll log are cleared. This cannot be undone.'
          }
          action="Reset"
          onDismiss={() => setConfirming(false)}
          onConfirm={onReset}
        />
      )}
      {deleting && onDelete !== null && (
        <Confirm
          title={`Delete ${name}?`}
          body="The character, everything it has done, and its roll log go. This cannot be undone."
          action="Delete"
          onDismiss={() => setDeleting(false)}
          onConfirm={onDelete}
        />
      )}
    </div>
  )
}

function RollLogDialog({ name, rollLog, onDismiss }: { name: string; rollLog: RollLog; onDismiss: () => void }) {
  useVersion(rollLog)
  const throws = rollLog.of(name)
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Roll log</FormTitle>}>
      {throws.length === 0 ? (
        <p>No rolls yet.</p>
      ) : (
        <div className="scroll" style={{ maxHeight: 480 }}>
          <p className="bodySmall muted">Dice as thrown. A table's own rule can change the verdict after.</p>
          {throws.map((row, i) => (
            <div key={i} className="row bodyLarge" style={{ padding: '6px 0' }}>
              <span className="grow">{row.label}</span>
              <span>{logLine(row)}</span>
            </div>
          ))}
        </div>
      )}
    </Dialog>
  )
}

function Confirm({ title, body, action, onDismiss, onConfirm }: { title: string; body: string; action: string; onDismiss: () => void; onConfirm: () => void }) {
  return (
    <Dialog
      onDismiss={onDismiss}
      title={<FormTitle form={null}>{title}</FormTitle>}
      dismiss={<CloseButton onClick={onDismiss} label="Cancel" />}
      confirm={
        <button className="danger-button" onClick={() => { onDismiss(); onConfirm() }}>
          <Caps text={action} className="labelLarge mono" color="var(--on-error)" />
        </button>
      }
    >
      <p>{body}</p>
    </Dialog>
  )
}
