// Web only: the phone keeps sessions.json in app storage; a browser keeps it
// in localStorage, so the file goes in and out through here. The text is
// Android's own, so a save moves between the two either way.
import { useRef, useState } from 'react'
import type { Saves } from '../../app/Saves'
import { BracketAction, Caps, CloseButton, Dialog, FormTitle, InkButton } from '../kit'

function download(text: string, name: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function SavesPanel({ saves }: { saves: Saves }) {
  const input = useRef<HTMLInputElement>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  return (
    <div className="saves-panel">
      <Caps text="Saves in this browser" />
      <p className="bodySmall muted">The same file as the Android app's sessions.json, so a save moves either way.</p>
      <div className="row wrap">
        <BracketAction label="Export saves" onClick={() => download(saves.exportText(), 'sessions.json')} />
        <BracketAction label="Import saves" onClick={() => input.current?.click()} />
      </div>
      <input
        ref={input}
        type="file"
        accept=".json,application/json"
        hidden
        onChange={async (e) => {
          const file = e.target.files?.[0]
          e.target.value = ''
          if (file) setPending(await file.text())
        }}
      />
      {pending !== null && (
        <Dialog
          onDismiss={() => setPending(null)}
          title={<FormTitle form={null}>Replace every save?</FormTitle>}
          dismiss={<CloseButton onClick={() => setPending(null)} label="Cancel" />}
          confirm={
            <InkButton
              label="Replace"
              onClick={() => {
                try {
                  saves.importText(pending)
                  setError(null)
                } catch (failure) {
                  setError(`Not imported: ${(failure as Error).message}`)
                }
                setPending(null)
              }}
            />
          }
        >
          Every character in this browser becomes the file's, as copying sessions.json onto a phone would. Export first to keep what is here.
        </Dialog>
      )}
      {error && <p className="bodySmall err" role="alert">{error}</p>}
    </div>
  )
}
