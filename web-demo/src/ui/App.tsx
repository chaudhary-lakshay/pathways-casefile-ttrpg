// MainActivity.kt App(): the saves, one session per open character, the roll
// log and scenes, and which screen shows. Android keeps the screen in
// `remember`; this keeps it in the URL hash, so a refresh lands where it was.
import { useEffect, useMemo, useState } from 'react'
import { fileLabel, fileNumber } from '../app/Gallery'
import { RollLog, Scenes, type CharacterDice } from '../app/RollLog'
import { Saves } from '../app/Saves'
import { BeyonderSession } from '../app/session/BeyonderSession'
import { BrowserStore, type StorageLike } from '../app/session/BrowserStore'
import { cleared, isTemplate, ownSheets } from '../app/session/Characters'
import { SavedSession } from '../app/session/SessionStore'
import * as Premades from '../rules/Premades'
import { browserDice } from '../rules/RandomSource'
import { Device, FRAMED } from './Device'
import { Gallery } from './gallery/Gallery'
import { useVersion } from './hooks'
import { DialogHost, FileLabel } from './kit'
import { Notices } from './Notices'
import { back, go, replace, useRoute } from './route'
import { SheetScreen } from './sheet/SheetScreen'
import { PathwayScreen } from './pathway/PathwayScreen'
import { BuilderScreen } from './builder/BuilderScreen'
import { GlossaryScreen } from './GlossaryScreen'
import { CreditsScreen } from './CreditsScreen'

/** localStorage, or a map in memory where the browser refuses it. */
function storage(): StorageLike {
  try {
    const s = window.localStorage
    s.getItem('pathways/probe')
    return s
  } catch {
    const items = new Map<string, string>()
    return { getItem: (k) => items.get(k) ?? null, setItem: (k, v) => void items.set(k, v), removeItem: (k) => void items.delete(k) }
  }
}

function useFramed() {
  const [framed, setFramed] = useState(() => matchMedia(FRAMED).matches)
  useEffect(() => {
    const q = matchMedia(FRAMED)
    const on = () => setFramed(q.matches)
    q.addEventListener('change', on)
    return () => q.removeEventListener('change', on)
  }, [])
  return framed
}

export function App() {
  const framed = useFramed()
  const [host, setHost] = useState<HTMLElement | null>(null)
  const content = (
    <div className="screen-host" ref={setHost}>
      <DialogHost.Provider value={host}>
        <div className="paper">
          <div className="column">
            <Screens />
          </div>
        </div>
      </DialogHost.Provider>
    </div>
  )
  return framed ? <Device>{content}</Device> : <div className="stage"><div className="phone"><div className="screen">{content}</div></div></div>
}

function Screens() {
  const route = useRoute()
  const saves = useMemo(() => new Saves(new BrowserStore(storage())), [])
  useVersion(saves)
  const premades = useMemo(() => Premades.all(), [])
  const rollLog = useMemo(() => new RollLog(browserDice), [])
  const scenes = useMemo(() => new Scenes(), [])
  const [resets, setResets] = useState(0)

  useEffect(() => {
    const on = (e: StorageEvent) => {
      if (e.key === null || e.key.startsWith('pathways/')) saves.reload()
    }
    window.addEventListener('storage', on)
    return () => window.removeEventListener('storage', on)
  }, [saves])

  const saved = saves.saved
  const sheets = [...premades, ...ownSheets(premades, saved)]
  const name = route.kind === 'character' ? route.name : null
  const sheet = name !== null ? sheets.find((it) => it.name === name) ?? null : null

  useEffect(() => {
    if (route.kind === 'character' && sheet === null) replace({ kind: 'gallery' })
  }, [route, sheet])

  const session = useMemo(
    () => {
      if (sheet === null) return null
      // A repair-on-load saves from inside the constructor, mid-render: quietly.
      let building = true
      const made = new BeyonderSession(saved.get(sheet.name) ?? SavedSession(), sheet, (updated) => saves.put(sheet.name, updated, building))
      building = false
      return made
    },
    // A session lives for (character, resets), as `remember(selected, resets)`;
    // a reload from storage replaces it too.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sheet?.name, resets, saves.generation],
  )
  const scene = useMemo(() => (sheet === null ? null : scenes.of(sheet.name)), [sheet?.name, resets, scenes])

  const top = (
    <Notices saves={saves} />
  )

  if (route.kind === 'glossary') return <>{top}<GlossaryScreen onBack={() => back(route)} /></>
  if (route.kind === 'credits') return <>{top}<CreditsScreen onBack={() => back(route)} /></>
  if (route.kind !== 'character' || sheet === null || session === null || scene === null) {
    return (
      <>
        {top}
        <Gallery
          sheets={sheets}
          saves={saves}
          bases={[...premades].sort((a, b) => Number(isTemplate(b)) - Number(isTemplate(a)))}
          onCreate={(newName, base) => {
            saves.put(newName, SavedSession({ basedOn: base.name }))
            go({ kind: 'character', name: newName, screen: 'sheet' })
          }}
          onSelect={(it) => go({ kind: 'character', name: it.name, screen: 'sheet' })}
          onGlossary={() => go({ kind: 'glossary' })}
          onCredits={() => go({ kind: 'credits' })}
        />
      </>
    )
  }

  const file = fileNumber(sheet, sheets)
  const dice: CharacterDice = (label) => scene.clearingSpiritOn(rollLog.dice(sheet.name, label))
  const toSheet = () => back(route)
  return (
    <FileLabel.Provider value={fileLabel(file)}>
      {top}
      {route.screen === 'sheet' && (
        <SheetScreen
          key={`${sheet.name}/${resets}/${saves.generation}`}
          sheet={sheet}
          session={session}
          scene={scene}
          file={file}
          dice={dice}
          rollLog={rollLog}
          onPathways={() => go({ ...route, screen: 'pathway' })}
          onBuild={() => go({ ...route, screen: 'builder' })}
          onBack={() => back(route)}
          onReset={() => {
            const entry = saves.saved.get(sheet.name)
            const kept = entry ? cleared(entry) : null
            if (kept !== null && kept.basedOn !== null) saves.put(sheet.name, kept)
            else saves.remove(sheet.name)
            rollLog.forget(sheet.name)
            scenes.forget(sheet.name)
            setResets((n) => n + 1)
          }}
          onDelete={
            saved.get(sheet.name)?.basedOn
              ? () => {
                  saves.remove(sheet.name)
                  rollLog.forget(sheet.name)
                  scenes.forget(sheet.name)
                  replace({ kind: 'gallery' })
                }
              : null
          }
        />
      )}
      {route.screen === 'pathway' && (
        <PathwayScreen
          sheet={sheet}
          session={session}
          dice={scene.clearingSpiritOn(rollLog.dice(sheet.name, 'Potion Consumption Roll'))}
          file={file}
          pcrModifier={-scene.spiritPending.value}
          onBack={toSheet}
        />
      )}
      {route.screen === 'builder' && <BuilderScreen sheet={sheet} session={session} onBack={toSheet} />}
    </FileLabel.Provider>
  )
}
