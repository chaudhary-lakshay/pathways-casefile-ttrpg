// SessionStore.kt's job in a browser: one localStorage entry holding the same
// text Android writes to sessions.json. Where Android discards an unreadable
// file, this keeps it aside first, so a bad decode never costs a save.
import { decodeSessions, encodeSessions, type SavedSession } from './SessionStore'

/** Namespaced: every Pages project on one GitHub account shares an origin. */
export const KEY = 'pathways/sessions'
export const BACKUP_KEY = 'pathways/sessions.unreadable'

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export interface Loaded {
  sessions: Map<string, SavedSession>
  /** The stored text could not be read; it is kept under [BACKUP_KEY]. */
  unreadable: boolean
}

export class BrowserStore {
  constructor(private readonly storage: StorageLike) {}

  private read(key: string): string | null {
    try {
      return this.storage.getItem(key)
    } catch {
      return null
    }
  }

  private write(key: string, value: string): boolean {
    try {
      this.storage.setItem(key, value)
      return true
    } catch {
      return false
    }
  }

  load(): Loaded {
    const raw = this.read(KEY)
    if (raw === null) return { sessions: new Map(), unreadable: false }
    try {
      return { sessions: decodeSessions(raw), unreadable: false }
    } catch {
      // ponytail: one backup slot; the first unreadable text wins, since a
      // later one is most likely the same corruption read again.
      if (this.read(BACKUP_KEY) === null) this.write(BACKUP_KEY, raw)
      return { sessions: new Map(), unreadable: true }
    }
  }

  /** False when the browser refused the write: private mode, or full. */
  save(sessions: Map<string, SavedSession>): boolean {
    return this.write(KEY, encodeSessions(sessions))
  }

  exportText(): string {
    return this.read(KEY) ?? '{}'
  }

  /**
   * Replaces every save with the file's, as copying sessions.json onto a
   * phone would. Throws, writing nothing, when the text is not a save.
   */
  importText(text: string): Map<string, SavedSession> {
    const sessions = decodeSessions(text)
    this.save(sessions)
    return sessions
  }

  unreadableText(): string | null {
    return this.read(BACKUP_KEY)
  }

  discardUnreadable() {
    try {
      this.storage.removeItem(BACKUP_KEY)
    } catch {
      // nothing to keep tidy if storage is gone
    }
  }
}
