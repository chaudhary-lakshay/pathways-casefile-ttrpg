// MainActivity.kt App()'s `saved` map and its store, as a subscribable object.
// Android stores the whole map after every change; so does this. What
// Android cannot meet (a second tab, a refused write, an unreadable file kept
// aside) becomes `notice`, printed by the app shell.
import type { BrowserStore } from './session/BrowserStore'
import type { SavedSession } from './session/SessionStore'

export class Saves {
  saved: Map<string, SavedSession>
  /** Bumped when the map is replaced from storage: every open session is rebuilt. */
  generation = 0
  notice: string | null = null
  unreadable: string | null
  version = 0
  private readonly listeners = new Set<() => void>()

  constructor(private readonly store: BrowserStore) {
    this.saved = store.load().sessions
    this.unreadable = store.unreadableText()
  }

  subscribe = (l: () => void) => {
    this.listeners.add(l)
    return () => this.listeners.delete(l)
  }
  getVersion = () => this.version

  private changed() {
    this.version++
    this.listeners.forEach((l) => l())
  }

  private persist() {
    const result = this.store.save(this.saved)
    if (result === 'saved') return
    if (result === 'stale') {
      this.reload()
      this.notice = 'Another tab changed your saves, so they were reloaded here. Your last change was not saved.'
    } else if (result === 'blocked') {
      this.notice = 'Changes are not saved while an unreadable save is waiting: export it or discard it first.'
    } else {
      this.notice = 'This browser refused to store your progress (private mode or full storage). Export to keep it.'
    }
  }

  /** `quiet`: stored without notifying, for a repair made while a screen renders. */
  put(name: string, s: SavedSession, quiet = false) {
    this.saved.set(name, s)
    this.persist()
    if (!quiet) this.changed()
  }

  remove(name: string) {
    this.saved.delete(name)
    this.persist()
    this.changed()
  }

  /** Reads the saves again: another tab wrote, or an import replaced them. */
  reload() {
    this.saved = this.store.load().sessions
    this.unreadable = this.store.unreadableText()
    this.generation++
    this.changed()
  }

  importText(text: string) {
    this.store.importText(text)
    this.notice = null
    this.reload()
  }

  exportText = () => this.store.exportText()

  discardUnreadable() {
    this.store.discardUnreadable()
    this.unreadable = this.store.unreadableText()
    this.changed()
  }

  dismissNotice() {
    this.notice = null
    this.changed()
  }
}
