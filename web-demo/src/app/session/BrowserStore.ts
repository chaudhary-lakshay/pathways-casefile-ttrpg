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

/**
 * `saved`; `stale` when another tab wrote since this one last read, so its
 * snapshot would delete that tab's work (load again); `blocked` while the
 * stored text is an unreadable save not yet safe in the backup slot;
 * `unavailable` when storage could not be read at all; `failed` when the
 * browser refused the write.
 */
export type SaveResult = 'saved' | 'stale' | 'blocked' | 'unavailable' | 'failed'

const UNREAD = Symbol('unread')

export class BrowserStore {
  /** The text this store last read or wrote under [KEY]; UNREAD if it never could. */
  private seen: string | null | typeof UNREAD = UNREAD
  /** An unreadable text waiting for the backup slot to be free. */
  private waiting: string | null = null

  constructor(private readonly storage: StorageLike) {}

  private read(key: string): string | null | typeof UNREAD {
    try {
      return this.storage.getItem(key)
    } catch {
      return UNREAD
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

  /** Keeps [raw] in the backup slot unless the slot holds another text. */
  private keepAside(raw: string): boolean {
    const slot = this.read(BACKUP_KEY)
    if (slot === raw) return true
    return slot === null && this.write(BACKUP_KEY, raw)
  }

  load(): Loaded {
    const raw = this.read(KEY)
    this.seen = raw
    this.waiting = null
    if (raw === UNREAD || raw === null) return { sessions: new Map(), unreadable: false }
    try {
      return { sessions: decodeSessions(raw), unreadable: false }
    } catch {
      if (!this.keepAside(raw)) this.waiting = raw
      return { sessions: new Map(), unreadable: true }
    }
  }

  save(sessions: Map<string, SavedSession>): SaveResult {
    if (this.seen === UNREAD) return 'unavailable'
    if (this.waiting !== null) return 'blocked'
    const now = this.read(KEY)
    if (now === UNREAD) return 'unavailable'
    if (now !== this.seen) return 'stale'
    const text = encodeSessions(sessions)
    if (!this.write(KEY, text)) return 'failed'
    this.seen = text
    return 'saved'
  }

  exportText(): string {
    const raw = this.read(KEY)
    return raw === UNREAD || raw === null ? '{}' : raw
  }

  /**
   * Replaces every save with the file's, as copying sessions.json onto a
   * phone would. Throws, writing nothing, when the text is not a save or the
   * browser refuses it.
   */
  importText(text: string): Map<string, SavedSession> {
    const sessions = decodeSessions(text)
    if (!this.write(KEY, encodeSessions(sessions))) throw new Error('The browser refused to store the save.')
    this.seen = this.read(KEY)
    this.waiting = null
    return sessions
  }

  unreadableText(): string | null {
    const slot = this.read(BACKUP_KEY)
    return slot === UNREAD ? null : slot
  }

  /** Empties the backup slot; an unreadable save waiting for it moves in. */
  discardUnreadable() {
    try {
      this.storage.removeItem(BACKUP_KEY)
    } catch {
      return
    }
    if (this.waiting !== null && this.keepAside(this.waiting)) this.waiting = null
  }
}
