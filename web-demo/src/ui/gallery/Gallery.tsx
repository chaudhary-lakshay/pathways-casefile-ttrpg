// PremadeGallery.kt + GalleryRows.kt: the case index.
import { useMemo, useState } from 'react'
import {
  fileDigits, fileNumber, galleryNote, isStrong, nextFileNumber, rosterOf, rowFor, ROSTERS, walkOf, walkStamp, type PlayedRow, type Roster,
} from '../../app/Gallery'
import { isTemplate, takenNames } from '../../app/session/Characters'
import { sessionIsLost, type SavedSession } from '../../app/session/SessionStore'
import type { BeyonderState } from '../../rules/model'
import type { PremadeSheet } from '../../rules/Premades'
import { Icon } from '../icons'
import { BoxedIconButton, Caps } from '../kit'
import { NewCharacterDialog } from './NewCharacterDialog'
import { SavesPanel } from './SavesPanel'
import type { Saves } from '../../app/Saves'
import './gallery.css'

export function Gallery({ sheets, saves, bases, onCreate, onSelect, onGlossary, onCredits }: {
  sheets: PremadeSheet[]
  saves: Saves
  bases: PremadeSheet[]
  onCreate: (name: string, base: PremadeSheet) => void
  onSelect: (sheet: PremadeSheet) => void
  onGlossary: () => void
  onCredits: () => void
}) {
  const saved = saves.saved
  const [roster, setRoster] = useState<Roster>('ALL')
  const [creating, setCreating] = useState(false)
  const people = sheets.filter((it) => !isTemplate(it))
  const templates = sheets.filter(isTemplate)
  const shown = rosterOf(roster, people, saved)
  return (
    <div className="scaffold">
      <div className="scroll gallery">
        <div className="gallery-head">
          <div className="row labelSmall caps muted" aria-hidden="true">
            <span className="grow">Midnight Poem · Case Index</span>
            <span>Vol. III</span>
          </div>
          <hr className="rule" style={{ marginTop: 6, borderTopWidth: 1.5, borderTopColor: 'var(--outline)' }} />
          <div className="row" style={{ padding: '8px 0 16px' }}>
            <h1 className="displaySmall grow">Characters</h1>
            <BoxedIconButton icon="info" label="Credits" onClick={onCredits} />
            <BoxedIconButton icon="menuBook" label="Glossary" onClick={onGlossary} />
          </div>
        </div>
        <div className="gallery-sticky">
          <div className="roster-tabs" role="tablist">
            {ROSTERS.map(([option, label]) => {
              const open = option === roster
              const count = rosterOf(option, people, saved).length
              return (
                <button key={option} role="tab" aria-selected={open} className={`roster-tab labelMedium mono${open ? ' open' : ''}`} aria-label={`${label}, ${count}`} onClick={() => setRoster(option)}>
                  <span className="caps">{label}</span>
                  <span className={open ? 'red' : ''}>&nbsp;{count}</span>
                </button>
              )
            })}
          </div>
          <div className="table-head table-sides" aria-hidden="true">
            <div className="row labelSmall caps muted">
              <span className="no">No.</span>
              <span className="grow">Subject</span>
              {['ST', 'DX', 'IQ', 'HT'].map((a) => <span className="stat" key={a}>{a}</span>)}
            </div>
            <hr className="rule thick" />
          </div>
        </div>
        {shown.map((sheet) => (
          <Row
            key={sheet.name}
            file={fileNumber(sheet, sheets)}
            sheet={sheet}
            entry={saved.get(sheet.name) ?? null}
            walk={walkOf(sheet, saved)}
            onClick={() => onSelect(sheet)}
          />
        ))}
        {templates.map((sheet) => (
          <button key={sheet.name} className="blank-sheet dashed" onClick={() => onSelect(sheet)}>
            <Icon name="add" size={20} />
            <span className="labelLarge mono caps">Start from a blank sheet</span>
          </button>
        ))}
        <SavesPanel saves={saves} />
      </div>
      <div className="bottom-bar">
        <button className="open-file" onClick={() => setCreating(true)}>
          <Icon name="createNewFolder" size={20} />
          <span className="labelLarge mono caps">Open new file</span>
        </button>
      </div>
      {creating && (
        <NewCharacterDialog
          bases={bases}
          taken={takenNames(sheets, saved)}
          file={nextFileNumber(sheets)}
          onDismiss={() => setCreating(false)}
          onCreate={(name, base) => {
            setCreating(false)
            onCreate(name, base)
          }}
        />
      )}
    </div>
  )
}

function Row({ file, sheet, entry, walk, onClick }: { file: number | null; sheet: PremadeSheet; entry: SavedSession | null; walk: BeyonderState | null; onClick: () => void }) {
  const numbers: PlayedRow = useMemo(() => rowFor(sheet, entry), [sheet, entry])
  const lost = entry !== null && sessionIsLost(entry)
  const a = numbers.attributes
  return (
    <button className={`gallery-row table-sides${lost ? ' lost' : ''}`} onClick={onClick}>
      <div className="row" style={{ alignItems: 'flex-start', padding: '14px 12px' }}>
        <span className="no labelMedium" style={{ paddingTop: 4, color: lost ? 'var(--secondary)' : 'var(--on-surface-variant)' }} aria-hidden="true">
          {file !== null ? fileDigits(file) : ''}
        </span>
        <span className="grow col" style={{ gap: 6, alignItems: 'flex-start' }}>
          <span className="titleLarge serif">{sheet.name}</span>
          <WalkStamp walk={walk} lost={lost} />
          <span className="bodySmall muted" aria-label={galleryNote(numbers, true)}>{galleryNote(numbers, false)}</span>
        </span>
        {([['ST', a.st], ['DX', a.dx], ['IQ', a.iq], ['HT', a.ht]] as const).map(([label, value]) => (
          <span key={label} className="stat titleSmall mono" style={{ paddingTop: 4, fontWeight: isStrong(value) ? 600 : 400 }} aria-label={`${label} ${value}`}>
            {value}
          </span>
        ))}
      </div>
      <hr className="rule" />
    </button>
  )
}

function WalkStamp({ walk, lost }: { walk: BeyonderState | null; lost: boolean }) {
  const text = walkStamp(walk)
  const red = 'var(--secondary)'
  return (
    <span
      className="walk-stamp"
      style={{ borderColor: lost || walk ? red : 'var(--outline-variant)', background: lost ? red : undefined }}
    >
      <Caps
        text={text}
        className="labelSmall semibold"
        color={lost ? 'var(--on-secondary)' : walk ? red : 'var(--on-surface-variant)'}
        spoken={lost ? `${text}, lost` : text}
      />
    </span>
  )
}
