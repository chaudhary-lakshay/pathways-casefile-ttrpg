// Web only: what Saves could not do quietly, and an unreadable save kept aside.
import type { Saves } from '../app/Saves'
import { BracketAction } from './kit'

function download(text: string, name: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function Notices({ saves }: { saves: Saves }) {
  const unreadable = saves.unreadable
  if (saves.notice === null && unreadable === null) return null
  return (
    <div className="notices" role="status">
      {saves.notice !== null && (
        <div className="notice">
          <p className="bodySmall grow">{saves.notice}</p>
          <BracketAction label="OK" onClick={() => saves.dismissNotice()} />
        </div>
      )}
      {unreadable !== null && (
        <div className="notice">
          <p className="bodySmall grow">A save in this browser could not be read. It is kept aside, untouched.</p>
          <BracketAction label="Export it" onClick={() => download(unreadable, 'sessions.unreadable.json')} />
          <BracketAction label="Discard" color="var(--error)" onClick={() => saves.discardUnreadable()} />
        </div>
      )}
    </div>
  )
}
